package com.tospery.base.performance

import com.tospery.base.logging.LogLevel

/**
 * 稳定、厂商无关的性能问题类型。
 *
 * 类型会成为外部监控协议的一部分，发布后不应随意改名。
 */
@JvmInline
value class PerformanceIssueType(
    val value: String,
) {
    init {
        require(IDENTIFIER_PATTERN.matches(value)) {
            "Performance issue type must use lowercase letters, digits, and underscores."
        }
    }

    private companion object {
        val IDENTIFIER_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
    }
}

/**
 * 主动上报的非致命问题内容。
 */
sealed interface PerformanceFailure {
    data class CaughtException(
        val cause: Throwable,
    ) : PerformanceFailure

    data class Message(
        val value: String,
    ) : PerformanceFailure {
        init {
            require(value.isNotBlank()) {
                "Performance failure message must not be blank."
            }
        }
    }
}

/**
 * 可选诊断数据。
 *
 * 两项默认关闭，因为系统日志和完整线程堆栈可能包含额外信息，
 * 且采集全部线程可能短暂挂起线程。
 */
data class PerformanceDiagnostics(
    val includeSystemLog: Boolean = false,
    val includeAllThreadStacks: Boolean = false,
)

/**
 * 主动上报的非致命性能或稳定性问题。
 */
data class PerformanceIssue(
    val type: PerformanceIssueType,
    val failure: PerformanceFailure,
    val diagnostics: PerformanceDiagnostics = PerformanceDiagnostics(),
)

/**
 * 稳定、厂商无关的性能诊断上下文字段名。
 */
@JvmInline
value class PerformanceContextName(
    val value: String,
) {
    init {
        require(IDENTIFIER_PATTERN.matches(value)) {
            "Performance context name must use lowercase letters, digits, and underscores."
        }
    }

    private companion object {
        val IDENTIFIER_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
    }
}

data class PerformanceContextAttribute(
    val name: PerformanceContextName,
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Performance context value must not be blank."
        }
    }
}

/**
 * 写入性能监控故障上下文的诊断日志。
 */
data class PerformanceDiagnosticLog(
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    init {
        require(tag.isNotBlank()) {
            "Performance diagnostic log tag must not be blank."
        }
        require(message.isNotBlank()) {
            "Performance diagnostic log message must not be blank."
        }
    }
}

/**
 * 厂商无关的性能监控端口。
 *
 * 自动崩溃、ANR、卡顿、启动、内存等采集能力由具体 adapter 在初始化阶段配置；
 * 此接口只暴露业务代码可能主动使用的非致命问题、上下文和诊断日志能力。
 *
 * 调用方不得在问题、上下文字段或诊断日志中放入凭据、授权码、请求头、
 * 请求正文、原始 URL、用户生成内容或其他敏感信息。
 */
interface PerformanceMonitor {
    fun isEnabled(): Boolean = true

    fun report(issue: PerformanceIssue)

    fun setContext(attribute: PerformanceContextAttribute)

    fun recordLog(entry: PerformanceDiagnosticLog)
}

object NoOpPerformanceMonitor : PerformanceMonitor {
    override fun isEnabled(): Boolean = false

    override fun report(issue: PerformanceIssue) = Unit

    override fun setContext(attribute: PerformanceContextAttribute) = Unit

    override fun recordLog(entry: PerformanceDiagnosticLog) = Unit
}

/**
 * 将性能监控信号分发给所有已启用的厂商 Adapter。
 *
 * App 始终依赖此组合器，后续新增其他厂商 Adapter 时只需追加实例，
 * 不需要修改业务调用方。
 */
class CompositePerformanceMonitor(
    monitors: List<PerformanceMonitor>,
) : PerformanceMonitor {
    private val monitors = monitors.toList()

    override fun isEnabled(): Boolean =
        monitors.any(PerformanceMonitor::isEnabled)

    override fun report(issue: PerformanceIssue) {
        enabledMonitors().forEach { monitor ->
            monitor.report(issue)
        }
    }

    override fun setContext(attribute: PerformanceContextAttribute) {
        enabledMonitors().forEach { monitor ->
            monitor.setContext(attribute)
        }
    }

    override fun recordLog(entry: PerformanceDiagnosticLog) {
        enabledMonitors().forEach { monitor ->
            monitor.recordLog(entry)
        }
    }

    private fun enabledMonitors(): Sequence<PerformanceMonitor> =
        monitors
            .asSequence()
            .filter(PerformanceMonitor::isEnabled)
}
