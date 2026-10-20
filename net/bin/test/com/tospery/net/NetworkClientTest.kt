package com.tospery.net

import com.tospery.base.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NetworkClientTest {
    @Test
    fun clientCanBeImplementedWithSuspendLambda() = runBlocking {
        val client = NetworkClient {
            AppResult.Success(byteArrayOf(1, 2, 3))
        }
        val request = NetworkRequest(
            method = HttpMethod.GET,
            endpoint = Endpoint.RelativeUrl("repos"),
        )

        val result = client.execute(request)

        val success = result as AppResult.Success
        assertArrayEquals(byteArrayOf(1, 2, 3), success.value)
    }
}