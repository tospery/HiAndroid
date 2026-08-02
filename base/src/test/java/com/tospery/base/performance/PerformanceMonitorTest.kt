package com.tospery.base.performance

import com.tospery.base.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PerformanceMonitorTest {
    @Test
    fun stableIdentifiersAcceptLowercaseProtocolNames() {
        assertEquals(
            "repository_load_failed",
            PerformanceIssueType("repository_load_failed").value,
        )
        assertEquals(
            "build_type",
            PerformanceContextName("build_type").value,
        )
    }

    @Test
    fun stableIdentifiersRejectUnsafeNames() {
        listOf(
            "",
            "Crash",
            "contains-dash",
            "contains space",
            "1starts_with_number",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                PerformanceIssueType(value)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PerformanceContextName(value)
            }
        }
    }

    @Test
    fun issueDefaultsToPrivacyPreservingDiagnostics() {
        val cause = IllegalStateException("safe diagnostic")
        val issue =
            PerformanceIssue(
                type = PerformanceIssueType("repository_load_failed"),
                failure = PerformanceFailure.CaughtException(cause),
            )

        assertSame(
            cause,
            (issue.failure as PerformanceFailure.CaughtException).cause,
        )
        assertFalse(issue.diagnostics.includeSystemLog)
        assertFalse(issue.diagnostics.includeAllThreadStacks)
    }

    @Test
    fun blankDiagnosticValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceFailure.Message(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceContextAttribute(
                name = PerformanceContextName("build_type"),
                value = "",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "",
                message = "initialized",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "performance",
                message = " ",
            )
        }
    }

    @Test
    fun noOpMonitorAcceptsOperationsAndRemainsDisabled() {
        NoOpPerformanceMonitor.report(
            PerformanceIssue(
                type = PerformanceIssueType("ignored_failure"),
                failure = PerformanceFailure.Message("ignored"),
            ),
        )
        NoOpPerformanceMonitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("build_type"),
                value = "debug",
            ),
        )
        NoOpPerformanceMonitor.recordLog(
            PerformanceDiagnosticLog(
                level = LogLevel.DEBUG,
                tag = "performance",
                message = "ignored",
            ),
        )

        assertFalse(NoOpPerformanceMonitor.isEnabled())
    }
}
