package com.tospery.core.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 管理应用可处理链接时可进入的系统设置页面。 */
enum class SupportedLinksSettingsDestination {
    OPEN_BY_DEFAULT,
    APPLICATION_DETAILS,
}

/**
 * 以系统标准入口管理本应用可处理的链接。
 *
 * Android 12 起优先进入“默认打开”页；较低系统版本或厂商未提供该页面时，回退到同一应用的详情页。
 * 不尝试厂商私有 Intent，避免把设备差异固化进产品行为。
 */
class SupportedLinksSettingsLauncher(
    private val sdkInt: Int,
    private val launchDestination: (SupportedLinksSettingsDestination) -> Boolean,
) {
    fun open(): Boolean {
        val destinations =
            if (sdkInt >= Build.VERSION_CODES.S) {
                listOf(
                    SupportedLinksSettingsDestination.OPEN_BY_DEFAULT,
                    SupportedLinksSettingsDestination.APPLICATION_DETAILS,
                )
            } else {
                listOf(SupportedLinksSettingsDestination.APPLICATION_DETAILS)
            }
        return destinations.any(::safeLaunch)
    }

    private fun safeLaunch(destination: SupportedLinksSettingsDestination): Boolean =
        try {
            launchDestination(destination)
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    companion object {
        fun create(context: Context): SupportedLinksSettingsLauncher {
            val applicationContext = context.applicationContext
            val packageUri = Uri.fromParts("package", applicationContext.packageName, null)
            return SupportedLinksSettingsLauncher(
                sdkInt = Build.VERSION.SDK_INT,
            ) { destination ->
                val action =
                    when (destination) {
                        SupportedLinksSettingsDestination.OPEN_BY_DEFAULT ->
                            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS

                        SupportedLinksSettingsDestination.APPLICATION_DETAILS ->
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                applicationContext.startActivity(
                    Intent(action, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }
        }
    }
}
