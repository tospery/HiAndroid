package com.tospery.net

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * 把底层异常转换成 net 模块统一错误类型。
 * 具体网络库可以复用默认实现，也可以在 adapter 层提供自己的映射规则。
 */
fun interface NetworkErrorMapper {
    fun map(throwable: Throwable): NetworkError
}

/**
 * 不依赖 Retrofit/OkHttp 的默认异常映射。
 */
object DefaultNetworkErrorMapper : NetworkErrorMapper {
    override fun map(throwable: Throwable): NetworkError {
        val debugMessage = throwable.message

        return when (throwable) {
            is SocketTimeoutException,
            is TimeoutCancellationException,
                -> ReachableError.Timeout(
                debugMessage = debugMessage,
                cause = throwable,
            )

            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
                -> ReachableError.NoConnectivity(
                debugMessage = debugMessage,
                cause = throwable,
            )

            is ProtocolException -> InvalidDataError.InvalidResponseFormat(
                debugMessage = debugMessage,
                cause = throwable,
            )

            else -> NetworkError.Unknown(
                debugMessage = debugMessage,
                cause = throwable,
            )
        }
    }
}

/**
 * 便捷扩展，方便调用方直接把异常转换成 NetworkError。
 */
fun Throwable.toNetworkError(
    mapper: NetworkErrorMapper = DefaultNetworkErrorMapper,
): NetworkError = mapper.map(this)