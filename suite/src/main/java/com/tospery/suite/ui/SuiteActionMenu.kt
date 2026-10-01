package com.tospery.suite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** 操作菜单的固定高对比配色，亮暗主题均保持近黑底、白字。 */
object SuiteActionMenuDefaults {
    val containerColor = Color(0xFF333333)
    val contentColor = Color.White
    val cornerRadius = 24.dp
}

/**
 * 锚定在父布局（通常是包住 IconButton 的 Box）上的纯文字气泡菜单。
 *
 * 内容顺序、文案与事件由调用方提供；组件处理屏幕边界、箭头定位、滚动与关闭。
 * [SuiteActionMenuItem] 会先关闭菜单再执行事件，禁用项不会触发关闭或回调。
 */
@Composable
fun SuiteActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var pointer by remember { mutableStateOf(MenuPointer()) }
    val positionProvider = remember(density) {
        ActionMenuPositionProvider(
            margin = with(density) { MenuScreenMargin.roundToPx() },
            gap = with(density) { MenuAnchorGap.roundToPx() },
            onPosition = { pointer = it },
        )
    }
    val maxWidth = with(density) { windowSize.width.toDp() - MenuScreenMargin * 2 }
        .coerceIn(1.dp, MenuMaxWidth)
    val maxHeight = with(density) {
        minOf(windowSize.height - MenuScreenMargin.roundToPx() * 2, pointer.availableHeight).toDp()
    }
        .coerceAtLeast(1.dp)
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = MenuMinWidth.coerceAtMost(maxWidth), max = maxWidth)
                .width(IntrinsicSize.Max)
                .heightIn(max = maxHeight),
            shape = ActionMenuShape(pointer),
            color = SuiteActionMenuDefaults.containerColor,
            contentColor = SuiteActionMenuDefaults.contentColor,
            shadowElevation = 8.dp,
        ) {
            CompositionLocalProvider(LocalMenuDismiss provides onDismissRequest) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = MenuContentPadding + if (pointer.aboveAnchor) 0.dp else MenuPointerHeight,
                            bottom = MenuContentPadding + if (pointer.aboveAnchor) MenuPointerHeight else 0.dp,
                        )
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

/** 菜单项不包含图标；保留至少 48dp 触控高度，长文案随字号换行。 */
@Composable
fun SuiteActionMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dismiss = LocalMenuDismiss.current
    Text(
        text = label,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button) {
                dismiss()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        color = SuiteActionMenuDefaults.contentColor.copy(alpha = if (enabled) 1f else 0.38f),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private data class MenuPointer(
    val x: Float = 0f,
    val aboveAnchor: Boolean = false,
    val availableHeight: Int = Int.MAX_VALUE,
)

private class ActionMenuPositionProvider(
    private val margin: Int,
    private val gap: Int,
    private val onPosition: (MenuPointer) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val x = preferredX.coerceIn(margin, maxX)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val maxY = (windowSize.height - margin - popupContentSize.height).coerceAtLeast(margin)
        val belowSpace = (windowSize.height - margin - below).coerceAtLeast(1)
        val aboveSpace = (anchorBounds.top - gap - margin).coerceAtLeast(1)
        val aboveAnchor = below > maxY && (above >= margin || aboveSpace > belowSpace)
        val y = (if (aboveAnchor) above else below).coerceIn(margin, maxY)
        // 必须在菜单边界修正后计算箭头，避免屏幕边缘的弹层与按钮失去指向关系。
        onPosition(
            MenuPointer(
                x = (anchorBounds.center.x - x).toFloat(),
                aboveAnchor = aboveAnchor,
                availableHeight = if (aboveAnchor) aboveSpace else belowSpace,
            ),
        )
        return IntOffset(x, y)
    }
}

private class ActionMenuShape(private val pointer: MenuPointer) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pointerHeight = with(density) { MenuPointerHeight.toPx() }
        val halfWidth = with(density) { MenuPointerHalfWidth.toPx() }
        val radius = with(density) { SuiteActionMenuDefaults.cornerRadius.toPx() }
            .coerceAtMost((size.height - pointerHeight) / 2).coerceAtLeast(0f)
        val tipX = pointer.x.coerceIn(halfWidth, (size.width - halfWidth).coerceAtLeast(halfWidth))
        val bodyTop = if (pointer.aboveAnchor) 0f else pointerHeight
        val bodyBottom = if (pointer.aboveAnchor) size.height - pointerHeight else size.height
        val body = Path().apply {
            addRoundRect(RoundRect(0f, bodyTop, size.width, bodyBottom, CornerRadius(radius)))
        }
        // 三角形与圆角主体相交，在靠近圆角时也不会出现断开的箭头或接缝。
        val baseY = if (pointer.aboveAnchor) bodyBottom - radius / 2 else bodyTop + radius / 2
        val arrow = Path().apply {
            moveTo(tipX, if (pointer.aboveAnchor) size.height else 0f)
            lineTo(tipX + halfWidth, baseY)
            lineTo(tipX - halfWidth, baseY)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, arrow))
    }
}

private val LocalMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }
private val MenuMinWidth = 168.dp
private val MenuMaxWidth = 280.dp
private val MenuScreenMargin = 12.dp
private val MenuAnchorGap = 2.dp
private val MenuContentPadding = 6.dp
private val MenuPointerHeight = 8.dp
private val MenuPointerHalfWidth = 8.dp
