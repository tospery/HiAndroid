package com.tospery.net.retrofit

import com.tospery.net.Endpoint
import com.tospery.net.HttpMethod
import com.tospery.net.NetworkRequest
import com.tospery.net.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OkHttpRequestFactoryTest {
    private val factory = OkHttpRequestFactory(
        RetrofitNetworkConfig(baseUrl = "https://api.github.com/"),
    )

    @Test
    fun factoryCreatesRelativeUrlRequestWithQueryAndHeaders() {
        val request = factory.create(
            NetworkRequest(
                method = HttpMethod.GET,
                endpoint = Endpoint.RelativeUrl("search/repositories"),
                headers = mapOf("Accept" to "application/json"),
                queryParameters = mapOf("q" to "kotlin compose"),
            ),
        )

        assertEquals("GET", request.method)
        assertEquals("https", request.url.scheme)
        assertEquals("api.github.com", request.url.host)
        assertEquals("/search/repositories", request.url.encodedPath)
        assertEquals("kotlin compose", request.url.queryParameter("q"))
        assertEquals("application/json", request.header("Accept"))
        assertNull(request.body)
    }

    @Test
    fun factoryCreatesAbsoluteUrlRequest() {
        val request = factory.create(
            NetworkRequest(
                method = HttpMethod.GET,
                endpoint = Endpoint.AbsoluteUrl("https://example.com/files/readme.txt"),
            ),
        )

        assertEquals("example.com", request.url.host)
        assertEquals("/files/readme.txt", request.url.encodedPath)
    }

    @Test
    fun factoryCreatesTextRequestBody() {
        val request = factory.create(
            NetworkRequest(
                method = HttpMethod.POST,
                endpoint = Endpoint.RelativeUrl("repos"),
                body = RequestBody.Text(
                    value = """{"name":"AtlasHub"}""",
                    contentType = "application/json",
                ),
            ),
        )

        assertEquals("POST", request.method)
        assertNotNull(request.body)
        assertEquals("application/json; charset=utf-8", request.body?.contentType().toString())
    }

    @Test
    fun factoryCreatesEmptyBodyForPostRequest() {
        val request = factory.create(
            NetworkRequest(
                method = HttpMethod.POST,
                endpoint = Endpoint.RelativeUrl("repos"),
            ),
        )

        assertEquals("POST", request.method)
        assertNotNull(request.body)
        assertEquals(0L, request.body?.contentLength())
    }
}