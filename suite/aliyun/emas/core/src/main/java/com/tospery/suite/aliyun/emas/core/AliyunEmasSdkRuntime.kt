package com.tospery.suite.aliyun.emas.core

import android.app.Application
import android.content.Context
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.error
import com.tospery.base.logging.info
import com.tospery.base.logging.warning
import com.tospery.base.sdk.ConsentAwareSdkRuntime
import com.tospery.base.sdk.PrivacyConsentStatus
import com.tospery.buildmetadata.module_suite_aliyun_emas_core.ModuleMetadata

private val aliyunEmasLifecycleLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "lifecycle",
    )

/**
 * 阿里云 EMAS 移动监控在单个进程内的生命周期所有者。
 *
 * [preInitialize] 只执行官方允许在隐私授权前完成的 `Apm.preStart` 配置；
 * 数据采集和主动上报必须等到隐私授权为 [PrivacyConsentStatus.GRANTED]。
 */
class AliyunEmasSdkRuntime internal constructor(
    private val configuration: AliyunEmasConfiguration,
    private val sdk: AliyunEmasSdk,
) : ConsentAwareSdkRuntime {
    private var consentStatus = PrivacyConsentStatus.UNKNOWN
    private var preInitialized = false
    private var initialized = false
    private var grantedCollectionConfigurationApplied = false
    private var currentUserInfo = configuration.initialUserInfo

    val performanceMonitor: AliyunEmasPerformanceMonitor =
        AliyunEmasPerformanceMonitor(
            configuration = configuration,
            canCollect = ::canCollect,
            sdk = sdk,
        )

    @Synchronized
    override fun isInitialized(): Boolean = initialized

    @Synchronized
    override fun preInitialize() {
        if (preInitialized) {
            return
        }

        val succeeded =
            callSdk(operation = "pre_initialize") {
                sdk.preStart(configuration)
            }
        if (!succeeded) {
            return
        }

        preInitialized = true
        when (consentStatus) {
            PrivacyConsentStatus.UNKNOWN -> Unit
            PrivacyConsentStatus.GRANTED -> applyGrantedCollectionConfiguration()
            PrivacyConsentStatus.DENIED -> disablePrivacyCollection()
        }

        info(
            tag = aliyunEmasLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "component_count",
                        value = configuration.components.size.toString(),
                    ),
                    LogAttribute(
                        key = "debug_logging_enabled",
                        value = configuration.debugLoggingEnabled.toString(),
                    ),
                    LogAttribute(
                        key = "consent_status",
                        value = consentStatus.name,
                    ),
                ),
        ) {
            "阿里云 EMAS 移动监控预配置完成。"
        }
    }

    @Synchronized
    override fun initialize() {
        if (initialized || consentStatus != PrivacyConsentStatus.GRANTED) {
            return
        }

        if (!preInitialized) {
            preInitialize()
        }
        if (!preInitialized) {
            return
        }

        applyGrantedCollectionConfiguration()
        val startResult =
            callSdkForResult(operation = "initialize") {
                sdk.start()
            } ?: return
        if (!startResult) {
            warning(tag = aliyunEmasLifecycleLogTag) {
                "阿里云 EMAS 移动监控初始化返回失败。"
            }
            return
        }

        initialized = true
        applyRemoteLogLevel()
        applyCurrentUserInfo()
        info(
            tag = aliyunEmasLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "component_count",
                        value = configuration.components.size.toString(),
                    ),
                ),
        ) {
            "阿里云 EMAS 移动监控正式初始化完成。"
        }
    }

    @Synchronized
    override fun updatePrivacyConsent(status: PrivacyConsentStatus) {
        if (status == PrivacyConsentStatus.UNKNOWN || consentStatus == status) {
            return
        }

        consentStatus = status
        when (status) {
            PrivacyConsentStatus.UNKNOWN -> error("UNKNOWN status is handled above.")
            PrivacyConsentStatus.GRANTED -> {
                if (preInitialized) {
                    applyGrantedCollectionConfiguration()
                }
                if (initialized) {
                    applyCurrentUserInfo()
                }
            }

            PrivacyConsentStatus.DENIED -> {
                if (preInitialized) {
                    disablePrivacyCollection()
                }
                info(
                    tag = aliyunEmasLifecycleLogTag,
                    attributes =
                        listOf(
                            LogAttribute(
                                key = "was_initialized",
                                value = initialized.toString(),
                            ),
                        ),
                ) {
                    "阿里云 EMAS 移动监控已处理隐私授权拒绝。"
                }
            }
        }
    }

    /** 更新运行时用户信息；传入 null 用于退出登录后清空关联信息。 */
    @Synchronized
    fun updateUserInfo(userInfo: AliyunEmasUserInfo?) {
        currentUserInfo = userInfo
        if (!canCollect()) {
            return
        }

        applyCurrentUserInfo()
    }

    @Synchronized
    internal fun canCollect(): Boolean =
        initialized && consentStatus == PrivacyConsentStatus.GRANTED

    private fun applyGrantedCollectionConfiguration() {
        if (grantedCollectionConfigurationApplied) {
            return
        }

        val succeeded = callSdk(operation = "apply_collection_configuration") {
            sdk.applyCollectionConfiguration(configuration.collection)
        }
        if (succeeded) {
            grantedCollectionConfigurationApplied = true
        }
    }

    private fun disablePrivacyCollection() {
        val succeeded = callSdk(operation = "disable_privacy_collection") {
            sdk.disablePrivacyCollection()
        }
        if (succeeded) {
            grantedCollectionConfigurationApplied = false
        }
    }

    private fun applyRemoteLogLevel() {
        if (!configuration.hasComponent(AliyunEmasComponent.REMOTE_LOG)) {
            return
        }

        callSdk(operation = "update_remote_log_level") {
            sdk.updateRemoteLogLevel(configuration.remoteLogLevel)
        }
    }

    private fun applyCurrentUserInfo() {
        val userInfo = currentUserInfo
        val succeeded =
            callSdk(operation = "set_user_info") {
                sdk.setUserInfo(userInfo)
            }
        if (!succeeded) {
            return
        }

        info(
            tag = aliyunEmasLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(
                        key = "has_user_id",
                        value = (userInfo?.id != null).toString(),
                    ),
                    LogAttribute(
                        key = "has_user_nickname",
                        value = (userInfo?.nickname != null).toString(),
                    ),
                ),
        ) {
            "阿里云 EMAS 移动监控用户信息已更新。"
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
            logSdkFailure(operation = operation, throwable = throwable)
            false
        }

    private inline fun <T> callSdkForResult(
        operation: String,
        action: () -> T,
    ): T? =
        try {
            action()
        } catch (throwable: Throwable) {
            logSdkFailure(operation = operation, throwable = throwable)
            null
        }

    private fun logSdkFailure(
        operation: String,
        throwable: Throwable,
    ) {
        error(
            tag = aliyunEmasLifecycleLogTag,
            attributes =
                listOf(
                    LogAttribute(key = "operation", value = operation),
                    LogAttribute(
                        key = "failure_type",
                        value = throwable.javaClass.name,
                    ),
                ),
        ) {
            "阿里云 EMAS 移动监控 SDK 调用失败。"
        }
    }

    companion object {
        fun create(
            context: Context,
            configuration: AliyunEmasConfiguration,
        ): AliyunEmasSdkRuntime {
            val application =
                context.applicationContext as? Application
                    ?: context as? Application
                    ?: error("阿里云 EMAS 移动监控需要 Application Context。")
            return AliyunEmasSdkRuntime(
                configuration = configuration,
                sdk = AndroidAliyunEmasSdk(application),
            )
        }
    }
}
