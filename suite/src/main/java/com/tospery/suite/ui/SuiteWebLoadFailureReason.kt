package com.tospery.suite.ui

/** 内置 Web 页主文档加载失败的低基数分类，不包含失败 URL。 */
enum class SuiteWebLoadFailureReason {
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    NETWORK,
    HTTP,
    TLS,
    RENDERER,
    UNKNOWN,
}
