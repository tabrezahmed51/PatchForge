package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchForgeDao {
    // Audit Runs
    @Query("SELECT * FROM audit_runs ORDER BY createdAt DESC")
    fun observeAuditRuns(): Flow<List<AuditRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditRun(run: AuditRunEntity)

    @Query("UPDATE audit_runs SET syncStatus = :syncStatus, remoteJobId = :remoteJobId WHERE runId = :runId")
    suspend fun updateAuditRunSync(runId: String, syncStatus: String, remoteJobId: String?)

    // AuditFinding records
    @Query("SELECT * FROM audit_findings WHERE run_id = :runId ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END")
    fun observeFindingsForRun(runId: String): Flow<List<AuditFinding>>

    @Query("SELECT * FROM audit_findings ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END")
    fun observeAllFindings(): Flow<List<AuditFinding>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<AuditFinding>)

    @Query("UPDATE audit_findings SET patch_status = :status WHERE id = :findingId")
    suspend fun updateFindingPatchStatus(findingId: String, status: String)

    // Offline Sync & Retry Queue
    @Query("SELECT * FROM pending_sync_actions ORDER BY createdAt DESC")
    fun observePendingSyncActions(): Flow<List<PendingSyncActionEntity>>

    @Query("SELECT * FROM pending_sync_actions WHERE status IN ('QUEUED', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getRetryableSyncActions(): List<PendingSyncActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncAction(action: PendingSyncActionEntity)

    @Query("UPDATE pending_sync_actions SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE actionId = :actionId")
    suspend fun updateSyncActionStatus(
        actionId: String,
        status: String,
        retryCount: Int,
        lastError: String?
    )

    @Query("DELETE FROM pending_sync_actions WHERE status = 'COMPLETED'")
    suspend fun purgeCompletedSyncActions()

    @Query("DELETE FROM pending_sync_actions WHERE actionId = :actionId")
    suspend fun deleteSyncAction(actionId: String)

    // Multi-turn Chat History
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getChatMessagesSnapshot(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()
}
