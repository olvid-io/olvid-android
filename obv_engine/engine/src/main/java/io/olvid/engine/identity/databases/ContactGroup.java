/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ContactGroup implements ObvDatabase {
    static final String TABLE_NAME = "contact_group";

    private final IdentityManagerSession identityManagerSession;

    private byte[] groupOwnerAndUid;
    static final String GROUP_OWNER_AND_UID = "group_owner_and_uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Identity groupOwner; // NULL for groups where you are the owner
    static final String GROUP_OWNER = "group_owner";
    private int publishedDetailsVersion;
    static final String PUBLISHED_DETAILS_VERSION = "published_details_version";
    public int latestOrTrustedDetailsVersion;
    static final String LATEST_OR_TRUSTED_DETAILS_VERSION = "latest_or_trusted_details_version";
    public long groupMembersVersion;
    static final String GROUP_MEMBERS_VERSION = "group_members_version";

    public byte[] getGroupOwnerAndUid() {
        return groupOwnerAndUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity getGroupOwner() {
        return groupOwner;
    }

    public long getGroupMembersVersion() {
        return groupMembersVersion;
    }

    public int getPublishedDetailsVersion() {
        return publishedDetailsVersion;
    }

    public ContactGroupDetails getPublishedDetails() throws SQLException {
        return ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, publishedDetailsVersion);
    }

    public ContactGroupDetails getLatestOrTrustedDetails() throws SQLException {
        return ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, latestOrTrustedDetailsVersion);
    }

    public int getLatestOrTrustedDetailsVersion() {
        return latestOrTrustedDetailsVersion;
    }

    public GroupInformation getGroupInformation() {
        String serializedGroupDetailsWithVersionAndPhoto;
        try {
            JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = getPublishedDetails().getJsonGroupDetailsWithVersionAndPhoto();
            serializedGroupDetailsWithVersionAndPhoto = identityManagerSession.jsonObjectMapper.writeValueAsString(jsonGroupDetailsWithVersionAndPhoto);
        } catch (Exception e) {
            return null;
        }
        UID groupUid = new UID(Arrays.copyOfRange(this.groupOwnerAndUid, this.groupOwnerAndUid.length-UID.UID_LENGTH, this.groupOwnerAndUid.length));
        return new GroupInformation((groupOwner==null) ? ownedIdentity : groupOwner, groupUid, serializedGroupDetailsWithVersionAndPhoto);
    }



    // region setters

    // update details of a group you do not own. Returns true if details were indeed updated
    public boolean updatePublishedDetails(JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto, boolean allowDowngrade) throws Exception {
        if (jsonGroupDetailsWithVersionAndPhoto == null) {
            return false;
        }
        final int newDetailsVersion = jsonGroupDetailsWithVersionAndPhoto.getVersion();
        if (!allowDowngrade && newDetailsVersion <= publishedDetailsVersion) {
            return false;
        }

        if (allowDowngrade && newDetailsVersion <= publishedDetailsVersion) {
            // check whether anything changed
            ContactGroupDetails publishedDetails = getPublishedDetails();
            if (newDetailsVersion == publishedDetailsVersion) {
                if (publishedDetails.getJsonGroupDetails().equals(jsonGroupDetailsWithVersionAndPhoto.getGroupDetails())) {
                    // details are the same, check photo labels
                    UID newPhotoServerLabel = (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel() == null) ? null : new UID(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerLabel());
                    if ((newPhotoServerLabel == null && publishedDetails.getPhotoServerLabel() == null) ||
                            (newPhotoServerLabel != null && newPhotoServerLabel.equals(publishedDetails.getPhotoServerLabel()))) {
                        // photo labels are the same, check keys
                        AuthEncKey newPhotoServerKey = (jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey() == null) ? null : (AuthEncKey) new Encoded(jsonGroupDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey();
                        if ((newPhotoServerKey == null && publishedDetails.getPhotoServerKey() == null) ||
                                (newPhotoServerKey != null && newPhotoServerKey.equals(publishedDetails.getPhotoServerKey()))) {
                            // nothing changed, do nothing!
                            return false;
                        }
                    }
                }
            }
            // something changed and we actually need to downgrade!
            // first, cleanup
            ContactGroupDetails.cleanup(identityManagerSession, ownedIdentity, groupOwnerAndUid, publishedDetailsVersion, latestOrTrustedDetailsVersion);
            // then move trusted details to number 0 if needed, and set trusted And published to 0 --> clean published details
            ContactGroupDetails trustedDetails = getLatestOrTrustedDetails();
            if (latestOrTrustedDetailsVersion != -1) {
                ContactGroupDetails zeroedDetails = ContactGroupDetails.copy(identityManagerSession, ownedIdentity, groupOwnerAndUid, latestOrTrustedDetailsVersion, -1);
                if (zeroedDetails == null) {
                    throw new Exception("Failed to copy contact groupd details to version 0");
                }
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                        " SET " + LATEST_OR_TRUSTED_DETAILS_VERSION + " = ?, " +
                        PUBLISHED_DETAILS_VERSION + " = ? " +
                        " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, -1);
                    statement.setInt(2, -1);
                    statement.setBytes(3, groupOwnerAndUid);
                    statement.setBytes(4, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.latestOrTrustedDetailsVersion = -1;
                    this.publishedDetailsVersion = -1;
                }
                trustedDetails.delete();
            } else {
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                        " SET " + LATEST_OR_TRUSTED_DETAILS_VERSION + " = ?, " +
                        PUBLISHED_DETAILS_VERSION + " = ? " +
                        " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, -1);
                    statement.setInt(2, -1);
                    statement.setBytes(3, groupOwnerAndUid);
                    statement.setBytes(4, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.latestOrTrustedDetailsVersion = -1;
                    this.publishedDetailsVersion = -1;
                }
            }
            // now that the published details are no longer linked, remove them from DB
            if (publishedDetails.getVersion() != trustedDetails.getVersion()) {
                publishedDetails.delete();
            }
            // insert the new details
            ContactGroupDetails newPublishedDetails = ContactGroupDetails.create(identityManagerSession, groupOwnerAndUid, ownedIdentity, jsonGroupDetailsWithVersionAndPhoto);
            if (newPublishedDetails.getPhotoServerLabel() != null &&
                    newPublishedDetails.getPhotoServerKey() != null) {
                if (newPublishedDetails.getPhotoServerLabel().equals(publishedDetails.getPhotoServerLabel()) &&
                        newPublishedDetails.getPhotoServerKey().equals(publishedDetails.getPhotoServerKey()) &&
                        publishedDetails.getPhotoUrl() != null) {
                    // photo is the same, copy the photoUrl
                    newPublishedDetails.setPhotoUrl(publishedDetails.getPhotoUrl(), false);
                }
            }
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PUBLISHED_DETAILS_VERSION + " = ? " +
                    " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, newPublishedDetails.getVersion());
                statement.setBytes(2, groupOwnerAndUid);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.publishedDetailsVersion = newPublishedDetails.getVersion();
            }
        } else {
            ContactGroupDetails newPublishedDetails = ContactGroupDetails.create(identityManagerSession, groupOwnerAndUid, ownedIdentity, jsonGroupDetailsWithVersionAndPhoto);
            ContactGroupDetails publishedDetails = getPublishedDetails();
            if (newPublishedDetails.getPhotoServerLabel() != null &&
                    newPublishedDetails.getPhotoServerKey() != null) {
                if (newPublishedDetails.getPhotoServerLabel().equals(publishedDetails.getPhotoServerLabel()) &&
                        newPublishedDetails.getPhotoServerKey().equals(publishedDetails.getPhotoServerKey()) &&
                        publishedDetails.getPhotoUrl() != null) {
                    // photo is the same, copy the photoUrl
                    newPublishedDetails.setPhotoUrl(publishedDetails.getPhotoUrl(), false);
                }
            }
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PUBLISHED_DETAILS_VERSION + " = ? " +
                    " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, newPublishedDetails.getVersion());
                statement.setBytes(2, groupOwnerAndUid);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.publishedDetailsVersion = newPublishedDetails.getVersion();
            }
        }
        // no need to notify if I am the group owner processing a propagated message
        if (groupOwner != null) {
            commitHookBits |= HOOK_BIT_NEW_PUBLISHED_DETAILS;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        return true;
    }

    // trust the details of a group you do not own
    public JsonGroupDetailsWithVersionAndPhoto trustPublishedDetails() throws SQLException {
        if (latestOrTrustedDetailsVersion == publishedDetailsVersion) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + LATEST_OR_TRUSTED_DETAILS_VERSION + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, publishedDetailsVersion);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.latestOrTrustedDetailsVersion = publishedDetailsVersion;
        }
        hookDetails = getLatestOrTrustedDetails().getJsonGroupDetailsWithVersionAndPhoto();
        commitHookBits |= HOOK_BIT_PUBLISHED_DETAILS_TRUSTED;
        identityManagerSession.session.addSessionCommitListener(this);
        return hookDetails;
    }

    // set details of a group you own
    public void setLatestDetails(JsonGroupDetails groupDetails) throws Exception {
        if (groupOwner != null || groupDetails == null || groupDetails.isEmpty()) {
            return;
        }
        JsonGroupDetails latestDetails = getLatestOrTrustedDetails().getJsonGroupDetails();
        if (latestDetails.equals(groupDetails)) {
            // nothing changed, so nothing to do
            return;
        }
        if (publishedDetailsVersion != latestOrTrustedDetailsVersion) {
            JsonGroupDetails publishedDetails = getPublishedDetails().getJsonGroupDetails();
            if (publishedDetails.equals(groupDetails)) {
                // changes were reverted --> we discard
                discardLatestDetails();
                return;
            }
        }
        // we indeed have a proper update to save
        ContactGroupDetails contactGroupDetails;
        if (publishedDetailsVersion == latestOrTrustedDetailsVersion) {
            contactGroupDetails = ContactGroupDetails.copy(identityManagerSession, ownedIdentity, groupOwnerAndUid, publishedDetailsVersion, null);
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                    LATEST_OR_TRUSTED_DETAILS_VERSION + " = ? " +
                    " WHERE " + GROUP_OWNER_AND_UID + " = ?" +
                    " AND " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, contactGroupDetails.getVersion());
                statement.setBytes(2, groupOwnerAndUid);
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.latestOrTrustedDetailsVersion = contactGroupDetails.getVersion();
            }
        } else {
            contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, latestOrTrustedDetailsVersion);
        }
        contactGroupDetails.setJsonDetails(groupDetails);
    }

    // set the photo of a group you own
    public void setOwnedGroupPhoto(String srcAbsolutePhotoUrl, boolean partOfGroupCreation) throws Exception {
        if (groupOwner != null) {
            return;
        }
        if (srcAbsolutePhotoUrl == null) {
            ContactGroupDetails contactGroupDetails;
            if (publishedDetailsVersion == latestOrTrustedDetailsVersion) {
                contactGroupDetails = ContactGroupDetails.copy(identityManagerSession, ownedIdentity, groupOwnerAndUid, publishedDetailsVersion, null);
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                        LATEST_OR_TRUSTED_DETAILS_VERSION + " = ? " +
                        " WHERE " + GROUP_OWNER_AND_UID + " = ?" +
                        " AND " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, contactGroupDetails.getVersion());
                    statement.setBytes(2, groupOwnerAndUid);
                    statement.setBytes(3, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.latestOrTrustedDetailsVersion = contactGroupDetails.getVersion();
                }
            } else {
                contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, latestOrTrustedDetailsVersion);
            }
            contactGroupDetails.setPhotoUrl(null, true);
        } else {
            File srcPhotoFile = new File(srcAbsolutePhotoUrl);
            if (!srcPhotoFile.canRead()) {
                return;
            }
            ContactGroupDetails contactGroupDetails;
            if (partOfGroupCreation) {
                contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, publishedDetailsVersion);
            } else {
                if (publishedDetailsVersion == latestOrTrustedDetailsVersion) {
                    contactGroupDetails = ContactGroupDetails.copy(identityManagerSession, ownedIdentity, groupOwnerAndUid, publishedDetailsVersion, null);
                    try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                            LATEST_OR_TRUSTED_DETAILS_VERSION + " = ? " +
                            " WHERE " + GROUP_OWNER_AND_UID + " = ?" +
                            " AND " + OWNED_IDENTITY + " = ?;")) {
                        statement.setInt(1, contactGroupDetails.getVersion());
                        statement.setBytes(2, groupOwnerAndUid);
                        statement.setBytes(3, ownedIdentity.getBytes());
                        statement.executeUpdate();
                        this.latestOrTrustedDetailsVersion = contactGroupDetails.getVersion();
                    }
                } else {
                    contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, latestOrTrustedDetailsVersion);
                }
            }
            // find a non-existing fileName

            String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator +  Logger.toHexString(Arrays.copyOfRange(groupOwnerAndUid, groupOwnerAndUid.length - 32, groupOwnerAndUid.length));
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
            contactGroupDetails.setPhotoUrl(randFileName, true);
        }
    }

    public void setPhotoLabelAndKey(int version, UID photoServerLabel, AuthEncKey photoServerKey) throws SQLException {
        ContactGroupDetails contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, version);
        if (contactGroupDetails != null) {
            contactGroupDetails.setPhotoServerLabelAndKey(photoServerLabel, photoServerKey);
        }
    }

    // publish details of a group you own
    public int publishLatestDetails() throws SQLException {
        if (latestOrTrustedDetailsVersion == publishedDetailsVersion) {
            return -1;
        }
        ContactGroupDetails publishedDetails = getPublishedDetails();
        ContactGroupDetails latestDetails = getLatestOrTrustedDetails();
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                PUBLISHED_DETAILS_VERSION + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, latestOrTrustedDetailsVersion);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.publishedDetailsVersion = latestOrTrustedDetailsVersion;
        }
        if (publishedDetails.getPhotoUrl() != null && (latestDetails.getPhotoUrl() == null || !latestDetails.getPhotoUrl().equals(publishedDetails.getPhotoUrl()))) {
            if (publishedDetails.getPhotoServerLabel() != null) {
                labelToDelete = publishedDetails.getPhotoServerLabel();
                commitHookBits |= HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED;
            }
        }
        commitHookBits |= HOOK_BIT_DETAILS_PUBLISHED;
        hookDetails = latestDetails.getJsonGroupDetailsWithVersionAndPhoto();
        identityManagerSession.session.addSessionCommitListener(this);
        return latestOrTrustedDetailsVersion;
    }

    public void discardLatestDetails() throws SQLException {
        if (latestOrTrustedDetailsVersion == publishedDetailsVersion) {
            return;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME + " SET " +
                LATEST_OR_TRUSTED_DETAILS_VERSION + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, publishedDetailsVersion);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.latestOrTrustedDetailsVersion = publishedDetailsVersion;
        }
    }

    // usually for group you do not own, but can be for owned groups after a backup restore or in multi-device
    public void setDetailsDownloadedPhotoUrl(int version, byte[] photo) throws Exception {
        ContactGroupDetails contactGroupDetails = ContactGroupDetails.get(identityManagerSession, groupOwnerAndUid, ownedIdentity, version);

        if (contactGroupDetails == null) {
            return;
        }

        // find a non-existing fileName
        String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator +  Logger.toHexString(Arrays.copyOfRange(groupOwnerAndUid, groupOwnerAndUid.length - 32, groupOwnerAndUid.length));
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
        contactGroupDetails.setPhotoUrl(randFileName, false);
        if (groupOwner == null) {
            // groups you own
            hookDetails = contactGroupDetails.getJsonGroupDetailsWithVersionAndPhoto();
            commitHookBits |= HOOK_BIT_PUBLISHED_DETAILS_TRUSTED;
        } else {
            // groups you do not own
            hookPhotoSetVersion = version;
            commitHookBits |= HOOK_BIT_PHOTO_SET;
        }
        identityManagerSession.session.addSessionCommitListener(this);
    }

    // for groups you own
    public void incrementGroupMembersVersion() throws Exception {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Called incrementGroupMembersVersion outside a transaction");
            throw new Exception();
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + GROUP_MEMBERS_VERSION + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, groupMembersVersion + 1);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.groupMembersVersion = groupMembersVersion + 1;
        }
    }

    // for groups you do not own
    public void setGroupMembersVersion(long groupMembersVersion) throws Exception {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + GROUP_MEMBERS_VERSION + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, groupMembersVersion);
            statement.setBytes(2, groupOwnerAndUid);
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.groupMembersVersion = groupMembersVersion;
        }
    }

    // endregion


    // region constructors

    public static ContactGroup create(IdentityManagerSession identityManagerSession, byte[] groupUid, Identity ownedIdentity, String serializedGroupDetailsWithVersionAndPhoto, Identity groupOwner, boolean createdByMeOnOtherDevice) {
        if ((groupUid == null) || (serializedGroupDetailsWithVersionAndPhoto == null) || (ownedIdentity == null)) {
            return null;
        }
        JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto;
        try {
            jsonGroupDetailsWithVersionAndPhoto = identityManagerSession.jsonObjectMapper.readValue(serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }

        try {
            if (!identityManagerSession.session.isInTransaction()) {
                Logger.e("Calling ContactGroup.create() outside a transaction");
                throw new SQLException();
            }
            ContactGroupDetails contactGroupDetails = ContactGroupDetails.create(identityManagerSession, groupUid, ownedIdentity, jsonGroupDetailsWithVersionAndPhoto);
            if (contactGroupDetails == null) {
                Logger.e("Error create contactGroupDetails in ContactGroup.create()");
                throw new SQLException();
            }
            ContactGroup contactGroup = new ContactGroup(identityManagerSession, groupUid, ownedIdentity, groupOwner, contactGroupDetails.getVersion());
            contactGroup.insert();
            if (createdByMeOnOtherDevice) {
                contactGroup.commitHookBits |= HOOK_BIT_CREATED_ON_OTHER_DEVICE;
            }
            return contactGroup;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    public ContactGroup(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity groupOwner, int version) {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = groupOwnerAndUid;
        this.ownedIdentity = ownedIdentity;
        this.groupOwner = groupOwner;
        this.publishedDetailsVersion = version;
        this.latestOrTrustedDetailsVersion = version;
        this.groupMembersVersion = 0;
    }

    private ContactGroup(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = res.getBytes(GROUP_OWNER_AND_UID);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            byte[] bytes = res.getBytes(GROUP_OWNER);
            this.groupOwner = (bytes == null)?null:Identity.of(bytes);
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.publishedDetailsVersion = res.getInt(PUBLISHED_DETAILS_VERSION);
        this.latestOrTrustedDetailsVersion = res.getInt(LATEST_OR_TRUSTED_DETAILS_VERSION);
        this.groupMembersVersion = res.getLong(GROUP_MEMBERS_VERSION);
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    GROUP_OWNER_AND_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    GROUP_OWNER + " BLOB, " + // NULL for groups you own
                    PUBLISHED_DETAILS_VERSION + " INT NOT NULL, " +
                    LATEST_OR_TRUSTED_DETAILS_VERSION + " INT NOT NULL, " +
                    GROUP_MEMBERS_VERSION + " BIGINT NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_OWNER_AND_UID + ", " + OWNED_IDENTITY + "), " +
                    " FOREIGN KEY (" + GROUP_OWNER + "," + OWNED_IDENTITY + ") REFERENCES " + ContactIdentity.TABLE_NAME + "(" + ContactIdentity.CONTACT_IDENTITY + "," + ContactIdentity.OWNED_IDENTITY + "), " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + "(" + OwnedIdentity.OWNED_IDENTITY + ")," +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + GROUP_OWNER_AND_UID + ", " + PUBLISHED_DETAILS_VERSION + ") REFERENCES " + ContactGroupDetails.TABLE_NAME + "(" + ContactGroupDetails.OWNED_IDENTITY + ", " + ContactGroupDetails.GROUP_OWNER_AND_UID + ", " + ContactGroupDetails.VERSION + ")," +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ", " + GROUP_OWNER_AND_UID + ", " + LATEST_OR_TRUSTED_DETAILS_VERSION + ") REFERENCES " + ContactGroupDetails.TABLE_NAME + "(" + ContactGroupDetails.OWNED_IDENTITY + ", " + ContactGroupDetails.GROUP_OWNER_AND_UID + ", " + ContactGroupDetails.VERSION + "));");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING contact_group DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("ALTER TABLE contact_group RENAME TO old_contact_group");
                statement.execute("CREATE TABLE contact_group_details (" +
                        " group_owner_and_uid BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " version INT NOT NULL, " +
                        " serialized_json_details TEXT NOT NULL, " +
                        " photo_url TEXT, " +
                        " photo_server_label BLOB, " +
                        " photo_server_key BLOB, " +
                        " CONSTRAINT PK_contact_group_details PRIMARY KEY(group_owner_and_uid, owned_identity, version));");
                statement.execute("CREATE TABLE contact_group (" +
                        " group_owner_and_uid BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " group_owner BLOB, " +
                        " published_details_version INT NOT NULL, " +
                        " latest_or_trusted_details_version INT NOT NULL, " +
                        " group_members_version BIGINT NOT NULL, " +
                        " CONSTRAINT PK_contact_group PRIMARY KEY(group_owner_and_uid, owned_identity), " +
                        " FOREIGN KEY (group_owner,owned_identity) REFERENCES contact_identity(identity,owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity(identity), " +
                        " FOREIGN KEY (owned_identity, group_owner_and_uid, published_details_version) REFERENCES contact_group_details(owned_identity, group_owner_and_uid, version), " +
                        " FOREIGN KEY (owned_identity, group_owner_and_uid, latest_or_trusted_details_version) REFERENCES contact_group_details(owned_identity, group_owner_and_uid, version));");
                ObjectMapper objectMapper = new ObjectMapper();
                try (ResultSet res = statement.executeQuery("SELECT * FROM old_contact_group")) {
                    while (res.next()) {
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_group VALUES (?,?,?,?,?, ?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setBytes(3, res.getBytes(4));
                            preparedStatement.setInt(4, 0);
                            preparedStatement.setInt(5, 0);
                            preparedStatement.setInt(6, 0);
                            preparedStatement.executeUpdate();
                        }
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_group_details VALUES (?,?,?,?,?, ?,?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setInt(3, 0);
                            HashMap<String, String> map = new HashMap<>();
                            map.put("name", res.getString(3));
                            try {
                                preparedStatement.setString(4, objectMapper.writeValueAsString(map));
                            } catch (Exception e) {
                                Logger.e("\n\n\n\nMIGRATION ERROR!!!\n\n\n");
                                throw new SQLException();
                            }
                            preparedStatement.setString(5, null);
                            preparedStatement.setBytes(6, null);
                            preparedStatement.setBytes(7, null);
                            preparedStatement.executeUpdate();
                        }
                    }
                }
                statement.execute("DROP TABLE old_contact_group");
            }
            oldVersion = 11;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?);")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, (groupOwner==null)?null:groupOwner.getBytes());
            statement.setInt(4, publishedDetailsVersion);
            statement.setInt(5, latestOrTrustedDetailsVersion);
            statement.setLong(6, groupMembersVersion);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Running ContactGroup.delete() outside a transaction");
            throw new SQLException();
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_DELETED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + ContactGroupDetails.TABLE_NAME +
                " WHERE " + ContactGroupDetails.GROUP_OWNER_AND_UID + " = ? " +
                " AND " + ContactGroupDetails.OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion


    // region getters

    public static ContactGroup get(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactGroup(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static ContactGroup[] getAll(IdentityManagerSession identityManagerSession) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + ";")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroup> list = new ArrayList<>();
                while (res.next()) {
                    ContactGroup contactGroup = new ContactGroup(identityManagerSession, res);
                    list.add(contactGroup);
                }
                return list.toArray(new ContactGroup[0]);
            }
        }
    }

    public static ContactGroup[] getAllForIdentity(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactGroup> list = new ArrayList<>();
                while (res.next()) {
                    ContactGroup contactGroup = new ContactGroup(identityManagerSession, res);
                    list.add(contactGroup);
                }
                return list.toArray(new ContactGroup[0]);
            }
        }
    }

    public static ContactGroup[] getAllForOwnedIdentityAndOwner(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwner) throws SQLException {
        if (ownedIdentity.equals(groupOwner)) {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + GROUP_OWNER + " IS NULL;")) {
                statement.setBytes(1, ownedIdentity.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    List<ContactGroup> list = new ArrayList<>();
                    while (res.next()) {
                        ContactGroup contactGroup = new ContactGroup(identityManagerSession, res);
                        list.add(contactGroup);
                    }
                    return list.toArray(new ContactGroup[0]);
                }
            }
        } else {
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + OWNED_IDENTITY + " = ? " +
                    " AND " + GROUP_OWNER + " = ?;")) {
                statement.setBytes(1, ownedIdentity.getBytes());
                statement.setBytes(2, groupOwner.getBytes());
                try (ResultSet res = statement.executeQuery()) {
                    List<ContactGroup> list = new ArrayList<>();
                    while (res.next()) {
                        ContactGroup contactGroup = new ContactGroup(identityManagerSession, res);
                        list.add(contactGroup);
                    }
                    return list.toArray(new ContactGroup[0]);
                }
            }
        }
    }

    public static byte[][] getGroupOwnerAndUidsOfGroupsOwnedByContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT " + GROUP_OWNER_AND_UID + " FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ? AND "  + GROUP_OWNER + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<byte[]> list = new ArrayList<>();
                while (res.next()) {
                    list.add(res.getBytes(GROUP_OWNER_AND_UID));
                }
                return list.toArray(new byte[0][]);
            }
        }
    }

    public static byte[][] getGroupOwnerAndUidsOfOwnedGroupsWithContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT g." + GROUP_OWNER_AND_UID +
                        " FROM " + TABLE_NAME + " AS g " +
                        " INNER JOIN " + ContactGroupMembersJoin.TABLE_NAME + " AS j " +
                        " ON g." + GROUP_OWNER_AND_UID + " = j." + ContactGroupMembersJoin.GROUP_OWNER_AND_UID +
                        " AND g." + OWNED_IDENTITY + " = j." + ContactGroupMembersJoin.OWNED_IDENTITY +
                        " WHERE g." + OWNED_IDENTITY + " = ? " +
                        " AND g."  + GROUP_OWNER + " IS NULL " +
                        " AND j." + ContactGroupMembersJoin.CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<byte[]> list = new ArrayList<>();
                while (res.next()) {
                    list.add(res.getBytes(GROUP_OWNER_AND_UID));
                }
                return list.toArray(new byte[0][]);
            }
        }
    }

    // endregion


    // region hooks

    private UID labelToDelete;
    private JsonGroupDetailsWithVersionAndPhoto hookDetails;
    private int hookPhotoSetVersion;

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;
    private static final long HOOK_BIT_DELETED = 0x2;
    private static final long HOOK_BIT_PUBLISHED_DETAILS_TRUSTED = 0x4;
    private static final long HOOK_BIT_NEW_PUBLISHED_DETAILS = 0x8;
    private static final long HOOK_BIT_PHOTO_SET = 0x10;
    private static final long HOOK_BIT_DETAILS_PUBLISHED = 0x20;
    private static final long HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED = 0x40;
    private static final long HOOK_BIT_CREATED_ON_OTHER_DEVICE = 0x80;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_CREATED_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_CREATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_CREATED_ON_OTHER_DEVICE_KEY, (commitHookBits & HOOK_BIT_CREATED_ON_OTHER_DEVICE) != 0);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_CREATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_DELETED_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_DELETED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DETAILS_PUBLISHED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY, hookDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_NEW_PUBLISHED_DETAILS) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_PUBLISHED_DETAILS_TRUSTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY, hookDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_PHOTO_SET) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_VERSION_KEY, hookPhotoSetVersion);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_IS_TRUSTED_KEY, hookPhotoSetVersion == latestOrTrustedDetailsVersion);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_SERVER_USER_DATA_CAN_BE_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_LABEL_KEY, labelToDelete);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED, userInfo);
        }
        commitHookBits = 0;
    }

    // endregion

    // region backup

    public static Pojo_0[] backupAllForOwner(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwnerIdentity) throws SQLException {
        ContactGroup[] contactGroups = getAllForOwnedIdentityAndOwner(identityManagerSession, ownedIdentity, groupOwnerIdentity);
        Pojo_0[] pojos = new Pojo_0[contactGroups.length];
        for (int i=0; i<contactGroups.length; i++) {
            pojos[i] = contactGroups[i].backup();
        }
        return pojos;
    }

    public static void restoreAllForOwner(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwner, Pojo_0[] pojos, long backupTimestamp) throws SQLException {
        if (pojos == null){
            return;
        }
        for (Pojo_0 pojo: pojos) {
            restoreForOwner(identityManagerSession, ownedIdentity, groupOwner, pojo, backupTimestamp);
        }
    }

    Pojo_0 backup() throws SQLException {
        Pojo_0 pojo = new Pojo_0();
        pojo.group_uid = Arrays.copyOfRange(this.groupOwnerAndUid, this.groupOwnerAndUid.length-UID.UID_LENGTH, this.groupOwnerAndUid.length);
        if (groupOwner == null) {
            pojo.published_details = getPublishedDetails().backup();
            if (latestOrTrustedDetailsVersion != publishedDetailsVersion) {
                pojo.latest_details = getLatestOrTrustedDetails().backup();
            }
        } else {
            pojo.trusted_details = getLatestOrTrustedDetails().backup();
            if (publishedDetailsVersion != latestOrTrustedDetailsVersion) {
                pojo.published_details = getPublishedDetails().backup();
            }
        }
        pojo.group_members_version = groupMembersVersion;
        pojo.members = ContactGroupMembersJoin.backupAll(identityManagerSession, ownedIdentity, groupOwnerAndUid);
        pojo.pending_members = PendingGroupMember.backupAll(identityManagerSession, ownedIdentity, groupOwnerAndUid);
        return pojo;
    }

    public static void restoreForOwner(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwner, Pojo_0 pojo, @SuppressWarnings("unused") long backupTimestamp) throws SQLException {
        byte[] groupOwnerAndUid = new byte[groupOwner.getBytes().length + UID.UID_LENGTH];
        System.arraycopy(groupOwner.getBytes(), 0, groupOwnerAndUid, 0, groupOwner.getBytes().length);
        System.arraycopy(pojo.group_uid, 0, groupOwnerAndUid, groupOwner.getBytes().length, UID.UID_LENGTH);

        identityManagerSession.session.startTransaction();
        if (groupOwner.equals(ownedIdentity)) {
            // owned group
            ContactGroupDetails publishedDetails = ContactGroupDetails.restore(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.published_details, true);
            ContactGroupDetails latestDetails = null;
            if (pojo.latest_details != null && pojo.latest_details.version != pojo.published_details.version) {
                latestDetails = ContactGroupDetails.restore(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.latest_details, true);
            }
            ContactGroup contactGroup = new ContactGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity, null, publishedDetails.getVersion());
            if (latestDetails != null) {
                contactGroup.latestOrTrustedDetailsVersion = latestDetails.getVersion();
            }
            contactGroup.insert();
        } else {
            // joined group
            ContactGroupDetails trustedDetails = ContactGroupDetails.restore(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.trusted_details, false);
            ContactGroupDetails publishDetails = null;
            if (pojo.published_details != null  && pojo.trusted_details.version != pojo.published_details.version) {
                publishDetails = ContactGroupDetails.restore(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.published_details, false);
            }
            ContactGroup contactGroup = new ContactGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity, groupOwner, trustedDetails.getVersion());
            if (publishDetails != null) {
                contactGroup.publishedDetailsVersion = publishDetails.getVersion();
            }
            contactGroup.groupMembersVersion = pojo.group_members_version;
            contactGroup.insert();
        }

        // now add members and pending
        ContactGroupMembersJoin.restoreAll(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.members);
        PendingGroupMember.restoreAll(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo.pending_members);
        identityManagerSession.session.commit();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public byte[] group_uid; // only the uid, not the owner
        public ContactGroupDetails.Pojo_0 published_details;
        public ContactGroupDetails.Pojo_0 latest_details;
        public ContactGroupDetails.Pojo_0 trusted_details;
        public long group_members_version;
        public ContactGroupMembersJoin.Pojo_0[] members;
        public PendingGroupMember.Pojo_0[] pending_members;
    }

    // endregion
}
