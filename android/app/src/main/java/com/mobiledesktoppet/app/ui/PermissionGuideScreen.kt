package com.mobiledesktoppet.app.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.mobiledesktoppet.app.keepalive.RomKeepAliveGuide
import com.mobiledesktoppet.app.permission.OverlayPermissionManager
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope

/**
 * 悬浮窗权限分步图示引导 + 各 ROM 保活设置跳转指引（P0-8；PRD §5.4 子页面）。
 */
class PermissionGuideScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    private val permissionManager by lazy { OverlayPermissionManager(activity) }

    fun createView(): View {
        val context = activity
        val root = PetTheme.verticalLayout(context)
        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        scroll.addView(root)

        // 顶栏
        val topBar = PetTheme.horizontalLayout(context)
        topBar.addView(
            TextView(context).apply {
                text = "🐾 权限与保活引导"
                textSize = 18f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topBar.addView(PetTheme.outlineButton(context, "← 返回") { navigate(MainActivity.ROUTE_MAIN) })
        root.addView(topBar)

        root.addView(
            PetTheme.body(
                context,
                "让宠物悬浮在所有应用之上，需要先授予「显示在其他应用上层」权限。"
            )
        )

        // 步骤 1：悬浮窗授权
        root.addView(PetTheme.title(context, "第 1 步 · 授予悬浮窗权限"))
        root.addView(
            PetTheme.body(
                context,
                "① 点击下方按钮打开系统设置页；\n" +
                    "② 找到「桌宠」并打开「允许显示在其他应用上层」开关；\n" +
                    "③ 返回本页后点「启动悬浮宠物」即可看到宠物。"
            )
        )
        root.addView(
            PetTheme.button(context, "去开启悬浮窗权限") {
                permissionManager.launchOverlaySettings()
            }
        )

        // 步骤 2：电池优化（仅引导，明示利弊）
        root.addView(PetTheme.title(context, "第 2 步 · 电池优化白名单（建议）"))
        root.addView(
            PetTheme.body(
                context,
                "将「桌宠」加入电池优化白名单可显著降低宠物被系统休眠的概率。" +
                    "利：悬浮宠物更稳定常驻；弊：后台电量消耗略增。是否开启由你决定。"
            )
        )
        root.addView(
            PetTheme.outlineButton(context, "打开电池优化设置") {
                permissionManager.launchBatteryOptimizationSettings()
            }
        )

        // 步骤 3：当前 ROM 保活指引
        val detected = RomKeepAliveGuide.detectGuide()
        root.addView(PetTheme.title(context, "第 3 步 · ${detected.romName} 保活指引"))
        detected.steps.forEachIndexed { index, step ->
            root.addView(PetTheme.body(context, "${index + 1}. $step"))
        }
        if (detected.autoStartComponents.isNotEmpty()) {
            root.addView(
                PetTheme.outlineButton(context, "尝试跳转自启动设置") {
                    permissionManager.launchRomAutoStart(detected)
                }
            )
        }

        // 全部 ROM 指引（折叠为纯文本）
        root.addView(PetTheme.title(context, "其他 ROM 指引"))
        val others = RomKeepAliveGuide.allGuides()
            .filter { it !== detected }
            .joinToString("\n\n") { guide ->
                "【${guide.romName}】\n" + guide.steps
                    .mapIndexed { i, s -> "${i + 1}. $s" }
                    .joinToString("\n")
            }
        root.addView(PetTheme.body(context, others))

        // 启动入口（授权完成后）
        root.addView(
            PetTheme.button(context, "启动悬浮宠物") {
                if (permissionManager.checkOverlayPermission()) {
                    com.mobiledesktoppet.app.service.OverlayPetService.start(context)
                    navigate(MainActivity.ROUTE_MAIN)
                } else {
                    permissionManager.launchOverlaySettings()
                }
            }
        )
        return scroll
    }
}
