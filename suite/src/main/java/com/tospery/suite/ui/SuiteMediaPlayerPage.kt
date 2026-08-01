@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import android.graphics.Color
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.warning
import com.tospery.buildmetadata.module_suite.ModuleMetadata
import com.tospery.suite.R
import com.tospery.suite.media.SuiteMediaKind
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
    var isPlaying by remember(request, retryVersion) { mutableStateOf(false) }
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
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    isPlaying = false
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
            isPlaying = false
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
                if (request.kind == SuiteMediaKind.AUDIO) {
                    SuiteAudioPlayerContent(
                        player = activePlayer,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
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
                            playerView.keepScreenOn = request.kind == SuiteMediaKind.VIDEO
                        },
                        onRelease = { playerView ->
                            playerView.player = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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

@Composable
private fun SuiteAudioPlayerContent(
    player: ExoPlayer,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val cdRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        while (true) {
            cdRotation.animateTo(
                targetValue = cdRotation.value + AUDIO_CD_DEGREES_PER_REVOLUTION,
                animationSpec = tween(
                    durationMillis = AUDIO_CD_ROTATION_DURATION_MILLIS,
                    easing = LinearEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Image(
            painter = painterResource(R.drawable.cd_img),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                color = MaterialTheme.colorScheme.primary,
                blendMode = BlendMode.SrcIn,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -AUDIO_CD_VERTICAL_OFFSET)
                .size(AUDIO_CD_SIZE)
                .graphicsLayer {
                    rotationZ = cdRotation.value
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AUDIO_CONTROL_AREA_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerControlView(viewContext).apply {
                        showTimeoutMs = 0
                        setTimeBarMinUpdateInterval(AUDIO_PROGRESS_UPDATE_INTERVAL_MILLIS)
                        setBackgroundColor(Color.TRANSPARENT)
                        findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.visibility =
                            View.GONE
                    }
                },
                update = { controlView ->
                    controlView.player = player
                },
                onRelease = { controlView ->
                    controlView.player = null
                },
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = AUDIO_PLAY_BUTTON_VERTICAL_OFFSET)
                    .size(AUDIO_PLAY_BUTTON_SIZE),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = AUDIO_PLAY_BUTTON_ELEVATION,
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector =
                            if (isPlaying) {
                                Icons.Outlined.Pause
                            } else {
                                Icons.Outlined.PlayArrow
                            },
                        contentDescription =
                            stringResource(
                                if (isPlaying) {
                                    R.string.suite_media_player_pause
                                } else {
                                    R.string.suite_media_player_play
                                },
                            ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
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

private const val AUDIO_CD_DEGREES_PER_REVOLUTION = 360f
private const val AUDIO_PROGRESS_UPDATE_INTERVAL_MILLIS = 16
private const val AUDIO_CD_ROTATION_DURATION_MILLIS = 8_000
private val AUDIO_CD_VERTICAL_OFFSET = 60.dp
private val AUDIO_CONTROL_AREA_HEIGHT = 152.dp
private val AUDIO_PLAY_BUTTON_SIZE = 64.dp
private val AUDIO_PLAY_BUTTON_VERTICAL_OFFSET = (-28).dp
private val AUDIO_PLAY_BUTTON_ELEVATION = 4.dp
private val AUDIO_CD_SIZE = 240.dp
