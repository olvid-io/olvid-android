/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.olvid.messenger.gallery

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import io.olvid.messenger.discussion.message.attachments.decodedThumbnailCacheKey
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.discussion.message.attachments.AttachmentDownloadProgress
import io.olvid.messenger.discussion.message.attachments.rememberAttachmentProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_SCALE_FACTOR = -1f // -1 = unconstrained

private fun fitScale(bmpW: Int, bmpH: Int, vw: Int, vh: Int): Float {
    if (bmpW == 0 || bmpH == 0 || vw == 0 || vh == 0) return 1f
    return min(vw.toFloat() / bmpW, vh.toFloat() / bmpH)
}

private fun clampCenter(
    cx: Float, cy: Float, scale: Float,
    viewW: Int, viewH: Int, bmpW: Int, bmpH: Int
): Pair<Float, Float> {
    val minCx = min(bmpW / 2f, viewW / 2f / scale)
    val minCy = min(bmpH / 2f, viewH / 2f / scale)
    return Pair(
        cx.coerceIn(minCx, (bmpW - minCx).coerceAtLeast(minCx)),
        cy.coerceIn(minCy, (bmpH - minCy).coerceAtLeast(minCy))
    )
}

@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    fyleAndStatus: FyleAndStatus,
    linkPreviewData: OpenGraph?,
    textBlocks: List<TextBlock>?,
    initialScrollDone: Boolean,
    onSingleTap: (List<TextBlock>?) -> Unit,
    onZoomedChanged: (Boolean) -> Unit,
    overlayBottomPadding: Dp = 0.dp,
) {
    val join = fyleAndStatus.fyleMessageJoinWithStatus
    val isFailed = join.status == FyleMessageJoinWithStatus.STATUS_FAILED
                || join.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED

    // Live download progress (only observed while the attachment is not yet complete)
    val attachmentProgress = if (!fyleAndStatus.fyle.isComplete) {
        rememberAttachmentProgress(join).value
    } else {
        null
    }

    // Frame counter to re-trigger drawBehind on each animated drawable frame
    var animFrameCount by remember { mutableIntStateOf(0) }

    // Three-state load result: null = still loading, Pair(true, content) = loaded OK,
    // Pair(false, null) = loaded but nothing to show.
    // FyleAndStatus.equals() only compares fyle.id and messageId, so also key on filePath
    // to re-run the load when a download completes in-place.
    val loadResult by produceState<Pair<Boolean, Any?>?>(null, fyleAndStatus, fyleAndStatus.fyle.filePath) {
        val content = withContext(Dispatchers.IO) {
            runCatching {
                if (fyleAndStatus.fyleMessageJoinWithStatus.mimeType == OpenGraph.MIME_TYPE && linkPreviewData != null) {
                    linkPreviewData.bitmap
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PreviewUtilsWithDrawables.getDrawablePreview(
                        fyleAndStatus.fyle,
                        fyleAndStatus.fyleMessageJoinWithStatus,
                        PreviewUtils.MAX_SIZE
                    )?.also { d ->
                        if (d is AnimatedImageDrawable) d.start()
                    }
                } else {
                    PreviewUtils.getBitmapPreview(
                        fyleAndStatus.fyle,
                        fyleAndStatus.fyleMessageJoinWithStatus,
                        PreviewUtils.MAX_SIZE
                    )
                }
            }.getOrNull()
        }
        value = Pair(content != null, content)
    }
    // Low-res thumbnail from the shared preview cache (previewPixelSize 1 accepts any cached
    // size), or the tile thumbnail Coil already decoded, shown while the full-resolution decode
    // runs so neither page swipes nor the viewer's opening morph land on a black page. Once
    // loading finishes, the real content (or the error message) takes over.
    val placeholder = remember(fyleAndStatus, fyleAndStatus.fyle.filePath) {
        PreviewUtils.getCachedBitmapPreview(
            fyleAndStatus.fyle,
            fyleAndStatus.fyleMessageJoinWithStatus,
            1
        ) ?: fyleAndStatus.decodedThumbnailCacheKey()?.let { App.imageLoader.memoryCache?.get(it)?.bitmap }
    }
    val loadedContent = if (loadResult == null) placeholder else loadResult?.second

    // Attach Drawable.Callback for animated drawables to drive recomposition
    DisposableEffect(loadResult) {
        val cb =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && loadedContent is AnimatedImageDrawable) {
                object : Drawable.Callback {
                    override fun invalidateDrawable(who: Drawable) {
                        animFrameCount++
                    }

                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
                }.also { loadedContent.callback = it }
            } else null
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cb?.let { (loadedContent as? AnimatedImageDrawable)?.callback = null }
            }
        }
    }

    // Viewport and image geometry state
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var bitmapWidth by remember { mutableIntStateOf(0) }
    var bitmapHeight by remember { mutableIntStateOf(0) }
    var bitmapCenterX by remember { mutableFloatStateOf(0f) }
    var bitmapCenterY by remember { mutableFloatStateOf(0f) }
    var bitmapScale by remember { mutableFloatStateOf(1f) }
    var autoFit by remember { mutableStateOf(true) }
    var targetZoomAndCenter by remember { mutableStateOf<Triple<Float,Float,Float>?>(null) }

    // Reset geometry when a new image is loaded
    LaunchedEffect(loadResult) {
        val (w, h) = when (loadedContent) {
            is Drawable -> Pair(loadedContent.intrinsicWidth, loadedContent.intrinsicHeight)
            is Bitmap -> Pair(loadedContent.width, loadedContent.height)
            else -> Pair(0, 0)
        }
        bitmapWidth = w
        bitmapHeight = h
        if (w > 0 && h > 0) {
            bitmapCenterX = w / 2f
            bitmapCenterY = h / 2f
            bitmapScale = fitScale(w, h, viewSize.width, viewSize.height)
            autoFit = true
        }
    }

    LaunchedEffect(targetZoomAndCenter) {
        targetZoomAndCenter?.let { target ->
            animateZoomTransform(bitmapScale, bitmapCenterX, bitmapCenterY, target) { s, x, y ->
                bitmapScale = s
                bitmapCenterX = x
                bitmapCenterY = y
            }
            targetZoomAndCenter = null
        }
    }

    // The image is pannable when its content overflows the viewport. Only then should pager
    // swipe / fling-to-dismiss be suppressed. A low-resolution image displayed smaller than the
    // screen never overflows, so dismiss stays available even when it is manually zoomed
    // (autoFit == false) but still fits within the viewport.
    fun contentOverflowsViewport() = bitmapScale * bitmapWidth > viewSize.width + 1 ||
            bitmapScale * bitmapHeight > viewSize.height + 1

    val latestOnZoomedChanged by rememberUpdatedState(onZoomedChanged)
    LaunchedEffect(Unit) {
        snapshotFlow { contentOverflowsViewport() }.collect { latestOnZoomedChanged(it) }
    }

    // Keep latest textBlocks accessible inside pointerInput without restarting
    val latestTextBlocks by rememberUpdatedState(textBlocks)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)

    val textBlockPaint = remember {
        Paint().apply {
            color = Color.YELLOW
            alpha = 50
            isAntiAlias = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        // Error / status overlays
        if (isFailed) {
            if (initialScrollDone) {
                Text(
                    text = stringResource(R.string.label_attachment_download_failed),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else if (loadResult != null && loadedContent == null
            && join.status != FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE
            && join.status != FyleMessageJoinWithStatus.STATUS_DOWNLOADING
        ) {
            // Loading finished but nothing to show (except when a download affordance is
            // shown instead, see below).
            Text(
                text = stringResource(R.string.label_unable_to_display_image),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Main drawing + gesture surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewSize = size
                    if (autoFit && bitmapWidth > 0) {
                        bitmapScale = fitScale(bitmapWidth, bitmapHeight, size.width, size.height)
                    }
                }
                .pointerInput(Unit) {
                    var dragStartCenterX = 0f
                    var dragStartCenterY = 0f
                    var zoomInitialScale = 1f
                    var zoomInitialCenterX = 0f
                    var zoomInitialCenterY = 0f
                    var zoomMidBitmapX = 0f
                    var zoomMidBitmapY = 0f

                    // Pan the bitmap center by a screen-space delta from the given base center
                    fun panBy(baseX: Float, baseY: Float, deltaX: Float, deltaY: Float) {
                        val (cx, cy) = clampCenter(
                            baseX - deltaX / bitmapScale,
                            baseY - deltaY / bitmapScale,
                            bitmapScale,
                            viewSize.width, viewSize.height,
                            bitmapWidth, bitmapHeight
                        )
                        bitmapCenterX = cx
                        bitmapCenterY = cy
                    }

                    detectZoomPanGestures(
                        isDraggable = ::contentOverflowsViewport,
                        onPanStart = {
                            dragStartCenterX = bitmapCenterX
                            dragStartCenterY = bitmapCenterY
                        },
                        onPan = { totalDeltaX, totalDeltaY ->
                            panBy(dragStartCenterX, dragStartCenterY, totalDeltaX, totalDeltaY)
                        },
                        onFlingBy = { deltaX, deltaY ->
                            panBy(bitmapCenterX, bitmapCenterY, deltaX, deltaY)
                        },
                        onZoomStart = { focusX, focusY ->
                            zoomInitialScale = bitmapScale
                            zoomInitialCenterX = bitmapCenterX
                            zoomInitialCenterY = bitmapCenterY
                            zoomMidBitmapX =
                                bitmapCenterX + (focusX - viewSize.width / 2f) / bitmapScale
                            zoomMidBitmapY =
                                bitmapCenterY + (focusY - viewSize.height / 2f) / bitmapScale
                            autoFit = false
                        },
                        onZoom = { scaleRatio ->
                            val minS = min(
                                1f,
                                fitScale(
                                    bitmapWidth, bitmapHeight,
                                    viewSize.width, viewSize.height
                                )
                            )
                            val rawScale = zoomInitialScale * scaleRatio
                            val newScale =
                                if (MAX_SCALE_FACTOR < 0) rawScale.coerceAtLeast(minS)
                                else rawScale.coerceIn(minS, MAX_SCALE_FACTOR)
                            // Keep the pinch focus point stationary on screen
                            val newCx = zoomMidBitmapX +
                                    (zoomInitialCenterX - zoomMidBitmapX) * zoomInitialScale / newScale
                            val newCy = zoomMidBitmapY +
                                    (zoomInitialCenterY - zoomMidBitmapY) * zoomInitialScale / newScale
                            val (cx, cy) = clampCenter(
                                newCx, newCy, newScale,
                                viewSize.width, viewSize.height,
                                bitmapWidth, bitmapHeight
                            )
                            bitmapScale = newScale
                            bitmapCenterX = cx
                            bitmapCenterY = cy
                        },
                        onDoubleTap = { tapX, tapY ->
                            // Toggle between FIT and 1:1 centered on the tapped point
                            if (autoFit) {
                                autoFit = false
                                val centerX =
                                    bitmapWidth / 2f + (tapX - viewSize.width / 2f) / bitmapScale
                                val centerY =
                                    bitmapHeight / 2f + (tapY - viewSize.height / 2f) / bitmapScale
                                val (cx, cy) = clampCenter(
                                    centerX, centerY, 1f,
                                    viewSize.width, viewSize.height,
                                    bitmapWidth, bitmapHeight
                                )
                                targetZoomAndCenter = Triple(1f, cx, cy)
                            } else {
                                autoFit = true
                                targetZoomAndCenter = Triple(
                                    fitScale(
                                        bitmapWidth, bitmapHeight,
                                        viewSize.width, viewSize.height
                                    ), bitmapWidth / 2f, bitmapHeight / 2f
                                )
                            }
                        },
                        onSingleTap = { latestOnSingleTap(null) },
                        onSingleTapImmediate = onSingleTapImmediate@{ tapX, tapY ->
                            // Dispatch right away when the tap lands on a text block
                            val blocks = latestTextBlocks
                            if (blocks.isNullOrEmpty()) return@onSingleTapImmediate false
                            val matrix = Matrix()
                            matrix.postTranslate(-bitmapCenterX, -bitmapCenterY)
                            matrix.postScale(bitmapScale, bitmapScale)
                            matrix.postTranslate(viewSize.width / 2f, viewSize.height / 2f)
                            val inverse = Matrix()
                            matrix.invert(inverse)
                            val point = floatArrayOf(tapX, tapY)
                            inverse.mapPoints(point)
                            val hitBlocks = blocks.filter { block ->
                                block.boundingBox?.contains(
                                    point[0].roundToInt(),
                                    point[1].roundToInt()
                                ) == true
                            }
                            if (hitBlocks.isEmpty()) return@onSingleTapImmediate false
                            val rectF = RectF()
                            val mapped = hitBlocks.map { block ->
                                block.copy(
                                    boundingBox = block.boundingBox?.let { bb ->
                                        val src = RectF(
                                            bb.left.toFloat(),
                                            bb.top.toFloat(),
                                            bb.right.toFloat(),
                                            bb.bottom.toFloat()
                                        )
                                        matrix.mapRect(rectF, src)
                                        Rect(
                                            rectF.left.roundToInt(),
                                            rectF.top.roundToInt(),
                                            rectF.right.roundToInt(),
                                            rectF.bottom.roundToInt()
                                        )
                                    }
                                )
                            }
                            latestOnSingleTap(mapped)
                            true
                        },
                    )
                }
                .drawBehind {
                    @Suppress("UNUSED_EXPRESSION")
                    animFrameCount // register dependency for animated drawables

                    val d = loadedContent
                    if (d == null || bitmapWidth <= 0 || bitmapHeight <= 0) return@drawBehind

                    val matrix = Matrix()
                    matrix.postTranslate(-bitmapCenterX, -bitmapCenterY)
                    matrix.postScale(bitmapScale, bitmapScale)
                    matrix.postTranslate(size.width / 2f, size.height / 2f)

                    val rectF = RectF()
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        when (d) {
                            is Bitmap -> native.drawBitmap(d, matrix, null)
                            is Drawable -> {
                                d.setBounds(0, 0, bitmapWidth, bitmapHeight)
                                native.withSave {
                                    concat(matrix)
                                    d.draw(this)
                                }
                            }
                        }
                        // Text block overlays
                        val blocks = latestTextBlocks
                        if (!blocks.isNullOrEmpty()) {
                            blocks.forEach { block ->
                                block.boundingBox?.let { bb ->
                                    rectF.set(
                                        bb.left.toFloat(), bb.top.toFloat(),
                                        bb.right.toFloat(), bb.bottom.toFloat()
                                    )
                                    matrix.mapRect(rectF)
                                    native.drawRect(rectF, textBlockPaint)
                                }
                            }
                        }
                    }
                }
        )

        // Download overlay for images that are not yet downloaded. Composed after the
        // gesture surface so it is topmost in hit-testing and taps reliably trigger /
        // cancel the download instead of being consumed by the gesture surface.
        when (join.status) {
            FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> {
                Image(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = overlayBottomPadding)
                        .padding(8.dp)
                        .size(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                        ) {
                            App.runThread(StartAttachmentDownloadTask(join))
                        },
                    painter = painterResource(id = R.drawable.ic_file_download),
                    contentDescription = stringResource(R.string.label_download)
                )
            }

            FyleMessageJoinWithStatus.STATUS_DOWNLOADING -> {
                AttachmentDownloadProgress(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = overlayBottomPadding)
                        .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                        ) {
                            App.runThread(StopAttachmentDownloadTask(join))
                        },
                    speed = attachmentProgress?.speed,
                    eta = attachmentProgress?.eta,
                    progress = attachmentProgress?.progress ?: 0f,
                    large = true
                )
            }
        }
    }
}
