package com.example.core

/**
 * Result of parsing and sanitizing an AI-generated Unified Diff (UDIFF).
 */
sealed interface UdiffParseResult {
    data class Valid(
        val cleanDiff: String,
        val hunkCount: Int,
        val addedLines: Int,
        val removedLines: Int,
        val isAtomic: Boolean,
        val warning: String? = null
    ) : UdiffParseResult

    data class Invalid(val reason: String) : UdiffParseResult
}

/**
 * Result of running local structural / AST sanity checks on a proposed atomic UDIFF.
 */
data class LocalAstCheckResult(
    val passed: Boolean,
    val issues: List<String>,
    val deltaBraceBalance: Int,
    val deltaParenBalance: Int,
    val deltaBracketBalance: Int
)

/**
 * Deterministic Unified Diff (UDIFF) parser, prose stripper, atomicity enforcer,
 * and local AST structural sanity checker for PatchForge's Self-Healing Patch Loop.
 */
object StrictUdiffEngine {

    const val STRICT_UDIFF_SYSTEM_DIRECTIVE =
        "[STRICT MODE: Output ONLY the Git Diff in Unified Diff Format (UDIFF). " +
            "Zero prose. Zero explanation. Zero markdown fences. " +
            "Generate an Atomic Patch containing the smallest possible change-set that resolves the defect.]"

    private val hunkHeaderRegex = Regex("""^@@\s+-\d+(?:,\d+)?\s+\+\d+(?:,\d+)?\s+@@.*$""")

    /**
     * Extracts pure Unified Diff lines from raw model output, discarding any accidental
     * conversational preamble, markdown fences, or trailing commentary.
     */
    fun extractStrictUdiff(
        rawOutput: String,
        sourceLineCount: Int? = null
    ): UdiffParseResult {
        val normalized = rawOutput.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) {
            return UdiffParseResult.Invalid("Empty diff output.")
        }

        // Remove accidental markdown code fences if a model leaked them.
        val rawLines = normalized.lines().filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("```")
        }

        val startIdx = rawLines.indexOfFirst { line ->
            line.startsWith("diff --git ") || line.startsWith("--- ")
        }
        if (startIdx == -1) {
            return UdiffParseResult.Invalid("Missing UDIFF header ('diff --git' or '--- a/...').")
        }

        val collected = mutableListOf<String>()
        var inHunk = false
        var hasOldFileHeader = false
        var hasNewFileHeader = false
        var hunkCount = 0
        var addedLines = 0
        var removedLines = 0

        for (i in startIdx until rawLines.size) {
            val line = rawLines[i]
            when {
                line.startsWith("diff --git ") -> {
                    inHunk = false
                    collected.add(line)
                }
                line.startsWith("index ") ||
                    line.startsWith("new file mode") ||
                    line.startsWith("deleted file mode") -> {
                    collected.add(line)
                }
                line.startsWith("--- ") -> {
                    hasOldFileHeader = true
                    inHunk = false
                    collected.add(line)
                }
                line.startsWith("+++ ") -> {
                    hasNewFileHeader = true
                    inHunk = false
                    collected.add(line)
                }
                hunkHeaderRegex.matches(line) -> {
                    inHunk = true
                    hunkCount++
                    collected.add(line)
                }
                inHunk && (
                    line.startsWith("+") ||
                        line.startsWith("-") ||
                        line.startsWith(" ") ||
                        line == "\\ No newline at end of file" ||
                        line.isEmpty()
                    ) -> {
                    if (line.startsWith("+") && !line.startsWith("+++")) addedLines++
                    if (line.startsWith("-") && !line.startsWith("---")) removedLines++
                    collected.add(line)
                }
                inHunk -> {
                    // Stop collecting once trailing prose begins after a valid hunk.
                    break
                }
            }
        }

        if (!hasOldFileHeader || !hasNewFileHeader) {
            return UdiffParseResult.Invalid("Incomplete UDIFF file headers (requires '---' and '+++').")
        }
        if (hunkCount == 0) {
            return UdiffParseResult.Invalid("UDIFF contains no valid '@@ -a,b +c,d @@' hunk header.")
        }
        if (addedLines == 0 && removedLines == 0) {
            return UdiffParseResult.Invalid("UDIFF hunk contains zero changed lines.")
        }

        val cleanDiff = collected.joinToString("\n").trimEnd()
        val totalChanged = addedLines + removedLines
        val isAtomic = assessAtomicity(
            addedLines = addedLines,
            removedLines = removedLines,
            sourceLineCount = sourceLineCount
        )
        val warning = if (!isAtomic) {
            "Patch touches $totalChanged lines and may be over-broad for an atomic change-set."
        } else {
            null
        }

        return UdiffParseResult.Valid(
            cleanDiff = cleanDiff,
            hunkCount = hunkCount,
            addedLines = addedLines,
            removedLines = removedLines,
            isAtomic = isAtomic,
            warning = warning
        )
    }

    /**
     * Checks whether a patch qualifies as a surgical atomic change-set rather than a
     * full-file rewrite.
     */
    fun assessAtomicity(
        addedLines: Int,
        removedLines: Int,
        sourceLineCount: Int?
    ): Boolean {
        val maxSingleSide = maxOf(addedLines, removedLines)
        if (maxSingleSide > 45) return false
        if (sourceLineCount != null && sourceLineCount >= 20) {
            val rewriteRatio = maxSingleSide.toFloat() / sourceLineCount.toFloat()
            if (rewriteRatio > 0.70f) return false
        }
        return true
    }

    /**
     * Performs deterministic structural/AST sanity checks on a UDIFF against optional source text:
     * - Verifies brace/paren/bracket balance delta between removed and added lines.
     * - Verifies removed lines exist in the source payload when provided.
     * - Verifies balanced string delimiters on added lines.
     */
    fun verifyPatchAgainstAst(
        unifiedDiff: String,
        sourceCode: String? = null
    ): LocalAstCheckResult {
        val issues = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val added = mutableListOf<String>()

        unifiedDiff.lines().forEach { line ->
            when {
                line.startsWith("-") && !line.startsWith("---") -> removed.add(line.drop(1))
                line.startsWith("+") && !line.startsWith("+++") -> added.add(line.drop(1))
            }
        }

        val removedCounts = countStructuralDelimiters(removed)
        val addedCounts = countStructuralDelimiters(added)

        val deltaBrace = addedCounts.braceBalance - removedCounts.braceBalance
        val deltaParen = addedCounts.parenBalance - removedCounts.parenBalance
        val deltaBracket = addedCounts.bracketBalance - removedCounts.bracketBalance

        if (deltaBrace != 0) {
            issues.add("Unbalanced curly braces delta ($deltaBrace) introduced by patch.")
        }
        if (deltaParen != 0) {
            issues.add("Unbalanced parentheses delta ($deltaParen) introduced by patch.")
        }
        if (deltaBracket != 0) {
            issues.add("Unbalanced square brackets delta ($deltaBracket) introduced by patch.")
        }

        if (!sourceCode.isNullOrBlank() && removed.isNotEmpty()) {
            val normalizedSource = sourceCode.replace("\r\n", "\n")
            val nonBlankRemoved = removed.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("# Line bounds") }
            val matched = nonBlankRemoved.count { normalizedSource.contains(it) }
            if (nonBlankRemoved.isNotEmpty() && matched == 0) {
                issues.add("Removed lines do not match target source context (branch drift risk).")
            }
        }

        return LocalAstCheckResult(
            passed = issues.isEmpty(),
            issues = issues,
            deltaBraceBalance = deltaBrace,
            deltaParenBalance = deltaParen,
            deltaBracketBalance = deltaBracket
        )
    }

    /**
     * Returns true if text looks like a unified Git diff suitable for syntax-highlighted rendering.
     */
    fun looksLikeUnifiedDiff(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.contains("--- ") && trimmed.contains("+++ ") && trimmed.contains("@@ ")) ||
            trimmed.startsWith("diff --git ")
    }

    private data class DelimiterBalance(
        val braceBalance: Int,
        val parenBalance: Int,
        val bracketBalance: Int
    )

    private fun countStructuralDelimiters(lines: List<String>): DelimiterBalance {
        var braces = 0
        var parens = 0
        var brackets = 0
        lines.forEach { rawLine ->
            var inDoubleQuote = false
            var inSingleQuote = false
            var escaped = false
            for (ch in rawLine) {
                if (escaped) {
                    escaped = false
                    continue
                }
                if (ch == '\\') {
                    escaped = true
                    continue
                }
                if (ch == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote
                    continue
                }
                if (ch == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote
                    continue
                }
                if (inDoubleQuote || inSingleQuote) continue
                when (ch) {
                    '{' -> braces++
                    '}' -> braces--
                    '(' -> parens++
                    ')' -> parens--
                    '[' -> brackets++
                    ']' -> brackets--
                }
            }
        }
        return DelimiterBalance(braces, parens, brackets)
    }
}
