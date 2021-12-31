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
public class ContactIdentityDetails implements ObvDatabase {
    static final String TABLE_NAME = "contact_identity_details";

    private final IdentityManagerSession identityManagerSession;

    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
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


    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public int getVersion() {
        return version;
    }

    public String getSerializedJsonDetails() {
        return serializedJsonDetails;
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

    // region constructors

    public static ContactIdentityDetails create(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto) {
        if (contactIdentity == null || ownedIdentity == null || jsonIdentityDetailsWithVersionAndPhoto == null || jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails() == null) {
            return null;
        }
        try {
            int version = jsonIdentityDetailsWithVersionAndPhoto.getVersion();
            String serializedJsonDetails = identityManagerSession.jsonObjectMapper.writeValueAsString(jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails());
            UID photoServerLabel = (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel() == null) ? null : new UID(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel());
            AuthEncKey photoServerKey = (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() == null) ? null : (AuthEncKey) new Encoded(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey();
            ContactIdentityDetails contactIdentityDetails = new ContactIdentityDetails(identityManagerSession, contactIdentity, ownedIdentity, version, serializedJsonDetails, null, photoServerLabel, photoServerKey);
            contactIdentityDetails.insert();
            return contactIdentityDetails;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ContactIdentityDetails copy(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity, int version, int newVersion) {
        if (ownedIdentity == null) {
            return null;
        }
        try {
            ContactIdentityDetails oldDetails = get(identityManagerSession, contactIdentity, ownedIdentity, version);
            if (oldDetails == null) {
                return null;
            }
            ContactIdentityDetails ownedIdentityDetails = new ContactIdentityDetails(identityManagerSession, contactIdentity, ownedIdentity, newVersion, oldDetails.serializedJsonDetails, oldDetails.photoUrl, oldDetails.photoServerLabel, oldDetails.photoServerKey);
            ownedIdentityDetails.insert();
            return ownedIdentityDetails;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private ContactIdentityDetails(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, int version, String serializedJsonDetails, String photoUrl, UID photoServerLabel, AuthEncKey photoServerKey) {
        this.identityManagerSession = identityManagerSession;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
        this.version = version;
        this.serializedJsonDetails = serializedJsonDetails;
        this.photoUrl = photoUrl;
        this.photoServerLabel = photoServerLabel;
        this.photoServerKey = photoServerKey;
    }

    private ContactIdentityDetails(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
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
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    VERSION + " INT NOT NULL, " +
                    SERIALIZED_JSON_DETAILS + " TEXT NOT NULL, " +
                    PHOTO_URL + " TEXT, " +
                    PHOTO_SERVER_LABEL + " BLOB, " +
                    PHOTO_SERVER_KEY + " BLOB, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ", " + VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, contactIdentity.getBytes());
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
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static ContactIdentityDetails get(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, int version) throws SQLException {
        if (ownedIdentity == null) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + CONTACT_IDENTITY + " = ?" +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " +  VERSION + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, version);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactIdentityDetails(identityManagerSession, res);
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

    public static List<ContactIdentityDetails> getAllWithMissingPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                        " WHERE " + PHOTO_URL + " IS NULL " +
                        " AND " + PHOTO_SERVER_KEY + " IS NOT NULL " +
                        " AND " + PHOTO_SERVER_LABEL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentityDetails> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentityDetails(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    // endregion

    // region setters

    public void setPhotoUrl(String photoUrl) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + PHOTO_URL + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, photoUrl);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.photoUrl = photoUrl;
        }
    }

    public void setSerializedJsonDetails(String serializedJsonDetails) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_JSON_DETAILS + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, serializedJsonDetails);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setInt(4, version);
            statement.executeUpdate();
            this.serializedJsonDetails = serializedJsonDetails;
        }
    }

    public static void cleanup(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity, int publishedVersion, int trustedVersion) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ? " +
                " AND " +  VERSION + " NOT IN (?,?);")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setInt(3, publishedVersion);
            statement.setInt(4, trustedVersion);
            statement.executeUpdate();
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

    public static ContactIdentityDetails restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity, Pojo_0 pojo) throws SQLException {
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
        ContactIdentityDetails contactIdentityDetails = new ContactIdentityDetails(identityManagerSession, contactIdentity, ownedIdentity, pojo.version, pojo.serialized_details, null, photoServerLabel, photoServerKey);
        contactIdentityDetails.insert();
        return contactIdentityDetails;
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
