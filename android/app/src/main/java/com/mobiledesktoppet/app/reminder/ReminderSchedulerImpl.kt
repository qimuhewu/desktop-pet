package com.mobiledesktoppet.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobiledesktoppet.app.data.PetRepository
import com.mobiledesktoppet.app.data.db.ReminderEntity
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.notification.NotificationHelper
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 提醒调度接口（类图 ReminderScheduler；与 iOS IOSReminderScheduler 对齐）。
 */
interface ReminderScheduler {
    /** 排程一条提醒。 */
    fun schedule(config: ReminderEntity)

    /** 取消一条提醒。 */
    fun cancel(id: String)

    /** 恢复全部已启用提醒（开机/App 启动后调用）。 */
    fun restoreAll()
}

/**
 * Android 提醒调度实现（决策 D5）：
 * - AlarmManager.setExactAndAllowWhileIdle 精确触发（无精确权限时降级 setWindow）；
 * - WorkManager 周期兜底（省电模式杀 Alarm 后由兜底 Worker 补发）；
 * - BootReceiver 开机后 [restoreAll] 重排。
 */
class ReminderSchedulerImpl(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val repository: PetRepository by lazy { ServiceLocator.petRepository }

    // ---------- ReminderScheduler ----------

    override fun schedule(config: ReminderEntity) {
        if (!config.enabled) return
        val nextTrigger = config.nextTriggerAt ?: return
        if (nextTrigger <= System.currentTimeMillis()) {
            // 过期排程交给兜底 Worker / 重排逻辑
            ServiceLocator.applicationScope.launch {
                repository.planNextTrigger(config)?.let { schedule(it) }
            }
            return
        }
        val pendingIntent = alarmPendingIntent(config)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // 无精确闹钟权限：降级窗口触发（±10 分钟）
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger,
                    TimeUnit.MINUTES.toMillis(10),
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
        }
        ensureFallbackWorker()
    }

    override fun cancel(id: String) {
        val pendingIntent = alarmPendingIntentById(id)
        alarmManager.cancel(pendingIntent)
        WorkManager.getInstance(context).cancelUniqueWork(fallbackWorkName(id))
    }

    override fun restoreAll() {
        ServiceLocator.applicationScope.launch {
            val enabled = runCatching { repository.getEnabledReminders() }.getOrDefault(emptyList())
            for (reminder in enabled) {
                val planned = repository.planNextTrigger(reminder)
                if (planned != null) {
                    schedule(planned)
                }
            }
        }
        ensureFallbackWorker()
    }

    // ---------- 到点处理（ReminderReceiver 调用） ----------

    /** 到点触发：发通知 + 重排下一次（时序图 5.4 loop 体）。 */
    fun onReminderFired(reminder: ReminderEntity) {
        ServiceLocator.applicationScope.launch {
            val profile = runCatching { repository.getProfile() }.getOrNull()
            val petName = profile?.name ?: "小希"
            NotificationHelper.showReminderNotification(
                context, petName, reminder.message, reminder.id.hashCode()
            )
            // 重排下一次
            val next = repository.planNextTrigger(reminder)
            if (next != null) {
                schedule(next)
            }
        }
    }

    // ---------- 内部工具 ----------

    private fun alarmPendingIntent(config: ReminderEntity): PendingIntent =
        alarmPendingIntentById(config.id, config.message)

    private fun alarmPendingIntentById(id: String, message: String? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
            putExtra(EXTRA_REMINDER_ID, id)
            if (message != null) putExtra(EXTRA_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 兜底周期 Worker（最小 15 分钟；省电模式杀 Alarm 时补发到点提醒）。 */
    private fun ensureFallbackWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderFallbackWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FALLBACK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun fallbackWorkName(id: String): String = "$FALLBACK_WORK_NAME.$id"

    companion object {
        const val ACTION_REMINDER_FIRE = "com.mobiledesktoppet.app.action.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_MESSAGE = "extra_message"
        const val FALLBACK_WORK_NAME = "pet_reminder_fallback"
    }
}

/**
 * 提醒兜底 Worker：扫描到点未触发的提醒（nextTriggerAt ≤ now），补发通知并重排。
 */
class ReminderFallbackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.petRepository
        val now = System.currentTimeMillis()
        val due = runCatching { repository.getEnabledReminders() }
            .getOrDefault(emptyList())
            .filter { (it.nextTriggerAt ?: 0L) in 1..now }
        for (reminder in due) {
            val profile = runCatching { repository.getProfile() }.getOrNull()
            NotificationHelper.showReminderNotification(
                applicationContext,
                profile?.name ?: "小希",
                reminder.message,
                reminder.id.hashCode()
            )
            repository.planNextTrigger(reminder)
        }
        return Result.success()
    }
}
