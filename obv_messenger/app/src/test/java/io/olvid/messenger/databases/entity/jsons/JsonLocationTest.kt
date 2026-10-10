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

package io.olvid.messenger.databases.entity.jsons

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonLocationTest {
    private val objectMapper = ObjectMapper()

    private fun parsePrecision(prec: String): Float? =
        objectMapper.readValue("""{"type":1,"lat":48.85,"long":2.35,"prec":$prec}""", JsonLocation::class.java).precision

    @Test
    fun `valid precision is kept`() {
        assertEquals(12.5f, parsePrecision("12.5"))
        assertEquals(0f, parsePrecision("0"))
    }

    @Test
    fun `negative or non-finite precision is dropped`() {
        assertNull(parsePrecision("-1"))
        assertNull(parsePrecision("\"NaN\""))
        assertNull(parsePrecision("\"Infinity\""))
        assertNull(parsePrecision("null"))
    }
}
