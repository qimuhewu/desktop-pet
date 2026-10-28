import SwiftUI
import ActivityKit
import WidgetKit
import AppIntents

/// 灵动岛 Live Activity UI（PetLiveActivity 扩展 target）：
/// 紧凑态（头像+状态） / 展开态（心情文案 + 喂食快捷按钮）（PRD §5.2，P1-4）。
struct PetLiveActivityUI: Widget {

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PetActivityAttributes.self) { context in
            // 锁屏 / 灵动岛展开态
            ExpandedView(state: context.state)
                .activityBackgroundTint(Color.black.opacity(0.6))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                // 展开态
                DynamicIslandExpandedRegion(.leading) {
                    Text("🐱")
                        .font(.title)
                }
                DynamicIslandExpandedRegion(.center) {
                    ExpandedView(state: context.state)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    // 喂食快捷按钮（AppIntent）：extension 环境要求 iOS 17.0+，
                    // iOS 16.x 降级为静态图标（无交互，不影响其他功能）
                    if #available(iOSApplicationExtension 17.0, *) {
                        Button(intent: FeedAppIntent()) {
                            Text("🍖")
                                .font(.title2)
                        }
                    } else {
                        Text("🍖")
                            .font(.title2)
                    }
                }
            } compactLeading: {
                // 紧凑态左侧：宠物迷你头像
                Text("🐱")
                    .font(.caption)
            } compactTrailing: {
                // 紧凑态右侧：Lv.♥78 式状态摘要
                CompactStateView(state: context.state)
            } minimal: {
                Text("🐱")
                    .font(.caption)
            }
        }
    }
}

/// 紧凑态右侧状态摘要（Lv.♥78）。
private struct CompactStateView: View {
    let state: PetActivityAttributes.ContentState

    var body: some View {
        Text("Lv.\(state.level) ♥\(state.intimacy)")
            .font(.caption2.bold())
            .foregroundStyle(.white)
    }
}

/// 展开态：心情文案 + 状态摘要。
private struct ExpandedView: View {
    let state: PetActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(state.petName) Lv.\(state.level)")
                .font(.headline)
                .foregroundStyle(.white)
            Text(state.message)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.85))
            Text("♥\(state.intimacy)  🍖\(state.hunger)  心情\(state.mood)")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.75))
        }
    }
}

// MARK: - 喂食快捷 AppIntent（时序图 5.3：灵动岛点喂食按钮）

/// 喂食快捷意图：写 pending 动作到 App Group → 深链打开 App 应用。
struct FeedAppIntent: AppIntent {

    static var title: LocalizedStringResource = "喂食"
    static var description = IntentDescription("给小希喂一份小鱼干")
    static var openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        // 扩展进程不能操作主 App 数据库：写入待处理动作文件，主 App 前台时消费
        let createdAt = Int64(Date().timeIntervalSince1970 * 1000)
        let action: [String: Any] = [
            "type": "feed",
            "food_id": "dried_fish",
            "created_at": createdAt
        ]
        if let data = try? JSONSerialization.data(withJSONObject: action, options: [.sortedKeys]) {
            try? data.write(
                to: AppInfo.appGroupContainerURL.appendingPathComponent("pending_actions.json"),
                options: .atomic
            )
        }
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
