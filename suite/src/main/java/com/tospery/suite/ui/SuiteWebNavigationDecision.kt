package com.tospery.suite.ui

/**
 * 宿主对 WebView 主文档导航的处理决定。
 *
 * Suite 只负责执行决定，不理解具体业务路由；GitHub、媒体、文档或系统 URI 等策略由宿主注入。
 */
enum class SuiteWebNavigationDecision {
    /** 仅当目标仍是安全 HTTP(S) URL 时，允许当前 WebView 继续加载。 */
    ALLOW_IN_WEB_VIEW,

    /** 导航已经由宿主处理，WebView 不再加载目标。 */
    CONSUMED,

    /** 明确拒绝该导航。 */
    BLOCKED,
}
