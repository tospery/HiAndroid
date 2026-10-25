package com.tospery.suite.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteListRefreshViewportTest {
    @Test
    fun newFirstRowScrollsToTopWhenOldListWasAtTop() {
        val placement =
            resolveListRefreshPlacement(
                viewport(listOf("row1", "row2"), index = 0),
                listOf("new", "row1", "row2"),
            )

        assertEquals(0, placement.index)
        assertTrue(placement.animateToTop)
        assertFalse(placement.showUpdate)
    }

    @Test
    fun removedTopVisibleRowUsesNextSurvivor() {
        val placement =
            resolveListRefreshPlacement(
                viewport(listOf("row1", "row2", "row3", "row4", "row5"), index = 2, offset = 18),
                listOf("new", "row1", "row2", "row4", "row5"),
            )

        assertEquals(3, placement.index)
        assertEquals(18, placement.offset)
        assertTrue(placement.showUpdate)
    }

    @Test
    fun removedLowerVisibleRowKeepsTopVisibleRow() {
        val placement =
            resolveListRefreshPlacement(
                viewport(listOf("row1", "row2", "row3", "row4", "row5"), index = 2),
                listOf("row1", "row2", "row3", "row5"),
            )

        assertEquals(2, placement.index)
        assertFalse(placement.showUpdate)
    }

    @Test
    fun removedAnchorFallsBackToPredecessorWhenNoSuccessorSurvives() {
        val placement =
            resolveListRefreshPlacement(
                viewport(listOf("row1", "row2", "row3"), index = 2),
                listOf("row1", "row2"),
            )

        assertEquals(1, placement.index)
        assertFalse(placement.showUpdate)
    }

    @Test
    fun newRowAboveScrolledAnchorOffersUpdateWithoutMovingIt() {
        val placement =
            resolveListRefreshPlacement(
                viewport(listOf("row1", "row2", "row3"), index = 2, offset = 11),
                listOf("new", "row1", "row2", "row3"),
            )

        assertEquals(3, placement.index)
        assertEquals(11, placement.offset)
        assertFalse(placement.animateToTop)
        assertTrue(placement.showUpdate)
    }

    @Test
    fun appendAndFirstLoadDoNotOfferUpdate() {
        assertFalse(
            resolveListRefreshPlacement(viewport(listOf("row1"), 0), listOf("row1", "row2"))
                .showUpdate,
        )
        assertEquals(
            null,
            resolveListRefreshPlacement(viewport(emptyList(), 0), listOf("row1")).index,
        )
    }

    @Test
    fun staticHeadersDoNotHideAChangedFirstDataRow() {
        val placement =
            resolveListRefreshPlacement(
                SuiteListRefreshViewport(
                    keys = listOf("header", "gap", "old"),
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                    leadingItems = 2,
                ),
                listOf("header", "gap", "new", "old"),
            )

        assertTrue(placement.animateToTop)
    }

    private fun viewport(
        keys: List<Any>,
        index: Int,
        offset: Int = 0,
    ) = SuiteListRefreshViewport(keys, index, offset)
}
