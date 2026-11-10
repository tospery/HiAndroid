package com.tospery.suite.logging

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class TimberAppLoggerTest {
    private val entries = mutableListOf<LogEntry>()
    private val messages = mutableListOf<String>()

    private val entryTree = object : LogEntryTree() {
        override fun logEntry(entry: LogEntry) {
            entries += entry
        }
    }

    private val plainTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            messages += message
        }
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun infoMinimumLevelFiltersVerboseAndDebug() {
        val logger = TimberAppLogger(minimumLevel = LogLevel.INFO)

        assertFalse(logger.isLoggable(LogLevel.VERBOSE, tag = null))
        assertFalse(logger.isLoggable(LogLevel.DEBUG, tag = null))
        assertTrue(logger.isLoggable(LogLevel.INFO, tag = null))
        assertTrue(logger.isLoggable(LogLevel.WARNING, tag = null))
        assertTrue(logger.isLoggable(LogLevel.ERROR, tag = null))
    }

    @Test
    fun sensitiveDataIsOnlyAcceptedWhenTheHostDeclaresIt() {
        assertFalse(TimberAppLogger().supportsSensitiveData)
        assertTrue(TimberAppLogger(supportsSensitiveData = true).supportsSensitiveData)
    }

    @Test
    fun entryTreeReceivesTheOriginalEntryUnchanged() {
        Timber.plant(entryTree)
        val failure = IllegalStateException("boom")
        val entry =
            LogEntry(
                level = LogLevel.WARNING,
                message = "Request failed 100% of the time",
                tag = "mylog-network",
                throwable = failure,
                attributes = listOf(LogAttribute("network_request_id", "r1"), LogAttribute("body", "{\"a\":1}")),
            )

        TimberAppLogger(supportsSensitiveData = true).log(entry)

        assertEquals(listOf(entry), entries)
        assertSame(failure, entries.single().throwable)
    }

    @Test
    fun plainTreeReceivesReadableMessageWithAttributes() {
        Timber.plant(plainTree)

        TimberAppLogger().log(
            LogEntry(
                level = LogLevel.INFO,
                message = "Loaded",
                attributes = listOf(LogAttribute("count", "3"), LogAttribute("page", "2")),
            ),
        )

        assertEquals(listOf("Loaded {count=3, page=2}"), messages)
    }

    @Test
    fun directTimberCallsAreSynthesizedWithoutDuplicatedStackTrace() {
        Timber.plant(entryTree)
        val failure = IllegalArgumentException("bad")

        Timber.tag("third-party").w(failure, "Retry %d of %d", 2, 3)
        Timber.e(failure)

        assertEquals(2, entries.size)
        assertEquals(LogEntry(LogLevel.WARNING, "Retry 2 of 3", "third-party", failure), entries[0])
        assertEquals(LogLevel.ERROR, entries[1].level)
        assertEquals("", entries[1].message)
        assertSame(failure, entries[1].throwable)
    }

    @Test
    fun entryTreeLevelFilterRunsBeforeTheEntryIsCaptured() {
        val infoOnly = object : LogEntryTree() {
            override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= android.util.Log.INFO

            override fun logEntry(entry: LogEntry) {
                entries += entry
            }
        }
        Timber.plant(infoOnly)
        val logger = TimberAppLogger()

        logger.log(LogEntry(LogLevel.DEBUG, "hidden"))
        logger.log(LogEntry(LogLevel.INFO, "shown"))

        assertEquals(listOf("shown"), entries.map(LogEntry::message))
    }
}
