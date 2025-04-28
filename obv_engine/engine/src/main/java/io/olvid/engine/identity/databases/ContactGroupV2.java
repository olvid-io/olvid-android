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
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.KeycloakGroupV2UpdateOutput;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.identity.datatypes.KeycloakGroupBlob;
import io.olvid.engine.identity.datatypes.KeycloakGroupMemberAndPermissions;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class ContactGroupV2 implements ObvDatabase {
    static final String TABLE_NAME = "contact_group_v2";

    private final IdentityManagerSession identityManagerSession;

    private final UID groupUid;
    static final String GROUP_UID = "group_uid";
    private final String serverUrl;
    static final String SERVER_URL = "server_url";
    private final int category;
    static final String CATEGORY = "category";
    private final Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private byte[] serializedOwnPermissions; // permission strings separated by 0x00 bytes --> allows storing future permissions
    static final String SERIALIZED_OWN_PERMISSIONS = "serialized_own_permissions";
    private int version; // always 0 for a keycloak group
    static final String VERSION = "version";
    public int trustedDetailsVersion; // always 0 for a keycloak group
    static final String TRUSTED_DETAILS_VERSION = "trusted_details_version";

    private byte[] verifiedAdministratorsChain; // null for a keycloak group
    static final String VERIFIED_ADMINISTRATORS_CHAIN = "verified_administrators_chain";
    private Seed blobMainSeed; // used to decrypt the blob on the server, null for a keycloak group
    static final String BLOB_MAIN_SEED = "blob_main_seed";
    private Seed blobVersionSeed; // used to decrypt the blob on the server, null for a keycloak group
    static final String BLOB_VERSION_SEED = "blob_version_seed";
    private ServerAuthenticationPrivateKey groupAdminServerAuthenticationPrivateKey; // non null for admins --> required to upload the blob
    static final String GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY = "group_admin_server_authentication_private_key";
    private byte[] ownGroupInvitationNonce;
    static final String OWN_GROUP_INVITATION_NONCE = "own_group_invitation_nonce";
    private boolean frozen; // set to true after a backup restore until the blob keys have been verified online
    static final String FROZEN = "frozen";
    private long lastModificationTimestamp;
    static final String LAST_MODIFICATION_TIMESTAMP = "last_modification_timestamp";
    private String pushTopic; // non-null only for keyclaok groups
    static final String PUSH_TOPIC = "push_topic";
    private String serializedSharedSettings; // non-null only for keyclaok groups
    static final String SERIALIZED_SHARED_SETTINGS = "serialized_shared_settings";
    private String serializedJsonGroupType;
    static final String SERIALIZED_JSON_GROUP_TYPE = "serialized_json_group_type";


    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public GroupV2.Identifier getGroupIdentifier() {
        return new GroupV2.Identifier(groupUid, serverUrl, category);
    }

    public Integer getVersion() {
        return version;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int getTrustedDetailsVersion() {
        return trustedDetailsVersion;
    }

    public List<String> getOwnPermissionStrings() {
        return GroupV2.Permission.deserializePermissions(serializedOwnPermissions);
    }

    public Seed getBlobMainSeed() {
        return blobMainSeed;
    }

    public Seed getBlobVersionSeed() {
        return blobVersionSeed;
    }

    public ServerAuthenticationPrivateKey getGroupAdminServerAuthenticationPrivateKey() {
        return groupAdminServerAuthenticationPrivateKey;
    }

    public byte[] getOwnGroupInvitationNonce() {
        return ownGroupInvitationNonce;
    }

    public byte[] getVerifiedAdministratorsChain() {
        return verifiedAdministratorsChain;
    }

    public long getLastModificationTimestamp() {
        return lastModificationTimestamp;
    }

    public String getPushTopic() {
        return pushTopic;
    }

    public String getSerializedSharedSettings() {
        return serializedSharedSettings;
    }

    public String getSerializedJsonGroupType() {
        return serializedJsonGroupType;
    }

    // region constructor

    // used only by the group creator to create a new group
    public static ContactGroupV2 createNew(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, String absolutePhotoUrl, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] verifiedAdministratorsChain, GroupV2.BlobKeys blobKeys, byte[] ownGroupInvitationNonce, List<String> ownPermissionStrings, String serializedGroupType) {
        if ((groupIdentifier == null) || (ownedIdentity == null) || (serializedGroupDetails == null) || (verifiedAdministratorsChain == null) || (blobKeys == null) || (ownGroupInvitationNonce == null)) {
            return null;
        }

        try {
            if (!identityManagerSession.session.isInTransaction()) {
                Logger.e("Calling ContactGroupV2.createNew() outside a transaction");
                return null;
            }
            ContactGroupV2Details contactGroupDetails = ContactGroupV2Details.createNew(identityManagerSession, ownedIdentity, groupIdentifier, serializedGroupDetails, absolutePhotoUrl, serverPhotoInfo);
            if (contactGroupDetails == null) {
                Logger.e("Error create contactGroupDetails in ContactGroupV2.createNew()");
                return null;
            }

            byte[] serializedOwnPermissions = GroupV2.Permission.serializePermissionStrings(ownPermissionStrings);

            // when first creating the group, it is frozen. It will be unfrozen once the group is successfully uploaded to the server and the members can be notified
            ContactGroupV2 contactGroup = new ContactGroupV2(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, serializedOwnPermissions, contactGroupDetails.getVersion(), verifiedAdministratorsChain, blobKeys, ownGroupInvitationNonce, true, System.currentTimeMillis(), null, null, serializedGroupType);
            contactGroup.insert();
            contactGroup.commitHookBits |= HOOK_BIT_INSERTED_AS_NEW | HOOK_BIT_FROZEN_CHANGED; // this way the app also receives a frozen notification to mark the group as updating
            return contactGroup;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }


    public static ContactGroupV2 createJoined(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, String serializedGroupDetails, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] verifiedAdministratorsChain, GroupV2.BlobKeys blobKeys, byte[] ownGroupInvitationNonce, List<String> ownPermissionStrings, String serializedGroupType, boolean createdByMeOnOtherDevice) {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (serializedGroupDetails == null) || (verifiedAdministratorsChain == null) || (blobKeys == null) || (ownGroupInvitationNonce == null) || (ownPermissionStrings == null)) {
            return null;
        }
        try {
            if (!identityManagerSession.session.isInTransaction()) {
                Logger.e("Calling ContactGroupV2.createJoined() outside a transaction");
                return null;
            }
            ContactGroupV2Details contactGroupDetails = ContactGroupV2Details.createJoined(identityManagerSession, ownedIdentity, groupIdentifier, version, serializedGroupDetails, serverPhotoInfo);
            if (contactGroupDetails == null) {
                Logger.e("Error create contactGroupDetails in ContactGroupV2.createJoined()");
                return null;
            }

            ContactGroupV2 contactGroup = new ContactGroupV2(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, GroupV2.Permission.serializePermissionStrings(ownPermissionStrings), contactGroupDetails.getVersion(), verifiedAdministratorsChain, blobKeys, ownGroupInvitationNonce, false, System.currentTimeMillis(), null, null, serializedGroupType);
            contactGroup.insert();
            if (createdByMeOnOtherDevice) {
                contactGroup.commitHookBits |= HOOK_BIT_INSERTED_AS_NEW | HOOK_BIT_CREATED_ON_OTHER_DEVICE;
            }
            return contactGroup;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public static ContactGroupV2 createKeycloak(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] ownGroupInvitationNonce, List<String> ownPermissionStrings, String pushTopic, String serializedSharedSettings, long lastModificationTimestamp) {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (serializedGroupDetails == null) || (ownGroupInvitationNonce == null) || (ownPermissionStrings == null)) {
            return null;
        }
        try {
            if (!identityManagerSession.session.isInTransaction()) {
                Logger.e("Calling ContactGroupV2.createJoined() outside a transaction");
                return null;
            }

            ContactGroupV2Details contactGroupDetails = ContactGroupV2Details.createOrUpdateKeycloak(identityManagerSession, ownedIdentity, groupIdentifier, serializedGroupDetails, serverPhotoInfo);
            if (contactGroupDetails == null) {
                Logger.e("Error create contactGroupDetails in ContactGroupV2.createJoined()");
                return null;
            }

            ContactGroupV2 contactGroup = new ContactGroupV2(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, GroupV2.Permission.serializePermissionStrings(ownPermissionStrings), contactGroupDetails.getVersion(), null, null, ownGroupInvitationNonce, false, lastModificationTimestamp, pushTopic, serializedSharedSettings, null);
            contactGroup.insert();
            if (pushTopic != null) {
                contactGroup.commitHookBits |= HOOK_BIT_NEW_PUSH_TOPIC;
                identityManagerSession.session.addSessionCommitListener(contactGroup);
            }
            return contactGroup;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public ContactGroupV2(IdentityManagerSession identityManagerSession, UID groupUid, String serverUrl, int category, Identity ownedIdentity, byte[] serializedOwnPermission, int version, byte[] verifiedAdministratorsChain, GroupV2.BlobKeys blobKeys, byte[] ownGroupInvitationNonce, boolean frozen, long lastModificationTimestamp, String pushTopic, String serializedSharedSettings, String serializedJsonGroupType) {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = groupUid;
        this.serverUrl = serverUrl;
        this.category = category;
        this.ownedIdentity = ownedIdentity;
        this.serializedOwnPermissions = serializedOwnPermission;
        this.version = version;
        this.trustedDetailsVersion = version;
        this.verifiedAdministratorsChain = verifiedAdministratorsChain;
        if (blobKeys == null) {
            this.blobMainSeed = null;
            this.blobVersionSeed = null;
            this.groupAdminServerAuthenticationPrivateKey = null;
        } else {
            this.blobMainSeed = blobKeys.blobMainSeed;
            this.blobVersionSeed = blobKeys.blobVersionSeed;
            this.groupAdminServerAuthenticationPrivateKey = blobKeys.groupAdminServerAuthenticationPrivateKey;
        }
        this.ownGroupInvitationNonce = ownGroupInvitationNonce;
        this.frozen = frozen;
        this.lastModificationTimestamp = lastModificationTimestamp;
        this.pushTopic = pushTopic;
        this.serializedSharedSettings = serializedSharedSettings;
        this.serializedJsonGroupType = serializedJsonGroupType;
    }

    private ContactGroupV2(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupUid = new UID(res.getBytes(GROUP_UID));
        this.serverUrl = res.getString(SERVER_URL);
        this.category = res.getInt(CATEGORY);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.serializedOwnPermissions = res.getBytes(SERIALIZED_OWN_PERMISSIONS);
        this.version = res.getInt(VERSION);
        this.trustedDetailsVersion = res.getInt(TRUSTED_DETAILS_VERSION);
        this.verifiedAdministratorsChain = res.getBytes(VERIFIED_ADMINISTRATORS_CHAIN);
        byte[] bytes = res.getBytes(BLOB_MAIN_SEED);
        this.blobMainSeed = bytes == null ? null : new Seed(bytes);
        bytes = res.getBytes(BLOB_VERSION_SEED);
        this.blobVersionSeed = bytes == null ? null : new Seed(bytes);
        bytes = res.getBytes(GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY);
        if (bytes == null) {
            this.groupAdminServerAuthenticationPrivateKey = null;
        } else {
            try {
                this.groupAdminServerAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(bytes).decodePrivateKey();
            } catch (DecodingException e) {
                throw new SQLException();
            }
        }
        this.ownGroupInvitationNonce = res.getBytes(OWN_GROUP_INVITATION_NONCE);
        this.frozen = res.getBoolean(FROZEN);
        this.lastModificationTimestamp = res.getLong(LAST_MODIFICATION_TIMESTAMP);
        this.pushTopic = res.getString(PUSH_TOPIC);
        this.serializedSharedSettings = res.getString(SERIALIZED_SHARED_SETTINGS);
        this.serializedJsonGroupType = res.getString(SERIALIZED_JSON_GROUP_TYPE);
    }


    // endregion




    // region Get and Set

    public static GroupV2.ServerBlob getServerBlob(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            throw new SQLException("Called ContactGroupV2.getServerBlob outside of a transaction!");
        }

        ContactGroupV2 group = get(identityManagerSession, ownedIdentity, groupIdentifier);
        if (group == null) {
            return null;
        }
        ContactGroupV2Details groupDetails = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, groupIdentifier, group.version);
        if (groupDetails == null) {
            return null;
        }

        GroupV2.AdministratorsChain administratorsChain;
        try {
            administratorsChain = GroupV2.AdministratorsChain.of(new Encoded(group.verifiedAdministratorsChain));
        } catch (DecodingException e) {
            return null;
        }
        String serializedGroupDetails = groupDetails.getSerializedJsonDetails();
        GroupV2.ServerPhotoInfo serverPhotoInfo = groupDetails.getServerPhotoInfo();

        HashSet<GroupV2.IdentityAndPermissionsAndDetails> groupMemberIdentityAndPermissionsAndDetailsList = new HashSet<>();

        {
            // add own details to the list
            String serializedDetails = OwnedIdentity.getSerializedPublishedDetails(identityManagerSession, ownedIdentity);
            GroupV2.IdentityAndPermissionsAndDetails ownDetails = new GroupV2.IdentityAndPermissionsAndDetails(
                    ownedIdentity,
                    group.getOwnPermissionStrings(),
                    serializedDetails,
                    group.ownGroupInvitationNonce
            );
            groupMemberIdentityAndPermissionsAndDetailsList.add(ownDetails);
        }

        {
            // add group members
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                    "SELECT " +
                            " gm." + ContactGroupV2Member.CONTACT_IDENTITY + " AS ci, " +
                            " gm." + ContactGroupV2Member.SERIALIZED_PERMISSIONS + " AS sp, " +
                            " details." + ContactIdentityDetails.SERIALIZED_JSON_DETAILS + " AS sd, " +
                            " gm." + ContactGroupV2Member.GROUP_INVITATION_NONCE + " AS gin " +
                            " FROM " + ContactGroupV2Member.TABLE_NAME + " AS gm " +
                            " INNER JOIN " + ContactIdentity.TABLE_NAME + " AS contact " +
                            " ON gm." + ContactGroupV2Member.CONTACT_IDENTITY + " = contact." + ContactIdentity.CONTACT_IDENTITY +
                            " AND gm." + ContactGroupV2Member.OWNED_IDENTITY + " = contact." + ContactIdentity.OWNED_IDENTITY +
                            " INNER JOIN " + ContactIdentityDetails.TABLE_NAME + " AS details " +
                            " ON details." + ContactIdentityDetails.CONTACT_IDENTITY + " = contact." + ContactIdentity.CONTACT_IDENTITY +
                            " AND details." + ContactIdentityDetails.OWNED_IDENTITY + " = contact." + ContactIdentity.OWNED_IDENTITY +
                            " AND details." + ContactIdentityDetails.VERSION + " = contact." + ContactIdentity.PUBLISHED_DETAILS_VERSION +
                            " WHERE gm." + ContactGroupV2Member.GROUP_UID + " = ? " +
                            " AND gm." + ContactGroupV2Member.SERVER_URL + " = ? " +
                            " AND gm." + ContactGroupV2Member.CATEGORY + " = ? " +
                            " AND gm." + ContactGroupV2Member.OWNED_IDENTITY + " = ?;"
                    )) {
                statement.setBytes(1, groupIdentifier.groupUid.getBytes());
                statement.setString(2, groupIdentifier.serverUrl);
                statement.setInt(3, groupIdentifier.category);
                statement.setBytes(4, ownedIdentity.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    while (res.next()) {
                        Identity contactIdentity = Identity.of(res.getBytes("ci"));
                        byte[] serializedPermissions = res.getBytes("sp");
                        String serializedDetails = res.getString("sd");
                        byte[] groupInvitationNonce = res.getBytes("gin");

                        groupMemberIdentityAndPermissionsAndDetailsList.add(new GroupV2.IdentityAndPermissionsAndDetails(
                                contactIdentity,
                                GroupV2.Permission.deserializePermissions(serializedPermissions),
                                serializedDetails,
                                groupInvitationNonce
                        ));
                    }
                } catch (DecodingException e) {
                    Logger.x(e);
                    return null;
                }
            }
        }

        {
            // add pending group members
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + ContactGroupV2PendingMember.TABLE_NAME +
                            " WHERE " + ContactGroupV2Member.GROUP_UID + " = ? " +
                            " AND " + ContactGroupV2Member.SERVER_URL + " = ? " +
                            " AND " + ContactGroupV2Member.CATEGORY + " = ? " +
                            " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ?;")) {
                statement.setBytes(1, groupIdentifier.groupUid.getBytes());
                statement.setString(2, groupIdentifier.serverUrl);
                statement.setInt(3, groupIdentifier.category);
                statement.setBytes(4, ownedIdentity.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    while (res.next()) {
                        Identity contactIdentity = Identity.of(res.getBytes(ContactGroupV2PendingMember.CONTACT_IDENTITY));
                        byte[] serializedPermissions = res.getBytes(ContactGroupV2PendingMember.SERIALIZED_PERMISSIONS);
                        String serializedDetails = res.getString(ContactGroupV2PendingMember.SERIALIZED_CONTACT_DETAILS);
                        byte[] groupInvitationNonce = res.getBytes(ContactGroupV2PendingMember.GROUP_INVITATION_NONCE);

                        groupMemberIdentityAndPermissionsAndDetailsList.add(new GroupV2.IdentityAndPermissionsAndDetails(
                                contactIdentity,
                                GroupV2.Permission.deserializePermissions(serializedPermissions),
                                serializedDetails,
                                groupInvitationNonce
                        ));
                    }
                } catch (DecodingException e) {
                    Logger.x(e);
                    return null;
                }
            }
        }

        return new GroupV2.ServerBlob(administratorsChain, groupMemberIdentityAndPermissionsAndDetailsList, group.version, serializedGroupDetails, serverPhotoInfo, group.serializedJsonGroupType);
    }

    public static String getPhotoUrl(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT det." + ContactGroupV2Details.PHOTO_URL + " AS photo " +
                " FROM " + TABLE_NAME + " AS g " +
                " INNER JOIN " + ContactGroupV2Details.TABLE_NAME + " AS det " +
                " ON g." + GROUP_UID + " = det." + ContactGroupV2Details.GROUP_UID +
                " AND g." + SERVER_URL + " = det." + ContactGroupV2Details.SERVER_URL +
                " AND g." + CATEGORY + " = det." + ContactGroupV2Details.CATEGORY +
                " AND g." + OWNED_IDENTITY + " = det." + ContactGroupV2Details.OWNED_IDENTITY +
                " AND g." + VERSION + " = det." + ContactGroupV2Details.VERSION +
                " WHERE g." + GROUP_UID + " = ? " +
                " AND g." + SERVER_URL + " = ? " +
                " AND g." + CATEGORY + " = ? " +
                " AND g." + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getString("photo");
                } else {
                    return null;
                }
            }
        }
    }

    public static Long getLastModificationTimestamp(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " + LAST_MODIFICATION_TIMESTAMP +
                " FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getLong(LAST_MODIFICATION_TIMESTAMP);
                } else {
                    return null;
                }
            }
        }
    }



    public static GroupV2.ServerPhotoInfo getServerPhotoInfo(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " +
                " det." + ContactGroupV2Details.PHOTO_SERVER_IDENTITY + " AS ident, " +
                " det." + ContactGroupV2Details.PHOTO_SERVER_LABEL + " AS label, " +
                " det." + ContactGroupV2Details.PHOTO_SERVER_KEY + " AS key " +
                " FROM " + TABLE_NAME + " AS g " +
                " INNER JOIN " + ContactGroupV2Details.TABLE_NAME + " AS det " +
                " ON g." + GROUP_UID + " = det." + ContactGroupV2Details.GROUP_UID +
                " AND g." + SERVER_URL + " = det." + ContactGroupV2Details.SERVER_URL +
                " AND g." + CATEGORY + " = det." + ContactGroupV2Details.CATEGORY +
                " AND g." + OWNED_IDENTITY + " = det." + ContactGroupV2Details.OWNED_IDENTITY +
                " AND g." + VERSION + " = det." + ContactGroupV2Details.VERSION +
                " WHERE g." + GROUP_UID + " = ? " +
                " AND g." + SERVER_URL + " = ? " +
                " AND g." + CATEGORY + " = ? " +
                " AND g." + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    Identity photoServerIdentity;
                    UID photoServerLabel;
                    AuthEncKey photoServerKey;
                    byte[] bytes = res.getBytes("ident");
                    if (bytes == null) {
                        photoServerIdentity = null;
                    } else {
                        try {
                            photoServerIdentity = Identity.of(bytes);
                        } catch (DecodingException e) {
                            photoServerIdentity = null;
                        }
                    }
                    bytes = res.getBytes("label");
                    if (bytes == null) {
                        photoServerLabel = null;
                    } else {
                        photoServerLabel = new UID(bytes);
                    }
                    bytes = res.getBytes("key");
                    if (bytes == null) {
                        photoServerKey = null;
                    } else {
                        try {
                            photoServerKey = (AuthEncKey) new Encoded(bytes).decodeSymmetricKey();
                        } catch (DecodingException e) {
                            photoServerKey = null;
                        }
                    }
                    if (photoServerIdentity != null && photoServerLabel != null && photoServerKey != null) {
                        return new GroupV2.ServerPhotoInfo(photoServerIdentity, photoServerLabel, photoServerKey);
                    }
                }
                return null;
            }
        }
    }


    public void setDownloadedPhotoUrl(Identity ownedIdentity, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] photo) throws Exception {
        List<ContactGroupV2Details> detailsList = ContactGroupV2Details.getByGroupIdentifierAndServerPhotoInfo(identityManagerSession, ownedIdentity, getGroupIdentifier(), serverPhotoInfo);

        if (detailsList.isEmpty()) {
            return;
        }

        // find a non-existing fileName
        String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator +  Logger.toHexString(groupUid.getBytes());
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

        for (ContactGroupV2Details details : detailsList) {
            // update the details
            details.setPhotoUrl(randFileName);
        }

        // notify that the group photo (trusted or published) changed
        commitHookBits |= HOOK_BIT_PHOTO_UPDATED;
        identityManagerSession.session.addSessionCommitListener(this);
    }

    public static HashSet<GroupV2.IdentityAndPermissions> getGroupV2OtherMembersAndPermissions(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                        " SELECT " + ContactGroupV2Member.CONTACT_IDENTITY + " AS id," +
                        ContactGroupV2Member.OWNED_IDENTITY + " AS oid, " +
                        ContactGroupV2Member.SERIALIZED_PERMISSIONS + " AS perm " +
                        " FROM " + ContactGroupV2Member.TABLE_NAME +
                        " WHERE " + ContactGroupV2Member.GROUP_UID + " = ? " +
                        " AND " + ContactGroupV2Member.SERVER_URL + " = ? " +
                        " AND " + ContactGroupV2Member.CATEGORY + " = ? " +
                        " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ? " +
                        " UNION SELECT " + ContactGroupV2PendingMember.CONTACT_IDENTITY + " AS id, " +
                        ContactGroupV2PendingMember.OWNED_IDENTITY + " AS oid, " +
                        ContactGroupV2PendingMember.SERIALIZED_PERMISSIONS + " AS perm " +
                        " FROM " + ContactGroupV2PendingMember.TABLE_NAME +
                        " WHERE " + ContactGroupV2PendingMember.GROUP_UID + " = ? " +
                        " AND " + ContactGroupV2PendingMember.SERVER_URL + " = ? " +
                        " AND " + ContactGroupV2PendingMember.CATEGORY + " = ? " +
                        " AND " + ContactGroupV2PendingMember.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, groupIdentifier.groupUid.getBytes());
            statement.setString(6, groupIdentifier.serverUrl);
            statement.setInt(7, groupIdentifier.category);
            statement.setBytes(8, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                HashSet<GroupV2.IdentityAndPermissions> set = new HashSet<>();
                while (res.next()) {
                    Identity identity = Identity.of(res.getBytes("id"));
                    HashSet<GroupV2.Permission> permissions = GroupV2.Permission.deserializeKnownPermissions(res.getBytes("perm"));
                    set.add(new GroupV2.IdentityAndPermissions(identity, permissions));
                }
                return set;
            }
        }
    }

    public static boolean getGroupV2HasOtherAdminMember(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            throw new Exception();
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                " SELECT " + ContactGroupV2Member.SERIALIZED_PERMISSIONS + " AS perm " +
                        " FROM " + ContactGroupV2Member.TABLE_NAME +
                        " WHERE " + ContactGroupV2Member.GROUP_UID + " = ? " +
                        " AND " + ContactGroupV2Member.SERVER_URL + " = ? " +
                        " AND " + ContactGroupV2Member.CATEGORY + " = ? " +
                        " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    byte[] serializedPermissions = res.getBytes("perm");
                    if (GroupV2.Permission.deserializeKnownPermissions(serializedPermissions).contains(GroupV2.Permission.GROUP_ADMIN)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public static List<String> getAllKeycloakPushTopics(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        if (ownedIdentity == null) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                " SELECT " + ContactGroupV2.PUSH_TOPIC + " AS pt " +
                        " FROM " + ContactGroupV2.TABLE_NAME +
                        " WHERE " + ContactGroupV2.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_KEYCLOAK +
                        " AND " + ContactGroupV2.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<String> list = new ArrayList<>();
                while (res.next()) {
                    list.add(res.getString("pt"));
                }
                return list;
            }
        }
    }

    public static List<ContactGroupV2> getAllWithPushTopic(IdentityManagerSession identityManagerSession, String pushTopic) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + PUSH_TOPIC + " = ?;")) {
            statement.setString(1, pushTopic);
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static ContactGroupV2 get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
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
                if (res.next()) {
                    return new ContactGroupV2(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static List<ContactGroupV2> getAllForIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static List<ContactGroupV2> getAllKeycloakForIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CATEGORY + " = " + GroupV2.Identifier.CATEGORY_KEYCLOAK + ";")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static List<ContactGroupV2> getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2(identityManagerSession, res));
                }
                return list;
            }
        }
    }

    public static List<ContactGroupV2> getAllKeycloak(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + CATEGORY + " = " + GroupV2.Identifier.CATEGORY_KEYCLOAK + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroupV2> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactGroupV2(identityManagerSession, res));
                }
                return list;
            }
        }
    }


    public void setFrozen(boolean frozen) throws SQLException {
        if (this.frozen == frozen) {
            return;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + FROZEN + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, frozen);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.frozen = frozen;
            commitHookBits |= HOOK_BIT_FROZEN_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setTrustedDetailsVersion(int trustedDetailsVersion) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + TRUSTED_DETAILS_VERSION + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, trustedDetailsVersion);
            statement.setBytes(2, groupUid.getBytes());
            statement.setString(3, serverUrl);
            statement.setInt(4, category);
            statement.setBytes(5, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.trustedDetailsVersion = trustedDetailsVersion;
            this.updatedByMe = true;
            commitHookBits |= HOOK_BIT_UPDATED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }



    public List<Identity> updateWithNewBlob(GroupV2.ServerBlob serverBlob, GroupV2.BlobKeys blobKeys, boolean updatedByMe) throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            throw new SQLException("Calling ContactGroupV2.updateGroupV2WithNewBlob outside a transaction!");
        }
        // check the blob is validated
        if (!serverBlob.administratorsChain.integrityWasChecked) {
            return null;
        }

        // if the blob is outdated, ignore it
        if (version > serverBlob.version) {
            return new ArrayList<>();
        }

        // build a hashmap of group members for easier access
        HashMap<Identity, GroupV2.IdentityAndPermissionsAndDetails> groupMembersMap = new HashMap<>();
        for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
            groupMembersMap.put(identityAndPermissionsAndDetails.identity, identityAndPermissionsAndDetails);
        }


        /////////////////////////////////////
        // update the local ContactGroupV2 fields

        // check I am indeed in the group (and remove myself from the map)
        GroupV2.IdentityAndPermissionsAndDetails ownIdentityAndPermissionsAndDetails = groupMembersMap.remove(ownedIdentity);
        if (ownIdentityAndPermissionsAndDetails == null) {
            return null;
        }

        // check the previous chain is a prefix of the new chain
        try {
            if (!serverBlob.administratorsChain.isPrefixedBy(GroupV2.AdministratorsChain.of(new Encoded(verifiedAdministratorsChain)))) {
                return null;
            }
        } catch (DecodingException e) {
            Logger.x(e);
            return null;
        }

        // update group fields
        serializedOwnPermissions = GroupV2.Permission.serializePermissionStrings(ownIdentityAndPermissionsAndDetails.permissionStrings);
        ownGroupInvitationNonce = ownIdentityAndPermissionsAndDetails.groupInvitationNonce;
        verifiedAdministratorsChain = serverBlob.administratorsChain.encode().getBytes();
        blobMainSeed = blobKeys.blobMainSeed;
        blobVersionSeed = blobKeys.blobVersionSeed;
        groupAdminServerAuthenticationPrivateKey = blobKeys.groupAdminServerAuthenticationPrivateKey;
        lastModificationTimestamp = System.currentTimeMillis();
        serializedJsonGroupType = serverBlob.serializedGroupType;

        // create the new group details
        GroupV2.Identifier groupIdentifier = getGroupIdentifier();
        ContactGroupV2Details publishedDetails = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, groupIdentifier, version);
        if (serverBlob.version != version) {
            ContactGroupV2Details newDetails = ContactGroupV2Details.create(identityManagerSession, ownedIdentity, groupIdentifier, serverBlob.version, serverBlob.serializedGroupDetails, serverBlob.serverPhotoInfo);
            if (newDetails == null) {
                return null;
            }
            if (serverBlob.serverPhotoInfo != null
                    && serverBlob.serverPhotoInfo.equals(publishedDetails.getServerPhotoInfo())
                    && publishedDetails.getPhotoUrl() != null) {
                // photo is the same, copy the photoUrl
                newDetails.setPhotoUrl(publishedDetails.getPhotoUrl());
            } else {
                if (Objects.equals(publishedDetails.getPhotoServerIdentity(), ownedIdentity)) {
                    // serverPhotoInfo changed and I was the previous upload, notify the user data can be deleted
                    labelToDelete = publishedDetails.getPhotoServerLabel();
                    commitHookBits |= HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED;
                }
                if (Objects.equals(newDetails.getPhotoServerIdentity(), ownedIdentity)) {
                    // new photo is owned by me --> create the ServerUserData to maintain it
                    ServerUserData.createForGroupV2(identityManagerSession, ownedIdentity, newDetails.getPhotoServerLabel(), getGroupIdentifier().getBytes());
                }
            }
            version = newDetails.getVersion();
        }

        // update the group in DB
        update();
        this.updatedByMe = updatedByMe;
        commitHookBits |= HOOK_BIT_UPDATED;
        identityManagerSession.session.addSessionCommitListener(this);

        // we do not check if we can auto-trust the new details --> this is the App's job
        // cleanup any obsolete details (after the update)
        ContactGroupV2Details.cleanup(identityManagerSession, ownedIdentity, groupIdentifier, version, trustedDetailsVersion);

        //////////////////////////
        // Now, update the members and pending members

        List<Identity> membersWithNewInvitationNonce = new ArrayList<>();

        try {
            for (ContactGroupV2Member contactGroupV2Member : ContactGroupV2Member.getAll(identityManagerSession, ownedIdentity, groupIdentifier)) {
                GroupV2.IdentityAndPermissionsAndDetails newPermissionsAndDetails = groupMembersMap.get(contactGroupV2Member.getContactIdentity());
                if (newPermissionsAndDetails == null) {
                    // user was removed from the group
                    contactGroupV2Member.delete();
                } else if (!Arrays.equals(contactGroupV2Member.getGroupInvitationNonce(), newPermissionsAndDetails.groupInvitationNonce)) {
                    // nonce changed --> member must be moved to pending members
                    //  - delete the member
                    //  - do not remove from groupMembersMap so that it is added to pending members a few lines below
                    contactGroupV2Member.delete();
                } else {
                    // remove the member from the map
                    groupMembersMap.remove(contactGroupV2Member.getContactIdentity());
                    // check if permissions are equal
                    if (!new HashSet<>(GroupV2.Permission.deserializePermissions(contactGroupV2Member.getSerializedPermissions())).equals(new HashSet<>(newPermissionsAndDetails.permissionStrings))) {
                        contactGroupV2Member.setPermissions(newPermissionsAndDetails.permissionStrings);
                    }

                }
            }

            for (ContactGroupV2PendingMember contactGroupV2PendingMember : ContactGroupV2PendingMember.getAll(identityManagerSession, ownedIdentity, groupIdentifier)) {
                GroupV2.IdentityAndPermissionsAndDetails newPermissionsAndDetails = groupMembersMap.remove(contactGroupV2PendingMember.getContactIdentity());
                if (newPermissionsAndDetails == null) {
                    // pending member was removed from the group
                    contactGroupV2PendingMember.delete();
                } else  {
                    // check if permissions are equal
                    if (!new HashSet<>(GroupV2.Permission.deserializePermissions(contactGroupV2PendingMember.getSerializedPermissions())).equals(new HashSet<>(newPermissionsAndDetails.permissionStrings))) {
                        contactGroupV2PendingMember.setPermissions(newPermissionsAndDetails.permissionStrings);
                    }

                    // check the invitation nonce
                    if (!Arrays.equals(contactGroupV2PendingMember.getGroupInvitationNonce(), newPermissionsAndDetails.groupInvitationNonce)) {
                        contactGroupV2PendingMember.setGroupInvitationNonce(newPermissionsAndDetails.groupInvitationNonce);
                        membersWithNewInvitationNonce.add(contactGroupV2PendingMember.getContactIdentity());
                    }

                    // check the serialized details
                    if (!contactGroupV2PendingMember.getSerializedContactDetails().equals(newPermissionsAndDetails.serializedIdentityDetails)) {
                        contactGroupV2PendingMember.setSerializedContactDetails(newPermissionsAndDetails.serializedIdentityDetails);
                    }
                }
            }

            // add all remaining members to ContactGroupV2PendingMember db
            for (GroupV2.IdentityAndPermissionsAndDetails pendingGroupMember : groupMembersMap.values()) {
                membersWithNewInvitationNonce.add(pendingGroupMember.identity);
                ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.create(
                        identityManagerSession,
                        ownedIdentity,
                        groupIdentifier,
                        pendingGroupMember.identity,
                        pendingGroupMember.serializedIdentityDetails,
                        pendingGroupMember.permissionStrings,
                        pendingGroupMember.groupInvitationNonce);

                if (pendingMember == null) {
                    throw new Exception("Unable to create new ContactGroupV2PendingMember");
                }
            }
        } catch (Exception e) {
            Logger.x(e);
            Logger.w("Error while updating group members from new serverBlob");
            return null;
        }

        return membersWithNewInvitationNonce;
    }

    public static List<Identity> getGroupV2MembersAndPendingMembersFromNonce(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, byte[] groupMemberInvitationNonce) throws Exception {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                " SELECT " + ContactGroupV2Member.CONTACT_IDENTITY + " AS id " +
                        " FROM " + ContactGroupV2Member.TABLE_NAME +
                        " WHERE " + ContactGroupV2Member.GROUP_UID + " = ? " +
                        " AND " + ContactGroupV2Member.SERVER_URL + " = ? " +
                        " AND " + ContactGroupV2Member.CATEGORY + " = ? " +
                        " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2Member.GROUP_INVITATION_NONCE + " = ? " +
                        " UNION SELECT " + ContactGroupV2PendingMember.CONTACT_IDENTITY + " AS id " +
                        " FROM " + ContactGroupV2PendingMember.TABLE_NAME +
                        " WHERE " + ContactGroupV2PendingMember.GROUP_UID + " = ? " +
                        " AND " + ContactGroupV2PendingMember.SERVER_URL + " = ? " +
                        " AND " + ContactGroupV2PendingMember.CATEGORY + " = ? " +
                        " AND " + ContactGroupV2PendingMember.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2PendingMember.GROUP_INVITATION_NONCE + " = ?;"
        )) {
            statement.setBytes(1, groupIdentifier.groupUid.getBytes());
            statement.setString(2, groupIdentifier.serverUrl);
            statement.setInt(3, groupIdentifier.category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, groupMemberInvitationNonce);
            statement.setBytes(6, groupIdentifier.groupUid.getBytes());
            statement.setString(7, groupIdentifier.serverUrl);
            statement.setInt(8, groupIdentifier.category);
            statement.setBytes(9, ownedIdentity.getBytes());
            statement.setBytes(10, groupMemberInvitationNonce);
            try (ResultSet res = statement.executeQuery()) {
                List<Identity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(Identity.of(res.getBytes("id")));
                }
                return list;
            }
        }
    }

    public void movePendingMemberToMembers(Identity groupMemberIdentity) throws Exception {
        if (!identityManagerSession.session.isInTransaction()) {
            throw new Exception("Called ContactGroupV2.movePendingMemberToMembers outside a transaction");
        }

        ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.get(identityManagerSession, ownedIdentity, getGroupIdentifier(), groupMemberIdentity);
        if (pendingMember == null) {
            return;
        }

        ContactGroupV2Member member = ContactGroupV2Member.get(identityManagerSession, ownedIdentity, getGroupIdentifier(), groupMemberIdentity);
        if (member == null) { // this should always be the case
            // add a contact if we don't have one, or add a trust origin
            if (category != GroupV2.Identifier.CATEGORY_KEYCLOAK) {
                if (!identityManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(identityManagerSession.session, ownedIdentity, groupMemberIdentity)) {
                    identityManagerSession.identityDelegate.addContactIdentity(identityManagerSession.session, groupMemberIdentity, pendingMember.getSerializedContactDetails(), ownedIdentity, TrustOrigin.createServerGroupV2TrustOrigin(System.currentTimeMillis(), getGroupIdentifier()), false);
                } else {
                    identityManagerSession.identityDelegate.addTrustOriginToContact(identityManagerSession.session, groupMemberIdentity, ownedIdentity, TrustOrigin.createServerGroupV2TrustOrigin(System.currentTimeMillis(), getGroupIdentifier()), false);
                }
            } else {
                // for keycloak groups, don't add a trust origin for each group, only have one keycloak origin
                if (!identityManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(identityManagerSession.session, ownedIdentity, groupMemberIdentity)) {
                    identityManagerSession.identityDelegate.addContactIdentity(identityManagerSession.session, groupMemberIdentity, pendingMember.getSerializedContactDetails(), ownedIdentity, TrustOrigin.createKeycloakTrustOrigin(System.currentTimeMillis(), serverUrl), false);
                }
            }

            // for Keycloak groups, before moving a pending member to actual member, check their details are actually keycloak certified
            // (still, we do add the contact before this so that they have a chance to present new updated and validly signed details)
            if (category != GroupV2.Identifier.CATEGORY_KEYCLOAK || identityManagerSession.identityDelegate.isContactIdentityCertifiedByOwnKeycloak(identityManagerSession.session, ownedIdentity, groupMemberIdentity)) {
                // crate the ContactGroupV2Member
                member = ContactGroupV2Member.create(identityManagerSession, ownedIdentity, getGroupIdentifier(), groupMemberIdentity, GroupV2.Permission.deserializePermissions(pendingMember.getSerializedPermissions()), pendingMember.getGroupInvitationNonce());
                if (member == null) {
                    throw new Exception("In ContactGroupV2.movePendingMemberToMembers, failed to create ContactGroupV2Member");
                }
                // delete the pending member
                pendingMember.delete();
            }
        } else {
            // only delete the pending member
            pendingMember.delete();
        }

        this.updatedByMe = false;
        commitHookBits |= HOOK_BIT_UPDATED;
        identityManagerSession.session.addSessionCommitListener(this);
    }


    public static GroupV2.IdentifierVersionAndKeys[] getServerGroupsV2IdentifierVersionAndKeysForContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM (SELECT " + ContactGroupV2Member.GROUP_UID + " AS uid, " +
                        ContactGroupV2Member.SERVER_URL + " AS url, " +
                        ContactGroupV2Member.SERIALIZED_PERMISSIONS + " AS perms " +
                        " FROM " + ContactGroupV2Member.TABLE_NAME +
                        " WHERE " + ContactGroupV2Member.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2Member.CONTACT_IDENTITY + " = ? " +
                        " UNION SELECT " + ContactGroupV2PendingMember.GROUP_UID + " AS uid, " +
                        ContactGroupV2PendingMember.SERVER_URL + " AS url, " +
                        ContactGroupV2PendingMember.SERIALIZED_PERMISSIONS + " AS perms " +
                        " FROM " + ContactGroupV2PendingMember.TABLE_NAME +
                        " WHERE " + ContactGroupV2PendingMember.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND " + ContactGroupV2PendingMember.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2PendingMember.CONTACT_IDENTITY + " = ?) AS gmj " +
                        " INNER JOIN " + ContactGroupV2.TABLE_NAME + " AS gr " +
                        " ON gmj.uid = gr." + ContactGroupV2.GROUP_UID +
                        " AND gmj.url = gr." + ContactGroupV2.SERVER_URL +
                        " AND gr." + ContactGroupV2.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND gr." + ContactGroupV2.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, contactIdentity.getBytes());
            statement.setBytes(5, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                ArrayList<GroupV2.IdentifierVersionAndKeys> list = new ArrayList<>();

                while (res.next()) {
                    byte[] groupUid = res.getBytes("uid");
                    String serverUrl = res.getString("url");
                    byte[] serializedContactPermissions = res.getBytes("perms");
                    int version = res.getInt(ContactGroupV2.VERSION);

                    byte[] bytesMainSeed = res.getBytes(ContactGroupV2.BLOB_MAIN_SEED);
                    byte[] bytesVersionSeed = res.getBytes(ContactGroupV2.BLOB_VERSION_SEED);
                    ServerAuthenticationPrivateKey serverAuthenticationPrivateKey = null;
                    // only include the serverAuthenticationPrivateKey if both I and the contact are admin
                    if (GroupV2.Permission.deserializeKnownPermissions(serializedContactPermissions).contains(GroupV2.Permission.GROUP_ADMIN)) {
                        byte[] bytesGroupAdminKey = res.getBytes(ContactGroupV2.GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY); // this is non-null only when I am admin
                        if (bytesGroupAdminKey != null) {
                            try {
                                serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(bytesGroupAdminKey).decodePrivateKey();
                            } catch (Exception ignored) { }
                        }
                    }

                    GroupV2.BlobKeys blobKeys = new GroupV2.BlobKeys(
                            new Seed(bytesMainSeed),
                            new Seed(bytesVersionSeed),
                            serverAuthenticationPrivateKey);


                    list.add(new GroupV2.IdentifierVersionAndKeys(
                            new GroupV2.Identifier(new UID(groupUid), serverUrl, GroupV2.Identifier.CATEGORY_SERVER),
                            version,
                            blobKeys
                    ));
                }
                return list.toArray(new GroupV2.IdentifierVersionAndKeys[0]);
            }
        }
    }

    public static GroupV2.IdentifierVersionAndKeys[] getAllServerGroupsV2IdentifierVersionAndKeys(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM " + ContactGroupV2.TABLE_NAME +
                        " WHERE " + ContactGroupV2.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND " + ContactGroupV2.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                ArrayList<GroupV2.IdentifierVersionAndKeys> list = new ArrayList<>();

                while (res.next()) {
                    byte[] groupUid = res.getBytes(ContactGroupV2.GROUP_UID);
                    String serverUrl = res.getString(ContactGroupV2.SERVER_URL);
                    int version = res.getInt(ContactGroupV2.VERSION);
                    byte[] bytesMainSeed = res.getBytes(ContactGroupV2.BLOB_MAIN_SEED);
                    byte[] bytesVersionSeed = res.getBytes(ContactGroupV2.BLOB_VERSION_SEED);
                    ServerAuthenticationPrivateKey serverAuthenticationPrivateKey = null;
                    byte[] bytesGroupAdminKey = res.getBytes(ContactGroupV2.GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY); // this is non-null only when I am admin
                    if (bytesGroupAdminKey != null) {
                        try {
                            serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(bytesGroupAdminKey).decodePrivateKey();
                        } catch (Exception ignored) { }
                    }

                    GroupV2.BlobKeys blobKeys = new GroupV2.BlobKeys(
                            new Seed(bytesMainSeed),
                            new Seed(bytesVersionSeed),
                            serverAuthenticationPrivateKey);


                    list.add(new GroupV2.IdentifierVersionAndKeys(
                            new GroupV2.Identifier(new UID(groupUid), serverUrl, GroupV2.Identifier.CATEGORY_SERVER),
                            version,
                            blobKeys
                    ));
                }
                return list.toArray(new GroupV2.IdentifierVersionAndKeys[0]);
            }
        }
    }


    public static GroupV2.IdentifierAndAdminStatus[] getServerGroupsV2IdentifierAndMyAdminStatusForContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM (SELECT " + ContactGroupV2Member.GROUP_UID + " AS uid, " +
                        ContactGroupV2Member.SERVER_URL + " AS url " +
                        " FROM " + ContactGroupV2Member.TABLE_NAME +
                        " WHERE " + ContactGroupV2Member.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND " + ContactGroupV2Member.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2Member.CONTACT_IDENTITY + " = ? " +
                        " UNION SELECT " + ContactGroupV2PendingMember.GROUP_UID + " AS uid, " +
                        ContactGroupV2PendingMember.SERVER_URL + " AS url " +
                        " FROM " + ContactGroupV2PendingMember.TABLE_NAME +
                        " WHERE " + ContactGroupV2PendingMember.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND " + ContactGroupV2PendingMember.OWNED_IDENTITY + " = ? " +
                        " AND " + ContactGroupV2PendingMember.CONTACT_IDENTITY + " = ?) AS gmj " +
                        " INNER JOIN " + ContactGroupV2.TABLE_NAME + " AS gr " +
                        " ON gmj.uid = gr." + ContactGroupV2.GROUP_UID +
                        " AND gmj.url = gr." + ContactGroupV2.SERVER_URL +
                        " AND gr." + ContactGroupV2.CATEGORY + " = " + GroupV2.Identifier.CATEGORY_SERVER +
                        " AND gr." + ContactGroupV2.OWNED_IDENTITY + " = ?;"
        )) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.setBytes(4, contactIdentity.getBytes());
            statement.setBytes(5, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                ArrayList<GroupV2.IdentifierAndAdminStatus> list = new ArrayList<>();

                while (res.next()) {
                    byte[] groupUid = res.getBytes("uid");
                    String serverUld = res.getString("url");
                    byte[] serializedOwnPermissions = res.getBytes(ContactGroupV2.SERIALIZED_OWN_PERMISSIONS);

                    list.add(new GroupV2.IdentifierAndAdminStatus(
                            new GroupV2.Identifier(new UID(groupUid), serverUld, GroupV2.Identifier.CATEGORY_SERVER),
                            GroupV2.Permission.deserializeKnownPermissions(serializedOwnPermissions).contains(GroupV2.Permission.GROUP_ADMIN)
                    ));
                }
                return list.toArray(new GroupV2.IdentifierAndAdminStatus[0]);
            }
        }
    }

    public KeycloakGroupV2UpdateOutput updateWithNewKeycloakBlob(KeycloakGroupBlob keycloakGroupBlob, ObjectMapper jsonObjectMapper) throws Exception {
        GroupV2.Identifier groupIdentifier = getGroupIdentifier();

        // build a hashmap of group members for easier access
        HashMap<Identity, KeycloakGroupMemberAndPermissions> groupMembersMap = new HashMap<>();
        for (KeycloakGroupMemberAndPermissions groupMemberAndPermissions : keycloakGroupBlob.groupMembersAndPermissions) {
            Identity memberIdentity = Identity.of(groupMemberAndPermissions.identity);
            groupMembersMap.put(memberIdentity, groupMemberAndPermissions);
        }

        // get my own updated information
        KeycloakGroupMemberAndPermissions ownKeycloakGroupMemberAndPermissions = groupMembersMap.remove(ownedIdentity);
        if (ownKeycloakGroupMemberAndPermissions == null) {
            return null;
        }



        // update the ContactGroupV2
        this.ownGroupInvitationNonce = ownKeycloakGroupMemberAndPermissions.groupInvitationNonce;
        this.serializedOwnPermissions = GroupV2.Permission.serializePermissionStrings(ownKeycloakGroupMemberAndPermissions.permissions);
        this.lastModificationTimestamp = keycloakGroupBlob.timestamp;
        if (!Objects.equals(this.pushTopic, keycloakGroupBlob.pushTopic)) {
            this.pushTopic = keycloakGroupBlob.pushTopic;
            commitHookBits |= HOOK_BIT_NEW_PUSH_TOPIC;
        }
        if (!Objects.equals(this.serializedSharedSettings, keycloakGroupBlob.serializedSharedSettings)) {
            this.serializedSharedSettings = keycloakGroupBlob.serializedSharedSettings;
            identityManagerSession.session.addSessionCommitListener(() -> {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY, ownedIdentity);
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_GROUP_IDENTIFIER_KEY, groupIdentifier);
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SERIALIZED_SHARED_SETTINGS_KEY, keycloakGroupBlob.serializedSharedSettings);
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY, keycloakGroupBlob.timestamp);
                identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS, userInfo);
            });
        }
        update();

        // create the new group details
        GroupV2.ServerPhotoInfo serverPhotoInfo = null;
        if (keycloakGroupBlob.photoUid != null && keycloakGroupBlob.encodedPhotoKey != null) {
            try {
                UID photoUid = new UID(keycloakGroupBlob.photoUid);
                AuthEncKey photoKey = (AuthEncKey) new Encoded(keycloakGroupBlob.encodedPhotoKey).decodeSymmetricKey();

                serverPhotoInfo = new GroupV2.ServerPhotoInfo(null, photoUid, photoKey);
            } catch (Exception ignored) {
                // can't get the photo info --> ignore the photo
            }
        }

        ContactGroupV2Details updatedDetails = ContactGroupV2Details.createOrUpdateKeycloak(identityManagerSession, ownedIdentity, groupIdentifier, jsonObjectMapper.writeValueAsString(keycloakGroupBlob.groupDetails), serverPhotoInfo);

        if (updatedDetails == null) {
            return null;
        }

        boolean photoNeedsToBeDownloaded = serverPhotoInfo != null && updatedDetails.getPhotoUrl() == null;

        //////////////////////////
        // Now, update the members and pending members

        List<Identity> membersWithNewInvitationNonce = new ArrayList<>();

        try {
            for (ContactGroupV2Member contactGroupV2Member : ContactGroupV2Member.getAll(identityManagerSession, ownedIdentity, groupIdentifier)) {
                KeycloakGroupMemberAndPermissions newPermissionsAndDetails = groupMembersMap.get(contactGroupV2Member.getContactIdentity());
                if (newPermissionsAndDetails == null) {
                    // user was removed from the group
                    contactGroupV2Member.delete();
                } else if (!Arrays.equals(contactGroupV2Member.getGroupInvitationNonce(), newPermissionsAndDetails.groupInvitationNonce)) {
                    // nonce changed --> member must be moved to pending members
                    //  - delete the member
                    //  - do not remove from groupMembersMap so that it is added to pending members a few lines below
                    contactGroupV2Member.delete();
                } else {
                    // remove the member from the map
                    groupMembersMap.remove(contactGroupV2Member.getContactIdentity());
                    // check if permissions are equal
                    if (!Objects.equals(
                            new HashSet<>(GroupV2.Permission.deserializePermissions(contactGroupV2Member.getSerializedPermissions())),
                            new HashSet<>(newPermissionsAndDetails.permissions))) {
                        contactGroupV2Member.setPermissions(newPermissionsAndDetails.permissions);
                    }
                }
            }

            JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                    .setSkipSignatureVerification()
                    .setSkipAllValidators()
                    .build();

            for (ContactGroupV2PendingMember contactGroupV2PendingMember : ContactGroupV2PendingMember.getAll(identityManagerSession, ownedIdentity, groupIdentifier)) {
                KeycloakGroupMemberAndPermissions newPermissionsAndDetails = groupMembersMap.remove(contactGroupV2PendingMember.getContactIdentity());
                if (newPermissionsAndDetails == null) {
                    // pending member was removed from the group
                    contactGroupV2PendingMember.delete();
                } else  {
                    // check if permissions are equal
                    if (!Objects.equals(
                            new HashSet<>(GroupV2.Permission.deserializePermissions(contactGroupV2PendingMember.getSerializedPermissions())),
                            new HashSet<>(newPermissionsAndDetails.permissions))) {
                        contactGroupV2PendingMember.setPermissions(newPermissionsAndDetails.permissions);
                    }

                    // check the invitation nonce
                    if (!Arrays.equals(contactGroupV2PendingMember.getGroupInvitationNonce(), newPermissionsAndDetails.groupInvitationNonce)) {
                        contactGroupV2PendingMember.setGroupInvitationNonce(newPermissionsAndDetails.groupInvitationNonce);
                        membersWithNewInvitationNonce.add(contactGroupV2PendingMember.getContactIdentity());
                    }

                    // check the serialized details
                    String serializedUnsignedDetails = noVerificationConsumer.processToClaims(newPermissionsAndDetails.signedUserDetails).getRawJson();
                    JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(serializedUnsignedDetails, JsonKeycloakUserDetails.class);
                    JsonIdentityDetails jsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(newPermissionsAndDetails.signedUserDetails);
                    String serializedIdentityDetails = jsonObjectMapper.writeValueAsString(jsonIdentityDetails);

                    if (!contactGroupV2PendingMember.getSerializedContactDetails().equals(serializedIdentityDetails)) {
                        contactGroupV2PendingMember.setSerializedContactDetails(serializedIdentityDetails);
                    }
                }
            }

            // add all remaining members to ContactGroupV2PendingMember db
            for (Map.Entry<Identity, KeycloakGroupMemberAndPermissions> entrySet : groupMembersMap.entrySet()) {
                Identity pendingMemberIdentity = entrySet.getKey();
                KeycloakGroupMemberAndPermissions pendingGroupMember = entrySet.getValue();

                membersWithNewInvitationNonce.add(pendingMemberIdentity);

                String serializedUnsignedDetails = noVerificationConsumer.processToClaims(pendingGroupMember.signedUserDetails).getRawJson();
                JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(serializedUnsignedDetails, JsonKeycloakUserDetails.class);
                JsonIdentityDetails jsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(pendingGroupMember.signedUserDetails);
                String serializedIdentityDetails = jsonObjectMapper.writeValueAsString(jsonIdentityDetails);

                ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.create(
                        identityManagerSession,
                        ownedIdentity,
                        groupIdentifier,
                        pendingMemberIdentity,
                        serializedIdentityDetails,
                        pendingGroupMember.permissions,
                        pendingGroupMember.groupInvitationNonce);

                if (pendingMember == null) {
                    throw new Exception("Unable to create new ContactGroupV2PendingMember");
                }
            }
        } catch (Exception e) {
            Logger.x(e);
            Logger.w("Error while updating group members from new serverBlob");
            return null;
        }

        commitHookBits |= HOOK_BIT_UPDATED;
        identityManagerSession.session.addSessionCommitListener(this);

        return new KeycloakGroupV2UpdateOutput(ownGroupInvitationNonce, photoNeedsToBeDownloaded, membersWithNewInvitationNonce);
    }

    public static void deleteAllKeycloakGroupsForOwnedIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        for (ContactGroupV2 contactGroupV2 : getAllKeycloakForIdentity(identityManagerSession, ownedIdentity)) {
            contactGroupV2.delete();
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
                    SERIALIZED_OWN_PERMISSIONS + " BLOB NOT NULL, " +
                    VERSION + " INT NOT NULL, " +
                    TRUSTED_DETAILS_VERSION + " INT NOT NULL, " +
                    VERIFIED_ADMINISTRATORS_CHAIN + " BLOB, " +
                    BLOB_MAIN_SEED + " BLOB, " +
                    BLOB_VERSION_SEED + " BLOB, " +
                    GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY + " BLOB, " +
                    OWN_GROUP_INVITATION_NONCE + " BLOB NOT NULL, " +
                    FROZEN + " BIT NOT NULL, " +
                    LAST_MODIFICATION_TIMESTAMP + " INTEGER NOT NULL, " +
                    PUSH_TOPIC + " TEXT, " +
                    SERIALIZED_SHARED_SETTINGS + " TEXT, " +
                    SERIALIZED_JSON_GROUP_TYPE + " TEXT, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + "), " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + "(" + OwnedIdentity.OWNED_IDENTITY + ")," +
                    " FOREIGN KEY (" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + ", " + VERSION + ") REFERENCES " + ContactGroupV2Details.TABLE_NAME + "(" + ContactGroupV2Details.GROUP_UID + ", " + ContactGroupV2Details.SERVER_URL + ", " + ContactGroupV2Details.CATEGORY + ", " + ContactGroupV2Details.OWNED_IDENTITY + ", " + ContactGroupV2Details.VERSION + ")," +
                    " FOREIGN KEY (" + GROUP_UID + ", " + SERVER_URL + ", " + CATEGORY + ", " + OWNED_IDENTITY + ", " + TRUSTED_DETAILS_VERSION + ") REFERENCES " + ContactGroupV2Details.TABLE_NAME + "(" + ContactGroupV2Details.GROUP_UID + ", " + ContactGroupV2Details.SERVER_URL + ", " + ContactGroupV2Details.CATEGORY + ", " + ContactGroupV2Details.OWNED_IDENTITY + ", " + ContactGroupV2Details.VERSION + ") );");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                Logger.d("CREATING contact_group_v2 DATABASE FOR VERSION 32");
                statement.execute("CREATE TABLE contact_group_v2 (" +
                        "group_uid BLOB NOT NULL, " +
                        "server_url TEXT NOT NULL, " +
                        "category INT NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "serialized_own_permissions BLOB NOT NULL, " +
                        "version INT NOT NULL, " +
                        "trusted_details_version INT NOT NULL, " +
                        "verified_administrators_chain BLOB NOT NULL, " +
                        "blob_main_seed BLOB NOT NULL, " +
                        "blob_version_seed BLOB NOT NULL, " +
                        "group_admin_server_authentication_private_key BLOB, " +
                        "own_group_invitation_nonce BLOB NOT NULL, " +
                        "frozen BIT NOT NULL, " +
                        " CONSTRAINT PK_contact_group_v2 PRIMARY KEY(group_uid, server_url, category, owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity (identity)," +
                        " FOREIGN KEY (group_uid, server_url, category, owned_identity, version) REFERENCES contact_group_v2_details (group_uid, server_url, category, owned_identity, version)," +
                        " FOREIGN KEY (group_uid, server_url, category, owned_identity, trusted_details_version) REFERENCES contact_group_v2_details (group_uid, server_url, category, owned_identity, version) );");
            }
            oldVersion = 32;
        }
        if (oldVersion < 34 && newVersion >= 34) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `contact_group_v2` DATABASE FROM VERSION " + oldVersion + " to 34");
                statement.execute("CREATE TABLE contact_group_v2_new (" +
                        "group_uid BLOB NOT NULL, " +
                        "server_url TEXT NOT NULL, " +
                        "category INT NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "serialized_own_permissions BLOB NOT NULL, " +
                        "version INT NOT NULL, " +
                        "trusted_details_version INT NOT NULL, " +
                        "verified_administrators_chain BLOB, " +
                        "blob_main_seed BLOB, " +
                        "blob_version_seed BLOB, " +
                        "group_admin_server_authentication_private_key BLOB, " +
                        "own_group_invitation_nonce BLOB NOT NULL, " +
                        "frozen BIT NOT NULL, " +
                        "last_modification_timestamp INTEGER NOT NULL, " +
                        "push_topic TEXT, " +
                        "serialized_shared_settings TEXT, " +
                        " CONSTRAINT PK_contact_group_v2 PRIMARY KEY(group_uid, server_url, category, owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity (identity)," +
                        " FOREIGN KEY (group_uid, server_url, category, owned_identity, version) REFERENCES contact_group_v2_details (group_uid, server_url, category, owned_identity, version)," +
                        " FOREIGN KEY (group_uid, server_url, category, owned_identity, trusted_details_version) REFERENCES contact_group_v2_details (group_uid, server_url, category, owned_identity, version) );");
                statement.execute("INSERT INTO contact_group_v2_new " +
                        " SELECT group_uid, server_url, category, owned_identity, serialized_own_permissions, version, trusted_details_version, verified_administrators_chain, blob_main_seed, blob_version_seed, group_admin_server_authentication_private_key, own_group_invitation_nonce, frozen, 0, NULL, NULL " +
                        " FROM contact_group_v2");
                statement.execute("DROP TABLE contact_group_v2");
                statement.execute("ALTER TABLE contact_group_v2_new RENAME TO contact_group_v2");
            }
            oldVersion = 34;
        }
        if (oldVersion < 35 && newVersion >= 35) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `contact_group_v2` DATABASE FROM VERSION " + oldVersion + " to 35");
                statement.execute("ALTER TABLE contact_group_v2 ADD COLUMN `serialized_json_group_type` TEXT DEFAULT NULL");
            }
        }
    }

    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?);")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.setBytes(5, serializedOwnPermissions);

            statement.setInt(6, version);
            statement.setInt(7, trustedDetailsVersion);
            statement.setBytes(8, verifiedAdministratorsChain);
            statement.setBytes(9, blobMainSeed == null ? null : blobMainSeed.getBytes());
            statement.setBytes(10, blobVersionSeed == null ? null : blobVersionSeed.getBytes());

            statement.setBytes(11, groupAdminServerAuthenticationPrivateKey == null ? null : Encoded.of(groupAdminServerAuthenticationPrivateKey).getBytes());
            statement.setBytes(12, ownGroupInvitationNonce);
            statement.setBoolean(13, frozen);
            statement.setLong(14, lastModificationTimestamp);
            statement.setString(15, pushTopic);
            statement.setString(16, serializedSharedSettings);
            statement.setString(17, serializedJsonGroupType);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Running ContactGroupV2.delete() outside a transaction");
            throw new SQLException();
        }

        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_DELETED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + ContactGroupV2Details.TABLE_NAME +
                " WHERE " + ContactGroupV2Details.GROUP_UID + " = ? " +
                " AND " + ContactGroupV2Details.SERVER_URL + " = ? " +
                " AND " + ContactGroupV2Details.CATEGORY + " = ? " +
                " AND " + ContactGroupV2Details.OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupUid.getBytes());
            statement.setString(2, serverUrl);
            statement.setInt(3, category);
            statement.setBytes(4, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    private void update() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + SERIALIZED_OWN_PERMISSIONS + " = ?, " +
                VERSION + " = ?, " +
                TRUSTED_DETAILS_VERSION + " = ?, " +
                VERIFIED_ADMINISTRATORS_CHAIN + " = ?, " +
                BLOB_MAIN_SEED + " = ?, " +
                BLOB_VERSION_SEED + " = ?, " +
                GROUP_ADMIN_SERVER_AUTHENTICATION_PRIVATE_KEY + " = ?, " +
                OWN_GROUP_INVITATION_NONCE + " = ?, " +
                FROZEN + " = ?, " +
                LAST_MODIFICATION_TIMESTAMP + " = ?, " +
                PUSH_TOPIC + " = ?, " +
                SERIALIZED_SHARED_SETTINGS + " = ?, " +
                SERIALIZED_JSON_GROUP_TYPE + " = ? " +
                " WHERE " + GROUP_UID + " = ? " +
                " AND " + SERVER_URL + " = ? " +
                " AND " + CATEGORY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, serializedOwnPermissions);
            statement.setInt(2, version);
            statement.setInt(3, trustedDetailsVersion);
            statement.setBytes(4, verifiedAdministratorsChain);
            statement.setBytes(5, blobVersionSeed == null ? null : blobMainSeed.getBytes());

            statement.setBytes(6, blobVersionSeed == null ? null : blobVersionSeed.getBytes());
            statement.setBytes(7, groupAdminServerAuthenticationPrivateKey == null ? null : Encoded.of(groupAdminServerAuthenticationPrivateKey).getBytes());
            statement.setBytes(8, ownGroupInvitationNonce);
            statement.setBoolean(9, frozen);
            statement.setLong(10, lastModificationTimestamp);

            statement.setString(11, pushTopic);
            statement.setString(12, serializedSharedSettings);

            statement.setString(13, serializedJsonGroupType);

            statement.setBytes(14, groupUid.getBytes());
            statement.setString(15, serverUrl);
            statement.setInt(16, category);
            statement.setBytes(17, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    public void triggerUpdateNotification() {
        commitHookBits |= HOOK_BIT_UPDATED;
        identityManagerSession.session.addSessionCommitListener(this);
    }

    // endregion





    // region hooks
    private UID labelToDelete;
    private boolean updatedByMe;

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;
    private static final long HOOK_BIT_INSERTED_AS_NEW = 0x2;
    private static final long HOOK_BIT_DELETED = 0x4;
    private static final long HOOK_BIT_FROZEN_CHANGED = 0x8;
    private static final long HOOK_BIT_UPDATED = 0x10;
    private static final long HOOK_BIT_PHOTO_UPDATED = 0x20;
    private static final long HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED = 0x40;
    private static final long HOOK_BIT_NEW_PUSH_TOPIC = 0x80;
    private static final long HOOK_BIT_CREATED_ON_OTHER_DEVICE = 0x100;
    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_GROUP_IDENTIFIER_KEY, getGroupIdentifier());
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_CREATED_BY_ME_KEY, (commitHookBits & HOOK_BIT_INSERTED_AS_NEW) != 0);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_ON_OTHER_DEVICE_KEY, (commitHookBits & HOOK_BIT_CREATED_ON_OTHER_DEVICE) != 0);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED_GROUP_IDENTIFIER_KEY, getGroupIdentifier());
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_FROZEN_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_GROUP_IDENTIFIER_KEY, getGroupIdentifier());
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_FROZEN_KEY, frozen);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_NEW_GROUP_KEY, (commitHookBits & HOOK_BIT_INSERTED_AS_NEW) != 0);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_UPDATED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_GROUP_IDENTIFIER_KEY, getGroupIdentifier());
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_BY_ME_KEY, updatedByMe);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_PHOTO_UPDATED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED_GROUP_IDENTIFIER_KEY, getGroupIdentifier());
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_LABEL_KEY, labelToDelete);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_NEW_PUSH_TOPIC) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC, userInfo);
        }
        commitHookBits = 0;
    }

    // endregion


    // region backup

    public static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        List<ContactGroupV2> groups = getAllForIdentity(identityManagerSession, ownedIdentity);
        Pojo_0[] pojos = new Pojo_0[groups.size()];
        for (int i=0; i<pojos.length; i++) {
            pojos[i] = groups.get(i).backup();
        }
        return pojos;
    }

    public static void restoreAll(IdentityManagerSession identityManagerSession, ProtocolStarterDelegate protocolStarterDelegate, Identity ownedIdentity, Pojo_0[] pojos) throws SQLException {
        if (pojos == null) {
            return;
        }
        for (Pojo_0 pojo : pojos) {
            restore(identityManagerSession, protocolStarterDelegate, ownedIdentity, pojo);
        }
    }

    Pojo_0 backup() throws SQLException {
        Pojo_0 pojo = new Pojo_0();
        pojo.group_uid = groupUid.getBytes();
        pojo.server_url = serverUrl;
        pojo.category = category;

        pojo.permissions = GroupV2.Permission.deserializePermissions(serializedOwnPermissions).toArray(new String[0]);
        pojo.version = version;
        pojo.details = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, getGroupIdentifier(), version).backup();
        if (trustedDetailsVersion != version) {
            pojo.trusted_details = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, getGroupIdentifier(), trustedDetailsVersion).backup();
        }

        pojo.verified_admin_chain = verifiedAdministratorsChain;
        pojo.main_seed = blobMainSeed == null ? null : blobMainSeed.getBytes();
        pojo.version_seed = blobVersionSeed == null ? null : blobVersionSeed.getBytes();
        if (groupAdminServerAuthenticationPrivateKey != null) {
            pojo.encoded_admin_key = Encoded.of(groupAdminServerAuthenticationPrivateKey).getBytes();
        }
        pojo.invitation_nonce = ownGroupInvitationNonce;
        pojo.last_modification_timestamp = lastModificationTimestamp;
        pojo.push_topic = pushTopic;
        pojo.serialized_shared_settings = serializedSharedSettings;
        pojo.serialized_json_group_type = serializedJsonGroupType;

        pojo.members = ContactGroupV2Member.backupAll(identityManagerSession, ownedIdentity, getGroupIdentifier());
        pojo.pending_members = ContactGroupV2PendingMember.backupAll(identityManagerSession, ownedIdentity, getGroupIdentifier());
        return pojo;
    }

    static void restore(IdentityManagerSession identityManagerSession, ProtocolStarterDelegate protocolStarterDelegate, Identity ownedIdentity, Pojo_0 pojo) throws SQLException {
        GroupV2.Identifier groupIdentifier = new GroupV2.Identifier(
                new UID(pojo.group_uid),
                pojo.server_url,
                pojo.category
        );

        identityManagerSession.session.startTransaction();
        // first restore the details
        ContactGroupV2Details.restore(identityManagerSession, ownedIdentity, groupIdentifier, pojo.version, pojo.details);
        if (pojo.trusted_details != null) {
            ContactGroupV2Details.restore(identityManagerSession, ownedIdentity, groupIdentifier, pojo.version - 1, pojo.trusted_details);
        }

        if (Arrays.equals(pojo.details.photo_server_identity, ownedIdentity.getBytes()) && pojo.details.photo_server_label != null) {
            // If I am the photo owner, also create the corresponding ServerUserData to maintain it
            try {
                ServerUserData.createForGroupV2(identityManagerSession, ownedIdentity, new UID(pojo.details.photo_server_label), groupIdentifier.getBytes());
            } catch (Exception ignored) { }
        }

        // then the group
        ServerAuthenticationPrivateKey serverAuthenticationPrivateKey = null;
        if (pojo.encoded_admin_key != null) {
            try {
                serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(pojo.encoded_admin_key).decodePrivateKey();
            } catch (Exception e) {
                Logger.x(e);
            }
        }
        GroupV2.BlobKeys blobKeys = ((pojo.main_seed == null) || (pojo.version_seed == null)) ? null : new GroupV2.BlobKeys(new Seed(pojo.main_seed), new Seed(pojo.version_seed), serverAuthenticationPrivateKey);
        ContactGroupV2 groupV2 = new ContactGroupV2(
                identityManagerSession,
                groupIdentifier.groupUid,
                pojo.server_url,
                pojo.category,
                ownedIdentity,
                GroupV2.Permission.serializePermissionStrings(Arrays.asList(pojo.permissions)),
                pojo.version,
                pojo.verified_admin_chain,
                blobKeys,
                pojo.invitation_nonce,
                false,
                pojo.last_modification_timestamp,
                pojo.push_topic,
                pojo.serialized_shared_settings,
                pojo.serialized_json_group_type
        );
        groupV2.insert();

        // finally the members and pending members
        ContactGroupV2Member.restoreAll(identityManagerSession, ownedIdentity, groupIdentifier, pojo.members);
        ContactGroupV2PendingMember.restoreAll(identityManagerSession, ownedIdentity, groupIdentifier, pojo.pending_members);

        try {
            protocolStarterDelegate.initiateGroupV2ReDownloadWithinTransaction(identityManagerSession.session, ownedIdentity, groupIdentifier);
        } catch (Exception e) {
            Logger.x(e);
        }

        identityManagerSession.session.commit();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public byte[] group_uid;
        public String server_url;
        public int category;
        public String[] permissions;
        public int version;
        public ContactGroupV2Details.Pojo_0 details;
        public ContactGroupV2Details.Pojo_0 trusted_details; // we do not store the version of the trusted details: we will use (version - 1) on restore

        public byte[] verified_admin_chain;
        public byte[] main_seed;
        public byte[] version_seed;
        public byte[] encoded_admin_key;
        public byte[] invitation_nonce;
        public long last_modification_timestamp;
        public String push_topic;
        public String serialized_shared_settings;
        public String serialized_json_group_type;

        public ContactGroupV2Member.Pojo_0[] members;
        public ContactGroupV2PendingMember.Pojo_0[] pending_members;
    }

    // endregion
}
