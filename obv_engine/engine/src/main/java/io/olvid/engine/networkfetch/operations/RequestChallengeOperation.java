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

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

class RequestChallengeOperation extends Operation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;

    public RequestChallengeOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity) {
        super(ownedIdentity.computeUniqueUid(), null, null);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
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
        ServerSession serverSession;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                serverSession = ServerSession.get(fetchManagerSession, ownedIdentity);

                if (serverSession == null) {
                    serverSession = ServerSession.create(fetchManagerSession, ownedIdentity);
                    if (serverSession == null) {
                        cancel(null);
                        return;
                    }
                    fetchManagerSession.session.commit();
                }
                if (serverSession.getChallenge() != null) {
                    finished = true;
                    return;
                }

                PRNGService prng = Suite.getPRNGService(PRNG.PRNG_HMAC_SHA256);
                byte[] nonce = prng.bytes(Constants.SERVER_SESSION_NONCE_LENGTH);


                if (cancelWasRequested()) {
                    return;
                }

                RequestChallengeServerMethod serverMethod = new RequestChallengeServerMethod(
                        ownedIdentity,
                        nonce
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        serverSession.setChallengeAndNonce(serverMethod.getChallenge(), nonce);
                        finished = true;
                        return;
                    default:
                        cancel(CreateServerSessionCompositeOperation.RFC_NETWORK_ERROR);
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

class RequestChallengeServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/requestChallenge";

    private final String server;
    private final Identity identity;
    private final byte[] nonce;

    private byte[] challenge = null;

    public RequestChallengeServerMethod(Identity identity, byte[] nonce) {
        this.server = identity.getServer();
        this.identity = identity;
        this.nonce = nonce;
    }

    public byte[] getChallenge() {
        return challenge;
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
                Encoded.of(nonce),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                byte[] challenge = receivedData[0].decodeBytes();
                byte[] serverNonce = receivedData[1].decodeBytes();
                if (!Arrays.equals(nonce, serverNonce) ||
                        (challenge.length != Constants.SERVER_SESSION_CHALLENGE_LENGTH)) {
                    returnStatus = ServerMethod.GENERAL_ERROR;
                    return;
                }
                this.challenge = challenge;
            } catch (DecodingException e) {
                Logger.x(e);
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }
}