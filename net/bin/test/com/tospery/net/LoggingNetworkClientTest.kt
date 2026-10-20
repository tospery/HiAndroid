package com.tospery.net

import com.tospery.base.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingNetworkClientTest {
    private val request = NetworkRequest(
        method = HttpMethod.GET,
        endpoint = Endpoint.RelativeUrl("repos"),
    )

    @Test
    fun loggingClientLogsStartedAndResponseEventsWhenRequestSucceeds() = runBlocking {
        val events = mutableListOf<NetworkLogEvent>()
        var now = 100L
        val client = LoggingNetworkClient(
            delegate = NetworkClient {
                now = 130L
                AppResult.Success(byteArrayOf(1, 2, 3))
            },
            logSink = NetworkLogSink { event -> events += event },
            currentTimeMillis = { now },
        )

        val result = client.execute(request)

        assertTrue(result is AppResult.Success)
        assertEquals(2, events.size)
        assertTrue(events[0] is NetworkLogEvent.RequestStarted)
        assertEquals(
            NetworkLogEvent.ResponseReceived(
                request = request,
                statusCode = 200,
                durationMillis = 30L,
            ),
            events[1],
        )
    }

    @Test
    fun loggingClientLogsStartedAndFailureEventsWhenRequestFails() = runBlocking {
        val events = mutableListOf<NetworkLogEvent>()
        var now = 200L
        val error = ReachableError.NoConnectivity(debugMessage = "no network")
        val client = LoggingNetworkClient(
            delegate = NetworkClient {
                now = 260L
                AppResult.Failure(error)
            },
            logSink = NetworkLogSink { event -> events += event },
            currentTimeMillis = { now },
        )

        val result = client.execute(request)

        assertTrue(result is AppResult.Failure)
        assertEquals(2, events.size)
        assertTrue(events[0] is NetworkLogEvent.RequestStarted)
        assertEquals(
            NetworkLogEvent.RequestFailed(
                request = request,
                error = error,
                durationMillis = 60L,
            ),
            events[1],
        )
    }
}