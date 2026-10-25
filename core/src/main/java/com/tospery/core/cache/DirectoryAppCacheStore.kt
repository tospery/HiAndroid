package com.tospery.core.cache

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 负责统计和清理由 App 独占管理的普通缓存目录。
 *
 * 该实现不会删除根目录，只删除根目录内部的内容，避免破坏调用方持有的目录引用。
 *
 * 不要用于 Coil、OkHttp 等维护自身缓存索引的组件，这些组件需要使用各自提供的缓存 API。
 */
class DirectoryAppCacheStore(
    directories: Collection<File>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppCacheStore {

    private val directories = directories
        .map(File::getAbsoluteFile)
        .distinctBy(File::getPath)

    override suspend fun sizeBytes(): Long = withContext(ioDispatcher) {
        directories.fold(0L) { totalSizeBytes, directory ->
            Math.addExact(
                totalSizeBytes,
                directory.calculateDirectorySizeBytes(),
            )
        }
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        directories.forEach(File::clearDirectoryContents)
    }
}

private fun File.calculateDirectorySizeBytes(): Long {
    if (!exists()) {
        return 0L
    }

    require(isDirectory) {
        "缓存路径必须是目录：$absolutePath"
    }

    return childrenOrThrow().fold(0L) { totalSizeBytes, child ->
        val childSizeBytes = when {
            child.isDirectory -> child.calculateDirectorySizeBytes()
            child.isFile -> child.length()
            else -> 0L
        }

        Math.addExact(totalSizeBytes, childSizeBytes)
    }
}

private fun File.clearDirectoryContents() {
    if (!exists()) {
        return
    }

    require(isDirectory) {
        "缓存路径必须是目录：$absolutePath"
    }

    childrenOrThrow().forEach { child ->
        if (!child.deleteRecursively()) {
            throw IOException("无法删除缓存路径：${child.absolutePath}")
        }
    }
}

private fun File.childrenOrThrow(): Array<File> =
    listFiles() ?: throw IOException("无法读取缓存目录：$absolutePath")