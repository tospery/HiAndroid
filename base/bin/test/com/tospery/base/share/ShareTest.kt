package com.tospery.base.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareTest {
    @Test
    fun contentAppendsUrlWithOneLineBreak() {
        val content =
            ShareContent(
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
            ShareChannel("WeChat Session")
        }
    }
}
