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

package io.olvid.messenger.group.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMemberRolesTest {
    private fun member(admin: Boolean = false, remoteDeleteAnything: Boolean = false) = GroupMember(
        bytesIdentity = byteArrayOf(1),
        contact = null,
        jsonIdentityDetails = null,
        fullSearchDisplayName = "",
        pending = false,
        isAdmin = admin,
        permissionRemoteDeleteAnything = remoteDeleteAnything,
        selected = false,
    )

    @Test
    fun remoteDeleteAnythingIsModeratorOnlyInKeycloakGroups() {
        assertTrue(member(remoteDeleteAnything = true).isModerator(keycloakGroup = true))
        assertFalse(member(remoteDeleteAnything = true).isModerator(keycloakGroup = false))
    }

    @Test
    fun adminIsNotTaggedModerator() {
        assertFalse(member(admin = true, remoteDeleteAnything = true).isModerator(keycloakGroup = true))
    }

    @Test
    fun noRemoteDeletePermissionIsNotModerator() {
        assertFalse(member().isModerator(keycloakGroup = true))
    }
}
