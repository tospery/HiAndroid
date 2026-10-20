package com.tospery.base.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeConsentAwareSdkRuntimeTest {
    @Test
    fun lifecycleSignalsAreForwardedInRegistrationOrder() {
        val calls = mutableListOf<String>()
        val first = RecordingSdkRuntime("first", calls)
        val second = RecordingSdkRuntime("second", calls)
        val runtime = CompositeConsentAwareSdkRuntime(listOf(first, second))

        runtime.preInitialize()
        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()

        assertEquals(
            listOf(
                "first:pre_initialize",
                "second:pre_initialize",
                "first:consent:GRANTED",
                "second:consent:GRANTED",
                "first:initialize",
                "second:initialize",
            ),
            calls,
        )
    }

    @Test
    fun initializedStateReflectsAnyInitializedRuntime() {
        assertFalse(CompositeConsentAwareSdkRuntime(emptyList()).isInitialized())

        val runtime =
            CompositeConsentAwareSdkRuntime(
                listOf(
                    RecordingSdkRuntime("first", initialized = false),
                    RecordingSdkRuntime("second", initialized = true),
                ),
            )

        assertTrue(runtime.isInitialized())
    }
}

private class RecordingSdkRuntime(
    private val name: String,
    private val calls: MutableList<String> = mutableListOf(),
    private var initialized: Boolean = false,
) : ConsentAwareSdkRuntime {
    override fun isInitialized(): Boolean = initialized

    override fun preInitialize() {
        calls += "$name:pre_initialize"
    }

    override fun initialize() {
        calls += "$name:initialize"
        initialized = true
    }

    override fun updatePrivacyConsent(status: PrivacyConsentStatus) {
        calls += "$name:consent:${status.name}"
    }
}
