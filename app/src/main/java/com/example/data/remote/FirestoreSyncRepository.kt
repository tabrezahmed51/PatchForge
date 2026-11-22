package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.AuditFinding
import com.example.data.local.AuditRunEntity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudAuditRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val filePath: String = "",
    val engineMode: String = "DUAL_ENGINE",
    val modelId: String = "",
    val summary: String = "",
    val riskScore: Long = 0L,
    val cyclomaticPeak: Long = 1L,
    val maxNestingDepth: Long = 0L,
    val findingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAuditFinding(
    @DocumentId val id: String = "",
    val userId: String = "",
    val runId: String = "",
    @get:PropertyName("file_path") @PropertyName("file_path") val filePath: String = "",
    @get:PropertyName("line_bounds") @PropertyName("line_bounds") val lineBounds: String = "",
    @get:PropertyName("threat_category") @PropertyName("threat_category") val threatCategory: String = "SEMANTIC_LOGIC",
    val severity: String = "MEDIUM",
    val description: String = "",
    @get:PropertyName("replacement_snippet") @PropertyName("replacement_snippet") val replacementSnippet: String = "",
    @get:PropertyName("original_snippet") @PropertyName("original_snippet") val originalSnippet: String = "",
    @get:PropertyName("engine_type") @PropertyName("engine_type") val engineType: String = "AGENTIC_REVIEW",
    @get:PropertyName("patch_status") @PropertyName("patch_status") val patchStatus: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Always initialize with the named enterprise database ID from firebase_applet_config.xml
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Firestore."
        }
        return uid
    }

    suspend fun ensureUserProfile(displayName: String, email: String?): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val safeName = displayName.trim().ifBlank { "Operator" }.take(80)
            val safeEmail = email?.trim()?.take(120)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!safeEmail.isNullOrBlank()) {
                    updateMap["email"] = safeEmail
                }
                docRef.update(updateMap).await()
            } else {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!safeEmail.isNullOrBlank()) {
                    createMap["email"] = safeEmail
                }
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeCloudAuditRuns(userId: String): Flow<List<CloudAuditRun>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for cloud audit runs." }
        val path = "users/$uid/audit_runs"
        emitAll(
            db.collection("users").document(uid).collection("audit_runs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAuditRun::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudFindings(userId: String): Flow<List<CloudAuditFinding>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for cloud audit findings." }
        val path = "users/$uid/audit_findings"
        emitAll(
            db.collection("users").document(uid).collection("audit_findings")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAuditFinding::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncAuditRunAndFindings(
        run: AuditRunEntity,
        findings: List<AuditFinding>
    ): Result<Unit> {
        var currentPath = "users/unauthenticated/audit_runs/${run.runId}"
        return try {
            val uid = requireUserId()
            val runRef = db.collection("users").document(uid)
                .collection("audit_runs").document(run.runId)
            currentPath = runRef.path

            val safeEngineMode = if (run.engineMode in ALLOWED_ENGINE_MODES) run.engineMode else "DUAL_ENGINE"
            val runPayload = mapOf(
                "userId" to uid,
                "title" to run.title.ifBlank { "audit" }.take(120),
                "filePath" to run.filePath.ifBlank { "unknown" }.take(300),
                "engineMode" to safeEngineMode,
                "modelId" to run.modelId.ifBlank { "gemini-4-hydrogen" }.take(80),
                "summary" to run.summary.take(4000),
                "riskScore" to run.riskScore.coerceIn(0, 100),
                "cyclomaticPeak" to run.cyclomaticPeak.coerceIn(0, 1000),
                "maxNestingDepth" to run.maxNestingDepth.coerceIn(0, 100),
                "findingCount" to findings.size.coerceIn(0, 500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            runRef.set(runPayload).await()

            for (finding in findings) {
                val fRef = db.collection("users").document(uid)
                    .collection("audit_findings").document(finding.findingId)
                currentPath = fRef.path
                val safeCategory = if (finding.threatCategory in ALLOWED_CATEGORIES) finding.threatCategory else "SEMANTIC_LOGIC"
                val safeSeverity = if (finding.severity in ALLOWED_SEVERITIES) finding.severity else "MEDIUM"
                val safeEngine = if (finding.engineType in ALLOWED_ENGINES) finding.engineType else "AGENTIC_REVIEW"
                val safePatchStatus = if (finding.patchStatus in ALLOWED_PATCH_STATUSES) finding.patchStatus else "PENDING"

                val findingPayload = mapOf(
                    "userId" to uid,
                    "runId" to finding.runId.take(128),
                    "file_path" to finding.filePath.ifBlank { "unknown" }.take(300),
                    "line_bounds" to finding.lineBounds.ifBlank { "L1-L1" }.take(64),
                    "threat_category" to safeCategory,
                    "severity" to safeSeverity,
                    "description" to finding.description.ifBlank { "Finding detected" }.take(2000),
                    "replacement_snippet" to finding.replacementSnippet.take(8000),
                    "original_snippet" to finding.originalSnippet.take(8000),
                    "engine_type" to safeEngine,
                    "patch_status" to safePatchStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                fRef.set(findingPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun updateCloudFindingStatus(findingId: String, status: String): Result<Unit> {
        var docPath = "users/unauthenticated/audit_findings/$findingId"
        return try {
            val uid = requireUserId()
            require(findingId.isNotBlank()) { "Finding ID must not be blank." }
            val safeStatus = if (status in ALLOWED_PATCH_STATUSES) status else "PENDING"
            val docRef = db.collection("users").document(uid)
                .collection("audit_findings").document(findingId)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "patch_status" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private val ALLOWED_ENGINE_MODES = setOf("DUAL_ENGINE", "SECURITY_ONLY", "AGENTIC_ONLY")
        private val ALLOWED_CATEGORIES = setOf(
            "TYPOSQUATTING", "INSTALL_SCRIPT", "TELEMETRY_EXFIL",
            "ABANDONED_PKG", "CVE_VULNERABILITY", "AST_COMPLEXITY",
            "SEMANTIC_LOGIC", "LINT_CONFIG"
        )
        private val ALLOWED_SEVERITIES = setOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
        private val ALLOWED_ENGINES = setOf("SECURITY_ENGINE", "AGENTIC_REVIEW")
        private val ALLOWED_PATCH_STATUSES = setOf("PENDING", "APPROVED", "DISMISSED", "QUEUED_SYNC", "APPLIED")
    }
}
