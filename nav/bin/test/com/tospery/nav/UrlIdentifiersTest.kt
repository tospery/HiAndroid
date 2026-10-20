package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlIdentifiersTest {
    @Test
    fun schemeNormalizesToLowercase() {
        assertEquals("atlashub", UrlScheme("AtlasHub").normalized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun schemeMustNotContainSeparator() {
        UrlScheme("atlashub://")
    }

    @Test
    fun hostNormalizesToLowercase() {
        assertEquals("atlashub.com", UrlHost("AtlasHub.com").normalized())
    }

    @Test(expected = IllegalArgumentException::class)
    fun hostMustNotContainPath() {
        UrlHost("atlashub.com/about")
    }
}