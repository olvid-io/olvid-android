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

import io.olvid.engine.engine.types.ObvProfileBackupsForRestore
import io.olvid.engine.engine.types.sync.ObvSyncSnapshot


data class ProfileBackupSnapshot(
    val threadId: ByteArray,
    val version: Long,
    val timestamp: Long,
    val thisDevice: Boolean,
    val deviceName: String?,
    val platform: String?,
    val contactCount: Int,
    val groupCount: Int,
    val keycloakStatus: ObvProfileBackupsForRestore.KeycloakStatus,
    val keycloakInfo: KeycloakInfo?,
    val snapshot: ObvSyncSnapshot,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileBackupSnapshot) return false

        if (version != other.version) return false
        if (timestamp != other.timestamp) return false
        if (thisDevice != other.thisDevice) return false
        if (contactCount != other.contactCount) return false
        if (groupCount != other.groupCount) return false
        if (!threadId.contentEquals(other.threadId)) return false
        if (deviceName != other.deviceName) return false
        if (platform != other.platform) return false
        if (keycloakStatus != other.keycloakStatus) return false
        if (keycloakInfo != other.keycloakInfo) return false
        if (snapshot != other.snapshot) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + thisDevice.hashCode()
        result = 31 * result + contactCount
        result = 31 * result + groupCount
        result = 31 * result + threadId.contentHashCode()
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + (platform?.hashCode() ?: 0)
        result = 31 * result + keycloakStatus.hashCode()
        result = 31 * result + (keycloakInfo?.hashCode() ?: 0)
        result = 31 * result + snapshot.hashCode()
        return result
    }

}

data class KeycloakInfo(val serverUrl: String, val clientId: String, val clientSecret: String?)