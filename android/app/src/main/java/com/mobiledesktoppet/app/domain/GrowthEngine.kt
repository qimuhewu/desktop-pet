package com.mobiledesktoppet.app.domain

/**
 * 成长引擎（纯逻辑，无平台依赖，与 iOS Domain/GrowthEngine.swift 镜像实现）。
 *
 * 算法唯一事实源：docs/04-数据模型与养成规则规范.md §3。
 * 规则常量唯一来源：shared/assets/growth_rules.json（代码禁止硬编码，
 * [GrowthRules.fallback] 仅为资产缺失时的应急兜底，数值与 JSON 一致）。
 *
 * 约定（架构文档 §8 共享知识 #9）：
 * - 任何读取宠物状态的入口必须先过 [applyElapsedDecay]（惰性衰减）；
 * - 衰减不修改 lastActiveAt，喂食/互动后才刷新。
 */
object GrowthEngine {

    /** 宠物可成长状态（hunger/mood/intimacy 用 Double 连续结算，展示时取整，见 docs/04 §1.5）。 */
    data class GrowthState(
        val level: Int = 1,
        val exp: Int = 0,
        val hunger: Double = 80.0,
        val mood: Double = 60.0,
        val intimacy: Double = 50.0
    )

    /** 养成规则常量（对应 growth_rules.json）。 */
    data class GrowthRules(
        val hungerDecayPerHour: Double = -2.0,
        val moodDecayPerHour: Double = -1.5,
        val intimacyDecayPerHour: Double = -0.5,
        val levelBase: Int = 100,
        val levelStep: Int = 50,
        val maxLevel: Int = 99,
        val valueMin: Double = 0.0,
        val valueMax: Double = 100.0,
        val hungerLowThreshold: Int = 30,
        val moodLowThreshold: Int = 30,
        val tapMoodGain: Int = 1,
        val tapIntimacyGain: Int = 1,
        val defaultName: String = "小希"
    ) {
        companion object {
            /** 资产缺失时的应急兜底（数值与 growth_rules.json v1 一致）。 */
            fun fallback(): GrowthRules = GrowthRules()
        }
    }

    /** 食物条目（对应 foods.json 的 foods[]）。 */
    data class FoodItem(
        val id: String,
        val name: String,
        val hungerGain: Int,
        val moodGain: Int,
        val intimacyGain: Int,
        val expGain: Int,
        val iconKey: String
    )

    /** 喂食结果：数值前后变化 + 是否升级。 */
    data class FeedResult(
        val foodId: String,
        val hungerBefore: Double,
        val hungerAfter: Double,
        val moodBefore: Double,
        val moodAfter: Double,
        val intimacyBefore: Double,
        val intimacyAfter: Double,
        val expGain: Int,
        val leveledUp: Boolean,
        val newLevel: Int
    )

    /** 数值封顶。 */
    private fun clamp(value: Double, rules: GrowthRules): Double =
        value.coerceIn(rules.valueMin, rules.valueMax)

    /** 升级曲线：expToNextLevel(L) = levelBase + (L-1) * levelStep。 */
    fun expToNextLevel(level: Int, rules: GrowthRules): Int =
        rules.levelBase + (level - 1) * rules.levelStep

    /**
     * 惰性衰减：按 lastActiveAt 到 now 的流逝小时数连续结算。
     * 不修改 lastActiveAt（由调用方在用户互动后刷新）。
     */
    fun applyElapsedDecay(
        state: GrowthState,
        lastActiveAt: Long,
        now: Long,
        rules: GrowthRules
    ): GrowthState {
        val elapsedHours = (now - lastActiveAt).coerceAtLeast(0L) / 3_600_000.0
        return state.copy(
            hunger = clamp(state.hunger + rules.hungerDecayPerHour * elapsedHours, rules),
            mood = clamp(state.mood + rules.moodDecayPerHour * elapsedHours, rules),
            intimacy = clamp(state.intimacy + rules.intimacyDecayPerHour * elapsedHours, rules)
        )
    }

    /** 喂食增益（不含升级判定，调用方紧接着调用 [checkLevelUp]）。 */
    fun applyFeed(state: GrowthState, food: FoodItem, rules: GrowthRules): GrowthState = state.copy(
        hunger = clamp(state.hunger + food.hungerGain, rules),
        mood = clamp(state.mood + food.moodGain, rules),
        intimacy = clamp(state.intimacy + food.intimacyGain, rules),
        exp = state.exp + food.expGain
    )

    /** 升级判定：可连升多级；满级后经验封顶为 expToNextLevel(maxLevel) - 1。 */
    fun checkLevelUp(state: GrowthState, rules: GrowthRules): GrowthState {
        var level = state.level
        var exp = state.exp
        while (level < rules.maxLevel && exp >= expToNextLevel(level, rules)) {
            exp -= expToNextLevel(level, rules)
            level += 1
        }
        if (level >= rules.maxLevel) {
            exp = exp.coerceAtMost(expToNextLevel(rules.maxLevel, rules) - 1)
        }
        return state.copy(level = level, exp = exp)
    }

    /** 点按互动增益（tapReaction.moodGain / tapIntimacyGain，不产经验）。 */
    fun applyTapReaction(state: GrowthState, rules: GrowthRules): GrowthState = state.copy(
        mood = clamp(state.mood + rules.tapMoodGain, rules),
        intimacy = clamp(state.intimacy + rules.tapIntimacyGain, rules)
    )
}
