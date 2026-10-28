import Foundation

/// 成长引擎（纯逻辑，与 Android domain/GrowthEngine.kt 镜像实现）。
///
/// 算法唯一事实源：docs/04-数据模型与养成规则规范.md §3。
/// 规则常量唯一来源：shared/assets/growth_rules.json（代码禁止硬编码，
/// `GrowthRules.fallback` 仅为资产缺失时的应急兜底，数值与 JSON 一致）。
enum GrowthEngine {

    /// 宠物可成长状态（hunger/mood/intimacy 用 Double 连续结算，展示时取整，见 docs/04 §1.5）。
    struct GrowthState: Codable, Equatable {
        var level: Int = 1
        var exp: Int = 0
        var hunger: Double = 80.0
        var mood: Double = 60.0
        var intimacy: Double = 50.0
    }

    /// 养成规则常量（对应 growth_rules.json）。
    struct GrowthRules: Codable {
        var hungerDecayPerHour: Double = -2.0
        var moodDecayPerHour: Double = -1.5
        var intimacyDecayPerHour: Double = -0.5
        var levelBase: Int = 100
        var levelStep: Int = 50
        var maxLevel: Int = 99
        var valueMin: Double = 0.0
        var valueMax: Double = 100.0
        var hungerLowThreshold: Int = 30
        var moodLowThreshold: Int = 30
        var tapMoodGain: Int = 1
        var tapIntimacyGain: Int = 1
        var defaultName: String = "小希"

        /// 资产缺失时的应急兜底（数值与 growth_rules.json v1 一致）。
        static func fallback() -> GrowthRules { GrowthRules() }
    }

    /// 食物条目（对应 foods.json 的 foods[]）。
    struct FoodItem: Codable, Equatable, Identifiable {
        var id: String
        var name: String
        var hungerGain: Int
        var moodGain: Int
        var intimacyGain: Int
        var expGain: Int
        var iconKey: String
    }

    /// 喂食结果：数值前后变化 + 是否升级。
    struct FeedResult: Equatable {
        var foodId: String
        var hungerBefore: Double
        var hungerAfter: Double
        var moodBefore: Double
        var moodAfter: Double
        var intimacyBefore: Double
        var intimacyAfter: Double
        var expGain: Int
        var leveledUp: Bool
        var newLevel: Int
    }

    /// 数值封顶。
    private static func clamp(_ value: Double, _ rules: GrowthRules) -> Double {
        min(rules.valueMax, max(rules.valueMin, value))
    }

    /// 升级曲线：expToNextLevel(L) = levelBase + (L-1) * levelStep。
    static func expToNextLevel(_ level: Int, _ rules: GrowthRules) -> Int {
        rules.levelBase + (level - 1) * rules.levelStep
    }

    /// 惰性衰减：按 lastActiveAt 到 now 的流逝小时数连续结算（毫秒时间戳）。
    /// 不修改 lastActiveAt（由调用方在用户互动后刷新）。
    static func applyElapsedDecay(
        _ state: GrowthState,
        lastActiveAt: Int64,
        now: Int64,
        rules: GrowthRules
    ) -> GrowthState {
        var s = state
        let elapsedHours = Double(max(0, now - lastActiveAt)) / 3_600_000.0
        s.hunger = clamp(s.hunger + rules.hungerDecayPerHour * elapsedHours, rules)
        s.mood = clamp(s.mood + rules.moodDecayPerHour * elapsedHours, rules)
        s.intimacy = clamp(s.intimacy + rules.intimacyDecayPerHour * elapsedHours, rules)
        return s
    }

    /// 喂食增益（不含升级判定，调用方紧接着调用 checkLevelUp）。
    static func applyFeed(_ state: GrowthState, _ food: FoodItem, _ rules: GrowthRules) -> GrowthState {
        var s = state
        s.hunger = clamp(s.hunger + Double(food.hungerGain), rules)
        s.mood = clamp(s.mood + Double(food.moodGain), rules)
        s.intimacy = clamp(s.intimacy + Double(food.intimacyGain), rules)
        s.exp += food.expGain
        return s
    }

    /// 升级判定：可连升多级；满级后经验封顶为 expToNextLevel(maxLevel) - 1。
    static func checkLevelUp(_ state: GrowthState, _ rules: GrowthRules) -> GrowthState {
        var s = state
        while s.level < rules.maxLevel && s.exp >= expToNextLevel(s.level, rules) {
            s.exp -= expToNextLevel(s.level, rules)
            s.level += 1
        }
        if s.level >= rules.maxLevel {
            s.exp = min(s.exp, expToNextLevel(rules.maxLevel, rules) - 1)
        }
        return s
    }

    /// 点按互动增益（tapReaction.moodGain / tapIntimacyGain，不产经验）。
    static func applyTapReaction(_ state: GrowthState, _ rules: GrowthRules) -> GrowthState {
        var s = state
        s.mood = clamp(s.mood + Double(rules.tapMoodGain), rules)
        s.intimacy = clamp(s.intimacy + Double(rules.tapIntimacyGain), rules)
        return s
    }
}
