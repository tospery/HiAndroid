package com.tospery.base.analytics

import com.tospery.base.sdk.PrivacyConsentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeAnalyticsProviderTest {
    @Test
    fun eventIdentityAndScreenSignalsFollowEnabledAndPairingRules() {
        val enabledProvider = RecordingAnalyticsProvider(enabled = true)
        val disabledProvider = RecordingAnalyticsProvider(enabled = false)
        val provider =
            CompositeAnalyticsProvider(
                listOf(enabledProvider, disabledProvider),
            )
        val properties: AnalyticsProperties =
            mapOf(
                "result" to AnalyticsValue.Text("success"),
            )
        val event =
            AnalyticsEvent(
                name = "test_event",
                properties = properties,
            )
        val user =
            AnalyticsUser(
                id = "pseudonymous-user",
                properties = properties,
            )
        val screen =
            AnalyticsScreen(
                name = "test_screen",
                className = "TestScreen",
                properties = properties,
            )

        provider.track(event)
        provider.identify(user)
        provider.setUserProperties(properties)
        provider.enterScreen(screen)
        provider.exitScreen(screen)

        assertEquals(listOf(event), enabledProvider.events)
        assertEquals(listOf(user), enabledProvider.users)
        assertEquals(listOf(properties), enabledProvider.userProperties)
        assertEquals(listOf(screen), enabledProvider.screenEntries)
        assertEquals(listOf(screen), enabledProvider.screenExits)

        assertTrue(disabledProvider.events.isEmpty())
        assertTrue(disabledProvider.users.isEmpty())
        assertTrue(disabledProvider.userProperties.isEmpty())
        assertTrue(disabledProvider.screenEntries.isEmpty())
        assertEquals(listOf(screen), disabledProvider.screenExits)
    }

    @Test
    fun lifecyclePrivacyAndControlSignalsAreForwardedToEveryProvider() {
        val firstProvider = RecordingAnalyticsProvider(enabled = true)
        val secondProvider = RecordingAnalyticsProvider(enabled = false)
        val provider =
            CompositeAnalyticsProvider(
                listOf(firstProvider, secondProvider),
            )

        provider.setEnabled(false)
        provider.updatePrivacyConsent(PrivacyConsentStatus.UNKNOWN)
        provider.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        provider.preInitialize()
        provider.initialize()
        provider.savePendingDataOnExit()
        provider.clearUser()
        provider.flush()
        provider.reset()

        listOf(firstProvider, secondProvider).forEach { recordedProvider ->
            assertEquals(listOf(false), recordedProvider.enabledValues)
            assertEquals(
                listOf(
                    PrivacyConsentStatus.UNKNOWN,
                    PrivacyConsentStatus.GRANTED,
                ),
                recordedProvider.consentStatuses,
            )
            assertEquals(1, recordedProvider.preInitializeCalls)
            assertEquals(1, recordedProvider.initializeCalls)
            assertEquals(1, recordedProvider.savePendingDataOnExitCalls)
            assertEquals(1, recordedProvider.clearUserCalls)
            assertEquals(1, recordedProvider.flushCalls)
            assertEquals(1, recordedProvider.resetCalls)
        }
    }

    @Test
    fun enabledStateReflectsAnyEnabledProvider() {
        val firstProvider = RecordingAnalyticsProvider(enabled = false)
        val secondProvider = RecordingAnalyticsProvider(enabled = true)
        val provider =
            CompositeAnalyticsProvider(
                listOf(firstProvider, secondProvider),
            )

        assertTrue(provider.isEnabled())

        secondProvider.setEnabled(false)

        assertFalse(provider.isEnabled())
    }

    @Test
    fun noOpProviderRemainsDisabledAndAcceptsAllOperations() {
        val properties: AnalyticsProperties =
            mapOf(
                "result" to AnalyticsValue.Text("ignored"),
            )
        val screen =
            AnalyticsScreen(
                name = "ignored_screen",
                properties = properties,
            )

        NoOpAnalyticsProvider.setEnabled(true)
        NoOpAnalyticsProvider.updatePrivacyConsent(PrivacyConsentStatus.UNKNOWN)
        NoOpAnalyticsProvider.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        NoOpAnalyticsProvider.preInitialize()
        NoOpAnalyticsProvider.initialize()
        NoOpAnalyticsProvider.track(
            AnalyticsEvent(
                name = "ignored_event",
                properties = properties,
            ),
        )
        NoOpAnalyticsProvider.identify(
            AnalyticsUser(
                id = "ignored-user",
                properties = properties,
            ),
        )
        NoOpAnalyticsProvider.setUserProperties(properties)
        NoOpAnalyticsProvider.enterScreen(screen)
        NoOpAnalyticsProvider.exitScreen(screen)
        NoOpAnalyticsProvider.savePendingDataOnExit()
        NoOpAnalyticsProvider.clearUser()
        NoOpAnalyticsProvider.flush()
        NoOpAnalyticsProvider.reset()

        assertFalse(NoOpAnalyticsProvider.isEnabled())
    }

    private class RecordingAnalyticsProvider(
        enabled: Boolean,
    ) : AnalyticsProvider {
        private var enabled: Boolean = enabled

        val enabledValues = mutableListOf<Boolean>()
        val events = mutableListOf<AnalyticsEvent>()
        val users = mutableListOf<AnalyticsUser>()
        val userProperties = mutableListOf<AnalyticsProperties>()
        val screenEntries = mutableListOf<AnalyticsScreen>()
        val screenExits = mutableListOf<AnalyticsScreen>()
        val consentStatuses = mutableListOf<PrivacyConsentStatus>()

        var preInitializeCalls: Int = 0
        var initializeCalls: Int = 0
        var savePendingDataOnExitCalls: Int = 0
        var clearUserCalls: Int = 0
        var flushCalls: Int = 0
        var resetCalls: Int = 0

        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            enabledValues += enabled
        }

        override fun track(event: AnalyticsEvent) {
            events += event
        }

        override fun identify(user: AnalyticsUser) {
            users += user
        }

        override fun setUserProperties(properties: AnalyticsProperties) {
            userProperties += properties
        }

        override fun enterScreen(screen: AnalyticsScreen) {
            screenEntries += screen
        }

        override fun exitScreen(screen: AnalyticsScreen) {
            screenExits += screen
        }

        override fun clearUser() {
            clearUserCalls++
        }

        override fun flush() {
            flushCalls++
        }

        override fun reset() {
            resetCalls++
        }

        override fun preInitialize() {
            preInitializeCalls++
        }

        override fun initialize() {
            initializeCalls++
        }

        override fun savePendingDataOnExit() {
            savePendingDataOnExitCalls++
        }

        override fun updatePrivacyConsent(status: PrivacyConsentStatus) {
            consentStatuses += status
        }
    }
}
