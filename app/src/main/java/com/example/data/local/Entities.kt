package com.example.data.local

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
    val syncStatus: String, // LOCAL_ENGINE, SYNCED, QUEUED_OFFLINE
    val remoteJobId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "findings",
    foreignKeys = [
        ForeignKey(
            entity = AuditRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class FindingEntity(
    @PrimaryKey val findingId: String,
    val runId: String,
    // Strictly enforced structured schema fields
    val filePath: String,
    val lineBounds: String,
    val threatCategory: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW
    val description: String,
    val replacementSnippet: String,
    // Supplemental operational metadata
    val originalSnippet: String,
    val engineType: String, // SECURITY_ENGINE, AGENTIC_REVIEW
    val packageOrSymbol: String,
    val cyclomaticComplexity: Int,
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
