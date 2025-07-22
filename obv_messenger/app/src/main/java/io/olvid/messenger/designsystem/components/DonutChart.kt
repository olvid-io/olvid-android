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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class DonutChartData(
    val label: String,
    val percentage: Double,
    val color: Color,
)

enum class DonutChartAnimationMode {
    ONCE,
    ON_DATA_CHANGE,
    NEVER
}

@Composable
fun DonutChart(
    data: List<DonutChartData>,
    modifier: Modifier = Modifier,
    strokeRatio: Float = 0.7f, // Added for better donut appearance: use 1f for full circle
    shrinkRatio: Float = 0.8f, // How much to shrink elements that are not the largest: use 1f for no shrink
    textSize: Float = 12f,
    gap: Dp = 3.dp,
    animationMode: DonutChartAnimationMode = DonutChartAnimationMode.ON_DATA_CHANGE
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available")
        }
        return
    }

    if (data.any { it.percentage < 0}) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Invalid data: Total percentage is zero or negative.")
        }
        return
    }

    val totalPercentage = data.sumOf { it.percentage }
    val degToRad = (PI/180).toFloat()
    val gapWidth = with(LocalDensity.current) { gap.toPx() }

    var snapshotValues by remember {
        mutableStateOf(emptyMap<String, Triple<Float, Float, Color>>())
    }
    var targetValues by remember {
        mutableStateOf(emptyMap<String, Triple<Float, Float, Color>>())
    }
    var keyOrder by remember {
        mutableStateOf(emptyList<String>())
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data, animationMode) {
        if (animatedProgress.value == 1f) {
            // if the animation if finished, use the previous targetValue as start snapshot
            snapshotValues = targetValues
        } else if (animatedProgress.value != 0f) {
            // if we are in the middle of an animation, compute the exact position we are at to restart the animation
            snapshotValues = targetValues.keys.union(snapshotValues.keys).associateWith {
                Triple(
                    animatedProgress.value * (targetValues[it]?.first ?: 0f) + (1 - animatedProgress.value) * (snapshotValues[it]?.first ?: 0f),
                    animatedProgress.value * (targetValues[it]?.second ?: snapshotValues[it]?.second ?: 0f) + (1 - animatedProgress.value) * (snapshotValues[it]?.second ?: targetValues[it]?.second ?: 0f),
                    targetValues[it]?.third ?: snapshotValues[it]?.third ?: Color.Transparent
                )
            }
        }

        // target value only depends on the input data
        targetValues = data.associate {
            it.label to Triple(
                (it.percentage / totalPercentage * 360).toFloat(),
                if (data.any {other -> other.percentage > it.percentage }) 1f else 0f,
                it.color
            )
        }

        // compute the keyOrder to use, based on the latest data, but also the previous keyOrder (removing any key that is neither in the snapshot nor the target)
        val dataLabels = data.map { it.label }
        keyOrder = buildList {
            var dataPos = 0
            var keyPos = 0
            while (dataPos < dataLabels.size && keyPos < keyOrder.size) {
                val dataLabel = dataLabels[dataPos]
                val keyLabel = keyOrder[keyPos]
                if (dataLabel == keyLabel) {
                    add(dataLabel)
                    dataPos++
                    keyPos++
                } else {
                    val dataPosInKeys = keyOrder.subList(keyPos + 1, keyOrder.size).indexOf(dataLabel)
                    if (dataPosInKeys != -1) {
                        addAll(keyOrder.subList(keyPos, keyPos + 1 + dataPosInKeys))
                        keyPos = keyPos + 2 + dataPosInKeys
                        add(dataLabel)
                        dataPos++
                    } else {
                        val keyPosInData = dataLabels.subList(dataPos + 1, dataLabels.size).indexOf(keyLabel)
                        if (keyPosInData != -1) {
                            addAll(dataLabels.subList(dataPos, dataPos + 2 + keyPosInData))
                            dataPos = dataPos + 2 + keyPosInData
                            keyPos++
                        } else {
                            add(dataLabel)
                            dataPos++
                        }
                    }
                }
            }

            // add all remaining elements in the list that is not finished
            if (dataPos < data.size) {
                addAll(dataLabels.subList(dataPos, dataLabels.size))
            } else if (keyPos < keyOrder.size) {
                addAll(keyOrder.subList(keyPos, keyOrder.size))
            }

            // remove any key that is no longer in the snapshot or the target
            retainAll { targetValues.keys.union(snapshotValues.keys).contains(it) }
        }


        when (animationMode) {
            DonutChartAnimationMode.ONCE -> {
                if (animatedProgress.value == 0f) {
                    animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
                }
            }
            DonutChartAnimationMode.ON_DATA_CHANGE -> {
                animatedProgress.snapTo(0f)
                animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
            }
            DonutChartAnimationMode.NEVER -> {
                animatedProgress.snapTo(1f)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = modifier
            .aspectRatio(1f) // Ensure the canvas is square for a circular chart
            .padding(16.dp)
        ) {
            val canvasSize = minOf(size.width, size.height)
            val chartRadius = canvasSize * (1 - strokeRatio / 2) / 2
            val shrunkenRadius = chartRadius * (1 - strokeRatio + strokeRatio * shrinkRatio / 2) / (1 - strokeRatio / 2)

            val drawingCenter = center

            // Draw the donut segments using Stroke style
            var currentStartAngle = -90f // Start from the top

            with(drawContext.canvas.nativeCanvas) {
                val checkpoint = saveLayer(null, null)
                keyOrder.forEach { key ->
                    val sweepAngle = animatedProgress.value * (targetValues[key]?.first ?: 0f) + (1 - animatedProgress.value) * (snapshotValues[key]?.first ?: 0f)
                    val shrinkAmount = animatedProgress.value * (targetValues[key]?.second ?: snapshotValues[key]?.second ?: 0f) + (1 - animatedProgress.value) * (snapshotValues[key]?.second ?: targetValues[key]?.second ?: 0f)

                    drawArc(
                        color = targetValues[key]?.third ?: snapshotValues[key]?.third ?: Color.Transparent,
                        startAngle = currentStartAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false, // Set to false for Stroke style
                        topLeft = Offset(
                            drawingCenter.x - (shrinkAmount * shrunkenRadius + (1 - shrinkAmount) * chartRadius),
                            drawingCenter.y - (shrinkAmount * shrunkenRadius + (1 - shrinkAmount) * chartRadius)
                        ),
                        size = Size(
                            2 * (shrinkAmount * shrunkenRadius + (1 - shrinkAmount) * chartRadius),
                            2 * (shrinkAmount * shrunkenRadius + (1 - shrinkAmount) * chartRadius)
                        ),
                        style = Stroke(
                            width = (canvasSize / 2) * strokeRatio * (shrinkAmount * shrinkRatio + (1 - shrinkAmount)),
                        )
                    )

                    if (gapWidth > 0) {
                        drawLine(
                            color = Color.Transparent,
                            start = drawingCenter,
                            end = drawingCenter + Offset(canvasSize * cos(currentStartAngle * degToRad), canvasSize * sin(currentStartAngle * degToRad)),
                            strokeWidth = gapWidth,
                            blendMode = BlendMode.Clear
                        )
                    }

                    currentStartAngle += sweepAngle
                }

                if (gapWidth > 0) {
                    drawLine(
                        color = Color.Transparent,
                        start = drawingCenter,
                        end = drawingCenter + Offset(0f, -canvasSize),
                        strokeWidth = gapWidth,
                        blendMode = BlendMode.Clear
                    )
                }

                restoreToCount(checkpoint)
            }
        }

        // Legend
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = spacedBy(8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            data.forEach { chartData ->
                Row(
                    modifier = Modifier.widthIn(max = 160.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(chartData.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        modifier = Modifier.weight(1f, false),
                        text = chartData.label, // Format percentage
                        fontSize = textSize.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorResource(R.color.almostBlack),
                    )
                    Text(
                        text = " (${chartData.percentage.roundToInt()}%)", // Format percentage
                        fontSize = textSize.sp,
                        color = colorResource(R.color.almostBlack),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDonutChart() {
    val chartData = listOf(
        DonutChartData("15 juin", 20.0, colorResource(R.color.poll0)),
        DonutChartData("16 juin", 20.0, colorResource(R.color.poll1)),
        DonutChartData("17 juin", 25.0, colorResource(R.color.poll2)),
        DonutChartData("19 juin", 35.0, colorResource(R.color.poll3))
    )

    DonutChart(
        data = chartData,
        animationMode = DonutChartAnimationMode.NEVER,
    )
}