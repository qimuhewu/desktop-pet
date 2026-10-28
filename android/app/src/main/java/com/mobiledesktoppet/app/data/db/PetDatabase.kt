package com.mobiledesktoppet.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库（表结构对照 shared/schema/pet_db_schema.sql，禁止单端加列）。
 */
@Database(
    entities = [PetProfileEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PetDatabase : RoomDatabase() {

    abstract fun petProfileDao(): PetProfileDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        /** 数据库文件名（双端一致命名）。 */
        private const val DB_NAME = "pet.db"

        /** 宠物档案固定行 id（第一版单宠物，docs/04 §2.1）。 */
        const val DEFAULT_PROFILE_ID = "default_pet"

        fun build(context: Context): PetDatabase =
            Room.databaseBuilder(context.applicationContext, PetDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
