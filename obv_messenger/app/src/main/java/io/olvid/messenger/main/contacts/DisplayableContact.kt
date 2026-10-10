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

package io.olvid.messenger.main.contacts

import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.settings.SettingsActivity


// This class is designed as a compose placeholder for a Contact, as a workaround
// for the too permissive .equals() method in the Contact class
data class DisplayableContact(
    val bytesOwnedIdentity: ByteArray,
    val bytesContactIdentity: ByteArray,
    val name: String, // this is the custom display name if one is set
    val description: String?, // if there is a custom display name, this is the original name, otherwise this is the position@company
    val photoUrl: String?, // this is the custom photo if there is one
    val active: Boolean,
    val hasChannelOrPreKey: Boolean,
    val recentlyOnline: Boolean,
    val keycloakManaged: Boolean,
    val oneToOne: Boolean,
    val trustLevel: Int,
) {
    constructor(contact : Contact) : this(
        bytesOwnedIdentity = contact.bytesContactIdentity,
        bytesContactIdentity = contact.bytesContactIdentity,
        name = contact.getCustomDisplayName(),
        description = runCatching {
            val identityDetails = contact.getIdentityDetails()
            if (contact.customDisplayName != null)
                return@runCatching identityDetails?.formatDisplayName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                    SettingsActivity.uppercaseLastName
                )
            else
                return@runCatching identityDetails?.formatPositionAndCompany(
                    SettingsActivity.contactDisplayNameFormat
                )
        }.getOrNull(),
        photoUrl = contact.getCustomPhotoUrl(),
        active = contact.active,
        hasChannelOrPreKey = contact.hasChannelOrPreKey(),
        recentlyOnline = contact.recentlyOnline,
        keycloakManaged = contact.keycloakManaged,
        oneToOne = contact.oneToOne,
        trustLevel = contact.trustLevel
    )

    fun avatarSource(): AvatarSource = AvatarSource.Single(
        bytes = bytesContactIdentity,
        initial = StringUtils.getInitial(name),
        photoUrl = App.absolutePathFromRelative(photoUrl),
        keycloakCertified = keycloakManaged,
        inactive = !active,
        notOneToOne = !oneToOne,
        recentlyOnline = recentlyOnline,
        trustLevel = trustLevel,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplayableContact) return false

        if (active != other.active) return false
        if (hasChannelOrPreKey != other.hasChannelOrPreKey) return false
        if (recentlyOnline != other.recentlyOnline) return false
        if (keycloakManaged != other.keycloakManaged) return false
        if (oneToOne != other.oneToOne) return false
        if (trustLevel != other.trustLevel) return false
        if (!bytesOwnedIdentity.contentEquals(other.bytesOwnedIdentity)) return false
        if (!bytesContactIdentity.contentEquals(other.bytesContactIdentity)) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (photoUrl != other.photoUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = active.hashCode()
        result = 31 * result + hasChannelOrPreKey.hashCode()
        result = 31 * result + recentlyOnline.hashCode()
        result = 31 * result + keycloakManaged.hashCode()
        result = 31 * result + oneToOne.hashCode()
        result = 31 * result + trustLevel
        result = 31 * result + bytesOwnedIdentity.contentHashCode()
        result = 31 * result + bytesContactIdentity.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (photoUrl?.hashCode() ?: 0)
        return result
    }

}
