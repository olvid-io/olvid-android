/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class OwnedIdentityDetails implements ObvDatabase {
    static final String TABLE_NAME = "owned_identity_details";

    private final IdentityManagerSession identityManagerSession;

    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private int version;
    static final String VERSION = "version";
    private String serializedJsonDetails;
    static final String SERIALIZED_JSON_DETAILS = "serialized_json_details";
    private String photoUrl;
    static final String PHOTO_URL = "photo_url";
    private UID photoServerLabel;
    static final String PHOTO_SERVER_LABEL = "photo_server_label";
    private AuthEncKey photoServerKey;
    static final String PHOTO_SERVER_KEY = "photo_server_key";

    public int getVersion() {
        return version;
    }

    public String getSerializedJsonDetails() {
        return serializedJsonDetails;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public JsonIdentityDetails getJsonIdentityDetails() {
        try {
            return identityManagerSession.jsonObjectMapper.readValue(serializedJsonDetails, JsonIdentityDetails.class);
        } catch (Exception e) {
            return null;
        }
    }

    public JsonIdentityDetailsWithVersionAndPhoto getJsonIdentityDetailsWithVersionAndPhoto() {
        try {
            JsonIdentityDetailsWithVersionAndPhoto json = new JsonIdentityDetailsWithVersionAndPhoto();
            json.setIdentityDetails(identityManagerSession.jsonObjectMapper.readValue(serializedJsonDetails, JsonIdentityDetails.class));
            json.setVersion(version);
            json.setPhotoUrl(photoUrl);
            if (photoServerKey != null && photoServerLabel != null) {
                json.setPhotoServerLabel(photoServerLabel.getBytes());
                json.setPhotoServerKey(Encoded.of(photoServerKey).getBytes());
            }
            return json;
        } catch (Exception e) {
            return null;
        }
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public UID getPhotoServerLabel() {
        return photoServerLabel;
    }

    public AuthEncKey getPhotoServerKey() {
        return photoServerKey;
    }

    // region constructors

    public static OwnedIdentityDetails create(IdentityManagerSession identityManagerSession, Identity ownedIdentity, String serializedJsonDetails) {
        if (ownedIdentity == null || serializedJsonDetails == null) {
            return null;
        }
        try {
            int newVersion = 1;
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " ORDER BY " + VERSION + " DESC LIMIT 1;")){
                statement.setBytes(1, ownedIdentity.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    if (res.next()) {
                        newVersion = res.getInt(VERSION) + 1;
                    }
                }
            }
            OwnedIdentityDetails ownedIdentityDetails = new OwnedIdentityDetails(identityManagerSession, ownedIdentity, newVersion, serializedJsonDetails);
            ownedIdentityDetails.insert();
            return ownedIdentityDetails;
        } catch (Exception e) {
            return null;
        }
    }

    public static OwnedIdentityDetails copy(IdentityManagerSession identityManagerSession, Identity ownedIdentity, int version) {
        if (ownedIdentity == null) {
            return null;
        }
        try {
            int newVersion = version + 1;
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " ORDER BY " + VERSION + " DESC LIMIT 1;")){
                statement.setBytes(1, ownedIdentity.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    if (res.next()) {
                        newVersion = res.getInt(VERSION) + 1;
                    }
                }
            }
            OwnedIdentityDetails oldDetails = get(identityManagerSession, ownedIdentity, version);
            if (oldDetails == null) {
                return null;
            }
            OwnedIdentityDetails ownedIdentityDetails = new OwnedIdentityDetails(identityManagerSession, ownedIdentity, newVersion, oldDetails.serializedJsonDetails, oldDetails.photoUrl, oldDetails.photoServerLabel, oldDetails.photoServerKey);
            ownedIdentityDetails.insert();
            return ownedIdentityDetails;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    private OwnedIdentityDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, int version, String serializedJsonDetails, String photoUrl, UID photoServerLabel, AuthEncKey photoServerKey) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.version = version;
        this.serializedJsonDetails = serializedJsonDetails;
        this.photoUrl = photoUrl;
        this.photoServerLabel = photoServerLabel;
        this.photoServerKey = photoServerKey;
    }

    private OwnedIdentityDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, int version, String serializedJsonDetails) {
        this.identityManagerSession = identityManagerSession;
        this.ownedIdentity = ownedIdentity;
        this.version = version;
        this.serializedJsonDetails = serializedJsonDetails;
        this.photoUrl = null;
        this.photoServerLabel = null;
        this.photoServerKey = null;
    }

    private OwnedIdentityDetails(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.version = res.getInt(VERSION);
        this.serializedJsonDetails = res.getString(SERIALIZED_JSON_DETAILS);
        this.photoUrl = res.getString(PHOTO_URL);
        byte[] bytes = res.getBytes(PHOTO_SERVER_LABEL);
        if (bytes == null) {
            this.photoServerLabel = null;
        } else {
            this.photoServerLabel = new UID(bytes);
        }
        bytes = res.getBytes(PHOTO_SERVER_KEY);
        if (bytes == null) {
            this.photoServerKey = null;
        } else {
            try {
                this.photoServerKey = (AuthEncKey) new Encoded(bytes).decodeSymmetricKey();
            } catch (DecodingException e) {
                e.printStackTrace();
                this.photoServerKey = null;
            }
        }
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    VERSION + " INT NOT NULL, " +
                    SERIALIZED_JSON_DETAILS + " TEXT NOT NULL, " +
                    PHOTO_URL + " TEXT, " +
                    PHOTO_SERVER_LABEL + " BLOB, " +
                    PHOTO_SERVER_KEY + " BLOB, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + OWNED_IDENTITY + ", " + VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setInt(2, version);
            statement.setString(3, serializedJsonDetails);
            statement.setString(4, photoUrl);
            statement.setBytes(5, photoServerLabel==null?null:photoServerLabel.getBytes());
            statement.setBytes(6, photoServerKey==null?null:Encoded.of(photoServerKey).getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static OwnedIdentityDetails get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, int version) throws SQLException {
        if ((ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ?" +
                " AND " +  VERSION + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setInt(2, version);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedIdentityDetails(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static List<String> getAllPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " + PHOTO_URL + " FROM " + TABLE_NAME + " WHERE " + PHOTO_URL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<String> list = new ArrayList<>();
                while (res.next()) {
                    list.add(res.getString(PHOTO_URL));
                }
                return list;
            }
        }
    }

    public static List<OwnedIdentityDetails> getAllWithMissinPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + PHOTO_URL + " IS NULL " +
                " AND " + PHOTO_SERVER_KEY + " IS NOT NULL " +
                " AND " + PHOTO_SERVER_LABEL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<OwnedIdentityDetails> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new OwnedIdentityDetails(identityManagerSession, res));
                }
                return list;
            }
        }
    }
    // endregion

    // region setters

    public static void cleanup(IdentityManagerSession identityManagerSession, Identity ownedIdentity, int publishedVersion, int latestVersion) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " +  VERSION + " NOT IN (?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setInt(2, publishedVersion);
            statement.setInt(3, latestVersion);
            statement.executeUpdate();
        }
    }


    public void setJsonDetails(JsonIdentityDetails jsonIdentityDetails) throws Exception {
        if (jsonIdentityDetails == null) {
            throw new Exception();
        }
        String serializedJsonDetails = identityManagerSession.jsonObjectMapper.writeValueAsString(jsonIdentityDetails);
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_JSON_DETAILS + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, serializedJsonDetails);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            statement.executeUpdate();
            this.serializedJsonDetails = serializedJsonDetails;
        }
    }

    public void setPhotoUrl(String photoUrl, boolean clearLabelAndKey) throws SQLException {
        if (clearLabelAndKey) {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                    PHOTO_URL + " = ?, " +
                    PHOTO_SERVER_LABEL + " = NULL, " +
                    PHOTO_SERVER_KEY + " = NULL " +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + VERSION + " = ?;")) {
                statement.setString(1, photoUrl);
                statement.setBytes(2, ownedIdentity.getBytes());
                statement.setInt(3, version);
                statement.executeUpdate();
                this.photoUrl = photoUrl;
                this.photoServerKey = null;
                this.photoServerLabel = null;
            }
        } else {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                    PHOTO_URL + " = ? " +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + VERSION + " = ?;")) {
                statement.setString(1, photoUrl);
                statement.setBytes(2, ownedIdentity.getBytes());
                statement.setInt(3, version);
                statement.executeUpdate();
                this.photoUrl = photoUrl;
            }
        }
    }

    public void setPhotoServerLabelAndKey(UID photoServerLabel, AuthEncKey photoServerKey) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                PHOTO_SERVER_LABEL + " = ?, " +
                PHOTO_SERVER_KEY + " = ? " +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, photoServerLabel.getBytes());
            statement.setBytes(2, Encoded.of(photoServerKey).getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.photoServerLabel = photoServerLabel;
            this.photoServerKey = photoServerKey;
        }
    }


    // endregion

    @Override
    public void wasCommitted() {

    }


    // region backup

    Pojo_0 backup() {
        Pojo_0 pojo = new Pojo_0();
        pojo.version = version;
        pojo.serialized_details = serializedJsonDetails;
        if (photoServerLabel != null) {
            pojo.photo_server_label = photoServerLabel.getBytes();
        }
        if (photoServerKey != null) {
            pojo.photo_server_key = Encoded.of(photoServerKey).getBytes();
        }
        return pojo;
    }

    public static OwnedIdentityDetails restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Pojo_0 pojo) throws SQLException {
        UID photoServerLabel = null;
        if (pojo.photo_server_label != null) {
            photoServerLabel = new UID(pojo.photo_server_label);
        }
        AuthEncKey photoServerKey = null;
        try {
            if (pojo.photo_server_key != null) {
                photoServerKey = (AuthEncKey) new Encoded(pojo.photo_server_key).decodeSymmetricKey();
            }
        } catch (DecodingException | ClassCastException e) {
            e.printStackTrace();
        }
        OwnedIdentityDetails ownedIdentityDetails = new OwnedIdentityDetails(identityManagerSession, ownedIdentity, pojo.version, pojo.serialized_details, null, photoServerLabel, photoServerKey);
        ownedIdentityDetails.insert();
        if (photoServerLabel != null && photoServerKey != null) {
            ServerUserData.createForOwnedIdentityDetails(identityManagerSession, ownedIdentity, photoServerLabel);
        }
        return ownedIdentityDetails;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public int version;
        public String serialized_details;
        public byte[] photo_server_label;
        public byte[] photo_server_key;
    }

    // endregion
}
