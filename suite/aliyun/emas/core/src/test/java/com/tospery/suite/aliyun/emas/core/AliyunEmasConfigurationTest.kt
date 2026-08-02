package com.tospery.suite.aliyun.emas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AliyunEmasConfigurationTest {
    @Test
    fun defaultsEnableEveryMobileMonitoringComponent() {
        val configuration = configuration()

        assertEquals(AliyunEmasComponent.entries.toSet(), configuration.components)
        assertTrue(configuration.collection.collectDeviceModel)
        assertTrue(configuration.collection.collectOsVersion)
        assertTrue(configuration.collection.collectScreenResolution)
        assertTrue(configuration.collection.collectNetworkInfo)
        assertEquals(20, configuration.remoteLogCacheSizeMegabytes)
        assertEquals("app", configuration.remoteLogModuleName)
    }

    @Test
    fun credentialsChannelComponentsAndRemoteLogLimitsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            configuration(appKey = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(appSecret = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(appRsaSecret = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(channel = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(channel = "x".repeat(129))
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(components = emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(remoteLogModuleName = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(remoteLogCacheSizeMegabytes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration(remoteLogCacheSizeMegabytes = 101)
        }
    }

    @Test
    fun userInfoValidatesOfficialFieldLimitsAndSupportsPartialIdentity() {
        AliyunEmasUserInfo(id = "user-id")
        AliyunEmasUserInfo(nickname = "nickname")

        assertThrows(IllegalArgumentException::class.java) {
            AliyunEmasUserInfo()
        }
        assertThrows(IllegalArgumentException::class.java) {
            AliyunEmasUserInfo(id = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AliyunEmasUserInfo(nickname = "x".repeat(129))
        }
    }
}

internal fun configuration(
    appKey: String = "test-app-key",
    appSecret: String = "test-app-secret",
    appRsaSecret: String = "test-app-rsa-secret",
    channel: String? = "unit-test",
    initialUserInfo: AliyunEmasUserInfo? = null,
    components: Set<AliyunEmasComponent> = AliyunEmasComponent.entries.toSet(),
    collection: AliyunEmasCollectionConfiguration =
        AliyunEmasCollectionConfiguration(),
    remoteLogModuleName: String = "app",
    remoteLogCacheSizeMegabytes: Int = 20,
): AliyunEmasConfiguration =
    AliyunEmasConfiguration(
        appKey = appKey,
        appSecret = appSecret,
        appRsaSecret = appRsaSecret,
        channel = channel,
        initialUserInfo = initialUserInfo,
        components = components,
        collection = collection,
        remoteLogModuleName = remoteLogModuleName,
        remoteLogCacheSizeMegabytes = remoteLogCacheSizeMegabytes,
    )
