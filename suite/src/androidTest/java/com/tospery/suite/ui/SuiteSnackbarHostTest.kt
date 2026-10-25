package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SuiteSnackbarHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneLineMessageUsesCenteredContentWidth() {
        val hostState = SnackbarHostState()
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(inverseSurface = SnackbarColor),
            ) {
                LaunchedEffect(hostState) {
                    hostState.showSnackbar("Saved", duration = SnackbarDuration.Indefinite)
                }
                Box(
                    modifier =
                        Modifier
                            .size(width = 360.dp, height = 180.dp)
                            .background(UnderlayColor)
                            .testTag(RootTag),
                ) {
                    SuiteSnackbarHost(
                        hostState = hostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        val messageBounds = composeRule.onNodeWithText("Saved").fetchSemanticsNode().boundsInRoot
        val image = composeRule.onNodeWithTag(RootTag).captureToImage().asAndroidBitmap()
        val centerY = messageBounds.center.y.toInt()
        val edgeX = with(composeRule.density) { 18.dp.roundToPx() }
        val centerX = with(composeRule.density) { 180.dp.roundToPx() }

        assertEquals(UnderlayColor.toArgb(), image.getPixel(edgeX, centerY))
        assertEquals(SnackbarColor.toArgb(), image.getPixel(centerX, centerY - 12))
    }

    @Test
    fun twoLineMessageUsesSafeAreaWidthAndStartAlignment() {
        val message = "Connection lost\nSaved content remains available"
        showMessage(message)

        val messageNode = composeRule.onNodeWithText(message)
        val messageBounds = messageNode.fetchSemanticsNode().boundsInRoot
        val image = composeRule.onNodeWithTag(RootTag).captureToImage().asAndroidBitmap()
        val centerY = messageBounds.center.y.toInt()
        val outsideX = with(composeRule.density) { 8.dp.roundToPx() }
        val insideX = with(composeRule.density) { 20.dp.roundToPx() }
        val expectedTextStart = with(composeRule.density) { 32.dp.toPx() }

        assertEquals(UnderlayColor.toArgb(), image.getPixel(outsideX, centerY))
        assertEquals(SnackbarColor.toArgb(), image.getPixel(insideX, centerY))
        assertTrue(messageBounds.left >= expectedTextStart)
        assertEquals(2, textLayout(message).lineCount)
    }

    @Test
    fun overflowingMessageStopsAtFiveLinesWithEllipsis() {
        val message = (1..6).joinToString("\n") { "Additional explanation line $it" }
        showMessage(message)

        val layout = textLayout(message)
        assertEquals(5, layout.lineCount)
        assertTrue(layout.isLineEllipsized(4))
    }

    @Test
    fun wrappingAtLargeFontSizeUsesFullWidthInDarkTheme() {
        val message = "Saved settings will be applied the next time this page opens."
        showMessage(message, dark = true, width = 280.dp, fontScale = 1.5f)

        val messageBounds = composeRule.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
        val image = composeRule.onNodeWithTag(RootTag).captureToImage().asAndroidBitmap()
        val insideX = with(composeRule.density) { 20.dp.roundToPx() }
        val outsideX = with(composeRule.density) { 8.dp.roundToPx() }

        assertTrue(textLayout(message).lineCount > 1)
        assertEquals(DarkSnackbarColor.toArgb(), image.getPixel(insideX, messageBounds.center.y.toInt()))
        assertEquals(UnderlayColor.toArgb(), image.getPixel(outsideX, messageBounds.center.y.toInt()))
    }

    @Test
    fun oneLineMessageWithActionUsesSafeAreaWidthAndKeepsAction() {
        val hostState = SnackbarHostState()
        val result = mutableStateOf<SnackbarResult?>(null)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(inverseSurface = SnackbarColor)) {
                LaunchedEffect(hostState) {
                    result.value =
                        hostState.showSnackbar(
                            message = "Deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Indefinite,
                        )
                }
                Box(
                    modifier =
                        Modifier
                            .size(width = 360.dp, height = 180.dp)
                            .background(UnderlayColor)
                            .testTag(RootTag),
                ) {
                    SuiteSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        val messageBounds = composeRule.onNodeWithText("Deleted").fetchSemanticsNode().boundsInRoot
        val image = composeRule.onNodeWithTag(RootTag).captureToImage().asAndroidBitmap()
        val insideX = with(composeRule.density) { 20.dp.roundToPx() }
        assertEquals(SnackbarColor.toArgb(), image.getPixel(insideX, messageBounds.center.y.toInt()))
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.runOnIdle { assertEquals(SnackbarResult.ActionPerformed, result.value) }
    }

    private fun showMessage(
        message: String,
        dark: Boolean = false,
        width: Dp = 360.dp,
        fontScale: Float = 1f,
    ) {
        val hostState = SnackbarHostState()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(
                    colorScheme =
                        if (dark) {
                            darkColorScheme(inverseSurface = DarkSnackbarColor)
                        } else {
                            lightColorScheme(inverseSurface = SnackbarColor)
                        },
                ) {
                    LaunchedEffect(hostState) {
                        hostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(width = width, height = 180.dp)
                                .background(UnderlayColor)
                                .testTag(RootTag),
                    ) {
                        SuiteSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    private fun textLayout(message: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(message)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private companion object {
        val SnackbarColor = Color(0xFF303039)
        val DarkSnackbarColor = Color(0xFFE5E1E9)
        val UnderlayColor = Color(0xFFE8E8EE)
        const val RootTag = "snackbar-root"
    }
}
