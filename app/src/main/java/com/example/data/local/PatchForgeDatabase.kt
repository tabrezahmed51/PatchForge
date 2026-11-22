package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AuditRunEntity::class,
        AuditFinding::class,
        PendingSyncActionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PatchForgeDatabase : RoomDatabase() {
    abstract fun patchForgeDao(): PatchForgeDao

    companion object {
        @Volatile
        private var INSTANCE: PatchForgeDatabase? = null

        fun getInstance(context: Context): PatchForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PatchForgeDatabase::class.java,
                    "patchforge_ops.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
