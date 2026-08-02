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
import com.tospery.base.logging.debug
import com.tospery.base.logging.info
import com.tospery.buildmetadata.module_suite_analytics_umeng.ModuleMetadata
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import com.uyumao.sdk.UYMManager

data class UmengAnalyticsConfiguration(
    val appKey: String,
    val channel: String,
    val collection: UmengCollectionConfiguration = UmengCollectionConfiguration(),
    val debugLoggingEnabled: Boolean = false,
) {
    init {
        require(APP_KEY_PATTERN.matches(appKey)) {
            "友盟 AppKey 必须是24位字母或数字。"
        }
        require(CHANNEL_PATTERN.matches(channel)) {
            "友盟渠道必须是1至64位字母、数字、点、下划线或连字符。"
        }
    }

    private companion object {
        val APP_KEY_PATTERN = Regex("[A-Za-z0-9]{24}")
        val CHANNEL_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
    }
}

data class UmengCollectionConfiguration(
    val collectImei: Boolean = false,
    val collectImsi: Boolean = false,
    val collectIccid: Boolean = false,
    val collectWifiMac: Boolean = false,
    val collectPreciseLocation: Boolean = false,
    val collectCachedLocation: Boolean = false,
    val collectConnectedWifi: Boolean = false,
    val collectWifiList: Boolean = false,
    val collectCellTower: Boolean = false,
    val collectInstalledApps: Boolean = false,
)

/**
 * 接入同一套友盟公共 SDK 生命周期的可选业务组件。
 *
 * U-APM 等组件通过该接口在唯一一次 UMConfigure.init 前完成配置。
 * 所有方法必须保持幂等、快速且不得抛出异常。
 */
interface UmengInitializationPlugin {
    fun configureBeforeInitialization()

    fun onInitialized() = Unit

    fun onPrivacyConsentDenied() = Unit
}

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
    initializationPlugins: List<UmengInitializationPlugin> = emptyList(),
) : AnalyticsProvider {
    private val initializationPlugins = initializationPlugins.toList()
    private var requestedEnabled: Boolean = true
    private var consentStatus: AnalyticsConsentStatus = AnalyticsConsentStatus.UNKNOWN
    private var preInitializationRequested: Boolean = false
    private var preInitialized: Boolean = false
    private var initializationPluginsConfigured: Boolean = false
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

    @Synchronized
    override fun preInitialize() {
        if (
            preInitializationRequested ||
            preInitialized ||
            sdkPermanentlyDisabled
        ) {
            return
        }

        preInitializationRequested = true
        if (consentStatus != AnalyticsConsentStatus.GRANTED) {
            debug(
                tag = umengAnalyticsLifecycleLogTag,
                attributes =
                    listOf(
                        LogAttribute(
                            key = "consent_status",
                            value = consentStatus.name,
                        ),
                    ),
            ) {
                "友盟 SDK 预初始化等待隐私授权。"
            }
            return
        }

        performPreInitialization()
    }

    @Synchronized
    override fun initialize() {
        if (
            initialized ||
            sdkPermanentlyDisabled ||
            !requestedEnabled ||
            consentStatus != AnalyticsConsentStatus.GRANTED
        ) {
            return
        }

        if (!preInitialized) {
            preInitialize()
        }
        if (!preInitialized) {
            return
        }

        if (!initializationPluginsConfigured) {
            initializationPlugins.forEach(
                UmengInitializationPlugin::configureBeforeInitialization,
            )
            initializationPluginsConfigured = true
        }

        sdk.initialize(
            appKey = configuration.appKey,
            channel = configuration.channel,
        )
        initialized = true
        initializationPlugins.forEach(UmengInitializationPlugin::onInitialized)

        info(
            tag = umengAnalyticsLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "plugin_count",
                        value = initializationPlugins.size.toString(),
                    ),
                    LogAttribute(
                        key = "debug_logging_enabled",
                        value = configuration.debugLoggingEnabled.toString(),
                    ),
                ),
        ) {
            "友盟 SDK 正式初始化完成。"
        }
    }

    @Synchronized
    override fun updatePrivacyConsent(status: AnalyticsConsentStatus) {
        if (consentStatus == status) {
            return
        }

        consentStatus = status
        when (status) {
            AnalyticsConsentStatus.UNKNOWN -> Unit
            AnalyticsConsentStatus.GRANTED -> {
                if (preInitializationRequested) {
                    performPreInitialization()
                }
                sdk.submitPrivacyConsent(granted = true)
            }

            AnalyticsConsentStatus.DENIED -> {
                val wasInitialized = initialized
                closeActiveScreens()
                clearIdentifiedUser()
                initializationPlugins.forEach(
                    UmengInitializationPlugin::onPrivacyConsentDenied,
                )
                sdk.submitPrivacyConsent(granted = false)

                if (initialized && !sdkPermanentlyDisabled) {
                    sdk.disableAnalytics()
                    sdkPermanentlyDisabled = true
                }

                info(
                    tag = umengAnalyticsLifecycleLogTag,
                    attributes =
                        listOf(
                            LogAttribute(
                                key = "was_initialized",
                                value = wasInitialized.toString(),
                            ),
                            LogAttribute(
                                key = "plugin_count",
                                value = initializationPlugins.size.toString(),
                            ),
                        ),
                ) {
                    "友盟 SDK 已处理隐私授权拒绝。"
                }
            }
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

    private fun performPreInitialization() {
        if (
            preInitialized ||
            sdkPermanentlyDisabled ||
            consentStatus != AnalyticsConsentStatus.GRANTED
        ) {
            return
        }

        sdk.setDebugLogging(configuration.debugLoggingEnabled)
        sdk.setManualPageCollection()
        sdk.applyCollectionConfiguration(configuration.collection)
        sdk.preInitialize(
            appKey = configuration.appKey,
            channel = configuration.channel,
        )
        preInitialized = true

        info(
            tag = umengAnalyticsLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "debug_logging_enabled",
                        value = configuration.debugLoggingEnabled.toString(),
                    ),
                ),
        ) {
            "友盟 SDK 预初始化完成。"
        }
    }

    private fun canCollect(): Boolean =
        requestedEnabled &&
            consentStatus == AnalyticsConsentStatus.GRANTED &&
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
            initializationPlugins: List<UmengInitializationPlugin> = emptyList(),
        ): UmengAnalyticsProvider =
            UmengAnalyticsProvider(
                configuration = configuration,
                sdk = AndroidUmengSdk(context),
                initializationPlugins = initializationPlugins,
            )

        private const val MAX_USER_PROPERTIES = 20
        private const val MAX_USER_ID_BYTES = 63
    }

    private fun String.isValidUmengUserId(): Boolean =
        isNotBlank() && toByteArray(Charsets.UTF_8).size <= MAX_USER_ID_BYTES
}

internal interface UmengSdk {
    fun setDebugLogging(enabled: Boolean)

    fun setManualPageCollection()

    fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration)

    fun preInitialize(appKey: String, channel: String)

    fun initialize(appKey: String, channel: String)

    fun submitPrivacyConsent(granted: Boolean)

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

    override fun setDebugLogging(enabled: Boolean) {
        UMConfigure.setLogEnabled(enabled)
    }

    override fun setManualPageCollection() {
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)
    }

    @Suppress("DEPRECATION")
    override fun applyCollectionConfiguration(configuration: UmengCollectionConfiguration) {
        UMConfigure.enableImeiCollection(configuration.collectImei)
        UMConfigure.enableImsiCollection(configuration.collectImsi)
        UMConfigure.enableIccidCollection(configuration.collectIccid)
        UMConfigure.enableWiFiMacCollection(configuration.collectWifiMac)
        UMConfigure.enableAplCollection(configuration.collectInstalledApps)

        UYMManager.enableYm1(applicationContext, configuration.collectPreciseLocation)
        UYMManager.enableYm2(applicationContext, configuration.collectCachedLocation)
        UYMManager.enableYm3(applicationContext, configuration.collectConnectedWifi)
        UYMManager.enableYm4(applicationContext, configuration.collectWifiList)
        UYMManager.enableYm5(applicationContext, configuration.collectCellTower)
        UYMManager.enableYm6(applicationContext, configuration.collectInstalledApps)
    }

    override fun preInitialize(appKey: String, channel: String) {
        UMConfigure.preInit(applicationContext, appKey, channel)
    }

    override fun initialize(appKey: String, channel: String) {
        UMConfigure.init(applicationContext, appKey, channel, UMConfigure.DEVICE_TYPE_PHONE, "")
    }

    override fun submitPrivacyConsent(granted: Boolean) {
        UMConfigure.submitPolicyGrantResult(applicationContext, granted)
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
