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

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class RefreshInboxAttachmentSignedUrlOperation extends Operation {
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_ATTACHMENT_NOT_FOUND = 2;
    public static final int RFC_DELETED_FROM_SERVER = 3;
    public static final int RFC_IDENTITY_IS_INACTIVE = 4;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;

    public RefreshInboxAttachmentSignedUrlOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, int attachmentNumber, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(InboxAttachment.computeUniqueUid(ownedIdentity, messageUid, attachmentNumber), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
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
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                InboxAttachment inboxAttachment = InboxAttachment.get(fetchManagerSession, ownedIdentity, messageUid, attachmentNumber);

                if (inboxAttachment == null) {
                    cancel(RFC_ATTACHMENT_NOT_FOUND);
                    return;
                }

                if (cancelWasRequested()) {
                    return;
                }

                RefreshInboxAttachmentSignedUrlServerMethod serverMethod = new RefreshInboxAttachmentSignedUrlServerMethod(
                        ownedIdentity,
                        messageUid,
                        attachmentNumber,
                        (int) ((inboxAttachment.getExpectedLength()-1)/inboxAttachment.getChunkLength()) + 1
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        inboxAttachment.setChunkDownloadPrivateUrls(serverMethod.getSignedUrls());
                        finished = true;
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
        }
    }
}

class RefreshInboxAttachmentSignedUrlServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/downloadAttachmentChunk";

    private final String server;
    private final UID messageUid;
    private final int attachmentNumber;
    private final int expectedChunkCount;

    private String[] signedUrls = null;

    public String[] getSignedUrls() {
        return signedUrls;
    }

    public RefreshInboxAttachmentSignedUrlServerMethod(Identity identity, UID messageUid, int attachmentNumber, int expectedChunkCount) {
        this.server = identity.getServer();
        this.messageUid = messageUid;
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
                Encoded.of(messageUid),
                Encoded.of(attachmentNumber),
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
