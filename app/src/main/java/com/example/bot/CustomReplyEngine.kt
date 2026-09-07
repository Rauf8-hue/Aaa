package com.example.bot

import com.example.model.CustomReply
import com.example.model.MatchMode

class CustomReplyEngine {

    /**
     * Checks if the incoming message matches any enabled custom reply rule.
     * Evaluates rules in order. Returns the first matching CustomReply or null.
     */
    fun findMatchingReply(incomingText: String, rules: List<CustomReply>): CustomReply? {
        val trimmedInput = incomingText.trim()
        if (trimmedInput.isEmpty()) return null

        val enabledRules = rules.filter { it.isEnabled }

        for (rule in enabledRules) {
            val trigger = rule.trigger.trim()
            if (trigger.isEmpty()) continue

            if (isMatch(trimmedInput, trigger, rule.matchMode)) {
                return rule
            }
        }

        return null
    }

    private fun isMatch(input: String, trigger: String, modeName: String): Boolean {
        return when (modeName) {
            MatchMode.EXACT.name -> {
                input == trigger
            }
            MatchMode.CONTAINS.name -> {
                input.contains(trigger, ignoreCase = true)
            }
            MatchMode.CASE_INSENSITIVE.name -> {
                // 1. Direct case-insensitive equality
                if (input.equals(trigger, ignoreCase = true)) {
                    return true
                }
                // 2. Normalized without common ending punctuation (e.g. "Hi!" vs "Hi")
                val cleanInput = normalizePunctuation(input)
                val cleanTrigger = normalizePunctuation(trigger)
                if (cleanInput.equals(cleanTrigger, ignoreCase = true)) {
                    return true
                }
                // 3. Word boundary exact match (e.g. "hello hi" contains whole word "hi")
                val words = cleanInput.split("\\s+".toRegex())
                words.any { it.equals(cleanTrigger, ignoreCase = true) }
            }
            else -> {
                input.equals(trigger, ignoreCase = true)
            }
        }
    }

    private fun normalizePunctuation(text: String): String {
        return text.trim().replace("[!?,.;:]+$".toRegex(), "").trim()
    }
}
