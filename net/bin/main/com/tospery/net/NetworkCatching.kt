package com.tospery.net

import com.tospery.base.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * 执行网络相关挂起任务，并把异常转换成 NetworkResult。
 *
 * 普通协程取消必须继续抛出，避免破坏结构化并发；
 * 超时取消可以映射为 ReachableError.Timeout。
 */
suspend fun <DATA> runNetworkCatching(
    errorMapper: NetworkErrorMapper = DefaultNetworkErrorMapper,
    block: suspend () -> DATA,
): NetworkResult<DATA> {
    return try {
        AppResult.Success(block())
    } catch (throwable: TimeoutCancellationException) {
        AppResult.Failure(errorMapper.map(throwable))
    } catch (throwable: CancellationException) {
        throw throwable
    } catch (throwable: Throwable) {
        AppResult.Failure(errorMapper.map(throwable))
    }
}