package com.tospery.suite.aliyun.emas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliyunEmasLogTagsTest {
    @Test
    fun everyAdapterLogTagUsesMylogPrefix() {
        val tags =
            listOf(
                AliyunEmasLogTags.lifecycle,
                AliyunEmasLogTags.crashAnalysis,
                AliyunEmasLogTags.performanceAnalysis,
                AliyunEmasLogTags.memoryAnalysis,
                AliyunEmasLogTags.remoteLog,
                AliyunEmasLogTags.networkAnalysis,
            )

        assertTrue(tags.all { tag -> tag.startsWith("mylog-") })
        assertEquals(tags.size, tags.distinct().size)
    }

    @Test
    fun everyComponentMapsToItsDedicatedTag() {
        assertEquals(
            setOf(
                AliyunEmasLogTags.crashAnalysis,
                AliyunEmasLogTags.performanceAnalysis,
                AliyunEmasLogTags.memoryAnalysis,
                AliyunEmasLogTags.remoteLog,
            ),
            AliyunEmasComponent.entries.map(AliyunEmasComponent::logTag).toSet(),
        )
    }
}
