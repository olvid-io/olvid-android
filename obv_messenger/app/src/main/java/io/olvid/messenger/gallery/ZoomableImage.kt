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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.TextBlock
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.core.graphics.withSave
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import kotlin.time.Duration.Companion.milliseconds

private const val DOUBLE_CLICK_DURATION_MS = 400L
private const val SCALE_ANIMATION_DURATION_MS = 200
private const val CLICK_DURATION_MS = 200L
private const val CLICK_MOVE_THRESHOLD_PX = 10f
private const val DOUBLE_CLICK_DISTANCE_THRESHOLD_PX = 100f
private const val MAX_SCALE_FACTOR = -1f // -1 = unconstrained
private const val FLING_INERTIA = 0.05
private const val FLING_STOP_THRESHOLD_SQ = 100 * 100f

private fun distancePx(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    return sqrt(dx * dx + dy * dy)
}

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

private enum class GestureMode { NONE, DRAG, ZOOM }

private class GestureState {
    var mode: GestureMode = GestureMode.NONE
    var clicking: Boolean = false
    var doubleClicking: Boolean = false
    var singleTapPending: Boolean = false
    var downTime: Long = 0L
    var lastUpTime: Long = 0L
    var downX: Float = 0f
    var downY: Float = 0f
    var dragStartCenterX: Float = 0f
    var dragStartCenterY: Float = 0f
    var zoomInitialDist: Float = 0f
    var zoomInitialScale: Float = 1f
    var zoomInitialCenterX: Float = 0f
    var zoomInitialCenterY: Float = 0f
    var zoomMidBitmapX: Float = 0f
    var zoomMidBitmapY: Float = 0f
    var flingActive: Boolean = false
}

@Composable
fun ZoomableImage(
    fyleAndStatus: FyleAndStatus,
    linkPreviewData: OpenGraph?,
    textBlocks: List<TextBlock>?,
    initialScrollDone: Boolean,
    onSingleTap: (List<TextBlock>?) -> Unit,
    onZoomedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFailed = fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED
                || fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED

    // Frame counter to re-trigger drawBehind on each animated drawable frame
    var animFrameCount by remember { mutableIntStateOf(0) }

    // Three-state load result: null = still loading, Pair(true, content) = loaded OK,
    // Pair(false, null) = loaded but nothing to show
    val loadResult by produceState<Pair<Boolean, Any?>?>(null, fyleAndStatus) {
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
    val loadedContent = loadResult?.second

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
            val initialScale = bitmapScale
            val initialX = bitmapCenterX
            val initialY = bitmapCenterY
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = SCALE_ANIMATION_DURATION_MS, easing = LinearEasing)
            ) { progress, _ ->
                bitmapScale = initialScale * (1-progress) + target.first * progress
                bitmapCenterX = initialX * (1-progress) + target.second * progress
                bitmapCenterY = initialY * (1-progress) + target.third * progress
            }
            targetZoomAndCenter = null
        }
    }

    SideEffect { onZoomedChanged(!autoFit) }

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

    val gestureState = remember { GestureState() }
    val coroutineScope = rememberCoroutineScope()

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
        } else if (loadResult != null && loadedContent == null) {
            // Loading finished but nothing to show
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
                    val velocityTracker = VelocityTracker()

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val allReleased = event.changes.all { !it.pressed }
                            val eventTimeMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L

                            // Image is pannable when it overflows the viewport
                            val isDraggable = bitmapScale * bitmapWidth > viewSize.width + 1
                                    || bitmapScale * bitmapHeight > viewSize.height + 1

                            when (event.type) {
                                PointerEventType.Press -> {
                                    when (pressed.size) {
                                        1 -> {
                                            gestureState.flingActive = false
                                            gestureState.clicking = true
                                            gestureState.doubleClicking =
                                                (eventTimeMs - gestureState.lastUpTime) < DOUBLE_CLICK_DURATION_MS
                                                        && distancePx(
                                                    pressed[0].position.x, pressed[0].position.y,
                                                    gestureState.downX, gestureState.downY
                                                ) < DOUBLE_CLICK_DISTANCE_THRESHOLD_PX
                                            if (gestureState.doubleClicking) {
                                                gestureState.singleTapPending = false
                                            }
                                            gestureState.downTime = eventTimeMs
                                            gestureState.downX = pressed[0].position.x
                                            gestureState.downY = pressed[0].position.y
                                            gestureState.dragStartCenterX = bitmapCenterX
                                            gestureState.dragStartCenterY = bitmapCenterY
                                            gestureState.mode = GestureMode.DRAG
                                            velocityTracker.resetTracking()
                                            velocityTracker.addPosition(
                                                eventTimeMs,
                                                pressed[0].position
                                            )
                                            // Consume immediately if already draggable so pager
                                            // never starts tracking this gesture as a horizontal swipe
                                            if (isDraggable) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }

                                        2 -> {
                                            gestureState.clicking = false
                                            val p0 = pressed[0].position
                                            val p1 = pressed[1].position
                                            val dist = distancePx(p0.x, p0.y, p1.x, p1.y)
                                            if (dist > 10f) {
                                                gestureState.zoomInitialDist = dist
                                                gestureState.zoomInitialScale = bitmapScale
                                                gestureState.zoomInitialCenterX = bitmapCenterX
                                                gestureState.zoomInitialCenterY = bitmapCenterY
                                                val midX = (p0.x + p1.x) / 2f
                                                val midY = (p0.y + p1.y) / 2f
                                                val offsetX = midX - viewSize.width / 2f
                                                val offsetY = midY - viewSize.height / 2f
                                                gestureState.zoomMidBitmapX =
                                                    bitmapCenterX + offsetX / bitmapScale
                                                gestureState.zoomMidBitmapY =
                                                    bitmapCenterY + offsetY / bitmapScale
                                                gestureState.mode = GestureMode.ZOOM
                                                autoFit = false
                                            }
                                            // Always consume multi-finger events — pager must not handle them
                                            event.changes.forEach { it.consume() }
                                        }

                                        else -> {
                                            gestureState.mode = GestureMode.NONE
                                            gestureState.clicking = false
                                        }
                                    }
                                }

                                PointerEventType.Move -> {
                                    when (gestureState.mode) {
                                        GestureMode.DRAG if pressed.size == 1 -> {
                                            val pos = pressed[0].position
                                            velocityTracker.addPosition(eventTimeMs, pos)
                                            if (distancePx(
                                                    pos.x, pos.y,
                                                    gestureState.downX, gestureState.downY
                                                ) > CLICK_MOVE_THRESHOLD_PX
                                            ) {
                                                gestureState.clicking = false
                                            }
                                            val newCx = gestureState.dragStartCenterX -
                                                    (pos.x - gestureState.downX) / bitmapScale
                                            val newCy = gestureState.dragStartCenterY -
                                                    (pos.y - gestureState.downY) / bitmapScale
                                            val (cx, cy) = clampCenter(
                                                newCx, newCy, bitmapScale,
                                                viewSize.width, viewSize.height,
                                                bitmapWidth, bitmapHeight
                                            )
                                            bitmapCenterX = cx
                                            bitmapCenterY = cy
                                            // Consume so the pager doesn't interpret this as a page swipe
                                            if (isDraggable) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }

                                        GestureMode.ZOOM if pressed.size >= 2 -> {
                                            val p0 = pressed[0].position
                                            val p1 = pressed[1].position
                                            val newDist = distancePx(p0.x, p0.y, p1.x, p1.y)
                                            if (newDist > 10f && gestureState.zoomInitialDist > 0f) {
                                                val minS = min(
                                                    1f,
                                                    fitScale(
                                                        bitmapWidth, bitmapHeight,
                                                        viewSize.width, viewSize.height
                                                    )
                                                )
                                                val rawScale =
                                                    gestureState.zoomInitialScale * newDist / gestureState.zoomInitialDist
                                                val newScale =
                                                    if (MAX_SCALE_FACTOR < 0) rawScale.coerceAtLeast(
                                                        minS
                                                    ) else rawScale.coerceIn(minS, MAX_SCALE_FACTOR)
                                                val newCx = gestureState.zoomMidBitmapX +
                                                        (gestureState.zoomInitialCenterX - gestureState.zoomMidBitmapX) *
                                                        gestureState.zoomInitialScale / newScale
                                                val newCy = gestureState.zoomMidBitmapY +
                                                        (gestureState.zoomInitialCenterY - gestureState.zoomMidBitmapY) *
                                                        gestureState.zoomInitialScale / newScale
                                                val (cx, cy) = clampCenter(
                                                    newCx, newCy, newScale,
                                                    viewSize.width, viewSize.height,
                                                    bitmapWidth, bitmapHeight
                                                )
                                                bitmapScale = newScale
                                                bitmapCenterX = cx
                                                bitmapCenterY = cy
                                            }
                                            // Always consume pinch events
                                            event.changes.forEach { it.consume() }
                                        }

                                        else -> {}
                                    }
                                }

                                PointerEventType.Release -> {
                                    when {
                                        // One finger lifted during pinch — transition to drag
                                        !allReleased && gestureState.mode == GestureMode.ZOOM -> {
                                            val stillPressed = event.changes.filter { it.pressed }
                                            if (stillPressed.size == 1) {
                                                val pos = stillPressed[0].position
                                                gestureState.mode = GestureMode.DRAG
                                                gestureState.clicking = false
                                                gestureState.downX = pos.x
                                                gestureState.downY = pos.y
                                                gestureState.dragStartCenterX = bitmapCenterX
                                                gestureState.dragStartCenterY = bitmapCenterY
                                                velocityTracker.resetTracking()
                                                velocityTracker.addPosition(eventTimeMs, pos)
                                            }
                                        }

                                        allReleased -> {
                                            // Fling check (only when dragging, not clicking)
                                            if (gestureState.mode == GestureMode.DRAG && !gestureState.clicking) {
                                                val velocity = velocityTracker.calculateVelocity()
                                                val speedSq =
                                                    velocity.x * velocity.x + velocity.y * velocity.y
                                                val isDraggable =
                                                    bitmapScale * bitmapWidth > viewSize.width + 1
                                                            || bitmapScale * bitmapHeight > viewSize.height + 1
                                                if (speedSq > FLING_STOP_THRESHOLD_SQ && isDraggable) {
                                                    gestureState.flingActive = true
                                                    var flingVx = velocity.x
                                                    var flingVy = velocity.y
                                                    var lastNanos = System.nanoTime()
                                                    coroutineScope.launch {
                                                        while (gestureState.flingActive) {
                                                            withFrameNanos { frameNanos ->
                                                                val elapsed =
                                                                    (frameNanos - lastNanos) / 1_000_000_000.0
                                                                lastNanos = frameNanos
                                                                val newCx =
                                                                    bitmapCenterX - (elapsed * flingVx / bitmapScale).toFloat()
                                                                val newCy =
                                                                    bitmapCenterY - (elapsed * flingVy / bitmapScale).toFloat()
                                                                val (cx, cy) = clampCenter(
                                                                    newCx, newCy, bitmapScale,
                                                                    viewSize.width, viewSize.height,
                                                                    bitmapWidth, bitmapHeight
                                                                )
                                                                bitmapCenterX = cx
                                                                bitmapCenterY = cy
                                                                val ratio =
                                                                    FLING_INERTIA.pow(elapsed)
                                                                        .toFloat()
                                                                flingVx *= ratio
                                                                flingVy *= ratio
                                                                if (flingVx * flingVx + flingVy * flingVy < FLING_STOP_THRESHOLD_SQ) {
                                                                    gestureState.flingActive = false
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            gestureState.mode = GestureMode.NONE

                                            // Tap detection
                                            if (gestureState.clicking &&
                                                (eventTimeMs - gestureState.downTime) < CLICK_DURATION_MS
                                            ) {
                                                if (gestureState.doubleClicking) {
                                                    // Double tap: toggle zoom
                                                    gestureState.flingActive = false
                                                    val tapX = gestureState.downX
                                                    val tapY = gestureState.downY
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
                                                        targetZoomAndCenter = Triple(fitScale(
                                                            bitmapWidth, bitmapHeight,
                                                            viewSize.width, viewSize.height
                                                        ), bitmapWidth / 2f, bitmapHeight / 2f)

                                                    }
                                                } else {
                                                    // Single tap — check text blocks, then dispatch
                                                    gestureState.lastUpTime = eventTimeMs
                                                    val tapX = gestureState.downX
                                                    val tapY = gestureState.downY
                                                    val blocks = latestTextBlocks
                                                    if (!blocks.isNullOrEmpty()) {
                                                        val matrix = Matrix()
                                                        matrix.postTranslate(
                                                            -bitmapCenterX,
                                                            -bitmapCenterY
                                                        )
                                                        matrix.postScale(bitmapScale, bitmapScale)
                                                        matrix.postTranslate(
                                                            viewSize.width / 2f,
                                                            viewSize.height / 2f
                                                        )
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
                                                        if (hitBlocks.isNotEmpty()) {
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
                                                            gestureState.clicking = false
                                                            continue
                                                        }
                                                    }
                                                    // Delay single tap to allow double-tap detection
                                                    gestureState.singleTapPending = true
                                                    val tapToken = gestureState.lastUpTime
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(DOUBLE_CLICK_DURATION_MS.milliseconds)
                                                        if (gestureState.singleTapPending && gestureState.lastUpTime == tapToken) {
                                                            gestureState.singleTapPending = false
                                                            latestOnSingleTap(null)
                                                        }
                                                    }
                                                }
                                            }
                                            gestureState.clicking = false
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
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
    }
}
