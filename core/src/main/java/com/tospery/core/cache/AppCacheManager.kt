package com.tospery.core.cache

import com.tospery.base.result.AppResult

/**
 * 单一非核心缓存来源。
 *
 * Coil、OkHttp 和普通临时目录可以分别实现该接口，
 * 避免通用代码直接依赖具体第三方缓存类型。
 */
interface AppCacheStore {
    /**
     * 返回当前缓存占用的字节数。
     */
    suspend fun sizeBytes(): Long

    /**
     * 清理当前缓存来源。
     */
    suspend fun clear()
}

/**
 * App 非核心缓存管理契约。
 *
 * 实现只能管理图片、HTTP、临时文件等可重新生成的数据，
 * 不能清理账号凭据、用户偏好、业务数据库等核心数据。
 */
interface AppCacheManager {
    suspend fun calculateSizeBytes(): AppResult<Long>

    suspend fun clear(): AppResult<Unit>
}