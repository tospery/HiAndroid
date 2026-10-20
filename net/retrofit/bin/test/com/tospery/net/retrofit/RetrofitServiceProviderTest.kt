package com.tospery.net.retrofit

import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET

class RetrofitServiceProviderTest {
    @Test
    fun providerCreatesServiceByClass() {
        val provider = RetrofitServiceProvider(
            RetrofitNetworkConfig(baseUrl = "https://api.github.com/"),
        )

        val service = provider.create(SampleService::class.java)

        assertTrue(service::class.java.name.isNotBlank())
    }

    @Test
    fun providerCreatesServiceByReifiedFunction() {
        val provider = RetrofitServiceProvider(
            RetrofitNetworkConfig(baseUrl = "https://api.github.com/"),
        )

        val service = provider.create<SampleService>()

        assertTrue(service::class.java.name.isNotBlank())
    }

    private interface SampleService {
        @GET("repos")
        suspend fun repos(): String
    }
}