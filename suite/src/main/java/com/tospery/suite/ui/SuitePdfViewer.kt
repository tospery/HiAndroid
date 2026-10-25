@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.warning
import com.tospery.buildmetadata.module_suite.ModuleMetadata
import com.tospery.suite.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

private val suitePdfViewerLogTag =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "pdf-viewer",
    )

/**
 * 以纵向列表渲染 PDF 文档的每一页，支持单页双指缩放与平移。
 *
 * [content] 会写入应用缓存目录下的临时文件供系统 `PdfRenderer` 读取，离开组合后关闭文档并删除该文件。
 * 文档无法打开（损坏、加密或使用不支持的特性）时显示组件自带的本地化错误文案；单页渲染失败只影响该页。
 */
@Composable
fun SuitePdfViewer(
    content: ByteArray,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val documentState by
        produceState<SuitePdfDocumentState>(
            initialValue = SuitePdfDocumentState.Loading,
            key1 = content,
        ) {
            val result =
                runCatching {
                    SuitePdfDocument.open(
                        cacheDirectory = context.cacheDir,
                        content = content,
                    )
                }
            val document = result.getOrNull()
            value =
                if (document == null || document.pageCount == 0) {
                    result.exceptionOrNull()?.let { throwable ->
                        warning(
                            tag = suitePdfViewerLogTag,
                            throwable = throwable,
                        ) {
                            "打开 PDF 文档失败。"
                        }
                    }
                    document?.close()
                    SuitePdfDocumentState.Error
                } else {
                    SuitePdfDocumentState.Ready(document)
                }
            try {
                awaitCancellation()
            } finally {
                if (documentStateOwnsDocument(value)) {
                    document?.close()
                }
            }
        }

    SuitePdfDocumentContent(
        state = documentState,
        modifier = modifier,
    )
}

@Composable
private fun SuitePdfDocumentContent(
    state: SuitePdfDocumentState,
    modifier: Modifier,
) {
    when (state) {
        SuitePdfDocumentState.Loading ->
            Box(modifier = modifier) {
                SuiteLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

        SuitePdfDocumentState.Error ->
            SuitePdfError(
                modifier = modifier,
            )

        is SuitePdfDocumentState.Ready ->
            LazyColumn(
                modifier =
                    modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = (0 until state.document.pageCount).toList(),
                    key = { pageIndex -> pageIndex },
                ) { pageIndex ->
                    SuitePdfPage(
                        document = state.document,
                        pageIndex = pageIndex,
                        pageCount = state.document.pageCount,
                    )
                }
            }
    }
}

@Composable
private fun SuitePdfPage(
    document: SuitePdfDocument,
    pageIndex: Int,
    pageCount: Int,
) {
    var transform by remember(document, pageIndex) { mutableStateOf(SuitePdfPageTransform()) }
    var viewportSize by remember(document, pageIndex) { mutableStateOf(IntSize.Zero) }
    val transformableState =
        rememberTransformableState { zoomChange, panChange, _ ->
            transform =
                transform.applyGesture(
                    zoomChange = zoomChange,
                    panChange = panChange,
                    viewportSize = viewportSize,
                )
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clipToBounds()
                .onSizeChanged { size ->
                    viewportSize = size
                    transform = transform.constrainedTo(size)
                }.transformable(
                    state = transformableState,
                    canPan = { panChange ->
                        transform.canPan(
                            panChange = panChange,
                            viewportSize = viewportSize,
                        )
                    },
                    lockRotationOnZoomPan = true,
                ),
    ) {
        val density = LocalDensity.current
        val targetWidthPx =
            with(density) {
                maxWidth.roundToPx()
            }.coerceAtLeast(1)
        val renderWidthPx =
            targetWidthPx
                .scaledForPdfRender(transform.scale)
                .coerceAtMost(MAX_PDF_PAGE_WIDTH_PX)
        val pageState by
            rememberSuitePdfPageState(
                document = document,
                pageIndex = pageIndex,
                targetWidthPx = renderWidthPx,
                delayHighResolutionRender = renderWidthPx > targetWidthPx,
            )

        SuitePdfPageContent(
            state = pageState,
            pageIndex = pageIndex,
            pageCount = pageCount,
            transform = transform,
        )
    }
}

@Composable
private fun rememberSuitePdfPageState(
    document: SuitePdfDocument,
    pageIndex: Int,
    targetWidthPx: Int,
    delayHighResolutionRender: Boolean,
): State<SuitePdfPageState> =
    produceState<SuitePdfPageState>(
        initialValue = SuitePdfPageState.Loading,
        key1 = document,
        key2 = pageIndex,
        key3 = targetWidthPx,
    ) {
        if (delayHighResolutionRender) {
            delay(PDF_HIGH_RESOLUTION_RENDER_DELAY_MILLIS)
        }
        value =
            runCatching {
                document.renderPage(
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                )
            }.fold(
                onSuccess = SuitePdfPageState::Ready,
                onFailure = { throwable ->
                    warning(
                        tag = suitePdfViewerLogTag,
                        throwable = throwable,
                        attributes =
                            listOf(
                                LogAttribute("page_index", pageIndex.toString()),
                            ),
                    ) {
                        "渲染 PDF 页面失败。"
                    }
                    SuitePdfPageState.Error
                },
            )
    }

@Composable
private fun SuitePdfPageContent(
    state: SuitePdfPageState,
    pageIndex: Int,
    pageCount: Int,
    transform: SuitePdfPageTransform,
) {
    when (state) {
        SuitePdfPageState.Loading ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(DEFAULT_PDF_PAGE_ASPECT_RATIO),
            ) {
                SuiteLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

        SuitePdfPageState.Error ->
            Text(
                text =
                    stringResource(
                        R.string.suite_pdf_page_error,
                        pageIndex + 1,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )

        is SuitePdfPageState.Ready ->
            Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription =
                    stringResource(
                        R.string.suite_pdf_page_description,
                        pageIndex + 1,
                        pageCount,
                    ),
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            state.bitmap.width.toFloat() / state.bitmap.height,
                        ).graphicsLayer {
                            scaleX = transform.scale
                            scaleY = transform.scale
                            translationX = transform.offset.x
                            translationY = transform.offset.y
                        },
            )
    }
}

@Composable
private fun SuitePdfError(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.suite_pdf_error_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
        )
    }
}

private fun documentStateOwnsDocument(state: SuitePdfDocumentState): Boolean = state is SuitePdfDocumentState.Ready

private sealed interface SuitePdfDocumentState {
    data object Loading : SuitePdfDocumentState

    data class Ready(
        val document: SuitePdfDocument,
    ) : SuitePdfDocumentState

    data object Error : SuitePdfDocumentState
}

private sealed interface SuitePdfPageState {
    data object Loading : SuitePdfPageState

    data class Ready(
        val bitmap: Bitmap,
    ) : SuitePdfPageState

    data object Error : SuitePdfPageState
}

private class SuitePdfDocument(
    private val cacheFile: File,
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val rendererLock = Any()
    private var isClosed = false

    val pageCount: Int
        get() = renderer.pageCount

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            synchronized(rendererLock) {
                check(!isClosed) { "PDF 文档已经关闭。" }
                require(pageIndex in 0 until pageCount) { "PDF 页码越界。" }

                renderer.openPage(pageIndex).use { page ->
                    require(page.width > 0 && page.height > 0) { "PDF 页面尺寸无效。" }
                    val requestedWidth = targetWidthPx.coerceAtMost(MAX_PDF_PAGE_WIDTH_PX)
                    val widthScale = requestedWidth.toFloat() / page.width
                    val heightScale = MAX_PDF_PAGE_HEIGHT_PX.toFloat() / page.height
                    val renderScale = minOf(widthScale, heightScale)
                    val bitmapWidth =
                        (page.width * renderScale)
                            .roundToInt()
                            .coerceAtLeast(1)
                    val bitmapHeight =
                        (page.height * renderScale)
                            .roundToInt()
                            .coerceAtLeast(1)
                    Bitmap
                        .createBitmap(
                            bitmapWidth,
                            bitmapHeight,
                            Bitmap.Config.ARGB_8888,
                        ).also { bitmap ->
                            // PDF 纸张默认是不透明白色，避免透明区域在暗色主题下变黑。
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                        }
                }
            }
        }

    override fun close() {
        synchronized(rendererLock) {
            if (isClosed) return
            isClosed = true
            renderer.close()
            fileDescriptor.close()
            cacheFile.delete()
        }
    }

    companion object {
        suspend fun open(
            cacheDirectory: File,
            content: ByteArray,
        ): SuitePdfDocument =
            withContext(Dispatchers.IO) {
                val cacheFile =
                    File.createTempFile(
                        PDF_CACHE_FILE_PREFIX,
                        PDF_CACHE_FILE_SUFFIX,
                        cacheDirectory,
                    )
                var fileDescriptor: ParcelFileDescriptor? = null
                runCatching {
                    cacheFile.outputStream().buffered().use { output ->
                        output.write(content)
                    }
                    fileDescriptor =
                        ParcelFileDescriptor.open(
                            cacheFile,
                            ParcelFileDescriptor.MODE_READ_ONLY,
                        )
                    SuitePdfDocument(
                        cacheFile = cacheFile,
                        fileDescriptor = fileDescriptor,
                        renderer = PdfRenderer(fileDescriptor),
                    )
                }.getOrElse { throwable ->
                    fileDescriptor?.close()
                    cacheFile.delete()
                    throw throwable
                }
            }
    }
}

private const val PDF_CACHE_FILE_PREFIX = "suite-pdf-"
private const val PDF_CACHE_FILE_SUFFIX = ".pdf"
private const val MAX_PDF_PAGE_WIDTH_PX = 2_048
private const val MAX_PDF_PAGE_HEIGHT_PX = 4_096
private const val DEFAULT_PDF_PAGE_ASPECT_RATIO = 0.707f
private const val PDF_HIGH_RESOLUTION_RENDER_DELAY_MILLIS = 120L
