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
import kotlin.math.sin
import kotlin.random.Random

class ScanFilterTest {

    private val width = 256
    private val height = 128

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    private fun Int.r() = this shr 16 and 0xff
    private fun Int.g() = this shr 8 and 0xff
    private fun Int.b() = this and 0xff

    // paper lit from the right (left side in the shade, slightly yellow), with a dark ink stroke
    private fun shadedPage(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val light = 0.55f + 0.45f * x / width
        if (y in 60..67 && x in 20..236) {
            rgb((40 * light).toInt(), (40 * light).toInt(), (45 * light).toInt())
        } else {
            rgb((235 * light).toInt(), (228 * light).toInt(), (200 * light).toInt())
        }
    }

    private fun IntArray.at(x: Int, y: Int) = this[y * width + x]

    @Test
    fun `grayscale output has equal channels`() {
        ScanFilter.GRAYSCALE.apply(shadedPage(), width, height).forEach {
            assertTrue(it.r() == it.g() && it.g() == it.b())
        }
    }

    @Test
    fun `enhanced removes the shade and keeps the ink dark`() {
        val out = ScanFilter.ENHANCED.apply(shadedPage(), width, height)
        val shadedPaper = out.at(10, 20)
        val litPaper = out.at(245, 20)
        assertTrue("shaded paper $shadedPaper not whitened", shadedPaper.r() > 230 && shadedPaper.g() > 230 && shadedPaper.b() > 230)
        assertTrue("lit paper $litPaper not white", litPaper.r() > 230)
        val ink = out.at(30, 63)
        assertTrue("ink ${ink.r()} not dark", ink.r() < 40)
    }

    @Test
    fun `black and white output has white paper and black ink`() {
        val out = ScanFilter.BLACK_AND_WHITE.apply(shadedPage(), width, height)
        assertEquals(255, out.at(10, 20).r())
        assertEquals(255, out.at(245, 100).r())
        assertEquals(0, out.at(30, 63).r())
        assertEquals(0, out.at(230, 63).r())
    }

    // shaded page with an orange photo (textured, no paper inside it)
    private fun pageWithPhoto(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val light = 0.6f + 0.4f * x / width
        if (x in 96..223 && y in 16..111) {
            val texture = (20 * sin(x / 5.0) * cos(y / 7.0)).toInt()
            rgb(((200 + texture) * light).toInt(), ((120 + texture) * light).toInt(), ((50 + texture / 2) * light).toInt())
        } else {
            rgb((235 * light).toInt(), (230 * light).toInt(), (220 * light).toInt())
        }
    }

    private fun IntArray.meanIn(x0: Int, x1: Int, y0: Int, y1: Int, channel: (Int) -> Int): Double {
        var sum = 0.0
        var n = 0
        for (y in y0..y1) for (x in x0..x1) { sum += channel(at(x, y)); n++ }
        return sum / n
    }

    @Test
    fun `enhanced keeps the contrast and the hue of a photo`() {
        val out = ScanFilter.ENHANCED.apply(pageWithPhoto(), width, height)
        val r = out.meanIn(110, 210, 30, 100) { it.r() }
        val g = out.meanIn(110, 210, 30, 100) { it.g() }
        val b = out.meanIn(110, 210, 30, 100) { it.b() }
        assertTrue("photo washed out: r=$r g=$g b=$b", g < 200 && b < 150)
        assertTrue("photo hue lost: r=$r g=$g b=$b", r > g + 40 && g > b + 30)
        // paper around it is whitened, shade included
        val paper = out.at(20, 60)
        assertTrue("shaded paper $paper not whitened", paper.r() > 230 && paper.g() > 230 && paper.b() > 230)
    }

    @Test
    fun `enhanced does not create blocks inside a photo`() {
        val photo = pageWithPhoto()
        val out = ScanFilter.ENHANCED.apply(photo, width, height)
        // the photo varies smoothly: so must the output, without steps at tile borders
        var maxJump = 0
        for (y in 30..100) for (x in 111..210) {
            maxJump = maxOf(maxJump, abs(out.at(x, y).g() - out.at(x - 1, y).g()), abs(out.at(x, y).g() - out.at(x, y - 1).g()))
        }
        assertTrue("tile step of $maxJump inside the photo", maxJump < 25)
    }

    @Test
    fun `enhanced does not amplify the noise of a dark picture`() {
        val random = Random(1)
        val dark = IntArray(width * height) { rgb(20 + random.nextInt(12), 10 + random.nextInt(12), 25 + random.nextInt(12)) }
        val out = ScanFilter.ENHANCED.apply(dark, width, height)
        val mean = out.map { (it.r() + it.g() + it.b()) / 3.0 }.average()
        assertTrue("dark picture brightened to $mean", mean < 80)
    }

    // page with a dark text line on the left and a gray photo with a gradient on the right
    private fun pageWithTextAndPhoto(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        when {
            x in 140..239 && y in 20..107 -> {
                val v = 60 + (x - 140) + (30 * sin(y / 6.0)).toInt()
                rgb(v, v, v)
            }
            x in 16..120 && y in 60..66 -> rgb(35, 35, 35)
            else -> rgb(230, 228, 222)
        }
    }

    @Test
    fun `black and white keeps photos in grayscale and text in black and white`() {
        val out = ScanFilter.BLACK_AND_WHITE.apply(pageWithTextAndPhoto(), width, height)
        // text and paper: binary
        assertEquals(0, out.at(60, 63).r())
        assertEquals(255, out.at(60, 30).r())
        assertEquals(255, out.at(60, 100).r())
        // photo: many gray levels, in order along its gradient
        val levels = (150..230).map { out.at(it, 64).r() }
        assertTrue("photo binarized: ${levels.distinct().size} levels", levels.distinct().size > 20)
        assertTrue(out.at(225, 64).r() > out.at(155, 64).r())
    }

    @Test
    fun `black and white of a text page is fully binary`() {
        val out = ScanFilter.BLACK_AND_WHITE.apply(shadedPage(), width, height)
        assertTrue(out.all { it.r() == 0 || it.r() == 255 })
    }

    @Test
    fun `original is left untouched`() {
        val page = shadedPage()
        assertTrue(page.contentEquals(ScanFilter.ORIGINAL.apply(page, width, height)))
    }
}
