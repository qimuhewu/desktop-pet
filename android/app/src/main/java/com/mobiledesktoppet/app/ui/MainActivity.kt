package com.mobiledesktoppet.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.permission.OverlayPermissionManager
import com.mobiledesktoppet.app.service.OverlayPetService
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 入口 Activity：路由到各 Screen（程序化 View 构建，无 XML 布局）。
 *
 * 路由目标：main / feed / chat / reminder / settings / permission。
 * 首次启动未授权悬浮窗 → 先展示 PermissionGuideScreen（时序图 5.1 首段）。
 */
class MainActivity : AppCompatActivity() {

    /** 页面级协程作用域（界面异步任务；onDestroy 取消）。 */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var container: FrameLayout

    private var currentScreenView: android.view.View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.isInitialized) {
            ServiceLocator.init(applicationContext)
        }
        container = FrameLayout(this)
        container.setBackgroundColor(PetTheme.SURFACE)
        setContentView(container)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 从系统授权页返回后重新评估路由
        if (currentRoute == ROUTE_PERMISSION && OverlayPermissionManager(this).checkOverlayPermission()) {
            navigate(ROUTE_MAIN)
        }
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy()
    }

    /** 当前路由（供返回授权页后判断）。 */
    private var currentRoute: String = ROUTE_MAIN

    // ---------- 路由 ----------

    /** 页面导航（各 Screen 回调入口）。 */
    fun navigate(route: String) {
        currentRoute = route
        currentScreenView?.let { container.removeView(it) }
        val view = when (route) {
            ROUTE_FEED -> FeedScreen(this, screenScope) { navigate(it) }.createView()
            ROUTE_CHAT -> ChatScreen(this, screenScope) { navigate(it) }.createView()
            ROUTE_REMINDER -> ReminderScreen(this, screenScope) { navigate(it) }.createView()
            ROUTE_SETTINGS -> SettingsScreen(this, screenScope) { navigate(it) }.createView()
            ROUTE_PERMISSION -> PermissionGuideScreen(this, screenScope) { navigate(it) }.createView()
            else -> MainScreen(this, screenScope) { navigate(it) }.createView()
        }
        currentScreenView = view
        container.addView(view)
    }

    // ---------- Intent / 深链 ----------

    private fun handleIntent(intent: Intent?) {
        val explicitTarget = intent?.getStringExtra(EXTRA_TARGET)
        if (explicitTarget != null) {
            navigate(explicitTarget)
            return
        }
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "petapp" && data.host == "reminder") {
            navigate(ROUTE_REMINDER)
            return
        }
        // 默认路由：悬浮窗权限未授予 → 权限引导；已授予 → 主界面并确保服务运行
        val permissionManager = OverlayPermissionManager(this)
        if (!permissionManager.checkOverlayPermission()) {
            navigate(ROUTE_PERMISSION)
        } else {
            navigate(ROUTE_MAIN)
            OverlayPetService.start(this)
        }
    }

    companion object {
        const val ROUTE_MAIN = "main"
        const val ROUTE_FEED = "feed"
        const val ROUTE_CHAT = "chat"
        const val ROUTE_REMINDER = "reminder"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_PERMISSION = "permission"

        const val EXTRA_TARGET = "extra_target"

        /** 跳转主界面对应页面（悬浮窗操作条/通知深链用）。 */
        fun launchIntent(context: Context, target: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TARGET, target)
    }
}

/**
 * UI 导航工具（OverlayPetService 深链跳转用；与 MainActivity.launchIntent 等价包装）。
 */
object UiNavigator {
    fun mainScreenIntent(context: Context, target: String): Intent =
        MainActivity.launchIntent(context, target)
}
