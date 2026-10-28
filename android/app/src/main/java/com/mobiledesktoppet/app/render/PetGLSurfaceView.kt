package com.mobiledesktoppet.app.render

import android.content.Context
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GL 渲染回调（仅 live2d 源集的 Live2DRenderer 实现）。
 * PetGLSurfaceView 把 GLSurfaceView.Renderer 的帧回调转发给该接口，
 * 主源集代码只依赖本接口，不依赖任何 Cubism 类型（硬性约束 2：无 SDK 可编译）。
 */
interface GlRenderDelegate {
    fun onSurfaceCreatedInternal(gl: GL10?, config: EGLConfig?)
    fun onSurfaceChangedInternal(gl: GL10?, width: Int, height: Int)
    fun onDrawFrameInternal(gl: GL10?)
}

/**
 * GLSurfaceView 封装：透明背景、生命周期挂接、渲染帧转发。
 *
 * - 仅在 Live2D 渲染路径使用（兜底渲染走 Canvas View）；
 * - 无 SDK 时该类不会被实例化（Live2DRenderer.loadModel 恒为 false → 兜底）。
 */
class PetGLSurfaceView(
    context: Context,
    private val petRenderer: PetRenderer
) : GLSurfaceView(context) {

    /** GL 帧回调 → 转发给 GlRenderDelegate（live2d 源集的 Live2DRenderer）。 */
    private inner class FrameForwarder : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            (petRenderer as? GlRenderDelegate)?.onSurfaceCreatedInternal(gl, config)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            (petRenderer as? GlRenderDelegate)?.onSurfaceChangedInternal(gl, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            (petRenderer as? GlRenderDelegate)?.onDrawFrameInternal(gl)
        }
    }

    init {
        setEGLContextClientVersion(2) // Cubism Android 支持 GLES2+
        setEGLConfigChooser(8, 8, 8, 8, 0, 0) // 透明背景（悬浮窗仅显示宠物本体）
        setZOrderOnTop(true)
        setRenderer(FrameForwarder())
        renderMode = RENDERMODE_CONTINUOUSLY
        // Continuous 模式下实际帧率由渲染器内部节流控制（live2d 源集 Live2DRenderer.setFrameRate）
    }

    /** App 生命周期挂接（Service 持有者调用）。 */
    fun onHostResume() {
        onResume()
    }

    fun onHostPause() {
        onPause()
    }
}
