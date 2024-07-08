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
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

class GetTokenOperation extends Operation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;

    private ServerSession.ApiKeyStatus apiKeyStatus;
    private List<ServerSession.Permission> permissions;
    private long apiKeyExpirationTimestamp;

    public GetTokenOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, OnFinishCallback onFinishCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, null);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public ServerSession.ApiKeyStatus getApiKeyStatus() {
        return apiKeyStatus;
    }

    public List<ServerSession.Permission> getPermissions() {
        return permissions;
    }

    public long getApiKeyExpirationTimestamp() {
        return apiKeyExpirationTimestamp;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        ServerSession serverSession;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                serverSession = ServerSession.get(fetchManagerSession, ownedIdentity);

                if (serverSession == null) {
                    cancel(CreateServerSessionCompositeOperation.RFC_SESSION_CANNOT_BE_FOUND);
                    return;
                }
                if (serverSession.getNonce() == null) {
                    cancel(CreateServerSessionCompositeOperation.RFC_SESSION_DOES_NOT_CONTAIN_A_NONCE);
                    return;
                }
                if (serverSession.getResponse() == null) {
                    cancel(CreateServerSessionCompositeOperation.RFC_SESSION_DOES_NOT_CONTAIN_A_RESPONSE);
                    return;
                }
                if (serverSession.getToken() != null) {
                    finished = true;
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

                GetTokenServerMethod serverMethod = new GetTokenServerMethod(
                        ownedIdentity,
                        serverSession.getResponse(),
                        serverSession.getNonce()
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        serverSession.setTokenAndPermissions(serverMethod.getToken(), serverMethod.getApiKeyStatus(), serverMethod.getPermissions(), serverMethod.getApiKeyExpiration());
                        apiKeyStatus = serverSession.getApiKeyStatus();
                        permissions = serverSession.getPermissions();
                        apiKeyExpirationTimestamp = serverSession.getApiKeyExpirationTimestamp();
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        serverSession.delete();
                        fetchManagerSession.session.commit();
                        cancel(CreateServerSessionCompositeOperation.RFC_INVALID_SESSION);
                        return;
                    default:
                        cancel(CreateServerSessionCompositeOperation.RFC_NETWORK_ERROR);
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
            cancel(null);
            processCancel();
        }
    }
}

class GetTokenServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/getToken";

    private final String server;
    private final Identity identity;
    private final byte[] response;
    private final byte[] nonce;

    private byte[] token = null;
    private int apiKeyStatus = -1;
    private long permissions = 0;
    private long apiKeyExpiration = 0;

    public GetTokenServerMethod(Identity identity, byte[] response, byte[] nonce) {
        this.server = identity.getServer();
        this.identity = identity;
        this.response = response;
        this.nonce = nonce;
    }

    public byte[] getToken() {
        return token;
    }

    public int getApiKeyStatus() {
        return apiKeyStatus;
    }

    public long getPermissions() {
        return permissions;
    }

    public long getApiKeyExpiration() {
        return apiKeyExpiration;
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
                Encoded.of(response),
                Encoded.of(nonce)
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                byte[] token = receivedData[0].decodeBytes();
                byte[] serverNonce = receivedData[1].decodeBytes();
                if (!Arrays.equals(nonce, serverNonce) ||
                        (token.length != Constants.SERVER_SESSION_TOKEN_LENGTH)) {
                    returnStatus = ServerMethod.GENERAL_ERROR;
                    return;
                }
                this.token = token;
                this.apiKeyStatus = (int) receivedData[2].decodeLong();
                this.permissions = receivedData[3].decodeLong();
                this.apiKeyExpiration = receivedData[4].decodeLong();
            } catch (DecodingException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }
}