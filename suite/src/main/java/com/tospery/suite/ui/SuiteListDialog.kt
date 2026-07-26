package com.tospery.suite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tospery.suite.R

/**
 * 带标题栏与可滚动列表的通用弹窗。
 *
 * 调用方通过 [content] 提供无业务含义的列表项；组件统一负责弹窗尺寸、关闭入口和滚动边界。
 */
@Composable
fun SuiteListDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SuiteListDialogContent(
            title = title,
            closeContentDescription = stringResource(R.string.suite_close_dialog),
            onCloseClick = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * [SuiteListDialog] 的纯内容，可由 Navigation Compose 的 Dialog destination 直接承载。
 */
@Composable
fun SuiteListDialogContent(
    title: String,
    closeContentDescription: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val dialogWidth =
            (maxWidth * DialogWidthFraction)
                .coerceIn(DialogMinWidth, DialogMaxWidth)

        Surface(
            modifier =
                modifier
                    .width(dialogWidth)
                    .heightIn(max = maxHeight * DialogMaxHeightFraction),
            shape = RoundedCornerShape(DialogCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = DialogTonalElevation,
        ) {
            Column {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(DialogHeaderHeight),
                ) {
                    Text(
                        text = title,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = DialogHeaderSideClearance),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(
                        onClick = onCloseClick,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = DialogCloseButtonEndPadding),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = closeContentDescription,
                        )
                    }
                }

                OnePixelHorizontalDivider()

                /*
                 * fill = false 让短列表按内容收起；weight 同时为长列表提供有限高度，
                 * 避免 LazyColumn 收到无限高度约束。
                 */
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(weight = 1f, fill = false),
                    content = content,
                )
            }
        }
    }
}

private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp
private val DialogCornerRadius = 28.dp
private val DialogHeaderHeight = 64.dp
private val DialogHeaderSideClearance = 64.dp
private val DialogCloseButtonEndPadding = 8.dp
private val DialogTonalElevation = 6.dp
private const val DialogWidthFraction = 0.8f
private const val DialogMaxHeightFraction = 0.72f
