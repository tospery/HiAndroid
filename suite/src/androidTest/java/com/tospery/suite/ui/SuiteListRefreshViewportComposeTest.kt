package com.tospery.suite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuiteListRefreshViewportComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun newFirstRowBecomesVisibleWhenListWasAtTop() {
        val harness = showList()
        composeRule.runOnIdle { harness.rows.add(0, "new") }

        composeRule.waitUntil(5_000) {
            harness.listState.firstVisibleItemIndex == 0 &&
                harness.listState.firstVisibleItemScrollOffset == 0 &&
                harness.listState.topVisibleKey() == "new"
        }
        composeRule.onNodeWithTag("row-new").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(harness.snackbarHostState.currentSnackbarData) }
    }

    @Test
    fun deletedTopVisibleRowIsReplacedByNextSurvivor() {
        val harness = showList()
        moveRowThreeToTop(harness)
        composeRule.runOnIdle { harness.rows.remove("row3") }

        composeRule.waitUntil(5_000) {
            harness.listState.topVisibleKey() == "row4"
        }
        composeRule.runOnIdle { assertNull(harness.snackbarHostState.currentSnackbarData) }
    }

    @Test
    fun deletingLowerRowKeepsAnchorAndNewHeadOffersViewAction() {
        val harness = showList()
        moveRowThreeToTop(harness)
        composeRule.runOnIdle { harness.rows.remove("row4") }
        composeRule.waitUntil(5_000) {
            harness.listState.topVisibleKey() == "row3"
        }
        composeRule.runOnIdle { assertNull(harness.snackbarHostState.currentSnackbarData) }

        composeRule.runOnIdle { harness.rows.add(0, "new") }
        composeRule.waitUntil(5_000) {
            harness.snackbarHostState.currentSnackbarData != null
        }
        composeRule.runOnIdle { harness.rows.add("tail") }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("row3", harness.listState.topVisibleKey())
            assertNotNull(
                harness.snackbarHostState.currentSnackbarData
                    ?.visuals
                    ?.actionLabel,
            )
            harness.snackbarHostState.currentSnackbarData?.performAction()
        }
        composeRule.waitUntil(5_000) {
            harness.listState.topVisibleKey() == "new"
        }
    }

    @Test
    fun scrolledAnchorReturnsAfterAnEmptyPagingGeneration() {
        val harness = showList()
        moveRowThreeToTop(harness)
        composeRule.runOnIdle { harness.rows.clear() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { harness.rows.addAll((1..30).map { "row$it" }) }

        composeRule.waitUntil(5_000) {
            harness.listState.topVisibleKey() == "row3"
        }
        composeRule.runOnIdle { assertNull(harness.snackbarHostState.currentSnackbarData) }
    }

    private fun showList(): Harness {
        val rows = mutableStateListOf<String>().apply { addAll((1..30).map { "row$it" }) }
        lateinit var listState: LazyListState
        lateinit var snackbarHostState: SnackbarHostState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            listState = rememberLazyListState()
            snackbarHostState = remember { SnackbarHostState() }
            scope = rememberCoroutineScope()
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SuiteListRefreshViewportEffect(
                        keys = rows.toList(),
                        listState = listState,
                        snackbarHostState = snackbarHostState,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                    ) {
                        items(rows, key = { it }) { row ->
                            Text(
                                text = row,
                                modifier = Modifier.fillMaxWidth().height(96.dp).testTag("row-$row"),
                            )
                        }
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return Harness(rows, listState, snackbarHostState, scope)
    }

    private fun moveRowThreeToTop(harness: Harness) {
        composeRule.runOnIdle {
            harness.scope.launch { harness.listState.animateScrollToItem(2) }
        }
        composeRule.waitUntil(5_000) {
            !harness.listState.isScrollInProgress && harness.listState.topVisibleKey() == "row3"
        }
    }

    private fun LazyListState.topVisibleKey(): Any? =
        layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                item.index >= firstVisibleItemIndex &&
                    item.offset + item.size > layoutInfo.viewportStartOffset
            }?.key

    private data class Harness(
        val rows: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
        val listState: LazyListState,
        val snackbarHostState: SnackbarHostState,
        val scope: CoroutineScope,
    )
}
