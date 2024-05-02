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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ContactIdentity implements ObvDatabase {
    static final String TABLE_NAME = "contact_identity";

    private final IdentityManagerSession identityManagerSession;

    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "identity";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private int trustedDetailsVersion;
    static final String TRUSTED_DETAILS_VERSION = "trusted_details_version";
    public int publishedDetailsVersion;
    static final String PUBLISHED_DETAILS_VERSION = "published_details_version";
    private TrustLevel trustLevel;
    static final String TRUST_LEVEL = "trust_level";
    private boolean certifiedByOwnKeycloak;
    static final String CERTIFIED_BY_OWN_KEYCLOAK = "keycloak_managed";
    public boolean revokedAsCompromised;
    static final String REVOKED_AS_COMPROMISED = "revoked_as_compromised";
    public boolean forcefullyTrustedByUser;
    static final String FORCEFULLY_TRUSTED_BY_USER = "forcefully_trusted_by_user";
    private int oneToOne;
    static final String ONE_TO_ONE = "one_to_one";
    private long lastNoDeviceContactDeviceDiscovery;
    static final String LAST_NO_DEVICE_CONTACT_DEVICE_DISCOVERY = "last_no_device_contact_device_discovery";


    public static final int ONE_TO_ONE_STATUS_FALSE = 0;
    public static final int ONE_TO_ONE_STATUS_TRUE = 1;
    public static final int ONE_TO_ONE_STATUS_UNKNOWN = 2;

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public int getTrustedDetailsVersion() {
        return trustedDetailsVersion;
    }

    public int getPublishedDetailsVersion() {
        return publishedDetailsVersion;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public boolean isCertifiedByOwnKeycloak() {
        return certifiedByOwnKeycloak;
    }

    public boolean isRevokedAsCompromised() {
        return revokedAsCompromised;
    }

    public boolean isForcefullyTrustedByUser() {
        return forcefullyTrustedByUser;
    }

    public boolean isActive() {
        return forcefullyTrustedByUser || !revokedAsCompromised;
    }

    public boolean isOneToOne() {
        return oneToOne == ONE_TO_ONE_STATUS_TRUE;
    }
    public boolean isNotOneToOne() {
        return oneToOne == ONE_TO_ONE_STATUS_FALSE;
    }

    public long getLastNoDeviceContactDeviceDiscovery() {
        return lastNoDeviceContactDeviceDiscovery;
    }
    // region computed properties

    public UID[] getDeviceUids() throws SQLException {
        ContactDevice[] contactDevices = ContactDevice.getAll(identityManagerSession, contactIdentity, ownedIdentity);
        UID[] uids = new UID[contactDevices.length];
        for (int i=0; i<contactDevices.length; i++) {
            uids[i] = contactDevices[i].getUid();
        }
        return uids;
    }

    public ContactIdentityDetails getPublishedDetails() throws SQLException {
        return ContactIdentityDetails.get(identityManagerSession, contactIdentity, ownedIdentity, publishedDetailsVersion);
    }

    public ContactIdentityDetails getTrustedDetails() throws SQLException {
        return ContactIdentityDetails.get(identityManagerSession, contactIdentity, ownedIdentity, trustedDetailsVersion);
    }

    // endregion

    // region setters

    public void updatePublishedDetails(JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto, boolean allowDowngrade) throws Exception {
        if (jsonIdentityDetailsWithVersionAndPhoto == null) {
            return;
        }
        final int newDetailsVersion = jsonIdentityDetailsWithVersionAndPhoto.getVersion();
        if (!allowDowngrade && jsonIdentityDetailsWithVersionAndPhoto.getVersion() <= publishedDetailsVersion) {
            return;
        }

        boolean notifyNewDetails = true;
        String lastKnownSerializedCertifiedDetails;

        if (allowDowngrade && newDetailsVersion <= publishedDetailsVersion) {
            // check whether anything changed
            ContactIdentityDetails publishedDetails = getPublishedDetails();
            lastKnownSerializedCertifiedDetails = publishedDetails.getSerializedJsonDetails();
            if (newDetailsVersion == publishedDetailsVersion) {
                if (publishedDetails.getJsonIdentityDetails().equals(jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails())) {
                    // details are the same, check photo labels
                    UID newPhotoServerLabel = (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel() == null) ? null : new UID(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel());
                    if (Objects.equals(newPhotoServerLabel, publishedDetails.getPhotoServerLabel())) {
                        // photo labels are the same, check keys
                        AuthEncKey newPhotoServerKey = (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() == null) ? null : (AuthEncKey) new Encoded(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey();
                        if (Objects.equals(newPhotoServerKey, publishedDetails.getPhotoServerKey())) {
                            // nothing changed, do nothing !
                            return;
                        }
                    }
                }
            }

            // something changed and we actually need to downgrade!
            // first, cleanup
            ContactIdentityDetails.cleanup(identityManagerSession, ownedIdentity, contactIdentity, publishedDetailsVersion, trustedDetailsVersion);
            // then move trusted details to number -1 if needed, and set trusted And published to -1 --> clean published details
            ContactIdentityDetails trustedDetails = getTrustedDetails();
            if (trustedDetailsVersion != -1) {
                ContactIdentityDetails.copy(identityManagerSession, ownedIdentity, contactIdentity, trustedDetailsVersion, -1);
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                        " SET " + TRUSTED_DETAILS_VERSION + " = ?, " +
                        PUBLISHED_DETAILS_VERSION + " = ? " +
                        " WHERE " + CONTACT_IDENTITY + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, -1);
                    statement.setInt(2, -1);
                    statement.setBytes(3, contactIdentity.getBytes());
                    statement.setBytes(4, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.trustedDetailsVersion = -1;
                    this.publishedDetailsVersion = -1;
                }
                trustedDetails.delete();
            } else {
                try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                        " SET " + TRUSTED_DETAILS_VERSION + " = ?, " +
                        PUBLISHED_DETAILS_VERSION + " = ? " +
                        " WHERE " + CONTACT_IDENTITY + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ?;")) {
                    statement.setInt(1, -1);
                    statement.setInt(2, -1);
                    statement.setBytes(3, contactIdentity.getBytes());
                    statement.setBytes(4, ownedIdentity.getBytes());
                    statement.executeUpdate();
                    this.trustedDetailsVersion = -1;
                    this.publishedDetailsVersion = -1;
                }
            }
            // now that the published details are no longer linked, remove them from DB
            if (publishedDetails.getVersion() != trustedDetails.getVersion()) {
                publishedDetails.delete();
            }
            // insert the new details
            ContactIdentityDetails newPublishedDetails = ContactIdentityDetails.create(identityManagerSession, contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto);
            if (newPublishedDetails.getPhotoServerLabel() != null &&
                    newPublishedDetails.getPhotoServerKey() != null) {
                if (newPublishedDetails.getPhotoServerLabel().equals(publishedDetails.getPhotoServerLabel()) &&
                        newPublishedDetails.getPhotoServerKey().equals(publishedDetails.getPhotoServerKey()) &&
                        publishedDetails.getPhotoUrl() != null) {
                    // photo is the same, copy the photoUrl
                    newPublishedDetails.setPhotoUrl(publishedDetails.getPhotoUrl());
                }
            }
            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PUBLISHED_DETAILS_VERSION + " = ? " +
                    " WHERE " + CONTACT_IDENTITY + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, newPublishedDetails.getVersion());
                statement.setBytes(2, contactIdentity.getBytes());
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.publishedDetailsVersion = newPublishedDetails.getVersion();
            }
        } else {
            // not in downgrade mode and number is indeed bigger
            ContactIdentityDetails newPublishedDetails = ContactIdentityDetails.create(identityManagerSession, contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto);
            ContactIdentityDetails publishedDetails = getPublishedDetails();
            lastKnownSerializedCertifiedDetails = publishedDetails.getSerializedJsonDetails();
            if (newPublishedDetails.getPhotoServerLabel() != null &&
                    newPublishedDetails.getPhotoServerKey() != null) {
                if (newPublishedDetails.getPhotoServerLabel().equals(publishedDetails.getPhotoServerLabel()) &&
                        newPublishedDetails.getPhotoServerKey().equals(publishedDetails.getPhotoServerKey()) &&
                        publishedDetails.getPhotoUrl() != null) {
                    // photo is the same, copy the photoUrl
                    newPublishedDetails.setPhotoUrl(publishedDetails.getPhotoUrl());
                }
            }
            try {
                // check if any detail actually changed
                if (publishedDetails.getJsonIdentityDetails().fieldsAreTheSame(newPublishedDetails.getJsonIdentityDetails())
                        && Objects.equals(publishedDetails.getPhotoServerKey(), newPublishedDetails.getPhotoServerKey())
                        && Objects.equals(publishedDetails.getPhotoServerLabel(), newPublishedDetails.getPhotoServerLabel())) {
                    // nothing user visible changed --> do not notify
                    notifyNewDetails = false;
                }
            } catch (Exception ignored) { }

            try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                    " SET " + PUBLISHED_DETAILS_VERSION + " = ? " +
                    " WHERE " + CONTACT_IDENTITY + " = ? " +
                    " AND " + OWNED_IDENTITY + " = ?;")) {
                statement.setInt(1, newPublishedDetails.getVersion());
                statement.setBytes(2, contactIdentity.getBytes());
                statement.setBytes(3, ownedIdentity.getBytes());
                statement.executeUpdate();
                this.publishedDetailsVersion = newPublishedDetails.getVersion();
            }
        }

        if (notifyNewDetails) {
            commitHookBits |= HOOK_BIT_NEW_PUBLISHED_DETAILS;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        if (jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails() != null) {
            JsonKeycloakUserDetails jsonKeycloakUserDetails = identityManagerSession.identityDelegate.verifyKeycloakSignature(identityManagerSession.session, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());
            if (jsonKeycloakUserDetails != null) {
                // the details are properly signed --> the call to markContactAsCertifiedByOwnKeycloak() will auto-trust the new details, so we can return
                JsonIdentityDetails certifiedJsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());
                markContactAsCertifiedByOwnKeycloak(certifiedJsonIdentityDetails);
                return;
            }
        }

        if (this.certifiedByOwnKeycloak) {
            // received non-signed (or with invalid signature) details for a keycloak certified contact --> no longer certified
            setCertifiedByOwnKeycloak(false, lastKnownSerializedCertifiedDetails);
        }

        ///////
        // compare the old (trusted) and the new published details
        // --> if only the signature/position/company changed, directly trust
        // note that for signed details, it is already auto-trusted in markContactAsCertifiedByOwnKeycloak()
        ///////
        if (trustedDetailsVersion != publishedDetailsVersion) {
            ContactIdentityDetails trustedDetails = getTrustedDetails();
            ContactIdentityDetails publishedDetails = getPublishedDetails();
            boolean same = publishedDetails.getJsonIdentityDetails().firstAndLastNamesAreTheSame(trustedDetails.getJsonIdentityDetails());
            if (same) {
                // check whether we are during the first channel creation --> in that case the trustedDetailsVersion is -1 and we auto trust even if the photo changed (it's always null for version 0)
                if (trustedDetailsVersion != -1) {
                    same = Objects.equals(trustedDetails.getPhotoServerLabel(), publishedDetails.getPhotoServerLabel())
                            && Objects.equals(trustedDetails.getPhotoServerKey(), publishedDetails.getPhotoServerKey());
                }
            }
            if (same) {
                trustPublishedDetails();
            }
        }
    }

    // when certifiedByOwnKeycloak is false, if possible, try providing the last known certified details
    // this allows settings these details for the pending member after a keycloak group member is demoted
    public void setCertifiedByOwnKeycloak(boolean certifiedByOwnKeycloak, String lastKnownSerializedCertifiedDetails) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + CERTIFIED_BY_OWN_KEYCLOAK + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, certifiedByOwnKeycloak);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.certifiedByOwnKeycloak = certifiedByOwnKeycloak;
            commitHookBits |= HOOK_BIT_KEYCLOAK_MANAGED_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);

            identityManagerSession.identityDelegate.rePingOrDemoteContactFromAllKeycloakGroups(identityManagerSession.session, ownedIdentity, contactIdentity, certifiedByOwnKeycloak, lastKnownSerializedCertifiedDetails);
        }
    }

    // this method always sets to ONE_TO_ONE_STATUS_TRUE or ONE_TO_ONE_STATUS_FALSE, but never leaves in ONE_TO_ONE_STATUS_UNKNOWN
    public void setOneToOne(boolean oneToOne) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + ONE_TO_ONE + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, oneToOne ? ONE_TO_ONE_STATUS_TRUE : ONE_TO_ONE_STATUS_FALSE);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            // do not notify when changing from unknown to false (normally this setter is not called in that case, but let's make sure!
            if (isOneToOne() != oneToOne) {
                commitHookBits |= HOOK_BIT_ONE_TO_ONE_CHANGED;
                identityManagerSession.session.addSessionCommitListener(this);
            }
            this.oneToOne = oneToOne ? ONE_TO_ONE_STATUS_TRUE : ONE_TO_ONE_STATUS_FALSE;
        }
    }

    public void setRevokedAsCompromised(boolean revokedAsCompromised) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + REVOKED_AS_COMPROMISED + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, revokedAsCompromised);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            if (!this.revokedAsCompromised && revokedAsCompromised) {
                commitHookBits |= HOOK_BIT_REVOKED;
            }
            this.revokedAsCompromised = revokedAsCompromised;
            commitHookBits |= HOOK_BIT_ACTIVE_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setForcefullyTrustedByUser(boolean forcefullyTrustedByUser) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + FORCEFULLY_TRUSTED_BY_USER + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, forcefullyTrustedByUser);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.forcefullyTrustedByUser = forcefullyTrustedByUser;
            commitHookBits |= HOOK_BIT_ACTIVE_CHANGED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void markContactAsCertifiedByOwnKeycloak(JsonIdentityDetails certifiedJsonIdentityDetails) throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            throw new SQLException("markContactAsCertifiedByOwnKeycloak can only be called from within a transaction");
        }

        // 1. mark contact as keycloakManaged and add a trust origin
        if (!isCertifiedByOwnKeycloak()) {
            setCertifiedByOwnKeycloak(true, null);
        }
        // 2. auto-trust if published != trusted
        if (trustedDetailsVersion != publishedDetailsVersion) {
            try {
                trustPublishedDetails();
            } catch (Exception e) {
                // do nothing, untrusted published details remain
            }
        }

        // 3. if needed, update details to match what is certified
        ContactIdentityDetails publishedDetails = getPublishedDetails();
        JsonIdentityDetails contactIdentityDetails = publishedDetails.getJsonIdentityDetails();
        if (!certifiedJsonIdentityDetails.equals(contactIdentityDetails)) {
            try {
                String serializedCertifiedIdentityDetails = identityManagerSession.jsonObjectMapper.writeValueAsString(certifiedJsonIdentityDetails);
                publishedDetails.setSerializedJsonDetails(serializedCertifiedIdentityDetails);
                hookTrustedDetails = publishedDetails.getJsonIdentityDetailsWithVersionAndPhoto();
                commitHookBits |= HOOK_BIT_PUBLISHED_DETAILS_TRUSTED;
                identityManagerSession.session.addSessionCommitListener(this);
            } catch (JsonProcessingException e) {
                // skip update if json fails
            }
        }

        // 4. add trust origin (this already checks for duplicates)
        String keycloakServerUrl = identityManagerSession.identityDelegate.getOwnedIdentityKeycloakServerUrl(identityManagerSession.session, ownedIdentity);
        if (keycloakServerUrl != null) {
            addTrustOrigin(TrustOrigin.createKeycloakTrustOrigin(System.currentTimeMillis(), keycloakServerUrl));
        }
    }


    public static void unmarkAllCertifiedByOwnKeycloakContacts(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        // get the list of all certified contacts
        List<ContactIdentity> certifiedContacts = new LinkedList<>();
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM  " + TABLE_NAME +
                " WHERE " + CERTIFIED_BY_OWN_KEYCLOAK + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBoolean(1, true);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    certifiedContacts.add(new ContactIdentity(identityManagerSession, res));
                }
            }
        }

        // for each of them, set them as not certified any more
        for (ContactIdentity contactIdentity: certifiedContacts) {
            // no need to provide lastKnownSerializedCertifiedDetails as unmarkAllCertifiedByOwnKeycloakContacts is only called when:
            // - our ownedIdentity is no longer certified
            contactIdentity.setCertifiedByOwnKeycloak(false, null);
        }
    }

    public JsonIdentityDetailsWithVersionAndPhoto trustPublishedDetails() throws SQLException {
        if (trustedDetailsVersion == publishedDetailsVersion) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + TRUSTED_DETAILS_VERSION + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setInt(1, publishedDetailsVersion);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.trustedDetailsVersion = publishedDetailsVersion;
        }
        hookTrustedDetails = getTrustedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
        commitHookBits |= HOOK_BIT_PUBLISHED_DETAILS_TRUSTED;
        identityManagerSession.session.addSessionCommitListener(this);
        return hookTrustedDetails;
    }

    public void setDetailsDownloadedPhotoUrl(int version, byte[] photo) throws Exception {
        ContactIdentityDetails contactIdentityDetails = ContactIdentityDetails.get(identityManagerSession, contactIdentity, ownedIdentity, version);

        if (contactIdentityDetails == null) {
            return;
        }

        // find a non-existing fileName
        String fileName = Constants.IDENTITY_PHOTOS_DIRECTORY + File.separator + Logger.toHexString(Arrays.copyOfRange(contactIdentity.getBytes(), contactIdentity.getBytes().length-32, contactIdentity.getBytes().length));
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
        contactIdentityDetails.setPhotoUrl(randFileName);
        hookPhotoSetVersion = version;
        commitHookBits |= HOOK_BIT_PHOTO_SET;
        identityManagerSession.session.addSessionCommitListener(this);
    }

    public void addTrustOrigin(TrustOrigin newTrustOrigin) throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Calling ContactIdentity.addTrustOrigin() outside a transaction");
            throw new SQLException();
        }

        //////////
        // if newTrustOrigin is not DIRECT, check if it is not already there --> avoid pointless duplicates
        //////////
        if (newTrustOrigin.getType() != TrustOrigin.TYPE.DIRECT) {
            ContactTrustOrigin[] contactTrustOrigins = ContactTrustOrigin.getAll(identityManagerSession, contactIdentity, ownedIdentity);
            for (ContactTrustOrigin contactTrustOrigin: contactTrustOrigins) {
                TrustOrigin other = contactTrustOrigin.getTrustOrigin();
                if (newTrustOrigin.equals(other)) {
                    // we have a duplicate --> do not add the newTrustOrigin
                    return;
                }
            }
        }

        ContactTrustOrigin contactTrustOrigin = ContactTrustOrigin.create(identityManagerSession, contactIdentity, ownedIdentity, newTrustOrigin);
        if (contactTrustOrigin == null) {
            Logger.e("Error create contactTrustOrigin in ContactIdentity.addTrustOrigin()");
            throw new SQLException();
        }
        TrustLevel newTrustLevel = contactTrustOrigin.getTrustLevel();
        if (newTrustLevel.compareTo(trustLevel) > 0) {
            setTrustLevel(newTrustLevel);
        }
    }

    private void setTrustLevel(TrustLevel trustLevel) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + TRUST_LEVEL + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setString(1, trustLevel.toString());
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.trustLevel = trustLevel;
            commitHookBits |= HOOK_BIT_TRUST_LEVEL_INCREASED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    public void setLastNoDeviceContactDeviceDiscovery(long lastNoDeviceContactDeviceDiscovery) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + LAST_NO_DEVICE_CONTACT_DEVICE_DISCOVERY + " = ? " +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setLong(1, lastNoDeviceContactDeviceDiscovery);
            statement.setBytes(2, contactIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            statement.executeUpdate();
            this.lastNoDeviceContactDeviceDiscovery = lastNoDeviceContactDeviceDiscovery;
        }
    }

    // endregion

    // region constructors

    public static ContactIdentity create(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto, TrustOrigin trustOrigin, boolean revokedAsCompromised, boolean oneToOne) {
        if ((contactIdentity == null) || (ownedIdentity == null) || (jsonIdentityDetailsWithVersionAndPhoto == null)) {
            return null;
        }
        try {
            if (!identityManagerSession.session.isInTransaction()) {
                Logger.e("Calling ContactIdentity.create() outside a transaction");
                throw new SQLException();
            }
            ContactIdentityDetails contactIdentityDetails = ContactIdentityDetails.create(identityManagerSession, contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto);
            if (contactIdentityDetails == null) {
                Logger.e("Error create contactIdentityDetails in ContactIdentity.create()");
                throw new SQLException();
            }

            // when creating a not one-to-one contact, set their one-to-one status as unknown
            ContactIdentity contactIdentityObject = new ContactIdentity(identityManagerSession, contactIdentity, ownedIdentity, contactIdentityDetails.getVersion(), new TrustLevel(0, 0), oneToOne ? ONE_TO_ONE_STATUS_TRUE : ONE_TO_ONE_STATUS_UNKNOWN);
            contactIdentityObject.revokedAsCompromised = revokedAsCompromised;
            contactIdentityObject.insert();

            JsonKeycloakUserDetails jsonKeycloakUserDetails = identityManagerSession.identityDelegate.verifyKeycloakSignature(identityManagerSession.session, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());

            if (jsonKeycloakUserDetails != null) {
                try {
                    JsonIdentityDetails certifiedJsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(jsonIdentityDetailsWithVersionAndPhoto.getIdentityDetails().getSignedUserDetails());
                    contactIdentityObject.markContactAsCertifiedByOwnKeycloak(certifiedJsonIdentityDetails);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (trustOrigin != null) {
                ContactTrustOrigin contactTrustOrigin = ContactTrustOrigin.create(identityManagerSession, contactIdentity, ownedIdentity, trustOrigin);
                if (contactTrustOrigin == null) {
                    Logger.e("Error create contactTrustOrigin in ContactIdentity.create()");
                    throw new SQLException();
                }
                contactIdentityObject.setTrustLevel(contactTrustOrigin.getTrustLevel());
            } else {
                contactIdentityObject.setTrustLevel(new TrustLevel(0, 0));
            }
            return contactIdentityObject;
        } catch (SQLException e) {
            return null;
        }
    }

    public ContactIdentity(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, int version, TrustLevel trustLevel, int oneToOne) {
        this.identityManagerSession = identityManagerSession;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
        this.trustedDetailsVersion = version;
        this.publishedDetailsVersion = version;
        this.trustLevel = trustLevel;
        this.certifiedByOwnKeycloak = false; // this will be set at a later time
        this.revokedAsCompromised = false;
        this.forcefullyTrustedByUser = false;
        this.oneToOne = oneToOne;
        this.lastNoDeviceContactDeviceDiscovery = 0;
    }

    private ContactIdentity(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.trustedDetailsVersion = res.getInt(TRUSTED_DETAILS_VERSION);
        this.publishedDetailsVersion = res.getInt(PUBLISHED_DETAILS_VERSION);
        this.trustLevel = TrustLevel.of(res.getString(TRUST_LEVEL));
        this.certifiedByOwnKeycloak = res.getBoolean(CERTIFIED_BY_OWN_KEYCLOAK);
        this.revokedAsCompromised = res.getBoolean(REVOKED_AS_COMPROMISED);
        this.forcefullyTrustedByUser = res.getBoolean(FORCEFULLY_TRUSTED_BY_USER);
        this.oneToOne = res.getInt(ONE_TO_ONE);
        this.lastNoDeviceContactDeviceDiscovery = res.getLong(LAST_NO_DEVICE_CONTACT_DEVICE_DISCOVERY);
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    TRUSTED_DETAILS_VERSION + " INT NOT NULL, " +
                    PUBLISHED_DETAILS_VERSION + " INT NOT NULL, " +
                    TRUST_LEVEL + " TEXT NOT NULL, " +
                    CERTIFIED_BY_OWN_KEYCLOAK + " BIT NOT NULL, " +
                    REVOKED_AS_COMPROMISED + " BIT NOT NULL, " +
                    FORCEFULLY_TRUSTED_BY_USER + " BIT NOT NULL, " +
                    ONE_TO_ONE + " BIT NOT NULL, " +
                    LAST_NO_DEVICE_CONTACT_DEVICE_DISCOVERY + " INTEGER NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + "), " +
                    " FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + "(" + OwnedIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE, " +
                    " FOREIGN KEY (" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ", " + TRUSTED_DETAILS_VERSION + ") REFERENCES " + ContactIdentityDetails.TABLE_NAME + "(" + ContactIdentityDetails.CONTACT_IDENTITY + ", " + ContactIdentityDetails.OWNED_IDENTITY + ", " + ContactIdentityDetails.VERSION + "), " +
                    " FOREIGN KEY (" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ", " + PUBLISHED_DETAILS_VERSION + ") REFERENCES " + ContactIdentityDetails.TABLE_NAME + "(" + ContactIdentityDetails.CONTACT_IDENTITY + ", " + ContactIdentityDetails.OWNED_IDENTITY + ", " + ContactIdentityDetails.VERSION + "));");
        }
    }

    @SuppressWarnings("UnusedAssignment")
    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 6 && newVersion >= 6) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE contact_identity RENAME TO old_contact_identity");
                statement.execute("CREATE TABLE IF NOT EXISTS contact_identity_details (" +
                        " contact_identity BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " version INT NOT NULL, " +
                        " serialized_json_details TEXT NOT NULL, " +
                        " photo_url TEXT, " +
                        " photo_server_label BLOB, " +
                        " photo_server_key BLOB, " +
                        " CONSTRAINT PK_contact_identity_details PRIMARY KEY(contact_identity, owned_identity, version));");
                statement.execute("CREATE TABLE IF NOT EXISTS contact_identity (" +
                        " identity BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " trusted_details_version INT NOT NULL, " +
                        " published_details_version INT NOT NULL, " +
                        " encoded_trust_origins BLOB NOT NULL, " +
                        " CONSTRAINT PK_contact_identity PRIMARY KEY(identity, owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity(identity)," +
                        " FOREIGN KEY (identity, owned_identity, trusted_details_version) REFERENCES contact_identity_details(contact_identity, owned_identity, version)," +
                        " FOREIGN KEY (identity, owned_identity, published_details_version) REFERENCES contact_identity_details(contact_identity, owned_identity, version));");
                ObjectMapper objectMapper = new ObjectMapper();
                try (ResultSet res = statement.executeQuery("SELECT * FROM old_contact_identity")) {
                    while (res.next()) {
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_identity VALUES (?,?,?,?,?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setInt(3, 0);
                            preparedStatement.setInt(4, 0);
                            preparedStatement.setBytes(5, res.getBytes(5));
                            preparedStatement.executeUpdate();
                        }
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_identity_details VALUES (?,?,?,?,?, ?,?);")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setInt(3, 0);
                            HashMap<String, String> map = new HashMap<>();
                            map.put("first_name", res.getString(3));
                            try {
                                preparedStatement.setString(4, objectMapper.writeValueAsString(map));
                            } catch (Exception e) {
                                e.printStackTrace();
                                // skip the contact
                                continue;
                            }
                            preparedStatement.setString(5, null);
                            preparedStatement.setBytes(6, null);
                            preparedStatement.setBytes(7, null);
                            preparedStatement.executeUpdate();
                        }
                    }
                }
                statement.execute("DROP TABLE old_contact_identity");
            }
            oldVersion = 6;
        }
        if (oldVersion < 9 && newVersion >= 9) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE contact_identity RENAME TO old_contact_identity");
                statement.execute("CREATE TABLE IF NOT EXISTS contact_identity (" +
                        " identity BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " trusted_details_version INT NOT NULL, " +
                        " published_details_version INT NOT NULL, " +
                        " trust_level TEXT NOT NULL, " +
                        " CONSTRAINT PK_contact_identity PRIMARY KEY(identity, owned_identity), " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity(identity)," +
                        " FOREIGN KEY (identity, owned_identity, trusted_details_version) REFERENCES contact_identity_details(contact_identity, owned_identity, version)," +
                        " FOREIGN KEY (identity, owned_identity, published_details_version) REFERENCES contact_identity_details(contact_identity, owned_identity, version));");
                statement.execute("CREATE TABLE IF NOT EXISTS contact_trust_origin (" +
                        "row_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "contact_identity BLOB NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "trust_type INTEGER NOT NULL, " +
                        "mediator_or_group_owner_identity BLOB, " +
                        "mediator_or_group_owner_trust_level_major INTEGER, " +
                        "identity_server TEXT, " +
                        " FOREIGN KEY (contact_identity, owned_identity ) REFERENCES contact_identity(identity, owned_identity) ON DELETE CASCADE);");
                try (ResultSet res = statement.executeQuery("SELECT * FROM old_contact_identity")) {
                    while (res.next()) {
                        try {
                            Encoded encodeds = new Encoded(res.getBytes(5));
                            int maxTL = 0;
                            for (Encoded encoded: encodeds.decodeList()) {
                                try {
                                    Encoded[] listOfEncoded = encoded.decodeList();
                                    if (listOfEncoded.length == 0) {
                                        continue;
                                    }
                                    int type = (int) listOfEncoded[0].decodeLong();
                                    switch (type) {
                                        case 0: // direct
                                            if (listOfEncoded.length != 2) {
                                                continue;
                                            }
                                            try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_trust_origin(contact_identity,owned_identity,timestamp,trust_type,mediator_or_group_owner_identity,mediator_or_group_owner_trust_level_major,identity_server) VALUES (?,?,?,?,?,?,?);")) {
                                                preparedStatement.setBytes(1, res.getBytes(1));
                                                preparedStatement.setBytes(2, res.getBytes(2));
                                                preparedStatement.setLong(3, listOfEncoded[1].decodeLong());
                                                preparedStatement.setInt(4, 1);
                                                preparedStatement.setBytes(5, null);
                                                preparedStatement.setNull(6, Types.INTEGER);
                                                preparedStatement.setString(7, null);
                                                preparedStatement.executeUpdate();
                                            }
                                            if (maxTL < 4) {
                                                maxTL = 4;
                                            }
                                            break;
                                        case 1: //mediator
                                            if (listOfEncoded.length != 3) {
                                                continue;
                                            }
                                            try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_trust_origin(contact_identity,owned_identity,timestamp,trust_type,mediator_or_group_owner_identity,mediator_or_group_owner_trust_level_major,identity_server) VALUES (?,?,?,?,?,?,?);")) {
                                                preparedStatement.setBytes(1, res.getBytes(1));
                                                preparedStatement.setBytes(2, res.getBytes(2));
                                                preparedStatement.setLong(3, listOfEncoded[1].decodeLong());
                                                preparedStatement.setInt(4, 2);
                                                preparedStatement.setBytes(5, listOfEncoded[2].decodeBytes());
                                                preparedStatement.setInt(6, 4);
                                                preparedStatement.setString(7, null);
                                                preparedStatement.executeUpdate();
                                            }
                                            if (maxTL < 2) {
                                                maxTL = 2;
                                            }
                                            break;
                                        case 2: // group
                                            if (listOfEncoded.length != 4) {
                                                continue;
                                            }
                                            try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_trust_origin(contact_identity,owned_identity,timestamp,trust_type,mediator_or_group_owner_identity,mediator_or_group_owner_trust_level_major,identity_server) VALUES (?,?,?,?,?,?,?);")) {
                                                preparedStatement.setBytes(1, res.getBytes(1));
                                                preparedStatement.setBytes(2, res.getBytes(2));
                                                preparedStatement.setLong(3, listOfEncoded[1].decodeLong());
                                                preparedStatement.setInt(4, 3);
                                                preparedStatement.setBytes(5, listOfEncoded[2].decodeBytes());
                                                preparedStatement.setInt(6, 4);
                                                preparedStatement.setString(7, null);
                                                preparedStatement.executeUpdate();
                                            }
                                            if (maxTL < 2) {
                                                maxTL = 2;
                                            }
                                            break;
                                    }
                                } catch (DecodingException e) {
                                    // do nothing
                                }
                            }
                            try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_identity VALUES (?,?,?,?,?);")) {
                                preparedStatement.setBytes(1, res.getBytes(1));
                                preparedStatement.setBytes(2, res.getBytes(2));
                                preparedStatement.setInt(3, res.getInt(3));
                                preparedStatement.setInt(4, res.getInt(4));
                                if (maxTL == 4) {
                                    preparedStatement.setString(5, "4.0");
                                } else if (maxTL == 2) {
                                    preparedStatement.setString(5, "2.4");
                                } else {
                                    preparedStatement.setString(5, "0.0");
                                }
                                preparedStatement.executeUpdate();
                            }
                        } catch (DecodingException ignored) { }
                    }
                }
                statement.execute("DROP TABLE old_contact_identity");
            }
            oldVersion = 9;
        }
        if (oldVersion < 20 && newVersion >= 20) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE contact_identity ADD COLUMN keycloak_managed BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 20;
        }
        if (oldVersion < 25 && newVersion >= 25) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE contact_identity ADD COLUMN revoked_as_compromised BIT NOT NULL DEFAULT 0");
                statement.execute("ALTER TABLE contact_identity ADD COLUMN forcefully_trusted_by_user BIT NOT NULL DEFAULT 0");
            }
            oldVersion = 25;
        }
        if (oldVersion < 28 && newVersion >= 28) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `contact_identity` TABLE FROM VERSION " + oldVersion + " TO 28");
                statement.execute("ALTER TABLE contact_identity ADD COLUMN one_to_one BIT NOT NULL DEFAULT 1");
            }
            oldVersion = 28;
        }
        if (oldVersion < 35 && newVersion >= 35) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING `contact_identity` TABLE FROM VERSION " + oldVersion + " TO 35");
                statement.execute("ALTER TABLE contact_identity ADD COLUMN last_no_device_contact_device_discovery INTEGER NOT NULL DEFAULT 0");
            }
            oldVersion = 35;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?, ?,?,?,?,?);")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setInt(3, trustedDetailsVersion);
            statement.setInt(4, publishedDetailsVersion);
            statement.setString(5, trustLevel.toString());

            statement.setBoolean(6, certifiedByOwnKeycloak);
            statement.setBoolean(7, revokedAsCompromised);
            statement.setBoolean(8, forcefullyTrustedByUser);
            statement.setInt(9, oneToOne);
            statement.setLong(10, lastNoDeviceContactDeviceDiscovery);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("Running ContactIdentity delete outside a transaction");
            throw new SQLException();
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_DELETED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + ContactIdentityDetails.TABLE_NAME +
                " WHERE " + ContactIdentityDetails.CONTACT_IDENTITY + " = ? " +
                " AND " + ContactIdentityDetails.OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.executeUpdate();
        }
    }

    // endregion

    // region getters

    public static ContactIdentity get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        if ((contactIdentity == null) || (ownedIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactIdentity(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static ContactIdentity[] getAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentity(identityManagerSession, res));
                }
                return list.toArray(new ContactIdentity[0]);
            }
        } catch (SQLException e) {
            return new ContactIdentity[0];
        }
    }

    public static List<ContactIdentity> getAllCertifiedByKeycloak(IdentityManagerSession identityManagerSession, Identity ownedIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + CERTIFIED_BY_OWN_KEYCLOAK + " = 1;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentity(identityManagerSession, res));
                }
                return list;
            }
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public static ContactIdentity[] getAllForAllOwnedIdentities(IdentityManagerSession identityManagerSession) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME)) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentity(identityManagerSession, res));
                }
                return list.toArray(new ContactIdentity[0]);
            }
        } catch (SQLException e) {
            return new ContactIdentity[0];
        }
    }

    public static ContactIdentity[] getAllActiveWithoutDevices(IdentityManagerSession identityManagerSession) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME + " AS c WHERE " +
                        " (" + REVOKED_AS_COMPROMISED + " = 0 OR " + FORCEFULLY_TRUSTED_BY_USER + " = 1) " +
                        " AND NOT EXISTS (" +
                        " SELECT 1 FROM " + ContactDevice.TABLE_NAME +
                        " WHERE " + ContactDevice.CONTACT_IDENTITY + " = c." + CONTACT_IDENTITY +
                        " AND " + ContactDevice.OWNED_IDENTITY + " = c." + OWNED_IDENTITY + ")")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentity(identityManagerSession, res));
                }
                return list.toArray(new ContactIdentity[0]);
            }
        } catch (SQLException e) {
            return new ContactIdentity[0];
        }
    }

    public static ContactIdentity[] getAllInactiveWithDevices(IdentityManagerSession identityManagerSession) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME + " AS c WHERE " +
                        " (" + REVOKED_AS_COMPROMISED + " = 1 AND " + FORCEFULLY_TRUSTED_BY_USER + " = 0) " +
                        " AND EXISTS (" +
                        " SELECT 1 FROM " + ContactDevice.TABLE_NAME +
                        " WHERE " + ContactDevice.CONTACT_IDENTITY + " = c." + CONTACT_IDENTITY +
                        " AND " + ContactDevice.OWNED_IDENTITY + " = c." + OWNED_IDENTITY + ")")) {
            try (ResultSet res = statement.executeQuery()) {
                List<ContactIdentity> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactIdentity(identityManagerSession, res));
                }
                return list.toArray(new ContactIdentity[0]);
            }
        } catch (SQLException e) {
            return new ContactIdentity[0];
        }
    }

    public static String getSerializedPublishedDetails(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT details." + ContactIdentityDetails.SERIALIZED_JSON_DETAILS +
                        " FROM " + TABLE_NAME + " AS contact " +
                        " INNER JOIN " + ContactIdentityDetails.TABLE_NAME + " AS details " +
                        " ON contact." + OWNED_IDENTITY + " = details." + ContactIdentityDetails.OWNED_IDENTITY +
                        " AND contact." + CONTACT_IDENTITY + " = details." + ContactIdentityDetails.CONTACT_IDENTITY +
                        " AND contact." + PUBLISHED_DETAILS_VERSION + " = details." + ContactIdentityDetails.VERSION +
                        " WHERE contact." + OWNED_IDENTITY + " = ? " +
                        " AND contact." + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setBytes(2, contactIdentity.getBytes());
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

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;
    private static final long HOOK_BIT_DELETED = 0x2;
    private static final long HOOK_BIT_PUBLISHED_DETAILS_TRUSTED = 0x4;
    private JsonIdentityDetailsWithVersionAndPhoto hookTrustedDetails;
    private static final long HOOK_BIT_NEW_PUBLISHED_DETAILS = 0x8;
    private int hookPhotoSetVersion;
    private static final long HOOK_BIT_PHOTO_SET = 0x10;
    private static final long HOOK_BIT_TRUST_LEVEL_INCREASED = 0x20;
    private static final long HOOK_BIT_KEYCLOAK_MANAGED_CHANGED = 0x40;
    private static final long HOOK_BIT_ACTIVE_CHANGED = 0x80;
    private static final long HOOK_BIT_REVOKED = 0x100;
    private static final long HOOK_BIT_ONE_TO_ONE_CHANGED = 0x200;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_KEYCLOAK_MANAGED_KEY, certifiedByOwnKeycloak);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ACTIVE_KEY, isActive());
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ONE_TO_ONE_KEY, isOneToOne());
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_TRUST_LEVEL_KEY, trustLevel.major);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_NEW_PUBLISHED_DETAILS) != 0 && (commitHookBits & HOOK_BIT_PUBLISHED_DETAILS_TRUSTED) == 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_PUBLISHED_DETAILS_TRUSTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY, hookTrustedDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_PHOTO_SET) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_VERSION_KEY, hookPhotoSetVersion);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_IS_TRUSTED_KEY, hookPhotoSetVersion == trustedDetailsVersion);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_TRUST_LEVEL_INCREASED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY, trustLevel);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_KEYCLOAK_MANAGED_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY, certifiedByOwnKeycloak);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_ACTIVE_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_ACTIVE_KEY, isActive());
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_REVOKED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_OWNED_IDENTITY_KEY, ownedIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_ONE_TO_ONE_CHANGED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY, isOneToOne());
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED, userInfo);
        }
        commitHookBits = 0;
   }

    // endregion




    // region backup

    public static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        ContactIdentity[] contactIdentities = getAll(identityManagerSession, ownedIdentity);
        Pojo_0[] pojos = new Pojo_0[contactIdentities.length];
        for (int i=0; i<contactIdentities.length; i++) {
            pojos[i] = contactIdentities[i].backup();
        }
        return pojos;
    }

    public static void restoreAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Pojo_0[] pojos, long backupTimestamp) throws SQLException {
        if (pojos == null) {
            return;
        }
        // first recreate all contacts
        for (Pojo_0 pojo: pojos) {
            restoreContact(identityManagerSession, ownedIdentity, pojo);
        }
        // then recreate all groups
        for (Pojo_0 pojo: pojos) {
            restoreContactGroups(identityManagerSession, ownedIdentity, pojo, backupTimestamp);
        }
    }

    Pojo_0 backup() throws SQLException {
        Pojo_0 pojo = new Pojo_0();
        pojo.contact_identity= contactIdentity.getBytes();
        pojo.trusted_details = getTrustedDetails().backup();
        if (publishedDetailsVersion != trustedDetailsVersion) {
            pojo.published_details = getPublishedDetails().backup();
        }
        pojo.trust_level = trustLevel.toString();
        pojo.revoked = revokedAsCompromised;
        pojo.forcefully_trusted = forcefullyTrustedByUser;
        switch (oneToOne) {
            case ONE_TO_ONE_STATUS_TRUE: {
                pojo.one_to_one = true;
                break;
            }
            case ONE_TO_ONE_STATUS_FALSE: {
                pojo.one_to_one = false;
                break;
            }
            case ONE_TO_ONE_STATUS_UNKNOWN:
            default: {
                pojo.one_to_one = null;
                break;
            }
        }
        pojo.trust_origins = ContactTrustOrigin.backupAll(identityManagerSession, ownedIdentity, contactIdentity);
        pojo.contact_groups = ContactGroup.backupAllForOwner(identityManagerSession, ownedIdentity, contactIdentity);

        return pojo;
    }

    private static void restoreContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Pojo_0 pojo) throws SQLException {
        Identity contactIdentity = null;
        try {
            contactIdentity = Identity.of(pojo.contact_identity);
        } catch (DecodingException e) {
            Logger.e("Error recreating ContactIdentity from backup!");
            e.printStackTrace();
        }
        if (contactIdentity == null) {
            return;
        }

        identityManagerSession.session.startTransaction();
        ContactIdentityDetails trusted_details = ContactIdentityDetails.restore(identityManagerSession, ownedIdentity, contactIdentity, pojo.trusted_details);
        ContactIdentityDetails published_details = null;
        if (pojo.published_details != null && pojo.published_details.version != pojo.trusted_details.version) {
            published_details = ContactIdentityDetails.restore(identityManagerSession, ownedIdentity, contactIdentity, pojo.published_details);
        }

        ContactIdentity contactIdentityObject = new ContactIdentity(identityManagerSession, contactIdentity, ownedIdentity, trusted_details.getVersion(), TrustLevel.of(pojo.trust_level), pojo.one_to_one == null ? ONE_TO_ONE_STATUS_UNKNOWN : (pojo.one_to_one ? ONE_TO_ONE_STATUS_TRUE : ONE_TO_ONE_STATUS_FALSE));
        if (published_details != null) {
            contactIdentityObject.publishedDetailsVersion = published_details.getVersion();
        }
        contactIdentityObject.revokedAsCompromised = pojo.revoked;
        contactIdentityObject.forcefullyTrustedByUser = pojo.forcefully_trusted;
        contactIdentityObject.insert();

        JsonKeycloakUserDetails jsonKeycloakUserDetails = identityManagerSession.identityDelegate.verifyKeycloakSignature(identityManagerSession.session, ownedIdentity, trusted_details.getJsonIdentityDetailsWithVersionAndPhoto().getIdentityDetails().getSignedUserDetails());
        if (jsonKeycloakUserDetails != null) {
            contactIdentityObject.setCertifiedByOwnKeycloak(true, null);
        }

        ContactTrustOrigin.restoreAll(identityManagerSession, ownedIdentity, contactIdentity, pojo.trust_origins);
        identityManagerSession.session.commit();
    }


    private static void restoreContactGroups(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Pojo_0 pojo, long backupTimestamp) throws SQLException {
        Identity contactIdentity = null;
        try {
            contactIdentity = Identity.of(pojo.contact_identity);
        } catch (DecodingException e) {
            Logger.e("Error recreating ContactIdentityGroups from backup!");
            e.printStackTrace();
        }
        if (contactIdentity == null) {
            return;
        }

        ContactGroup.restoreAllForOwner(identityManagerSession, ownedIdentity, contactIdentity, pojo.contact_groups, backupTimestamp);
    }





    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        public byte[] contact_identity;
        public ContactIdentityDetails.Pojo_0 trusted_details;
        public ContactIdentityDetails.Pojo_0 published_details;
        public String trust_level;
        public boolean revoked;
        public boolean forcefully_trusted;
        public Boolean one_to_one;
        public ContactTrustOrigin.Pojo_0[] trust_origins;
        public ContactGroup.Pojo_0[] contact_groups;
    }

    // endregion
}
