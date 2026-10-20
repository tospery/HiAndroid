package com.tospery.base.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConsentAwareSdkRuntimeTest {
    @Test
    fun privacyConsentStatusKeepsStableProtocolValues() {
        assertArrayEquals(
            arrayOf(
                PrivacyConsentStatus.UNKNOWN,
                PrivacyConsentStatus.GRANTED,
                PrivacyConsentStatus.DENIED,
            ),
            PrivacyConsentStatus.entries.toTypedArray(),
        )
    }

    @Test
    fun noOpRuntimeAcceptsLifecycleOperationsAndRemainsUninitialized() {
        NoOpConsentAwareSdkRuntime.preInitialize()
        NoOpConsentAwareSdkRuntime.updatePrivacyConsent(
            PrivacyConsentStatus.GRANTED,
        )
        NoOpConsentAwareSdkRuntime.initialize()

        assertFalse(NoOpConsentAwareSdkRuntime.isInitialized())
    }
}
