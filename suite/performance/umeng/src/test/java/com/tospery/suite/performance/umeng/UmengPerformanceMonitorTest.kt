package com.tospery.suite.performance.umeng

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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengPerformanceMonitorTest {
    @Test
    fun configurationUsesPrivacyPreservingDefaultsAndValidatesLimits() {
        val configuration = UmengPerformanceConfiguration()

        assertTrue(configuration.crash.javaCrashEnabled)
        assertTrue(configuration.crash.nativeCrashEnabled)
        assertTrue(configuration.crash.anrEnabled)
        assertTrue(configuration.automatic.stallEnabled)
        assertTrue(configuration.automatic.launchEnabled)
        assertTrue(configuration.automatic.memoryEnabled)
        assertTrue(configuration.automatic.nativePageEnabled)
        assertTrue(configuration.automatic.powerEnabled)

        assertFalse(configuration.automatic.networkEnabled)
        assertFalse(configuration.automatic.h5PageEnabled)
        assertFalse(configuration.automatic.flutterEnabled)
        assertFalse(configuration.automatic.memoryLeakEnabled)
        assertFalse(configuration.diagnostics.codeLogEnabled)
        assertFalse(configuration.diagnostics.localLogBackupEnabled)
        assertFalse(
            configuration.diagnostics
                .includeAllThreadStacksInAutomaticReports,
        )

        assertThrows(IllegalArgumentException::class.java) {
            UmengAutomaticPerformanceConfiguration(
                stallThresholdMillis = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UmengAutomaticPerformanceConfiguration(
                stallThresholdMillis = 4_001,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UmengPerformanceDiagnosticConfiguration(
                customLogCacheSize = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UmengPerformanceDiagnosticConfiguration(
                customLogCacheSize = 101,
            )
        }
    }

    @Test
    fun sharedInitializationLifecycleIsOrderedAndIdempotent() {
        val sdk = RecordingUmengPerformanceSdk()
        val configuration =
            UmengPerformanceConfiguration(
                automatic =
                    UmengAutomaticPerformanceConfiguration(
                        networkEnabled = true,
                    ),
            )
        val monitor =
            UmengPerformanceMonitor(
                configuration = configuration,
                sdk = sdk,
            )

        monitor.configureBeforeInitialization()
        monitor.configureBeforeInitialization()

        assertEquals(configuration, sdk.configuration)
        assertEquals(1, sdk.configureCount)
        assertFalse(monitor.isEnabled())

        monitor.onInitialized()
        monitor.onInitialized()

        assertTrue(monitor.isEnabled())
        assertEquals(1, sdk.configureCount)
    }

    @Test
    fun reportsCaughtExceptionsAndMessagesOnlyAfterInitialization() {
        val sdk = RecordingUmengPerformanceSdk()
        val monitor =
            UmengPerformanceMonitor(
                configuration = UmengPerformanceConfiguration(),
                sdk = sdk,
            )

        monitor.report(messageIssue(type = "ignored_before_initialization"))
        assertTrue(sdk.messageReports.isEmpty())

        monitor.configureBeforeInitialization()
        monitor.onInitialized()

        val cause = IllegalStateException("safe diagnostic")
        monitor.report(
            PerformanceIssue(
                type = PerformanceIssueType("repository_load_failed"),
                failure = PerformanceFailure.CaughtException(cause),
                diagnostics =
                    PerformanceDiagnostics(
                        includeSystemLog = true,
                        includeAllThreadStacks = false,
                    ),
            ),
        )
        monitor.report(
            PerformanceIssue(
                type = PerformanceIssueType("cache_write_failed"),
                failure = PerformanceFailure.Message("cache write failed"),
                diagnostics =
                    PerformanceDiagnostics(
                        includeSystemLog = false,
                        includeAllThreadStacks = true,
                    ),
            ),
        )

        val caughtReport = sdk.caughtReports.single()
        assertEquals("repository_load_failed", caughtReport.type)
        assertSame(cause, caughtReport.cause)
        assertTrue(caughtReport.includeSystemLog)
        assertFalse(caughtReport.includeAllThreadStacks)

        val messageReport = sdk.messageReports.single()
        assertEquals("cache_write_failed", messageReport.type)
        assertEquals("cache write failed", messageReport.message)
        assertFalse(messageReport.includeSystemLog)
        assertTrue(messageReport.includeAllThreadStacks)
    }

    @Test
    fun contextAndDiagnosticLogsRespectSdkLimits() {
        val sdk = RecordingUmengPerformanceSdk()
        val monitor =
            initializedMonitor(
                sdk = sdk,
                configuration =
                    UmengPerformanceConfiguration(
                        diagnostics =
                            UmengPerformanceDiagnosticConfiguration(
                                codeLogEnabled = true,
                            ),
                    ),
            )

        repeat(10) { index ->
            monitor.setContext(
                PerformanceContextAttribute(
                    name = PerformanceContextName("context_$index"),
                    value =
                        if (index == 0) {
                            "值".repeat(500)
                        } else {
                            "value_$index"
                        },
                ),
            )
        }
        monitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("context_10"),
                value = "ignored",
            ),
        )
        monitor.setContext(
            PerformanceContextAttribute(
                name = PerformanceContextName("context_0"),
                value = "updated",
            ),
        )

        assertEquals(11, sdk.contexts.size)
        assertTrue(
            sdk.contexts.first().second
                .toByteArray(Charsets.UTF_8)
                .size <= 1_024,
        )
        assertEquals("context_0" to "updated", sdk.contexts.last())

        LogLevel.entries.forEach { level ->
            monitor.recordLog(
                PerformanceDiagnosticLog(
                    level = level,
                    tag = "标签".repeat(64),
                    message = "诊断".repeat(1_500),
                ),
            )
        }

        assertEquals(
            UmengDiagnosticLogLevel.entries,
            sdk.logs.map(RecordedDiagnosticLog::level),
        )
        sdk.logs.forEach { log ->
            assertTrue(log.tag.toByteArray(Charsets.UTF_8).size <= 64)
            assertTrue(log.message.toByteArray(Charsets.UTF_8).size <= 2_048)
        }
    }

    @Test
    fun diagnosticLogsRemainDisabledByDefault() {
        val sdk = RecordingUmengPerformanceSdk()
        val monitor = initializedMonitor(sdk)

        monitor.recordLog(
            PerformanceDiagnosticLog(
                level = LogLevel.INFO,
                tag = "performance",
                message = "ignored",
            ),
        )

        assertTrue(sdk.logs.isEmpty())
    }

    @Test
    fun privacyDenialPermanentlyDisablesCurrentMonitorInstance() {
        val sdk = RecordingUmengPerformanceSdk()
        val monitor = initializedMonitor(sdk)

        monitor.report(messageIssue(type = "before_denial"))
        monitor.onPrivacyConsentDenied()
        monitor.onPrivacyConsentDenied()
        monitor.report(messageIssue(type = "after_denial"))

        assertFalse(monitor.isEnabled())
        assertEquals(1, sdk.disableCount)
        assertEquals(
            listOf("before_denial"),
            sdk.messageReports.map(RecordedMessageReport::type),
        )
    }

    @Test
    fun sdkFailuresDoNotEscapeAdapterBoundary() {
        val configurationFailureSdk =
            RecordingUmengPerformanceSdk(
                failConfiguration = true,
            )
        val configurationFailureMonitor =
            UmengPerformanceMonitor(
                configuration = UmengPerformanceConfiguration(),
                sdk = configurationFailureSdk,
            )

        configurationFailureMonitor.configureBeforeInitialization()
        configurationFailureMonitor.onInitialized()

        assertFalse(configurationFailureMonitor.isEnabled())

        val reportFailureSdk = RecordingUmengPerformanceSdk()
        val reportFailureMonitor = initializedMonitor(reportFailureSdk)
        reportFailureSdk.failReports = true

        reportFailureMonitor.report(
            messageIssue(type = "sdk_report_failed"),
        )

        assertTrue(reportFailureMonitor.isEnabled())
    }

    private fun initializedMonitor(
        sdk: RecordingUmengPerformanceSdk,
        configuration: UmengPerformanceConfiguration =
            UmengPerformanceConfiguration(),
    ): UmengPerformanceMonitor =
        UmengPerformanceMonitor(
            configuration = configuration,
            sdk = sdk,
        ).also { monitor ->
            monitor.configureBeforeInitialization()
            monitor.onInitialized()
        }

    private fun messageIssue(type: String): PerformanceIssue =
        PerformanceIssue(
            type = PerformanceIssueType(type),
            failure = PerformanceFailure.Message("safe diagnostic"),
        )
}

private class RecordingUmengPerformanceSdk(
    private val failConfiguration: Boolean = false,
) : UmengPerformanceSdk {
    var configuration: UmengPerformanceConfiguration? = null
    var configureCount: Int = 0
    var disableCount: Int = 0
    var failReports: Boolean = false

    val caughtReports = mutableListOf<RecordedCaughtReport>()
    val messageReports = mutableListOf<RecordedMessageReport>()
    val contexts = mutableListOf<Pair<String, String>>()
    val logs = mutableListOf<RecordedDiagnosticLog>()

    override fun configure(configuration: UmengPerformanceConfiguration) {
        configureCount += 1
        if (failConfiguration) {
            throw IllegalStateException("configuration failed")
        }
        this.configuration = configuration
    }

    override fun disable() {
        disableCount += 1
    }

    override fun reportCaughtException(
        type: String,
        cause: Throwable,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    ) {
        if (failReports) {
            throw IllegalStateException("report failed")
        }
        caughtReports +=
            RecordedCaughtReport(
                type = type,
                cause = cause,
                includeSystemLog = includeSystemLog,
                includeAllThreadStacks = includeAllThreadStacks,
            )
    }

    override fun reportMessage(
        type: String,
        message: String,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    ) {
        if (failReports) {
            throw IllegalStateException("report failed")
        }
        messageReports +=
            RecordedMessageReport(
                type = type,
                message = message,
                includeSystemLog = includeSystemLog,
                includeAllThreadStacks = includeAllThreadStacks,
            )
    }

    override fun setContext(
        name: String,
        value: String,
    ) {
        contexts += name to value
    }

    override fun recordLog(
        level: UmengDiagnosticLogLevel,
        tag: String,
        message: String,
    ) {
        logs +=
            RecordedDiagnosticLog(
                level = level,
                tag = tag,
                message = message,
            )
    }
}

private data class RecordedCaughtReport(
    val type: String,
    val cause: Throwable,
    val includeSystemLog: Boolean,
    val includeAllThreadStacks: Boolean,
)

private data class RecordedMessageReport(
    val type: String,
    val message: String,
    val includeSystemLog: Boolean,
    val includeAllThreadStacks: Boolean,
)

private data class RecordedDiagnosticLog(
    val level: UmengDiagnosticLogLevel,
    val tag: String,
    val message: String,
)
