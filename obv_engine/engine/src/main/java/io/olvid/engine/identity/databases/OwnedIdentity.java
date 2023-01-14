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

package io.olvid.engine.identity.databases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.PrivateIdentity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;


@SuppressWarnings("FieldMayBeFinal")
public class OwnedIdentity implements ObvDatabase {
    static final String TABLE_NAME = "owned_identity";

    private final IdentityManagerSession identityManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "identity";
    private PrivateIdentity privateIdentity;
    static final String PRIVATE_IDENTITY = "private_identity";
    private int publishedDetailsVersion;
    static final String PUBLISHED_DETAILS_VERSION = "published_details_version";
    private int latestDetailsVersion;
    static final String LATEST_DETAILS_VERSION = "latest_details_version";
    private UUID apiKey;
    static final String API_KEY = "api_key";
    private boolean active;
    static final String ACTIVE = "active";
    private String keycloakServerUrl;
    static final String KEYCLOAK_SERVER_URL = "keycloak_server_url";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public PrivateIdentity getPrivateIdentity() {
        return privateIdentity;
    }

    public UUID getApiKey() {
        return apiKey;
    }

    public int getPublishedDetailsVersion() {
        return publishedDetailsVersion;
    }

    public int getLatestDetailsVersion() {
        return latestDetailsVersion;
    }

    public boolean isActive() {
        return active;
    }

    public String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }

    public boolean isKeycloakManaged() {
        return keycloakServerUrl != null;
    }

    // region computed properties

    public ContactIdentity[] getContactIdentities() {
        return ContactIdentity.getAll(identityManagerSession, ownedIdentity);
    }

    public UID[] getOtherDeviceUids() throws SQLException {
        OwnedDevice[] ownedDevices = OwnedDevice.getOtherDevicesOfOwnedIdentity(identityManagerSession, ownedIdentity);
        UID[] uids = new UID[ownedDevices.length];
        for (int i=0; i<ownedDevices.length; i++) {
            uids[i] = ownedDevices[i].getUid();
        }
        return uids;
    }

    public UID[] getAllDeviceUids() throws SQLException {
        OwnedDevice[] ownedDevices = OwnedDevice.getAllDevicesOfIdentity(identityManagerSession, ownedIdentity);
        UID[] uids = new UID[ownedDevices.length];
        for (int i=0; i<ownedDevices.length; i++) {
            uids[i] = ownedDevices[i].getUid();
        }
        return uids;
    }

    public UID getCurrentDeviceUid() throws SQLException {
        return OwnedDevice.getCurrentDeviceOfOwnedIdentity(identityManagerSession, ownedIdentity).getUid();
    }

    public OwnedIdentityDetails getPublishedDetails() throws SQLException {
        return OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, publishedDetailsVersion);
    }

    public OwnedIdentityDetails getLatestDetails() throws SQLException {
        return OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, latestDetailsVersion);
    }

    public KeycloakServer getKeycloakServer() throws SQLException {
        if (keycloakServerUrl != null) {
            return KeycloakServer.get(identityManagerSession, keycloakServerUrl, ownedIdentity);
        }
        return null;
    }



    public ObvKeycloakState getKeycloakState() throws SQLException {
        if (keycloakServerUrl != null) {
            KeycloakServer keycloakServer = KeycloakServer.get(identityManagerSession, keycloakServerUrl, ownedIdentity);
            if (keycloakServer != null) {
                try {
                    JsonWebKeySet jwks = keycloakServer.getJwks();
                    JsonWebKey signatureKey = keycloakServer.getSignatureKey();
                    return new ObvKeycloakState(keycloakServer.getServerUrl(), keycloakServer.getClientId(), keycloakServer.getClientSecret(), jwks, signatureKey, keycloakServer.getSerializedAuthState(), keycloakServer.getLatestRevocationListTimestamp());
                } catch (Exception e) {
                    return new ObvKeycloakState(keycloakServer.getServerUrl(), keycloakServer.getClientId(), keycloakServer.getClientSecret(), null, null, keycloakServer.getSerializedAuthState(), keycloakServer.getLatestRevocationListTimestamp());
                }
            }
        }
        return null;
    }

    public JsonWebKey getKeycloakSignatureKey() throws SQLException {
        if (keycloakServerUrl != null) {
            KeycloakServer keycloakServer = KeycloakServer.get(identityManagerSession, keycloakServerUrl, ownedIdentity);
            if (keycloakServer != null) {
                try {
                    return keycloakServer.getSignatureKey();
                } catch (Exception e) {
                    // nothing
                }
            }
        }
        return null;
    }


    public String getKeycloakUserId() throws SQLException {
        if (keycloakServerUrl != null) {
            KeycloakServer keycloakServer = KeycloakServer.get(identityManagerSession, keycloakServerUrl, ownedIdentity);
            if (keycloakServer != null) {
                return keycloakServer.getKeycloakUserId();
            }
        }
        return null;
    }


    // endregion




    // region setters

    public void setLatestDetails(JsonIdentityDetails identityDetails) throws Exception {
        if (identityDetails == null || identityDetails.isEmpty()) {
            return;
        }

        // We no longer check if details changed: if we were instructed to update the details, we do so
        // checks that more than the signature has changed were already made
        if (publishedDetailsVersion != latestDetailsVersion) {
            JsonIdentityDetails publishedDetails = getPublishedDetails().getJsonIdentityDetails();
            if (publishedDetails.equals(identityDetails)) {
                // changes were reverted --> we discard
                discardLatestDetails();
                return;
            }
        }
        // we indeed have a proper update to save
        OwnedIdentityDetails ownedIdentityDetails;
        if (publishedDetailsVersion == latestDetailsVersion) {
            ownedIdentityDetails = OwnedIdentityDetails.copy(identityManagerSession, ownedIdentity, publishedDetailsVersion);
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                    LATEST_DETAILS_VERSION + " = ? " +
                    " WHERE " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, ownedIdentityDetails.getVersion());
                statement.setBytes(2, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.latestDetailsVersion = ownedIdentityDetails.getVersion();
                commitHookBits |= HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED;
                identityManagerSession.session.addSessionCommitListener(this);
            }
        } else {
            ownedIdentityDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, latestDetailsVersion);
        }
        ownedIdentityDetails.setJsonDetails(identityDetails);
    }

    public void setPhoto(String srcAbsolutePhotoUrl) throws Exception {
        if (srcAbsolutePhotoUrl == null) {
            // we were requested to remove a photo, just create a new details without it
            OwnedIdentityDetails ownedIdentityDetails;
            if (publishedDetailsVersion == latestDetailsVersion) {
                ownedIdentityDetails = OwnedIdentityDetails.copy(identityManagerSession, ownedIdentity, publishedDetailsVersion);
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                        LATEST_DETAILS_VERSION + " = ? " +
                        " WHERE " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, ownedIdentityDetails.getVersion());
                    statement.setBytes(2, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.latestDetailsVersion = ownedIdentityDetails.getVersion();
                    commitHookBits |= HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED;
                    identityManagerSession.session.addSessionCommitListener(this);
                }
            } else {
                ownedIdentityDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, latestDetailsVersion);
            }
            ownedIdentityDetails.setPhotoUrl(null, true);
        } else {
            File srcPhotoFile = new File(srcAbsolutePhotoUrl);
            if (!srcPhotoFile.canRead()) {
                return;
            }
            OwnedIdentityDetails ownedIdentityDetails;
            if (publishedDetailsVersion == latestDetailsVersion) {
                ownedIdentityDetails = OwnedIdentityDetails.copy(identityManagerSession, ownedIdentity, publishedDetailsVersion);
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                        LATEST_DETAILS_VERSION + " = ? " +
                        " WHERE " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, ownedIdentityDetails.getVersion());
                    statement.setBytes(2, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.latestDetailsVersion = ownedIdentityDetails.getVersion();
                    commitHookBits |= HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED;
                    identityManagerSession.session.addSessionCommitListener(this);
                }
            } else {
                ownedIdentityDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, latestDetailsVersion);
            }
            // find a non-existing fileName
            String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator + Logger.toHexString(Arrays.copyOfRange(ownedIdentity.getBytes(), ownedIdentity.getBytes().length - 32, ownedIdentity.getBytes().length));
            String randFileName;
            Random random = new Random();
            File dstPhotoFile;
            do {
                randFileName = fileName + "_" + random.nextInt(65536);
                dstPhotoFile = new File(identityManagerSession.engineBaseDirectory, randFileName);
            } while (dstPhotoFile.exists());

            // copy the file
            try (InputStream is = new FileInputStream(srcPhotoFile)) {
                try (OutputStream os = new FileOutputStream(dstPhotoFile)) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                }
            }

            // update the details
            ownedIdentityDetails.setPhotoUrl(randFileName, true);
        }
    }


    public void setDetailsDownloadedPhotoUrl(int version, byte[] photo) throws Exception {
        OwnedIdentityDetails ownedIdentityDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, version);

        if (ownedIdentityDetails == null) {
            return;
        }

        // find a non-existing fileName
        String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator + Logger.toHexString(Arrays.copyOfRange(ownedIdentity.getBytes(), ownedIdentity.getBytes().length - 32, ownedIdentity.getBytes().length));
        String randFileName;
        Random random = new Random();
        File dstPhotoFile;
        do {
            randFileName = fileName + "_" + random.nextInt(65536);
            dstPhotoFile = new File(identityManagerSession.engineBaseDirectory, randFileName);
        } while (dstPhotoFile.exists());

        // copy the file
        try (OutputStream os = new FileOutputStream(dstPhotoFile)){
            os.write(photo, 0, photo.length);
        }

        // update the details
        ownedIdentityDetails.setPhotoUrl(randFileName, false);

        hookDetails = ownedIdentityDetails.getJsonIdentityDetailsWithVersionAndPhoto();
        commitHookBits |= HOOK_BIT_IDENTITY_DETAILS_PUBLISHED;
        identityManagerSession.session.addSessionCommitListener(this);
    }


    public void setPhotoLabelAndKey(int version, UID photoServerLabel, AuthEncKey photoServerKey) throws SQLException {
        OwnedIdentityDetails ownedIdentityDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity, version);
        if (ownedIdentityDetails != null) {
            ownedIdentityDetails.setPhotoServerLabelAndKey(photoServerLabel, photoServerKey);
        }
    }

    public int publishLatestDetails() throws SQLException {
        if (latestDetailsVersion == publishedDetailsVersion) {
            return -1;
        }
        OwnedIdentityDetails publishedDetails = getPublishedDetails();
        OwnedIdentityDetails latestDetails = getLatestDetails();
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                PUBLISHED_DETAILS_VERSION + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, latestDetailsVersion);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.publishedDetailsVersion = latestDetailsVersion;
            commitHookBits |= HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        if (publishedDetails.getPhotoUrl() != null && (latestDetails.getPhotoUrl() == null || !latestDetails.getPhotoUrl().equals(publishedDetails.getPhotoUrl()))) {
            if (publishedDetails.getPhotoServerLabel() != null) {
                labelToDelete = publishedDetails.getPhotoServerLabel();
                commitHookBits |= HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED;
            }
        }
        hookDetails = latestDetails.getJsonIdentityDetailsWithVersionAndPhoto();
        commitHookBits |= HOOK_BIT_IDENTITY_DETAILS_PUBLISHED;
        identityManagerSession.session.addSessionCommitListener(this);
        return latestDetailsVersion;
    }

    public void discardLatestDetails() throws SQLException {
        if (latestDetailsVersion == publishedDetailsVersion) {
            return;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                LATEST_DETAILS_VERSION + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, publishedDetailsVersion);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.latestDetailsVersion = publishedDetailsVersion;
            commitHookBits |= HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setApiKey(UUID apiKey) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + API_KEY + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, Logger.getUuidString(apiKey));
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.apiKey = apiKey;
        }
    }

    public void setActive(boolean active) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + ACTIVE + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, active);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.active = active;
            commitHookBits |= HOOK_BIT_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setKeycloakServerUrl(String keycloakServerUrl) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + KEYCLOAK_SERVER_URL + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, keycloakServerUrl);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.keycloakServerUrl = keycloakServerUrl;
            commitHookBits |= HOOK_BIT_IDENTITY_LIST_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    // endregion

    // region constructors

    public static OwnedIdentity create(IdentityManagerSession identityManagerSession, String server, Byte serverAuthenticationAlgoImplByte, Byte encryptionAlgoImplByte, JsonIdentityDetails identityDetails, UUID apiKey, PRNGService prng) {
        if (identityDetails == null || identityDetails.isEmpty() || apiKey == null) {
            return null;
        }
        KeyPair serverAuthKeyPair = Suite.generateServerAuthenticationKeyPair(serverAuthenticationAlgoImplByte, prng);
        KeyPair encryptionKeyPair = Suite.generateEncryptionKeyPair(encryptionAlgoImplByte, prng);
        if (serverAuthKeyPair == null || encryptionKeyPair == null) {
            return null;
        }
        MACKey macKey = Suite.getDefaultMAC(0).generateKey(prng);
        try {
            Identity identity = new Identity(server, (ServerAuthenticationPublicKey) serverAuthKeyPair.getPublicKey(), (EncryptionPublicKey) encryptionKeyPair.getPublicKey());
            PrivateIdentity privateIdentity = new PrivateIdentity(identity, (ServerAuthenticationPrivateKey) serverAuthKeyPair.getPrivateKey(), (EncryptionPrivateKey) encryptionKeyPair.getPrivateKey(), macKey);
            OwnedIdentityDetails ownedIdentityDetails = OwnedIdentityDetails.create(identityManagerSession, identity, identityManagerSession.jsonObjectMapper.writeValueAsString(identityDetails));
            OwnedIdentity ownedIdentity = new OwnedIdentity(identityManagerSession, privateIdentity, ownedIdentityDetails.getVersion(), apiKey);
            ownedIdentity.insert();
            OwnedDevice.createCurrentDevice(identityManagerSession, identity, prng);
            return ownedIdentity;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private OwnedIdentity(IdentityManagerSession identityManagerSession, PrivateIdentity privateIdentity, int detailsVersion, UUID apiKey) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = privateIdentity.getPublicIdentity();
        this.privateIdentity = privateIdentity;
        this.publishedDetailsVersion = detailsVersion;
        this.latestDetailsVersion = detailsVersion;
        this.apiKey = apiKey;
        this.active = true;
        this.keycloakServerUrl = null;
    }

    private OwnedIdentity(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.privateIdentity = PrivateIdentity.deserialize(res.getBytes(PRIVATE_IDENTITY));
        this.publishedDetailsVersion = res.getInt(PUBLISHED_DETAILS_VERSION);
        this.latestDetailsVersion = res.getInt(LATEST_DETAILS_VERSION);
        try {
            this.apiKey = UUID.fromString(res.getString(API_KEY));
        } catch (Exception e) {
            this.apiKey = null;
        }
        this.active = res.getBoolean(ACTIVE);
        this.keycloakServerUrl = res.getString(KEYCLOAK_SERVER_URL);
    }


    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB PRIMARY KEY, " +
                    PRIVATE_IDENTITY + " BLOB NOT NULL, " +
                    PUBLISHED_DETAILS_VERSION + " INT NOT NULL, " +
                    LATEST_DETAILS_VERSION + " INT NOT NULL, " +
                    API_KEY + " VARCHAR NOT NULL, " +
                    ACTIVE + " BIT NOT NULL, " +
                    KEYCLOAK_SERVER_URL + " TEXT, " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + PUBLISHED_DETAILS_VERSION + ") REFERENCES " + OwnedIdentityDetails.TABLE_NAME + "(" + OwnedIdentityDetails.OWNED_IDENTITY + ", " + OwnedIdentityDetails.VERSION + ")," +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + LATEST_DETAILS_VERSION + ") REFERENCES " + OwnedIdentityDetails.TABLE_NAME + "(" + OwnedIdentityDetails.OWNED_IDENTITY + ", " + OwnedIdentityDetails.VERSION + ")," +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + KEYCLOAK_SERVER_URL + ") REFERENCES " + KeycloakServer.TABLE_NAME + "(" + KeycloakServer.OWNED_IDENTITY + ", " + KeycloakServer.SERVER_URL + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 5 && newVersion >= 5) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE owned_identity RENAME TO old_owned_identities");
                statement.execute("CREATE TABLE IF NOT EXISTS owned_identity_details (" +
                        " owned_identity BLOB NOT NULL, " +
                        " version INT NOT NULL, " +
                        " serialized_json_details TEXT NOT NULL, " +
                        " photo_url TEXT, " +
                        " photo_server_label BLOB, " +
                        " photo_server_key BLOB, " +
                        " CONSTRAINT PK_owned_identity_details PRIMARY KEY(owned_identity, version));");
                statement.execute("CREATE TABLE IF NOT EXISTS owned_identity (" +
                        " identity BLOB PRIMARY KEY, " +
                        " private_identity BLOB NOT NULL, " +
                        " published_details_version INT NOT NULL, " +
                        " latest_details_version INT NOT NULL, " +
                        " single_use BIT NOT NULL, " +
                        " api_key VARCHAR NOT NULL, " +
                        " FOREIGN KEY (identity, published_details_version) REFERENCES owned_identity_details(owned_identity, version)," +
                        " FOREIGN KEY (identity, latest_details_version) REFERENCES owned_identity_details(owned_identity, version));");
                ObjectMapper objectMapper = new ObjectMapper();
                try (ResultSet res = statement.executeQuery("SELECT * FROM old_owned_identities")) {
                    while (res.next()) {
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO owned_identity VALUES (?,?,?,?,?, ?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setInt(3, 1);
                            preparedStatement.setInt(4, 1);
                            preparedStatement.setBoolean(5, res.getBoolean(4));
                            preparedStatement.setString(6, res.getString(5));
                            preparedStatement.executeUpdate();
                        }
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO owned_identity_details VALUES (?,?,?,?,?, ?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setInt(2, 1);
                            HashMap<String, String> map = new HashMap<>();
                            map.put("first_name", res.getString(3));
                            try {
                                preparedStatement.setString(3, objectMapper.writeValueAsString(map));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // skip the contact
                                continue;
                            }
                            preparedStatement.setString(4, null);
                            preparedStatement.setBytes(5, null);
                            preparedStatement.setBytes(6, null);
                            preparedStatement.executeUpdate();
                        }
                    }
                }
                statement.execute("DROP TABLE old_owned_identities");
            }
            oldVersion = 5;
        }
        if (oldVersion < 15 && newVersion >= 15) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `owned_identity` DATABASE FROM VERSION " + oldVersion + " TO 15");
                statement.execute("ALTER TABLE owned_identity RENAME TO old_owned_identity");
                statement.execute("CREATE TABLE IF NOT EXISTS owned_identity (" +
                        " identity BLOB PRIMARY KEY, " +
                        " private_identity BLOB NOT NULL, " +
                        " published_details_version INT NOT NULL, " +
                        " latest_details_version INT NOT NULL, " +
                        " api_key VARCHAR NOT NULL, " +
                        " active BIT NOT NULL, " +
                        " FOREIGN KEY (identity, published_details_version) REFERENCES owned_identity_details(owned_identity, version)," +
                        " FOREIGN KEY (identity, latest_details_version) REFERENCES owned_identity_details(owned_identity, version));");
                statement.execute("INSERT INTO owned_identity SELECT identity, private_identity, published_details_version, latest_details_version, api_key, 1 FROM old_owned_identity");
                statement.execute("DROP TABLE old_owned_identity");
            }
            oldVersion = 15;
        }
        if (oldVersion < 20 && newVersion >= 20) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `owned_identity` DATABASE FROM VERSION " + oldVersion + " TO 20");
                statement.execute("ALTER TABLE owned_identity RENAME TO old_owned_identity");
                statement.execute("CREATE TABLE IF NOT EXISTS owned_identity (" +
                        " identity BLOB PRIMARY KEY, " +
                        " private_identity BLOB NOT NULL, " +
                        " published_details_version INT NOT NULL, " +
                        " latest_details_version INT NOT NULL, " +
                        " api_key VARCHAR NOT NULL, " +
                        " active BIT NOT NULL, " +
                        " keycloak_server_url TEXT, " +
                        " FOREIGN KEY (identity, published_details_version) REFERENCES owned_identity_details(owned_identity, version)," +
                        " FOREIGN KEY (identity, latest_details_version) REFERENCES owned_identity_details(owned_identity, version)," +
                        " FOREIGN KEY (identity, keycloak_server_url) REFERENCES keycloak_server(owned_identity, server_url) ON DELETE SET NULL);");
                statement.execute("INSERT INTO owned_identity SELECT identity, private_identity, published_details_version, latest_details_version, api_key, active, NULL FROM old_owned_identity");
                statement.execute("DROP TABLE old_owned_identity");
            }
            oldVersion = 20;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, privateIdentity.serialize());
            statement.setInt(3, publishedDetailsVersion);
            statement.setInt(4, latestDetailsVersion);
            statement.setString(5, Logger.getUuidString(apiKey));
            statement.setBoolean(6, active);
            statement.setString(7, keycloakServerUrl);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_IDENTITY_LIST_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Running OwnedIdentity delete outside a transaction");
            throw new SQLException();
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_IDENTITY_LIST_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + OwnedIdentityDetails.TABLE_NAME +
                " WHERE " + OwnedIdentityDetails.OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static OwnedIdentity get(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        if ((ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedIdentity(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static boolean isActive(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        if ((ownedIdentity == null)) {
            return false;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " + ACTIVE + " FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getBoolean(ACTIVE);
                } else {
                    return false;
                }
            }
        }
    }

    public static OwnedIdentity[] getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedIdentity(identityManagerSession, res));
                }
                return list.toArray(new OwnedIdentity[0]);
            }
        }
    }


    public static OwnedIdentity[] getAllActive(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + ACTIVE + " = 1;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedIdentity(identityManagerSession, res));
                }
                return list.toArray(new OwnedIdentity[0]);
            }
        }
    }

    public static String getSerializedPublishedDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT details." + OwnedIdentityDetails.SERIALIZED_JSON_DETAILS +
                        " FROM " + TABLE_NAME + " AS identity " +
                        " INNER JOIN " + OwnedIdentityDetails.TABLE_NAME + " AS details " +
                        " ON identity." + OWNED_IDENTITY + " = details." + OwnedIdentityDetails.OWNED_IDENTITY +
                        " AND identity." + PUBLISHED_DETAILS_VERSION + " = details." + OwnedIdentityDetails.VERSION +
                        " WHERE identity." + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getString(1);
                }
                return null;
            }
        } catch (SQLException e) {
            return null;
        }
    }


    // endregion

    // region hooks

    private UID labelToDelete;
    private JsonIdentityDetailsWithVersionAndPhoto hookDetails;

    private long commitHookBits = 0;
    private static final long HOOK_BIT_IDENTITY_LIST_CHANGED = 0x1;
    private static final long HOOK_BIT_IDENTITY_DETAILS_PUBLISHED = 0x2;
    private static final long HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED = 0x4;
    private static final long HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED = 0x8;
    private static final long HOOK_BIT_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS = 0x10;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_IDENTITY_LIST_CHANGED) != 0) {
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED, new HashMap<>());
        }
        if ((commitHookBits & HOOK_BIT_IDENTITY_DETAILS_PUBLISHED) != 0) {
            // trigger an update of the App ownedIdentity
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_IDENTITY_DETAILS_KEY, hookDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_LABEL_KEY, labelToDelete);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_LATEST_IDENTITY_DETAILS_VERSION_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY, latestDetailsVersion != publishedDetailsVersion);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY, active);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, userInfo);
        }
        commitHookBits = 0;
    }

    // endregion

    // region backup

    public static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession) throws SQLException {
        OwnedIdentity[] ownedIdentities = getAll(identityManagerSession);
        Pojo_0[] pojos = new Pojo_0[ownedIdentities.length];
        for (int i=0; i<ownedIdentities.length; i++) {
            pojos[i] = ownedIdentities[i].backup();
        }
        return pojos;
    }

    private Pojo_0 backup() throws SQLException {
        Pojo_0 pojo = new Pojo_0();
        pojo.owned_identity = ownedIdentity.getBytes();
        pojo.private_identity = backupPrivateIdentity();
        pojo.published_details = getPublishedDetails().backup();
        if (latestDetailsVersion != publishedDetailsVersion) {
            pojo.latest_details = getLatestDetails().backup();
        }
        pojo.api_key = Logger.getUuidString(apiKey);
        pojo.active = active;
        if (keycloakServerUrl != null) {
            pojo.keycloak = getKeycloakServer().backup();
        }
        pojo.contact_identities = ContactIdentity.backupAll(identityManagerSession, ownedIdentity);
        pojo.owned_groups = ContactGroup.backupAllForOwner(identityManagerSession, ownedIdentity, ownedIdentity);
        pojo.groups_v2 = ContactGroupV2.backupAll(identityManagerSession, ownedIdentity);
        return pojo;
    }

    public static ObvIdentity restore(IdentityManagerSession identityManagerSession, Pojo_0 pojo, PRNGService prng) throws SQLException {
        Identity ownedIdentity = null;
        try {
            ownedIdentity = Identity.of(pojo.owned_identity);
        } catch (DecodingException e) {
            Logger.e("Error recreating OwnedIdentity from backup!");
            e.printStackTrace();
        }
        if (ownedIdentity == null) {
            return null;
        }
        PrivateIdentity privateIdentity = restorePrivateIdentity(ownedIdentity, pojo.private_identity);
        if (privateIdentity == null) {
            return null;
        }

        OwnedIdentityDetails published_details = OwnedIdentityDetails.restore(identityManagerSession, ownedIdentity, pojo.published_details);
        OwnedIdentityDetails latest_details = null;
        if (pojo.latest_details != null && pojo.latest_details.version != pojo.published_details.version) {
            latest_details = OwnedIdentityDetails.restore(identityManagerSession, ownedIdentity, pojo.latest_details);
        }
        OwnedIdentity ownedIdentityObject = new OwnedIdentity(identityManagerSession, privateIdentity, published_details.getVersion(), UUID.fromString(pojo.api_key));
        if (latest_details != null) {
            ownedIdentityObject.latestDetailsVersion = latest_details.getVersion();
        }
        // after a restore, the identity is marked inactive in the Engine (to delay all message sending/reception until the device is registered on server).
        // It will be re-activated once a register push notification is successful
        ownedIdentityObject.active = false;
        ownedIdentityObject.insert();

        if (pojo.keycloak != null) {
            KeycloakServer keycloakServer = KeycloakServer.restore(identityManagerSession, ownedIdentity, pojo.keycloak);
            if (keycloakServer != null) {
                ownedIdentityObject.setKeycloakServerUrl(keycloakServer.getServerUrl());
            }
        }

        OwnedDevice currentOwnedDevice = OwnedDevice.createCurrentDevice(identityManagerSession, ownedIdentity, prng);
        // when restoring a backup, directly set all currentDevices to the most up to data capabilities
        // rationale: all channels will be recreated and contact devices will be notified properly.
        currentOwnedDevice.setRawDeviceCapabilities(ObvCapability.capabilityListToStringArray(ObvCapability.currentCapabilities));


        // The ObvIdentity returned here contains the active status of the OwnedIdentity when it was backup
        // --> this will let the app determine if push notification registration is required
        return new ObvIdentity(ownedIdentity, published_details.getJsonIdentityDetails(), ownedIdentityObject.isKeycloakManaged(), pojo.active == null || pojo.active);
    }

    private PrivateIdentityPojo_0 backupPrivateIdentity() {
        PrivateIdentityPojo_0 privateIdentityPojo = new PrivateIdentityPojo_0();
        privateIdentityPojo.server_authentication_private_key = Encoded.of(privateIdentity.getServerAuthenticationPrivateKey()).getBytes();
        privateIdentityPojo.encryption_private_key = Encoded.of(privateIdentity.getEncryptionPrivateKey()).getBytes();
        privateIdentityPojo.mac_key = Encoded.of(privateIdentity.getMacKey()).getBytes();
        return privateIdentityPojo;
    }

    private static PrivateIdentity restorePrivateIdentity(Identity publicIdentity, PrivateIdentityPojo_0 pojo) {
        try {
            ServerAuthenticationPrivateKey serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(pojo.server_authentication_private_key).decodePrivateKey();
            EncryptionPrivateKey encryptionPrivateKey = (EncryptionPrivateKey) new Encoded(pojo.encryption_private_key).decodePrivateKey();
            MACKey macKey = (MACKey) new Encoded(pojo.mac_key).decodeSymmetricKey();
            return new PrivateIdentity(publicIdentity, serverAuthenticationPrivateKey, encryptionPrivateKey, macKey);
        } catch (DecodingException | ClassCastException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public byte[] owned_identity;
        public PrivateIdentityPojo_0 private_identity;
        public OwnedIdentityDetails.Pojo_0 published_details;
        public OwnedIdentityDetails.Pojo_0 latest_details;
        public String api_key;
        public Boolean active;
        public KeycloakServer.Pojo_0 keycloak;
        public ContactIdentity.Pojo_0[] contact_identities;
        public ContactGroup.Pojo_0[] owned_groups;
        public ContactGroupV2.Pojo_0[] groups_v2;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrivateIdentityPojo_0 {
        public byte[] server_authentication_private_key;
        public byte[] encryption_private_key;
        public byte[] mac_key;
    }
    // endregion
}
