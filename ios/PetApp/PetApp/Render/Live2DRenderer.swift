import Foundation

#if canImport(Live2DCubismCore)
import MetalKit
import Live2DCubismCore

/// Live2D Cubism 真实渲染实现（**Metal + Cubism SDK** 分支：仅当工程可导入 Live2DCubismCore 时编译）。
///
/// ⚠️ 实包固化点（架构文档 §9 待明确事项 #1）：本文件为「可编译的接入骨架」——
/// 模型目录解析/校验已完整实现；Cubism 模型对象创建与 Metal 管线绑定需按实包的
/// 实际 API 固化（对照官方 iOS Framework 的 CubismRenderer / LAppView 样例）。
/// 未放置 SDK 时编译 `#else` 占位分支，loadModel 恒为 false → 自动兜底，绝不崩溃（硬性约束 1/2）。
final class Live2DRenderer: PetRenderer, MTKViewDelegate {

    private(set) var device: MTLDevice?
    private weak var view: MTKView?

    private(set) var currentMotion: PetMotion = .idle

    private var paused = false
    private var released = false
    private var modelLoaded = false
    private var modelDirectory: URL?

    init() {
        self.device = MTLCreateSystemDefaultDevice()
    }

    // MARK: - PetRenderer

    func loadModel(path: String) -> Bool {
        guard !released else { return false }
        guard let device = device else {
            NSLog("[Live2DRenderer] 无可用 Metal 设备，回退兜底渲染")
            return false
        }
        let directory = URL(fileURLWithPath: path)
        let manager = ModelAssetManager()
        let validation = manager.validate(directory: directory)
        guard validation.ok else {
            NSLog("[Live2DRenderer] 模型校验失败: \(validation.errors.joined(separator: "; "))")
            return false
        }
        modelDirectory = directory
        modelLoaded = true
        NSLog("[Live2DRenderer] 模型校验通过（Metal 管线接入待实包固化）: \(path)")
        _ = device // 设备已就绪，供实包固化时创建渲染管线使用
        return true
    }

    func playMotion(name: String) {
        guard modelLoaded else { return }
        currentMotion = PetMotion(rawValue: name.lowercase()) ?? .idle
        // ⚠️ 实包固化点：motionManager.startMotionPriority(动作组, 优先级)
    }

    func setFrameRate(fps: Int) {
        let safe = max(5, min(60, fps))
        view?.preferredFramesPerSecond = safe
    }

    func pause() {
        paused = true
    }

    func resume() {
        paused = false
    }

    func release() {
        released = true
        modelLoaded = false
        modelDirectory = nil
        view = nil
        device = nil
    }

    // MARK: - MTKView 注入（由 Live2DContainerView 调用）

    /// 注入 MTKView 并接管渲染循环（透明背景）。
    func attach(view: MTKView) {
        self.view = view
        if view.device == nil {
            view.device = device
        }
        view.delegate = self
        view.isOpaque = false
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.enableSetNeedsDisplay = false
    }

    // MARK: - MTKViewDelegate

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        // ⚠️ 实包固化点：更新投影/视图矩阵
    }

    func draw(in view: MTKView) {
        guard !paused, !released, modelLoaded else { return }
        guard let _ = view.currentDrawable, let _ = view.currentRenderPassDescriptor else { return }
        // ⚠️ 实包固化点：
        // 1. CubismModel.loadParameters / motionManager.update / saveParameters / update
        // 2. CubismRenderer(Metal) setMvpMatrix + drawModel
        // 3. 当前版本仅提交透明帧，保证无实包固化时画面透明且不崩溃
    }
}

#else

/// Live2DRenderer 占位实现（**无 SDK 分支**：third_party/cubism-ios 未放置时编译）。
///
/// 与 `#if canImport(Live2DCubismCore)` 分支保持相同公开接口；
/// loadModel 恒返回 false → 调用方自动切换 FallbackPetView 程序绘制兜底。
final class Live2DRenderer: PetRenderer {

    init() {}

    func loadModel(path: String) -> Bool {
        NSLog("[Live2DRenderer] Cubism SDK 未放置（third_party/cubism-ios 缺失），Live2D 渲染不可用。" +
              "请执行 scripts/setup-cubism-ios.sh 后重新生成工程。")
        return false
    }

    func playMotion(name: String) {}

    func setFrameRate(fps: Int) {}

    func pause() {}

    func resume() {}

    func release() {}
}

#endif
