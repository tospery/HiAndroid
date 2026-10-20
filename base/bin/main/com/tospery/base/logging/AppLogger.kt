package com.tospery.base.logging

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class LogAttribute(
    val key: String,
    val value: String,
)

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val tag: String? = null,
    val throwable: Throwable? = null,
    val attributes: List<LogAttribute> = emptyList(),
)

/**
 * 迁移期兼容的日志接口。
 *
 * 新代码应直接使用文件级日志函数；该接口保留给尚未迁移的外部调用方。
 */
interface AppLogger : LogProvider {
    override fun isLoggable(level: LogLevel, tag: String?): Boolean = true

    override fun log(entry: LogEntry)

    fun log(
        level: LogLevel,
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) {
        if (!isLoggable(level = level, tag = tag)) return

        log(
            LogEntry(
                level = level,
                tag = tag,
                throwable = throwable,
                attributes = attributes,
                message = message(),
            ),
        )
    }

    fun verbose(
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) = log(LogLevel.VERBOSE, tag, throwable, attributes, message)

    fun debug(
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) = log(LogLevel.DEBUG, tag, throwable, attributes, message)

    fun info(
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) = log(LogLevel.INFO, tag, throwable, attributes, message)

    fun warning(
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) = log(LogLevel.WARNING, tag, throwable, attributes, message)

    fun error(
        tag: String? = null,
        throwable: Throwable? = null,
        attributes: List<LogAttribute> = emptyList(),
        message: () -> String,
    ) = log(LogLevel.ERROR, tag, throwable, attributes, message)
}

object NoOpAppLogger : AppLogger {
    override fun isLoggable(level: LogLevel, tag: String?): Boolean = false

    override fun log(entry: LogEntry) = Unit
}
