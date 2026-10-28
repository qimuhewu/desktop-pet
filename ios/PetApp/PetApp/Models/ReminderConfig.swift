import Foundation
import SwiftData

/// 提醒配置（字段对照 shared/schema/pet_db_schema.sql 的 reminder_config 表，docs/04 §2.2）。
/// enabled 由 SwiftData Boolean / JSON Bool 存储，与 Android Integer(0/1) 等价。
struct ReminderConfig: Codable, Equatable, Identifiable {
    var id: String
    var templateKey: String
    var intervalMinutes: Int
    var message: String
    var enabled: Bool
    var nextTriggerAt: Int64?

    /// 从模板创建（模板来自 reminder_templates.json）。
    static func fromTemplate(
        _ template: AssetsLoader.ReminderTemplate,
        petName: String,
        id: String = UUID().uuidString
    ) -> ReminderConfig {
        ReminderConfig(
            id: id,
            templateKey: template.key,
            intervalMinutes: template.defaultIntervalMinutes,
            message: template.message.replacingOccurrences(of: "{petName}", with: petName),
            enabled: true,
            nextTriggerAt: nil
        )
    }
}

/// SwiftData 提醒配置模型（iOS 17+；iOS 16 由 JSON 文件存储兜底）。
@available(iOS 17.0, *)
@Model
final class ReminderConfigModel {

    @Attribute(.unique) var id: String
    var templateKey: String
    var intervalMinutes: Int
    var message: String
    var enabled: Bool
    var nextTriggerAt: Int64?

    init(config: ReminderConfig) {
        self.id = config.id
        self.templateKey = config.templateKey
        self.intervalMinutes = config.intervalMinutes
        self.message = config.message
        self.enabled = config.enabled
        self.nextTriggerAt = config.nextTriggerAt
    }

    func apply(_ config: ReminderConfig) {
        self.templateKey = config.templateKey
        self.intervalMinutes = config.intervalMinutes
        self.message = config.message
        self.enabled = config.enabled
        self.nextTriggerAt = config.nextTriggerAt
    }

    func toStruct() -> ReminderConfig {
        ReminderConfig(
            id: id,
            templateKey: templateKey,
            intervalMinutes: intervalMinutes,
            message: message,
            enabled: enabled,
            nextTriggerAt: nextTriggerAt
        )
    }
}
