package com.tospery.net

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointTest {
    @Test
    fun relativeUrlAcceptsPath() {
        val endpoint = Endpoint.RelativeUrl("repos/octocat/hello-world")

        assertEquals("repos/octocat/hello-world", endpoint.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun relativeUrlRejectsAbsoluteHttpUrl() {
        Endpoint.RelativeUrl("http://example.com/repos")
    }

    @Test(expected = IllegalArgumentException::class)
    fun relativeUrlRejectsAbsoluteHttpsUrl() {
        Endpoint.RelativeUrl("https://example.com/repos")
    }

    @Test
    fun absoluteUrlAcceptsHttpUrl() {
        val endpoint = Endpoint.AbsoluteUrl("http://example.com/repos")

        assertEquals("http://example.com/repos", endpoint.value)
    }

    @Test
    fun absoluteUrlAcceptsHttpsUrl() {
        val endpoint = Endpoint.AbsoluteUrl("https://example.com/repos")

        assertEquals("https://example.com/repos", endpoint.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun absoluteUrlRejectsRelativePath() {
        Endpoint.AbsoluteUrl("repos/octocat/hello-world")
    }
}