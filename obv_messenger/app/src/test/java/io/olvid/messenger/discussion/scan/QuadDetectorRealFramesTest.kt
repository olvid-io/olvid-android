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
import org.junit.Test
import kotlin.math.hypot

/**
 * Camera preview frames (640×480 luminance, as fed to the detector by the live scanner) of cases
 * that failed on a device. Expected corners are in frame coordinates and may lie outside the frame.
 */
class QuadDetectorRealFramesTest {

    private class Frame(val luminance: ByteArray, val width: Int, val height: Int)

    private fun load(name: String): Frame {
        val bytes = javaClass.getResourceAsStream(name)!!.use { it.readBytes() }
        // binary PGM: magic, comment lines, size, max value, then pixels
        var offset = 0
        val fields = ArrayList<String>()
        while (fields.size < 4) {
            val end = bytes.indexOf('\n'.code.toByte(), offset)
            val line = String(bytes, offset, end - offset, Charsets.US_ASCII)
            offset = end + 1
            if (!line.startsWith("#")) fields.addAll(line.split(" "))
        }
        val width = fields[1].toInt()
        val height = fields[2].toInt()
        return Frame(bytes.copyOfRange(offset, offset + width * height), width, height)
    }

    private fun ByteArray.indexOf(value: Byte, from: Int): Int = (from until size).first { this[it] == value }

    private fun assertDetects(name: String, vararg expected: Pair<Float, Float>) {
        val frame = load(name)
        val quad = QuadDetector().detect(frame.luminance, frame.width, frame.height)
        assertNotNull("no page detected in $name", quad)
        val expectedQuad = Quad.fromUnordered(expected.map { ScanPoint(it.first, it.second) })
        expectedQuad.points.zip(quad!!.alignedTo(expectedQuad).points).forEach { (e, a) ->
            assertEquals("$name: corner $e detected at $a", 0f, hypot(e.x - a.x, e.y - a.y), TOLERANCE)
        }
    }

    // white page on a light desk, more sheets under it, a grid of photos whose rows produce strong
    // lines parallel to the page borders, bottom right corner outside the frame
    @Test
    fun `stacked sheets with a photo grid, frame 1`() = assertDetects(
        "stacked_sheets_photo_grid_1.pgm",
        86.9f to 195.9f, 390.5f to -49.3f, 531.0f to 411.5f, 144.6f to 465.0f,
    )

    @Test
    fun `stacked sheets with a photo grid, frame 2`() = assertDetects(
        "stacked_sheets_photo_grid_2.pgm",
        64.6f to 150.2f, 417.8f to -66.1f, 513.0f to 381.0f, 94.6f to 433.5f,
    )

    @Test
    fun `stacked sheets with a photo grid, frame 3`() = assertDetects(
        "stacked_sheets_photo_grid_3.pgm",
        32.9f to 183.1f, 382.6f to -38.5f, 493.8f to 428.7f, 73.8f to 475.0f,
    )

    // right side mostly outside the frame, crumpled bottom border below long text rows
    @Test
    fun `page cut on the right`() = assertDetects(
        "page_cut_on_the_right.pgm",
        53.3f to 67.9f, 534.7f to -116.3f, 578.6f to 416.2f, 54.0f to 396.8f,
    )

    companion object {
        private const val TOLERANCE = 12f
    }
}
