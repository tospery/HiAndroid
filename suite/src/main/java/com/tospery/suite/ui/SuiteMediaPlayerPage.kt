@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.ui.PlayerView
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.warning
import com.tospery.buildmetadata.module_suite.ModuleMetadata
import com.tospery.suite.R
import com.tospery.suite.media.SuiteMediaKind
import com.tospery.suite.media.SuiteMediaRequest
import kotlinx.coroutines.delay

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
    var hasStartedPlaying by remember(request, retryVersion) { mutableStateOf(false) }
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
                    if (playing) {
                        hasStartedPlaying = true
                    }
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
                        hasStartedPlaying = hasStartedPlaying,
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
    hasStartedPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val cdRotation = remember { Animatable(0f) }
    var currentPosition by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var isSeeking by remember(player) { mutableStateOf(false) }
    var seekPosition by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(player, isPlaying, isSeeking) {
        while (true) {
            duration = player.duration.takeIf { it > 0L } ?: 0L
            if (!isSeeking) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
            }
            if (!isPlaying) break
            delay(AUDIO_PROGRESS_UPDATE_INTERVAL_MILLIS.toLong())
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AUDIO_CONTROL_HORIZONTAL_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(modifier = Modifier.height(AUDIO_CONTROL_TOP_SPACING))

                Surface(
                    modifier = Modifier.size(AUDIO_PLAY_BUTTON_SIZE),
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

                Spacer(modifier = Modifier.height(AUDIO_PLAY_TO_PROGRESS_SPACING))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!hasStartedPlaying) {
                        Text(
                            text = stringResource(R.string.suite_media_player_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text =
                            stringResource(
                                R.string.suite_media_player_time,
                                formatPlaybackTime(
                                    if (isSeeking) seekPosition else currentPosition,
                                ),
                                formatPlaybackTime(duration),
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Slider(
                    value =
                        if (duration > 0L) {
                            (
                                (if (isSeeking) seekPosition else currentPosition)
                                    .toFloat() / duration.toFloat()
                            ).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                    onValueChange = { value ->
                        if (duration > 0L) {
                            isSeeking = true
                            seekPosition = (value * duration).toLong()
                        }
                    },
                    onValueChangeFinished = {
                        if (duration > 0L) {
                            player.seekTo(seekPosition)
                            currentPosition = seekPosition
                        }
                        isSeeking = false
                    },
                    enabled = duration > 0L,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val paddedMinutes = minutes.toString().padStart(2, '0')
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:$paddedMinutes:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
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
private val AUDIO_CONTROL_HORIZONTAL_PADDING = 24.dp
private val AUDIO_CONTROL_TOP_SPACING = 12.dp
private val AUDIO_PLAY_TO_PROGRESS_SPACING = 12.dp
private val AUDIO_CD_VERTICAL_OFFSET = 60.dp
private val AUDIO_CONTROL_AREA_HEIGHT = 176.dp
private val AUDIO_PLAY_BUTTON_SIZE = 64.dp
private val AUDIO_PLAY_BUTTON_ELEVATION = 4.dp
private val AUDIO_CD_SIZE = 240.dp
