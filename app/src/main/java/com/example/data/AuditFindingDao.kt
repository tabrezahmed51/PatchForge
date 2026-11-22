package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.AuditFinding
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for CRUD operations on [AuditFinding] entities.
 * All queries use parameterized Room bindings to prevent SQL injection.
 */
@Dao
interface AuditFindingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(finding: AuditFinding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(findings: List<AuditFinding>)

    @Query(
        """
        SELECT * FROM audit_findings
        ORDER BY CASE severity
            WHEN 'CRITICAL' THEN 0
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            ELSE 3
        END, id ASC
        """
    )
    fun getAllFindings(): Flow<List<AuditFinding>>

    @Query(
        """
        SELECT * FROM audit_findings
        WHERE run_id = :runId
        ORDER BY CASE severity
            WHEN 'CRITICAL' THEN 0
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            ELSE 3
        END, id ASC
        """
    )
    fun getFindingsByRunId(runId: String): Flow<List<AuditFinding>>

    @Query("SELECT * FROM audit_findings WHERE severity = :severity ORDER BY id ASC")
    fun getFindingsBySeverity(severity: String): Flow<List<AuditFinding>>

    @Query("SELECT * FROM audit_findings WHERE id = :id LIMIT 1")
    suspend fun getFindingById(id: String): AuditFinding?

    @Query("UPDATE audit_findings SET patch_status = :status WHERE id = :id")
    suspend fun updatePatchStatus(id: String, status: String)

    @Delete
    suspend fun delete(finding: AuditFinding)

    @Query("DELETE FROM audit_findings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM audit_findings")
    suspend fun deleteAll()
}
