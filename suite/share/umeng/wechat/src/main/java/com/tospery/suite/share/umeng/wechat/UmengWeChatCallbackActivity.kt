package com.tospery.suite.share.umeng.wechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.umeng.socialize.PlatformConfig
import com.umeng.socialize.UMShareAPI
import com.umeng.socialize.bean.SHARE_MEDIA
import com.umeng.socialize.handler.UMWXHandler

/**
 * Host implements the consent check at <applicationId>.wxapi.WXEntryActivity.
 * The vendor callback superclass initializes U-Share unconditionally, so this activity owns the
 * Android lifecycle and reaches the vendor only after the host confirms its runtime is ready.
 */
abstract class UmengWeChatCallbackActivity : Activity() {
    protected abstract fun isShareSdkReady(): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchCallback(intent)
    }

    private fun dispatchCallback(callbackIntent: Intent) {
        if (!isShareSdkReady()) {
            finish()
            return
        }
        val handled = runCatching {
            val handler = UMShareAPI.get(applicationContext).getHandler(SHARE_MEDIA.WEIXIN) as UMWXHandler
            handler.onCreate(applicationContext, PlatformConfig.getPlatform(SHARE_MEDIA.WEIXIN))
            handler.wxApi.handleIntent(callbackIntent, object : IWXAPIEventHandler {
                override fun onReq(request: BaseReq?) {
                    finish()
                }

                override fun onResp(response: BaseResp?) {
                    try {
                        // This entry belongs to sharing, never to OAuth authorization.
                        if (response?.type == SEND_MESSAGE_RESPONSE_TYPE) handler.wxEventHandler.onResp(response)
                    } finally {
                        finish()
                    }
                }
            })
        }.getOrDefault(false)
        if (!handled) finish()
    }

    companion object {
        private const val SEND_MESSAGE_RESPONSE_TYPE = 2
    }
}
