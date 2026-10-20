package com.tospery.net

/**
 * 与具体 HTTP 客户端无关的网络请求执行器。
 * Retrofit/OkHttp 等实现应放在实现层模块中。
 */
fun interface NetworkClient {
    suspend fun execute(request: NetworkRequest): NetworkResult<ByteArray>
}