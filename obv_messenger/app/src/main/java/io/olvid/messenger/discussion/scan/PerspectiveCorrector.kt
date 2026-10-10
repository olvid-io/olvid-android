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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.scale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PerspectiveCorrector {

    /**
     * Maps [quad] (in [source] coordinates, [source] being the whole picture) onto an upright
     * rectangle with the page's real aspect ratio (see [PageAspect]), its longest side being the
     * longest side of [quad], capped to [maxSide]. [focalRatio]: see [PageAspect.estimate].
     */
    fun correct(source: Bitmap, quad: Quad, maxSide: Int = 2480, focalRatio: Double? = null): Bitmap {
        val ratio = PageAspect.snap(PageAspect.estimate(quad, source.width, source.height, focalRatio))
        val longest = maxOf(
            distance(quad.topLeft, quad.topRight), distance(quad.bottomLeft, quad.bottomRight),
            distance(quad.topLeft, quad.bottomLeft), distance(quad.topRight, quad.bottomRight),
        )
        val (width, height) = if (ratio >= 1) longest to (longest / ratio).toFloat() else (longest * ratio).toFloat() to longest
        val scale = min(1f, maxSide / max(width, height))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)

        val src = quad.points.flatMap { listOf(it.x, it.y) }.toFloatArray()
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            // corners can lie outside the picture when the page is cut by the frame
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return output
    }

    /**
     * Detects the page outline in a full resolution [bitmap], working on a copy downscaled to
     * [analysisSize]. Returns corners in [bitmap] coordinates.
     */
    fun detect(bitmap: Bitmap, detector: QuadDetector = QuadDetector(), analysisSize: Int = 640): Quad? {
        val ratio = min(1f, analysisSize / max(bitmap.width, bitmap.height).toFloat())
        val small = if (ratio < 1f) bitmap.scale((bitmap.width * ratio).roundToInt(), (bitmap.height * ratio).roundToInt()) else bitmap
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val luminance = ByteArray(pixels.size) {
            val c = pixels[it]
            (((c shr 16 and 0xff) * 77 + (c shr 8 and 0xff) * 150 + (c and 0xff) * 29) shr 8).toByte()
        }
        return detector.detect(luminance, small.width, small.height)
            ?.map { ScanPoint(it.x / ratio, it.y / ratio) }
    }

    private fun distance(a: ScanPoint, b: ScanPoint) = hypot(b.x - a.x, b.y - a.y)
}
