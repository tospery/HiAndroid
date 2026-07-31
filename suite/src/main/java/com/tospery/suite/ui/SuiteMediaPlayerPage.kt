@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.warning
import com.tospery.buildmetadata.module_suite.ModuleMetadata
import com.tospery.suite.R
import com.tospery.suite.media.SuiteMediaRequest

private val suiteMediaPlayerLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "media-player",
    )

/**
 * 基于 AndroidX Media3 的单媒体播放页面。
 *
 * 页面随宿主 START/STOP 生命周期创建和释放 ExoPlayer，避免离开 destination 后继续占用
 * decoder、音频焦点或网络连接。[httpRequestHeaders] 只在运行时交给 Media3，不写入路由和日志。
 */
@OptIn(UnstableApi::class)
@Composable
fun SuiteMediaPlayerPage(
    request: SuiteMediaRequest,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    httpRequestHeaders: Map<String, String> = emptyMap(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    var retryVersion by remember(request) { mutableIntStateOf(0) }
    var player by remember(request, retryVersion) { mutableStateOf<ExoPlayer?>(null) }
    var hasPlaybackError by remember(request, retryVersion) { mutableStateOf(false) }

    BackHandler(onBack = currentOnBack)

    LifecycleStartEffect(request, retryVersion, httpRequestHeaders) {
        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(false)
                .setDefaultRequestProperties(httpRequestHeaders)
        val dataSourceFactory =
            DefaultDataSource.Factory(
                context,
                httpDataSourceFactory,
            )
        val createdPlayer =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(dataSourceFactory),
                ).build()
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    hasPlaybackError = true
                    warning(
                        tag = suiteMediaPlayerLogTag,
                        throwable = error,
                        attributes =
                            listOf(
                                LogAttribute("media_kind", request.kind.value),
                                LogAttribute("error_code", error.errorCodeName),
                            ),
                    ) {
                        "媒体播放失败。"
                    }
                }
            }

        createdPlayer.addListener(listener)
        createdPlayer.setMediaItem(request.toMediaItem())
        createdPlayer.playWhenReady = request.playWhenReady
        createdPlayer.prepare()
        player = createdPlayer

        onStopOrDispose {
            player = null
            createdPlayer.removeListener(listener)
            createdPlayer.release()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SuiteCenterAlignedTopAppBar(
                title = {
                    Text(
                        text =
                            request.title
                                ?: stringResource(R.string.suite_media_player_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = currentOnBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription =
                                stringResource(R.string.suite_media_player_back),
                        )
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            player?.let { activePlayer ->
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            controllerAutoShow = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            // 视频画布固定使用黑色，避免画面比例变化时边缘闪烁主题背景。
                            setBackgroundColor(Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = activePlayer
                        playerView.keepScreenOn = request.kind == com.tospery.suite.media.SuiteMediaKind.VIDEO
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: SuiteLoadingIndicator()

            if (hasPlaybackError) {
                SuiteErrorState(
                    title = stringResource(R.string.suite_media_player_error_title),
                    description =
                        stringResource(R.string.suite_media_player_error_description),
                    actionText = stringResource(R.string.suite_media_player_retry),
                    onActionClick = {
                        hasPlaybackError = false
                        retryVersion += 1
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

private fun SuiteMediaRequest.toMediaItem(): MediaItem {
    val metadata =
        MediaMetadata.Builder()
            .setTitle(title)
            .build()
    return MediaItem.Builder()
        .setUri(url)
        .setMediaMetadata(metadata)
        .build()
}
