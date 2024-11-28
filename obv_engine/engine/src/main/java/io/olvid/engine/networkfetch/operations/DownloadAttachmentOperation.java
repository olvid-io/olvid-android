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

package io.olvid.engine.networkfetch.operations;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.HashMap;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Chunk;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.EtaEstimator;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.PriorityOperation;
import io.olvid.engine.datatypes.ServerMethodForS3;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.coordinators.DownloadAttachmentCoordinator;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class DownloadAttachmentOperation extends PriorityOperation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SIGNED_URL = 2;
    public static final int RFC_ATTACHMENT_CANNOT_BE_FOUND = 3;
    public static final int RFC_DECRYPTION_ERROR = 5;
    public static final int RFC_ATTACHMENT_CANNOT_BE_FETCHED = 6;
    public static final int RFC_NOT_YET_AVAILABLE_ON_SERVER = 7;
    public static final int RFC_DOES_NOT_HAVE_THE_HIGHEST_PRIORITY = 8;
    public static final int RFC_MARKED_FOR_DELETION = 9;
    public static final int RFC_FETCH_NOT_REQUESTED = 10;
    public static final int RFC_INVALID_CHUNK = 11;
    public static final int RFC_UNABLE_TO_WRITE_CHUNK_TO_FILE = 12;
    public static final int RFC_DOWNLOAD_PAUSED = 13;
    public static final int RFC_UPLOAD_CANCELLED_BY_SENDER = 14;
    public static final int RFC_IDENTITY_IS_INACTIVE = 15;


    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;
    private final int priorityCategory;
    private final WeakReference<DownloadAttachmentCoordinator> coordinatorWeakReference;
    private long priority; // will be updated as the attachment is downloaded, so cannot be final

    public DownloadAttachmentOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory, long initialPriority, DownloadAttachmentCoordinator coordinator, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(InboxAttachment.computeUniqueUid(ownedIdentity, messageUid, attachmentNumber), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
        this.priorityCategory = priorityCategory;
        this.priority = initialPriority;
        coordinatorWeakReference = new WeakReference<>(coordinator);
    }


    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    InboxAttachment attachment;

    @Override
    public void doExecute() {
        boolean finished = false;
        attachment = null;
        try (final FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                attachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
                if (attachment == null) {
                    cancel(RFC_ATTACHMENT_CANNOT_BE_FOUND);
                    return;
                }
                if (attachment.isMarkedForDeletion()) {
                    cancel(RFC_MARKED_FOR_DELETION);
                    return;
                }
                if (attachment.cannotBeFetched()) {
                    cancel(RFC_ATTACHMENT_CANNOT_BE_FETCHED);
                    return;
                }

                if (!attachment.isDownloadRequested()) {
                    cancel(RFC_FETCH_NOT_REQUESTED);
                    return;
                }

                final EtaEstimator etaEstimator = new EtaEstimator(attachment.getReceivedLength(), attachment.getExpectedLength());

                while (attachment.getReceivedLength() != attachment.getExpectedLength()) {
                    if (cancelWasRequested()) {
                        return;
                    }
                    if (attachment.isMarkedForDeletion()) {
                        cancel(RFC_MARKED_FOR_DELETION);
                        return;
                    }
                    if (!attachment.isDownloadRequested()) {
                        cancel(RFC_DOWNLOAD_PAUSED);
                        return;
                    }
                    if (attachment.getChunkDownloadPrivateUrls().length == 0) {
                        cancel(RFC_INVALID_SIGNED_URL);
                        return;
                    }
                    if (attachment.getChunkDownloadPrivateUrls()[attachment.getReceivedChunkCount()].isEmpty()) {
                        cancel(RFC_UPLOAD_CANCELLED_BY_SENDER);
                        return;
                    }

                    DownloadAttachmentServerMethodForS3 serverMethod = new DownloadAttachmentServerMethodForS3(
                            attachment.getChunkDownloadPrivateUrls()[attachment.getReceivedChunkCount()]
                    );
                    serverMethod.setSslSocketFactory(sslSocketFactory);
                    serverMethod.setProgressListener(150, new ServerMethodForS3.ServerMethodForS3ProgressListener() {
                        final HashMap<String, Object> userInfo;
                        {
                            userInfo = new HashMap<>();
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_OWNED_IDENTITY_KEY, ownedIdentity);
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_UID_KEY, messageUid);
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                        }

                        @Override
                        public void onProgress(long byteCount) {
                            float progress = (float) (attachment.getReceivedLength() + byteCount) / attachment.getExpectedLength();
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY, progress);
                            etaEstimator.update(attachment.getReceivedLength() + byteCount);
                            EtaEstimator.SpeedAndEta speedAndEta = etaEstimator.getSpeedAndEta();
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY, speedAndEta.speedBps);
                            userInfo.put(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY, speedAndEta.etaSeconds);
                            fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS, userInfo);
                        }
                    });

                    byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                    fetchManagerSession.session.startTransaction();
                    switch (returnStatus) {
                        case ServerMethodForS3.OK:
                            EncryptedBytes encryptedChunk = serverMethod.getEncryptedChunk();
                            Chunk attachmentChunk;
                            if (encryptedChunk.length != attachment.getChunkLength() &&
                                    attachment.getReceivedLength() + encryptedChunk.length != attachment.getExpectedLength()) {
                                cancel(RFC_INVALID_CHUNK);
                                return;
                            }
                            try {
                                AuthEncKey key = attachment.getKey();
                                AuthEnc authEnc = Suite.getAuthEnc(key);
                                Encoded encodedChunk = new Encoded(authEnc.decrypt(key, encryptedChunk));
                                attachmentChunk = Chunk.of(encodedChunk);
                            } catch (Exception e) {
                                cancel(RFC_DECRYPTION_ERROR);
                                return;
                            }
                            if (attachmentChunk.getChunkNumber() != attachment.getReceivedChunkCount()) {
                                cancel(RFC_INVALID_CHUNK);
                                return;
                            }
                            boolean success = attachment.writeToAttachmentFile(attachmentChunk.getData(), encryptedChunk.length);
                            if (! success) {
                                cancel(RFC_UNABLE_TO_WRITE_CHUNK_TO_FILE);
                                return;
                            }
                            fetchManagerSession.session.commit();
                            DownloadAttachmentCoordinator coordinator = coordinatorWeakReference.get();
                            if (coordinator != null) {
                                coordinator.resetFailedAttemptCount(ownedIdentity, messageUid, attachmentNumber);
                            }
                            break;
                        case ServerMethodForS3.INVALID_SIGNED_URL:
                            cancel(RFC_INVALID_SIGNED_URL);
                            return;
                        case ServerMethodForS3.NOT_FOUND:
                            cancel(RFC_NOT_YET_AVAILABLE_ON_SERVER);
                            return;
                        case ServerMethodForS3.IDENTITY_IS_NOT_ACTIVE:
                            cancel(RFC_IDENTITY_IS_INACTIVE);
                            return;
                        default:
                            cancel(RFC_NETWORK_ERROR);
                            return;
                    }

                    // refresh the object in memory to check for externally requested cancel
                    attachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);
                    this.priority = attachment.getPriority();
                }
                finished = true;
            } catch (Exception e) {
                Logger.x(e);
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
                    setFinished();
                } else {
                    fetchManagerSession.session.rollback();
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
            cancel(null);
            processCancel();
        }
    }

    @Override
    public long getPriority() {
        return priority;
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

    public int getPriorityCategory() {
        return priorityCategory;
    }
}

class DownloadAttachmentServerMethodForS3 extends ServerMethodForS3 {
    private final String url;

    private EncryptedBytes encryptedChunk;

    public EncryptedBytes getEncryptedChunk() {
        return encryptedChunk;
    }

    DownloadAttachmentServerMethodForS3(String url) {
        this.url = url;
    }

    @Override
    protected String getUrl() {
        return url;
    }

    @Override
    protected byte[] getDataToSend() {
        return new byte[0];
    }

    @Override
    protected void handleReceivedData(byte[] receivedData) {
        encryptedChunk = new EncryptedBytes(receivedData);
    }

    @Override
    protected String getMethod() {
        return METHOD_GET;
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}
