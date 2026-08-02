package com.tospery.suite.performance.umeng

import android.os.Bundle
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.error
import com.tospery.base.logging.info
import com.tospery.base.logging.warning
import com.tospery.base.performance.PerformanceContextAttribute
import com.tospery.base.performance.PerformanceDiagnosticLog
import com.tospery.base.performance.PerformanceFailure
import com.tospery.base.performance.PerformanceIssue
import com.tospery.base.performance.PerformanceMonitor
import com.tospery.buildmetadata.module_suite_performance_umeng.ModuleMetadata
import com.tospery.suite.analytics.umeng.UmengInitializationPlugin
import com.umeng.umcrash.UMCrash
import com.umeng.umcrash.customlog.UAPMCustomLog
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class UmengPerformanceCrashConfiguration(
    val javaCrashEnabled: Boolean = true,
    val nativeCrashEnabled: Boolean = true,
    val anrEnabled: Boolean = true,
)

data class UmengAutomaticPerformanceConfiguration(
    val stallEnabled: Boolean = true,
    val launchEnabled: Boolean = true,
    val memoryEnabled: Boolean = true,
    val networkEnabled: Boolean = false,
    val h5PageEnabled: Boolean = false,
    val nativePageEnabled: Boolean = true,
    val powerEnabled: Boolean = true,
    val flutterEnabled: Boolean = false,
    val memoryLeakEnabled: Boolean = false,
    val stallThresholdMillis: Long = DEFAULT_STALL_THRESHOLD_MILLIS,
) {
    init {
        require(stallThresholdMillis in MIN_STALL_THRESHOLD_MILLIS..MAX_STALL_THRESHOLD_MILLIS) {
            "友盟卡顿阈值必须在1至4000毫秒之间。"
        }
    }

    private companion object {
        const val DEFAULT_STALL_THRESHOLD_MILLIS = 2_000L
        const val MIN_STALL_THRESHOLD_MILLIS = 1L
        const val MAX_STALL_THRESHOLD_MILLIS = 4_000L
    }
}

data class UmengPerformanceDiagnosticConfiguration(
    val codeLogEnabled: Boolean = false,
    val localLogBackupEnabled: Boolean = false,
    val includeAllThreadStacksInAutomaticReports: Boolean = false,
    val customLogCacheSize: Int = DEFAULT_CUSTOM_LOG_CACHE_SIZE,
) {
    init {
        require(customLogCacheSize in MIN_CUSTOM_LOG_CACHE_SIZE..MAX_CUSTOM_LOG_CACHE_SIZE) {
            "友盟代码日志缓存条数必须在1至100之间。"
        }
    }

    private companion object {
        const val DEFAULT_CUSTOM_LOG_CACHE_SIZE = 100
        const val MIN_CUSTOM_LOG_CACHE_SIZE = 1
        const val MAX_CUSTOM_LOG_CACHE_SIZE = 100
    }
}

data class UmengPerformanceConfiguration(
    val crash: UmengPerformanceCrashConfiguration =
        UmengPerformanceCrashConfiguration(),
    val automatic: UmengAutomaticPerformanceConfiguration =
        UmengAutomaticPerformanceConfiguration(),
    val diagnostics: UmengPerformanceDiagnosticConfiguration =
        UmengPerformanceDiagnosticConfiguration(),
)

private val umengPerformanceLifecycleLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "lifecycle",
    )

/**
 * 将厂商无关的性能监控协议适配到友盟 U-APM。
 *
 * 功能开关通过 [UmengInitializationPlugin] 在唯一一次友盟公共 SDK 初始化前配置；
 * 主动上报能力仅在公共 SDK 初始化完成且隐私授权未拒绝时可用。
 */
class UmengPerformanceMonitor internal constructor(
    private val configuration: UmengPerformanceConfiguration,
    private val sdk: UmengPerformanceSdk,
) : PerformanceMonitor, UmengInitializationPlugin {
    private var configured = false
    private var initialized = false
    private var permanentlyDisabled = false
    private val registeredContextNames = linkedSetOf<String>()

    @Synchronized
    override fun isEnabled(): Boolean = canCollect()

    @Synchronized
    override fun configureBeforeInitialization() {
        if (configured || permanentlyDisabled) {
            return
        }

        val succeeded =
            callSdk(operation = "configure") {
                sdk.configure(configuration)
            }
        if (!succeeded) {
            return
        }

        configured = true
        info(
            tag = umengPerformanceLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "enabled_feature_count",
                        value = configuration.enabledFeatureCount().toString(),
                    ),
                    LogAttribute(
                        key = "network_enabled",
                        value = configuration.automatic.networkEnabled.toString(),
                    ),
                    LogAttribute(
                        key = "code_log_enabled",
                        value = configuration.diagnostics.codeLogEnabled.toString(),
                    ),
                    LogAttribute(
                        key = "local_log_backup_enabled",
                        value =
                            configuration.diagnostics.localLogBackupEnabled.toString(),
                    ),
                ),
        ) {
            "友盟 U-APM 初始化配置完成。"
        }
    }

    @Synchronized
    override fun onInitialized() {
        if (!configured || initialized || permanentlyDisabled) {
            return
        }

        initialized = true
        info(tag = umengPerformanceLifecycleLogTag) {
            "友盟 U-APM 已随公共 SDK 完成初始化。"
        }
    }

    @Synchronized
    override fun onPrivacyConsentDenied() {
        if (permanentlyDisabled) {
            return
        }

        val wasConfigured = configured
        val wasInitialized = initialized
        initialized = false
        permanentlyDisabled = true
        registeredContextNames.clear()

        if (wasConfigured) {
            callSdk(operation = "disable_after_consent_denied") {
                sdk.disable()
            }
        }

        info(
            tag = umengPerformanceLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "was_configured",
                        value = wasConfigured.toString(),
                    ),
                    LogAttribute(
                        key = "was_initialized",
                        value = wasInitialized.toString(),
                    ),
                ),
        ) {
            "友盟 U-APM 已处理隐私授权拒绝。"
        }
    }

    @Synchronized
    override fun report(issue: PerformanceIssue) {
        if (!canCollect()) {
            return
        }

        val attributes =
            listOf(
                LogAttribute(
                    key = "issue_type",
                    value = issue.type.value,
                ),
            )
        val diagnostics = issue.diagnostics

        when (val failure = issue.failure) {
            is PerformanceFailure.CaughtException -> {
                callSdk(
                    operation = "report_caught_exception",
                    attributes = attributes,
                ) {
                    sdk.reportCaughtException(
                        type = issue.type.value,
                        cause = failure.cause,
                        includeSystemLog = diagnostics.includeSystemLog,
                        includeAllThreadStacks =
                            diagnostics.includeAllThreadStacks,
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
                        includeSystemLog = diagnostics.includeSystemLog,
                        includeAllThreadStacks =
                            diagnostics.includeAllThreadStacks,
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

        val name = attribute.name.value
        val isExistingName = name in registeredContextNames
        if (!isExistingName && registeredContextNames.size >= MAX_CONTEXT_ATTRIBUTE_COUNT) {
            warning(
                tag = umengPerformanceLifecycleLogTag,
                attributes =
                    listOf(
                        LogAttribute(
                            key = "context_name",
                            value = name,
                        ),
                        LogAttribute(
                            key = "context_limit",
                            value = MAX_CONTEXT_ATTRIBUTE_COUNT.toString(),
                        ),
                    ),
            ) {
                "友盟 U-APM 上下文字段已达到数量上限。"
            }
            return
        }

        val succeeded =
            callSdk(
                operation = "set_context",
                attributes =
                    listOf(
                        LogAttribute(
                            key = "context_name",
                            value = name,
                        ),
                    ),
            ) {
                sdk.setContext(
                    name = name,
                    value =
                        attribute.value.truncateToUtf8Bytes(
                            MAX_CONTEXT_VALUE_BYTES,
                        ),
                )
            }

        if (succeeded) {
            registeredContextNames += name
        }
    }

    @Synchronized
    override fun recordLog(entry: PerformanceDiagnosticLog) {
        if (!canCollect() || !configuration.diagnostics.codeLogEnabled) {
            return
        }

        callSdk(operation = "record_diagnostic_log") {
            sdk.recordLog(
                level = entry.level.toUmengDiagnosticLogLevel(),
                tag = entry.tag.truncateToUtf8Bytes(MAX_DIAGNOSTIC_LOG_TAG_BYTES),
                message =
                    entry.message.truncateToUtf8Bytes(
                        MAX_DIAGNOSTIC_LOG_MESSAGE_BYTES,
                    ),
            )
        }
    }

    private fun canCollect(): Boolean = initialized && !permanentlyDisabled

    private inline fun callSdk(
        operation: String,
        attributes: List<LogAttribute> = emptyList(),
        action: () -> Unit,
    ): Boolean {
        return try {
            action()
            true
        } catch (throwable: Throwable) {
            error(
                tag = umengPerformanceLifecycleLogTag,
                throwable = throwable,
                attributes =
                    listOf(
                        LogAttribute(
                            key = "operation",
                            value = operation,
                        ),
                    ) + attributes,
            ) {
                "友盟 U-APM 调用失败。"
            }
            false
        }
    }

    companion object {
        fun create(
            configuration: UmengPerformanceConfiguration =
                UmengPerformanceConfiguration(),
        ): UmengPerformanceMonitor =
            UmengPerformanceMonitor(
                configuration = configuration,
                sdk = AndroidUmengPerformanceSdk,
            )

        private const val MAX_CONTEXT_ATTRIBUTE_COUNT = 10
        private const val MAX_CONTEXT_VALUE_BYTES = 1_024
        private const val MAX_DIAGNOSTIC_LOG_TAG_BYTES = 64
        private const val MAX_DIAGNOSTIC_LOG_MESSAGE_BYTES = 2_048
    }
}

internal interface UmengPerformanceSdk {
    fun configure(configuration: UmengPerformanceConfiguration)

    fun disable()

    fun reportCaughtException(
        type: String,
        cause: Throwable,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    )

    fun reportMessage(
        type: String,
        message: String,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    )

    fun setContext(
        name: String,
        value: String,
    )

    fun recordLog(
        level: UmengDiagnosticLogLevel,
        tag: String,
        message: String,
    )
}

internal enum class UmengDiagnosticLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

private object AndroidUmengPerformanceSdk : UmengPerformanceSdk {
    override fun configure(configuration: UmengPerformanceConfiguration) {
        UMCrash.initConfig(configuration.toUmengBundle())
        UMCrash.enableLogBackup(
            configuration.diagnostics.localLogBackupEnabled,
        )
        UMCrash.enableDumpAllStackTrace(
            configuration.diagnostics.includeAllThreadStacksInAutomaticReports,
        )
        UAPMCustomLog.setCache(
            configuration.diagnostics.customLogCacheSize,
        )
    }

    @Suppress("DEPRECATION")
    override fun disable() {
        UMCrash.initConfig(disabledUmengBundle())
        // U-APM 2.0.8 没有统一 shutdown API；授权撤回后同时关闭仍可运行时切换的采集器。
        UMCrash.enableNativeLog(false)
        UMCrash.enableANRLog(false)
        UMCrash.enableMemoryMonitor(false)
        UMCrash.enableLogBackup(false)
        UMCrash.enableDumpAllStackTrace(false)
    }

    override fun reportCaughtException(
        type: String,
        cause: Throwable,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    ) {
        UMCrash.generateCustomLog(
            cause,
            type,
            includeSystemLog,
            includeAllThreadStacks,
        )
    }

    override fun reportMessage(
        type: String,
        message: String,
        includeSystemLog: Boolean,
        includeAllThreadStacks: Boolean,
    ) {
        UMCrash.generateCustomLog(
            message,
            type,
            includeSystemLog,
            includeAllThreadStacks,
        )
    }

    override fun setContext(
        name: String,
        value: String,
    ) {
        UMCrash.addCustomInfo(name, value)
    }

    override fun recordLog(
        level: UmengDiagnosticLogLevel,
        tag: String,
        message: String,
    ) {
        when (level) {
            UmengDiagnosticLogLevel.VERBOSE -> UAPMCustomLog.v(tag, message)
            UmengDiagnosticLogLevel.DEBUG -> UAPMCustomLog.d(tag, message)
            UmengDiagnosticLogLevel.INFO -> UAPMCustomLog.i(tag, message)
            UmengDiagnosticLogLevel.WARNING -> UAPMCustomLog.w(tag, message)
            UmengDiagnosticLogLevel.ERROR -> UAPMCustomLog.e(tag, message)
        }
    }
}

private fun UmengPerformanceConfiguration.toUmengBundle(): Bundle =
    Bundle().apply {
        putBoolean(
            UMCrash.KEY_ENABLE_CRASH_JAVA,
            crash.javaCrashEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_CRASH_NATIVE,
            crash.nativeCrashEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_ANR,
            crash.anrEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_PA,
            automatic.stallEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_LAUNCH,
            automatic.launchEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_MEM,
            automatic.memoryEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_NET,
            automatic.networkEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_H5PAGE,
            automatic.h5PageEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_PAGE,
            automatic.nativePageEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_POWER,
            automatic.powerEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_FLUTTER,
            automatic.flutterEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_MEMLEAK,
            automatic.memoryLeakEnabled,
        )
        putBoolean(
            UMCrash.KEY_ENABLE_CODE_LOG,
            diagnostics.codeLogEnabled,
        )
        putLong(
            UMCrash.KEY_PA_TIMEOUT_TIME,
            automatic.stallThresholdMillis,
        )
    }

private fun disabledUmengBundle(): Bundle =
    Bundle().apply {
        putBoolean(UMCrash.KEY_ENABLE_CRASH_JAVA, false)
        putBoolean(UMCrash.KEY_ENABLE_CRASH_NATIVE, false)
        putBoolean(UMCrash.KEY_ENABLE_ANR, false)
        putBoolean(UMCrash.KEY_ENABLE_PA, false)
        putBoolean(UMCrash.KEY_ENABLE_LAUNCH, false)
        putBoolean(UMCrash.KEY_ENABLE_MEM, false)
        putBoolean(UMCrash.KEY_ENABLE_NET, false)
        putBoolean(UMCrash.KEY_ENABLE_H5PAGE, false)
        putBoolean(UMCrash.KEY_ENABLE_PAGE, false)
        putBoolean(UMCrash.KEY_ENABLE_POWER, false)
        putBoolean(UMCrash.KEY_ENABLE_FLUTTER, false)
        putBoolean(UMCrash.KEY_ENABLE_MEMLEAK, false)
        putBoolean(UMCrash.KEY_ENABLE_CODE_LOG, false)
        putLong(UMCrash.KEY_PA_TIMEOUT_TIME, 2_000L)
    }

private fun LogLevel.toUmengDiagnosticLogLevel(): UmengDiagnosticLogLevel =
    when (this) {
        LogLevel.VERBOSE -> UmengDiagnosticLogLevel.VERBOSE
        LogLevel.DEBUG -> UmengDiagnosticLogLevel.DEBUG
        LogLevel.INFO -> UmengDiagnosticLogLevel.INFO
        LogLevel.WARNING -> UmengDiagnosticLogLevel.WARNING
        LogLevel.ERROR -> UmengDiagnosticLogLevel.ERROR
    }

private fun UmengPerformanceConfiguration.enabledFeatureCount(): Int =
    listOf(
        crash.javaCrashEnabled,
        crash.nativeCrashEnabled,
        crash.anrEnabled,
        automatic.stallEnabled,
        automatic.launchEnabled,
        automatic.memoryEnabled,
        automatic.networkEnabled,
        automatic.h5PageEnabled,
        automatic.nativePageEnabled,
        automatic.powerEnabled,
        automatic.flutterEnabled,
        automatic.memoryLeakEnabled,
        diagnostics.codeLogEnabled,
    ).count { it }

private fun String.truncateToUtf8Bytes(maxBytes: Int): String {
    val bytes = toByteArray(StandardCharsets.UTF_8)
    if (bytes.size <= maxBytes) {
        return this
    }

    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.IGNORE)
        .onUnmappableCharacter(CodingErrorAction.IGNORE)
        .decode(ByteBuffer.wrap(bytes, 0, maxBytes))
        .toString()
}
