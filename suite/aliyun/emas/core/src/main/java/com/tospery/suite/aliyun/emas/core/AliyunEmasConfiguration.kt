package com.tospery.suite.aliyun.emas.core

import com.tospery.base.logging.LogLevel

/** 阿里云 EMAS 移动监控中可独立启用的产品组件。 */
enum class AliyunEmasComponent {
    CRASH_ANALYSIS,
    PERFORMANCE_ANALYSIS,
    MEMORY_ANALYSIS,
    REMOTE_LOG,
}

/**
 * EMAS 移动监控可选隐私字段。
 *
 * 这些开关只控制 SDK 2.9.0 公开支持的四类字段；隐私授权仍由
 * [com.tospery.base.sdk.ConsentAwareSdkRuntime] 单独控制。
 */
data class AliyunEmasCollectionConfiguration(
    val collectDeviceModel: Boolean = true,
    val collectOsVersion: Boolean = true,
    val collectScreenResolution: Boolean = true,
    val collectNetworkInfo: Boolean = true,
)

/** 关联 EMAS 监控数据的非敏感用户标识。 */
data class AliyunEmasUserInfo(
    val id: String? = null,
    val nickname: String? = null,
) {
    init {
        require(id != null || nickname != null) {
            "阿里云 EMAS 用户 ID 和昵称不能同时为空。"
        }
        id?.let { value ->
            require(value.isNotBlank() && value.length <= MAX_USER_FIELD_LENGTH) {
                "阿里云 EMAS 用户 ID 必须是1至128个字符。"
            }
        }
        nickname?.let { value ->
            require(value.isNotBlank() && value.length <= MAX_USER_FIELD_LENGTH) {
                "阿里云 EMAS 用户昵称必须是1至128个字符。"
            }
        }
    }

    private companion object {
        const val MAX_USER_FIELD_LENGTH = 128
    }
}

/** 阿里云 EMAS 移动监控公共配置。 */
data class AliyunEmasConfiguration(
    val appKey: String,
    val appSecret: String,
    val appRsaSecret: String,
    val channel: String? = null,
    val initialUserInfo: AliyunEmasUserInfo? = null,
    val components: Set<AliyunEmasComponent> = AliyunEmasComponent.entries.toSet(),
    val collection: AliyunEmasCollectionConfiguration =
        AliyunEmasCollectionConfiguration(),
    val debugLoggingEnabled: Boolean = false,
    val remoteLogLevel: LogLevel = LogLevel.INFO,
    val remoteLogModuleName: String = DEFAULT_REMOTE_LOG_MODULE_NAME,
    val remoteLogCacheSizeMegabytes: Int = DEFAULT_REMOTE_LOG_CACHE_SIZE_MEGABYTES,
) {
    init {
        require(appKey.isNotBlank()) {
            "阿里云 EMAS AppKey 不能为空。"
        }
        require(appSecret.isNotBlank()) {
            "阿里云 EMAS AppSecret 不能为空。"
        }
        require(appRsaSecret.isNotBlank()) {
            "阿里云 EMAS App RSA Secret 不能为空。"
        }
        channel?.let { value ->
            require(value.isNotBlank() && value.length <= MAX_CHANNEL_LENGTH) {
                "阿里云 EMAS 渠道必须是1至128个字符。"
            }
        }
        require(components.isNotEmpty()) {
            "阿里云 EMAS 至少需要启用一个移动监控组件。"
        }
        require(remoteLogModuleName.isNotBlank()) {
            "阿里云 EMAS 远程日志模块名不能为空。"
        }
        require(
            remoteLogCacheSizeMegabytes in
                MIN_REMOTE_LOG_CACHE_SIZE_MEGABYTES..MAX_REMOTE_LOG_CACHE_SIZE_MEGABYTES,
        ) {
            "阿里云 EMAS 远程日志缓存必须在1至100 MB之间。"
        }
    }

    internal fun hasComponent(component: AliyunEmasComponent): Boolean =
        component in components

    private companion object {
        const val MAX_CHANNEL_LENGTH = 128
        const val DEFAULT_REMOTE_LOG_MODULE_NAME = "app"
        const val DEFAULT_REMOTE_LOG_CACHE_SIZE_MEGABYTES = 20
        const val MIN_REMOTE_LOG_CACHE_SIZE_MEGABYTES = 1
        const val MAX_REMOTE_LOG_CACHE_SIZE_MEGABYTES = 100
    }
}
