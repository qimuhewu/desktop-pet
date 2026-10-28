import Foundation
import ActivityKit

/// 灵动岛 ActivityAttributes 定义（主 App 与 PetLiveActivity 扩展共用，project.yml 双端编译）。
///
/// 紧凑态：宠物迷你状态（Lv.♥78）；展开态：动画 + 状态摘要 + 喂食快捷按钮（PRD §5.2）。
@available(iOS 16.1, *)
struct PetActivityAttributes: ActivityAttributes {

    /// 展示内容（随状态更新）。
    struct ContentState: Codable, Hashable {
        /// 宠物名。
        var petName: String
        /// 等级。
        var level: Int
        /// 心情值（0~100）。
        var mood: Int
        /// 亲密度（0~100）。
        var intimacy: Int
        /// 饥饿度（0~100）。
        var hunger: Int
        /// 当前台词/心情文案（如「心情很好~」）。
        var message: String
    }

    /// 静态属性（活动期间不变）：暂无，保留结构以便扩展。
}
