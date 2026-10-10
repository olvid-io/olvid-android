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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private const val ZOOM_ANIMATION_DURATION_MS = 200
private const val CLICK_DURATION_MS = 200L
private const val DOUBLE_CLICK_DURATION_MS = 400L
private const val CLICK_MOVE_THRESHOLD_PX = 10f
private const val DOUBLE_CLICK_DISTANCE_THRESHOLD_PX = 100f
private const val MIN_PINCH_DIST_PX = 10f
private const val FLING_INERTIA = 0.05
private const val FLING_STOP_THRESHOLD_SQ = 100 * 100f

private enum class ZoomGestureMode { NONE, DRAG, ZOOM }

/**
 * Animate a zoom transform towards [target] (scale, x, y), starting from the given values.
 * Shared by the double-tap zoom of both gallery zoomable surfaces.
 */
internal suspend fun animateZoomTransform(
    fromScale: Float,
    fromX: Float,
    fromY: Float,
    target: Triple<Float, Float, Float>,
    onFrame: (scale: Float, x: Float, y: Float) -> Unit,
) {
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(durationMillis = ZOOM_ANIMATION_DURATION_MS, easing = LinearEasing)
    ) { progress, _ ->
        onFrame(
            fromScale + (target.first - fromScale) * progress,
            fromX + (target.second - fromX) * progress,
            fromY + (target.third - fromY) * progress,
        )
    }
}

/**
 * Shared tap / double-tap / drag / pinch / fling detector for the gallery zoomable surfaces
 * (images and videos).
 *
 * All positions and deltas are expressed in the untransformed coordinate space of the node this
 * runs on: it must be installed BEFORE any graphicsLayer applying the zoom transform, otherwise
 * pointer coordinates would be fed back through the very transform they drive.
 *
 * Events are only consumed for multi-finger gestures or when [isDraggable] returns true, so the
 * gallery pager swipe and the fling-to-dismiss keep working while the content is not zoomed.
 *
 * [onPan] receives the total displacement since the pan started ([onPanStart] is the point where
 * the consumer should snapshot its start transform). [onZoom] receives the pinch distance ratio
 * since [onZoomStart], whose argument is the initial pinch midpoint. [onSingleTapImmediate] may
 * return true to handle a tap right away (e.g. text block hit) and skip the delayed [onSingleTap].
 */
internal suspend fun PointerInputScope.detectZoomPanGestures(
    isDraggable: () -> Boolean,
    onPanStart: () -> Unit,
    onPan: (totalDeltaX: Float, totalDeltaY: Float) -> Unit,
    onFlingBy: (deltaX: Float, deltaY: Float) -> Unit,
    onZoomStart: (focusX: Float, focusY: Float) -> Unit,
    onZoom: (scaleRatio: Float) -> Unit,
    onDoubleTap: (x: Float, y: Float) -> Unit,
    onSingleTap: () -> Unit,
    onSingleTapImmediate: (x: Float, y: Float) -> Boolean = { _, _ -> false },
): Unit = coroutineScope {
    val velocityTracker = VelocityTracker()
    var mode = ZoomGestureMode.NONE
    var clicking = false
    var doubleClicking = false
    var singleTapPending = false
    var downTime = 0L
    var lastUpTime = 0L
    var downPosition = Offset.Zero
    var zoomInitialDist = 0f
    var flingActive = false

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.fastFilter { it.pressed }
            val eventTimeMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L

            when (event.type) {
                PointerEventType.Press -> {
                    when (pressed.size) {
                        1 -> {
                            flingActive = false
                            clicking = true
                            doubleClicking =
                                (eventTimeMs - lastUpTime) < DOUBLE_CLICK_DURATION_MS
                                        && (pressed[0].position - downPosition).getDistance() < DOUBLE_CLICK_DISTANCE_THRESHOLD_PX
                            if (doubleClicking) {
                                singleTapPending = false
                            }
                            downTime = eventTimeMs
                            downPosition = pressed[0].position
                            mode = ZoomGestureMode.DRAG
                            onPanStart()
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(eventTimeMs, pressed[0].position)
                            // Consume immediately when pannable so the pager never starts
                            // tracking this gesture as a horizontal swipe
                            if (isDraggable()) {
                                event.changes.fastForEach { it.consume() }
                            }
                        }

                        2 -> {
                            clicking = false
                            val dist = (pressed[1].position - pressed[0].position).getDistance()
                            if (dist > MIN_PINCH_DIST_PX) {
                                zoomInitialDist = dist
                                mode = ZoomGestureMode.ZOOM
                                val mid = (pressed[0].position + pressed[1].position) / 2f
                                onZoomStart(mid.x, mid.y)
                            }
                            // Always consume multi-finger events — pager must not handle them
                            event.changes.fastForEach { it.consume() }
                        }

                        else -> {
                            mode = ZoomGestureMode.NONE
                            clicking = false
                        }
                    }
                }

                PointerEventType.Move -> {
                    when (mode) {
                        ZoomGestureMode.DRAG if pressed.size == 1 -> {
                            val pos = pressed[0].position
                            velocityTracker.addPosition(eventTimeMs, pos)
                            val delta = pos - downPosition
                            if (delta.getDistance() > CLICK_MOVE_THRESHOLD_PX) {
                                clicking = false
                            }
                            onPan(delta.x, delta.y)
                            // Consume so the pager doesn't interpret this as a page swipe
                            if (isDraggable()) {
                                event.changes.fastForEach { it.consume() }
                            }
                        }

                        ZoomGestureMode.ZOOM if pressed.size >= 2 -> {
                            val newDist = (pressed[1].position - pressed[0].position).getDistance()
                            if (newDist > MIN_PINCH_DIST_PX && zoomInitialDist > 0f) {
                                onZoom(newDist / zoomInitialDist)
                            }
                            // Always consume pinch events
                            event.changes.fastForEach { it.consume() }
                        }

                        else -> {}
                    }
                }

                PointerEventType.Release -> {
                    val allReleased = event.changes.fastAll { !it.pressed }
                    when {
                        // One finger lifted during pinch — keep panning with the other
                        !allReleased && mode == ZoomGestureMode.ZOOM -> {
                            if (pressed.size == 1) {
                                val pos = pressed[0].position
                                mode = ZoomGestureMode.DRAG
                                clicking = false
                                downPosition = pos
                                onPanStart()
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(eventTimeMs, pos)
                            }
                        }

                        allReleased -> {
                            // Fling check (only when dragging, not clicking)
                            if (mode == ZoomGestureMode.DRAG && !clicking) {
                                val velocity = velocityTracker.calculateVelocity()
                                val speedSq = velocity.x * velocity.x + velocity.y * velocity.y
                                if (speedSq > FLING_STOP_THRESHOLD_SQ && isDraggable()) {
                                    flingActive = true
                                    var flingVx = velocity.x
                                    var flingVy = velocity.y
                                    var lastNanos = System.nanoTime()
                                    launch {
                                        while (flingActive) {
                                            withFrameNanos { frameNanos ->
                                                val elapsed =
                                                    (frameNanos - lastNanos) / 1_000_000_000.0
                                                lastNanos = frameNanos
                                                onFlingBy(
                                                    (elapsed * flingVx).toFloat(),
                                                    (elapsed * flingVy).toFloat()
                                                )
                                                val ratio = FLING_INERTIA.pow(elapsed).toFloat()
                                                flingVx *= ratio
                                                flingVy *= ratio
                                                if (flingVx * flingVx + flingVy * flingVy < FLING_STOP_THRESHOLD_SQ) {
                                                    flingActive = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            mode = ZoomGestureMode.NONE

                            // Tap detection
                            if (clicking && (eventTimeMs - downTime) < CLICK_DURATION_MS) {
                                lastUpTime = eventTimeMs
                                if (doubleClicking) {
                                    onDoubleTap(downPosition.x, downPosition.y)
                                } else if (!onSingleTapImmediate(downPosition.x, downPosition.y)) {
                                    // Delay the single tap so a following tap can be recognised
                                    // as a double tap instead
                                    singleTapPending = true
                                    val tapToken = eventTimeMs
                                    launch {
                                        delay(DOUBLE_CLICK_DURATION_MS.milliseconds)
                                        if (singleTapPending && lastUpTime == tapToken) {
                                            singleTapPending = false
                                            onSingleTap()
                                        }
                                    }
                                }
                            }
                            clicking = false
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
