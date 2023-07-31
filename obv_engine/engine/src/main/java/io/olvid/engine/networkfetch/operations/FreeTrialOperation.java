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
import java.util.HashMap;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class FreeTrialOperation extends Operation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final boolean retrieveApiKey;

    public FreeTrialOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, boolean retrieveApiKey) {
        super(ownedIdentity.computeUniqueUid(), null, null);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.retrieveApiKey = retrieveApiKey;
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
                    FreeTrialServerMethod serverMethod = new FreeTrialServerMethod(
                            ownedIdentity,
                            serverSessionToken,
                            retrieveApiKey
                    );
                    serverMethod.setSslSocketFactory(sslSocketFactory);

                    byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                    fetchManagerSession.session.startTransaction();
                    switch (returnStatus) {
                        case ServerMethod.OK: {
                            if (retrieveApiKey) {
                                HashMap<String, Object> userInfo = new HashMap<>();
                                userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS_OWNED_IDENTITY_KEY, ownedIdentity);
                                fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS, userInfo);

                                ServerSession.deleteForIdentity(fetchManagerSession, ownedIdentity);
                                fetchManagerSession.createServerSessionDelegate.createServerSession(ownedIdentity);
                            } else {
                                HashMap<String, Object> userInfo = new HashMap<>();
                                userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_OWNED_IDENTITY_KEY, ownedIdentity);
                                userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY, true);
                                fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS, userInfo);
                            }
                            finished = true;
                            return;
                        }
                        case ServerMethod.FREE_TRIAL_ALREADY_USED: {
                            if (!retrieveApiKey) {
                                HashMap<String, Object> userInfo = new HashMap<>();
                                userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_OWNED_IDENTITY_KEY, ownedIdentity);
                                userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY, false);
                                fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS, userInfo);
                                finished = true;
                                return;
                            }
                            break;
                        }
                        case ServerMethod.INVALID_SESSION: {
                            ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                            fetchManagerSession.session.commit();
                            break;
                        }
                    }
                }

                // did not get an OK response --> notify failed
                if (retrieveApiKey) {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED_OWNED_IDENTITY_KEY, ownedIdentity);
                    fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED, userInfo);
                } else {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED_OWNED_IDENTITY_KEY, ownedIdentity);
                    fetchManagerSession.notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED, userInfo);
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

class FreeTrialServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/freeTrial";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final boolean retrieveApiKey;

    private UUID apiKey = null;

    public UUID getApiKey() {
        return apiKey;
    }

    public FreeTrialServerMethod(Identity identity, byte[] token, boolean retrieveApiKey) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.retrieveApiKey = retrieveApiKey;
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
                Encoded.of(retrieveApiKey),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                if (retrieveApiKey) {
                    this.apiKey = receivedData[0].decodeUuid();
                }
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