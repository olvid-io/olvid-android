/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.databases.tasks.migration

import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.tasks.UpdateAllGroupMembersNames





class SetContactsAndPendingMembersFirstNamesTask : Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()

        // set all contact first names
        db.contactDao().allSync.forEach { contact ->
            try {
                AppSingleton.getJsonObjectMapper()
                    .readValue(contact.identityDetails, JsonIdentityDetails::class.java)?.firstName?.let { firstName ->
                        contact.firstName = firstName
                        db.contactDao().updateFirstName(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.firstName)
                    }
            } catch (ignored: Exception) {
            }
        }

        // set all groupV2 members first names
        db.group2PendingMemberDao().all.forEach { pendingMember ->
            try {
                AppSingleton.getJsonObjectMapper()
                    .readValue(pendingMember.identityDetails, JsonIdentityDetails::class.java)?.firstName?.let { firstName ->
                        pendingMember.firstName = firstName
                        db.group2PendingMemberDao().updateFirstName(pendingMember.bytesOwnedIdentity, pendingMember.bytesGroupIdentifier, pendingMember.bytesContactIdentity, pendingMember.firstName)
                    }
            } catch (ignored: Exception) {
            }
        }

        // update all group members names
        UpdateAllGroupMembersNames().run()

        // reload cache
        AppSingleton.reloadCachedDisplayNamesAndHues()
    }
}