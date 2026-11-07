package com.tospery.suite.socialsharing.umeng

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.tospery.base.sdk.SdkInitializationState
import com.tospery.base.socialsharing.SocialSharingChannel
import com.tospery.base.socialsharing.SocialSharingContent
import com.tospery.base.socialsharing.SocialSharingImage
import com.tospery.base.socialsharing.SocialSharingProvider
import com.tospery.base.socialsharing.SocialSharingRequest
import com.tospery.base.socialsharing.SocialSharingResult
import com.umeng.socialize.ShareAction
import com.umeng.socialize.PlatformConfig
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.UMShareListener
import com.umeng.socialize.bean.SHARE_MEDIA
import com.umeng.socialize.media.UMImage
import com.umeng.socialize.media.UMWeb
import java.io.File

private val defaultSupportedChannels =
    setOf(SocialSharingChannel.ShortMessage, SocialSharingChannel.Email)

/** Concrete execution path selected by the U-Share adapter for a share request. */
enum class UmengSocialSharingExecutionPath {
    UMENG_PLATFORM_HANDLER,
    ANDROID_SYSTEM_INTENT_FALLBACK,
}

/** U-Share adapter. It never calls U-Share before the shared Umeng runtime is ready. */
class UmengSocialSharingProvider internal constructor(
    private val sdk: UmengSocialSharingSdk,
    override val supportedChannels: Set<SocialSharingChannel> = defaultSupportedChannels,
) : SocialSharingProvider {
    override fun share(
        request: SocialSharingRequest,
        onResult: (SocialSharingResult) -> Unit,
    ) {
        if (request.channel !in supportedChannels) {
            onResult(SocialSharingResult.Failed(UnsupportedOperationException(request.channel.value)))
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
            enabledChannels: Set<SocialSharingChannel> = defaultSupportedChannels,
            weChatAppId: String = "",
            fileProviderAuthority: String = "${activity.packageName}.fileprovider",
            onExecutionPathSelected: (SocialSharingChannel, UmengSocialSharingExecutionPath) -> Unit = { _, _ -> },
        ): UmengSocialSharingProvider {
            val fallbackSdk = AndroidSystemSocialSharingSdk(activity)
            val weChatReady = sdkState.isInitialized() && weChatAppId.isNotBlank()
            if (weChatReady) {
                // 分享只需要公开 AppID；AppSecret 永远不由客户端配置或获取。
                PlatformConfig.setWeixin(weChatAppId, "")
                PlatformConfig.setWXFileProvider(fileProviderAuthority)
            }
            val weChatAvailable =
                isUmengPlatformModuleAvailable(SocialSharingChannel.WeChat) &&
                    runCatching { activity.packageManager.getPackageInfo("com.tencent.mm", 0) }.isSuccess
            return UmengSocialSharingProvider(
                sdk =
                    PlatformAwareUmengSocialSharingSdk(
                        primarySdk = AndroidUmengSocialSharingSdk(activity),
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

internal fun interface UmengSocialSharingSdk {
    fun share(
        channel: SocialSharingChannel,
        content: SocialSharingContent,
        onResult: (SocialSharingResult) -> Unit,
    )
}

/** Uses U-Share when its offline platform module exists, otherwise delegates to Android. */
internal class PlatformAwareUmengSocialSharingSdk(
    private val primarySdk: UmengSocialSharingSdk,
    private val fallbackSdk: UmengSocialSharingSdk,
    private val isUmengSdkInitialized: () -> Boolean,
    private val isPlatformModuleAvailable: (SocialSharingChannel) -> Boolean,
    private val onExecutionPathSelected: (SocialSharingChannel, UmengSocialSharingExecutionPath) -> Unit = { _, _ -> },
) : UmengSocialSharingSdk {
    override fun share(
        channel: SocialSharingChannel,
        content: SocialSharingContent,
        onResult: (SocialSharingResult) -> Unit,
    ) {
        val usesUmengPlatform =
            isUmengSdkInitialized() && isPlatformModuleAvailable(channel)
        if (channel.isWeChat() && !usesUmengPlatform) {
            onResult(SocialSharingResult.Failed(UnsupportedOperationException("WeChat SDK is unavailable.")))
            return
        }
        val executionPath =
            if (usesUmengPlatform) {
                UmengSocialSharingExecutionPath.UMENG_PLATFORM_HANDLER
            } else {
                UmengSocialSharingExecutionPath.ANDROID_SYSTEM_INTENT_FALLBACK
            }
        onExecutionPathSelected(channel, executionPath)
        val sdk = if (usesUmengPlatform) primarySdk else fallbackSdk
        sdk.share(channel, content, onResult)
    }
}

private class AndroidUmengSocialSharingSdk(
    private val activity: Activity,
) : UmengSocialSharingSdk {
    override fun share(
        channel: SocialSharingChannel,
        content: SocialSharingContent,
        onResult: (SocialSharingResult) -> Unit,
    ) {
        val platform = channel.toUmengPlatform()
        val action = ShareAction(activity)
            .setPlatform(platform)
            .withSubject(content.title)
            .withText(content.textWithUrl)
        if (channel.isWeChat()) {
            val url = content.url
            if (url.isNullOrBlank()) {
                onResult(SocialSharingResult.Failed(IllegalArgumentException("WeChat cards require a URL.")))
                return
            }
            val web = UMWeb(url).apply {
                title = content.title
                description = content.text
                content.image?.let { image ->
                    setThumb(
                        when (image) {
                            is SocialSharingImage.LocalFile -> UMImage(activity, File(image.path))
                            is SocialSharingImage.RemoteUrl -> UMImage(activity, image.url)
                        },
                    )
                }
            }
            action.withMedia(web)
        }
        action.setCallback(
                object : UMShareListener {
                    override fun onStart(platform: SHARE_MEDIA) {
                        onResult(SocialSharingResult.Started)
                    }

                    override fun onResult(platform: SHARE_MEDIA) {
                        onResult(SocialSharingResult.Completed)
                    }

                    override fun onError(
                        platform: SHARE_MEDIA,
                        throwable: Throwable,
                    ) {
                        onResult(SocialSharingResult.Failed(throwable))
                    }

                    override fun onCancel(platform: SHARE_MEDIA) {
                        onResult(SocialSharingResult.Cancelled)
                    }
                },
            ).share()
    }

    private fun SocialSharingChannel.toUmengPlatform(): SHARE_MEDIA =
        when (this) {
            SocialSharingChannel.ShortMessage -> SHARE_MEDIA.SMS
            SocialSharingChannel.Email -> SHARE_MEDIA.EMAIL
            SocialSharingChannel.WeChat -> SHARE_MEDIA.WEIXIN
            SocialSharingChannel.WeChatMoments -> SHARE_MEDIA.WEIXIN_CIRCLE
            else -> throw UnsupportedOperationException(value)
        }
}

private class AndroidSystemSocialSharingSdk(
    private val activity: Activity,
) : UmengSocialSharingSdk {
    fun isAvailable(channel: SocialSharingChannel): Boolean =
        createIntent(channel = channel, content = null)
            ?.resolveActivity(activity.packageManager) != null

    override fun share(
        channel: SocialSharingChannel,
        content: SocialSharingContent,
        onResult: (SocialSharingResult) -> Unit,
    ) {
        val intent = createIntent(channel = channel, content = content)
        if (intent == null) {
            onResult(
                SocialSharingResult.Failed(
                    UnsupportedOperationException(channel.value),
                ),
            )
            return
        }

        if (!isAvailable(channel)) {
            onResult(
                SocialSharingResult.Failed(
                    ActivityNotFoundException("No Android handler for ${channel.value}."),
                ),
            )
            return
        }

        runCatching { activity.startActivity(intent) }
            .fold(
                onSuccess = { onResult(SocialSharingResult.Started) },
                onFailure = { throwable -> onResult(SocialSharingResult.Failed(throwable)) },
            )
    }

    private fun createIntent(
        channel: SocialSharingChannel,
        content: SocialSharingContent?,
    ): Intent? =
        when (channel) {
            SocialSharingChannel.ShortMessage ->
                Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI)).apply {
                    content?.let { putExtra(SMS_BODY_EXTRA, it.textWithUrl) }
                }

            SocialSharingChannel.Email ->
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
    enabledChannels: Set<SocialSharingChannel>,
    isSystemHandlerAvailable: (SocialSharingChannel) -> Boolean,
    isUmengSdkReady: Boolean = false,
    isWeChatAvailable: Boolean = false,
): Set<SocialSharingChannel> =
    enabledChannels.filterTo(linkedSetOf()) { channel ->
        if (channel.isWeChat()) isUmengSdkReady && isWeChatAvailable else isSystemHandlerAvailable(channel)
    }

private fun SocialSharingChannel.isWeChat(): Boolean =
    this == SocialSharingChannel.WeChat || this == SocialSharingChannel.WeChatMoments

private fun isUmengPlatformModuleAvailable(channel: SocialSharingChannel): Boolean {
    val handlerClassName =
        when (channel) {
            SocialSharingChannel.ShortMessage -> "com.umeng.socialize.handler.SmsHandler"
            SocialSharingChannel.Email -> "com.umeng.socialize.handler.EmailHandler"
            SocialSharingChannel.WeChat, SocialSharingChannel.WeChatMoments -> "com.umeng.socialize.handler.UMWXHandler"
            else -> return false
        }
    return runCatching { Class.forName(handlerClassName, false, UmengSocialSharingProvider::class.java.classLoader) }.isSuccess
}
