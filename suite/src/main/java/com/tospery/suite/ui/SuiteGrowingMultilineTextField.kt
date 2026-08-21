package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val DEFAULT_MINIMUM_HEIGHT = 180.dp
private val DEFAULT_SCROLL_BAR_WIDTH = 3.dp
private val DEFAULT_SCROLL_BAR_END_PADDING = 4.dp
private val DEFAULT_SCROLL_BAR_VERTICAL_PADDING = 8.dp
private val DEFAULT_SCROLL_BAR_MINIMUM_THUMB_HEIGHT = 32.dp
private val DEFAULT_CONTENT_PADDING =
    PaddingValues(horizontal = 20.dp, vertical = 18.dp)

/**
 * 高度随内容增长、达到父布局上限后在内部滚动的通用多行输入框。
 *
 * 调用方需要通过父布局约束最大可用高度，例如在 [androidx.compose.foundation.layout.Column]
 * 中使用 `weight(fill = false)`。只有内容实际溢出时才显示右侧滚动条。
 */
@Composable
fun SuiteGrowingMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    minimumHeight: Dp = DEFAULT_MINIMUM_HEIGHT,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentPadding: PaddingValues = DEFAULT_CONTENT_PADDING,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier =
            modifier
                .background(containerColor),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minimumHeight)
                    .verticalScroll(scrollState)
                    .padding(contentPadding),
            enabled = enabled,
            textStyle =
                textStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = textStyle,
                        )
                    }
                    innerTextField()
                }
            },
        )

        SuiteVerticalScrollBar(
            scrollValue = scrollState.value,
            scrollMaxValue = scrollState.maxValue,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        end = DEFAULT_SCROLL_BAR_END_PADDING,
                        top = DEFAULT_SCROLL_BAR_VERTICAL_PADDING,
                        bottom = DEFAULT_SCROLL_BAR_VERTICAL_PADDING,
                    ),
        )
    }
}

@Composable
private fun SuiteVerticalScrollBar(
    scrollValue: Int,
    scrollMaxValue: Int,
    modifier: Modifier = Modifier,
) {
    if (scrollMaxValue <= 0) {
        return
    }

    BoxWithConstraints(
        modifier = modifier.width(DEFAULT_SCROLL_BAR_WIDTH),
    ) {
        val density = LocalDensity.current
        val minimumThumbHeightPx =
            with(density) {
                DEFAULT_SCROLL_BAR_MINIMUM_THUMB_HEIGHT.toPx()
            }
        val thumb =
            calculateScrollBarThumb(
                viewportHeightPx = constraints.maxHeight.toFloat(),
                scrollValuePx = scrollValue.toFloat(),
                scrollMaxValuePx = scrollMaxValue.toFloat(),
                minimumThumbHeightPx = minimumThumbHeightPx,
            )

        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            x = 0,
                            y = thumb.offsetPx.roundToInt(),
                        )
                    }
                    .fillMaxWidth()
                    .height(with(density) { thumb.heightPx.toDp() })
                    .clip(RoundedCornerShape(DEFAULT_SCROLL_BAR_WIDTH))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.48f,
                        ),
                    ),
        )
    }
}

internal data class SuiteScrollBarThumb(
    val heightPx: Float,
    val offsetPx: Float,
)

/**
 * 根据视口和内容滚动范围计算滚动条滑块，保持算法与 Compose 测量逻辑解耦以便单元测试。
 */
internal fun calculateScrollBarThumb(
    viewportHeightPx: Float,
    scrollValuePx: Float,
    scrollMaxValuePx: Float,
    minimumThumbHeightPx: Float,
): SuiteScrollBarThumb {
    if (viewportHeightPx <= 0f || scrollMaxValuePx <= 0f) {
        return SuiteScrollBarThumb(heightPx = 0f, offsetPx = 0f)
    }

    val contentHeightPx = viewportHeightPx + scrollMaxValuePx
    val thumbHeightPx =
        (viewportHeightPx * viewportHeightPx / contentHeightPx)
            .coerceAtLeast(minimumThumbHeightPx)
            .coerceAtMost(viewportHeightPx)
    val scrollFraction =
        (scrollValuePx / scrollMaxValuePx).coerceIn(0f, 1f)
    val thumbOffsetPx =
        (viewportHeightPx - thumbHeightPx) * scrollFraction

    return SuiteScrollBarThumb(
        heightPx = thumbHeightPx,
        offsetPx = thumbOffsetPx,
    )
}
