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

package io.olvid.engine.networkfetch.operations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.UidAndBoolean;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.MessageBatchProvider;


public class DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SERVER_SESSION = 2;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final MessageBatchProvider messageBatchProvider;
    private UidAndBoolean[] messageUidsAndMarkAsListed;

    public DeleteMessageAndAttachmentFromServerAndLocalInboxesOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, MessageBatchProvider messageBatchProvider, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageBatchProvider = messageBatchProvider;
    }


    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UidAndBoolean[] getMessageUidsAndMarkAsListed() {
        return messageUidsAndMarkAsListed;
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
                this.messageUidsAndMarkAsListed = messageBatchProvider.getBatchOFMessageUids(ownedIdentity);

                List<MessageAndMarkAsListed> messageAndPendingDeletes = new ArrayList<>();
                for (UidAndBoolean messageUidAndMarkAsListed : messageUidsAndMarkAsListed) {
                    // message may be null in case of re-list of an already deleted message
                    InboxMessage message = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUidAndMarkAsListed.uid);

                    if (messageUidAndMarkAsListed.bool) {
                        if (message == null) {
                            continue;
                        }
                    } else {
                        if (message != null && !message.canBeDeleted()) {
                            continue;
                        }
                    }

                    messageAndPendingDeletes.add(new MessageAndMarkAsListed(message, messageUidAndMarkAsListed));
                }

                if (messageAndPendingDeletes.isEmpty()) {
                    // nothing to actually do!
                    finished = true;
                    return;
                }

                if (messageUidsAndMarkAsListed.length != messageAndPendingDeletes.size()) {
                    // some messages were skipped, update the messageUidsAndMarkAsListed to avoid unnecessary re-queues in case of failure
                    messageUidsAndMarkAsListed = new UidAndBoolean[messageAndPendingDeletes.size()];
                    for (int i=0; i<messageUidsAndMarkAsListed.length; i++) {
                        messageUidsAndMarkAsListed[i] = messageAndPendingDeletes.get(i).messageUidAndMarkAsListed;
                    }
                }

                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, ownedIdentity);
                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                UID currentDeviceUid = fetchManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(fetchManagerSession.session, ownedIdentity);

                DeleteMessageAndAttachmentServerMethod serverMethod = new DeleteMessageAndAttachmentServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        currentDeviceUid,
                        messageUidsAndMarkAsListed
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        for (MessageAndMarkAsListed messageAndMarkAsListed : messageAndPendingDeletes) {
                            if (!messageAndMarkAsListed.messageUidAndMarkAsListed.bool && messageAndMarkAsListed.message != null) {
                                messageAndMarkAsListed.message.delete();
                            }
                        }
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    default:
                        cancel(RFC_NETWORK_ERROR);
                        return;
                }
            } catch (Exception e) {
                Logger.x(e);
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
            Logger.x(e);
            cancel(null);
            processCancel();
        }
    }
}

class DeleteMessageAndAttachmentServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/deleteMessageAndAttachments";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final UID currentDeviceUid;
    private final UidAndBoolean[] messageUidsAndMarkAsListed;

    DeleteMessageAndAttachmentServerMethod(Identity identity, byte[] token, UID currentDeviceUid, UidAndBoolean[] messageUidsAndMarkAsListed) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.currentDeviceUid = currentDeviceUid;
        this.messageUidsAndMarkAsListed = messageUidsAndMarkAsListed;
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
        Encoded[] encodedMessageUidsAndMarkAsListed = new Encoded[2* messageUidsAndMarkAsListed.length];
        for (int i = 0; i< messageUidsAndMarkAsListed.length; i++) {
            encodedMessageUidsAndMarkAsListed[2*i] = Encoded.of(messageUidsAndMarkAsListed[i].uid);
            encodedMessageUidsAndMarkAsListed[2*i + 1] = Encoded.of(messageUidsAndMarkAsListed[i].bool);
        }

        return Encoded.of(new Encoded[]{
                Encoded.of(identity),
                Encoded.of(token),
                Encoded.of(currentDeviceUid),
                Encoded.of(encodedMessageUidsAndMarkAsListed),
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

class MessageAndMarkAsListed {
    final InboxMessage message;
    final UidAndBoolean messageUidAndMarkAsListed;

    public MessageAndMarkAsListed(InboxMessage message, UidAndBoolean messageUidAndMarkAsListed) {
        this.message = message;
        this.messageUidAndMarkAsListed = messageUidAndMarkAsListed;
    }
}