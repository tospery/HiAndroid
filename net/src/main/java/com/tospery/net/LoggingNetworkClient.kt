package com.tospery.net

import com.tospery.base.result.AppResult

/**
 * 为任意 NetworkClient 增加网络日志能力。
 * 这里使用装饰器模式，不要求具体 HTTP 实现继承某个基类。
 */
class LoggingNetworkClient(
    private val delegate: NetworkClient,
    private val logSink: NetworkLogSink = NoOpNetworkLogSink,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : NetworkClient {
    override suspend fun execute(request: NetworkRequest): NetworkResult<ByteArray> {
        val startedAt = currentTimeMillis()
        logSink.log(NetworkLogEvent.RequestStarted(request))

        return when (val result = delegate.execute(request)) {
            is AppResult.Success -> {
                logSink.log(
                    NetworkLogEvent.ResponseReceived(
                        request = request,
                        statusCode = 200,
                        durationMillis = currentTimeMillis() - startedAt,
                    ),
                )
                result
            }

            is AppResult.Failure -> {
                val error = result.error as? NetworkError
                    ?: NetworkError.Unknown(
                        message = result.error.message,
                        debugMessage = result.error.debugMessage,
                        cause = result.error.cause,
                    )

                logSink.log(
                    NetworkLogEvent.RequestFailed(
                        request = request,
                        error = error,
                        durationMillis = currentTimeMillis() - startedAt,
                    ),
                )
                result
            }
        }
    }
}
