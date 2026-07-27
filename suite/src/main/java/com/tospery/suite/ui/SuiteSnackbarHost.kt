package com.tospery.suite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/** App 统一的轻提示容器，确保错误文案不会超过三行。 */
@Composable
fun SuiteSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { snackbarData ->
        Snackbar(
            action =
                snackbarData.visuals.actionLabel?.let { actionLabel ->
                    {
                        TextButton(onClick = snackbarData::performAction) {
                            Text(
                                text = actionLabel,
                                maxLines = SNACKBAR_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            dismissAction =
                if (snackbarData.visuals.withDismissAction) {
                    {
                        IconButton(onClick = snackbarData::dismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                            )
                        }
                    }
                } else {
                    null
                },
        ) {
            Text(
                text = snackbarData.visuals.message,
                maxLines = SNACKBAR_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SNACKBAR_MAX_LINES = 3
