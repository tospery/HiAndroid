package com.tospery.net.retrofit

import com.tospery.net.ReachableError
import com.tospery.net.ServerError
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RetrofitNetworkErrorMapperTest {
    @Test
    fun httpExceptionKeepsStatusCodeAndApiMessage() {
        val throwable = HttpException(
            Response.error<Any>(
                403,
                """{"message":"GitHub access is restricted."}""".toResponseBody(),
            ),
        )

        val error = RetrofitNetworkErrorMapper().map(throwable)

        assertTrue(error is ServerError.HttpFailure)
        error as ServerError.HttpFailure
        assertEquals(403, error.statusCode)
        assertEquals("GitHub access is restricted.", error.message)
        assertSame(throwable, error.cause)
    }

    @Test
    fun directHttpResponseKeepsApiMessage() {
        val response =
            Response.error<Any>(
                422,
                """{"message":"Validation failed."}""".toResponseBody(),
            )

        val error = response.toHttpFailure()

        assertEquals(422, error.statusCode)
        assertEquals("Validation failed.", error.message)
    }

    @Test
    fun nonRetrofitExceptionUsesDefaultMapping() {
        val throwable = UnknownHostException("offline")

        val error = RetrofitNetworkErrorMapper().map(throwable)

        assertTrue(error is ReachableError.NoConnectivity)
        assertSame(throwable, error.cause)
    }
}
