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

import net.iharder.Base64;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

public class ContactGroupV2PendingMember implements ObvDatabase {
    static final String TABLE_NAME = "contact_group_v2_pending_member";

    private final IdentityManagerSession identityManagerSession;

    private final UID groupUid;
    static final String GROUP_UID = "group_uid";
    private final String serverUrl;
    static final String SERVER_URL = "server_url";
    private final int category;
    static final String CATEGORY = "category";
    private final Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private final Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private String serializedContactDetails;
    static final String SERIALIZED_CONTACT_DETAILS = "serialized_contact_details";
    private byte[] serializedPermissions; // permission strings separated by 0x00 bytes --> allows storing future permissions
    static final String SERIALIZED_PERMISSIONS = "serialized_permissions";
    private byte[] groupInvitationNonce;
    static final String GROUP_INVITATION_NONCE = "group_invitation_nonce";

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public String getSerializedContactDetails() {
        return serializedContactDetails;
    }

    public byte[] getSerializedPermissions() {
        return serializedPermissions;
    }

    public byte[] getGroupInvitationNonce() {
        return groupInvitationNonce;
    }


    // region constructor

    public static ContactGroupV2PendingMember create(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity contactIdentity, String serializedContactDetails, Collection<String> permissionStrings, byte[] groupInvitationNonce) {
        if ((identityManagerSession == null) || (ownedIdentity == null) || (groupIdentifier == null) || (contactIdentity == null) || (permissionStrings == null) || (serializedContactDetails == null) || (groupInvitationNonce == null)) {
            return null;
        }

        try {
            byte[] serializedPermissions = GroupV2.Permission.serializePermissionStrings(permissionStrings);

            ContactGroupV2PendingMember contactGroupPendingMember = new ContactGroupV2PendingMember(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, contactIdentity, serializedContactDetails, serializedPermissions, groupInvitationNonce);
            contactGroupPendingMember.insert();
            return contactGroupPendingMember;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }


    private ContactGroupV2PendingMember(IdentityManagerSession identityManagerSession, UID groupUid, String serverUrl, int category, Identity ownedIdentity, Identity contactIdentity, String serializedContactDetails, byte[] serializedPermissions, byte[] groupInvitationNonce) {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = groupUid;
        this.serverUrl = serverUrl;
        this.category = category;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
        this.serializedContactDetails = serializedContactDetails;
        this.serializedPermissions = serializedPermissions;
        this.groupInvitationNonce = groupInvitationNonce;
    }

    public ContactGroupV2PendingMember(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = new UID(res.getBytes(GROUP_UID));
        this.serverUrl = res.getString(SERVER_URL);
        this.category = res.getInt(CATEGORY);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.serializedContactDetails = res.getString(SERIALIZED_CONTACT_DETAILS);
        this.serializedPermissions = res.getBytes(SERIALIZED_PERMISSIONS);
        this.groupInvitationNonce = res.getBytes(GROUP_INVITATION_NONCE);
    }

    // endregion


    // region Get and Set

    public static ContactGroupV2PendingMember get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity contactIdentity) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (contactIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactGroupV2PendingMember(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static List<ContactGroupV2PendingMember> getAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2PendingMember> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2PendingMember(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public void setPermissions(List<String> permissionStrings) throws Exception {
        byte[] serializedPermissions = GroupV2.Permission.serializePermissionStrings(permissionStrings);
        if (serializedPermissions == null) {
            throw new Exception("Unable to serialize permissions");
        }

        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_PERMISSIONS + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, serializedPermissions);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setBytes(6, contactIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public void setGroupInvitationNonce(byte[] groupInvitationNonce) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + GROUP_INVITATION_NONCE + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupInvitationNonce);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setBytes(6, contactIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public void setSerializedContactDetails(String serializedContactDetails) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_CONTACT_DETAILS + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?" +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setString(1, serializedContactDetails);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.setBytes(6, contactIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public static List<GroupV2.Identifier> getKeycloakGroupV2IdentifiersWhereContactIsPending(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        if ((ownedIdentity == null) || (contactIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " + GROUP_UID + " as uid, " + SERVER_URL + " as url FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?" +
                " AND " + CATEGORY + " = " + GroupV2.Identifier.CATEGORY_KEYCLOAK + ";")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<GroupV2.Identifier> list = new ArrayList<>();
                while (res.next()) {
                    try {
                        list.add(new GroupV2.Identifier(
                                new UID(res.getBytes("uid")),
                                res.getString("url"),
                                GroupV2.Identifier.CATEGORY_KEYCLOAK
                        ));
                    } catch (Exception e) {
                        Logger.x(e);
                    }
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
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    SERIALIZED_CONTACT_DETAILS + " TEXT NOT NULL, " +
                    SERIALIZED_PERMISSIONS + " BLOB NOT NULL, " +
                    GROUP_INVITATION_NONCE + " BLOB NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + ", " + CONTACT_IDENTITY + "), " +
                    " FOREIGN KEY (" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + ") REFERENCES " + ContactGroupV2.TABLE_NAME + "(" + ContactGroupV2.GROUP_UID + ", " + ContactGroupV2.SERVER_URL + ", " + ContactGroupV2.CATEGORY + ", " + ContactGroupV2.OWNED_IDENTITY + ") ON DELETE CASCADE );");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                Logger.d("CREATING contact_group_v2_member DATABASE FOR VERSION 32");
                statement.execute("CREATE TABLE contact_group_v2_pending_member (" +
                        "group_uid BLOB NOT NULL, " +
                        "server_url TEXT NOT NULL, " +
                        "category INT NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "contact_identity BLOB NOT NULL, " +
                        "serialized_contact_details TEXT NOT NULL, " +
                        "serialized_permissions BLOB NOT NULL, " +
                        "group_invitation_nonce BLOB NOT NULL, " +
                        " CONSTRAINT PK_contact_group_v2_pending_member PRIMARY KEY(group_uid, server_url, category, owned_identity, contact_identity), " +
                        " FOREIGN KEY (group_uid, server_url, category, owned_identity) REFERENCES contact_group_v2 (group_uid, server_url, category, owned_identity) ON DELETE CASCADE );");
            }
            oldVersion = 32;
        }
    }


    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?);")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, contactIdentity.getBytes());

            statement.setString(6, serializedContactDetails);
            statement.setBytes(7, serializedPermissions);
            statement.setBytes(8, groupInvitationNonce);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, contactIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    @Override
    public void wasCommitted() {

    }



    // region backup

    static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        List<ContactGroupV2PendingMember> members = getAll(identityManagerSession, ownedIdentity, groupIdentifier);
        Pojo_0[] pojos = new Pojo_0[members.size()];
        for (int i=0; i< pojos.length; i++) {
            pojos[i] = members.get(i).backup();
        }
        return pojos;
    }

    Pojo_0 backup() {
        Pojo_0 pojo = new Pojo_0();
        pojo.contact_identity = contactIdentity.getBytes();
        pojo.serialized_details = serializedContactDetails;
        pojo.permissions = GroupV2.Permission.deserializePermissions(serializedPermissions).toArray(new String[0]);
        pojo.invitation_nonce = groupInvitationNonce;
        return pojo;
    }


    static void restoreAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Pojo_0[] pojos) {
        if (pojos == null) {
            return;
        }
        for (Pojo_0 pojo : pojos) {
            try {
                String sanitizedSerializedDetails = null;
                try {
                    // check whether the input is base64 or plain json (there was a bug on iOS where the details were base64 encoded)
                    identityManagerSession.jsonObjectMapper.readValue(pojo.serialized_details, JsonIdentityDetails.class);
                    sanitizedSerializedDetails = pojo.serialized_details;
                } catch (Exception ignored) {
                    try {
                        String serializedDetailsString = new String(Base64.decode(pojo.serialized_details), StandardCharsets.UTF_8);
                        identityManagerSession.jsonObjectMapper.readValue(serializedDetailsString, JsonIdentityDetails.class);
                        sanitizedSerializedDetails = serializedDetailsString;
                    } catch (Exception e) {
                        Logger.i("Could not determine serialized details of GroupV2 pending member.");
                    }
                }

                if (sanitizedSerializedDetails != null) {
                    create(identityManagerSession, ownedIdentity, groupIdentifier, Identity.of(pojo.contact_identity), sanitizedSerializedDetails, Arrays.asList(pojo.permissions), pojo.invitation_nonce);
                }
            } catch (DecodingException e) {
                Logger.x(e);
            }
        }
    }



    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public byte[] contact_identity;
        public String serialized_details;
        public String[] permissions;
        public byte[] invitation_nonce;
    }

    // endregion
}
