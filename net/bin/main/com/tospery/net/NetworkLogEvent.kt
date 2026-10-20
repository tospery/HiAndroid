package com.tospery.net

/**
 * 与具体 HTTP 客户端无关的网络日志事件。
 * 具体实现层负责把 OkHttp/Retrofit 等事件转换成该模型。
 */
sealed interface NetworkLogEvent {
    data class RequestStarted(
        val request: NetworkRequest,
    ) : NetworkLogEvent

    data class ResponseReceived(
        val request: NetworkRequest,
        val statusCode: Int,
        val durationMillis: Long?,
    ) : NetworkLogEvent

    data class RequestFailed(
        val request: NetworkRequest,
        val error: NetworkError,
        val durationMillis: Long?,
    ) : NetworkLogEvent
}

fun interface NetworkLogSink {
    fun log(event: NetworkLogEvent)
}

/**
 * 默认空实现，用于调用方不关心网络日志的场景。
 */
object NoOpNetworkLogSink : NetworkLogSink {
    override fun log(event: NetworkLogEvent) = Unit
}

/**
 * 组合多个网络日志接收器，把同一个事件分发给所有接收器。
 */
class CompositeNetworkLogSink(
    private val sinks: List<NetworkLogSink>,
) : NetworkLogSink {
    constructor(vararg sinks: NetworkLogSink) : this(sinks.toList())

    override fun log(event: NetworkLogEvent) {
        sinks.forEach { sink ->
            sink.log(event)
        }
    }
}