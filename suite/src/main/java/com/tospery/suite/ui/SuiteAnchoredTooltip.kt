package com.tospery.suite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private val SuiteTooltipHorizontalMargin = 8.dp
private val SuiteTooltipContentPadding = 12.dp
private val SuiteTooltipCornerRadius = 14.dp
private val SuiteTooltipCaretWidth = 16.dp
private val SuiteTooltipCaretHeight = 8.dp

/** 气泡默认显示时长，之后渐隐消失。 */
const val SUITE_TOOLTIP_AUTO_DISMISS_MILLIS = 5_000L

/** 气泡自动消失时的渐隐时长。 */
const val SUITE_TOOLTIP_FADE_OUT_MILLIS = 300

/**
 * 在本地布局坐标中的锚点附近展示的通用提示气泡。
 *
 * [anchorBounds] 必须相对调用方提供的布局区域。组件会根据可用空间自动选择上方或下方，
 * 并限制气泡不超出左右边界。点击气泡外区域时调用 [onDismissRequest]；调用方决定显示状态
 * 和文案，组件不包含业务语义。[caretTipOffset] 可使尖端在锚点内水平偏移，用于贴边目标
 * 的短斜箭头效果；正值向右，负值向左。[caretCornerInset] 可缩短贴边箭头的斜边；未传入时
 * 仍完整避开气泡圆角。
 *
 * 气泡显示 [autoDismissMillis] 毫秒后用 [SUITE_TOOLTIP_FADE_OUT_MILLIS] 毫秒渐隐，渐隐结束时调用
 * [onDismissRequest]；锚点变化（例如点了另一个目标）时重新计时。传 `null` 时不自动消失。
 */
@Composable
fun SuiteAnchoredTooltip(
    anchorBounds: IntRect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    caretTipOffset: Dp = 0.dp,
    caretCornerInset: Dp? = null,
    autoDismissMillis: Long? = SUITE_TOOLTIP_AUTO_DISMISS_MILLIS,
    content: @Composable () -> Unit,
) {
    val anchor = anchorBounds ?: return
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val visibility = remember(anchor) { MutableTransitionState(true) }
    LaunchedEffect(anchor, autoDismissMillis) {
        val millis = autoDismissMillis ?: return@LaunchedEffect
        delay(millis)
        visibility.targetState = false
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) {
            currentOnDismissRequest()
        }
    }
    val tooltipColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipContentColor = MaterialTheme.colorScheme.inverseOnSurface

    BoxWithConstraints(modifier = modifier) {
        var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val containerWidth = with(density) { maxWidth.roundToPx() }
        val caretWidth = with(density) { SuiteTooltipCaretWidth.roundToPx() }
        val caretHeight = with(density) { SuiteTooltipCaretHeight.roundToPx() }
        val horizontalMargin = with(density) { SuiteTooltipHorizontalMargin.roundToPx() }
        val caretCornerInsetPx =
            with(density) { (caretCornerInset ?: SuiteTooltipCornerRadius).roundToPx() }
        val canShowAbove = anchor.top >= tooltipSize.height + caretHeight
        val rawTooltipLeft = anchor.center.x - (tooltipSize.width / 2)
        val tooltipLeft =
            rawTooltipLeft.coerceIn(
                minimumValue = horizontalMargin,
                maximumValue = max(horizontalMargin, containerWidth - horizontalMargin - tooltipSize.width),
            )
        val tooltipTop =
            if (canShowAbove) {
                anchor.top - tooltipSize.height - caretHeight
            } else {
                anchor.bottom + caretHeight
            }
        val caretTipCenterX =
            anchor.center.x + with(density) { caretTipOffset.roundToPx() }
        val rawCaretLeft = caretTipCenterX - (caretWidth / 2)
        // 气泡贴边时，底边默认避开圆角；边缘调用方可缩小避让距离以保持短斜箭头。
        // 尖端默认对准锚点中心，也可在锚点内偏移。
        // 否则最后一列等靠近边界的目标会与前一列共用被截断的箭头位置。
        val caretBaseLeft =
            rawCaretLeft.coerceIn(
                minimumValue = tooltipLeft + caretCornerInsetPx,
                maximumValue =
                    tooltipLeft +
                        max(
                            caretCornerInsetPx,
                            tooltipSize.width - caretCornerInsetPx - caretWidth,
                        ),
            )
        val caretCanvasLeft = min(rawCaretLeft, caretBaseLeft)
        val caretCanvasWidth =
            max(rawCaretLeft + caretWidth, caretBaseLeft + caretWidth) - caretCanvasLeft
        val caretBaseStart = caretBaseLeft - caretCanvasLeft
        val caretBaseEnd = caretBaseStart + caretWidth
        val caretTipX = caretTipCenterX - caretCanvasLeft
        val caretTop =
            if (canShowAbove) {
                tooltipTop + tooltipSize.height
            } else {
                tooltipTop - caretHeight
            }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tooltipLeft, tooltipTop, tooltipSize, onDismissRequest) {
                            detectTapGestures { tapOffset ->
                                val tappedTooltip =
                                    tapOffset.x in
                                        tooltipLeft.toFloat()..
                                        (tooltipLeft + tooltipSize.width).toFloat() &&
                                        tapOffset.y in
                                        tooltipTop.toFloat()..
                                        (tooltipTop + tooltipSize.height).toFloat()
                                if (!tappedTooltip) {
                                    onDismissRequest()
                                }
                            }
                        },
            )
            AnimatedVisibility(
                visibleState = visibility,
                enter = EnterTransition.None,
                exit = fadeOut(tween(SUITE_TOOLTIP_FADE_OUT_MILLIS)),
            ) {
                SuiteTooltipBubble(
                    tooltipColor = tooltipColor,
                    tooltipContentColor = tooltipContentColor,
                    tooltipOffset = IntOffset(tooltipLeft, tooltipTop),
                    onTooltipSizeChanged = { tooltipSize = it },
                    caretOffset = IntOffset(caretCanvasLeft, caretTop),
                    caretCanvasWidth = with(density) { caretCanvasWidth.toDp() },
                    caretPointsDown = canShowAbove,
                    caretBaseStart = caretBaseStart.toFloat(),
                    caretBaseEnd = caretBaseEnd.toFloat(),
                    caretTipX = caretTipX.toFloat(),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SuiteTooltipBubble(
    tooltipColor: androidx.compose.ui.graphics.Color,
    tooltipContentColor: androidx.compose.ui.graphics.Color,
    tooltipOffset: IntOffset,
    onTooltipSizeChanged: (IntSize) -> Unit,
    caretOffset: IntOffset,
    caretCanvasWidth: Dp,
    caretPointsDown: Boolean,
    caretBaseStart: Float,
    caretBaseEnd: Float,
    caretTipX: Float,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = tooltipColor,
            contentColor = tooltipContentColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SuiteTooltipCornerRadius),
            modifier =
                Modifier
                    .offset { tooltipOffset }
                    .onSizeChanged(onTooltipSizeChanged)
                    .shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(SuiteTooltipCornerRadius)),
        ) {
            Box(modifier = Modifier.padding(SuiteTooltipContentPadding)) {
                content()
            }
        }
        Canvas(
            modifier =
                Modifier
                    .offset { caretOffset }
                    .size(
                        width = caretCanvasWidth,
                        height = SuiteTooltipCaretHeight,
                    ),
        ) {
            val path = Path()
            if (caretPointsDown) {
                path.moveTo(caretBaseStart, 0f)
                path.lineTo(caretBaseEnd, 0f)
                path.lineTo(caretTipX, size.height)
            } else {
                path.moveTo(caretBaseStart, size.height)
                path.lineTo(caretBaseEnd, size.height)
                path.lineTo(caretTipX, 0f)
            }
            path.close()
            drawPath(path = path, color = tooltipColor, style = Fill)
        }
    }
}
