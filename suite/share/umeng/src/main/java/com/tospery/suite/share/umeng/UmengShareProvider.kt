package com.tospery.suite.share.umeng

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.tospery.base.share.ShareChannel
import com.tospery.base.share.ShareContent
import com.tospery.base.share.ShareProvider
import com.tospery.base.share.ShareRequest
import com.tospery.base.share.ShareResult
import com.umeng.socialize.ShareAction
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.UMShareListener
import com.umeng.socialize.bean.SHARE_MEDIA

private val defaultSupportedChannels =
    setOf(ShareChannel.ShortMessage, ShareChannel.Email)

/** Concrete execution path selected by the U-Share adapter for a share request. */
enum class UmengShareExecutionPath {
    UMENG_PLATFORM_HANDLER,
    ANDROID_SYSTEM_INTENT_FALLBACK,
}

/** U-Share adapter. The host must initialize the Umeng common SDK after privacy consent. */
class UmengShareProvider internal constructor(
    private val sdk: UmengShareSdk,
    override val supportedChannels: Set<ShareChannel> = defaultSupportedChannels,
) : ShareProvider {
    override fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        if (request.channel !in supportedChannels) {
            onResult(ShareResult.Failed(UnsupportedOperationException(request.channel.value)))
            return
        }

        sdk.share(
            channel = request.channel,
            content = request.content,
            onResult = onResult,
        )
    }

    companion object {
        fun create(
            activity: Activity,
            enabledChannels: Set<ShareChannel> = defaultSupportedChannels,
            onExecutionPathSelected: (ShareChannel, UmengShareExecutionPath) -> Unit = { _, _ -> },
        ): UmengShareProvider {
            val fallbackSdk = AndroidSystemShareSdk(activity)
            return UmengShareProvider(
                sdk =
                    PlatformAwareUmengShareSdk(
                        primarySdk = AndroidUmengShareSdk(activity),
                        fallbackSdk = fallbackSdk,
                        isPlatformModuleAvailable = ::isUmengPlatformModuleAvailable,
                        onExecutionPathSelected = onExecutionPathSelected,
                    ),
                supportedChannels =
                    resolveSupportedChannels(
                        enabledChannels = enabledChannels,
                        isSystemHandlerAvailable = fallbackSdk::isAvailable,
                    ),
            )
        }

        fun onActivityResult(
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
        ) {
            UMShareAPI.get(activity).onActivityResult(requestCode, resultCode, data)
        }

        fun release(activity: Activity) {
            UMShareAPI.get(activity).release()
        }
    }
}

internal fun interface UmengShareSdk {
    fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    )
}

/** Uses U-Share when its offline platform module exists, otherwise delegates to Android. */
internal class PlatformAwareUmengShareSdk(
    private val primarySdk: UmengShareSdk,
    private val fallbackSdk: UmengShareSdk,
    private val isPlatformModuleAvailable: (ShareChannel) -> Boolean,
    private val onExecutionPathSelected: (ShareChannel, UmengShareExecutionPath) -> Unit = { _, _ -> },
) : UmengShareSdk {
    override fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    ) {
        val usesUmengPlatform = isPlatformModuleAvailable(channel)
        val executionPath =
            if (usesUmengPlatform) {
                UmengShareExecutionPath.UMENG_PLATFORM_HANDLER
            } else {
                UmengShareExecutionPath.ANDROID_SYSTEM_INTENT_FALLBACK
            }
        onExecutionPathSelected(channel, executionPath)
        val sdk = if (usesUmengPlatform) primarySdk else fallbackSdk
        sdk.share(channel, content, onResult)
    }
}

private class AndroidUmengShareSdk(
    private val activity: Activity,
) : UmengShareSdk {
    override fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    ) {
        val platform = channel.toUmengPlatform()
        ShareAction(activity)
            .setPlatform(platform)
            .withSubject(content.title)
            .withText(content.textWithUrl)
            .setCallback(
                object : UMShareListener {
                    override fun onStart(platform: SHARE_MEDIA) {
                        onResult(ShareResult.Started)
                    }

                    override fun onResult(platform: SHARE_MEDIA) {
                        onResult(ShareResult.Completed)
                    }

                    override fun onError(
                        platform: SHARE_MEDIA,
                        throwable: Throwable,
                    ) {
                        onResult(ShareResult.Failed(throwable))
                    }

                    override fun onCancel(platform: SHARE_MEDIA) {
                        onResult(ShareResult.Cancelled)
                    }
                },
            ).share()
    }

    private fun ShareChannel.toUmengPlatform(): SHARE_MEDIA =
        when (this) {
            ShareChannel.ShortMessage -> SHARE_MEDIA.SMS
            ShareChannel.Email -> SHARE_MEDIA.EMAIL
            else -> throw UnsupportedOperationException(value)
        }
}

private class AndroidSystemShareSdk(
    private val activity: Activity,
) : UmengShareSdk {
    fun isAvailable(channel: ShareChannel): Boolean =
        createIntent(channel = channel, content = null)
            ?.resolveActivity(activity.packageManager) != null

    override fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    ) {
        val intent = createIntent(channel = channel, content = content)
        if (intent == null) {
            onResult(
                ShareResult.Failed(
                    UnsupportedOperationException(channel.value),
                ),
            )
            return
        }

        if (!isAvailable(channel)) {
            onResult(
                ShareResult.Failed(
                    ActivityNotFoundException("No Android handler for ${channel.value}."),
                ),
            )
            return
        }

        runCatching { activity.startActivity(intent) }
            .fold(
                onSuccess = { onResult(ShareResult.Started) },
                onFailure = { throwable -> onResult(ShareResult.Failed(throwable)) },
            )
    }

    private fun createIntent(
        channel: ShareChannel,
        content: ShareContent?,
    ): Intent? =
        when (channel) {
            ShareChannel.ShortMessage ->
                Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI)).apply {
                    content?.let { putExtra(SMS_BODY_EXTRA, it.textWithUrl) }
                }

            ShareChannel.Email ->
                Intent(Intent.ACTION_SENDTO, Uri.parse(EMAIL_URI)).apply {
                    content?.let {
                        putExtra(Intent.EXTRA_SUBJECT, it.title)
                        putExtra(Intent.EXTRA_TEXT, it.textWithUrl)
                    }
                }

            else -> null
        }

    companion object {
        private const val SMS_URI = "smsto:"
        private const val EMAIL_URI = "mailto:"
        private const val SMS_BODY_EXTRA = "sms_body"
    }
}

internal fun resolveSupportedChannels(
    enabledChannels: Set<ShareChannel>,
    isSystemHandlerAvailable: (ShareChannel) -> Boolean,
): Set<ShareChannel> =
    enabledChannels.filterTo(linkedSetOf(), isSystemHandlerAvailable)

private fun isUmengPlatformModuleAvailable(channel: ShareChannel): Boolean {
    val handlerClassName =
        when (channel) {
            ShareChannel.ShortMessage -> "com.umeng.socialize.handler.SmsHandler"
            ShareChannel.Email -> "com.umeng.socialize.handler.EmailHandler"
            else -> return false
        }
    return runCatching { Class.forName(handlerClassName) }.isSuccess
}
