package com.tospery.net.retrofit

/**
 * Retrofit 网络实现层配置。
 *
 * baseUrl 仍然需要满足 Retrofit 的要求，以 / 结尾；
 * 动态完整 URL 请求后续由 @Url 或 OkHttp Request 层处理。
 */
data class RetrofitNetworkConfig(
    val baseUrl: String,
    val connectTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val writeTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val defaultHeaders: Map<String, String> = emptyMap(),
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl 不能为空。" }
        require(baseUrl.endsWith("/")) { "baseUrl 必须以 / 结尾。" }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis 必须大于 0。" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis 必须大于 0。" }
        require(writeTimeoutMillis > 0) { "writeTimeoutMillis 必须大于 0。" }
        require(defaultHeaders.keys.none { it.isBlank() }) { "默认请求头名称不能为空。" }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000L
    }
}