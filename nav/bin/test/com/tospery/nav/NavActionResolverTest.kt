package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavActionResolverTest {
    private val resolver = NavActionResolver(
        routeTable = NavRouteTable(
            definitions = listOf(
                NavRouteDefinition(
                    id = NavRouteId("about"),
                ),
                NavRouteDefinition(
                    id = NavRouteId("profile"),
                    requiresAuth = true,
                ),
                NavRouteDefinition(
                    id = NavRouteId("repository"),
                    path = "{owner}/{repo}",
                ),
                NavRouteDefinition(
                    id = NavRouteId("account"),
                    path = "{login}",
                ),
                navOverlayRouteDefinition(
                    presentation = NavPresentation.DIALOG,
                    requiresAuth = true,
                ),
                navOverlayRouteDefinition(
                    presentation = NavPresentation.SHEET,
                ),
            ),
        ),
    )

    @Test
    fun registeredInternalRouteResolvesToForwardAction() {
        val action = resolver.resolve(
            target = UrlNavigationTarget.InternalRoute(NavRoute("about")),
            isAuthenticated = false,
        )

        assertEquals(
            NavAction.Forward(route = NavRoute("about")),
            action,
        )
    }

    @Test
    fun dynamicRepositoryRouteProvidesPathParameters() {
        val route = NavRoute("devxoul/ReactorKit")

        val action =
            resolver.resolve(
                target = UrlNavigationTarget.InternalRoute(route),
                isAuthenticated = true,
            )

        assertEquals(
            NavAction.Forward(
                route = route,
                pathParameters =
                    mapOf(
                        "owner" to "devxoul",
                        "repo" to "ReactorKit",
                    ),
            ),
            action,
        )
    }

    @Test
    fun unregisteredInternalRouteResolvesToInvalidAction() {
        val action = resolver.resolve(
            target =
                UrlNavigationTarget.InternalRoute(
                    NavRoute("missing/too/many"),
                ),
            isAuthenticated = true,
        )

        assertTrue(action is NavAction.Invalid)
    }

    @Test
    fun dialogWithoutArgumentsResolvesAsAccountRoute() {
        val route = NavRoute("dialog")

        val action =
            resolver.resolve(
                target = UrlNavigationTarget.InternalRoute(route),
                isAuthenticated = true,
            )

        assertEquals(
            NavAction.Forward(
                route = route,
                pathParameters =
                    mapOf("login" to "dialog"),
            ),
            action,
        )
    }

    @Test
    fun authRequiredRouteResolvesToRequiresAuthenticationWhenLoggedOut() {
        val action = resolver.resolve(
            target = UrlNavigationTarget.InternalRoute(NavRoute("profile")),
            isAuthenticated = false,
        )

        assertEquals(
            NavAction.RequiresAuthentication(
                target = NavAction.Forward(route = NavRoute("profile")),
            ),
            action,
        )
    }

    @Test
    fun authRequiredRouteResolvesToForwardWhenLoggedIn() {
        val action = resolver.resolve(
            target = UrlNavigationTarget.InternalRoute(NavRoute("profile")),
            isAuthenticated = true,
        )

        assertEquals(
            NavAction.Forward(route = NavRoute("profile")),
            action,
        )
    }

    @Test
    fun predefinedDialogResolvesToPresentedDialogAction() {
        val route =
            NavOverlayRoute.Predefined(
                presentation = NavPresentation.DIALOG,
                id = NavOverlayId("clearcache"),
            ).toNavRoute()

        val action =
            resolver.resolve(
                target = UrlNavigationTarget.InternalRoute(route),
                isAuthenticated = true,
            )

        assertEquals(
            NavAction.Forward(
                route = route,
                mode = ForwardMode.PRESENT,
                presentation = NavPresentation.DIALOG,
            ),
            action,
        )
    }

    @Test
    fun generatedSheetResolvesToPresentedSheetAction() {
        val route =
            NavOverlayRoute.Generated(
                presentation = NavPresentation.SHEET,
                title = "Choose",
                actions =
                    listOf(
                        NavOverlayAction(
                            id = NavOverlayActionId("close"),
                            type = NavOverlayActionType.DISMISS,
                            title = "Close",
                        ),
                    ),
            ).toNavRoute()

        val action =
            resolver.resolve(
                target = UrlNavigationTarget.InternalRoute(route),
                isAuthenticated = false,
            )

        assertEquals(
            NavAction.Forward(
                route = route,
                mode = ForwardMode.PRESENT,
                presentation = NavPresentation.SHEET,
            ),
            action,
        )
    }

    @Test
    fun malformedDialogArgumentsResolveToInvalidAction() {
        val action =
            resolver.resolve(
                target =
                    UrlNavigationTarget.InternalRoute(
                        NavRoute("dialog?title=MissingActions"),
                    ),
                isAuthenticated = true,
            )

        assertTrue(action is NavAction.Invalid)
    }

    @Test
    fun externalAppTargetResolvesToOpenAction() {
        val target = UrlNavigationTarget.ExternalApp("mqqapi://example")
        val action = resolver.resolve(target, isAuthenticated = false)

        assertEquals(
            NavAction.Open(target = target, mode = OpenMode.EXTERNAL_APP),
            action,
        )
    }

    @Test
    fun systemUriTargetResolvesToOpenAction() {
        val target = UrlNavigationTarget.SystemUri("mailto:hello@example.com")
        val action = resolver.resolve(target, isAuthenticated = false)

        assertEquals(
            NavAction.Open(target = target, mode = OpenMode.SYSTEM_URI),
            action,
        )
    }

    @Test
    fun webUrlTargetResolvesToOpenAction() {
        val target = UrlNavigationTarget.WebUrl("https://example.com/about")
        val action = resolver.resolve(target, isAuthenticated = false)

        assertEquals(
            NavAction.Open(target = target, mode = OpenMode.WEB),
            action,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatedRoutePathThrowsException() {
        NavRouteTable(
            definitions = listOf(
                NavRouteDefinition(
                    id = NavRouteId("repo"),
                    path = "repo",
                ),
                NavRouteDefinition(
                    id = NavRouteId("repository"),
                    path = "/repo/",
                ),
            ),
        )
    }
}
