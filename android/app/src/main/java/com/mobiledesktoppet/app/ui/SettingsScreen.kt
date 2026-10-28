package com.mobiledesktoppet.app.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.mobiledesktoppet.app.permission.OverlayPermissionManager
import com.mobiledesktoppet.app.service.OverlayPetService
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope

/**
 * 设置页：帧率/省电/悬浮窗大小（Android）/ 提醒入口 / 权限引导 / 关于与版权声明（PRD §5.4）。
 */
class SettingsScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    private val prefs by lazy {
        activity.getSharedPreferences(OverlayPetService.SETTINGS_PREFS, Activity.MODE_PRIVATE)
    }

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
                text = "⚙ 设置"
                textSize = 18f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topBar.addView(PetTheme.outlineButton(context, "← 返回") { navigate(MainActivity.ROUTE_MAIN) })
        root.addView(topBar)

        // ---------- 动画与省电（P1-5） ----------
        root.addView(PetTheme.title(context, "动画与省电"))

        val batterySaverSwitch = buildSwitch("省电模式（降低帧率、减缓走动）", prefs.getBoolean(OverlayPetService.KEY_BATTERY_SAVER, false)) { checked ->
            prefs.edit().putBoolean(OverlayPetService.KEY_BATTERY_SAVER, checked).apply()
        }
        root.addView(batterySaverSwitch)

        val walkSwitch = buildSwitch("允许宠物随机走动", prefs.getBoolean(OverlayPetService.KEY_WALK_ENABLED, true)) { checked ->
            prefs.edit().putBoolean(OverlayPetService.KEY_WALK_ENABLED, checked).apply()
        }
        root.addView(walkSwitch)

        // 帧率选择
        root.addView(PetTheme.body(context, "悬浮窗动画帧率"))
        val frameRow = PetTheme.horizontalLayout(context)
        for (fps in listOf(15, 30, 60)) {
            frameRow.addView(
                PetTheme.outlineButton(context, "${fps}fps") {
                    prefs.edit().putInt(OverlayPetService.KEY_FRAME_RATE, fps).apply()
                }
            )
        }
        root.addView(frameRow)

        // ---------- 悬浮窗（Android 专属） ----------
        root.addView(PetTheme.title(context, "悬浮窗"))
        val sizeLabel = PetTheme.body(context, "悬浮窗大小：${prefs.getInt(OverlayPetService.KEY_OVERLAY_SIZE_DP, OverlayPetService.DEFAULT_OVERLAY_SIZE_DP)}dp")
        root.addView(sizeLabel)
        val seek = SeekBar(context).apply {
            max = 100 // 100~200dp
            progress = prefs.getInt(OverlayPetService.KEY_OVERLAY_SIZE_DP, OverlayPetService.DEFAULT_OVERLAY_SIZE_DP) - 100
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val dp = progress + 100
                    sizeLabel.text = "悬浮窗大小：${dp}dp"
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    prefs.edit().putInt(OverlayPetService.KEY_OVERLAY_SIZE_DP, (bar?.progress ?: 40) + 100).apply()
                }
            })
        }
        root.addView(seek)

        val serviceRunning = OverlayPetService.instance != null
        root.addView(
            if (serviceRunning) {
                PetTheme.outlineButton(context, "停止悬浮宠物") { OverlayPetService.stop(context) }
            } else {
                PetTheme.button(context, "启动悬浮宠物") {
                    val pm = OverlayPermissionManager(context)
                    if (pm.checkOverlayPermission()) {
                        OverlayPetService.start(context)
                    } else {
                        navigate(MainActivity.ROUTE_PERMISSION)
                    }
                }
            }
        )

        // ---------- 其他 ----------
        root.addView(PetTheme.title(context, "其他"))
        root.addView(PetTheme.outlineButton(context, "悬浮窗权限与保活引导") {
            navigate(MainActivity.ROUTE_PERMISSION)
        })
        root.addView(PetTheme.outlineButton(context, "提醒管理") {
            navigate(MainActivity.ROUTE_REMINDER)
        })

        // ---------- 关于与版权声明（共享知识 #11） ----------
        root.addView(PetTheme.title(context, "关于与许可声明"))
        root.addView(
            PetTheme.body(
                context,
                "本应用使用的 Live2D Cubism SDK 与示例模型「Hiyori」版权归 Live2D Inc. 所有。\n" +
                    "示例模型的使用遵循 Live2D 免费素材许可证（Free Material License Agreement），仅限非商业用途。" +
                    "如需商业使用，请替换为已获授权的模型资产。\n" +
                    "Live2D 及 Cubism 为 Live2D Inc. 的商标。\n\n" +
                    "桌宠 v1.0.0 · 第一版全本地运行，不收集任何用户数据。"
            )
        )
        return scroll
    }

    /** MaterialSwitch 快捷创建。 */
    private fun buildSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val context = activity
        val row = PetTheme.horizontalLayout(context)
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            MaterialSwitch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            }
        )
        return row
    }
}
