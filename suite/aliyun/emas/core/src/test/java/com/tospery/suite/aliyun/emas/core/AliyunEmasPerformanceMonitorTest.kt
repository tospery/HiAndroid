package com.tospery.suite.aliyun.emas.core

import com.tospery.base.logging.LogLevel
import com.tospery.base.performance.PerformanceContextAttribute
import com.tospery.base.performance.PerformanceContextName
import com.tospery.base.performance.PerformanceDiagnosticLog
import com.tospery.base.performance.PerformanceDiagnostics
import com.tospery.base.performance.PerformanceFailure
import com.tospery.base.performance.PerformanceIssue
import com.tospery.base.performance.PerformanceIssueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AliyunEmasPerformanceMonitorTest {
    @Test
    fun reportsCaughtExceptionsAndMessagesOnlyWhileCollectionIsAllowed() {
        val sdk = RecordingAliyunEmasSdk()
        var collectionAllowed = false
        val monitor = monitor(sdk = sdk, canCollect = { collectionAllowed })
        val cause = IllegalStateException("safe diagnostic")

        monitor.report(messageIssue("ignored_before_consent"))
        assertTrue(sdk.messageReports.isEmpty())

        collectionAllowed = true
        monitor.report(
            PerformanceIssue(
                type = PerformanceIssueType("repository_load_failed"),
                failure = PerformanceFailure.CaughtException(cause),
                diagnostics =
                    PerformanceDiagnostics(
                        includeSystemLog = true,
                        includeAllThreadStacks = true,
                    ),
            ),
        )
        monitor.report(messageIssue("cache_write_failed"))

        assertTrue(monitor.isEnabled())
        assertEquals("repository_load_failed", sdk.caughtReports.single().type)
        assertSame(cause, sdk.caughtReports.single().cause)
        assertEquals("cache_write_failed", sdk.messageReports.single().type)
        assertEquals("safe diagnostic", sdk.messageReports.single().message)
    }

    @Test
    fun contextAndDiagnosticLogsMapToCrashAndRemoteLogApis() {
        val sdk = RecordingAliyunEmasSdk()
        val monitor = monitor(sdk)

        monitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("build_variant"),
                value = "值".repeat(1_100),
            ),
        )
        LogLevel.entries.forEach { level ->
            monitor.recordLog(
                PerformanceDiagnosticLog(
                    level = level,
                    tag = "诊断".repeat(100),
                    message = "日志".repeat(3_000),
                ),
            )
        }

        assertEquals("build_variant", sdk.contexts.single().first)
        assertTrue(sdk.contexts.single().second.length <= 1_024)
        assertEquals(LogLevel.entries, sdk.remoteLogs.map(RecordedRemoteLog::level))
        assertEquals(5, sdk.crashLogs.size)
        sdk.remoteLogs.forEach { log ->
            assertEquals("app", log.module)
            assertTrue(log.tag.toByteArray(Charsets.UTF_8).size <= 128)
            assertTrue(log.message.toByteArray(Charsets.UTF_8).size <= 4_096)
        }
        sdk.crashLogs.forEach { log ->
            assertTrue(log.toByteArray(Charsets.UTF_8).size <= 4_096)
        }
    }

    @Test
    fun componentSelectionControlsActiveReportsAndLogDestinations() {
        val sdk = RecordingAliyunEmasSdk()
        val remoteOnlyMonitor =
            monitor(
                sdk = sdk,
                configuration =
                    configuration(
                        components = setOf(AliyunEmasComponent.REMOTE_LOG),
                    ),
            )

        remoteOnlyMonitor.report(messageIssue("ignored_without_crash_component"))
        remoteOnlyMonitor.recordLog(
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "network",
                message = "request completed",
            ),
        )

        assertTrue(sdk.messageReports.isEmpty())
        assertTrue(sdk.crashLogs.isEmpty())
        assertEquals(1, sdk.remoteLogs.size)
    }

    @Test
    fun sdkFailuresDoNotEscapePerformanceAdapter() {
        val sdk =
            RecordingAliyunEmasSdk(
                failingOperations =
                    setOf(
                        "report_message",
                        "set_context",
                        "record_crash_log",
                        "record_remote_log",
                    ),
            )
        val monitor = monitor(sdk)

        monitor.report(messageIssue("sdk_report_failed"))
        monitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("safe_context"),
                value = "safe",
            ),
        )
        monitor.recordLog(
            PerformanceDiagnosticLog(
                level = LogLevel.ERROR,
                tag = "safe_tag",
                message = "safe message",
            ),
        )

        assertTrue(monitor.isEnabled())
    }

    @Test
    fun disabledCollectionMakesEveryActiveOperationANoOp() {
        val sdk = RecordingAliyunEmasSdk()
        val monitor = monitor(sdk = sdk, canCollect = { false })

        monitor.report(messageIssue("ignored"))
        monitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("ignored_context"),
                value = "ignored",
            ),
        )
        monitor.recordLog(
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "ignored",
                message = "ignored",
            ),
        )

        assertFalse(monitor.isEnabled())
        assertTrue(sdk.calls.isEmpty())
    }

    private fun monitor(
        sdk: RecordingAliyunEmasSdk,
        configuration: AliyunEmasConfiguration = configuration(),
        canCollect: () -> Boolean = { true },
    ): AliyunEmasPerformanceMonitor =
        AliyunEmasPerformanceMonitor(
            configuration = configuration,
            canCollect = canCollect,
            sdk = sdk,
        )

    private fun messageIssue(type: String): PerformanceIssue =
        PerformanceIssue(
            type = PerformanceIssueType(type),
            failure = PerformanceFailure.Message("safe diagnostic"),
        )
}
