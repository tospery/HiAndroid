package com.tospery.core.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayStoreLauncherTest {
    @Test
    fun opensPlayAppBeforeHttpsFallback() {
        val opened = mutableListOf<String>()
        val launcher =
            GooglePlayStoreLauncher { uri ->
                opened += uri
                uri.startsWith("https://")
            }

        assertTrue(
            launcher.open(
                "https://play.google.com/store/apps/details?id=com.tencent.mm",
            ),
        )
        assertEquals(
            listOf(
                "market://details?id=com.tencent.mm",
                "https://play.google.com/store/apps/details?id=com.tencent.mm",
            ),
            opened,
        )
    }

    @Test
    fun rejectsNonPlayUrlsWithoutLaunchingAnything() {
        val opened = mutableListOf<String>()
        val launcher = GooglePlayStoreLauncher { uri -> opened += uri; true }

        assertFalse(launcher.open("https://example.com/app.apk"))
        assertEquals(emptyList<String>(), opened)
    }
}
