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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class ServerQueryOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_BAD_ENCODED_SERVER_QUERY = 2;
    public static final int RFC_INVALID_SERVER_SESSION = 3;
    public static final int RFC_IDENTITY_IS_INACTIVE = 4;
    public static final int RFC_USER_DATA_TOO_LARGE = 5;

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final UID serverQueryUid;
    private final PRNG prng;
    private ServerQuery serverQuery; // will be set if the operation finishes normally
    private Encoded serverResponse; // will be set if the operation finishes normally

    public ServerQueryOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, UID serverQueryUid, PRNG prng, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(serverQueryUid, onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.serverQueryUid = serverQueryUid;
        this.prng = prng;
    }

    public UID getServerQueryUid() {
        return serverQueryUid;
    }

    public ServerQuery getServerQuery() {
        return serverQuery;
    }

    public Encoded getServerResponse() {
        return serverResponse;
    }

    @Override
    public void doCancel() {
        // Nothings special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                PendingServerQuery pendingServerQuery = PendingServerQuery.get(fetchManagerSession, serverQueryUid);
                if (pendingServerQuery == null) {
                    cancel(RFC_BAD_ENCODED_SERVER_QUERY);
                    return;
                }
                try {
                    serverQuery = ServerQuery.of(pendingServerQuery.getEncodedQuery());
                } catch (DecodingException e) {
                    cancel(RFC_BAD_ENCODED_SERVER_QUERY);
                    return;
                }

                ServerQueryServerMethod serverMethod;
                switch (serverQuery.getType().getId()) {
                    case ServerQuery.Type.DEVICE_DISCOVERY_QUERY_ID: {
                        serverMethod = new DeviceDiscoveryServerMethod(serverQuery.getType().getIdentity());
                        break;
                    }
                    case ServerQuery.Type.PUT_USER_DATA_QUERY_ID: {
                        byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, serverQuery.getType().getIdentity());
                        if (serverSessionToken == null) {
                            cancel(RFC_INVALID_SERVER_SESSION);
                            return;
                        }
                        // encrypt the photo
                        File photoFile = new File(fetchManagerSession.engineBaseDirectory, serverQuery.getType().getDataUrl());
                        byte[] buffer;
                        try {
                            buffer = new byte[(int) photoFile.length()];
                            if (buffer.length == 0) {
                                throw new Exception();
                            }
                        } catch (Exception e) {
                            // unable to find source file. Finish normally so the protocol can finish
                            Logger.e("PutUserData Error: Unable to open file " + photoFile);
                            serverResponse = null;
                            finished = true;
                            return;
                        }
                        try (FileInputStream f = new FileInputStream(photoFile)) {
                            int bufferFullness = 0;
                            while (bufferFullness < buffer.length) {
                                int count = f.read(buffer, bufferFullness, buffer.length - bufferFullness);
                                if (count < 0) {
                                    break;
                                }
                                bufferFullness += count;
                            }
                        }

                        AuthEnc authEnc = Suite.getAuthEnc(serverQuery.getType().getDataKey());
                        EncryptedBytes encryptedPhoto = authEnc.encrypt(serverQuery.getType().getDataKey(), buffer, prng);

                        serverMethod = new PutUserDataServerMethod(serverQuery.getType().getIdentity(), serverSessionToken, serverQuery.getType().getServerLabel(), encryptedPhoto);
                        break;
                    }
                    case ServerQuery.Type.GET_USER_DATA_QUERY_ID: {
                        serverMethod = new GetUserDataServerMethod(serverQuery.getType().getIdentity(), serverQuery.getType().getServerLabel(), fetchManagerSession.engineBaseDirectory);
                        break;
                    }
                    case ServerQuery.Type.CHECK_KEYCLOAK_REVOCATION_QUERY_ID: {
                        serverMethod = new CheckKeycloakRevocationServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getSignedContactDetails());
                        break;
                    }
                    default:
                        cancel(RFC_BAD_ENCODED_SERVER_QUERY);
                        return;
                }
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, serverQuery.getOwnedIdentity()));

                switch (returnStatus) {
                    case ServerMethod.OK:
                    case ServerMethod.DELETED_FROM_SERVER:
                        // in case it is deleted from server, we finish normally with an empty file name --> this way the protocol can chose how to handle this case
                        serverResponse = serverMethod.getServerResponse();
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE:
                        cancel(RFC_IDENTITY_IS_INACTIVE);
                        return;
                    case ServerMethod.PAYLOAD_TOO_LARGE:
                        cancel(RFC_USER_DATA_TOO_LARGE);
                        return;
                    default:
                        cancel(RFC_NETWORK_ERROR);
                        return;
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            e.printStackTrace();
        }
    }
}

abstract class ServerQueryServerMethod extends ServerMethod {
    public abstract Encoded getServerResponse();

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}

class DeviceDiscoveryServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/deviceDiscovery";

    private final String server;
    private final Identity identity;

    private Encoded serverResponse;

    public DeviceDiscoveryServerMethod(Identity identity) {
        this.server = identity.getServer();
        this.identity = identity;
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
                Encoded.of(identity)
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                // check that decoding works properly
                receivedData[0].decodeUidArray();
                serverResponse = receivedData[0];
            } catch (DecodingException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class PutUserDataServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/putUserData";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final UID serverLabel;
    private final EncryptedBytes data;

    private Encoded serverResponse;

    public PutUserDataServerMethod(Identity identity, byte[] token, UID serverLabel, EncryptedBytes data) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.serverLabel = serverLabel;
        this.data = data;
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
                Encoded.of(serverLabel),
                Encoded.of(data),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            if (receivedData.length == 0) {
                // check that decoding works properly
                serverResponse = null;
            } else {
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class GetUserDataServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/getUserData";

    private final String server;
    private final Identity identity;
    private final UID serverLabel;
    private final String engineBaseDirectory;

    private Encoded serverResponse;

    public GetUserDataServerMethod(Identity identity, UID serverLabel, String engineBaseDirectory) {
        this.server = identity.getServer();
        this.identity = identity;
        this.serverLabel = serverLabel;
        this.engineBaseDirectory = engineBaseDirectory;
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
                Encoded.of(serverLabel),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                // write the result to a file
                EncryptedBytes encryptedData = receivedData[0].decodeEncryptedData();
                // Ugly hack: the filename contains a timestamp after which the file is considered "orphan" and can be deleted
                String userDataPath = Constants.DOWNLOADED_USER_DATA_DIRECTORY + File.separator + (System.currentTimeMillis() + Constants.GET_USER_DATA_LOCAL_FILE_LIFESPAN) + "." + Logger.toHexString(serverLabel.getBytes());
                try (FileOutputStream fis = new FileOutputStream(new File(engineBaseDirectory, userDataPath))) {
                    fis.write(encryptedData.getBytes());
                }
                serverResponse = Encoded.of(userDataPath);
            } catch (DecodingException | IOException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER) {
            serverResponse = Encoded.of("");
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class CheckKeycloakRevocationServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "olvid-rest/verify";

    private final String server;
    private final String path;
    private final String signedContactDetails; // this is a JWT

    private Encoded serverResponse;

    public CheckKeycloakRevocationServerMethod(String keycloakServerUrl, String signedContactDetails) {
        this.signedContactDetails = signedContactDetails;

        String url = keycloakServerUrl + SERVER_METHOD_PATH;
        int pos = url.indexOf('/', 8);
        this.server = url.substring(0, pos);
        this.path = url.substring(pos);
    }

    @Override
    protected String getServer() {
        return server;
    }

    @Override
    protected String getServerMethod() {
        return path;
    }

    @Override
    protected byte[] getDataToSend() {
        String jsonString = "{\"signature\": \"" + signedContactDetails + "\"}";
        return jsonString.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                boolean verificationSuccessful = receivedData[0].decodeBoolean();
                Logger.w("Server responded to verify server query: " + verificationSuccessful);
                serverResponse = Encoded.of(verificationSuccessful);
            } catch (DecodingException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}