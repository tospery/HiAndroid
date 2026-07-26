package com.tospery.suite.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteWebPageUrlTest {
    @Test
    fun `accepts HTTPS URLs with a host`() {
        assertTrue("https://example.com".isSafeHttpsWebUrl())
        assertTrue(
            "https://harry0703.github.io/mpt-assets/?video=demo.mp4"
                .isSafeHttpsWebUrl(),
        )
    }

    @Test
    fun `rejects non HTTPS local and credential URLs`() {
        listOf(
            "",
            "http://example.com",
            "javascript:alert(1)",
            "file:///tmp/video.mp4",
            "content://media/video/1",
            "https://user@example.com",
            "https:\\\\example.com\\video",
            "https:///missing-host",
        ).forEach { url ->
            assertFalse(url, url.isSafeHttpsWebUrl())
        }
    }

    @Test
    fun `recognizes HTTPS image documents by path extension`() {
        listOf(
            "https://github.com/owner/repo/blob/commit/docs/webui.jpg",
            "https://raw.githubusercontent.com/owner/repo/commit/image.PNG?raw=true",
            "https://example.com/assets/vector.svg#preview",
        ).forEach { url ->
            assertTrue(url, url.isLikelyHttpsImageUrl())
        }
    }

    @Test
    fun `does not classify pages or unsafe image URLs as image documents`() {
        listOf(
            "https://github.com/owner/repo/blob/commit/README.md",
            "https://example.com/gallery?image=photo.jpg",
            "http://example.com/photo.jpg",
        ).forEach { url ->
            assertFalse(url, url.isLikelyHttpsImageUrl())
        }
    }
}
