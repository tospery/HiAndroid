package com.tospery.suite.socialsharing.umeng

import com.tospery.base.socialsharing.SocialSharingChannel
import com.tospery.base.socialsharing.SocialSharingContent
import com.tospery.base.socialsharing.SocialSharingRequest
import com.tospery.base.socialsharing.SocialSharingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengSocialSharingProviderTest {
    @Test
    fun weChatRequiresReadySdkAndClientWhileSmsKeepsSystemFallback() {
        val channels = linkedSetOf(SocialSharingChannel.WeChat, SocialSharingChannel.WeChatMoments, SocialSharingChannel.ShortMessage)
        for (sdkReady in listOf(false, true)) {
            for (clientAvailable in listOf(false, true)) {
                assertEquals(
                    if (sdkReady && clientAvailable) channels else setOf(SocialSharingChannel.ShortMessage),
                    resolveSupportedChannels(
                        enabledChannels = channels,
                        isSystemHandlerAvailable = { true },
                        isUmengSdkReady = sdkReady,
                        isWeChatAvailable = clientAvailable,
                    ),
                )
            }
        }
    }

    @Test
    fun supportedRequestIsForwardedToSdk() {
        val sdk = RecordingSdk()
        val provider = UmengSocialSharingProvider(sdk)
        val results = mutableListOf<SocialSharingResult>()

        provider.share(request(SocialSharingChannel.Email), results::add)

        assertEquals(SocialSharingChannel.Email, sdk.channel)
        assertEquals("body\nhttps://example.com", sdk.content?.textWithUrl)
        assertEquals(listOf(SocialSharingResult.Started), results)
    }

    @Test
    fun unsupportedChannelFailsWithoutCallingSdk() {
        val sdk = RecordingSdk()
        val provider = UmengSocialSharingProvider(sdk)
        val results = mutableListOf<SocialSharingResult>()

        provider.share(request(SocialSharingChannel.X), results::add)

        assertEquals(null, sdk.channel)
        assertTrue(results.single() is SocialSharingResult.Failed)
    }

    @Test
    fun supportedChannelsIntersectProjectSelectionAndSystemAvailability() {
        val supportedChannels =
            resolveSupportedChannels(
                enabledChannels =
                    linkedSetOf(
                        SocialSharingChannel.ShortMessage,
                        SocialSharingChannel.Email,
                    ),
                isSystemHandlerAvailable = { channel ->
                    channel == SocialSharingChannel.ShortMessage
                },
            )

        assertEquals(setOf(SocialSharingChannel.ShortMessage), supportedChannels)
    }

    @Test
    fun missingOfflinePlatformModuleUsesAndroidFallback() {
        val primarySdk = RecordingSdk()
        val fallbackSdk = RecordingSdk()
        val selectedPaths = mutableListOf<Pair<SocialSharingChannel, UmengSocialSharingExecutionPath>>()
        val sdk =
            PlatformAwareUmengSocialSharingSdk(
                primarySdk = primarySdk,
                fallbackSdk = fallbackSdk,
                isUmengSdkInitialized = { true },
                isPlatformModuleAvailable = { false },
                onExecutionPathSelected = { channel, path ->
                    selectedPaths += channel to path
                },
            )

        sdk.share(
            channel = SocialSharingChannel.ShortMessage,
            content = request(SocialSharingChannel.ShortMessage).content,
            onResult = {},
        )

        assertEquals(null, primarySdk.channel)
        assertEquals(SocialSharingChannel.ShortMessage, fallbackSdk.channel)
        assertEquals(
            listOf(
                SocialSharingChannel.ShortMessage to
                    UmengSocialSharingExecutionPath.ANDROID_SYSTEM_INTENT_FALLBACK,
            ),
            selectedPaths,
        )
    }

    @Test
    fun installedOfflinePlatformModuleUsesUShare() {
        val primarySdk = RecordingSdk()
        val fallbackSdk = RecordingSdk()
        val selectedPaths = mutableListOf<Pair<SocialSharingChannel, UmengSocialSharingExecutionPath>>()
        val sdk =
            PlatformAwareUmengSocialSharingSdk(
                primarySdk = primarySdk,
                fallbackSdk = fallbackSdk,
                isUmengSdkInitialized = { true },
                isPlatformModuleAvailable = { true },
                onExecutionPathSelected = { channel, path ->
                    selectedPaths += channel to path
                },
            )

        sdk.share(
            channel = SocialSharingChannel.Email,
            content = request(SocialSharingChannel.Email).content,
            onResult = {},
        )

        assertEquals(SocialSharingChannel.Email, primarySdk.channel)
        assertEquals(null, fallbackSdk.channel)
        assertEquals(
            listOf(
                SocialSharingChannel.Email to UmengSocialSharingExecutionPath.UMENG_PLATFORM_HANDLER,
            ),
            selectedPaths,
        )
    }

    @Test
    fun uninitializedCommonSdkUsesAndroidFallbackEvenWhenPlatformModuleExists() {
        val primarySdk = RecordingSdk()
        val fallbackSdk = RecordingSdk()
        val selectedPaths = mutableListOf<UmengSocialSharingExecutionPath>()
        val sdk =
            PlatformAwareUmengSocialSharingSdk(
                primarySdk = primarySdk,
                fallbackSdk = fallbackSdk,
                isUmengSdkInitialized = { false },
                isPlatformModuleAvailable = { true },
                onExecutionPathSelected = { _, path -> selectedPaths += path },
            )

        sdk.share(
            channel = SocialSharingChannel.Email,
            content = request(SocialSharingChannel.Email).content,
            onResult = {},
        )

        assertEquals(null, primarySdk.channel)
        assertEquals(SocialSharingChannel.Email, fallbackSdk.channel)
        assertEquals(
            listOf(UmengSocialSharingExecutionPath.ANDROID_SYSTEM_INTENT_FALLBACK),
            selectedPaths,
        )
    }

    private fun request(channel: SocialSharingChannel): SocialSharingRequest =
        SocialSharingRequest(
            channel = channel,
            content = SocialSharingContent("title", "body", "https://example.com"),
        )
}

private class RecordingSdk : UmengSocialSharingSdk {
    var channel: SocialSharingChannel? = null
    var content: SocialSharingContent? = null

    override fun share(
        channel: SocialSharingChannel,
        content: SocialSharingContent,
        onResult: (SocialSharingResult) -> Unit,
    ) {
        this.channel = channel
        this.content = content
        onResult(SocialSharingResult.Started)
    }
}
