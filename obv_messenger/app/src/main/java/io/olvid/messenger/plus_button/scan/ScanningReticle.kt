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

package io.olvid.messenger.plus_button.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R

@Composable
fun ScanningReticle(brush: Brush, fraction: Float = 0.6f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cornerRadius = 24.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val boxSize = (size.width.coerceAtMost(size.height) * fraction).coerceAtLeast(160.dp.toPx())

        val topLeft = Offset(
            x = (size.width - boxSize) / 2,
            y = (size.height - boxSize) / 2 + 24.dp.toPx()
        )

        with(drawContext.canvas.nativeCanvas) {
            val checkpoint = saveLayer(null, null)
            // Semi-transparent background - extend height to draw under the rounded corners of the overlay
            drawRect(
                color = Color.Black.copy(alpha = 0.8f),
                size = Size(size.width, size.height + 48.dp.toPx())
            )

            // Clear center rect
            drawRoundRect(
                topLeft = topLeft,
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(cornerRadius),
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )

            restoreToCount(checkpoint)
        }

        with(drawContext.canvas.nativeCanvas) {
            val checkpoint = saveLayer(null, null)
            // border
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(topLeft, Size(boxSize, boxSize)),
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                )
            }

            val pathMeasure = PathMeasure()
            pathMeasure.setPath(path, false)

            drawPath(
                path = path,
                brush = brush,
                style = Stroke(width = strokeWidth)
            )

            drawRect(
                topLeft = topLeft.copy(x = topLeft.x - strokeWidth, y = topLeft.y + boxSize / 4),
                size = Size(boxSize + 2 * strokeWidth, 2 * boxSize / 4),
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )

            drawRect(
                topLeft = topLeft.copy(x = topLeft.x + boxSize / 4, y = topLeft.y - strokeWidth),
                size = Size(2 * boxSize / 4, boxSize + 2 * strokeWidth),
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )


            restoreToCount(checkpoint)
        }
    }
}

@Preview
@Composable
fun ScanViewfinderOverlayPreview() {
    ScanningReticle(brush = SolidColor(Color.White))
}

@Preview
@Composable
fun ScanViewfinderOverlayGradientPreview() {
    ScanningReticle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF6BB700),
                colorResource(R.color.olvid_gradient_light)
            )
        )
    )
}
