package com.mobiledesktoppet.app.domain

import com.mobiledesktoppet.app.domain.ReminderPlanner.ReminderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 提醒计划镜像单测（用例编号见 tests/README.md，与 iOS ReminderPlannerTests.swift 一一对应）。
 */
class ReminderPlannerTest {

    private val config = ReminderConfig(
        id = "r1",
        templateKey = "drink_water",
        intervalMinutes = 60,
        message = "该喝水啦~",
        enabled = true
    )

    /** RP-01 新配置（nextTriggerAt=null）：next = from + interval。 */
    @Test
    fun rp01_newConfig() {
        val from = 1_000_000_000_000L
        val next = ReminderPlanner.nextTriggerTime(config, from)
        assertEquals(from + 60 * 60_000L, next)
    }

    /** RP-02 已排程且在未来：保持原 nextTriggerAt。 */
    @Test
    fun rp02_futureScheduledKept() {
        val from = 1_000_000_000_000L
        val future = from + 30 * 60_000L
        val next = ReminderPlanner.nextTriggerTime(config.copy(nextTriggerAt = future), from)
        assertEquals(future, next)
    }

    /** RP-03 已过期：顺延到未来第一个整周期。 */
    @Test
    fun rp03_expiredAdvanced() {
        val from = 1_000_000_000_000L
        val intervalMs = 60 * 60_000L
        // nextTriggerAt = from - 2.5h → 顺延 3 个整周期到 from + 0.5h
        // （QA 严过关修正：原断言误写 from + intervalMs，即 from + 1h，与顺延结果 from + 0.5h 不符）
        val expired = from - intervalMs * 2 - 30 * 60_000L
        val next = ReminderPlanner.nextTriggerTime(config.copy(nextTriggerAt = expired), from)
        assertEquals(from + 30 * 60_000L, next)
    }

    /** RP-04 enabled=false → 0（无效）。 */
    @Test
    fun rp04_disabledReturnsZero() {
        val next = ReminderPlanner.nextTriggerTime(config.copy(enabled = false), 1_000_000_000_000L)
        assertEquals(0L, next)
    }

    /** 附加：间隔下限保护（0 分钟视为无效）。 */
    @Test
    fun zeroIntervalInvalid() {
        val next = ReminderPlanner.nextTriggerTime(config.copy(intervalMinutes = 0), 1_000_000_000_000L)
        assertEquals(0L, next)
    }
}
