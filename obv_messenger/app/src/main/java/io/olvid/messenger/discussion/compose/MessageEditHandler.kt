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

package io.olvid.messenger.discussion.compose

import io.olvid.messenger.App
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.SaveDraftTask
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.mention.MentionViewModel

class MessageEditHandler(
    private val discussionViewModel: DiscussionViewModel,
    private val composeMessageViewModel: ComposeMessageViewModel,
    private val mentionViewModel: MentionViewModel,
) {
    fun getCurrentComposeMessageText(): String {
        return composeMessageViewModel.rawNewMessageText.toString()
    }

    fun enterEditModeIfAllowed(message: Message) {
        if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE
            || message.wipeStatus == Message.WIPE_STATUS_WIPED
            || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
            || message.isLocationMessage
            || message.isPollMessage
            || discussionViewModel.locked == true
            || discussionViewModel.canEdit != true
        ) {
            // prevent editing messages that cannot be edited
            return
        }
        discussionViewModel.discussionId?.let {
            // only save draft if we were not already in edit mode
            if (!isEditMode()) {
                // keep values and save draft after edit mode is on
                val previousDraft = composeMessageViewModel.getDraftMessage().value
                val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                    composeMessageViewModel.rawNewMessageText,
                    mentionViewModel.mentions
                )
                App.runThread(
                    SaveDraftTask(
                        it,
                        trimAndMentions.first,
                        previousDraft,
                        trimAndMentions.second,
                        true
                    )
                )
            }
            composeMessageViewModel.setMessageBeingEdited(message)
        }
    }

    fun isEditMode(): Boolean {
        return composeMessageViewModel.getMessageBeingEdited().value != null
    }
}