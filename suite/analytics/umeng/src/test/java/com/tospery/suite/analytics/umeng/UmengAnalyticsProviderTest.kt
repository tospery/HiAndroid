package com.tospery.suite.analytics.umeng

import com.tospery.base.analytics.AnalyticsConsentStatus
import com.tospery.base.analytics.AnalyticsEvent
import com.tospery.base.analytics.AnalyticsScreen
import com.tospery.base.analytics.AnalyticsUser
import com.tospery.base.analytics.AnalyticsValue
import com.tospery.suite.umeng.core.UmengInitializationPlugin
import com.tospery.suite.umeng.core.UmengPrivacyConsentStatus
import com.tospery.suite.umeng.core.UmengSdkLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengAnalyticsProviderTest {
    @Test
    fun lifecycleMethodsDelegateToSharedRuntime() {
        val lifecycle = RecordingUmengSdkLifecycle()
        val provider = provider(RecordingUmengSdk(), lifecycle)

        provider.preInitialize()
        provider.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
        provider.initialize()
        provider.initialize()

        assertEquals(
            listOf(
                "pre_initialize",
                "consent:${UmengPrivacyConsentStatus.GRANTED}",
                "initialize",
                "initialize",
            ),
            lifecycle.calls,
        )
        assertTrue(provider.isEnabled())
    }

    @Test
    fun analyticsConfigurationRunsAsSharedInitializationPluginOnce() {
        val sdk = RecordingUmengSdk()
        val lifecycle = RecordingUmengSdkLifecycle()
        val collection = UmengCollectionConfiguration(collectInstalledApps = true)
        val provider = provider(sdk, lifecycle, collection)

        provider.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
        provider.initialize()
        provider.initialize()

        assertEquals(
            listOf(
                "page_mode:manual",
                "collection:$collection",
            ),
            sdk.calls,
        )
        assertTrue(provider.isEnabled())
    }

    @Test
    fun denialAfterInitializationClosesScreensAndDisablesAnalyticsOnly() {
        val sdk = RecordingUmengSdk()
        val lifecycle = RecordingUmengSdkLifecycle()
        val provider = initializedProvider(sdk, lifecycle)

        provider.identify(AnalyticsUser(id = "user-1"))
        provider.enterScreen(AnalyticsScreen("settings"))
        provider.updatePrivacyConsent(AnalyticsConsentStatus.DENIED)
        provider.track(AnalyticsEvent("ignored_after_denial"))

        assertEquals(
            listOf(
                "sign_in:user-1",
                "screen_enter:settings",
                "screen_exit:settings",
                "sign_out",
                "disable",
            ),
            sdk.calls.takeLast(5),
        )
        assertFalse(provider.isEnabled())
        assertEquals(
            UmengPrivacyConsentStatus.DENIED,
            lifecycle.lastConsentStatus,
        )
    }

    @Test
    fun eventsAndIdentifiedUserPropertiesUseSupportedValueTypes() {
        val sdk = RecordingUmengSdk()
        val provider = initializedProvider(sdk, RecordingUmengSdkLifecycle())

        provider.track(
            AnalyticsEvent(
                name = "test_event",
                properties =
                    linkedMapOf(
                        "text" to AnalyticsValue.Text("value"),
                        "integer" to AnalyticsValue.IntegerNumber(7),
                        "decimal" to AnalyticsValue.DecimalNumber(2.5),
                        "boolean" to AnalyticsValue.BooleanValue(true),
                        "null" to AnalyticsValue.Null,
                        "invalid_decimal" to AnalyticsValue.DecimalNumber(Double.NaN),
                    ),
            ),
        )
        provider.identify(
            AnalyticsUser(
                id = "user-1",
                properties =
                    mapOf(
                        "tier" to AnalyticsValue.Text("free"),
                        "verified" to AnalyticsValue.BooleanValue(false),
                    ),
            ),
        )

        assertEquals(
            mapOf(
                "text" to "value",
                "integer" to 7L,
                "decimal" to 2.5,
                "boolean" to "true",
            ),
            sdk.events.single().second,
        )
        assertEquals(
            listOf("tier" to "free", "verified" to "false"),
            sdk.userProperties,
        )
    }

    @Test
    fun disablingAndExitSavingCloseOnlyActiveScreens() {
        val sdk = RecordingUmengSdk()
        val provider = initializedProvider(sdk, RecordingUmengSdkLifecycle())

        provider.enterScreen(AnalyticsScreen("first"))
        provider.enterScreen(AnalyticsScreen("second"))
        provider.exitScreen(AnalyticsScreen("first"))
        provider.setEnabled(false)
        provider.savePendingDataOnExit()

        assertEquals(
            listOf(
                "screen_enter:first",
                "screen_enter:second",
                "screen_exit:first",
                "screen_exit:second",
                "save_exit",
            ),
            sdk.calls.takeLast(5),
        )
        assertFalse(provider.isEnabled())
    }

    private fun initializedProvider(
        sdk: RecordingUmengSdk,
        lifecycle: RecordingUmengSdkLifecycle,
    ): UmengAnalyticsProvider =
        provider(sdk, lifecycle).also {
            it.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
            it.initialize()
        }

    private fun provider(
        sdk: RecordingUmengSdk,
        lifecycle: RecordingUmengSdkLifecycle,
        collection: UmengCollectionConfiguration = UmengCollectionConfiguration(),
    ): UmengAnalyticsProvider {
        val provider =
            UmengAnalyticsProvider(
                configuration = UmengAnalyticsConfiguration(collection = collection),
                sdk = sdk,
                sdkLifecycle = lifecycle,
            )
        lifecycle.registerInitializationPlugin(provider)
        return provider
    }
}

private class RecordingUmengSdkLifecycle : UmengSdkLifecycle {
    private val plugins = mutableListOf<UmengInitializationPlugin>()
    private var initialized = false
    private var configured = false
    val calls = mutableListOf<String>()
    var lastConsentStatus: UmengPrivacyConsentStatus = UmengPrivacyConsentStatus.UNKNOWN

    override fun isInitialized(): Boolean = initialized

    override fun registerInitializationPlugin(plugin: UmengInitializationPlugin) {
        plugins += plugin
    }

    override fun preInitialize() {
        calls += "pre_initialize"
    }

    override fun initialize() {
        calls += "initialize"
        if (initialized || lastConsentStatus != UmengPrivacyConsentStatus.GRANTED) {
            return
        }
        if (!configured) {
            plugins.forEach(UmengInitializationPlugin::configureBeforeInitialization)
            configured = true
        }
        initialized = true
        plugins.forEach(UmengInitializationPlugin::onInitialized)
    }

    override fun updatePrivacyConsent(status: UmengPrivacyConsentStatus) {
        if (lastConsentStatus == status) {
            return
        }
        lastConsentStatus = status
        calls += "consent:$status"
        if (status == UmengPrivacyConsentStatus.DENIED) {
            plugins.forEach(UmengInitializationPlugin::onPrivacyConsentDenied)
            initialized = false
        }
    }
}

private class RecordingUmengSdk : UmengSdk {
    val calls = mutableListOf<String>()
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val userProperties = mutableListOf<Pair<String, Any>>()

    override fun setManualPageCollection() {
        calls += "page_mode:manual"
    }

    override fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration) {
        calls += "collection:$configuration"
    }

    override fun disableAnalytics() {
        calls += "disable"
    }

    override fun trackEvent(name: String, properties: Map<String, Any>) {
        calls += "event:$name"
        events += name to properties
    }

    override fun signIn(userId: String) {
        calls += "sign_in:$userId"
    }

    override fun setUserProperty(name: String, value: Any) {
        calls += "user_property:$name:$value"
        userProperties += name to value
    }

    override fun signOut() {
        calls += "sign_out"
    }

    override fun enterScreen(name: String) {
        calls += "screen_enter:$name"
    }

    override fun exitScreen(name: String) {
        calls += "screen_exit:$name"
    }

    override fun savePendingDataOnExit() {
        calls += "save_exit"
    }
}
