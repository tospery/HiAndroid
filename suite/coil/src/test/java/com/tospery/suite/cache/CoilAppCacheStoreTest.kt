package com.tospery.suite.cache

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilAppCacheStoreTest {

    @Test
    fun sizeBytesReportsDiskCacheSize() = runTest {
        val store = CoilAppCacheStore(
            readDiskCacheSizeBytes = { 128L },
            clearMemoryCache = {},
            clearDiskCache = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(128L, store.sizeBytes())
    }

    @Test
    fun clearRunsMemoryAndDiskCacheOperations() = runTest {
        var memoryCacheWasCleared = false
        var diskCacheWasCleared = false

        val store = CoilAppCacheStore(
            readDiskCacheSizeBytes = { 0L },
            clearMemoryCache = {
                memoryCacheWasCleared = true
            },
            clearDiskCache = {
                diskCacheWasCleared = true
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.clear()

        assertTrue(memoryCacheWasCleared)
        assertTrue(diskCacheWasCleared)
    }
}