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

package io.olvid.messenger.onboarding.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardDeferredLinkTest {
    private val token = "AbC-xyz_0129"

    @Test
    fun flaggedReferrerIsDetected() {
        assertTrue(ClipboardDeferredLink.referrerCarriesFlag("olvid_link=1"))
        assertTrue(ClipboardDeferredLink.referrerCarriesFlag("utm_source=web&olvid_link=1&utm_medium=link"))
    }

    @Test
    fun unflaggedOrForeignReferrerIsIgnored() {
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag(""))
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag("olvid_link=0"))
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag("olvid_link="))
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag("utm_source=google-play&utm_medium=organic"))
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag("olvid_linkX=1"))
        // the legacy token payload is no longer accepted: the link must come from the clipboard
        assertFalse(ClipboardDeferredLink.referrerCarriesFlag("olvid_inv=$token"))
    }

    @Test
    fun everyOlvidLinkTypeIsExtracted() {
        assertEquals(
            "https://invitation.olvid.io/#$token",
            ClipboardDeferredLink.extractOlvidLink("https://invitation.olvid.io/#$token")
        )
        assertEquals(
            "https://invitation.olvid.io/2#$token",
            ClipboardDeferredLink.extractOlvidLink("https://invitation.olvid.io/2#$token")
        )
        assertEquals(
            "https://configuration.olvid.io/#$token",
            ClipboardDeferredLink.extractOlvidLink("https://configuration.olvid.io/#$token")
        )
        assertEquals(
            "https://web.olvid.io/#$token",
            ClipboardDeferredLink.extractOlvidLink("https://web.olvid.io/#$token")
        )
        assertEquals(
            "olvid://invitation.olvid.io/#$token",
            ClipboardDeferredLink.extractOlvidLink("olvid://invitation.olvid.io/#$token")
        )
    }

    @Test
    fun linkIsExtractedFromSurroundingText() {
        assertEquals(
            "https://invitation.olvid.io/#$token",
            ClipboardDeferredLink.extractOlvidLink("Join me on Olvid: https://invitation.olvid.io/#$token !")
        )
    }

    @Test
    fun clipboardWithoutOlvidLinkYieldsNothing() {
        assertNull(ClipboardDeferredLink.extractOlvidLink(null))
        assertNull(ClipboardDeferredLink.extractOlvidLink(""))
        assertNull(ClipboardDeferredLink.extractOlvidLink("https://play.google.com/store/apps/details?id=io.olvid.messenger"))
        assertNull(ClipboardDeferredLink.extractOlvidLink("https://invitation.olvid.io/"))
        assertNull(ClipboardDeferredLink.extractOlvidLink("https://evil.example/#$token"))
    }
}
