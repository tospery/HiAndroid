package com.tospery.net

import com.tospery.base.result.AppResult
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCatchingTest {
    @Test
    fun runNetworkCatchingReturnsSuccessResult() = runBlocking {
        val result = runNetworkCatching {
            "ok"
        }

        assertEquals(AppResult.Success("ok"), result)
    }

    @Test
    fun runNetworkCatchingMapsThrowableToFailureResult() = runBlocking {
        val throwable = UnknownHostException("unknown host")

        val result = runNetworkCatching {
            throw throwable
        }

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is ReachableError.NoConnectivity)
        assertSame(throwable, result.error.cause)
    }

    @Test(expected = CancellationException::class)
    fun runNetworkCatchingRethrowsCancellationException() {
        runBlocking {
            runNetworkCatching {
                throw CancellationException("cancelled")
            }
        }
    }
}