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

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;

public class RefreshOutboxAttachmentSignedUrlOperation extends Operation {
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_ATTACHMENT_NOT_FOUND = 2;
    public static final int RFC_DELETED_FROM_SERVER = 3;
    public static final int RFC_IDENTITY_IS_INACTIVE = 4;
    public static final int RFC_INVALID_NONCE = 5;

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;

    public RefreshOutboxAttachmentSignedUrlOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, int attachmentNumber, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(OutboxAttachment.computeUniqueUid(ownedIdentity, messageUid, attachmentNumber), onFinishCallback, onCancelCallback);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
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

    @Override
    public void doExecute() {
        boolean finished = false;
        OutboxAttachment outboxAttachment;
        OutboxMessage outboxMessage;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                outboxAttachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);

                if (outboxAttachment == null) {
                    cancel(RFC_ATTACHMENT_NOT_FOUND);
                    return;
                }
                outboxMessage = OutboxMessage.get(sendManagerSession, outboxAttachment.getOwnedIdentity(), outboxAttachment.getMessageUid());
                if (outboxMessage == null || outboxMessage.getUidFromServer() == null) {
                    cancel(RFC_ATTACHMENT_NOT_FOUND);
                    return;
                }

                if (cancelWasRequested()) {
                    return;
                }

                RefreshOutboxAttachmentSignedUrlServerMethod serverMethod = new RefreshOutboxAttachmentSignedUrlServerMethod(
                        outboxMessage.getServer(),
                        outboxMessage.getUidFromServer(),
                        outboxMessage.getNonce(),
                        attachmentNumber,
                        outboxAttachment.getNumberOfChunks()
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity));

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        outboxAttachment.setChunkUploadPrivateUrls(serverMethod.getSignedUrls());
                        finished = true;
                        return;
                    case ServerMethod.INVALID_NONCE:
                        cancel(RFC_INVALID_NONCE);
                        return;
                    case ServerMethod.DELETED_FROM_SERVER:
                        cancel(RFC_DELETED_FROM_SERVER);
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE:
                        cancel(RFC_IDENTITY_IS_INACTIVE);
                        return;
                    default:
                        cancel(RFC_NETWORK_ERROR);
                        return;
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

class RefreshOutboxAttachmentSignedUrlServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/uploadAttachment";

    private final String server;
    private final UID messageUidFromServer;
    private final byte[] nonce;
    private final int attachmentNumber;
    private final int expectedChunkCount;

    private String[] signedUrls = null;

    public String[] getSignedUrls() {
        return signedUrls;
    }

    public RefreshOutboxAttachmentSignedUrlServerMethod(String server, UID messageUidFromServer, byte[] nonce, int attachmentNumber, int expectedChunkCount) {
        this.server = server;
        this.messageUidFromServer = messageUidFromServer;
        this.nonce = nonce;
        this.attachmentNumber = attachmentNumber;
        this.expectedChunkCount = expectedChunkCount;
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
                Encoded.of(messageUidFromServer),
                Encoded.of(attachmentNumber),
                Encoded.of(nonce),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                String[] signedUrls = receivedData[0].decodeStringArray();
                if (signedUrls.length != expectedChunkCount) {
                    throw new DecodingException("Attachment chunk count mismatch");
                }
                this.signedUrls = signedUrls;
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
}
