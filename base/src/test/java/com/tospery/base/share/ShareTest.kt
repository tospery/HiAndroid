package com.tospery.base.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareTest {
    @Test
    fun contentAppendsUrlWithOneLineBreak() {
        val content =
            ShareContent(
                title = "HiGit",
                text = "Explore GitHub",
                url = "https://github.com/tospery/HiGit",
            )

        assertEquals(
            "Explore GitHub\nhttps://github.com/tospery/HiGit",
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
