package com.vitalis.healthos

import java.time.LocalDate

/** Bounded checks before crossing from untrusted JavaScript into native operations. */
internal object BridgeInputPolicy {
    fun date(raw: String?): LocalDate? {
        if (raw == null || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw.trim())) return null
        return try { LocalDate.parse(raw.trim()) } catch (_: Exception) { null }
    }

    fun requestId(raw: String?): Boolean =
        raw != null && raw.length in 1..120 && raw.all { it.isLetterOrDigit() || it in "-_:." }

    fun mealJsonSize(raw: String?): Boolean = raw != null && raw.isNotBlank() && raw.length <= 32_000

    fun mealImage(raw: String?): Boolean {
        if (raw == null || raw.length !in 32..(NutritionImagePolicy.MAX_BASE64_CHARACTERS + 32)) {
            return false
        }
        val prefix = listOf(
            "data:image/jpeg;base64,",
            "data:image/png;base64,",
            "data:image/webp;base64,"
        ).firstOrNull(raw::startsWith) ?: return false
        val payload = raw.substring(prefix.length)
        return payload.length <= NutritionImagePolicy.MAX_BASE64_CHARACTERS && payload.all {
            it.isLetterOrDigit() || it == '+' || it == '/' || it == '='
        }
    }

    fun apiKey(raw: String?): Boolean = raw != null && raw.length in 30..512 && raw.startsWith("sk-")

    fun externalUrl(raw: String?): Boolean = NavigationPolicy.decide(raw) == NavigationPolicy.Decision.EXTERNAL_HTTPS
}
