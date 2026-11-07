package com.tospery.base.socialsharing

/** Stable, vendor-independent identifier for a share destination. */
@JvmInline
value class SocialSharingChannel(
    val value: String,
) {
    init {
        require(CHANNEL_PATTERN.matches(value)) {
            "Share channel must contain only lowercase letters, digits, and underscores."
        }
    }

    companion object {
        private val CHANNEL_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")

        val ShortMessage = SocialSharingChannel("short_message")
        val Email = SocialSharingChannel("email")
        val X = SocialSharingChannel("x")
        val WeChat = SocialSharingChannel("wechat")
        val WeChatMoments = SocialSharingChannel("wechat_moments")
    }
}

/** Content selected by the application for sharing. */
data class SocialSharingContent(
    val title: String,
    val text: String,
    val url: String? = null,
    val image: SocialSharingImage? = null,
) {
    init {
        require(title.isNotBlank()) { "Share title must not be blank." }
        require(text.isNotBlank()) { "Share text must not be blank." }
        require(url == null || url.isNotBlank()) { "Share URL must be null or non-blank." }
    }

    val textWithUrl: String
        get() = url?.let { "$text\n$it" } ?: text
}

/** Portable image sources. Android resources are resolved by the host before dispatch. */
sealed interface SocialSharingImage {
    data class RemoteUrl(val url: String) : SocialSharingImage {
        init {
            require(url.startsWith("https://")) { "Share images require HTTPS." }
        }
    }

    data class LocalFile(val path: String) : SocialSharingImage {
        init {
            require(path.isNotBlank()) { "Share image path must not be blank." }
        }
    }
}

data class SocialSharingRequest(
    val channel: SocialSharingChannel,
    val content: SocialSharingContent,
)

sealed interface SocialSharingResult {
    data object Started : SocialSharingResult

    data object Completed : SocialSharingResult

    data object Cancelled : SocialSharingResult

    data class Failed(
        val cause: Throwable? = null,
    ) : SocialSharingResult
}

/**
 * Vendor-independent sharing port.
 *
 * Android adapters bind any required Activity when they are created, keeping this contract free
 * of Android and third-party SDK types.
 */
interface SocialSharingProvider {
    val supportedChannels: Set<SocialSharingChannel>

    fun share(
        request: SocialSharingRequest,
        onResult: (SocialSharingResult) -> Unit = {},
    )
}
