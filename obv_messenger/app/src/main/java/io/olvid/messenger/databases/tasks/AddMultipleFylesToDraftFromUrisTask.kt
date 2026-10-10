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

import android.net.Uri
import android.provider.OpenableColumns
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.StringUtils2.Companion.normalize
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import java.util.concurrent.ConcurrentHashMap

/**
 * Attaches several files to the discussion draft, one after the other, all to the same message.
 * The message is flagged [isBeingBuilt] meanwhile, so that [Message.post] holds it (like an attachment
 * still COPYING) if the user presses send before the last file is in; it is re-posted at the end.
 * The flag is in memory only: after a process death, [DiscardInterruptedAttachmentCopiesTask] takes over.
 */
class AddMultipleFylesToDraftFromUrisTask(
    private val uris: List<Uri>,
    private val discussionId: Long,
) : Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()
        val discussion = db.discussionDao().getById(discussionId) ?: return
        val draft = db.runInTransaction<Message> {
            db.messageDao().getDiscussionDraftMessageSync(discussionId)
                ?: Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier)
                    .also { it.id = db.messageDao().insert(it) }
        }

        buildersPerMessage.merge(draft.id, 1, Int::plus)
        try {
            for (uri in uris) {
                AddFyleToDraftFromUriTask(draft, uri, null, null, discussionId).run()
            }
        } finally {
            buildersPerMessage.computeIfPresent(draft.id) { _, count -> (count - 1).takeIf { it > 0 } }
            // send the message if it was put on hold while we were adding files
            AddFyleToDraftFromUriTask.repostIfDraftIsUnprocessed(db, draft.id)
        }
    }

    companion object {
        private val buildersPerMessage = ConcurrentHashMap<Long, Int>()

        /** true while at least one [AddMultipleFylesToDraftFromUrisTask] is still adding files to this message */
        @JvmStatic
        fun isBeingBuilt(messageId: Long): Boolean = buildersPerMessage.containsKey(messageId)
    }
}

/** Pickers return files in an arbitrary order: sort them by display name so the attachment order is predictable. */
fun List<Uri>.sortedByDisplayName(): List<Uri> {
    val resolver = App.getContext().contentResolver
    return map { uri ->
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: ""
        uri to name.normalize()
    }.sortedBy { it.second }.map { it.first }
}
