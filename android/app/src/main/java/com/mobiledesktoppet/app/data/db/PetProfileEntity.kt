package com.mobiledesktoppet.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 宠物档案表实体（对照 shared/schema/pet_db_schema.sql 的 pet_profile 表，逐列一致）。
 * hunger/mood/intimacy 用 Double 连续结算（docs/04 §1.5），展示时取整。
 */
@Entity(tableName = "pet_profile")
data class PetProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val level: Int,
    val exp: Int,
    val hunger: Double,
    val mood: Double,
    val intimacy: Double,
    val lastActiveAt: Long,
    val createdAt: Long
)
