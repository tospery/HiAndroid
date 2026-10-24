package com.tospery.net.retrofit

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tospery.base.logging.LogLevel
import com.tospery.base.logging.isLoggable
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * 创建 net-retrofit 默认依赖对象。
 */
object RetrofitNetworkFactory {
    /**
     * 根据通用配置创建 OkHttpClient。
     *
     * 完整正文及原始认证值必须由已安装的受保护 LogProvider 承接；未满足时参数会安全
     * 退回脱敏、限长模式。完整模式按实际发送/读取的字节记录分块日志，不预读流式响应。
     */
    fun createOkHttpClient(
        config: RetrofitNetworkConfig,
        // Raw capture is honored only when the installed provider declares a protected sink.
        redactSensitiveData: Boolean = true,
        logBodies: Boolean = true,
        captureFullBodies: Boolean = false,
        logGraphQl: Boolean = false,
        fullBodyByteLimit: Long = 100L * 1024L * 1024L,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                if (config.defaultHeaders.isNotEmpty()) {
                    addInterceptor(DefaultHeadersInterceptor(config.defaultHeaders))
                }
            }
            .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .apply(configure)
            .apply {
                // 日志拦截器最后注册，才能记录默认头和鉴权拦截器补全后的真实请求。
                if (isNetworkLoggable()) {
                    addInterceptor(
                        AppLoggerInterceptor(
                            redactSensitiveData = redactSensitiveData,
                            logBodies = logBodies,
                            captureFullBodies = captureFullBodies,
                            logGraphQl = logGraphQl,
                            fullBodyByteLimit = fullBodyByteLimit,
                        )
                    )
                }
            }
            .build()
    }

    private fun isNetworkLoggable(): Boolean {
        return isLoggable(LogLevel.DEBUG, NET_LOG_TAG) ||
            isLoggable(LogLevel.INFO, NET_LOG_TAG) ||
            isLoggable(LogLevel.WARNING, NET_LOG_TAG) ||
            isLoggable(LogLevel.ERROR, NET_LOG_TAG)
    }

    /**
     * 创建默认 Moshi 实例。
     */
    fun createMoshi(
        configure: Moshi.Builder.() -> Unit = {},
    ): Moshi {
        return Moshi.Builder()
            .apply(configure)
            // Kotlin data class 需要该 adapter，否则 Retrofit 无法为 @Body 创建 Moshi 转换器。
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * 根据 OkHttpClient 和 Moshi 创建 Retrofit。
     */
    fun createRetrofit(
        config: RetrofitNetworkConfig,
        okHttpClient: OkHttpClient = createOkHttpClient(config),
        moshi: Moshi = createMoshi(),
        configure: Retrofit.Builder.() -> Unit = {},
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .apply(configure)
            .build()
    }
}
