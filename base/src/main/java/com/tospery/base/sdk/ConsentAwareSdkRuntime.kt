package com.tospery.base.sdk

/**
 * App 当前掌握的第三方 SDK 隐私授权状态。
 *
 * 此状态只表示 App 隐私政策授权，不代表通知、定位等 Android 运行时权限。
 */
enum class PrivacyConsentStatus {
    UNKNOWN,
    GRANTED,
    DENIED,
}

/**
 * 第三方 SDK 隐私授权状态入口。
 */
fun interface PrivacyConsentController {
    /**
     * 更新隐私授权状态。
     *
     * UNKNOWN 表示用户尚未选择，不得转换为厂商的同意或拒绝结果。
     */
    fun updatePrivacyConsent(status: PrivacyConsentStatus)
}

/**
 * 第三方 SDK 的只读初始化状态。
 *
 * 分享、深度链接、推送等功能 Adapter 可以只依赖此接口，
 * 避免获得初始化和隐私授权控制能力。
 */
fun interface SdkInitializationState {
    fun isInitialized(): Boolean
}

/**
 * 需要隐私授权才能正式启动的第三方 SDK 公共生命周期。
 *
 * 同一厂商的统计、性能、分享、推送等组件如果共享公共 SDK，
 * 应由一个 Runtime 统一实现该生命周期。
 */
interface ConsentAwareSdkRuntime :
    PrivacyConsentController,
    SdkInitializationState {

    /**
     * 执行不会采集或上报数据的预配置。
     *
     * 只有厂商明确保证合规时才允许在隐私授权前调用。
     * 实现必须保证重复调用安全。
     */
    fun preInitialize()

    /**
     * 正式初始化 SDK。
     *
     * 仅当隐私授权为 GRANTED 时才能开始采集或上传数据。
     * 实现必须保证重复调用安全。
     */
    fun initialize()
}

/**
 * 未接入具体 SDK 时使用的安全空实现。
 */
object NoOpConsentAwareSdkRuntime : ConsentAwareSdkRuntime {
    override fun isInitialized(): Boolean = false

    override fun preInitialize() = Unit

    override fun initialize() = Unit

    override fun updatePrivacyConsent(status: PrivacyConsentStatus) = Unit
}
