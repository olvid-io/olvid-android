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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.exifinterface.media.ExifInterface
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import kotlin.math.min

/**
 * `View`-free, window-less, synchronous avatar rasterizer for notification / widget / shortcut
 * icons. It is a thin wrapper: it resolves an [AvatarSource], synchronously decodes the photo if
 * any, then runs the **shared** [DrawScope.drawAvatar] (AvatarDrawing.kt) — the exact same routine
 * the on-screen `InitialView` composable uses — through a headless [CanvasDrawScope].
 *
 * Because both the composable and this renderer call [drawAvatar], notification icons can never
 * drift from on-screen avatars. Compose offers no way to rasterize the *composable* off-screen
 * without a window, so we re-run its drawing instead.
 *
 * Group clusters have no off-screen equivalent (they would need member photos), so a
 * [AvatarSource.GroupCluster] renders as the single group glyph — exactly as notifications did.
 */
object AvatarBitmapRenderer {

    @JvmStatic
    @JvmOverloads
    fun render(
        context: Context,
        target: AvatarTarget,
        sizePx: Int,
        showBadges: Boolean = true
    ): Bitmap =
        render(context, target.toAvatarSource(), sizePx, showBadges)

    /** Notification "Person" icon that only has a name. */
    @JvmStatic
    @JvmOverloads
    fun renderInitial(
        context: Context,
        bytes: ByteArray?,
        initial: String?,
        sizePx: Int,
        showBadges: Boolean = true
    ): Bitmap =
        render(context, AvatarSource.initial(bytes, initial), sizePx, showBadges)

    /** Identity with a (relative) photo path — ports legacy setPhotoUrl(). */
    @JvmStatic
    @JvmOverloads
    fun renderPhoto(
        context: Context,
        bytes: ByteArray?,
        relativePhotoUrl: String?,
        sizePx: Int,
        showBadges: Boolean = true
    ): Bitmap =
        render(context, AvatarSource.photo(bytes, relativePhotoUrl), sizePx, showBadges)

    /** Core entry point: rasterizes a resolved [AvatarSource] to a square [sizePx] bitmap. */
    @JvmStatic
    @JvmOverloads
    fun render(
        context: Context,
        source: AvatarSource,
        sizePx: Int,
        showBadges: Boolean = true
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val single = source.toRenderableSingle()
        val photo = single.photoUrl?.let { decodeSquarePhoto(it, size)?.asImageBitmap() }
        val imageBitmap = ImageBitmap(size, size)
        val density = context.composeDensity()
        CanvasDrawScope().draw(
            density,
            LayoutDirection.Ltr,
            Canvas(imageBitmap),
            Size(size.toFloat(), size.toFloat())
        ) {
            drawAvatar(single, photo, context, context.headlessTextMeasurer(density), showBadges)
        }
        return imageBitmap.asAndroidBitmap()
    }

    /**
     * Adaptive launcher-icon variant: a full-bleed background with the avatar inset by the
     * adaptive-icon safe zone (no circle, no trust dot — the launcher masks the shape). Ports the
     * legacy `adaptiveBitmap` getter, reusing the shared region-drawing helpers.
     */
    @JvmStatic
    fun renderAdaptive(context: Context, target: AvatarTarget): Bitmap =
        renderAdaptive(context, target.toAvatarSource())

    @JvmStatic
    fun renderAdaptive(context: Context, source: AvatarSource): Bitmap {
        val outerSize = context.resources.getDimensionPixelSize(R.dimen.shortcut_icon_size)
        val innerSize = context.resources.getDimensionPixelSize(R.dimen.inner_shortcut_icon_size)
        val padding = ((outerSize - innerSize) / 2).toFloat()
        val single = source.toRenderableSingle()
        val darkMode = context.isNightMode()

        val imageBitmap = ImageBitmap(outerSize, outerSize)
        val density = context.composeDensity()
        CanvasDrawScope().draw(
            density,
            LayoutDirection.Ltr,
            Canvas(imageBitmap),
            Size(outerSize.toFloat(), outerSize.toFloat())
        ) {
            val photo = single.photoUrl?.let { decodeSquarePhoto(it, innerSize)?.asImageBitmap() }
            val pattern =
                if (photo == null && usePatternAvatars && single.bytes.isNotEmpty() && !single.locked) {
                    avatarPattern(single.bytes, darkMode)
                } else {
                    null
                }
            if (pattern == null) {
                fillBackground(
                    if (single.bytes.isEmpty()) Color.Black else avatarLightColor(
                        single.bytes,
                        darkMode
                    ),
                )
            }
            if (photo != null) {
                drawPhotoInRegion(
                    photo,
                    innerSize,
                    padding.toInt(),
                    desaturate = single.locked || single.inactive
                )
            } else if (pattern != null) {
                drawAvatarPatternFullBleed(pattern)
                if (single.initial == null) {
                    drawDrawableInRegion(
                        context,
                        R.drawable.ic_group,
                        .75f,
                        innerSize.toFloat(),
                        padding,
                        pattern.glyphColor
                    )
                }
                if (single.inactive) {
                    drawDrawableInRegion(
                        context,
                        R.drawable.ic_block_outlined,
                        .8f,
                        innerSize.toFloat(),
                        padding
                    )
                }
            } else {
                val darkColor = avatarDarkColor(single.bytes, darkMode)
                when {
                    single.locked ->
                        drawDrawableInRegion(
                            context,
                            R.drawable.ic_lock,
                            .45f,
                            innerSize.toFloat(),
                            padding,
                            darkColor
                        )

                    single.initial == null ->
                        drawDrawableInRegion(
                            context,
                            R.drawable.ic_group,
                            .75f,
                            innerSize.toFloat(),
                            padding,
                            darkColor
                        )

                    else ->
                        drawInitialInRegion(
                            context.headlessTextMeasurer(density),
                            single.initial,
                            darkColor,
                            innerSize.toFloat(),
                            padding
                        )
                }
                if (single.inactive) {
                    drawDrawableInRegion(
                        context,
                        R.drawable.ic_block_outlined,
                        .8f,
                        innerSize.toFloat(),
                        padding
                    )
                }
            }
        }
        return imageBitmap.asAndroidBitmap()
    }

    private fun AvatarSource.toRenderableSingle(): AvatarSource.Single = when (this) {
        is AvatarSource.Single -> this
        is AvatarSource.Cached -> resolve()
        is AvatarSource.GroupCluster -> AvatarSource.Single(
            bytes = bytes,
            initial = null,
            keycloakCertified = keycloakCertified,
            inactive = inactive,
        )
    }

    private fun decodeSquarePhoto(photoUrl: String, targetSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoUrl, bounds)
        val photoSize = min(bounds.outWidth, bounds.outHeight)
        if (photoSize <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply { inSampleSize = photoSize / targetSize }
        val decoded = BitmapFactory.decodeFile(photoUrl, options) ?: return null
        return runCatching {
            val orientation = ExifInterface(photoUrl)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            PreviewUtils.rotateBitmap(decoded, orientation)
        }.getOrDefault(decoded)
    }

    private fun Context.composeDensity() =
        Density(resources.displayMetrics.density, resources.configuration.fontScale)

    private fun Context.headlessTextMeasurer(density: Density) =
        TextMeasurer(createFontFamilyResolver(this), density, LayoutDirection.Ltr)
}
