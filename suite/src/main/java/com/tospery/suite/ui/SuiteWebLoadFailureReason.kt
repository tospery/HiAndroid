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

/** Web 加载失败后保留网页内容或提供恢复入口的呈现策略。 */
internal enum class SuiteWebFailurePresentation {
    KEEP_WEB_CONTENT,
    ERROR_PAGE,
    RETRY_SNACKBAR,
}

/**
 * HTTP 错误仍可能包含网站可读的错误页，因此不覆盖其内容。
 *
 * 已成功渲染网页后的后续导航失败则保留原页面，以便用户通过提示重试；
 * 渲染进程失效无法保留内容，始终回退到错误页。
 */
internal fun SuiteWebLoadFailure.presentationAfter(
    hasRenderedDocument: Boolean,
): SuiteWebFailurePresentation =
    when (reason) {
        SuiteWebLoadFailureReason.HTTP -> SuiteWebFailurePresentation.KEEP_WEB_CONTENT
        SuiteWebLoadFailureReason.RENDERER -> SuiteWebFailurePresentation.ERROR_PAGE
        else ->
            if (hasRenderedDocument) {
                SuiteWebFailurePresentation.RETRY_SNACKBAR
            } else {
                SuiteWebFailurePresentation.ERROR_PAGE
            }
    }
