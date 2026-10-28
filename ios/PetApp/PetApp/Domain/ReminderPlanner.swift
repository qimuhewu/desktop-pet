import Foundation

/// 提醒计划纯逻辑（与 Android domain/ReminderPlanner.kt 镜像实现）。
///
/// 算法唯一事实源：docs/04-数据模型与养成规则规范.md §5。
/// ReminderConfig 定义见 Models/ReminderConfig.swift。
enum ReminderPlanner {

    /// 间隔下限保护：0 或负间隔视为无效，返回 0（不排程）。
    private static let minIntervalMinutes = 1

    /// 计算下一次触发时间（Unix 毫秒时间戳）。
    ///
    /// - 未启用 → 0（无效）；
    /// - nextTriggerAt 为 nil（从未排程）→ from + interval；
    /// - nextTriggerAt 在未来 → 保持原值（RP-02）；
    /// - nextTriggerAt 已过期 → 顺延到未来第一个整周期（RP-03）。
    static func nextTriggerTime(_ config: ReminderConfig, fromMillis: Int64) -> Int64 {
        guard config.enabled, config.intervalMinutes >= minIntervalMinutes else { return 0 }
        let intervalMs = Int64(config.intervalMinutes) * 60_000
        var next = config.nextTriggerAt ?? (fromMillis + intervalMs)
        while next <= fromMillis {
            next += intervalMs
        }
        return next
    }
}
