@file:Suppress("FunctionNaming")

package com.tospery.suite.nav.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.tospery.nav.NavOverlayAction
import com.tospery.nav.NavOverlayActionType
import com.tospery.nav.NavOverlayRoute
import com.tospery.nav.NavOverlayRouteArguments
import com.tospery.nav.NavPresentation
import com.tospery.nav.ParsedNavRoute
import com.tospery.nav.navOverlayRoutePath
import com.tospery.nav.navOverlayRoutePattern

/**
 * 为 Navigation Compose 注册统一的页面级 Dialog/Sheet URL destination。
 *
 * 预置弹层由调用方按 id 渲染业务 UI；自动生成弹层由本模块渲染，并把受限动作对象回传。
 * 关闭开关默认保持既有行为；需要显式关闭入口的 Sheet 可禁用系统返回与遮罩点击。
 */
fun NavGraphBuilder.routedOverlayDestination(
    navController: NavHostController,
    presentation: NavPresentation,
    onGeneratedAction: (NavOverlayRoute.Generated, NavOverlayAction) -> Unit = { _, _ -> },
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    predefinedContent: @Composable (NavOverlayRoute.Predefined, () -> Unit) -> Unit,
) {
    require(presentation != NavPresentation.SCREEN) {
        "Routed overlays only support dialog and sheet presentations."
    }

    dialog(
        route = navOverlayRoutePattern(presentation),
        arguments = overlayNavigationArguments(),
        dialogProperties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = false,
                // Sheet 的 Surface 延伸到系统导航条下方，由内容自行补足安全间距。
                decorFitsSystemWindows = presentation != NavPresentation.SHEET,
            ),
    ) { entry ->
        val dismiss: () -> Unit = remember(navController) {
            {
                navController.dismissOverlay()
            }
        }
        val overlay =
            remember(entry.arguments, presentation) {
                NavOverlayRoute.parseOrNull(
                    ParsedNavRoute(
                        path = navOverlayRoutePath(presentation),
                        queryParameters =
                            NavOverlayRouteArguments.allNames
                                .mapNotNull { name ->
                                    entry.arguments
                                        ?.getString(name)
                                        ?.let { value -> name to value }
                                }.toMap(),
                    ),
                )
            }

        if (overlay == null) {
            LaunchedEffect(entry.id) {
                dismiss()
            }
            return@dialog
        }

        RoutedOverlayFrame(
            presentation = presentation,
            onDismiss = dismiss,
            dismissOnClickOutside = dismissOnClickOutside,
        ) {
            when (overlay) {
                is NavOverlayRoute.Predefined -> {
                    predefinedContent(overlay, dismiss)
                }

                is NavOverlayRoute.Generated -> {
                    GeneratedOverlayContent(
                        overlay = overlay,
                        onAction = { action ->
                            onGeneratedAction(overlay, action)
                            dismiss()
                        },
                    )
                }
            }
        }
    }
}

private fun NavHostController.dismissOverlay() {
    popBackStack()
}

private fun overlayNavigationArguments() =
    NavOverlayRouteArguments.allNames.map { name ->
        navArgument(name) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    }

private val NavOverlayRouteArguments.allNames: List<String>
    get() = listOf(ID, TITLE, MESSAGE, ACTIONS)

@Composable
private fun RoutedOverlayFrame(
    presentation: NavPresentation,
    onDismiss: () -> Unit,
    dismissOnClickOutside: Boolean,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentAlignment =
        when (presentation) {
            NavPresentation.DIALOG -> Alignment.Center
            NavPresentation.SHEET -> Alignment.BottomCenter
            NavPresentation.SCREEN -> error("Screen is not an overlay presentation.")
        }
    val contentPadding =
        when (presentation) {
            NavPresentation.DIALOG -> PaddingValues(horizontal = 24.dp, vertical = 32.dp)
            NavPresentation.SHEET -> PaddingValues(top = 48.dp)
            NavPresentation.SCREEN -> PaddingValues(0.dp)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { if (dismissOnClickOutside) onDismiss() },
                ).padding(contentPadding),
        contentAlignment = contentAlignment,
    ) {
        Box(
            modifier =
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun GeneratedOverlayContent(
    overlay: NavOverlayRoute.Generated,
    onAction: (NavOverlayAction) -> Unit,
) {
    val sheetModifier =
        if (overlay.presentation == NavPresentation.SHEET) {
            Modifier.navigationBarsPadding()
        } else {
            Modifier
        }
    val shape =
        if (overlay.presentation == NavPresentation.SHEET) {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        } else {
            RoundedCornerShape(28.dp)
        }

    Surface(
        modifier =
            sheetModifier
                .fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = overlay.title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            overlay.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            overlay.actions.forEach { action ->
                GeneratedOverlayAction(
                    action = action,
                    onClick = { onAction(action) },
                )
            }
        }
    }
}

@Composable
private fun GeneratedOverlayAction(
    action: NavOverlayAction,
    onClick: () -> Unit,
) {
    when (action.type) {
        NavOverlayActionType.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.title)
            }
        }

        NavOverlayActionType.SECONDARY -> {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.title)
            }
        }

        NavOverlayActionType.DESTRUCTIVE -> {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(text = action.title)
            }
        }

        NavOverlayActionType.DISMISS -> {
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = action.title)
            }
        }
    }
}
