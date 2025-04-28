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

package io.olvid.messenger.customClasses

import io.olvid.engine.engine.types.JsonIdentityDetails

data class DeviceBackupProfile(
    val bytesProfileIdentity: ByteArray,
    val nickName: String?,
    val identityDetails: JsonIdentityDetails,
    val keycloakManaged: Boolean,
    val photo: Any?, // can be a url or a ProfilePictureLabelAndKey --> used by AsyncImage
    val profileAlreadyPresent: Boolean,
    val profileBackupSeed: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceBackupProfile) return false

        if (keycloakManaged != other.keycloakManaged) return false
        if (profileAlreadyPresent != other.profileAlreadyPresent) return false
        if (!bytesProfileIdentity.contentEquals(other.bytesProfileIdentity)) return false
        if (nickName != other.nickName) return false
        if (identityDetails != other.identityDetails) return false
        if (photo != other.photo) return false
        if (profileBackupSeed != other.profileBackupSeed) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keycloakManaged.hashCode()
        result = 31 * result + profileAlreadyPresent.hashCode()
        result = 31 * result + bytesProfileIdentity.contentHashCode()
        result = 31 * result + (nickName?.hashCode() ?: 0)
        result = 31 * result + identityDetails.hashCode()
        result = 31 * result + (photo?.hashCode() ?: 0)
        result = 31 * result + profileBackupSeed.hashCode()
        return result
    }
}