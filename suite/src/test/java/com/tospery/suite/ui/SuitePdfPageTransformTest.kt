package com.tospery.suite.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuitePdfPageTransformTest {
    @Test
    fun `render width keeps original resolution and quantizes zoomed resolution`() {
        assertEquals(1_000, 1_000.scaledForPdfRender(scale = 1f))
        assertEquals(1_280, 1_000.scaledForPdfRender(scale = 1.1f))
    }

    @Test
    fun `zoom is clamped and reset clears pan offset`() {
        val viewportSize = IntSize(width = 1_000, height = 2_000)
        val zoomed =
            SuitePdfPageTransform()
                .applyGesture(
                    zoomChange = 10f,
                    panChange = Offset(400f, 600f),
                    viewportSize = viewportSize,
                )

        assertEquals(4f, zoomed.scale)
        assertEquals(Offset(400f, 600f), zoomed.offset)

        val reset =
            zoomed.applyGesture(
                zoomChange = 0.01f,
                panChange = Offset(100f, 100f),
                viewportSize = viewportSize,
            )

        assertEquals(1f, reset.scale)
        assertEquals(Offset.Zero, reset.offset)
    }

    @Test
    fun `pan is clamped to scaled page bounds`() {
        val transformed =
            SuitePdfPageTransform()
                .applyGesture(
                    zoomChange = 2f,
                    panChange = Offset(2_000f, -3_000f),
                    viewportSize = IntSize(width = 1_000, height = 2_000),
                )

        assertEquals(Offset(500f, -1_000f), transformed.offset)
    }

    @Test
    fun `pan is disabled at original scale and released at zoomed edge`() {
        val viewportSize = IntSize(width = 1_000, height = 2_000)
        assertFalse(
            SuitePdfPageTransform().canPan(
                panChange = Offset(0f, 10f),
                viewportSize = viewportSize,
            ),
        )

        val atBottomEdge =
            SuitePdfPageTransform(
                scale = 2f,
                offset = Offset(0f, 1_000f),
            )
        assertFalse(
            atBottomEdge.canPan(
                panChange = Offset(0f, 10f),
                viewportSize = viewportSize,
            ),
        )
        assertTrue(
            atBottomEdge.canPan(
                panChange = Offset(0f, -10f),
                viewportSize = viewportSize,
            ),
        )
    }
}
