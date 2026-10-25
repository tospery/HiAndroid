package com.tospery.core.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * 打开 Google Play 商店详情页。
 *
 * 先用 `market://` 协议交给 Google Play App，失败后回退到同一个 HTTPS 商店页面。
 * 只接受 `https://play.google.com/store/apps/details?id=...` 形式的地址，其余输入直接返回 false，
 * 避免把任意外部链接当作商店入口打开。
 */
class GooglePlayStoreLauncher(
    private val launchUri: (String) -> Boolean,
) {
    fun open(storeUrl: String): Boolean {
        val appId = storeUrl.playAppIdOrNull() ?: return false
        return safeLaunch("market://details?id=$appId") || safeLaunch(storeUrl)
    }

    private fun safeLaunch(uri: String): Boolean =
        try {
            launchUri(uri)
        } catch (_: Throwable) {
            false
        }

    companion object {
        fun create(context: Context): GooglePlayStoreLauncher {
            val applicationContext = context.applicationContext
            return GooglePlayStoreLauncher { rawUri ->
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(rawUri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (rawUri.startsWith("market://")) {
                    // market 协议仅交给 Google Play，避免第三方商店截获更新入口。
                    intent.setPackage(GOOGLE_PLAY_PACKAGE)
                }
                applicationContext.startActivity(intent)
                true
            }
        }

        private const val GOOGLE_PLAY_PACKAGE = "com.android.vending"
    }
}

private fun String.playAppIdOrNull(): String? {
    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    if (
        !uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("play.google.com", ignoreCase = true) ||
        uri.port != -1 ||
        uri.rawUserInfo != null ||
        uri.rawPath != "/store/apps/details" ||
        uri.rawFragment != null
    ) {
        return null
    }
    return uri.rawQuery
        .orEmpty()
        .split('&')
        .firstNotNullOfOrNull { parameter ->
            parameter.substringAfter("id=", missingDelimiterValue = "")
                .takeIf { parameter.substringBefore('=') == "id" }
                ?.takeIf(PLAY_APP_ID::matches)
        }
}

private val PLAY_APP_ID = Regex("[A-Za-z0-9._]+")
