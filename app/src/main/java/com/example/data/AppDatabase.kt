package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.AuditFinding
import com.example.data.local.AuditRunEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.PatchForgeDao
import com.example.data.local.PendingSyncActionEntity

/**
 * Primary Room database for PatchForge in the `data` package.
 * Preserves existing tables (`audit_runs`, `audit_findings`, `pending_sync_actions`, `chat_messages`)
 * and exposes both [AuditFindingDao] and [PatchForgeDao].
 */
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
abstract class AppDatabase : RoomDatabase() {

    abstract fun auditFindingDao(): AuditFindingDao

    abstract fun patchForgeDao(): PatchForgeDao

    companion object {
        const val DATABASE_NAME = "patchforge_ops.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * In-memory builder for deterministic JVM / Robolectric unit tests.
         */
        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
