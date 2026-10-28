package com.mobiledesktoppet.app.domain

/**
 * 提醒计划纯逻辑（与 iOS Domain/ReminderPlanner.swift 镜像实现）。
 *
 * 算法唯一事实源：docs/04-数据模型与养成规则规范.md §5。
 * 模板文案来源：shared/assets/reminder_templates.json（本类只做时间计算，不解析模板）。
 */
object ReminderPlanner {

    /** 提醒配置（对应 shared/schema/pet_db_schema.sql 的 reminder_config 表）。 */
    data class ReminderConfig(
        val id: String,
        val templateKey: String,
        val intervalMinutes: Int,
        val message: String,
        val enabled: Boolean,
        val nextTriggerAt: Long? = null
    )

    /** 间隔下限保护：0 或负间隔视为无效，返回 0（不排程）。 */
    private const val MIN_INTERVAL_MINUTES = 1

    /**
     * 计算下一次触发时间（毫秒时间戳）。
     *
     * - 未启用 → 0（无效）；
     * - nextTriggerAt 为 null（从未排程）→ from + interval；
     * - nextTriggerAt 在未来 → 保持原值（RP-02）；
     * - nextTriggerAt 已过期 → 顺延到未来第一个整周期（RP-03）。
     */
    fun nextTriggerTime(config: ReminderConfig, fromMillis: Long): Long {
        if (!config.enabled) return 0L
        if (config.intervalMinutes < MIN_INTERVAL_MINUTES) return 0L
        val intervalMs = config.intervalMinutes * 60_000L
        var next = config.nextTriggerAt ?: (fromMillis + intervalMs)
        while (next <= fromMillis) {
            next += intervalMs
        }
        return next
    }
}
