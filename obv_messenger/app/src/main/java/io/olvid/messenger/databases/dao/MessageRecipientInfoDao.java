/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.databases.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;


@Dao
public interface MessageRecipientInfoDao {
    String PREFIX_MESSAGE_RECIPIENT_INFO_COLUMNS = " mri." + MessageRecipientInfo.MESSAGE_ID + " AS mri_" + MessageRecipientInfo.MESSAGE_ID + ", " +
            " mri." + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + " AS mri_" + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + ", " +
            " mri." + MessageRecipientInfo.RETURN_RECEIPT_NONCE + " AS mri_" + MessageRecipientInfo.RETURN_RECEIPT_NONCE + ", " +
            " mri." + MessageRecipientInfo.RETURN_RECEIPT_KEY + " AS mri_" + MessageRecipientInfo.RETURN_RECEIPT_KEY + ", " +
            " mri." + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + " AS mri_" + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + ", " +
            " mri." + MessageRecipientInfo.UNSENT_ATTACHMENT_NUMBERS + " AS mri_" + MessageRecipientInfo.UNSENT_ATTACHMENT_NUMBERS + ", " +
            " mri." + MessageRecipientInfo.TIMESTAMP_SENT + " AS mri_" + MessageRecipientInfo.TIMESTAMP_SENT + ", " +
            " mri." + MessageRecipientInfo.TIMESTAMP_DELIVERED + " AS mri_" + MessageRecipientInfo.TIMESTAMP_DELIVERED + ", " +
            " mri." + MessageRecipientInfo.TIMESTAMP_READ + " AS mri_" + MessageRecipientInfo.TIMESTAMP_READ + ", " +
            " mri." + MessageRecipientInfo.UNDELIVERED_ATTACHMENT_NUMBERS + " AS mri_" + MessageRecipientInfo.UNDELIVERED_ATTACHMENT_NUMBERS + ", " +
            " mri." + MessageRecipientInfo.UNREAD_ATTACHMENT_NUMBERS + " AS mri_" + MessageRecipientInfo.UNREAD_ATTACHMENT_NUMBERS;

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(MessageRecipientInfo... messageRecipientInfos);

    @Delete
    void delete(MessageRecipientInfo... messageRecipientInfos);

    @Update(onConflict = OnConflictStrategy.IGNORE)
    void update(MessageRecipientInfo... messageRecipientInfos);

    @Query("SELECT mri.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON mri." + MessageRecipientInfo.MESSAGE_ID + " = mess.id " +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON mess." + Message.DISCUSSION_ID + " = disc.id " +
            " WHERE disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mri." + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + " = :engineIdentifier")
    List<MessageRecipientInfo> getAllByEngineMessageIdentifier(byte[] bytesOwnedIdentity, byte[] engineIdentifier);

    @Query("SELECT * FROM " + MessageRecipientInfo.TABLE_NAME + " WHERE " + MessageRecipientInfo.MESSAGE_ID + " = :messageId")
    List<MessageRecipientInfo> getAllByMessageId(long messageId);

    @Query("SELECT * FROM " + MessageRecipientInfo.TABLE_NAME +
            " WHERE " + MessageRecipientInfo.MESSAGE_ID + " = :messageId " +
            " ORDER BY CASE WHEN " + MessageRecipientInfo.TIMESTAMP_READ + " IS NULL THEN 1 ELSE 0 END, " + MessageRecipientInfo.TIMESTAMP_READ + ", " +
            " CASE WHEN " + MessageRecipientInfo.TIMESTAMP_DELIVERED + " IS NULL THEN 1 ELSE 0 END, " + MessageRecipientInfo.TIMESTAMP_DELIVERED + ", " +
            " CASE WHEN " + MessageRecipientInfo.TIMESTAMP_SENT + " IS NULL THEN 1 ELSE 0 END, " + MessageRecipientInfo.TIMESTAMP_SENT)
    LiveData<List<MessageRecipientInfo>> getAllByMessageIdLiveAndSorted(long messageId);


    @Query("SELECT * FROM " + MessageRecipientInfo.TABLE_NAME +
            " WHERE " + MessageRecipientInfo.MESSAGE_ID + " = :messageId " +
            " AND " + MessageRecipientInfo.TIMESTAMP_SENT + " IS NULL")
    List<MessageRecipientInfo> getAllNotSentByMessageId(long messageId);

    @Query("SELECT MIN(" + MessageRecipientInfo.TIMESTAMP_SENT + ") FROM " + MessageRecipientInfo.TABLE_NAME +
            " WHERE " + MessageRecipientInfo.MESSAGE_ID + " = :messageId " +
            " AND " + MessageRecipientInfo.TIMESTAMP_SENT + " IS NOT NULL")
    Long getOriginalServerTimestampForMessage(long messageId);

    @Query("SELECT " + PREFIX_MESSAGE_RECIPIENT_INFO_COLUMNS + ", " +
            " message.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS message " +
            " ON message.id = mri." + MessageRecipientInfo.MESSAGE_ID +
            " WHERE mri." + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND message." + Message.SENDER_IDENTIFIER + " = :bytesOwnedIdentity " +
            " AND mri." + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + " IS NULL")
    List<MessageRecipientInfoAndMessage> getAllUnsentForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT DISTINCT " + MessageRecipientInfo.RETURN_RECEIPT_KEY + " FROM " + MessageRecipientInfo.TABLE_NAME +
            " WHERE " + MessageRecipientInfo.RETURN_RECEIPT_NONCE + " = :nonce")
    List<byte[]> getReturnReceiptKeysForNonce(byte[] nonce);

    @Query("SELECT mri.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS message " +
            " ON message.id = mri." + MessageRecipientInfo.MESSAGE_ID +
            " WHERE message." + Message.SENDER_IDENTIFIER + " = :bytesOwnedIdentity " +
            " AND message." + Message.MESSAGE_TYPE + " = " + Message.TYPE_OUTBOUND_MESSAGE +
            " AND mri." + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity" +
            " AND mri." + MessageRecipientInfo.RETURN_RECEIPT_NONCE + " = :returnReceiptNonce" +
            " AND mri." + MessageRecipientInfo.RETURN_RECEIPT_KEY + " = :returnReceiptKey")
    List<MessageRecipientInfo> getFromReturnReceipt(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, byte[] returnReceiptNonce, byte[] returnReceiptKey);

    @Query("SELECT mri.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS m " +
            " ON m.id = mri." + MessageRecipientInfo.MESSAGE_ID +
            " INNER JOIN " + Discussion.TABLE_NAME + " AS d " +
            " ON d.id = m." + Message.DISCUSSION_ID +
            " WHERE d." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND mri." + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND mri." + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + " IS NULL")
    List<MessageRecipientInfo> getUnsentForContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT " + PREFIX_MESSAGE_RECIPIENT_INFO_COLUMNS + ", m.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS m " +
            " ON m.id = mri." + MessageRecipientInfo.MESSAGE_ID +
            " WHERE m." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND mri." + MessageRecipientInfo.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND mri." + MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER + " IS NULL")
    List<MessageRecipientInfoAndMessage> getUnsentForContactInDiscussion(long discussionId, byte[] bytesContactIdentity);


    @Query("SELECT mri.* FROM " + MessageRecipientInfo.TABLE_NAME + " AS mri " +
            " INNER JOIN " + Message.TABLE_NAME + " AS m " +
            " ON m.id = mri." + MessageRecipientInfo.MESSAGE_ID +
            " WHERE m." + Message.DISCUSSION_ID + " = :discussionId " +
            " AND mri." + MessageRecipientInfo.TIMESTAMP_SENT + " IS NULL")
    List<MessageRecipientInfo> getAllUnsentForDiscussion(long discussionId);


    class MessageRecipientInfoAndMessage {
        @Embedded
        public Message message;
        @Embedded(prefix = "mri_")
        public MessageRecipientInfo messageRecipientInfo;
    }
}
