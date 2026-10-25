package com.tospery.suite.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tospery.suite.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuiteLoadMoreFooterComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loadingFooterIsCompactCenteredAndAnimated() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                SuiteLoadMoreFooter(
                    state = SuiteLoadMoreState.LOADING,
                    onRetry = {},
                    modifier = Modifier.width(320.dp).testTag("footer"),
                )
            }
        }
        composeRule.waitForIdle()

        val footer = composeRule.onNodeWithTag("footer").getUnclippedBoundsInRoot()
        val dots =
            composeRule.onNodeWithTag("suite_load_more_dots", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val loadingText =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.suite_load_more_loading)
        val text =
            composeRule.onNodeWithText(loadingText, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val footerCenter = (footer.top.value + footer.bottom.value) / 2f
        val dotsCenter = (dots.top.value + dots.bottom.value) / 2f
        val textCenter = (text.top.value + text.bottom.value) / 2f
        assertEquals(52f, footer.bottom.value - footer.top.value, 1f)
        assertEquals(footerCenter, dotsCenter, 1f)
        assertEquals(footerCenter, textCenter, 1f)

        val firstFrame =
            composeRule.onNodeWithTag("suite_load_more_dots", useUnmergedTree = true)
                .captureToImage()
        composeRule.mainClock.advanceTimeBy(320)
        val secondFrame =
            composeRule.onNodeWithTag("suite_load_more_dots", useUnmergedTree = true)
                .captureToImage()
        val x = firstFrame.width / 6
        val y = firstFrame.height / 2
        assertNotEquals(
            firstFrame.toPixelMap()[x, y],
            secondFrame.toPixelMap()[x, y],
        )
    }
}
