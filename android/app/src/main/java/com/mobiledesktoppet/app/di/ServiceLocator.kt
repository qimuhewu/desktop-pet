package com.mobiledesktoppet.app.di

import android.content.Context
import com.mobiledesktoppet.app.data.AssetsLoader
import com.mobiledesktoppet.app.data.PetRepository
import com.mobiledesktoppet.app.data.ReminderTemplate
import com.mobiledesktoppet.app.data.db.PetDatabase
import com.mobiledesktoppet.app.domain.DialogueEngine
import com.mobiledesktoppet.app.domain.GrowthEngine
import com.mobiledesktoppet.app.domain.GrowthEngine.FoodItem
import com.mobiledesktoppet.app.domain.GrowthEngine.GrowthRules
import com.mobiledesktoppet.app.render.FallbackPetRenderer
import com.mobiledesktoppet.app.render.Live2DRenderer
import com.mobiledesktoppet.app.render.ModelAssetManager
import com.mobiledesktoppet.app.render.PetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * 轻量依赖定位器（决策 D8：不引 Hilt，个人项目降低构建面）。
 * 在 [com.mobiledesktoppet.app.PetApplication.onCreate] 中初始化一次。
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** 是否已初始化（Receiver 等无上下文入口需检查）。 */
    val isInitialized: Boolean get() = this::appContext.isInitialized

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
    }

    private val assetsLoader: AssetsLoader by lazy { AssetsLoader(appContext) }

    val database: PetDatabase by lazy { PetDatabase.build(appContext) }

    /** 养成规则（唯一来源 growth_rules.json）。 */
    val growthRules: GrowthRules by lazy { assetsLoader.loadGrowthRules() }

    /** 食物表（唯一来源 foods.json）。 */
    val foods: List<FoodItem> by lazy { assetsLoader.loadFoods() }

    /** 对话引擎（语料 dialogue_zh.json）。 */
    val dialogueEngine: DialogueEngine by lazy {
        DialogueEngine(assetsLoader.loadDialogueCorpus())
    }

    /** 提醒模板（reminder_templates.json，UI 选择用）。 */
    val reminderTemplates: List<ReminderTemplate> by lazy {
        assetsLoader.loadReminderTemplates()
    }

    /** 宠物仓储（Room 持久化）。 */
    val petRepository: PetRepository by lazy {
        PetRepository(
            profileDao = database.petProfileDao(),
            reminderDao = database.reminderDao(),
            rules = growthRules,
            foods = foods,
            defaultProfileId = assetsLoader.loadDefaultProfileId(),
            defaultName = growthRules.defaultName,
            defaultState = assetsLoader.loadDefaultProfileValues(growthRules)
        )
    }

    /** 应用级 IO 协程作用域（Service/Receiver 内轻量异步任务）。 */
    val applicationScope = CoroutineScope(Dispatchers.Default)

    /**
     * 宠物渲染器工厂（时序图 5.1 的 alt 分支）：
     * 有模型且 Live2D 加载成功 → Live2DRenderer；否则一律 FallbackPetRenderer 兜底，不崩溃。
     * @return Pair(渲染器, 是否 Live2D 路径)
     */
    fun createPetRenderer(context: Context): Pair<PetRenderer, Boolean> {
        val assetManager = ModelAssetManager(context)
        val modelDir = assetManager.resolveActiveModelDir()
        if (modelDir != null) {
            val live2d = Live2DRenderer(context)
            if (live2d.loadModel(modelDir.absolutePath)) {
                return live2d to true
            }
            live2d.release()
        }
        val fallback = FallbackPetRenderer(context)
        fallback.loadModel("")
        return fallback to false
    }
}
