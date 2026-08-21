package com.tospery.suite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SuiteSurfaceDefaultsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sharedContainersUseLightThemeSurfaceRolesByDefault() {
        assertSharedDefaultColors(
            colorScheme =
                lightColorScheme(
                    surfaceContainer = LightPageContainer,
                    surfaceContainerLow = LightContentRow,
                ),
            expectedPageContainer = LightPageContainer,
            expectedContentRow = LightContentRow,
        )
    }

    @Test
    fun sharedContainersUseDarkThemeSurfaceRolesByDefault() {
        assertSharedDefaultColors(
            colorScheme =
                darkColorScheme(
                    surfaceContainer = DarkPageContainer,
                    surfaceContainerLow = DarkContentRow,
                ),
            expectedPageContainer = DarkPageContainer,
            expectedContentRow = DarkContentRow,
        )
    }

    @Test
    fun listRowVariantsForwardTheirContainerColorOverride() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    SuiteListActionRow(
                        title = "Action",
                        onClick = {},
                        modifier = Modifier.testTag(ActionRowTag),
                        containerColor = OverrideContentRow,
                    )
                    SuiteSelectableListRow(
                        title = "Selectable",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag(SelectableRowTag),
                        containerColor = OverrideContentRow,
                    )
                    SuiteNavigationListRow(
                        title = "Navigation",
                        onClick = {},
                        modifier = Modifier.testTag(NavigationRowTag),
                        containerColor = OverrideContentRow,
                    )
                }
            }
        }

        listOf(ActionRowTag, SelectableRowTag, NavigationRowTag).forEach { tag ->
            assertNodeBackground(tag = tag, expectedColor = OverrideContentRow)
        }
    }

    private fun assertSharedDefaultColors(
        colorScheme: ColorScheme,
        expectedPageContainer: Color,
        expectedContentRow: Color,
    ) {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = colorScheme) {
                SuiteDefaultColorSpecimen()
            }
        }

        assertNodeBackground(
            tag = TopAppBarTag,
            expectedColor = expectedPageContainer,
        )
        assertNodeBackground(
            tag = ListSectionTag,
            expectedColor = expectedContentRow,
        )
        assertNodeBackground(
            tag = ListRowTag,
            expectedColor = expectedContentRow,
        )
        assertNodeBackground(
            tag = TextFieldTag,
            expectedColor = expectedContentRow,
        )
        assertNodeBackground(
            tag = MultilineTextFieldTag,
            expectedColor = expectedContentRow,
        )
        assertNodeBackground(
            tag = GrowingTextFieldTag,
            expectedColor = expectedContentRow,
        )
    }

    @Composable
    private fun SuiteDefaultColorSpecimen() {
        Column {
            SuiteCenterAlignedTopAppBar(
                title = { Text(text = "Top app bar") },
                modifier = Modifier.testTag(TopAppBarTag),
            )
            SuiteListSection(modifier = Modifier.testTag(ListSectionTag)) {
                Text(text = "Section")
            }
            SuiteListRow(
                title = "Row",
                modifier = Modifier.testTag(ListRowTag),
            )
            SuiteSingleLineTextField(
                value = "Field",
                onValueChange = {},
                modifier = Modifier.testTag(TextFieldTag),
            )
            SuiteMultilineTextField(
                value = "Multiline",
                onValueChange = {},
                height = 56.dp,
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.testTag(MultilineTextFieldTag),
            )
            SuiteGrowingMultilineTextField(
                value = "Growing",
                onValueChange = {},
                minimumHeight = 56.dp,
                modifier = Modifier.testTag(GrowingTextFieldTag),
            )
        }
    }

    private fun assertNodeBackground(
        tag: String,
        expectedColor: Color,
    ) {
        val image = composeTestRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        assertEquals(
            expectedColor.toArgb(),
            image.getPixel(SamplePixelOffset, SamplePixelOffset),
        )
    }

    private companion object {
        const val TopAppBarTag = "suite_default_top_app_bar"
        const val ListSectionTag = "suite_default_list_section"
        const val ListRowTag = "suite_default_list_row"
        const val TextFieldTag = "suite_default_text_field"
        const val MultilineTextFieldTag = "suite_default_multiline_text_field"
        const val GrowingTextFieldTag = "suite_default_growing_text_field"
        const val ActionRowTag = "suite_action_row"
        const val SelectableRowTag = "suite_selectable_row"
        const val NavigationRowTag = "suite_navigation_row"
        const val SamplePixelOffset = 2

        val LightPageContainer = Color(0xFF103050)
        val LightContentRow = Color(0xFF507090)
        val DarkPageContainer = Color(0xFF406080)
        val DarkContentRow = Color(0xFF80A0C0)
        val OverrideContentRow = Color(0xFFA0C0E0)
    }
}
