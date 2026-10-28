import SwiftUI

/// App 环境容器（手写注入，架构文档 §1.3：不引 DI 框架）。
/// 各处通过 `AppEnvironment.shared` 访问共享实例。
@MainActor
final class AppEnvironment: ObservableObject {

    static let shared = AppEnvironment()

    /// 宠物仓储（SwiftData / iOS16 文件兜底由内部选择）。
    let repository: PetRepository

    /// 对话引擎（语料 dialogue_zh.json）。
    let dialogueEngine: DialogueEngine

    /// 食物表（foods.json）。
    let foods: [GrowthEngine.FoodItem]

    /// 提醒模板（reminder_templates.json）。
    let reminderTemplates: [AssetsLoader.ReminderTemplate]

    /// 养成规则（growth_rules.json）。
    let growthRules: GrowthEngine.GrowthRules

    /// 提醒调度器（UNUserNotificationCenter）。
    let reminderScheduler: IOSReminderScheduler

    /// 灵动岛控制器（iOS < 16.1 时为空实现，自动降级为仅小组件）。
    let liveActivityController: LiveActivityControlling

    private init() {
        let loader = AssetsLoader()
        self.repository = PetRepository(assetsLoader: loader)
        self.dialogueEngine = DialogueEngine(corpus: loader.loadDialogueCorpus())
        self.foods = loader.loadFoods()
        self.reminderTemplates = loader.loadReminderTemplates()
        self.growthRules = loader.loadGrowthRules()
        self.reminderScheduler = IOSReminderScheduler(repository: repository)
        self.liveActivityController = LiveActivityControllerFactory.create()
    }
}

/// App 入口：环境容器初始化 + 根视图。
@main
struct PetApp: App {

    @StateObject private var environment = AppEnvironment.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(environment)
                .onAppear {
                    // 首次启动确保档案行存在（默认宠物「小希」）
                    Task { _ = try? await AppEnvironment.shared.repository.getProfile() }
                }
        }
    }
}
