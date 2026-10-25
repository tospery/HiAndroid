package com.tospery.suite.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal data class SuitePdfPageTransform(
    val scale: Float = MIN_PDF_PAGE_SCALE,
    val offset: Offset = Offset.Zero,
) {
    fun applyGesture(
        zoomChange: Float,
        panChange: Offset,
        viewportSize: IntSize,
    ): SuitePdfPageTransform {
        val updatedScale =
            (scale * zoomChange)
                .coerceIn(MIN_PDF_PAGE_SCALE, MAX_PDF_PAGE_SCALE)
        if (updatedScale == MIN_PDF_PAGE_SCALE) {
            return SuitePdfPageTransform()
        }
        return copy(
            scale = updatedScale,
            offset =
                (offset + panChange).coerceToPdfPageBounds(
                    viewportSize = viewportSize,
                    scale = updatedScale,
                ),
        )
    }

    fun constrainedTo(viewportSize: IntSize): SuitePdfPageTransform =
        copy(
            offset =
                offset.coerceToPdfPageBounds(
                    viewportSize = viewportSize,
                    scale = scale,
                ),
        )

    fun canPan(
        panChange: Offset,
        viewportSize: IntSize,
    ): Boolean {
        if (scale <= MIN_PDF_PAGE_SCALE || viewportSize == IntSize.Zero) return false
        val maxOffset = viewportSize.maxPdfPageOffset(scale)
        val canPanHorizontally =
            (panChange.x > 0f && offset.x < maxOffset.x) ||
                (panChange.x < 0f && offset.x > -maxOffset.x)
        val canPanVertically =
            (panChange.y > 0f && offset.y < maxOffset.y) ||
                (panChange.y < 0f && offset.y > -maxOffset.y)
        return canPanHorizontally || canPanVertically
    }
}

internal fun Int.scaledForPdfRender(scale: Float): Int {
    if (scale <= MIN_PDF_PAGE_SCALE) return this
    val desiredWidth = (this * scale).roundToInt().coerceAtLeast(this)
    return desiredWidth.roundUpToMultiple(PDF_RENDER_WIDTH_STEP_PX)
}

private fun Int.roundUpToMultiple(step: Int): Int = ((this + step - 1) / step) * step

private fun Offset.coerceToPdfPageBounds(
    viewportSize: IntSize,
    scale: Float,
): Offset {
    val maxOffset = viewportSize.maxPdfPageOffset(scale)
    return Offset(
        x = x.coerceIn(-maxOffset.x, maxOffset.x),
        y = y.coerceIn(-maxOffset.y, maxOffset.y),
    )
}

private fun IntSize.maxPdfPageOffset(scale: Float): Offset {
    val scaleOverflow = (scale - MIN_PDF_PAGE_SCALE).coerceAtLeast(0f)
    return Offset(
        x = width * scaleOverflow / 2f,
        y = height * scaleOverflow / 2f,
    )
}

private const val MIN_PDF_PAGE_SCALE = 1f
private const val MAX_PDF_PAGE_SCALE = 4f
private const val PDF_RENDER_WIDTH_STEP_PX = 256
