package com.tospery.core.cache

import com.tospery.base.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultAppCacheManagerTest {
    @Test
    fun calculateSizeBytesSumsAllStores() =
        runTest {
            val manager =
                DefaultAppCacheManager(
                    stores =
                        listOf(
                            FakeAppCacheStore(sizeBytes = 12L),
                            FakeAppCacheStore(sizeBytes = 30L),
                        ),
                )

            when (val result = manager.calculateSizeBytes()) {
                is AppResult.Success -> {
                    assertEquals(42L, result.value)
                }

                is AppResult.Failure -> {
                    fail("Expected cache size calculation to succeed.")
                }
            }
        }

    @Test
    fun calculateSizeBytesRejectsNegativeStoreSize() =
        runTest {
            val manager =
                DefaultAppCacheManager(
                    stores =
                        listOf(
                            FakeAppCacheStore(sizeBytes = -1L),
                        ),
                )

            assertTrue(
                manager.calculateSizeBytes() is AppResult.Failure,
            )
        }

    @Test
    fun clearReturnsSuccessWhenAllStoresSucceed() =
        runTest {
            val firstStore = FakeAppCacheStore()
            val secondStore = FakeAppCacheStore()
            val manager =
                DefaultAppCacheManager(
                    stores = listOf(firstStore, secondStore),
                )
            val result = manager.clear()

            assertTrue(result is AppResult.Success)
            assertEquals(1, firstStore.clearCallCount)
            assertEquals(1, secondStore.clearCallCount)
        }

    @Test
    fun clearAttemptsEveryStoreWhenOneStoreFails() =
        runTest {
            val failingStore =
                FakeAppCacheStore(
                    clearFailure =
                        IllegalStateException("Clear failed."),
                )
            val succeedingStore = FakeAppCacheStore()
            val manager =
                DefaultAppCacheManager(
                    stores =
                        listOf(
                            failingStore,
                            succeedingStore,
                        ),
                )
            val result = manager.clear()

            assertTrue(result is AppResult.Failure)
            assertEquals(1, failingStore.clearCallCount)
            assertEquals(1, succeedingStore.clearCallCount)
        }

    private class FakeAppCacheStore(
        private val sizeBytes: Long = 0L,
        private val clearFailure: Exception? = null,
    ) : AppCacheStore {
        var clearCallCount: Int = 0
            private set

        override suspend fun sizeBytes(): Long {
            return sizeBytes
        }

        override suspend fun clear() {
            clearCallCount += 1
            clearFailure?.let { throw it }
        }
    }
}
