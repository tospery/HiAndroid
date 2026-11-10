package com.tospery.suite.logging

import com.tospery.base.logging.LogEntry
import java.io.PrintWriter
import java.io.StringWriter
import timber.log.Timber

/**
 * 以 [LogEntry] 接收日志的 Timber Tree 基类。
 *
 * 经 [TimberAppLogger] 记录的条目原样到达，包括 tag、属性与异常；第三方直接调用 Timber 的
 * 日志合成为同样的条目，消息已按格式参数展开，异常栈不重复拼入消息。子类仍可覆写
 * `isLoggable(tag, priority)` 过滤级别。
 */
abstract class LogEntryTree : Timber.Tree() {
    private val carried = ThreadLocal<LogEntry?>()

    final override fun formatMessage(message: String, args: Array<out Any?>): String {
        val carrier = args.singleOrNull() as? TimberLogEntry
        if (carrier != null && message == TimberLogEntry.FORMAT) {
            // Timber 在同一线程内先格式化、后调用 log，这里只暂存到紧接着的 log 调用。
            carried.set(carrier.entry)
            return carrier.entry.message
        }
        return super.formatMessage(message, args)
    }

    final override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val entry = carried.get()
        carried.remove()
        logEntry(
            entry ?: LogEntry(
                level = priority.toLogLevel(),
                message = message.withoutStackTrace(t),
                tag = tag,
                throwable = t,
            ),
        )
    }

    /** 接收一条完整日志；实现不应抛出异常，以免影响调用方。 */
    protected abstract fun logEntry(entry: LogEntry)
}

/** Timber 在消息后追加“换行 + 异常栈”；合成条目时去掉，避免与 throwable 重复。 */
private fun String.withoutStackTrace(throwable: Throwable?): String {
    if (throwable == null) return this
    val stackTrace = StringWriter(256).also { writer ->
        PrintWriter(writer, false).use { printer ->
            throwable.printStackTrace(printer)
            printer.flush()
        }
    }.toString()
    return when {
        this == stackTrace -> ""
        endsWith("\n$stackTrace") -> removeSuffix("\n$stackTrace")
        else -> this
    }
}
