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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.olvid.messenger.R


enum class TransferFailReason {
    ABORTED, // this is only visible for user initiated aborts, for disconnect UNKNOWN_REASON is shown
    UNKNOWN_REASON, // most often this is a connection loss, but could be an internal exception

    // only used when restoring a zip
    OWNED_IDENTITY_MISMATCH,
    BAD_ZIP_PASSWORD,
    BAD_ZIP_FORMAT,
}

@Composable
fun TransferFailReason.getDescription(): String {
    return when (this) {
        TransferFailReason.ABORTED -> stringResource(R.string.history_transfer_fail_reason_aborted)
        TransferFailReason.UNKNOWN_REASON -> stringResource(R.string.history_transfer_fail_reason_unknown)
        TransferFailReason.OWNED_IDENTITY_MISMATCH -> stringResource(R.string.history_transfer_fail_reason_identity_mismatch)
        TransferFailReason.BAD_ZIP_PASSWORD -> stringResource(R.string.history_transfer_fail_reason_wrong_passwrod)
        TransferFailReason.BAD_ZIP_FORMAT -> stringResource(R.string.history_transfer_fail_reason_invalid_zip)
    }
}