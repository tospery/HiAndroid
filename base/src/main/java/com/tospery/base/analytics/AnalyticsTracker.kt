package com.tospery.base.analytics

typealias AnalyticsProperties = Map<String, AnalyticsValue>

sealed interface AnalyticsValue {
    data class Text(val value: String) : AnalyticsValue

    data class IntegerNumber(val value: Long) : AnalyticsValue

    data class DecimalNumber(val value: Double) : AnalyticsValue

    data class BooleanValue(val value: Boolean) : AnalyticsValue

    data object Null : AnalyticsValue
}

data class AnalyticsEvent(
    val name: String,
    val properties: AnalyticsProperties = emptyMap(),
)

data class AnalyticsUser(
    val id: String,
    val properties: AnalyticsProperties = emptyMap(),
)

data class AnalyticsScreen(
    val name: String,
    val className: String? = null,
    val properties: AnalyticsProperties = emptyMap(),
)

interface AnalyticsTracker {
    fun isEnabled(): Boolean = true

    fun setEnabled(enabled: Boolean)

    fun track(event: AnalyticsEvent)

    fun identify(user: AnalyticsUser)

    fun setUserProperties(properties: AnalyticsProperties)

    /**
     * 记录页面进入。调用方应使用相同的稳定页面名配对调用 [exitScreen]。
     */
    fun enterScreen(screen: AnalyticsScreen)

    /**
     * 记录页面退出，用于完成页面停留时长统计。
     */
    fun exitScreen(screen: AnalyticsScreen)

    fun clearUser()

    fun flush()

    fun reset()
}

object NoOpAnalyticsTracker : AnalyticsTracker {
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
}
