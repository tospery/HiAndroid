package com.tospery.base.logging

/**
 * 日志输出实现的抽象。
 *
 * 具体输出方式由 suite 或应用层提供，base 不依赖 Timber、Android Log 等实现。
 */
interface LogProvider {
    fun isLoggable(
        level: LogLevel,
        tag: String?,
    ): Boolean = true

    fun log(entry: LogEntry)
}

/**
 * 应用尚未安装具体日志实现时的安全兜底。
 */
object NoOpLogProvider : LogProvider {
    override fun isLoggable(
        level: LogLevel,
        tag: String?,
    ): Boolean = false

    override fun log(entry: LogEntry) = Unit
}

/**
 * 全局日志实现注册中心。
 */
object LogRegistry {
    @Volatile
    private var provider: LogProvider = NoOpLogProvider

    fun install(provider: LogProvider) {
        this.provider = provider
    }

    internal fun current(): LogProvider = provider
}

fun isLoggable(
    level: LogLevel,
    tag: String? = null,
): Boolean = LogRegistry.current().isLoggable(level = level, tag = tag)

fun log(
    level: LogLevel = LogLevel.DEBUG,
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) {
    val provider = LogRegistry.current()

    if (!provider.isLoggable(level = level, tag = tag)) return

    provider.log(
        LogEntry(
            level = level,
            message = message(),
            tag = tag,
            throwable = throwable,
            attributes = attributes,
        ),
    )
}

fun verbose(
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) = log(
    level = LogLevel.VERBOSE,
    tag = tag,
    throwable = throwable,
    attributes = attributes,
    message = message,
)

fun debug(
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) = log(
    level = LogLevel.DEBUG,
    tag = tag,
    throwable = throwable,
    attributes = attributes,
    message = message,
)

fun info(
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) = log(
    level = LogLevel.INFO,
    tag = tag,
    throwable = throwable,
    attributes = attributes,
    message = message,
)

fun warning(
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) = log(
    level = LogLevel.WARNING,
    tag = tag,
    throwable = throwable,
    attributes = attributes,
    message = message,
)

fun error(
    tag: String? = null,
    throwable: Throwable? = null,
    attributes: List<LogAttribute> = emptyList(),
    message: () -> String,
) = log(
    level = LogLevel.ERROR,
    tag = tag,
    throwable = throwable,
    attributes = attributes,
    message = message,
)
