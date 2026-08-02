package com.tospery.suite.aliyun.emas.core

import com.tospery.base.logging.LogTags
import com.tospery.buildmetadata.module_suite_aliyun_emas_core.ModuleMetadata

/** EMAS 适配层统一使用的 `mylog-*` 日志标签。 */
internal object AliyunEmasLogTags {
    private val root = LogTags.moduleTag(ModuleMetadata.path)

    val lifecycle = LogTags.child(parent = root, segment = "lifecycle")
    val crashAnalysis = LogTags.child(parent = root, segment = "crash-analysis")
    val performanceAnalysis = LogTags.child(parent = root, segment = "performance-analysis")
    val memoryAnalysis = LogTags.child(parent = root, segment = "memory-analysis")
    val remoteLog = LogTags.child(parent = root, segment = "remote-log")
    val networkAnalysis = LogTags.child(parent = root, segment = "network-analysis")
}

internal val AliyunEmasComponent.logTag: String
    get() =
        when (this) {
            AliyunEmasComponent.CRASH_ANALYSIS -> AliyunEmasLogTags.crashAnalysis
            AliyunEmasComponent.PERFORMANCE_ANALYSIS -> AliyunEmasLogTags.performanceAnalysis
            AliyunEmasComponent.MEMORY_ANALYSIS -> AliyunEmasLogTags.memoryAnalysis
            AliyunEmasComponent.REMOTE_LOG -> AliyunEmasLogTags.remoteLog
        }

internal val AliyunEmasComponent.displayName: String
    get() =
        when (this) {
            AliyunEmasComponent.CRASH_ANALYSIS -> "崩溃分析"
            AliyunEmasComponent.PERFORMANCE_ANALYSIS -> "性能分析"
            AliyunEmasComponent.MEMORY_ANALYSIS -> "内存分析"
            AliyunEmasComponent.REMOTE_LOG -> "远程日志"
        }
