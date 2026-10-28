import Foundation
import SwiftData

/// 宠物档案（字段对照 shared/schema/pet_db_schema.sql 的 pet_profile 表，docs/04 §2.1）。
/// 时间字段为 Unix 毫秒时间戳；hunger/mood/intimacy 用 Double 连续结算。
struct PetProfile: Codable, Equatable {
    var id: String
    var name: String
    var level: Int
    var exp: Int
    var hunger: Double
    var mood: Double
    var intimacy: Double
    var lastActiveAt: Int64
    var createdAt: Int64

    /// 展示用快照摘要（灵动岛/小组件/通知使用，数值取整）。
    struct Snapshot: Codable, Equatable {
        var name: String
        var level: Int
        var hunger: Int
        var mood: Int
        var intimacy: Int
    }

    func snapshot() -> Snapshot {
        Snapshot(
            name: name,
            level: level,
            hunger: Int(hunger.rounded()),
            mood: Int(mood.rounded()),
            intimacy: Int(intimacy.rounded())
        )
    }
}

/// SwiftData 宠物档案模型（iOS 17+；iOS 16 由 PetRepository 内的 JSON 文件存储兜底）。
@available(iOS 17.0, *)
@Model
final class PetProfileModel {

    @Attribute(.unique) var id: String
    var name: String
    var level: Int
    var exp: Int
    var hunger: Double
    var mood: Double
    var intimacy: Double
    var lastActiveAt: Int64
    var createdAt: Int64

    init(profile: PetProfile) {
        self.id = profile.id
        self.name = profile.name
        self.level = profile.level
        self.exp = profile.exp
        self.hunger = profile.hunger
        self.mood = profile.mood
        self.intimacy = profile.intimacy
        self.lastActiveAt = profile.lastActiveAt
        self.createdAt = profile.createdAt
    }

    func apply(_ profile: PetProfile) {
        self.name = profile.name
        self.level = profile.level
        self.exp = profile.exp
        self.hunger = profile.hunger
        self.mood = profile.mood
        self.intimacy = profile.intimacy
        self.lastActiveAt = profile.lastActiveAt
        self.createdAt = profile.createdAt
    }

    func toStruct() -> PetProfile {
        PetProfile(
            id: id, name: name, level: level, exp: exp,
            hunger: hunger, mood: mood, intimacy: intimacy,
            lastActiveAt: lastActiveAt, createdAt: createdAt
        )
    }
}

/// Unix 毫秒时间戳工具（双端时间约定 docs/04 §1.1）。
extension Date {
    var millisecondsSince1970: Int64 {
        Int64((timeIntervalSince1970 * 1000.0).rounded())
    }

    static func fromMilliseconds(_ ms: Int64) -> Date {
        Date(timeIntervalSince1970: Double(ms) / 1000.0)
    }
}
