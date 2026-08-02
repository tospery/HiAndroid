package com.tospery.suite.share.umeng

import com.tospery.base.share.ShareChannel
import com.tospery.base.share.ShareContent
import com.tospery.base.share.ShareRequest
import com.tospery.base.share.ShareResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengShareProviderTest {
    @Test
    fun supportedRequestIsForwardedToSdk() {
        val sdk = RecordingSdk()
        val provider = UmengShareProvider(sdk)
        val results = mutableListOf<ShareResult>()

        provider.share(request(ShareChannel.Email), results::add)

        assertEquals(ShareChannel.Email, sdk.channel)
        assertEquals("body\nhttps://example.com", sdk.content?.textWithUrl)
        assertEquals(listOf(ShareResult.Started), results)
    }

    @Test
    fun unsupportedChannelFailsWithoutCallingSdk() {
        val sdk = RecordingSdk()
        val provider = UmengShareProvider(sdk)
        val results = mutableListOf<ShareResult>()

        provider.share(request(ShareChannel.X), results::add)

        assertEquals(null, sdk.channel)
        assertTrue(results.single() is ShareResult.Failed)
    }

    @Test
    fun supportedChannelsIntersectProjectSelectionAndSystemAvailability() {
        val supportedChannels =
            resolveSupportedChannels(
                enabledChannels =
                    linkedSetOf(
                        ShareChannel.ShortMessage,
                        ShareChannel.Email,
                    ),
                isSystemHandlerAvailable = { channel ->
                    channel == ShareChannel.ShortMessage
                },
            )

        assertEquals(setOf(ShareChannel.ShortMessage), supportedChannels)
    }

    @Test
    fun missingOfflinePlatformModuleUsesAndroidFallback() {
        val primarySdk = RecordingSdk()
        val fallbackSdk = RecordingSdk()
        val selectedPaths = mutableListOf<Pair<ShareChannel, UmengShareExecutionPath>>()
        val sdk =
            PlatformAwareUmengShareSdk(
                primarySdk = primarySdk,
                fallbackSdk = fallbackSdk,
                isPlatformModuleAvailable = { false },
                onExecutionPathSelected = { channel, path ->
                    selectedPaths += channel to path
                },
            )

        sdk.share(
            channel = ShareChannel.ShortMessage,
            content = request(ShareChannel.ShortMessage).content,
            onResult = {},
        )

        assertEquals(null, primarySdk.channel)
        assertEquals(ShareChannel.ShortMessage, fallbackSdk.channel)
        assertEquals(
            listOf(
                ShareChannel.ShortMessage to
                    UmengShareExecutionPath.ANDROID_SYSTEM_INTENT_FALLBACK,
            ),
            selectedPaths,
        )
    }

    @Test
    fun installedOfflinePlatformModuleUsesUShare() {
        val primarySdk = RecordingSdk()
        val fallbackSdk = RecordingSdk()
        val selectedPaths = mutableListOf<Pair<ShareChannel, UmengShareExecutionPath>>()
        val sdk =
            PlatformAwareUmengShareSdk(
                primarySdk = primarySdk,
                fallbackSdk = fallbackSdk,
                isPlatformModuleAvailable = { true },
                onExecutionPathSelected = { channel, path ->
                    selectedPaths += channel to path
                },
            )

        sdk.share(
            channel = ShareChannel.Email,
            content = request(ShareChannel.Email).content,
            onResult = {},
        )

        assertEquals(ShareChannel.Email, primarySdk.channel)
        assertEquals(null, fallbackSdk.channel)
        assertEquals(
            listOf(
                ShareChannel.Email to UmengShareExecutionPath.UMENG_PLATFORM_HANDLER,
            ),
            selectedPaths,
        )
    }

    private fun request(channel: ShareChannel): ShareRequest =
        ShareRequest(
            channel = channel,
            content = ShareContent("title", "body", "https://example.com"),
        )
}

private class RecordingSdk : UmengShareSdk {
    var channel: ShareChannel? = null
    var content: ShareContent? = null

    override fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    ) {
        this.channel = channel
        this.content = content
        onResult(ShareResult.Started)
    }
}
