package com.example.data.repository

import com.example.data.local.AuditFinding

object PatchGenerator {

    fun buildUnifiedDiff(finding: AuditFinding): String {
        val cleanPath = finding.filePath.trim().removePrefix("/")
        val startLine = parseStartLine(finding.lineBounds)
        val removedLines = finding.originalSnippet
            .takeIf { it.isNotBlank() }
            ?.lines()
            ?: listOf("# Line bounds ${finding.lineBounds} (${finding.packageOrSymbol})")
        val addedLines = finding.replacementSnippet
            .takeIf { it.isNotBlank() }
            ?.lines()
            ?: emptyList()

        val oldCount = removedLines.size.coerceAtLeast(1)
        val newCount = addedLines.size.coerceAtLeast(1)

        return buildString {
            appendLine("diff --git a/$cleanPath b/$cleanPath")
            appendLine("--- a/$cleanPath")
            appendLine("+++ b/$cleanPath")
            appendLine("@@ -$startLine,$oldCount +$startLine,$newCount @@ ${finding.threatCategory} [${finding.severity}]")
            removedLines.forEach { line ->
                appendLine("-$line")
            }
            addedLines.forEach { line ->
                appendLine("+$line")
            }
        }.trimEnd()
    }

    fun buildCombinedPatchBundle(findings: List<AuditFinding>): String {
        if (findings.isEmpty()) return ""
        return findings.joinToString(separator = "\n\n") { buildUnifiedDiff(it) }
    }

    private fun parseStartLine(lineBounds: String): Int {
        val digits = lineBounds
            .substringBefore("-")
            .filter { it.isDigit() }
        return digits.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }
}
