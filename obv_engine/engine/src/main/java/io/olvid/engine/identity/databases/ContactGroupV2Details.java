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

package io.olvid.engine.identity.databases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

public class ContactGroupV2Details implements ObvDatabase {
    static final String TABLE_NAME = "contact_group_v2_details";

    private final IdentityManagerSession identityManagerSession;


    private final UID groupUid;
    static final String GROUP_UID = "group_uid";
    private final String serverUrl;
    static final String SERVER_URL = "server_url";
    private final int category;
    static final String CATEGORY = "category";
    private final Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private final int version;
    static final String VERSION = "version";
    private String serializedJsonDetails;
    static final String SERIALIZED_JSON_DETAILS = "serialized_json_details";
    private String photoUrl;
    static final String PHOTO_URL = "photo_url";
    private Identity photoServerIdentity;  // this is null for Keycloak groups with a photo
    static final String PHOTO_SERVER_IDENTITY = "photo_server_identity";
    private UID photoServerLabel;
    static final String PHOTO_SERVER_LABEL = "photo_server_label";
    private AuthEncKey photoServerKey;
    static final String PHOTO_SERVER_KEY = "photo_server_key";

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getGroupUid() {
        return groupUid;
    }

    public int getVersion() {
        return version;
    }

    public String getSerializedJsonDetails() {
        return serializedJsonDetails;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public GroupV2.ServerPhotoInfo getServerPhotoInfo() {
        if ((photoServerLabel == null) || (photoServerKey == null)) {
            return null;
        }
        return new GroupV2.ServerPhotoInfo(photoServerIdentity, photoServerLabel, photoServerKey);
    }

    public GroupV2.Identifier getGroupIdentifier() {
        return new GroupV2.Identifier(groupUid, serverUrl, category);
    }

    public Identity getPhotoServerIdentity() {
        return photoServerIdentity;
    }

    public UID getPhotoServerLabel() {
        return photoServerLabel;
    }

    public AuthEncKey getPhotoServerKey() {
        return photoServerKey;
    }

    // region Constructor

    public static ContactGroupV2Details createNew(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, String absolutePhotoUrl, GroupV2.ServerPhotoInfo serverPhotoInfo) {
        if ((groupIdentifier == null) || (ownedIdentity == null) || (serializedGroupDetails == null)) {
            return null;
        }

        try {
            String photoUrl = null;

            if (absolutePhotoUrl != null) {
                if ((serverPhotoInfo == null)) {
                    Logger.e("Calling ContactGroupV2Details.createNew with a photoUrl and no label or key");
                    return null;
                }

                try {
                    // copy the file to the appropriate place
                    String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator + Logger.toHexString(groupIdentifier.groupUid.getBytes());
                    String randFileName;
                    Random random = new Random();
                    File dstPhotoFile;
                    do {
                        randFileName = fileName + "_" + random.nextInt(65536);
                        dstPhotoFile = new File(identityManagerSession.engineBaseDirectory, randFileName);
                    } while (dstPhotoFile.exists());

                    // copy the file
                    File srcPhotoFile = new File(absolutePhotoUrl);
                    try (InputStream is = new FileInputStream(srcPhotoFile)) {
                        try (OutputStream os = new FileOutputStream(dstPhotoFile)) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                os.write(buffer, 0, length);
                            }
                        }
                    }

                    photoUrl = randFileName;
                } catch (IOException e) {
                    Logger.x(e);
                    Logger.w("Error copying the photo for the groupV2 --> creating a group without photo");
                }
            }

            int version = 0;
            ContactGroupV2Details contactGroupDetails;
            if (photoUrl == null) {
                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, null, null, null, null);
            } else {
                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, photoUrl, serverPhotoInfo.serverPhotoIdentity, serverPhotoInfo.serverPhotoLabel, serverPhotoInfo.serverPhotoKey);
            }
            contactGroupDetails.insert();
            return contactGroupDetails;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }



    public static ContactGroupV2Details createJoined(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, String serializedGroupDetails, GroupV2.ServerPhotoInfo serverPhotoInfo) {
        try {
            ContactGroupV2Details contactGroupDetails;
            if (serverPhotoInfo == null) {
                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, null, null, null, null);
            } else {
                // check if we have a photoUrl to copy from previous versions
                String photoUrl = null;
                for (ContactGroupV2Details otherDetails : getAll(identityManagerSession, ownedIdentity, groupIdentifier)) {
                    if (otherDetails.photoUrl != null && Objects.equals(otherDetails.getServerPhotoInfo(), serverPhotoInfo)) {
                        photoUrl = otherDetails.photoUrl;
                        break;
                    }
                }

                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, photoUrl, serverPhotoInfo.serverPhotoIdentity, serverPhotoInfo.serverPhotoLabel, serverPhotoInfo.serverPhotoKey);
            }
            contactGroupDetails.insert();
            return contactGroupDetails;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }


    public static ContactGroupV2Details create(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, String serializedGroupDetails, GroupV2.ServerPhotoInfo serverPhotoInfo) {
        if ((groupIdentifier == null) || (ownedIdentity == null) || (serializedGroupDetails == null)) {
            return null;
        }

        try {
            ContactGroupV2Details contactGroupDetails;
            if (serverPhotoInfo == null) {
                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, null, null, null, null);
            } else {
                contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serializedGroupDetails, null, serverPhotoInfo.serverPhotoIdentity, serverPhotoInfo.serverPhotoLabel, serverPhotoInfo.serverPhotoKey);
            }
            contactGroupDetails.insert();
            return contactGroupDetails;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public static ContactGroupV2Details createOrUpdateKeycloak(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, GroupV2.ServerPhotoInfo serverPhotoInfo) {
        try {
            // first check if we already have some details
            ContactGroupV2Details contactGroupDetails = get(identityManagerSession, ownedIdentity, groupIdentifier, 0);
            if (contactGroupDetails == null) {
                if (serverPhotoInfo == null) {
                    contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, 0, serializedGroupDetails, null, null, null, null);
                } else {
                    contactGroupDetails = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, 0, serializedGroupDetails, null, serverPhotoInfo.serverPhotoIdentity, serverPhotoInfo.serverPhotoLabel, serverPhotoInfo.serverPhotoKey);
                }
                contactGroupDetails.insert();
            } else {
                contactGroupDetails.serializedJsonDetails = serializedGroupDetails;
                if (serverPhotoInfo != null) {
                    // we already have some details, simply update them
                    if (contactGroupDetails.photoUrl != null) {
                        // we already have a photo, check if it changed or not
                        GroupV2.ServerPhotoInfo oldServerPhotoInfo = new GroupV2.ServerPhotoInfo(contactGroupDetails.photoServerIdentity, contactGroupDetails.photoServerLabel, contactGroupDetails.photoServerKey);
                        if (!Objects.equals(oldServerPhotoInfo, serverPhotoInfo)) {
                            contactGroupDetails.photoUrl = null;
                        }
                    }
                    contactGroupDetails.photoServerIdentity = serverPhotoInfo.serverPhotoIdentity;
                    contactGroupDetails.photoServerLabel = serverPhotoInfo.serverPhotoLabel;
                    contactGroupDetails.photoServerKey = serverPhotoInfo.serverPhotoKey;
                } else {
                    contactGroupDetails.photoUrl = null;
                    contactGroupDetails.photoServerIdentity = null;
                    contactGroupDetails.photoServerLabel = null;
                    contactGroupDetails.photoServerKey = null;
                }
                contactGroupDetails.update();
            }

            return contactGroupDetails;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public ContactGroupV2Details(IdentityManagerSession identityManagerSession, UID groupUid, String serverUrl, int category, Identity ownedIdentity, int version, String serializedJsonDetails, String photoUrl, Identity photoServerIdentity, UID photoServerLabel, AuthEncKey photoServerKey) {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = groupUid;
        this.serverUrl = serverUrl;
        this.category = category;
        this.ownedIdentity = ownedIdentity;
        this.version = version;
        this.serializedJsonDetails = serializedJsonDetails;
        this.photoUrl = photoUrl;
        this.photoServerIdentity = photoServerIdentity;
        this.photoServerLabel = photoServerLabel;
        this.photoServerKey = photoServerKey;
    }

    private ContactGroupV2Details(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = new UID(res.getBytes(GROUP_UID));
        this.serverUrl = res.getString(SERVER_URL);
        this.category = res.getInt(CATEGORY);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.version = res.getInt(VERSION);
        this.serializedJsonDetails = res.getString(SERIALIZED_JSON_DETAILS);
        this.photoUrl = res.getString(PHOTO_URL);
        byte[] bytes = res.getBytes(PHOTO_SERVER_IDENTITY);
        if (bytes == null) {
            this.photoServerIdentity = null;
        } else {
            try {
                this.photoServerIdentity = Identity.of(bytes);
            } catch (DecodingException e) {
                this.photoServerIdentity = null;
            }
        }
        bytes = res.getBytes(PHOTO_SERVER_LABEL);
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
                this.photoServerKey = null;
            }
        }
    }

    // endregion

    // region Get and Set


    public static ContactGroupV2Details get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version) throws SQLException {
        if ((groupIdentifier == null) || (ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.get",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setInt(5, version);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactGroupV2Details(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }


    public static List<ContactGroupV2Details> getAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((groupIdentifier == null) || (ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.getAll",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2Details> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2Details(identityManagerSession, res));
                }
                return list;
            }
        }
    }



    public void setPhotoUrl(String photoUrl) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.setPhotoUrl",
                "UPDATE " + TABLE_NAME +
                " SET " + PHOTO_URL + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, photoUrl);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setInt(6, version);
            statement.executeUpdate();
            this.photoUrl = photoUrl;
        }
    }

    public void setAbsolutePhotoUrl(String absolutePhotoUrl) throws Exception {
        // copy the photo
        String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator + Logger.toHexString(groupUid.getBytes());
        String randFileName;
        Random random = new Random();
        File dstPhotoFile;
        do {
            randFileName = fileName + "_" + random.nextInt(65536);
            dstPhotoFile = new File(identityManagerSession.engineBaseDirectory, randFileName);
        } while (dstPhotoFile.exists());

        // copy the file
        File srcPhotoFile = new File(absolutePhotoUrl);
        try (InputStream is = new FileInputStream(srcPhotoFile)) {
            try (OutputStream os = new FileOutputStream(dstPhotoFile)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            }
        }

        photoUrl = randFileName;

        // update the DB
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.setAbsolutePhotoUrl",
                "UPDATE " + TABLE_NAME +
                " SET " + PHOTO_URL + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, photoUrl);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setInt(6, version);
            statement.executeUpdate();
        }
    }

    public static void cleanup(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, int trustedVersion) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.cleanup",
                "DELETE FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " NOT IN (?,?);")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setInt(5, version);
            statement.setInt(6, trustedVersion);
            statement.executeUpdate();
        }
    }


    public static List<ContactGroupV2Details> getByGroupIdentifierAndServerPhotoInfo(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (serverPhotoInfo == null)) {
            return null;
        }
        if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.getByGroupIdentifierAndServerPhotoInfo",
                    "SELECT * FROM " + TABLE_NAME +
                    " WHERE " + GROUP_UID + " = ? " +
                    " AND " + SERVER_URL + " = ? " +
                    " AND " + CATEGORY + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ? " +
                    " AND " + PHOTO_SERVER_IDENTITY + " IS NULL " +
                    " AND " + PHOTO_SERVER_LABEL + " = ?;")) {
                statement.setBytes(1, groupIdentifier.groupUid.getBytes());
                statement.setString(2, groupIdentifier.serverUrl);
                statement.setInt(3, groupIdentifier.category);
                statement.setBytes(4, ownedIdentity.getBytes());
                statement.setBytes(5, serverPhotoInfo.serverPhotoLabel.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    List<ContactGroupV2Details> list = new ArrayList<>();
                    while (res.next()) {
                        ContactGroupV2Details contactGroupV2Details = new ContactGroupV2Details(identityManagerSession, res);
                        if (Objects.equals(contactGroupV2Details.photoServerKey, serverPhotoInfo.serverPhotoKey)) {
                            list.add(contactGroupV2Details);
                        }
                    }
                    return list;
                }
            }
        } else {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.getByGroupIdentifierAndServerPhotoInfo",
                    "SELECT * FROM " + TABLE_NAME +
                    " WHERE " + GROUP_UID + " = ? " +
                    " AND " + SERVER_URL + " = ? " +
                    " AND " + CATEGORY + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ? " +
                    " AND " + PHOTO_SERVER_IDENTITY + " = ? " +
                    " AND " + PHOTO_SERVER_LABEL + " = ?;")) {
                statement.setBytes(1, groupIdentifier.groupUid.getBytes());
                statement.setString(2, groupIdentifier.serverUrl);
                statement.setInt(3, groupIdentifier.category);
                statement.setBytes(4, ownedIdentity.getBytes());
                statement.setBytes(5, serverPhotoInfo.serverPhotoIdentity.getBytes());
                statement.setBytes(6, serverPhotoInfo.serverPhotoLabel.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    List<ContactGroupV2Details> list = new ArrayList<>();
                    while (res.next()) {
                        ContactGroupV2Details contactGroupV2Details = new ContactGroupV2Details(identityManagerSession, res);
                        if (Objects.equals(contactGroupV2Details.photoServerKey, serverPhotoInfo.serverPhotoKey)) {
                            list.add(contactGroupV2Details);
                        }
                    }
                    return list;
                }
            }
        }
    }

    public static List<String> getAllPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.getAllPhotoUrl",
                "SELECT " + PHOTO_URL + " FROM " + TABLE_NAME +
                " WHERE " + PHOTO_URL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<String> list = new ArrayList<>();
                while (res.next()) {
                    list.add(res.getString(PHOTO_URL));
                }
                return list;
            }
        }
    }

    public static List<ContactGroupV2Details> getAllWithMissingPhotoUrl(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.getAllWithMissingPhotoUrl",
                "SELECT * FROM " + TABLE_NAME +
                " WHERE " + PHOTO_URL + " IS NULL " +
                " AND (" + PHOTO_SERVER_IDENTITY + " IS NOT NULL" +
                " OR " + CATEGORY + " = " + GroupV2.Identifier.CATEGORY_KEYCLOAK + ") " +
                " AND " + PHOTO_SERVER_KEY + " IS NOT NULL " +
                " AND " + PHOTO_SERVER_LABEL + " IS NOT NULL;")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2Details> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2Details(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    // endregion



    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    GROUP_UID + " BLOB NOT NULL, " +
                    SERVER_URL + " TEXT NOT NULL, " +
                    CATEGORY + " INT NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    VERSION + " INT NOT NULL, " +
                    SERIALIZED_JSON_DETAILS + " TEXT NOT NULL, " +
                    PHOTO_URL + " TEXT, " +
                    PHOTO_SERVER_IDENTITY + " BLOB, " +
                    PHOTO_SERVER_LABEL + " BLOB, " +
                    PHOTO_SERVER_KEY + " BLOB, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + ", " + VERSION + ") );");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                Logger.d("CREATING contact_group_v2_details DATABASE FOR VERSION 32");
                statement.execute("CREATE TABLE contact_group_v2_details (" +
                        "group_uid BLOB NOT NULL, " +
                        "server_url TEXT NOT NULL, " +
                        "category INT NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "version INT NOT NULL, " +
                        "serialized_json_details TEXT NOT NULL, " +
                        "photo_url TEXT, " +
                        "photo_server_identity BLOB, " +
                        "photo_server_label BLOB, " +
                        "photo_server_key BLOB, " +
                        " CONSTRAINT PK_contact_group_v2_details PRIMARY KEY(group_uid, server_url, category, owned_identity, version) );");
            }
            oldVersion = 32;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.insert",
                "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?);")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setInt(5, version);

            statement.setString(6, serializedJsonDetails);
            statement.setString(7, photoUrl);
            statement.setBytes(8, photoServerIdentity == null ? null : photoServerIdentity.getBytes());
            statement.setBytes(9, photoServerLabel == null ? null : photoServerLabel.getBytes());
            statement.setBytes(10, photoServerKey == null ? null : Encoded.of(photoServerKey).getBytes());
            statement.executeUpdate();
        }
    }

    public void update() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.update",
                "UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_JSON_DETAILS + " = ?, " +
                PHOTO_URL + " = ?, " +
                PHOTO_SERVER_IDENTITY + " = ?, " +
                PHOTO_SERVER_LABEL + " = ?, " +
                PHOTO_SERVER_KEY + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setString(1, serializedJsonDetails);
            statement.setString(2, photoUrl);
            statement.setBytes(3, photoServerIdentity == null ? null : photoServerIdentity.getBytes());
            statement.setBytes(4, photoServerLabel == null ? null : photoServerLabel.getBytes());
            statement.setBytes(5, photoServerKey == null ? null : Encoded.of(photoServerKey).getBytes());

            statement.setBytes(6, groupUid.getBytes());
            statement.setString(7, serverUrl);
            statement.setInt(8, category);
            statement.setBytes(9, ownedIdentity.getBytes());
            statement.setInt(10, version);

            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("ContactGroupV2Details.delete",
                "DELETE FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + VERSION + " = ?;")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setInt(5, version);
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
        pojo.serialized_details = serializedJsonDetails;
        if (photoServerLabel != null && photoServerKey != null) {
            pojo.photo_server_identity = photoServerIdentity == null ? null : photoServerIdentity.getBytes();
            pojo.photo_server_label = photoServerLabel.getBytes();
            pojo.photo_server_key = Encoded.of(photoServerKey).getBytes();
        }
        return pojo;
    }

    static void restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, Pojo_0 pojo) throws SQLException {
        ContactGroupV2Details contactGroupV2Details = null;
        if (pojo.photo_server_label != null && pojo.photo_server_key != null) {
            try {
                Identity photoServerIdentity = pojo.photo_server_identity == null ? null : Identity.of(pojo.photo_server_identity);
                UID photoServerLabel = new UID(pojo.photo_server_label);
                AuthEncKey photoServerKey = (AuthEncKey) new Encoded(pojo.photo_server_key).decodeSymmetricKey();

                contactGroupV2Details = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, pojo.serialized_details, null, photoServerIdentity, photoServerLabel, photoServerKey);
            } catch (Exception e) {
                Logger.x(e);
            }
        }

        if (contactGroupV2Details == null) {
            contactGroupV2Details = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, pojo.serialized_details, null, null, null, null);
        }
        contactGroupV2Details.insert();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public String serialized_details;
        public byte[] photo_server_identity;
        public byte[] photo_server_label;
        public byte[] photo_server_key;
    }

    // endregion
}
