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

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DarkGradientBackground(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color.Black
            )
    ) {
        val sizePx: Size = with(LocalDensity.current) {
            Size(maxWidth.toPx(), maxHeight.toPx())
        }

        if (Build.VERSION.SDK_INT < 31) {
            Box(
                modifier = Modifier
                    .offset(x = (-80).dp, y = (-150).dp)
                    .requiredSize(800.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x662F65F5),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "blue")
            val blueOffset = remember { (Math.random() * 2* PI).toFloat() }
            val blueAngle by infiniteTransition.animateFloat(
                initialValue = blueOffset,
                targetValue = blueOffset + 2*PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(30_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            val greenOffset = remember { (Math.random() * 2* PI).toFloat() }
            val greenAngle by infiniteTransition.animateFloat(
                initialValue = greenOffset,
                targetValue = greenOffset + 2*PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(23_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = sizePx.width * (.16 + .4 * cos(blueAngle - .75*PI)).toFloat()
                        translationY = sizePx.height * (.25 + .15 * sin(blueAngle - .6*PI)).toFloat()
                    }
                    .width(275.dp)
                    .height(240.dp)
                    .blur(radius = 250.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        color = Color(0xFF2F65F5),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = sizePx.width * (.25 + .15 * cos(greenAngle)).toFloat()
                        translationY = sizePx.height * (.45 - .07 * sin(greenAngle + .25*PI)).toFloat()
                    }
                    .size(300.dp)
                    .blur(radius = 320.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6BB700),
                                Color(0xFF2F65F5)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        content()
    }
}

@Preview(apiLevel = 30)
@Preview
@Composable
fun DarkGradientBackgroundPreview() {
    DarkGradientBackground(content = {})
}

