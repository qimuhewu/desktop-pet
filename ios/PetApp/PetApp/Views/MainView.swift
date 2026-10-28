import SwiftUI

/// 主界面：宠物舞台 + 状态条（PRD §5.4；时序图 5.3 的 App 侧）。
struct MainView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @State private var profile: PetProfile?
    @State private var bubbleText: String = ""
    @State private var renderer: Live2DRenderer? = nil
    @State private var useLive2D = false

    var body: some View {
        VStack(spacing: 12) {
            // 顶栏：名字/等级
            HStack {
                Text("🐱 \(profile?.name ?? "小希")  Lv.\(profile?.level ?? 1)")
                    .font(.headline)
                Spacer()
                NavigationLink("设置") {
                    SettingsView()
                }
                .font(.subheadline)
            }
            .padding(.horizontal)

            // 宠物舞台：有模型 → Live2D（Metal 桥接）；无模型 → 程序绘制兜底
            ZStack {
                if useLive2D, let renderer = renderer {
                    Live2DContainerView(renderer: renderer, renderMode: .live2d)
                } else {
                    FallbackPetView(pose: currentPose)
                }
                if !bubbleText.isEmpty {
                    Text(bubbleText)
                        .font(.subheadline)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule())
                        .transition(.opacity)
                        .offset(y: -90)
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .onTapGesture { tapPet() }

            // 状态条：饥饿/心情/亲密度（数值展示取整）
            statusRow(title: "饥饿", value: Int(profile?.hunger.rounded() ?? 80), color: .orange)
            statusRow(title: "心情", value: Int(profile?.mood.rounded() ?? 60), color: .pink)
            statusRow(title: "亲密", value: Int(profile?.intimacy.rounded() ?? 50), color: .purple)
        }
        .padding(.vertical, 8)
        .task {
            await reloadProfile()
            setupRenderer()
        }
    }

    private var currentPose: PetMotion {
        if let hunger = profile?.hunger, hunger < 30 { return .hungry }
        return .idle
    }

    // MARK: - 互动（共享知识 #9：互动刷新 lastActiveAt + 系统表面刷新）

    private func tapPet() {
        let reaction = environment.dialogueEngine.pickTapReaction()
        withAnimation(.easeInOut(duration: 0.2)) {
            bubbleText = reaction.text
        }
        Task {
            _ = await environment.repository.recordInteraction()
            await refreshSystemSurfaces(message: reaction.text)
            await reloadProfile()
        }
        // 3 秒后收起气泡
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            withAnimation { bubbleText = "" }
        }
    }

    /// 互动后刷新系统级表面：PNG 序列导出 → 灵动岛更新 → 小组件时间线重载（时序图 5.3）。
    private func refreshSystemSurfaces(message: String) async {
        SpriteExporter().exportPoses()
        if let current = profile, #available(iOS 16.1, *) {
            environment.liveActivityController.updateState(profile: current, message: message)
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func reloadProfile() async {
        profile = await environment.repository.getProfile()
    }

    private func setupRenderer() {
        // 尝试 Live2D 路径；loadModel 失败自动兜底（业务无感知，不崩溃）
        let live2d = Live2DRenderer()
        if let dir = ModelAssetManager().resolveActiveModelDir(),
           live2d.loadModel(path: dir.path) {
            renderer = live2d
            useLive2D = true
        }
    }

    // MARK: - 状态条

    private func statusRow(title: String, value: Int, color: Color) -> some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.subheadline)
                .frame(width: 36, alignment: .leading)
            ProgressView(value: Double(value), total: 100)
                .tint(color)
            Text("\(value)")
                .font(.subheadline.monospacedDigit())
                .frame(width: 34, alignment: .trailing)
        }
        .padding(.horizontal)
    }
}
