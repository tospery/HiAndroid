package com.tospery.net

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkLogSinkTest {
    @Test
    fun sinkReceivesNetworkLogEvent() {
        val events = mutableListOf<NetworkLogEvent>()
        val sink = NetworkLogSink { event ->
            events += event
        }
        val request = NetworkRequest(
            method = HttpMethod.GET,
            endpoint = Endpoint.RelativeUrl("repos"),
        )

        sink.log(NetworkLogEvent.RequestStarted(request))

        assertEquals(
            listOf(NetworkLogEvent.RequestStarted(request)),
            events,
        )
    }

    @Test
    fun noOpSinkIgnoresEvent() {
        val request = NetworkRequest(
            method = HttpMethod.GET,
            endpoint = Endpoint.RelativeUrl("repos"),
        )

        NoOpNetworkLogSink.log(NetworkLogEvent.RequestStarted(request))
    }

    @Test
    fun compositeSinkDispatchesEventToAllSinks() {
        val firstEvents = mutableListOf<NetworkLogEvent>()
        val secondEvents = mutableListOf<NetworkLogEvent>()
        val sink = CompositeNetworkLogSink(
            NetworkLogSink { event -> firstEvents += event },
            NetworkLogSink { event -> secondEvents += event },
        )
        val request = NetworkRequest(
            method = HttpMethod.GET,
            endpoint = Endpoint.RelativeUrl("repos"),
        )
        val event = NetworkLogEvent.RequestStarted(request)

        sink.log(event)

        assertEquals(listOf(event), firstEvents)
        assertEquals(listOf(event), secondEvents)
    }
}