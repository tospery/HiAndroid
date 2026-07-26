package com.tospery.suite.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SuiteListDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun titleItemsAndCloseActionAreExposed() {
        var closeCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                SuiteListDialogContent(
                    title = "Branches",
                    closeContentDescription = "Close dialog",
                    onCloseClick = { closeCount++ },
                ) {
                    item {
                        Text(text = "main")
                    }
                    item {
                        Text(text = "release")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Branches").assertIsDisplayed()
        composeTestRule.onNodeWithText("main").assertIsDisplayed()
        composeTestRule.onNodeWithText("release").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close dialog").performClick()

        assertEquals(1, closeCount)
    }
}
