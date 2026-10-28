package com.mobiledesktoppet.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.notification.NotificationHelper
import com.mobiledesktoppet.app.render.PetGLSurfaceView
import com.mobiledesktoppet.app.render.PetRenderer
import com.mobiledesktoppet.app.ui.UiNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 悬浮窗前台服务（时序图 5.1）：WindowManager.addView 常驻宠物视图 + 前台通知。
 *
 * 职责：
 * - 渲染器选择（有模型 → Live2D；否则兜底，业务不崩溃）；
 * - 拖拽/点按/双击手势 → [OverlayTouchController]；
 * - 随机走动（可开关，省电模式下降低频率，P1-5）；
 * - 前台通知状态摘要随喂食等互动更新（时序图 5.2）。
 */
class OverlayPetService : Service(), OverlayTouchController.Callbacks {

    private lateinit var windowManager: WindowManager
    private var overlayRoot: View? = null
    private var bubbleView: BubbleView? = null
    private var miniActionBar: MiniActionBar? = null
    private var touchController: OverlayTouchController? = null
    private var glSurfaceView: PetGLSurfaceView? = null
    private var renderer: PetRenderer? = null

    /** 随机走动协程（walkEnabled=false 时挂起）。 */
    private var walkJob: Job? = null

    private val prefs: SharedPreferences
        get() = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 1. 前台常驻通知（specialUse 类型，API 34 必需）
        ServiceCompat.startForeground(
            this,
            NotificationHelper.OVERLAY_NOTIFICATION_ID,
            NotificationHelper.buildOverlayNotification(this, null),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // 2. 渲染器选择 + 附加悬浮视图
        val (petRenderer, isLive2d) = ServiceLocator.createPetRenderer(this)
        renderer = petRenderer
        attachOverlayView(petRenderer, isLive2d)

        // 3. 状态刷新 / 走动循环
        instance = this
        startWalkLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 喂食等互动后 UI 通过静态入口请求刷新前台通知（时序图 5.2 末段）
        ServiceLocator.applicationScope.launch {
            val profile = runCatching { ServiceLocator.petRepository.getProfile() }.getOrNull()
            val notification =
                NotificationHelper.buildOverlayNotification(this@OverlayPetService, profile)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NotificationHelper.OVERLAY_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        walkJob?.cancel()
        touchController = null
        glSurfaceView?.onHostPause()
        overlayRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // 视图未附加
            }
        }
        overlayRoot = null
        renderer?.release()
        renderer = null
        super.onDestroy()
    }

    // ---------- 悬浮视图 ----------

    /** 附加悬浮窗视图（宠物 + 气泡 + 迷你操作条）。 */
    private fun attachOverlayView(petRenderer: PetRenderer, isLive2d: Boolean) {
        val sizeDp = prefs.getInt(KEY_OVERLAY_SIZE_DP, DEFAULT_OVERLAY_SIZE_DP)
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()

        val root = android.widget.FrameLayout(this)
        root.clipChildren = false

        // 宠物渲染视图（Live2D → GLSurfaceView；兜底 → Canvas View）
        val petView: View = if (isLive2d) {
            val gl = PetGLSurfaceView(this, petRenderer)
            glSurfaceView = gl
            gl
        } else {
            petRenderer.platformView()
        }
        root.addView(
            petView,
            android.widget.FrameLayout.LayoutParams(sizePx, sizePx)
        )

        // 台词气泡
        val bubble = BubbleView(this)
        root.addView(
            bubble,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = -(sizePx / 3) }
        )
        bubbleView = bubble

        // 迷你操作条（双击/长按弹出）
        val miniBar = MiniActionBar(this, miniBarCallbacks)
        root.addView(
            miniBar,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        miniActionBar = miniBar

        // 悬浮窗布局参数
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - 16
            y = resources.displayMetrics.heightPixels / 2
        }

        // 触摸手势
        val controller = OverlayTouchController(windowManager, root, layoutParams, this)
        touchController = controller
        root.setOnTouchListener { _, event ->
            controller.onTouchEvent(event)
            true
        }

        overlayRoot = root
        windowManager.addView(root, layoutParams)
    }

    // ---------- OverlayTouchController.Callbacks ----------

    override fun onSingleTap() {
        // 点按互动：随机台词 + 随机动作 + 点按增益（共享知识 #9：刷新 lastActiveAt）
        val engine = ServiceLocator.dialogueEngine
        val reaction = engine.pickTapReaction()
        val motions = listOf("happy", "shy", "idle")
        renderer?.playMotion(motions[Random.nextInt(motions.size)])
        bubbleView?.show(reaction.text)
        ServiceLocator.applicationScope.launch {
            runCatching { ServiceLocator.petRepository.recordInteraction() }
        }
    }

    override fun onDoubleTap() {
        miniActionBar?.show()
    }

    override fun onDragStart() {
        miniActionBar?.dismiss()
    }

    override fun onDragEnd(x: Int, y: Int) {
        // 吸附完成后回到待机姿态
        renderer?.playMotion("idle")
    }

    // ---------- 走动循环（P0-3 / P1-5） ----------

    private fun startWalkLoop() {
        walkJob?.cancel()
        walkJob = ServiceLocator.applicationScope.launch {
            while (isActive) {
                val batterySaver = prefs.getBoolean(KEY_BATTERY_SAVER, false)
                val walkEnabled = prefs.getBoolean(KEY_WALK_ENABLED, true)
                delay(if (batterySaver) 12_000L else 4_000L)
                if (!walkEnabled) continue
                val root = overlayRoot ?: continue
                val lp = root.layoutParams as? WindowManager.LayoutParams ?: continue
                val metrics = resources.displayMetrics
                // 随机横移一小段（走动），随后回待机
                val deltaX = Random.nextInt(-120, 120)
                lp.x = (lp.x + deltaX).coerceIn(0, metrics.widthPixels - root.width)
                withContext(Dispatchers.Main) {
                    try {
                        windowManager.updateViewLayout(root, lp)
                    } catch (_: Exception) {
                    }
                }
                renderer?.playMotion("walk")
                delay(1_200L)
                renderer?.playMotion("idle")
            }
        }
    }

    // ---------- 迷你操作条转发 ----------

    private val miniBarCallbacks = object : MiniActionBar.Callbacks {
        override fun onFeedClicked() = navigateTo("feed")
        override fun onChatClicked() = navigateTo("chat")
        override fun onSettingsClicked() = navigateTo("settings")
        override fun onCollapseClicked() {
            // 收起：暂停渲染并隐藏视图（保留前台通知，用户可从主界面恢复）
            renderer?.pause()
            overlayRoot?.visibility = View.INVISIBLE
            ServiceLocator.applicationScope.launch {
                delay(8_000L)
                renderer?.resume()
                overlayRoot?.visibility = View.VISIBLE
            }
        }
    }

    /** 深链跳转主界面对应页面（petapp://reminder 等同机制）。 */
    private fun navigateTo(target: String) {
        val intent = UiNavigator.mainScreenIntent(this, target)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        /** 设置 SharedPreferences 名称（SettingsScreen 与 Service 共用）。 */
        const val SETTINGS_PREFS = "pet_settings"
        const val KEY_BATTERY_SAVER = "battery_saver"
        const val KEY_WALK_ENABLED = "walk_enabled"
        const val KEY_OVERLAY_SIZE_DP = "overlay_size_dp"
        const val KEY_FRAME_RATE = "frame_rate"
        const val DEFAULT_OVERLAY_SIZE_DP = 140

        /** 运行实例（UI 层刷新前台通知用；Service 非绑定生命周期下允许静态弱引用）。 */
        @Volatile
        var instance: OverlayPetService? = null
            private set

        /** 便捷启动（UI 层调用；需已授权悬浮窗权限）。 */
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayPetService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayPetService::class.java))
        }

        /** 时序图 5.2：喂食等互动后刷新前台通知中的状态摘要。 */
        fun notifyStateChanged() {
            instance?.let { svc ->
                val intent = Intent(svc, OverlayPetService::class.java)
                svc.startService(intent)
            }
        }
    }
}
