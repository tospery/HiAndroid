package com.tospery.nav

import com.tospery.base.logging.LogEntry
import com.tospery.base.logging.LogProvider
import com.tospery.base.logging.LogRegistry
import com.tospery.base.logging.NoOpLogProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLoggingTest {
    @Test
    fun `navigation log URL redacts query values and fragment`() {
        val safeUrl =
            "higit://login?code=secret-code&state=secret-state#private"
                .redactNavigationUrl()

        assertEquals(
            "higit://login?code=<redacted>&state=<redacted>#<redacted>",
            safeUrl,
        )
        assertFalse(safeUrl.contains("secret-code"))
        assertFalse(safeUrl.contains("secret-state"))
        assertFalse(safeUrl.contains("private"))
    }

    @Test
    fun `navigation log URL exposes only explicitly allowed query values`() {
        assertEquals(
            "home?tab=3&token=<redacted>",
            "home?tab=3&token=secret"
                .redactNavigationUrl(visibleQueryParameters = setOf("tab")),
        )
    }

    @Test
    fun `navigation log URL preserves complete URL when redaction is disabled`() {
        val url = "web?url=https%3A%2F%2Fexample.com%2Fvideo%3Ftoken%3Ddebug#player"

        assertEquals(
            url,
            url.toNavigationLogUrl(redactSensitiveLogValues = false),
        )
    }

    @Test
    fun `system URI log hides opaque recipient data`() {
        assertEquals(
            "tel:<redacted>",
            UrlNavigationTarget.SystemUri("tel:+8613800138000")
                .toNavigationLogUrl(),
        )
    }

    @Test
    fun `route navigation emits sanitized structured log`() {
        val entries = mutableListOf<LogEntry>()
        LogRegistry.install(
            object : LogProvider {
                override fun log(entry: LogEntry) {
                    entries += entry
                }
            },
        )

        try {
            logRouteNavigation(
                routeUrl = "web?url=https%3A%2F%2Fexample.com%2Fprivate",
                source = "test",
            )
        } finally {
            LogRegistry.install(NoOpLogProvider)
        }

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(NAV_LOG_TAG, entry.tag)
        assertEquals(
            "web?url=<redacted>",
            entry.attributes.single { it.key == "route_url" }.value,
        )
        assertEquals(
            "test",
            entry.attributes.single { it.key == "source" }.value,
        )
        assertTrue(entry.message.contains("路由 URL"))
    }

    @Test
    fun `route navigation emits original URL when redaction is disabled`() {
        val entries = mutableListOf<LogEntry>()
        LogRegistry.install(
            object : LogProvider {
                override fun log(entry: LogEntry) {
                    entries += entry
                }
            },
        )
        val routeUrl = "web?url=https%3A%2F%2Fexample.com%2Fvideo%3Ftoken%3Ddebug#player"

        try {
            logRouteNavigation(
                routeUrl = routeUrl,
                source = "test",
                redactSensitiveLogValues = false,
            )
        } finally {
            LogRegistry.install(NoOpLogProvider)
        }

        assertEquals(
            routeUrl,
            entries.single().attributes.single { it.key == "route_url" }.value,
        )
    }
}
