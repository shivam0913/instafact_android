package com.instafact.app.utils

import android.content.Context
import com.instafact.app.R

fun String.platformIconRes(): Int {
    val value = lowercase()
    return when {
        value.contains("instagram.com") || value.contains("instagram") -> R.drawable.ic_instagram
        value.contains("youtube.com") || value.contains("youtu.be") || value.contains("youtube") -> R.drawable.ic_youtube
        else -> R.drawable.ic_link
    }
}

fun String.platformLabelFromUrl(context: Context): String {
    val value = lowercase()
    return when {
        value.contains("instagram.com") || value.contains("instagram") -> context.getString(R.string.platform_instagram)
        value.contains("youtube.com") || value.contains("youtu.be") || value.contains("youtube") -> context.getString(R.string.platform_youtube)
        else -> context.getString(R.string.platform_unknown)
    }
}

fun String.platformLabel(context: Context): String {
    return when (lowercase()) {
        "instagram" -> context.getString(R.string.platform_instagram)
        "youtube" -> context.getString(R.string.platform_youtube)
        else -> context.getString(R.string.platform_unknown)
    }
}

fun String.platformBadge(context: Context): String {
    return when {
        lowercase().contains("instagram") -> context.getString(R.string.instagram_reel_badge)
        lowercase().contains("youtube") -> context.getString(R.string.youtube_short_badge)
        else -> context.getString(R.string.shared_link_badge)
    }
}

fun String.platformSourceLabel(context: Context): String {
    val value = lowercase()
    return when {
        value.contains("instagram.com") || value.contains("instagram") -> context.getString(R.string.platform_instagram_source)
        value.contains("youtube.com") || value.contains("youtu.be") || value.contains("youtube") -> context.getString(R.string.platform_youtube_source)
        else -> context.getString(R.string.platform_unknown)
    }
}
