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

package io.olvid.messenger;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.olvid.engine.datatypes.EtaEstimator;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.tasks.ExpiringOutboundMessageSent;
import io.olvid.messenger.databases.tasks.HandleMessageExtendedPayloadTask;
import io.olvid.messenger.databases.tasks.HandleNewMessageNotificationTask;
import io.olvid.messenger.databases.tasks.HandleReceiveReturnReceipt;
import io.olvid.messenger.services.UnifiedForegroundService;

public class EngineNotificationProcessorForMessages implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessorForMessages(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName: new String[] {
                EngineNotifications.NEW_MESSAGE_RECEIVED,
                EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED,
                EngineNotifications.RETURN_RECEIPT_RECEIVED,
                EngineNotifications.ATTACHMENT_DOWNLOADED,
                EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS,
                EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED,
                EngineNotifications.ATTACHMENT_UPLOADED,
                EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS,
                EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED,
                EngineNotifications.MESSAGE_UPLOADED,
                EngineNotifications.MESSAGE_UPLOAD_FAILED,

        }) {
            engine.addNotificationListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.NEW_MESSAGE_RECEIVED: {
                ObvMessage obvMessage = (ObvMessage) userInfo.get(EngineNotifications.NEW_MESSAGE_RECEIVED_MESSAGE_KEY);
                if (obvMessage == null) {
                    break;
                }
                new HandleNewMessageNotificationTask(engine, obvMessage).run();
                break;
            }
            case EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_IDENTIFIER_KEY);
                byte[] extendedPayload = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY);
                if (bytesOwnedIdentity == null || messageIdentifier == null || extendedPayload == null) {
                    break;
                }
                App.runThread(new HandleMessageExtendedPayloadTask(bytesOwnedIdentity, messageIdentifier, extendedPayload));
                break;
            }
            case EngineNotifications.RETURN_RECEIPT_RECEIVED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_BYTES_OWNED_IDENTITY_KEY);
                byte[] serverUid = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY);
                byte[] returnReceiptNonce = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_NONCE_KEY);
                byte[] encryptedPayload = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY);
                Long timestamp = (Long) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY);
                if (bytesOwnedIdentity == null || serverUid == null || returnReceiptNonce == null || encryptedPayload == null || timestamp == null) {
                    break;
                }

                new HandleReceiveReturnReceipt(engine, bytesOwnedIdentity, serverUid, returnReceiptNonce, encryptedPayload, timestamp).run();
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOADED: {
                final ObvAttachment downloadedAttachment = (ObvAttachment) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOADED_ATTACHMENT_KEY);
                if (downloadedAttachment == null) {
                    break;
                }
                try {
                    clearFyleAndMessageIdFromEngineIdentifier(downloadedAttachment.getBytesOwnedIdentity(), downloadedAttachment.getMessageIdentifier(), downloadedAttachment.getNumber());
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(downloadedAttachment.getBytesOwnedIdentity(), downloadedAttachment.getMessageIdentifier(), downloadedAttachment.getNumber());
                    if (fyleMessageJoinWithStatus != null) {
                        final Fyle fyle = db.fyleDao().getById(fyleMessageJoinWithStatus.fyleId);
                        final byte[] sha256 = fyle.sha256;
                        if (sha256 == null) {
                            break;
                        }
                        try {
                            Fyle.acquireLock(sha256);
                            Fyle.SizeAndSha256 sizeAndSha256 = Fyle.computeSHA256FromFile(App.absolutePathFromRelative(downloadedAttachment.getUrl()));
                            if ((sizeAndSha256 == null) || !Arrays.equals(sha256, sizeAndSha256.sha256)) {
                                // OMG, the metadata contained an erroneous sha256!!! Delete everything
                                List<Long> messageIds = db.fyleMessageJoinWithStatusDao().getMessageIdsForFyleSync(fyle.id);
                                if ((messageIds.size() == 1) && (messageIds.get(0) == fyleMessageJoinWithStatus.messageId)) {
                                    fyle.delete();
                                } else {
                                    db.fyleMessageJoinWithStatusDao().delete(fyleMessageJoinWithStatus);
                                }

                                Message message = db.messageDao().get(fyleMessageJoinWithStatus.messageId);
                                if (message != null) {
                                    message.recomputeAttachmentCount(db);
                                    db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, 0, message.imageResolutions);
                                }
                            } else {
                                // the file matches its metadata sha256, move the file to the Fyle directory and mark it as complete
                                fyle.moveToFyleDirectory(App.absolutePathFromRelative(downloadedAttachment.getUrl()));
                                db.fyleDao().update(fyle);

                                // mark the corresponding FyleMessageJoinWithStatus as complete too
                                fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                                FyleProgressSingleton.INSTANCE.finishProgress(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
                                //noinspection ConstantConditions
                                fyleMessageJoinWithStatus.filePath = fyle.filePath;
                                db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                                fyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                                fyleMessageJoinWithStatus.computeTextContentForFullTextSearchOnOtherThread(db, fyle);

                                // check all other FyleMessageJoinWithStatus that are still in STATUS_DOWNLOADABLE or STATUS_DOWNLOADING and "complete" them
                                List<FyleMessageJoinWithStatus> fyleMessageJoinWithStatusList = db.fyleMessageJoinWithStatusDao().getForFyleId(fyle.id);
                                for (FyleMessageJoinWithStatus otherFyleMessageJoinWithStatus : fyleMessageJoinWithStatusList) {
                                    switch (otherFyleMessageJoinWithStatus.status) {
                                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                                        case FyleMessageJoinWithStatus.STATUS_FAILED:
                                            otherFyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                                            FyleProgressSingleton.INSTANCE.finishProgress(otherFyleMessageJoinWithStatus.fyleId, otherFyleMessageJoinWithStatus.messageId);
                                            otherFyleMessageJoinWithStatus.filePath = fyleMessageJoinWithStatus.filePath;
                                            otherFyleMessageJoinWithStatus.size = fyleMessageJoinWithStatus.size;
                                            db.fyleMessageJoinWithStatusDao().update(otherFyleMessageJoinWithStatus);
                                            otherFyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                                            if (otherFyleMessageJoinWithStatus.engineNumber != null) {
                                                engine.markAttachmentForDeletion(otherFyleMessageJoinWithStatus.bytesOwnedIdentity, otherFyleMessageJoinWithStatus.engineMessageIdentifier,
                                                        otherFyleMessageJoinWithStatus.engineNumber);
                                            }
                                            otherFyleMessageJoinWithStatus.computeTextContentForFullTextSearchOnOtherThread(db, fyle);
                                            break;
                                    }
                                }
                            }
                        } finally {
                            Fyle.releaseLock(sha256);
                        }
                    }
                    engine.markAttachmentForDeletion(downloadedAttachment);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                Integer attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                Float progress = (Float) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY);
                Float speed = (Float) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY);
                Integer eta = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY);
                if (bytesOwnedIdentity == null || messageIdentifier == null || attachmentNumber == null || progress == null) {
                    break;
                }
                Pair<Long, Long> ids = getFyleAndMessageIdFromEngineIdentifier(bytesOwnedIdentity, messageIdentifier, attachmentNumber);
                if (ids != null) {
                    EtaEstimator.SpeedAndEta speedAndEta = (speed != null && eta != null) ? new EtaEstimator.SpeedAndEta(speed, eta) : null;
                    FyleProgressSingleton.INSTANCE.updateProgress(ids.first, ids.second, progress, speedAndEta);
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY);
                if (bytesOwnedIdentity != null && engineMessageIdentifier != null && engineNumber != null) {
                    clearFyleAndMessageIdFromEngineIdentifier(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                    if (fyleMessageJoinWithStatus != null) {
                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_FAILED;
                        FyleProgressSingleton.INSTANCE.finishProgress(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
                        db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                    }
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_ATTACHMENT_NUMBER_KEY);
                if (bytesOwnedIdentity != null && engineMessageIdentifier != null && engineNumber != null) {
                    db.runInTransaction(() -> {
                        clearFyleAndMessageIdFromEngineIdentifier(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        if (fyleMessageJoinWithStatus != null) {
                            fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                            FyleProgressSingleton.INSTANCE.finishProgress(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
                            db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                        }

                        List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                        if (!messageRecipientInfos.isEmpty()) {
                            long timestamp = System.currentTimeMillis();
                            long messageId = messageRecipientInfos.get(0).messageId;
                            List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                            boolean complete = false;
                            for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                                if (messageRecipientInfo.markAttachmentSent(engineNumber)) {
                                    if (messageRecipientInfo.unsentAttachmentNumbers == null && messageRecipientInfo.timestampSent == null) {
                                        messageRecipientInfo.timestampSent = timestamp;
                                        complete = true;
                                    }
                                    updatedMessageRecipientInfos.add(messageRecipientInfo);
                                }
                            }

                            if (!updatedMessageRecipientInfos.isEmpty()) {
                                db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                                Message message = db.messageDao().get(messageId);
                                if (message != null) {
                                    if (complete && message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                                        UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                                    }
                                    if (message.refreshOutboundStatus()) {
                                        db.messageDao().updateStatus(message.id, message.status);
                                    }
                                }
                            }
                        } else {
                            UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                        }
                    });
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                Integer attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                Float progress = (Float) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY);
                Float speed = (Float) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY);
                Integer eta = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY);
                if (bytesOwnedIdentity == null || messageIdentifier == null || attachmentNumber == null || progress == null) {
                    break;
                }
                Pair<Long, Long> ids = getFyleAndMessageIdFromEngineIdentifier(bytesOwnedIdentity, messageIdentifier, attachmentNumber);
                if (ids != null) {
                    EtaEstimator.SpeedAndEta speedAndEta = (speed != null && eta != null) ? new EtaEstimator.SpeedAndEta(speed, eta) : null;
                    FyleProgressSingleton.INSTANCE.updateProgress(ids.first, ids.second, progress, speedAndEta);
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED: {
                // TODO: handle this differently than ATTACHMENT_UPLOADED to show users that the attachment was never actually sent.
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY);
                if (bytesOwnedIdentity != null && engineMessageIdentifier != null && engineNumber != null) {
                    db.runInTransaction(() -> {
                        clearFyleAndMessageIdFromEngineIdentifier(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        if (fyleMessageJoinWithStatus != null) {
                            fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                            FyleProgressSingleton.INSTANCE.finishProgress(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
                            db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                        }

                        List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                        if (!messageRecipientInfos.isEmpty()) {
                            long timestamp = System.currentTimeMillis();
                            long messageId = messageRecipientInfos.get(0).messageId;
                            List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                            boolean complete = false;
                            for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                                if (messageRecipientInfo.markAttachmentSent(engineNumber)) {
                                    if (messageRecipientInfo.unsentAttachmentNumbers == null && messageRecipientInfo.timestampSent == null) {
                                        messageRecipientInfo.timestampSent = timestamp;
                                        complete = true;
                                    }
                                    updatedMessageRecipientInfos.add(messageRecipientInfo);
                                }
                            }

                            if (!updatedMessageRecipientInfos.isEmpty()) {
                                db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                                Message message = db.messageDao().get(messageId);
                                if (message != null) {
                                    if (complete && message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                                        UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                                    }
                                    if (message.refreshOutboundStatus()) {
                                        db.messageDao().updateStatus(message.id, message.status);
                                    }
                                }
                            }
                        } else {
                            UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                        }
                    });
                }
                break;
            }
            case EngineNotifications.MESSAGE_UPLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_IDENTIFIER_KEY);
                Long timestampFromServer = (Long) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER);
                if (bytesOwnedIdentity == null || engineMessageIdentifier == null || timestampFromServer == null) {
                    break;
                }

                List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                if (!messageRecipientInfos.isEmpty()) {
                    long messageId = messageRecipientInfos.get(0).messageId;
                    List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                    for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                        if (messageRecipientInfo.unsentAttachmentNumbers == null) {
                            messageRecipientInfo.timestampSent = timestampFromServer;
                            updatedMessageRecipientInfos.add(messageRecipientInfo);
                        }
                    }

                    if (!updatedMessageRecipientInfos.isEmpty()) {
                        db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                        Message message = db.messageDao().get(messageId);
                        if (message != null) {
                            // if we reach this point, the message did not have any attachments and was indeed fully uploaded
                            if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                                UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                            }
                            if (message.refreshOutboundStatus()) {
                                db.messageDao().updateStatus(message.id, message.status);
                            }
                        }
                    }
                } else {
                    UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                }
                break;
            }
            case EngineNotifications.MESSAGE_UPLOAD_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOAD_FAILED_IDENTIFIER_KEY);

                List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                if (!messageRecipientInfos.isEmpty()) {
                    long messageId = messageRecipientInfos.get(0).messageId;
                    boolean hasUnsentMessageRecipientInfo = false;

                    for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                        if (messageRecipientInfo.timestampSent == null) {
                            hasUnsentMessageRecipientInfo = true;
                            break;
                        }
                    }

                    if (hasUnsentMessageRecipientInfo) {
                        Message message = db.messageDao().get(messageId);
                        if (message != null && message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                            // if we reach this point, the message was indeed not sent to at least one user (and will never be)
                            UnifiedForegroundService.processUploadedMessageIdentifier(engineMessageIdentifier);
                            message.status = Message.STATUS_UNDELIVERED;
                            db.messageDao().updateStatus(message.id, message.status);
                            db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_UNDELIVERED, System.currentTimeMillis()));
                            if (message.jsonExpiration != null) {
                                App.runThread(new ExpiringOutboundMessageSent(message));
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    private static class FyleEngineIdentifier {
        @NonNull private final byte[] bytesOwnedIdentity;
        @NonNull private final byte[] engineMessageIdentifier;
        private final int engineNumber;

        public FyleEngineIdentifier(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] engineMessageIdentifier, int engineNumber) {
            this.bytesOwnedIdentity = bytesOwnedIdentity;
            this.engineMessageIdentifier = engineMessageIdentifier;
            this.engineNumber = engineNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FyleEngineIdentifier)) return false;
            FyleEngineIdentifier that = (FyleEngineIdentifier) o;
            return engineNumber == that.engineNumber && Arrays.equals(bytesOwnedIdentity, that.bytesOwnedIdentity) && Arrays.equals(engineMessageIdentifier, that.engineMessageIdentifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(bytesOwnedIdentity), Arrays.hashCode(engineMessageIdentifier), engineNumber);
        }
    }

    private final HashMap<FyleEngineIdentifier, Pair<Long, Long>> fyleAndMessageIdCache = new HashMap<>();

    private void clearFyleAndMessageIdFromEngineIdentifier(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] engineMessageIdentifier, int engineNumber) {
        fyleAndMessageIdCache.remove(new FyleEngineIdentifier(bytesOwnedIdentity, engineMessageIdentifier, engineNumber));
    }

    @Nullable
    private Pair<Long, Long> getFyleAndMessageIdFromEngineIdentifier(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] engineMessageIdentifier, int engineNumber) {
        FyleEngineIdentifier fyleEngineIdentifier = new FyleEngineIdentifier(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
        Pair<Long, Long> ids = fyleAndMessageIdCache.get(fyleEngineIdentifier);
        if (ids != null) {
            return ids;
        }
        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
        if (fyleMessageJoinWithStatus != null) {
            ids = new Pair<>(fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.messageId);
            fyleAndMessageIdCache.put(fyleEngineIdentifier, ids);
            return ids;
        }
        return null;
    }



    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return registrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return registrationNumber != null;
    }
}
