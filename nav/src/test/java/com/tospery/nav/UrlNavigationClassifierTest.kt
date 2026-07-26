package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNavigationClassifierTest {
    private val classifier = UrlNavigationClassifier(
        config = UrlNavigationConfig(
            appSchemes = setOf(UrlScheme("higit")),
            trustedHosts = setOf(UrlHost("higit.com")),
        ),
    )

    @Test
    fun customSchemeUsesHostAsInternalRoutePath() {
        val target = classifier.classify("higit://about")

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("about"),
                origin =
                    InternalRouteOrigin.AppScheme(
                        scheme = UrlScheme("higit"),
                    ),
            ),
            target,
        )
    }

    @Test
    fun customSchemeSupportsNonDnsRouteAuthority() {
        val target = classifier.classify("higit://received_events")

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("received_events"),
                origin =
                    InternalRouteOrigin.AppScheme(
                        scheme = UrlScheme("higit"),
                    ),
            ),
            target,
        )
    }

    @Test
    fun customSchemeOverlayPreservesEncodedRouteArguments() {
        val overlay =
            NavOverlayRoute.Predefined(
                presentation = NavPresentation.DIALOG,
                id = NavOverlayId("clearcache"),
            )

        val target =
            classifier.classify(
                overlay.toUri(UrlScheme("higit")),
            )

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("dialog?id=clearcache"),
                origin =
                    InternalRouteOrigin.AppScheme(
                        scheme = UrlScheme("higit"),
                    ),
            ),
            target,
        )
    }

    @Test
    fun appLinkUsesPathAsInternalRoutePath() {
        val target = classifier.classify("https://higit.com/about")

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("about"),
                origin =
                    InternalRouteOrigin.TrustedWebHost(
                        scheme = UrlScheme("https"),
                        host = UrlHost("higit.com"),
                    ),
            ),
            target,
        )
    }

    @Test
    fun appLinkPreservesEncodedPathSeparatorsForRouteValidation() {
        val target = classifier.classify("https://higit.com/owner/repo/blob/main/src%2Fmain/App.kt")

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("owner/repo/blob/main/src%2Fmain/App.kt"),
                origin =
                    InternalRouteOrigin.TrustedWebHost(
                        scheme = UrlScheme("https"),
                        host = UrlHost("higit.com"),
                    ),
            ),
            target,
        )
    }

    @Test
    fun relativeRoutePreservesRelativeOrigin() {
        val target = classifier.classify("home?tab=3")

        assertEquals(
            UrlNavigationTarget.InternalRoute(
                route = NavRoute("home?tab=3"),
                origin = InternalRouteOrigin.RelativeRoute,
            ),
            target,
        )
    }

    @Test
    fun thirdPartySchemeIsExternalApp() {
        val target = classifier.classify("mqqapi://example")

        assertEquals(
            UrlNavigationTarget.ExternalApp("mqqapi://example"),
            target,
        )
    }

    @Test
    fun systemSchemeIsSystemUri() {
        val target = classifier.classify("mailto:hello@example.com")

        assertEquals(
            UrlNavigationTarget.SystemUri("mailto:hello@example.com"),
            target,
        )
    }

    @Test
    fun untrustedHttpsUrlIsWebUrl() {
        val target = classifier.classify("https://example.com/about")

        assertEquals(
            UrlNavigationTarget.WebUrl("https://example.com/about"),
            target,
        )
    }

    @Test
    fun invalidUriIsUnknown() {
        val target = classifier.classify("https://exa mple.com")

        assertTrue(target is UrlNavigationTarget.Unknown)
    }

    @Test
    fun customSchemeWithoutRoutePathIsUnknown() {
        val target = classifier.classify("higit://")

        assertTrue(target is UrlNavigationTarget.Unknown)
    }

    @Test
    fun appLinkWithoutRoutePathIsUnknown() {
        val target = classifier.classify("https://higit.com")

        assertTrue(target is UrlNavigationTarget.Unknown)
    }
}
