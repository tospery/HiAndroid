package com.tospery.suite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tospery.core.content.findActivity

/**
 * 为 Compose 页面设置系统栏图标明暗外观。
 *
 * 边到边页面的背景可能与应用默认页面不同，因此由页面明确声明图标颜色；离开页面后恢复调用方提供的默认外观。
 */
@Composable
fun SystemBarIconAppearance(
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean,
    restoreDarkStatusBarIcons: Boolean = useDarkStatusBarIcons,
    restoreDarkNavigationBarIcons: Boolean = useDarkNavigationBarIcons,
) {
    val view = LocalView.current
    DisposableEffect(
        view,
        useDarkStatusBarIcons,
        useDarkNavigationBarIcons,
        restoreDarkStatusBarIcons,
        restoreDarkNavigationBarIcons,
    ) {
        val window = view.context.findActivity()?.window
        if (window == null || view.isInEditMode) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = useDarkStatusBarIcons
            controller.isAppearanceLightNavigationBars = useDarkNavigationBarIcons

            onDispose {
                controller.isAppearanceLightStatusBars = restoreDarkStatusBarIcons
                controller.isAppearanceLightNavigationBars = restoreDarkNavigationBarIcons
            }
        }
    }
}

