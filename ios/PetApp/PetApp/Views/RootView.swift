import SwiftUI
import WidgetKit

/// 根视图 + Tab 导航（架构文档 §3 Views/RootView）。
/// 首次出现时消费灵动岛喂食 pending 动作（时序图 5.3 的 App Intent 回路）。
struct RootView: View {

    @EnvironmentObject private var environment: AppEnvironment

    /// Tab 路由（深链切换用）。
    private enum Tab: Hashable {
        case main, feed, chat, reminder, settings
    }

    @State private var selectedTab: Tab = .main

    var body: some View {
        TabView(selection: $selectedTab) {
            MainView()
                .tabItem { Label("主页", systemImage: "cat.fill") }
                .tag(Tab.main)
            FeedView()
                .tabItem { Label("喂食", systemImage: "fork.knife") }
                .tag(Tab.feed)
            ChatView()
                .tabItem { Label("聊天", systemImage: "bubble.left.and.bubble.right") }
                .tag(Tab.chat)
            ReminderView()
                .tabItem { Label("提醒", systemImage: "alarm") }
                .tag(Tab.reminder)
            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .task {
            await consumePendingActions()
            await restoreReminders()
        }
        .onOpenURL { url in
            // 深链：petapp://reminder / petapp://feed（小组件与通知入口）
            switch url.host {
            case "reminder": selectedTab = .reminder
            case "feed": selectedTab = .feed
            default: break
            }
        }
    }

    /// 消费灵动岛「喂食」快捷按钮写入的 pending 动作（App Group pending_actions.json）。
    private func consumePendingActions() async {
        let url = AppInfo.appGroupContainerURL.appendingPathComponent("pending_actions.json")
        guard let data = try? Data(contentsOf: url),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              dict["type"] as? String == "feed" else {
            return
        }
        // 消费后立即删除，避免重复喂食
        try? FileManager.default.removeItem(at: url)
        if let foodId = dict["food_id"] as? String {
            _ = await environment.repository.feed(foodId: foodId)
            await refreshSystemSurfaces(message: "谢谢灵动岛投喂！")
        }
    }

    /// 恢复全部已启用提醒（本地通知由系统持久化，此处幂等重排）。
    private func restoreReminders() async {
        await environment.reminderScheduler.restoreAll()
    }

    /// 互动后刷新系统级表面：PNG 序列导出 → 灵动岛更新 → 小组件时间线重载（时序图 5.3）。
    func refreshSystemSurfaces(message: String) async {
        let profile = await environment.repository.getProfile()
        SpriteExporter().exportPoses()
        if #available(iOS 16.1, *) {
            environment.liveActivityController.updateState(profile: profile, message: message)
        }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
