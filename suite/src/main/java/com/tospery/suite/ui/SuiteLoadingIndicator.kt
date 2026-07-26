package com.tospery.suite.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用的不定加载指示器。
 *
 * 固定弧长配合恒速旋转，为 AppBar、页面和 Row 等场景提供一致的动画节奏。动画值只在绘制阶段读取，
 * 每帧仅使 Canvas 重绘，不会触发调用方组合树重组。
 */
@Composable
fun SuiteLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = DEFAULT_STROKE_WIDTH,
    trackColor: Color = Color.Transparent,
) {
    val transition = rememberInfiniteTransition(label = "suite_loading_indicator")
    val rotation =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_ROTATION_DEGREES,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = ROTATION_DURATION_MILLIS,
                            easing = LinearEasing,
                        ),
                ),
            label = "suite_loading_indicator_rotation",
        )

    Canvas(
        modifier =
            modifier
                .progressSemantics()
                .size(DEFAULT_INDICATOR_SIZE),
    ) {
        val strokeWidthPx = strokeWidth.toPx()
        val diameter = minOf(size.width, size.height) - strokeWidthPx
        if (diameter <= 0f) return@Canvas

        val topLeft =
            Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
        val arcSize = Size(width = diameter, height = diameter)
        val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

        if (trackColor.alpha > 0f) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = FULL_ROTATION_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        drawArc(
            color = color,
            startAngle = rotation.value - QUARTER_ROTATION_DEGREES,
            sweepAngle = INDICATOR_SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

private val DEFAULT_INDICATOR_SIZE = 40.dp
private val DEFAULT_STROKE_WIDTH = 4.dp
private const val ROTATION_DURATION_MILLIS = 800
private const val FULL_ROTATION_DEGREES = 360f
private const val QUARTER_ROTATION_DEGREES = 90f
private const val INDICATOR_SWEEP_DEGREES = 270f
