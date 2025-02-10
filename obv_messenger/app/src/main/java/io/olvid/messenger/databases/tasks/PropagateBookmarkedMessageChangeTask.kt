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

package io.olvid.messenger.databases.tasks

import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.engine.engine.types.sync.ObvSyncAtom.MessageIdentifier
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
class PropagateBookmarkedMessageChangeTask(private val bytesOwnedIdentity : ByteArray, private val message: Message, private val bookmarked : Boolean) : Runnable {
    override fun run() {
        try {
            val discussionIdentifier = AppDatabase.getInstance().discussionDao().getById(message.discussionId)?.run {
                ObvSyncAtom.DiscussionIdentifier(
                    when (discussionType) {
                        Discussion.TYPE_CONTACT -> ObvSyncAtom.DiscussionIdentifier.CONTACT
                        Discussion.TYPE_GROUP -> ObvSyncAtom.DiscussionIdentifier.GROUP_V1
                        Discussion.TYPE_GROUP_V2 -> ObvSyncAtom.DiscussionIdentifier.GROUP_V2
                        else -> ObvSyncAtom.DiscussionIdentifier.CONTACT // should never happen
                    },
                    bytesDiscussionIdentifier
                )
            }
            val messageIdentifier =  MessageIdentifier(discussionIdentifier, message.senderIdentifier, message.senderThreadIdentifier, message.senderSequenceNumber)
            AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createBookmarkedMessageChange(messageIdentifier, bookmarked))
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }
}