package com.example.data

import android.content.Context
import androidx.room.Room
import com.example.data.local.PatchForgeDao
import dagger.hilt.EntryPoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the singleton Room [AppDatabase] and DAO bindings
 * for storing and querying PatchForge audit findings and operational telemetry.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideAuditFindingDao(database: AppDatabase): AuditFindingDao {
        return database.auditFindingDao()
    }

    @Provides
    fun providePatchForgeDao(database: AppDatabase): PatchForgeDao {
        return database.patchForgeDao()
    }
}

/**
 * Hilt EntryPoint allowing user-scoped ViewModel factories (keyed by Firebase UID)
 * to retrieve the singleton Hilt-managed Room database and DAOs.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun appDatabase(): AppDatabase
    fun auditFindingDao(): AuditFindingDao
    fun patchForgeDao(): PatchForgeDao
}
