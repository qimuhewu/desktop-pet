import SwiftUI

#if canImport(Live2DCubismCore)
import MetalKit

/// UIViewRepresentable 桥接 MTKView（架构文档 §1.3：Live2D 视图桥接）。
///
/// - `renderMode == .live2d` 时挂接 Metal 渲染循环；
/// - 兜底/加载失败由调用方直接展示 `FallbackPetView`（不进入本视图）。
struct Live2DContainerView: UIViewRepresentable {

    enum RenderMode {
        case live2d
        case fallback
    }

    let renderer: Live2DRenderer
    let renderMode: RenderMode

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: renderer.device)
        view.isOpaque = false
        view.backgroundColor = .clear
        if renderMode == .live2d {
            renderer.attach(view: view)
        }
        return view
    }

    func updateUIView(_ uiView: MTKView, context: Context) {
        // 渲染循环由 Live2DRenderer 驱动，无需逐帧更新
    }

    static func dismantleUIView(_ uiView: MTKView, coordinator: ()) {
        uiView.delegate = nil
    }
}

#else

/// 无 SDK 时的占位容器：直接渲染 SwiftUI 兜底小动物（保证 UI 层无须条件编译）。
struct Live2DContainerView: View {

    enum RenderMode {
        case live2d
        case fallback
    }

    let renderer: Live2DRenderer
    let renderMode: RenderMode

    var body: some View {
        FallbackPetView(pose: .idle)
    }
}

#endif
