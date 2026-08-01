package com.tospery.base.analytics

/**
 * App 当前掌握的统计隐私授权状态。
 *
 * UNKNOWN 表示用户尚未作出选择。厂商适配器收到该状态时不得正式初始化、
 * 上传授权结果或开始采集数据。
 */
enum class AnalyticsConsentStatus {
    UNKNOWN,
    GRANTED,
    DENIED,
}

/**
 * 统计服务的应用生命周期能力。
 *
 * 具体厂商所需的 Context、AppKey 和渠道等配置应在适配器构造时注入，
 * 不得泄漏到该纯 Kotlin 抽象中。
 */
interface AnalyticsLifecycle {
    /**
     * 执行不采集设备信息、不上报数据的预初始化。
     *
     * 实现应保证重复调用安全。
     */
    fun preInitialize()

    /**
     * 在用户授权后执行正式初始化。
     *
     * 实现应保证重复调用安全，并在授权状态未知或拒绝时拒绝开始采集。
     */
    fun initialize()

    /**
     * 在程序退出前保存尚未持久化的统计数据。
     */
    fun savePendingDataOnExit()
}

/**
 * 厂商无关的隐私授权状态入口。
 */
interface AnalyticsPrivacyController {
    /**
     * 更新当前隐私授权状态。
     *
     * 具体适配器只应在 GRANTED 或 DENIED 时调用厂商的授权结果上传接口；
     * UNKNOWN 不得转换成任何厂商授权结果。
     */
    fun updatePrivacyConsent(status: AnalyticsConsentStatus)
}

/**
 * 一个完整的统计服务提供者。
 *
 * 事件、账号、页面及采集开关复用 [AnalyticsTracker]；
 * 初始化、退出保存及隐私授权通过独立接口表达。
 */
interface AnalyticsProvider :
    AnalyticsTracker,
    AnalyticsLifecycle,
    AnalyticsPrivacyController

/**
 * 不执行任何操作的统计 Provider。
 */
object NoOpAnalyticsProvider : AnalyticsProvider {
    override fun isEnabled(): Boolean = false

    override fun setEnabled(enabled: Boolean) = Unit

    override fun track(event: AnalyticsEvent) = Unit

    override fun identify(user: AnalyticsUser) = Unit

    override fun setUserProperties(properties: AnalyticsProperties) = Unit

    override fun trackScreen(screen: AnalyticsScreen) = Unit

    override fun clearUser() = Unit

    override fun flush() = Unit

    override fun reset() = Unit

    override fun preInitialize() = Unit

    override fun initialize() = Unit

    override fun savePendingDataOnExit() = Unit

    override fun updatePrivacyConsent(status: AnalyticsConsentStatus) = Unit
}
