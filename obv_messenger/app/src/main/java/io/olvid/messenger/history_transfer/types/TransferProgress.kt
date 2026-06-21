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


sealed interface TransferProgress {
    data class DestinationWaitingForConfirmation(val sourceDeviceName: String?): TransferProgress
    data object ContactingOtherDevice: TransferProgress
    data object Connecting: TransferProgress
    data object Negotiating : TransferProgress
    data class Transferring(val messagesProgress: Int, val messagesTotal: Int, val filesProgress: Long, val filesTotal: Long): TransferProgress
    data object Finished: TransferProgress
    // the following state is only used on DST side, after a connection loss but received messages are still processing
    data class ProcessingReceivedData(val messagesProgress: Int, val messagesTotal: Int, val filesProgress: Long, val filesTotal: Long): TransferProgress
    data class Failed(val reason: TransferFailReason): TransferProgress

    @Composable
    fun TransferProgress?.getStepName(): String {
        return when (this) {
            is DestinationWaitingForConfirmation -> stringResource(R.string.history_transfer_step_awaiting_confirmation)
            ContactingOtherDevice -> stringResource(R.string.history_transfer_step_contacting_other_device)
            Connecting -> stringResource(R.string.history_transfer_step_connecting)
            Negotiating -> stringResource(R.string.history_transfer_step_negotiating)
            is Transferring -> stringResource(R.string.history_transfer_step_transferring)
            Finished -> stringResource(R.string.history_transfer_step_finished)
            is ProcessingReceivedData -> stringResource(R.string.history_transfer_step_processing_buffered_data)
            is Failed -> stringResource(R.string.history_transfer_step_failed)
            null -> stringResource(R.string.history_transfer_step_none)
        }
    }
}
