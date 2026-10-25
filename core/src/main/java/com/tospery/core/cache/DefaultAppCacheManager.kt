package com.tospery.core.cache

import com.tospery.base.error.UnknownAppError
import com.tospery.base.result.AppResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * 聚合多个非核心缓存来源的默认实现。
 *
 * 某个 Store 清理失败时，仍会继续清理其他 Store，
 * 最终再统一返回失败结果。
 */
class DefaultAppCacheManager(
    stores: Collection<AppCacheStore>,
) : AppCacheManager {
    private val stores = stores.toList()

    override suspend fun calculateSizeBytes(): AppResult<Long> {
        return try {
            var totalSizeBytes = 0L

            for (store in stores) {
                val storeSizeBytes = store.sizeBytes()

                require(storeSizeBytes >= 0L) {
                    "AppCacheStore 返回的缓存大小不能为负数。"
                }

                totalSizeBytes =
                    Math.addExact(
                        totalSizeBytes,
                        storeSizeBytes,
                    )
            }

            AppResult.Success(totalSizeBytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            AppResult.Failure(
                UnknownAppError(
                    debugMessage = "计算 App 非核心缓存大小失败。",
                    cause = exception,
                ),
            )
        }
    }

    override suspend fun clear(): AppResult<Unit> {
        val failures = mutableListOf<Exception>()

        for (store in stores) {
            try {
                store.clear()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                failures += exception
            }
        }

        if (failures.isEmpty()) {
            return AppResult.Success(Unit)
        }

        val combinedCause =
            IllegalStateException(
                "一个或多个缓存来源清理失败。",
                failures.first(),
            ).apply {
                failures
                    .drop(1)
                    .forEach { failure ->
                        addSuppressed(failure)
                    }
            }

        return AppResult.Failure(
            UnknownAppError(
                debugMessage = "清理 App 非核心缓存失败。",
                cause = combinedCause,
            ),
        )
    }
}