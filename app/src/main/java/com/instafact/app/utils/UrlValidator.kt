package com.instafact.app.utils

import android.net.Uri

/**
 * Client-side gate for shareable links.
 *
 * This must stay in step with `parse_video_url` in the backend
 * (app/utils/url_parser.py). When the client is stricter, users are told a link is
 * unsupported that the server would have happily fact-checked; when it is looser, the
 * submit fails with a generic 400 instead of the friendly explanation dialog.
 */
object UrlValidator {

    private val INSTAGRAM_HOSTS = setOf("instagram.com", "m.instagram.com")

    // Instagram posts (/p/) are fact-checked from their images, the same as reels.
    private val INSTAGRAM_PATH_PREFIXES = setOf("reel", "p")

    fun isSupportedVideoUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val pathSegments = uri.pathSegments.orEmpty()

        return when {
            host in INSTAGRAM_HOSTS ->
                pathSegments.size >= 2 && pathSegments.first().lowercase() in INSTAGRAM_PATH_PREFIXES

            host == "youtube.com" || host == "m.youtube.com" ->
                pathSegments.size >= 2 && pathSegments.first().lowercase() == "shorts"

            // Shortened form the YouTube share sheet produces: youtu.be/<id>.
            host == "youtu.be" -> pathSegments.size >= 1 && pathSegments.first().isNotBlank()

            else -> false
        }
    }
}
