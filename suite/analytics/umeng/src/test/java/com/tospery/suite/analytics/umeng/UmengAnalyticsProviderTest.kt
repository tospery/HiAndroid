package com.tospery.suite.analytics.umeng

import com.tospery.base.analytics.AnalyticsConsentStatus
import com.tospery.base.analytics.AnalyticsEvent
import com.tospery.base.analytics.AnalyticsScreen
import com.tospery.base.analytics.AnalyticsUser
import com.tospery.base.analytics.AnalyticsValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengAnalyticsProviderTest {
    @Test
    fun preInitializationWaitsForConsentAndRemainsIdempotent() {
        val sdk = RecordingUmengSdk()
        val collection = UmengCollectionConfiguration(collectInstalledApps = true)
        val provider = provider(sdk = sdk, collection = collection, debugLoggingEnabled = true)

        provider.preInitialize()
        provider.preInitialize()

        assertTrue(sdk.calls.isEmpty())

        provider.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
        provider.preInitialize()

        assertEquals(
            listOf(
                "debug:true",
                "page_mode:manual",
                "collection:$collection",
                "pre_init:$APP_KEY:$CHANNEL",
                "consent:true",
            ),
            sdk.calls,
        )
    }

    @Test
    fun initializationRequiresGrantedConsentAndIsIdempotent() {
        val sdk = RecordingUmengSdk()
        val provider = provider(sdk)

        provider.initialize()
        assertFalse(provider.isEnabled())

        provider.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
        provider.initialize()
        provider.initialize()

        assertTrue(provider.isEnabled())
        assertEquals(1, sdk.calls.count { it.startsWith("pre_init:") })
        assertEquals(1, sdk.calls.count { it.startsWith("init:") })
        assertEquals(1, sdk.calls.count { it == "consent:true" })
    }

    @Test
    fun initializationPluginsRunAroundSharedSdkInitializationOnce() {
        val sdk = RecordingUmengSdk()
        val plugin = RecordingUmengInitializationPlugin(sdk.calls)
        val provider =
            provider(
                sdk = sdk,
                initializationPlugins = listOf(plugin),
            )

        provider.preInitialize()
        provider.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
        provider.initialize()
        provider.initialize()

        assertEquals(
            listOf(
                "debug:false",
                "page_mode:manual",
                "collection:${UmengCollectionConfiguration()}",
                "pre_init:$APP_KEY:$CHANNEL",
                "consent:true",
                "plugin_configure",
                "init:$APP_KEY:$CHANNEL",
                "plugin_initialized",
            ),
            sdk.calls,
        )
    }

    @Test
    fun privacyDenialIsForwardedToPluginsOnce() {
        val sdk = RecordingUmengSdk()
        val plugin = RecordingUmengInitializationPlugin(sdk.calls)
        val provider =
            provider(
                sdk = sdk,
                initializationPlugins = listOf(plugin),
            )

        provider.updatePrivacyConsent(AnalyticsConsentStatus.DENIED)
        provider.updatePrivacyConsent(AnalyticsConsentStatus.DENIED)

        assertEquals(
            listOf(
                "plugin_denied",
                "consent:false",
            ),
            sdk.calls,
        )
        assertFalse(provider.isEnabled())
    }

    @Test
    fun denialAfterInitializationClosesScreensAndDisablesSdk() {
        val sdk = RecordingUmengSdk()
        val provider = initializedProvider(sdk)

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
                "consent:false",
                "disable",
            ),
            sdk.calls.takeLast(6),
        )
        assertFalse(provider.isEnabled())
    }

    @Test
    fun eventsAndIdentifiedUserPropertiesUseSupportedValueTypes() {
        val sdk = RecordingUmengSdk()
        val provider = initializedProvider(sdk)

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
        val provider = initializedProvider(sdk)

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

    private fun initializedProvider(sdk: RecordingUmengSdk): UmengAnalyticsProvider =
        provider(sdk).also {
            it.updatePrivacyConsent(AnalyticsConsentStatus.GRANTED)
            it.initialize()
        }

    private fun provider(
        sdk: RecordingUmengSdk,
        collection: UmengCollectionConfiguration = UmengCollectionConfiguration(),
        debugLoggingEnabled: Boolean = false,
        initializationPlugins: List<UmengInitializationPlugin> = emptyList(),
    ): UmengAnalyticsProvider =
        UmengAnalyticsProvider(
            configuration =
                UmengAnalyticsConfiguration(
                    appKey = APP_KEY,
                    channel = CHANNEL,
                    collection = collection,
                    debugLoggingEnabled = debugLoggingEnabled,
                ),
            sdk = sdk,
            initializationPlugins = initializationPlugins,
        )

    private companion object {
        const val APP_KEY = "123456789012345678901234"
        const val CHANNEL = "unit_test"
    }
}

private class RecordingUmengSdk : UmengSdk {
    val calls = mutableListOf<String>()
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val userProperties = mutableListOf<Pair<String, Any>>()

    override fun setDebugLogging(enabled: Boolean) {
        calls += "debug:$enabled"
    }

    override fun setManualPageCollection() {
        calls += "page_mode:manual"
    }

    override fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration) {
        calls += "collection:$configuration"
    }

    override fun preInitialize(appKey: String, channel: String) {
        calls += "pre_init:$appKey:$channel"
    }

    override fun initialize(appKey: String, channel: String) {
        calls += "init:$appKey:$channel"
    }

    override fun submitPrivacyConsent(granted: Boolean) {
        calls += "consent:$granted"
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

private class RecordingUmengInitializationPlugin(
    private val calls: MutableList<String>,
) : UmengInitializationPlugin {
    override fun configureBeforeInitialization() {
        calls += "plugin_configure"
    }

    override fun onInitialized() {
        calls += "plugin_initialized"
    }

    override fun onPrivacyConsentDenied() {
        calls += "plugin_denied"
    }
}
