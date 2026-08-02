package com.tospery.suite.analytics.umeng

import android.content.Context
import com.tospery.base.analytics.AnalyticsConsentStatus
import com.tospery.base.analytics.AnalyticsEvent
import com.tospery.base.analytics.AnalyticsProperties
import com.tospery.base.analytics.AnalyticsProvider
import com.tospery.base.analytics.AnalyticsScreen
import com.tospery.base.analytics.AnalyticsUser
import com.tospery.base.analytics.AnalyticsValue
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.info
import com.tospery.buildmetadata.module_suite_analytics_umeng.ModuleMetadata
import com.tospery.suite.umeng.core.UmengInitializationPlugin
import com.tospery.suite.umeng.core.UmengPrivacyConsentStatus
import com.tospery.suite.umeng.core.UmengSdkLifecycle
import com.umeng.analytics.MobclickAgent
import com.uyumao.sdk.UYMManager

data class UmengAnalyticsConfiguration(
    val collection: UmengCollectionConfiguration = UmengCollectionConfiguration(),
)

/** U-Yumao 流失卸载分析的可选采集配置，默认全部关闭。 */
data class UmengCollectionConfiguration(
    val collectPreciseLocation: Boolean = false,
    val collectCachedLocation: Boolean = false,
    val collectConnectedWifi: Boolean = false,
    val collectWifiList: Boolean = false,
    val collectCellTower: Boolean = false,
    val collectInstalledApps: Boolean = false,
)

private val umengAnalyticsLifecycleLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "lifecycle",
    )

/**
 * 将厂商无关的统计协议适配到友盟移动统计。
 *
 * 正式初始化及所有统计信号均受隐私授权状态约束。
 */
class UmengAnalyticsProvider internal constructor(
    private val configuration: UmengAnalyticsConfiguration,
    private val sdk: UmengSdk,
    private val sdkLifecycle: UmengSdkLifecycle,
) : AnalyticsProvider, UmengInitializationPlugin {
    private var requestedEnabled: Boolean = true
    private var configured: Boolean = false
    private var initialized: Boolean = false
    private var sdkPermanentlyDisabled: Boolean = false
    private var identified: Boolean = false
    private val activeScreens = mutableListOf<String>()

    @Synchronized
    override fun isEnabled(): Boolean = canCollect()

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        if (requestedEnabled == enabled) {
            return
        }

        if (!enabled) {
            closeActiveScreens()
        }
        requestedEnabled = enabled
    }

    override fun preInitialize() {
        sdkLifecycle.preInitialize()
    }

    override fun initialize() {
        if (requestedEnabled && !sdkPermanentlyDisabled) {
            sdkLifecycle.initialize()
        }
    }

    override fun updatePrivacyConsent(status: AnalyticsConsentStatus) {
        sdkLifecycle.updatePrivacyConsent(status.toUmengPrivacyConsentStatus())
    }

    @Synchronized
    override fun configureBeforeInitialization() {
        if (configured || sdkPermanentlyDisabled) {
            return
        }

        sdk.setManualPageCollection()
        sdk.applyCollectionConfiguration(configuration.collection)
        configured = true
        info(tag = umengAnalyticsLifecycleLogTag) {
            "友盟移动分析初始化配置完成。"
        }
    }

    @Synchronized
    override fun onInitialized() {
        if (!configured || initialized || sdkPermanentlyDisabled) {
            return
        }

        initialized = true
        info(tag = umengAnalyticsLifecycleLogTag) {
            "友盟移动分析已随公共 SDK 完成初始化。"
        }
    }

    @Synchronized
    override fun onPrivacyConsentDenied() {
        if (sdkPermanentlyDisabled) {
            return
        }

        val wasInitialized = initialized
        closeActiveScreens()
        clearIdentifiedUser()
        initialized = false
        if (wasInitialized) {
            sdk.disableAnalytics()
            sdkPermanentlyDisabled = true
        }

        info(
            tag = umengAnalyticsLifecycleLogTag,
            attributes = listOf(LogAttribute("was_initialized", wasInitialized.toString())),
        ) {
            "友盟移动分析已处理隐私授权拒绝。"
        }
    }

    @Synchronized
    override fun track(event: AnalyticsEvent) {
        if (!canCollect() || event.name.isBlank()) {
            return
        }

        sdk.trackEvent(
            name = event.name,
            properties = event.properties.toUmengMap(),
        )
    }

    @Synchronized
    override fun identify(user: AnalyticsUser) {
        if (!canCollect() || !user.id.isValidUmengUserId()) {
            return
        }

        sdk.signIn(user.id)
        identified = true
        setUserPropertiesInternal(user.properties)
    }

    @Synchronized
    override fun setUserProperties(properties: AnalyticsProperties) {
        if (!canCollect() || !identified) {
            return
        }

        setUserPropertiesInternal(properties)
    }

    @Synchronized
    override fun enterScreen(screen: AnalyticsScreen) {
        if (!canCollect() || screen.name.isBlank()) {
            return
        }

        sdk.enterScreen(screen.name)
        activeScreens += screen.name
    }

    @Synchronized
    override fun exitScreen(screen: AnalyticsScreen) {
        if (!initialized || sdkPermanentlyDisabled) {
            return
        }

        val index = activeScreens.indexOfLast { it == screen.name }
        if (index < 0) {
            return
        }

        activeScreens.removeAt(index)
        sdk.exitScreen(screen.name)
    }

    @Synchronized
    override fun clearUser() {
        clearIdentifiedUser()
    }

    /** 友盟没有公开的通用立即上传接口；退出保存由 [savePendingDataOnExit] 负责。 */
    override fun flush() = Unit

    @Synchronized
    override fun reset() {
        closeActiveScreens()
        clearIdentifiedUser()
    }

    @Synchronized
    override fun savePendingDataOnExit() {
        closeActiveScreens()

        if (initialized && !sdkPermanentlyDisabled) {
            sdk.savePendingDataOnExit()
        }
    }

    private fun canCollect(): Boolean =
        requestedEnabled &&
            initialized &&
            !sdkPermanentlyDisabled

    private fun setUserPropertiesInternal(properties: AnalyticsProperties) {
        properties.entries
            .asSequence()
            .filter { it.key.isNotBlank() }
            .mapNotNull { entry ->
                entry.value.toUmengValue()?.let { value ->
                    entry.key to value
                }
            }
            .take(MAX_USER_PROPERTIES)
            .forEach { (name, value) ->
                sdk.setUserProperty(name, value)
            }
    }

    private fun clearIdentifiedUser() {
        if (initialized && identified && !sdkPermanentlyDisabled) {
            sdk.signOut()
        }
        identified = false
    }

    private fun closeActiveScreens() {
        activeScreens
            .asReversed()
            .forEach(sdk::exitScreen)
        activeScreens.clear()
    }

    companion object {
        fun create(
            context: Context,
            configuration: UmengAnalyticsConfiguration,
            sdkLifecycle: UmengSdkLifecycle,
        ): UmengAnalyticsProvider {
            val provider = UmengAnalyticsProvider(
                configuration = configuration,
                sdk = AndroidUmengSdk(context),
                sdkLifecycle = sdkLifecycle,
            )
            sdkLifecycle.registerInitializationPlugin(provider)
            return provider
        }

        private const val MAX_USER_PROPERTIES = 20
        private const val MAX_USER_ID_BYTES = 63
    }

    private fun String.isValidUmengUserId(): Boolean =
        isNotBlank() && toByteArray(Charsets.UTF_8).size <= MAX_USER_ID_BYTES
}

internal interface UmengSdk {
    fun setManualPageCollection()

    fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration)

    fun disableAnalytics()

    fun trackEvent(name: String, properties: Map<String, Any>)

    fun signIn(userId: String)

    fun setUserProperty(name: String, value: Any)

    fun signOut()

    fun enterScreen(name: String)

    fun exitScreen(name: String)

    fun savePendingDataOnExit()
}

private class AndroidUmengSdk(
    context: Context,
) : UmengSdk {
    private val applicationContext: Context = context.applicationContext ?: context

    override fun setManualPageCollection() {
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)
    }

    override fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration) {
        UYMManager.enableYm1(applicationContext, configuration.collectPreciseLocation)
        UYMManager.enableYm2(applicationContext, configuration.collectCachedLocation)
        UYMManager.enableYm3(applicationContext, configuration.collectConnectedWifi)
        UYMManager.enableYm4(applicationContext, configuration.collectWifiList)
        UYMManager.enableYm5(applicationContext, configuration.collectCellTower)
        UYMManager.enableYm6(applicationContext, configuration.collectInstalledApps)
    }

    override fun disableAnalytics() {
        MobclickAgent.disable()
    }

    override fun trackEvent(name: String, properties: Map<String, Any>) {
        if (properties.isEmpty()) {
            MobclickAgent.onEvent(applicationContext, name)
        } else {
            MobclickAgent.onEventObject(applicationContext, name, properties)
        }
    }

    override fun signIn(userId: String) {
        MobclickAgent.onProfileSignIn(userId)
    }

    override fun setUserProperty(name: String, value: Any) {
        MobclickAgent.userProfile(name, value)
    }

    override fun signOut() {
        MobclickAgent.onProfileSignOff()
    }

    override fun enterScreen(name: String) {
        MobclickAgent.onPageStart(name)
    }

    override fun exitScreen(name: String) {
        MobclickAgent.onPageEnd(name)
    }

    override fun savePendingDataOnExit() {
        MobclickAgent.onKillProcess(applicationContext)
    }
}

private fun AnalyticsProperties.toUmengMap(): Map<String, Any> =
    buildMap {
        this@toUmengMap.forEach { (name, value) ->
            if (name.isNotBlank()) {
                value.toUmengValue()?.let { mappedValue ->
                    put(name, mappedValue)
                }
            }
        }
    }

private fun AnalyticsValue.toUmengValue(): Any? =
    when (this) {
        is AnalyticsValue.Text -> value
        is AnalyticsValue.IntegerNumber -> value
        is AnalyticsValue.DecimalNumber -> value.takeIf { it.isFinite() }
        is AnalyticsValue.BooleanValue -> value.toString()
        AnalyticsValue.Null -> null
    }

private fun AnalyticsConsentStatus.toUmengPrivacyConsentStatus(): UmengPrivacyConsentStatus =
    when (this) {
        AnalyticsConsentStatus.UNKNOWN -> UmengPrivacyConsentStatus.UNKNOWN
        AnalyticsConsentStatus.GRANTED -> UmengPrivacyConsentStatus.GRANTED
        AnalyticsConsentStatus.DENIED -> UmengPrivacyConsentStatus.DENIED
    }
