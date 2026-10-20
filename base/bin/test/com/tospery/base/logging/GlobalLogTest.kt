package com.tospery.base.logging

import com.tospery.buildmetadata.module_base.ModuleMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalLogTest {
    @After
    fun resetLogProvider() {
        LogRegistry.install(NoOpLogProvider)
    }

    @Test
    fun logForwardsEntryToInstalledProvider() {
        val provider = RecordingLogProvider()
        LogRegistry.install(provider)

        debug(
            tag = baseLogTag,
            throwable = IllegalStateException("failure"),
            attributes = listOf(LogAttribute("source", "test")),
        ) {
            "message"
        }

        val entry = provider.entries.single()
        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals(baseLogTag, entry.tag)
        assertEquals("message", entry.message)
        assertEquals("failure", entry.throwable?.message)
        assertEquals(listOf(LogAttribute("source", "test")), entry.attributes)
    }

    @Test
    fun filteredLogDoesNotEvaluateMessage() {
        val provider = RecordingLogProvider(loggable = false)
        LogRegistry.install(provider)
        var evaluated = false

        info {
            evaluated = true
            "should not be built"
        }

        assertFalse(evaluated)
        assertTrue(provider.entries.isEmpty())
    }

    @Test
    fun childCreatesModuleSpecificTag() {
        assertEquals(
            LogTags.moduleTag(":net:retrofit"),
            LogTags.child(LogTags.moduleTag(":net"), "retrofit"),
        )
    }

    @Test
    fun generatedModuleMetadataUsesCurrentModulePath() {
        assertEquals(":base", ModuleMetadata.path)
        assertEquals("mylog-base", baseLogTag)
    }

    @Test
    fun moduleTagConvertsGradlePathToLogTag() {
        assertEquals("mylog-github-model-core", LogTags.moduleTag(":github:model:core"))
        assertEquals("mylog-app-feature-mine", LogTags.moduleTag(":app:feature:mine"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun childRejectsBlankSegment() {
        LogTags.child(LogTags.moduleTag(":net"), " ")
    }

    private companion object {
        val baseLogTag = LogTags.moduleTag(ModuleMetadata.path)
    }

    private class RecordingLogProvider(
        private val loggable: Boolean = true,
    ) : LogProvider {
        val entries = mutableListOf<LogEntry>()

        override fun isLoggable(
            level: LogLevel,
            tag: String?,
        ): Boolean = loggable

        override fun log(entry: LogEntry) {
            entries += entry
        }
    }
}
