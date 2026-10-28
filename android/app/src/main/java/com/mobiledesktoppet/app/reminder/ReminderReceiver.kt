package com.mobiledesktoppet.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobiledesktoppet.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * 提醒到点广播接收器（时序图 5.4）：
 * 取宠物快照 → 发宠物形象通知 → 重排下一次 Alarm。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderSchedulerImpl.ACTION_REMINDER_FIRE) return
        val reminderId = intent.getStringExtra(ReminderSchedulerImpl.EXTRA_REMINDER_ID) ?: return

        if (!ServiceLocator.isInitialized) {
            ServiceLocator.init(context.applicationContext)
        }

        // goAsync：协程查库后触发通知与重排
        val pendingResult = goAsync()
        ServiceLocator.applicationScope.launch {
            try {
                val repository = ServiceLocator.petRepository
                val reminder = runCatching { repository.getReminders() }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.id == reminderId }
                if (reminder != null && reminder.enabled) {
                    val scheduler = ReminderSchedulerImpl(context.applicationContext)
                    scheduler.onReminderFired(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
