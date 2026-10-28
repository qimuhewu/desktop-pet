import SwiftUI
import WidgetKit

/// 喂食页：食物列表（foods.json）+ 喂食反馈（时序图 5.3 App 侧喂食闭环）。
struct FeedView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @State private var feedbackText: String?
    @State private var leveledUp = false

    var body: some View {
        List {
            if let feedback = feedbackText {
                Section {
                    Text(feedback)
                        .font(.subheadline)
                        .foregroundStyle(leveledUp ? Color.orange : Color.primary)
                }
            }
            Section("选择食物投喂") {
                ForEach(environment.foods) { food in
                    Button {
                        feed(food)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(food.name)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                Text("饥饿+\(food.hungerGain)  心情+\(food.moodGain)  亲密+\(food.intimacyGain)  EXP+\(food.expGain)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "gift.fill")
                                .foregroundStyle(.purple)
                        }
                    }
                }
            }
        }
        .navigationTitle("🍖 喂食")
    }

    /// 喂食事务（decay → feed → levelUp 由仓储内部保证）+ 系统表面刷新。
    private func feed(_ food: GrowthEngine.FoodItem) {
        Task {
            guard let result = await environment.repository.feed(foodId: food.id) else {
                feedbackText = "没有找到这种食物，稍后再试试吧~"
                leveledUp = false
                return
            }
            let reaction = environment.dialogueEngine.pickFeedReaction(
                hungerAfter: Int(result.hungerAfter.rounded()),
                foodName: food.name
            )
            leveledUp = result.leveledUp
            feedbackText = result.leveledUp
                ? "🎉 升级到 Lv.\(result.newLevel)！\(reaction)"
                : reaction

            // 刷新系统表面：PNG 导出 → 灵动岛 → 小组件（时序图 5.3）
            SpriteExporter().exportPoses()
            let profile = await environment.repository.getProfile()
            if #available(iOS 16.1, *) {
                environment.liveActivityController.updateState(
                    profile: profile,
                    message: leveledUp ? "升级啦！谢谢投喂~" : reaction
                )
            }
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}
