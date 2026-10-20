package com.tospery.net.retrofit

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 为请求补充调用方配置的默认请求头。
 *
 * 如果单个请求已经显式声明同名 header，这里不覆盖它。
 */
class DefaultHeadersInterceptor(
    private val headers: Map<String, String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        headers.forEach { (name, value) ->
            if (request.header(name) == null) {
                builder.header(name, value)
            }
        }

        return chain.proceed(builder.build())
    }
}