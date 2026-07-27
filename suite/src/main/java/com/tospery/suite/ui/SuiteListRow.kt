package com.tospery.suite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SuiteListRowContentPadding =
    PaddingValues(horizontal = 20.dp, vertical = 8.dp)
private val SuiteListRowMinHeight = 56.dp
private val SuiteListRowTrailingTextSpacing = 16.dp
private val SuiteListRowTrailingTextMinWidth = 24.dp
private val SuiteListRowTrailingTextMinFontSize = 12.sp
private val SuiteListRowTrailingTextStepSize = 1.sp

/**
 * 面向设置、资料等列表场景的无业务通用行。
 *
 * [supportingText] 用于标题下方的辅助说明；
 * [trailingText] 适合“标题 + 可省略的右侧值 + 箭头”等常见资料行。标题会优先占用其完整显示
 * 所需的宽度；右侧值会在剩余空间内先自动缩小字号，达到最小字号后仍无法完整显示才使用省略号；
 * [trailingContent] 则用于图标、开关等自定义尾部内容。
 */
@Composable
fun SuiteListRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleTextAlign: TextAlign = TextAlign.Start,
    titleWeight: Float? = null,
    supportingText: String? = null,
    supportingTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingText: String? = null,
    trailingTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    trailingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = SuiteListRowContentPadding,
    showDivider: Boolean = true,
) {
    val layoutDirection = LocalLayoutDirection.current
    val rowModifier =
        if (onClick == null) {
            modifier
        } else {
            modifier.clickable(onClick = onClick)
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                rowModifier
                    .fillMaxWidth()
                    .heightIn(min = SuiteListRowMinHeight)
                    .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.let { content ->
                content()
                Spacer(modifier = Modifier.width(14.dp))
            }
            SuiteListRowTitleAndTrailingText(
                title = title,
                titleStyle = titleStyle,
                titleColor = titleColor,
                titleTextAlign = titleTextAlign,
                titleWeight = titleWeight,
                supportingText = supportingText,
                supportingTextStyle = supportingTextStyle,
                supportingTextColor = supportingTextColor,
                trailingText = trailingText,
                trailingTextStyle = trailingTextStyle,
                trailingTextColor = trailingTextColor,
                modifier = Modifier.weight(1f),
            )
            trailingContent?.let { content ->
                Spacer(modifier = Modifier.width(8.dp))
                content()
            }
        }
        if (showDivider) {
            OnePixelHorizontalDivider(
                modifier =
                    Modifier.padding(
                        start = contentPadding.calculateLeftPadding(layoutDirection),
                    ),
            )
        }
    }
}

@Composable
private fun SuiteListRowTitleAndTrailingText(
    title: String,
    titleStyle: TextStyle,
    titleColor: Color,
    titleTextAlign: TextAlign,
    titleWeight: Float?,
    supportingText: String?,
    supportingTextStyle: TextStyle,
    supportingTextColor: Color,
    trailingText: String?,
    trailingTextStyle: TextStyle,
    trailingTextColor: Color,
    modifier: Modifier = Modifier,
) {
    if (trailingText == null) {
        SuiteListRowTitleContent(
            title = title,
            titleStyle = titleStyle,
            titleColor = titleColor,
            titleTextAlign = titleTextAlign,
            supportingText = supportingText,
            supportingTextStyle = supportingTextStyle,
            supportingTextColor = supportingTextColor,
            modifier = modifier.fillMaxWidth(),
            expandToMaxWidth = true,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val titleMaxWidth =
            suiteListRowTitleMaxWidth(
                availableWidth = maxWidth,
                titleWeight = titleWeight,
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuiteListRowTitleContent(
                title = title,
                titleStyle = titleStyle,
                titleColor = titleColor,
                titleTextAlign = titleTextAlign,
                supportingText = supportingText,
                supportingTextStyle = supportingTextStyle,
                supportingTextColor = supportingTextColor,
                modifier = Modifier.widthIn(max = titleMaxWidth),
                expandToMaxWidth = false,
            )
            Spacer(modifier = Modifier.width(SuiteListRowTrailingTextSpacing))
            Text(
                text = trailingText,
                modifier = Modifier.weight(1f),
                color = trailingTextColor,
                autoSize = trailingTextStyle.suiteListRowTrailingTextAutoSize(),
                style = trailingTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SuiteListRowTitleContent(
    title: String,
    titleStyle: TextStyle,
    titleColor: Color,
    titleTextAlign: TextAlign,
    supportingText: String?,
    supportingTextStyle: TextStyle,
    supportingTextColor: Color,
    modifier: Modifier = Modifier,
    expandToMaxWidth: Boolean,
) {
    val textModifier = if (expandToMaxWidth) Modifier.fillMaxWidth() else Modifier

    Column(modifier = modifier) {
        Text(
            text = title,
            modifier = textModifier,
            color = titleColor,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = titleTextAlign,
        )

        supportingText?.let { value ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                modifier = textModifier,
                color = supportingTextColor,
                style = supportingTextStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = titleTextAlign,
            )
        }
    }
}

private fun suiteListRowTitleMaxWidth(
    availableWidth: Dp,
    titleWeight: Float?,
): Dp {
    val maxTitleWidth =
        maxOf(
            0.dp,
            availableWidth - SuiteListRowTrailingTextSpacing - SuiteListRowTrailingTextMinWidth,
        )
    val requestedTitleWidth =
        titleWeight?.let { weight ->
            require(weight > 0f) { "titleWeight must be greater than zero." }
            availableWidth * (weight / (weight + 1f))
        }

    return requestedTitleWidth?.let { minOf(it, maxTitleWidth) } ?: maxTitleWidth
}

private fun TextStyle.suiteListRowTrailingTextAutoSize(): TextAutoSize? {
    val maxFontSize: TextUnit = fontSize
    if (!maxFontSize.isSp || maxFontSize <= SuiteListRowTrailingTextMinFontSize) {
        return null
    }

    return TextAutoSize.StepBased(
        minFontSize = SuiteListRowTrailingTextMinFontSize,
        maxFontSize = maxFontSize,
        stepSize = SuiteListRowTrailingTextStepSize,
    )
}
