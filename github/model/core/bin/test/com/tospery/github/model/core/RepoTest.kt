package com.tospery.github.model.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RepoTest {
    @Test
    fun repoIdReturnsRawValueAsString() {
        val id = RepoId("github:123")

        assertEquals("github:123", id.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun repoIdRejectsBlankValue() {
        RepoId(" ")
    }
}
