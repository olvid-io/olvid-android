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

package io.olvid.engine.networksend.operations;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.HashMap;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.EtaEstimator;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PriorityOperation;
import io.olvid.engine.datatypes.ServerMethodForS3;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.coordinators.SendAttachmentCoordinator;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class UploadAttachmentOperation extends PriorityOperation {
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;
    private long priority; // will be updated as the attachment is downloaded, so cannot be final
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final WeakReference<SendAttachmentCoordinator> coordinatorWeakReference;

    public UploadAttachmentOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, int attachmentNumber, long initialPriority, SendAttachmentCoordinator coordinator) {
        super(OutboxAttachment.computeUniqueUid(ownedIdentity, messageUid, attachmentNumber), null, null);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
        this.priority = initialPriority;
        this.coordinatorWeakReference = new WeakReference<>(coordinator);
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public int getAttachmentNumber() {
        return attachmentNumber;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    private OutboxAttachment outboxAttachment;

    @Override
    public void doExecute() {
        boolean finished = false;
        try (final SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                final OutboxMessage outboxMessage;
                try {
                    outboxAttachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);
                    outboxMessage = OutboxMessage.get(sendManagerSession, ownedIdentity, messageUid);
                } catch (SQLException e) {
                    return;
                }

                if (outboxAttachment == null) {
                    cancel(UploadAttachmentCompositeOperation.RFC_ATTACHMENT_NOT_FOUND_IN_DATABASE);
                    return;
                }
                if (outboxMessage == null || outboxMessage.getUidFromServer() == null) {
                    cancel(UploadAttachmentCompositeOperation.RFC_MESSAGE_HAS_NO_UID_FROM_SERVER);
                    return;
                }
                if (outboxAttachment.isAcknowledged() || outboxAttachment.isCancelExternallyRequested()) {
                    finished = true;
                    return;
                }


                try (final RandomAccessFile f = new RandomAccessFile(new File(sendManagerSession.engineBaseDirectory, outboxAttachment.getUrl()), "r")) {
                    final int cleartextChunkLength = outboxAttachment.getCleartextChunkLength();
                    byte[] buffer = new byte[cleartextChunkLength];

                    long cleartextOffset = (long) outboxAttachment.getAcknowledgedChunkCount() * cleartextChunkLength;
                    f.seek(cleartextOffset);

                    AuthEnc authEnc = Suite.getAuthEnc(outboxAttachment.getKey());
                    PRNGService prng = Suite.getPRNGService(PRNG.PRNG_HMAC_SHA256);

                    final EtaEstimator etaEstimator = new EtaEstimator((long) outboxAttachment.getCiphertextChunkLength() * (long) outboxAttachment.getAcknowledgedChunkCount(), outboxAttachment.getCiphertextLength());

                    while (outboxAttachment != null && !outboxAttachment.isAcknowledged()) {
                        if (cancelWasRequested()) {
                            return;
                        }

                        if (outboxAttachment.isCancelExternallyRequested()) {
                            finished = true;
                            return;
                        }
                        if (outboxAttachment.getChunkUploadPrivateUrls().length == 0) {
                            cancel(UploadAttachmentCompositeOperation.RFC_INVALID_SIGNED_URL);
                            return;
                        }

                        int bufferFullness = 0;
                        while (bufferFullness < buffer.length) {
                            int count = f.read(buffer, bufferFullness, buffer.length - bufferFullness);
                            if (count < 0) {
                                break;
                            }
                            bufferFullness += count;
                        }
                        int chunkNumber = outboxAttachment.getAcknowledgedChunkCount();

                        UploadAttachmentServerMethodForS3 serverMethod = new UploadAttachmentServerMethodForS3(
                                outboxAttachment.getChunkUploadPrivateUrls()[chunkNumber],
                                authEnc.encrypt(outboxAttachment.getKey(), Encoded.encodeChunk(chunkNumber, buffer, bufferFullness), prng));
                        serverMethod.setSslSocketFactory(sslSocketFactory);

                        serverMethod.setProgressListener(100, new ServerMethodForS3.ServerMethodForS3ProgressListener() {
                            final HashMap<String, Object> userInfo;
                            final long totalLength;
                            final long chunkLength;
                            {
                                userInfo = new HashMap<>();
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_OWNED_IDENTITY_KEY, ownedIdentity);
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_UID_KEY, messageUid);
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                                totalLength = outboxAttachment.getCiphertextLength();
                                chunkLength = outboxAttachment.getCiphertextChunkLength();
                            }

                            @Override
                            public void onProgress(long byteCount) {
                                float progress = (float) (outboxAttachment.getAcknowledgedChunkCount()*chunkLength + byteCount)/totalLength;
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY, progress);
                                etaEstimator.update(outboxAttachment.getAcknowledgedChunkCount()*chunkLength + byteCount);
                                EtaEstimator.SpeedAndEta speedAndEta = etaEstimator.getSpeedAndEta();
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY, speedAndEta.speedBps);
                                userInfo.put(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY, speedAndEta.etaSeconds);
                                sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS, userInfo);
                            }
                        });
                        byte returnStatus = serverMethod.execute(sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity));

                        switch (returnStatus) {
                            case ServerMethodForS3.OK:
                                SendAttachmentCoordinator coordinator = coordinatorWeakReference.get();
                                if (coordinator != null) {
                                    coordinator.resetFailedAttemptCount(ownedIdentity, messageUid, attachmentNumber);
                                }
                                outboxAttachment.setAcknowledgedChunkCount(chunkNumber+1);
                                sendManagerSession.session.commit();
                                break;
                            case ServerMethodForS3.GENERAL_ERROR:
                                cancel(null);
                                return;
                            case ServerMethodForS3.INVALID_SIGNED_URL:
                                cancel(UploadAttachmentCompositeOperation.RFC_INVALID_SIGNED_URL);
                                return;
                            case ServerMethodForS3.IDENTITY_IS_NOT_ACTIVE:
                                cancel(UploadAttachmentCompositeOperation.RFC_IDENTITY_IS_INACTIVE);
                                return;
                            default:
                                cancel(UploadAttachmentCompositeOperation.RFC_NETWORK_ERROR);
                                return;
                        }

                        // refresh the object in memory to check for externally requested cancel
                        outboxAttachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);
                        if (outboxAttachment != null) {
                            this.priority = outboxAttachment.getPriority();
                        }
                    }
                    finished = true;
                } catch (FileNotFoundException e) {
                    Logger.w("Attachment not found");
                    cancel(UploadAttachmentCompositeOperation.RFC_ATTACHMENT_FILE_NOT_READABLE);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (finished) {
                    sendManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getPriority() {
        return priority;
    }
}

class UploadAttachmentServerMethodForS3 extends ServerMethodForS3 {
    private final String url;
    private final EncryptedBytes encryptedAttachmentChunk;

    UploadAttachmentServerMethodForS3(String url, EncryptedBytes encryptedAttachmentChunk) {
        this.url = url;
        this.encryptedAttachmentChunk = encryptedAttachmentChunk;
    }


    @Override
    protected String getUrl() {
        return url;
    }

    @Override
    protected byte[] getDataToSend() {
        return encryptedAttachmentChunk.getBytes();
    }

    @Override
    protected void handleReceivedData(byte[] receivedData) {
        // nothing to do;
    }

    @Override
    protected String getMethod() {
        return METHOD_PUT;
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}