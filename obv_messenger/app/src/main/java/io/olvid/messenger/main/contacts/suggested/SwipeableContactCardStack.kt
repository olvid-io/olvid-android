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

package io.olvid.messenger.main.contacts.suggested

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import io.olvid.messenger.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

enum class SwipeDirection { LEFT, RIGHT }

private const val DISMISS_FRACTION = .35f
private const val VELOCITY_THRESHOLD_PX_S = 1000f
private const val MAX_ROTATION_DEG = 15f
// rotation anchor: top-center and high above the card, so rotation alone swings the card sideways
private val TRANSFORM_ORIGIN = TransformOrigin(0.5f, -2.5f)
private val CARD_SHAPE = RoundedCornerShape(32.dp)
private const val VISIBLE_CARDS = 4

// front-most -> back-most
private val BACK_SCALE = floatArrayOf(1f, .875f, .75f, .625f)
private val BACK_ALPHA = floatArrayOf(1f, .75f, .5f, 0f)
private val BACK_Y = arrayOf(0.dp, 12.dp, 22.dp, 30.dp)

private val SETTLE_SPEC = spring<Float>(dampingRatio = .6f, stiffness = Spring.StiffnessMedium)
private val DISMISS_SPEC = tween<Float>(durationMillis = 240, easing = FastOutLinearInEasing)

/**
 * Ephemeral UI state for the dating-app-style stack: which card is on top, and the horizontal drag
 * amount of the front card.
 */
class SwipeStackState(
    val currentIndex: MutableIntState,
    val itemCount: Int,
    private val scope: CoroutineScope,
) {
    // horizontal drag in px; drives the front card's rotation and the swipe progress
    var dragX by mutableFloatStateOf(0f)
        private set
    var containerWidthPx by mutableFloatStateOf(1f)
    // the single in-flight settle/fling animation; a new drag or swipe cancels it
    private var animationJob: Job? = null

    val isFinished: Boolean get() = currentIndex.intValue >= itemCount

    /** -1f (full left) ... +1f (full right), normalized against the dismiss threshold (steep) */
    val swipeProgress: Float
        get() = (dragX / (containerWidthPx * DISMISS_FRACTION)).coerceIn(-1f, 1f)

    /** -1f ... +1f normalized against the full card width: only saturates near a full fling (gentle) */
    val washProgress: Float
        get() = (dragX / containerWidthPx).coerceIn(-1f, 1f)

    fun drag(dx: Float) {
        animationJob?.cancel()
        animationJob = null
        dragX += dx
    }

    /**
     * Scripted tutorial swipe: drags the front card halfway in [direction] as a finger would, holds
     * it a beat so the wash and badge are readable, calls [onRelease] (the finger lifts off), then
     * flings it out and advances the deck — exactly what a real swipe does, minus the side effect.
     * Suspends until the card is gone.
     */
    suspend fun demoSwipe(direction: SwipeDirection, onRelease: () -> Unit = {}) {
        if (isFinished) return
        animationJob?.cancel()
        val sign = if (direction == SwipeDirection.RIGHT) 1f else -1f
        animate(
            dragX,
            sign * containerWidthPx * 0.45f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        ) { value, _ -> dragX = value }
        delay(400.milliseconds)
        onRelease()
        animate(dragX, sign * 1.6f * containerWidthPx, animationSpec = DISMISS_SPEC) { value, _ -> dragX = value }
        Snapshot.withMutableSnapshot {
            currentIndex.intValue += 1
            dragX = 0f
        }
    }

    fun snapBack() {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(dragX, 0f, animationSpec = SETTLE_SPEC) { value, _ -> dragX = value }
        }
    }

    fun settle(velocityX: Float, onDismissed: (SwipeDirection) -> Unit) {
        val passedDistance = abs(dragX) > containerWidthPx * DISMISS_FRACTION
        val passedVelocity = abs(velocityX) > VELOCITY_THRESHOLD_PX_S
        if (passedDistance || passedVelocity) {
            animateOut(if (dragX >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT, onDismissed)
        } else {
            snapBack()
        }
    }

    fun swipe(direction: SwipeDirection, onDismissed: (SwipeDirection) -> Unit) =
        animateOut(direction, onDismissed)

    /** Arc the front card off-screen, run the side effect, then advance + reset atomically. */
    private fun animateOut(direction: SwipeDirection, onDismissed: (SwipeDirection) -> Unit) {
        if (isFinished) return
        animationJob?.cancel()
        val sign = if (direction == SwipeDirection.RIGHT) 1f else -1f
        animationJob = scope.launch {
            animate(dragX, sign * 1.6f * containerWidthPx, animationSpec = DISMISS_SPEC) { value, _ ->
                dragX = value
            }
            onDismissed(direction)
            // advance the index and reset the drag in a single snapshot, so no frame ever renders
            // with the new index but the old (flung) drag value — which is what caused the glitch.
            Snapshot.withMutableSnapshot {
                currentIndex.intValue += 1
                dragX = 0f
            }
        }
    }
}

@Composable
fun rememberSwipeStackState(itemCount: Int): SwipeStackState {
    val scope = rememberCoroutineScope()
    val currentIndex = rememberSaveable(itemCount) { mutableIntStateOf(0) }
    return remember(itemCount) { SwipeStackState(currentIndex, itemCount, scope) }
}

@Composable
fun SwipeableContactCardStack(
    state: SwipeStackState,
    items: List<SuggestedContactItem>,
    onMoreInfo: (SuggestedContactItem) -> Unit,
    onDismissed: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    currentCardUiState: MutableState<SuggestedContactUiState?>?,
    gesturesEnabled: Boolean = true,
) {
    // keep the latest callback without restarting the (stable) container gesture
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    BoxWithConstraints(
        modifier = modifier
            .sizeIn(maxWidth = CARD_WIDTH.dp, maxHeight = CARD_HEIGHT.dp)
            .wrapContentSize(Alignment.Center)
            .aspectRatio(CARD_ASPECT)
            .then(
                if (gesturesEnabled) Modifier.pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            state.drag(dragAmount.x)
                        },
                        onDragEnd = { state.settle(tracker.calculateVelocity().x) { direction ->
                            latestOnDismissed(direction)
                        } },
                        onDragCancel = { state.snapBack() },
                    )
                } else Modifier
            ),
    ) {
        state.containerWidthPx = constraints.maxWidth.toFloat()

        // Render only the visible window over the full (uncapped) list, back-to-front so the front
        // card draws on top. As the window slides the cards are matched by their stable key, so a
        // card sliding back -> front keeps its (skippable) body and its DAO observers — only the one
        // newly-entering back card mounts.
        val from = state.currentIndex.intValue
        val to = minOf(items.size, from + VISIBLE_CARDS)
        for (index in (to - 1) downTo from) {
            val item = items[index]
            val depth = index - from
            key(item.key) {
                val cardState = rememberSuggestedContactUiState(item)
                LaunchedEffect(depth, cardState) {
                    if (depth == 0) {
                        currentCardUiState?.value = cardState
                    }
                }
                val onCardMoreInfo = remember(item, onMoreInfo) { { onMoreInfo(item) } }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (depth == 0) {
                                // front: anchored, rotation only (negated to follow the finger)
                                rotationZ = -(state.dragX / state.containerWidthPx) * MAX_ROTATION_DEG
                                transformOrigin = TRANSFORM_ORIGIN
                                shadowElevation = 12.dp.toPx()
                                shape = CARD_SHAPE
                            } else {
                                // back cards rise toward the front-ward slot as the swipe progresses
                                val p = abs(state.swipeProgress)
                                val s = lerp(BACK_SCALE[depth], BACK_SCALE[depth - 1], p)
                                scaleX = s
                                scaleY = s
                                alpha = lerp(BACK_ALPHA[depth], BACK_ALPHA[depth - 1], p)
                                translationY = lerp(BACK_Y[depth], BACK_Y[depth - 1], p).toPx()
                                // scale about the bottom so the yOffset makes the card peek below
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                shadowElevation = 12.dp.toPx()
                                shape = CARD_SHAPE
                            }
                        },
                ) {
                    SuggestedContactCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        state = cardState,
                        onMoreInfo = onCardMoreInfo,
                    )
                    if (depth == 0) {
                        // progress is read in the draw/layer phase, so dragging never recomposes
                        SwipeColorWash(washProgress = { state.washProgress })
                        SwipeBadges(swipeProgress = { state.swipeProgress })
                    }
                }
            }
        }
    }
}

/**
 * Full-card colour wash matching the Figma "Scroll Direction" flung-card states (solid #008f61 / red).
 * [washProgress] is read at draw time so the swipe never recomposes this subtree.
 */
@Composable
private fun BoxScope.SwipeColorWash(washProgress: () -> Float) {
    val red = colorResource(R.color.red)
    val green = colorResource(R.color.green)
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(CARD_SHAPE)
            .drawBehind {
                val p = washProgress()
                when {
                    p > 0f -> drawRect(green.copy(alpha = p.coerceIn(0f, 1f)))
                    p < 0f -> drawRect(red.copy(alpha = (-p).coerceIn(0f, 1f)))
                }
            },
    )
}

/**
 * On-card swipe indicators, matching the Figma flung-card badge: a white circle with a coloured
 * glyph — green check at the top-left (right/invite swipe), red cross at the top-right (left/skip).
 * [swipeProgress] is read inside graphicsLayer so the swipe never recomposes these.
 */
@Composable
private fun BoxScope.SwipeBadges(swipeProgress: () -> Float) {
    SwipeBadge(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
            .graphicsLayer { alpha = (swipeProgress() - .25f).coerceIn(0f, 1f) },
        icon = R.drawable.ic_ok,
        tint = colorResource(R.color.green),
    )
    SwipeBadge(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .graphicsLayer { alpha = (-swipeProgress() - .25f).coerceIn(0f, 1f) },
        icon = R.drawable.ic_close,
        tint = colorResource(R.color.red),
    )
}

@Composable
private fun SwipeBadge(
    modifier: Modifier,
    icon: Int,
    tint: Color,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.alwaysWhite)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(icon),
            tint = tint,
            contentDescription = null,
        )
    }
}
