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

package io.olvid.engine.protocol;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.ProtocolReceivedDialogResponse;
import io.olvid.engine.datatypes.containers.ProtocolReceivedMessage;
import io.olvid.engine.datatypes.containers.ProtocolReceivedServerResponse;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvDeviceManagementRequest;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.EngineOwnedIdentityCleanupDelegate;
import io.olvid.engine.metamanager.FullRatchetProtocolStarterDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.metamanager.ProtocolDelegate;
import io.olvid.engine.metamanager.PushNotificationDelegate;
import io.olvid.engine.protocol.coordinators.ProtocolStepCoordinator;
import io.olvid.engine.protocol.databases.ChannelCreationPingSignatureReceived;
import io.olvid.engine.protocol.databases.ChannelCreationProtocolInstance;
import io.olvid.engine.protocol.databases.GroupV2SignatureReceived;
import io.olvid.engine.protocol.databases.IdentityDeletionSignatureReceived;
import io.olvid.engine.protocol.databases.LinkBetweenProtocolInstances;
import io.olvid.engine.protocol.databases.MutualScanSignatureReceived;
import io.olvid.engine.protocol.databases.ProtocolInstance;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.databases.TrustEstablishmentCommitmentReceived;
import io.olvid.engine.protocol.databases.WaitingForOneToOneContactProtocolInstance;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.GenericProtocolMessageToSend;
import io.olvid.engine.protocol.datatypes.GenericReceivedProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSessionFactory;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocols.ChannelCreationWithContactDeviceProtocol;
import io.olvid.engine.protocol.protocols.ChannelCreationWithOwnedDeviceProtocol;
import io.olvid.engine.protocol.protocols.ContactManagementProtocol;
import io.olvid.engine.protocol.protocols.ContactMutualIntroductionProtocol;
import io.olvid.engine.protocol.protocols.DeviceCapabilitiesDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.DeviceDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.DownloadGroupPhotoChildProtocol;
import io.olvid.engine.protocol.protocols.DownloadGroupV2PhotoProtocol;
import io.olvid.engine.protocol.protocols.DownloadIdentityPhotoChildProtocol;
import io.olvid.engine.protocol.protocols.FullRatchetProtocol;
import io.olvid.engine.protocol.protocols.GroupManagementProtocol;
import io.olvid.engine.protocol.protocols.GroupsV2Protocol;
import io.olvid.engine.protocol.protocols.IdentityDetailsPublicationProtocol;
import io.olvid.engine.protocol.protocols.KeycloakBindingAndUnbindingProtocol;
import io.olvid.engine.protocol.protocols.KeycloakContactAdditionProtocol;
import io.olvid.engine.protocol.protocols.OneToOneContactInvitationProtocol;
import io.olvid.engine.protocol.protocols.OwnedDeviceDiscoveryProtocol;
import io.olvid.engine.protocol.protocols.OwnedDeviceManagementProtocol;
import io.olvid.engine.protocol.protocols.OwnedIdentityDeletionProtocol;
import io.olvid.engine.protocol.protocols.OwnedIdentityTransferProtocol;
import io.olvid.engine.protocol.protocols.SynchronizationProtocol;
import io.olvid.engine.protocol.protocols.TrustEstablishmentWithMutualScanProtocol;
import io.olvid.engine.protocol.protocols.TrustEstablishmentWithSasProtocol;

public class ProtocolManager implements ProtocolDelegate, ProtocolStarterDelegate, FullRatchetProtocolStarterDelegate, ProtocolManagerSessionFactory, ObvManager {

    private CreateSessionDelegate createSessionDelegate;
    private ChannelDelegate channelDelegate;
    private IdentityDelegate identityDelegate;
    private ObvBackupAndSyncDelegate identityBackupAndSyncDelegate;
    private EncryptionForIdentityDelegate encryptionForIdentityDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private NotificationListeningDelegate notificationListeningDelegate;
    private EngineOwnedIdentityCleanupDelegate engineOwnedIdentityCleanupDelegate;
    private PushNotificationDelegate pushNotificationDelegate;

    private final ProtocolStepCoordinator protocolStepCoordinator;
    private final String engineBaseDirectory;
    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;
    private final NewDeviceListener newDeviceListener;
    private final ContactDeletedListener contactDeletedListener;
    private final ContactTrustLevelListener contactTrustLevelListener;
    private final ObvBackupAndSyncDelegate appBackupAndSyncDelegate;

//    private final ScheduledExecutorService schedulerForPeriodicSync;

    public ProtocolManager(MetaManager metaManager, ObvBackupAndSyncDelegate appBackupAndSyncDelegate, String engineBaseDirectory, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.appBackupAndSyncDelegate = appBackupAndSyncDelegate;
        this.engineBaseDirectory = engineBaseDirectory;
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;
        this.protocolStepCoordinator = new ProtocolStepCoordinator(this, this.prng, this.jsonObjectMapper);
        this.newDeviceListener = new NewDeviceListener();
        this.contactDeletedListener = new ContactDeletedListener();
        this.contactTrustLevelListener = new ContactTrustLevelListener();
//        this.schedulerForPeriodicSync = Executors.newScheduledThreadPool(1);

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, ChannelDelegate.class);
        metaManager.requestDelegate(this, EncryptionForIdentityDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.requestDelegate(this, ObvBackupAndSyncDelegate.class);
        metaManager.requestDelegate(this, NotificationListeningDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, EngineOwnedIdentityCleanupDelegate.class);
        metaManager.requestDelegate(this, PushNotificationDelegate.class);
        metaManager.registerImplementedDelegates(this);

    }

    @Override
    public void initialisationComplete() {
        protocolStepCoordinator.initialQueueing();

        // check all contact oneToOne for WaitingForTLIncreaseProtocolInstance
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            for (WaitingForOneToOneContactProtocolInstance waitingForOneToOneContactProtocolInstance : WaitingForOneToOneContactProtocolInstance.getAll(protocolManagerSession)) {
                boolean oneToOne = identityDelegate.isIdentityAOneToOneContactOfOwnedIdentity(protocolManagerSession.session, waitingForOneToOneContactProtocolInstance.getOwnedIdentity(), waitingForOneToOneContactProtocolInstance.getContactIdentity());
                if (oneToOne) {
                    GenericProtocolMessageToSend message = waitingForOneToOneContactProtocolInstance.getGenericProtocolMessageToSendWhenTrustLevelIncreased();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message.generateChannelProtocolMessageToSend(), prng);
                }
            }

//            // trigger all SynchronizationProtocol instances to detect new changes and re-notify the app of current diffs
//            for (ProtocolInstance protocolInstance : ProtocolInstance.getAllForProtocolId(protocolManagerSession, ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID)) {
//                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(protocolInstance.getOwnedIdentity()),
//                        ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                        protocolInstance.getUid(),
//                        false);
//                ChannelMessageToSend message = new SynchronizationProtocol.TriggerSyncMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
//            }
//
//            // for all confirmed oblivious channels, initiate a SynchronizationProtocol in case one is not already running (message is ignored if protocol is already running)
//            for (Identity ownedIdentity : identityDelegate.getOwnedIdentities(protocolManagerSession.session)) {
//                UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, ownedIdentity);
//                for (UID otherDeviceUid : channelDelegate.getConfirmedObliviousChannelDeviceUids(protocolManagerSession.session, ownedIdentity, ownedIdentity)) {
//                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
//                            ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                            SynchronizationProtocol.computeOngoingProtocolInstanceUid(ownedIdentity, currentDeviceUid, otherDeviceUid),
//                            false);
//                    ChannelMessageToSend message = new SynchronizationProtocol.InitiateSyncMessage(coreProtocolMessage, otherDeviceUid).generateChannelProtocolMessageToSend();
//                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
//                }
//            }

            // delete all unfinished transfer instances
            ProtocolInstance.deleteAllTransfer(protocolManagerSession);

            protocolManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }

//        schedulerForPeriodicSync.schedule(this::triggerOwnedDevicesSync, Constants.PERIODIC_OWNED_DEVICE_SYNC_INTERVAL, Constants.PERIODIC_OWNED_DEVICE_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void startProcessing() {
        protocolStepCoordinator.startProcessing();
    }



//    private void triggerOwnedDevicesSync() {
//        try (ProtocolManagerSession protocolManagerSession = getSession()) {
//            for (ProtocolInstance protocolInstance : ProtocolInstance.getAllForProtocolId(protocolManagerSession, ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID)) {
//                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(protocolInstance.getOwnedIdentity()),
//                        ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                        protocolInstance.getUid(),
//                        false);
//                ChannelMessageToSend message = new SynchronizationProtocol.TriggerSyncMessage(coreProtocolMessage, false).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
//            }
//
//            protocolManagerSession.session.commit();
//        } catch (Exception e) {
//            Logger.x(e);
//        }
//    }

    // region SetDelegate

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            ReceivedMessage.createTable(protocolManagerSession.session);
            ProtocolInstance.createTable(protocolManagerSession.session);
            LinkBetweenProtocolInstances.createTable(protocolManagerSession.session);
            ChannelCreationProtocolInstance.createTable(protocolManagerSession.session);
            WaitingForOneToOneContactProtocolInstance.createTable(protocolManagerSession.session);
            ChannelCreationPingSignatureReceived.createTable(protocolManagerSession.session);
            TrustEstablishmentCommitmentReceived.createTable(protocolManagerSession.session);
            MutualScanSignatureReceived.createTable(protocolManagerSession.session);
            GroupV2SignatureReceived.createTable(protocolManagerSession.session);
            IdentityDeletionSignatureReceived.createTable(protocolManagerSession.session);
            protocolManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
            throw new RuntimeException("Unable to create protocol databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        ReceivedMessage.upgradeTable(session, oldVersion, newVersion);
        ProtocolInstance.upgradeTable(session, oldVersion, newVersion);
        LinkBetweenProtocolInstances.upgradeTable(session, oldVersion, newVersion);
        ChannelCreationProtocolInstance.upgradeTable(session, oldVersion, newVersion);
        WaitingForOneToOneContactProtocolInstance.upgradeTable(session, oldVersion, newVersion);
        ChannelCreationPingSignatureReceived.upgradeTable(session, oldVersion, newVersion);
        TrustEstablishmentCommitmentReceived.upgradeTable(session, oldVersion, newVersion);
        MutualScanSignatureReceived.upgradeTable(session, oldVersion, newVersion);
        GroupV2SignatureReceived.upgradeTable(session, oldVersion, newVersion);
        IdentityDeletionSignatureReceived.upgradeTable(session, oldVersion, newVersion);
    }

    public void setDelegate(ChannelDelegate channelDelegate) {
        this.channelDelegate = channelDelegate;
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }
    public void setDelegate(ObvBackupAndSyncDelegate identityBackupAndSyncDelegate) {
        this.identityBackupAndSyncDelegate = identityBackupAndSyncDelegate;
    }

    public void setDelegate(EncryptionForIdentityDelegate encryptionForIdentityDelegate) {
        this.encryptionForIdentityDelegate = encryptionForIdentityDelegate;
    }

    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE, newDeviceListener);
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE, newDeviceListener);
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED, contactDeletedListener);
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED, contactTrustLevelListener);
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setDelegate(EngineOwnedIdentityCleanupDelegate engineOwnedIdentityCleanupDelegate) {
        this.engineOwnedIdentityCleanupDelegate = engineOwnedIdentityCleanupDelegate;
    }

    public void setDelegate(PushNotificationDelegate pushNotificationDelegate) {
        this.pushNotificationDelegate = pushNotificationDelegate;
    }

    // endregion

    class NewDeviceListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE: {
                    try {
                        UID contactDeviceUid = (UID) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_DEVICE_UID_KEY);
                        Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY);
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                        Boolean channelCreationAlreadyInProgress = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CHANNEL_CREATION_ALREADY_IN_PROGRESS_KEY);
                        if (channelCreationAlreadyInProgress == null || !channelCreationAlreadyInProgress) {
                            startChannelCreationWithContactDeviceProtocol(ownedIdentity, contactIdentity, contactDeviceUid);
                        }
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                    break;
                }
                case IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE: {
                    try {
                        UID ownedDeviceUid = (UID) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_DEVICE_UID_KEY);
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_OWNED_IDENTITY_KEY);
                        Boolean channelCreationAlreadyInProgress = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_OWNED_DEVICE_CHANNEL_CREATION_ALREADY_IN_PROGRESS_KEY);
                        if (channelCreationAlreadyInProgress == null || !channelCreationAlreadyInProgress) {
                            startChannelCreationWithOwnedDeviceProtocol(ownedIdentity, ownedDeviceUid);
                        }
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                    break;
                }
            }
        }
    }

    class ContactDeletedListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            //noinspection SwitchStatementWithTooFewBranches
            switch (notificationName) {
                case IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED:
                    try {
                        Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_CONTACT_IDENTITY_KEY);
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_OWNED_IDENTITY_KEY);
                        try (ProtocolManagerSession protocolManagerSession = getSession()) {
                            ChannelCreationProtocolInstance[] channelCreationProtocolInstances = ChannelCreationProtocolInstance.getAllForContact(protocolManagerSession, contactIdentity, ownedIdentity);
                            if (channelCreationProtocolInstances == null) {
                                break;
                            }
                            for (ChannelCreationProtocolInstance channelCreationProtocolInstance: channelCreationProtocolInstances) {
                                abortProtocol(protocolManagerSession.session, channelCreationProtocolInstance.getProtocolInstanceUid(), ownedIdentity);
                            }
                            protocolManagerSession.session.commit();
                        }
                        // To improve: delete any other protocol related to this contact
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                    break;
            }
        }
    }

    class ContactTrustLevelListener implements NotificationListener {
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            //noinspection SwitchStatementWithTooFewBranches
            switch (notificationName) {
                case IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED:
                    try {
                        Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_OWNED_IDENTITY_KEY);
                        Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_CONTACT_IDENTITY_KEY);
                        boolean oneToOne = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY);
                        if (oneToOne) {
                            try (ProtocolManagerSession protocolManagerSession = getSession()) {
                                WaitingForOneToOneContactProtocolInstance[] waitingForOneToOneContactProtocolInstances = WaitingForOneToOneContactProtocolInstance.getAllForContact(protocolManagerSession, ownedIdentity, contactIdentity);
                                for (WaitingForOneToOneContactProtocolInstance waitingForOneToOneContactProtocolInstance : waitingForOneToOneContactProtocolInstances) {
                                    GenericProtocolMessageToSend message = waitingForOneToOneContactProtocolInstance.getGenericProtocolMessageToSendWhenTrustLevelIncreased();
                                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message.generateChannelProtocolMessageToSend(), prng);
                                }
                                protocolManagerSession.session.commit();
                            }
                        }
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                    break;
            }
        }
    }

    public void deleteOwnedIdentity(Session session, Identity ownedIdentity, UID excludedProtocolInstanceUid) throws SQLException {
        // delete ReceivedMessage
        ReceivedMessage.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete ProtocolInstance
        ProtocolInstance.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity, excludedProtocolInstanceUid);
        // delete TrustEstablishmentCommitmentReceived
        TrustEstablishmentCommitmentReceived.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete MutualScanNonceReceived
        MutualScanSignatureReceived.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete GroupV2SignatureReceived
        GroupV2SignatureReceived.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete LinkBetweenProtocolInstances
        LinkBetweenProtocolInstances.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete ChannelCreationProtocolInstance
        ChannelCreationProtocolInstance.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete ChannelCreationPingSignatureReceived
        ChannelCreationPingSignatureReceived.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete WaitingForTrustLevelIncreaseProtocolInstance
        WaitingForOneToOneContactProtocolInstance.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
        // delete IdentityDeletionSignatureReceived
        IdentityDeletionSignatureReceived.deleteAllForOwnedIdentity(wrapSession(session), ownedIdentity);
    }

    // region Implement ProtocolDelegate


    @Override
    public void abortProtocol(Session session, UID protocolInstanceUid, Identity ownedIdentity) throws Exception {
        // To improve: execute this on the protocol step execution thread
        //       move this to the ProtocolReceivedMessageProcessorDelegate API instead (in the ProtocolStepCoordinator)
        //       do something "protocol specific" to notify other devices that protocol was aborted when necessary.
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        Logger.w("Aborting Protocol " + protocolInstanceUid);

        // Find child protocol instances (and delete them just after)
        LinkBetweenProtocolInstances[] linksToParent = LinkBetweenProtocolInstances.getAllParentLinks(protocolManagerSession, protocolInstanceUid, ownedIdentity);
        LinkBetweenProtocolInstances[] linksToChild = LinkBetweenProtocolInstances.getAllChildLinks(protocolManagerSession, protocolInstanceUid, ownedIdentity);

        // Delete the associated ProtocolInstance
        ProtocolInstance protocolInstance = ProtocolInstance.get(protocolManagerSession, protocolInstanceUid, ownedIdentity);
        if (protocolInstance != null) {
            protocolInstance.delete();
        }

        // Delete all remaining ReceivedMessage for this protocol
        for (ReceivedMessage receivedMessage: ReceivedMessage.getAll(protocolManagerSession, protocolInstanceUid, ownedIdentity)) {
            receivedMessage.delete();
        }


        // recursively abort child and parent protocols
        for (LinkBetweenProtocolInstances linkToParent: linksToParent) {
            ProtocolInstance parentProtocolInstance = ProtocolInstance.get(protocolManagerSession, protocolInstanceUid, ownedIdentity);
            if (parentProtocolInstance != null) {
                abortProtocol(session, linkToParent.getParentProtocolInstanceUid(), ownedIdentity);
            }
        }

        for (LinkBetweenProtocolInstances linkToChild: linksToChild) {
            ProtocolInstance childProtocolInstance = ProtocolInstance.get(protocolManagerSession, protocolInstanceUid, ownedIdentity);
            if (childProtocolInstance != null) {
                abortProtocol(session, linkToChild.getChildProtocolInstanceUid(), ownedIdentity);
            }
        }
    }

    @Override
    public void process(Session session, ProtocolReceivedMessage message) throws Exception {
        if (!identityDelegate.isOwnedIdentity(session, message.getOwnedIdentity())
                && !Objects.equals(message.getOwnedIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
            throw new Exception();
        }
        GenericReceivedProtocolMessage genericReceivedProtocolMessage = GenericReceivedProtocolMessage.of(message);
        ReceivedMessage.create(wrapSession(session),
                genericReceivedProtocolMessage,
                prng);
    }

    @Override
    public void process(Session session, ProtocolReceivedDialogResponse message) throws Exception {
        if (!identityDelegate.isOwnedIdentity(session, message.getToIdentity())
                && !Objects.equals(message.getToIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
            throw new Exception();
        }
        GenericReceivedProtocolMessage genericReceivedProtocolMessage = GenericReceivedProtocolMessage.of(message);
        ReceivedMessage.create(wrapSession(session),
                genericReceivedProtocolMessage,
                prng);
    }

    @Override
    public void process(Session session, ProtocolReceivedServerResponse message) throws Exception {
        if (!identityDelegate.isOwnedIdentity(session, message.getToIdentity())
                && !Objects.equals(message.getToIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
            throw new Exception();
        }
        GenericReceivedProtocolMessage genericReceivedProtocolMessage = GenericReceivedProtocolMessage.of(message);
        ReceivedMessage.create(wrapSession(session),
                genericReceivedProtocolMessage,
                prng);
    }

    @Override
    public boolean isChannelCreationInProgress(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception {
        return ChannelCreationProtocolInstance.get(wrapSession(session), contactDeviceUid, contactIdentity, ownedIdentity) != null;
    }

    // endregion


    // region Implement ProtocolManagerSessionFactory

    @Override
    public ProtocolManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in ChannelManager.");
        }
        return new ProtocolManagerSession(createSessionDelegate.getSession(), channelDelegate, identityDelegate, encryptionForIdentityDelegate, protocolStepCoordinator, this, this, notificationPostingDelegate, notificationListeningDelegate, engineOwnedIdentityCleanupDelegate, pushNotificationDelegate, engineBaseDirectory, identityBackupAndSyncDelegate, appBackupAndSyncDelegate);
    }

    private ProtocolManagerSession wrapSession(Session session) {
        return new ProtocolManagerSession(session, channelDelegate, identityDelegate, encryptionForIdentityDelegate, protocolStepCoordinator, this, this, notificationPostingDelegate, notificationListeningDelegate, engineOwnedIdentityCleanupDelegate, pushNotificationDelegate, engineBaseDirectory, identityBackupAndSyncDelegate, appBackupAndSyncDelegate);
    }
    // endregion



    // region Implement ProtocolStarterDelegate

    @Override
    public void startDeviceDiscoveryProtocol(Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a DeviceDiscovery protocol with contactIdentity == ownedIdentity");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.DEVICE_DISCOVERY_PROTOCOL_ID,
                    protocolInstanceUid);
            ChannelMessageToSend message = new DeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage, contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startDeviceDiscoveryProtocolWithinTransaction(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a DeviceDiscovery protocol with contactIdentity == ownedIdentity");
            return;
        }
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.DEVICE_DISCOVERY_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new DeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage, contactIdentity).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
    }

    @Override
    public void startOwnedDeviceDiscoveryProtocol(Identity ownedIdentity) throws Exception {
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.OWNED_DEVICE_DISCOVERY_PROTOCOL_ID,
                    protocolInstanceUid);
            ChannelMessageToSend message = new OwnedDeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startOwnedDeviceDiscoveryProtocolWithinTransaction(Session session, Identity ownedIdentity) throws Exception {
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.OWNED_DEVICE_DISCOVERY_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new OwnedDeviceDiscoveryProtocol.InitialMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(session, message, prng);
    }

    @Override
    public void startChannelCreationProtocolWithOwnedDevice(Session session, Identity ownedIdentity, UID ownedDeviceUid) throws Exception {
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new ChannelCreationWithOwnedDeviceProtocol.InitialMessage(coreProtocolMessage, ownedDeviceUid).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(session, message, prng);
    }

    @Override
    public void startChannelCreationProtocolWithContactDevice(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a ChannelCreationWithContactDeviceProtocol with contactIdentity == ownedIdentity");
            return;
        }
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new ChannelCreationWithContactDeviceProtocol.InitialMessage(coreProtocolMessage, contactIdentity, contactDeviceUid).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(session, message, prng);
    }

    @Override
    public void startDownloadIdentityPhotoProtocolWithinTransaction(Session session, Identity ownedIdentity, Identity contactIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto) throws Exception {
        if (ownedIdentity == null || contactIdentity == null || jsonIdentityDetailsWithVersionAndPhoto == null) {
            return;
        }
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, contactIdentity, jsonObjectMapper.writeValueAsString(jsonIdentityDetailsWithVersionAndPhoto)).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
    }

    @Override
    public void startDownloadGroupPhotoProtocolWithinTransaction(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto) throws Exception {
        if (ownedIdentity == null || groupOwnerAndUid == null || groupOwnerAndUid.length < UID.UID_LENGTH || jsonGroupDetailsWithVersionAndPhoto == null) {
            return;
        }

        GroupInformation groupInformation = new GroupInformation(
                Identity.of(Arrays.copyOfRange(groupOwnerAndUid, 0, groupOwnerAndUid.length - UID.UID_LENGTH)),
                new UID(Arrays.copyOfRange(groupOwnerAndUid, groupOwnerAndUid.length - UID.UID_LENGTH, groupOwnerAndUid.length)),
                jsonObjectMapper.writeValueAsString(jsonGroupDetailsWithVersionAndPhoto));
        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.DOWNLOAD_GROUP_PHOTO_CHILD_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new DownloadGroupPhotoChildProtocol.InitialMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
    }

    @Override
    public void startDownloadGroupV2PhotoProtocolWithinTransaction(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo) throws Exception {
        if (ownedIdentity == null || groupIdentifier == null || serverPhotoInfo == null) {
            return;
        }

        ProtocolManagerSession protocolManagerSession = wrapSession(session);
        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new DownloadGroupV2PhotoProtocol.InitialMessage(coreProtocolMessage, groupIdentifier, serverPhotoInfo).generateChannelProtocolMessageToSend();
        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
    }

    @Override
    public void startTrustEstablishmentProtocol(Identity ownedIdentity, Identity contactIdentity, String contactDisplayName) throws Exception {
        startTrustEstablishmentWithSasProtocol(contactIdentity, contactDisplayName, ownedIdentity);
    }

    @Override
    public void initiateGroupV2ReDownloadWithinTransaction(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (session == null || ownedIdentity == null || groupIdentifier == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new GroupsV2Protocol.GroupReDownloadInitialMessage(coreProtocolMessage, groupIdentifier).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }


    @Override
    public void initiateKeycloakGroupV2TargetedPing(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity contactIdentity) throws Exception {
        if (session == null || ownedIdentity == null || groupIdentifier == null || groupIdentifier.category != GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            throw new Exception();
        }

        UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new GroupsV2Protocol.InitiateTargetedPingMessage(coreProtocolMessage, groupIdentifier, contactIdentity).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    private void startTrustEstablishmentWithSasProtocol(Identity contactIdentity, String contactDisplayName, Identity ownedIdentity) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a trust establishment protocol with contactIdentity == ownedIdentity");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.TRUST_ESTABLISHMENT_WITH_SAS_PROTOCOL_ID,
                    protocolInstanceUid);
            String ownedIdentityDetails = identityDelegate.getSerializedPublishedDetailsOfOwnedIdentity(protocolManagerSession.session, ownedIdentity);
            if (ownedIdentityDetails == null) {
                Logger.e("Error finding own identity details in startTrustEstablishmentProtocol");
                return;
            }
            ChannelMessageToSend message = new TrustEstablishmentWithSasProtocol.InitialMessage(coreProtocolMessage, contactIdentity, contactDisplayName, ownedIdentityDetails).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startMutualScanTrustEstablishmentProtocol(Identity ownedIdentity, Identity contactIdentity, byte[] signature) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a mutual scan protocol with contactIdentity == ownedIdentity");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.TRUST_ESTABLISHMENT_WITH_MUTUAL_SCAN_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new TrustEstablishmentWithMutualScanProtocol.InitialMessage(coreProtocolMessage, contactIdentity, signature).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }

    }

    private void startChannelCreationWithContactDeviceProtocol(Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception {
        if (contactIdentity.equals(ownedIdentity)) {
            Logger.w("Cannot start a protocol with contactIdentity == ownedIdentity");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.CHANNEL_CREATION_WITH_CONTACT_DEVICE_PROTOCOL_ID,
                    protocolInstanceUid);
            ChannelMessageToSend message = new ChannelCreationWithContactDeviceProtocol.InitialMessage(coreProtocolMessage, contactIdentity, contactDeviceUid).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    public void startChannelCreationWithOwnedDeviceProtocol(Identity ownedIdentity, UID ownedDeviceUid) throws Exception {
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.CHANNEL_CREATION_WITH_OWNED_DEVICE_PROTOCOL_ID,
                    protocolInstanceUid);
            ChannelMessageToSend message = new ChannelCreationWithOwnedDeviceProtocol.InitialMessage(coreProtocolMessage, ownedDeviceUid).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startContactMutualIntroductionProtocol(Identity ownedIdentity, Identity contactIdentityA, Identity[] contactIdentities) throws Exception {
        if (contactIdentityA.equals(ownedIdentity)) {
            Logger.w("Cannot start a protocol with contactIdentity == ownedIdentity");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            protocolManagerSession.session.startTransaction();
            for (Identity contactIdentityB: contactIdentities) {
                if (contactIdentityB.equals(ownedIdentity)) {
                    Logger.w("Cannot start a protocol with contactIdentity == ownedIdentity");
                    return;
                }
                UID protocolInstanceUid = new UID(prng);
                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                        ConcreteProtocol.CONTACT_MUTUAL_INTRODUCTION_PROTOCOL_ID,
                        protocolInstanceUid);
                ChannelMessageToSend message = new ContactMutualIntroductionProtocol.InitialMessage(coreProtocolMessage, contactIdentityA, contactIdentityB).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            }
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startGroupCreationProtocol(Identity ownedIdentity, String serializedGroupDetailsWithVersionAndPhoto, String absolutePhotoUrl, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndSerializedDetails) throws Exception {
        if (serializedGroupDetailsWithVersionAndPhoto == null || ownedIdentity == null || groupMemberIdentitiesAndSerializedDetails == null) {
            throw new Exception();
        }

        if (groupMemberIdentitiesAndSerializedDetails.contains(new IdentityWithSerializedDetails(ownedIdentity, ""))) {
            Logger.e("Error in startGroupCreationProtocol: ownedIdentity contained in groupMemberIdentitiesAndSerializedDetails");
            throw new Exception();
        }


        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            GroupInformation groupInformation = GroupInformation.generate(ownedIdentity, serializedGroupDetailsWithVersionAndPhoto, prng);
            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.InitiateGroupCreationMessage(coreProtocolMessage, groupInformation, absolutePhotoUrl, groupMemberIdentitiesAndSerializedDetails).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }


    public void startGroupV2CreationProtocol(Identity ownedIdentity, String serializedGroupDetails, String absolutePhotoUrl, HashSet<GroupV2.Permission> ownPermissions, HashSet<GroupV2.IdentityAndPermissions> otherGroupMembers, String serializedGroupType) throws Exception {
        if (serializedGroupDetails == null || ownedIdentity == null || ownPermissions == null || otherGroupMembers == null) {
            throw new Exception();
        }

        if (otherGroupMembers.contains(new GroupV2.IdentityAndPermissions(ownedIdentity, null))) {
            Logger.e("Error in startGroupV2CreationProtocol: ownedIdentity contained in otherGroupMembers");
            throw new Exception();
        }

        if (!ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
            Logger.e("Error in startGroupV2CreationProtocol: ownedPermissions do not containt GROUP_ADMIN");
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupsV2Protocol.GroupCreationInitialMessage(coreProtocolMessage, ownPermissions, otherGroupMembers, serializedGroupDetails, absolutePhotoUrl, serializedGroupType).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void initiateGroupV2Update(Identity ownedIdentity, GroupV2.Identifier groupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet) throws Exception {
        if (ownedIdentity == null || groupIdentifier == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupsV2Protocol.GroupUpdateInitialMessage(coreProtocolMessage, groupIdentifier, changeSet).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void initiateGroupV2Leave(Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (ownedIdentity == null || groupIdentifier == null || groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupsV2Protocol.GroupLeaveInitialMessage(coreProtocolMessage, groupIdentifier).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void initiateGroupV2Disband(Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (ownedIdentity == null || groupIdentifier == null || groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupsV2Protocol.GroupDisbandInitialMessage(coreProtocolMessage, groupIdentifier).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }


    @Override
    public void initiateGroupV2ReDownload(Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (ownedIdentity == null || groupIdentifier == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupsV2Protocol.GroupReDownloadInitialMessage(coreProtocolMessage, groupIdentifier).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void initiateGroupV2BatchKeysResend(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception {
        if (session == null || ownedIdentity == null || contactIdentity == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new GroupsV2Protocol.InitiateBatchKeysResendMessage(coreProtocolMessage, contactIdentity, contactDeviceUid).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void createOrUpdateKeycloakGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedKeycloakGroupBlob) throws Exception {
        if (session == null || ownedIdentity == null || groupIdentifier == null || serializedKeycloakGroupBlob == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = groupIdentifier.computeProtocolInstanceUid();
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.GROUPS_V2_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new GroupsV2Protocol.CreateOrUpdateKeycloakGroupMessage(coreProtocolMessage, groupIdentifier, serializedKeycloakGroupBlob).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }


    @Override
    public void processDeviceManagementRequest(Identity ownedIdentity, ObvDeviceManagementRequest deviceManagementRequest) throws Exception {
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            processDeviceManagementRequest(protocolManagerSession.session, ownedIdentity, deviceManagementRequest);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void processDeviceManagementRequest(Session session, Identity ownedIdentity, ObvDeviceManagementRequest deviceManagementRequest) throws Exception {
        if (session == null || ownedIdentity == null || deviceManagementRequest == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.OWNED_DEVICE_MANAGEMENT_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new OwnedDeviceManagementProtocol.InitialMessage(coreProtocolMessage, deviceManagementRequest).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void startIdentityDetailsPublicationProtocol(Session session, Identity ownedIdentity, int version) throws Exception {
        if (ownedIdentity == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.IDENTITY_DETAILS_PUBLICATION_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new IdentityDetailsPublicationProtocol.InitialMessage(coreProtocolMessage, version).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void startGroupDetailsPublicationProtocol(Session session, Identity ownedIdentity, byte[] groupUid)  throws Exception {
        if (ownedIdentity == null || groupUid == null) {
            throw new Exception();
        }

        GroupInformation groupInformation = identityDelegate.getGroupInformation(session, ownedIdentity, groupUid);
        if (groupInformation == null) {
            throw new Exception();
        }
        UID protocolInstanceUid = groupInformation.computeProtocolUid();
        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                protocolInstanceUid);
        ChannelMessageToSend message = new GroupManagementProtocol.GroupMembersOrDetailsChangedTriggerMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void startOneToOneInvitationProtocol(Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (ownedIdentity == null || contactIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID,
                    protocolInstanceUid);
            ChannelMessageToSend message = new OneToOneContactInvitationProtocol.InitialMessage(coreProtocolMessage, contactIdentity).generateChannelProtocolMessageToSend();
            channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void inviteContactsToGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, HashSet<Identity> newMembersIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null || newMembersIdentity == null || newMembersIdentity.isEmpty()) {
            throw new Exception();
        }

        if (newMembersIdentity.contains(ownedIdentity)) {
            Logger.e("Error in inviteContactsToGroup: ownedIdentity contained in newMembersIdentity");
            throw new Exception();
        }


        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in inviteContactsToGroup: group not found");
                throw new Exception();
            }

            for (IdentityWithSerializedDetails pendingIdentity: group.getPendingGroupMembers()) {
                if (newMembersIdentity.contains(pendingIdentity.identity)) {
                    Logger.e("Error in inviteContactsToGroup: adding a member that is already pending");
                    throw new Exception();
                }
            }

            for (Identity memberIdentity: group.getGroupMembers()) {
                if (newMembersIdentity.contains(memberIdentity)) {
                    Logger.e("Error in inviteContactsToGroup: adding a member that is already in the group");
                    throw new Exception();
                }
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.AddGroupMembersMessage(coreProtocolMessage, groupInformation, newMembersIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void reinvitePendingToGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity pendingMemberIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null || pendingMemberIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in reinvitePendingToGroup: group not found");
                throw new Exception();
            }

            if (!group.isPendingMember(pendingMemberIdentity)) {
                Logger.e("Error in reinvitePendingToGroup: pendingMemberIdentity is not a PendingMember");
                throw new Exception();
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.ReinvitePendingMemberMessage(coreProtocolMessage, groupInformation, pendingMemberIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }

    }


    @Override
    public void removeContactsFromGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, HashSet<Identity> removedMemberIdentities) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null || removedMemberIdentities == null || removedMemberIdentities.isEmpty()) {
            throw new Exception();
        }

        if (removedMemberIdentities.contains(ownedIdentity)) {
            Logger.e("Error in inviteContactsToGroup: ownedIdentity contained in removedMemberIdentities");
            throw new Exception();
        }


        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in inviteContactsToGroup: group not found");
                throw new Exception();
            }

            for (Identity removedMemberIdentity: removedMemberIdentities) {
                if (!group.isMember(removedMemberIdentity) && !group.isPendingMember(removedMemberIdentity)) {
                    Logger.e("Error in removedMemberIdentities: removing a member that is neither member nor pending");
                    throw new Exception();
                }
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.RemoveGroupMembersMessage(coreProtocolMessage, groupInformation, removedMemberIdentities).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }

    }

    @Override
    public void leaveGroup(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in leaveGroup: group not found");
                throw new Exception();
            }

            if (group.getGroupOwner() == null) {
                Logger.e("Error in leaveGroup: trying to leave a group you own");
                throw new Exception();
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.LeaveGroupMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void disbandGroup(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in disbandGroup: group not found");
                throw new Exception();
            }

            if (group.getGroupOwner() != null) {
                Logger.e("Error in disbandGroup: trying to disband a group you do not own");
                throw new Exception();
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.DisbandGroupMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void queryGroupMembers(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in queryGroupMembers: group not found");
                throw new Exception();
            }

            if (group.getGroupOwner() == null) {
                throw new Exception();
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.InitiateGroupMembersQueryMessage(coreProtocolMessage, groupInformation).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }


    @Override
    public void reinviteAndPushMembersToContact(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (groupOwnerAndUid == null || ownedIdentity == null || contactIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Group group = identityDelegate.getGroup(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            GroupInformation groupInformation = identityDelegate.getGroupInformation(protocolManagerSession.session, ownedIdentity, groupOwnerAndUid);
            if (group == null || groupInformation == null) {
                Logger.e("Error in reinviteAndPushMembersToContact: group not found");
                throw new Exception();
            }

            if (group.getGroupOwner() != null) {
                Logger.e("Error in reinviteAndPushMembersToContact: trying to reinvite to a group you do not own");
                throw new Exception();
            }

            UID protocolInstanceUid = groupInformation.computeProtocolUid();

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.GROUP_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new GroupManagementProtocol.TriggerReinviteMessage(coreProtocolMessage, groupInformation, contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }


    @Override
    public void deleteContact(Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (contactIdentity == null || ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            if (!identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, ownedIdentity, contactIdentity)) {
                Logger.e("Error in deleteContact: contact not found");
                throw new Exception();
            }

            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.CONTACT_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new ContactManagementProtocol.InitiateContactDeletionMessage(coreProtocolMessage, contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void downgradeOneToOneContact(Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (contactIdentity == null || ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            if (!identityDelegate.isIdentityAContactOfOwnedIdentity(protocolManagerSession.session, ownedIdentity, contactIdentity)) {
                Logger.e("Error in downgradeContact: contact not found");
                throw new Exception();
            }

            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.CONTACT_MANAGEMENT_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new ContactManagementProtocol.InitiateContactDowngradeMessage(coreProtocolMessage, contactIdentity).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void addKeycloakContact(Identity ownedIdentity, Identity contactIdentity, String signedContactDetails) throws Exception {
        if (contactIdentity == null || ownedIdentity == null || signedContactDetails == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.KEYCLOAK_CONTACT_ADDITION_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new KeycloakContactAdditionProtocol.InitialMessage(coreProtocolMessage, contactIdentity, signedContactDetails).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startProtocolForBindingOwnedIdentityToKeycloakWithinTransaction(Session session, Identity ownedIdentity, ObvKeycloakState keycloakState, String keycloakUserId) throws Exception {
        if (ownedIdentity == null
                || keycloakState == null
                || keycloakUserId == null
                || keycloakState.keycloakServer == null
                || keycloakState.jwks == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new KeycloakBindingAndUnbindingProtocol.OwnedIdentityKeycloakBindingMessage(coreProtocolMessage, keycloakState, keycloakUserId).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void updateCurrentDeviceCapabilitiesForOwnedIdentity(Session session, Identity ownedIdentity, List<ObvCapability> newOwnCapabilities) throws Exception {
        if (newOwnCapabilities == null) {
            return;
        }

        UID protocolInstanceUid = new UID(prng);

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new DeviceCapabilitiesDiscoveryProtocol.InitialForAddingOwnCapabilitiesMessage(coreProtocolMessage, newOwnCapabilities).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

    @Override
    public void startProtocolForUnbindingOwnedIdentityFromKeycloak(Identity ownedIdentity) throws Exception {
        if (ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.KEYCLOAK_BINDING_AND_UNBINDING_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new KeycloakBindingAndUnbindingProtocol.OwnedIdentityKeycloakUnbindingMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void startOwnedIdentityDeletionProtocol(Session session, Identity ownedIdentity, boolean deleteEverywhere) throws Exception {
        if (session == null || ownedIdentity == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.OWNED_IDENTITY_DELETION_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new OwnedIdentityDeletionProtocol.InitialMessage(coreProtocolMessage, deleteEverywhere).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }


    @Override
    public void initiateSingleItemSync(Session session, Identity ownedIdentity, ObvSyncAtom obvSyncAtom) throws Exception {
        if (session == null || ownedIdentity == null || obvSyncAtom == null) {
            throw new Exception();
        }

        UID protocolInstanceUid = new UID(prng);

        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
                protocolInstanceUid);

        ChannelMessageToSend message = new SynchronizationProtocol.InitiateSingleItemSyncMessage(coreProtocolMessage, obvSyncAtom).generateChannelProtocolMessageToSend();
        channelDelegate.post(session, message, prng);
    }

//    @Override
//    public void triggerOwnedDevicesSync(Session session, Identity ownedIdentity) {
//        try {
//            ProtocolManagerSession protocolManagerSession = wrapSession(session);
//            for (ProtocolInstance protocolInstance : ProtocolInstance.getAllForOwnedIdentityProtocolId(protocolManagerSession, ownedIdentity, ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID)) {
//                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
//                        ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                        protocolInstance.getUid(),
//                        false);
//                ChannelMessageToSend message = new SynchronizationProtocol.TriggerSyncMessage(coreProtocolMessage, true).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
//            }
//        } catch (Exception e) {
//            Logger.x(e);
//        }
//    }

    @Override
    public void initiateOwnedIdentityTransferProtocolOnSourceDevice(Identity ownedIdentity) throws Exception {
        if (ownedIdentity == null) {
            throw new Exception();
        }

        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                    ConcreteProtocol.OWNED_IDENTITY_TRANSFER_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new OwnedIdentityTransferProtocol.InitiateTransferOnSourceDeviceMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }

    @Override
    public void initiateOwnedIdentityTransferProtocolOnTargetDevice(String deviceName) throws Exception {
        KeyPair serverAuthKeyPair = Suite.generateServerAuthenticationKeyPair(null, prng);
        KeyPair encryptionKeyPair = Suite.generateEncryptionKeyPair(null, prng);
        if (serverAuthKeyPair == null || encryptionKeyPair == null) {
            throw new Exception();
        }
        MACKey macKey = Suite.getDefaultMAC(0).generateKey(prng);
        Identity ephemeralIdentity = new Identity(Constants.EPHEMERAL_IDENTITY_SERVER, (ServerAuthenticationPublicKey) serverAuthKeyPair.getPublicKey(), (EncryptionPublicKey) encryptionKeyPair.getPublicKey());


        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            UID protocolInstanceUid = new UID(prng);

            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ephemeralIdentity),
                    ConcreteProtocol.OWNED_IDENTITY_TRANSFER_PROTOCOL_ID,
                    protocolInstanceUid);

            ChannelMessageToSend message = new OwnedIdentityTransferProtocol.InitiateTransferOnTargetDeviceMessage(coreProtocolMessage, deviceName, (ServerAuthenticationPrivateKey) serverAuthKeyPair.getPrivateKey(), (EncryptionPrivateKey) encryptionKeyPair.getPrivateKey(), macKey).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
            protocolManagerSession.session.commit();
        }
    }


    // endregion

    // region Implement FullRatchetProtocolStarterDelegate
    @Override
    public void startFullRatchetProtocolForObliviousChannel(UID currentDeviceUid, UID remoteDeviceUid, Identity remoteIdentity) throws Exception {
        try (ProtocolManagerSession protocolManagerSession = getSession()) {
            Identity ownedIdentity = identityDelegate.getOwnedIdentityForCurrentDeviceUid(protocolManagerSession.session, currentDeviceUid);
            if (ownedIdentity != null) {
                UID protocolInstanceUid = FullRatchetProtocol.computeProtocolUid(ownedIdentity, remoteIdentity, currentDeviceUid, remoteDeviceUid);

                CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(ownedIdentity),
                        ConcreteProtocol.FULL_RATCHET_PROTOCOL_ID,
                        protocolInstanceUid,
                        true,
                        false);

                ChannelMessageToSend message = new FullRatchetProtocol.InitialMessage(coreProtocolMessage, remoteIdentity, remoteDeviceUid).generateChannelProtocolMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, prng);
                protocolManagerSession.session.commit();
            }
        }
    }
    // endregion
}
