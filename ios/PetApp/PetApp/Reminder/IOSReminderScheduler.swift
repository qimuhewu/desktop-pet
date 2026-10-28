import Foundation
import UserNotifications

/// iOS 提醒调度（类图 IosReminderScheduler；时序图 5.4 iOS 通道）：
/// UNUserNotificationCenter 日历/时间间隔触发，无后台负担（架构文档 D5）。
final class IOSReminderScheduler {

    private let repository: PetRepository

    init(repository: PetRepository) {
        self.repository = repository
    }

    // MARK: - 权限

    /// 申请通知权限（使用提醒功能时按需申请，PRD §6.2）。
    func requestAuthorizationIfNeeded() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        default:
            return false
        }
    }

    // MARK: - ReminderScheduler 对齐接口

    /// 排程一条提醒（重复时间间隔触发；系统按重复规则自动续期，无需手动重排）。
    func schedule(_ config: ReminderConfig) async {
        guard config.enabled else { return }
        let granted = await requestAuthorizationIfNeeded()
        guard granted else { return }

        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [config.id])

        let content = UNMutableNotificationContent()
        content.title = "🐱 \(config.message.isEmpty ? "提醒" : "小希提醒")"
        content.body = config.message
        content.sound = .default

        // UNTimeIntervalNotificationTrigger 重复触发要求间隔 ≥ 60s（1 分钟起步满足）
        let intervalSeconds = max(60, config.intervalMinutes * 60)
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: TimeInterval(intervalSeconds), repeats: true
        )
        let request = UNNotificationRequest(
            identifier: config.id, content: content, trigger: trigger
        )
        do {
            try await center.add(request)
        } catch {
            NSLog("[IOSReminderScheduler] 排程失败: \(error)")
        }
    }

    /// 取消一条提醒。
    func cancel(id: String) {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [id])
    }

    /// 恢复全部已启用提醒（App 启动/配置变更后调用；重复触发型请求由系统持久化，
    /// 此处做幂等重排保证与本地配置一致）。
    func restoreAll() async {
        let enabled = await repository.getEnabledReminders()
        for config in enabled {
            await schedule(config)
        }
    }
}
