package com.tospery.suite.media

import java.net.URI
import java.util.Locale

/** 播放器需要区分的媒体大类；具体容器和编解码能力由 Media3 与设备决定。 */
enum class SuiteMediaKind(
    val value: String,
) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun fromValue(value: String?): SuiteMediaKind? =
            entries.firstOrNull { kind ->
                kind.value.equals(value?.trim(), ignoreCase = true)
            }
    }
}

/**
 * 无业务通用的单媒体播放请求。
 *
 * HTTP(S) 适用于远程媒体，content/android.resource 适用于宿主安全暴露的本地媒体。
 * 凭据等请求头不属于可序列化路由数据，应在创建播放器页面时单独注入。
 */
data class SuiteMediaRequest(
    val url: String,
    val kind: SuiteMediaKind,
    val title: String? = null,
    val playWhenReady: Boolean = true,
) {
    init {
        val uri =
            runCatching { URI(url.trim()) }
                .getOrElse { throw IllegalArgumentException("媒体 URL 格式无效。", it) }
        require(url == url.trim() && url.isNotEmpty()) { "媒体 URL 不能为空或包含首尾空白。" }
        require(url.none(Char::isISOControl) && '\\' !in url) {
            "媒体 URL 包含不安全字符。"
        }
        require(uri.isAbsolute && uri.scheme?.lowercase(Locale.ROOT) in supportedSchemes) {
            "媒体 URL scheme 不受支持。"
        }
        if (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
        ) {
            require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
                "远程媒体 URL host 无效。"
            }
        }
        require(title == title?.trim()) { "媒体标题不能包含首尾空白。" }
    }

    private companion object {
        val supportedSchemes = setOf("http", "https", "content", "android.resource")
    }
}

/**
 * 根据 URL path 或文件路径识别无歧义的常见音视频类型，不读取 query，避免误判网页参数。
 * 仅凭扩展名无法区分的格式应由调用方显式提供媒体类型。
 */
object SuiteMediaUrlDetector {
    fun detect(value: String?): SuiteMediaKind? {
        val normalizedValue = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val rawPath =
            runCatching { URI(normalizedValue).rawPath }
                .getOrNull()
                ?.takeIf(String::isNotEmpty)
                ?: normalizedValue.substringBefore('?').substringBefore('#')
        val extension =
            rawPath
                .substringAfterLast('/')
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)

        return when (extension) {
            in audioExtensions -> SuiteMediaKind.AUDIO
            in videoExtensions -> SuiteMediaKind.VIDEO
            else -> null
        }
    }

    private val audioExtensions =
        setOf(
            "aac",
            "amr",
            "flac",
            "m4a",
            "mid",
            "midi",
            "mp3",
            "oga",
            "ogg",
            "opus",
            "wav",
        )
    private val videoExtensions =
        setOf(
            "3g2",
            "3gp",
            "avi",
            "m3u8",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpd",
            "mpeg",
            "mpg",
            "webm",
        )
}
