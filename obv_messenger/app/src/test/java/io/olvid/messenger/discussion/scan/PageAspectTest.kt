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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class PageAspectTest {
    private val imageWidth = 3000
    private val imageHeight = 4000
    // a phone main camera: 0.6 diagonal
    private val focal = 3000.0

    /** Corners of a [width] × [height] page tilted by [pitch] / [yaw] / [roll] degrees, [distance] away. */
    private fun photograph(width: Double, height: Double, pitch: Double, yaw: Double, roll: Double, distance: Double, shiftX: Double = 0.0): Quad {
        val (a, b, c) = Triple(Math.toRadians(pitch), Math.toRadians(yaw), Math.toRadians(roll))
        fun project(x: Double, y: Double): ScanPoint {
            // roll (z), then pitch (x), then yaw (y)
            var px = x * cos(c) - y * sin(c)
            var py = x * sin(c) + y * cos(c)
            var pz = 0.0
            val y1 = py * cos(a) - pz * sin(a); val z1 = py * sin(a) + pz * cos(a); py = y1; pz = z1
            val x2 = px * cos(b) + pz * sin(b); val z2 = -px * sin(b) + pz * cos(b); px = x2; pz = z2
            pz += distance
            px += shiftX
            return ScanPoint((imageWidth / 2 + focal * px / pz).toFloat(), (imageHeight / 2 + focal * py / pz).toFloat())
        }
        return Quad(project(-width / 2, -height / 2), project(width / 2, -height / 2), project(width / 2, height / 2), project(-width / 2, height / 2))
    }

    private val a4 = 210.0 / 297.0

    @Test
    fun `recovers the ratio of a page seen at an angle`() {
        listOf(
            doubleArrayOf(35.0, 0.0, 0.0),
            doubleArrayOf(30.0, 20.0, 10.0),
            doubleArrayOf(-25.0, 15.0, -30.0),
            doubleArrayOf(45.0, -10.0, 5.0),
        ).forEach { (pitch, yaw, roll) ->
            val quad = photograph(210.0, 297.0, pitch, yaw, roll, distance = 450.0, shiftX = 30.0)
            val ratio = PageAspect.estimate(quad, imageWidth, imageHeight)
            assertEquals("tilt $pitch/$yaw/$roll", a4, ratio, a4 * 0.03)
        }
    }

    @Test
    fun `side lengths alone get it wrong at an angle`() {
        // the previous estimate (longest of each pair of opposite sides), kept here as a reference
        val quad = photograph(210.0, 297.0, 35.0, 0.0, 0.0, distance = 450.0)
        val width = max(hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y), hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y))
        val height = max(hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y), hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y))
        assertTrue(abs(width / height - a4) > a4 * 0.08)
        assertEquals(a4, PageAspect.estimate(quad, imageWidth, imageHeight), a4 * 0.03)
    }

    @Test
    fun `a known focal length is used as is`() {
        // corners off by a few pixels make the estimated focal length unreliable, not the known one
        val quad = photograph(210.0, 297.0, 30.0, 20.0, 10.0, distance = 450.0).map { ScanPoint(it.x + 25, it.y - 18) }
        val focalRatio = focal / hypot(imageWidth.toDouble(), imageHeight.toDouble())
        assertEquals(a4, PageAspect.estimate(quad, imageWidth, imageHeight, focalRatio), a4 * 0.03)
    }

    @Test
    fun `a frontal page falls back to its side lengths`() {
        val quad = photograph(210.0, 297.0, 0.0, 0.0, 12.0, distance = 450.0)
        assertEquals(a4, PageAspect.estimate(quad, imageWidth, imageHeight), a4 * 0.01)
    }

    @Test
    fun `close ratios snap to A4 or Letter, others are kept`() {
        assertEquals(1 / sqrt(2.0), PageAspect.snap(0.72), 1e-9)
        assertEquals(sqrt(2.0), PageAspect.snap(1.40), 1e-9)
        assertEquals(8.5 / 11, PageAspect.snap(0.78), 1e-9)
        assertEquals(0.63, PageAspect.snap(0.63), 1e-9)
        assertEquals(1.0, PageAspect.snap(1.0), 1e-9)
    }
}
