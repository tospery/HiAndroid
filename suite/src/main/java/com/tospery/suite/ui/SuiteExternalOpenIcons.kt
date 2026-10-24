package com.tospery.suite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.ui.graphics.vector.ImageVector

/** 外开动作的统一视觉语义；所有宿主与功能入口共用。 */
object SuiteExternalOpenIcons {
    val Browser: ImageVector = Icons.Outlined.OpenInBrowser
    val ExternalApp: ImageVector = Icons.AutoMirrored.Outlined.OpenInNew
}
