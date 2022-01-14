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
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ContactGroupDetails implements ObvDatabase {
    static final String TABLE_NAME = "contact_group_details";

    private final IdentityManagerSession identityManagerSession;

    private byte[] groupOwnerAndUid;
    static final String GROUP_OWNER_AND_UID = "group_owner_and_uid";
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

    public byte[] getGroupOwnerAndUid() {
        return groupOwnerAndUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public int getVersion() {
        return version;
    }

    public JsonGroupDetails getJsonGroupDetails() {
        try {
            return identityManagerSession.jsonObjectMapper.readValue(serializedJsonDetails, JsonGroupDetails.class);
        } catch (Exception e) {
            return null;
        }
    }

    public JsonGroupDetailsWithVersionAndPhoto getJsonGroupDetailsWithVersionAndPhoto() {
        try {
            JsonGroupDetailsWithVersionAndPhoto json = new JsonGroupDetailsWithVersionAndPhoto();
            json.setGroupDetails(identityManagerSession.jsonObjectMapper.readValue(serializedJsonDetails, JsonGroupDetails.class));
            json.setVersion(version);
            json.setPhotoUrl(photoUrl);
            if (photoServerLabel != null && photoServerKey != null) {
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

    // region setters

    public static void cleanup(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid, int publishedVersion, int latestOrTrustedVersion) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " +  VERSION + " NOT IN (?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, groupOwnerAndUid);
            statement.setInt(3, publishedVersion);
            statement.setInt(4, latestOrTrustedVersion);
            statement.executeUpdate();
        }
    }


    public void setJsonDetails(JsonGroupDetails jsonGroupDetails) throws Exception {
        if (jsonGroupDetails == null) {
            throw new Exception();
        }
        String serializedJsonDetails = identityManagerSession.jsonObjectMapper.writeValueAsString(jsonGroupDetails);
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                SERIALIZED_JSON_DETAILS + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, serializedJsonDetails);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.serializedJsonDetails = serializedJsonDetails;
        }
    }

    public void setPhotoUrl(String photoUrl, boolean clearLabelAndKey) throws SQLException {
        if (clearLabelAndKey) {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PHOTO_URL + " = ?, " +
                    PHOTO_SERVER_LABEL + " = NULL, " +
                    PHOTO_SERVER_KEY + " = NULL " +
                    " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ? " +
                    " AND " + VERSION + " = ?;")) {
                statement.setString(1, photoUrl);
                statement.setBytes(2, groupOwnerAndUid);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.setInt(4, version);
                statement.executeUpdate();
                this.photoUrl = photoUrl;
                this.photoServerKey = null;
                this.photoServerLabel = null;
            }
        } else {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PHOTO_URL + " = ? " +
                    " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ? " +
                    " AND " + VERSION + " = ?;")) {
                statement.setString(1, photoUrl);
                statement.setBytes(2, groupOwnerAndUid);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.setInt(4, version);
                statement.executeUpdate();
                this.photoUrl = photoUrl;
            }
        }
    }

    public void setPhotoServerLabelAndKey(UID photoServerLabel, AuthEncKey photoServerKey) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                PHOTO_SERVER_LABEL + " = ?, " +
                PHOTO_SERVER_KEY + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, photoServerLabel.getBytes());
            statement.setBytes(2, Encoded.of(photoServerKey).getBytes());
            statement.setBytes(3, groupOwnerAndUid);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setInt(5, version);
            statement.executeUpdate();
            this.photoServerLabel = photoServerLabel;
            this.photoServerKey = photoServerKey;
        }
    }

    // endregion


    // region constructors

    public static ContactGroupDetails create(IdentityManagerSession identityManagerSession, byte[] groupUid, Identity ownedIdentity, JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto) {
        if (groupUid == null || ownedIdentity == null || jsonGroupDetailsWithVersionAndPhoto == null || jsonGroupDetailsWithVersionAndPhoto.getGroupDetails() == null) {
            return null;
        }
        try {
            int version = jsonGroupDetailsWithVersionAndPhoto.getVersion();
            String serializedJsonDetails = identityManagerSession.jsonObjectMapper.writeValueAsString(jsonGroupDetailsWithVersionAndPhoto.getGroupDetails());
            UID photoServerLabel = (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel() == null) ? null : new UID(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel());
            AuthEncKey photoServerKey = (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey() == null) ? null : (AuthEncKey) new Encoded(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey();
            ContactGroupDetails contactGroupDetails = new ContactGroupDetails(identityManagerSession, groupUid, ownedIdentity, version, serializedJsonDetails, null, photoServerLabel, photoServerKey);
            contactGroupDetails.insert();
            return contactGroupDetails;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ContactGroupDetails copy(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupUid, int version, Integer newVersion) {
        if (groupUid == null || ownedIdentity == null) {
            return null;
        }
        try {
            if (newVersion == null) {
                newVersion = version + 1;
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                        " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ? " +
                        " ORDER BY " + VERSION + " DESC LIMIT 1;")) {
                    statement.setBytes(1, groupUid);
                    statement.setBytes(2, ownedIdentity.getBytes());
                    try (ResultSet res = statement.executeQuery()) {
                        if (res.next()) {
                            newVersion = res.getInt(VERSION) + 1;
                        }
                    }
                }
            }
            ContactGroupDetails oldDetails = get(identityManagerSession, groupUid, ownedIdentity, version);
            if (oldDetails == null) {
                return null;
            }
            ContactGroupDetails contactGroupDetails = new ContactGroupDetails(identityManagerSession, groupUid, ownedIdentity, newVersion, oldDetails.serializedJsonDetails, oldDetails.photoUrl, oldDetails.photoServerLabel, oldDetails.photoServerKey);
            contactGroupDetails.insert();
            return contactGroupDetails;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private ContactGroupDetails(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, int version, String serializedJsonDetails, String photoUrl, UID photoServerLabel, AuthEncKey photoServerKey) {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = groupOwnerAndUid;
        this.ownedIdentity = ownedIdentity;
        this.version = version;
        this.serializedJsonDetails = serializedJsonDetails;
        this.photoUrl = photoUrl;
        this.photoServerLabel = photoServerLabel;
        this.photoServerKey = photoServerKey;
    }

    private ContactGroupDetails(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = res.getBytes(GROUP_OWNER_AND_UID);
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
                    GROUP_OWNER_AND_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    VERSION + " INT NOT NULL, " +
                    SERIALIZED_JSON_DETAILS + " TEXT NOT NULL, " +
                    PHOTO_URL + " TEXT, " +
                    PHOTO_SERVER_LABEL + " BLOB, " +
                    PHOTO_SERVER_KEY + " BLOB, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_OWNER_AND_UID + ", " + OWNED_IDENTITY + ", " + VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            statement.setString(4, serializedJsonDetails);
            statement.setString(5, photoUrl);
            statement.setBytes(6, photoServerLabel==null?null:photoServerLabel.getBytes());
            statement.setBytes(7, photoServerKey==null?null:Encoded.of(photoServerKey).getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static ContactGroupDetails get(IdentityManagerSession identityManagerSession, byte[] groupUid, Identity ownedIdentity, int version) throws SQLException {
        if ((ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + GROUP_OWNER_AND_UID + " = ?" +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " +  VERSION + " = ?;")) {
            statement.setBytes(1, groupUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactGroupDetails(identityManagerSession, res);
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

    public static List<ContactGroupDetails> getAllWithMissingPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + PHOTO_URL + " IS NULL " +
                " AND " + PHOTO_SERVER_KEY + " IS NOT NULL " +
                " AND " + PHOTO_SERVER_LABEL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupDetails> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupDetails(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {
    }

    // endregion

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

    public static ContactGroupDetails restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid, Pojo_0 pojo, boolean ownedGroup) throws SQLException {
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
        ContactGroupDetails contactGroupDetails = new ContactGroupDetails(identityManagerSession, groupOwnerAndUid, ownedIdentity, pojo.version, pojo.serialized_details, null, photoServerLabel, photoServerKey);
        contactGroupDetails.insert();
        if (ownedGroup && photoServerLabel != null && photoServerKey != null) {
            ServerUserData.createForOwnedGroupDetails(identityManagerSession, ownedIdentity, photoServerLabel, groupOwnerAndUid);
        }
        return contactGroupDetails;
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
