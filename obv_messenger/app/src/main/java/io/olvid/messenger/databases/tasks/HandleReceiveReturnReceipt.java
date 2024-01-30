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

package io.olvid.messenger.databases.tasks;


import androidx.annotation.NonNull;

import java.util.List;

import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvReturnReceipt;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;

public class HandleReceiveReturnReceipt  implements Runnable {
    @NonNull private final Engine engine;
    @NonNull private final byte[] bytesOwnedIdentity;
    @NonNull private final byte[] serverUid;
    @NonNull private final byte[] returnReceiptNonce;
    @NonNull private final byte[] encryptedPayload;
    private final long timestamp;

    public HandleReceiveReturnReceipt(@NonNull Engine engine, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] serverUid, @NonNull byte[] returnReceiptNonce, @NonNull byte[] encryptedPayload, long timestamp) {
        this.engine = engine;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.serverUid = serverUid;
        this.returnReceiptNonce = returnReceiptNonce;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = timestamp;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();

        List<byte[]> returnReceiptKeys = db.messageRecipientInfoDao().getReturnReceiptKeysForNonce(returnReceiptNonce);

        ObvReturnReceipt obvReturnReceipt = null;
        byte[] goodReturnReceiptKey = null;
        for (byte[] returnReceiptKey : returnReceiptKeys) {
            obvReturnReceipt = engine.decryptReturnReceipt(returnReceiptKey, encryptedPayload);
            if (obvReturnReceipt != null) {
                goodReturnReceiptKey = returnReceiptKey;
                break;
            }
        }


        if (obvReturnReceipt != null) {
            List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getFromReturnReceipt(bytesOwnedIdentity, obvReturnReceipt.getBytesContactIdentity(), returnReceiptNonce, goodReturnReceiptKey);
            for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                if (obvReturnReceipt.getAttachmentNumber() == null) {
                    // this is a return receipt for a message
                    switch (obvReturnReceipt.getStatus()) {
                        case Message.RETURN_RECEIPT_STATUS_DELIVERED: {
                            if (messageRecipientInfo.timestampDelivered == null) {
                                messageRecipientInfo.timestampDelivered = timestamp;
                                if (messageRecipientInfo.engineMessageIdentifier == null) {
                                    messageRecipientInfo.engineMessageIdentifier = new byte[0];
                                }
                                if (messageRecipientInfo.timestampSent == null) {
                                    messageRecipientInfo.timestampSent = 0L;
                                }
                                db.messageRecipientInfoDao().update(messageRecipientInfo);
                                Message message = db.messageDao().get(messageRecipientInfo.messageId);
                                if (message != null && message.refreshOutboundStatus()) {
                                    db.messageDao().updateStatus(message.id, message.status);
                                }
                            }
                            break;
                        }
                        case Message.RETURN_RECEIPT_STATUS_READ: {
                            if (messageRecipientInfo.timestampRead == null) {
                                messageRecipientInfo.timestampRead = timestamp;
                                if (messageRecipientInfo.engineMessageIdentifier == null) {
                                    messageRecipientInfo.engineMessageIdentifier = new byte[0];
                                }
                                if (messageRecipientInfo.timestampSent == null) {
                                    messageRecipientInfo.timestampSent = 0L;
                                }
                                if (messageRecipientInfo.timestampDelivered == null) {
                                    messageRecipientInfo.timestampDelivered = 0L;
                                }
                                db.messageRecipientInfoDao().update(messageRecipientInfo);
                                Message message = db.messageDao().get(messageRecipientInfo.messageId);
                                if (message != null && message.refreshOutboundStatus()) {
                                    db.messageDao().updateStatus(message.id, message.status);
                                }
                            }
                            break;
                        }
                    }
                } else {
                    // this is a return receipt for an attachment
                    switch (obvReturnReceipt.getStatus()) {
                        case Message.RETURN_RECEIPT_STATUS_DELIVERED: {
                            if (messageRecipientInfo.markAttachmentDelivered(obvReturnReceipt.getAttachmentNumber())) {
                                db.messageRecipientInfoDao().update(messageRecipientInfo);
                                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByIdAndAttachmentNumber(messageRecipientInfo.messageId, obvReturnReceipt.getAttachmentNumber());
                                if (fyleMessageJoinWithStatus != null && fyleMessageJoinWithStatus.refreshOutboundStatus(this.bytesOwnedIdentity)) {
                                    db.fyleMessageJoinWithStatusDao().updateReceptionStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.receptionStatus);
                                }
                            }
                            break;
                        }
                        case Message.RETURN_RECEIPT_STATUS_READ: {
                            if (messageRecipientInfo.markAttachmentRead(obvReturnReceipt.getAttachmentNumber())) {
                                db.messageRecipientInfoDao().update(messageRecipientInfo);
                                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByIdAndAttachmentNumber(messageRecipientInfo.messageId, obvReturnReceipt.getAttachmentNumber());
                                if (fyleMessageJoinWithStatus != null && fyleMessageJoinWithStatus.refreshOutboundStatus(this.bytesOwnedIdentity)) {
                                    db.fyleMessageJoinWithStatusDao().updateReceptionStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.receptionStatus);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        engine.deleteReturnReceipt(bytesOwnedIdentity, serverUid);
    }
}
