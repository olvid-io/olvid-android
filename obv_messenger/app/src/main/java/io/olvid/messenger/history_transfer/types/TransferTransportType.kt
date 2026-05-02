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

package io.olvid.messenger.history_transfer.types

import android.net.Uri


sealed interface TransferTransportType {
    data class WebRtcWithOwnedDevice(val bytesOwnedIdentity: ByteArray, val bytesOtherDeviceUid: ByteArray) : TransferTransportType {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WebRtcWithOwnedDevice) return false

            if (!bytesOwnedIdentity.contentEquals(other.bytesOwnedIdentity)) return false
            if (!bytesOtherDeviceUid.contentEquals(other.bytesOtherDeviceUid)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytesOwnedIdentity.contentHashCode()
            result = 31 * result + bytesOtherDeviceUid.contentHashCode()
            return result
        }
    }

    @Suppress("ArrayInDataClass")
    data class ZipFileExport(val bytesOwnedIdentity: ByteArray, val zipWritableFileUri: Uri, val password: String?) : TransferTransportType

    @Suppress("ArrayInDataClass")
    data class ZipFileImport(val bytesOwnedIdentity: ByteArray, val zipReadableFileUri: Uri, val password: String?) : TransferTransportType
}