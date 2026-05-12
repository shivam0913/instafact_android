package com.instafact.app.utils

import android.net.Uri

object UrlValidator {

    fun isSupportedVideoUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false

        return when {
            host.contains("instagram.com") -> uri.pathSegments.contains("reel")
            host == "youtu.be" -> !uri.lastPathSegment.isNullOrBlank()
            host.contains("youtube.com") -> uri.pathSegments.firstOrNull() == "shorts"
            else -> false
        }
    }
}
