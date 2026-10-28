import Foundation

/// 共享资产加载器：读取 Resources 下共享 JSON（规则/语料/食物/模板）。
/// 双端加载同一份文件（shared/ 同步副本），解析失败时返回引擎内置兜底值，绝不抛异常。
final class AssetsLoader {

    /// 提醒模板（对应 reminder_templates.json 的 templates[]，用于提醒管理页 UI）。
    struct ReminderTemplate: Codable, Identifiable {
        var key: String
        var name: String
        var defaultIntervalMinutes: Int
        var message: String

        var id: String { key }
    }

    /// 从 Bundle 读取并解码 JSON；失败返回 nil。
    private func load<T: Decodable>(_ fileName: String, as type: T.Type) -> T? {
        guard let url = Bundle.main.url(forResource: fileName, withExtension: "json"),
              let data = try? Data(contentsOf: url) else {
            return nil
        }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    // ---------- 养成规则（growth_rules.json） ----------

    private struct GrowthRulesFile: Codable {
        struct Decay: Codable { var hunger: Double; var mood: Double; var intimacy: Double }
        struct LevelCurve: Codable { var base: Int; var step: Int; var maxLevel: Int }
        struct Clamp: Codable { var min: Double; var max: Double }
        struct Thresholds: Codable { var hungerLow: Int; var moodLow: Int }
        struct TapReaction: Codable { var moodGain: Int; var intimacyGain: Int }
        struct Defaults: Codable {
            var profileId: String
            var name: String
            var level: Int
            var exp: Int
            var hunger: Double
            var mood: Double
            var intimacy: Double
        }
        var decayPerHour: Decay
        var levelCurve: LevelCurve
        var clamp: Clamp
        var thresholds: Thresholds
        var tapReaction: TapReaction
        var defaults: Defaults
    }

    func loadGrowthRules() -> GrowthEngine.GrowthRules {
        guard let file = load("growth_rules", as: GrowthRulesFile.self) else {
            return GrowthEngine.GrowthRules.fallback()
        }
        return GrowthEngine.GrowthRules(
            hungerDecayPerHour: file.decayPerHour.hunger,
            moodDecayPerHour: file.decayPerHour.mood,
            intimacyDecayPerHour: file.decayPerHour.intimacy,
            levelBase: file.levelCurve.base,
            levelStep: file.levelCurve.step,
            maxLevel: file.levelCurve.maxLevel,
            valueMin: file.clamp.min,
            valueMax: file.clamp.max,
            hungerLowThreshold: file.thresholds.hungerLow,
            moodLowThreshold: file.thresholds.moodLow,
            tapMoodGain: file.tapReaction.moodGain,
            tapIntimacyGain: file.tapReaction.intimacyGain,
            defaultName: file.defaults.name
        )
    }

    /// 默认档案（growth_rules.json 的 defaults）。
    func loadDefaultProfile(rules: GrowthEngine.GrowthRules) -> PetProfile {
        guard let file = load("growth_rules", as: GrowthRulesFile.self) else {
            let state = GrowthEngine.GrowthState()
            return PetProfile(
                id: "default_pet", name: rules.defaultName,
                level: state.level, exp: state.exp,
                hunger: state.hunger, mood: state.mood, intimacy: state.intimacy,
                lastActiveAt: Date.now.millisecondsSince1970,
                createdAt: Date.now.millisecondsSince1970
            )
        }
        let now = Date.now.millisecondsSince1970
        return PetProfile(
            id: file.defaults.profileId,
            name: file.defaults.name,
            level: file.defaults.level,
            exp: file.defaults.exp,
            hunger: file.defaults.hunger,
            mood: file.defaults.mood,
            intimacy: file.defaults.intimacy,
            lastActiveAt: now,
            createdAt: now
        )
    }

    // ---------- 食物表（foods.json） ----------

    private struct FoodsFile: Codable { var foods: [GrowthEngine.FoodItem] }

    func loadFoods() -> [GrowthEngine.FoodItem] {
        load("foods", as: FoodsFile.self)?.foods ?? []
    }

    // ---------- 对话语料（dialogue_zh.json） ----------

    func loadDialogueCorpus() -> DialogueEngine.DialogueCorpus {
        load("dialogue_zh", as: DialogueEngine.DialogueCorpus.self)
            ?? DialogueEngine.DialogueCorpus.fallback()
    }

    // ---------- 提醒模板（reminder_templates.json） ----------

    private struct ReminderTemplatesFile: Codable { var templates: [ReminderTemplate] }

    func loadReminderTemplates() -> [ReminderTemplate] {
        load("reminder_templates", as: ReminderTemplatesFile.self)?.templates ?? []
    }
}
