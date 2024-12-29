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

package io.olvid.engine.identity;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

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
import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.PreKeyBlobOnServer;
import io.olvid.engine.datatypes.containers.AuthEncKeyAndChannelInfo;
import io.olvid.engine.datatypes.containers.EncodedOwnedPreKey;
import io.olvid.engine.datatypes.containers.OwnedDeviceAndPreKey;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.PrivateIdentity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.SessionCommitListener;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.KeycloakGroupV2UpdateOutput;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.containers.UidAndPreKey;
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
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvContactDeviceCount;
import io.olvid.engine.engine.types.ObvContactInfo;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactDevice;
import io.olvid.engine.identity.databases.ContactGroup;
import io.olvid.engine.identity.databases.ContactGroupDetails;
import io.olvid.engine.identity.databases.ContactGroupMembersJoin;
import io.olvid.engine.identity.databases.ContactGroupV2;
import io.olvid.engine.identity.databases.ContactGroupV2Details;
import io.olvid.engine.identity.databases.ContactGroupV2Member;
import io.olvid.engine.identity.databases.ContactGroupV2PendingMember;
import io.olvid.engine.identity.databases.ContactIdentity;
import io.olvid.engine.identity.databases.ContactIdentityDetails;
import io.olvid.engine.identity.databases.ContactTrustOrigin;
import io.olvid.engine.identity.databases.KeycloakRevokedIdentity;
import io.olvid.engine.identity.databases.KeycloakServer;
import io.olvid.engine.identity.databases.OwnedDevice;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.databases.OwnedPreKey;
import io.olvid.engine.identity.databases.PendingGroupMember;
import io.olvid.engine.identity.databases.ServerUserData;
import io.olvid.engine.identity.databases.sync.IdentityManagerSyncSnapshot;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.identity.datatypes.IdentityManagerSessionFactory;
import io.olvid.engine.identity.datatypes.KeycloakGroupBlob;
import io.olvid.engine.identity.datatypes.KeycloakGroupDeletionData;
import io.olvid.engine.identity.datatypes.KeycloakGroupMemberAndPermissions;
import io.olvid.engine.identity.datatypes.KeycloakGroupMemberKickedData;
import io.olvid.engine.metamanager.BackupDelegate;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.metamanager.PreKeyEncryptionDelegate;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class IdentityManager implements IdentityDelegate, SolveChallengeDelegate, EncryptionForIdentityDelegate, PreKeyEncryptionDelegate, ObvBackupAndSyncDelegate, IdentityManagerSessionFactory, ObvManager {
    private final String engineBaseDirectory;
    private final ObjectMapper jsonObjectMapper;
    private final PRNGService prng;
    private final SessionCommitListener backupNeededSessionCommitListener;

    private CreateSessionDelegate createSessionDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private ProtocolStarterDelegate protocolStarterDelegate;
    private ChannelDelegate channelDelegate;
    private final Timer deviceDiscoveryTimer;

    private final HashMap<Identity, UID> currentDeviceUidCache = new HashMap<>();

    public IdentityManager(MetaManager metaManager, String engineBaseDirectory, ObjectMapper jsonObjectMapper, PRNGService prng) {
        this.engineBaseDirectory = engineBaseDirectory;
        this.jsonObjectMapper = jsonObjectMapper;
        this.prng = prng;
        this.backupNeededSessionCommitListener = () -> {
            if (notificationPostingDelegate != null) {
                notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED, new HashMap<>());
            }
        };
        this.deviceDiscoveryTimer = new Timer("Engine-DeviceDiscoveryTimer");

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
        } catch (Exception e) {
            Logger.x(e);
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
            Logger.x(e);
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
            Logger.x(e);
        }

        // clean old ownedIdentityDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (OwnedIdentity ownedIdentity : OwnedIdentity.getAll(identityManagerSession)) {
                OwnedIdentityDetails.cleanup(identityManagerSession, ownedIdentity.getOwnedIdentity(), ownedIdentity.getPublishedDetailsVersion(), ownedIdentity.getLatestDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }
        // clean old contactIdentityDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactIdentity contactIdentity : ContactIdentity.getAllForAllOwnedIdentities(identityManagerSession)) {
                ContactIdentityDetails.cleanup(identityManagerSession, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity(), contactIdentity.getPublishedDetailsVersion(), contactIdentity.getTrustedDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }
        // clean old contactGroupDetails
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactGroup contactGroup : ContactGroup.getAll(identityManagerSession)) {
                ContactGroupDetails.cleanup(identityManagerSession, contactGroup.getOwnedIdentity(), contactGroup.getGroupOwnerAndUid(), contactGroup.getPublishedDetailsVersion(), contactGroup.getLatestOrTrustedDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }
        // clean old ContactGroupV2Details
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactGroupV2 contactGroupV2 : ContactGroupV2.getAll(identityManagerSession)) {
                ContactGroupV2Details.cleanup(identityManagerSession, contactGroupV2.getOwnedIdentity(), contactGroupV2.getGroupIdentifier(), contactGroupV2.getVersion(), contactGroupV2.getTrustedDetailsVersion());
            }
            identityManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
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
                for (String photoUrl : ContactGroupV2Details.getAllPhotoUrl(identityManagerSession)) {
                    photoUrlsToKeep.add(new File(photoUrl).getName());
                }

                for (String listedPhotoUrl : listedPhotoUrls) {
                    if (!photoUrlsToKeep.contains(listedPhotoUrl)) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            new File(photoDir, listedPhotoUrl).delete();
                        } catch (Exception e) {
                            Logger.x(e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        }

        // check if device capabilities changed for any current owned device
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (OwnedIdentity ownedIdentity : OwnedIdentity.getAll(identityManagerSession)) {
                OwnedDevice ownedDevice = OwnedDevice.getCurrentDeviceOfOwnedIdentity(identityManagerSession, ownedIdentity.getOwnedIdentity());

                HashSet<ObvCapability> currentCapabilities = new HashSet<>(ObvCapability.currentCapabilities);
                List<ObvCapability> publishedCapabilitiesList = ownedDevice.getDeviceCapabilities();
                HashSet<ObvCapability> publishedCapabilities = publishedCapabilitiesList == null ? null : new HashSet<>(publishedCapabilitiesList);

                if (!currentCapabilities.equals(publishedCapabilities)) {
                    protocolStarterDelegate.updateCurrentDeviceCapabilitiesForOwnedIdentity(identityManagerSession.session, ownedIdentity.getOwnedIdentity(), ObvCapability.currentCapabilities);
                }
            }
            // commit the session, in case a protocol was indeed started
            identityManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }

        // re-notify the app for all Keycloak groups shared settings to make sure it remains synchronized
        try (IdentityManagerSession identityManagerSession = getSession()) {
            for (ContactGroupV2 contactGroupV2 : ContactGroupV2.getAllKeycloak(identityManagerSession)) {
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY, contactGroupV2.getOwnedIdentity());
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_GROUP_IDENTIFIER_KEY, contactGroupV2.getGroupIdentifier());
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SERIALIZED_SHARED_SETTINGS_KEY, contactGroupV2.getSerializedSharedSettings());
                userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY, contactGroupV2.getLastModificationTimestamp());
                notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS, userInfo);
            }
        } catch (Exception e) {
            Logger.x(e);
        }

        deviceDiscoveryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // do an OwnedDeviceDiscoveryProtocol at every startup
                try (IdentityManagerSession identityManagerSession = getSession()) {
                    for (OwnedIdentity ownedIdentity : OwnedIdentity.getAll(identityManagerSession)) {
                        if (ownedIdentity.isActive()) {
                            protocolStarterDelegate.startOwnedDeviceDiscoveryProtocolWithinTransaction(identityManagerSession.session, ownedIdentity.getOwnedIdentity());
                        }
                    }
                    identityManagerSession.session.commit();
                } catch (Exception e) {
                    Logger.x(e);
                }

                // search for all contact identities with no deviceUids and run a deviceDiscovery
                try (IdentityManagerSession identityManagerSession = getSession()) {
                    ContactIdentity[] contactIdentities = ContactIdentity.getAllActiveWithoutDevices(identityManagerSession, System.currentTimeMillis() - Constants.NO_DEVICE_CONTACT_DEVICE_DISCOVERY_INTERVAL);
                    if (contactIdentities.length > 0) {
                        Logger.i("Found " + contactIdentities.length + " contacts with no device. Starting corresponding deviceDiscoveryProtocols.");
                        for (ContactIdentity contactIdentity : contactIdentities) {
                            protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(identityManagerSession.session, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity());
                            contactIdentity.setLastContactDeviceDiscoveryTimestamp(System.currentTimeMillis());
                        }
                        identityManagerSession.session.commit();
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }

                // search all active contact identities with devices without a recent device discovery
                try (IdentityManagerSession identityManagerSession = getSession()) {
                    ContactIdentity[] contactIdentities = ContactIdentity.getAllActiveWithDevicesAndOldDiscovery(identityManagerSession, System.currentTimeMillis() - Constants.CONTACT_DEVICE_DISCOVERY_INTERVAL);
                    if (contactIdentities.length > 0) {
                        Logger.i("Found " + contactIdentities.length + " contacts with outdated device discovery. Starting corresponding deviceDiscoveryProtocols.");
                        for (ContactIdentity contactIdentity : contactIdentities) {
                            protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(identityManagerSession.session, contactIdentity.getOwnedIdentity(), contactIdentity.getContactIdentity());
                            contactIdentity.setLastContactDeviceDiscoveryTimestamp(System.currentTimeMillis());
                        }
                        identityManagerSession.session.commit();
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
            }
        }, 0, Constants.OWNED_DEVICE_DISCOVERY_INTERVAL);
    }

    @SuppressWarnings("unused")
    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentityDetails.createTable(identityManagerSession.session);
            KeycloakServer.createTable(identityManagerSession.session);
            KeycloakRevokedIdentity.createTable(identityManagerSession.session);
            OwnedIdentity.createTable(identityManagerSession.session);
            OwnedDevice.createTable(identityManagerSession.session);
            OwnedPreKey.createTable(identityManagerSession.session);
            ContactIdentityDetails.createTable(identityManagerSession.session);
            ContactIdentity.createTable(identityManagerSession.session);
            ContactTrustOrigin.createTable(identityManagerSession.session);
            ContactDevice.createTable(identityManagerSession.session);
            ContactGroupDetails.createTable(identityManagerSession.session);
            ContactGroup.createTable(identityManagerSession.session);
            ContactGroupMembersJoin.createTable(identityManagerSession.session);
            PendingGroupMember.createTable(identityManagerSession.session);
            ServerUserData.createTable(identityManagerSession.session);
            ContactGroupV2Details.createTable(identityManagerSession.session);
            ContactGroupV2.createTable(identityManagerSession.session);
            ContactGroupV2Member.createTable(identityManagerSession.session);
            ContactGroupV2PendingMember.createTable(identityManagerSession.session);
            identityManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
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
        OwnedPreKey.upgradeTable(session, oldVersion, newVersion);
        ContactIdentityDetails.upgradeTable(session, oldVersion, newVersion);
        ContactIdentity.upgradeTable(session, oldVersion, newVersion);
        ContactTrustOrigin.upgradeTable(session, oldVersion, newVersion);
        ContactDevice.upgradeTable(session, oldVersion, newVersion);
        ContactGroupDetails.upgradeTable(session, oldVersion, newVersion);
        ContactGroup.upgradeTable(session, oldVersion, newVersion);
        ContactGroupMembersJoin.upgradeTable(session, oldVersion, newVersion);
        PendingGroupMember.upgradeTable(session, oldVersion, newVersion);
        ServerUserData.upgradeTable(session, oldVersion, newVersion);
        ContactGroupV2Details.upgradeTable(session, oldVersion, newVersion);
        ContactGroupV2.upgradeTable(session, oldVersion, newVersion);
        ContactGroupV2Member.upgradeTable(session, oldVersion, newVersion);
        ContactGroupV2PendingMember.upgradeTable(session, oldVersion, newVersion);
    }

    @SuppressWarnings("unused")
    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    @SuppressWarnings("unused")
    public void setDelegate(ProtocolStarterDelegate protocolStarterDelegate) {
        this.protocolStarterDelegate = protocolStarterDelegate;
    }

    @SuppressWarnings("unused")
    public void setDelegate(ChannelDelegate channelDelegate) {
        this.channelDelegate = channelDelegate;
    }

    public IdentityManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in IdentityManager.");
        }
        return new IdentityManagerSession(createSessionDelegate.getSession(), notificationPostingDelegate, this, engineBaseDirectory, jsonObjectMapper, prng);
    }

    private IdentityManagerSession wrapSession(Session session) {
        return new IdentityManagerSession(session, notificationPostingDelegate, this, engineBaseDirectory, jsonObjectMapper, prng);
    }

    public ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

   @Override
   public void downloadAllUserData(Session session) throws Exception {
        List<OwnedIdentityDetails> ownedIdentityDetailsList = OwnedIdentityDetails.getAllWithMissingPhotoUrl(wrapSession(session));
        for (OwnedIdentityDetails ownedIdentityDetails : ownedIdentityDetailsList) {
            protocolStarterDelegate.startDownloadIdentityPhotoProtocolWithinTransaction(session, ownedIdentityDetails.getOwnedIdentity(), ownedIdentityDetails.getOwnedIdentity(), ownedIdentityDetails.getJsonIdentityDetailsWithVersionAndPhoto());
        }

        List<ContactIdentityDetails> contactIdentityDetailsList = ContactIdentityDetails.getAllWithMissingPhotoUrl(wrapSession(session));
        for (ContactIdentityDetails contactIdentityDetails : contactIdentityDetailsList) {
            protocolStarterDelegate.startDownloadIdentityPhotoProtocolWithinTransaction(session, contactIdentityDetails.getOwnedIdentity(), contactIdentityDetails.getContactIdentity(), contactIdentityDetails.getJsonIdentityDetailsWithVersionAndPhoto());
        }

        List<ContactGroupDetails> contactGroupDetailsList = ContactGroupDetails.getAllWithMissingPhotoUrl(wrapSession(session));
        for (ContactGroupDetails contactGroupDetails : contactGroupDetailsList) {
            protocolStarterDelegate.startDownloadGroupPhotoProtocolWithinTransaction(session, contactGroupDetails.getOwnedIdentity(), contactGroupDetails.getGroupOwnerAndUid(), contactGroupDetails.getJsonGroupDetailsWithVersionAndPhoto());
        }

        List<ContactGroupV2Details> contactGroupV2DetailsList = ContactGroupV2Details.getAllWithMissingPhotoUrl(wrapSession(session));
        for (ContactGroupV2Details contactGroupV2Details : contactGroupV2DetailsList) {
            protocolStarterDelegate.startDownloadGroupV2PhotoProtocolWithinTransaction(session, contactGroupV2Details.getOwnedIdentity(), contactGroupV2Details.getGroupIdentifier(), contactGroupV2Details.getServerPhotoInfo());
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
            Logger.x(e);
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
    public Identity generateOwnedIdentity(Session session, String server, JsonIdentityDetails jsonIdentityDetails, ObvKeycloakState keycloakState, String deviceDisplayName, PRNGService prng) throws SQLException {
        if (!session.isInTransaction()) {
            session.startTransaction();
        }
        OwnedIdentity ownedIdentity = OwnedIdentity.create(wrapSession(session), server, null, null, jsonIdentityDetails, deviceDisplayName, prng);
        if (ownedIdentity == null) {
            return null;
        }

        try {
            protocolStarterDelegate.updateCurrentDeviceCapabilitiesForOwnedIdentity(session, ownedIdentity.getOwnedIdentity(), ObvCapability.currentCapabilities);
        } catch (Exception e) {
            Logger.w("Failed to update generated identity capabilities");
            Logger.x(e);
        }

        if (keycloakState != null) {
            KeycloakServer keycloakServer = KeycloakServer.create(wrapSession(session), keycloakState.keycloakServer, ownedIdentity.getOwnedIdentity(), keycloakState.jwks.toJson(), keycloakState.signatureKey == null ? null : keycloakState.signatureKey.toJson(), keycloakState.clientId, keycloakState.clientSecret, keycloakState.transferRestricted);
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
        currentDeviceUidCache.remove(ownedIdentity);
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            // delete all contact groups (and associated details)
            //  - this cascade deletes ContactGroupMembersJoin
            //  - this cascade deletes PendingGroupMember
            ContactGroup[] contactGroups = ContactGroup.getAllForIdentity(wrapSession(session), ownedIdentity);
            for (ContactGroup contactGroup : contactGroups) {
                contactGroup.delete();
            }

            // delete all contact groupsV2 (and associated details)
            //  - this cascade deletes ContactGroupV2Members
            //  - this cascade deletes ContactGroupV2PendingMember
            List<ContactGroupV2> contactGroupsV2 = ContactGroupV2.getAllForIdentity(wrapSession(session), ownedIdentity);
            for (ContactGroupV2 contactGroupV2 : contactGroupsV2) {
                contactGroupV2.delete();
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

    public void createOwnedIdentityServerUserData(Session session, Identity ownedIdentity, UID photoServerLabel) throws SQLException {
        if (ServerUserData.createForOwnedIdentityDetails(wrapSession(session), ownedIdentity, photoServerLabel) == null) {
            throw new SQLException();
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
    public boolean setOwnedIdentityDetailsFromOtherDevice(Session session, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto ownDetailsWithVersionAndPhoto) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            return ownedIdentityObject.setOwnedIdentityDetailsFromOtherDevice(ownDetailsWithVersionAndPhoto);
        }
        return false;
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
    public Collection<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(Session session, String pushTopic) throws SQLException {
        List<KeycloakServer> keycloakServers = KeycloakServer.getAllWithPushTopic(wrapSession(session), pushTopic);
        List<ContactGroupV2> keycloakGroups = ContactGroupV2.getAllWithPushTopic(wrapSession(session), pushTopic);
        HashSet<ObvIdentity> ownedIdentities = new HashSet<>();
        for (KeycloakServer keycloakServer: keycloakServers) {
            ownedIdentities.add(new ObvIdentity(session, this, keycloakServer.getOwnedIdentity()));
        }
        for (ContactGroupV2 keycloakGroup : keycloakGroups) {
            ownedIdentities.add(new ObvIdentity(session, this, keycloakGroup.getOwnedIdentity()));
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
            if (signatureKey == null) {
                ContactGroupV2.deleteAllKeycloakGroupsForOwnedIdentity(wrapSession(session), ownedIdentity);
            }
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
                ContactIdentityDetails publishedDetails = contactIdentity.getPublishedDetails();
                JwtClaims claims = noVerificationConsumer.processToClaims(publishedDetails.getJsonIdentityDetails().getSignedUserDetails());
                JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(claims.getRawJson(), JsonKeycloakUserDetails.class);

                if (jsonKeycloakUserDetails.getTimestamp() != null && jsonKeycloakUserDetails.getTimestamp() < latestRevocationListTimestamp - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS) {
                    // signature no longer valid --> remove certification
                    contactIdentity.setCertifiedByOwnKeycloak(false, publishedDetails.getSerializedJsonDetails());
                }
            } catch (Exception ignored) { }
        }
    }

    @Override
    public List<String> getKeycloakPushTopics(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null || !ownedIdentityObject.isKeycloakManaged()) {
            return new ArrayList<>(0);
        }
        List<String> pushTopics = new ArrayList<>();

        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity);
        if (keycloakServer != null) {
            pushTopics.addAll(keycloakServer.getPushTopics());
        }
        List<String> groupPushTopics = ContactGroupV2.getAllKeycloakPushTopics(wrapSession(session), ownedIdentity);
        if (groupPushTopics != null) {
            pushTopics.addAll(groupPushTopics);
        }

        return pushTopics;
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
                        ContactIdentity contactIdentity = ContactIdentity.get(wrapSession(session), ownedIdentity, revokedIdentity);
                        if (contactIdentity != null) {
                            switch (jsonKeycloakRevocation.getRevocationType()) {
                                case KeycloakRevokedIdentity.TYPE_LEFT_COMPANY:
                                    if (contactIdentity.isCertifiedByOwnKeycloak()) {

                                        JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                                                .setSkipSignatureVerification()
                                                .setSkipAllValidators()
                                                .build();
                                        ContactIdentityDetails publishedDetails = contactIdentity.getPublishedDetails();
                                        JwtClaims claims = noVerificationConsumer.processToClaims(publishedDetails.getJsonIdentityDetails().getSignedUserDetails());
                                        JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(claims.getRawJson(), JsonKeycloakUserDetails.class);

                                        if (jsonKeycloakUserDetails.getTimestamp() == null || jsonKeycloakRevocation.getRevocationTimestamp() > jsonKeycloakUserDetails.getTimestamp()) {
                                            // the user left the company after the signature of his details --> unmark as certified
                                            contactIdentity.setCertifiedByOwnKeycloak(false, publishedDetails.getSerializedJsonDetails());
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
                                    ContactIdentityDetails publishedDetails = contactIdentity.getPublishedDetails();
                                    contactIdentity.setCertifiedByOwnKeycloak(false, publishedDetails.getSerializedJsonDetails());
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
    public JsonKeycloakUserDetails verifyKeycloakIdentitySignature(Session session, Identity ownedIdentity, String signature) {
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
    public String verifyKeycloakSignature(Session session, Identity ownedIdentity, String signature) {
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
                // signature is valid
                return context.getJwtClaims().getRawJson();
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
    public void saveKeycloakApiKey(Session session, Identity ownedIdentity, String apiKey) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && ownedIdentityObject.isKeycloakManaged()) {
            KeycloakServer.saveApiKey(wrapSession(session), ownedIdentityObject.getKeycloakServerUrl(), ownedIdentity, apiKey);
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

        KeycloakServer keycloakServer = KeycloakServer.create(wrapSession(session), keycloakState.keycloakServer, ownedIdentity, keycloakState.jwks.toJson(), keycloakState.signatureKey == null ? null : keycloakState.signatureKey.toJson(), keycloakState.clientId, keycloakState.clientSecret, keycloakState.transferRestricted);
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
            // also remove position and company
            jsonIdentityDetails.setPosition(null);
            jsonIdentityDetails.setCompany(null);

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
    public void updateKeycloakTransferRestrictedIfNeeded(Session session, Identity ownedIdentity, String serverUrl, boolean transferRestricted) throws SQLException {
        KeycloakServer keycloakServer = KeycloakServer.get(wrapSession(session), serverUrl, ownedIdentity);

        if (keycloakServer != null) {
            if (transferRestricted ^ keycloakServer.isTransferRestricted()) {
                keycloakServer.setTransferRestricted(transferRestricted);
            }
        }
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

        if (keycloakServer != null && !Objects.equals(keycloakServer.getSelfRevocationTestNonce(), nonce)) {
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
    public void updateKeycloakGroups(Session session, Identity ownedIdentity, List<String> signedGroupBlobs, List<String> signedGroupDeletions, List<String> signedGroupKicks, long keycloakCurrentTimestamp) throws Exception {
        if (!session.isInTransaction()) {
            Logger.e("Called updateKeycloakGroups outside a transaction");
            throw new Exception();
        }
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject == null || !ownedIdentityObject.isKeycloakManaged()) {
            Logger.e("Called updateKeycloakGroups for an identity that is not keycloak managed");
            throw new Exception();
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


        // first process group deletions
        if (signedGroupDeletions != null) {
            for (String signedGroupDeletion : signedGroupDeletions) {
                try {
                    JwtClaims claims = jwtConsumer.processToClaims(signedGroupDeletion);
                    if (claims == null) {
                        // invalid signature --> ignore it
                        continue;
                    }

                    KeycloakGroupDeletionData keycloakGroupDeletionData = jsonObjectMapper.readValue(claims.getRawJson(), KeycloakGroupDeletionData.class);
                    UID groupUid = new UID(keycloakGroupDeletionData.groupUid);
                    GroupV2.Identifier groupIdentifier = new GroupV2.Identifier(groupUid, keycloakServer.getServerUrl(), GroupV2.Identifier.CATEGORY_KEYCLOAK);

                    Long groupLastModificationTimestamp = ContactGroupV2.getLastModificationTimestamp(wrapSession(session), ownedIdentity, groupIdentifier);
                    if (groupLastModificationTimestamp == null || groupLastModificationTimestamp > keycloakGroupDeletionData.timestamp) {
                        // if the group is not found, or is more recent than the signed deletion, do not do anything
                        continue;
                    }

                    // group was disbanded, delete it locally
                    deleteGroupV2(session, ownedIdentity, groupIdentifier);
                } catch (InvalidJwtException | JsonProcessingException | IllegalArgumentException e) {
                    // unable to process signed deletion --> ignore it
                    Logger.x(e);
                }
            }
        }

        // then group kicks
        if (signedGroupKicks != null) {
            for (String signedGroupKick : signedGroupKicks) {
                try {
                    JwtClaims claims = jwtConsumer.processToClaims(signedGroupKick);
                    if (claims == null) {
                        // invalid signature --> ignore it
                        continue;
                    }

                    KeycloakGroupMemberKickedData keycloakGroupMemberKickedData = jsonObjectMapper.readValue(claims.getRawJson(), KeycloakGroupMemberKickedData.class);
                    UID groupUid = new UID(keycloakGroupMemberKickedData.groupUid);
                    // verify it's indeed me who's getting kicked
                    if (!Arrays.equals(ownedIdentity.getBytes(), keycloakGroupMemberKickedData.identity)) {
                        continue;
                    }
                    GroupV2.Identifier groupIdentifier = new GroupV2.Identifier(groupUid, keycloakServer.getServerUrl(), GroupV2.Identifier.CATEGORY_KEYCLOAK);

                    Long groupLastModificationTimestamp = ContactGroupV2.getLastModificationTimestamp(wrapSession(session), ownedIdentity, groupIdentifier);
                    if (groupLastModificationTimestamp == null || groupLastModificationTimestamp > keycloakGroupMemberKickedData.timestamp) {
                        // if the group is not found, or is more recent than the signed deletion, do not do anything
                        continue;
                    }

                    // I was kicked from the group, delete it locally
                    deleteGroupV2(session, ownedIdentity, groupIdentifier);
                } catch (InvalidJwtException | JsonProcessingException | IllegalArgumentException e) {
                    // unable to process signed deletion --> ignore it
                    Logger.x(e);
                }
            }
        }

        // update group blobs
        if (signedGroupBlobs != null) {
            for (String signedGroupBlob : signedGroupBlobs) {
                try {
                    JwtClaims claims = jwtConsumer.processToClaims(signedGroupBlob);
                    if (claims == null) {
                        // invalid signature --> ignore it
                        continue;
                    }

                    String serializedKeycloakGroupBlob = claims.getRawJson();
                    KeycloakGroupBlob keycloakGroupBlob = jsonObjectMapper.readValue(serializedKeycloakGroupBlob, KeycloakGroupBlob.class);
                    UID groupUid = new UID(keycloakGroupBlob.bytesGroupUid);
                    GroupV2.Identifier groupIdentifier = new GroupV2.Identifier(groupUid, keycloakServer.getServerUrl(), GroupV2.Identifier.CATEGORY_KEYCLOAK);

                    if (keycloakGroupBlob.timestamp < keycloakCurrentTimestamp - Constants.KEYCLOAK_SIGNATURE_VALIDITY_MILLIS) {
                        Logger.w("Received a signed keyclaok groupblob with an outdated signature");
                        continue;
                    }

                    protocolStarterDelegate.createOrUpdateKeycloakGroupV2(session, ownedIdentity, groupIdentifier, serializedKeycloakGroupBlob);
                } catch (InvalidJwtException | JsonProcessingException | IllegalArgumentException e) {
                    // unable to process signed deletion --> ignore it
                    Logger.x(e);
                }
            }
        }

        // finally set the lastGroupUpdateTimestamp
        keycloakServer.setLatestGroupUpdateTimestamp(keycloakCurrentTimestamp);
    }

    @Override
    public void reactivateOwnedIdentityIfNeeded(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && !ownedIdentityObject.isActive()) {
            ownedIdentityObject.setActive(true);
            ////////////
            // After reactivating an identity, we must recreate all channels (that were destroyed after the deactivation)
            //  - restart channel creation for all owned devices (those were not deleted during deactivation)
            //  - restart all device discovery protocols
            //  - also do an owned device discovery
            try {
                for (UID ownedDeviceUid : getOtherDeviceUidsOfOwnedIdentity(session, ownedIdentity)) {
                    protocolStarterDelegate.startChannelCreationProtocolWithOwnedDevice(session, ownedIdentity, ownedDeviceUid);
                }
            } catch (Exception e) {
                Logger.x(e);
            }

            ContactIdentity[] contactIdentities = ContactIdentity.getAll(wrapSession(session), ownedIdentity);
            for (ContactIdentity contactIdentity : contactIdentities) {
                try {
                    protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(session, ownedIdentity, contactIdentity.getContactIdentity());
                } catch (Exception e) {
                    Logger.x(e);
                }
            }
            try {
                protocolStarterDelegate.startOwnedDeviceDiscoveryProtocolWithinTransaction(session, ownedIdentity);
            } catch (Exception e) {
                Logger.x(e);
            }

        }
    }

    @Override
    public void deactivateOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        // set inactive even if it is already deactivated to trigger the notification
        ownedIdentityObject.setActive(false);
        // also clear any timestamp in the current device
        OwnedDevice ownedDevice = OwnedDevice.getCurrentDeviceOfOwnedIdentity(wrapSession(session), ownedIdentity);
        ownedDevice.setTimestamps(null, null);
        ////////////
        // After deactivating an identity, we must delete all channels
        //  - clear all contact deviceUid
        //  - delete all channels
        //  - we keep our owned devices, so that the app still knows the list
        //  - we trigger all ongoing sync protocols so that they detect the channel is gone and can finish
        ContactDevice.deleteAll(wrapSession(session), ownedIdentity);
        channelDelegate.deleteAllChannelsForOwnedIdentity(session, ownedIdentity);
//        protocolStarterDelegate.triggerOwnedDevicesSync(session, ownedIdentity);
    }

    @Override
    public void markOwnedIdentityForDeletion(Session session, Identity ownedIdentity) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            ownedIdentityObject.markForDeletion();
        }
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
    public UID getCurrentDeviceUidOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        UID cachedUid = currentDeviceUidCache.get(ownedIdentity);
        if (cachedUid != null) {
            return cachedUid;
        }
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null) {
            UID deviceUid = ownedIdentityObject.getCurrentDeviceUid();
            currentDeviceUidCache.put(ownedIdentity, deviceUid);
            return deviceUid;
        }
        return null;
    }

    @Override
    public Identity getOwnedIdentityForCurrentDeviceUid(Session session, UID currentDeviceUid) throws SQLException {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), currentDeviceUid);
        if (ownedDevice != null && ownedDevice.isCurrentDevice()) {
            return ownedDevice.getOwnedIdentity();
        }
        return null;
    }

    @Override
    public void addDeviceForOwnedIdentity(Session session, Identity ownedIdentity, UID deviceUid, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKeyBlobOnServer preKeyBlob, boolean channelCreationAlreadyInProgress) throws SQLException {
        // check if the device already exists first
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), deviceUid);
        if (ownedDevice != null && !Objects.equals(ownedDevice.getOwnedIdentity(), ownedIdentity)) {
            Logger.e("Error: trying to addDeviceForOwnedIdentity for a deviceUid already used by another identity");
            throw new SQLException();
        }
        // only create the device if it does not already exist
        if (ownedDevice == null) {
            ownedDevice = OwnedDevice.createOtherDevice(wrapSession(session), deviceUid, ownedIdentity, displayName, expirationTimestamp, lastRegistrationTimestamp, preKeyBlob, channelCreationAlreadyInProgress);
            if (ownedDevice == null) {
                throw new SQLException();
            }
        }
    }

    @Override
    public void updateOwnedDevice(Session session, Identity ownedIdentity, UID deviceUid, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKeyBlobOnServer preKeyBlob) throws SQLException {
        // check that the device exists and is for the right ownedIdentity
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), deviceUid);
        if (ownedDevice != null && Objects.equals(ownedDevice.getOwnedIdentity(), ownedIdentity)) {
            if (!Objects.equals(displayName, ownedDevice.getDisplayName())) {
                ownedDevice.setDisplayName(displayName);
            }
            if (!Objects.equals(expirationTimestamp, ownedDevice.getExpirationTimestamp())
                    || !Objects.equals(lastRegistrationTimestamp, ownedDevice.getLastRegistrationTimestamp())) {
                ownedDevice.setTimestamps(expirationTimestamp, lastRegistrationTimestamp);
            }
            if (preKeyBlob == null) {
                if (ownedDevice.getPreKey() != null) {
                    ownedDevice.setPreKey(null);
                }
            } else {
                if (!ownedDevice.hasPreKey() || !Objects.equals(ownedDevice.getPreKey().keyId, preKeyBlob.preKey.keyId)) {
                    ownedDevice.setPreKey(preKeyBlob.preKey);
                }
                if (ownedDevice.getDeviceCapabilities() == null) {
                    ownedDevice.setRawDeviceCapabilities(preKeyBlob.rawDeviceCapabilities);
                }
            }
        }
    }

    @Override
    public void removeDeviceForOwnedIdentity(Session session, Identity ownedIdentity, UID deviceUid) throws SQLException {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), deviceUid);
        if (ownedDevice != null && ownedDevice.getOwnedIdentity().equals(ownedIdentity)) {
            ownedDevice.delete();
        }
    }


    @Override
    public List<ObvOwnedDevice> getDevicesOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        List<OwnedDevice> ownedDevices = OwnedDevice.getAllDevicesOfIdentity(wrapSession(session), ownedIdentity);
        List<ObvOwnedDevice> obvOwnedDevices = new ArrayList<>();
        for (OwnedDevice ownedDevice : ownedDevices) {
            obvOwnedDevices.add(new ObvOwnedDevice(ownedDevice.getOwnedIdentity().getBytes(),
                    ownedDevice.getUid().getBytes(),
                    new ObvOwnedDevice.ServerDeviceInfo(ownedDevice.getDisplayName(), ownedDevice.getExpirationTimestamp(), ownedDevice.getLastRegistrationTimestamp()),
                    ownedDevice.isCurrentDevice(),
                    channelDelegate.checkIfObliviousChannelIsConfirmed(session, ownedIdentity, ownedDevice.getUid(), ownedIdentity),
                    ownedDevice.hasPreKey()
            ));
        }
        return obvOwnedDevices;
    }

    @Override
    public List<OwnedDeviceAndPreKey> getDevicesAndPreKeysOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        List<OwnedDevice> ownedDevices = OwnedDevice.getAllDevicesOfIdentity(wrapSession(session), ownedIdentity);
        List<OwnedDeviceAndPreKey> ownedDeviceAndPreKeys = new ArrayList<>();
        for (OwnedDevice ownedDevice : ownedDevices) {
            ownedDeviceAndPreKeys.add(new OwnedDeviceAndPreKey(ownedDevice.getOwnedIdentity(),
                    ownedDevice.getUid(),
                    ownedDevice.isCurrentDevice(),
                    ownedDevice.getPreKey(),
                    new ObvOwnedDevice.ServerDeviceInfo(ownedDevice.getDisplayName(), ownedDevice.getExpirationTimestamp(), ownedDevice.getLastRegistrationTimestamp())
            ));
        }
        return ownedDeviceAndPreKeys;
    }

    @Override
    public String getCurrentDeviceDisplayName(Session session, Identity ownedIdentity) throws SQLException {
        OwnedDevice device = OwnedDevice.getCurrentDeviceOfOwnedIdentity(wrapSession(session), ownedIdentity);
        if (device != null) {
            return device.getDisplayName();
        }
        return null;
    }

    @Override
    public EncodedOwnedPreKey getLatestPreKeyForOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        OwnedPreKey ownedPreKey = OwnedPreKey.getLatest(wrapSession(session), ownedIdentity);
        if (ownedPreKey != null) {
            return new EncodedOwnedPreKey(ownedPreKey.getKeyId(), ownedPreKey.getExpirationTimestamp(), ownedPreKey.getEncodedSignedPreKey());
        }
        return null;
    }

    @Override
    public Encoded generateNewPreKey(Session session, Identity ownedIdentity, long expirationTimestamp) throws SQLException {
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
        OwnedDevice device = OwnedDevice.getCurrentDeviceOfOwnedIdentity(wrapSession(session), ownedIdentity);
        if (ownedIdentityObject != null && device != null) {
            OwnedPreKey ownedPreKey = OwnedPreKey.create(wrapSession(session), ownedIdentity, ownedIdentityObject.getPrivateIdentity(), device.getUid(), expirationTimestamp, prng);
            if (ownedPreKey != null) {
                return ownedPreKey.getEncodedSignedPreKey();
            }
        }
        return null;
    }

    @Override
    public void expireContactAndOwnedPreKeys(Session session, Identity ownedIdentity, String server, long serverTimestamp) throws SQLException {
        if (ownedIdentity.getServer().equals(server)) {
            // expire own pre-keys
            List<OwnedDevice> ownedDevices = OwnedDevice.getAllWithExpiredPreKey(wrapSession(session), ownedIdentity, serverTimestamp);
            for (OwnedDevice ownedDevice : ownedDevices) {
                ownedDevice.setPreKey(null);
            }
        }

        List<ContactDevice> contactDevices = ContactDevice.getAllWithExpiredPreKey(wrapSession(session), ownedIdentity, serverTimestamp);
        for (ContactDevice contactDevice : contactDevices) {
            if (contactDevice.getContactIdentity().getServer().equals(server)) {
                contactDevice.setPreKey(null);
            }
        }
    }

    @Override
    public void expireCurrentDeviceOwnedPreKeys(Session session, Identity ownedIdentity, long currentServerTimestamp) throws SQLException {
        OwnedPreKey.deleteExpired(wrapSession(session), ownedIdentity, currentServerTimestamp - Constants.PRE_KEY_CONSERVATION_DURATION);
    }

    @Override
    public long getLatestChannelCreationPingTimestampForOwnedDevice(Session session, Identity ownedIdentity, UID ownedDeviceUid) throws SQLException {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), ownedDeviceUid);
        if (ownedDevice != null && ownedDevice.getOwnedIdentity().equals(ownedIdentity)) {
            return ownedDevice.getLatestChannelCreationPingTimestamp();
        }
        return -1;
    }

    @Override
    public void setLatestChannelCreationPingTimestampForOwnedDevice(Session session, Identity ownedIdentity, UID ownedDeviceUid, long timestamp) throws Exception {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), ownedDeviceUid);
        if (ownedDevice != null && ownedDevice.getOwnedIdentity().equals(ownedIdentity)) {
            ownedDevice.setLatestChannelCreationPingTimestamp(timestamp);
        }
    }


    // endregion


    @Override
    public void addContactIdentity(Session session, Identity contactIdentity, String serializedDetails, Identity ownedIdentity, TrustOrigin trustOrigin, boolean oneToOne) throws Exception {
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

            ContactIdentity contactIdentityObject = ContactIdentity.create(wrapSession(session), contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto, trustOrigin, contactIsRevoked, oneToOne);
            if (contactIdentityObject == null) {
                Logger.w("An error occurred while creating a ContactIdentity.");
                throw new SQLException();
            }
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        } catch (Exception e) {
            Logger.x(e);
            throw new Exception();
        }
    }

    @Override
    public void addTrustOriginToContact(Session session, Identity contactIdentity, Identity ownedIdentity, TrustOrigin trustOrigin, boolean markAsOneToOne) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject == null) {
            Logger.e("Error in addTrustOriginToContact: contactIdentity is not a ContactIdentity of ownedIdentity");
            throw new SQLException();
        }
        contactIdentityObject.addTrustOrigin(trustOrigin);
        if (markAsOneToOne && !contactIdentityObject.isOneToOne()) {
            contactIdentityObject.setOneToOne(true);
        }
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
            Logger.x(e);
        }
        return null;
    }

    public ObvContactDeviceCount getContactDeviceCounts(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        int count = 0;
        int establihed = 0;
        int preKey = 0;
        HashSet<UID> confirmedChannelUids = new HashSet<>(Arrays.asList(channelDelegate.getConfirmedObliviousChannelDeviceUids(session, ownedIdentity, contactIdentity)));
        for (UidAndPreKey uidAndPreKey : getDeviceUidsAndPreKeysOfContactIdentity(session, ownedIdentity, contactIdentity)) {
            count++;
            if (confirmedChannelUids.contains(uidAndPreKey.uid)) {
                establihed++;
            } else if (uidAndPreKey.preKey != null) {
                preKey++;
            }
        }
        return new ObvContactDeviceCount(count, establihed, preKey);
    }

    public List<ObvContactInfo> getContactsInfoOfOwnedIdentity(Session session, Identity ownedIdentity) throws Exception {
        ContactIdentity[] contactIdentities = ContactIdentity.getAll(wrapSession(session), ownedIdentity);
        List<ObvContactInfo> contactInfos = new ArrayList<>();
        for (ContactIdentity contactIdentity : contactIdentities) {

            ContactIdentityDetails trustedDetails = contactIdentity.getTrustedDetails();
            contactInfos.add(new ObvContactInfo(
                    contactIdentity.getOwnedIdentity().getBytes(),
                    contactIdentity.getContactIdentity().getBytes(),
                    trustedDetails.getJsonIdentityDetails(),
                    contactIdentity.isCertifiedByOwnKeycloak(),
                    contactIdentity.isOneToOne(),
                    trustedDetails.getPhotoUrl(),
                    contactIdentity.isActive(),
                    contactIdentity.isRecentlyOnline(),
                    contactIdentity.getTrustLevel().major,
                    getContactDeviceCounts(session, ownedIdentity, contactIdentity.getContactIdentity())
            ));
        }
        return contactInfos;
    }

    @Override
    public JsonIdentityDetailsWithVersionAndPhoto trustPublishedContactDetails(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            JsonIdentityDetailsWithVersionAndPhoto details = contactIdentityObject.trustPublishedDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
            return details;
        }
        return null;
    }

    @Override
    public void setContactPublishedDetails(Session session, Identity contactIdentity, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto, boolean allowDowngrade) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.updatePublishedDetails(jsonIdentityDetailsWithVersionAndPhoto, allowDowngrade);
            session.addSessionCommitListener(backupNeededSessionCommitListener);
        }
    }

    @Override
    public void setContactDetailsDownloadedPhoto(Session session, Identity contactIdentity, Identity ownedIdentity, int version, byte[] photo) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.setDetailsDownloadedPhotoUrl(version, photo);
        }
    }

    @Override
    public String getSerializedPublishedDetailsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) {
        return ContactIdentity.getSerializedPublishedDetails(wrapSession(session), ownedIdentity, contactIdentity);
    }

    @Override
    public JsonIdentityDetails getContactIdentityTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getTrustedDetails().getJsonIdentityDetails();
        }
        return null;
    }

    @Override
    public String getContactTrustedDetailsPhotoUrl(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getTrustedDetails().getPhotoUrl();
        }
        return null;
    }

    @Override
    public boolean contactHasUntrustedPublishedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getPublishedDetailsVersion() != contactIdentityObject.getTrustedDetailsVersion();
        }
        return false;
    }


    @Override
    public JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
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
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
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
        for (ContactIdentity contactIdentity : ContactIdentity.getAll(wrapSession(session), ownedIdentity)) {
            ContactIdentityDetails publishedDetails = contactIdentity.getPublishedDetails();

            if (publishedDetails != null) {
                JsonIdentityDetails identityDetails = publishedDetails.getJsonIdentityDetails();
                if (identityDetails != null && identityDetails.getSignedUserDetails() != null) {
                    JsonKeycloakUserDetails jsonKeycloakUserDetails = verifyKeycloakIdentitySignature(session, ownedIdentity, identityDetails.getSignedUserDetails());

                    if (jsonKeycloakUserDetails != null) {
                        // the contact has some valid signed details
                        try {
                            JsonIdentityDetails certifiedJsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(identityDetails.getSignedUserDetails());
                            contactIdentity.markContactAsCertifiedByOwnKeycloak(certifiedJsonIdentityDetails);
                            continue;
                        } catch (Exception e) {
                            // error parsing signed details --> do nothing
                            Logger.x(e);
                        }
                    }
                }
            }

            if (contactIdentity.isCertifiedByOwnKeycloak()) {
                contactIdentity.setCertifiedByOwnKeycloak(false, null);
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
    public TrustLevel getContactTrustLevel(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            return contactIdentityObject.getTrustLevel();
        }
        return null;
    }

    @Override
    public void deleteContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, boolean failIfGroup) throws Exception {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            // check there are no Groups where this contact is
            if (failIfGroup) {
                byte[][] memberGroupUids = ContactGroupMembersJoin.getGroupOwnerAndUidsOfGroupsContainingContact(wrapSession(session), contactIdentity, ownedIdentity);
                if (memberGroupUids.length > 0) {
                    Logger.w("Attempted to delete a contact still member of some groups.");
                    throw new Exception();
                }

                if (ContactGroupV2Member.isContactMemberOfAGroupV2(wrapSession(session), ownedIdentity, contactIdentity)) {
                    Logger.w("Attempted to delete a contact still member of some groups v2.");
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
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        return (contactIdentityObject != null && contactIdentityObject.isActive());
    }

    @Override
    public boolean isIdentityAContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        return (contactIdentityObject != null);
    }

    @Override
    public boolean isIdentityAOneToOneContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        return (contactIdentityObject != null && contactIdentityObject.isOneToOne());
    }

    @Override
    public boolean isIdentityANotOneToOneContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        return (contactIdentityObject != null && contactIdentityObject.isNotOneToOne());
    }


    // this method always sets to ONE_TO_ONE_STATUS_TRUE or ONE_TO_ONE_STATUS_FALSE, but never leaves in ONE_TO_ONE_STATUS_UNKNOWN
    @Override
    public void setContactOneToOne(Session session, Identity ownedIdentity, Identity contactIdentity, boolean oneToOne) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        // only actually call the setter if the oneToOne is changed (or if contact oneToOne was unknown)
        if (contactIdentityObject != null
                && ((oneToOne && !contactIdentityObject.isOneToOne()) || (!oneToOne && !contactIdentityObject.isNotOneToOne()))) {
            contactIdentityObject.setOneToOne(oneToOne);
        }
    }

    @Override
    public EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
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
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject == null) {
            return false;
        }
        contactIdentityObject.setForcefullyTrustedByUser(true);
        return true;
    }

    @Override
    public boolean reBlockForcefullyUnblockedContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
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
            Logger.x(e);
            return false;
        }
    }

    @Override
    public void setContactRecentlyOnline(Session session, Identity ownedIdentity, Identity contactIdentity, boolean recentlyOnline) throws SQLException {
        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contactIdentityObject != null) {
            contactIdentityObject.setRecentlyOnline(recentlyOnline);
        }
    }

    @Override
    public boolean addDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid, PreKeyBlobOnServer preKeyBlob, boolean channelCreationAlreadyInProgress) throws SQLException {
        ContactIdentity contact = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
        if (contact != null && contact.isActive()) {
            ContactDevice contactDevice = ContactDevice.get(wrapSession(session), deviceUid, contactIdentity, ownedIdentity);
            // only create the contact device if it does not already exist
            if (contactDevice == null) {
                contactDevice = ContactDevice.create(wrapSession(session), deviceUid, contactIdentity, ownedIdentity, preKeyBlob, channelCreationAlreadyInProgress);
                if (contactDevice == null) {
                    throw new SQLException();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isContactDeviceKnown(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException {
        return ContactDevice.exists(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity);
    }

    @Override
    public void updateContactDevicePreKey(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid, PreKeyBlobOnServer preKeyBlob) throws SQLException {
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), deviceUid, contactIdentity, ownedIdentity);
        if (contactDevice != null) {
            contactDevice.setPreKey(preKeyBlob);
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
            ContactDevice[] contactDevices = ContactDevice.getAll(wrapSession(session), contactIdentity, ownedIdentity);
            UID[] uids = new UID[contactDevices.length];
            for (int i = 0; i < contactDevices.length; i++) {
                uids[i] = contactDevices[i].getUid();
            }
            return uids;
        } catch (SQLException e) {
            Logger.x(e);
        }
        return new UID[0];
    }

    @Override
    public List<UidAndPreKey> getDeviceUidsAndPreKeysOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) {
        try {
            List<UidAndPreKey> uids = new ArrayList<>();
            ContactDevice[] contactDevices = ContactDevice.getAll(wrapSession(session), contactIdentity, ownedIdentity);
            for (ContactDevice contactDevice : contactDevices) {
                uids.add(new UidAndPreKey(contactDevice.getUid(), contactDevice.getPreKey()));
            }
            return uids;
        } catch (SQLException e) {
            Logger.x(e);
        }
        return Collections.emptyList();
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
    public long getLatestChannelCreationPingTimestampForContactDevice(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException {
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity);
        if (contactDevice != null) {
            return contactDevice.getLatestChannelCreationPingTimestamp();
        }
        return -1;
    }

    @Override
    public void setLatestChannelCreationPingTimestampForContactDevice(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid, long timestamp) throws Exception {
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity);
        if (contactDevice != null) {
            contactDevice.setLatestChannelCreationPingTimestamp(timestamp);
        }
    }

    @Override
    public List<ObvCapability> getContactCapabilities(Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        // for now, we compute the intersection of all device capabilities. This may change in the future depending on capabilities we will add
        try (IdentityManagerSession identityManagerSession = getSession()) {
            return getContactCapabilities(identityManagerSession, ownedIdentity, contactIdentity);
        }
    }

    private List<ObvCapability> getContactCapabilities(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        ContactDevice[] contactDevices = ContactDevice.getAll(identityManagerSession, contactIdentity, ownedIdentity);
        HashSet<ObvCapability> contactCapabilities = null;
        for (ContactDevice contactDevice : contactDevices) {
            List<ObvCapability> deviceCapabilities = contactDevice.getDeviceCapabilities();
            if (deviceCapabilities == null) {
                continue;
            }
            if (deviceCapabilities.isEmpty()) {
                return new ArrayList<>();
            }
            if (contactCapabilities == null) {
                contactCapabilities = new HashSet<>(deviceCapabilities);
            } else {
                contactCapabilities.retainAll(deviceCapabilities);
                if (contactCapabilities.isEmpty()) {
                    return new ArrayList<>();
                }
            }
        }
        if (contactCapabilities == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(contactCapabilities);
    }

    @Override
    public String[] getContactDeviceCapabilities(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException{
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity);
        if (contactDevice != null) {
            return contactDevice.getRawDeviceCapabilities();
        }
        return new String[0];
    }

    @Override
    public void setContactDeviceCapabilities(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid, String[] rawDeviceCapabilities) throws Exception {
        ContactDevice contactDevice = ContactDevice.get(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity);
        if (contactDevice == null) {
            throw new Exception();
        }
        contactDevice.setRawDeviceCapabilities(rawDeviceCapabilities);
    }

    @Override
    public List<ObvCapability> getOwnCapabilities(Identity ownedIdentity) throws SQLException {
        // for now, we compute the intersection of all device capabilities. This may change in the future depending on capabilities we will add
        try (IdentityManagerSession identityManagerSession = getSession()) {
            // initialize with current capabilities
            HashSet<ObvCapability> ownCapabilities = new HashSet<>(ObvCapability.currentCapabilities);

            // update with other devices
            OwnedDevice[] ownedDevices = OwnedDevice.getOtherDevicesOfOwnedIdentity(identityManagerSession, ownedIdentity);
            for (OwnedDevice ownedDevice : ownedDevices) {
                List<ObvCapability> deviceCapabilities = ownedDevice.getDeviceCapabilities();
                if (deviceCapabilities == null) {
                    // skip this device, we do not know its capabilities yet
                    continue;
                }
                if (deviceCapabilities.isEmpty()) {
                    return new ArrayList<>();
                }
                ownCapabilities.retainAll(deviceCapabilities);
                if (ownCapabilities.isEmpty()) {
                    return new ArrayList<>();
                }
            }
            return new ArrayList<>(ownCapabilities);
        }
    }

    @Override
    public List<ObvCapability> getCurrentDevicePublishedCapabilities(Session session, Identity ownedIdentity) throws Exception {
        OwnedDevice ownedDevice = OwnedDevice.getCurrentDeviceOfOwnedIdentity(wrapSession(session), ownedIdentity);
        List<ObvCapability> capabilities = ownedDevice.getDeviceCapabilities();
        return (capabilities == null)  ? new ArrayList<>() : capabilities;
    }

    @Override
    public void setCurrentDevicePublishedCapabilities(Session session, Identity ownedIdentity, List<ObvCapability> capabilities) throws Exception {
        OwnedDevice ownedDevice = OwnedDevice.getCurrentDeviceOfOwnedIdentity(wrapSession(session), ownedIdentity);
        ownedDevice.setRawDeviceCapabilities(ObvCapability.capabilityListToStringArray(capabilities));
    }

    @Override
    public String[] getOtherOwnedDeviceCapabilities(Session session, Identity ownedIdentity, UID otherDeviceUid) throws Exception {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), otherDeviceUid);
        if (ownedDevice == null || !ownedDevice.getOwnedIdentity().equals(ownedIdentity)) {
            throw new Exception();
        }
        return ownedDevice.getRawDeviceCapabilities();
    }

    @Override
    public void setOtherOwnedDeviceCapabilities(Session session, Identity ownedIdentity, UID otherOwnedDeviceUID, String[] rawDeviceCapabilities) throws Exception {
        OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), otherOwnedDeviceUID);
        if (ownedDevice == null || !ownedDevice.getOwnedIdentity().equals(ownedIdentity)) {
            throw new Exception();
        }
        ownedDevice.setRawDeviceCapabilities(rawDeviceCapabilities);
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


    @Override
    public byte[] signIdentities(Session session, Constants.SignatureContext signatureContext, Identity[] identities, Identity ownedIdentity, PRNGService prng) throws Exception {
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
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            for (Identity identity: identities) {
                baos.write(identity.getBytes());
            }
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            baos.write(padding);
            byte[] challenge = baos.toByteArray();
            baos.close();
            Signature signature = Suite.getSignature(signaturePrivateKey);
            byte[] signatureBytes =  signature.sign(signaturePrivateKey, signaturePublicKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            Logger.x(e);
            return null;
        }
    }


    @Override
    public byte[] signChannel(Session session, Constants.SignatureContext signatureContext, Identity contactIdentity, UID contactDeviceUid, Identity ownedIdentity, UID ownedDeviceUid, PRNGService prng) throws Exception {
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
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            baos.write(contactDeviceUid.getBytes());
            baos.write(ownedDeviceUid.getBytes());
            baos.write(contactIdentity.getBytes());
            baos.write(ownedIdentity.getBytes());
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            baos.write(padding);
            byte[] challenge = baos.toByteArray();
            baos.close();
            Signature signature = Suite.getSignature(signaturePrivateKey);
            byte[] signatureBytes =  signature.sign(signaturePrivateKey, signaturePublicKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public byte[] signBlock(Session session, Constants.SignatureContext signatureContext, byte[] block, Identity ownedIdentity, PRNGService prng) throws Exception {
        try {
            IdentityManagerSession identityManagerSession = wrapSession(session);
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(identityManagerSession, ownedIdentity);
            if (ownedIdentityObject == null) {
                throw new Exception("Unknown owned identity");
            }
            PrivateIdentity privateIdentity = ownedIdentityObject.getPrivateIdentity();
            SignaturePublicKey signaturePublicKey = ownedIdentity.getServerAuthenticationPublicKey().getSignaturePublicKey();
            SignaturePrivateKey signaturePrivateKey = privateIdentity.getServerAuthenticationPrivateKey().getSignaturePrivateKey();

            byte[] prefix = Constants.getSignatureChallengePrefix(signatureContext);
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            //noinspection ConstantConditions
            byte[] challenge = new byte[prefix.length + block.length + Constants.SIGNATURE_PADDING_LENGTH];
            System.arraycopy(prefix, 0, challenge, 0, prefix.length);
            System.arraycopy(block, 0, challenge, prefix.length, block.length);
            System.arraycopy(padding, 0, challenge, prefix.length + block.length, Constants.SIGNATURE_PADDING_LENGTH);

            Signature signature = Suite.getSignature(signaturePrivateKey);
            byte[] signatureBytes =  signature.sign(signaturePrivateKey, signaturePublicKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public byte[] signGroupInvitationNonce(Session session, Constants.SignatureContext signatureContext, GroupV2.Identifier groupIdentifier, byte[] nonce, Identity contactIdentity, Identity ownedIdentity, PRNGService prng) throws Exception {
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
            //noinspection ConstantConditions
            baos.write(Constants.getSignatureChallengePrefix(signatureContext));
            baos.write(groupIdentifier.getBytes());
            baos.write(nonce);
            if (contactIdentity != null) {
                baos.write(contactIdentity.getBytes());
            }
            byte[] padding = prng.bytes(Constants.SIGNATURE_PADDING_LENGTH);
            baos.write(padding);
            byte[] challenge = baos.toByteArray();
            baos.close();

            Signature signature = Suite.getSignature(signaturePrivateKey);
            byte[] signatureBytes =  signature.sign(signaturePrivateKey, signaturePublicKey, challenge, prng);
            byte[] output = new byte[Constants.SIGNATURE_PADDING_LENGTH + signatureBytes.length];
            System.arraycopy(padding, 0, output, 0, Constants.SIGNATURE_PADDING_LENGTH);
            System.arraycopy(signatureBytes, 0, output, Constants.SIGNATURE_PADDING_LENGTH, signatureBytes.length);
            return output;
        } catch (InvalidKeyException e) {
            Logger.x(e);
            return null;
        }
    }




    // region groups

    @Override
    public void createContactGroup(Session session, Identity ownedIdentity, GroupInformation groupInformation, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers, boolean createdByMeOnOtherDevice) throws Exception {
        // check that all members are indeed existing contacts
        for (Identity groupMember: groupMembers) {
            if (!isIdentityAContactOfOwnedIdentity(session, ownedIdentity, groupMember)) {
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
                groupInformation.groupOwnerIdentity.equals(ownedIdentity) ? null : groupInformation.groupOwnerIdentity,
                createdByMeOnOtherDevice
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
    public void leaveGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
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
            if (!isIdentityAContactOfOwnedIdentity(session, ownedIdentity, contactIdentity)) {
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
        if (!isIdentityAContactOfOwnedIdentity(session, ownedIdentity, contactIdentity)) {
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

        ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, contactIdentity);
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

        boolean iAmTheGroupOwner = ownedIdentity.equals(groupInformation.groupOwnerIdentity);

        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupInformation.getGroupOwnerAndUid(), ownedIdentity);
        if (contactGroup == null) {
            Logger.w("Error: in updateGroupMembersAndDetails, group not found");
            throw new Exception();
        }

        // first, update the details (if needed)
        JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = jsonObjectMapper.readValue(groupInformation.serializedGroupDetailsWithVersionAndPhoto, JsonGroupDetailsWithVersionAndPhoto.class);
        if (contactGroup.updatePublishedDetails(jsonGroupDetailsWithVersionAndPhoto, false)) {
            if (iAmTheGroupOwner) {
                // If I updated the group, auto-trust new details
                contactGroup.trustPublishedDetails();
            }
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
                    ContactIdentity contactIdentityObject = ContactIdentity.get(wrapSession(session), ownedIdentity, groupMember.identity);
                    if (contactIdentityObject == null) {
                        if (ownedIdentity.equals(groupInformation.groupOwnerIdentity)) {
                            // We are forced to create a contact without a contact origin
                            // --> this is not good, but we don't have a choice. A group was created/updated on another device but we do not know this contact yet...
                            addContactIdentity(session, groupMember.identity, groupMember.serializedDetails, ownedIdentity, null, false);
                        } else {
                            addContactIdentity(session, groupMember.identity, groupMember.serializedDetails, ownedIdentity, TrustOrigin.createGroupTrustOrigin(System.currentTimeMillis(), groupInformation.groupOwnerIdentity), false);
                        }
                    } else if (!ownedIdentity.equals(groupInformation.groupOwnerIdentity)) {
                        addTrustOriginToContact(session, groupMember.identity, ownedIdentity, TrustOrigin.createGroupTrustOrigin(System.currentTimeMillis(), groupInformation.groupOwnerIdentity), false);
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
    public void forcefullyRemoveMemberOrPendingFromJoinedGroup(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, Identity contactIdentity) throws SQLException {
        PendingGroupMember pendingGroupMember = PendingGroupMember.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
        if (pendingGroupMember != null) {
            pendingGroupMember.delete();
        }
        ContactGroupMembersJoin contactGroupMembersJoin = ContactGroupMembersJoin.get(wrapSession(session), groupOwnerAndUid, ownedIdentity, contactIdentity);
        if (contactGroupMembersJoin != null) {
            contactGroupMembersJoin.delete();
        }
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
    public JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
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
    public JsonGroupDetailsWithVersionAndPhoto trustPublishedGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException {
        ContactGroup contactGroup = ContactGroup.get(wrapSession(session), groupOwnerAndUid, ownedIdentity);
        if (contactGroup != null) {
            JsonGroupDetailsWithVersionAndPhoto details = contactGroup.trustPublishedDetails();
            session.addSessionCommitListener(backupNeededSessionCommitListener);
            return details;
        }
        return null;
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

    public void createGroupV1ServerUserData(Session session, Identity ownedIdentity, UID photoServerLabel, byte[] groupOwnerAndUid) throws SQLException {
        if (ServerUserData.createForOwnedGroupDetails(wrapSession(session), ownedIdentity, photoServerLabel, groupOwnerAndUid) == null) {
            throw new SQLException();
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
    public byte[][] getGroupOwnerAndUidsOfGroupsContainingContact(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        return ContactGroupMembersJoin.getGroupOwnerAndUidsOfGroupsContainingContact(wrapSession(session), contactIdentity, ownedIdentity);
    }

    @Override
    public void refreshMembersOfGroupsOwnedByGroupOwner(UID currentDeviceUid, Identity groupOwner) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedDevice ownedDevice = OwnedDevice.get(identityManagerSession, currentDeviceUid);
            if (ownedDevice == null || !ownedDevice.isCurrentDevice()) {
                return;
            }
            Identity ownedIdentity = ownedDevice.getOwnedIdentity();
            byte[][] groupOwnerAndUids = ContactGroup.getGroupOwnerAndUidsOfGroupsOwnedByContact(identityManagerSession, ownedIdentity, groupOwner);
            for (byte[] groupOwnerAndUid: groupOwnerAndUids) {
                try {
                    protocolStarterDelegate.queryGroupMembers(groupOwnerAndUid, ownedIdentity);
                } catch (Exception e) {
                    Logger.x(e);
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void pushMembersOfOwnedGroupsToContact(UID currentDeviceUid, Identity contactIdentity) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedDevice ownedDevice = OwnedDevice.get(identityManagerSession, currentDeviceUid);
            if (ownedDevice == null || !ownedDevice.isCurrentDevice()) {
                return;
            }
            Identity ownedIdentity = ownedDevice.getOwnedIdentity();
            {
                byte[][] groupOwnerAndUids = ContactGroup.getGroupOwnerAndUidsOfOwnedGroupsWithContact(identityManagerSession, ownedIdentity, contactIdentity);
                for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                    try {
                        protocolStarterDelegate.reinviteAndPushMembersToContact(groupOwnerAndUid, ownedIdentity, contactIdentity);
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }
            }
            {
                byte[][] groupOwnerAndUids = PendingGroupMember.getGroupOwnerAndUidOfGroupsWhereContactIsPending(identityManagerSession, contactIdentity, ownedIdentity, true);
                for (byte[] groupOwnerAndUid : groupOwnerAndUids) {
                    try {
                        protocolStarterDelegate.reinvitePendingToGroup(groupOwnerAndUid, ownedIdentity, contactIdentity);
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    // endregion





    // region Groups v2

    @Override
    public void createNewGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, String absolutePhotoUrl, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] verifiedAdministratorsChain, GroupV2.BlobKeys blobKeys, byte[] ownGroupInvitationNonce, List<String> ownPermissionStrings, HashSet<GroupV2.IdentityAndPermissionsAndDetails> otherGroupMembers, String serializedGroupType) throws Exception {
        if (!ownPermissionStrings.contains(GroupV2.Permission.GROUP_ADMIN.getString())) {
            Logger.e("Error in createNewContactGroupV2: ownPermissions do not contain GROUP_ADMIN.");
            throw new Exception();
        }
        for (GroupV2.IdentityAndPermissionsAndDetails groupMember: otherGroupMembers) {
            if (!isIdentityAContactOfOwnedIdentity(session, ownedIdentity, groupMember.identity)) {
                Logger.e("Error in createNewContactGroupV2: a groupMember is not a Contact.");
                throw new Exception();
            }
            if (!getContactCapabilities(wrapSession(session), ownedIdentity, groupMember.identity).contains(ObvCapability.GROUPS_V2)) {
                Logger.e("Error in createNewContactGroupV2: a groupMember does not have groupV2 capability.");
                throw new Exception();
            }
        }

        IdentityManagerSession identityManagerSession = wrapSession(session);
        ContactGroupV2 group = ContactGroupV2.createNew(
                identityManagerSession,
                ownedIdentity,
                groupIdentifier,
                serializedGroupDetails,
                absolutePhotoUrl,
                serverPhotoInfo,
                verifiedAdministratorsChain,
                blobKeys,
                ownGroupInvitationNonce,
                ownPermissionStrings,
                serializedGroupType
        );
        if (group == null) {
            throw new Exception("Unable to create ContactGroupV2");
        }
        // if any, add the user data to the ServerUserData
        if (serverPhotoInfo != null) {
            if (ServerUserData.createForGroupV2(identityManagerSession, ownedIdentity, serverPhotoInfo.serverPhotoLabel, groupIdentifier.encode().getBytes()) == null) {
                throw new Exception("Unable to create ServerUserData");
            }
        }

        // add pending group members
        for (GroupV2.IdentityAndPermissionsAndDetails groupMember: otherGroupMembers) {
            ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.create(
                    identityManagerSession,
                    ownedIdentity,
                    groupIdentifier,
                    groupMember.identity,
                    groupMember.serializedIdentityDetails,
                    groupMember.permissionStrings,
                    groupMember.groupInvitationNonce
            );

            if (pendingMember == null) {
                throw new Exception("Unable to create ContactGroupV2PendingMember");
            }
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    @Override
    public boolean createJoinedGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.BlobKeys blobKeys, GroupV2.ServerBlob serverBlob, boolean createdByMeOnOtherDevice) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) || (serverBlob == null)) {
            throw new Exception();
        }

        if (!session.isInTransaction()) {
            throw new SQLException("Called IdentityManager.createJoinedGroupV2 outside of a transaction!");
        }

        IdentityManagerSession identityManagerSession = wrapSession(session);

        if (ContactGroupV2.get(identityManagerSession, ownedIdentity, groupIdentifier) != null) {
            Logger.e("Called IdentityManager.createJoinedGroupV2 for an existing group!");
            return false;
        }

        if (!serverBlob.administratorsChain.integrityWasChecked) {
            Logger.e("In IdentityManager.createJoinedGroupV2, serverBlob.administratorsChain has integrityWasChecked false");
            return false;
        }

        // check I am member of the group
        GroupV2.IdentityAndPermissionsAndDetails ownIdentityAndPermissionsAndDetails = null;
        for (GroupV2.IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
            if (identityAndPermissionsAndDetails.identity.equals(ownedIdentity)) {
                ownIdentityAndPermissionsAndDetails = identityAndPermissionsAndDetails;
                break;
            }
        }
        if (ownIdentityAndPermissionsAndDetails == null) {
            Logger.e("In IdentityManager.createJoinedGroupV2, ownedIdentity not part of the group");
            return false;
        }

        ContactGroupV2 group = ContactGroupV2.createJoined(
                identityManagerSession,
                ownedIdentity,
                groupIdentifier,
                serverBlob.version,
                serverBlob.serializedGroupDetails,
                serverBlob.serverPhotoInfo,
                serverBlob.administratorsChain.encode().getBytes(),
                blobKeys,
                ownIdentityAndPermissionsAndDetails.groupInvitationNonce,
                ownIdentityAndPermissionsAndDetails.permissionStrings,
                serverBlob.serializedGroupType,
                createdByMeOnOtherDevice
        );
        if (group == null) {
            throw new Exception("Unable to create joined ContactGroupV2");
        }
        for (GroupV2.IdentityAndPermissionsAndDetails groupMember : serverBlob.groupMemberIdentityAndPermissionsAndDetailsList) {
            if (groupMember.identity.equals(ownedIdentity)) {
                continue;
            }

            ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.create(
                    identityManagerSession,
                    ownedIdentity,
                    groupIdentifier,
                    groupMember.identity,
                    groupMember.serializedIdentityDetails,
                    groupMember.permissionStrings,
                    groupMember.groupInvitationNonce
            );

            if (pendingMember == null) {
                throw new Exception("Unable to create ContactGroupV2PendingMember");
            }
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);
        return true;
    }

    @Override
    public GroupV2.ServerBlob getGroupV2ServerBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return null;
        }

        return ContactGroupV2.getServerBlob(wrapSession(session), ownedIdentity, groupIdentifier);
    }

    @Override
    public String getGroupV2PhotoUrl(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }

        return ContactGroupV2.getPhotoUrl(wrapSession(session), ownedIdentity, groupIdentifier);
    }

    @Override
    public void deleteGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if (groupIdentifier == null) {
            return;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 != null) {
            groupV2.delete();
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    @Override
    public void freezeGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 != null) {
            groupV2.setFrozen(true);
        }
    }

    @Override
    public void unfreezeGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 != null) {
            groupV2.setFrozen(false);
        }
    }

    @Override
    public Integer getGroupV2Version(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }
        return groupV2.getVersion();
    }

    @Override
    public String getGroupV2JsonGroupType(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }
        return groupV2.getSerializedJsonGroupType();
    }

    @Override
    public boolean isGroupV2Frozen(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return false;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return false;
        }
        return groupV2.isFrozen();

    }

    @Override
    public GroupV2.BlobKeys getGroupV2BlobKeys(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }
        return new GroupV2.BlobKeys(groupV2.getBlobMainSeed(), groupV2.getBlobVersionSeed(), groupV2.getGroupAdminServerAuthenticationPrivateKey());
    }

    @Override
    public HashSet<GroupV2.IdentityAndPermissions> getGroupV2OtherMembersAndPermissions(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        return ContactGroupV2.getGroupV2OtherMembersAndPermissions(wrapSession(session), ownedIdentity, groupIdentifier);
    }

    public boolean getGroupV2HasOtherAdminMember(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            throw new Exception();
        }
        return ContactGroupV2.getGroupV2HasOtherAdminMember(wrapSession(session), ownedIdentity, groupIdentifier);
    }

    @Override
    public List<Identity> updateGroupV2WithNewBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerBlob serverBlob, GroupV2.BlobKeys blobKeys, boolean updatedByMe) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) || (serverBlob == null) || (blobKeys == null)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);

        return groupV2.updateWithNewBlob(serverBlob, blobKeys, updatedByMe);
    }

    @Override
    public List<Identity> getGroupV2MembersAndPendingMembersFromNonce(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, byte[] groupMemberInvitationNonce) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupMemberInvitationNonce == null)) {
            return null;
        }
        return ContactGroupV2.getGroupV2MembersAndPendingMembersFromNonce(wrapSession(session), ownedIdentity, groupIdentifier, groupMemberInvitationNonce);
    }

    @Override
    public byte[] getGroupV2OwnGroupInvitationNonce(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }

        return groupV2.getOwnGroupInvitationNonce();
    }

    @Override
    public void moveGroupV2PendingMemberToMembers(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity groupMemberIdentity) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupMemberIdentity == null)) {
            return;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return;
        }

        groupV2.movePendingMemberToMembers(groupMemberIdentity);

        session.addSessionCommitListener(backupNeededSessionCommitListener);
    }

    @Override
    public void setGroupV2DownloadedPhoto(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] photo) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (serverPhotoInfo == null) || (photo == null)) {
            return;
        }

        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return;
        }

        groupV2.setDownloadedPhotoUrl(ownedIdentity, serverPhotoInfo, photo);
    }

    @Override
    public ObvGroupV2 getObvGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }

        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }

        return groupV2toObvGroupV2(wrapSession(session), ownedIdentity, groupIdentifier, groupV2);
    }

    private static ObvGroupV2 groupV2toObvGroupV2(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, ContactGroupV2 groupV2) throws Exception {
        HashSet<ObvGroupV2.ObvGroupV2Member> otherGroupMembers = new HashSet<>();
        List<ContactGroupV2Member> members = ContactGroupV2Member.getAll(identityManagerSession, ownedIdentity, groupIdentifier);
        for (ContactGroupV2Member member : members) {
            otherGroupMembers.add(new ObvGroupV2.ObvGroupV2Member(
                    member.getContactIdentity().getBytes(),
                    GroupV2.Permission.deserializeKnownPermissions(member.getSerializedPermissions())
            ));
        }

        HashSet<ObvGroupV2.ObvGroupV2PendingMember> pendingGroupMembers = new HashSet<>();
        List<ContactGroupV2PendingMember> pendingMembers = ContactGroupV2PendingMember.getAll(identityManagerSession, ownedIdentity, groupIdentifier);
        for (ContactGroupV2PendingMember pendingMember : pendingMembers) {
            pendingGroupMembers.add(new ObvGroupV2.ObvGroupV2PendingMember(
                    pendingMember.getContactIdentity().getBytes(),
                    GroupV2.Permission.deserializeKnownPermissions(pendingMember.getSerializedPermissions()),
                    pendingMember.getSerializedContactDetails()
            ));
        }

        ContactGroupV2Details trustedDetails = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, groupIdentifier, groupV2.getTrustedDetailsVersion());
        if (trustedDetails == null) {
            return null;
        }

        String serializedGroupDetails = trustedDetails.getSerializedJsonDetails();
        String photoUrl = trustedDetails.getPhotoUrl();
        if (photoUrl == null && trustedDetails.getServerPhotoInfo() != null) { // photo not downloaded yet
            photoUrl = "";
        }

        String serializedPublishedDetails;
        String publishedPhotoUrl;
        if (groupV2.getVersion() != groupV2.getTrustedDetailsVersion()) {
            ContactGroupV2Details publishedDetails = ContactGroupV2Details.get(identityManagerSession, ownedIdentity, groupIdentifier, groupV2.getVersion());
            if (publishedDetails == null) {
                return null;
            }
            serializedPublishedDetails = publishedDetails.getSerializedJsonDetails();
            publishedPhotoUrl = publishedDetails.getPhotoUrl();
            if (publishedPhotoUrl == null && publishedDetails.getServerPhotoInfo() != null) { // photo not downloaded yet
                publishedPhotoUrl = "";
            }
        } else {
            serializedPublishedDetails = null;
            publishedPhotoUrl = null;
        }


        return new ObvGroupV2(
                ownedIdentity.getBytes(),
                groupIdentifier,
                GroupV2.Permission.fromStrings(groupV2.getOwnPermissionStrings()),
                otherGroupMembers,
                pendingGroupMembers,
                serializedGroupDetails,
                photoUrl,
                serializedPublishedDetails,
                publishedPhotoUrl
        );
    }

    @Override
    public int trustGroupV2PublishedDetails(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return -1;
        }

        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return -1;
        }
        int trustedVersion = groupV2.getTrustedDetailsVersion();
        if (trustedVersion != groupV2.getVersion()) {
            groupV2.setTrustedDetailsVersion(groupV2.getVersion());
            ContactGroupV2Details.cleanup(wrapSession(session), ownedIdentity, groupIdentifier, groupV2.getVersion(), groupV2.getVersion());
        }
        session.addSessionCommitListener(backupNeededSessionCommitListener);
        return groupV2.getVersion();
    }

    // only for CATEGORY_SERVER groups. This is only used for UserData management
    @Override
    public GroupV2.ServerPhotoInfo getGroupV2PublishedServerPhotoInfo(Session session, Identity ownedIdentity, byte[] bytesGroupIdentifier) {
        if ((ownedIdentity == null) || (bytesGroupIdentifier == null)) {
            return null;
        }
        try {
            GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);
            if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
                return null;
            }
            return ContactGroupV2.getServerPhotoInfo(wrapSession(session), ownedIdentity, groupIdentifier);
        } catch (Exception e) {
            Logger.x(e);
        }
        return null;
    }

    @Override
    public ObvGroupV2.ObvGroupV2DetailsAndPhotos getGroupV2DetailsAndPhotos(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        try {
            ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
            if (groupV2 == null) {
                return null;
            }

            ContactGroupV2Details trustedDetails = ContactGroupV2Details.get(wrapSession(session), ownedIdentity, groupIdentifier, groupV2.getTrustedDetailsVersion());
            if (trustedDetails == null) {
                return null;
            }

            String serializedGroupDetails = trustedDetails.getSerializedJsonDetails();
            String photoUrl = trustedDetails.getPhotoUrl();
            if (photoUrl == null && trustedDetails.getServerPhotoInfo() != null) { // photo not downloaded yet
                photoUrl = "";
            }

            String serializedPublishedDetails;
            String publishedPhotoUrl;
            if (groupV2.getVersion() != groupV2.getTrustedDetailsVersion()) {
                ContactGroupV2Details publishedDetails = ContactGroupV2Details.get(wrapSession(session), ownedIdentity, groupIdentifier, groupV2.getVersion());
                if (publishedDetails == null) {
                    return null;
                }
                serializedPublishedDetails = publishedDetails.getSerializedJsonDetails();
                publishedPhotoUrl = publishedDetails.getPhotoUrl();
                if (publishedPhotoUrl == null && publishedDetails.getServerPhotoInfo() != null) { // photo not downloaded yet
                    publishedPhotoUrl = "";
                }
            } else {
                serializedPublishedDetails = null;
                publishedPhotoUrl = null;
            }

            return new ObvGroupV2.ObvGroupV2DetailsAndPhotos(serializedGroupDetails, photoUrl, serializedPublishedDetails, publishedPhotoUrl);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public void setUpdatedGroupV2PhotoUrl(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, String absolutePhotoUrl) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (absolutePhotoUrl == null)) {
            return;
        }

        ContactGroupV2Details details = ContactGroupV2Details.get(wrapSession(session), ownedIdentity, groupIdentifier, version);
        if (details == null) {
            return;
        }

        details.setAbsolutePhotoUrl(absolutePhotoUrl);
    }

    @Override
    public GroupV2.AdministratorsChain getGroupV2AdministratorsChain(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return null;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }

        byte[] serializedAdministratorsChain = groupV2.getVerifiedAdministratorsChain();

        return GroupV2.AdministratorsChain.of(new Encoded(serializedAdministratorsChain));
    }

    @Override
    public boolean getGroupV2AdminStatus(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if ((ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK)) {
            return false;
        }
        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return false;
        }

        return groupV2.getOwnPermissionStrings().contains(GroupV2.Permission.GROUP_ADMIN.getString());
    }

    @Override
    public List<ObvGroupV2> getObvGroupsV2ForOwnedIdentity(Session session, Identity ownedIdentity) throws Exception {
        if (ownedIdentity == null) {
            throw new Exception();
        }

        IdentityManagerSession identityManagerSession = wrapSession(session);
        List<ContactGroupV2> groupsV2 = ContactGroupV2.getAllForIdentity(identityManagerSession, ownedIdentity);

        List<ObvGroupV2> obvGroupsV2 = new ArrayList<>();
        for (ContactGroupV2 groupV2 : groupsV2) {
            ObvGroupV2 obvGroupV2 = groupV2toObvGroupV2(identityManagerSession, ownedIdentity, groupV2.getGroupIdentifier(), groupV2);
            if (obvGroupV2 != null) {
                obvGroupsV2.add(obvGroupV2);
            }
        }
        return obvGroupsV2;
    }

    @Override
    public GroupV2.IdentifierVersionAndKeys[] getServerGroupsV2IdentifierVersionAndKeysForContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (ownedIdentity == null || contactIdentity == null) {
            throw new Exception();
        }

        return ContactGroupV2.getServerGroupsV2IdentifierVersionAndKeysForContact(wrapSession(session), ownedIdentity, contactIdentity);
    }


    @Override
    public GroupV2.IdentifierVersionAndKeys[] getAllServerGroupsV2IdentifierVersionAndKeys(Session session, Identity ownedIdentity) throws Exception {
        if (ownedIdentity == null) {
            throw new Exception();
        }

        return ContactGroupV2.getAllServerGroupsV2IdentifierVersionAndKeys(wrapSession(session), ownedIdentity);
    }


    @Override
    public GroupV2.IdentifierAndAdminStatus[] getServerGroupsV2IdentifierAndMyAdminStatusForContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (ownedIdentity == null || contactIdentity == null) {
            throw new Exception();
        }

        return ContactGroupV2.getServerGroupsV2IdentifierAndMyAdminStatusForContact(wrapSession(session), ownedIdentity, contactIdentity);
    }


        @Override
    public void initiateGroupV2BatchKeysResend(UID currentDeviceUid, Identity contactIdentity, UID contactDeviceUid) {
        if (contactIdentity == null || contactDeviceUid == null) {
            return;
        }

        try (IdentityManagerSession identityManagerSession = getSession()) {
            Identity ownedIdentity = getOwnedIdentityForCurrentDeviceUid(identityManagerSession.session, currentDeviceUid);
            if (ownedIdentity == null) {
                return;
            }

            try {
                protocolStarterDelegate.initiateGroupV2BatchKeysResend(identityManagerSession.session, ownedIdentity, contactIdentity, contactDeviceUid);
                identityManagerSession.session.commit();
            } catch (Exception e) {
                Logger.x(e);
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void forcefullyRemoveMemberOrPendingFromNonAdminGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity contactIdentity) throws SQLException {
        ContactGroupV2 contactGroupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (contactGroupV2 != null) {
            if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
                moveKeycloakMemberToPendingMember(wrapSession(session), groupIdentifier, ownedIdentity, contactIdentity, null);
            } else {
                contactGroupV2.triggerUpdateNotification();
                ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.get(wrapSession(session), ownedIdentity, groupIdentifier, contactIdentity);
                if (pendingMember != null) {
                    pendingMember.delete();
                }
                ContactGroupV2Member member = ContactGroupV2Member.get(wrapSession(session), ownedIdentity, groupIdentifier, contactIdentity);
                if (member != null) {
                    member.delete();
                }
            }
        }
    }

    @Override
    public Long getGroupV2LastModificationTimestamp(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException {
        if ((ownedIdentity == null) || (groupIdentifier == null)) {
            return null;
        }
        return ContactGroupV2.getLastModificationTimestamp(wrapSession(session), ownedIdentity, groupIdentifier);
    }

    @Override
    public byte[] createKeycloakGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, KeycloakGroupBlob keycloakGroupBlob) {
        if (ownedIdentity == null || groupIdentifier == null || groupIdentifier.category != GroupV2.Identifier.CATEGORY_KEYCLOAK || keycloakGroupBlob == null) {
            return null;
        }

        try {
            IdentityManagerSession identityManagerSession = wrapSession(session);

            // first, find my own permissions and invitation nonce in the groupMembersAndPermissions set
            byte[] ownInvitationNonce = null;
            List<String> ownPermissions = null;
            List<KeycloakGroupMemberAndPermissions> otherMembers = new ArrayList<>();

            for (KeycloakGroupMemberAndPermissions groupMemberAndPermissions : keycloakGroupBlob.groupMembersAndPermissions) {
                if (Arrays.equals(ownedIdentity.getBytes(), groupMemberAndPermissions.identity)) {
                    ownInvitationNonce = groupMemberAndPermissions.groupInvitationNonce;
                    ownPermissions = groupMemberAndPermissions.permissions;
                } else {
                    otherMembers.add(groupMemberAndPermissions);
                }
            }


            GroupV2.ServerPhotoInfo serverPhotoInfo = null;
            if (keycloakGroupBlob.photoUid != null && keycloakGroupBlob.encodedPhotoKey != null) {
                try {
                    UID photoUid = new UID(keycloakGroupBlob.photoUid);
                    AuthEncKey photoKey = (AuthEncKey) new Encoded(keycloakGroupBlob.encodedPhotoKey).decodeSymmetricKey();
                    serverPhotoInfo = new GroupV2.ServerPhotoInfo(null, photoUid, photoKey);
                } catch (Exception e) {
                    Logger.x(e);
                }
            }

            ContactGroupV2 groupV2 = ContactGroupV2.createKeycloak(
                    identityManagerSession,
                    ownedIdentity,
                    groupIdentifier,
                    jsonObjectMapper.writeValueAsString(keycloakGroupBlob.groupDetails),
                    serverPhotoInfo,
                    ownInvitationNonce,
                    ownPermissions,
                    keycloakGroupBlob.pushTopic,
                    keycloakGroupBlob.serializedSharedSettings,
                    keycloakGroupBlob.timestamp
            );

            if (groupV2 == null) {
                throw new Exception("Called createKeycloakGroupV2 and group already exists");
            }

            JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                    .setSkipSignatureVerification()
                    .setSkipAllValidators()
                    .build();

            for (KeycloakGroupMemberAndPermissions groupMemberAndPermissions: otherMembers) {
                try {
                    // the signedUserDetails contained in the KeycloakGroupMemberAndPermissions are a JWT, containing the "raw" details
                    // we deserialize these raw details and enrich them with the signed details
                    String serializedUnsignedDetails = noVerificationConsumer.processToClaims(groupMemberAndPermissions.signedUserDetails).getRawJson();
                    JsonKeycloakUserDetails jsonKeycloakUserDetails = jsonObjectMapper.readValue(serializedUnsignedDetails, JsonKeycloakUserDetails.class);
                    JsonIdentityDetails jsonIdentityDetails = jsonKeycloakUserDetails.getIdentityDetails(groupMemberAndPermissions.signedUserDetails);
                    String serializedIdentityDetails = jsonObjectMapper.writeValueAsString(jsonIdentityDetails);

                    Identity groupMemberIdentity = Identity.of(groupMemberAndPermissions.identity);

                    ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.create(
                            identityManagerSession,
                            ownedIdentity,
                            groupIdentifier,
                            groupMemberIdentity,
                            serializedIdentityDetails,
                            groupMemberAndPermissions.permissions,
                            groupMemberAndPermissions.groupInvitationNonce
                    );

                    if (pendingMember == null) {
                        throw new Exception("Unable to create ContactGroupV2PendingMember");
                    }
                } catch (InvalidJwtException | JsonProcessingException e) {
                    Logger.w("Unable to process one keycloak group member --> skipping them");
                    Logger.x(e);
                }
            }

            if (keycloakGroupBlob.serializedSharedSettings != null) {
                session.addSessionCommitListener(() -> {
                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY, ownedIdentity);
                    userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_GROUP_IDENTIFIER_KEY, groupIdentifier);
                    userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SERIALIZED_SHARED_SETTINGS_KEY, keycloakGroupBlob.serializedSharedSettings);
                    userInfo.put(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY, keycloakGroupBlob.timestamp);
                    notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS, userInfo);
                });
            }

            session.addSessionCommitListener(backupNeededSessionCommitListener);
            return ownInvitationNonce;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public KeycloakGroupV2UpdateOutput updateKeycloakGroupV2WithNewBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, KeycloakGroupBlob keycloakGroupBlob) throws Exception {
        if ((session == null) || (ownedIdentity == null) || (groupIdentifier == null) || (groupIdentifier.category != GroupV2.Identifier.CATEGORY_KEYCLOAK) || (keycloakGroupBlob == null)) {
            return null;
        }

        if (!session.isInTransaction()) {
            throw new SQLException("Calling updateKeycloakGroupV2WithNewBlob outside a transaction!");
        }

        ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
        if (groupV2 == null) {
            return null;
        }

        session.addSessionCommitListener(backupNeededSessionCommitListener);
        return groupV2.updateWithNewKeycloakBlob(keycloakGroupBlob, jsonObjectMapper);
    }

    @Override
    public void rePingOrDemoteContactFromAllKeycloakGroups(Session session, Identity ownedIdentity, Identity contactIdentity, boolean certifiedByOwnKeycloak, String lastKnownSerializedCertifiedDetails) throws SQLException {
        if ((session == null) || (ownedIdentity == null) || (contactIdentity == null)) {
            return;
        }
        IdentityManagerSession identityManagerSession = wrapSession(session);

        if (certifiedByOwnKeycloak) {
            List<GroupV2.Identifier> groupIdentifiers = ContactGroupV2PendingMember.getKeycloakGroupV2IdentifiersWhereContactIsPending(identityManagerSession, ownedIdentity, contactIdentity);
            if (groupIdentifiers != null) {
                for (GroupV2.Identifier groupIdentifier : groupIdentifiers) {
                    try {
                        protocolStarterDelegate.initiateKeycloakGroupV2TargetedPing(session, ownedIdentity, groupIdentifier, contactIdentity);
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }
            }
        } else {
            List<GroupV2.Identifier> groupIdentifiers = ContactGroupV2Member.getKeycloakGroupV2IdentifiersWhereContactIsMember(identityManagerSession, ownedIdentity, contactIdentity);
            if (groupIdentifiers != null) {
                for (GroupV2.Identifier groupIdentifier : groupIdentifiers) {
                    try {
                        moveKeycloakMemberToPendingMember(identityManagerSession, groupIdentifier, ownedIdentity, contactIdentity, lastKnownSerializedCertifiedDetails);
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }
            }
        }
    }

    private void moveKeycloakMemberToPendingMember(IdentityManagerSession identityManagerSession, GroupV2.Identifier groupIdentifier, Identity ownedIdentity, Identity groupMemberIdentity, String lastKnownSerializedCertifiedDetails) throws SQLException {
        if (groupIdentifier.category != GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            return;
        }

        ContactGroupV2Member member = ContactGroupV2Member.get(identityManagerSession, ownedIdentity, groupIdentifier, groupMemberIdentity);
        String serializedPublishedDetails = lastKnownSerializedCertifiedDetails == null ? ContactIdentity.getSerializedPublishedDetails(identityManagerSession, ownedIdentity, groupMemberIdentity) : lastKnownSerializedCertifiedDetails;
        if (member == null || serializedPublishedDetails == null) {
            return;
        }

        ContactGroupV2PendingMember pendingMember = ContactGroupV2PendingMember.get(identityManagerSession, ownedIdentity, groupIdentifier, groupMemberIdentity);
        if (pendingMember == null) { // this should always be the case
            // crate the ContactGroupV2PendingMember
            pendingMember = ContactGroupV2PendingMember.create(identityManagerSession, ownedIdentity, groupIdentifier, groupMemberIdentity, serializedPublishedDetails, GroupV2.Permission.deserializePermissions(member.getSerializedPermissions()), member.getGroupInvitationNonce());

            if (pendingMember == null) {
                throw new SQLException("In IdentityManager.moveKeycloakMemberToPendingMember, failed to create ContactGroupV2PendingMember");
            }
        }

        // delete the member
        member.delete();

        identityManagerSession.session.addSessionCommitListener(() -> {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_GROUP_IDENTIFIER_KEY, groupIdentifier);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_BY_ME_KEY, false);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED, userInfo);
        });
    }

    // return true only if the identity is a PendingMember: if it is a GroupMember, it returns false
    @Override
    public boolean isIdentityAPendingGroupV2Member(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity identity) throws SQLException {
        if (session == null || ownedIdentity == null || groupIdentifier == null || identity == null) {
            return false;
        }

       return ContactGroupV2PendingMember.get(wrapSession(session), ownedIdentity, groupIdentifier, identity) != null;
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
                Logger.x(e);
                backupDelegate.backupFailed(tag, backupKeyUid, version);
            }
        }, "Identity Backup").start();
    }

    @Override
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String serializedJsonPojo, String deviceDisplayName, PRNGService prng) {
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
                restoredIdentities.add(OwnedIdentity.restore(identityManagerSession, ownedIdentityPojo, deviceDisplayName, prng));
            }
            identityManagerSession.session.commit();

            return restoredIdentities.toArray(new ObvIdentity[0]);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public void restoreContactsAndGroupsFromBackup(String serializedJsonPojo, ObvIdentity[] restoredOwnedIdentities, long backupTimestamp) {
        Set<Identity> restoredIdentities = new HashSet<>();
        for (ObvIdentity obvOwnedIdentity : restoredOwnedIdentities) {
            restoredIdentities.add(obvOwnedIdentity.getIdentity());
        }

        try (IdentityManagerSession identityManagerSession = getSession()) {
            OwnedIdentity.Pojo_0[] ownedIdentityPojos = jsonObjectMapper.readValue(serializedJsonPojo, new TypeReference<>() {});

            for (OwnedIdentity.Pojo_0 ownedIdentityPojo : ownedIdentityPojos) {
                Identity ownedIdentity = Identity.of(ownedIdentityPojo.owned_identity);
                if (!restoredIdentities.contains(ownedIdentity)) {
                    continue;
                }

                ContactIdentity.restoreAll(identityManagerSession, ownedIdentity, ownedIdentityPojo.contact_identities, backupTimestamp);
                ContactGroup.restoreAllForOwner(identityManagerSession, ownedIdentity, ownedIdentity, ownedIdentityPojo.owned_groups, backupTimestamp);
                ContactGroupV2.restoreAll(identityManagerSession, protocolStarterDelegate, ownedIdentity, ownedIdentityPojo.groups_v2);
            }


            for (ObvIdentity obvOwnedIdentity : restoredOwnedIdentities) {
                if (obvOwnedIdentity.isActive()) {
                    reactivateOwnedIdentityIfNeeded(identityManagerSession.session, obvOwnedIdentity.getIdentity());
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }
    // endregion

    // region userData

    @Override
    public UserData[] getAllUserData(Session session) throws Exception {
        ServerUserData[] serverUserData = ServerUserData.getAll(wrapSession(session));
        UserData[] userData = new UserData[serverUserData.length];
        for (int i=0; i<serverUserData.length; i++) {
            userData[i] = serverUserData[i].getUserData();
        }
        return userData;
    }

    @Override
    public UserData getUserData(Session session, Identity ownedIdentity, UID label) throws Exception {
        ServerUserData serverUserData = ServerUserData.get(wrapSession(session), ownedIdentity, label);
        if (serverUserData != null) {
            return serverUserData.getUserData();
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

    // region Device sync

    @Override
    public void processSyncItem(Session session, Identity ownedIdentity, ObvSyncAtom obvSyncAtom) throws Exception {
        switch (obvSyncAtom.syncType) {
            case ObvSyncAtom.TYPE_TRUST_CONTACT_DETAILS: {
                try {
                    JsonIdentityDetailsWithVersionAndPhoto atomDetails = jsonObjectMapper.readValue(obvSyncAtom.getStringValue(), JsonIdentityDetailsWithVersionAndPhoto.class);
                    JsonIdentityDetailsWithVersionAndPhoto[] dbDetails = getContactPublishedAndTrustedDetails(session, ownedIdentity, obvSyncAtom.getContactIdentity());
                    // check if there are indeed details to trust
                    if (dbDetails.length == 2) {
                        // check that the published details actually match those we received
                        if (Objects.equals(dbDetails[0].getPhotoServerKey() == null ? null : new Encoded(dbDetails[0].getPhotoServerKey()).decodeSymmetricKey(),
                                atomDetails.getPhotoServerKey() == null ? null : new Encoded(atomDetails.getPhotoServerKey()).decodeSymmetricKey())
                                && Arrays.equals(dbDetails[0].getPhotoServerLabel(), atomDetails.getPhotoServerLabel())
                                && Objects.equals(dbDetails[0].getIdentityDetails(), atomDetails.getIdentityDetails())) {
                            trustPublishedContactDetails(session, obvSyncAtom.getContactIdentity(), ownedIdentity);
                        }
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            }
            case ObvSyncAtom.TYPE_TRUST_GROUP_V1_DETAILS: {
                try {
                    JsonGroupDetailsWithVersionAndPhoto atomDetails = jsonObjectMapper.readValue(obvSyncAtom.getStringValue(), JsonGroupDetailsWithVersionAndPhoto.class);
                    JsonGroupDetailsWithVersionAndPhoto[] dbDetails = getGroupPublishedAndLatestOrTrustedDetails(session, ownedIdentity, obvSyncAtom.getBytesGroupOwnerAndUid());
                    // check if there are indeed details to trust
                    if (dbDetails.length == 2) {
                        // check that the published details actually match those we received
                        if (Objects.equals(dbDetails[0].getPhotoServerKey() == null ? null : new Encoded(dbDetails[0].getPhotoServerKey()).decodeSymmetricKey(),
                                atomDetails.getPhotoServerKey() == null ? null : new Encoded(atomDetails.getPhotoServerKey()).decodeSymmetricKey())
                                && Arrays.equals(dbDetails[0].getPhotoServerLabel(), atomDetails.getPhotoServerLabel())
                                && Objects.equals(dbDetails[0].getGroupDetails(), atomDetails.getGroupDetails())) {
                            trustPublishedGroupDetails(session, ownedIdentity, obvSyncAtom.getBytesGroupOwnerAndUid());
                        }
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            }
            case ObvSyncAtom.TYPE_TRUST_GROUP_V2_DETAILS: {
                try {
                    int version = obvSyncAtom.getIntegerValue();
                    GroupV2.Identifier groupIdentifier = obvSyncAtom.getGroupIdentifier();
                    ContactGroupV2 groupV2 = ContactGroupV2.get(wrapSession(session), ownedIdentity, groupIdentifier);
                    // check if there are indeed details to trust matching the version
                    if (groupV2 != null && groupV2.getVersion() != groupV2.getTrustedDetailsVersion() && groupV2.getVersion() == version) {
                        trustGroupV2PublishedDetails(session, ownedIdentity, groupIdentifier);
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            }
            default: {
                throw new Exception("Unknown Identity Manager sync atom type");
            }
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

    @Override
    public byte[] decrypt(Session session, EncryptedBytes ciphertext, Identity toIdentity) throws SQLException {
        try {
            OwnedIdentity ownedIdentity = OwnedIdentity.get(wrapSession(session), toIdentity);
            if (ownedIdentity == null) {
                return null;
            }
            PrivateIdentity privateIdentity = ownedIdentity.getPrivateIdentity();
            PublicKeyEncryption pubEnc = Suite.getPublicKeyEncryption(privateIdentity.getEncryptionPublicKey());
            return pubEnc.decrypt(privateIdentity.getEncryptionPrivateKey(), ciphertext);
        } catch (DecryptionException | InvalidKeyException e) {
            return null;
        }
    }

    // endregion




    // region implement PreKeyEncryptionDelegate

    @Override
    public EncryptedBytes wrapWithPreKey(Session session, AuthEncKey messageKey, Identity ownedIdentity, Identity remoteIdentity, UID remoteDeviceUid, PRNGService prng) {
        try {
            OwnedIdentity ownedIdentityObject = OwnedIdentity.get(wrapSession(session), ownedIdentity);
            if (ownedIdentityObject == null) {
                Logger.w("In wrapWithPreKey(), unknown OwnedIdentity");
                return null;
            }

            // find the PreKey to use for encryption
            final PreKey preKey;
            if (ownedIdentity.equals(remoteIdentity)) {
                OwnedDevice ownedDevice = OwnedDevice.get(wrapSession(session), remoteDeviceUid);
                if (ownedDevice == null || !Objects.equals(ownedDevice.getOwnedIdentity(), ownedIdentity)) {
                    Logger.w("In wrapWithPreKey(), unable to find the correct ownedDevice");
                    return null;
                }
                preKey = ownedDevice.getPreKey();
            } else {
                ContactDevice contactDevice = ContactDevice.get(wrapSession(session), remoteDeviceUid, remoteIdentity, ownedIdentity);
                if (contactDevice == null) {
                    Logger.w("In wrapWithPreKey(), unable to find the correct contactDevice");
                    return null;
                }
                preKey = contactDevice.getPreKey();
            }

            if (preKey == null) {
                Logger.w("In wrapWithPreKey(), remote device does not have a preKey");
                return null;
            }

            // build the message payload
            UID currentDeviceUid = getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
            Encoded encodedPayload = Encoded.of(new Encoded[]{
                    Encoded.of(messageKey),
                    Encoded.of(currentDeviceUid),
                    Encoded.of(ownedIdentity),
            });


            // compute the signature
            Encoded signaturePayload = Encoded.of(new Encoded[]{
                    encodedPayload,
                    Encoded.of(remoteIdentity),
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(preKey.keyId.getBytes()),
            });


            byte[] signature = Signature.sign(Constants.SignatureContext.ENCRYPTION_WITH_PRE_KEY, signaturePayload.getBytes(), ownedIdentityObject.getPrivateIdentity().getServerAuthenticationPrivateKey().getSignaturePrivateKey(), prng);
            if (signature == null) {
                Logger.w("In wrapWithPreKey(), unable to compute signature?!");
                return null;
            }

            // encrypt the signed payload
            Encoded encodedPlaintext = Encoded.of(new Encoded[]{
                    encodedPayload,
                    Encoded.of(signature),
            });
            EncryptedBytes encryptedBytes = Suite.getPublicKeyEncryption(preKey.encryptionPublicKey).encrypt(preKey.encryptionPublicKey, encodedPlaintext.getBytes(), prng);
            byte[] outputBytes = new byte[KeyId.KEYID_LENGTH + encryptedBytes.length];
            System.arraycopy(preKey.keyId.getBytes(), 0, outputBytes, 0, KeyId.KEYID_LENGTH);
            System.arraycopy(encryptedBytes.getBytes(), 0, outputBytes, KeyId.KEYID_LENGTH, encryptedBytes.length);

            return new EncryptedBytes(outputBytes);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    @Override
    public AuthEncKeyAndChannelInfo unwrapWithPreKey(Session session, EncryptedBytes wrappedKey, Identity ownedIdentity) throws SQLException {
        try {
            if (wrappedKey.length < KeyId.KEYID_LENGTH) {
                return null;
            }
            KeyId keyId = new KeyId(Arrays.copyOfRange(wrappedKey.getBytes(), 0, KeyId.KEYID_LENGTH));
            OwnedPreKey ownedPreKey = OwnedPreKey.get(wrapSession(session), ownedIdentity, keyId);
            if (ownedPreKey == null) {
                return null;
            }

            byte[] plaintextBytes;
            try {
                plaintextBytes = Suite.getPublicKeyEncryption(ownedPreKey.getEncryptionPrivateKey()).decrypt(ownedPreKey.getEncryptionPrivateKey(), new EncryptedBytes(Arrays.copyOfRange(wrappedKey.getBytes(), KeyId.KEYID_LENGTH, wrappedKey.length)));
            } catch (InvalidKeyException | DecryptionException ignored) {
                return null;
            }
            Encoded[] encodeds = new Encoded(plaintextBytes).decodeList();

            Encoded[] payloadEncodeds = encodeds[0].decodeList();
            byte[] signature = encodeds[1].decodeBytes();

            AuthEncKey messageKey = (AuthEncKey) payloadEncodeds[0].decodeSymmetricKey();
            UID remoteDeviceUid = payloadEncodeds[1].decodeUid();
            Identity remoteIdentity = payloadEncodeds[2].decodeIdentity();

            UID currentDeviceUid = getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
            Encoded signatureEncoded = Encoded.of(new Encoded[]{
                    encodeds[0],
                    Encoded.of(ownedIdentity),
                    Encoded.of(currentDeviceUid),
                    Encoded.of(keyId.getBytes()),
            });

            if (!Signature.verify(Constants.SignatureContext.ENCRYPTION_WITH_PRE_KEY, signatureEncoded.getBytes(), remoteIdentity, signature)) {
                Logger.w("PreKey wrapped messageKey signature verification failed!");
                return null;
            }
            return new AuthEncKeyAndChannelInfo(messageKey, ReceptionChannelInfo.createPreKeyChannelInfo(remoteDeviceUid, remoteIdentity));
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    // endregion




    // region implement ObvBackupAndSyncDelegate

    @Override
    public String getTag() {
        return "identity";
    }

    @Override
    public ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity) {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            try {
                // start a transaction to be sure the db is not modified while the snapshot is being computed!
                identityManagerSession.session.startTransaction();
                return getSyncSnapshotWithinTransaction(identityManagerSession, ownedIdentity);
            } catch (Exception e) {
                Logger.x(e);
                return null;
            } finally {
                // always rollback as the snapshot creation should never modify the DB.
                identityManagerSession.session.rollback();
            }
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }
    
    private ObvSyncSnapshotNode getSyncSnapshotWithinTransaction(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws Exception {
        if (!identityManagerSession.session.isInTransaction()) {
            Logger.e("ERROR: called IdentityManager.getSyncSnapshot outside a transaction!");
            throw new Exception();
        }
        return IdentityManagerSyncSnapshot.of(identityManagerSession, ownedIdentity);
    }


    @Override
    public RestoreFinishedCallback restoreOwnedIdentity(ObvIdentity ownedIdentity, ObvSyncSnapshotNode node) throws Exception {
        // this method does not do anything for the IdentityManager: the ownedIdentity has already been restored before calling this
        return null;
    }

    @Override
    public RestoreFinishedCallback restoreSyncSnapshot(ObvSyncSnapshotNode node) throws Exception {
        try (IdentityManagerSession identityManagerSession = getSession()) {
            boolean transactionSuccessful = false;
            try {
                // start a transaction to be sure the db is not modified while the snapshot is being computed!
                identityManagerSession.session.startTransaction();
                RestoreFinishedCallback callback = restoreSyncSnapshotWithinTransaction(identityManagerSession, node);
                transactionSuccessful = true;
                return callback;
            } catch (Exception e) {
                Logger.x(e);
            } finally {
                if (transactionSuccessful) {
                    identityManagerSession.session.commit();
                } else {
                    identityManagerSession.session.rollback();
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
        return null;
    }

    private RestoreFinishedCallback restoreSyncSnapshotWithinTransaction(IdentityManagerSession identityManagerSession, ObvSyncSnapshotNode node) throws Exception {
        if (!(node instanceof IdentityManagerSyncSnapshot)) {
            throw new Exception();
        }
        ((IdentityManagerSyncSnapshot) node).restore(identityManagerSession, protocolStarterDelegate);
        return null;
    }

    @Override
    public byte[] serialize(ObvSyncSnapshotNode snapshotNode) throws Exception {
        if (!(snapshotNode instanceof IdentityManagerSyncSnapshot)) {
            throw new Exception("IdentityBackupDelegate can only serialize IdentityManagerSyncSnapshot");
        }
        return jsonObjectMapper.writeValueAsBytes(snapshotNode);
    }

    @Override
    public ObvSyncSnapshotNode deserialize(byte[] serializedSnapshotNode) throws Exception {
        return jsonObjectMapper.readValue(serializedSnapshotNode, IdentityManagerSyncSnapshot.class);
    }

    @Override
    public ObvBackupAndSyncDelegate getSyncDelegateWithinTransaction(Session session) {
        return new ObvBackupAndSyncDelegate() {
            private final IdentityManagerSession identityManagerSession = wrapSession(session);
            @Override
            public String getTag() {
                return IdentityManager.this.getTag();
            }

            @Override
            public ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity) {
                try {
                    return IdentityManager.this.getSyncSnapshotWithinTransaction(identityManagerSession, ownedIdentity);
                } catch (Exception e) {
                    Logger.x(e);
                    return null;
                }
            }

            @Override
            public RestoreFinishedCallback restoreOwnedIdentity(ObvIdentity ownedIdentity, ObvSyncSnapshotNode node) throws Exception {
                return IdentityManager.this.restoreOwnedIdentity(ownedIdentity, node);
            }

            @Override
            public RestoreFinishedCallback restoreSyncSnapshot(ObvSyncSnapshotNode node) throws Exception {
                return IdentityManager.this.restoreSyncSnapshotWithinTransaction(identityManagerSession, node);
            }

            @Override
            public byte[] serialize(ObvSyncSnapshotNode snapshotNode) throws Exception {
                return IdentityManager.this.serialize(snapshotNode);
            }

            @Override
            public ObvSyncSnapshotNode deserialize(byte[] serializedSnapshotNode) throws Exception {
                return IdentityManager.this.deserialize(serializedSnapshotNode);
            }
        };
    }

    @Override
    public ObvIdentity restoreTransferredOwnedIdentity(Session session, String deviceName, IdentityManagerSyncSnapshot node) throws Exception {
        Identity ownedIdentity = Identity.of(node.owned_identity);
        return node.owned_identity_node.restoreOwnedIdentity(wrapSession(session), deviceName, ownedIdentity);
    }

    // endregion
}
