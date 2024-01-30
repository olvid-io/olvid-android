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

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class CancelAttachmentUploadOperation extends Operation {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;

    public CancelAttachmentUploadOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        super(IdentityAndUid.computeUniqueUid(ownedIdentity, messageUid), null, null);
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

    public int getAttachmentNumber() { return attachmentNumber; }

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
                outboxMessage = OutboxMessage.get(sendManagerSession, ownedIdentity, messageUid);
                outboxAttachment = OutboxAttachment.get(sendManagerSession, ownedIdentity, messageUid, attachmentNumber);

                if (outboxMessage == null || outboxAttachment == null) {
                    cancel(CancelAttachmentUploadCompositeOperation.RFC_ATTACHMENT_NOT_FOUND_IN_DATABASE);
                    return;
                }
                if (outboxMessage.getUidFromServer() == null) {
                    finished = true;
                    return;
                }

                if (outboxAttachment.isAcknowledged()) {
                    finished = true;
                    return;
                }

                if (cancelWasRequested()) {
                    return;
                }

                CancelAttachmentUploadServerMethod serverMethod = new CancelAttachmentUploadServerMethod(
                        outboxMessage.getServer(),
                        outboxMessage.getUidFromServer(),
                        outboxMessage.getNonce(),
                        attachmentNumber);
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity));

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.INVALID_NONCE:
                        Logger.i("Received INVALID_NONCE in CancelAttachmentUploadOperation");
                        // even if we get an invalid nonce error, there is not much we can do
                        //   --> we do as if it was a success to then allow to delete the outboxMessage
                        //noinspection fallthrough
                    case ServerMethod.OK:
                        outboxAttachment.setCancelProcessed();
                        finished = true;
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE:
                        cancel(CancelAttachmentUploadCompositeOperation.RFC_IDENTITY_IS_INACTIVE);
                        return;
                    default:
                        cancel(CancelAttachmentUploadCompositeOperation.RFC_NETWORK_ERROR);
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


class CancelAttachmentUploadServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/cancelAttachmentUpload";

    private final String server;
    private final UID uidFromServer;
    private final byte[] nonce;
    private final int attachmentNumber;


    CancelAttachmentUploadServerMethod(String server, UID uidFromServer, byte[] nonce, int attachmentNumber) {
        this.server = server;
        this.uidFromServer = uidFromServer;
        this.nonce = nonce;
        this.attachmentNumber = attachmentNumber;
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
                Encoded.of(uidFromServer),
                Encoded.of(attachmentNumber),
                Encoded.of(nonce),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        // nothing to parse here
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}
