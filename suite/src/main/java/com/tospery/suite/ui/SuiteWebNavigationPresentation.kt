package com.tospery.suite.ui

/** 用户触发返回时，内置 Web 页应执行的导航动作。 */
internal enum class SuiteWebBackAction {
    GO_BACK,
    EXIT_WEB_PAGE,
}

/** 内置 Web 页 AppBar 的历史导航呈现状态。 */
internal data class SuiteWebNavigationPresentation(
    val backAction: SuiteWebBackAction,
    val showCloseControl: Boolean,
)

/**
 * 仅以 WebView 的真实历史能力决定控制项，避免把重定向或加载事件误作可返回的网页浏览。
 */
internal fun suiteWebNavigationPresentation(
    canGoBack: Boolean,
): SuiteWebNavigationPresentation =
    if (canGoBack) {
        SuiteWebNavigationPresentation(
            backAction = SuiteWebBackAction.GO_BACK,
            showCloseControl = true,
        )
    } else {
        SuiteWebNavigationPresentation(
            backAction = SuiteWebBackAction.EXIT_WEB_PAGE,
            showCloseControl = false,
        )
    }
