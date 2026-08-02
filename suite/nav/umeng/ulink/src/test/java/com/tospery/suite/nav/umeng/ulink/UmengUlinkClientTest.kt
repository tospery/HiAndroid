package com.tospery.suite.nav.umeng.ulink

import com.tospery.base.sdk.SdkInitializationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmengUlinkClientTest {
    @Test
    fun onlyMarkedHiGitUrlsAreRecognizedAsUlinkWakeups() {
        val client = client(RecordingSdk())

        assertEquals(
            UmengUlinkWakeupUrlClassification.VALID,
            client.classifyWakeupUrl(
                "higit://tospery.com/ulink?_sdk=umeng&route=octocat",
            ),
        )
        assertTrue(client.isUlinkWakeupUrl("higit://tospery.com/ulink?linkid=42"))
        assertEquals(
            UmengUlinkWakeupUrlClassification.NOT_ULINK,
            client.classifyWakeupUrl("higit://octocat"),
        )
        assertEquals(
            UmengUlinkWakeupUrlClassification.INVALID,
            client.classifyWakeupUrl("higit://evil.example/ulink?_sdk=umeng"),
        )
        assertEquals(
            UmengUlinkWakeupUrlClassification.INVALID,
            client.classifyWakeupUrl("https://example.com/?_sdk=umeng"),
        )
        assertFalse(client.isUlinkWakeupUrl("higit://tospery.com/ulink?_sdk=umeng#fragment"))
    }

    @Test
    fun deferredWakeupUrlIsResolvedThroughTheSameCallback() {
        val sdk = RecordingSdk()
        val client = client(sdk)
        val results = mutableListOf<UmengUlinkResult>()

        client.requestDeferredLink(onResult = results::add)
        sdk.installCallback?.onInstall(
            parameters = mapOf("invite" to "abc"),
            wakeupUrl = "higit://tospery.com/ulink?_sdk=umeng&route=octocat",
        )
        sdk.handleCallback?.onLink(
            path = "/ulink",
            queryParameters = mapOf("route" to "octocat"),
        )

        assertEquals(
            UmengUlinkResult.Resolved(
                target =
                    UmengUlinkTarget(
                        path = "/ulink",
                        queryParameters = mapOf("route" to "octocat"),
                    ),
                installParameters = mapOf("invite" to "abc"),
            ),
            results.single(),
        )
        assertEquals(false, sdk.clipboardEnabled)
    }

    @Test
    fun sdkCallsFailFastBeforeSharedRuntimeInitialization() {
        val sdk = RecordingSdk()
        val client = client(sdk, initialized = false)
        val results = mutableListOf<UmengUlinkResult>()

        client.handleWakeupUrl(
            "higit://tospery.com/ulink?_sdk=umeng",
            results::add,
        )
        client.requestDeferredLink(onResult = results::add)

        assertTrue(results.all { it is UmengUlinkResult.Failed })
        assertEquals(null, sdk.handleCallback)
        assertEquals(null, sdk.installCallback)
    }

    @Test
    fun deferredCallbackDoesNotReenterSdkAfterRuntimeBecomesUnavailable() {
        val sdk = RecordingSdk()
        var initialized = true
        val client =
            UmengUlinkClient(
                sdk = sdk,
                sdkState = SdkInitializationState { initialized },
                appScheme = "higit",
                concatenationHost = "tospery.com",
            )
        val results = mutableListOf<UmengUlinkResult>()

        client.requestDeferredLink(onResult = results::add)
        initialized = false
        sdk.installCallback?.onInstall(
            parameters = emptyMap(),
            wakeupUrl = "higit://tospery.com/ulink?_sdk=umeng",
        )

        assertTrue(results.single() is UmengUlinkResult.Failed)
        assertEquals(null, sdk.handleCallback)
    }

    private fun client(
        sdk: RecordingSdk,
        initialized: Boolean = true,
    ): UmengUlinkClient =
        UmengUlinkClient(
            sdk = sdk,
            sdkState = SdkInitializationState { initialized },
            appScheme = "higit",
            concatenationHost = "tospery.com",
        )
}

private class RecordingSdk : UmengUlinkSdk {
    var handleCallback: UmengUlinkCallback? = null
    var installCallback: UmengUlinkCallback? = null
    var clipboardEnabled: Boolean? = null

    override fun handle(
        url: String,
        listener: UmengUlinkCallback,
    ) {
        handleCallback = listener
    }

    override fun requestInstallParameters(
        clipboardEnabled: Boolean,
        listener: UmengUlinkCallback,
    ) {
        this.clipboardEnabled = clipboardEnabled
        installCallback = listener
    }
}
