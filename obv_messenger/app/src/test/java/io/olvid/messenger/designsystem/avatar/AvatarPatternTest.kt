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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class AvatarPatternTest {

    private val random = Random(42)

    private fun randomIdentity() = random.nextBytes(64)

    @Test
    fun `same bytes always produce the same spec`() {
        repeat(50) {
            val bytes = randomIdentity()
            assertEquals(avatarPattern(bytes, darkMode = false), avatarPattern(bytes.copyOf(), darkMode = false))
            assertEquals(avatarPattern(bytes, darkMode = true), avatarPattern(bytes.copyOf(), darkMode = true))
        }
    }

    @Test
    fun `dark and light theme share geometry and differ only in colors`() {
        repeat(50) {
            val bytes = randomIdentity()
            val light = avatarPattern(bytes, darkMode = false)
            val dark = avatarPattern(bytes, darkMode = true)
            assertEquals(3, light.rects.size)
            light.rects.zip(dark.rects).forEach { (l, d) ->
                assertEquals(l.centerX, d.centerX, 0f)
                assertEquals(l.centerY, d.centerY, 0f)
                assertEquals(l.width, d.width, 0f)
                assertEquals(l.height, d.height, 0f)
                assertEquals(l.rotationDegrees, d.rotationDegrees, 0f)
            }
            // the dark palette is a darker triad of the same family, never the same background
            assertNotEquals(light.background, dark.background)
        }
    }

    @Test
    fun `all 12 color families are reachable`() {
        val backgrounds = mutableSetOf<Int>()
        repeat(2000) {
            backgrounds.add(hueFromBytes(randomIdentity()) * 12 / 360)
        }
        assertEquals((0..11).toSet(), backgrounds)
    }

    @Test
    fun `geometry stays within the documented ranges`() {
        repeat(200) {
            val spec = avatarPattern(randomIdentity(), darkMode = false)
            spec.rects.forEachIndexed { _, rect ->
                assertTrue(rect.width in 0.25f..1.00f)
                assertTrue(rect.height in 0.40f..1.25f)
                assertTrue(hypot(rect.centerX, rect.centerY) in 0.20f..0.65f)
                assertTrue(rect.rotationDegrees in 0f..360f)
            }
        }
    }

    @Test
    fun `rects use exactly two shades, both distinct from the background`() {
        repeat(200) {
            val spec = avatarPattern(randomIdentity(), darkMode = it % 2 == 0)
            val rectColors = spec.rects.map { rect -> rect.color }.toSet()
            assertEquals(2, rectColors.size)
            assertTrue(spec.background !in rectColors)
        }
    }
}
