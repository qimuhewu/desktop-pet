package com.mobiledesktoppet.app.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 悬浮窗授权检测与跳转引导（P0-8；时序图 5.1 首段）。
 */
class OverlayPermissionManager(private val context: Context) {

    /** 是否已授予悬浮窗权限。 */
    fun checkOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    /** 跳转系统「显示在其他应用上层」授权页。 */
    fun launchOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                // 个别 ROM 无该页面时回退到应用详情页
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(fallback) }
            }
    }

    /** 跳转电池优化白名单（仅引导，不强制；明示利弊）。 */
    fun launchBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { launchAppDetailsSettings() }
    }

    /** 跳转本应用系统设置详情页（通知权限等）。 */
    fun launchAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 跳转通知设置页（API 26+）。 */
    fun launchNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** 尝试跳转指定 ROM 的自启动设置页（跳转失败返回 false，UI 显示手动路径指引）。 */
    fun launchRomAutoStart(rom: com.mobiledesktoppet.app.keepalive.RomKeepAliveGuide.RomGuide): Boolean {
        for (flatComponent in rom.autoStartComponents) {
            val intent = Intent().apply {
                component = ComponentName.unflattenFromString(flatComponent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return true
            }
        }
        return false
    }
}
