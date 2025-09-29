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

package io.olvid.messenger.designsystem.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DarkGradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = Color.Black
            )
    ) {
        if (Build.VERSION.SDK_INT < 31) {
            Box(
                modifier = Modifier
                    .offset(x = (-80).dp, y = (-150).dp)
                    .requiredSize(800.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x552F65F5),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = (-27).dp)
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
                    .offset(x = 86.dp, y = 256.dp)
                    .size(300.dp).blur(radius = 350.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
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

@Preview
@Preview(apiLevel = 30)
@Composable
fun DarkGradientBackgroundPreview() {
    DarkGradientBackground(content = {})
}

