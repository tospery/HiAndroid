package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SuiteSemanticColorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun webPageUsesPageContainerForItsCanvas() {
        composeTestRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(surfaceContainer = PageContainer),
            ) {
                Box(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
                    SuiteWebPage(
                        url = "invalid-url",
                        onBack = {},
                        modifier = Modifier.testTag(WebPageTag),
                    )
                }
            }
        }

        assertPixel(
            tag = WebPageTag,
            x = 2,
            y = 2,
            expectedColor = PageContainer,
        )
    }

    @Test
    fun hudUsesScrimAndHighContainerRoles() {
        composeTestRule.setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        scrim = Scrim,
                        surfaceContainerHigh = OverlayContainer,
                        surfaceTint = Color.Transparent,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(320.dp)
                            .background(Underlay),
                ) {
                    AppHud(
                        visible = true,
                        modifier = Modifier.testTag(HudTag),
                    )
                }
            }
        }

        val expectedScrim = Scrim.copy(alpha = DefaultScrimAlpha).compositeOver(Underlay)
        assertPixel(tag = HudTag, x = 2, y = 2, expectedColor = expectedScrim)
        assertContainsColor(tag = HudTag, expectedColor = OverlayContainer)
    }

    @Test
    fun snackbarUsesInverseRolesIncludingItsAction() {
        val hostState = SnackbarHostState()
        composeTestRule.setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = UnrelatedPrimary,
                        inverseSurface = SnackbarContainer,
                        inverseOnSurface = SnackbarContent,
                        inversePrimary = SnackbarAction,
                    ),
            ) {
                LaunchedEffect(hostState) {
                    hostState.showSnackbar(
                        message = SnackbarMessage,
                        actionLabel = SnackbarActionLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                SuiteSnackbarHost(
                    hostState = hostState,
                    modifier = Modifier.testTag(SnackbarHostTag),
                )
            }
        }

        composeTestRule.onNodeWithText(SnackbarMessage).assertExists()
        composeTestRule.onNodeWithText(SnackbarActionLabel).assertExists()
        assertContainsColor(tag = SnackbarHostTag, expectedColor = SnackbarContainer)
        assertContainsColor(tag = SnackbarHostTag, expectedColor = SnackbarContent)
        assertContainsColor(tag = SnackbarHostTag, expectedColor = SnackbarAction)
    }

    private fun assertPixel(
        tag: String,
        x: Int,
        y: Int,
        expectedColor: Color,
    ) {
        val bitmap = composeTestRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        assertEquals(expectedColor.toArgb(), bitmap.getPixel(x, y))
    }

    private fun assertContainsColor(
        tag: String,
        expectedColor: Color,
    ) {
        val bitmap = composeTestRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        assertTrue(
            "Expected ${expectedColor.toArgb()} in $tag",
            expectedColor.toArgb() in pixels,
        )
    }

    private companion object {
        const val WebPageTag = "suite_semantic_web_page"
        const val HudTag = "suite_semantic_hud"
        const val SnackbarHostTag = "suite_semantic_snackbar"
        const val SnackbarMessage = "Message"
        const val SnackbarActionLabel = "ACTION"
        const val DefaultScrimAlpha = 0.24f

        val PageContainer = Color(0xFF123456)
        val Underlay = Color(0xFFFFFFFF)
        val Scrim = Color(0xFF000000)
        val OverlayContainer = Color(0xFF246824)
        val UnrelatedPrimary = Color(0xFFFF00FF)
        val SnackbarContainer = Color(0xFF102030)
        val SnackbarContent = Color(0xFFFFC000)
        val SnackbarAction = Color(0xFF00FFFF)
    }
}
