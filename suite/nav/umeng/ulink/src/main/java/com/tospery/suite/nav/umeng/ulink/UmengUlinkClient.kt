package com.tospery.suite.nav.umeng.ulink

import android.content.Context
import android.net.Uri
import com.umeng.umlink.MobclickLink
import com.umeng.umlink.UMLinkListener
import java.net.URI

data class UmengUlinkTarget(
    val path: String,
    val queryParameters: Map<String, String>,
)

sealed interface UmengUlinkResult {
    data class Resolved(
        val target: UmengUlinkTarget,
        val installParameters: Map<String, String> = emptyMap(),
    ) : UmengUlinkResult

    data class NoDeferredLink(
        val installParameters: Map<String, String> = emptyMap(),
    ) : UmengUlinkResult

    data class Failed(
        val message: String,
    ) : UmengUlinkResult
}

enum class UmengUlinkWakeupUrlClassification {
    VALID,
    NOT_ULINK,
    INVALID,
}

/**
 * U-Link wake-up and deferred-deep-link adapter.
 *
 * The adapter returns data only. The application must still validate and resolve the target with
 * its own route table instead of passing the path directly to a navigation framework.
 */
class UmengUlinkClient internal constructor(
    private val sdk: UmengUlinkSdk,
    appScheme: String,
    concatenationHost: String,
) {
    private val normalizedAppScheme = appScheme.lowercase()
    private val normalizedConcatenationHost = concatenationHost.lowercase()

    init {
        require(APP_SCHEME_PATTERN.matches(normalizedAppScheme)) {
            "U-Link app scheme is invalid."
        }
        require(HOST_PATTERN.matches(normalizedConcatenationHost)) {
            "U-Link concatenation host is invalid."
        }
    }

    fun classifyWakeupUrl(url: String): UmengUlinkWakeupUrlClassification {
        val uri = runCatching { URI(url) }.getOrNull()
        val parameterNames = queryParameterNames(uri?.rawQuery ?: url.rawQueryFallback())
        val hasUlinkMarker =
            UMENG_SDK_QUERY_PARAMETER in parameterNames ||
                UMENG_LINK_ID_QUERY_PARAMETER in parameterNames
        if (!hasUlinkMarker) return UmengUlinkWakeupUrlClassification.NOT_ULINK
        if (uri == null) return UmengUlinkWakeupUrlClassification.INVALID

        val hasExpectedScheme = uri.scheme.equals(normalizedAppScheme, ignoreCase = true)
        val hasExpectedHost = uri.host.equals(normalizedConcatenationHost, ignoreCase = true)
        val hasSafeAuthority =
            uri.userInfo == null &&
                uri.port == -1 &&
                uri.fragment == null

        return if (hasExpectedScheme && hasExpectedHost && hasSafeAuthority) {
            UmengUlinkWakeupUrlClassification.VALID
        } else {
            UmengUlinkWakeupUrlClassification.INVALID
        }
    }

    fun isUlinkWakeupUrl(url: String): Boolean =
        classifyWakeupUrl(url) == UmengUlinkWakeupUrlClassification.VALID

    private fun queryParameterNames(rawQuery: String?): Set<String> =
        rawQuery
            ?.split('&')
            .orEmpty()
            .mapNotNull { parameter ->
                parameter.substringBefore('=', missingDelimiterValue = parameter)
                    .takeIf(String::isNotBlank)
            }.toSet()

    private fun String.rawQueryFallback(): String? =
        substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
            .takeIf(String::isNotBlank)

    fun handleWakeupUrl(
        url: String,
        onResult: (UmengUlinkResult) -> Unit,
    ) {
        sdk.handle(url, listener(onResult = onResult))
    }

    fun requestDeferredLink(
        clipboardEnabled: Boolean = false,
        onResult: (UmengUlinkResult) -> Unit,
    ) {
        sdk.requestInstallParameters(
            clipboardEnabled = clipboardEnabled,
            listener = listener(onResult = onResult),
        )
    }

    private fun listener(
        installParameters: Map<String, String> = emptyMap(),
        onResult: (UmengUlinkResult) -> Unit,
    ): UmengUlinkCallback =
        object : UmengUlinkCallback {
            override fun onLink(
                path: String,
                queryParameters: Map<String, String>,
            ) {
                onResult(
                    UmengUlinkResult.Resolved(
                        target = UmengUlinkTarget(path, queryParameters.toMap()),
                        installParameters = installParameters,
                    ),
                )
            }

            override fun onInstall(
                parameters: Map<String, String>,
                wakeupUrl: String?,
            ) {
                if (wakeupUrl.isNullOrBlank()) {
                    onResult(UmengUlinkResult.NoDeferredLink(parameters.toMap()))
                    return
                }

                sdk.handle(
                    wakeupUrl,
                    listener(
                        installParameters = parameters.toMap(),
                        onResult = onResult,
                    ),
                )
            }

            override fun onError(message: String) {
                onResult(UmengUlinkResult.Failed(message))
            }
        }

    companion object {
        fun create(
            context: Context,
            appScheme: String,
            concatenationHost: String,
        ): UmengUlinkClient =
            UmengUlinkClient(
                sdk = AndroidUmengUlinkSdk(context),
                appScheme = appScheme,
                concatenationHost = concatenationHost,
            )

        private const val UMENG_SDK_QUERY_PARAMETER = "_sdk"
        private const val UMENG_LINK_ID_QUERY_PARAMETER = "linkid"
        private val APP_SCHEME_PATTERN = Regex("[a-z][a-z0-9+.-]{1,31}")
        private val HOST_PATTERN =
            Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")
    }
}

internal interface UmengUlinkSdk {
    fun handle(
        url: String,
        listener: UmengUlinkCallback,
    )

    fun requestInstallParameters(
        clipboardEnabled: Boolean,
        listener: UmengUlinkCallback,
    )
}

internal interface UmengUlinkCallback {
    fun onLink(
        path: String,
        queryParameters: Map<String, String>,
    )

    fun onInstall(
        parameters: Map<String, String>,
        wakeupUrl: String?,
    )

    fun onError(message: String)
}

private class AndroidUmengUlinkSdk(
    context: Context,
) : UmengUlinkSdk {
    private val applicationContext = context.applicationContext ?: context

    override fun handle(
        url: String,
        listener: UmengUlinkCallback,
    ) {
        MobclickLink.handleUMLinkURI(
            applicationContext,
            Uri.parse(url),
            listener.toUmengListener(),
        )
    }

    override fun requestInstallParameters(
        clipboardEnabled: Boolean,
        listener: UmengUlinkCallback,
    ) {
        MobclickLink.getInstallParams(
            applicationContext,
            clipboardEnabled,
            listener.toUmengListener(),
        )
    }

    private fun UmengUlinkCallback.toUmengListener(): UMLinkListener =
        object : UMLinkListener {
            override fun onLink(
                path: String,
                queryParameters: HashMap<String, String>,
            ) {
                this@toUmengListener.onLink(path, queryParameters)
            }

            override fun onInstall(
                installParameters: HashMap<String, String>,
                wakeupUri: Uri,
            ) {
                this@toUmengListener.onInstall(
                    installParameters,
                    wakeupUri.toString().takeIf(String::isNotBlank),
                )
            }

            override fun onError(error: String) {
                this@toUmengListener.onError(error)
            }
        }
}
