package com.tospery.base.analytics

import com.tospery.base.sdk.PrivacyConsentController
import com.tospery.base.sdk.PrivacyConsentStatus

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

/** 统计服务的隐私授权边界。 */
interface AnalyticsPrivacyController : PrivacyConsentController

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

    override fun enterScreen(screen: AnalyticsScreen) = Unit

    override fun exitScreen(screen: AnalyticsScreen) = Unit

    override fun clearUser() = Unit

    override fun flush() = Unit

    override fun reset() = Unit

    override fun preInitialize() = Unit

    override fun initialize() = Unit

    override fun savePendingDataOnExit() = Unit

    override fun updatePrivacyConsent(status: PrivacyConsentStatus) = Unit
}
