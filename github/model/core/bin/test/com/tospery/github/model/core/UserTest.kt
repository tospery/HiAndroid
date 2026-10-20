package com.tospery.github.model.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UserTest {
    @Test
    fun userIdReturnsRawValueAsString() {
        val id = UserId("github:1")

        assertEquals("github:1", id.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun userIdRejectsBlankValue() {
        UserId(" ")
    }
}
