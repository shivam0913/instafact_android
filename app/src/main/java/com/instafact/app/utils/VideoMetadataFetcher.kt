package com.instafact.app.utils

import android.net.Uri
import android.text.Html
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ClientVideoMetadata(
    val title: String? = null,
    val channelName: String? = null,
    val creatorId: String? = null,
    val thumbnailUrl: String? = null,
)

object VideoMetadataFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Instafact) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun fetch(videoUrl: String): ClientVideoMetadata? {
        val normalizedUrl = normalizeVideoUrl(videoUrl)
        val host = Uri.parse(normalizedUrl).host?.lowercase().orEmpty()
        SessionDebugLogger.logMetadataFetchStart("VideoMetadataFetcher.fetch", videoUrl)
        return runCatching {
            when {
                host == "youtu.be" || host.contains("youtube.com") -> fetchYouTubeMetadata(normalizedUrl)
                host.contains("instagram.com") -> fetchInstagramMetadata(normalizedUrl)
                else -> null
            }?.normalized()
        }.onSuccess { metadata ->
            SessionDebugLogger.logMetadataFetchResult("VideoMetadataFetcher.fetch", videoUrl, metadata)
        }.onFailure { throwable ->
            SessionDebugLogger.logMetadataFetchFailure("VideoMetadataFetcher.fetch", videoUrl, throwable)
        }.getOrNull()
    }

    private fun fetchYouTubeMetadata(videoUrl: String): ClientVideoMetadata? {
        val encodedUrl = URLEncoder.encode(videoUrl, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://www.youtube.com/oembed?url=$encodedUrl&format=json")
            .get()
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            return ClientVideoMetadata(
                title = json.optString("title").nullIfBlank(),
                channelName = json.optString("author_name").nullIfBlank(),
                creatorId = json.optString("author_url")
                    .substringAfterLast("/", "")
                    .substringBefore("?")
                    .nullIfBlank(),
                thumbnailUrl = json.optString("thumbnail_url").nullIfBlank(),
            )
        }
    }

    private fun fetchInstagramMetadata(videoUrl: String): ClientVideoMetadata? {
        val requestUrls = buildInstagramCandidateUrls(videoUrl)
        requestUrls.forEach { requestUrl ->
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .addHeader("Referer", "https://www.instagram.com/")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                SessionDebugLogger.logMetadataAttempt(
                    source = "VideoMetadataFetcher.fetchInstagramMetadata",
                    requestUrl = requestUrl,
                    finalUrl = response.request.url.toString(),
                    statusCode = response.code,
                    bodySnippet = body.take(500).replace("\\s+".toRegex(), " ").trim(),
                )
                if (!response.isSuccessful || body.isBlank()) return@forEach

                parseInstagramJsonMetadata(body)?.let { metadata ->
                    return metadata
                }

                val title = extractInstagramTitle(body)
                val channelName = extractInstagramCreatorName(body)
                val creatorId = extractInstagramCreatorId(body) ?: channelName
                val thumbnailUrl = extractInstagramThumbnail(body)

                val metadata = ClientVideoMetadata(
                    title = title.takeUnless { it.isGenericInstagramText() },
                    channelName = channelName.takeUnless { it.isGenericInstagramText() },
                    creatorId = creatorId.takeUnless { it.isGenericInstagramText() },
                    thumbnailUrl = thumbnailUrl.takeUnless { it.isGenericInstagramThumbnail() },
                ).normalized()

                if (metadata != null) {
                    return metadata
                }
            }
        }

        return null
    }

    private fun extractInstagramCreatorName(html: String): String? {
        return extractJsonLdString(html, listOf("author.name", "author.alternateName", "creator.name"))
            ?: extractJsonValue(html, "\"username\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"owner\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"author_name\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractMetaContent(html, listOf("og:title"))
            ?: extractTitleTag(html)
                ?.substringBefore(" on Instagram")
                ?.substringBefore(" • Instagram")
                ?.cleanMetadataValue()
        }

    private fun extractInstagramCreatorId(html: String): String? {
        return extractJsonValue(html, "\"owner\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"author_name\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractInstagramCreatorName(html)
    }

    private fun extractInstagramTitle(html: String): String? {
        val description = extractMetaContent(
            html = html,
            keys = listOf("og:description", "twitter:description", "description", "twitter:title"),
        )?.cleanMetadataValue()
            ?: extractJsonLdString(html, listOf("caption", "name", "headline", "description"))
            ?: extractJsonValue(html, "\"caption\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"accessibility_caption\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractTitleTag(html)?.cleanMetadataValue()

        return when {
            description.isNullOrBlank() -> null
            description.startsWith("@") && description.contains(":") -> {
                description.substringAfter(":", description).cleanMetadataValue()
            }
            else -> description
        }
    }

    private fun extractInstagramThumbnail(html: String): String? {
        return extractMetaContent(
            html = html,
            keys = listOf("og:image", "og:image:secure_url", "twitter:image", "twitter:image:src"),
        )
            ?: extractJsonLdString(html, listOf("thumbnailUrl", "image", "contentUrl"))
            ?: extractJsonValue(html, "\"display_resources\"\\s*:\\s*\\[(?:[^\\]]*?\"src\"\\s*:\\s*\"([^\"]+)\")")
            ?: extractJsonValue(html, "\"thumbnail_url\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"thumbnail_src\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"display_url\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractJsonValue(html, "\"image_versions2\"\\s*:\\s*\\{[^}]*\"candidates\"\\s*:\\s*\\[(?:[^\\]]*?\"url\"\\s*:\\s*\"([^\"]+)\")")
            ?: extractRegexValue(html, "<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>")
            ?: extractRegexValue(html, "<video[^>]+poster=[\"']([^\"']+)[\"'][^>]*>")
            ?: extractRegexValue(html, "(https?:\\\\?/\\\\?/[^\"'\\s>]+(?:cdninstagram|fbcdn)[^\"'\\s>]+)")
                ?.replace("\\/", "/")
    }

    private fun extractMetaContent(html: String, keys: List<String>): String? {
        val metaRegex = Regex("<meta\\s+[^>]*>", RegexOption.IGNORE_CASE)
        val contentRegex = Regex("content\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)

        return metaRegex.findAll(html)
            .map { it.value }
            .firstNotNullOfOrNull { tag ->
                val matchesKey = keys.any { key ->
                    Regex("(property|name)\\s*=\\s*[\"']${Regex.escape(key)}[\"']", RegexOption.IGNORE_CASE)
                        .containsMatchIn(tag)
                }
                if (!matchesKey) return@firstNotNullOfOrNull null
                contentRegex.find(tag)?.groupValues?.getOrNull(1)?.cleanMetadataValue()
            }
    }

    private fun extractJsonValue(html: String, pattern: String): String? {
        return Regex(pattern, RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanMetadataValue()
    }

    private fun extractRegexValue(html: String, pattern: String): String? {
        return Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanMetadataValue()
    }

    private fun extractTitleTag(html: String): String? {
        return Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanMetadataValue()
    }

    private fun extractJsonLdString(html: String, paths: List<String>): String? {
        val scriptRegex = Regex(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        scriptRegex.findAll(html).forEach { match ->
            val scriptContent = match.groupValues.getOrNull(1).orEmpty().trim()
            if (scriptContent.isBlank()) return@forEach
            runCatching {
                when {
                    scriptContent.startsWith("[") -> JSONArray(scriptContent)
                    else -> JSONObject(scriptContent)
                }
            }.getOrNull()?.let { json ->
                paths.firstNotNullOfOrNull { path -> findJsonPathValue(json, path) }?.let { return it }
            }
        }
        return null
    }

    private fun findJsonPathValue(container: Any, path: String): String? {
        val segments = path.split(".")
        return findJsonPathValue(container, segments, 0)
    }

    private fun findJsonPathValue(container: Any, segments: List<String>, index: Int): String? {
        if (index >= segments.size) {
            return when (container) {
                is String -> container.cleanMetadataValue()
                else -> null
            }
        }

        return when (container) {
            is JSONObject -> {
                val segment = segments[index]
                if (!container.has(segment) || container.isNull(segment)) return null
                findJsonPathValue(container.get(segment), segments, index + 1)
            }

            is JSONArray -> {
                for (i in 0 until container.length()) {
                    val value = container.opt(i) ?: continue
                    findJsonPathValue(value, segments, index)?.let { return it }
                }
                null
            }

            else -> null
        }
    }

    private fun buildInstagramCandidateUrls(videoUrl: String): List<String> {
        val normalized = normalizeVideoUrl(videoUrl).trimEnd('/')
        return listOf(
            "$normalized/?__a=1&__d=dis",
            "$normalized/embed/captioned/",
            "$normalized/embed/",
            "$normalized/",
        ).distinct()
    }

    private fun parseInstagramJsonMetadata(body: String): ClientVideoMetadata? {
        val trimmedBody = body.trim()
        if (!trimmedBody.startsWith("{")) return null

        val jsonObject = runCatching { JSONObject(trimmedBody) }.getOrNull() ?: return null
        return ClientVideoMetadata(
            title = findJsonPathValue(
                jsonObject,
                "items.caption.text",
            ) ?: findJsonPathValue(jsonObject, "graphql.shortcode_media.edge_media_to_caption.edges.node.text")
                ?: findJsonPathValue(jsonObject, "caption"),
            channelName = findJsonPathValue(jsonObject, "items.user.username")
                ?: findJsonPathValue(jsonObject, "author_name")
                ?: findJsonPathValue(jsonObject, "graphql.shortcode_media.owner.username"),
            creatorId = findJsonPathValue(jsonObject, "items.user.username")
                ?: findJsonPathValue(jsonObject, "graphql.shortcode_media.owner.username"),
            thumbnailUrl = findJsonPathValue(jsonObject, "items.image_versions2.candidates.url")
                ?: findJsonPathValue(jsonObject, "graphql.shortcode_media.display_url")
                ?: findJsonPathValue(jsonObject, "thumbnail_url"),
        ).normalized()
    }

    private fun normalizeVideoUrl(videoUrl: String): String {
        val uri = Uri.parse(videoUrl)
        val normalizedPath = uri.path.orEmpty().trimEnd('/')
        return uri.buildUpon()
            .clearQuery()
            .fragment(null)
            .path(normalizedPath)
            .build()
            .toString()
    }

    private fun ClientVideoMetadata.normalized(): ClientVideoMetadata? {
        val normalizedTitle = title.cleanMetadataValue()
        val normalizedChannelName = channelName.cleanMetadataValue()
        val normalizedCreatorId = creatorId.cleanMetadataValue()
        val normalizedThumbnailUrl = thumbnailUrl.cleanMetadataValue()

        if (
            normalizedTitle == null &&
            normalizedChannelName == null &&
            normalizedCreatorId == null &&
            normalizedThumbnailUrl == null
        ) {
            return null
        }

        return copy(
            title = normalizedTitle,
            channelName = normalizedChannelName,
            creatorId = normalizedCreatorId,
            thumbnailUrl = normalizedThumbnailUrl,
        )
    }

    private fun String?.cleanMetadataValue(): String? {
        if (this.isNullOrBlank()) return null
        return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
            .nullIfBlank()
    }

    private fun String?.isGenericInstagramText(): Boolean {
        val value = this?.cleanMetadataValue()?.lowercase().orEmpty()
        if (value.isBlank()) return false
        return value == "instagram" ||
            value == "login • instagram" ||
            value == "login • instagram photos and videos" ||
            value == "instagram photos and videos"
    }

    private fun String?.isGenericInstagramThumbnail(): Boolean {
        val value = this?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return false
        return value.contains("static.cdninstagram.com/rsrc.php") ||
            value.contains("/favicon") ||
            value.contains("apple-touch-icon") ||
            value.contains("instagram.com/static/images")
    }

    private fun String.nullIfBlank(): String? = if (isBlank()) null else this
}
