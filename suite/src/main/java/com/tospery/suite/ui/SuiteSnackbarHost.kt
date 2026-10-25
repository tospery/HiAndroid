package com.tospery.suite.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tospery.suite.R

/** 通用轻提示：纯文案单行随内容收缩，多行与带操作的提示占满安全边距内的宽度。 */
@Composable
fun SuiteSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { snackbarData ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SNACKBAR_SIDE_INSET),
            contentAlignment = Alignment.Center,
        ) {
            val message = snackbarData.visuals.message
            val messageStyle = MaterialTheme.typography.bodyMedium
            val textMeasurer = rememberTextMeasurer()
            val contentInsetPx = with(LocalDensity.current) { (SNACKBAR_CONTENT_INSET * 2).roundToPx() }
            val maxTextWidth = (constraints.maxWidth - contentInsetPx).coerceAtLeast(1)
            // 在完整可用宽度下测量实际折行，避免先收缩再测量导致布局反复切换。
            val lineCount =
                textMeasurer.measure(
                    text = AnnotatedString(message),
                    style = messageStyle,
                    constraints = Constraints(maxWidth = maxTextWidth),
                    maxLines = SNACKBAR_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                ).lineCount
            val actionLabel = snackbarData.visuals.actionLabel
            val actionMaxWidth = maxWidth / 3
            val hasDismissAction = snackbarData.visuals.withDismissAction
            val fullWidth = lineCount > 1 || actionLabel != null || hasDismissAction

            Surface(
                modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = SNACKBAR_CONTENT_INSET, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        modifier = if (fullWidth) Modifier.weight(1f) else Modifier,
                        style = messageStyle,
                        textAlign = if (fullWidth) TextAlign.Start else TextAlign.Center,
                        maxLines = SNACKBAR_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    actionLabel?.let { label ->
                        TextButton(
                            onClick = snackbarData::performAction,
                            modifier = Modifier.widthIn(max = actionMaxWidth),
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.inversePrimary,
                                ),
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasDismissAction) {
                        IconButton(
                            onClick = snackbarData::dismiss,
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.suite_snackbar_dismiss),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SNACKBAR_SIDE_INSET = 16.dp
private val SNACKBAR_CONTENT_INSET = 16.dp
private const val SNACKBAR_MAX_LINES = 5
