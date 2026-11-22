package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuditFinding
import com.example.data.repository.PatchGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PatchForge", appName)
    }

    @Test
    fun `patch generator emits unified git diff with strict AuditFinding fields`() {
        val finding = AuditFinding(
            id = "RUN-1-F1",
            runId = "RUN-1",
            filePath = "services/web/package.json",
            lineBounds = "L10-L10",
            threatCategory = "TYPOSQUATTING",
            severity = "CRITICAL",
            description = "Typosquatted package lodahs detected.",
            replacementSnippet = "\"lodash\": \"^4.17.21\",",
            originalSnippet = "\"lodahs\": \"4.17.21\",",
            engineType = "SECURITY_ENGINE",
            packageOrSymbol = "lodahs",
            cyclomaticComplexity = 1
        )
        val diff = PatchGenerator.buildUnifiedDiff(finding)
        assertTrue(diff.contains("--- a/services/web/package.json"))
        assertTrue(diff.contains("+++ b/services/web/package.json"))
        assertTrue(diff.contains("@@ -10,1 +10,1 @@ TYPOSQUATTING [CRITICAL]"))
        assertTrue(diff.contains("-\"lodahs\": \"4.17.21\","))
        assertTrue(diff.contains("+\"lodash\": \"^4.17.21\","))
    }
}
