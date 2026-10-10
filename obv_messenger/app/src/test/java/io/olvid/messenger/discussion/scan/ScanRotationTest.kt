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
import org.junit.Test

class ScanRotationTest {
    private val w = 300f
    private val h = 200f

    @Test
    fun `rotations are clockwise, like Matrix postRotate`() {
        // a w × h picture rotated by 90° is h wide: its top left corner ends up at the top right
        assertEquals(ScanPoint(h, 0f), rotate(ScanPoint(0f, 0f), 90, w, h))
        assertEquals(ScanPoint(0f, 0f), rotate(ScanPoint(w, 0f), 270, w, h))
        assertEquals(ScanPoint(w, h), rotate(ScanPoint(0f, 0f), 180, w, h))
    }

    @Test
    fun `unrotate is the inverse of rotate`() {
        val point = ScanPoint(37f, 151f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            assertEquals("rotation $rotation", point, unrotate(rotate(point, rotation, w, h), rotation, w, h))
        }
    }
}
