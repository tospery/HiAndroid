package com.tospery.suite.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteMediaRequestTest {
    @Test
    fun `detector recognizes common audio video and streaming paths`() {
        mapOf(
            "https://example.com/audio/track.MP3?download=1" to SuiteMediaKind.AUDIO,
            "assets/demo.m4a" to SuiteMediaKind.AUDIO,
            "https://example.com/video/movie.mp4#preview" to SuiteMediaKind.VIDEO,
            "https://example.com/live/index.m3u8?token=value" to SuiteMediaKind.VIDEO,
            "https://example.com/dash/manifest.mpd" to SuiteMediaKind.VIDEO,
        ).forEach { (value, expected) ->
            assertEquals(expected, SuiteMediaUrlDetector.detect(value))
        }
    }

    @Test
    fun `detector ignores media-looking query on a web page`() {
        assertNull(
            SuiteMediaUrlDetector.detect(
                "https://example.com/player?video=movie.mp4",
            ),
        )
    }

    @Test
    fun detectorLeavesAmbiguousTypeScriptPathsUnresolved() {
        listOf(
            "scripts/set-version.ts",
            "types/index.d.ts",
            "https://example.com/source/MAIN.TS?raw=1",
        ).forEach { value ->
            assertNull(value, SuiteMediaUrlDetector.detect(value))
        }
    }

    @Test
    fun explicitVideoRequestStillAcceptsAmbiguousTransportStreamExtension() {
        SuiteMediaRequest(
            url = "https://example.com/media/segment.ts",
            kind = SuiteMediaKind.VIDEO,
        )
    }

    @Test
    fun `request accepts remote and content URLs`() {
        SuiteMediaRequest(
            url = "https://example.com/media/movie.mp4",
            kind = SuiteMediaKind.VIDEO,
            title = "movie.mp4",
        )
        SuiteMediaRequest(
            url = "content://com.example.files/media/42",
            kind = SuiteMediaKind.AUDIO,
        )
    }

    @Test
    fun `request rejects unsafe or unsupported URLs`() {
        listOf(
            "",
            "javascript:alert(1)",
            "file:///data/user/0/example/private.mp4",
            "https://user@example.com/media.mp4",
            " https://example.com/media.mp4",
            "https:\\\\example.com\\media.mp4",
        ).forEach { url ->
            assertTrue(
                "Expected URL to be rejected: $url",
                runCatching {
                    SuiteMediaRequest(
                        url = url,
                        kind = SuiteMediaKind.VIDEO,
                    )
                }.isFailure,
            )
        }
    }
}
