/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class DownloadMessagesExtendedPayloadOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SERVER_SESSION = 2;
    public static final int RFC_IDENTITY_IS_INACTIVE = 3;
    public static final int RFC_EXTENDED_PAYLOAD_UNAVAILABLE_OR_INVALID = 4;
    public static final int RFC_MESSAGE_CANNOT_BE_FOUND = 5;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public DownloadMessagesExtendedPayloadOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
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
                InboxMessage inboxMessage = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
                if (inboxMessage == null) {
                    cancel(RFC_MESSAGE_CANNOT_BE_FOUND);
                    return;
                }
                AuthEncKey extendedPayloadKey = inboxMessage.getExtendedPayloadKey();
                if (extendedPayloadKey == null) {
                    cancel(RFC_EXTENDED_PAYLOAD_UNAVAILABLE_OR_INVALID);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                DownloadMessagesExtendedPayloadServerMethod serverMethod = new DownloadMessagesExtendedPayloadServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        messageUid
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                switch (returnStatus) {
                    case ServerMethod.OK: {
                        byte[] messageExtendedPayload;
                        try {
                            AuthEnc authEnc = Suite.getAuthEnc(extendedPayloadKey);
                            messageExtendedPayload = authEnc.decrypt(extendedPayloadKey, serverMethod.getEncryptedMessageExtendedPayload());
                        } catch (Exception e) {
                            cancel(RFC_EXTENDED_PAYLOAD_UNAVAILABLE_OR_INVALID);
                            return;
                        }

                        fetchManagerSession.session.startTransaction();
                        inboxMessage.setExtendedPayload(messageExtendedPayload);
                        finished = true;
                        return;
                    }
                    case ServerMethod.INVALID_SESSION: {
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    }
                    case ServerMethod.EXTENDED_PAYLOAD_UNAVAILABLE: {
                        cancel(RFC_EXTENDED_PAYLOAD_UNAVAILABLE_OR_INVALID);
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


class DownloadMessagesExtendedPayloadServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/downloadMessageExtendedContent";

    private final String server;
    private final Identity ownedIdentity;
    private final byte[] token;
    private final UID messageUid;

    private EncryptedBytes encryptedMessageExtendedPayload;

    public EncryptedBytes getEncryptedMessageExtendedPayload() {
        return encryptedMessageExtendedPayload;
    }

    DownloadMessagesExtendedPayloadServerMethod(Identity ownedIdentity, byte[] token, UID messageUid) {
        this.server = ownedIdentity.getServer();
        this.ownedIdentity = ownedIdentity;
        this.token = token;
        this.messageUid = messageUid;
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
                Encoded.of(messageUid)
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                encryptedMessageExtendedPayload = receivedData[0].decodeEncryptedData();
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
