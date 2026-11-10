package com.tospery.suite.logging

import android.util.Log
import com.tospery.base.logging.AppLogger
import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.LogTags
import com.tospery.buildmetadata.module_suite_log_timber.ModuleMetadata
import timber.log.Timber

private val suiteLogTag = LogTags.moduleTag(ModuleMetadata.path)

/**
 * 把日志契约转交给 Timber 的适配器。
 *
 * 完整的 [LogEntry] 作为 Timber 格式参数随调用传递：[LogEntryTree] 可无损取回，
 * 其他 Tree 得到“消息 + 属性”的可读文本。
 *
 * @param supportsSensitiveData 宿主声明已安装的 Tree 能承接凭据与原始正文（例如加密存储，
 *   或宿主明确允许的明文开发诊断）。本库不判断渠道或环境，默认不接收。
 */
class TimberAppLogger(
    private val defaultTag: String? = null,
    private val minimumLevel: LogLevel = LogLevel.VERBOSE,
    override val supportsSensitiveData: Boolean = false,
) : AppLogger {
    override fun isLoggable(level: LogLevel, tag: String?): Boolean {
        return level.ordinal >= minimumLevel.ordinal
    }

    override fun log(entry: LogEntry) {
        if (!isLoggable(level = entry.level, tag = entry.tag)) return

        Timber.tag(entry.tag ?: defaultTag ?: suiteLogTag).log(
            entry.level.toTimberPriority(),
            entry.throwable,
            TimberLogEntry.FORMAT,
            TimberLogEntry(entry),
        )
    }
}

/** 随 Timber 调用传递的完整日志条目；普通 Tree 通过 [toString] 得到可读文本。 */
class TimberLogEntry internal constructor(val entry: LogEntry) {
    override fun toString(): String = entry.readableMessage()

    internal companion object {
        const val FORMAT = "%s"
    }
}

internal fun LogEntry.readableMessage(): String {
    if (attributes.isEmpty()) return message
    return attributes.joinToString(prefix = "$message {", postfix = "}", separator = ", ") { attribute ->
        "${attribute.key}=${attribute.value}"
    }
}

internal fun LogLevel.toTimberPriority(): Int =
    when (this) {
        LogLevel.VERBOSE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARNING -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }

internal fun Int.toLogLevel(): LogLevel =
    when {
        this <= Log.VERBOSE -> LogLevel.VERBOSE
        this == Log.DEBUG -> LogLevel.DEBUG
        this == Log.INFO -> LogLevel.INFO
        this == Log.WARN -> LogLevel.WARNING
        else -> LogLevel.ERROR
    }
