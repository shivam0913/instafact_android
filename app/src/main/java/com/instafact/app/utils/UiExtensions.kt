package com.instafact.app.utils

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.ColorRes
import com.instafact.app.R
import java.net.URLDecoder
import java.text.DecimalFormat
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun String?.displayStatus(context: Context): String {
    return when (this?.lowercase()) {
        "pending" -> context.getString(R.string.pending)
        "processing" -> context.getString(R.string.processing)
        "completed" -> context.getString(R.string.completed)
        "failed" -> context.getString(R.string.failed)
        else -> context.getString(R.string.unknown)
    }
}

fun String?.displayVerdict(context: Context): String {
    return this ?: context.getString(R.string.verdict_pending)
}

fun Int?.displayConfidence(context: Context): String {
    return this?.let { context.getString(R.string.confidence_format, it) }
        ?: context.getString(R.string.unknown)
}

fun String?.verdictSectionTitle(context: Context): String {
    return when (this?.lowercase()) {
        "true" -> context.getString(R.string.why_this_is_true)
        "false" -> context.getString(R.string.why_this_is_false)
        "misleading" -> context.getString(R.string.why_this_is_misleading)
        else -> context.getString(R.string.why_this_is_unverified)
    }
}

@ColorRes
fun String?.verdictColorRes(): Int {
    return when (this?.lowercase()) {
        "true" -> R.color.brand_status_true
        "false" -> R.color.brand_status_false
        "misleading" -> R.color.brand_status_misleading
        else -> R.color.brand_status_unverified
    }
}

@ColorRes
fun String?.verdictSoftColorRes(): Int {
    return when (this?.lowercase()) {
        "true" -> R.color.brand_status_true_soft
        "false" -> R.color.brand_status_false_soft
        "misleading" -> R.color.brand_status_misleading_soft
        else -> R.color.brand_status_unverified_soft
    }
}

fun String?.sourceCountLabel(context: Context, fallback: Int = 3): String {
    val count = when (this?.lowercase()) {
        "true" -> 3
        "false" -> 2
        "misleading" -> 3
        else -> fallback
    }
    return context.getString(R.string.checked_sources_count, count)
}

fun String.toReadableHeadline(): String {
    return runCatching {
        val trimmed = trim()
        if (trimmed.isBlank()) return trimmed
        val pathPart = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val decoded = URLDecoder.decode(pathPart, StandardCharsets.UTF_8.name())
        decoded.replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }.getOrDefault(this).ifBlank { this }
}

fun String.ellipsized(maxLength: Int = 58): String {
    return if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "..."
}

fun Int.formatFactCheckCount(context: Context): String {
    if (this <= 0) return context.getString(R.string.recently)
    val compactValue = when {
        this >= 1_000_000 -> DecimalFormat("0.#M").format(this / 1_000_000.0)
        this >= 1_000 -> DecimalFormat("0.#K").format(this / 1_000.0)
        else -> this.toString()
    }
    return "$compactValue fact-checks"
}

fun String.sourceUrlLabel(): String {
    return runCatching {
        val uri = java.net.URI(this)
        val host = uri.host?.removePrefix("www.").orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')
        val visiblePath = if (path.isBlank()) "" else path
        (host + visiblePath).ifBlank { this }
    }.getOrDefault(this)
}

fun String?.toRelativeTimeLabel(context: Context): String? {
    val instant = this.toInstantOrNull() ?: return null
    return DateUtils.getRelativeTimeSpanString(
        instant.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

fun String?.toCompactRelativeTimeLabel(context: Context): String? {
    return toRelativeTimeLabel(context)
        ?.replace("Yesterday", "1d ago", ignoreCase = true)
        ?.replace(Regex("(\\d+)\\s+minutes?\\s+ago", RegexOption.IGNORE_CASE), "$1m ago")
        ?.replace(Regex("(\\d+)\\s+hours?\\s+ago", RegexOption.IGNORE_CASE), "$1h ago")
        ?.replace(Regex("(\\d+)\\s+days?\\s+ago", RegexOption.IGNORE_CASE), "$1d ago")
        ?.replace(Regex("(\\d+)\\s+weeks?\\s+ago", RegexOption.IGNORE_CASE), "$1w ago")
        ?.replace(Regex("(\\d+)\\s+months?\\s+ago", RegexOption.IGNORE_CASE), "$1mo ago")
        ?.replace(Regex("(\\d+)\\s+years?\\s+ago", RegexOption.IGNORE_CASE), "$1y ago")
}

fun String?.explanationAsBullets(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return ""
    val parts = value.split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return if (parts.size <= 1) {
        "\u2022 $value"
    } else {
        parts.joinToString("\n\n") { "\u2022 $it" }
    }
}

private fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null

    return parseAttempt { Instant.parse(this) }
        ?: parseAttempt { OffsetDateTime.parse(this).toInstant() }
        ?: parseAttempt {
            LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }
}

private inline fun parseAttempt(block: () -> Instant): Instant? {
    return try {
        block()
    } catch (_: DateTimeParseException) {
        null
    }
}
