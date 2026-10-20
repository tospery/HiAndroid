package com.tospery.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NetworkRequestTest {
    @Test
    fun byteRequestBodiesWithSameContentAreEqual() {
        val first = RequestBody.Bytes(
            value = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
        )
        val second = RequestBody.Bytes(
            value = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun byteRequestBodiesWithDifferentContentAreNotEqual() {
        val first = RequestBody.Bytes(
            value = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
        )
        val second = RequestBody.Bytes(
            value = byteArrayOf(1, 2, 4),
            contentType = "application/octet-stream",
        )

        assertNotEquals(first, second)
    }
}