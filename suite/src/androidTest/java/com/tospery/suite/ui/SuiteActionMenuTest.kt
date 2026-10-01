package com.tospery.suite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SuiteActionMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingAnActionDismissesBeforeInvokingItsCallback() {
        var selected = ""
        composeRule.setContent {
            MaterialTheme {
                val expanded = remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Box(Modifier.size(48.dp)) {
                        SuiteActionMenu(
                            expanded = expanded.value,
                            onDismissRequest = { expanded.value = false },
                        ) {
                            SuiteActionMenuItem(
                                label = "Copy link",
                                onClick = { selected = if (expanded.value) "still open" else "copied" },
                            )
                        }
                    }
                }
                Text(selected)
            }
        }
        composeRule.onNodeWithText("Copy link").performClick()
        composeRule.onNodeWithText("Copy link").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("copied", selected) }
    }

    @Test
    fun lightMenuPointsAtTheRightEdgeAnchor() = assertMenuPointer(Alignment.TopEnd, dark = false)

    @Test
    fun darkMenuFlipsItsPointerAtTheBottomEdge() = assertMenuPointer(Alignment.BottomEnd, dark = true)

    @Test
    fun menuWidthTracksTheLongestOption() {
        val labels = mutableStateOf(listOf("复制链接", "外部浏览"))
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Box(Modifier.size(48.dp)) {
                        SuiteActionMenu(true, {}, Modifier.testTag("menu")) {
                            labels.value.forEach { label -> SuiteActionMenuItem(label, {}) }
                        }
                    }
                }
            }
        }
        val shortWidth = assertWidthFitsText("复制链接")
        composeRule.runOnIdle { labels.value = listOf("复制链接", "Open externally") }
        val longerWidth = assertWidthFitsText("Open externally")
        assertTrue(longerWidth > shortWidth)
    }

    private fun assertWidthFitsText(longestLabel: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(longestLabel)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val textWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        val menuWidth = composeRule.onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot.width
        val padding = with(composeRule.density) { 60.dp.toPx() }
        // 文字测量和两侧内边距各自取整，允许一个 dp 内的像素舍入差异。
        val roundingTolerance = with(composeRule.density) { 1.dp.toPx() }
        assertTrue(
            "Menu width $menuWidth should fit text $textWidth plus $padding padding",
            abs(menuWidth - textWidth - padding) <= roundingTolerance,
        )
        return menuWidth
    }

    private fun assertMenuPointer(alignment: Alignment, dark: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
                    Box(Modifier.size(48.dp).testTag("anchor")) {
                        SuiteActionMenu(true, {}, Modifier.testTag("menu")) {
                            SuiteActionMenuItem(label = "Copy link", onClick = {})
                            SuiteActionMenuItem(label = "Open externally", onClick = {})
                        }
                    }
                }
            }
        }
        val anchorLayout = composeRule.onNodeWithTag("anchor").fetchSemanticsNode().layoutInfo.coordinates
        val anchor = Rect(anchorLayout.positionOnScreen(), androidx.compose.ui.geometry.Size(
            anchorLayout.size.width.toFloat(), anchorLayout.size.height.toFloat()))
        val menuNode = composeRule.onNodeWithTag("menu")
        val menuLayout = menuNode.fetchSemanticsNode().layoutInfo.coordinates
        val menu = Rect(menuLayout.positionOnScreen(), androidx.compose.ui.geometry.Size(
            menuLayout.size.width.toFloat(), menuLayout.size.height.toFloat()))
        val pixels = menuNode.captureToImage().toPixelMap()
        val x = (anchor.center.x - menu.left).toInt()
        val tipY = if (dark) pixels.height - 7 else 6
        val arrowPixels = (0 until pixels.width).filter { pixels[it, tipY].toArgb() == 0xFF333333.toInt() }
        assertEquals("anchor=$anchor menu=$menu x=$x arrowPixels=$arrowPixels image=${pixels.width}x${pixels.height}",
            0xFF333333.toInt(), pixels[x, tipY].toArgb())
        assertEquals(0xFF333333.toInt(), pixels[4, pixels.height / 2].toArgb())
        assertTrue(if (dark) menu.bottom < anchor.top else menu.top > anchor.bottom)
    }
}
