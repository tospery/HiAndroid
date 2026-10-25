package com.tospery.suite.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.res.stringResource
import com.tospery.suite.R
import kotlinx.coroutines.launch

private data class VisibleRow(
    val index: Int,
    val offset: Int,
    val key: Any?,
)

private fun LazyListState.visibleRow(): VisibleRow {
    val layout = layoutInfo
    val item =
        layout.visibleItemsInfo.firstOrNull { info ->
            info.index >= firstVisibleItemIndex &&
                info.offset + info.size > layout.viewportStartOffset
        }
    return if (item == null) {
        VisibleRow(firstVisibleItemIndex, firstVisibleItemScrollOffset, null)
    } else {
        VisibleRow(
            index = item.index,
            offset = (layout.viewportStartOffset - item.offset).coerceAtLeast(0),
            key = item.key,
        )
    }
}

/** A viewport captured against the IDs that were visible before a list replacement. */
data class SuiteListRefreshViewport(
    val keys: List<Any>,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val leadingItems: Int = 0,
)

data class SuiteListRefreshPlacement(
    val index: Int?,
    val offset: Int = 0,
    val animateToTop: Boolean = false,
    val showUpdate: Boolean = false,
)

/** Resolve position by stable ID, so removing a visible row never falls back to a stale index. */
@Suppress("ReturnCount")
fun resolveListRefreshPlacement(
    previous: SuiteListRefreshViewport,
    newKeys: List<Any>,
): SuiteListRefreshPlacement {
    val oldKeys = previous.keys
    if (
        oldKeys.size <= previous.leadingItems ||
        oldKeys == newKeys ||
        newKeys.size <= previous.leadingItems
    ) {
        return SuiteListRefreshPlacement(index = null)
    }
    val oldHead = oldKeys[previous.leadingItems]
    val newHead = newKeys[previous.leadingItems]
    val wasAtTop =
        previous.firstVisibleItemIndex == 0 && previous.firstVisibleItemScrollOffset == 0
    if (wasAtTop) {
        return SuiteListRefreshPlacement(
            index = 0,
            animateToTop = oldHead != newHead,
        )
    }

    val oldAnchorIndex = previous.firstVisibleItemIndex.coerceIn(oldKeys.indices)
    val newIndexByKey = newKeys.withIndex().associate { (index, key) -> key to index }
    val anchor =
        oldKeys[oldAnchorIndex].takeIf(newIndexByKey::containsKey)
            ?: oldKeys.drop(oldAnchorIndex + 1).firstOrNull(newIndexByKey::containsKey)
            ?: oldKeys.take(oldAnchorIndex).lastOrNull(newIndexByKey::containsKey)
    val targetIndex = anchor?.let(newIndexByKey::get) ?: 0
    val oldKeySet = oldKeys.toSet()
    val addedAboveAnchor = newKeys.take(targetIndex).any { it !in oldKeySet }
    val headReordered =
        oldHead != newHead &&
            oldHead in newIndexByKey &&
            newHead in oldKeySet
    return SuiteListRefreshPlacement(
        index = targetIndex,
        offset = previous.firstVisibleItemScrollOffset,
        showUpdate = addedAboveAnchor || headReordered,
    )
}

/** Connect a list's stable IDs to the shared refresh and viewport behavior. */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod", "ComplexCondition")
@Composable
fun SuiteListRefreshViewportEffect(
    keys: List<Any>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    resetKey: Any? = null,
    leadingItems: Int = 0,
    updatedMessage: String = stringResource(R.string.suite_list_updated),
    viewAction: String = stringResource(R.string.suite_list_view),
) {
    val currentKeys by rememberUpdatedState(keys)
    val effectScope = rememberCoroutineScope()
    var viewport by remember(listState, resetKey) { mutableStateOf<SuiteListRefreshViewport?>(null) }
    var crossedEmptyGeneration by remember(listState, resetKey) { mutableStateOf(false) }

    LaunchedEffect(listState, resetKey) {
        var wasScrolling = false
        snapshotFlow {
            listState.visibleRow() to listState.isScrollInProgress
        }.collect { (visible, isScrolling) ->
            val captured = viewport ?: return@collect
            // Only a scroll may change the remembered viewport. Key retention can move an
            // index before the new key list reaches composition, including after deletion.
            if (
                (isScrolling || wasScrolling) &&
                captured.keys == currentKeys &&
                visible.index in captured.keys.indices &&
                captured.keys[visible.index] == visible.key
            ) {
                viewport =
                    captured.copy(
                        firstVisibleItemIndex = visible.index,
                        firstVisibleItemScrollOffset = visible.offset,
                    )
            }
            wasScrolling = isScrolling
        }
    }

    LaunchedEffect(keys, listState, resetKey) {
        // Paging can briefly publish an empty generation while replacing loaded pages.
        // Keep the last content viewport until its replacement is available.
        if (keys.size <= leadingItems) {
            crossedEmptyGeneration = viewport != null
            return@LaunchedEffect
        }
        val previous = viewport
        if (previous == null) {
            crossedEmptyGeneration = false
            val visible = listState.visibleRow()
            viewport =
                SuiteListRefreshViewport(
                    keys = keys.toList(),
                    firstVisibleItemIndex = visible.index,
                    firstVisibleItemScrollOffset = visible.offset,
                    leadingItems = leadingItems,
                )
            return@LaunchedEffect
        }
        if (previous.keys == keys && !crossedEmptyGeneration) return@LaunchedEffect

        val placement =
            if (previous.keys == keys) {
                SuiteListRefreshPlacement(
                    index = previous.firstVisibleItemIndex,
                    offset = previous.firstVisibleItemScrollOffset,
                )
            } else {
                resolveListRefreshPlacement(previous, keys)
            }
        val mustRestoreAnchor = crossedEmptyGeneration
        crossedEmptyGeneration = false
        val previousAnchorSurvives =
            previous.keys.getOrNull(previous.firstVisibleItemIndex) in keys
        viewport =
            SuiteListRefreshViewport(
                keys = keys.toList(),
                firstVisibleItemIndex = placement.index ?: listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = placement.offset,
                leadingItems = leadingItems,
            )
        if (
            placement.index != null &&
            !placement.animateToTop &&
            (!previousAnchorSurvives || mustRestoreAnchor) &&
            !listState.isScrollInProgress
        ) {
            val targetKey = keys[placement.index]
            effectScope.launch {
                withFrameNanos { }
                val layout = listState.layoutInfo
                val target = layout.visibleItemsInfo.firstOrNull { it.key == targetKey }
                if (target == null) {
                    listState.animateScrollToItem(placement.index, placement.offset)
                } else {
                    val delta = target.offset - layout.viewportStartOffset + placement.offset
                    listState.animateScrollBy(delta.toFloat())
                }
            }
        }
        if (placement.animateToTop && !listState.isScrollInProgress) {
            // Let the new keyed rows measure before animating from the old first row.
            effectScope.launch {
                withFrameNanos { }
                listState.animateScrollToItem(0)
            }
        } else if (placement.showUpdate) {
            effectScope.launch {
                if (
                    snackbarHostState.showSnackbar(
                        message = updatedMessage,
                        actionLabel = viewAction,
                        duration = SnackbarDuration.Long,
                    ) == SnackbarResult.ActionPerformed
                ) {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }
}
