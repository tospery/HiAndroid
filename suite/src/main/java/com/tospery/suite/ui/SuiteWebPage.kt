package com.tospery.suite.ui

import android.webkit.CookieManager
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tospery.suite.R
import kotlinx.coroutines.launch
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
    onLoadFailure: (SuiteWebLoadFailure) -> Unit = {},
) {
    val isValidUrl = url.isSafeWebUrl()
    val isLikelyImageDocument = remember(url) { url.isLikelyImageUrl() }
    val preferredTitle = title?.trim()?.takeIf(String::isNotEmpty)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenExternal by rememberUpdatedState(onOpenExternal)
    val currentOnNavigationRequest by rememberUpdatedState(onNavigationRequest)
    val currentOnLoadFailure by rememberUpdatedState(onLoadFailure)
    var documentTitle by rememberSaveable(url) { mutableStateOf("") }
    var loadingProgress by remember(url) { mutableIntStateOf(0) }
    var activeUrl by rememberSaveable(url) { mutableStateOf(url) }
    var lastRenderedUrl by rememberSaveable(url) { mutableStateOf(url) }
    var activeWebView by remember(url) { mutableStateOf<WebView?>(null) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var hasRenderedDocument by rememberSaveable(url) { mutableStateOf(false) }
    var savedWebViewState by rememberSaveable(url) { mutableStateOf<Bundle?>(null) }
    var savedWebViewScrollX by rememberSaveable(url) { mutableIntStateOf(0) }
    var savedWebViewScrollY by rememberSaveable(url) { mutableIntStateOf(0) }
    var shouldRestoreSavedScroll by remember(url) { mutableStateOf(false) }
    var hasFatalRendererFailure by remember(url) { mutableStateOf(false) }
    var discardReleasedWebViewState by remember(url) { mutableStateOf(false) }
    var webViewGeneration by remember(url) { mutableIntStateOf(0) }
    var errorPageFailure by remember(url) { mutableStateOf<SuiteWebLoadFailure?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val updateNavigationAvailability: (WebView) -> Unit = { webView ->
        canGoBack = webView.canGoBack()
    }
    val saveWebSession: (WebView) -> Unit = { webView ->
        savedWebViewState = Bundle().also(webView::saveState)
        savedWebViewScrollX = webView.scrollX
        savedWebViewScrollY = webView.scrollY
    }
    val restoreLatestRenderedPage: () -> Unit = {
        activeUrl = lastRenderedUrl
        discardReleasedWebViewState = true
        webViewGeneration++
    }
    val retryNavigation: (String) -> Unit = { targetUrl ->
        errorPageFailure = null
        val webView = activeWebView
        if (webView == null || hasFatalRendererFailure) {
            savedWebViewState = null
            activeUrl = targetUrl
            webViewGeneration++
            hasFatalRendererFailure = false
        } else {
            webView.loadUrl(targetUrl)
        }
    }
    val refreshWebPage: () -> Unit = {
        errorPageFailure = null
        val webView = activeWebView
        if (webView == null || hasFatalRendererFailure) {
            savedWebViewState = null
            webViewGeneration++
            hasFatalRendererFailure = false
        } else {
            webView.reload()
        }
    }

    LaunchedEffect(url, isValidUrl) {
        if (!isValidUrl) {
            currentOnLoadFailure(
                SuiteWebLoadFailure(
                    url = url,
                    reason = SuiteWebLoadFailureReason.INVALID_URL,
                ),
            )
        }
    }
    val navigationPresentation = suiteWebNavigationPresentation(canGoBack)
    val navigateWebHistoryOrBack = {
        val currentWebView = activeWebView
        if (
            navigationPresentation.backAction == SuiteWebBackAction.GO_BACK &&
            currentWebView != null
        ) {
            currentWebView.goBack()
            updateNavigationAvailability(currentWebView)
        } else {
            currentOnBack()
        }
    }

    BackHandler(
        enabled = isValidUrl,
        onBack = navigateWebHistoryOrBack,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SuiteSnackbarHost(hostState = snackbarHostState) },
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
                        Row {
                            IconButton(onClick = navigateWebHistoryOrBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription =
                                        stringResource(R.string.suite_web_back),
                                )
                            }
                            if (navigationPresentation.showCloseControl) {
                                IconButton(onClick = currentOnBack) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription =
                                            stringResource(R.string.suite_web_close),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (isValidUrl && currentOnOpenExternal != null) {
                            IconButton(
                                onClick = { currentOnOpenExternal?.invoke(activeUrl) },
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
                if (!hasFatalRendererFailure) {
                key(url, webViewGeneration) {
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
                                setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                                    savedWebViewScrollX = scrollX
                                    savedWebViewScrollY = scrollY
                                }

                                webViewClient =
                                    object : WebViewClient() {
                                        private var hasReportedCurrentLoadFailure = false

                                        override fun onPageStarted(
                                            view: WebView,
                                            url: String,
                                            favicon: Bitmap?,
                                        ) {
                                            hasReportedCurrentLoadFailure = false
                                            errorPageFailure = null
                                            loadingProgress = 0
                                            updateNavigationAvailability(view)
                                        }

                                        override fun doUpdateVisitedHistory(
                                            view: WebView,
                                            url: String?,
                                            isReload: Boolean,
                                        ) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            url?.let { activeUrl = it }
                                            updateNavigationAvailability(view)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            if (!request.isForMainFrame) return false
                                            val targetUrl = request.url.toString()
                                            return when (
                                                val decision =
                                                    currentOnNavigationRequest?.invoke(targetUrl)
                                            ) {
                                                SuiteWebNavigationDecision.ALLOW_IN_WEB_VIEW,
                                                null,
                                                -> {
                                                    val isBlocked = !targetUrl.isSafeWebUrl()
                                                    if (isBlocked) {
                                                        currentOnLoadFailure(
                                                            SuiteWebLoadFailure(
                                                                url = targetUrl,
                                                                reason = targetUrl.navigationFailureReason(),
                                                            ),
                                                        )
                                                    }
                                                    isBlocked
                                                }

                                                SuiteWebNavigationDecision.CONSUMED -> true
                                                SuiteWebNavigationDecision.BLOCKED -> {
                                                    currentOnLoadFailure(
                                                        SuiteWebLoadFailure(
                                                            url = targetUrl,
                                                            reason = targetUrl.navigationFailureReason(),
                                                        ),
                                                    )
                                                    true
                                                }
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError,
                                        ) {
                                            if (request.isForMainFrame) {
                                                reportLoadFailure(
                                                    url = request.url.toString(),
                                                    reason = error.errorCode.toSuiteWebLoadFailureReason(),
                                                )
                                            }
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            errorResponse: WebResourceResponse,
                                        ) {
                                            if (request.isForMainFrame) {
                                                currentOnLoadFailure(
                                                    SuiteWebLoadFailure(
                                                        url = request.url.toString(),
                                                        reason = SuiteWebLoadFailureReason.HTTP,
                                                    ),
                                                )
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView,
                                            handler: SslErrorHandler,
                                            error: SslError,
                                        ) {
                                            reportLoadFailure(
                                                url = error.url,
                                                reason = SuiteWebLoadFailureReason.TLS,
                                            )
                                            handler.cancel()
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView,
                                            detail: RenderProcessGoneDetail,
                                        ): Boolean {
                                            reportLoadFailure(
                                                url = view.url?.takeIf(String::isNotBlank) ?: activeUrl,
                                                reason = SuiteWebLoadFailureReason.RENDERER,
                                            )
                                            hasFatalRendererFailure = true
                                            discardReleasedWebViewState = true
                                            return true
                                        }

                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String,
                                        ) {
                                            if (hasReportedCurrentLoadFailure) return
                                            activeUrl = url
                                            lastRenderedUrl = url
                                            loadingProgress = 100
                                            hasRenderedDocument = true
                                            errorPageFailure = null
                                            updateNavigationAvailability(view)
                                            saveWebSession(view)
                                            if (shouldRestoreSavedScroll) {
                                                shouldRestoreSavedScroll = false
                                                view.post {
                                                    view.scrollTo(
                                                        savedWebViewScrollX,
                                                        savedWebViewScrollY,
                                                    )
                                                }
                                            }
                                            view.evaluateJavascript(
                                                WEB_VIDEO_LAYOUT_FALLBACK_SCRIPT,
                                                null,
                                            )
                                        }

                                        private fun reportLoadFailure(
                                            url: String,
                                            reason: SuiteWebLoadFailureReason,
                                        ) {
                                            if (hasReportedCurrentLoadFailure) return
                                            hasReportedCurrentLoadFailure = true
                                            val failure = SuiteWebLoadFailure(url = url, reason = reason)
                                            currentOnLoadFailure(failure)
                                            when (
                                                failure.presentationAfter(
                                                    hasRenderedDocument = hasRenderedDocument,
                                                )
                                            ) {
                                                SuiteWebFailurePresentation.KEEP_WEB_CONTENT -> Unit
                                                SuiteWebFailurePresentation.ERROR_PAGE -> {
                                                    errorPageFailure = failure
                                                }

                                                SuiteWebFailurePresentation.RETRY_SNACKBAR -> {
                                                    restoreLatestRenderedPage()
                                                    scope.launch {
                                                        if (
                                                            snackbarHostState.showSnackbar(
                                                                message = context.getString(
                                                                    R.string.suite_web_load_failed,
                                                                ),
                                                                actionLabel = context.getString(
                                                                    R.string.suite_web_retry,
                                                                ),
                                                        ) == SnackbarResult.ActionPerformed
                                                        ) {
                                                            retryNavigation(failure.url)
                                                        }
                                                    }
                                                }
                                            }
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

                                val restoredHistory =
                                    savedWebViewState
                                        ?.let(::Bundle)
                                        ?.let(::restoreState)
                                shouldRestoreSavedScroll = restoredHistory != null
                                if (restoredHistory == null) {
                                    loadUrl(activeUrl)
                                } else {
                                    updateNavigationAvailability(this)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { releasedWebView ->
                            if (activeWebView === releasedWebView) {
                                activeWebView = null
                            }
                            if (!discardReleasedWebViewState) {
                                saveWebSession(releasedWebView)
                            }
                            discardReleasedWebViewState = false
                            releasedWebView.onPause()
                            releasedWebView.stopLoading()
                            releasedWebView.webChromeClient = WebChromeClient()
                            releasedWebView.webViewClient = WebViewClient()
                            releasedWebView.removeAllViews()
                            releasedWebView.destroy()
                        },
                    )
                }
                }
                if (errorPageFailure != null) {
                    SuiteWebLoadErrorContent(
                        onRetry = refreshWebPage,
                        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun SuiteWebLoadErrorContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.suite_web_load_error),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.suite_web_retry))
        }
    }
}

internal fun Int.toSuiteWebLoadFailureReason(): SuiteWebLoadFailureReason =
    when (this) {
        WebViewClient.ERROR_BAD_URL -> SuiteWebLoadFailureReason.INVALID_URL
        WebViewClient.ERROR_UNSUPPORTED_SCHEME ->
            SuiteWebLoadFailureReason.UNSUPPORTED_SCHEME
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT,
        WebViewClient.ERROR_IO,
        WebViewClient.ERROR_PROXY_AUTHENTICATION,
        -> SuiteWebLoadFailureReason.NETWORK
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> SuiteWebLoadFailureReason.TLS
        WebViewClient.ERROR_UNKNOWN -> SuiteWebLoadFailureReason.UNKNOWN
        else -> SuiteWebLoadFailureReason.UNKNOWN
    }

private fun String.navigationFailureReason(): SuiteWebLoadFailureReason {
    val scheme = runCatching { URI(trim()).scheme }.getOrNull()
    return if (
        scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true)
    ) {
        SuiteWebLoadFailureReason.INVALID_URL
    } else {
        SuiteWebLoadFailureReason.UNSUPPORTED_SCHEME
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
