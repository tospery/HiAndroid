package com.tospery.base.analytics

/**
 * 将统计操作分发到多个厂商 Provider。
 *
 * 事件、账号和页面进入信号沿用 [CompositeAnalyticsTracker] 的启用状态过滤规则；
 * 页面退出、生命周期、隐私状态、清理和持久化操作始终发送给所有 Provider，
 * 确保被禁用的 Provider 也能收到页面闭合、拒绝授权、清理账号等安全指令。
 */
class CompositeAnalyticsProvider(
    providers: List<AnalyticsProvider>,
) : AnalyticsProvider {
    private val providers: List<AnalyticsProvider> = providers.toList()
    private val tracker: AnalyticsTracker = CompositeAnalyticsTracker(this.providers)

    override fun isEnabled(): Boolean = tracker.isEnabled()

    override fun setEnabled(enabled: Boolean) {
        tracker.setEnabled(enabled)
    }

    override fun track(event: AnalyticsEvent) {
        tracker.track(event)
    }

    override fun identify(user: AnalyticsUser) {
        tracker.identify(user)
    }

    override fun setUserProperties(properties: AnalyticsProperties) {
        tracker.setUserProperties(properties)
    }

    override fun enterScreen(screen: AnalyticsScreen) {
        tracker.enterScreen(screen)
    }

    override fun exitScreen(screen: AnalyticsScreen) {
        tracker.exitScreen(screen)
    }

    override fun clearUser() {
        tracker.clearUser()
    }

    override fun flush() {
        tracker.flush()
    }

    override fun reset() {
        tracker.reset()
    }

    override fun preInitialize() {
        providers.forEach(AnalyticsLifecycle::preInitialize)
    }

    override fun initialize() {
        providers.forEach(AnalyticsLifecycle::initialize)
    }

    override fun savePendingDataOnExit() {
        providers.forEach(AnalyticsLifecycle::savePendingDataOnExit)
    }

    override fun updatePrivacyConsent(status: AnalyticsConsentStatus) {
        providers.forEach { provider ->
            provider.updatePrivacyConsent(status)
        }
    }
}
