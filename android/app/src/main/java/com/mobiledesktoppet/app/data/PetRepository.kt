package com.mobiledesktoppet.app.data

import com.mobiledesktoppet.app.data.db.PetDatabase
import com.mobiledesktoppet.app.data.db.PetProfileDao
import com.mobiledesktoppet.app.data.db.PetProfileEntity
import com.mobiledesktoppet.app.data.db.ReminderDao
import com.mobiledesktoppet.app.data.db.ReminderEntity
import com.mobiledesktoppet.app.domain.GrowthEngine
import com.mobiledesktoppet.app.domain.ReminderPlanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 宠物仓储：读写档案、喂食事务、惰性衰减结算入口（与 iOS Data/PetRepository.swift 同名同接口）。
 *
 * 约定（架构文档 §8 共享知识 #9）：
 * - 任何读取状态的入口都先过 [lazilyApplyDecay]；
 * - 用户互动（点按/喂食/聊天）后刷新 lastActiveAt。
 * 上层 UI 不感知持久化差异（Android=Room / iOS=SwiftData）。
 */
class PetRepository(
    private val profileDao: PetProfileDao,
    private val reminderDao: ReminderDao,
    private val rules: GrowthEngine.GrowthRules,
    private val foods: List<GrowthEngine.FoodItem>,
    private val defaultProfileId: String = PetDatabase.DEFAULT_PROFILE_ID,
    private val defaultName: String = rules.defaultName,
    private val defaultState: GrowthEngine.GrowthState = GrowthEngine.GrowthState(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** 串行化读写，避免并发喂食/结算竞态。 */
    private val mutex = Mutex()

    // ---------- 档案读写 ----------

    /**
     * 获取档案（保证行存在 + 先结算惰性衰减再返回）。
     */
    suspend fun getProfile(): PetProfileEntity = mutex.withLock {
        val now = clock()
        val existing = profileDao.getById(defaultProfileId)
        if (existing == null) {
            val created = PetProfileEntity(
                id = defaultProfileId,
                name = defaultName,
                level = defaultState.level,
                exp = defaultState.exp,
                hunger = defaultState.hunger,
                mood = defaultState.mood,
                intimacy = defaultState.intimacy,
                lastActiveAt = now,
                createdAt = now
            )
            profileDao.upsert(created)
            return created
        }
        lazilyApplyDecayLocked(existing, now)
    }

    /** 观察档案流（UI 状态条实时刷新；读端自行结算衰减可另行调用 getProfile）。 */
    fun observeProfile(): kotlinx.coroutines.flow.Flow<PetProfileEntity?> =
        profileDao.observeById(defaultProfileId)

    /** 重命名（互动：刷新 lastActiveAt）。 */
    suspend fun rename(name: String) = mutex.withLock {
        val now = clock()
        val profile = ensureProfileLocked(now)
        profileDao.upsert(profile.copy(name = name, lastActiveAt = now))
    }

    /** 追加经验（互动入口之一；返回是否升级）。 */
    suspend fun addExp(amount: Int): Boolean = mutex.withLock {
        val now = clock()
        val profile = ensureProfileLocked(now)
        val state = GrowthEngine.GrowthState(
            level = profile.level, exp = profile.exp + amount,
            hunger = profile.hunger, mood = profile.mood, intimacy = profile.intimacy
        )
        val leveled = GrowthEngine.checkLevelUp(state, rules)
        profileDao.upsert(
            profile.copy(
                level = leveled.level,
                exp = leveled.exp,
                lastActiveAt = now
            )
        )
        leveled.level > profile.level
    }

    // ---------- 喂食事务（时序图 5.2：decay → feed → levelUp → 持久化） ----------

    /** 喂食：找不到食物 id 时返回 null（UI 侧提示）。 */
    suspend fun feed(foodId: String): GrowthEngine.FeedResult? = mutex.withLock {
        val food = foods.firstOrNull { it.id == foodId } ?: return null
        val now = clock()
        val profile = ensureProfileLocked(now)

        // 1. 先结算惰性衰减（不刷新 lastActiveAt）
        val decayed = applyDecayToState(profile, now)
        // 2. 喂食增益
        val fed = GrowthEngine.applyFeed(decayed, food, rules)
        // 3. 升级判定
        val leveled = GrowthEngine.checkLevelUp(fed, rules)

        profileDao.upsert(
            profile.copy(
                level = leveled.level,
                exp = leveled.exp,
                hunger = leveled.hunger,
                mood = leveled.mood,
                intimacy = leveled.intimacy,
                lastActiveAt = now // 喂食是用户互动：刷新 lastActiveAt
            )
        )

        GrowthEngine.FeedResult(
            foodId = food.id,
            hungerBefore = profile.hunger,
            hungerAfter = leveled.hunger,
            moodBefore = profile.mood,
            moodAfter = leveled.mood,
            intimacyBefore = profile.intimacy,
            intimacyAfter = leveled.intimacy,
            expGain = food.expGain,
            leveledUp = leveled.level > profile.level,
            newLevel = leveled.level
        )
    }

    /** 点按/聊天等轻互动：应用点按增益并刷新 lastActiveAt。 */
    suspend fun recordInteraction(): PetProfileEntity = mutex.withLock {
        val now = clock()
        val profile = ensureProfileLocked(now)
        val state = GrowthEngine.applyTapReaction(
            GrowthEngine.GrowthState(
                level = profile.level, exp = profile.exp,
                hunger = profile.hunger, mood = profile.mood, intimacy = profile.intimacy
            ),
            rules
        )
        val updated = profile.copy(
            mood = state.mood,
            intimacy = state.intimacy,
            lastActiveAt = now
        )
        profileDao.upsert(updated)
        updated
    }

    // ---------- 提醒配置 ----------

    suspend fun getReminders(): List<ReminderEntity> = reminderDao.getAll()

    suspend fun getEnabledReminders(): List<ReminderEntity> = reminderDao.getEnabled()

    suspend fun upsertReminder(entity: ReminderEntity) {
        reminderDao.upsert(entity)
    }

    suspend fun deleteReminder(id: String) {
        reminderDao.deleteById(id)
    }

    /** 依据计划器更新某条提醒的下次触发时间并持久化。 */
    suspend fun planNextTrigger(entity: ReminderEntity, fromMillis: Long = clock()): ReminderEntity? {
        val config = ReminderPlanner.ReminderConfig(
            id = entity.id,
            templateKey = entity.templateKey,
            intervalMinutes = entity.intervalMinutes,
            message = entity.message,
            enabled = entity.enabled,
            nextTriggerAt = entity.nextTriggerAt
        )
        val next = ReminderPlanner.nextTriggerTime(config, fromMillis)
        if (next == 0L) return null
        val updated = entity.copy(nextTriggerAt = next)
        reminderDao.upsert(updated)
        return updated
    }

    // ---------- 导出（CloudSyncAdapter 预留格式，docs/04 §6） ----------

    /** 导出快照 JSON（snake_case），即未来云同步协议。 */
    suspend fun exportSnapshot(): String = mutex.withLock {
        val now = clock()
        val profile = lazilyApplyDecayLocked(ensureProfileLocked(now), now)
        val reminders = reminderDao.getAll()

        val profileJson = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("level", profile.level)
            put("exp", profile.exp)
            put("hunger", profile.hunger)
            put("mood", profile.mood)
            put("intimacy", profile.intimacy)
            put("last_active_at", profile.lastActiveAt)
            put("created_at", profile.createdAt)
        }
        val remindersJson = JSONArray().apply {
            reminders.forEach { r ->
                put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("template_key", r.templateKey)
                        put("interval_minutes", r.intervalMinutes)
                        put("message", r.message)
                        put("enabled", r.enabled)
                        put("next_trigger_at", r.nextTriggerAt ?: JSONObject.NULL)
                    }
                )
            }
        }
        JSONObject().apply {
            put("schema_version", 1)
            put("profile", profileJson)
            put("reminders", remindersJson)
        }.toString(2)
    }

    // ---------- 内部工具 ----------

    /** 保证档案行存在（调用方已持锁）。 */
    private suspend fun ensureProfileLocked(now: Long): PetProfileEntity {
        return profileDao.getById(defaultProfileId) ?: run {
            val created = PetProfileEntity(
                id = defaultProfileId,
                name = defaultName,
                level = defaultState.level,
                exp = defaultState.exp,
                hunger = defaultState.hunger,
                mood = defaultState.mood,
                intimacy = defaultState.intimacy,
                lastActiveAt = now,
                createdAt = now
            )
            profileDao.upsert(created)
            created
        }
    }

    /**
     * 惰性衰减结算（调用方已持锁）：衰减 → 持久化 → 返回最新档案。
     * 不刷新 lastActiveAt（衰减不是用户互动）。
     */
    private suspend fun lazilyApplyDecayLocked(
        profile: PetProfileEntity,
        now: Long
    ): PetProfileEntity {
        val state = applyDecayToState(profile, now)
        val updated = profile.copy(
            hunger = state.hunger,
            mood = state.mood,
            intimacy = state.intimacy
        )
        profileDao.upsert(updated)
        return updated
    }

    /** 纯衰减计算（不落库）。 */
    private fun applyDecayToState(profile: PetProfileEntity, now: Long): GrowthEngine.GrowthState {
        return GrowthEngine.applyElapsedDecay(
            state = GrowthEngine.GrowthState(
                level = profile.level,
                exp = profile.exp,
                hunger = profile.hunger,
                mood = profile.mood,
                intimacy = profile.intimacy
            ),
            lastActiveAt = profile.lastActiveAt,
            now = now,
            rules = rules
        )
    }
}
