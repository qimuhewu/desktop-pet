import Foundation
import WidgetKit

/// 小组件时间线数据模型与 Provider（类图 PetWidgetProvider）。
///
/// 数据通道（docs/04 §7 / D3 决策）：
/// - App Group `state.json`：宠物展示快照（主 App 写入）；
/// - App Group `sprites/`：PNG 姿态序列 + `manifest.json`（SpriteExporter 导出）；
/// - 无文件时使用程序绘制兜底姿态（Widget 进程禁止任何 Cubism 调用）。
struct WidgetPetEntry: TimelineEntry {
    let date: Date
    let name: String
    let level: Int
    let hunger: Int
    let mood: Int
    let intimacy: Int
    let message: String
    /// 当前姿态文件名（App Group 内，nil = 走程序绘制兜底）。
    let spriteFileName: String?
}

/// 共享状态读取器（App Group，只读）。
struct SharedPetStateReader {

    /// 展示快照（state.json）。
    struct Snapshot {
        var name: String = "小希"
        var level: Int = 1
        var hunger: Int = 80
        var mood: Int = 60
        var intimacy: Int = 50
        var message: String = "今天也想多陪陪你~"
    }

    /// 读取展示快照；缺失时返回默认值（不崩溃）。
    static func readSnapshot() -> Snapshot {
        let url = AppInfo.appGroupContainerURL.appendingPathComponent("state.json")
        guard let data = try? Data(contentsOf: url),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return Snapshot()
        }
        var snapshot = Snapshot()
        if let name = dict["name"] as? String { snapshot.name = name }
        if let level = dict["level"] as? Int { snapshot.level = level }
        if let hunger = dict["hunger"] as? Int { snapshot.hunger = hunger }
        if let mood = dict["mood"] as? Int { snapshot.mood = mood }
        if let intimacy = dict["intimacy"] as? Int { snapshot.intimacy = intimacy }
        if let message = dict["message"] as? String { snapshot.message = message }
        return snapshot
    }

    /// 读取姿态序列（manifest.json → pet_<pose>_<index>.png 文件名列表）。
    static func readSpriteFiles(pose: String) -> [String] {
        let url = AppInfo.spritesDirectoryURL.appendingPathComponent("manifest.json")
        guard let data = try? Data(contentsOf: url),
              let manifest = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let poses = manifest["poses"] as? [String: [String]],
              let files = poses[pose] else {
            return []
        }
        return files
    }

    /// 读取姿态 PNG 数据。
    static func readSpriteData(fileName: String) -> Data? {
        try? Data(contentsOf: AppInfo.spritesDirectoryURL.appendingPathComponent(fileName))
    }
}

/// 时间线 Provider：按时间驱动姿态切换（每 30 分钟一帧，受系统调度约束，R4 预期管理）。
struct WidgetTimelineProvider: TimelineProvider {

    private static let poseCycle = ["idle", "happy", "idle", "hungry"]

    func placeholder(in context: Context) -> WidgetPetEntry {
        WidgetPetEntry(
            date: Date(), name: "小希", level: 1,
            hunger: 80, mood: 60, intimacy: 50,
            message: "今天也想多陪陪你~", spriteFileName: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (WidgetPetEntry) -> Void) {
        completion(makeEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WidgetPetEntry>) -> Void) {
        var entries: [WidgetPetEntry] = []
        let now = Date()
        // 未来 4 小时：每 30 分钟一条，按姿态循环切换（时序图 5.3 时间驱动姿态变化）
        for index in 0..<8 {
            let date = now.addingTimeInterval(Double(index) * 30 * 60)
            entries.append(makeEntry(date: date, cycleIndex: index))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    /// 组装单条时间线数据：读快照 + 按姿态取 sprite（无 sprite 则走程序绘制兜底）。
    private func makeEntry(date: Date, cycleIndex: Int = 0) -> WidgetPetEntry {
        let snapshot = SharedPetStateReader.readSnapshot()
        let pose = Self.poseCycle[cycleIndex % Self.poseCycle.count]
        let files = SharedPetStateReader.readSpriteFiles(pose: pose)
        // 姿态序列内按时间片轮换帧
        let fileName = files.isEmpty ? nil : files[cycleIndex % files.count]
        return WidgetPetEntry(
            date: date,
            name: snapshot.name,
            level: snapshot.level,
            hunger: snapshot.hunger,
            mood: snapshot.mood,
            intimacy: snapshot.intimacy,
            message: snapshot.message,
            spriteFileName: fileName
        )
    }
}
