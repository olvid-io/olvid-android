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

import io.olvid.engine.engine.Engine
import io.olvid.engine.engine.types.ObvReturnReceipt
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageReturnReceipt


class HandleReceiveReturnReceipt(
    private val engine: Engine,
    private val bytesOwnedIdentity: ByteArray,
    private val serverUid: ByteArray,
    private val returnReceiptNonce: ByteArray,
    private val encryptedPayload: ByteArray,
    private val timestamp: Long
) :
    Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()

        val returnReceiptKeys = db.messageRecipientInfoDao().getReturnReceiptKeysForNonce(
            returnReceiptNonce
        )

        var obvReturnReceipt: ObvReturnReceipt? = null
        var goodReturnReceiptKey: ByteArray? = null
        for (returnReceiptKey in returnReceiptKeys) {
            obvReturnReceipt = engine.decryptReturnReceipt(returnReceiptKey, encryptedPayload)
            if (obvReturnReceipt != null) {
                goodReturnReceiptKey = returnReceiptKey
                break
            }
        }

        if (obvReturnReceipt != null && goodReturnReceiptKey != null) {
            obvReturnReceipt.process(
                bytesOwnedIdentity,
                returnReceiptNonce,
                goodReturnReceiptKey,
                timestamp
            )
        } else {
            db.messageReturnReceiptDao()
                .insert(MessageReturnReceipt(0, returnReceiptNonce, encryptedPayload, timestamp))
        }
        engine.deleteReturnReceipt(bytesOwnedIdentity, serverUid)
    }

}


fun ObvReturnReceipt.process(
    bytesOwnedIdentity: ByteArray,
    returnReceiptNonce: ByteArray,
    goodReturnReceiptKey: ByteArray,
    timestamp: Long
) {
    val db = AppDatabase.getInstance()
    val messageRecipientInfos = db.messageRecipientInfoDao().getFromReturnReceipt(
        bytesOwnedIdentity, bytesContactIdentity,
        returnReceiptNonce, goodReturnReceiptKey
    )
    for (messageRecipientInfo in messageRecipientInfos) {
        if (attachmentNumber == null) {
            // this is a return receipt for a message
            when (status) {
                Message.RETURN_RECEIPT_STATUS_DELIVERED -> {
                    if (messageRecipientInfo.timestampDelivered == null) {
                        messageRecipientInfo.timestampDelivered = timestamp
                        if (messageRecipientInfo.engineMessageIdentifier == null) {
                            messageRecipientInfo.engineMessageIdentifier = ByteArray(0)
                        }
                        if (messageRecipientInfo.timestampSent == null) {
                            messageRecipientInfo.timestampSent = 0L
                        }
                        db.messageRecipientInfoDao().update(messageRecipientInfo)
                        val message = db.messageDao()[messageRecipientInfo.messageId]
                        if (message != null && message.refreshOutboundStatus()) {
                            db.messageDao().updateStatus(message.id, message.status)
                        }
                    }
                }

                Message.RETURN_RECEIPT_STATUS_READ -> {
                    if (messageRecipientInfo.timestampRead == null) {
                        messageRecipientInfo.timestampRead = timestamp
                        if (messageRecipientInfo.engineMessageIdentifier == null) {
                            messageRecipientInfo.engineMessageIdentifier = ByteArray(0)
                        }
                        if (messageRecipientInfo.timestampSent == null) {
                            messageRecipientInfo.timestampSent = 0L
                        }
                        if (messageRecipientInfo.timestampDelivered == null) {
                            messageRecipientInfo.timestampDelivered = 0L
                        }
                        db.messageRecipientInfoDao().update(messageRecipientInfo)
                        val message = db.messageDao()[messageRecipientInfo.messageId]
                        if (message != null && message.refreshOutboundStatus()) {
                            db.messageDao().updateStatus(message.id, message.status)
                        }
                    }
                }
            }
        } else {
            // this is a return receipt for an attachment
            when (status) {
                Message.RETURN_RECEIPT_STATUS_DELIVERED -> {
                    if (messageRecipientInfo.markAttachmentDelivered(attachmentNumber)) {
                        db.messageRecipientInfoDao().update(messageRecipientInfo)
                        val fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao()
                            .getByIdAndAttachmentNumber(
                                messageRecipientInfo.messageId,
                                attachmentNumber
                            )
                        if (fyleMessageJoinWithStatus != null && fyleMessageJoinWithStatus.refreshOutboundStatus(
                                bytesOwnedIdentity
                            )
                        ) {
                            db.fyleMessageJoinWithStatusDao().updateReceptionStatus(
                                fyleMessageJoinWithStatus.messageId,
                                fyleMessageJoinWithStatus.fyleId,
                                fyleMessageJoinWithStatus.receptionStatus
                            )
                        }
                    }
                }

                Message.RETURN_RECEIPT_STATUS_READ -> {
                    if (messageRecipientInfo.markAttachmentRead(attachmentNumber)) {
                        db.messageRecipientInfoDao().update(messageRecipientInfo)
                        val fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao()
                            .getByIdAndAttachmentNumber(
                                messageRecipientInfo.messageId,
                                attachmentNumber
                            )
                        if (fyleMessageJoinWithStatus != null && fyleMessageJoinWithStatus.refreshOutboundStatus(
                                bytesOwnedIdentity
                            )
                        ) {
                            db.fyleMessageJoinWithStatusDao().updateReceptionStatus(
                                fyleMessageJoinWithStatus.messageId,
                                fyleMessageJoinWithStatus.fyleId,
                                fyleMessageJoinWithStatus.receptionStatus
                            )
                        }
                    }
                }
            }
        }
    }
}