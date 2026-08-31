package spam.blocker.service.ai

import spam.blocker.def.Def

object SmsAiPrompt {

    const val DEFAULT = Def.DEFAULT_SMS_AI_PROMPT

    data class PromptCategory(
        val name: String,
        val description: String = "",
    )

    fun formatList(categories: List<PromptCategory>): String {
        val meanings = categories.joinToString("\n") { cat ->
            val n = cat.name.trim()
            val d = cat.description.trim()
            if (d.isEmpty()) n else "$n: $d"
        }
        val names = categories.joinToString(", ") { it.name.trim() }.trim()
        return if (names.isEmpty()) {
            meanings
        } else {
            "$meanings\n\nPick one of: $names"
        }
    }

    fun quoteSms(smsContent: String): String {
        if (smsContent.isEmpty()) {
            return ">"
        }
        return smsContent.lineSequence().joinToString("\n") { "> $it" }
    }

    fun format(template: String, categories: List<PromptCategory>, smsContent: String): String {
        val list = formatList(categories)
        val quoted = quoteSms(smsContent)
        var remaining = template
        val first = remaining.indexOf("{}")
        if (first >= 0) {
            remaining = remaining.substring(0, first) + list + remaining.substring(first + 2)
        }
        val second = remaining.indexOf("{}")
        if (second >= 0) {
            remaining = remaining.substring(0, second) + quoted + remaining.substring(second + 2)
        }
        return remaining
    }

    fun normalizeReply(raw: String): String {
        return raw.lineSequence()
            .map {
                it.trim()
                    .trimStart('-', '*', '•')
                    .trim()
                    .trim('"', '\'', '`', '.', ',', ':', ';')
                    .removePrefix("Category:")
                    .trim()
            }
            .firstOrNull { it.isNotEmpty() }
            ?: ""
    }

    fun matchCategory(reply: String?, categories: List<String>): String? {
        if (reply.isNullOrBlank() || categories.isEmpty()) {
            return null
        }
        val normalized = normalizeReply(reply)
        if (normalized.isEmpty()) {
            return null
        }
        categories.firstOrNull { it.equals(normalized, ignoreCase = true) }?.let { return it }

        val lower = normalized.lowercase()
        return categories.sortedByDescending { it.length }.firstOrNull { name ->
            val n = name.lowercase()
            Regex("\\b${Regex.escape(n)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }
    }

    // True when generation can stop: first line is a category, a newline ended the
    // answer, or the reply is already long enough to match.
    fun shouldStopGeneration(partial: String, categories: List<String>): Boolean {
        val normalized = normalizeReply(partial)
        if (normalized.isEmpty()) {
            return false
        }
        if (categories.any { it.equals(normalized, ignoreCase = true) }) {
            return true
        }
        if (partial.contains('\n')) {
            return true
        }
        return partial.trim().length >= 48
    }

    // LiteRT-LM LLGuidance regex: the whole decode must be one category name.
    // Escape by hand; Java `\Q..\E` is not valid in LLGuidance.
    fun escapeConstraint(text: String): String {
        val special = ".\\^$*+?()[]{}|"
        return buildString {
            for (c in text) {
                if (c in special) append('\\')
                append(c)
            }
        }
    }

    fun constraintNames(categories: List<String>): List<String> {
        return categories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
    }

    fun categoryConstraintRegex(categories: List<String>): String? {
        val names = constraintNames(categories)
        if (names.isEmpty()) {
            return null
        }
        return " ?(" + names.joinToString("|") { escapeConstraint(it) } + ")"
    }

    // llama.cpp GBNF analog of [categoryConstraintRegex]: optional space, then one name.
    fun escapeGbnf(text: String): String {
        return buildString {
            for (c in text) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }

    fun categoryConstraintGbnf(categories: List<String>): String? {
        val names = constraintNames(categories)
        if (names.isEmpty()) {
            return null
        }
        val alts = names.joinToString(" | ") { "\"${escapeGbnf(it)}\"" }
        return "root ::= \" \"? ($alts)"
    }
}
