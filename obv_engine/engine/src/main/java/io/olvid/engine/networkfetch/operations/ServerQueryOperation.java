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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

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
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
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
    public static final int RFC_DEVICE_DOES_NOT_EXIST = 6;
    public static final int RFC_DEVICE_NOT_YET_REGISTERED = 7;
    public static final int RFC_MALFORMED_URL = 8;

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

                Logger.d("?? Starting server query operation of type " + serverQuery.getType().getId());

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
                        String absoluteOrNotPhotoUrl = serverQuery.getType().getDataUrl();
                        File photoFile;
                        if (new File(absoluteOrNotPhotoUrl).isAbsolute()) {
                            photoFile = new File(absoluteOrNotPhotoUrl);
                        } else {
                            photoFile = new File(fetchManagerSession.engineBaseDirectory, absoluteOrNotPhotoUrl);
                        }
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

                        serverMethod = new PutUserDataServerMethod(serverQuery.getType().getIdentity(), serverSessionToken, serverQuery.getType().getServerLabelOrDeviceUid(), encryptedPhoto);
                        break;
                    }
                    case ServerQuery.Type.GET_USER_DATA_QUERY_ID: {
                        serverMethod = new GetUserDataServerMethod(serverQuery.getType().getIdentity(), serverQuery.getType().getServerLabelOrDeviceUid(), fetchManagerSession.engineBaseDirectory);
                        break;
                    }
                    case ServerQuery.Type.CHECK_KEYCLOAK_REVOCATION_QUERY_ID: {
                        serverMethod = new CheckKeycloakRevocationServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getSignedContactDetails());
                        break;
                    }
                    case ServerQuery.Type.CREATE_GROUP_BLOB_QUERY_ID: {
                        byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, serverQuery.getOwnedIdentity());
                        if (serverSessionToken == null) {
                            cancel(RFC_INVALID_SERVER_SESSION);
                            return;
                        }
                        serverMethod = new CreateGroupBlobServerMethod(serverQuery.getOwnedIdentity(), serverSessionToken, serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getEncodedGroupAdminPublicKey(), serverQuery.getType().getEncryptedBlob());
                        break;
                    }
                    case ServerQuery.Type.GET_GROUP_BLOB_QUERY_ID: {
                        serverMethod = new GetGroupBlobServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getNonce());
                        break;
                    }
                    case ServerQuery.Type.LOCK_GROUP_BLOB_QUERY_ID: {
                        serverMethod = new LockGroupBlobServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getNonce(), serverQuery.getType().getQuerySignature());
                        break;
                    }
                    case ServerQuery.Type.UPDATE_GROUP_BLOB_QUERY_ID: {
                        serverMethod = new UpdateGroupBlobServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getNonce(), serverQuery.getType().getEncryptedBlob(), serverQuery.getType().getEncodedGroupAdminPublicKey(), serverQuery.getType().getQuerySignature());
                        break;
                    }
                    case ServerQuery.Type.PUT_GROUP_LOG_QUERY_ID: {
                        serverMethod = new PutGroupLogServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getQuerySignature());
                        break;
                    }
                    case ServerQuery.Type.DELETE_GROUP_BLOB_QUERY_ID: {
                        serverMethod = new DeleteGroupBlobServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getQuerySignature());
                        break;
                    }
                    case ServerQuery.Type.GET_KEYCLOAK_DATA_QUERY_ID: {
                        serverMethod = new GetKeycloakDataServerMethod(serverQuery.getType().getServer(), serverQuery.getType().getServerLabelOrDeviceUid(), fetchManagerSession.engineBaseDirectory);
                        break;
                    }
                    case ServerQuery.Type.OWNED_DEVICE_DISCOVERY_QUERY_ID: {
                        serverMethod = new OwnedDeviceDiscoveryServerMethod(serverQuery.getType().getIdentity());
                        break;
                    }
                    case ServerQuery.Type.DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID:
                    case ServerQuery.Type.DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                    case ServerQuery.Type.DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID: {
                        byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, serverQuery.getOwnedIdentity());
                        if (serverSessionToken == null) {
                            cancel(RFC_INVALID_SERVER_SESSION);
                            return;
                        }
                        serverMethod = new DeviceManagementServerMethod(serverQuery.getOwnedIdentity(), serverSessionToken, serverQuery.getType().getId(),  serverQuery.getType().getServerLabelOrDeviceUid(), serverQuery.getType().getEncryptedBlob());
                        break;
                    }
                    default:
                        cancel(RFC_BAD_ENCODED_SERVER_QUERY);
                        return;
                }
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, serverQuery.getOwnedIdentity()));
                Logger.d("?? Server query return status (after parse): " + returnStatus);

                switch (returnStatus) {
                    case ServerMethod.OK:
                        // some parseReceivedData methods change the actual returnStatus to OK --> this way the protocol can properly finish/abort
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
                    case ServerMethod.DEVICE_IS_NOT_REGISTERED: {
                        // if the device is not registered:
                        // - cancel if this is a remote device
                        // - retry later if this is our current device for a set nickname request
                        if (serverQuery.getType().getId() == ServerQuery.Type.DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID && serverQuery.getType().isCurrentDevice()) {
                            cancel(RFC_DEVICE_NOT_YET_REGISTERED);
                        } else {
                            cancel(RFC_DEVICE_DOES_NOT_EXIST);
                        }
                        return;
                    }
                    case ServerMethod.MALFORMED_URL:
                        cancel(RFC_MALFORMED_URL);
                        return;
                    default:
                        // check if the serverQuery has expired
                        if (System.currentTimeMillis() > pendingServerQuery.getCreationTimestamp() + Constants.SERVER_QUERY_EXPIRATION_DELAY) {
                            switch (serverQuery.getType().getId()) {
                                case ServerQuery.Type.DEVICE_DISCOVERY_QUERY_ID:
                                    serverResponse = Encoded.of(new Encoded[]{
                                            Encoded.of(Constants.BROADCAST_UID),
                                    }); // return the broadcast deviceUid so we know it's not a real output
                                    finished = true;
                                    return;
                                case ServerQuery.Type.OWNED_DEVICE_DISCOVERY_QUERY_ID:
                                    serverResponse = Encoded.of(new byte[0]); // return an empty byte array
                                    finished = true;
                                    return;
                                case ServerQuery.Type.PUT_USER_DATA_QUERY_ID:
                                case ServerQuery.Type.GET_GROUP_BLOB_QUERY_ID:
                                case ServerQuery.Type.PUT_GROUP_LOG_QUERY_ID:
                                case ServerQuery.Type.LOCK_GROUP_BLOB_QUERY_ID:
                                    serverResponse = null;
                                    finished = true;
                                    return;
                                case ServerQuery.Type.GET_USER_DATA_QUERY_ID:
                                case ServerQuery.Type.GET_KEYCLOAK_DATA_QUERY_ID:
                                    serverResponse = Encoded.of(""); // as if it was deleted from the server
                                    finished = true;
                                    return;
                                case ServerQuery.Type.CHECK_KEYCLOAK_REVOCATION_QUERY_ID:
                                    serverResponse = Encoded.of(true); // consider the user is not revoked (rationale: another protocol has probably been run since then, we do not want to delete the user)
                                    finished = true;
                                    return;
                                case ServerQuery.Type.CREATE_GROUP_BLOB_QUERY_ID:
                                case ServerQuery.Type.UPDATE_GROUP_BLOB_QUERY_ID:
                                case ServerQuery.Type.DELETE_GROUP_BLOB_QUERY_ID:
                                    serverResponse = Encoded.of(false); // consider the query failed
                                    finished = true;
                                    return;
                            }
                        }
                        cancel(RFC_NETWORK_ERROR);
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

    @Override
    public void doCancel() {
        // Nothings special to do on cancel
    }
}

abstract class ServerQueryServerMethod extends ServerMethod {
    public abstract Encoded getServerResponse();

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        Logger.d("?? Server query return status (before parse): " + returnStatus);
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
        super.parseReceivedData(receivedData);
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

class OwnedDeviceDiscoveryServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/ownedDeviceDiscovery";

    private final String server;
    private final Identity identity;

    private Encoded serverResponse;

    public OwnedDeviceDiscoveryServerMethod(Identity identity) {
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
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            try {
                // check that decoding works properly
                receivedData[0].decodeEncryptedData();
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
        super.parseReceivedData(receivedData);
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
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            try {
                // write the result to a file
                EncryptedBytes encryptedData = receivedData[0].decodeEncryptedData();
                // Ugly hack: the filename contains a timestamp after which the file is considered "orphan" and can be deleted
                String userDataPath = Constants.DOWNLOADED_USER_DATA_DIRECTORY + File.separator + (System.currentTimeMillis() + Constants.GET_USER_DATA_LOCAL_FILE_LIFESPAN) + "." + Logger.toHexString(serverLabel.getBytes()) + "-" + Logger.getUuidString(UUID.randomUUID());
                try (FileOutputStream fis = new FileOutputStream(new File(engineBaseDirectory, userDataPath))) {
                    fis.write(encryptedData.getBytes());
                }
                serverResponse = Encoded.of(userDataPath);
            } catch (DecodingException | IOException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER) {
            returnStatus = ServerMethod.OK;
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
        super.parseReceivedData(receivedData);
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

class CreateGroupBlobServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupBlobCreate";

    private final Identity ownedIdentity;
    private final byte[] token;
    private final String server;
    private final UID groupUid;
    private final Encoded encodedGroupAdminPublicKey;
    private final EncryptedBytes encryptedBlob;

    private Encoded serverResponse;

    public CreateGroupBlobServerMethod(Identity ownedIdentity, byte[] token, String server, UID groupUid, Encoded encodedGroupAdminPublicKey, EncryptedBytes encryptedBlob) {
        this.ownedIdentity = ownedIdentity;
        this.token = token;
        this.server = server;
        this.groupUid = groupUid;
        this.encodedGroupAdminPublicKey = encodedGroupAdminPublicKey;
        this.encryptedBlob = encryptedBlob;
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
                Encoded.of(groupUid),
                encodedGroupAdminPublicKey,
                Encoded.of(encryptedBlob),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            serverResponse = Encoded.of(0); // success
        } else if (returnStatus == ServerMethod.GROUP_UID_ALREADY_USED) {
            returnStatus = ServerMethod.OK;
            serverResponse = Encoded.of(2); // definitive fail
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class GetGroupBlobServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupBlobGet";

    private final String server;
    private final UID groupUid;
    private final byte[] nonce;

    private Encoded serverResponse;

    public GetGroupBlobServerMethod(String server, UID groupUid, byte[] nonce) {
        this.server = server;
        this.groupUid = groupUid;
        this.nonce = nonce;
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
                Encoded.of(groupUid),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            try {
                if (receivedData.length != 3) {
                    throw new DecodingException();
                }
                //noinspection unused
                EncryptedBytes encryptedBlob = receivedData[0].decodeEncryptedData();
                Encoded[] encodedLogItems = receivedData[1].decodeList();
                for (Encoded encodedLogItem : encodedLogItems) {
                    encodedLogItem.decodeBytes();
                }
                //noinspection unused
                ServerAuthenticationPublicKey groupAdminPublicKey = (ServerAuthenticationPublicKey) receivedData[2].decodePublicKey();
                serverResponse = Encoded.of(new Encoded[]{
                        receivedData[0],
                        receivedData[1],
                        receivedData[2],
                        Encoded.of(nonce),
                });
            } catch (Exception e) {
                e.printStackTrace();
                returnStatus = ServerMethod.MALFORMED_SERVER_RESPONSE;
            }
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER) {
            // if the blob is not found on the server, behave as a success to let the protocol know the blob was deleted
            returnStatus = ServerMethod.OK;
            serverResponse = Encoded.of(new Encoded[]{
                    Encoded.of(true),
            });
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class LockGroupBlobServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupBlobLock";

    private final String server;
    private final UID groupUid;
    private final byte[] lockNonce;
    private final byte[] signature;

    private Encoded serverResponse;

    public LockGroupBlobServerMethod(String server, UID groupUid, byte[] lockNonce, byte[] signature) {
        this.server = server;
        this.groupUid = groupUid;
        this.lockNonce = lockNonce;
        this.signature = signature;
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
                Encoded.of(groupUid),
                Encoded.of(lockNonce),
                Encoded.of(signature),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            try {
                if (receivedData.length != 3) {
                    throw new DecodingException();
                }
                //noinspection unused
                EncryptedBytes encryptedBlob = receivedData[0].decodeEncryptedData();
                Encoded[] encodedLogItems = receivedData[1].decodeList();
                for (Encoded encodedLogItem : encodedLogItems) {
                    encodedLogItem.decodeBytes();
                }
                //noinspection unused
                ServerAuthenticationPublicKey groupAdminPublicKey = (ServerAuthenticationPublicKey) receivedData[2].decodePublicKey();
                serverResponse = Encoded.of(new Encoded[]{
                        receivedData[0],
                        receivedData[1],
                        receivedData[2],
                        Encoded.of(lockNonce),
                });
            } catch (Exception e) {
                e.printStackTrace();
                returnStatus = ServerMethod.MALFORMED_SERVER_RESPONSE;
            }
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER
                || returnStatus == ServerMethod.INVALID_SIGNATURE) {
            // if the blob is not found on the server, or the signature is invalid, behave as a success to let the protocol properly abort
            returnStatus = ServerMethod.OK;
            serverResponse = null;
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class UpdateGroupBlobServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupBlobUpdate";

    private final String server;
    private final UID groupUid;
    private final byte[] lockNonce;
    private final EncryptedBytes encryptedBlob;
    private final Encoded encodedGroupAdminPublicKey;
    private final byte[] signature;

    private Encoded serverResponse;

    public UpdateGroupBlobServerMethod(String server, UID groupUid, byte[] lockNonce, EncryptedBytes encryptedBlob, Encoded encodedGroupAdminPublicKey, byte[] signature) {
        this.server = server;
        this.groupUid = groupUid;
        this.lockNonce = lockNonce;
        this.encryptedBlob = encryptedBlob;
        this.encodedGroupAdminPublicKey = encodedGroupAdminPublicKey;
        this.signature = signature;
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
                Encoded.of(groupUid),
                Encoded.of(lockNonce),
                Encoded.of(encryptedBlob),
                encodedGroupAdminPublicKey,
                Encoded.of(signature),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            serverResponse = Encoded.of(0); // success
        } else if (returnStatus == ServerMethod.GROUP_NOT_LOCKED) {
            returnStatus = ServerMethod.OK;
            serverResponse = Encoded.of(1); // retry-able fail
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER
                || returnStatus == ServerMethod.INVALID_SIGNATURE) {
            returnStatus = ServerMethod.OK;
            serverResponse = Encoded.of(2); // definitive fail
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class PutGroupLogServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupLogPut";

    private final String server;
    private final UID groupUid;
    private final byte[] signature;

    public PutGroupLogServerMethod(String server, UID groupUid, byte[] signature) {
        this.server = server;
        this.groupUid = groupUid;
        this.signature = signature;
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        // this server query is also called when an owned identity is deleted with contact notification --> it should not require an active identity
        return false;
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
                Encoded.of(groupUid),
                Encoded.of(signature),
        }).getBytes();
    }

    @Override
    public Encoded getServerResponse() {
        return null;
    }
}


class DeleteGroupBlobServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/groupBlobDelete";

    private final String server;
    private final UID groupUid;
    private final byte[] signature;

    private Encoded serverResponse;

    public DeleteGroupBlobServerMethod(String server, UID groupUid, byte[] signature) {
        this.server = server;
        this.groupUid = groupUid;
        this.signature = signature;
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        // this server query is also called when an owned identity is deleted with contact notification --> it should not require an active identity
        return false;
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
                Encoded.of(groupUid),
                Encoded.of(signature),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        serverResponse = Encoded.of(returnStatus == ServerMethod.OK);

        if (returnStatus == ServerMethod.INVALID_SIGNATURE) {
            // if the signature is invalid, still mark the query as successful
            returnStatus = ServerMethod.OK;
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class GetKeycloakDataServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "olvid-rest/getData";

    private final String server;
    private final String path;
    private final UID serverLabel;
    private final String engineBaseDirectory;
    private Encoded serverResponse;

    public GetKeycloakDataServerMethod(String keycloakServerUrl, UID serverLabel, String engineBaseDirectory) {
        this.serverLabel = serverLabel;

        String url = keycloakServerUrl + SERVER_METHOD_PATH;
        int pos = url.indexOf('/', 8);
        this.server = url.substring(0, pos);
        this.path = url.substring(pos);
        this.engineBaseDirectory = engineBaseDirectory;
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
        return serverLabel.getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
        if (returnStatus == ServerMethod.OK) {
            try {
                // write the result to a file
                EncryptedBytes encryptedData = receivedData[0].decodeEncryptedData();
                // Ugly hack: the filename contains a timestamp after which the file is considered "orphan" and can be deleted
                String userDataPath = Constants.DOWNLOADED_USER_DATA_DIRECTORY + File.separator + (System.currentTimeMillis() + Constants.GET_USER_DATA_LOCAL_FILE_LIFESPAN) + "." + Logger.toHexString(serverLabel.getBytes()) + "-" + Logger.getUuidString(UUID.randomUUID());
                try (FileOutputStream fis = new FileOutputStream(new File(engineBaseDirectory, userDataPath))) {
                    fis.write(encryptedData.getBytes());
                }
                serverResponse = Encoded.of(userDataPath);
            } catch (DecodingException | IOException e) {
                e.printStackTrace();
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        } else if (returnStatus == ServerMethod.DELETED_FROM_SERVER) {
            returnStatus = ServerMethod.OK;
            serverResponse = Encoded.of("");
        }
    }

    @Override
    public Encoded getServerResponse() {
        return serverResponse;
    }
}

class DeviceManagementServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/deviceManagement";

    private final String server;
    private final Identity identity;
    private final byte[] token;
    private final int queryType;
    private final UID deviceUid;
    private final EncryptedBytes encryptedDeviceName;

    private Encoded serverResponse;

    public DeviceManagementServerMethod(Identity identity, byte[] token, int queryType, UID deviceUid, EncryptedBytes encryptedDeviceName) {
        this.server = identity.getServer();
        this.identity = identity;
        this.token = token;
        this.queryType = queryType;
        this.deviceUid = deviceUid;
        this.encryptedDeviceName = encryptedDeviceName;
    }

    public UID getDeviceUid() {
        return deviceUid;
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
        switch (queryType) {
            case ServerQuery.Type.DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(identity),
                        Encoded.of(token),
                        Encoded.of(new byte[]{(byte) 0x00}),
                        Encoded.of(deviceUid),
                        Encoded.of(encryptedDeviceName),
                }).getBytes();
            }
            case ServerQuery.Type.DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(identity),
                        Encoded.of(token),
                        Encoded.of(new byte[]{(byte) 0x01}),
                        Encoded.of(deviceUid),
                }).getBytes();
            }
            case ServerQuery.Type.DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(identity),
                        Encoded.of(token),
                        Encoded.of(new byte[]{(byte) 0x02}),
                        Encoded.of(deviceUid),
                }).getBytes();
            }
            default: {
                // invalid query type
                return new byte[0];
            }
        }
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
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


class RegisterApiKeyServerMethod extends ServerQueryServerMethod {
    private static final String SERVER_METHOD_PATH = "/registerApiKey";

    private final String server;
    private final Identity ownedIdentity;
    private final byte[] token;
    private  final String apiKeyString;

    public RegisterApiKeyServerMethod(Identity ownedIdentity, byte[] token, String apiKeyString) {
        this.server = ownedIdentity.getServer();
        this.ownedIdentity = ownedIdentity;
        this.token = token;
        this.apiKeyString = apiKeyString;
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
                Encoded.of(apiKeyString),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        super.parseReceivedData(receivedData);
    }

    @Override
    public Encoded getServerResponse() {
        return null;
    }
}
