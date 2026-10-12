package com.tospery.suite.share.umeng

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.tospery.base.sdk.SdkInitializationState
import com.tospery.base.share.ShareChannel
import com.tospery.base.share.ShareContent
import com.tospery.base.share.ShareImage
import com.tospery.base.share.ShareProvider
import com.tospery.base.share.ShareRequest
import com.tospery.base.share.ShareResult
import com.umeng.socialize.ShareAction
import com.umeng.socialize.PlatformConfig
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.UMShareListener
import com.umeng.socialize.bean.SHARE_MEDIA
import com.umeng.socialize.media.UMImage
import com.umeng.socialize.media.UMWeb
import java.io.File

private val defaultSupportedChannels =
    setOf(ShareChannel.ShortMessage, ShareChannel.Email)

/** Concrete execution path selected by the U-Share adapter for a share request. */
enum class UmengShareExecutionPath {
    UMENG_PLATFORM_HANDLER,
    ANDROID_SYSTEM_INTENT_FALLBACK,
}

/** U-Share adapter. It never calls U-Share before the shared Umeng runtime is ready. */
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
            sdkState: SdkInitializationState,
            enabledChannels: Set<ShareChannel> = defaultSupportedChannels,
            weChatAppId: String = "",
            fileProviderAuthority: String = "${activity.packageName}.fileprovider",
            onExecutionPathSelected: (ShareChannel, UmengShareExecutionPath) -> Unit = { _, _ -> },
        ): UmengShareProvider {
            val fallbackSdk = AndroidSystemShareSdk(activity)
            val weChatReady = sdkState.isInitialized() && weChatAppId.isNotBlank()
            if (weChatReady) {
                // 分享只需要公开 AppID；AppSecret 永远不由客户端配置或获取。
                PlatformConfig.setWeixin(weChatAppId, "")
                PlatformConfig.setWXFileProvider(fileProviderAuthority)
            }
            val weChatAvailable =
                isUmengPlatformModuleAvailable(ShareChannel.WeChat) &&
                    runCatching { activity.packageManager.getPackageInfo("com.tencent.mm", 0) }.isSuccess
            return UmengShareProvider(
                sdk =
                    PlatformAwareUmengShareSdk(
                        primarySdk = AndroidUmengShareSdk(activity),
                        fallbackSdk = fallbackSdk,
                        isUmengSdkInitialized = sdkState::isInitialized,
                        isPlatformModuleAvailable = ::isUmengPlatformModuleAvailable,
                        onExecutionPathSelected = onExecutionPathSelected,
                    ),
                supportedChannels =
                    resolveSupportedChannels(
                        enabledChannels = enabledChannels,
                        isSystemHandlerAvailable = fallbackSdk::isAvailable,
                        isUmengSdkReady = weChatReady,
                        isWeChatAvailable = weChatAvailable,
                    ),
            )
        }

        fun onActivityResult(
            sdkState: SdkInitializationState,
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
        ) {
            if (sdkState.isInitialized()) {
                UMShareAPI.get(activity).onActivityResult(requestCode, resultCode, data)
            }
        }

        fun release(
            sdkState: SdkInitializationState,
            activity: Activity,
        ) {
            if (sdkState.isInitialized()) {
                UMShareAPI.get(activity).release()
            }
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
    private val isUmengSdkInitialized: () -> Boolean,
    private val isPlatformModuleAvailable: (ShareChannel) -> Boolean,
    private val onExecutionPathSelected: (ShareChannel, UmengShareExecutionPath) -> Unit = { _, _ -> },
) : UmengShareSdk {
    override fun share(
        channel: ShareChannel,
        content: ShareContent,
        onResult: (ShareResult) -> Unit,
    ) {
        val usesUmengPlatform =
            isUmengSdkInitialized() && isPlatformModuleAvailable(channel)
        if (channel.isWeChat() && !usesUmengPlatform) {
            onResult(ShareResult.Failed(UnsupportedOperationException("WeChat SDK is unavailable.")))
            return
        }
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
        val action = ShareAction(activity)
            .setPlatform(platform)
            .withSubject(content.title)
            .withText(content.textWithUrl)
        if (channel.isWeChat()) {
            val url = content.url
            if (url.isNullOrBlank()) {
                onResult(ShareResult.Failed(IllegalArgumentException("WeChat cards require a URL.")))
                return
            }
            val web = UMWeb(url).apply {
                title = content.title
                description = content.text
                content.image?.let { image ->
                    setThumb(
                        when (image) {
                            is ShareImage.LocalFile -> UMImage(activity, File(image.path))
                            is ShareImage.RemoteUrl -> UMImage(activity, image.url)
                        },
                    )
                }
            }
            action.withMedia(web)
        }
        action.setCallback(
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
            ShareChannel.WeChat -> SHARE_MEDIA.WEIXIN
            ShareChannel.WeChatMoments -> SHARE_MEDIA.WEIXIN_CIRCLE
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
    isUmengSdkReady: Boolean = false,
    isWeChatAvailable: Boolean = false,
): Set<ShareChannel> =
    enabledChannels.filterTo(linkedSetOf()) { channel ->
        if (channel.isWeChat()) isUmengSdkReady && isWeChatAvailable else isSystemHandlerAvailable(channel)
    }

private fun ShareChannel.isWeChat(): Boolean =
    this == ShareChannel.WeChat || this == ShareChannel.WeChatMoments

private fun isUmengPlatformModuleAvailable(channel: ShareChannel): Boolean {
    val handlerClassName =
        when (channel) {
            ShareChannel.ShortMessage -> "com.umeng.socialize.handler.SmsHandler"
            ShareChannel.Email -> "com.umeng.socialize.handler.EmailHandler"
            ShareChannel.WeChat, ShareChannel.WeChatMoments -> "com.umeng.socialize.handler.UMWXHandler"
            else -> return false
        }
    return runCatching { Class.forName(handlerClassName, false, UmengShareProvider::class.java.classLoader) }.isSuccess
}
