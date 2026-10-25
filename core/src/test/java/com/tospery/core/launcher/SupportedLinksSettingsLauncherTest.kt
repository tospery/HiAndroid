package com.tospery.core.launcher

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLinksSettingsLauncherTest {
    @Test
    fun `API 31 opens the system default-opening page first`() {
        val opened = mutableListOf<SupportedLinksSettingsDestination>()
        val launcher =
            SupportedLinksSettingsLauncher(sdkInt = 31) { destination ->
                opened += destination
                true
            }

        assertTrue(launcher.open())
        assertEquals(
            listOf(SupportedLinksSettingsDestination.OPEN_BY_DEFAULT),
            opened,
        )
    }

    @Test
    fun `API 31 falls back to app details when default-opening settings is unavailable`() {
        val opened = mutableListOf<SupportedLinksSettingsDestination>()
        val launcher =
            SupportedLinksSettingsLauncher(sdkInt = 31) { destination ->
                opened += destination
                destination == SupportedLinksSettingsDestination.APPLICATION_DETAILS
            }

        assertTrue(launcher.open())
        assertEquals(
            listOf(
                SupportedLinksSettingsDestination.OPEN_BY_DEFAULT,
                SupportedLinksSettingsDestination.APPLICATION_DETAILS,
            ),
            opened,
        )
    }

    @Test
    fun `API 31 falls back to app details when the primary system intent is absent`() {
        val opened = mutableListOf<SupportedLinksSettingsDestination>()
        val launcher =
            SupportedLinksSettingsLauncher(sdkInt = 31) { destination ->
                opened += destination
                if (destination == SupportedLinksSettingsDestination.OPEN_BY_DEFAULT) {
                    throw ActivityNotFoundException()
                }
                true
            }

        assertTrue(launcher.open())
        assertEquals(
            listOf(
                SupportedLinksSettingsDestination.OPEN_BY_DEFAULT,
                SupportedLinksSettingsDestination.APPLICATION_DETAILS,
            ),
            opened,
        )
    }

    @Test
    fun `API 30 opens app details without requesting unavailable default-opening settings`() {
        val opened = mutableListOf<SupportedLinksSettingsDestination>()
        val launcher =
            SupportedLinksSettingsLauncher(sdkInt = 30) { destination ->
                opened += destination
                true
            }

        assertTrue(launcher.open())
        assertEquals(
            listOf(SupportedLinksSettingsDestination.APPLICATION_DETAILS),
            opened,
        )
    }

    @Test
    fun `launcher reports failure after both system destinations reject the request`() {
        val launcher = SupportedLinksSettingsLauncher(sdkInt = 31) { false }

        assertFalse(launcher.open())
    }
}
