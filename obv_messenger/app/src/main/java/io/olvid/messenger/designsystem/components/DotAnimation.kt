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


package io.olvid.messenger.designsystem.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R

@Composable
fun DotAnimation(
    modifier: Modifier = Modifier,
    dotColor: Color = colorResource(R.color.greyTint),
    dotSize: Dp = 4.dp,
    spacing: Dp = 5.dp,
    scaleTo: Float = 2f
) {
    val totalDuration = 1200 // A total cycle for all dots to animate and have a pause

    val infiniteTransition = rememberInfiniteTransition(label = "dot_animation_transition")
    val masterClock = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = totalDuration.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = totalDuration, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "master_clock"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (i in 0..2) {
            val startTime = i * 200
            val scaleX = getScaleForDot(masterClock = masterClock.value, startTime = startTime, scaleTo = scaleTo)
            val scaleY = getScaleForDot(masterClock = masterClock.value, startTime = startTime, timeOffset = 50, scaleTo = scaleTo)

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                    }
                    .background(color = dotColor, shape = CircleShape)
            )
        }
    }
}

private fun getScaleForDot(masterClock: Float, startTime: Int, timeOffset: Int = 0, scaleTo: Float = 1.5f): Float {
    val animStartTime = (startTime + timeOffset).toFloat()
    val animEndTime = animStartTime + 600 // 300ms up, 300ms down

    if (masterClock !in animStartTime..animEndTime) {
        return 1f // Not in animation phase
    }

    val progress = (masterClock - animStartTime) / 300f
    return if (progress <= 1.0f) {
        // Scaling up
        1f + (scaleTo - 1f) * progress
    } else {
        // Scaling down
        scaleTo - (scaleTo - 1f) * (progress - 1f)
    }
}


@Preview(showBackground = true)
@Composable
private fun DotAnimationPreview() {
    DotAnimation()
 }
