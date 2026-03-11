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

package io.olvid.messenger.history_transfer.types

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.olvid.messenger.R


sealed interface TransferProgress {
    data object ContactingOtherDevice: TransferProgress
    data object Connecting: TransferProgress
    data object Negotiating : TransferProgress
    data class TransferringMessages(val progress: Int, val total: Int) : TransferProgress
    data class TransferringFiles(val progress: Long, val total: Long) : TransferProgress
    data object Finished: TransferProgress
    data class Failed(val reason: TransferFailReason): TransferProgress

    @Composable
    fun TransferProgress?.getStepName(shortName: Boolean): String {
        return when (this) {
            ContactingOtherDevice -> stringResource(R.string.history_transfer_step_contacting_other_device)
            Connecting -> stringResource(R.string.history_transfer_step_connecting)
            Negotiating -> if (shortName) stringResource(R.string.history_transfer_step_negotiating_short) else stringResource(R.string.history_transfer_step_negotiating)
            is TransferringMessages -> if (shortName) stringResource(R.string.history_transfer_step_transferring_messages_short) else stringResource(R.string.history_transfer_step_transferring_messages)
            is TransferringFiles -> if (shortName) stringResource(R.string.history_transfer_step_transferring_files_short) else stringResource(R.string.history_transfer_step_transferring_files)
            Finished -> stringResource(R.string.history_transfer_step_finished)
            is Failed -> stringResource(R.string.history_transfer_step_failed) // TODO: change message depending on fail reason
            null -> stringResource(R.string.history_transfer_step_none)
        }
    }
}
