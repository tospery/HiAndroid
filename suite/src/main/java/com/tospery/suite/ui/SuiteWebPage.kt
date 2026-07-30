package com.tospery.suite.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tospery.suite.R
import java.net.URI

/**
 * 用于展示 HTTP(S) 页面的无业务通用组件。
 *
 * 为获得接近移动浏览器的渲染效果，启用 JavaScript 和 DOM Storage；
 * 同时保持 Web scheme 白名单、禁止本地文件与 ContentProvider 访问，并且不暴露原生 JS bridge。
 * [title] 有非空值时优先显示；否则使用网页通过 WebChromeClient 返回的标题。
 */
@Composable
fun SuiteWebPage(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onOpenExternal: ((String) -> Unit)? = null,
    onNavigationRequest: ((String) -> SuiteWebNavigationDecision)? = null,
) {
    val isValidUrl = url.isSafeWebUrl()
    val isLikelyImageDocument = remember(url) { url.isLikelyImageUrl() }
    val preferredTitle = title?.trim()?.takeIf(String::isNotEmpty)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenExternal by rememberUpdatedState(onOpenExternal)
    val currentOnNavigationRequest by rememberUpdatedState(onNavigationRequest)
    var documentTitle by remember(url) { mutableStateOf("") }
    var loadingProgress by remember(url) { mutableIntStateOf(0) }
    var activeUrl by remember(url) { mutableStateOf(url) }
    var activeWebView by remember(url) { mutableStateOf<WebView?>(null) }
    val navigateBack = {
        val currentWebView = activeWebView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            currentOnBack()
        }
    }

    BackHandler(
        enabled = isValidUrl,
        onBack = navigateBack,
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                SuiteCenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = preferredTitle ?: documentTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription =
                                    stringResource(R.string.suite_web_back),
                            )
                        }
                    },
                    actions = {
                        if (isValidUrl && currentOnOpenExternal != null) {
                            IconButton(
                                onClick = {
                                    currentOnOpenExternal?.invoke(activeUrl)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInBrowser,
                                    contentDescription =
                                        stringResource(R.string.suite_web_open_external),
                                )
                            }
                        }
                    },
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                ) {
                    if (isValidUrl && loadingProgress < 100) {
                        LinearProgressIndicator(
                            progress = { loadingProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (isValidUrl) {
                key(url) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                activeWebView = this
                                settings.apply {
                                    // 该组件用于真实网页；README 等不受信任静态文档使用独立的无脚本 WebView。
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)

                                    allowFileAccess = false
                                    allowContentAccess = false
                                    mixedContentMode =
                                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    safeBrowsingEnabled = true
                                    mediaPlaybackRequiresUserGesture = true

                                    // 保留系统 WebView 的真实版本，只移除嵌入式标识以请求站点的移动浏览器页面。
                                    userAgentString = userAgentString.asMobileBrowserUserAgent()

                                    // 图片文档先完整适配控件宽度，之后仍可通过双指手势查看原始细节。
                                    useWideViewPort = isLikelyImageDocument
                                    loadWithOverviewMode = isLikelyImageDocument
                                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

                                    // 保留移动端浏览器常用的双指缩放，但隐藏旧式屏幕缩放按钮。
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            if (!request.isForMainFrame) return false
                                            val targetUrl = request.url.toString()
                                            return when (
                                                currentOnNavigationRequest?.invoke(targetUrl)
                                            ) {
                                                SuiteWebNavigationDecision.ALLOW_IN_WEB_VIEW,
                                                null,
                                                -> !targetUrl.isSafeWebUrl()

                                                SuiteWebNavigationDecision.CONSUMED,
                                                SuiteWebNavigationDecision.BLOCKED,
                                                -> true
                                            }
                                        }

                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String,
                                        ) {
                                            activeUrl = url
                                            loadingProgress = 100
                                            view.evaluateJavascript(
                                                WEB_VIDEO_LAYOUT_FALLBACK_SCRIPT,
                                                null,
                                            )
                                        }
                                    }

                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onReceivedTitle(
                                            view: WebView,
                                            receivedTitle: String,
                                        ) {
                                            documentTitle = receivedTitle.trim()
                                        }

                                        override fun onProgressChanged(
                                            view: WebView,
                                            newProgress: Int,
                                        ) {
                                            loadingProgress =
                                                newProgress.coerceIn(0, 100)
                                        }
                                    }

                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { releasedWebView ->
                            if (activeWebView === releasedWebView) {
                                activeWebView = null
                            }
                            releasedWebView.onPause()
                            releasedWebView.stopLoading()
                            releasedWebView.webChromeClient = WebChromeClient()
                            releasedWebView.webViewClient = WebViewClient()
                            releasedWebView.removeAllViews()
                            releasedWebView.destroy()
                        },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.suite_web_invalid_url),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

internal fun String.isSafeWebUrl(): Boolean {
    val normalizedUrl = trim()
    if (
        normalizedUrl.isEmpty() ||
        normalizedUrl.any(Char::isISOControl) ||
        '\\' in normalizedUrl
    ) {
        return false
    }
    val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return false
    val isSupportedScheme =
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
    return uri.isAbsolute &&
        isSupportedScheme &&
        !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null
}

internal fun String.isLikelyImageUrl(): Boolean {
    if (!isSafeWebUrl()) return false
    val path = runCatching { URI(trim()).path }.getOrNull() ?: return false
    return IMAGE_FILE_NAME_REGEX.matches(path.substringAfterLast('/'))
}

private fun String.asMobileBrowserUserAgent(): String {
    return replace("; wv", "", ignoreCase = true)
        .replace(" Version/4.0", "", ignoreCase = true)
}

/**
 * Android WebView 在控件首次以 0 高度创建时，个别网页的 `vh` 上限会停留在 0px。
 * 仅修复已经取得元数据、可见、具有宽度且最终高度仍为 0 的 video，不影响正常布局。
 */
private val WEB_VIDEO_LAYOUT_FALLBACK_SCRIPT =
    """
    (() => {
      const updateVideoLayout = (video) => {
        const styles = window.getComputedStyle(video);
        const rect = video.getBoundingClientRect();
        const computedMaxHeight = Number.parseFloat(styles.maxHeight);
        const viewportHeight = Math.max(
          window.innerHeight,
          document.documentElement.clientHeight
        );
        if (
          video.videoWidth <= 0 ||
          video.videoHeight <= 0 ||
          video.controls !== true ||
          styles.display === "none" ||
          styles.visibility === "hidden" ||
          rect.width <= 0 ||
          rect.height > 0 ||
          computedMaxHeight !== 0 ||
          viewportHeight <= 0
        ) {
          return;
        }

        const naturalHeight = rect.width * video.videoHeight / video.videoWidth;
        video.style.maxHeight = viewportHeight + "px";
        video.style.height = Math.min(naturalHeight, viewportHeight) + "px";
        video.style.objectFit = "contain";
      };

      const updateAllVideos = () => {
        document.querySelectorAll("video").forEach((video) => {
          if (video.dataset.suiteLayoutFallback !== "true") {
            video.dataset.suiteLayoutFallback = "true";
            video.addEventListener(
              "loadedmetadata",
              () => updateVideoLayout(video)
            );
          }
          updateVideoLayout(video);
        });
      };

      updateAllVideos();
      window.requestAnimationFrame(updateAllVideos);
      window.setTimeout(updateAllVideos, 250);
      window.addEventListener("resize", updateAllVideos);
    })();
    """.trimIndent()

private val IMAGE_FILE_NAME_REGEX =
    Regex(
        pattern = """.+\.(?:avif|bmp|gif|jpe?g|png|svg|webp)""",
        option = RegexOption.IGNORE_CASE,
    )
