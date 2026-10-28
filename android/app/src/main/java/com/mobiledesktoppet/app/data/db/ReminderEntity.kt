package com.mobiledesktoppet.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提醒配置表实体（对照 shared/schema/pet_db_schema.sql 的 reminder_config 表，逐列一致）。
 * 列名显式映射为 schema 的 snake_case（Room 默认用 camelCase 字段名作列名，会导致
 * DAO 手写 SQL 中的 next_trigger_at 等列找不到）。
 * enabled 以 Integer 存储（true=1）。
 */
@Entity(tableName = "reminder_config")
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_key") val templateKey: String,
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "next_trigger_at") val nextTriggerAt: Long?
)
