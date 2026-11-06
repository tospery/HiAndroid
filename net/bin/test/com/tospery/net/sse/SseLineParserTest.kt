package com.tospery.net.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseLineParserTest {
    @Test
    fun extractsDataPayloadWithoutPrefixOrLeadingWhitespace() {
        assertEquals("""{"type":"content"}""", SseLineParser.dataPayloadOrNull("""  data:   {"type":"content"}"""))
        assertEquals("""{"a":1}""", SseLineParser.dataPayloadOrNull("""data:{"a":1}"""))
    }

    @Test
    fun ignoresBlankCommentAndNonDataLines() {
        listOf("", "   ", ": keep-alive", "event: message", "id: 1", "retry: 1000", "data:", "data:    ")
            .forEach { line -> assertNull(line, SseLineParser.dataPayloadOrNull(line)) }
    }
}
