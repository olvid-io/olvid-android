/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.PendingDeleteFromServer;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SERVER_SESSION = 2;
    public static final int RFC_MESSAGE_AND_ATTACHMENTS_CANNOT_BE_DELETED = 3;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;

    public DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(InboxMessage.computeUniqueUid(ownedIdentity, messageUid), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        PendingDeleteFromServer pendingDeleteFromServer;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                pendingDeleteFromServer = PendingDeleteFromServer.get(fetchManagerSession, ownedIdentity, messageUid);
                if (pendingDeleteFromServer == null) {
                    finished = true;
                    return;
                }
                InboxMessage message = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
                if (message == null) {
                    fetchManagerSession.session.startTransaction();
                    pendingDeleteFromServer.delete();
                    finished = true;
                    return;
                }

                UID toDeviceUid = fetchManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(fetchManagerSession.session, ownedIdentity);

                if (! message.canBeDeleted()) {
                    cancel(RFC_MESSAGE_AND_ATTACHMENTS_CANNOT_BE_DELETED);
                    return;
                }

                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, message.getOwnedIdentity());
                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                DeleteMessageAndAttachmentServerMethod serverMethod = new DeleteMessageAndAttachmentServerMethod(
                        message.getOwnedIdentity(),
                        serverSessionToken,
                        messageUid,
                        toDeviceUid
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        pendingDeleteFromServer.delete();
                        message.delete();
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, message.getOwnedIdentity());
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
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

class DeleteMessageAndAttachmentServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/deleteMessageAndAttachments";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final UID messageUid;
    private final UID deviceUid;

    DeleteMessageAndAttachmentServerMethod(Identity identity, byte[] token, UID messageUid, UID deviceUid) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.messageUid = messageUid;
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
                Encoded.of(identity),
                Encoded.of(token),
                Encoded.of(messageUid),
                Encoded.of(deviceUid)
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        // Nothing to parse here
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }
}
