package com.tospery.suite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 确认操作的语义样式。
 *
 * [DESTRUCTIVE] 用于清除、删除等不可逆或需要额外提醒的操作；具体业务含义仍由调用方定义。
 */
enum class ConfirmActionStyle {
    PRIMARY,
    DESTRUCTIVE,
}

/**
 * 跨业务通用的二次确认弹窗。
 *
 * 文案与确认操作的语义由业务调用方提供，组件只负责统一的 Android 平台交互与视觉层级。
 * [supportingContent] 用于在说明下方放置调用方拥有语义的补充内容。
 * 自定义布局确保标题、说明和操作区的对齐方式不会随平台默认 [androidx.compose.material3.AlertDialog] 改变。
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmActionStyle: ConfirmActionStyle = ConfirmActionStyle.PRIMARY,
    messageMaxVisibleLines: Int? = null,
    dismissible: Boolean = true,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = {
            if (dismissible) onDismiss()
        },
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        ConfirmActionDialogContent(
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmActionStyle = confirmActionStyle,
            messageMaxVisibleLines = messageMaxVisibleLines,
            modifier = Modifier.padding(horizontal = 24.dp),
            confirmEnabled = confirmEnabled,
            dismissEnabled = dismissEnabled,
            supportingContent = supportingContent,
        )
    }
}

/**
 * 确认弹窗的纯内容，可由 Navigation Compose 的 Dialog destination 直接承载。
 */
@Composable
fun ConfirmActionDialogContent(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmActionStyle: ConfirmActionStyle = ConfirmActionStyle.PRIMARY,
    messageMaxVisibleLines: Int? = null,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    require(messageMaxVisibleLines == null || messageMaxVisibleLines > 0) {
        "messageMaxVisibleLines 必须大于 0。"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 操作按钮按对话框宽度计算，避免文案长度改变视觉重心。
            val actionWidth = maxWidth / 3
            val layoutDirection = LocalLayoutDirection.current
            val actionLabelMaxWidth =
                actionWidth -
                    ButtonDefaults.ContentPadding.calculateStartPadding(layoutDirection) -
                    ButtonDefaults.ContentPadding.calculateEndPadding(layoutDirection)
            val actionLabelMaxWidthPx =
                with(LocalDensity.current) {
                    actionLabelMaxWidth.roundToPx().coerceAtLeast(0)
                }
            val actionLabelStyle =
                rememberActionLabelStyle(
                    texts = listOfNotNull(dismissText, confirmText),
                    maxWidthPx = actionLabelMaxWidthPx,
                    baseStyle = MaterialTheme.typography.labelLarge,
                    textMeasurer = rememberTextMeasurer(),
                )

            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                )

                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    val messageStyle = MaterialTheme.typography.bodyMedium
                    val messageModifier =
                        if (messageMaxVisibleLines == null) {
                            Modifier
                        } else {
                            // 只限制可见视口，完整消息仍可在弹窗内部滚动阅读。
                            val maxMessageHeight =
                                with(LocalDensity.current) {
                                    messageStyle.lineHeight.toDp() * messageMaxVisibleLines
                                }
                            Modifier
                                .heightIn(max = maxMessageHeight)
                                .verticalScroll(rememberScrollState())
                        }
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().then(messageModifier),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = messageStyle,
                    )
                }

                supportingContent?.let { content ->
                    // 补充内容保留调用方语义；组件只统一它在说明与操作区之间的位置。
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }

                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        if (dismissText == null) {
                            Arrangement.Center
                        } else {
                            Arrangement.SpaceBetween
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissText?.let { text ->
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = dismissEnabled,
                            modifier = Modifier.width(actionWidth),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = actionLabelStyle,
                            )
                        }
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = Modifier.width(actionWidth),
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            when (confirmActionStyle) {
                                ConfirmActionStyle.PRIMARY -> ButtonDefaults.buttonColors()
                                ConfirmActionStyle.DESTRUCTIVE ->
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    )
                            },
                    ) {
                        Text(
                            text = confirmText,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = actionLabelStyle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberActionLabelStyle(
    texts: List<String>,
    maxWidthPx: Int,
    baseStyle: TextStyle,
    textMeasurer: TextMeasurer,
): TextStyle =
    remember(texts, maxWidthPx, baseStyle, textMeasurer) {
        val fontSize =
            actionLabelFontSizes.firstOrNull { candidate ->
                texts.all { text ->
                    !textMeasurer
                        .measure(
                            text = AnnotatedString(text),
                            style = baseStyle.copy(fontSize = candidate),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            constraints = Constraints(maxWidth = maxWidthPx),
                        ).hasVisualOverflow
                }
            } ?: actionLabelFontSizes.last()

        // 双按钮共用同一字号，避免仅长文案缩小后破坏操作区的视觉平衡。
        baseStyle.copy(fontSize = fontSize)
    }

private val actionLabelFontSizes = listOf(14.sp, 13.sp, 12.sp)
