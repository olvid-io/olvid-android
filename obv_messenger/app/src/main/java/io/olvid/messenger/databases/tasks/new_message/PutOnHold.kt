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

package io.olvid.messenger.databases.tasks.new_message

import io.olvid.engine.Logger
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.OnHoldInboxMessage
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference
import io.olvid.messenger.databases.entity.jsons.JsonOneToOneMessageIdentifier


// returns true if the message was put on hold because the corresponding discussion is missing
fun putMessageOnHoldIfDiscussionIsMissing(
    db: AppDatabase,
    messageEngineIdentifier: ByteArray,
    serverTimestamp: Long,
    messageSender: MessageSender,
    oneToOneMessageIdentifier: JsonOneToOneMessageIdentifier?,
    bytesGroupOwner: ByteArray?,
    bytesGroupUid: ByteArray?,
    bytesGroupIdentifier: ByteArray?,
): Boolean {
    // for owned message, do not check the existence of the group/contact, but of the discussion --> this allows to properly delete locked discussions too
    // for other messages, check the group/contact exists
    val putOnHold = if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
        when {
            bytesGroupUid != null && bytesGroupOwner != null -> {
                db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(messageSender.bytesOwnedIdentity, bytesGroupOwner + bytesGroupUid) == null
            }
            bytesGroupIdentifier != null -> {
                db.discussionDao().getByGroupIdentifierWithAnyStatus(messageSender.bytesOwnedIdentity, bytesGroupIdentifier) == null
            }
            oneToOneMessageIdentifier != null -> {
                oneToOneMessageIdentifier.getBytesContactIdentity(messageSender.bytesOwnedIdentity)?.let { bytesContactIdentity ->
                    db.discussionDao().getByContactWithAnyStatus(
                        messageSender.bytesOwnedIdentity,
                        bytesContactIdentity
                    ) == null
                } ?: false
            }
            else -> false // this never happens for properly formatted messages
        }
    } else {
        when {
            bytesGroupUid != null && bytesGroupOwner != null -> {
                db.groupDao().get(messageSender.bytesOwnedIdentity, bytesGroupOwner + bytesGroupUid)?.let { group ->
                    db.contactGroupJoinDao()
                        .isGroupMember(messageSender.bytesOwnedIdentity, messageSender.senderIdentity, group.bytesGroupOwnerAndUid)
                        .not()
                } ?: true
            }
            bytesGroupIdentifier != null -> {
                db.group2Dao().get(messageSender.bytesOwnedIdentity, bytesGroupIdentifier)?.let {
                    db.group2Dao()
                        .isContactAMemberOrPendingMember(messageSender.bytesOwnedIdentity, bytesGroupIdentifier, messageSender.senderIdentity)
                        .not()
                } ?: true
            }
            oneToOneMessageIdentifier != null -> {
                oneToOneMessageIdentifier.getBytesContactIdentity(messageSender.bytesOwnedIdentity)?.let { bytesContactIdentity ->
                    // only put on hold if contact is non-null and
                    db.contactDao()
                        .get(messageSender.bytesOwnedIdentity, bytesContactIdentity)
                        ?.oneToOne == false
                } ?: false
            }
            else -> false // this never happens for properly formatted messages
        }
    }

    if (putOnHold) {
        Logger.i("⏸️ Putting received message on hold until discussion is created ${Logger.toHexString(messageEngineIdentifier.sliceArray(0..4))}")
        // the OnHoldInboxMessageDao.insert specifies an OnConflictStrategy.IGNORE, so no problem if this message is already on hold
        db.onHoldInboxMessageDao().insert(
            OnHoldInboxMessage(
                messageSender.bytesOwnedIdentity,
                messageEngineIdentifier,
                serverTimestamp,
                oneToOneMessageIdentifier?.getBytesContactIdentity(messageSender.bytesOwnedIdentity),
                bytesGroupOwner,
                bytesGroupUid,
                bytesGroupIdentifier,
            )
        )
        return true
    }
    return false
}


fun putMessageOnHoldBecauseOfMissingMessage(
    db: AppDatabase,
    messageEngineIdentifier: ByteArray,
    serverTimestamp: Long,
    discussion: Discussion,
    messageReference: JsonMessageReference,
) {
    Logger.i("⏸️ Putting received message on hold until message is received ${Logger.toHexString(messageEngineIdentifier.sliceArray(0..4))}")
    db.onHoldInboxMessageDao().insert(
        OnHoldInboxMessage(
            messageEngineIdentifier,
            serverTimestamp,
            discussion,
            messageReference.senderIdentifier,
            messageReference.senderThreadIdentifier,
            messageReference.senderSequenceNumber
        )
    )
}
