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

import java.sql.SQLException;
import java.util.HashMap;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.databases.MessageHeader;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class UploadMessageAndGetUidsOperation extends Operation {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;

    public UploadMessageAndGetUidsOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid) {
        super(IdentityAndUid.computeUniqueUid(ownedIdentity, messageUid), null, null);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }


    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        OutboxMessage outboxMessage;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                outboxMessage = OutboxMessage.get(sendManagerSession, ownedIdentity, messageUid);

                if (outboxMessage == null) {
                    cancel(UploadMessageCompositeOperation.RFC_MESSAGE_NOT_FOUND_IN_DATABASE);
                    return;
                }
                if (outboxMessage.getUidFromServer() != null) {
                    finished = true;
                    return;
                }
                MessageHeader[] headers = outboxMessage.getHeaders();
                OutboxAttachment[] attachments = outboxMessage.getAttachments();

                if (cancelWasRequested()) {
                    return;
                }

                UploadMessageAndGetUidsServerMethod serverMethod = new UploadMessageAndGetUidsServerMethod(
                        outboxMessage.getServer(),
                        outboxMessage.getEncryptedContent(),
                        outboxMessage.getEncryptedExtendedContent(),
                        outboxMessage.isApplicationMessage(),
                        outboxMessage.isVoipMessage(),
                        headers,
                        attachments);
                serverMethod.setSslSocketFactory(sslSocketFactory);

                // we need to block sending message for any inactive ownedIdentity, but, if the ownedIdentity was deleted, we should send the message
                // this is required for the OwnedIdentityDeletion protocol, to inform your contacts
                boolean sendMessage = sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity)
                        || !sendManagerSession.identityDelegate.isOwnedIdentity(sendManagerSession.session, ownedIdentity);
                byte returnStatus = serverMethod.execute(sendMessage);

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        outboxMessage.setUidFromServer(serverMethod.getUidFromServer(), serverMethod.getNonce(), serverMethod.getTimestampFromServer());
                        String[][] attachmentChunkUploadPrivateUrls = serverMethod.getAttachmentChunkUploadPrivateUrls();
                        for (int i=0; i<attachments.length; i++) {
                            attachments[i].setChunkUploadPrivateUrls(attachmentChunkUploadPrivateUrls[i]);
                        }
                        finished = true;
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE:
                        cancel(UploadMessageCompositeOperation.RFC_IDENTITY_IS_INACTIVE);
                        return;
                    case ServerMethod.OK_WITH_MALFORMED_SERVER_RESPONSE:
                        // unable to parse server response and get message Uid --> cancel all attachments and finish the operation
                        outboxMessage.setUidFromServer(new UID(new byte[UID.UID_LENGTH]), new byte[0], 0);
                        for (OutboxAttachment attachment: attachments) {
                            attachment.setCancelExternallyRequested();
                            attachment.setCancelProcessed();
                        }
                        finished = true;
                        return;
                    case ServerMethod.PAYLOAD_TOO_LARGE:
                    case ServerMethod.GENERAL_ERROR:
                    default:
                        if (returnStatus == ServerMethod.PAYLOAD_TOO_LARGE
                                || System.currentTimeMillis() > outboxMessage.getCreationTimestamp() + Constants.OUTBOX_MESSAGE_MAX_SEND_DELAY) {
                            // message is too large or too old --> we no longer try sending it
                            outboxMessage.setUidFromServer(new UID(new byte[UID.UID_LENGTH]), new byte[0], 0);
                            for (OutboxAttachment attachment: attachments) {
                                attachment.setCancelExternallyRequested();
                                attachment.setCancelProcessed();
                            }
                            HashMap<String, Object> userInfo = new HashMap<>();
                            userInfo.put(UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED_UID_KEY, outboxMessage.getUid());
                            userInfo.put(UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED_OWNED_IDENTITY_KEY, outboxMessage.getOwnedIdentity());
                            sendManagerSession.notificationPostingDelegate.postNotification(UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED, userInfo);
                            finished = true;
                        } else {
                            cancel(UploadMessageCompositeOperation.RFC_NETWORK_ERROR);
                        }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendManagerSession.session.rollback();
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
}


class UploadMessageAndGetUidsServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/uploadMessageAndGetUids";

    private final String server;
    private final EncryptedBytes encryptedMessage;
    private final EncryptedBytes encryptedExtendedMessage;
    private final boolean isApplicationMessage;
    private final boolean isVoipMessage;
    private final MessageHeader[] headers;
    private final OutboxAttachment[] attachments;

    private UID uidFromServer = null;
    private byte[] nonce = null;
    private long timestampFromServer = 0;
    private String[][] attachmentChunkUploadPrivateUrls = null;


    public UID getUidFromServer() {
        return uidFromServer;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public long getTimestampFromServer() {
        return timestampFromServer;
    }

    public String[][] getAttachmentChunkUploadPrivateUrls() {
        return attachmentChunkUploadPrivateUrls;
    }

    UploadMessageAndGetUidsServerMethod(String server, EncryptedBytes encryptedMessage, EncryptedBytes encryptedExtendedMessage, boolean isApplicationMessage, boolean isVoipMessage, MessageHeader[] headers, OutboxAttachment[] attachments) {
        this.server = server;
        this.encryptedMessage = encryptedMessage;
        this.encryptedExtendedMessage = encryptedExtendedMessage;
        this.isApplicationMessage = isApplicationMessage;
        this.isVoipMessage = isVoipMessage;
        this.headers = headers;
        this.attachments = attachments;
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
        Encoded[] encodedHeaders = new Encoded[headers.length * 3];
        for (int i=0; i<headers.length; i++) {
            encodedHeaders[3*i] = Encoded.of(headers[i].getDeviceUid());
            encodedHeaders[3*i+1] = Encoded.of(headers[i].getWrappedKey());
            encodedHeaders[3*i+2] = Encoded.of(headers[i].getToIdentity());
        }
        Encoded[] encodedAttachmentLengths = new Encoded[attachments.length];
        Encoded[] encodedChunkLengths = new Encoded[attachments.length];
        for (int i=0; i<attachments.length; i++) {
            encodedAttachmentLengths[i] = Encoded.of(attachments[i].getCiphertextLength());
            encodedChunkLengths[i] = Encoded.of(attachments[i].getCiphertextChunkLength());
        }
        if (encryptedExtendedMessage == null || encodedAttachmentLengths.length > Constants.MAX_MESSAGE_EXTENDED_CONTENT_LENGTH) {
            return Encoded.of(new Encoded[]{
                    Encoded.of(encodedHeaders),
                    Encoded.of(encryptedMessage),
                    Encoded.of(isApplicationMessage),
                    Encoded.of(isVoipMessage),
                    Encoded.of(encodedAttachmentLengths),
                    Encoded.of(encodedChunkLengths)
            }).getBytes();
        } else {
            return Encoded.of(new Encoded[]{
                    Encoded.of(encodedHeaders),
                    Encoded.of(encryptedMessage),
                    Encoded.of(encryptedExtendedMessage),
                    Encoded.of(isApplicationMessage),
                    Encoded.of(isVoipMessage),
                    Encoded.of(encodedAttachmentLengths),
                    Encoded.of(encodedChunkLengths)
            }).getBytes();
        }
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                uidFromServer = receivedData[0].decodeUid();
                nonce = receivedData[1].decodeBytes();
                timestampFromServer = receivedData[2].decodeLong();
                Encoded[] encodeds = receivedData[3].decodeList();
                if (encodeds.length != attachments.length) {
                    throw new Exception();
                }
                attachmentChunkUploadPrivateUrls = new String[attachments.length][];
                for (int i=0; i< encodeds.length; i++) {
                    attachmentChunkUploadPrivateUrls[i] = encodeds[i].decodeStringArray();
                }
            } catch (Exception e) {
                e.printStackTrace();
                returnStatus = ServerMethod.OK_WITH_MALFORMED_SERVER_RESPONSE;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}
