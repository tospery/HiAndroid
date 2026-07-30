@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.tospery.suite.R
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 通用图片浏览项。
 *
 * [key] 必须在当前图片列表内稳定且唯一；[model] 可传入 Coil 支持的 URL、Uri、File、
 * ImageRequest 等图片数据模型。
 */
data class SuiteImageViewerItem(
    val key: String,
    val model: Any?,
    val contentDescription: String? = null,
)

/**
 * 支持左右分页、双指缩放和放大后拖动的通用图片浏览器。
 *
 * 宿主负责顶部栏、业务加载状态和当前页标题；该组件只管理图片展示手势及单页加载失败重试。
 * 图片保持原比例适配视口，缩放范围为 1x 到 5x。图片处于 1x 时横向手势切页，放大后横向
 * 手势用于平移，用户缩回 1x 后可继续切页。
 */
@Composable
fun SuiteImageViewer(
    items: List<SuiteImageViewerItem>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty()) { "SuiteImageViewer 至少需要一张图片。" }
    require(selectedIndex in items.indices) { "SuiteImageViewer selectedIndex 越界。" }
    require(items.map(SuiteImageViewerItem::key).distinct().size == items.size) {
        "SuiteImageViewer item key 必须唯一。"
    }

    val pagerState =
        rememberPagerState(
            initialPage = selectedIndex,
            pageCount = { items.size },
        )
    val pageScales = remember(items) { mutableStateMapOf<String, Float>() }
    val currentKey = items.getOrNull(pagerState.currentPage)?.key
    val currentScale = currentKey?.let(pageScales::get) ?: MIN_IMAGE_SCALE

    LaunchedEffect(selectedIndex, items) {
        if (pagerState.currentPage != selectedIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onSelectedIndexChanged)
    }

    HorizontalPager(
        state = pagerState,
        key = { index -> items[index].key },
        userScrollEnabled = currentScale <= MIN_IMAGE_SCALE + SCALE_EPSILON,
        modifier = modifier,
    ) { page ->
        val item = items[page]
        ZoomableSuiteImage(
            item = item,
            onScaleChanged = { scale -> pageScales[item.key] = scale },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ZoomableSuiteImage(
    item: SuiteImageViewerItem,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(item.key) { mutableFloatStateOf(MIN_IMAGE_SCALE) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(item.key) { mutableStateOf(IntSize.Zero) }
    val currentOnScaleChanged by rememberUpdatedState(onScaleChanged)
    val transformableState =
        rememberTransformableState { centroid, zoomChange, panChange, _ ->
            val previousScale = scale
            val nextScale = (scale * zoomChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
            scale = nextScale
            offset =
                if (nextScale <= MIN_IMAGE_SCALE + SCALE_EPSILON) {
                    Offset.Zero
                } else {
                    val centroidFromCenter =
                        if (centroid != Offset.Unspecified && viewportSize != IntSize.Zero) {
                            centroid -
                                Offset(
                                    x = viewportSize.width / 2f,
                                    y = viewportSize.height / 2f,
                                )
                        } else {
                            Offset.Zero
                        }
                    val appliedZoom = nextScale / previousScale
                    (
                        offset +
                            (centroidFromCenter - offset) * (1f - appliedZoom) +
                            panChange
                    ).coerceToViewport(
                        viewportSize = viewportSize,
                        scale = nextScale,
                    )
                }
            currentOnScaleChanged(nextScale)
        }

    DisposableEffect(item.key) {
        currentOnScaleChanged(MIN_IMAGE_SCALE)
        onDispose {
            currentOnScaleChanged(MIN_IMAGE_SCALE)
        }
    }

    Box(
        modifier =
            modifier
                .clipToBounds()
                .onSizeChanged { size ->
                    viewportSize = size
                    offset = offset.coerceToViewport(size, scale)
                }
                .transformable(
                    state = transformableState,
                    canPan = { scale > MIN_IMAGE_SCALE + SCALE_EPSILON },
                    lockRotationOnZoomPan = true,
                ),
        contentAlignment = Alignment.Center,
    ) {
        SuiteNetworkImage(
            item = item,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
        )
    }
}

@Composable
private fun SuiteNetworkImage(
    item: SuiteImageViewerItem,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember(item.key) { mutableIntStateOf(0) }
    var isLoading by remember(item.key, retryKey) { mutableStateOf(true) }
    var hasError by remember(item.key, retryKey) { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        key(retryKey) {
            AsyncImage(
                model = item.model,
                contentDescription = item.contentDescription,
                contentScale = ContentScale.Fit,
                onLoading = {
                    isLoading = true
                    hasError = false
                },
                onSuccess = {
                    isLoading = false
                    hasError = false
                },
                onError = {
                    isLoading = false
                    hasError = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when {
            hasError -> {
                SuiteErrorState(
                    title = stringResource(R.string.suite_image_viewer_image_error_title),
                    description =
                        stringResource(R.string.suite_image_viewer_image_error_description),
                    actionText = stringResource(R.string.suite_image_viewer_retry),
                    onActionClick = { retryKey += 1 },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            isLoading -> SuiteLoadingIndicator()
        }
    }
}

private fun Offset.coerceToViewport(
    viewportSize: IntSize,
    scale: Float,
): Offset {
    if (viewportSize == IntSize.Zero || scale <= MIN_IMAGE_SCALE) return Offset.Zero
    val maxX = viewportSize.width * (scale - MIN_IMAGE_SCALE) / 2f
    val maxY = viewportSize.height * (scale - MIN_IMAGE_SCALE) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

private const val MIN_IMAGE_SCALE = 1f
private const val MAX_IMAGE_SCALE = 5f
private const val SCALE_EPSILON = 0.001f
