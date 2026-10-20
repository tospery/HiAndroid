package com.tospery.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavOverlayRouteTest {
    @Test
    fun predefinedDialogBuildsStableUrlRoute() {
        val overlay =
            NavOverlayRoute.Predefined(
                presentation = NavPresentation.DIALOG,
                id = NavOverlayId("clearcache"),
            )

        assertEquals("dialog?id=clearcache", overlay.toNavRoute().value)
        assertEquals(
            "atlashub://dialog?id=clearcache",
            overlay.toUri(UrlScheme("atlashub")),
        )
        assertEquals(
            overlay,
            NavOverlayRoute.parseOrNull(overlay.toNavRoute()),
        )
    }

    @Test
    fun generatedSheetRoundTripsStructuredActionsJson() {
        val overlay =
            NavOverlayRoute.Generated(
                presentation = NavPresentation.SHEET,
                title = "标题",
                message = "信息",
                actions =
                    listOf(
                        NavOverlayAction(
                            id = NavOverlayActionId("confirm"),
                            type = NavOverlayActionType.PRIMARY,
                            title = "确认",
                        ),
                        NavOverlayAction(
                            id = NavOverlayActionId("cancel"),
                            type = NavOverlayActionType.DISMISS,
                            title = "取消",
                        ),
                    ),
            )

        val route = overlay.toNavRoute()

        assertTrue(route.value.startsWith("sheet?title="))
        assertTrue(route.value.contains("&actions="))
        assertEquals(overlay, NavOverlayRoute.parseOrNull(route))
    }

    @Test
    fun mixedPredefinedAndGeneratedArgumentsAreRejected() {
        val route = NavRoute("dialog?id=clearcache&title=Unexpected")

        assertNull(NavOverlayRoute.parseOrNull(route))
    }

    @Test
    fun generatedOverlayWithUnknownActionTypeIsRejected() {
        val route =
            navRoute("dialog") {
                query("title", "Title")
                query(
                    "actions",
                    """[{"id":"confirm","type":"execute","title":"Confirm"}]""",
                )
            }

        assertNull(NavOverlayRoute.parseOrNull(route))
    }
}
