package com.mobiledesktoppet.app

import android.app.Application
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.notification.NotificationHelper

/**
 * Application 入口：ServiceLocator 初始化 + 通知渠道统一创建
 * （pet_overlay 常驻静默 / pet_reminder 提醒默认重要度，架构文档 §8 共享知识 #12）。
 */
class PetApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.ensureChannels(this)
    }
}
