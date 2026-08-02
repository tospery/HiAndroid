package com.tospery.suite.aliyun.emas.core

import com.tospery.base.logging.LogLevel

internal class RecordingAliyunEmasSdk(
    private val startResult: Boolean = true,
    private val failingOperations: Set<String> = emptySet(),
) : AliyunEmasSdk {
    val calls = mutableListOf<String>()
    val userInfos = mutableListOf<AliyunEmasUserInfo?>()
    val caughtReports = mutableListOf<RecordedCaughtReport>()
    val messageReports = mutableListOf<RecordedMessageReport>()
    val contexts = mutableListOf<Pair<String, String>>()
    val crashLogs = mutableListOf<String>()
    val remoteLogs = mutableListOf<RecordedRemoteLog>()

    override fun preStart(configuration: AliyunEmasConfiguration) {
        record("pre_start")
    }

    override fun start(): Boolean {
        record("start")
        return startResult
    }

    override fun applyCollectionConfiguration(
        configuration: AliyunEmasCollectionConfiguration,
    ) {
        record("apply_collection:$configuration")
    }

    override fun disablePrivacyCollection() {
        record("disable_privacy_collection")
    }

    override fun updateRemoteLogLevel(level: LogLevel) {
        record("remote_log_level:$level")
    }

    override fun setUserInfo(userInfo: AliyunEmasUserInfo?) {
        record("set_user:$userInfo")
        userInfos += userInfo
    }

    override fun reportCaughtException(
        type: String,
        cause: Throwable,
    ) {
        record("report_caught_exception")
        caughtReports += RecordedCaughtReport(type = type, cause = cause)
    }

    override fun reportMessage(
        type: String,
        message: String,
    ) {
        record("report_message")
        messageReports += RecordedMessageReport(type = type, message = message)
    }

    override fun setContext(
        name: String,
        value: String,
    ) {
        record("set_context")
        contexts += name to value
    }

    override fun recordCrashLog(message: String) {
        record("record_crash_log")
        crashLogs += message
    }

    override fun recordRemoteLog(
        level: LogLevel,
        module: String,
        tag: String,
        message: String,
    ) {
        record("record_remote_log")
        remoteLogs +=
            RecordedRemoteLog(
                level = level,
                module = module,
                tag = tag,
                message = message,
            )
    }

    private fun record(operation: String) {
        calls += operation
        if (operation.substringBefore(':') in failingOperations) {
            throw IllegalStateException("$operation failed")
        }
    }
}

internal data class RecordedCaughtReport(
    val type: String,
    val cause: Throwable,
)

internal data class RecordedMessageReport(
    val type: String,
    val message: String,
)

internal data class RecordedRemoteLog(
    val level: LogLevel,
    val module: String,
    val tag: String,
    val message: String,
)
