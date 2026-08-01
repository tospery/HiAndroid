package com.tospery.base.share

/** Stable, vendor-independent identifier for a share destination. */
@JvmInline
value class ShareChannel(
    val value: String,
) {
    init {
        require(CHANNEL_PATTERN.matches(value)) {
            "Share channel must contain only lowercase letters, digits, and underscores."
        }
    }

    companion object {
        private val CHANNEL_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")

        val ShortMessage = ShareChannel("short_message")
        val Email = ShareChannel("email")
        val X = ShareChannel("x")
    }
}

/** Content selected by the application for sharing. */
data class ShareContent(
    val title: String,
    val text: String,
    val url: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Share title must not be blank." }
        require(text.isNotBlank()) { "Share text must not be blank." }
        require(url == null || url.isNotBlank()) { "Share URL must be null or non-blank." }
    }

    val textWithUrl: String
        get() = url?.let { "$text\n$it" } ?: text
}

data class ShareRequest(
    val channel: ShareChannel,
    val content: ShareContent,
)

sealed interface ShareResult {
    data object Started : ShareResult

    data object Completed : ShareResult

    data object Cancelled : ShareResult

    data class Failed(
        val cause: Throwable? = null,
    ) : ShareResult
}

/**
 * Vendor-independent sharing port.
 *
 * Android adapters bind any required Activity when they are created, keeping this contract free
 * of Android and third-party SDK types.
 */
interface ShareProvider {
    val supportedChannels: Set<ShareChannel>

    fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit = {},
    )
}
