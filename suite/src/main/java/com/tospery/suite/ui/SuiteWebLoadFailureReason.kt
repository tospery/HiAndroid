package com.tospery.suite.ui

/** 内置 Web 页主文档加载失败的低基数分类。 */
enum class SuiteWebLoadFailureReason {
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    NETWORK,
    HTTP,
    TLS,
    RENDERER,
    UNKNOWN,
}

/**
 * 内置 Web 页主文档加载失败详情。
 *
 * [url] 仅供调用方记录本地诊断日志或展示错误上下文，调用方不得将它作为统计属性上报。
 */
data class SuiteWebLoadFailure(
    val url: String,
    val reason: SuiteWebLoadFailureReason,
)
