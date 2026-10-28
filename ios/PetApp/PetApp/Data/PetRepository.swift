import Foundation
import SwiftData

// MARK: - 存储协议（Room / SwiftData 差异被此层屏蔽，上层 UI 不感知）

/// 宠物存储协议：iOS 17+ 用 SwiftData，iOS 16 用 JSON 文件兜底（架构文档 §9 方案，接口不变）。
protocol PetStoreProtocol {
    func loadProfile(id: String) -> PetProfile?
    func saveProfile(_ profile: PetProfile)
    func loadReminders() -> [ReminderConfig]
    func saveReminder(_ config: ReminderConfig)
    func deleteReminder(id: String)
}

// MARK: - SwiftData 存储（iOS 17+，持久化主通道）

@available(iOS 17.0, *)
final class SwiftDataPetStore: PetStoreProtocol {

    private let container: ModelContainer
    private let context: ModelContext

    init?(inMemory: Bool = false) {
        let schema = Schema([PetProfileModel.self, ReminderConfigModel.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        guard let container = try? ModelContainer(for: schema, configurations: [configuration]) else {
            return nil
        }
        self.container = container
        self.context = ModelContext(container)
        context.autosaveEnabled = true
    }

    func loadProfile(id: String) -> PetProfile? {
        let descriptor = FetchDescriptor<PetProfileModel>(predicate: #Predicate { $0.id == id })
        let models = (try? context.fetch(descriptor)) ?? []
        return models.first?.toStruct()
    }

    func saveProfile(_ profile: PetProfile) {
        let descriptor = FetchDescriptor<PetProfileModel>(predicate: #Predicate { $0.id == profile.id })
        let models = (try? context.fetch(descriptor)) ?? []
        if let existing = models.first {
            existing.apply(profile)
        } else {
            context.insert(PetProfileModel(profile: profile))
        }
        try? context.save()
    }

    func loadReminders() -> [ReminderConfig] {
        let descriptor = FetchDescriptor<ReminderConfigModel>(
            sortBy: [SortDescriptor(\.nextTriggerAt, order: .forward)]
        )
        return ((try? context.fetch(descriptor)) ?? []).map { $0.toStruct() }
    }

    func saveReminder(_ config: ReminderConfig) {
        let descriptor = FetchDescriptor<ReminderConfigModel>(predicate: #Predicate { $0.id == config.id })
        let models = (try? context.fetch(descriptor)) ?? []
        if let existing = models.first {
            existing.apply(config)
        } else {
            context.insert(ReminderConfigModel(config: config))
        }
        try? context.save()
    }

    func deleteReminder(id: String) {
        let descriptor = FetchDescriptor<ReminderConfigModel>(predicate: #Predicate { $0.id == id })
        let models = (try? context.fetch(descriptor)) ?? []
        for model in models {
            context.delete(model)
        }
        try? context.save()
    }
}

// MARK: - JSON 文件存储（iOS 16 兜底 / 灵动岛 Extension 共享通道）

/// JSON 文件存储：profile.json + reminders.json（Application Support/PetStore/）。
/// 同时供灵动岛「喂食快捷按钮」写 pending 动作文件（App Group 共享）。
final class FilePetStore: PetStoreProtocol {

    private let directory: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(directory: URL? = nil) {
        if let directory = directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            self.directory = base.appendingPathComponent("PetStore", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
        let formatter = JSONEncoder()
        formatter.outputFormatting = [.prettyPrinted, .sortedKeys]
        self.encoder = formatter
        self.decoder = JSONDecoder()
    }

    private var profileURL: URL { directory.appendingPathComponent("profile.json") }
    private var remindersURL: URL { directory.appendingPathComponent("reminders.json") }

    func loadProfile(id: String) -> PetProfile? {
        guard let data = try? Data(contentsOf: profileURL),
              let profile = try? decoder.decode(PetProfile.self, from: data),
              profile.id == id else {
            return nil
        }
        return profile
    }

    func saveProfile(_ profile: PetProfile) {
        if let data = try? encoder.encode(profile) {
            try? data.write(to: profileURL, options: .atomic)
        }
    }

    func loadReminders() -> [ReminderConfig] {
        guard let data = try? Data(contentsOf: remindersURL),
              let list = try? decoder.decode([ReminderConfig].self, from: data) else {
            return []
        }
        return list
    }

    func saveReminder(_ config: ReminderConfig) {
        var list = loadReminders()
        if let index = list.firstIndex(where: { $0.id == config.id }) {
            list[index] = config
        } else {
            list.append(config)
        }
        if let data = try? encoder.encode(list) {
            try? data.write(to: remindersURL, options: .atomic)
        }
    }

    func deleteReminder(id: String) {
        var list = loadReminders()
        list.removeAll { $0.id == id }
        if let data = try? encoder.encode(list) {
            try? data.write(to: remindersURL, options: .atomic)
        }
    }
}

// MARK: - 仓储（与 Android data/PetRepository.kt 同名同接口）

/// 宠物仓储：读写档案、喂食事务、惰性衰减结算入口。
///
/// 约定（架构文档 §8 共享知识 #9）：
/// - 任何读取状态的入口都先过 lazilyApplyDecay；
/// - 用户互动（点按/喂食/聊天）后刷新 lastActiveAt。
actor PetRepository {

    private let store: PetStoreProtocol
    private let rules: GrowthEngine.GrowthRules
    private let foods: [GrowthEngine.FoodItem]
    private let defaultProfile: PetProfile

    init(
        store: PetStoreProtocol? = nil,
        assetsLoader: AssetsLoader = AssetsLoader()
    ) {
        let rules = assetsLoader.loadGrowthRules()
        self.rules = rules
        self.foods = assetsLoader.loadFoods()
        self.defaultProfile = assetsLoader.loadDefaultProfile(rules: rules)

        // 存储选择：iOS 17+ SwiftData；iOS 16 / SwiftData 初始化失败 → JSON 文件兜底
        if let provided = store {
            self.store = provided
        } else if #available(iOS 17.0, *), let swiftDataStore = SwiftDataPetStore() {
            self.store = swiftDataStore
        } else {
            self.store = FilePetStore()
        }
    }

    // ---------- 档案读写 ----------

    /// 获取档案（保证存在 + 先结算惰性衰减再返回）。
    func getProfile() -> PetProfile {
        let now = Date.now.millisecondsSince1970
        if let existing = store.loadProfile(id: defaultProfile.id) {
            return lazilyApplyDecay(existing, now: now)
        }
        store.saveProfile(defaultProfile)
        return defaultProfile
    }

    /// 重命名（互动：刷新 lastActiveAt）。
    func rename(_ name: String) {
        let now = Date.now.millisecondsSince1970
        var profile = ensureProfile(now: now)
        profile.name = name
        profile.lastActiveAt = now
        store.saveProfile(profile)
    }

    /// 追加经验（互动入口之一；返回是否升级）。
    func addExp(_ amount: Int) -> Bool {
        let now = Date.now.millisecondsSince1970
        var profile = ensureProfile(now: now)
        var state = GrowthEngine.GrowthState(
            level: profile.level, exp: profile.exp + amount,
            hunger: profile.hunger, mood: profile.mood, intimacy: profile.intimacy
        )
        state = GrowthEngine.checkLevelUp(state, rules)
        let leveledUp = state.level > profile.level
        profile.level = state.level
        profile.exp = state.exp
        profile.lastActiveAt = now
        store.saveProfile(profile)
        return leveledUp
    }

    // ---------- 喂食事务（时序图 5.3：decay → feed → levelUp → 持久化） ----------

    /// 喂食：找不到食物 id 时返回 nil（UI 侧提示）。
    func feed(foodId: String) -> GrowthEngine.FeedResult? {
        guard let food = foods.first(where: { $0.id == foodId }) else { return nil }
        let now = Date.now.millisecondsSince1970
        let profile = ensureProfile(now: now)

        // 1. 先结算惰性衰减
        var state = GrowthEngine.GrowthState(
            level: profile.level, exp: profile.exp,
            hunger: profile.hunger, mood: profile.mood, intimacy: profile.intimacy
        )
        state = GrowthEngine.applyElapsedDecay(state, lastActiveAt: profile.lastActiveAt, now: now, rules: rules)
        // 2. 喂食增益
        state = GrowthEngine.applyFeed(state, food, rules)
        // 3. 升级判定
        state = GrowthEngine.checkLevelUp(state, rules)

        var updated = profile
        updated.level = state.level
        updated.exp = state.exp
        updated.hunger = state.hunger
        updated.mood = state.mood
        updated.intimacy = state.intimacy
        updated.lastActiveAt = now // 喂食是用户互动：刷新 lastActiveAt
        store.saveProfile(updated)

        return GrowthEngine.FeedResult(
            foodId: food.id,
            hungerBefore: profile.hunger,
            hungerAfter: state.hunger,
            moodBefore: profile.mood,
            moodAfter: state.mood,
            intimacyBefore: profile.intimacy,
            intimacyAfter: state.intimacy,
            expGain: food.expGain,
            leveledUp: state.level > profile.level,
            newLevel: state.level
        )
    }

    /// 点按/聊天等轻互动：应用点按增益并刷新 lastActiveAt。
    func recordInteraction() -> PetProfile {
        let now = Date.now.millisecondsSince1970
        var profile = ensureProfile(now: now)
        var state = GrowthEngine.GrowthState(
            level: profile.level, exp: profile.exp,
            hunger: profile.hunger, mood: profile.mood, intimacy: profile.intimacy
        )
        state = GrowthEngine.applyTapReaction(state, rules)
        profile.mood = state.mood
        profile.intimacy = state.intimacy
        profile.lastActiveAt = now
        store.saveProfile(profile)
        return profile
    }

    // ---------- 提醒配置 ----------

    func getReminders() -> [ReminderConfig] {
        store.loadReminders()
    }

    func getEnabledReminders() -> [ReminderConfig] {
        store.loadReminders().filter { $0.enabled }
    }

    func upsertReminder(_ config: ReminderConfig) {
        store.saveReminder(config)
    }

    func deleteReminder(id: String) {
        store.deleteReminder(id: id)
    }

    /// 依据计划器更新某条提醒的下次触发时间并持久化。
    func planNextTrigger(_ config: ReminderConfig, fromMillis: Int64 = Date.now.millisecondsSince1970) -> ReminderConfig? {
        let next = ReminderPlanner.nextTriggerTime(config, fromMillis: fromMillis)
        guard next != 0 else { return nil }
        var updated = config
        updated.nextTriggerAt = next
        store.saveReminder(updated)
        return updated
    }

    // ---------- 导出（CloudSyncAdapter 预留格式，docs/04 §6） ----------

    /// 导出快照 JSON（snake_case），即未来云同步协议。
    func exportSnapshot() -> String {
        let now = Date.now.millisecondsSince1970
        let profile = lazilyApplyDecay(ensureProfile(now: now), now: now)
        let reminders = store.loadReminders()

        let profileDict: [String: Any] = [
            "id": profile.id,
            "name": profile.name,
            "level": profile.level,
            "exp": profile.exp,
            "hunger": profile.hunger,
            "mood": profile.mood,
            "intimacy": profile.intimacy,
            "last_active_at": profile.lastActiveAt,
            "created_at": profile.createdAt
        ]
        let remindersArray: [[String: Any]] = reminders.map { reminder in
            var dict: [String: Any] = [
                "id": reminder.id,
                "template_key": reminder.templateKey,
                "interval_minutes": reminder.intervalMinutes,
                "message": reminder.message,
                "enabled": reminder.enabled
            ]
            if let next = reminder.nextTriggerAt {
                dict["next_trigger_at"] = next
            }
            return dict
        }
        let root: [String: Any] = [
            "schema_version": 1,
            "profile": profileDict,
            "reminders": remindersArray
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys]),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }

    // ---------- 内部工具 ----------

    /// 保证档案行存在。
    private func ensureProfile(now: Int64) -> PetProfile {
        store.loadProfile(id: defaultProfile.id) ?? defaultProfile
    }

    /// 惰性衰减结算：衰减 → 持久化 → 返回最新档案。不刷新 lastActiveAt（衰减不是用户互动）。
    private func lazilyApplyDecay(_ profile: PetProfile, now: Int64) -> PetProfile {
        var state = GrowthEngine.GrowthState(
            level: profile.level, exp: profile.exp,
            hunger: profile.hunger, mood: profile.mood, intimacy: profile.intimacy
        )
        state = GrowthEngine.applyElapsedDecay(state, lastActiveAt: profile.lastActiveAt, now: now, rules: rules)
        var updated = profile
        updated.hunger = state.hunger
        updated.mood = state.mood
        updated.intimacy = state.intimacy
        store.saveProfile(updated)
        return updated
    }
}
