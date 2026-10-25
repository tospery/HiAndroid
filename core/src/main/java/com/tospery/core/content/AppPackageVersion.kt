package com.tospery.core.content

import android.content.Context

/** 当前安装包的版本信息；[name] 可能因构建未配置 `versionName` 而为空。 */
data class AppPackageVersion(
    val name: String?,
    val code: Long,
)

/** 读取当前应用的安装包版本；系统查询失败时返回 null。 */
fun Context.appPackageVersionOrNull(): AppPackageVersion? =
    runCatching {
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        AppPackageVersion(
            name = packageInfo.versionName?.takeIf(String::isNotBlank),
            code = packageInfo.longVersionCode,
        )
    }.getOrNull()
