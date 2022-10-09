/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = MessageRecipientInfo.TABLE_NAME,
        primaryKeys = {MessageRecipientInfo.MESSAGE_ID, MessageRecipientInfo.BYTES_CONTACT_IDENTITY},
        foreignKeys = @ForeignKey(entity = Message.class,
                parentColumns = "id",
                childColumns = MessageRecipientInfo.MESSAGE_ID,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(MessageRecipientInfo.MESSAGE_ID),
                @Index(MessageRecipientInfo.BYTES_CONTACT_IDENTITY),
                @Index(MessageRecipientInfo.RETURN_RECEIPT_NONCE),
                @Index(MessageRecipientInfo.ENGINE_MESSAGE_IDENTIFIER),
                @Index(value = {MessageRecipientInfo.MESSAGE_ID, MessageRecipientInfo.BYTES_CONTACT_IDENTITY})
        }
)
public class MessageRecipientInfo {
    public static final String TABLE_NAME = "message_recipient_info_table";

    public static final String MESSAGE_ID = "message_id";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String RETURN_RECEIPT_NONCE = "return_receipt_nonce"; // set at the same time as the ENGINE_MESSAGE_IDENTIFIER
    public static final String RETURN_RECEIPT_KEY = "return_receipt_key"; // set at the same time as the ENGINE_MESSAGE_IDENTIFIER
    public static final String ENGINE_MESSAGE_IDENTIFIER = "engine_message_identifier"; // null means the message was not passed to the engine
    public static final String UNSENT_ATTACHMENT_NUMBERS = "unsent_attachment_numbers"; // null once all attachments are sent or cancelled
    public static final String TIMESTAMP_SENT = "timestamp_sent"; // timestamp from the server returned by uploadMessageAndGetUids
    public static final String TIMESTAMP_DELIVERED = "timestamp_delivered"; // server timestamp from the return receipt
    public static final String TIMESTAMP_READ = "timestamp_read"; // server timestamp from the return receipt
    public static final String UNDELIVERED_ATTACHMENT_NUMBERS = "undelivered_attachment_numbers"; // null once all attachments are delivered
    public static final String UNREAD_ATTACHMENT_NUMBERS = "unread_attachment_numbers"; // null once all attachments are read

    public static final int RECIPIENT_STATUS_NOT_SENT_YET = 0;
    public static final int RECIPIENT_STATUS_PROCESSING = 1;
    public static final int RECIPIENT_STATUS_SENT = 2;
    public static final int RECIPIENT_STATUS_DELIVERED = 3;
    public static final int RECIPIENT_STATUS_DELIVERED_AND_READ = 4;

    @ColumnInfo(name = MESSAGE_ID)
    public long messageId;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @NonNull
    public byte[] bytesContactIdentity;

    @ColumnInfo(name = RETURN_RECEIPT_NONCE)
    public byte[] returnReceiptNonce;

    @ColumnInfo(name = RETURN_RECEIPT_KEY)
    public byte[] returnReceiptKey;

    @ColumnInfo(name = ENGINE_MESSAGE_IDENTIFIER)
    @Nullable
    public byte[] engineMessageIdentifier;

    @ColumnInfo(name = UNSENT_ATTACHMENT_NUMBERS)
    @Nullable
    public String unsentAttachmentNumbers;

    @ColumnInfo(name = TIMESTAMP_SENT)
    @Nullable
    public Long timestampSent;

    @ColumnInfo(name = TIMESTAMP_DELIVERED)
    @Nullable
    public Long timestampDelivered;

    @ColumnInfo(name = TIMESTAMP_READ)
    @Nullable
    public Long timestampRead;

    @ColumnInfo(name = UNDELIVERED_ATTACHMENT_NUMBERS)
    @Nullable
    public String undeliveredAttachmentNumbers;

    @ColumnInfo(name = UNREAD_ATTACHMENT_NUMBERS)
    @Nullable
    public String unreadAttachmentNumbers;

    // default constructor required by Room
    public MessageRecipientInfo(long messageId, @NonNull byte[] bytesContactIdentity, byte[] returnReceiptNonce, byte[] returnReceiptKey, @Nullable byte[] engineMessageIdentifier, @Nullable String unsentAttachmentNumbers, @Nullable Long timestampSent, @Nullable Long timestampDelivered, @Nullable Long timestampRead, @Nullable String undeliveredAttachmentNumbers, @Nullable String unreadAttachmentNumbers) {
        this.messageId = messageId;
        this.bytesContactIdentity = bytesContactIdentity;
        this.returnReceiptNonce = returnReceiptNonce;
        this.returnReceiptKey = returnReceiptKey;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.unsentAttachmentNumbers = unsentAttachmentNumbers;
        this.timestampSent = timestampSent;
        this.timestampDelivered = timestampDelivered;
        this.timestampRead = timestampRead;
        this.undeliveredAttachmentNumbers = undeliveredAttachmentNumbers;
        this.unreadAttachmentNumbers = unreadAttachmentNumbers;
    }

    @Ignore
    public MessageRecipientInfo(long messageId, int attachmentCount, @NonNull byte[] bytesContactIdentity, @Nullable byte[] engineMessageIdentifier, byte[] returnReceiptNonce, byte[] returnReceiptKey) {
        this.messageId = messageId;
        this.bytesContactIdentity = bytesContactIdentity;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.unsentAttachmentNumbers = getUnsentAttachmentNumbers(attachmentCount);
        this.returnReceiptNonce = returnReceiptNonce;
        this.returnReceiptKey = returnReceiptKey;
        this.timestampSent = null;
        this.timestampDelivered = null;
        this.timestampRead = null;
        this.undeliveredAttachmentNumbers = this.unsentAttachmentNumbers;
        this.unreadAttachmentNumbers = this.unsentAttachmentNumbers;
    }

    @Ignore
    public MessageRecipientInfo(long messageId, int attachmentCount, @NonNull byte[] bytesContactIdentity) {
        this.messageId = messageId;
        this.bytesContactIdentity = bytesContactIdentity;
        this.unsentAttachmentNumbers = getUnsentAttachmentNumbers(attachmentCount);
        this.engineMessageIdentifier = null;
        this.returnReceiptNonce = null;
        this.returnReceiptKey = null;
        this.timestampSent = null;
        this.timestampDelivered = null;
        this.timestampRead = null;
        this.undeliveredAttachmentNumbers = this.unsentAttachmentNumbers;
        this.unreadAttachmentNumbers = this.unsentAttachmentNumbers;
    }

    public int status() {
        return (engineMessageIdentifier == null?0:1) + (timestampSent == null?0:1) + (timestampDelivered == null?0:1) + (timestampRead == null?0:1);
    }


    private static String getUnsentAttachmentNumbers(int attachmentCount) {
        if (attachmentCount == 0) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder("0");
            for (int i=1; i<attachmentCount; i++) {
                sb.append(",");
                sb.append(i);
            }
            return sb.toString();
        }
    }

    public boolean markAttachmentSent(int attachmentNumber) {
        if (unsentAttachmentNumbers == null) {
            return false;
        }
        String newNumbers = removeAttachmentFromString(attachmentNumber, unsentAttachmentNumbers);
        if (!Objects.equals(unsentAttachmentNumbers, newNumbers)) {
            unsentAttachmentNumbers = newNumbers;
            return true;
        }
        return false;
    }

    public boolean markAttachmentDelivered(int attachmentNumber) {
        if (undeliveredAttachmentNumbers == null) {
            return false;
        }
        String newNumbers = removeAttachmentFromString(attachmentNumber, undeliveredAttachmentNumbers);
        if (!Objects.equals(undeliveredAttachmentNumbers, newNumbers)) {
            undeliveredAttachmentNumbers = newNumbers;
            return true;
        }
        return false;
    }

    public boolean markAttachmentRead(int attachmentNumber) {
        boolean wasNotMarkedDelivered = markAttachmentDelivered(attachmentNumber);
        if (unreadAttachmentNumbers == null) {
            return wasNotMarkedDelivered;
        }
        String newNumbers = removeAttachmentFromString(attachmentNumber, unreadAttachmentNumbers);
        if (!Objects.equals(unreadAttachmentNumbers, newNumbers)) {
            unreadAttachmentNumbers = newNumbers;
            return true;
        }
        return wasNotMarkedDelivered;
    }

    @Nullable
    private static String removeAttachmentFromString(int attachmentNumber, @NonNull String input) {
        String attachmentNumberString = Integer.toString(attachmentNumber);
        String[] numbers = input.split(",");
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        for (String number : numbers) {
            if (number.equals(attachmentNumberString)) {
                continue;
            }
            if (!first) {
                sb.append(",");
            } else {
                first = false;
            }
            sb.append(number);
        }
        if (sb.length() == 0) {
            return null;
        } else {
            return sb.toString();
        }
    }

    public static boolean isAttachmentNumberPresent(int attachmentNumber, @Nullable String attachmentNumbers) {
        if (attachmentNumbers == null) {
            return false;
        }
        String[] numbers = attachmentNumbers.split(",");
        return Arrays.asList(numbers).contains(Integer.toString(attachmentNumber));
    }
}
