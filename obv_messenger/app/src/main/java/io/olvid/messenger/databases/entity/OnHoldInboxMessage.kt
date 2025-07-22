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
package io.olvid.messenger.databases.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = OnHoldInboxMessage.TABLE_NAME,
    primaryKeys = [OnHoldInboxMessage.BYTES_OWNED_IDENTITY, OnHoldInboxMessage.MESSAGE_ENGINE_IDENTIFIER, OnHoldInboxMessage.WAITING_FOR_MESSAGE],
    indices = [
        Index(OnHoldInboxMessage.BYTES_OWNED_IDENTITY, OnHoldInboxMessage.EXPIRATION_TIMESTAMP),
        // indexes to efficiently update messages for a discussion
        Index(OnHoldInboxMessage.BYTES_OWNED_IDENTITY, OnHoldInboxMessage.WAITING_FOR_MESSAGE, OnHoldInboxMessage.BYTES_CONTACT_IDENTITY, OnHoldInboxMessage.BYTES_GROUP_OWNER_AND_UID, OnHoldInboxMessage.BYTES_GROUP_IDENTIFIER, OnHoldInboxMessage.SERVER_TIMESTAMP),
        // index to efficiently update messages for a message
        Index(OnHoldInboxMessage.BYTES_OWNED_IDENTITY, OnHoldInboxMessage.SENDER_IDENTIFIER, OnHoldInboxMessage.SENDER_THREAD_IDENTIFIER, OnHoldInboxMessage.SENDER_SEQUENCE_NUMBER, OnHoldInboxMessage.SERVER_TIMESTAMP),
        // index to efficiently fetch ready to process messages
        Index(OnHoldInboxMessage.BYTES_OWNED_IDENTITY, OnHoldInboxMessage.READY_TO_PROCESS, OnHoldInboxMessage.SERVER_TIMESTAMP),
    ]
)
data class OnHoldInboxMessage // default constructor required by Room
    (
    @JvmField @ColumnInfo(name = BYTES_OWNED_IDENTITY) var bytesOwnedIdentity: ByteArray,
    @JvmField @ColumnInfo(name = MESSAGE_ENGINE_IDENTIFIER) var messageEngineIdentifier: ByteArray,
    @JvmField @ColumnInfo(name = WAITING_FOR_MESSAGE) var waitingForMessage: Boolean,
    @JvmField @ColumnInfo(name = SERVER_TIMESTAMP) var serverTimestamp: Long,
    @JvmField @ColumnInfo(name = EXPIRATION_TIMESTAMP) var expirationTimestamp: Long?,

    @JvmField @ColumnInfo(name = BYTES_CONTACT_IDENTITY) var bytesContactIdentity: ByteArray?,
    @JvmField @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID) var bytesGroupOwnerAndUid: ByteArray?,
    @JvmField @ColumnInfo(name = BYTES_GROUP_IDENTIFIER) var bytesGroupIdentifier: ByteArray?,

    @JvmField @ColumnInfo(name = SENDER_IDENTIFIER) var senderIdentifier: ByteArray?,
    @JvmField @ColumnInfo(name = SENDER_THREAD_IDENTIFIER) var senderThreadIdentifier: UUID?,
    @JvmField @ColumnInfo(name = SENDER_SEQUENCE_NUMBER) var senderSequenceNumber: Long?,

    @JvmField @ColumnInfo(name = READY_TO_PROCESS) var readyToProcess: Boolean,
) {
    companion object {
        const val TABLE_NAME: String = "on_hold_inbox_message_table"

        const val BYTES_OWNED_IDENTITY: String = "bytes_owned_identity"
        const val MESSAGE_ENGINE_IDENTIFIER: String = "engine_message_identifier"
        const val WAITING_FOR_MESSAGE: String = "waiting_for_message"
        const val SERVER_TIMESTAMP: String = "server_timestamp"
        const val EXPIRATION_TIMESTAMP: String = "expiration_timestamp" // timestamp after which the message should be deleted
        // discussion reference: one of these 3 fields should be non-null
        const val BYTES_CONTACT_IDENTITY: String = "bytes_contact_identity"
        const val BYTES_GROUP_OWNER_AND_UID: String = "bytes_group_owner_and_uid"
        const val BYTES_GROUP_IDENTIFIER: String = "bytes_group_identifier"
        // message reference (for reactions, edit, delete, poll vote, etc.)
        const val SENDER_IDENTIFIER: String = "sender_identifier"
        const val SENDER_THREAD_IDENTIFIER: String = "sender_thread_identifier"
        const val SENDER_SEQUENCE_NUMBER: String = "sender_sequence_number"

        const val READY_TO_PROCESS: String = "ready_to_process"

        const val TTL: Long = 30 * 86_400_000L // expire message after 30 days
    }

    constructor(
        bytesOwnedIdentity: ByteArray,
        messageEngineIdentifier: ByteArray,
        serverTimestamp: Long,
        bytesContactIdentity: ByteArray?,
        bytesGroupOwner: ByteArray?,
        bytesGroupUid: ByteArray?,
        bytesGroupIdentifier: ByteArray?
    ) : this(
        bytesOwnedIdentity,
        messageEngineIdentifier,
        false,
        serverTimestamp,
        null,
        bytesContactIdentity,
        if (bytesGroupOwner != null && bytesGroupUid != null) bytesGroupOwner + bytesGroupUid else null,
        bytesGroupIdentifier,
        null,
        null,
        null,
        false
    )

    constructor(
        messageEngineIdentifier: ByteArray,
        serverTimestamp: Long,
        discussion: Discussion,
        senderIdentifier: ByteArray?,
        senderThreadIdentifier: UUID?,
        senderSequenceNumber: Long?,
    ) : this(
        discussion.bytesOwnedIdentity,
        messageEngineIdentifier,
        true,
        serverTimestamp,
        null,
        discussion.bytesDiscussionIdentifier.takeIf { discussion.discussionType == Discussion.TYPE_CONTACT },
        discussion.bytesDiscussionIdentifier.takeIf { discussion.discussionType == Discussion.TYPE_GROUP },
        discussion.bytesDiscussionIdentifier.takeIf { discussion.discussionType == Discussion.TYPE_GROUP_V2 },
        senderIdentifier,
        senderThreadIdentifier,
        senderSequenceNumber,
        false
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnHoldInboxMessage) return false

        if (waitingForMessage != other.waitingForMessage) return false
        if (serverTimestamp != other.serverTimestamp) return false
        if (expirationTimestamp != other.expirationTimestamp) return false
        if (senderSequenceNumber != other.senderSequenceNumber) return false
        if (readyToProcess != other.readyToProcess) return false
        if (!bytesOwnedIdentity.contentEquals(other.bytesOwnedIdentity)) return false
        if (!messageEngineIdentifier.contentEquals(other.messageEngineIdentifier)) return false
        if (!bytesContactIdentity.contentEquals(other.bytesContactIdentity)) return false
        if (!bytesGroupOwnerAndUid.contentEquals(other.bytesGroupOwnerAndUid)) return false
        if (!bytesGroupIdentifier.contentEquals(other.bytesGroupIdentifier)) return false
        if (!senderIdentifier.contentEquals(other.senderIdentifier)) return false
        if (senderThreadIdentifier != other.senderThreadIdentifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = waitingForMessage.hashCode()
        result = 31 * result + serverTimestamp.hashCode()
        result = 31 * result + (expirationTimestamp?.hashCode() ?: 0)
        result = 31 * result + (senderSequenceNumber?.hashCode() ?: 0)
        result = 31 * result + readyToProcess.hashCode()
        result = 31 * result + bytesOwnedIdentity.contentHashCode()
        result = 31 * result + messageEngineIdentifier.contentHashCode()
        result = 31 * result + (bytesContactIdentity?.contentHashCode() ?: 0)
        result = 31 * result + (bytesGroupOwnerAndUid?.contentHashCode() ?: 0)
        result = 31 * result + (bytesGroupIdentifier?.contentHashCode() ?: 0)
        result = 31 * result + (senderIdentifier?.contentHashCode() ?: 0)
        result = 31 * result + (senderThreadIdentifier?.hashCode() ?: 0)
        return result
    }

}