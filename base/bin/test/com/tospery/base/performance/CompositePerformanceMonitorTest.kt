package com.tospery.base.performance

import com.tospery.base.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositePerformanceMonitorTest {
    @Test
    fun operationsAreForwardedOnlyToEnabledMonitors() {
        val enabledMonitor = RecordingPerformanceMonitor(enabled = true)
        val disabledMonitor = RecordingPerformanceMonitor(enabled = false)
        val monitor =
            CompositePerformanceMonitor(
                listOf(enabledMonitor, disabledMonitor),
            )
        val issue =
            PerformanceIssue(
                type = PerformanceIssueType("repository_load_failed"),
                failure = PerformanceFailure.Message("safe diagnostic"),
            )
        val attribute =
            PerformanceContextAttribute(
                name = PerformanceContextName("build_type"),
                value = "debug",
            )
        val log =
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "performance",
                message = "initialized",
            )

        monitor.report(issue)
        monitor.setContext(attribute)
        monitor.recordLog(log)

        assertTrue(monitor.isEnabled())
        assertEquals(listOf(issue), enabledMonitor.issues)
        assertEquals(listOf(attribute), enabledMonitor.attributes)
        assertEquals(listOf(log), enabledMonitor.logs)

        assertTrue(disabledMonitor.issues.isEmpty())
        assertTrue(disabledMonitor.attributes.isEmpty())
        assertTrue(disabledMonitor.logs.isEmpty())
    }

    @Test
    fun emptyOrFullyDisabledCompositionRemainsDisabled() {
        assertFalse(CompositePerformanceMonitor(emptyList()).isEnabled())
        assertFalse(
            CompositePerformanceMonitor(
                listOf(RecordingPerformanceMonitor(enabled = false)),
            ).isEnabled(),
        )
    }
}

private class RecordingPerformanceMonitor(
    private val enabled: Boolean,
) : PerformanceMonitor {
    val issues = mutableListOf<PerformanceIssue>()
    val attributes = mutableListOf<PerformanceContextAttribute>()
    val logs = mutableListOf<PerformanceDiagnosticLog>()

    override fun isEnabled(): Boolean = enabled

    override fun report(issue: PerformanceIssue) {
        issues += issue
    }

    override fun setContext(attribute: PerformanceContextAttribute) {
        attributes += attribute
    }

    override fun recordLog(entry: PerformanceDiagnosticLog) {
        logs += entry
    }
}
