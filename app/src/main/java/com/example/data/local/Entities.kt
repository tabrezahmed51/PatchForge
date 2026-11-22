package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "audit_runs")
data class AuditRunEntity(
    @PrimaryKey val runId: String,
    val title: String,
    val filePath: String,
    val engineMode: String, // DUAL_ENGINE, SECURITY_ONLY, AGENTIC_ONLY
    val inputPayload: String,
    val modelId: String,
    val highThinkingEnabled: Boolean,
    val summary: String,
    val riskScore: Int, // 0..100
    val maxNestingDepth: Int,
    val cyclomaticPeak: Int,
    val astHotspots: String,
    val syncStatus: String, // LOCAL_ENGINE, CLOUD_SYNCED, SYNCED, QUEUED_OFFLINE
    val remoteJobId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room database entity for 'AuditFinding' mapping strictly to the required schema fields:
 * file_path, line_bounds, threat_category, severity, description, and replacement_snippet.
 */
@Entity(
    tableName = "audit_findings",
    foreignKeys = [
        ForeignKey(
            entity = AuditRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("run_id")]
)
data class AuditFinding(
    @PrimaryKey
    @ColumnInfo(name = "finding_id")
    val findingId: String,

    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "line_bounds")
    val lineBounds: String,

    @ColumnInfo(name = "threat_category")
    val threatCategory: String,

    @ColumnInfo(name = "severity")
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "replacement_snippet")
    val replacementSnippet: String,

    @ColumnInfo(name = "original_snippet")
    val originalSnippet: String = "",

    @ColumnInfo(name = "engine_type")
    val engineType: String = "AGENTIC_REVIEW", // SECURITY_ENGINE, AGENTIC_REVIEW

    @ColumnInfo(name = "package_or_symbol")
    val packageOrSymbol: String = "",

    @ColumnInfo(name = "cyclomatic_complexity")
    val cyclomaticComplexity: Int = 1,

    @ColumnInfo(name = "patch_status")
    val patchStatus: String = PatchStatus.PENDING // PENDING, APPROVED, DISMISSED, QUEUED_SYNC, APPLIED
)

object PatchStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val DISMISSED = "DISMISSED"
    const val QUEUED_SYNC = "QUEUED_SYNC"
    const val APPLIED = "APPLIED"
}

@Entity(tableName = "pending_sync_actions")
data class PendingSyncActionEntity(
    @PrimaryKey val actionId: String,
    val actionType: String, // PATCH_APPLY, REMOTE_ANALYZE
    val targetEndpoint: String,
    val targetBranch: String,
    val commitMessage: String,
    val payloadJson: String,
    val status: String, // QUEUED, RETRYING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val role: String, // user, model
    val content: String,
    val modelId: String,
    val agentPersona: String,
    val highThinkingUsed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
