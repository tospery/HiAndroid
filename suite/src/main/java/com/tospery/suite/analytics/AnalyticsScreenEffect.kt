package com.tospery.suite.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tospery.base.analytics.AnalyticsScreen
import com.tospery.base.analytics.AnalyticsTracker

/**
 * 将 Compose destination 的可见生命周期转换成成对、串行的页面进入/退出信号。
 *
 * 页面只在 STARTED 及以上状态计时；切换 destination、进入后台或离开组合时都会结束当前页面。
 */
@Composable
fun AnalyticsScreenEffect(
    screen: AnalyticsScreen?,
    tracker: AnalyticsTracker,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // 隐私授权完成后，宿主通常会随 UI state 重组；把启用状态作为 effect key 可立即开始当前页。
    val isTrackerEnabled = tracker.isEnabled()

    DisposableEffect(lifecycleOwner, screen, tracker, isTrackerEnabled) {
        val controller =
            screen?.takeIf { isTrackerEnabled }?.let { value ->
                AnalyticsScreenLifecycleController(
                    screen = value,
                    tracker = tracker,
                )
            }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> controller?.start()
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                    -> controller?.stop()

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.stop()
        }
    }
}

internal class AnalyticsScreenLifecycleController(
    private val screen: AnalyticsScreen,
    private val tracker: AnalyticsTracker,
) {
    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        tracker.enterScreen(screen)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        tracker.exitScreen(screen)
    }
}
