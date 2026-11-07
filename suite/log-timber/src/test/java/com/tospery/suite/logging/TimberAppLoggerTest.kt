package com.tospery.suite.logging

import com.tospery.base.logging.LogLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimberAppLoggerTest {
    @Test
    fun infoMinimumLevelFiltersVerboseAndDebug() {
        val logger = TimberAppLogger(minimumLevel = LogLevel.INFO)

        assertFalse(logger.isLoggable(LogLevel.VERBOSE, tag = null))
        assertFalse(logger.isLoggable(LogLevel.DEBUG, tag = null))
        assertTrue(logger.isLoggable(LogLevel.INFO, tag = null))
        assertTrue(logger.isLoggable(LogLevel.WARNING, tag = null))
        assertTrue(logger.isLoggable(LogLevel.ERROR, tag = null))
    }
}
