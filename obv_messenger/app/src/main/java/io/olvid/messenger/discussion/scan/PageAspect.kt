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

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real width / height ratio of a rectangle seen in perspective, from its 4 corners: Zhang & He,
 * "Whiteboard scanning and image enhancement" (2006). The focal length is estimated from the corners,
 * with the principal point at the center of the picture and square pixels, unless the camera focal
 * length is known; when it cannot be (the page is tilted around one axis only), a typical phone camera
 * focal length is used.
 */
object PageAspect {
    private val STANDARD_RATIOS = doubleArrayOf(1 / sqrt(2.0), 8.5 / 11.0)
    // relative distance under which the ratio snaps to A4 or Letter
    private const val SNAP_TOLERANCE = 0.04
    // focal lengths, in picture diagonals: a phone main camera is about 26 mm (full frame equivalent),
    // a Pixel 10a reports 0.57
    private const val TYPICAL_FOCAL = 0.6
    private const val MIN_FOCAL = 0.3
    private const val MAX_FOCAL = 2.0

    /**
     * @param focalRatio focal length of the camera that took the picture, in picture diagonals, when
     * known: estimating it from the corners is very sensitive to their accuracy
     */
    fun estimate(quad: Quad, imageWidth: Int, imageHeight: Int, focalRatio: Double? = null): Double {
        val u0 = imageWidth / 2.0
        val v0 = imageHeight / 2.0
        // m1 m2 is the top side, m3 m4 the bottom one
        val m1 = vector(quad.topLeft)
        val m2 = vector(quad.topRight)
        val m3 = vector(quad.bottomLeft)
        val m4 = vector(quad.bottomRight)

        val k2 = dot(cross(m1, m4), m3) / dot(cross(m2, m4), m3)
        val k3 = dot(cross(m1, m4), m2) / dot(cross(m3, m4), m2)
        val n2 = sub(scale(m2, k2), m1)
        val n3 = sub(scale(m3, k3), m1)

        // with a vanishing point at infinity (page tilted around one axis only) or an implausible
        // estimate, use the focal length of a typical phone main camera
        val diagonal = hypot(imageWidth.toDouble(), imageHeight.toDouble())
        val estimated = -((n2[0] * n3[0] - (n2[0] * n3[2] + n2[2] * n3[0]) * u0 + n2[2] * n3[2] * u0 * u0) +
                (n2[1] * n3[1] - (n2[1] * n3[2] + n2[2] * n3[1]) * v0 + n2[2] * n3[2] * v0 * v0)) / (n2[2] * n3[2])
        val f2 = focalRatio?.let { (it * diagonal).pow(2) }
            ?: estimated.takeIf { it.isFinite() && it > (MIN_FOCAL * diagonal).pow(2) && it < (MAX_FOCAL * diagonal).pow(2) }
            ?: (TYPICAL_FOCAL * diagonal).pow(2)
        val fallback = sideRatio(quad)

        // nᵀ (A Aᵀ)⁻¹ n, with A the intrinsic matrix
        fun norm(n: DoubleArray): Double {
            val x = n[0] - u0 * n[2]
            val y = n[1] - v0 * n[2]
            return (x * x + y * y) / f2 + n[2] * n[2]
        }
        val ratio = sqrt(norm(n2) / norm(n3))
        return if (ratio.isFinite() && ratio > 0) ratio else fallback
    }

    /** Snaps [ratio] (width / height) to A4 or Letter, in portrait or landscape, when close enough. */
    fun snap(ratio: Double): Double {
        val portrait = if (ratio > 1) 1 / ratio else ratio
        val standard = STANDARD_RATIOS.minBy { abs(ln(it / portrait)) }
        if (abs(ln(standard / portrait)) > SNAP_TOLERANCE) return ratio
        return if (ratio > 1) 1 / standard else standard
    }

    private fun sideRatio(quad: Quad): Double {
        val width = (hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y) +
                hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y)) / 2.0
        val height = (hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y) +
                hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y)) / 2.0
        return width / height
    }

    private fun vector(p: ScanPoint) = doubleArrayOf(p.x.toDouble(), p.y.toDouble(), 1.0)
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun scale(a: DoubleArray, k: Double) = doubleArrayOf(a[0] * k, a[1] * k, a[2] * k)
    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
}
