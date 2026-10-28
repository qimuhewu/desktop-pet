package com.mobiledesktoppet.app.render

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import android.view.View
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.Id.CubismId
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix
import com.live2d.sdk.cubism.framework.model.CubismModel
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.utils.CubismDebug

/**
 * Live2D Cubism 真实渲染实现（**live2d 源集**：仅当 third_party/cubism-android 存在时参与编译，
 * 见 app/build.gradle.kts 的源集隔离逻辑；硬性约束 2 的「编译开关」方案）。
 *
 * ⚠️ 说明：本文件按 Cubism 5 SDK for Android（Native）Framework 的公开 API 编写。
 * 实包目录/类名可能随 SDK 版本微调，**用户放置实包后请按实际目录结构固化 import 与初始化参数**
 * （架构文档 §9 待明确事项 #1）。固化前以官方 LAppModel/LAppView 样例为对照基准。
 *
 * 职责：模型加载（model3.json 解析 → moc3 → 贴图 → 动作）、动作状态机、GL 绘制循环、帧率节流。
 */
class Live2DRenderer(private val context: Context) : PetRenderer, GlRenderDelegate {

    /** Cubism 用户模型封装（持有 CubismModel 与动作管理）。 */
    private class PetUserModel : CubismUserModel() {
        var currentMotionIndex = -1
    }

    private var userModel: PetUserModel? = null
    private var modelDir: String? = null

    // 视图矩阵（官方 Framework 标准：投影 × 视图）
    private val viewMatrix = CubismViewMatrix()
    private val projectionMatrix = CubismMatrix44()

    @Volatile
    private var frameIntervalNanos: Long = 33_333_333L // 默认 ~30fps

    @Volatile
    private var lastFrameNanos: Long = 0L

    @Volatile
    private var paused = false

    @Volatile
    private var released = false

    private var surfaceCreated = false

    // ---------- PetRenderer ----------

    override fun loadModel(path: String): Boolean {
        if (released) return false
        return try {
            // CubismFramework 全局初始化（幂等：startUp/initialize 需在模型加载前完成）
            if (!CubismFramework.isInitialized()) {
                val option = CubismFramework.Option()
                option.logFunction = CubismDebug.cubismLogDebug
                option.loggingLevel = CubismFramework.Option.LogLevel.LOGLEVEL_VERBOSE.value
                CubismFramework.cleanUp()
                CubismFramework.startUp(option)
                CubismFramework.initialize()
            }

            val model = PetUserModel()
            // model3.json 路由：加载 moc3/贴图/物理/动作组（官方 CubismModelSettingJson 路径）
            model.loadModel(path) // Framework 支持直接传 model3.json 文件路径
            model.loadPhysics()
            model.createRenderer()

            this.userModel = model
            this.modelDir = path
            this.surfaceCreated = false
            Log.i(TAG, "Live2D 模型加载成功: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Live2D 模型加载失败: $path", e)
            releaseModel()
            false
        }
    }

    override fun playMotion(name: String) {
        val model = userModel ?: return
        try {
            val motionName = mapMotionName(name)
            // 动作组按名称索引（model3.json 的 FileReferences.Motions；缺省组名 "Idle"）
            val groupId: CubismId = CubismFramework.getIdManager().getId(MOTION_GROUP)
            val index = model.getModel().getMotionIndex(groupId, motionName)
            if (index >= 0) {
                model.motionManager.startMotionPrioritize(
                    groupId, index,
                    com.live2d.sdk.cubism.framework.utils.CubismFrameworkPriority.PRIORITY_NORMAL
                )
                model.currentMotionIndex = index
            }
        } catch (e: Exception) {
            // 动作缺失不致命：保持待机状态继续渲染
            Log.w(TAG, "动作播放失败（保持待机）: $name", e)
        }
    }

    override fun setFrameRate(fps: Int) {
        val safe = fps.coerceIn(5, 60)
        frameIntervalNanos = 1_000_000_000L / safe
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
        lastFrameNanos = 0L
    }

    override fun release() {
        released = true
        releaseModel()
    }

    override fun platformView(): View {
        // 渲染视图由 PetGLSurfaceView 承载（Service 创建时传入本渲染器）
        throw IllegalStateException(
            "Live2DRenderer 的宿主视图请通过 PetGLSurfaceView(context, renderer) 创建"
        )
    }

    // ---------- GlRenderDelegate（GL 线程回调，由 PetGLSurfaceView 驱动） ----------

    override fun onSurfaceCreatedInternal(gl: android.opengl.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        surfaceCreated = true
        GLES20.glClearColor(0f, 0f, 0f, 0f) // 透明清屏
    }

    override fun onSurfaceChangedInternal(gl: android.opengl.GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewMatrix.setScreenRect(0f, width.toFloat(), 0f, height.toFloat())
        viewMatrix.scale(width.toFloat() / height.toFloat(), 1f)
        // 逻辑坐标 → 设备坐标（Cubism 标准逻辑窗口 2x2）
        projectionMatrix.loadIdentity()
        val ratio = width.toFloat() / height.toFloat()
        projectionMatrix.scale(1f / ratio * 2f, 2f)
    }

    override fun onDrawFrameInternal(gl: android.opengl.GL10?) {
        if (paused || released || !surfaceCreated) return

        // 帧率节流（省电模式：setFrameRate 降低后跳帧）
        val now = System.nanoTime()
        if (lastFrameNanos != 0L && now - lastFrameNanos < frameIntervalNanos) {
            return
        }
        lastFrameNanos = now

        val model = userModel ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        try {
            model.getModel().loadParameters()
            model.motionManager.updateMotion(model.getModel(), now / 1_000_000L.toDouble())
            model.getModel().saveParameters()
            model.getModel().update()
            val projView = CubismMatrix44.multiply(projectionMatrix, viewMatrix)
            model.getRenderer().setMvpMatrix(projView)
            model.getRenderer().drawModel()
        } catch (e: Exception) {
            Log.e(TAG, "渲染帧异常（跳过本帧）", e)
        }
    }

    // ---------- 内部工具 ----------

    private fun releaseModel() {
        val model = userModel ?: return
        try {
            model.deleteRenderer()
        } catch (_: Exception) {
            // 忽略释放异常
        }
        userModel = null
        modelDir = null
    }

    /** 动作名映射（业务语义 → model3.json 动作名，实包固化时可按模型实际动作名调整）。 */
    private fun mapMotionName(name: String): String = when (name.lowercase()) {
        "walk" -> "Walk"
        "happy" -> "Happy"
        "shy" -> "Shy"
        "eat" -> "Eat"
        else -> "Idle"
    }

    companion object {
        private const val TAG = "Live2DRenderer"
        private const val MOTION_GROUP = "Idle"
    }
}
