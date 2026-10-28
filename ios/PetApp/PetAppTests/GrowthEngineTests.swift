import XCTest
@testable import PetApp

/// 成长引擎镜像单测（用例编号见 tests/README.md，与 Android GrowthEngineTest.kt 一一对应）。
/// 数值常量与 shared/assets/growth_rules.json v1 一致（来源标注）。
final class GrowthEngineTests: XCTestCase {

    private let rules = GrowthEngine.GrowthRules.fallback() // 与 growth_rules.json v1 同值

    // 食物（与 foods.json 同值）
    private let driedFish = GrowthEngine.FoodItem(
        id: "dried_fish", name: "小鱼干", hungerGain: 15, moodGain: 8, intimacyGain: 8, expGain: 15, iconKey: "dried_fish")
    private let bento = GrowthEngine.FoodItem(
        id: "bento", name: "便当", hungerGain: 40, moodGain: 5, intimacyGain: 2, expGain: 10, iconKey: "bento")
    private let cake = GrowthEngine.FoodItem(
        id: "cake", name: "蛋糕", hungerGain: 20, moodGain: 15, intimacyGain: 5, expGain: 12, iconKey: "cake")

    /// GC-01 喂食小鱼干：hunger 80→95，mood 60→68，intimacy 50→58，exp 0→15。
    func testGC01FeedDriedFish() {
        let state = GrowthEngine.GrowthState(level: 1, exp: 0, hunger: 80, mood: 60, intimacy: 50)
        let result = GrowthEngine.checkLevelUp(GrowthEngine.applyFeed(state, driedFish, rules), rules)
        XCTAssertEqual(result.hunger, 95.0, accuracy: 1e-9)
        XCTAssertEqual(result.mood, 68.0, accuracy: 1e-9)
        XCTAssertEqual(result.intimacy, 58.0, accuracy: 1e-9)
        XCTAssertEqual(result.exp, 15)
        XCTAssertEqual(result.level, 1)
    }

    /// GC-02 喂食溢出封顶：hunger 90 + 便当(+40) → 100。
    func testGC02FeedClampOverflow() {
        let state = GrowthEngine.GrowthState(level: 1, exp: 0, hunger: 90, mood: 60, intimacy: 50)
        let result = GrowthEngine.applyFeed(state, bento, rules)
        XCTAssertEqual(result.hunger, 100.0, accuracy: 1e-9)
    }

    /// GC-03 闲置 24h 衰减：hunger 80-48=32，mood 60-36=24，intimacy 50-12=38。
    func testGC03Decay24h() {
        let now: Int64 = 1_000_000_000_000
        let state = GrowthEngine.GrowthState(level: 1, exp: 0, hunger: 80, mood: 60, intimacy: 50)
        let result = GrowthEngine.applyElapsedDecay(
            state, lastActiveAt: now - 24 * 3_600_000, now: now, rules: rules)
        XCTAssertEqual(result.hunger, 32.0, accuracy: 1e-9)
        XCTAssertEqual(result.mood, 24.0, accuracy: 1e-9)
        XCTAssertEqual(result.intimacy, 38.0, accuracy: 1e-9)
    }

    /// GC-04 闲置 24h 后喂食蛋糕：先衰减再增益（hunger 32+20=52）。
    func testGC04DecayThenFeed() {
        let now: Int64 = 1_000_000_000_000
        let state = GrowthEngine.GrowthState(level: 1, exp: 0, hunger: 80, mood: 60, intimacy: 50)
        let decayed = GrowthEngine.applyElapsedDecay(
            state, lastActiveAt: now - 24 * 3_600_000, now: now, rules: rules)
        let fed = GrowthEngine.applyFeed(decayed, cake, rules)
        XCTAssertEqual(fed.hunger, 52.0, accuracy: 1e-9)
    }

    /// GC-05 升级：exp 95 + 小鱼干(+15)=110 ≥ 100 → level 2，exp 10。
    func testGC05LevelUp() {
        let state = GrowthEngine.GrowthState(level: 1, exp: 95, hunger: 80, mood: 60, intimacy: 50)
        let fed = GrowthEngine.applyFeed(state, driedFish, rules)
        let leveled = GrowthEngine.checkLevelUp(fed, rules)
        XCTAssertEqual(leveled.level, 2)
        XCTAssertEqual(leveled.exp, 10)
    }

    /// GC-06 连升验证（按 foods.json：便当 expGain=10；QA 严过关修正：原断言误按每次 +30 计算）。
    func testGC06MultiLevelUp() {
        var state = GrowthEngine.GrowthState(level: 1, exp: 0, hunger: 80, mood: 60, intimacy: 50)
        for _ in 0..<3 {
            state = GrowthEngine.checkLevelUp(GrowthEngine.applyFeed(state, bento, rules), rules)
        }
        XCTAssertEqual(state.level, 1)
        XCTAssertEqual(state.exp, 30)
        // 连升两级：注入 exp=250（从 Lv1）：250≥100 → Lv2(150)；150≥expToNext(2)=150 → Lv3(0)
        let doubleUp = GrowthEngine.checkLevelUp(state.copy(exp: 250), rules)
        XCTAssertEqual(doubleUp.level, 3)
        XCTAssertEqual(doubleUp.exp, 0)
        // 连升三级未满：注入 exp=600（从 Lv1）：600≥100 → Lv2(500)；500≥150 → Lv3(350)；
        // 350≥200 → Lv4(150)；150 < expToNext(4)=250 停止 → Lv4/exp150
        let jump = GrowthEngine.checkLevelUp(state.copy(exp: 600), rules)
        XCTAssertEqual(jump.level, 4)
        XCTAssertEqual(jump.exp, 150)
    }

    /// GC-07 满级 99：等级不再增长，经验封顶为 expToNextLevel(99)-1。
    func testGC07MaxLevelCap() {
        let state = GrowthEngine.GrowthState(level: 99, exp: 0, hunger: 80, mood: 60, intimacy: 50)
        let fed = GrowthEngine.applyFeed(state, bento, rules)
        let capped = GrowthEngine.checkLevelUp(fed, rules)
        XCTAssertEqual(capped.level, 99)
        // exp 10 < 4999（expToNextLevel(99)-1），未触及封顶
        XCTAssertEqual(capped.exp, 10)
        // 注入超上限经验验证封顶
        let overCap = GrowthEngine.checkLevelUp(state.copy(exp: 9999), rules)
        XCTAssertEqual(overCap.level, 99)
        XCTAssertEqual(overCap.exp, 4999)
    }

    /// 附加：升级曲线公式 expToNextLevel(L) = 100 + (L-1)*50。
    func testExpCurveFormula() {
        XCTAssertEqual(GrowthEngine.expToNextLevel(1, rules), 100)
        XCTAssertEqual(GrowthEngine.expToNextLevel(2, rules), 150)
        XCTAssertEqual(GrowthEngine.expToNextLevel(99, rules), 5000)
    }
}

/// 测试辅助：copy(with:)（Swift 无 Kotlin data class copy，这里手动实现等价工具）。
extension GrowthEngine.GrowthState {
    func copy(level: Int? = nil, exp: Int? = nil, hunger: Double? = nil,
              mood: Double? = nil, intimacy: Double? = nil) -> GrowthEngine.GrowthState {
        var s = self
        if let level = level { s.level = level }
        if let exp = exp { s.exp = exp }
        if let hunger = hunger { s.hunger = hunger }
        if let mood = mood { s.mood = mood }
        if let intimacy = intimacy { s.intimacy = intimacy }
        return s
    }
}
