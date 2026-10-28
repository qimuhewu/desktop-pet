package com.mobiledesktoppet.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobiledesktoppet.app.di.ServiceLocator

/**
 * 开机重排提醒（RECEIVE_BOOT_COMPLETED；时序图 5.4 注记）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ServiceLocator.isInitialized) {
            ServiceLocator.init(context.applicationContext)
        }
        ReminderSchedulerImpl(context.applicationContext).restoreAll()
    }
}
