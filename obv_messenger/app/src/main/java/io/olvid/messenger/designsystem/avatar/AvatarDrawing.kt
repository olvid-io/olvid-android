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
package io.olvid.messenger.designsystem.avatar

import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import io.olvid.messenger.R
import io.olvid.messenger.settings.SettingsActivity
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Centers short avatar texts (initial, +N badge) exactly: no Android font padding, line box
 * centered and trimmed. Shared by the composable avatar text and the headless [drawAvatar].
 */
internal val AvatarTextStyle = TextStyle(
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * Single source of truth for drawing **one** avatar — photo, generative pattern (see
 * AvatarPattern.kt), or colored background with the centre glyph (lock / group) or initial, plus
 * the trust dot and the status badge.
 *
 * It is a plain [DrawScope] function (not `@Composable`), so the very same code runs:
 *  - on-screen, from the `InitialView` composable's [androidx.compose.foundation.Canvas], and
 *  - off-screen, from [AvatarBitmapRenderer] via a `CanvasDrawScope` — no composition or window,
 *    which is what lets it produce notification / widget bitmaps from a background thread.
 *
 * Because both paths execute this function, the avatar geometry/colors/badges can never diverge.
 *
 * Contract:
 *  - [photo] is supplied already-decoded by the caller (Coil on-screen, BitmapFactory off-screen);
 *    `null` renders the colored/glyph avatar.
 *  - The avatar is assumed circular (every call site uses [androidx.compose.foundation.shape.CircleShape]).
 *  - Must run inside an offscreen-compositing layer (on-screen: `graphicsLayer { compositingStrategy =
 *    Offscreen }`; off-screen: the target `ImageBitmap` is itself the buffer) so the trust dot's
 *    [BlendMode.Clear] punches a clean transparent ring.
 */
fun DrawScope.drawAvatar(
    source: AvatarSource.Single,
    photo: ImageBitmap?,
    context: Context,
    textMeasurer: TextMeasurer,
    showBadges: Boolean,
) {
    val darkMode = context.isNightMode()
    val diameter = size.minDimension

    if (photo != null) {
        clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }) {
            drawAvatarPhoto(photo, desaturate = source.locked || source.inactive)
        }
    } else if (usePatternAvatars && source.bytes.isNotEmpty() && !source.locked) {
        val pattern = avatarPattern(source.bytes, darkMode)
        if (source.initial == null) {
            drawCircle(pattern.background, radius = diameter / 2f)
            drawCenteredIcon(context, R.drawable.ic_group, fraction = .75f, tint = pattern.glyphColor)
        } else {
            drawAvatarPattern(pattern)
        }
    } else {
        drawCircle(avatarLightColor(source.bytes, darkMode), radius = diameter / 2f)
        val darkColor = avatarDarkColor(source.bytes, darkMode)
        when {
            source.locked ->
                drawCenteredIcon(context, R.drawable.ic_lock, fraction = .45f, tint = darkColor)

            source.initial == null ->
                drawCenteredIcon(context, R.drawable.ic_group, fraction = .75f, tint = darkColor)

            else ->
                drawCenteredInitial(textMeasurer, source.initial, darkColor)
        }
    }

    if (!showBadges) {
        return
    }
    if (source.trustLevel != null && source.trustLevel != -1 && SettingsActivity.showTrustLevels()) {
        drawTrustDot(context, source)
    }
    // mutually-exclusive status badge, same priority as the composable InAvatarBadge
    when {
        photo != null && source.locked ->
            drawTopRightIcon(context, R.drawable.ic_lock_circled, fraction = .3f)

        source.inactive ->
            drawCenteredIcon(context, R.drawable.ic_block_outlined, fraction = .8f)

        source.keycloakCertified ->
            drawTopRightIcon(context, R.drawable.ic_keycloak_certified, fraction = .3f)

        !source.recentlyOnline ->
            drawTopRightIcon(context, R.drawable.ic_snooze, fraction = .3f)
    }
}

/** Cover-crop the largest centered square of [photo] into the (square) avatar bounds. */
private fun DrawScope.drawAvatarPhoto(photo: ImageBitmap, desaturate: Boolean) {
    val src = min(photo.width, photo.height)
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - src) / 2, (photo.height - src) / 2),
        srcSize = IntSize(src, src),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        colorFilter = if (desaturate) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.5f) })
        } else {
            null
        },
    )
}

private fun DrawScope.drawCenteredInitial(
    textMeasurer: TextMeasurer,
    initial: String,
    color: Color
) {
    val style = AvatarTextStyle.copy(color = color, fontSize = (size.minDimension * .6f).toSp())
    val layout = textMeasurer.measure(initial, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            (size.width - layout.size.width) / 2f,
            (size.height - layout.size.height) / 2f,
        ),
    )
}

private fun DrawScope.drawCenteredIcon(
    context: Context,
    @DrawableRes res: Int,
    fraction: Float,
    tint: Color? = null
) {
    val iconPx = (fraction * size.minDimension).roundToInt()
    val image = rasterizeVector(context, res, iconPx, tint?.toArgb())
    drawImage(image, topLeft = Offset((size.width - iconPx) / 2f, (size.height - iconPx) / 2f))
}

private fun DrawScope.drawTopRightIcon(context: Context, @DrawableRes res: Int, fraction: Float) {
    val iconPx = (fraction * size.minDimension).roundToInt()
    val image = rasterizeVector(context, res, iconPx, tintArgb = null)
    drawImage(image, topLeft = Offset(size.width - iconPx, 0f))
}

private fun DrawScope.drawTrustDot(context: Context, source: AvatarSource.Single) {
    val dotSize = .3f * size.minDimension
    val center = Offset(size.width - dotSize / 2f, size.height - dotSize / 2f)
    // punch a transparent hole, drop the (smaller) trust dot in it, leaving a ring
    drawCircle(
        Color.Transparent,
        radius = dotSize / 2f,
        center = center,
        blendMode = BlendMode.Clear
    )
    drawCircle(trustColor(context, source.trustLevel), radius = .375f * dotSize, center = center)
    if (source.notOneToOne) {
        drawCircle(
            Color.Transparent,
            radius = .15f * dotSize,
            center = center,
            blendMode = BlendMode.Clear
        )
    }
}

private fun trustColor(context: Context, trustLevel: Int?): Color = Color(
    ContextCompat.getColor(
        context,
        when (trustLevel) {
            4 -> R.color.green
            3 -> R.color.olvid_gradient_light
            2, 1 -> R.color.orange
            else -> R.color.mediumGrey
        },
    )
)

private data class VectorKey(val res: Int, val sizePx: Int, val tintArgb: Int?)

private const val VECTOR_CACHE_MAX = 64

// rasterized icons are immutable and identical across avatars of the same (icon, size, tint),
// so we memoize them. Accessed from both the UI thread (composable) and the notification
// background thread, hence the synchronization; the LRU bound keeps tinted glyphs from piling up.
private val vectorCache = object : LinkedHashMap<VectorKey, ImageBitmap>(16, .75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<VectorKey, ImageBitmap>) =
        size > VECTOR_CACHE_MAX
}

/** Rasterizes a (vector) drawable to a square [ImageBitmap] so it can be drawn in any DrawScope. */
internal fun rasterizeVector(
    context: Context,
    @DrawableRes res: Int,
    sizePx: Int,
    tintArgb: Int?
): ImageBitmap {
    val s = sizePx.coerceAtLeast(1)
    val key = VectorKey(res, s, tintArgb)
    synchronized(vectorCache) { vectorCache[key] }?.let { return it }

    val bitmap = createBitmap(s, s)
    val drawable = ResourcesCompat.getDrawable(context.resources, res, null)
    if (drawable != null) {
        tintArgb?.let { drawable.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        drawable.setBounds(0, 0, s, s)
        drawable.draw(android.graphics.Canvas(bitmap))
    }
    val image = bitmap.asImageBitmap()
    synchronized(vectorCache) { vectorCache[key] = image }
    return image
}

/** Draws an initial centered inside an arbitrary square region (used by the adaptive shortcut icon). */
internal fun DrawScope.drawInitialInRegion(
    textMeasurer: TextMeasurer,
    initial: String,
    color: Color,
    regionSizePx: Float,
    regionOffsetPx: Float,
) {
    val style = AvatarTextStyle.copy(color = color, fontSize = (regionSizePx * .6f).toSp())
    val layout = textMeasurer.measure(initial, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            regionOffsetPx + (regionSizePx - layout.size.width) / 2f,
            regionOffsetPx + (regionSizePx - layout.size.height) / 2f,
        ),
    )
}

internal fun DrawScope.drawDrawableInRegion(
    context: Context,
    @DrawableRes res: Int,
    fraction: Float,
    regionSizePx: Float,
    regionOffsetPx: Float,
    tint: Color? = null,
) {
    val iconPx = (fraction * regionSizePx).roundToInt()
    val image = rasterizeVector(context, res, iconPx, tint?.toArgb())
    drawImage(
        image,
        topLeft = Offset(
            regionOffsetPx + (regionSizePx - iconPx) / 2f,
            regionOffsetPx + (regionSizePx - iconPx) / 2f,
        ),
    )
}

internal fun DrawScope.drawPhotoInRegion(
    photo: ImageBitmap,
    regionSizePx: Int,
    regionOffsetPx: Int,
    desaturate: Boolean
) {
    val src = min(photo.width, photo.height)
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - src) / 2, (photo.height - src) / 2),
        srcSize = IntSize(src, src),
        dstOffset = IntOffset(regionOffsetPx, regionOffsetPx),
        dstSize = IntSize(regionSizePx, regionSizePx),
        colorFilter = if (desaturate) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.5f) })
        } else {
            null
        },
    )
}

internal fun DrawScope.fillBackground(color: Color) {
    drawIntoCanvas { it.nativeCanvas.drawColor(color.toArgb()) }
}

internal fun Context.isNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
