package com.tospery.core.cache

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryAppCacheStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sizeBytesIncludesNestedFilesAcrossDirectories() = runTest {
        val firstDirectory = temporaryFolder.newFolder("first")
        val secondDirectory = temporaryFolder.newFolder("second")
        val nestedDirectory = File(secondDirectory, "nested").apply {
            mkdirs()
        }

        File(firstDirectory, "first.bin").writeBytes(ByteArray(4))
        File(nestedDirectory, "second.bin").writeBytes(ByteArray(7))

        val store = DirectoryAppCacheStore(
            directories = listOf(firstDirectory, secondDirectory),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(11L, store.sizeBytes())
    }

    @Test
    fun duplicateDirectoriesAreCountedOnce() = runTest {
        val directory = temporaryFolder.newFolder("cache")
        File(directory, "cache.bin").writeBytes(ByteArray(5))

        val store = DirectoryAppCacheStore(
            directories = listOf(directory, directory.absoluteFile),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(5L, store.sizeBytes())
    }

    @Test
    fun clearRemovesContentsAndPreservesRootDirectories() = runTest {
        val directory = temporaryFolder.newFolder("cache")
        val nestedDirectory = File(directory, "nested").apply {
            mkdirs()
        }

        File(directory, "first.bin").writeBytes(ByteArray(3))
        File(nestedDirectory, "second.bin").writeBytes(ByteArray(4))

        val store = DirectoryAppCacheStore(
            directories = listOf(directory),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.clear()

        assertTrue(directory.exists())
        assertTrue(directory.isDirectory)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun missingDirectoryHasZeroSizeAndCanBeCleared() = runTest {
        val missingDirectory = File(temporaryFolder.root, "missing")

        val store = DirectoryAppCacheStore(
            directories = listOf(missingDirectory),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(0L, store.sizeBytes())

        store.clear()

        assertTrue(!missingDirectory.exists())
    }
}