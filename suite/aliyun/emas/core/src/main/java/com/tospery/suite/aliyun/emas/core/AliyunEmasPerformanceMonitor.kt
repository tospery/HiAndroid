package com.tospery.suite.aliyun.emas.core

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.error
import com.tospery.base.logging.warning
import com.tospery.base.performance.PerformanceContextAttribute
import com.tospery.base.performance.PerformanceDiagnosticLog
import com.tospery.base.performance.PerformanceFailure
import com.tospery.base.performance.PerformanceIssue
import com.tospery.base.performance.PerformanceMonitor
import com.tospery.buildmetadata.module_suite_aliyun_emas_core.ModuleMetadata
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private val aliyunEmasPerformanceLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "performance",
    )

/** 将厂商无关的性能监控端口适配到阿里云 EMAS 移动监控。 */
class AliyunEmasPerformanceMonitor internal constructor(
    private val configuration: AliyunEmasConfiguration,
    private val canCollect: () -> Boolean,
    private val sdk: AliyunEmasSdk,
) : PerformanceMonitor {
    @Synchronized
    override fun isEnabled(): Boolean = canCollect()

    @Synchronized
    override fun report(issue: PerformanceIssue) {
        if (!canCollect() || !configuration.hasComponent(AliyunEmasComponent.CRASH_ANALYSIS)) {
            return
        }

        val attributes =
            listOf(
                LogAttribute(
                    key = "issue_type",
                    value = issue.type.value,
                ),
            )
        if (issue.diagnostics.includeSystemLog) {
            warning(
                tag = aliyunEmasPerformanceLogTag,
                attributes = attributes,
            ) {
                "阿里云 EMAS 自定义异常不提供按次附加系统日志的接口。"
            }
        }
        if (issue.diagnostics.includeAllThreadStacks) {
            warning(
                tag = aliyunEmasPerformanceLogTag,
                attributes = attributes,
            ) {
                "阿里云 EMAS 自定义异常只接收当前异常的堆栈。"
            }
        }

        when (val failure = issue.failure) {
            is PerformanceFailure.CaughtException -> {
                callSdk(
                    operation = "report_caught_exception",
                    attributes = attributes,
                ) {
                    sdk.reportCaughtException(
                        type = issue.type.value,
                        cause = failure.cause,
                    )
                }
            }

            is PerformanceFailure.Message -> {
                callSdk(
                    operation = "report_message",
                    attributes = attributes,
                ) {
                    sdk.reportMessage(
                        type = issue.type.value,
                        message = failure.value,
                    )
                }
            }
        }
    }

    @Synchronized
    override fun setContext(attribute: PerformanceContextAttribute) {
        if (!canCollect()) {
            return
        }

        callSdk(
            operation = "set_context",
            attributes =
                listOf(
                    LogAttribute(
                        key = "context_name",
                        value = attribute.name.value,
                    ),
                ),
        ) {
            sdk.setContext(
                name = attribute.name.value,
                value = attribute.value.take(MAX_CONTEXT_VALUE_CHARACTERS),
            )
        }
    }

    @Synchronized
    override fun recordLog(entry: PerformanceDiagnosticLog) {
        if (!canCollect()) {
            return
        }

        val tag = entry.tag.truncateToUtf8Bytes(MAX_REMOTE_LOG_TAG_BYTES)
        val message = entry.message.truncateToUtf8Bytes(MAX_REMOTE_LOG_MESSAGE_BYTES)
        val formattedCrashLog =
            "[${entry.level.name}][$tag] $message"
                .truncateToUtf8Bytes(MAX_CRASH_LOG_ENTRY_BYTES)

        if (configuration.hasComponent(AliyunEmasComponent.CRASH_ANALYSIS)) {
            callSdk(operation = "record_crash_log") {
                sdk.recordCrashLog(formattedCrashLog)
            }
        }
        if (configuration.hasComponent(AliyunEmasComponent.REMOTE_LOG)) {
            callSdk(operation = "record_remote_log") {
                sdk.recordRemoteLog(
                    level = entry.level,
                    module = configuration.remoteLogModuleName,
                    tag = tag,
                    message = message,
                )
            }
        }
    }

    private inline fun callSdk(
        operation: String,
        attributes: List<LogAttribute> = emptyList(),
        action: () -> Unit,
    ): Boolean =
        try {
            action()
            true
        } catch (throwable: Throwable) {
            error(
                tag = aliyunEmasPerformanceLogTag,
                attributes =
                    listOf(
                        LogAttribute(
                            key = "operation",
                            value = operation,
                        ),
                        LogAttribute(
                            key = "failure_type",
                            value = throwable.javaClass.name,
                        ),
                    ) + attributes,
            ) {
                "阿里云 EMAS 性能监控调用失败。"
            }
            false
        }

    private companion object {
        const val MAX_CONTEXT_VALUE_CHARACTERS = 1_024
        const val MAX_REMOTE_LOG_TAG_BYTES = 128
        const val MAX_REMOTE_LOG_MESSAGE_BYTES = 4_096
        const val MAX_CRASH_LOG_ENTRY_BYTES = 4_096
    }
}

private fun String.truncateToUtf8Bytes(maxBytes: Int): String {
    val encoded = toByteArray(StandardCharsets.UTF_8)
    if (encoded.size <= maxBytes) {
        return this
    }

    val decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE)
    return decoder.decode(ByteBuffer.wrap(encoded, 0, maxBytes)).toString()
}
