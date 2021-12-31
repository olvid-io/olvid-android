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
import java.util.HashMap;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class VerifyReceiptOperation extends Operation {
    public static final int RFC_INVALID_SERVER_SESSION = 0;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final String storeToken;

    public VerifyReceiptOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, String storeToken, OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), null, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.storeToken = storeToken;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public String getStoreToken() {
        return storeToken;
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
                if (serverSessionToken != null) {
                    VerifyReceiptServerMethod serverMethod = new VerifyReceiptServerMethod(
                            ownedIdentity,
                            serverSessionToken,
                            storeToken
                    );
                    serverMethod.setSslSocketFactory(sslSocketFactory);

                    byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                    fetchManagerSession.session.startTransaction();
                    switch (returnStatus) {
                        case ServerMethod.OK: {
                            HashMap<String, Object> userInfo = new HashMap<>();
                            userInfo.put(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_OWNED_IDENTITY_KEY, ownedIdentity);
                            userInfo.put(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY, storeToken);
                            fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS, userInfo);

                            fetchManagerSession.identityDelegate.updateApiKeyOfOwnedIdentity(fetchManagerSession.session, ownedIdentity, serverMethod.getApiKey());
                            ServerSession.deleteForIdentity(fetchManagerSession, ownedIdentity);
                            fetchManagerSession.createServerSessionDelegate.createServerSession(ownedIdentity);
                            finished = true;
                            return;
                        }
                        case ServerMethod.INVALID_SESSION: {
                            ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                            fetchManagerSession.session.commit();
                            cancel(RFC_INVALID_SERVER_SESSION);
                            break;
                        }
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
        }
    }
}

class VerifyReceiptServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/verifyReceipt";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final String storeToken;

    private UUID apiKey = null;

    public UUID getApiKey() {
        return apiKey;
    }

    public VerifyReceiptServerMethod(Identity identity, byte[] token, String storeToken) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.storeToken = storeToken;
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
                Encoded.of(Constants.ANDROID_STORE_ID),
                Encoded.of(storeToken),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                this.apiKey = receivedData[0].decodeUuid();
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