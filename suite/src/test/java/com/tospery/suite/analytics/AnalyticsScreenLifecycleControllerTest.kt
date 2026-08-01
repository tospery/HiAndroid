package com.tospery.suite.analytics

import com.tospery.base.analytics.AnalyticsEvent
import com.tospery.base.analytics.AnalyticsProperties
import com.tospery.base.analytics.AnalyticsScreen
import com.tospery.base.analytics.AnalyticsTracker
import com.tospery.base.analytics.AnalyticsUser
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsScreenLifecycleControllerTest {
    @Test
    fun `start and stop produce one paired screen session`() {
        val tracker = RecordingAnalyticsTracker()
        val screen = AnalyticsScreen("repository")
        val controller = AnalyticsScreenLifecycleController(screen, tracker)

        controller.start()
        controller.start()
        controller.stop()
        controller.stop()

        assertEquals(listOf(screen), tracker.enteredScreens)
        assertEquals(listOf(screen), tracker.exitedScreens)
    }

    @Test
    fun `screen can start a new session after app returns to foreground`() {
        val tracker = RecordingAnalyticsTracker()
        val screen = AnalyticsScreen("search")
        val controller = AnalyticsScreenLifecycleController(screen, tracker)

        controller.start()
        controller.stop()
        controller.start()
        controller.stop()

        assertEquals(listOf(screen, screen), tracker.enteredScreens)
        assertEquals(listOf(screen, screen), tracker.exitedScreens)
    }
}

private class RecordingAnalyticsTracker : AnalyticsTracker {
    val enteredScreens = mutableListOf<AnalyticsScreen>()
    val exitedScreens = mutableListOf<AnalyticsScreen>()

    override fun setEnabled(enabled: Boolean) = Unit

    override fun track(event: AnalyticsEvent) = Unit

    override fun identify(user: AnalyticsUser) = Unit

    override fun setUserProperties(properties: AnalyticsProperties) = Unit

    override fun enterScreen(screen: AnalyticsScreen) {
        enteredScreens += screen
    }

    override fun exitScreen(screen: AnalyticsScreen) {
        exitedScreens += screen
    }

    override fun clearUser() = Unit

    override fun flush() = Unit

    override fun reset() = Unit
}
