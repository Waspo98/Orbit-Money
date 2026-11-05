package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account

data class SimpleFinIssueItem(
    val rawMessage: String,
    val accountLabel: String
)

object SimpleFinWarningParser {
    private val prefixRegex = Regex(
        "^(?:partial\\s+sync\\s*[\\u2014\\u2013-]\\s*)?simplefin\\s+reported:\\s*",
        RegexOption.IGNORE_CASE
    )
    private val connectionToRegex = Regex(
        "^connection\\s+to\\s+(.+?)(?:\\s+(?:may\\s+need|needs?\\s+attention|failed|is\\s+broken|is\\s+down|requires?|timed?\\s+out|could\\s+not|cannot|has\\s+an?\\s+error)|[.;(]|$)",
        RegexOption.IGNORE_CASE
    )
    private val altAccountRegex = Regex(
        "^(?:attention\\s+needed\\s+for|unable\\s+to\\s+(?:sync|refresh|connect\\s+to)|error\\s+syncing)\\s+(.+?)(?:[.:;(]|$)",
        RegexOption.IGNORE_CASE
    )

    fun parseUnmutedIssues(
        rawError: String?,
        knownAccounts: List<Account> = emptyList(),
        mutedAccounts: List<String> = emptyList()
    ): List<SimpleFinIssueItem> {
        if (rawError.isNullOrBlank()) return emptyList()
        val body = rawError.trim().replace(prefixRegex, "").trim()
        if (body.isEmpty()) return emptyList()

        val parts = body.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedMuted = mutedAccounts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val sortedAccountCandidates = buildList {
            knownAccounts.forEach { acct ->
                if (acct.name.trim().length >= 3) add(acct.name.trim())
                val inst = acct.institution?.trim().orEmpty()
                if (inst.length >= 3) add(inst)
            }
        }.distinctBy { it.lowercase() }.sortedByDescending { it.length }

        return parts.map { part ->
            val extracted = connectionToRegex.find(part)?.groupValues?.getOrNull(1)?.trim()
                ?: altAccountRegex.find(part)?.groupValues?.getOrNull(1)?.trim()
                ?: sortedAccountCandidates.firstOrNull { candidate ->
                    part.contains(candidate, ignoreCase = true)
                }
                ?: part.substringBefore(":").trim().takeIf { it.length in 3..64 && part.contains(":") }
                ?: part

            SimpleFinIssueItem(
                rawMessage = part,
                accountLabel = extracted.trimEnd('.', ',', ';', '-', ' ')
            )
        }.filterNot { item ->
            val labelLower = item.accountLabel.lowercase()
            val msgLower = item.rawMessage.lowercase()
            normalizedMuted.any { muted ->
                labelLower == muted ||
                    labelLower.contains(muted) ||
                    muted.contains(labelLower) ||
                    msgLower.contains(muted)
            }
        }
    }
}
