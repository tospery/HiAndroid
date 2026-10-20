package com.tospery.net.retrofit

import org.junit.Assert.assertEquals
import org.junit.Test

class RetrofitNetworkConfigTest {
    @Test
    fun configUsesDefaultTimeouts() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
        )

        assertEquals(30_000L, config.connectTimeoutMillis)
        assertEquals(30_000L, config.readTimeoutMillis)
        assertEquals(30_000L, config.writeTimeoutMillis)
    }

    @Test
    fun configKeepsDefaultHeaders() {
        val config = RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
            defaultHeaders = mapOf("Accept" to "application/vnd.github+json"),
        )

        assertEquals("application/vnd.github+json", config.defaultHeaders["Accept"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsBlankHeaderName() {
        RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
            defaultHeaders = mapOf(" " to "value"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsBlankBaseUrl() {
        RetrofitNetworkConfig(baseUrl = " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsBaseUrlWithoutTrailingSlash() {
        RetrofitNetworkConfig(baseUrl = "https://api.github.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun configRejectsInvalidTimeout() {
        RetrofitNetworkConfig(
            baseUrl = "https://api.github.com/",
            connectTimeoutMillis = 0L,
        )
    }
}