package com.tospery.suite.ui

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteWebPageUrlTest {
    @Test
    fun `accepts HTTP and HTTPS URLs with a host`() {
        assertTrue("http://example.com".isSafeWebUrl())
        assertTrue("https://example.com".isSafeWebUrl())
        assertTrue(
            "https://harry0703.github.io/mpt-assets/?video=demo.mp4"
                .isSafeWebUrl(),
        )
    }

    @Test
    fun `rejects non Web local and credential URLs`() {
        listOf(
            "",
            "javascript:alert(1)",
            "file:///tmp/video.mp4",
            "content://media/video/1",
            "https://user@example.com",
            "https:\\\\example.com\\video",
            "https:///missing-host",
        ).forEach { url ->
            assertFalse(url, url.isSafeWebUrl())
        }
    }

    @Test
    fun `recognizes HTTP and HTTPS image documents by path extension`() {
        listOf(
            "http://example.com/photo.jpg",
            "https://github.com/owner/repo/blob/commit/docs/webui.jpg",
            "https://raw.githubusercontent.com/owner/repo/commit/image.PNG?raw=true",
            "https://example.com/assets/vector.svg#preview",
        ).forEach { url ->
            assertTrue(url, url.isLikelyImageUrl())
        }
    }

    @Test
    fun `does not classify pages or unsafe image URLs as image documents`() {
        listOf(
            "https://github.com/owner/repo/blob/commit/README.md",
            "https://example.com/gallery?image=photo.jpg",
            "javascript:alert('photo.jpg')",
        ).forEach { url ->
            assertFalse(url, url.isLikelyImageUrl())
        }
    }

    @Test
    fun `classifies WebView main document failures without URL data`() {
        assertEquals(
            SuiteWebLoadFailureReason.NETWORK,
            WebViewClient.ERROR_HOST_LOOKUP.toSuiteWebLoadFailureReason(),
        )
        assertEquals(
            SuiteWebLoadFailureReason.TLS,
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE.toSuiteWebLoadFailureReason(),
        )
        assertEquals(
            SuiteWebLoadFailureReason.UNSUPPORTED_SCHEME,
            WebViewClient.ERROR_UNSUPPORTED_SCHEME.toSuiteWebLoadFailureReason(),
        )
        assertEquals(
            SuiteWebLoadFailureReason.UNKNOWN,
            Int.MIN_VALUE.toSuiteWebLoadFailureReason(),
        )
    }
}
