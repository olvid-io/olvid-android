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
import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.coordinators.WellKnownCoordinator;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.WellKnownCacheDelegate;

public class GetTurnCredentialsOperation extends Operation {
    public static final int RFC_INVALID_SERVER_SESSION = 1;
    public static final int RFC_WELL_KNOWN_NOT_CACHED = 2;
    public static final int RFC_PERMISSION_DENIED = 3;
    public static final int RFC_SERVER_DOES_NOT_SUPPORT_CALLS = 4;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final WellKnownCacheDelegate wellKnownCacheDelegate;
    private final Identity ownedIdentity;
    private final UUID callUuid;
    private final String username1;
    private final String username2;

    private List<String> turnServers;
    private String expiringUsername1;
    private String password1;
    private String expiringUsername2;
    private String password2;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UUID getCallUuid() {
        return callUuid;
    }

    public List<String> getTurnServers() {
        return turnServers;
    }

    public String getExpiringUsername1() {
        return expiringUsername1;
    }

    public String getPassword1() {
        return password1;
    }

    public String getExpiringUsername2() {
        return expiringUsername2;
    }

    public String getPassword2() {
        return password2;
    }

    public GetTurnCredentialsOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, WellKnownCacheDelegate wellKnownCacheDelegate, Identity ownedIdentity, UUID callUuid, String username1, String username2, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.wellKnownCacheDelegate = wellKnownCacheDelegate;
        this.ownedIdentity = ownedIdentity;
        this.callUuid = callUuid;
        this.username1 = username1;
        this.username2 = username2;
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
                try {
                    turnServers = wellKnownCacheDelegate.getTurnUrls(ownedIdentity.getServer());
                } catch (WellKnownCoordinator.NotCachedException e) {
                    cancel(RFC_WELL_KNOWN_NOT_CACHED);
                    return;
                }

                if (turnServers == null || turnServers.size() == 0) {
                    cancel(RFC_SERVER_DOES_NOT_SUPPORT_CALLS);
                    return;
                }
                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, ownedIdentity);

                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }


                GetTurnCredentialsServerMethod serverMethod = new GetTurnCredentialsServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        username1,
                        username2
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                switch (returnStatus) {
                    case ServerMethod.OK: {
                        expiringUsername1 = serverMethod.getExpiringUsername1();
                        password1 = serverMethod.getPassword1();
                        expiringUsername2 = serverMethod.getExpiringUsername2();
                        password2 = serverMethod.getPassword2();
                        break;
                    }
                    case ServerMethod.INVALID_SESSION: {
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    }
                    case ServerMethod.PERMISSION_DENIED:
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE: {
                        cancel(RFC_PERMISSION_DENIED);
                        return;
                    }
                }
                finished = true;
            } catch (Exception e) {
                Logger.x(e);
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
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

class GetTurnCredentialsServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/getTurnCredentials";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final String username1;
    private final String username2;

    private String expiringUsername1 = null;
    private String password1 = null;
    private String expiringUsername2 = null;
    private String password2 = null;

    public String getExpiringUsername1() {
        return expiringUsername1;
    }

    public String getPassword1() {
        return password1;
    }

    public String getExpiringUsername2() {
        return expiringUsername2;
    }

    public String getPassword2() {
        return password2;
    }

    public GetTurnCredentialsServerMethod(Identity identity, byte[] token, String username1, String username2) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.username1 = username1;
        this.username2 = username2;
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
                Encoded.of(username1),
                Encoded.of(username2),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                expiringUsername1 = receivedData[0].decodeString();
                password1 = receivedData[1].decodeString();
                expiringUsername2 = receivedData[2].decodeString();
                password2 = receivedData[3].decodeString();
            } catch (DecodingException e) {
                Logger.x(e);
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}