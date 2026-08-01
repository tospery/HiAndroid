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
