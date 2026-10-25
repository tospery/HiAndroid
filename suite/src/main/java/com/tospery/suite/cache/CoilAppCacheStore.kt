package com.tospery.suite.cache

import coil3.ImageLoader
import com.tospery.core.cache.AppCacheStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将 Coil ImageLoader 缓存适配为通用 AppCacheStore。
 *
 * 设置页展示的缓存大小只统计磁盘缓存，避免把易变且可能重复计算的内存占用包含在内；
 * 执行清理时同时释放内存缓存和磁盘缓存。
 */
class CoilAppCacheStore(
    private val readDiskCacheSizeBytes: () -> Long,
    private val clearMemoryCache: () -> Unit,
    private val clearDiskCache: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher,
) : AppCacheStore {

    constructor(
        imageLoader: ImageLoader,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        readDiskCacheSizeBytes = {
            imageLoader.diskCache?.size ?: 0L
        },
        clearMemoryCache = {
            imageLoader.memoryCache?.clear()
        },
        clearDiskCache = {
            imageLoader.diskCache?.clear()
        },
        ioDispatcher = ioDispatcher,
    )

    override suspend fun sizeBytes(): Long = withContext(ioDispatcher) {
        readDiskCacheSizeBytes()
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        clearMemoryCache()
        clearDiskCache()
    }
}