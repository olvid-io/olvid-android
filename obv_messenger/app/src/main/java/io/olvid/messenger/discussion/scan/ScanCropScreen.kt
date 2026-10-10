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

package io.olvid.messenger.discussion.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlin.math.min

/**
 * Corner adjustment and rotation. The picture is shown rotated; corners stay in picture
 * coordinates and are only rotated for display.
 */
@Composable
internal fun ScanCropScreen(page: ScanPage, onCancel: () -> Unit, onApply: (quad: Quad, rotation: Int) -> Unit) {
    var corners by remember(page.id) { mutableStateOf(page.quad.points) }
    var rotation by remember(page.id) { mutableIntStateOf(page.rotation) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    val image = remember(page.preview, rotation) { page.preview.rotated(rotation).asImageBitmap() }
    val density = LocalDensity.current
    val grabRadius = with(density) { 40.dp.toPx() }
    val loupeRadius = with(density) { 56.dp.toPx() }
    val w = page.width.toFloat()
    val h = page.height.toFloat()
    val (shownWidth, shownHeight) = if (rotation % 180 == 0) w to h else h to w

    val blue = colorResource(R.color.olvid_gradient_light)
    val content = colorResource(R.color.almostBlack)

    Column(Modifier.fillMaxSize().background(colorResource(R.color.almostWhite)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, colors = IconButtonDefaults.iconButtonColors(contentColor = content)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.button_label_cancel), tint = content)
            }
            Text(
                text = stringResource(R.string.button_label_scan_crop_rotate),
                color = content,
                style = OlvidTypography.h3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val padding = with(density) { 24.dp.toPx() }
            val areaWidth = constraints.maxWidth.toFloat()
            val areaHeight = constraints.maxHeight.toFloat()
            // displayed size, and picture coordinates → display coordinates factor
            val scale = min((areaWidth - 2 * padding) / shownWidth, (areaHeight - 2 * padding) / shownHeight)
            val displayedDp = with(density) { DpSize((shownWidth * scale).toDp(), (shownHeight * scale).toDp()) }
            // top left of the picture in the area: corners may lie outside the picture (detected
            // corners of a page cut by the frame), so they can be dragged anywhere in the area
            val origin = Offset((areaWidth - shownWidth * scale) / 2, (areaHeight - shownHeight * scale) / 2)
            fun toDisplay(p: ScanPoint) = rotate(p, rotation, w, h).let { Offset(it.x * scale, it.y * scale) }
            fun coerceInArea(p: Offset) = Offset(p.x.coerceIn(-origin.x, areaWidth - origin.x), p.y.coerceIn(-origin.y, areaHeight - origin.y))

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page.id, rotation, scale, origin) {
                        detectDragGestures(
                            onDragStart = { start ->
                                // a corner beyond the area can be grabbed at the area's edge
                                val distances = corners.map { (coerceInArea(toDisplay(it)) - (start - origin)).getDistance() }
                                val nearest = distances.indices.minBy { distances[it] }
                                dragged = nearest.takeIf { distances[it] < grabRadius }
                            },
                            onDragEnd = { dragged = null },
                            onDragCancel = { dragged = null },
                            onDrag = { change, amount ->
                                val index = dragged ?: return@detectDragGestures
                                change.consume()
                                val shown = coerceInArea(toDisplay(corners[index]) + amount)
                                val moved = unrotate(ScanPoint(shown.x / scale, shown.y / scale), rotation, w, h)
                                corners = corners.toMutableList().also { it[index] = moved }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(displayedDp)) {
                    Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val shown = corners.map { toDisplay(it) }
                        val outline = Path().apply {
                            moveTo(shown[0].x, shown[0].y)
                            shown.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        // darken outside the page
                        clipPath(outline, clipOp = ClipOp.Difference) {
                            drawRect(Color(0x66000000))
                        }
                        drawPath(outline, blue, style = Stroke(width = 2.dp.toPx()))
                        shown.forEachIndexed { index, point ->
                            drawCircle(Color.White, radius = (if (index == dragged) 14 else 11).dp.toPx(), center = point)
                            drawCircle(blue, radius = (if (index == dragged) 14 else 11).dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
                        }

                        dragged?.let { index ->
                            val finger = shown[index]
                            // magnifier in the top corner away from the dragged one
                            val margin = 8.dp.toPx()
                            val center = Offset(
                                if (finger.x < size.width / 2) size.width - loupeRadius - margin else loupeRadius + margin,
                                loupeRadius + margin,
                            )
                            val circle = Path().apply { addOval(Rect(center, loupeRadius)) }
                            clipPath(circle) {
                                drawRect(Color.Black, topLeft = center - Offset(loupeRadius, loupeRadius), size = Size(2 * loupeRadius, 2 * loupeRadius))
                                translate(center.x - finger.x * LOUPE_ZOOM, center.y - finger.y * LOUPE_ZOOM) {
                                    scale(LOUPE_ZOOM, LOUPE_ZOOM, pivot = Offset.Zero) {
                                        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                                        drawPath(outline, blue, style = Stroke(width = 1.dp.toPx()))
                                    }
                                }
                            }
                            drawCircle(Color.White, radius = loupeRadius, center = center, style = Stroke(width = 3.dp.toPx()))
                            val cross = 10.dp.toPx()
                            drawLine(blue, center - Offset(cross, 0f), center + Offset(cross, 0f), strokeWidth = 2.dp.toPx())
                            drawLine(blue, center - Offset(0f, cross), center + Offset(0f, cross), strokeWidth = 2.dp.toPx())
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            CropChip(R.drawable.ic_rotate_image, R.string.button_label_scan_rotate) { rotation = (rotation + 90) % 360 }
            CropChip(R.drawable.ic_scan_crop, R.string.button_label_scan_auto_crop) { corners = (page.detected ?: page.fullImage).points }
            CropChip(R.drawable.ic_reset_image, R.string.button_label_scan_full_image) { corners = page.fullImage.points }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.button_label_cancel),
                containerColor = Color.Transparent,
                contentColor = blue,
                outlinedColor = blue,
                large = true,
                shape = CircleShape,
                onClick = onCancel,
            )
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.button_label_done),
                large = true,
                shape = CircleShape,
            ) { onApply(Quad.fromUnordered(corners), rotation) }
        }
    }
}

@Composable
private fun CropChip(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    val content = colorResource(R.color.almostBlack)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colorResource(R.color.lightGrey))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text = stringResource(label), color = content, style = OlvidTypography.body2, maxLines = 1)
    }
}

// clockwise rotation of a point of a w × h picture, as Matrix.postRotate does
internal fun rotate(p: ScanPoint, rotation: Int, w: Float, h: Float) = when (rotation) {
    90 -> ScanPoint(h - p.y, p.x)
    180 -> ScanPoint(w - p.x, h - p.y)
    270 -> ScanPoint(p.y, w - p.x)
    else -> p
}

internal fun unrotate(p: ScanPoint, rotation: Int, w: Float, h: Float) = when (rotation) {
    90 -> ScanPoint(p.y, h - p.x)
    180 -> ScanPoint(w - p.x, h - p.y)
    270 -> ScanPoint(w - p.y, p.x)
    else -> p
}

private fun Bitmap.rotated(rotation: Int): Bitmap =
    if (rotation == 0) this else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)

private const val LOUPE_ZOOM = 2.5f
