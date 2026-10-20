package com.tospery.net.retrofit

import retrofit2.Retrofit

/**
 * Retrofit service 创建入口。
 * 业务 data 模块可以通过它创建自己的 Retrofit API interface。
 */
class RetrofitServiceProvider(
    private val retrofit: Retrofit,
) {
    constructor(
        config: RetrofitNetworkConfig,
    ) : this(
        retrofit = RetrofitNetworkFactory.createRetrofit(config),
    )

    fun <SERVICE : Any> create(
        serviceClass: Class<SERVICE>,
    ): SERVICE {
        return retrofit.create(serviceClass)
    }
}

/**
 * Kotlin 调用方使用的 reified 便捷方法。
 */
inline fun <reified SERVICE : Any> RetrofitServiceProvider.create(): SERVICE {
    return create(SERVICE::class.java)
}