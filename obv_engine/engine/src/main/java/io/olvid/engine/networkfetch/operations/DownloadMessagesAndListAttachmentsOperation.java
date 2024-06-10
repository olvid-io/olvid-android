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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class DownloadMessagesAndListAttachmentsOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SERVER_SESSION = 2;
    public static final int RFC_IDENTITY_IS_INACTIVE = 4;
    public static final int RFC_DEVICE_NOT_REGISTERED = 5;

    private static final Set<Identity> notifiedIdentities = new HashSet<>();

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID deviceUid;
    private boolean listingTruncated = false;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public boolean getListingTruncated() {
        return listingTruncated;
    }

    public UID getDeviceUid() {
        return deviceUid;
    }

    public DownloadMessagesAndListAttachmentsOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID deviceUid, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.deviceUid = deviceUid;
    }


    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, ownedIdentity);
                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                // if this is the first listing for this identity, notify that a not-user-initiated listing is in progress
                synchronized (notifiedIdentities) {
                    if (!notifiedIdentities.contains(ownedIdentity)) {
                        notifiedIdentities.add(ownedIdentity);

                        HashMap<String, Object> userInfo = new HashMap<>();
                        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_OWNED_IDENTITY_KEY, ownedIdentity);
                        userInfo.put(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_USER_INITIATED_KEY, false);
                        fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED, userInfo);
                    }
                }

                DownloadMessagesAndListAttachmentsServerMethod serverMethod = new DownloadMessagesAndListAttachmentsServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        deviceUid
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));
                long localDownloadTimestamp = System.currentTimeMillis();

                switch (returnStatus) {
                    case ServerMethod.OK:
                    case ServerMethod.LISTING_TRUNCATED: {
                        long downloadTimestamp = serverMethod.getDownloadTimestamp();
                        DownloadMessagesAndListAttachmentsServerMethod.MessageAndAttachmentLengths[] messageAndAttachmentLengthsArray = serverMethod.getMessageAndAttachmentLengthsArray();
                        int count = 0;

                        fetchManagerSession.session.startTransaction();

                        for (DownloadMessagesAndListAttachmentsServerMethod.MessageAndAttachmentLengths messageAndAttachmentLengths : messageAndAttachmentLengthsArray) {
                            InboxMessage message = InboxMessage.get(fetchManagerSession, ownedIdentity, messageAndAttachmentLengths.messageUid);
                            if (message == null) {
                                message = InboxMessage.create(fetchManagerSession,
                                        ownedIdentity,
                                        messageAndAttachmentLengths.messageUid,
                                        messageAndAttachmentLengths.messageContent,
                                        messageAndAttachmentLengths.wrappedKey,
                                        messageAndAttachmentLengths.serverTimestamp,
                                        downloadTimestamp,
                                        localDownloadTimestamp,
                                        messageAndAttachmentLengths.hasExtendedContent
                                );
                                if (message == null) {
                                    continue;
                                }
                                count++;

                                for (int i = 0; i < messageAndAttachmentLengths.attachmentLengths.length; i++) {
                                    InboxAttachment attachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageAndAttachmentLengths.messageUid, i);
                                    if (attachment == null) {
                                        if (messageAndAttachmentLengths.chunkDownloadPrivateUrls[i] == null) {
                                            Logger.i("Empty list of chunks. Attachment was deleted from server.");
                                        }
                                        InboxAttachment.create(fetchManagerSession,
                                                ownedIdentity,
                                                messageAndAttachmentLengths.messageUid,
                                                i,
                                                messageAndAttachmentLengths.attachmentLengths[i],
                                                messageAndAttachmentLengths.chunkLengths[i],
                                                messageAndAttachmentLengths.chunkDownloadPrivateUrls[i]
                                        );
                                    }
                                }
                            } else {
                                // we relisted a message --> mark it as listed
                                fetchManagerSession.markAsListedAndDeleteOnServerListener.messageCanBeMarkedAsListedOnServer(ownedIdentity, messageAndAttachmentLengths.messageUid);
                            }
                        }
                        Logger.d("DownloadMessagesAndListAttachmentsOperation found " + messageAndAttachmentLengthsArray.length + " messages (" + count + " new) on the server.");
                        this.listingTruncated = (returnStatus == ServerMethod.LISTING_TRUNCATED);
                        finished = true;
                        return;
                    }
                    case ServerMethod.INVALID_SESSION: {
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    }
                    case ServerMethod.DEVICE_IS_NOT_REGISTERED: {
                        cancel(RFC_DEVICE_NOT_REGISTERED);
                        break;
                    }
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE: {
                        cancel(RFC_IDENTITY_IS_INACTIVE);
                        return;
                    }
                    default: {
                        cancel(RFC_NETWORK_ERROR);
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
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
            cancel(null);
            processCancel();
        }
    }
}


class DownloadMessagesAndListAttachmentsServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/downloadMessagesAndListAttachments";

    private final String server;
    private final Identity ownedIdentity;
    private final byte[] token;
    private final UID deviceUid;

    private MessageAndAttachmentLengths[] messageAndAttachmentLengthsArray;
    private long downloadTimestamp;

    public MessageAndAttachmentLengths[] getMessageAndAttachmentLengthsArray() {
        return messageAndAttachmentLengthsArray;
    }

    public long getDownloadTimestamp() {
        return downloadTimestamp;
    }

    DownloadMessagesAndListAttachmentsServerMethod(Identity ownedIdentity, byte[] token, UID deviceUid) {
        this.server = ownedIdentity.getServer();
        this.ownedIdentity = ownedIdentity;
        this.token = token;
        this.deviceUid = deviceUid;
    }

    @Override
    protected String getServer() {
        return server;
    }

    @Override
    protected String getServerMethod() {
        return SERVER_METHOD_PATH;
    }

    @Override
    protected byte[] getDataToSend() {
        return Encoded.of(new Encoded[]{
                Encoded.of(ownedIdentity),
                Encoded.of(token),
                Encoded.of(deviceUid)
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK
                || returnStatus == ServerMethod.LISTING_TRUNCATED) {
            try {
                downloadTimestamp = receivedData[0].decodeLong();
                List<MessageAndAttachmentLengths> list = new ArrayList<>();
                for (int i = 0; i < receivedData.length - 1; i++) {
                    Encoded[] parts = receivedData[i + 1].decodeList();
                    int attachmentCount = parts.length - 5;
                    MessageAndAttachmentLengths messageAndAttachmentLengths = new MessageAndAttachmentLengths(
                            parts[0].decodeUid(),
                            parts[1].decodeLong(),
                            parts[2].decodeEncryptedData(),
                            parts[3].decodeEncryptedData(),
                            parts[4].decodeBoolean(),
                            attachmentCount);
                    for (int j = 0; j < attachmentCount; j++) {
                        Encoded[] attachmentParts = parts[5 + j].decodeList();
                        int attachmentNumber = (int) attachmentParts[0].decodeLong();
                        long attachmentLength = attachmentParts[1].decodeLong();
                        int chunkLength = (int) attachmentParts[2].decodeLong();
                        String[] privateUrls = attachmentParts[3].decodeStringArray();
                        messageAndAttachmentLengths.attachmentLengths[attachmentNumber] = attachmentLength;
                        messageAndAttachmentLengths.chunkLengths[attachmentNumber] = chunkLength;
                        if (chunkLength == 0) {
                            continue;
                        }
                        if (privateUrls.length == (int) ((attachmentLength - 1) / chunkLength) + 1) {
                            messageAndAttachmentLengths.chunkDownloadPrivateUrls[attachmentNumber] = privateUrls;
                        } else {
                            messageAndAttachmentLengths.chunkDownloadPrivateUrls[attachmentNumber] = null;
                        }
                    }
                    list.add(messageAndAttachmentLengths);
                }
                messageAndAttachmentLengthsArray = list.toArray(new MessageAndAttachmentLengths[0]);
            } catch (DecodingException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }

    static class MessageAndAttachmentLengths {
        final UID messageUid;
        final long serverTimestamp;
        final EncryptedBytes wrappedKey;
        final EncryptedBytes messageContent;
        final boolean hasExtendedContent;
        final long[] attachmentLengths;
        final int[] chunkLengths;
        final String[][] chunkDownloadPrivateUrls;

        MessageAndAttachmentLengths(UID messageUid, long serverTimestamp, EncryptedBytes wrappedKey, EncryptedBytes messageContent, boolean hasExtendedContent, int attachmentCount) {
            this.messageUid = messageUid;
            this.serverTimestamp = serverTimestamp;
            this.wrappedKey = wrappedKey;
            this.messageContent = messageContent;
            this.hasExtendedContent = hasExtendedContent;
            this.attachmentLengths = new long[attachmentCount];
            this.chunkLengths = new int[attachmentCount];
            this.chunkDownloadPrivateUrls = new String[attachmentCount][];
        }
    }
}
