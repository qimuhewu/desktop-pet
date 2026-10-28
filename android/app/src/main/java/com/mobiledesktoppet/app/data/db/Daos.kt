package com.mobiledesktoppet.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 宠物档案 DAO（单行表：固定 id = default_pet）。 */
@Dao
interface PetProfileDao {

    @Query("SELECT * FROM pet_profile WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PetProfileEntity?

    @Query("SELECT * FROM pet_profile WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PetProfileEntity?>

    @Upsert
    suspend fun upsert(profile: PetProfileEntity)
}

/** 提醒配置 DAO。 */
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminder_config ORDER BY next_trigger_at IS NULL, next_trigger_at ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminder_config WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder_config WHERE enabled = 1")
    suspend fun getEnabled(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReminderEntity)

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Query("DELETE FROM reminder_config WHERE id = :id")
    suspend fun deleteById(id: String)
}
