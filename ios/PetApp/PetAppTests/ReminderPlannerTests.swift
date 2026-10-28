import XCTest
@testable import PetApp

/// 提醒计划镜像单测（用例编号见 tests/README.md，与 Android ReminderPlannerTest.kt 一一对应）。
final class ReminderPlannerTests: XCTestCase {

    private var config: ReminderConfig {
        ReminderConfig(
            id: "r1",
            templateKey: "drink_water",
            intervalMinutes: 60,
            message: "该喝水啦~",
            enabled: true,
            nextTriggerAt: nil
        )
    }

    /// RP-01 新配置（nextTriggerAt=nil）：next = from + interval。
    func testRP01NewConfig() {
        let from: Int64 = 1_000_000_000_000
        let next = ReminderPlanner.nextTriggerTime(config, fromMillis: from)
        XCTAssertEqual(next, from + 60 * 60_000)
    }

    /// RP-02 已排程且在未来：保持原 nextTriggerAt。
    func testRP02FutureScheduledKept() {
        let from: Int64 = 1_000_000_000_000
        let future = from + 30 * 60_000
        let next = ReminderPlanner.nextTriggerTime(config.copy(nextTriggerAt: future), fromMillis: from)
        XCTAssertEqual(next, future)
    }

    /// RP-03 已过期：顺延到未来第一个整周期。
    func testRP03ExpiredAdvanced() {
        let from: Int64 = 1_000_000_000_000
        let intervalMs: Int64 = 60 * 60_000
        // 顺延 3 个整周期到 from + 0.5h（QA 严过关修正：原断言误写 from + intervalMs）
        let expired = from - intervalMs * 2 - 30 * 60_000
        let next = ReminderPlanner.nextTriggerTime(config.copy(nextTriggerAt: expired), fromMillis: from)
        XCTAssertEqual(next, from + 30 * 60_000)
    }

    /// RP-04 enabled=false → 0（无效）。
    func testRP04DisabledReturnsZero() {
        let next = ReminderPlanner.nextTriggerTime(config.copy(enabled: false), fromMillis: 1_000_000_000_000)
        XCTAssertEqual(next, 0)
    }

    /// 附加：间隔下限保护（0 分钟视为无效）。
    func testZeroIntervalInvalid() {
        let next = ReminderPlanner.nextTriggerTime(config.copy(intervalMinutes: 0), fromMillis: 1_000_000_000_000)
        XCTAssertEqual(next, 0)
    }
}

/// 测试辅助：copy(with:)（与 Android data class copy 等价）。
extension ReminderConfig {
    func copy(id: String? = nil, templateKey: String? = nil, intervalMinutes: Int? = nil,
              message: String? = nil, enabled: Bool? = nil,
              nextTriggerAt: Int64?? = nil) -> ReminderConfig {
        var c = self
        if let id = id { c.id = id }
        if let templateKey = templateKey { c.templateKey = templateKey }
        if let intervalMinutes = intervalMinutes { c.intervalMinutes = intervalMinutes }
        if let message = message { c.message = message }
        if let enabled = enabled { c.enabled = enabled }
        if let nextTriggerAt = nextTriggerAt { c.nextTriggerAt = nextTriggerAt }
        return c
    }
}
