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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class QuadDetectorTest {

    private class Scene(val width: Int, val height: Int, background: Int, rowStride: Int = width) {
        val stride = rowStride
        val pixels = FloatArray(width * height) { background.toFloat() }

        fun fillPolygon(polygon: List<ScanPoint>, value: Int) {
            // 2x2 supersampling for anti-aliased edges
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var inside = 0
                    for (sy in 0..1) for (sx in 0..1) {
                        if (contains(polygon, x + 0.25f + sx * 0.5f, y + 0.25f + sy * 0.5f)) inside++
                    }
                    if (inside > 0) {
                        val i = y * width + x
                        pixels[i] = pixels[i] * (4 - inside) / 4 + value * inside / 4f
                    }
                }
            }
        }

        fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, value: Int) {
            for (y in y0 until y1) for (x in x0 until x1) pixels[y * width + x] = value.toFloat()
        }

        fun addNoise(amplitude: Int, seed: Int = 42) {
            val random = Random(seed)
            for (i in pixels.indices) pixels[i] += random.nextInt(-amplitude, amplitude + 1)
        }

        fun luminance(): ByteArray {
            val out = ByteArray(stride * height)
            for (y in 0 until height) for (x in 0 until width) {
                out[y * stride + x] = pixels[y * width + x].toInt().coerceIn(0, 255).toByte()
            }
            return out
        }

        private fun contains(p: List<ScanPoint>, x: Float, y: Float): Boolean {
            var sign = 0
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
                val s = if (cross >= 0) 1 else -1
                if (sign == 0) sign = s else if (s != sign) return false
            }
            return true
        }
    }

    // text-like dark strokes inside the page
    private fun Scene.addText(page: Quad, value: Int) {
        for (line in 1..8) {
            val t = line / 10f
            val left = lerp(page.topLeft, page.bottomLeft, t)
            val right = lerp(page.topRight, page.bottomRight, t)
            var s = 0.12f
            while (s < 0.85f) {
                val a = lerp(left, right, s)
                val b = lerp(left, right, s + 0.07f)
                fillPolygon(listOf(a, b, ScanPoint(b.x, b.y + 3), ScanPoint(a.x, a.y + 3)), value)
                s += 0.1f
            }
        }
    }

    private fun lerp(a: ScanPoint, b: ScanPoint, t: Float) = ScanPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun assertQuadNear(expected: Quad, actual: Quad?, tolerance: Float) {
        assertNotNull("no quad detected", actual)
        expected.points.zip(actual!!.points).forEach { (e, a) ->
            val distance = hypot(e.x - a.x, e.y - a.y)
            assertEquals("corner $e detected at $a", 0f, distance, tolerance)
        }
    }

    private val detector = QuadDetector()

    @Test
    fun `detects a frontal page on a dark desk`() {
        val page = Quad(ScanPoint(120f, 90f), ScanPoint(520f, 90f), ScanPoint(520f, 400f), ScanPoint(120f, 400f))
        val scene = Scene(640, 480, background = 60)
        scene.fillPolygon(page.points, 220)
        scene.addText(page, 40)
        scene.addNoise(6)

        assertQuadNear(page, detector.detect(scene.luminance(), 640, 480), tolerance = 6f)
    }

    @Test
    fun `detects a rotated page seen in perspective`() {
        val page = Quad(ScanPoint(190f, 70f), ScanPoint(500f, 120f), ScanPoint(560f, 430f), ScanPoint(90f, 380f))
        val scene = Scene(640, 480, background = 90)
        scene.fillPolygon(page.points, 230)
        scene.addText(page, 50)
        scene.addNoise(8)

        assertQuadNear(page, detector.detect(scene.luminance(), 640, 480), tolerance = 6f)
    }

    @Test
    fun `detects a white page on a light desk`() {
        val page = Quad(ScanPoint(150f, 60f), ScanPoint(470f, 80f), ScanPoint(490f, 420f), ScanPoint(130f, 400f))
        val scene = Scene(640, 480, background = 185)
        scene.fillPolygon(page.points, 215)
        scene.addText(page, 80)
        scene.addNoise(6)

        assertQuadNear(page, detector.detect(scene.luminance(), 640, 480), tolerance = 6f)
    }

    @Test
    fun `ignores strong straight lines outside the page`() {
        val page = Quad(ScanPoint(170f, 100f), ScanPoint(470f, 110f), ScanPoint(460f, 360f), ScanPoint(160f, 350f))
        val scene = Scene(640, 480, background = 70)
        // desk edge below the page and a dark object on the side
        scene.fillRect(0, 420, 640, 480, 20)
        scene.fillRect(520, 40, 600, 300, 150)
        scene.fillPolygon(page.points, 225)
        scene.addText(page, 40)
        scene.addNoise(6)

        assertQuadNear(page, detector.detect(scene.luminance(), 640, 480), tolerance = 6f)
    }

    @Test
    fun `picks the page and not a picture aligned inside it`() {
        // A4 page with a dark full-width picture: its strong vertical edges run parallel to the page sides
        val page = Quad(ScanPoint(200f, 40f), ScanPoint(520f, 50f), ScanPoint(510f, 490f), ScanPoint(190f, 480f))
        val scene = Scene(720, 540, background = 200)
        scene.fillPolygon(page.points, 222)
        val picture = Quad(
            lerp(lerp(page.topLeft, page.topRight, 0.08f), lerp(page.bottomLeft, page.bottomRight, 0.08f), 0.15f),
            lerp(lerp(page.topLeft, page.topRight, 0.92f), lerp(page.bottomLeft, page.bottomRight, 0.92f), 0.15f),
            lerp(lerp(page.topLeft, page.topRight, 0.92f), lerp(page.bottomLeft, page.bottomRight, 0.92f), 0.75f),
            lerp(lerp(page.topLeft, page.topRight, 0.08f), lerp(page.bottomLeft, page.bottomRight, 0.08f), 0.75f),
        )
        scene.fillPolygon(picture.points, 30)
        scene.addNoise(6)

        assertQuadNear(page, detector.detect(scene.luminance(), 720, 540), tolerance = 6f)
    }

    @Test
    fun `keeps the faint page border below text lines`() {
        // text rows down to the bottom margin produce long horizontal edges, stronger than the page border
        val page = Quad(ScanPoint(190f, 30f), ScanPoint(510f, 30f), ScanPoint(510f, 482f), ScanPoint(190f, 482f))
        val scene = Scene(720, 540, background = 200)
        scene.fillPolygon(page.points, 220)
        for (row in 0 until 30) {
            val y = 40 + row * 14
            scene.fillRect(205, y, 495, y + 4, 40)
        }
        scene.addNoise(5)

        assertQuadNear(page, detector.detect(scene.luminance(), 720, 540), tolerance = 6f)
    }

    @Test
    fun `stays on the previous outline when two outlines are plausible`() {
        // two separate pages of the same quality: the tracked one must win
        val first = Quad(ScanPoint(60f, 80f), ScanPoint(330f, 80f), ScanPoint(330f, 460f), ScanPoint(60f, 460f))
        val second = Quad(ScanPoint(400f, 130f), ScanPoint(670f, 150f), ScanPoint(655f, 520f), ScanPoint(385f, 500f))
        val scene = Scene(720, 540, background = 60)
        scene.fillPolygon(first.points, 220)
        scene.fillPolygon(second.points, 220)
        scene.addNoise(6)
        val luminance = scene.luminance()

        assertQuadNear(first, detector.detect(luminance, 720, 540, previous = first.map { ScanPoint(it.x + 4, it.y - 3) }), tolerance = 6f)
        assertQuadNear(second, detector.detect(luminance, 720, 540, previous = second.map { ScanPoint(it.x - 4, it.y + 3) }), tolerance = 6f)
    }

    @Test
    fun `works on a high resolution frame with row padding`() {
        val page = Quad(ScanPoint(500f, 300f), ScanPoint(2400f, 380f), ScanPoint(2300f, 2800f), ScanPoint(420f, 2700f))
        val scene = Scene(3000, 3200, background = 50, rowStride = 3072)
        scene.fillPolygon(page.points, 210)
        scene.addNoise(10)

        assertQuadNear(page, detector.detect(scene.luminance(), 3000, 3200, rowStride = 3072), tolerance = 25f)
    }

    @Test
    fun `finds nothing in pure noise`() {
        val scene = Scene(640, 480, background = 128)
        scene.addNoise(20)

        assertNull(detector.detect(scene.luminance(), 640, 480))
    }

    @Test
    fun `finds nothing when the page is too small`() {
        val page = Quad(ScanPoint(300f, 200f), ScanPoint(380f, 200f), ScanPoint(380f, 260f), ScanPoint(300f, 260f))
        val scene = Scene(640, 480, background = 60)
        scene.fillPolygon(page.points, 220)

        assertNull(detector.detect(scene.luminance(), 640, 480))
    }

    @Test
    fun `orders unordered corners clockwise from the top left`() {
        val quad = Quad.fromUnordered(listOf(ScanPoint(90f, 380f), ScanPoint(500f, 120f), ScanPoint(190f, 70f), ScanPoint(560f, 430f)))

        assertEquals(Quad(ScanPoint(190f, 70f), ScanPoint(500f, 120f), ScanPoint(560f, 430f), ScanPoint(90f, 380f)), quad)
    }
}
