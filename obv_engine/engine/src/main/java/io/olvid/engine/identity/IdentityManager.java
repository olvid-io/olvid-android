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

package io.olvid.engine.identity;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.InvalidKeyException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.MAC;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.ServerAuthentication;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PrivateIdentity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.SessionCommitListener;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonKeycloakRevocation;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.identity.databases.ContactDevice;
import io.olvid.engine.identity.databases.ContactGroup;
import io.olvid.engine.identity.databases.ContactGroupDetails;
import io.olvid.engine.identity.databases.ContactGroupMembersJoin;
import io.olvid.engine.identity.databases.ContactIdentity;
import io.olvid.engine.identity.databases.ContactIdentityDetails;
import io.olvid.engine.identity.databases.ContactTrustOrigin;
import io.olvid.engine.identity.databases.KeycloakRevokedIdentity;
import io.olvid.engine.identity.databases.KeycloakServer;
import io.olvid.engine.identity.databases.OwnedDevice;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.databases.PendingGroupMember;
import io.olvid.engine.identity.databases.ServerUserData;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.identity.datatypes.IdentityManagerSessionFactory;
import io.olvid.engine.metamanager.BackupDelegate;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class IdentityManager implements IdentityDelegate, SolveChallengeDelegate, EncryptionForIdentityDelegate, IdentityManagerSessionFactory, ObvManager {
    private final String engineBaseDirectory;
    private final ObjectMapper jsonObjectMapper;
    private final SessionCommitListener backupNeededSessionCommitListener;

    private CreateSessionDelegate createSessionDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private ProtocolStarterDelegate protocolStarterDelegate;
    private ChannelDelegate channelDelegate;

    public IdentityManager(MetaManager metaManager, String engineBaseDirectory, ObjectMapper jsonObjectMapper) {
        this.engineBaseDirectory = engineBaseDirectory;
        this.jsonObjectMapper = jsonObjectMapper;
        this.backupNeededSessionCommitListener = () -> {
            if (notificationPostingDelegate != null) {
                notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED, new HashMap<>());
            }
        };

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, ProtocolStarterDelegate.class);
        metaManager.requestDelegate(this, ChannelDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    @Override
    public void initialisationComplete() {
        // notify if an ownedIdentity is inactive
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity[] ownedIdentities = OwnedIdentity.getAll(identityManagerSession);
            for (OwnedIdentity ownedIdentity : ownedIdentities) {
                if (!ownedIdentity.isActive()) {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY, ownedIdentity.getOwnedIdentity());
                    userInfo.put(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY, false);
                    identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS, userInfo);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // search for all contact identities with no deviceUids and run a deviceDiscovery
        try (IdentityManagerSession identityManagerSession = getSession()) {
            ContactIdentity[] contactIdentities = ContactIdentity.getAllActiveWithoutDevices(identityManagerSession);
            if (contactIdentities.length > 0) {
                Logger.i("Found " + contactIdentities.length + " contacts with no device. Starting corresponding deviceDiscoveryProtocols.");
                for (ContactIdentity contactIdentity : contactIdentities) {
                    protocolStarterDelegate.startDeviceDiscoveryProtocol(contactIdentity.getContactIdentity(), contactIdentity.getOwnedIdentity());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // search for all inactive contact identities with some deviceUids and delete them
        try (IdentityManagerSession identityManagerSession = getSession()) {
            ContactIdentity[] contactIdentities = ContactIdentity.getAllInactiveWithDevices(identityManagerSession);
            if (contactIdentities.length > 0) {
                Logger.i("Found " + contactIdentities.length + " inactive contacts with some devices. Cleaning them up!");
                for (ContactIdentity contactIdentity : contactIdentities) {
                    channelDelegate.deleteObliviousChannelsWithContact(identityManagerSession.session, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity());
                    removeAllDevicesForContactIdentity(identityManagerSession.session, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity());
                }
                identityManagerSession.session.commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // prune old revocation list records
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (OwnedIdentity ownedIdentity : OwnedIdentity.getAll(identityManagerSession)) {
                if (ownedIdentity.isKeycloakManaged()) {
                    KeycloakServer keycloakServer = ownedIdentity.getKeycloakServer();
                    if (keycloakServer != null) {
                        long revocationPruneTime = keycloakServer.getLatestRevocationListTimestamp() - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS;
                        KeycloakRevokedIdentity.prune(identityManagerSession, ownedIdentity.getOwnedIdentity(), ownedIdentity.getKeycloakServerUrl(), revocationPruneTime);
                    }
                }
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // clean old ownedIdentityDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (OwnedIdentity ownedIdentity : OwnedIdentity.getAll(identityManagerSession)) {
                OwnedIdentityDetails.cleanup(identityManagerSession, ownedIdentity.getOwnedIdentity(), ownedIdentity.getPublishedDetailsVersion(), ownedIdentity.getLatestDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // clean old contactIdentityDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactIdentity contactIdentity : ContactIdentity.getAllForAllOwnedIdentities(identityManagerSession)) {
                ContactIdentityDetails.cleanup(identityManagerSession, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity(), contactIdentity.getPublishedDetailsVersion(), contactIdentity.getTrustedDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // clean old contactGroupDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactGroup contactGroup : ContactGroup.getAll(identityManagerSession)) {
                ContactGroupDetails.cleanup(identityManagerSession, contactGroup.getOwnedIdentity(), contactGroup.getGroupOwnerAndUid(), contactGroup.getPublishedDetailsVersion(), contactGroup.getLatestOrTrustedDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // get the set of all owned identity, contact, group profile picture photoUrl and remove all photoUrl not in this set
        try (IdentityManagerSession identityManagerSession = getSession()) {
            File photoDir = new File(engineBaseDirectory, Constants.IDENTITY_PHOTOS_DIRECTORY);
            String[] listedPhotoUrls = photoDir.list();
            if (listedPhotoUrls != null) {
                Set<String> photoUrlsToKeep = new HashSet<>();
                for (String photoUrl : OwnedIdentityDetails.getAllPhotoUrl(identityManagerSession)) {
                    photoUrlsToKeep.add(new File(photoUrl).getName());
                }
                for (String photoUrl : ContactIdentityDetails.getAllPhotoUrl(identityManagerSession)) {
                    photoUrlsToKeep.add(new File(photoUrl).getName());
                }
                for (String photoUrl : ContactGroupDetails.getAllPhotoUrl(identityManagerSession)) {
                    photoUrlsToKeep.add(new File(photoUrl).getName());
                }

                for (String listedPhotoUrl : listedPhotoUrls) {
                    if (!photoUrlsToKeep.contains(listedPhotoUrl)) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            new File(photoDir, listedPhotoUrl).delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentityDetails.createTable(identityManagerSession.session);
            KeycloakServer.createTable(identityManagerSession.session);
            KeycloakRevokedIdentity.createTable(identityManagerSession.session);
            OwnedIdentity.createTable(identityManagerSession.session);
            OwnedDevice.createTable(identityManagerSession.session);
            ContactIdentityDetails.createTable(identityManagerSession.session);
            ContactIdentity.createTable(identityManagerSession.session);
            ContactTrustOrigin.createTable(identityManagerSession.session);
            ContactDevice.createTable(identityManagerSession.session);
            ContactGroupDetails.createTable(identityManagerSession.session);
            ContactGroup.createTable(identityManagerSession.session);
            ContactGroupMembersJoin.createTable(identityManagerSession.session);
            PendingGroupMember.createTable(identityManagerSession.session);
            ServerUserData.createTable(identityManagerSession.session);
            identityManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create identity databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        OwnedIdentityDetails.upgradeTable(session, oldVersion, newVersion);
        KeycloakServer.upgradeTable(session, oldVersion, newVersion);
        KeycloakRevokedIdentity.upgradeTable(session, oldVersion, newVersion);
        OwnedIdentity.upgradeTable(session, oldVersion, newVersion);
        OwnedDevice.upgradeTable(session, oldVersion, newVersion);
        if (oldVersion < 14 && newVersion >= 14) {
            // Drop the OwnedEphemeralIdentity table
            try (Statement statement = session.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS owned_ephemeral_identity");
            }
        }
        ContactIdentityDetails.upgradeTable(session, oldVersion, newVersion);
        ContactIdentity.upgradeTable(session, oldVersion, newVersion);
        ContactTrustOrigin.upgradeTable(session, oldVersion, newVersion);
        ContactDevice.upgradeTable(session, oldVersion, newVersion);
        ContactGroupDetails.upgradeTable(session, oldVersion, newVersion);
        ContactGroup.upgradeTable(session, oldVersion, newVersion);
        ContactGroupMembersJoin.upgradeTable(session, oldVersion, newVersion);
        PendingGroupMember.upgradeTable(session, oldVersion, newVersion);
        ServerUserData.upgradeTable(session, oldVersion, newVersion);
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setDelegate(ProtocolStarterDelegate protocolStarterDelegate) {
        this.protocolStarterDelegate = protocolStarterDelegate;
    }

    public void setDelegate(ChannelDelegate channelDelegate) {
        this.channelDelegate = channelDelegate;
    }

    public IdentityManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in IdentityManager.");
        }
        return new IdentityManagerSession(createSessionDelegate.getSession(), notificationPostingDelegate, this, engineBaseDirectory, jsonObjectMapper);
    }

    private IdentityManagerSession wrapSession(Session session) {
        return new IdentityManagerSession(session, notificationPostingDelegate, this, engineBaseDirectory, jsonObjectMapper);
    }

    public ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

    public void downloadAllUserData(Session session) throws Exception {
        List<OwnedIdentityDetails> ownedIdentityDetailsList = OwnedIdentityDetails.getAllWithMissinPhotoUrl(wrapSession(session));
        for (OwnedIdentityDetails ownedIdentityDetails : ownedIdentityDetailsList) {
            protocolStarterDelegate.startDownloadIdentityPhotoProtocolWithinTransaction(session, ownedIdentityDetails.getOwnedIdentity(), ownedIdentityDetails.getOwnedIdentity(), ownedIdentityDetails.getJsonIdentityDetailsWithVersionAndPhoto());
        }

        List<ContactIdentityDetails> contactIdentityDetailsList = ContactIdentityDetails.getAllWithMissingPhotoUrl(wrapSession(session));
        for (ContactIdentityDetails contactIdentityDetails : contactIdentityDetailsList) {
            protocolStarterDelegate.startDownloadIdentityPhotoProtocolWithinTransaction(session, contactIdentityDetails.getOwnedIdentity(), contactIdentityDetails.getContactIdentity(), contactIdentityDetails.getJsonIdentityDetailsWithVersionAndPhoto());
        }

        List<ContactGroupDetails> contactGroupDetailsList = ContactGroupDetails.getAllWithMissingPhotoUrl(wrapSession(session));
        for (ContactGroupDetails contactGroupDetails : contactGroupDetailsList) {
            protocolStarterDelegate.startDownloadGroupPhotoWithinTransactionProtocol(session, contactGroupDetails.getOwnedIdentity(), contactGroupDetails.getGroupOwnerAndUid(), contactGroupDetails.getJsonGroupDetailsWithVersionAndPhoto());
        }
    }


    // region Implement SolveChallengeDelegate

    @Override
    public byte[] solveChallenge(byte[] challenge, Identity identity, PRNGService prng) throws Exception {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity ownedIdentity = OwnedIdentity.get(identityManagerSession, identity);
            if (ownedIdentity == null) {
                throw new Exception("Unknown owned identity");
            }
            PrivateIdentity privateIdentity = ownedIdentity.getPrivateIdentity();
            ServerAuthentication serverAuth = Suite.getServerAuthentication(privateIdentity.getServerAuthenticationPublicKey());
            return serverAuth.solveChallenge(challenge, privateIdentity.getServerAuthenticationPrivateKey(), privateIdentity.getServerAuthenticationPublicKey(), prng);
        } catch (InvalidKeyException | SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public UUID getApiKey(Identity identity) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity ownedIdentity = OwnedIdentity.get(identityManagerSession, identity);
            if (ownedIdentity != null) {
                return ownedIdentity.getApiKey();
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // endregion


    // region Implement IdentityDelegate

    // region OwnedIdentity

    @Override
    public boolean isOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        return ownedIdentityObject != null;
    }

    @Override
    public boolean isActiveOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        return OwnedIdentity.isActive(wrapSession(session), ownedIdentity);
    }

    @Override
    public Identity generateOwnedIdentity(Session session, String server, JsonIdentityDetails jsonIdentityDetails, UUID apiKey, ObvKeycloakState keycloakState, PRNGService prng) throws SQLException {
        if (!session.isInTransaction()) {
            session.startTransaction();
        }
        OwnedIdentity ownedIdentity = OwnedIdentity.create(wrapSession(session), server, null, null, jsonIdentityDetails, apiKey, prng);
        if (ownedIdentity == null) {
            return null;
        }

        if (keycloakState != null) {
            KeycloakServer keycloakServer = KeycloakServer.create(wrapSession(session), keycloakState.keycloakServer, ownedIdentity.getOwnedIdentity(), keycloakState.jwks.toJson(), keycloakState.signatureKey == null ? null : keycloakState.signatureKey.toJson(), keycloakState.clientId, keycloakState.clientSecret);
            if (keycloakServer == null) {
                return null;
            }
            ownedIdentity.setKeycloakServerUrl(keycloakServer.getServerUrl());
            KeycloakServer.saveAuthState(wrapSession(session), keycloakState.keycloakServer, ownedIdentity.getOwnedIdentity(), keycloakState.serializedAuthState);
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);
        return ownedIdentity.getOwnedIdentity();
    }

    @Override
    public void deleteOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            // delete all contact groups (and associated details)
            //  - this cascade deletes ContactGroupMembersJoin
            //  - this cascade deletes PendingGroupMember
            ContactGroup[] contactGroups = ContactGroup.getAllForIdentity(wrapSession(session), ownedIdentity);
            for (ContactGroup contactGroup : contactGroups) {
                contactGroup.delete();
            }

            // delete all contacts (and associated details)
            //  - this cascade deletes ContactDevice
            //  - this cascade deletes ContactTrustOrigin
            ContactIdentity[] contactIdentities = ContactIdentity.getAll(wrapSession(session), ownedIdentity);
            for (ContactIdentity contactIdentity : contactIdentities) {
                contactIdentity.delete();
            }

            // delete server user data
            ServerUserData.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);

            // delete the ownedIdentity (and associated details)
            //  - this cascade deletes OwnedDevice
            ownedIdentityObject.delete();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public Identity[] getOwnedIdentities(Session session) throws SQLException {
        OwnedIdentity[] ownedIdentities = OwnedIdentity.getAll(wrapSession(session));
        Identity[] identities = new Identity[ownedIdentities.length];
        for (int i = 0; i < ownedIdentities.length; i++) {
            identities[i] = ownedIdentities[i].getOwnedIdentity();
        }
        return identities;
    }

    @Override
    public void updateLatestIdentityDetails(Session session, Identity ownedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.setLatestDetails(jsonIdentityDetails);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void updateOwnedIdentityPhoto(Session session, Identity ownedIdentity, String absolutePhotoUrl) throws Exception {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.setPhoto(absolutePhotoUrl);
        }
    }

    @Override
    public void setOwnedDetailsDownloadedPhoto(Session session, Identity ownedIdentity, int version, byte[] photo) throws Exception {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.setDetailsDownloadedPhotoUrl(version, photo);
        }
    }


    @Override
    public void setOwnedIdentityDetailsServerLabelAndKey(Session session, Identity ownedIdentity, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.setPhotoLabelAndKey(version, photoServerLabel, photoServerKey);
            if (ServerUserData.createForOwnedIdentityDetails(wrapSession(session), ownedIdentity, photoServerLabel) == null) {
                throw new SQLException();
            }
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public int publishLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            session.addSessionCommitListener(backupNeededSessionCommitListener);
            return ownedIdentityObject.publishLatestDetails();
        }
        return -1;
    }

    @Override
    public void discardLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.discardLatestDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public String getSerializedPublishedDetailsOfOwnedIdentity(Session session, Identity ownedIdentity) {
        return OwnedIdentity.getSerializedPublishedDetails(wrapSession(session), ownedIdentity);
    }

    @Override
    public JsonIdentityDetailsWithVersionAndPhoto getOwnedIdentityPublishedDetails(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getPublishedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
        }
        return null;
    }

    @Override
    public boolean isOwnedIdentityKeycloakManaged(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.isKeycloakManaged();
        }
        return false;
    }

    @Override
    public List<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(Session session, String pushTopic) throws SQLException {
        List<KeycloakServer> keycloakServers = KeycloakServer.getAllWithPushTopic(wrapSession(session), pushTopic);
        List<ObvIdentity> ownedIdentities = new ArrayList<>();
        for (KeycloakServer keycloakServer: keycloakServers) {
            ownedIdentities.add(new ObvIdentity(session, this, keycloakServer.getOwnedIdentity()));
        }
        return ownedIdentities;
    }

    @Override
    public ObvKeycloakState getOwnedIdentityKeycloakState(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getKeycloakState();
        }
        return null;
    }

    @Override
    public JsonWebKeySet getOwnedIdentityKeycloakJwks(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getKeycloakJwks();
        }
        return null;
    }

    @Override
    public JsonWebKey getOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getKeycloakSignatureKey();
        }
        return null;
    }

    @Override
    public void setOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity, JsonWebKey signatureKey) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.setSignatureKey(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, signatureKey);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void setKeycloakLatestRevocationListTimestamp(Session session, Identity ownedIdentity, long latestRevocationListTimestamp) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.setLatestRevocationListTimestamp(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, latestRevocationListTimestamp);
            long revocationPruneTime = latestRevocationListTimestamp - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS;
            KeycloakRevokedIdentity.prune(wrapSession(session), ownedIdentity, ownedIdentityObject.getKeycloakServerUrl(), revocationPruneTime);
        }
    }

    @Override
    public void unCertifyExpiredSignedContactDetails(Session session, Identity ownedIdentity, long latestRevocationListTimestamp) {
        for (ContactIdentity contactIdentity : ContactIdentity.getAllCertifiedByKeycloak(wrapSession(session), ownedIdentity)) {
            try {
                JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                        .setSkipSignatureVerification()
                        .setSkipAllValidators()
                        .build();
                JwtContext contactContext = noVerificationConsumer.process(contactIdentity.getPublishedDetails().getJsonIdentityDetails().getSignedUserDetails());
                JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(contactContext.getJwtClaims().getRawJson(), JsonKeycloakUserDetails.class);

                if (jsonKeycloakUserDetails.getTimestamp() != null && jsonKeycloakUserDetails.getTimestamp() < latestRevocationListTimestamp - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS) {
                    // signature no longer valid --> remove certification
                    contactIdentity.setCertifiedByOwnKeycloak(false);
                }
            } catch (Exception ignored) { }
        }
    }

    @Override
    public JsonWebKeySet getTrustedKeycloakJwks(Session session, Identity ownedIdentity, String keycloakServerUrl) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), keycloakServerUrl, ownedIdentity);
        if (keycloakServer != null) {
            try {
                return keycloakServer.getJwks();
            } catch (Exception e) {
                // nothing
            }
        }
        return null;
    }

    @Override
    public JsonWebKey getTrustedKeycloakSignatureKey(Session session, Identity ownedIdentity, String keycloakServerUrl) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), keycloakServerUrl, ownedIdentity);
        if (keycloakServer != null) {
            try {
                return keycloakServer.getSignatureKey();
            } catch (Exception e) {
                // nothing
            }
        }
        return null;
    }

    @Override
    public List<String> getKeycloakPushTopics(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null || !ownedIdentityObject.isKeycloakManaged()) {
            return new ArrayList<>(0);
        }
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity);
        return keycloakServer == null ? new ArrayList<>(0) : keycloakServer.getPushTopics();
    }

    @Override
    public void verifyAndAddRevocationList(Session session, Identity ownedIdentity, List<String> signedRevocations) throws Exception {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer keycloakServer = ownedIdentityObject.getKeycloakServer();
            if (keycloakServer != null) {
                final JwksVerificationKeyResolver jwksResolver;
                JsonWebKey signatureKey = keycloakServer.getSignatureKey();
                if (signatureKey != null) {
                    jwksResolver = new JwksVerificationKeyResolver(Collections.singletonList(signatureKey));
                } else {
                    JsonWebKeySet jwks = keycloakServer.getJwks();
                    jwksResolver = new JwksVerificationKeyResolver(jwks.getJsonWebKeys());
                }
                JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                        .setExpectedAudience(false)
                        .setVerificationKeyResolver(jwksResolver)
                        .build();

                for (String signedRevocation : signedRevocations) {
                    try {
                        JwtContext context = jwtConsumer.process(signedRevocation);
                        if (context.getJwtClaims() == null) {
                            // signature is invalid, ignore this entry and proceed with the next one
                            continue;
                        }

                        JsonKeycloakRevocation jsonKeycloakRevocation = jsonObjectMapper.readValue(context.getJwtClaims().getRawJson(), JsonKeycloakRevocation.class);
                        if (jsonKeycloakRevocation == null || jsonKeycloakRevocation.getBytesRevokedIdentity() == null || jsonKeycloakRevocation.getRevocationTimestamp() == 0) {
                            // signature content is invalid, ignore this entry and proceed with the next one
                            continue;
                        }
                        Identity revokedIdentity = Identity.of(jsonKeycloakRevocation.getBytesRevokedIdentity());
                        List<KeycloakRevokedIdentity> keycloakRevokedIdentities =  KeycloakRevokedIdentity.get(wrapSession(session), ownedIdentity, revokedIdentity);
                        if (keycloakRevokedIdentities != null) {
                            boolean found = false;
                            for (KeycloakRevokedIdentity keycloakRevokedIdentity : keycloakRevokedIdentities) {
                                if (keycloakServer.getServerUrl().equals(keycloakRevokedIdentity.getKeycloakServerUrl())
                                        && jsonKeycloakRevocation.getRevocationType() == keycloakRevokedIdentity.getRevocationType()
                                        && jsonKeycloakRevocation.getRevocationTimestamp() == keycloakRevokedIdentity.getRevocationTimestamp()) {
                                    // this revocation was already inserted
                                    found = true;
                                    break;
                                }
                            }
                            if (found) {
                                // revocation already in database -> ignore this entry and proceed with the next one
                                continue;
                            }
                        }

                        // this revocation is valid and not present in database
                        KeycloakRevokedIdentity.create(wrapSession(session), ownedIdentity, keycloakServer.getServerUrl(), revokedIdentity, jsonKeycloakRevocation.getRevocationType(), jsonKeycloakRevocation.getRevocationTimestamp());

                        // now, check if the revokedIdentity is part of our contacts
                        ContactIdentity contactIdentity = ContactIdentity.get(wrapSession(session), revokedIdentity, ownedIdentity);
                        if (contactIdentity != null) {
                            switch (jsonKeycloakRevocation.getRevocationType()) {
                                case KeycloakRevokedIdentity.TYPE_LEFT_COMPANY:
                                    if (contactIdentity.isCertifiedByOwnKeycloak()) {

                                        JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                                                .setSkipSignatureVerification()
                                                .setSkipAllValidators()
                                                .build();
                                        JwtContext contactContext = noVerificationConsumer.process(contactIdentity.getPublishedDetails().getJsonIdentityDetails().getSignedUserDetails());
                                        JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(contactContext.getJwtClaims().getRawJson(), JsonKeycloakUserDetails.class);

                                        if (jsonKeycloakUserDetails.getTimestamp() == null || jsonKeycloakRevocation.getRevocationTimestamp() > jsonKeycloakUserDetails.getTimestamp()) {
                                            // the user left the company after the signature of his details --> unmark as certified
                                            contactIdentity.setCertifiedByOwnKeycloak(false);
                                        }
                                    }
                                    break;
                                case KeycloakRevokedIdentity.TYPE_COMPROMISED:
                                default:
                                    // user key is compromised: mark the contact as revoked and delete all devices/channels from this contact
                                    if (!contactIdentity.isForcefullyTrustedByUser()) {
                                        channelDelegate.deleteObliviousChannelsWithContact(session, ownedIdentity, revokedIdentity);
                                        removeAllDevicesForContactIdentity(session, ownedIdentity, revokedIdentity);
                                    }
                                    contactIdentity.setCertifiedByOwnKeycloak(false);
                                    contactIdentity.setRevokedAsCompromised(true);
                                    break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Override
    public JsonKeycloakUserDetails verifyKeycloakSignature(Session session, Identity ownedIdentity, String signature) {
        try {
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
            if (ownedIdentityObject == null || !ownedIdentityObject.isKeycloakManaged()) {
                return null;
            }
            KeycloakServer keycloakServer = ownedIdentityObject.getKeycloakServer();

            final JwksVerificationKeyResolver jwksResolver;
            JsonWebKey signatureKey = keycloakServer.getSignatureKey();
            if (signatureKey != null) {
                jwksResolver = new JwksVerificationKeyResolver(Collections.singletonList(signatureKey));
            } else {
                JsonWebKeySet jwks = keycloakServer.getJwks();
                jwksResolver = new JwksVerificationKeyResolver(jwks.getJsonWebKeys());
            }
            JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                    .setExpectedAudience(false)
                    .setVerificationKeyResolver(jwksResolver)
                    .build();


            JwtContext context = jwtConsumer.process(signature);
            if (context.getJwtClaims() != null) {
                // signature is valid, now check for a revocation
                JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(context.getJwtClaims().getRawJson(), JsonKeycloakUserDetails.class);

                if (jsonKeycloakUserDetails.getIdentity() != null) {
                    try {
                        Identity identityToVerify = Identity.of(jsonKeycloakUserDetails.getIdentity());

                        List<KeycloakRevokedIdentity> keycloakRevokedIdentities = KeycloakRevokedIdentity.get(wrapSession(session), ownedIdentity, identityToVerify);
                        if (keycloakRevokedIdentities != null) {
                            // there was a revocation!
                            for (KeycloakRevokedIdentity keycloakRevokedIdentity : keycloakRevokedIdentities) {
                                switch (keycloakRevokedIdentity.getRevocationType()) {
                                    case KeycloakRevokedIdentity.TYPE_LEFT_COMPANY:
                                        if (jsonKeycloakUserDetails.getTimestamp() == null || keycloakRevokedIdentity.getRevocationTimestamp() > jsonKeycloakUserDetails.getTimestamp()) {
                                            // the user left the company after the signature of his details --> reject
                                            return null;
                                        }
                                        break;
                                    case KeycloakRevokedIdentity.TYPE_COMPROMISED:
                                    default:
                                        return null;
                                }
                            }
                        }
                    } catch (DecodingException ignored) { }
                }

                if (jsonKeycloakUserDetails.getTimestamp() != null && jsonKeycloakUserDetails.getTimestamp() < keycloakServer.getLatestRevocationListTimestamp() - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS) {
                    // this signature is too old --> reject
                    return null;
                }

                return jsonKeycloakUserDetails;
            }
        } catch (Exception ignored) { }
        return null;
    }


    @Override
    public String getOwnedIdentityKeycloakServerUrl(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getKeycloakServerUrl();
        }
        return null;
    }

    @Override
    public void saveKeycloakAuthState(Session session, Identity ownedIdentity, String serializedAuthState) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.saveAuthState(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, serializedAuthState);
        }
    }

    @Override
    public void saveKeycloakJwks(Session session, Identity ownedIdentity, String serializedJwks) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.saveJwks(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, serializedJwks);
        }
    }

    @Override
    public String getOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getKeycloakUserId();
        }
        return null;
    }

    @Override
    public void setOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity, String userId) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.setKeycloakUserId(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, userId);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void bindOwnedIdentityToKeycloak(Session session, Identity ownedIdentity, String keycloakUserId, ObvKeycloakState keycloakState) throws Exception {
        if (ownedIdentity == null || keycloakState == null || keycloakUserId == null) {
            Logger.e("Error in bindOwnedIdentityToKeycloak: bad inputs --> aborting");
            throw new Exception();
        }
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null) {
            Logger.e("Owned identity not found in bindOwnedIdentityToKeycloak");
            throw new Exception();
        }
        if (ownedIdentityObject.isKeycloakManaged()) {
            // identity already managed --> switch keycloak server Url
            KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity);
            ownedIdentityObject.setKeycloakServerUrl(null);
            if (keycloakServer != null) {
                keycloakServer.delete();
            }
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);

        KeycloakServer keycloakServer = KeycloakServer.create(wrapSession(session), keycloakState.keycloakServer, ownedIdentity, keycloakState.jwks.toJson(), keycloakState.signatureKey == null ? null : keycloakState.signatureKey.toJson(), keycloakState.clientId, keycloakState.clientSecret);
        if (keycloakServer == null) {
            Logger.e("Unable to create new KeycloakServer db entry");
            throw new Exception();
        }
        ownedIdentityObject.setKeycloakServerUrl(keycloakServer.getServerUrl());
        keycloakServer.setKeycloakUserId(keycloakUserId);
        KeycloakServer.saveAuthState(wrapSession(session), keycloakState.keycloakServer, ownedIdentity, keycloakState.serializedAuthState);
    }


    @Override
    public int unbindOwnedIdentityFromKeycloak(Session session, Identity ownedIdentity) throws Exception {
        if (ownedIdentity == null) {
            Logger.e("Error in unbindOwnedIdentityToKeycloak: bad inputs --> aborting");
            throw new Exception();
        }
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null) {
            Logger.e("Owned identity not found in unbindOwnedIdentityFromKeycloak");
            throw new Exception();
        }
        // only do something if the identity is indeed managed
        if (ownedIdentityObject.isKeycloakManaged()) {
            /////////
            // remove the keycloak server
            KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity);
            ownedIdentityObject.setKeycloakServerUrl(null);
            if (keycloakServer != null) {
                keycloakServer.delete();
            }

            ////////
            // update owned identity details to remove signed part
            JsonIdentityDetails jsonIdentityDetails = ownedIdentityObject.getPublishedDetails().getJsonIdentityDetails();
            jsonIdentityDetails.setSignedUserDetails(null);

            ownedIdentityObject.discardLatestDetails();
            ownedIdentityObject.setLatestDetails(jsonIdentityDetails);
            return ownedIdentityObject.publishLatestDetails();
        }
        return -2;
    }


    @Override
    public JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            JsonIdentityDetailsWithVersionAndPhoto[] res;
            if (ownedIdentityObject.getPublishedDetailsVersion() == ownedIdentityObject.getLatestDetailsVersion()) {
                res = new JsonIdentityDetailsWithVersionAndPhoto[1];
                res[0] = ownedIdentityObject.getPublishedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
            } else {
                res = new JsonIdentityDetailsWithVersionAndPhoto[2];
                res[0] = ownedIdentityObject.getPublishedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
                res[1] = ownedIdentityObject.getLatestDetails().getJsonIdentityDetailsWithVersionAndPhoto();
            }
            return res;
        }
        return null;
    }


    @Override
    public void updateApiKeyOfOwnedIdentity(Session session, Identity ownedIdentity, UUID newApiKey) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        ownedIdentityObject.setApiKey(newApiKey);
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    @Override
    public boolean updateKeycloakPushTopicsIfNeeded(Session session, Identity ownedIdentity, String serverUrl, List<String> pushTopics) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), serverUrl, ownedIdentity);

        if (keycloakServer != null) {
            HashSet<String> oldSet = new HashSet<>(keycloakServer.getPushTopics());
            HashSet<String> newSet = new HashSet<>();
            if (pushTopics != null) {
                newSet.addAll(pushTopics);
            }

            if (!oldSet.equals(newSet)) {
                keycloakServer.setPushTopics(pushTopics);
                return true;
            }
        }
        return false;
    }

    @Override
    public void setOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl, String nonce) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), serverUrl, ownedIdentity);

        if (keycloakServer != null) {
            keycloakServer.setSelfRevocationTestNonce(nonce);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public String getOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), serverUrl, ownedIdentity);

        if (keycloakServer != null) {
            return keycloakServer.getSelfRevocationTestNonce();
        }
        return null;
    }


    @Override
    public void reactivateOwnedIdentityIfNeeded(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && !ownedIdentityObject.isActive()) {
            ownedIdentityObject.setActive(true);
            ////////////
            // After reactivating an identity, we must recreate all channels (that were destroyed after the deactivation
            //  - restart all device discovery protocols
            ContactIdentity[] contactIdentities = ContactIdentity.getAll(wrapSession(session), ownedIdentity);
            for (ContactIdentity contactIdentity : contactIdentities) {
                try {
                    protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(session, contactIdentity.getContactIdentity(), ownedIdentity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void deactivateOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        // set inactive even if it is already deactivated to trigger the notification
        ownedIdentityObject.setActive(false);
        ////////////
        // After deactivating an identity, we should delete all channels
        //  - clear all contact device Uid
        //  - delete all channels
        ContactDevice.deleteAll(wrapSession(session), ownedIdentity);
        channelDelegate.deleteAllChannelsForOwnedIdentity(session, ownedIdentity);
    }

    // endregion

    // region device Uids

    @Override
    public UID[] getDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null) {
            return null;
        }
        return ownedIdentityObject.getAllDeviceUids();
    }

    @Override
    public UID[] getOtherDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null) {
            return null;
        }
        return ownedIdentityObject.getOtherDeviceUids();
    }

    @Override
    // Either returns the currentDeviceUid of an ObvOwnedIdentity or the deviceUid of an OwnedEphemeralIdentity
    public UID getCurrentDeviceUidOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.getCurrentDeviceUid();
        }
        return null;
    }

    @Override
    public Identity getOwnedIdentityForDeviceUid(Session session, UID currentDeviceUid) throws SQLException {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), currentDeviceUid);
        if (ownedDevice != null) {
            return ownedDevice.getIdentity();
        }
        return null;
    }

    @Override
    public void addDeviceForOwnedIdentity(Session session, UID deviceUid, Identity ownedIdentity) throws SQLException {
        // This returns null (FOREIGN KEY CONSTRAINT FAILED) if ownedIdentity is not an OwnedIdentity
        OwnedDevice ownedDevice = OwnedDevice.createOtherDevice(wrapSession(session), deviceUid, ownedIdentity);
        if (ownedDevice == null) {
            throw new SQLException();
        }
    }

    @Override
    public boolean isRemoteDeviceUidOfOwnedIdentity(Session session, UID deviceUid, Identity ownedIdentity) throws SQLException {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), deviceUid);
        return (ownedDevice != null) && !ownedDevice.isCurrentDevice();
    }

    // endregion


    @Override
    public void addContactIdentity(Session session, Identity contactIdentity, String serializedDetails, Identity ownedIdentity, TrustOrigin trustOrigin) throws Exception {
        try {
            if (contactIdentity.equals(ownedIdentity)) {
                throw new Exception("Error: trying to add your ownedIdentity as a contact");
            }

            JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto = new JsonIdentityDetailsWithVersionAndPhoto();
            jsonIdentityDetailsWithVersionAndPhoto.setVersion(-1);
            JsonIdentityDetails jsonIdentityDetails = jsonObjectMapper.readValue(serializedDetails, JsonIdentityDetails.class);
            jsonIdentityDetailsWithVersionAndPhoto.setIdentityDetails(jsonIdentityDetails);

            boolean contactIsRevoked = false;
            List<KeycloakRevokedIdentity> keycloakRevokedIdentities = KeycloakRevokedIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
            if (keycloakRevokedIdentities != null) {
                for (KeycloakRevokedIdentity keycloakRevokedIdentity : keycloakRevokedIdentities) {
                    if (keycloakRevokedIdentity.getRevocationType() == KeycloakRevokedIdentity.TYPE_COMPROMISED) {
                        contactIsRevoked = true;
                        break;
                    }
                }
            }

            ContactIdentity contactIdentityObject = ContactIdentity.create(wrapSession(session), contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto, trustOrigin, contactIsRevoked);
            if (contactIdentityObject == null) {
                Logger.w("An error occurred while creating a ContactIdentity.");
                throw new SQLException();
            }
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception();
        }
    }

    @Override
    public void addTrustOriginToContact(Session session, Identity contactIdentity, Identity ownedIdentity, TrustOrigin trustOrigin) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject == null) {
            Logger.e("Error in addTrustOriginToContact: contactIdentity is not a ContactIdentity of ownedIdentity");
            throw new SQLException();
        }
        contactIdentityObject.addTrustOrigin(trustOrigin);
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    @Override
    public Identity[] getContactsOfOwnedIdentity(Session session, Identity ownedIdentity) {
        try {
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
            if (ownedIdentityObject != null) {
                ContactIdentity[] contactIdentities = ownedIdentityObject.getContactIdentities();
                Identity[] identities = new Identity[contactIdentities.length];
                for (int i = 0; i < contactIdentities.length; i++) {
                    identities[i] = contactIdentities[i].getContactIdentity();
                }
                return identities;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public void trustPublishedContactDetails(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.trustPublishedDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void setContactPublishedDetails(Session session, Identity contactIdentity, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto, boolean allowDowngrade) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.updatePublishedDetails(jsonIdentityDetailsWithVersionAndPhoto, allowDowngrade);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void setContactDetailsDownloadedPhoto(Session session, Identity contactIdentity, Identity ownedIdentity, int version, byte[] photo) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.setDetailsDownloadedPhotoUrl(version, photo);
        }
    }

    @Override
    public String getSerializedPublishedDetailsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) {
        return ContactIdentity.getSerializedPublishedDetails(wrapSession(session), contactIdentity, ownedIdentity);
    }

    @Override
    public JsonIdentityDetails getContactIdentityTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getTrustedDetails().getJsonIdentityDetails();
        }
        return null;
    }

    @Override
    public String getContactTrustedDetailsPhotoUrl(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getTrustedDetails().getPhotoUrl();
        }
        return null;
    }

    @Override
    public boolean contactHasUntrustedPublishedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getPublishedDetailsVersion() != contactIdentityObject.getTrustedDetailsVersion();
        }
        return false;
    }


    @Override
    public JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            JsonIdentityDetailsWithVersionAndPhoto[] res;
            if (contactIdentityObject.getPublishedDetailsVersion() == contactIdentityObject.getTrustedDetailsVersion()) {
                res = new JsonIdentityDetailsWithVersionAndPhoto[1];
                res[0] = contactIdentityObject.getPublishedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
            } else {
                res = new JsonIdentityDetailsWithVersionAndPhoto[2];
                res[0] = contactIdentityObject.getPublishedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
                res[1] = contactIdentityObject.getTrustedDetails().getJsonIdentityDetailsWithVersionAndPhoto();
            }
            return res;
        }
        return null;
    }

    @Override
    public boolean isContactIdentityCertifiedByOwnKeycloak(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.isCertifiedByOwnKeycloak();
        }
        return false;
    }

    @Override
    public void unmarkAllCertifiedByOwnKeycloakContacts(Session session, Identity ownedIdentity) throws SQLException {
        ContactIdentity.unmarkAllCertifiedByOwnKeycloakContacts(wrapSession(session), ownedIdentity);
    }

    @Override
    public void reCheckAllCertifiedByOwnKeycloakContacts(Session session, Identity ownedIdentity) throws SQLException {
        ContactIdentity.unmarkAllCertifiedByOwnKeycloakContacts(wrapSession(session), ownedIdentity);

        for (ContactIdentity contactIdentity : ContactIdentity.getAll(wrapSession(session), ownedIdentity)) {
            ContactIdentityDetails publishedDetails = contactIdentity.getPublishedDetails();

            if (publishedDetails == null) {
                continue;
            }
            JsonIdentityDetails identityDetails = publishedDetails.getJsonIdentityDetails();

            if (identityDetails != null && identityDetails.getSignedUserDetails() != null) {
                JsonKeycloakUserDetails jsonKeycloakUserDetails = verifyKeycloakSignature(session, ownedIdentity, identityDetails.getSignedUserDetails());

                if (jsonKeycloakUserDetails == null) {
                    continue;
                }
                // the contact has some signed details
                try {
                    JsonIdentityDetails certifiedJsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(identityDetails.getSignedUserDetails());
                    contactIdentity.markContactAsCertifiedByOwnKeycloak(certifiedJsonIdentityDetails);
                } catch (Exception e) {
                    // error parsing signed details --> do nothing
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public TrustOrigin[] getTrustOriginsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) {
        ContactTrustOrigin[] contactTrustOrigins = ContactTrustOrigin.getAll(wrapSession(session), contactIdentity, ownedIdentity);
        TrustOrigin[] trustOrigins = new TrustOrigin[contactTrustOrigins.length];
        for (int i = 0; i < contactTrustOrigins.length; i++) {
            trustOrigins[i] = contactTrustOrigins[i].getTrustOrigin();
        }
        return trustOrigins;
    }

    @Override
    public void deleteContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, boolean failIfGroup) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject != null) {
            // check there are no Groups where this contact is
            if (failIfGroup) {
                byte[][] memberGroupUids = ContactGroupMembersJoin.getGroupOwnerAndUidsOfGroupsContainingContact(wrapSession(session), contactIdentity, ownedIdentity);
                if (memberGroupUids.length > 0) {
                    Logger.w("Attempted to delete a contact still member of some groups.");
                    throw new Exception();
                }
            }

            // delete the contact
            contactIdentityObject.delete();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public byte[][] getGroupOwnerAndUidsOfGroupsOwnedByContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        return ContactGroup.getGroupOwnerAndUidsOfGroupsOwnedByContact(wrapSession(session), ownedIdentity, contactIdentity);
    }

    @Override
    public boolean isIdentityAnActiveContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        return ContactIdentity.isActive(wrapSession(session), ownedIdentity, contactIdentity);
    }

    @Override
    public boolean isIdentityAContactIdentityOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        return (contactIdentityObject != null);
    }

    @Override
    public TrustLevel getContactIdentityTrustLevel(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject == null) {
            return null;
        }
        return contactIdentityObject.getTrustLevel();
    }

    @Override
    public EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject == null) {
            return null;
        }
        EnumSet<ObvContactActiveOrInactiveReason> reasons = EnumSet.noneOf(ObvContactActiveOrInactiveReason.class);
        if (contactIdentityObject.isRevokedAsCompromised()) {
            reasons.add(ObvContactActiveOrInactiveReason.REVOKED);
        }
        if (contactIdentityObject.isForcefullyTrustedByUser()) {
            reasons.add(ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED);
        }
        return reasons;
    }

    @Override
    public boolean forcefullyUnblockContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject == null) {
            return false;
        }
        contactIdentityObject.setForcefullyTrustedByUser(true);
        return true;
    }

    @Override
    public boolean reBlockForcefullyUnblockedContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contactIdentityObject == null || !contactIdentityObject.isForcefullyTrustedByUser()) {
            return false;
        }
        try {
            if (contactIdentityObject.isRevokedAsCompromised()) {
                channelDelegate.deleteObliviousChannelsWithContact(session, ownedIdentity, contactIdentity);
                removeAllDevicesForContactIdentity(session, ownedIdentity, contactIdentity);
            }
            contactIdentityObject.setForcefullyTrustedByUser(false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }



    @Override
    public void addDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid) throws SQLException {
        ContactIdentity contact = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        if (contact != null && contact.isActive()) {
            ContactDevice contactDevice = ContactDevice.get(wrapSession(session), deviceUid, contactIdentity, ownedIdentity);
            // only create the contact device if it does not already exist
            if (contactDevice == null) {
                contactDevice = ContactDevice.create(wrapSession(session), deviceUid, contactIdentity, ownedIdentity);
                if (contactDevice == null) {
                    throw new SQLException();
                }
            }
        }
    }

    @Override
    public void removeDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid) throws SQLException {
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), deviceUid, contactIdentity, ownedIdentity);
        if (contactDevice != null) {
            contactDevice.delete();
        }
    }

    @Override
    public void removeAllDevicesForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactDevice[] contactDevices = ContactDevice.getAll(wrapSession(session), contactIdentity, ownedIdentity);
        for (ContactDevice contactDevice: contactDevices) {
            contactDevice.delete();
        }
    }

    @Override
    public UID[] getDeviceUidsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) {
        try {
            ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
            if (contactIdentityObject != null) {
                return contactIdentityObject.getDeviceUids();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new UID[0];
    }

    @Override
    public Map<Identity, Map<Identity, Set<UID>>> getAllDeviceUidsOfAllContactsOfAllOwnedIdentities(Session session) throws SQLException {
        HashMap<Identity, Map<Identity, Set<UID>>> output = new HashMap<>();
        ContactDevice[] contactDevices = ContactDevice.getAll(wrapSession(session));
        for (ContactDevice contactDevice: contactDevices) {
            Map<Identity, Set<UID>> ownedIdentityMap = output.get(contactDevice.getOwnedIdentity());
            //noinspection Java8MapApi
            if (ownedIdentityMap == null) {
                ownedIdentityMap = new HashMap<>();
                output.put(contactDevice.getOwnedIdentity(), ownedIdentityMap);
            }
            Set<UID> contactDeviceUids = ownedIdentityMap.get(contactDevice.getContactIdentity());
            //noinspection Java8MapApi
            if (contactDeviceUids == null) {
                contactDeviceUids = new HashSet<>();
                ownedIdentityMap.put(contactDevice.getContactIdentity(), contactDeviceUids);
            }
            contactDeviceUids.add(contactDevice.getUid());
        }
        return output;
    }

    @Override
    public Seed getDeterministicSeedForOwnedIdentity(Identity ownedIdentity, byte[] diversificationTag) throws Exception {
        if (diversificationTag.length == 0) {
            throw new Exception();
        }
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(identityManagerSession, ownedIdentity);
            if (ownedIdentity == null) {
                throw new SQLException("OwnedIdentity not found");
            }
            PrivateIdentity privateIdentity = ownedIdentityObject.getPrivateIdentity();
            MAC mac = Suite.getMAC(privateIdentity.getMacKey());
            byte[] digest = mac.digest(privateIdentity.getMacKey(), new byte[]{0x55});
            byte[] hashInput = new byte[digest.length + diversificationTag.length];
            System.arraycopy(digest, 0, hashInput, 0, digest.length);
            System.arraycopy(diversificationTag, 0, hashInput, digest.length, diversificationTag.length);
            Hash sha256 = Suite.getHash(Hash.SHA256);
            byte[] hash = sha256.digest(hashInput);
            return new Seed(hash);
        }
    }


    private static final int PADDING_LENGTH = 16;

    @Override
    public byte[] signIdentities(Session session, byte[] prefix, Identity[] identities, Identity ownedIdentity, PRNGService prng) throws Exception {
        try {
            IdentityManagerSession identityManagerSession = wrapSession(session);
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(identityManagerSession, ownedIdentity);
            if (ownedIdentityObject == null) {
                throw new Exception("Unknown owned identity");
            }
            PrivateIdentity privateIdentity = ownedIdentityObject.getPrivateIdentity();
            SignaturePublicKey signaturePublicKey = ownedIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();
            SignaturePrivateKey signaturePrivateKey = privateIdentity.getServerAuthenticationPrivateKey().getSignaturePrivateKey();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(prefix);
            for (Identity identity: identities) {
                baos.write(identity.getBytes());
            }
            byte[] padding = prng.bytes(PADDING_LENGTH);
            baos.write(padding);
            byte[] challenge = baos.toByteArray();
            baos.close();
            Signature signature = Suite.getSignature(signaturePrivateKey);
            byte[] signatureBytes =  signature.sign(signaturePrivateKey, signaturePublicKey, challenge, prng);
            byte[] output = new byte[PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public boolean verifyIdentitiesSignature(byte[] prefix, Identity[] identities, Identity signerIdentity, byte[] signature) throws Exception {
        try {
            SignaturePublicKey signaturePublicKey = signerIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(prefix);
            for (Identity identity: identities) {
                baos.write(identity.getBytes());
            }
            baos.write(Arrays.copyOfRange(signature, 0, PADDING_LENGTH));
            byte[] challenge = baos.toByteArray();

            Signature signatureAlgo = Suite.getSignature(signaturePublicKey);
            return signatureAlgo.verify(signaturePublicKey, challenge, Arrays.copyOfRange(signature, PADDING_LENGTH, signature.length));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    // region groups

    @Override
    public void createContactGroup(Session session, Identity ownedIdentity, GroupInformation groupInformation, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers) throws Exception {
        // check that all members are indeed existing contacts
        for (Identity groupMember: groupMembers) {
            if (!isIdentityAContactIdentityOfOwnedIdentity(session, ownedIdentity, groupMember)) {
                Logger.e("Error in createContactGroup: a GroupMember is not a Contact.");
                throw new Exception();
            }
        }

        IdentityManagerSession identityManagerSession = wrapSession(session);
        ContactGroup.create(
                identityManagerSession,
                groupInformation.getGroupOwnerAndUid(),
                ownedIdentity,
                groupInformation.serializedGroupDetailsWithVersionAndPhoto,
                groupInformation.groupOwnerIdentity.equals(ownedIdentity) ? null : groupInformation.groupOwnerIdentity
        );
        for (Identity groupMember: groupMembers) {
            ContactGroupMembersJoin.create(identityManagerSession, groupInformation.getGroupOwnerAndUid(), ownedIdentity, groupMember);
        }
        for (IdentityWithSerializedDetails pendingGroupMember: pendingGroupMembers) {
            PendingGroupMember.create(identityManagerSession, groupInformation.getGroupOwnerAndUid(), ownedIdentity, pendingGroupMember.identity, pendingGroupMember.serializedDetails);
        }
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    // only for groups you do not own, when you get kicked or you leave
    @Override
    public void leaveGroup(Session session, byte[] groupUid, Identity ownedIdentity) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in leaveGroup: group not found");
            throw new Exception();
        }
        if (contactGroup.getGroupOwner() == null) {
            Logger.e("Error in leaveGroup: you are the group owner");
            throw new Exception();
        }

        contactGroup.delete();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    // only for groups you own, when disbanding
    @Override
    public void deleteGroup(Session session, byte[] groupUid, Identity ownedIdentity) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in deleteGroup: group not found");
            throw new Exception();
        }
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in deleteGroup: you are not the group owner");
            throw new Exception();
        }

        contactGroup.delete();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    // only for groups you own
    @Override
    public void addPendingMembersToGroup(Session session, byte[] groupUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception {
        // check that the group exists
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in addPendingMembersToGroup: ContactGroup not found.");
            throw new Exception();
        }
        // check that you are the owner of the group
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in addPendingMembersToGroup: you are not the owner of the group.");
            throw new Exception();
        }

        Group group = getGroup(session, ownedIdentity, groupUid);
        // check the contactIdentities are indeed ContactIdentity of the ownedIdentity
        for (Identity contactIdentity: contactIdentities) {
            if (!isIdentityAContactIdentityOfOwnedIdentity(session, ownedIdentity, contactIdentity)) {
                Logger.e("Error in addPendingMembersToGroup: contactIdentity is not a Contact.");
                throw new Exception();
            }
            if (group.isMember(contactIdentity) || group.isPendingMember(contactIdentity)) {
                Logger.e("Error in addPendingMembersToGroup: contactIdentity is already in group.");
                throw new Exception();
            }
        }
        if (!session.isInTransaction()) {
            Logger.e("Called addPendingMembersToGroup outside a transaction");
            throw new Exception();
        }
        // create the pending group members
        for (Identity contactIdentity: contactIdentities) {
            String contactSerializedDetails = getSerializedPublishedDetailsOfContactIdentity(session, ownedIdentity, contactIdentity);
            PendingGroupMember.create(wrapSession(session), groupUid, ownedIdentity, contactIdentity, contactSerializedDetails);
        }

        // increment the group members version;
        contactGroup.incrementGroupMembersVersion();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
        if (groupMembersChangedCallback != null) {
            groupMembersChangedCallback.callback();
        }
    }

    // only for groups you own
    @Override
    public void removeMembersAndPendingFromGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception {
        // check that the group exists
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in removeMembersAndPendingFromGroup: ContactGroup not found.");
            throw new Exception();
        }
        // check that you are the owner of the group
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in removeMembersAndPendingFromGroup: you are not the owner of the group.");
            throw new Exception();
        }

        Group group = getGroup(session, ownedIdentity, groupOwnerAndUid);
        // check the contactIdentities are indeed group members or pending
        for (Identity contactIdentity: contactIdentities) {
            if (!group.isMember(contactIdentity) && !group.isPendingMember(contactIdentity)) {
                Logger.e("Error in removeMembersAndPendingFromGroup: contactIdentity is not member or pending.");
                throw new Exception();
            }
        }

        if (!session.isInTransaction()) {
            Logger.e("Called removeMembersAndPendingFromGroup outside a transaction");
            throw new Exception();
        }
        // remove the group members
        for (Identity contactIdentity: contactIdentities) {
            PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
            if (pendingGroupMember != null) {
                pendingGroupMember.delete();
            }
            ContactGroupMembersJoin contactGroupMembersJoin = ContactGroupMembersJoin.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
            if (contactGroupMembersJoin != null) {
                contactGroupMembersJoin.delete();
            }
        }

        // increment the group members version;
        contactGroup.incrementGroupMembersVersion();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
        if (groupMembersChangedCallback != null) {
            groupMembersChangedCallback.callback();
        }
    }


    // only for groups you own
    @Override
    public void addGroupMemberFromPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception {
        // check that the group exists
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in addGroupMemberFromPendingMember: ContactGroup not found.");
            throw new Exception();
        }
        // check that you are the owner of the group
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in addGroupMemberFromPendingMember: you are not the owner of the group.");
            throw new Exception();
        }
        // check the contactIdentity is indeed a ContactIdentity of the ownedIdentity
        if (!isIdentityAContactIdentityOfOwnedIdentity(session, ownedIdentity, contactIdentity)) {
            Logger.e("Error in addGroupMemberFromPendingMember: contactIdentity is not a Contact.");
            throw new Exception();
        }
        if (!session.isInTransaction()) {
            Logger.e("Called addGroupMemberFromPendingMember outside a transaction");
            throw new Exception();
        }
        // remove the pending group member (if present)
        PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
        if (pendingGroupMember != null) {
            pendingGroupMember.delete();
        }

        ContactGroupMembersJoin.create(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);

        // increment the group members version;
        contactGroup.incrementGroupMembersVersion();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
        if (groupMembersChangedCallback != null) {
            groupMembersChangedCallback.callback();
        }
    }

    // only for groups you own
    @Override
    public void demoteGroupMemberToDeclinedPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception {
        // check that the group exists
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            Logger.e("Error in demoteGroupMemberToDeclinedPendingMember: ContactGroup not found.");
            throw new Exception();
        }
        // check that you are the owner of the group
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in demoteGroupMemberToDeclinedPendingMember: you are not the owner of the group.");
            throw new Exception();
        }

        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), contactIdentity, ownedIdentity);
        // check the contactIdentity is indeed a ContactIdentity of the ownedIdentity
        if (contactIdentityObject == null) {
            Logger.e("Error in demoteGroupMemberToDeclinedPendingMember: contactIdentity is not a Contact.");
            throw new Exception();
        }
        if (!session.isInTransaction()) {
            Logger.e("Called demoteGroupMemberToDeclinedPendingMember outside a transaction");
            throw new Exception();
        }
        // remove the group member (if present)
        ContactGroupMembersJoin contactGroupMembersJoin = ContactGroupMembersJoin.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
        if (contactGroupMembersJoin != null) {
            contactGroupMembersJoin.delete();
        }

        PendingGroupMember pendingGroupMember = PendingGroupMember.create(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity, contactIdentityObject.getPublishedDetails().getSerializedJsonDetails());
        pendingGroupMember.setDeclined(true);

        // increment the group members version;
        contactGroup.incrementGroupMembersVersion();
        session.addSessionCommitListener(backupNeededSessionCommitListener);
        if (groupMembersChangedCallback != null) {
            groupMembersChangedCallback.callback();
        }
    }

    @Override
    public void setPendingMemberDeclined(Session session, byte[] groupUid, Identity ownedIdentity, Identity contactIdentity, boolean declined) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupUid, ownedIdentity);
        // check that the group exists
        if (contactGroup == null) {
            Logger.e("Error in setPendingMemberDeclined: ContactGroup not found.");
            throw new Exception();
        }

        // check that you are group owner
        if (contactGroup.getGroupOwner() != null) {
            Logger.e("Error in setPendingMemberDeclined: you are not the groupOwner.");
            throw new Exception();
        }

        // get the pending group member and mark him as "declined"
        PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupUid, ownedIdentity, contactIdentity);
        if (pendingGroupMember != null) {
            pendingGroupMember.setDeclined(declined);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }


    @Override
    public void updateGroupMembersAndDetails(Session session, Identity ownedIdentity, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMembers, HashSet<IdentityWithSerializedDetails> pendingMembers, long membersVersion) throws Exception {
        if (!session.isInTransaction()) {
            Logger.e("Calling updateGroupMembersAndDetails from outside a transaction");
            throw new Exception();
        }

        // this method should only be called for groups you do not own
        if (ownedIdentity.equals(groupInformation.groupOwnerIdentity)) {
            Logger.w("Error: in updateGroupMembersAndDetails, group is owned");
            throw new Exception();
        }

        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity);
        if (contactGroup == null) {
            Logger.w("Error: in updateGroupMembersAndDetails, group not found");
            throw new Exception();
        }

        // first, update the details (if needed)
        JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = jsonObjectMapper.readValue(groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
        if (contactGroup.updatePublishedDetails(jsonGroupDetailsWithVersionAndPhoto, false)) {
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }


        // second, update members version number
        if (contactGroup.getGroupMembersVersion() < membersVersion) {
            session.addSessionCommitListener(backupNeededSessionCommitListener);
            contactGroup.setGroupMembersVersion(membersVersion);

            // group members diff
            Group group = getGroup(session, ownedIdentity, groupInformation.getGroupOwnerAndUid());
            if (group == null) {
                Logger.e("A ContactGroup exists but getGroup returned null");
                throw new Exception();
            }
            HashSet<Identity> oldMembers = new HashSet<>(Arrays.asList(group.getGroupMembers()));
            HashSet<IdentityWithSerializedDetails> oldPendings = new HashSet<>(Arrays.asList(group.getPendingGroupMembers()));

            for (IdentityWithSerializedDetails groupMember : groupMembers) {
                if (groupMember.identity.equals(ownedIdentity)) {
                    continue;
                }

                if (oldMembers.contains(groupMember.identity)) {
                    oldMembers.remove(groupMember.identity);
                } else {
                    // we need to add a new member. If he is pending, remove him from pending

                    // remove the pending group member (if present)
                    PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity, groupMember.identity);
                    if (pendingGroupMember != null) {
                        pendingGroupMember.delete();
                        oldPendings.remove(groupMember);
                    }

                    // create contact if it does not exist
                    ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), groupMember.identity, ownedIdentity);
                    if (contactIdentityObject == null) {
                        addContactIdentity(session, groupMember.identity, groupMember.serializedDetails, ownedIdentity, TrustOrigin.createGroupTrustOrigin(System.currentTimeMillis(), groupInformation.groupOwnerIdentity));
                    }

                    // add contact to group
                    ContactGroupMembersJoin.create(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity, groupMember.identity);
                }
            }
            // now remove remaining old group members
            for (Identity oldMember : oldMembers) {
                ContactGroupMembersJoin contactGroupMembersJoin = ContactGroupMembersJoin.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity, oldMember);
                if (contactGroupMembersJoin != null) {
                    contactGroupMembersJoin.delete();
                }
            }

            // pending members diff
            for (IdentityWithSerializedDetails pendingMember : pendingMembers) {
                if (oldPendings.contains(pendingMember)) {
                    oldPendings.remove(pendingMember);
                } else {
                    // create a new pending Member
                    PendingGroupMember.create(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity, pendingMember.identity, pendingMember.serializedDetails);
                }
            }
            // now remove remaining old pending members
            for (IdentityWithSerializedDetails oldPending : oldPendings) {
                PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity, oldPending.identity);
                if (pendingGroupMember != null) {
                    pendingGroupMember.delete();
                }
            }
        }
    }

    @Override
    public void resetGroupMembersAndPublishedDetailsVersions(Session session, Identity ownedIdentity, GroupInformation groupInformation) throws Exception {
        if (!session.isInTransaction()) {
            Logger.e("Calling resetGroupMembersAndPublishedDetailsVersions from outside a transaction");
            throw new Exception();
        }

        // this method should only be called for groups you do not own
        if (ownedIdentity.equals(groupInformation.groupOwnerIdentity)) {
            Logger.w("Error: in resetGroupMembersAndPublishedDetailsVersions, group is owned");
            throw new Exception();
        }

        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity);
        if (contactGroup == null) {
            Logger.w("Error: in resetGroupMembersAndPublishedDetailsVersions, group not found");
            throw new Exception();
        }

        // first, rollback group details (if needed)
        JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = jsonObjectMapper.readValue(groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
        contactGroup.updatePublishedDetails(jsonGroupDetailsWithVersionAndPhoto, true);

        // then, set groupMembersVersion to 0 to make sure the next update is taken into account
        contactGroup.setGroupMembersVersion(0);
        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }


    @Override
    public GroupWithDetails[] getGroupsForOwnedIdentity(Session session, Identity ownedIdentity) throws Exception {
        ContactGroup[] contactGroups = ContactGroup.getAllForIdentity(wrapSession(session), ownedIdentity);
        GroupWithDetails[] groups = new GroupWithDetails[contactGroups.length];
        for (int i=0; i<contactGroups.length; i++) {
            groups[i] = new GroupWithDetails(
                    contactGroups[i].getGroupOwnerAndUid(),
                    ownedIdentity,
                    ContactGroupMembersJoin.getContactIdentitiesInGroup(wrapSession(session), contactGroups[i].getGroupOwnerAndUid(), ownedIdentity),
                    PendingGroupMember.getPendingMembersInGroup(wrapSession(session), contactGroups[i].getGroupOwnerAndUid(), ownedIdentity),
                    PendingGroupMember.getDeclinedPendingMembersInGroup(wrapSession(session), contactGroups[i].getGroupOwnerAndUid(), ownedIdentity),
                    contactGroups[i].getGroupOwner(),
                    contactGroups[i].getGroupMembersVersion(),
                    contactGroups[i].getPublishedDetails().getJsonGroupDetails(),
                    contactGroups[i].getLatestOrTrustedDetails().getJsonGroupDetails(),
                    contactGroups[i].getLatestOrTrustedDetails().getVersion() != contactGroups[i].getPublishedDetails().getVersion()
            );
        }
        return groups;
    }

    @Override
    public Group getGroup(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception {
        if (ownedIdentity == null || groupOwnerAndUid == null) {
            return null;
        }
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            return null;
        }
        return new Group(
                contactGroup.getGroupOwnerAndUid(),
                ownedIdentity,
                ContactGroupMembersJoin.getContactIdentitiesInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                PendingGroupMember.getPendingMembersInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                PendingGroupMember.getDeclinedPendingMembersInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                contactGroup.getGroupOwner(),
                contactGroup.getGroupMembersVersion()
        );
    }

    @Override
    public GroupWithDetails getGroupWithDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception {
        if (ownedIdentity == null || groupOwnerAndUid == null) {
            return null;
        }
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            return null;
        }
        return new GroupWithDetails(
                contactGroup.getGroupOwnerAndUid(),
                ownedIdentity,
                ContactGroupMembersJoin.getContactIdentitiesInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                PendingGroupMember.getPendingMembersInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                PendingGroupMember.getDeclinedPendingMembersInGroup(wrapSession(session), contactGroup.getGroupOwnerAndUid(), ownedIdentity),
                contactGroup.getGroupOwner(),
                contactGroup.getGroupMembersVersion(),
                contactGroup.getPublishedDetails().getJsonGroupDetails(),
                contactGroup.getLatestOrTrustedDetails().getJsonGroupDetails(),
                contactGroup.getPublishedDetails().getVersion() != contactGroup.getLatestOrTrustedDetails().getVersion());
    }

    @Override
    public GroupInformation getGroupInformation(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception {
        if (ownedIdentity == null || groupOwnerAndUid == null) {
            return null;
        }
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup == null) {
            return null;
        }
        return contactGroup.getGroupInformation();
    }

    @Override
    public JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(Session session, Identity ownedIdentity, byte[] groupUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupUid, ownedIdentity);
        if (contactGroup != null) {
            JsonGroupDetailsWithVersionAndPhoto[] res;
            if (contactGroup.getPublishedDetailsVersion() == contactGroup.getLatestOrTrustedDetailsVersion()) {
                res = new JsonGroupDetailsWithVersionAndPhoto[1];
                res[0] = contactGroup.getPublishedDetails().getJsonGroupDetailsWithVersionAndPhoto();
            } else {
                res = new JsonGroupDetailsWithVersionAndPhoto[2];
                res[0] = contactGroup.getPublishedDetails().getJsonGroupDetailsWithVersionAndPhoto();
                res[1] = contactGroup.getLatestOrTrustedDetails().getJsonGroupDetailsWithVersionAndPhoto();
            }
            return res;
        }
        return null;
    }

    @Override
    public String getGroupPhotoUrl(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            if (contactGroup.getGroupOwner() == null) {
                return contactGroup.getPublishedDetails().getPhotoUrl();
            } else {
                return contactGroup.getLatestOrTrustedDetails().getPhotoUrl();
            }
        }
        return null;
    }

    @Override
    public void trustPublishedGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            contactGroup.trustPublishedDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void updateLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            contactGroup.setLatestDetails(jsonGroupDetails);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void setOwnedGroupDetailsServerLabelAndKey(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            contactGroup.setPhotoLabelAndKey(version, photoServerLabel, photoServerKey);
            if (ServerUserData.createForOwnedGroupDetails(wrapSession(session), ownedIdentity, photoServerLabel, groupOwnerAndUid) == null) {
                throw new SQLException();
            }
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void updateOwnedGroupPhoto(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, String absolutePhotoUrl, boolean partOfGroupCreation) throws Exception {
        ContactGroup group = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (group != null) {
            group.setOwnedGroupPhoto(absolutePhotoUrl, partOfGroupCreation);
        }
    }

    @Override
    public void setContactGroupDownloadedPhoto(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, byte[] photo) throws Exception {
        ContactGroup group = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (group != null) {
            group.setDetailsDownloadedPhotoUrl(version, photo);
        }
    }

    @Override
    public int publishLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            session.addSessionCommitListener(backupNeededSessionCommitListener);
            return contactGroup.publishLatestDetails();
        }
        return -1;
    }

    @Override
    public void discardLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            contactGroup.discardLatestDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public byte[][] getGroupOwnerAndUidOfGroupsWhereContactIsPending(Session session, Identity contactIdentity, Identity ownedIdentity) {
        return PendingGroupMember.getGroupOwnerAndUidOfGroupsWhereContactIsPending(wrapSession(session), contactIdentity, ownedIdentity, false);
    }

    @Override
    public void refreshMembersOfGroupsOwnedByGroupOwner(UID currentDeviceUid, Identity groupOwner) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedDevice ownedDevice = OwnedDevice.get(identityManagerSession, currentDeviceUid);
            if (ownedDevice == null || !ownedDevice.isCurrentDevice()) {
                return;
            }
            Identity ownedIdentity = ownedDevice.getIdentity();
            byte[][] groupOwnerAndUids = ContactGroup.getGroupOwnerAndUidsOfGroupsOwnedByContact(identityManagerSession, ownedIdentity, groupOwner);
            for (byte[] groupOwnerAndUid: groupOwnerAndUids) {
                try {
                    protocolStarterDelegate.queryGroupMembers(groupOwnerAndUid, ownedIdentity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void pushMembersOfOwnedGroupsToContact(UID currentDeviceUid, Identity contactIdentity) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedDevice ownedDevice = OwnedDevice.get(identityManagerSession, currentDeviceUid);
            if (ownedDevice == null || !ownedDevice.isCurrentDevice()) {
                return;
            }
            Identity ownedIdentity = ownedDevice.getIdentity();
            {
                byte[][] groupOwnerAndUids = ContactGroup.getGroupOwnerAndUidsOfOwnedGroupsWithContact(identityManagerSession, ownedIdentity, contactIdentity);
                for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                    try {
                        protocolStarterDelegate.reinviteAndPushMembersToContact(groupOwnerAndUid, ownedIdentity, contactIdentity);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            {
                byte[][] groupOwnerAndUids = PendingGroupMember.getGroupOwnerAndUidOfGroupsWhereContactIsPending(identityManagerSession, contactIdentity, ownedIdentity, true);
                for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                    try {
                        protocolStarterDelegate.reinvitePendingToGroup(groupOwnerAndUid, ownedIdentity, contactIdentity);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // endregion

    // region backup

    @Override
    public void initiateBackup(final BackupDelegate backupDelegate, final String tag, final UID backupKeyUid, final int version) {
        new Thread(() -> {
            try (IdentityManagerSession identityManagerSession = getSession()) {
                OwnedIdentity.Pojo_0[] ownedIdentityPojos = OwnedIdentity.backupAll(identityManagerSession);
                if (ownedIdentityPojos.length == 0) {
                    // no active identity --> abort backup
                    backupDelegate.backupFailed(tag, backupKeyUid, version);
                    return;
                }
                String jsonString = jsonObjectMapper.writeValueAsString(ownedIdentityPojos);
                backupDelegate.backupSuccess(tag, backupKeyUid, version, jsonString);
            } catch (SQLException | JsonProcessingException e) {
                e.printStackTrace();
                backupDelegate.backupFailed(tag, backupKeyUid, version);
            }
        }, "Identity Backup").start();
    }

    @Override
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String serializedJsonPojo, PRNGService prng) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            ////////////////
            // If an ownedIdentity already exists, we abort
            ////////////////
            OwnedIdentity[] ownedIdentities = OwnedIdentity.getAll(identityManagerSession);
            if (ownedIdentities.length != 0) {
                Logger.e("Trying to restore a backup while an OwnedIdentity already exists. Aborting.");
                return new ObvIdentity[0];
            }
            /////////////////

            List<ObvIdentity> restoredIdentities = new ArrayList<>();
            OwnedIdentity.Pojo_0[] ownedIdentityPojos = jsonObjectMapper.readValue(serializedJsonPojo, new TypeReference<>() {});

            identityManagerSession.session.startTransaction();
            for (OwnedIdentity.Pojo_0 ownedIdentityPojo : ownedIdentityPojos) {
                restoredIdentities.add(OwnedIdentity.restore(identityManagerSession, ownedIdentityPojo, prng));
            }
            identityManagerSession.session.commit();

            return restoredIdentities.toArray(new ObvIdentity[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void restoreContactsAndGroupsFromBackup(String serializedJsonPojo, Identity[] restoredIdentities, long backupTimestamp) {
        Set<Identity> restoredOwnedIdentities = new HashSet<>(Arrays.asList(restoredIdentities));

        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity.Pojo_0[] ownedIdentityPojos = jsonObjectMapper.readValue(serializedJsonPojo, new TypeReference<>() {});

            for (OwnedIdentity.Pojo_0 ownedIdentityPojo : ownedIdentityPojos) {
                Identity ownedIdentity = Identity.of(ownedIdentityPojo.owned_identity);
                if (!restoredOwnedIdentities.contains(ownedIdentity)) {
                    continue;
                }

                ContactIdentity.restoreAll(identityManagerSession, ownedIdentity, ownedIdentityPojo.contact_identities, backupTimestamp);
                ContactGroup.restoreAllForOwner(identityManagerSession, ownedIdentity, ownedIdentity, ownedIdentityPojo.owned_groups, backupTimestamp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // endregion

    // region userData

    @Override
    public UserData[] getAllUserData(Session session) throws Exception {
        ServerUserData[] serverUserData = ServerUserData.getAll(wrapSession(session));
        UserData[] userData = new UserData[serverUserData.length];
        for (int i=0; i<serverUserData.length; i++) {
            userData[i] = new UserData(serverUserData[i].getOwnedIdentity(), serverUserData[i].getLabel(), serverUserData[i].getNextRefreshTimestamp(), serverUserData[i].getGroupDetailsOwnerAndUid());
        }
        return userData;
    }

    @Override
    public UserData getUserData(Session session, Identity ownedIdentity, UID label) throws Exception {
        ServerUserData serverUserData = ServerUserData.get(wrapSession(session), ownedIdentity, label);
        if (serverUserData != null) {
            return new UserData(serverUserData.getOwnedIdentity(), serverUserData.getLabel(), serverUserData.getNextRefreshTimestamp(), serverUserData.getGroupDetailsOwnerAndUid());
        }
        return null;
    }

    @Override
    public void deleteUserData(Session session, Identity ownedIdentity, UID label) throws Exception {
        ServerUserData serverUserData = ServerUserData.get(wrapSession(session), ownedIdentity, label);
        if (serverUserData != null) {
            serverUserData.delete();
        }
    }

    @Override
    public void updateUserDataNextRefreshTimestamp(Session session, Identity ownedIdentity, UID label) throws Exception {
        ServerUserData serverUserData = ServerUserData.get(wrapSession(session), ownedIdentity, label);
        if (serverUserData != null) {
            serverUserData.updateNextRefreshTimestamp();
        }
    }

    // endregion
    // endregion





    // region Implement EncryptionForIdentityDelegate

    @Override
    public EncryptedBytes wrap(AuthEncKey messageKey, Identity toIdentity, PRNGService prng) {
        try {
            PublicKeyEncryption pubEnc = Suite.getPublicKeyEncryption(toIdentity.getEncryptionPublicKey());
            return pubEnc.encrypt(toIdentity.getEncryptionPublicKey(), Encoded.of(messageKey).getBytes(), prng);
        } catch (InvalidKeyException e) {
            return null;
        }
    }

    @Override
    public AuthEncKey unwrap(Session session, EncryptedBytes wrappedKey, Identity toIdentity) throws SQLException {
        try {
            OwnedIdentity ownedIdentity = OwnedIdentity.get(wrapSession(session), toIdentity);
            if (ownedIdentity == null) {
                return null;
            }
            PrivateIdentity privateIdentity = ownedIdentity.getPrivateIdentity();
            PublicKeyEncryption pubEnc = Suite.getPublicKeyEncryption(privateIdentity.getEncryptionPublicKey());
            byte[] unwrappedBytes = pubEnc.decrypt(privateIdentity.getEncryptionPrivateKey(), wrappedKey);
            return (AuthEncKey) new Encoded(unwrappedBytes).decodeSymmetricKey();
        } catch (DecryptionException | InvalidKeyException | DecodingException  e) {
            return null;
        }
    }

    // endregion
}
