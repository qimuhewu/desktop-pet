package com.mobiledesktoppet.app.keepalive

import android.os.Build

/**
 * 各 ROM 保活设置跳转表（P0-8 / R1 风险缓解；docs/03 §5 文案的代码内嵌版）。
 * UI（PermissionGuideScreen）按厂商匹配展示并尝试深链跳转。
 */
object RomKeepAliveGuide {

    /** 单条 ROM 指引。 */
    data class RomGuide(
        val romName: String,
        val manufacturerPrefixes: List<String>,
        val steps: List<String>,
        /** 自启动设置页组件名（多个候选，逐一尝试；均失败则走手动路径）。 */
        val autoStartComponents: List<String> = emptyList()
    )

    val guides: List<RomGuide> = listOf(
        RomGuide(
            romName = "MIUI / HyperOS（小米）",
            manufacturerPrefixes = listOf("xiaomi", "redmi"),
            steps = listOf(
                "设置 → 应用设置 → 应用管理 → 桌宠",
                "开启「自启动」",
                "省电策略 → 选择「无限制」",
                "近期任务下拉桌宠卡片 → 加锁"
            ),
            autoStartComponents = listOf(
                "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        RomGuide(
            romName = "ColorOS（OPPO / 一加）",
            manufacturerPrefixes = listOf("oppo", "oneplus", "realme"),
            steps = listOf(
                "设置 → 电池 → 更多设置 → 关闭「应用速冻」（对桌宠）",
                "设置 → 应用 → 桌宠 → 耗电管理 → 允许后台运行",
                "开启「自启动」与「关联启动」"
            ),
            autoStartComponents = listOf(
                "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        ),
        RomGuide(
            romName = "EMUI / HarmonyOS（华为）",
            manufacturerPrefixes = listOf("huawei", "honor"),
            steps = listOf(
                "设置 → 应用 → 应用启动管理 → 桌宠",
                "改为「手动管理」：允许自启动 / 关联启动 / 后台活动",
                "电池 → 更多电池设置 → 关闭「休眠时始终保持网络连接」外的休眠限制"
            ),
            autoStartComponents = listOf(
                "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager/com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        ),
        RomGuide(
            romName = "OriginOS / FuntouchOS（vivo）",
            manufacturerPrefixes = listOf("vivo", "iqoo"),
            steps = listOf(
                "i 管家 → 权限管理 → 自启动 → 允许桌宠",
                "设置 → 电池 → 后台耗电管理 → 桌宠 → 允许后台高耗电",
                "多任务卡片下拉 → 加锁"
            ),
            autoStartComponents = listOf(
                "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
        ),
        RomGuide(
            romName = "其他 / 原生 Android",
            manufacturerPrefixes = listOf("google", "motorola", "samsung", "sony"),
            steps = listOf(
                "设置 → 应用 → 桌宠 → 电池 → 取消电池优化（仅引导，明示利弊）",
                "保持悬浮窗权限已授予",
                "从最近任务移除后如宠物消失，重新打开 App 即可恢复"
            )
        )
    )

    /** 按设备厂商匹配指引（未匹配时返回「其他」）。 */
    fun detectGuide(): RomGuide {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        return guides.firstOrNull { guide ->
            guide.manufacturerPrefixes.any { manufacturer.contains(it) }
        } ?: guides.last()
    }

    /** 全部指引（引导页完整列表）。 */
    fun allGuides(): List<RomGuide> = guides
}
