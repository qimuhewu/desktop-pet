package com.mobiledesktoppet.app.render

import android.content.Context
import android.util.Log
import android.view.View

/**
 * Live2DRenderer 占位实现（**nolive2d 源集**：third_party/cubism-android 未放置时参与编译）。
 *
 * 职责：与 live2d 源集的真实实现保持**完全相同的类名/包名/方法签名**，
 * loadModel 恒返回 false → 调用方（OverlayPetService / MainScreen）自动切换
 * FallbackPetRenderer 程序绘制兜底，业务零感知、绝不崩溃（硬性约束 1/2）。
 *
 * 放置 SDK 后（scripts/setup-cubism-android.ps1）构建自动切换到 src/live2d/java 的真实实现。
 */
class Live2DRenderer(private val context: Context) : PetRenderer {

    override fun loadModel(path: String): Boolean {
        Log.w(TAG, "Cubism SDK 未放置（third_party/cubism-android 缺失），Live2D 渲染不可用。" +
            "请执行 scripts/setup-cubism-android.ps1 放置 SDK 后重新构建。")
        return false
    }

    override fun playMotion(name: String) {
        // 占位实现：无动作
    }

    override fun setFrameRate(fps: Int) {
        // 占位实现：无渲染循环
    }

    override fun pause() {
        // 占位实现：无渲染循环
    }

    override fun resume() {
        // 占位实现：无渲染循环
    }

    override fun release() {
        // 占位实现：无资源
    }

    override fun platformView(): View {
        // 正常流程不会走到这里（loadModel 返回 false 后即切换兜底渲染）；
        // 返回空 View 保证接口完整、调用安全。
        return View(context)
    }

    companion object {
        private const val TAG = "Live2DRenderer"
    }
}
