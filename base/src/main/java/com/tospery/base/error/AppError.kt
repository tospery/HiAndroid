package com.tospery.base.error

interface AppError {
    /** 可安全展示给用户的错误文案；不包含底层异常或调试信息。 */
    val message: String?
    val debugMessage: String?
    val cause: Throwable?
}

data class UnknownAppError(
    override val message: String? = null,
    override val debugMessage: String? = null,
    override val cause: Throwable? = null,
) : AppError

/**
 * 在只接受 [Throwable] 的边界（例如 Paging）中保留结构化 [AppError]。
 *
 * [message] 优先作为异常消息，方便宿主展示服务端可读错误；未提供时才保留调试消息。
 */
class AppErrorException(
    val appError: AppError,
) : RuntimeException(
        appError.message ?: appError.debugMessage,
        appError.cause,
    )

/** 返回可向用户展示的 API 错误文案，不回退到调试文案。 */
fun Throwable.appErrorMessageOrNull(): String? =
    (this as? AppErrorException)
        ?.appError
        ?.message
        ?.trim()
        ?.takeIf(String::isNotEmpty)
