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

package io.olvid.messenger.databases.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.olvid.messenger.databases.entity.OnHoldInboxMessage
import java.util.UUID

@Dao
interface OnHoldInboxMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(onHoldInboxMessage: OnHoldInboxMessage)

    @Update
    fun update(onHoldInboxMessage: OnHoldInboxMessage)

    @Delete
    fun delete(onHoldInboxMessage: OnHoldInboxMessage)


    @Query(
        "UPDATE " + OnHoldInboxMessage.TABLE_NAME +
                " SET " + OnHoldInboxMessage.EXPIRATION_TIMESTAMP + " = :expirationTimestamp " +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.EXPIRATION_TIMESTAMP + " IS NULL "
    )
    fun setExpirationOfAllWithoutExpiration(
        bytesOwnedIdentity: ByteArray,
        expirationTimestamp: Long
    ): Int

    @Query(
        "SELECT * FROM " + OnHoldInboxMessage.TABLE_NAME +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.EXPIRATION_TIMESTAMP + " < :currentTimestamp "
    )
    fun getAllExpired(
        bytesOwnedIdentity: ByteArray,
        currentTimestamp: Long
    ): List<OnHoldInboxMessage>


    @Query(
        "SELECT * FROM " + OnHoldInboxMessage.TABLE_NAME +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " ORDER BY " + OnHoldInboxMessage.SERVER_TIMESTAMP + " ASC "
    )
    fun getAllReadyToProcessForOwnedIdentity(
        bytesOwnedIdentity: ByteArray,
    ): List<OnHoldInboxMessage>

    @Query(
        "SELECT * FROM " + OnHoldInboxMessage.TABLE_NAME +
                " WHERE " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " ORDER BY " + OnHoldInboxMessage.SERVER_TIMESTAMP + " ASC "
    )
    fun getAllReadyToProcess(): List<OnHoldInboxMessage>


    @Query(
        "UPDATE " + OnHoldInboxMessage.TABLE_NAME +
                " SET " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.WAITING_FOR_MESSAGE + " = 0 " +
                " AND " + OnHoldInboxMessage.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_OWNER_AND_UID + " IS NULL " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_IDENTIFIER + " IS NULL "
    )
    fun markAsReadyToProcessForContactDiscussion(
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentity: ByteArray,
    ): Int

    @Query(
        "UPDATE " + OnHoldInboxMessage.TABLE_NAME +
                " SET " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.WAITING_FOR_MESSAGE + " = 0 " +
                " AND " + OnHoldInboxMessage.BYTES_CONTACT_IDENTITY + " IS NULL " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_IDENTIFIER + " IS NULL "
    )
    fun markAsReadyToProcessForGroupDiscussion(
        bytesOwnedIdentity: ByteArray,
        bytesGroupOwnerAndUid: ByteArray,
    ): Int

    @Query(
        "UPDATE " + OnHoldInboxMessage.TABLE_NAME +
                " SET " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.WAITING_FOR_MESSAGE + " = 0 " +
                " AND " + OnHoldInboxMessage.BYTES_CONTACT_IDENTITY + " IS NULL " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_OWNER_AND_UID + " IS NULL " +
                " AND " + OnHoldInboxMessage.BYTES_GROUP_IDENTIFIER + " = :bytesGroupIdentifier "
    )
    fun markAsReadyToProcessForGroupV2Discussion(
        bytesOwnedIdentity: ByteArray,
        bytesGroupIdentifier: ByteArray,
    ): Int


    @Query(
        "UPDATE " + OnHoldInboxMessage.TABLE_NAME +
                " SET " + OnHoldInboxMessage.READY_TO_PROCESS + " = 1 " +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.SENDER_IDENTIFIER + " = :senderIdentifier " +
                " AND " + OnHoldInboxMessage.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
                " AND " + OnHoldInboxMessage.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber "
    )
    fun markAsReadyToProcessForMessage(
        bytesOwnedIdentity: ByteArray,
        senderIdentifier: ByteArray,
        senderThreadIdentifier: UUID,
        senderSequenceNumber: Long,
    ): Int

    @Query(
        "SELECT * FROM " + OnHoldInboxMessage.TABLE_NAME +
                " WHERE " + OnHoldInboxMessage.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " AND " + OnHoldInboxMessage.SENDER_IDENTIFIER + " = :senderIdentifier " +
                " AND " + OnHoldInboxMessage.SENDER_THREAD_IDENTIFIER + " = :senderThreadIdentifier " +
                " AND " + OnHoldInboxMessage.SENDER_SEQUENCE_NUMBER + " = :senderSequenceNumber "
    )
    fun getAllForMessage(
        bytesOwnedIdentity: ByteArray,
        senderIdentifier: ByteArray,
        senderThreadIdentifier: UUID,
        senderSequenceNumber: Long,
    ): List<OnHoldInboxMessage>

}