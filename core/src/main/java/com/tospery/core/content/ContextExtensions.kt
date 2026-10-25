package com.tospery.core.content

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * 沿 [ContextWrapper] 链查找宿主 Activity。
 *
 * Compose `LocalContext`、Hilt 或主题包装都可能把 Activity 包在多层 wrapper 中；找不到时返回 null，
 * 调用方据此降级，而不是强制转换。
 */
tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * 把纯文本写入系统剪贴板。
 *
 * [label] 是系统剪贴板展示给用户的描述，应传入本地化文案。设备没有剪贴板服务时返回 false。
 */
fun Context.copyPlainTextToClipboard(
    label: CharSequence,
    text: CharSequence,
): Boolean {
    val clipboardManager = getSystemService(ClipboardManager::class.java) ?: return false
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}

/**
 * 启动 Activity，并把“没有可处理的应用”和“目标拒绝访问”转换为 false。
 *
 * 非 Activity Context 启动时系统要求 [Intent.FLAG_ACTIVITY_NEW_TASK]，这里自动补上；
 * 其他异常仍向上抛出，避免掩盖调用方的编程错误。
 */
fun Context.safeStartActivity(intent: Intent): Boolean {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
