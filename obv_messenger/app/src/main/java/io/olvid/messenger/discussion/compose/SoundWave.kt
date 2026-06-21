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

package io.olvid.messenger.discussion.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.Companion.timeFromMs
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun SoundWave(
    modifier: Modifier = Modifier,
    sample: SampleAndTicker,
    showStopButton: Boolean = true,
    stop: () -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showStopButton) {
            Icon(
                modifier = Modifier
                    .requiredSize(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = false,
                            radius = 16.dp,
                            color = colorResource(R.color.olvid_gradient_light)
                        )
                    ) { stop() },
                tint = colorResource(id = R.color.olvid_gradient_light),
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = stringResource(
                    id = R.string.button_label_cancel
                )
            )
        }
        Text(
            modifier = Modifier.padding(8.dp),
            text = timeFromMs(sample.size * VoiceMessageRecorder.SAMPLE_INTERVAL),
            maxLines = 1,
            style = OlvidTypography.body1.copy(
                color = colorResource(R.color.almostBlack)
            )
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
            sample.samples.take((canvasWidth / waveWidth / 2).toInt() + 1)
                .forEachIndexed { index, value ->
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
        Spacer(modifier = Modifier.width(12.dp))
    }
}

@Preview(widthDp = 200)
@Preview(widthDp = 400)
@Composable
fun SoundWavePreview() {
    SoundWave(sample = SampleAndTicker(samples = mutableListOf(1f, .5f, 0f, .7f))) {}
}

@Composable
fun StaticSoundWave(
    modifier: Modifier = Modifier,
    amplitudes: List<Float>,
    color: Color = Color.Gray,
    progress: Float = 0f,
    playtimeMs: Long? = null,
    durationMs: Long? = null,
    onSeek: ((Float) -> Unit)? = null,
    onSeekStarted: (() -> Unit)? = null,
    onSeekEnded: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playtimeMs != null) {
            Text(
                modifier = Modifier.padding(end = 4.dp),
                text = timeFromMs(playtimeMs),
                maxLines = 1,
                style = OlvidTypography.subtitle1.copy(
                    color = colorResource(id = R.color.greyTint)
                )
            )
        }

        val density = LocalDensity.current
        var canvasWidth by remember { mutableFloatStateOf(0f) }

        val barWidth = with(density) { 3.dp.toPx() }
        val stride = with(density) { 3.dp.toPx() * 2 }
        
        val sampledAmplitudes = remember(amplitudes, canvasWidth) {
            val maxBars = ((canvasWidth - barWidth) / stride).toInt() + 1
            if (maxBars <= 0) return@remember emptyList()
            if (amplitudes.isEmpty()) return@remember emptyList()

            List(maxBars) { i ->
                val start = (i * amplitudes.size).toFloat() / maxBars
                val startInt = start.toInt().coerceIn(0, amplitudes.size - 1)
                val end = ((i + 1) * amplitudes.size).toFloat() / maxBars
                val endInt = end.toInt().coerceIn(0, amplitudes.size)
                if (startInt == endInt) {
                    // interval is entirely inside one amplitude --> return it
                    return@List amplitudes[startInt]
                } else {
                    // interval spans several different amplitudes --> compute a weighted average
                    var sum = amplitudes.subList(startInt, endInt).sum()
                    if (endInt < amplitudes.size) {
                        sum += (end - endInt) * amplitudes[endInt]
                    }
                    sum -= (start - startInt) * amplitudes[startInt]
                    return@List sum / (end - start)
                }
            }
        }

        val animations = remember(sampledAmplitudes.isEmpty()) {
            List(sampledAmplitudes.size) { Animatable(0f) }
        }

        LaunchedEffect(sampledAmplitudes) {
            animations.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 10L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    canvasWidth = coordinates.size.width.toFloat()
                }
                .then(
                    if (onSeek == null) Modifier
                    else Modifier
                        .pointerInput(onSeek) {
                            // detectTapGestures only fires on a confirmed tap (up without
                            // crossing slop), so vertical scrolls in the parent list are
                            // not interpreted as seeks.
                            detectTapGestures { offset ->
                                val w = canvasWidth
                                if (w > 0f) {
                                    onSeek((offset.x / w).coerceIn(0f, 1f))
                                }
                            }
                        }
                        .pointerInput(onSeek) {
                            detectHorizontalDragGestures(
                                onDragStart = { onSeekStarted?.invoke() },
                                onDragEnd = { onSeekEnded?.invoke() },
                                onDragCancel = { onSeekEnded?.invoke() },
                            ) { change, _ ->
                                val w = canvasWidth
                                if (w > 0f) {
                                    onSeek((change.position.x / w).coerceIn(0f, 1f))
                                }
                            }
                        }
                )
        ) {
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val canvasWidthPrecise = size.width
            val canvasHeight = size.height

            val waveWidthPx = 3.dp.toPx()
            val stridePx = waveWidthPx * 2

            val waveHeight = canvasHeight * .75f
            val cornerRadius = CornerRadius(24.dp.toPx())

            drawContext.canvas.saveLayer(size.toRect(), Paint())

            sampledAmplitudes.forEachIndexed { index, value ->
                val x = if (isRtl) canvasWidthPrecise - waveWidthPx - (index * stridePx) else index * stridePx
                if (x < 0 || x + waveWidthPx > canvasWidthPrecise) return@forEachIndexed

                val scale = animations.getOrNull(index)?.value ?: 1f
                val height = (value * waveHeight).coerceAtLeast(waveWidthPx) * scale

                drawRoundRect(
                    color = color,
                    alpha = 1f,
                    topLeft = Offset(
                        x = x,
                        y = canvasHeight / 2 - height / 2
                    ),
                    size = Size(width = waveWidthPx, height = height),
                    cornerRadius = cornerRadius
                )
            }
            // apply alpha based on progress
            drawRect(
                color = Color.Black.copy(alpha = .4f),
                topLeft = if (isRtl) Offset.Zero else Offset(canvasWidthPrecise * progress, 0f),
                size = Size(canvasWidthPrecise * (1f-progress), canvasHeight),
                blendMode = BlendMode.DstIn
            )

            drawContext.canvas.restore()
        }

        if (durationMs != null) {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = timeFromMs(durationMs),
                maxLines = 1,
                style = OlvidTypography.subtitle1.copy(
                    color = colorResource(R.color.greyTint)
                )
            )
        }
    }
}