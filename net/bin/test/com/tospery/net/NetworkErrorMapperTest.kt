package com.tospery.net

import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorMapperTest {
    @Test
    fun socketTimeoutMapsToTimeoutError() {
        val throwable = SocketTimeoutException("timeout")

        val error = throwable.toNetworkError()

        assertTrue(error is ReachableError.Timeout)
        assertSame(throwable, error.cause)
    }

    @Test
    fun unknownHostMapsToNoConnectivityError() {
        val throwable = UnknownHostException("unknown host")

        val error = throwable.toNetworkError()

        assertTrue(error is ReachableError.NoConnectivity)
        assertSame(throwable, error.cause)
    }

    @Test
    fun protocolExceptionMapsToInvalidResponseFormat() {
        val throwable = ProtocolException("bad protocol")

        val error = throwable.toNetworkError()

        assertTrue(error is InvalidDataError.InvalidResponseFormat)
        assertSame(throwable, error.cause)
    }

    @Test
    fun unknownThrowableMapsToUnknownError() {
        val throwable = IllegalStateException("unexpected")

        val error = throwable.toNetworkError()

        assertTrue(error is NetworkError.Unknown)
        assertSame(throwable, error.cause)
    }
}