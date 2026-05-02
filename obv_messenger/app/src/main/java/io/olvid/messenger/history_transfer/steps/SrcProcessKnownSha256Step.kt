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

package io.olvid.messenger.history_transfer.steps

import io.olvid.engine.Logger
import io.olvid.messenger.history_transfer.json.DstExpectedSha256
import io.olvid.messenger.history_transfer.types.SrcTransferProtocolState
import kotlinx.coroutines.Runnable


class SrcProcessKnownSha256Step(
    val srcTransferProtocolState: SrcTransferProtocolState,
    val dstExpectedSha256: DstExpectedSha256,
) : Runnable {

    override fun run() {
        Logger.i("🫠 Running step SrcProcessKnownSha256Step")
        // this step should normally only be run after SrcSendDiscussionsStep has run its first part
        srcTransferProtocolState.discussionIdentifiers ?: return

        srcTransferProtocolState.expectedSha256s = dstExpectedSha256.expectedSha256s
        srcTransferProtocolState.totalBytes = dstExpectedSha256.expectedSha256s?.values?.sum() ?: 0L
    }
}