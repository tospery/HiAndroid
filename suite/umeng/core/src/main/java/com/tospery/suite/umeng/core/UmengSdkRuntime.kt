package com.tospery.suite.umeng.core

import android.content.Context
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.error
import com.tospery.base.logging.info
import com.tospery.buildmetadata.module_suite_umeng_core.ModuleMetadata
import com.umeng.commonsdk.UMConfigure

/** 友盟公共组件可以采集的设备字段，默认全部关闭。 */
data class UmengCommonCollectionConfiguration(
    val collectImei: Boolean = false,
    val collectImsi: Boolean = false,
    val collectIccid: Boolean = false,
    val collectWifiMac: Boolean = false,
    val collectInstalledApps: Boolean = false,
)

/** 所有友盟产品共享的公共 SDK 配置。 */
data class UmengSdkConfiguration(
    val appKey: String,
    val channel: String,
    val collection: UmengCommonCollectionConfiguration =
        UmengCommonCollectionConfiguration(),
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

enum class UmengPrivacyConsentStatus {
    UNKNOWN,
    GRANTED,
    DENIED,
}

/** 只读的友盟公共 SDK 运行状态，供具体产品 Adapter 阻止未授权调用。 */
fun interface UmengSdkState {
    fun isInitialized(): Boolean
}

/**
 * 接入同一套友盟公共 SDK 生命周期的可选产品组件。
 *
 * 配置必须幂等、快速且不抛出异常；公共运行时仍会隔离异常，避免单个产品阻塞其他产品。
 */
interface UmengInitializationPlugin {
    fun configureBeforeInitialization()

    fun onInitialized() = Unit

    fun onPrivacyConsentDenied() = Unit
}

/** 友盟公共 SDK 生命周期契约，具体产品不能直接调用 UMConfigure 初始化 API。 */
interface UmengSdkLifecycle : UmengSdkState {
    fun registerInitializationPlugin(plugin: UmengInitializationPlugin)

    fun preInitialize()

    fun initialize()

    fun updatePrivacyConsent(status: UmengPrivacyConsentStatus)
}

private val umengSdkLifecycleLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "lifecycle",
    )

/**
 * 友盟公共 SDK 在单个进程内的唯一生命周期所有者。
 *
 * App 无论选择移动分析、U-APM、U-Share 或 U-Link 中的哪些产品，都只创建一个实例。
 */
class UmengSdkRuntime internal constructor(
    private val configuration: UmengSdkConfiguration,
    private val sdk: UmengCommonSdk,
) : UmengSdkLifecycle {
    private val initializationPlugins = mutableListOf<UmengInitializationPlugin>()
    private var consentStatus = UmengPrivacyConsentStatus.UNKNOWN
    private var preInitializationRequested = false
    private var pluginsConfigured = false
    private var preInitialized = false
    private var initialized = false
    private var permanentlyDisabled = false

    @Synchronized
    override fun isInitialized(): Boolean = initialized && !permanentlyDisabled

    @Synchronized
    override fun registerInitializationPlugin(plugin: UmengInitializationPlugin) {
        check(!preInitialized && !initialized && consentStatus == UmengPrivacyConsentStatus.UNKNOWN) {
            "友盟产品插件必须在公共 SDK 预初始化和隐私授权提交前注册。"
        }
        if (initializationPlugins.none { registered -> registered === plugin }) {
            initializationPlugins += plugin
        }
    }

    @Synchronized
    override fun preInitialize() {
        if (preInitializationRequested || preInitialized || permanentlyDisabled) {
            return
        }

        preInitializationRequested = true
        if (consentStatus != UmengPrivacyConsentStatus.GRANTED) {
            info(
                tag = umengSdkLifecycleLogTag,
                attributes =
                    listOf(
                        LogAttribute("consent_status", consentStatus.name),
                    ),
            ) {
                "友盟公共 SDK 预初始化等待隐私授权。"
            }
            return
        }

        performPreInitialization()
    }

    @Synchronized
    override fun initialize() {
        if (
            initialized ||
            permanentlyDisabled ||
            consentStatus != UmengPrivacyConsentStatus.GRANTED
        ) {
            return
        }

        if (!preInitialized) {
            preInitialize()
        }
        if (!preInitialized) {
            return
        }

        val succeeded =
            callSdk(operation = "initialize") {
                sdk.initialize(
                    appKey = configuration.appKey,
                    channel = configuration.channel,
                )
            }
        if (!succeeded) {
            return
        }

        initialized = true
        initializationPlugins.forEachSafely(
            operation = "plugin_initialized",
            UmengInitializationPlugin::onInitialized,
        )
        info(
            tag = umengSdkLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute("plugin_count", initializationPlugins.size.toString()),
                    LogAttribute(
                        "debug_logging_enabled",
                        configuration.debugLoggingEnabled.toString(),
                    ),
                ),
        ) {
            "友盟公共 SDK 正式初始化完成。"
        }
    }

    @Synchronized
    override fun updatePrivacyConsent(status: UmengPrivacyConsentStatus) {
        if (
            status == UmengPrivacyConsentStatus.UNKNOWN ||
            consentStatus == status ||
            permanentlyDisabled
        ) {
            return
        }

        consentStatus = status
        when (status) {
            UmengPrivacyConsentStatus.UNKNOWN -> error("UNKNOWN status is handled above.")
            UmengPrivacyConsentStatus.GRANTED -> {
                if (preInitializationRequested) {
                    performPreInitialization()
                }
                callSdk(operation = "submit_consent_granted") {
                    sdk.submitPrivacyConsent(granted = true)
                }
            }

            UmengPrivacyConsentStatus.DENIED -> {
                val wasInitialized = initialized
                initializationPlugins.forEachSafely(
                    operation = "plugin_consent_denied",
                    UmengInitializationPlugin::onPrivacyConsentDenied,
                )
                callSdk(operation = "submit_consent_denied") {
                    sdk.submitPrivacyConsent(granted = false)
                }
                if (wasInitialized) {
                    initialized = false
                    permanentlyDisabled = true
                }

                info(
                    tag = umengSdkLifecycleLogTag,
                    attributes =
                        listOf(
                            LogAttribute("was_initialized", wasInitialized.toString()),
                            LogAttribute(
                                "plugin_count",
                                initializationPlugins.size.toString(),
                            ),
                        ),
                ) {
                    "友盟公共 SDK 已处理隐私授权拒绝。"
                }
            }
        }
    }

    private fun performPreInitialization() {
        if (
            preInitialized ||
            permanentlyDisabled ||
            consentStatus != UmengPrivacyConsentStatus.GRANTED
        ) {
            return
        }

        if (!pluginsConfigured) {
            initializationPlugins.forEachSafely(
                operation = "plugin_configure",
                UmengInitializationPlugin::configureBeforeInitialization,
            )
            pluginsConfigured = true
        }

        val succeeded =
            callSdk(operation = "pre_initialize") {
                sdk.setDebugLogging(configuration.debugLoggingEnabled)
                sdk.applyCollectionConfiguration(configuration.collection)
                sdk.preInitialize(
                    appKey = configuration.appKey,
                    channel = configuration.channel,
                )
            }
        if (!succeeded) {
            return
        }

        preInitialized = true
        info(
            tag = umengSdkLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        "debug_logging_enabled",
                        configuration.debugLoggingEnabled.toString(),
                    ),
                    LogAttribute("plugin_count", initializationPlugins.size.toString()),
                ),
        ) {
            "友盟公共 SDK 预初始化完成。"
        }
    }

    private inline fun callSdk(
        operation: String,
        action: () -> Unit,
    ): Boolean =
        try {
            action()
            true
        } catch (throwable: Throwable) {
            error(
                tag = umengSdkLifecycleLogTag,
                throwable = throwable,
                attributes = listOf(LogAttribute("operation", operation)),
            ) {
                "友盟公共 SDK 调用失败。"
            }
            false
        }

    private inline fun List<UmengInitializationPlugin>.forEachSafely(
        operation: String,
        action: (UmengInitializationPlugin) -> Unit,
    ) {
        forEachIndexed { index, plugin ->
            try {
                action(plugin)
            } catch (throwable: Throwable) {
                error(
                    tag = umengSdkLifecycleLogTag,
                    throwable = throwable,
                    attributes =
                        listOf(
                            LogAttribute("operation", operation),
                            LogAttribute("plugin_index", index.toString()),
                        ),
                ) {
                    "友盟产品插件生命周期调用失败。"
                }
            }
        }
    }

    companion object {
        fun create(
            context: Context,
            configuration: UmengSdkConfiguration,
        ): UmengSdkRuntime =
            UmengSdkRuntime(
                configuration = configuration,
                sdk = AndroidUmengCommonSdk(context),
            )
    }
}

internal interface UmengCommonSdk {
    fun setDebugLogging(enabled: Boolean)

    fun applyCollectionConfiguration(configuration: UmengCommonCollectionConfiguration)

    fun preInitialize(appKey: String, channel: String)

    fun initialize(appKey: String, channel: String)

    fun submitPrivacyConsent(granted: Boolean)
}

private class AndroidUmengCommonSdk(
    context: Context,
) : UmengCommonSdk {
    private val applicationContext = context.applicationContext ?: context

    override fun setDebugLogging(enabled: Boolean) {
        UMConfigure.setLogEnabled(enabled)
    }

    @Suppress("DEPRECATION")
    override fun applyCollectionConfiguration(
        configuration: UmengCommonCollectionConfiguration,
    ) {
        UMConfigure.enableImeiCollection(configuration.collectImei)
        UMConfigure.enableImsiCollection(configuration.collectImsi)
        UMConfigure.enableIccidCollection(configuration.collectIccid)
        UMConfigure.enableWiFiMacCollection(configuration.collectWifiMac)
        UMConfigure.enableAplCollection(configuration.collectInstalledApps)
    }

    override fun preInitialize(appKey: String, channel: String) {
        UMConfigure.preInit(applicationContext, appKey, channel)
    }

    override fun initialize(appKey: String, channel: String) {
        UMConfigure.init(
            applicationContext,
            appKey,
            channel,
            UMConfigure.DEVICE_TYPE_PHONE,
            "",
        )
    }

    override fun submitPrivacyConsent(granted: Boolean) {
        UMConfigure.submitPolicyGrantResult(applicationContext, granted)
    }
}
