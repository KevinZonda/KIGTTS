package com.lhtstudio.kigtts.app.service

internal object PttTranscriptMerger {
    private val comparisonIgnoredCharacters = setOf(
        '，', '。', '！', '？', '；', '：', '、',
        ',', '.', '!', '?', ';', ':',
        ' ', '\t', '\r', '\n'
    )

    fun merge(existing: String, incoming: String): String {
        val a = existing.trim()
        val b = incoming.trim()
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        if (a == b) return a

        val aKey = comparisonKey(a)
        val bKey = comparisonKey(b)
        if (aKey == bKey) return preferRicherText(a, b)
        if (bKey.startsWith(aKey)) return b
        if (aKey.startsWith(bKey)) return a
        if (aKey.contains(bKey)) return a
        if (bKey.contains(aKey)) return b

        val overlapMax = minOf(aKey.length, bKey.length)
        for (length in overlapMax downTo 1) {
            if (aKey.regionMatches(aKey.length - length, bKey, 0, length, ignoreCase = false)) {
                val incomingTail = b.substring(indexAfterComparisonCharacters(b, length))
                return stitch(a, incomingTail)
            }
        }
        return stitch(a, b)
    }

    fun mergeRolling(existing: String, incoming: String): String {
        val cleanedIncoming = collapseAdjacentRollingClauses(incoming.trim())
        if (cleanedIncoming.isEmpty()) return existing.trim()
        return collapseAdjacentRollingClauses(merge(existing, cleanedIncoming))
    }

    fun updateListeningPreview(existing: String, incoming: String): String {
        val next = collapseAdjacentRollingClauses(incoming.trim())
        return if (next.isNotEmpty()) next else collapseAdjacentRollingClauses(existing.trim())
    }

    fun finalizeListeningCaption(streaming: String, finalResult: String): String {
        val finalized = collapseAdjacentRollingClauses(finalResult.trim())
        return if (finalized.isNotEmpty()) {
            finalized
        } else {
            collapseAdjacentRollingClauses(streaming.trim())
        }
    }

    fun isSameRollingUtterance(first: String, second: String): Boolean {
        val firstKey = comparisonKey(first)
        val secondKey = comparisonKey(second)
        if (firstKey.isEmpty() || secondKey.isEmpty()) return false
        if (firstKey == secondKey) return true
        return minOf(firstKey.length, secondKey.length) >= 2 &&
            (firstKey.contains(secondKey) || secondKey.contains(firstKey))
    }

    private fun collapseAdjacentRollingClauses(text: String): String {
        if (text.isBlank()) return ""
        val clauses = rollingClausePattern.findAll(text).mapNotNull { match ->
            val content = match.groupValues[1].trim()
            if (content.isEmpty()) null else RollingClause(content, match.groupValues[2])
        }.toMutableList()
        if (clauses.size < 2) return text.trim()

        val collapsed = mutableListOf<RollingClause>()
        clauses.forEach { incomingClause ->
            val previous = collapsed.lastOrNull()
            if (previous == null) {
                collapsed += incomingClause
                return@forEach
            }
            val previousKey = comparisonKey(previous.text)
            val incomingKey = comparisonKey(incomingClause.text)
            when {
                previousKey == incomingKey -> {
                    collapsed[collapsed.lastIndex] = previous.copy(
                        punctuation = richerPunctuation(previous.punctuation, incomingClause.punctuation)
                    )
                }
                previousKey.length >= 2 && previousKey.contains(incomingKey) -> {
                    collapsed[collapsed.lastIndex] = previous.copy(
                        punctuation = richerPunctuation(previous.punctuation, incomingClause.punctuation)
                    )
                }
                incomingKey.length >= 2 && incomingKey.contains(previousKey) -> {
                    collapsed[collapsed.lastIndex] = incomingClause
                }
                else -> collapsed += incomingClause
            }
        }
        return collapsed.joinToString(separator = "") { clause -> clause.text + clause.punctuation }
            .trim()
    }

    private fun richerPunctuation(existing: String, incoming: String): String =
        if (incoming.length >= existing.length) incoming else existing

    private data class RollingClause(val text: String, val punctuation: String)

    private val rollingClausePattern = Regex("([^，。！？；、,.!?;:\\n]+)([，。！？；、,.!?;:\\n]*)")

    private fun comparisonKey(text: String): String = buildString(text.length) {
        text.forEach { character ->
            if (character !in comparisonIgnoredCharacters) append(character)
        }
    }

    private fun preferRicherText(existing: String, incoming: String): String {
        val existingMarks = existing.count { it in comparisonIgnoredCharacters && !it.isWhitespace() }
        val incomingMarks = incoming.count { it in comparisonIgnoredCharacters && !it.isWhitespace() }
        return if (incomingMarks >= existingMarks) incoming else existing
    }

    private fun indexAfterComparisonCharacters(text: String, count: Int): Int {
        var consumed = 0
        text.forEachIndexed { index, character ->
            if (character !in comparisonIgnoredCharacters) {
                consumed++
                if (consumed == count) return index + 1
            }
        }
        return text.length
    }

    private fun stitch(existing: String, incoming: String): String {
        if (incoming.isEmpty()) return existing
        var left = existing
        var right = incoming
        val leftBoundary = left.lastOrNull()
        val rightBoundary = right.firstOrNull()
        if (leftBoundary != null && rightBoundary != null &&
            leftBoundary.isComparisonPunctuation() && rightBoundary.isComparisonPunctuation()
        ) {
            left = left.trimEnd { it.isComparisonPunctuation() || it.isWhitespace() }
        }
        if (left.isEmpty()) return right.trim()
        if (right.isEmpty()) return left.trim()
        val separator = if (left.last().isAsciiWordCharacter() && right.first().isAsciiWordCharacter()) " " else ""
        return (left + separator + right).trim()
    }

    private fun Char.isComparisonPunctuation(): Boolean =
        this in comparisonIgnoredCharacters && !isWhitespace()

    private fun Char.isAsciiWordCharacter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

}
