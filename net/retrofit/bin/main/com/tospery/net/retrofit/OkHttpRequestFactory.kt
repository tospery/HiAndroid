package com.tospery.net.retrofit

import com.tospery.net.Endpoint
import com.tospery.net.HttpMethod
import com.tospery.net.NetworkRequest
import com.tospery.net.RequestBody as NetworkRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 把通用 NetworkRequest 转换成 OkHttp Request。
 */
class OkHttpRequestFactory(
    private val baseUrl: HttpUrl,
) {
    constructor(config: RetrofitNetworkConfig) : this(config.baseUrl.toHttpUrl())

    fun create(request: NetworkRequest): Request {
        val url = resolveUrl(request.endpoint)
            .newBuilder()
            .apply {
                request.queryParameters.forEach { (key, value) ->
                    addQueryParameter(key, value)
                }
            }
            .build()

        return Request.Builder()
            .url(url)
            .apply {
                request.headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .method(request.method.name, request.toOkHttpBody())
            .build()
    }

    private fun resolveUrl(endpoint: Endpoint): HttpUrl {
        return when (endpoint) {
            is Endpoint.AbsoluteUrl -> endpoint.value.toHttpUrl()
            is Endpoint.RelativeUrl -> requireNotNull(baseUrl.resolve(endpoint.value)) {
                "无法根据 baseUrl 解析相对 URL：${endpoint.value}"
            }
        }
    }

    private fun NetworkRequest.toOkHttpBody(): okhttp3.RequestBody? {
        val body = when (val requestBody = body) {
            NetworkRequestBody.Empty -> null
            is NetworkRequestBody.Text -> requestBody.value.toRequestBody(
                requestBody.contentType.toMediaType(),
            )
            is NetworkRequestBody.Bytes -> requestBody.value.toRequestBody(
                requestBody.contentType.toMediaType(),
            )
        }

        return when (method) {
            HttpMethod.GET,
            HttpMethod.HEAD,
                -> null

            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
                -> body ?: ByteArray(0).toRequestBody()

            HttpMethod.DELETE,
            HttpMethod.OPTIONS,
                -> body
        }
    }
}