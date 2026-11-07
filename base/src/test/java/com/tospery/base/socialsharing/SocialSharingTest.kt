package com.tospery.base.socialsharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialSharingTest {
    @Test
    fun contentAppendsUrlWithOneLineBreak() {
        val content =
            SocialSharingContent(
                title = "AtlasHub",
                text = "Explore GitHub",
                url = "https://github.com/tospery/AtlasHub",
            )

        assertEquals(
            "Explore GitHub\nhttps://github.com/tospery/AtlasHub",
            content.textWithUrl,
        )
    }

    @Test
    fun channelRejectsVendorUnsafeIdentifier() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialSharingChannel("WeChat Session")
        }
    }
}
