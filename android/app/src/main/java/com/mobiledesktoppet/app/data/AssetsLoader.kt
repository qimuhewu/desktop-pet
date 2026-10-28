package com.mobiledesktoppet.app.data

import android.content.Context
import com.mobiledesktoppet.app.domain.DialogueEngine
import com.mobiledesktoppet.app.domain.GrowthEngine
import com.mobiledesktoppet.app.domain.ReminderPlanner
import org.json.JSONArray
import org.json.JSONObject

/** 提醒模板（对应 reminder_templates.json 的 templates[]，用于提醒管理页 UI）。 */
data class ReminderTemplate(
    val key: String,
    val name: String,
    val defaultIntervalMinutes: Int,
    val message: String
)

/**
 * 共享资产加载器：读取 assets 下的共享 JSON（规则/语料/食物/模板）。
 * 双端加载同一份文件（shared/ 同步副本），解析失败时返回引擎内置兜底值，绝不抛异常。
 */
class AssetsLoader(private val context: Context) {

    /** 从 assets 读取文本；失败返回 null。 */
    private fun readAssetText(fileName: String): String? = try {
        context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }

    // ---------- 养成规则 ----------

    fun loadGrowthRules(): GrowthEngine.GrowthRules {
        val root = readAssetText("growth_rules.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return GrowthEngine.GrowthRules.fallback()
        return try {
            val decay = root.getJSONObject("decayPerHour")
            val curve = root.getJSONObject("levelCurve")
            val clamp = root.getJSONObject("clamp")
            val thresholds = root.getJSONObject("thresholds")
            val tap = root.getJSONObject("tapReaction")
            val defaults = root.getJSONObject("defaults")
            GrowthEngine.GrowthRules(
                hungerDecayPerHour = decay.getDouble("hunger"),
                moodDecayPerHour = decay.getDouble("mood"),
                intimacyDecayPerHour = decay.getDouble("intimacy"),
                levelBase = curve.getInt("base"),
                levelStep = curve.getInt("step"),
                maxLevel = curve.getInt("maxLevel"),
                valueMin = clamp.getDouble("min"),
                valueMax = clamp.getDouble("max"),
                hungerLowThreshold = thresholds.getInt("hungerLow"),
                moodLowThreshold = thresholds.getInt("moodLow"),
                tapMoodGain = tap.getInt("moodGain"),
                tapIntimacyGain = tap.getInt("intimacyGain"),
                defaultName = defaults.getString("name")
            )
        } catch (_: Exception) {
            GrowthEngine.GrowthRules.fallback()
        }
    }

    /** 默认档案初值（growth_rules.json 的 defaults）。 */
    fun loadDefaultProfileValues(rules: GrowthEngine.GrowthRules): GrowthEngine.GrowthState {
        val root = readAssetText("growth_rules.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return GrowthEngine.GrowthState()
        return try {
            val defaults = root.getJSONObject("defaults")
            GrowthEngine.GrowthState(
                level = defaults.optInt("level", 1),
                exp = defaults.optInt("exp", 0),
                hunger = defaults.optDouble("hunger", 80.0),
                mood = defaults.optDouble("mood", 60.0),
                intimacy = defaults.optDouble("intimacy", 50.0)
            )
        } catch (_: Exception) {
            GrowthEngine.GrowthState()
        }
    }

    /** 默认档案 id（growth_rules.json 的 defaults.profileId）。 */
    fun loadDefaultProfileId(): String {
        val root = readAssetText("growth_rules.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return PetDatabaseIds.DEFAULT_PROFILE_ID
        return try {
            root.getJSONObject("defaults").optString("profileId", PetDatabaseIds.DEFAULT_PROFILE_ID)
        } catch (_: Exception) {
            PetDatabaseIds.DEFAULT_PROFILE_ID
        }
    }

    // ---------- 食物表 ----------

    fun loadFoods(): List<GrowthEngine.FoodItem> {
        val root = readAssetText("foods.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return emptyList()
        return try {
            val array: JSONArray = root.getJSONArray("foods")
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                GrowthEngine.FoodItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    hungerGain = o.getInt("hungerGain"),
                    moodGain = o.getInt("moodGain"),
                    intimacyGain = o.getInt("intimacyGain"),
                    expGain = o.getInt("expGain"),
                    iconKey = o.getString("iconKey")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- 对话语料 ----------

    fun loadDialogueCorpus(): DialogueEngine.DialogueCorpus {
        val root = readAssetText("dialogue_zh.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return DialogueEngine.DialogueCorpus.fallback()
        return try {
            val rulesArray: JSONArray = root.getJSONArray("rules")
            val rules = (0 until rulesArray.length()).map { i ->
                val r = rulesArray.getJSONObject(i)
                val keywords = mutableListOf<String>()
                val kwArray = r.getJSONArray("keywords")
                for (j in 0 until kwArray.length()) keywords.add(kwArray.getString(j))
                val responses = mutableListOf<String>()
                val respArray = r.getJSONArray("responses")
                for (j in 0 until respArray.length()) responses.add(respArray.getString(j))
                DialogueEngine.DialogueRule(
                    id = r.getString("id"),
                    keywords = keywords,
                    priority = r.getInt("priority"),
                    responses = responses
                )
            }
            val fallbackList = mutableListOf<String>()
            val fallbackArray = root.getJSONArray("fallback")
            for (j in 0 until fallbackArray.length()) fallbackList.add(fallbackArray.getString(j))
            val tapList = mutableListOf<String>()
            val tapArray = root.getJSONArray("tapReactions")
            for (j in 0 until tapArray.length()) tapList.add(tapArray.getString(j))
            val feed = root.getJSONObject("feedReactions")
            DialogueEngine.DialogueCorpus(
                rules = rules,
                fallback = fallbackList,
                tapReactions = tapList,
                feedReactionFull = feed.getString("full"),
                feedReactionNormal = feed.getString("normal")
            )
        } catch (_: Exception) {
            DialogueEngine.DialogueCorpus.fallback()
        }
    }

    // ---------- 提醒模板 ----------

    fun loadReminderTemplates(): List<ReminderTemplate> {
        val root = readAssetText("reminder_templates.json")?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        } ?: return emptyList()
        return try {
            val array: JSONArray = root.getJSONArray("templates")
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ReminderTemplate(
                    key = o.getString("key"),
                    name = o.getString("name"),
                    defaultIntervalMinutes = o.getInt("defaultIntervalMinutes"),
                    message = o.getString("message")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** 内部引用 PetDatabase.DEFAULT_PROFILE_ID 的间接层（避免 AssetsLoader 依赖 Room）。 */
private object PetDatabaseIds {
    const val DEFAULT_PROFILE_ID = "default_pet"
}
