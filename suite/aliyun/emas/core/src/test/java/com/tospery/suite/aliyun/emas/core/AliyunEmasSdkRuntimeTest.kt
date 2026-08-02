package com.tospery.suite.aliyun.emas.core

import com.tospery.base.sdk.ConsentAwareSdkRuntime
import com.tospery.base.sdk.PrivacyConsentStatus
import com.tospery.base.sdk.SdkInitializationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AliyunEmasSdkRuntimeTest {
    @Test
    fun runtimeImplementsVendorNeutralLifecycleContracts() {
        val lifecycle: ConsentAwareSdkRuntime = runtime(RecordingAliyunEmasSdk())
        val state: SdkInitializationState = lifecycle

        assertFalse(state.isInitialized())
    }

    @Test
    fun preStartRunsBeforeConsentAndFormalInitializationWaitsForGrant() {
        val sdk = RecordingAliyunEmasSdk()
        val runtime = runtime(sdk)

        runtime.preInitialize()
        runtime.preInitialize()
        runtime.initialize()

        assertEquals(listOf("pre_start"), sdk.calls)
        assertFalse(runtime.isInitialized())
        assertFalse(runtime.performanceMonitor.isEnabled())

        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()
        runtime.initialize()

        assertEquals(
            listOf(
                "pre_start",
                "apply_collection:$COLLECTION",
                "start",
                "remote_log_level:INFO",
                "set_user:$INITIAL_USER",
            ),
            sdk.calls,
        )
        assertTrue(runtime.isInitialized())
        assertTrue(runtime.performanceMonitor.isEnabled())
    }

    @Test
    fun denialPreventsStartAndDisablesEverySupportedPrivacyField() {
        val sdk = RecordingAliyunEmasSdk()
        val runtime = runtime(sdk)

        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        runtime.preInitialize()
        runtime.initialize()

        assertEquals(
            listOf(
                "pre_start",
                "disable_privacy_collection",
            ),
            sdk.calls,
        )
        assertFalse(runtime.isInitialized())
        assertFalse(runtime.performanceMonitor.isEnabled())
    }

    @Test
    fun denialAfterStartStopsAdapterCollectionAndLaterGrantRestoresIt() {
        val sdk = RecordingAliyunEmasSdk()
        val runtime = runtime(sdk)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()
        sdk.calls.clear()

        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.DENIED)

        assertEquals(listOf("disable_privacy_collection"), sdk.calls)
        assertTrue(runtime.isInitialized())
        assertFalse(runtime.performanceMonitor.isEnabled())

        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)

        assertEquals(
            listOf(
                "disable_privacy_collection",
                "apply_collection:$COLLECTION",
                "set_user:$INITIAL_USER",
            ),
            sdk.calls,
        )
        assertTrue(runtime.performanceMonitor.isEnabled())
    }

    @Test
    fun runtimeUserInformationCanBeUpdatedAndClearedAfterInitialization() {
        val sdk = RecordingAliyunEmasSdk()
        val runtime = runtime(sdk)
        runtime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        runtime.initialize()
        sdk.userInfos.clear()

        val changedUser = AliyunEmasUserInfo(id = "changed-user", nickname = "Changed")
        runtime.updateUserInfo(changedUser)
        runtime.updateUserInfo(null)

        assertEquals(listOf(changedUser, null), sdk.userInfos)
    }

    @Test
    fun falseStartAndSdkExceptionsRemainInsideAdapterBoundary() {
        val falseStartSdk = RecordingAliyunEmasSdk(startResult = false)
        val falseStartRuntime = runtime(falseStartSdk)
        falseStartRuntime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        falseStartRuntime.initialize()

        assertFalse(falseStartRuntime.isInitialized())

        val failingSdk = RecordingAliyunEmasSdk(failingOperations = setOf("pre_start"))
        val failingRuntime = runtime(failingSdk)
        failingRuntime.updatePrivacyConsent(PrivacyConsentStatus.GRANTED)
        failingRuntime.initialize()

        assertFalse(failingRuntime.isInitialized())
        assertEquals(listOf("pre_start"), failingSdk.calls)
    }

    private fun runtime(sdk: RecordingAliyunEmasSdk): AliyunEmasSdkRuntime =
        AliyunEmasSdkRuntime(
            configuration =
                configuration(
                    initialUserInfo = INITIAL_USER,
                    collection = COLLECTION,
                ),
            sdk = sdk,
        )

    private companion object {
        val INITIAL_USER = AliyunEmasUserInfo(id = "initial-user", nickname = "Initial")
        val COLLECTION =
            AliyunEmasCollectionConfiguration(
                collectDeviceModel = false,
                collectOsVersion = true,
                collectScreenResolution = false,
                collectNetworkInfo = true,
            )
    }
}
