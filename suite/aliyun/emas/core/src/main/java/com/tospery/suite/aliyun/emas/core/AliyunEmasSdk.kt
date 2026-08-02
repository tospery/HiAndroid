package com.tospery.suite.aliyun.emas.core

import android.app.Application
import com.aliyun.emas.apm.Apm
import com.aliyun.emas.apm.ApmOptions
import com.aliyun.emas.apm.crash.ApmCrashAnalysis
import com.aliyun.emas.apm.crash.ApmCrashAnalysisComponent
import com.aliyun.emas.apm.crash.ExceptionModel
import com.aliyun.emas.apm.crash.StackFrame
import com.aliyun.emas.apm.logger.LoggerLevel
import com.aliyun.emas.apm.mem.monitor.ApmMemMonitorComponent
import com.aliyun.emas.apm.performance.ApmPerformanceComponent
import com.aliyun.emas.apm.remote.log.ApmRemoteLog
import com.aliyun.emas.apm.remote.log.ApmRemoteLogComponent
import com.aliyun.emas.apm.remote.log.ApmRemoteLogLevel
import com.aliyun.emas.apm.remote.log.RemoteLogOptions
import com.tospery.base.logging.LogLevel

/** 隔离静态厂商 API，供公共运行时和单元测试复用。 */
internal interface AliyunEmasSdk {
    fun preStart(configuration: AliyunEmasConfiguration)

    fun start(): Boolean

    fun applyCollectionConfiguration(
        configuration: AliyunEmasCollectionConfiguration,
    )

    fun disablePrivacyCollection()

    fun updateRemoteLogLevel(level: LogLevel)

    fun setUserInfo(userInfo: AliyunEmasUserInfo?)

    fun reportCaughtException(
        type: String,
        cause: Throwable,
    )

    fun reportMessage(
        type: String,
        message: String,
    )

    fun setContext(
        name: String,
        value: String,
    )

    fun recordCrashLog(message: String)

    fun recordRemoteLog(
        level: LogLevel,
        module: String,
        tag: String,
        message: String,
    )
}

internal class AndroidAliyunEmasSdk(
    private val application: Application,
) : AliyunEmasSdk {
    override fun preStart(configuration: AliyunEmasConfiguration) {
        val builder =
            ApmOptions.Builder()
                .setApplication(application)
                .setAppKey(configuration.appKey)
                .setAppSecret(configuration.appSecret)
                .setAppRsaSecret(configuration.appRsaSecret)
                .openDebug(configuration.debugLoggingEnabled)

        configuration.channel?.let(builder::setChannel)
        configuration.initialUserInfo?.id?.let(builder::setUserId)
        configuration.initialUserInfo?.nickname?.let(builder::setUserNick)

        configuration.components.forEach { component ->
            when (component) {
                AliyunEmasComponent.CRASH_ANALYSIS ->
                    builder.addComponent(ApmCrashAnalysisComponent::class.java)

                AliyunEmasComponent.PERFORMANCE_ANALYSIS ->
                    builder.addComponent(ApmPerformanceComponent::class.java)

                AliyunEmasComponent.MEMORY_ANALYSIS ->
                    builder.addComponent(ApmMemMonitorComponent::class.java)

                AliyunEmasComponent.REMOTE_LOG ->
                    builder.addComponent(ApmRemoteLogComponent::class.java)
            }
        }

        if (configuration.hasComponent(AliyunEmasComponent.REMOTE_LOG)) {
            builder.addProductOptions(
                RemoteLogOptions.Builder()
                    .setRemoteLogFileMaxSize(
                        configuration.remoteLogCacheSizeMegabytes,
                    ).build(),
            )
        }

        Apm.setLoggerLevel(
            if (configuration.debugLoggingEnabled) {
                LoggerLevel.DEBUG
            } else {
                LoggerLevel.INFO
            },
        )
        Apm.preStart(builder.build())
    }

    override fun start(): Boolean = Apm.start()

    override fun applyCollectionConfiguration(
        configuration: AliyunEmasCollectionConfiguration,
    ) {
        Apm.setPrivacySwitch(configuration.disabledPrivacyMask())
    }

    override fun disablePrivacyCollection() {
        Apm.setPrivacySwitch(ALL_PRIVACY_FIELDS_DISABLED_MASK)
    }

    override fun updateRemoteLogLevel(level: LogLevel) {
        ApmRemoteLog.updateLogLevel(level.toRemoteLogLevel())
    }

    override fun setUserInfo(userInfo: AliyunEmasUserInfo?) {
        Apm.setUserId(userInfo?.id)
        Apm.setUserNick(userInfo?.nickname)
    }

    override fun reportCaughtException(
        type: String,
        cause: Throwable,
    ) {
        ApmCrashAnalysis.getInstance().recordException(
            ExceptionModel.Builder()
                .setName(type)
                .setReason(cause.message ?: cause.javaClass.name)
                .setStackTrace(cause.stackTrace.map(StackTraceElement::toAliyunStackFrame))
                .setCustom(true)
                .setUrgent(false)
                .setQuitApp(false)
                .build(),
        )
    }

    override fun reportMessage(
        type: String,
        message: String,
    ) {
        ApmCrashAnalysis.getInstance().recordException(
            ExceptionModel.Builder()
                .setName(type)
                .setReason(message)
                .setCustom(true)
                .setUrgent(false)
                .setQuitApp(false)
                .build(),
        )
    }

    override fun setContext(
        name: String,
        value: String,
    ) {
        Apm.setCustomKey(name, value)
    }

    override fun recordCrashLog(message: String) {
        ApmCrashAnalysis.getInstance().log(message)
    }

    override fun recordRemoteLog(
        level: LogLevel,
        module: String,
        tag: String,
        message: String,
    ) {
        when (level) {
            LogLevel.VERBOSE -> ApmRemoteLog.v(module, tag, message)
            LogLevel.DEBUG -> ApmRemoteLog.d(module, tag, message)
            LogLevel.INFO -> ApmRemoteLog.i(module, tag, message)
            LogLevel.WARNING -> ApmRemoteLog.w(module, tag, message)
            LogLevel.ERROR -> ApmRemoteLog.e(module, tag, message)
        }
    }
}

private fun AliyunEmasCollectionConfiguration.disabledPrivacyMask(): Int {
    var mask = 0
    if (!collectDeviceModel) mask = mask or Apm.DISABLE_DEVICE_MODEL
    if (!collectOsVersion) mask = mask or Apm.DISABLE_OS_VERSION
    if (!collectScreenResolution) mask = mask or Apm.DISABLE_SCREEN_RESOLUTION
    if (!collectNetworkInfo) mask = mask or Apm.DISABLE_NETWORK_INFO
    return mask
}

private fun LogLevel.toRemoteLogLevel(): ApmRemoteLogLevel =
    when (this) {
        LogLevel.VERBOSE -> ApmRemoteLogLevel.VERBOSE
        LogLevel.DEBUG -> ApmRemoteLogLevel.DEBUG
        LogLevel.INFO -> ApmRemoteLogLevel.INFO
        LogLevel.WARNING -> ApmRemoteLogLevel.WARN
        LogLevel.ERROR -> ApmRemoteLogLevel.ERROR
    }

private fun StackTraceElement.toAliyunStackFrame(): StackFrame =
    StackFrame.Builder()
        .setSymbol("$className.$methodName")
        .setFile(fileName ?: UNKNOWN_STACK_FRAME_FILE)
        .setLine(lineNumber.coerceAtLeast(0))
        .build()

private const val UNKNOWN_STACK_FRAME_FILE = "Unknown"
private val ALL_PRIVACY_FIELDS_DISABLED_MASK =
    Apm.DISABLE_DEVICE_MODEL or
        Apm.DISABLE_OS_VERSION or
        Apm.DISABLE_SCREEN_RESOLUTION or
        Apm.DISABLE_NETWORK_INFO
