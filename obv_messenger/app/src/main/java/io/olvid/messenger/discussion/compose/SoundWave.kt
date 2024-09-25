/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.discussion.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding


@Composable
fun SoundWave(sample: SampleAndTicker, stop: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(36.dp)
    ) {
        Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(modifier = Modifier
                    .requiredSize(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 16.dp)
                    ) { stop() },
                    tint = colorResource(id = R.color.olvid_gradient_light),
                    painter = painterResource(id = R.drawable.ic_stop),
                    contentDescription = stringResource(
                        id = R.string.button_label_cancel
                    )
                )
            Text(
                modifier = Modifier.padding(4.dp),
                text = AudioAttachmentServiceBinding.timeFromMs(sample.size * VoiceMessageRecorder.SAMPLE_INTERVAL)
            )

            Canvas(
                modifier = Modifier
                    .weight(1f, true)
                    .requiredHeight(36.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val waveWidth = 3.dp.toPx()
                val waveHeight = canvasHeight * .75f
                val cornerRadius = CornerRadius(24.dp.toPx())

                val offset = (sample.ticker * waveWidth * 2) / SampleAndTicker.TICKS_PER_SAMPLE
                sample.samples.take((canvasWidth / waveWidth / 2).toInt() + 1).forEachIndexed { index, value ->
                    val height = (value * waveHeight).coerceAtLeast(waveWidth)
                    drawRoundRect(
                        color = Color.Gray,
                        topLeft = Offset(
                            x = canvasWidth - index * waveWidth * 2 - offset,
                            y = canvasHeight / 2 - height / 2
                        ),
                        size = Size(width = waveWidth, height = height),
                        cornerRadius = cornerRadius
                    )
                }
            }
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

@Preview(widthDp = 200)
@Preview(widthDp = 400)
@Composable
fun SoundWavePreview() {
    AppCompatTheme {
        SoundWave(SampleAndTicker(samples = mutableListOf(1f, .5f))) {}
    }
}