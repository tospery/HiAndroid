package com.tospery.nav

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.debug

class NavActionResolver(
    private val routeTable: NavRouteTable,
    private val redactSensitiveLogValues: Boolean = true,
) {
    fun resolve(
        target: UrlNavigationTarget,
        isAuthenticated: Boolean,
    ): NavAction {
        val action = when (target) {
            is UrlNavigationTarget.InternalRoute -> resolveInternalRoute(
                route = target.route,
                isAuthenticated = isAuthenticated,
            )

            is UrlNavigationTarget.ExternalApp -> NavAction.Open(
                target = target,
                mode = OpenMode.EXTERNAL_APP,
            )

            is UrlNavigationTarget.SystemUri -> NavAction.Open(
                target = target,
                mode = OpenMode.SYSTEM_URI,
            )

            is UrlNavigationTarget.WebUrl -> NavAction.Open(
                target = target,
                mode = OpenMode.WEB,
            )

            is UrlNavigationTarget.Unknown -> NavAction.Invalid(
                reason = "Unknown navigation target.",
                source = target.uri,
            )
        }

        debug(
            tag = NAV_LOG_TAG,
            attributes =
                listOf(
                    LogAttribute(
                        key = "target_type",
                        value = target.navigationLogType(),
                    ),
                    LogAttribute(
                        key = "target_url",
                        value =
                            target.toNavigationLogUrl(
                                redactSensitiveLogValues = redactSensitiveLogValues,
                            ),
                    ),
                    LogAttribute(
                        key = "action_type",
                        value = action.navigationLogType(),
                    ),
                    LogAttribute(
                        key = "authenticated",
                        value = isAuthenticated.toString(),
                    ),
                ),
        ) {
            "URL 导航动作解析完成。"
        }

        return action
    }

    private fun resolveInternalRoute(
        route: NavRoute,
        isAuthenticated: Boolean,
    ): NavAction {
        val parsedRoute = route.parse()
        val match = routeTable.match(parsedRoute)
            ?: return NavAction.Invalid(
                reason = "Route is not registered.",
                source = route.value,
            )
        val definition = match.definition

        if (!definition.presentation.accepts(parsedRoute)) {
            return NavAction.Invalid(
                reason = "Route arguments do not match its presentation.",
                source = route.value,
            )
        }

        val action = NavAction.Forward(
            route = route,
            pathParameters = match.pathParameters,
            mode = definition.defaultForwardMode,
            presentation = definition.presentation,
        )

        return if (definition.requiresAuth && !isAuthenticated) {
            NavAction.RequiresAuthentication(action)
        } else {
            action
        }
    }
}

private fun NavPresentation.accepts(route: ParsedNavRoute): Boolean {
    return when (this) {
        NavPresentation.SCREEN -> true
        NavPresentation.DIALOG ->
            NavOverlayRoute.parseOrNull(route)?.presentation == NavPresentation.DIALOG
        NavPresentation.SHEET ->
            NavOverlayRoute.parseOrNull(route)?.presentation == NavPresentation.SHEET
    }
}
