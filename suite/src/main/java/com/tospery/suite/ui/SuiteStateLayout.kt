package com.tospery.suite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SuiteStateLayout(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    descriptionMaxLines: Int = Int.MAX_VALUE,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon?.let { content ->
                content()
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )

            description
                ?.takeIf(String::isNotBlank)
                ?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        maxLines = descriptionMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            action?.let { content ->
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}
