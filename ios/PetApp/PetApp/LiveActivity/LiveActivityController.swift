import Foundation
import ActivityKit

// MARK: - 可用性门面（非门控协议，iOS 16.0 即可引用）

/// 灵动岛控制接口（非门控）：让 AppEnvironment / View 层无须感知 iOS 16.1 可用性。
/// iOS < 16.1 时由 [DisabledLiveActivityController] 提供空实现（自动降级为仅小组件，PRD §7.2 #7）。
protocol LiveActivityControlling {
    /// 机型/系统是否支持灵动岛。
    static var isSupported: Bool { get }
    func startActivity(profile: PetProfile)
    @discardableResult
    func updateState(profile: PetProfile, message: String?) -> Bool
    func stopActivity()
    var hasActiveActivity: Bool { get }
}

/// 空实现（iOS < 16.1 / 不支持机型）。
final class DisabledLiveActivityController: LiveActivityControlling {
    static var isSupported: Bool { false }
    func startActivity(profile: PetProfile) {}
    func updateState(profile: PetProfile, message: String?) -> Bool { false }
    func stopActivity() {}
    var hasActiveActivity: Bool { false }
}

/// 工厂：按系统版本选择实现。
enum LiveActivityControllerFactory {
    static func create() -> LiveActivityControlling {
        if #available(iOS 16.1, *) {
            return LiveActivityController()
        }
        return DisabledLiveActivityController()
    }
}

// MARK: - 真实实现（ActivityKit，iOS 16.1+）

/// 灵动岛活动控制器：启动/更新/结束 Live Activity（iOS 16.1+，类图 LiveActivityController）。
@available(iOS 16.1, *)
final class LiveActivityController: LiveActivityControlling {

    /// 是否支持灵动岛（无 ActivityKit 权限/机型时调用方降级为仅小组件，PRD §7.2 第 7 项）。
    static var isSupported: Bool {
        ActivityAuthorizationInfo().areActivitiesEnabled
    }

    /// 当前活动 id 集合（单活动策略）。
    private var activityId: String?

    /// 启动灵动岛活动（时序图 5.3：App 内状态变化后由 App 驱动更新）。
    /// 去重策略：同步遍历 Activity.activities 判空——有活动则走更新分支，无活动才 request 新建，
    /// 保证不会重复创建灵动岛活动（QA 第 1 轮修复项 1）。
    func startActivity(profile: PetProfile) {
        guard Self.isSupported else { return }
        // 同步判重：已有运行中的活动则只更新状态，不再新建
        if !Activity<PetActivityAttributes>.activities.isEmpty {
            updateState(profile: profile)
            return
        }
        do {
            let attributes = PetActivityAttributes()
            let initialState = PetActivityAttributes.ContentState(
                petName: profile.name,
                level: profile.level,
                mood: Int(profile.mood.rounded()),
                intimacy: Int(profile.intimacy.rounded()),
                hunger: Int(profile.hunger.rounded()),
                message: "心情不错，来陪我玩吧~"
            )
            let content = ActivityContent(state: initialState, staleDate: nil)
            let activity = try Activity<PetActivityAttributes>.request(
                attributes: attributes,
                content: content,
                pushType: nil // 本地更新，不用推送
            )
            activityId = activity.id
        } catch {
            NSLog("[LiveActivityController] 启动灵动岛活动失败: \(error)")
        }
    }

    /// 更新状态（喂食/点按等互动后调用；同时把快照写入 App Group 供小组件读取）。
    ///
    /// 返回值语义（QA 第 1 轮修复项 1）：以**同步**判定的「是否存在运行中活动」为准，
    /// 不依赖异步 Task 内的回写标志；Task 内仅执行实际的 update 调用。
    @discardableResult
    func updateState(profile: PetProfile, message: String? = nil) -> Bool {
        writeSharedState(profile: profile, message: message)
        guard Self.isSupported else { return false }
        // 同步判定：存在运行中活动才需要更新（无活动返回 false，由调用方决定是否新建）
        let runningActivities = Activity<PetActivityAttributes>.activities
        guard !runningActivities.isEmpty else { return false }

        let snapshot = profile.snapshot()
        let newState = PetActivityAttributes.ContentState(
            petName: snapshot.name,
            level: snapshot.level,
            mood: snapshot.mood,
            intimacy: snapshot.intimacy,
            hunger: snapshot.hunger,
            message: message ?? "一直陪着你~"
        )
        let content = ActivityContent(state: newState, staleDate: nil)
        Task {
            for activity in runningActivities {
                await activity.update(content)
            }
        }
        return true
    }

    /// 结束灵动岛活动（用户关闭常驻展示时）。
    func stopActivity() {
        guard Self.isSupported else { return }
        Task {
            for activity in Activity<PetActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
        activityId = nil
    }

    /// 是否存在运行中的灵动岛活动。
    var hasActiveActivity: Bool {
        guard Self.isSupported else { return false }
        return !Activity<PetActivityAttributes>.activities.isEmpty
    }

    // ---------- App Group 状态同步（小组件数据通道） ----------

    /// 把展示快照写入 App Group `state.json`（WidgetTimelineProvider 读取）。
    private func writeSharedState(profile: PetProfile, message: String?) {
        let snapshot = profile.snapshot()
        let dict: [String: Any] = [
            "name": snapshot.name,
            "level": snapshot.level,
            "mood": snapshot.mood,
            "intimacy": snapshot.intimacy,
            "hunger": snapshot.hunger,
            "message": message ?? "一直陪着你~",
            "updated_at": Date.now.millisecondsSince1970
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: dict, options: [.sortedKeys]) else {
            return
        }
        try? data.write(
            to: AppInfo.appGroupContainerURL.appendingPathComponent("state.json"),
            options: .atomic
        )
    }
}
