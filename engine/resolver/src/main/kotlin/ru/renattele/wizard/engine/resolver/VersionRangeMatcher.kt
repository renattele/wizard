package ru.renattele.wizard.engine.resolver

internal object VersionRangeMatcher {
    fun matches(range: String?, version: String): Boolean {
        if (range.isNullOrBlank()) return true
        val normalized = range.trim()

        if (normalized.contains("x", ignoreCase = true)) {
            val prefix = normalized.substringBefore("x").trimEnd('.')
            return version.startsWith(prefix)
        }

        if (normalized.startsWith(">") || normalized.startsWith("<") || normalized.startsWith("=")) {
            return normalized
                .split(",", " ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .all { token -> matchComparator(token, version) }
        }

        return normalized == version
    }

    fun looksLikeSupportedRange(range: String): Boolean {
        val trimmed = range.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.contains("x", ignoreCase = true)) return true
        if (trimmed.startsWith(">") || trimmed.startsWith("<") || trimmed.startsWith("=")) return true
        return Regex("\\d+(\\.\\d+){0,2}").matches(trimmed)
    }

    private fun matchComparator(token: String, version: String): Boolean {
        val comparator = when {
            token.startsWith(">=") -> ">="
            token.startsWith("<=") -> "<="
            token.startsWith(">") -> ">"
            token.startsWith("<") -> "<"
            token.startsWith("=") -> "="
            else -> "="
        }

        val expected = token.removePrefix(comparator)
        val left = SemanticVersion.parse(version)
        val right = SemanticVersion.parse(expected)

        return when (comparator) {
            ">=" -> left >= right
            "<=" -> left <= right
            ">" -> left > right
            "<" -> left < right
            else -> left == right
        }
    }
}

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        fun parse(raw: String): SemanticVersion {
            val cleaned = raw.trim().substringBefore('-').substringBefore('+')
            val parts = cleaned.split('.')
            return SemanticVersion(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
    }
}
