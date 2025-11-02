/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.discussion.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


@Composable
fun SwipeForActionBox(
    modifier: Modifier = Modifier,
    maxOffset: Dp,
    enabledFromStartToEnd: Boolean = true,
    enabledFromEndToStart: Boolean = true,
    backgroundContentFromStartToEnd: @Composable (BoxScope.(progress: Float) -> Unit)? = null,
    backgroundContentFromEndToStart: @Composable (BoxScope.(progress: Float) -> Unit)? = null,
    callbackStartToEnd:  (() -> Unit)? = null,
    callbackEndToStart:  (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var offset: Int by remember {
        mutableIntStateOf(0)
    }
    var resetOffset by remember {
        mutableStateOf(false)
    }

    val maxOffsetPx = with(LocalDensity.current) { maxOffset.toPx().roundToInt() }

    LaunchedEffect(resetOffset) {
        if (resetOffset) {
            val durationMs = 150L
            val initialOffset = offset

            var playTime = 0L
            val startTime = withFrameNanos { it }
            do {
                offset = ((initialOffset * (durationMs - playTime)) / durationMs).toInt()
                playTime = (withFrameNanos { it } - startTime) / 1_000_000
            } while (playTime < durationMs)

            offset = 0
            resetOffset = false
        }
    }

    Box(
        modifier = modifier
            .pointerInput(maxOffset, enabledFromEndToStart, enabledFromStartToEnd, callbackStartToEnd, callbackEndToStart) {
                var initialOffset : Offset? = null
                var isSwiping = false
                val mnOffset = if (enabledFromEndToStart) -maxOffsetPx else 0
                val mxOffset = if (enabledFromStartToEnd) maxOffsetPx else 0


                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size == 1) {
                            val change = event.changes[0]
                            if (!change.isConsumed) {
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        initialOffset = change.position
                                        isSwiping = false
                                        continue
                                    }

                                    PointerEventType.Move -> {
                                        initialOffset?.let {
                                            val offsetDiff = if (isRtl) it - change.position else change.position - it
                                            if (!isSwiping && offsetDiff.x.absoluteValue > 16 * density) {
                                                if (offsetDiff.y.absoluteValue > offsetDiff.x.absoluteValue * .5f) {
                                                    initialOffset = null
                                                } else {
                                                    initialOffset = change.position
                                                    isSwiping = true
                                                }
                                            } else if (isSwiping) {
                                                change.consume()
                                                offset = offsetDiff.x.roundToInt().coerceIn(mnOffset, mxOffset)
                                            }
                                        }
                                        continue
                                    }

                                    PointerEventType.Release -> {
                                        if (initialOffset != null && isSwiping) {
                                            change.consume()
                                            if (offset == maxOffsetPx) {
                                                callbackStartToEnd?.invoke()
                                            } else if (offset == -maxOffsetPx) {
                                                callbackEndToStart?.invoke()
                                            }
                                        }
                                    }

                                    else -> continue
                                }
                            }
                        }

                        // if we did not call continue in the previous when, then the swipe is finished or cancelled --> return to standard state
                        initialOffset = null
                        if (offset != 0) {
                            resetOffset = true
                        }
                    }
                }
            }
    ) {
        if (offset > 0) {
            backgroundContentFromStartToEnd?.let { backgroundContent ->
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(offset.toFloat() / maxOffsetPx)
                ) {
                    backgroundContent(offset.toFloat() / maxOffsetPx)
                }
            }
        }
        if (offset < 0) {
            backgroundContentFromEndToStart?.let { backgroundContent ->
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(-offset.toFloat() / maxOffsetPx)
                ) {
                    backgroundContent(-offset.toFloat() / maxOffsetPx)
                }
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(offset, 0)
                }
        ) {
            content.invoke()
        }
    }
}