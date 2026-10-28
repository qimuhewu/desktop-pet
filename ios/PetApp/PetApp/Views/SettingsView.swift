import SwiftUI
import WidgetKit
import ActivityKit

/// 设置页（iOS 侧）：灵动岛/小组件控制、提醒入口、关于与 Cubism 版权声明（共享知识 #11）。
struct SettingsView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @State private var liveActivityEnabled = false

    var body: some View {
        List {
            // ---------- 形象与渲染 ----------
            Section("形象") {
                modelStatusRow
                Button {
                    Task {
                        // 重新检测模型（用户放置实包/导入后可点此刷新）
                        SpriteExporter().exportPoses()
                        WidgetCenter.shared.reloadAllTimelines()
                    }
                } label: {
                    Label("刷新小组件姿态图（PNG 序列导出）", systemImage: "arrow.clockwise")
                }
            }

            // ---------- 灵动岛（iOS 16.1+） ----------
            Section("灵动岛") {
                if #available(iOS 16.1, *) {
                    Toggle("常驻展示宠物状态", isOn: $liveActivityEnabled)
                        .onChange(of: liveActivityEnabled) { enabled in
                            Task { await toggleLiveActivity(enabled) }
                        }
                    Text("灵动岛仅 iPhone 14 Pro 及之后机型支持；不支持的机型自动降级为仅小组件（PRD §7.2 第 7 项）。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Text("需要 iOS 16.1 及以上系统。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            // ---------- 提醒 ----------
            Section("提醒") {
                NavigationLink("提醒管理") {
                    ReminderView()
                }
            }

            // ---------- 关于与版权声明 ----------
            Section("关于与许可声明") {
                Text(licenseText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("桌宠 v1.0.0 · 第一版全本地运行，不收集任何用户数据。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("⚙ 设置")
        .task {
            // areActivitiesEnabled 反映系统设置中用户对实时活动的总开关（isSupported 是 ActivityKit 静态成员，不能经协议实例调用）
            liveActivityEnabled = ActivityAuthorizationInfo().areActivitiesEnabled &&
                environment.liveActivityController.hasActiveActivity
        }
    }

    // ---------- 模型状态 ----------

    private var modelStatusRow: some View {
        let hasModel = ModelAssetManager().resolveActiveModelDir() != nil
        return HStack {
            Image(systemName: hasModel ? "photo.fill" : "photo")
            VStack(alignment: .leading, spacing: 2) {
                Text(hasModel ? "Live2D 模型：已就绪" : "Live2D 模型：未放置")
                    .font(.subheadline)
                Text(hasModel
                     ? "渲染使用 Cubism 实时渲染。"
                     : "当前使用程序绘制兜底形象；放置 Cubism SDK 与模型后自动启用 Live2D（见仓库 docs/03 接入指南）。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // ---------- 灵动岛开关 ----------

    private func toggleLiveActivity(_ enabled: Bool) async {
        guard #available(iOS 16.1, *) else { return }
        if enabled {
            let profile = await environment.repository.getProfile()
            environment.liveActivityController.startActivity(profile: profile)
        } else {
            environment.liveActivityController.stopActivity()
        }
    }

    // ---------- 版权声明（docs/03 §6 文案） ----------

    private var licenseText: String {
        "本应用使用的 Live2D Cubism SDK 与示例模型「Hiyori」版权归 Live2D Inc. 所有。\n" +
        "示例模型的使用遵循 Live2D 免费素材许可证（Free Material License Agreement），仅限非商业用途。" +
        "如需商业使用，请替换为已获授权的模型资产。\n" +
        "Live2D 及 Cubism 为 Live2D Inc. 的商标。"
    }
}
