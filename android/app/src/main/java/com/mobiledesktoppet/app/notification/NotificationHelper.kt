package com.mobiledesktoppet.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.mobiledesktoppet.app.data.db.PetProfileEntity

/**
 * 通知渠道与通知构建（架构文档 §8 共享知识 #12）：
 * - pet_overlay：常驻悬浮窗通知（低优先级静默）；
 * - pet_reminder：提醒通知（默认重要度，深链打开提醒管理页）。
 */
object NotificationHelper {

    const val CHANNEL_OVERLAY = "pet_overlay"
    const val CHANNEL_REMINDER = "pet_reminder"

    /** 常驻悬浮窗通知 id（前台 Service 专用）。 */
    const val OVERLAY_NOTIFICATION_ID = 1001

    /** 提醒通知基础 id（叠加递增避免覆盖）。 */
    private const val REMINDER_NOTIFICATION_ID_BASE = 2000

    /** 在 PetApplication.onCreate 统一创建渠道（幂等）。 */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY, "桌宠常驻", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮窗宠物的前台常驻通知（静默）"
            setShowBadge(false)
            setSound(null, null)
        }
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER, "宠物提醒", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "喝水/久坐/番茄钟等定时提醒"
        }
        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(reminderChannel)
    }

    /** 常驻前台通知：宠物名 + 状态摘要。 */
    fun buildOverlayNotification(context: Context, profile: PetProfileEntity?): Notification {
        val contentText = if (profile != null) {
            "Lv.${profile.level} ${profile.name} · 饥饿${profile.hunger.toInt()} 心情${profile.mood.toInt()} 亲密${profile.intimacy.toInt()}"
        } else {
            "小宠物正在陪伴你~"
        }
        return NotificationCompat.Builder(context, CHANNEL_OVERLAY)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("🐱 ${profile?.name ?: "小希"} 在这里")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 提醒通知：宠物形象文案 + 深链跳转提醒管理页。 */
    fun showReminderNotification(context: Context, petName: String, message: String, notificationId: Int) {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("petapp://reminder")
        ).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("🐱 $petName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(REMINDER_NOTIFICATION_ID_BASE + notificationId, notification)
        } catch (_: SecurityException) {
            // 通知权限未授予：静默跳过（下次进 App 会引导授权）
        }
    }
}
