package com.tospery.base.sdk

/**
 * 将隐私授权和初始化操作按注册顺序分发给多个第三方 SDK 运行时。
 *
 * 输入列表会复制为不可变快照。只要至少一个子运行时初始化成功，
 * App 级组合运行时就视为已初始化，以免单个厂商失败阻断其他已就绪能力。
 *
 * 具体厂商运行时负责隔离 SDK 异常并记录带厂商上下文的日志；
 * 组合器只负责稳定、有序地转发生命周期信号。
 */
class CompositeConsentAwareSdkRuntime(
    runtimes: List<ConsentAwareSdkRuntime>,
) : ConsentAwareSdkRuntime {
    private val runtimes = runtimes.toList()

    override fun isInitialized(): Boolean =
        runtimes.any(ConsentAwareSdkRuntime::isInitialized)

    override fun preInitialize() {
        runtimes.forEach(ConsentAwareSdkRuntime::preInitialize)
    }

    override fun initialize() {
        runtimes.forEach(ConsentAwareSdkRuntime::initialize)
    }

    override fun updatePrivacyConsent(status: PrivacyConsentStatus) {
        runtimes.forEach { runtime ->
            runtime.updatePrivacyConsent(status)
        }
    }
}
