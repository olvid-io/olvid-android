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

package io.olvid.messenger.databases.tasks

import io.olvid.engine.Logger
import io.olvid.messenger.databases.AppDatabase
import java.io.File

/**
 * App start: an attachment left in COPYING status by a previous process can never complete (the source
 * Uri permission died with that process) and would keep its message from ever being posted. Drop these
 * placeholders; the caller then re-posts UNPROCESSED messages with the attachments that made it.
 */
class DiscardInterruptedAttachmentCopiesTask : Runnable {
    override fun run() {
        val copying = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getCopyingFylesAndStatus()
        if (copying.isEmpty()) {
            return
        }
        Logger.i("Discarding ${copying.size} attachment(s) whose copy was interrupted")
        for (fyleAndStatus in copying) {
            // The DiscardInterruptedAttachmentCopiesTask is run with a delay at startup, so some attachment may be
            // in the process of being attached
            if (AddMultipleFylesToDraftFromUrisTask.isBeingBuilt(fyleAndStatus.fyleMessageJoinWithStatus.messageId)) {
                Logger.i("Skipping 1 still being attached")
                continue
            }

            runCatching { File(fyleAndStatus.fyleMessageJoinWithStatus.absoluteFilePath).delete() }
            DeleteAttachmentTask(fyleAndStatus).run()
        }
    }
}
