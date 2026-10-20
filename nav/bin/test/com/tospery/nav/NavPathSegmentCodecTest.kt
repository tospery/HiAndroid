package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavPathSegmentCodecTest {
    @Test
    fun spaceIsEncodedWithoutFormUrlPlusSemantics() {
        assertEquals("a%20b", encodeNavPathSegment("a b"))
    }

    @Test
    fun unicodeIsEncodedAsUtf8Bytes() {
        assertEquals(
            "%E4%B8%AD%E6%96%87",
            encodeNavPathSegment("中文"),
        )
    }

    @Test
    fun unreservedCharactersRemainReadable() {
        assertEquals(
            "Az09-._~",
            encodeNavPathSegment("Az09-._~"),
        )
    }

    @Test
    fun unsafeDecodedSegmentsAreRejectedByEncoder() {
        listOf(
            "",
            ".",
            "..",
            "owner/repo",
            "owner\\repo",
            "owner\u0000repo",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                encodeNavPathSegment(value)
            }
        }
    }
}
