package com.tospery.nav

enum class ForwardMode {
    PUSH,
    PRESENT,
    REPLACE,
    SINGLE_TOP,
}

/**
 * 内部路由在 Android UI 中的呈现方式。
 *
 * Dialog 与 Sheet 仍是可进入返回栈的页面级目标；Toast、Snackbar、tips 等瞬时反馈
 * 不属于路由目标，应由各端的 UI effect 系统处理。
 */
enum class NavPresentation {
    SCREEN,
    DIALOG,
    SHEET,
}

enum class BackMode {
    AUTO,
    POP_ONE,
    POP_TO_ROOT,
    DISMISS,
}

enum class OpenMode {
    EXTERNAL_APP,
    SYSTEM_URI,
    WEB,
}

sealed interface NavAction {
    data class Forward(
        val route: NavRoute,
        val pathParameters: Map<String, String> = emptyMap(),
        val mode: ForwardMode = ForwardMode.PUSH,
        val presentation: NavPresentation = NavPresentation.SCREEN,
    ) : NavAction

    data class Back(
        val mode: BackMode = BackMode.AUTO,
    ) : NavAction

    data class Open(
        val target: UrlNavigationTarget,
        val mode: OpenMode,
    ) : NavAction

    data class RequiresAuthentication(
        val target: NavAction,
    ) : NavAction

    data class Invalid(
        val reason: String,
        val source: String? = null,
    ) : NavAction
}
