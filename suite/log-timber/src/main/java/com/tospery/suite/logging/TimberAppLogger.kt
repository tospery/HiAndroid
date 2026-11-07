package com.tospery.suite.logging

import com.tospery.base.logging.AppLogger
import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.LogTags
import com.tospery.buildmetadata.module_suite_log_timber.ModuleMetadata
import timber.log.Timber

private val suiteLogTag = LogTags.moduleTag(ModuleMetadata.path)

class TimberAppLogger(
    private val defaultTag: String? = null,
    private val minimumLevel: LogLevel = LogLevel.VERBOSE,
) : AppLogger {
    override fun isLoggable(level: LogLevel, tag: String?): Boolean {
        return level.ordinal >= minimumLevel.ordinal
    }

    override fun log(entry: LogEntry) {
        if (!isLoggable(level = entry.level, tag = entry.tag)) return

        val tree = Timber.tag(entry.tag ?: defaultTag ?: suiteLogTag)
        val message = entry.formatMessage()

        when (entry.level) {
            LogLevel.VERBOSE -> tree.v(entry.throwable, message)
            LogLevel.DEBUG -> tree.d(entry.throwable, message)
            LogLevel.INFO -> tree.i(entry.throwable, message)
            LogLevel.WARNING -> tree.w(entry.throwable, message)
            LogLevel.ERROR -> tree.e(entry.throwable, message)
        }
    }

    private fun LogEntry.formatMessage(): String {
        if (attributes.isEmpty()) return message

        val formattedAttributes = attributes.joinToString(
            prefix = " {",
            postfix = "}",
            separator = ", ",
        ) { attribute ->
            "${attribute.key}=${attribute.value}"
        }

        return message + formattedAttributes
    }
}
