package com.tospery.suite.umeng.core

import com.tospery.base.sdk.ConsentAwareSdkRuntime
import com.tospery.base.sdk.PrivacyConsentStatus
import com.tospery.base.sdk.SdkInitializationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengSdkRuntimeTest {
    @Test
    fun runtimeImplementsVendorNeutralLifecycleContracts() {
        val lifecycle: ConsentAwareSdkRuntime = runtime(RecordingUmengCommonSdk())
        val state: SdkInitializationState = lifecycle

        assertFalse(state.isInitialized())
    }

    @Test
    fun configurationValidatesSharedIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            UmengSdkConfiguration(appKey = "invalid", channel = CHANNEL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UmengSdkConfiguration(appKey = APP_KEY, channel = "invalid channel")
        }
    }

    @Test
    fun preInitializationWaitsForConsentAndFormalInitializationRunsOnce() {
        val sdk = RecordingUmengCommonSdk()
        val plugin = RecordingPlugin(sdk.calls)
        val runtime = runtime(sdk).also { it.registerInitializationPlugin(plugin) }

        runtime.preInitialize()
        runtime.preInitialize()
        assertTrue(sdk.calls.isEmpty())

        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()
        runtime.initialize()

        assertEquals(
            listOf(
                "plugin_configure",
                "debug:true",
                "collection:$COLLECTION",
                "pre_init:$APP_KEY:$CHANNEL",
                "consent:true",
                "init:$APP_KEY:$CHANNEL",
                "plugin_initialized",
            ),
            sdk.calls,
        )
        assertTrue(runtime.isInitialized())
    }

    @Test
    fun denialIsIdempotentAndDisablesInitializedRuntime() {
        val sdk = RecordingUmengCommonSdk()
        val plugin = RecordingPlugin(sdk.calls)
        val runtime = runtime(sdk).also { it.registerInitializationPlugin(plugin) }

        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()
        sdk.calls.clear()

        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()

        assertEquals(
            listOf(
                "plugin_denied",
                "consent:false",
            ),
            sdk.calls,
        )
        assertFalse(runtime.isInitialized())
    }

    @Test
    fun denialBeforeAnySdkConfigurationCanBeFollowedByConsent() {
        val sdk = RecordingUmengCommonSdk()
        val plugin = RecordingPlugin(sdk.calls)
        val runtime = runtime(sdk).also { it.registerInitializationPlugin(plugin) }

        runtime.preInitialize()
        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.UNKNOWN)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()

        assertEquals(
            listOf(
                "plugin_denied",
                "consent:false",
                "plugin_configure",
                "debug:true",
                "collection:$COLLECTION",
                "pre_init:$APP_KEY:$CHANNEL",
                "consent:true",
                "init:$APP_KEY:$CHANNEL",
                "plugin_initialized",
            ),
            sdk.calls,
        )
        assertTrue(runtime.isInitialized())
    }

    @Test
    fun oneFailingPluginDoesNotBlockOtherProductsOrCommonInitialization() {
        val sdk = RecordingUmengCommonSdk()
        val runtime = runtime(sdk)
        runtime.registerInitializationPlugin(
            object : UmengInitializationPlugin {
                override fun configureBeforeInitialization() {
                    error("expected test failure")
                }
            },
        )
        runtime.registerInitializationPlugin(RecordingPlugin(sdk.calls))

        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()

        assertTrue(runtime.isInitialized())
        assertTrue("plugin_configure" in sdk.calls)
        assertEquals(1, sdk.calls.count { it.startsWith("init:") })
    }

    private fun runtime(sdk: RecordingUmengCommonSdk): UmengSdkRuntime =
        UmengSdkRuntime(
            configuration =
                UmengSdkConfiguration(
                    appKey = APP_KEY,
                    channel = CHANNEL,
                    collection = COLLECTION,
                    debugLoggingEnabled = true,
                ),
            sdk = sdk,
        )

    private companion object {
        const val APP_KEY = "123456789012345678901234"
        const val CHANNEL = "unit_test"
        val COLLECTION =
            UmengCommonCollectionConfiguration(
                collectInstalledApps = true,
            )
    }
}

private class RecordingPlugin(
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

private class RecordingUmengCommonSdk : UmengCommonSdk {
    val calls = mutableListOf<String>()

    override fun setDebugLogging(enabled: Boolean) {
        calls += "debug:$enabled"
    }

    override fun applyCollectionConfiguration(
        configuration: UmengCommonCollectionConfiguration,
    ) {
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
}
