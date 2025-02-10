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

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class DeleteUserDataOperation extends Operation {
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_USER_DATA_NOT_FOUND = 2;
    public static final int RFC_INVALID_SERVER_SESSION = 3;
    public static final int RFC_IDENTITY_IS_INACTIVE = 4;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID label;

    public DeleteUserDataOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID label, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(IdentityAndUid.computeUniqueUid(ownedIdentity, label), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.label = label;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getLabel() {
        return label;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        UserData userData;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                userData = fetchManagerSession.identityDelegate.getUserData(fetchManagerSession.session, ownedIdentity, label);
                if (userData == null) {
                    cancel(RFC_USER_DATA_NOT_FOUND);
                    return;
                }

                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, ownedIdentity);
                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                DeleteUserDataServerMethod serverMethod = new DeleteUserDataServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        label
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        fetchManagerSession.identityDelegate.deleteUserData(fetchManagerSession.session, ownedIdentity, label);
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE: {
                        cancel(RFC_IDENTITY_IS_INACTIVE);
                        return;
                    }
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

class DeleteUserDataServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/deleteUserData";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final UID label;

    public DeleteUserDataServerMethod(Identity identity, byte[] token, UID label) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.label = label;
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
                Encoded.of(label),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        // Nothing to parse here
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}