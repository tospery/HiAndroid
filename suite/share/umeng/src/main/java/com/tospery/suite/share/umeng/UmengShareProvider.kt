package com.tospery.suite.share.umeng

import android.app.Activity
import android.content.Intent
import com.tospery.base.share.ShareChannel
import com.tospery.base.share.ShareContent
import com.tospery.base.share.ShareProvider
import com.tospery.base.share.ShareRequest
import com.tospery.base.share.ShareResult
import com.umeng.socialize.ShareAction
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.UMShareListener
import com.umeng.socialize.bean.SHARE_MEDIA

/** U-Share adapter. The host must initialize the Umeng common SDK after privacy consent. */
class UmengShareProvider internal constructor(
    private val sdk: UmengShareSdk,
) : ShareProvider {
    override val supportedChannels: Set<ShareChannel> =
        setOf(ShareChannel.ShortMessage, ShareChannel.Email)

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
        fun create(activity: Activity): UmengShareProvider =
            UmengShareProvider(AndroidUmengShareSdk(activity))

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
