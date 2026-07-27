package com.tospery.net

import com.tospery.base.error.AppError

/**
 * net 模块统一错误类型。
 * 子类型按错误原因分组，便于调用方做精准处理。
 */
sealed interface NetworkError : AppError {
    data class Unknown(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : NetworkError
}

/**
 * 网络可达性错误，例如无网络、超时。
 */
sealed interface ReachableError : NetworkError {
    data class NoConnectivity(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : ReachableError

    data class Timeout(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : ReachableError
}

/**
 * 远端返回数据不符合预期导致的错误。
 */
sealed interface InvalidDataError : NetworkError {
    data class InvalidResponseFormat(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : InvalidDataError

    data class ParseFailure(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : InvalidDataError

    data class EmptyList(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : InvalidDataError
}

/**
 * 鉴权或登录态相关错误。
 */
sealed interface AuthError : NetworkError {
    data class Unauthorized(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AuthError

    data class SessionExpired(
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AuthError
}

/**
 * HTTP 状态码或服务端业务 code 表示的错误。
 */
sealed interface ServerError : NetworkError {
    data class HttpFailure(
        val statusCode: Int,
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : ServerError

    data class BusinessFailure(
        val code: String,
        override val message: String? = null,
        override val debugMessage: String? = null,
        override val cause: Throwable? = null,
    ) : ServerError
}
