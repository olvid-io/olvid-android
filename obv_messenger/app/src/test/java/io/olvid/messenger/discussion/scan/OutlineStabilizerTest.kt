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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OutlineStabilizerTest {

    private fun quad(x: Float, y: Float, w: Float = 0.5f, h: Float = 0.7f) =
        Quad(ScanPoint(x, y), ScanPoint(x + w, y), ScanPoint(x + w, y + h), ScanPoint(x, y + h))

    private fun Quad.rotatedLabels() = Quad(topRight, bottomRight, bottomLeft, topLeft)

    @Test
    fun `smooths small moves`() {
        val stabilizer = OutlineStabilizer()
        stabilizer.update(quad(0.2f, 0.1f))
        val outline = stabilizer.update(quad(0.22f, 0.1f))!!

        assertEquals(0.21f, outline.topLeft.x, 1e-4f)
    }

    @Test
    fun `does not swap corners when the detection labels them differently`() {
        val stabilizer = OutlineStabilizer()
        stabilizer.update(quad(0.2f, 0.1f))
        val outline = stabilizer.update(quad(0.2f, 0.1f).rotatedLabels())!!

        assertEquals(quad(0.2f, 0.1f), outline)
    }

    @Test
    fun `ignores a different outline seen on a single frame`() {
        val stabilizer = OutlineStabilizer()
        val page = quad(0.2f, 0.1f)
        stabilizer.update(page)
        stabilizer.update(quad(0.3f, 0.25f, w = 0.3f, h = 0.3f))

        assertEquals(page, stabilizer.update(page))
    }

    @Test
    fun `switches to a different outline once it is stable`() {
        val stabilizer = OutlineStabilizer(switchFrames = 3)
        stabilizer.update(quad(0.2f, 0.1f))
        val other = quad(0.3f, 0.25f, w = 0.3f, h = 0.3f)
        stabilizer.update(other)
        stabilizer.update(other)

        assertEquals(other, stabilizer.update(other))
    }

    @Test
    fun `keeps the outline through a few missed frames only`() {
        val stabilizer = OutlineStabilizer(maxMisses = 2)
        val page = quad(0.2f, 0.1f)
        stabilizer.update(page)

        assertSame(stabilizer.outline, stabilizer.update(null))
        assertSame(stabilizer.outline, stabilizer.update(null))
        assertNull(stabilizer.update(null))
    }
}
