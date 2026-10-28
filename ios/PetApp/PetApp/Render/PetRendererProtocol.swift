import Foundation

/// 宠物渲染接口（与 Android render/PetRenderer.kt 对齐，架构文档 D1 决策）。
///
/// 双实现：
/// - `Live2DRenderer`：Cubism SDK 实现（`#if canImport(Live2DCubismCore)` 编译开关，缺 SDK 时为占位分支）；
/// - `FallbackPetView`：SwiftUI 程序绘制兜底（缺 SDK/模型时使用，业务不崩溃）。
protocol PetRenderer: AnyObject {

    /// 加载模型（标准 Cubism 结构：目录内含 *.model3.json）。
    /// 返回 false = 失败（调用方应切换到兜底渲染）。
    func loadModel(path: String) -> Bool

    /// 播放动作（idle/walk/happy/shy/eat 等）。
    func playMotion(name: String)

    /// 设置动画帧率（省电模式降低帧率，P1-5）。
    func setFrameRate(fps: Int)

    /// 暂停渲染循环。
    func pause()

    /// 恢复渲染循环。
    func resume()

    /// 释放资源，调用后实例不可复用。
    func release()
}

/// 宠物姿态枚举（业务语义动作名，双端一致；hungry/sleepy 为小组件展示用语义姿态）。
enum PetMotion: String, CaseIterable {
    case idle = "idle"
    case walk = "walk"
    case happy = "happy"
    case shy = "shy"
    case eat = "eat"
    case hungry = "hungry"
    case sleepy = "sleepy"
}
