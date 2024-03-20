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

package io.olvid.engine.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.JsonWebKey;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.BackupManager;
import io.olvid.engine.channel.ChannelManager;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCKeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaMDCKeyPair;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelApplicationMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelDialogResponseMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.databases.EngineDbSchemaVersion;
import io.olvid.engine.engine.databases.UserInterfaceDialog;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.datatypes.EngineSessionFactory;
import io.olvid.engine.engine.datatypes.UserInterfaceDialogListener;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonOsmStyle;
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvDeviceList;
import io.olvid.engine.engine.types.ObvDeviceManagementRequest;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.engine.types.RegisterApiKeyResult;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.ObvReturnReceipt;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.engine.types.identities.ObvTrustOrigin;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.identity.IdentityManager;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.EngineOwnedIdentityCleanupDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.networkfetch.FetchManager;
import io.olvid.engine.networkfetch.datatypes.DownloadAttachmentPriorityCategory;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;
import io.olvid.engine.networksend.SendManager;
import io.olvid.engine.notification.NotificationManager;
import io.olvid.engine.protocol.ProtocolManager;

public class Engine implements UserInterfaceDialogListener, EngineSessionFactory, EngineAPI, EngineOwnedIdentityCleanupDelegate {
    // region fields

    private long instanceCounter;
    private final HashMap<String, HashMap<Long, WeakReference<EngineNotificationListener>>> listeners;
    private final ReentrantLock listenersLock;
    private final BlockingQueue<EngineNotification> notificationQueue;

    private final PRNGService prng;
    final ObjectMapper jsonObjectMapper;


    private final String dbPath;
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // will be used once we use SQL cipher
    private final String dbKey;
    private final CreateSessionDelegate createSessionDelegate;

    final ChannelManager channelManager;
    final IdentityManager identityManager;
    final FetchManager fetchManager;
    final SendManager sendManager;
    final NotificationManager notificationManager;
    final ProtocolManager protocolManager;
    final BackupManager backupManager;
    final NotificationWorker notificationWorker;

    // endregion

    public Engine(File baseDirectory, ObvBackupAndSyncDelegate appBackupAndSyncDelegate, String dbKey, SSLSocketFactory sslSocketFactory, Logger.LogOutputter logOutputter, int logLevel) throws Exception {
        instanceCounter = 0;
        listeners = new HashMap<>();
        listenersLock = new ReentrantLock();
        notificationQueue = new ArrayBlockingQueue<>(1_000);
        notificationWorker = new NotificationWorker();

        Logger.setOutputter(logOutputter);
        Logger.setOutputLogLevel(logLevel);

        this.prng = Suite.getDefaultPRNGService(0);

        this.jsonObjectMapper = new ObjectMapper();
        this.jsonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        if (baseDirectory == null) {
            baseDirectory = new File(System.getProperty("java.io.tmpdir"));
        }
        String baseDirectoryPath = baseDirectory.getPath();

        this.dbPath = new File(baseDirectory, Constants.ENGINE_DB_FILENAME).getPath();
        this.dbKey = dbKey;

        File inboundAttachmentDirectory = new File(baseDirectory, Constants.INBOUND_ATTACHMENTS_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        inboundAttachmentDirectory.mkdir();

        File identityPhotosDirectory = new File(baseDirectory, Constants.IDENTITY_PHOTOS_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        identityPhotosDirectory.mkdir();

        File userDataDirectory = new File(baseDirectory, Constants.DOWNLOADED_USER_DATA_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        userDataDirectory.mkdir();


        // check whether a database upgrade is required
        try (Session session = Session.getUpgradeTablesSession(dbPath, dbKey)) {
            session.startTransaction();
            EngineDbSchemaVersion.createTable(session);
            session.commit();
            EngineDbSchemaVersion engineDbSchemaVersion = EngineDbSchemaVersion.get(wrapSession(session));
            if (engineDbSchemaVersion == null) {
                throw new SQLException();
            }
            if (engineDbSchemaVersion.getVersion() != Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION) {
                Logger.w("WARNING ENGINE DB SCHEMA VERSION CHANGED FROM " + engineDbSchemaVersion.getVersion() + " TO " + Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION);
                for (int version = engineDbSchemaVersion.getVersion(); version < Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION; version++) {
                    if (version == 15) { // the migration from 15 to 16 changes the path format of inboundAttachments, we delete them and reset their progress
                        deleteRecursive(inboundAttachmentDirectory);
                        //noinspection ResultOfMethodCallIgnored
                        inboundAttachmentDirectory.mkdir();
                    }
                    session.startTransaction();
                    Logger.w("WARNING    -  STEP VERSION " + version + " TO " + (version + 1));
                    Engine.upgradeTables(session, version, version + 1);
                    ChannelManager.upgradeTables(session, version, version + 1);
                    IdentityManager.upgradeTables(session, version, version + 1);
                    FetchManager.upgradeTables(session, version, version + 1);
                    SendManager.upgradeTables(session, version, version + 1);
                    ProtocolManager.upgradeTables(session, version, version + 1);
                    BackupManager.upgradeTables(session, version, version + 1);
                    engineDbSchemaVersion.update(version + 1);
                    session.commit();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to check for tables upgrade", e);
        }


        MetaManager metaManager = new MetaManager();
        this.createSessionDelegate = () -> Session.getSession(dbPath, dbKey);
        metaManager.registerImplementedDelegates(this.createSessionDelegate);
        metaManager.registerImplementedDelegates(this);

        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog.createTable(engineSession.session);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create engine databases");
        }

        this.channelManager = new ChannelManager(metaManager);
        this.identityManager = new IdentityManager(metaManager, baseDirectoryPath, jsonObjectMapper, prng);
        this.fetchManager = new FetchManager(metaManager, sslSocketFactory, baseDirectoryPath, prng, jsonObjectMapper);
        this.sendManager = new SendManager(metaManager, sslSocketFactory, baseDirectoryPath, prng);
        this.notificationManager = new NotificationManager(metaManager);
        this.protocolManager = new ProtocolManager(metaManager, appBackupAndSyncDelegate, baseDirectoryPath, prng, jsonObjectMapper);
        this.backupManager = new BackupManager(metaManager, prng, jsonObjectMapper);

        registerToInternalNotifications();
        initializationComplete();
        metaManager.initializationComplete();
    }

    private static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        UserInterfaceDialog.upgradeTable(session, oldVersion, newVersion);
    }

    private void initializationComplete() {
        try {
            // clear all transfer protocol UserInterfaceDialog
            try (EngineSession engineSession = getSession()) {
                for (UserInterfaceDialog userInterfaceDialog : UserInterfaceDialog.getAll(engineSession)) {
                    try {
                        if (userInterfaceDialog.getObvDialog().getCategory().getId() == ObvDialog.Category.TRANSFER_DIALOG_CATEGORY) {
                            userInterfaceDialog.delete();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            userInterfaceDialog.delete();
                        } catch (Exception ignored) { }
                    }
                }
                engineSession.session.commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null) {
            return;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child: children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }
    // region External Notifications

    public void addNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener) {
        listenersLock.lock();
        long listenerNumber;
        if (engineNotificationListener.hasEngineNotificationListenerRegistrationNumber()) {
            listenerNumber = engineNotificationListener.getEngineNotificationListenerRegistrationNumber();
        } else {
            listenerNumber = instanceCounter;
            instanceCounter++;
            engineNotificationListener.setEngineNotificationListenerRegistrationNumber(listenerNumber);
        }
        HashMap<Long, WeakReference<EngineNotificationListener>> notificationObservers = listeners.get(notificationName);
        if (notificationObservers == null) {
            notificationObservers = new HashMap<>();
            listeners.put(notificationName, notificationObservers);
        }
        WeakReference<EngineNotificationListener> weakReference = new WeakReference<>(engineNotificationListener);
        notificationObservers.put(listenerNumber, weakReference);
        listenersLock.unlock();
    }


    public void removeNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener) {
        if (engineNotificationListener != null && engineNotificationListener.hasEngineNotificationListenerRegistrationNumber()) {
            removeNotificationListener(notificationName, engineNotificationListener.getEngineNotificationListenerRegistrationNumber());
        }
    }

    public void startSendingNotifications() {
        notificationWorker.start();
    }

    public void stopSendingNotifications() {
        notificationWorker.stop();
    }

    private void removeNotificationListener(String notificationName, long notificationListenerRegistrationNumber) {
        listenersLock.lock();
        HashMap<Long, WeakReference<EngineNotificationListener>> notificationObservers = listeners.get(notificationName);
        if (notificationObservers != null) {
            notificationObservers.remove(notificationListenerRegistrationNumber);
        }
        listenersLock.unlock();
    }


    void postEngineNotification(String notificationName, HashMap<String, Object> userInfo) {
        Logger.d("Posting engine notification with name " + notificationName);
        try {
            notificationQueue.put(new EngineNotification(notificationName, userInfo));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class NotificationWorker {
        private boolean started = false;
        private Thread thread = null;

        public synchronized void start() {
            if (started) {
                return;
            }
            started = true;
            thread = new Thread(() -> {
                while (started) {
                    EngineNotification engineNotification = null;
                    try {
                        engineNotification = notificationQueue.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (engineNotification == null) {
                        continue;
                    }

                    listenersLock.lock();
                    HashMap<Long, WeakReference<EngineNotificationListener>> notificationObservers = listeners.get(engineNotification.notificationName);
                    if (notificationObservers != null) {
                        notificationObservers = new HashMap<>(notificationObservers); // we clone the HashMap to make sure that, even outside the lock, we can iterate on the HashMap
                        listenersLock.unlock();
                        for (HashMap.Entry<Long, WeakReference<EngineNotificationListener>> entry : notificationObservers.entrySet()) {
                            EngineNotificationListener listener = entry.getValue().get();
                            if (listener == null) { // remove the listener
                                removeNotificationListener(engineNotification.notificationName, entry.getKey());
                            } else { // call callback method
                                try {
                                    listener.callback(engineNotification.notificationName, engineNotification.userInfo);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else {
                        listenersLock.unlock();
                    }
                }
            });
            thread.setName("Engine-EngineNotificationPosting");
            thread.start();
        }

        public synchronized void stop() {
            if (!started) {
                return;
            }
            started = false;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }
    }

    private static class EngineNotification {
        public final String notificationName;
        public final HashMap<String, Object> userInfo;

        EngineNotification(String notificationName, HashMap<String, Object> userInfo) {
            this.notificationName = notificationName;
            this.userInfo = userInfo;
        }
    }

    // endregion

    // region Internal Notifications Listener
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerChannelsAndProtocols notificationListenerChannelsAndProtocols;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerDownloads notificationListenerDownloads;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerIdentity notificationListenerIdentity;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerGroups notificationListenerGroups;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerGroupsV2 notificationListenerGroupsV2;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerUploads notificationListenerUploads;
    @SuppressWarnings("FieldCanBeLocal")
    private NotificationListenerBackups notificationListenerBackups;

    private void registerToInternalNotifications() {
        notificationListenerChannelsAndProtocols = new NotificationListenerChannelsAndProtocols(this);
        notificationListenerChannelsAndProtocols.registerToNotifications(this.notificationManager);

        notificationListenerDownloads = new NotificationListenerDownloads(this);
        notificationListenerDownloads.registerToNotifications(this.notificationManager);

        notificationListenerIdentity = new NotificationListenerIdentity(this);
        notificationListenerIdentity.registerToNotifications(this.notificationManager);

        notificationListenerIdentity = new NotificationListenerIdentity(this);
        notificationListenerIdentity.registerToNotifications(this.notificationManager);

        notificationListenerGroups = new NotificationListenerGroups(this);
        notificationListenerGroups.registerToNotifications(this.notificationManager);

        notificationListenerGroupsV2 = new NotificationListenerGroupsV2(this);
        notificationListenerGroupsV2.registerToNotifications(this.notificationManager);

        notificationListenerUploads = new NotificationListenerUploads(this);
        notificationListenerUploads.registerToNotifications(this.notificationManager);

        notificationListenerBackups = new NotificationListenerBackups(this);
        notificationListenerBackups.registerToNotifications(this.notificationManager);
    }

    // endregion

    // region EngineSessionFactory

    @Override
    public EngineSession getSession() throws SQLException {
        return new EngineSession(createSessionDelegate.getSession(), this, jsonObjectMapper);
    }

    EngineSession wrapSession(Session session) {
        return new EngineSession(session, this, jsonObjectMapper);
    }

    // endregion

    // region UserInterfaceDialogListener

    @Override
    public void sendUserInterfaceDialogNotification(UUID uuid, ObvDialog dialog, long creationTimestamp) {
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(EngineNotifications.UI_DIALOG_UUID_KEY, uuid);
        userInfo.put(EngineNotifications.UI_DIALOG_DIALOG_KEY, dialog);
        userInfo.put(EngineNotifications.UI_DIALOG_CREATION_TIMESTAMP_KEY, creationTimestamp);
        postEngineNotification(EngineNotifications.UI_DIALOG, userInfo);
    }

    @Override
    public void sendUserInterfaceDialogDeletionNotification(UUID uuid) {
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(EngineNotifications.UI_DIALOG_DELETED_UUID_KEY, uuid);
        postEngineNotification(EngineNotifications.UI_DIALOG_DELETED, userInfo);
    }

    ObvDialog createDialog(ChannelDialogMessageToSend channelDialogMessageToSend) {
        ObvDialog.Category category;
        Identity ownedIdentity = channelDialogMessageToSend.getSendChannelInfo().getToIdentity();

        DialogType dialogType = channelDialogMessageToSend.getSendChannelInfo().getDialogType();
        switch (dialogType.id) {
            case DialogType.INVITE_SENT_DIALOG_ID: {
                category = ObvDialog.Category.createInviteSent(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails);
                break;
            }
            case DialogType.ACCEPT_INVITE_DIALOG_ID: {
                category = ObvDialog.Category.createAcceptInvite(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, dialogType.serverTimestamp);
                break;
            }
            case DialogType.SAS_EXCHANGE_DIALOG_ID: {
                category = ObvDialog.Category.createSasExchange(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, dialogType.sasToDisplay, dialogType.serverTimestamp);
                break;
            }
            case DialogType.SAS_CONFIRMED_DIALOG_ID: {
                category = ObvDialog.Category.createSasConfirmed(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, dialogType.sasToDisplay, dialogType.sasEntered);
                break;
            }
            case DialogType.INVITE_ACCEPTED_DIALOG_ID: {
                category = ObvDialog.Category.createInviteAccepted(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails);
                break;
            }
            case DialogType.ACCEPT_MEDIATOR_INVITE_DIALOG_ID: {
                byte[] bytesMediatorIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesMediatorIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
                }
                category = ObvDialog.Category.createAcceptMediatorInvite(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, bytesMediatorIdentity, dialogType.serverTimestamp);
                break;
            }
            case DialogType.MEDIATOR_INVITE_ACCEPTED_DIALOG_ID: {
                byte[] bytesMediatorIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesMediatorIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
                }
                category = ObvDialog.Category.createMediatorInviteAccepted(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, bytesMediatorIdentity);
                break;
            }
            case DialogType.ACCEPT_GROUP_INVITE_DIALOG_ID: {
                byte[] bytesGroupOwnedIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesGroupOwnedIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
                }
                ObvIdentity[] pendingGroupMemberIdentities = new ObvIdentity[dialogType.pendingGroupMemberIdentities.length];
                for (int i = 0; i < pendingGroupMemberIdentities.length; i++) {
                    try {
                        JsonIdentityDetails identityDetails = jsonObjectMapper.readValue(dialogType.pendingGroupMemberSerializedDetails[i], JsonIdentityDetails.class);
                        pendingGroupMemberIdentities[i] = new ObvIdentity(dialogType.pendingGroupMemberIdentities[i], identityDetails, false, true);
                    } catch (Exception e) {
                        break;
                    }
                }
                category = ObvDialog.Category.createAcceptGroupInvite(dialogType.serializedGroupDetails, dialogType.groupUid.getBytes(), bytesGroupOwnedIdentity, pendingGroupMemberIdentities, dialogType.serverTimestamp);
                break;
            }
            case DialogType.ONE_TO_ONE_INVITATION_SENT_DIALOG_ID: {
                category = ObvDialog.Category.createOneToOneInvitationSent(dialogType.contactIdentity.getBytes());
                break;
            }
            case DialogType.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_ID: {
                category = ObvDialog.Category.createAcceptOneToOneInvitation(dialogType.contactIdentity.getBytes(), dialogType.serverTimestamp);
                break;
            }
            case DialogType.ACCEPT_GROUP_V2_INVITATION_DIALOG_ID: {
                category = ObvDialog.Category.createGroupV2Invitation(dialogType.mediatorOrGroupOwnerIdentity.getBytes(), dialogType.obvGroupV2);
                break;
            }
            case DialogType.GROUP_V2_FROZEN_INVITATION_DIALOG_ID: {
                category = ObvDialog.Category.createGroupV2FrozenInvitation(dialogType.mediatorOrGroupOwnerIdentity.getBytes(), dialogType.obvGroupV2);
                break;
            }
            case DialogType.SYNC_ITEM_TO_APPLY_DIALOG_ID: {
                category = ObvDialog.Category.createSyncItemToApply(dialogType.obvSyncAtom);
                break;
            }
            case DialogType.TRANSFER_DIALOG_ID: {
                category = ObvDialog.Category.createTransferDialog(dialogType.obvTransferStep);
                break;
            }
            default:
                Logger.w("Unknown DialogType " + dialogType.id);
                return null;
        }
        return new ObvDialog(channelDialogMessageToSend.getUuid(), channelDialogMessageToSend.getEncodedElements(), ownedIdentity.getBytes(), category);
    }

    // endregion

    // region EngineOwnedIdentityCleanupDelegate

    @Override
    public void deleteOwnedIdentityFromInboxOutboxProtocolsAndDialogs(Session session, Identity ownedIdentity, UID excludedProtocolInstanceUid) throws Exception {
        protocolManager.deleteOwnedIdentity(session, ownedIdentity, excludedProtocolInstanceUid);
        sendManager.deleteOwnedIdentity(session, ownedIdentity);
        // do not delete the server session if called with a non-null excludedProtocolInstanceUid
        //  --> this server session is used in the OwnedIdentityDeletionProtocol to run a server query
        fetchManager.deleteOwnedIdentity(session, ownedIdentity, excludedProtocolInstanceUid != null);

        for (UserInterfaceDialog userInterfaceDialog : UserInterfaceDialog.getAll(wrapSession(session))) {
            ObvDialog obvDialog = userInterfaceDialog.getObvDialog();
            if (Arrays.equals(obvDialog.getBytesOwnedIdentity(), ownedIdentity.getBytes())) {
                userInterfaceDialog.delete();
            }
        }
    }

    @Override
    public void deleteOwnedIdentityServerSession(Session session, Identity ownedIdentity) {
        fetchManager.deleteExistingServerSession(session, ownedIdentity, false);
    }
    // endregion


    // region Public API
    // region Managing Owned Identities


    @Override
    public String getServerOfIdentity(byte[] bytesIdentity) {
        try {
            Identity identity = Identity.of(bytesIdentity);
            return identity.getServer();
        } catch (DecodingException e) {
            // nothing
        }
        return null;
    }

    @Override
    public ObvIdentity[] getOwnedIdentities() throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity[] identities = identityManager.getOwnedIdentities(engineSession.session);
            ObvIdentity[] ownedIdentities = new ObvIdentity[identities.length];

            for (int i = 0; i < identities.length; i++) {
                ownedIdentities[i] = new ObvIdentity(engineSession.session, identityManager, identities[i]);
            }
            return ownedIdentities;
        }
    }

    @Override
    public ObvIdentity getOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return new ObvIdentity(engineSession.session, identityManager, ownedIdentity);
        }
    }

    @Override
    public ObvIdentity generateOwnedIdentity(String server, JsonIdentityDetails identityDetails, ObvKeycloakState keycloakState, String deviceDisplayName) {
        try (EngineSession engineSession = getSession()) {
            if (server == null) {
                server = "";
            }
            Identity identity = identityManager.generateOwnedIdentity(engineSession.session, server, identityDetails, keycloakState, deviceDisplayName, prng);
            if (identity == null) {
                return null;
            }

            ObvIdentity ownedIdentity = new ObvIdentity(identity, identityDetails, keycloakState != null, true);
            engineSession.session.commit();
            return ownedIdentity;
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    public RegisterApiKeyResult registerOwnedIdentityApiKeyOnServer(byte[] bytesOwnedIdentity, UUID apiKey) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            byte[] serverSessionToken = fetchManager.getServerAuthenticationToken(ownedIdentity);
            if (serverSessionToken == null) {
                fetchManager.createServerSession(ownedIdentity);
                return RegisterApiKeyResult.WAIT_FOR_SERVER_SESSION;
            }

            StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, ownedIdentity, new ServerQuery.RegisterApiKeyQuery(ownedIdentity, serverSessionToken, Logger.getUuidString(apiKey))));

            OperationQueue queue = new OperationQueue();
            queue.queue(standaloneServerQueryOperation);
            queue.execute(1, "Engine-registerOwnedIdentityApiKeyOnServer");
            queue.join();

            if (standaloneServerQueryOperation.isFinished()) {
                recreateServerSession(bytesOwnedIdentity);
                return RegisterApiKeyResult.SUCCESS;
            } else {
                if (standaloneServerQueryOperation.getReasonForCancel() != null) {
                    switch (standaloneServerQueryOperation.getReasonForCancel()) {
                        case StandaloneServerQueryOperation.RFC_INVALID_API_KEY: {
                            return RegisterApiKeyResult.INVALID_KEY;
                        }
                        case StandaloneServerQueryOperation.RFC_INVALID_SERVER_SESSION: {
                            recreateServerSession(bytesOwnedIdentity);
                            return RegisterApiKeyResult.WAIT_FOR_SERVER_SESSION;
                        }
                        case StandaloneServerQueryOperation.RFC_UNSUPPORTED_SERVER_QUERY_TYPE:
                        case StandaloneServerQueryOperation.RFC_NETWORK_ERROR:
                        default: {
                            break;
                        }
                    }
                }
                return RegisterApiKeyResult.FAILED;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return RegisterApiKeyResult.FAILED;
        }
    }

    @Override
    public void updateKeycloakPushTopicsIfNeeded(byte[] bytesOwnedIdentity, String serverUrl, List<String> pushTopics) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            boolean updated = identityManager.updateKeycloakPushTopicsIfNeeded(engineSession.session, ownedIdentity, serverUrl, pushTopics);
            engineSession.session.commit();

            if (updated) {
                fetchManager.forceRegisterPushNotification(ownedIdentity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateKeycloakRevocationList(byte[] bytesOwnedIdentity, long latestRevocationListTimestamp, List<String> signedRevocations) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            engineSession.session.startTransaction();
            identityManager.verifyAndAddRevocationList(engineSession.session, ownedIdentity, signedRevocations);
            identityManager.setKeycloakLatestRevocationListTimestamp(engineSession.session, ownedIdentity, latestRevocationListTimestamp);
            engineSession.session.commit();
            // commit once to get out of the transaction fast
            identityManager.unCertifyExpiredSignedContactDetails(engineSession.session, ownedIdentity, latestRevocationListTimestamp);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl, String nonce) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.setOwnedIdentityKeycloakSelfRevocationTestNonce(engineSession.session, ownedIdentity, serverUrl, nonce);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnedIdentityKeycloakSelfRevocationTestNonce(engineSession.session, ownedIdentity, serverUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // returns true if the update was successful
    @Override
    public boolean updateKeycloakGroups(byte[] bytesOwnedIdentity, List<String> signedGroupBlobs, List<String> signedGroupDeletions, List<String> signedGroupKicks, long keycloakCurrentTimestamp) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            boolean success = false;
            try {
                engineSession.session.startTransaction();
                identityManager.updateKeycloakGroups(engineSession.session, ownedIdentity, signedGroupBlobs, signedGroupDeletions, signedGroupKicks, keycloakCurrentTimestamp);
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (success) {
                    engineSession.session.commit();
                } else {
                    engineSession.session.rollback();
                }
            }
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void recreateServerSession(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.deleteExistingServerSession(engineSession.session, ownedIdentity, true);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            engineSession.session.startTransaction();
            channelManager.deleteAllChannelsForOwnedIdentity(engineSession.session, ownedIdentity);
            identityManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);

            deleteOwnedIdentityFromInboxOutboxProtocolsAndDialogs(engineSession.session, ownedIdentity, null);

            engineSession.session.commit();
        }
    }


    @Override
    public JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnedIdentityPublishedAndLatestDetails(engineSession.session, ownedIdentity);
        }
    }


    @Override
    public ObvKeycloakState getOwnedIdentityKeycloakState(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnedIdentityKeycloakState(engineSession.session, ownedIdentity);
        }
    }

    @Override
    public void saveKeycloakAuthState(byte[] bytesOwnedIdentity, String serializedAuthState) throws Exception {
        Logger.d("Saving keycloak authState in Engine");
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.saveKeycloakAuthState(engineSession.session, ownedIdentity, serializedAuthState);
            engineSession.session.commit();
        }
    }

    @Override
    public void saveKeycloakJwks(byte[] bytesOwnedIdentity, String serializedJwks) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.saveKeycloakJwks(engineSession.session, ownedIdentity, serializedJwks);
            engineSession.session.commit();
        }
    }

    @Override
    public void saveKeycloakApiKey(byte[] bytesOwnedIdentity, String apiKey) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.saveKeycloakApiKey(engineSession.session, ownedIdentity, apiKey);
            engineSession.session.commit();
        }
    }

    @Override
    public Collection<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(String pushTopic) throws Exception {
        try (EngineSession engineSession = getSession()) {
            return identityManager.getOwnedIdentitiesWithKeycloakPushTopic(engineSession.session, pushTopic);
        }
    }

    @Override
    public String getOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnedIdentityKeycloakUserId(engineSession.session, ownedIdentity);
        }
    }

    @Override
    public void setOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity, String userId) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.setOwnedIdentityKeycloakUserId(engineSession.session, ownedIdentity, userId);
            engineSession.session.commit();
        }
    }

    @Override
    public JsonWebKey getOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnedIdentityKeycloakSignatureKey(engineSession.session, ownedIdentity);
        }
    }

    @Override
    public void setOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity, JsonWebKey signatureKey) throws Exception {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.setOwnedIdentityKeycloakSignatureKey(engineSession.session, ownedIdentity, signatureKey);
            identityManager.reCheckAllCertifiedByOwnKeycloakContacts(engineSession.session, ownedIdentity);
            engineSession.session.commit();
        }
    }

    @Override
    public ObvIdentity bindOwnedIdentityToKeycloak(byte[] bytesOwnedIdentity, ObvKeycloakState keycloakState, String keycloakUserId) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            protocolManager.startProtocolForBindingOwnedIdentityToKeycloakWithinTransaction(engineSession.session, ownedIdentity, keycloakState, keycloakUserId);
            ObvIdentity obvIdentity = new ObvIdentity(engineSession.session, identityManager, ownedIdentity);
            engineSession.session.commit();
            return obvIdentity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void unbindOwnedIdentityFromKeycloak(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            protocolManager.startProtocolForUnbindingOwnedIdentityFromKeycloak(ownedIdentity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerToPushNotification(byte[] bytesOwnedIdentity, ObvPushNotificationType pushNotificationType, boolean reactivateCurrentDevice, byte[] bytesDeviceUidToReplace) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            UID currentDeviceUid = identityManager.getCurrentDeviceUidOfOwnedIdentity(engineSession.session, ownedIdentity);
            UID deviceUidToReplace = bytesDeviceUidToReplace == null ? null : new UID(bytesDeviceUidToReplace);

            PushNotificationTypeAndParameters pushNotificationTypeAndParameters;
            switch (pushNotificationType.platform) {
                case ANDROID: {
                    if (pushNotificationType.firebaseToken == null) {
                        pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createWebsocketOnlyAndroid(reactivateCurrentDevice, deviceUidToReplace);
                    } else {
                        // We pick a random identityMaskingUid in case we need to register (only useful when configuration changed)
                        UID identityMaskingUid = new UID(prng);
                        byte[] firebaseTokenBytes = pushNotificationType.firebaseToken.getBytes(StandardCharsets.UTF_8);
                        pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createFirebaseAndroid(firebaseTokenBytes, identityMaskingUid, reactivateCurrentDevice, deviceUidToReplace);
                    }
                    break;
                }
                case WINDOWS: {
                    pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createWindows(reactivateCurrentDevice, deviceUidToReplace);
                    break;
                }
                case LINUX: {
                    pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createLinux(reactivateCurrentDevice, deviceUidToReplace);
                    break;
                }
                case DAEMON: {
                    pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createDaemon(reactivateCurrentDevice, deviceUidToReplace);
                    break;
                }
                default: {
                    Logger.e("Engine.registerToPushNotification: unknown pushNotificationType.platform");
                    throw new Exception();
                }
            }
            engineSession.session.startTransaction();
            fetchManager.registerPushNotificationIfConfigurationChanged(engineSession.session, ownedIdentity, currentDeviceUid, pushNotificationTypeAndParameters);
            engineSession.session.commit();
        }
    }


    @Override
    public void processAndroidPushNotification(String maskingUidString) {
        fetchManager.processAndroidPushNotification(maskingUidString);
    }

    @Override
    public byte[] getOwnedIdentityFromMaskingUid(String maskingUidString) {
        Identity ownedIdentity = fetchManager.getOwnedIdentityFromMaskingUid(maskingUidString);
        if (ownedIdentity != null) {
            return ownedIdentity.getBytes();
        }
        return null;
    }

    @Override
    public void processDeviceManagementRequest(byte[] bytesOwnedIdentity, ObvDeviceManagementRequest deviceManagementRequest) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            protocolManager.processDeviceManagementRequest(engineSession.session, ownedIdentity, deviceManagementRequest);
            engineSession.session.commit();
        }
    }

    @Override
    public void updateLatestIdentityDetails(byte[] bytesOwnedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            identityManager.updateLatestIdentityDetails(engineSession.session, ownedIdentity, jsonIdentityDetails);
            engineSession.session.commit();
        }
    }

    @Override
    public void discardLatestIdentityDetails(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.discardLatestIdentityDetails(engineSession.session, ownedIdentity);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void publishLatestIdentityDetails(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            int version = identityManager.publishLatestIdentityDetails(engineSession.session, ownedIdentity);
            if (version != -1) {
                protocolManager.startIdentityDetailsPublicationProtocol(engineSession.session, ownedIdentity, version);
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateOwnedIdentityPhoto(byte[] bytesOwnedIdentity, String absolutePhotoUrl) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            identityManager.updateOwnedIdentityPhoto(engineSession.session, ownedIdentity, absolutePhotoUrl);
            engineSession.session.commit();
        }
    }

    @Override
    public byte[] getServerAuthenticationToken(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return fetchManager.getServerAuthenticationToken(ownedIdentity);
        } catch (DecodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // returns null in case of error, empty list if there are no capabilities
    @Override
    public List<ObvCapability> getOwnCapabilities(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getOwnCapabilities(ownedIdentity);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<ObvOwnedDevice> getOwnedDevices(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getDevicesOfOwnedIdentity(engineSession.session, ownedIdentity);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ObvDeviceList queryRegisteredOwnedDevicesFromServer(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

            StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, ownedIdentity, new ServerQuery.OwnedDeviceDiscoveryQuery(ownedIdentity)));

            OperationQueue queue = new OperationQueue();
            queue.queue(standaloneServerQueryOperation);
            queue.execute(1, "Engine-queryRegisterOwnedDevicesFromServer");
            queue.join();

            if (standaloneServerQueryOperation.isFinished() && standaloneServerQueryOperation.getServerResponse() != null) {
                // decrypt the received device list
                byte[] decryptedPayload = identityManager.decrypt(engineSession.session, standaloneServerQueryOperation.getServerResponse().decodeEncryptedData(), ownedIdentity);

                HashMap<DictionaryKey, Encoded> map = new Encoded(decryptedPayload).decodeDictionary();

                // check for multi-device (is null if server could not determine if multi-device is available)
                Encoded encodedMulti = map.get(new DictionaryKey("multi"));
                Boolean multiDevice;
                if (encodedMulti != null) {
                    multiDevice = encodedMulti.decodeBoolean();
                } else {
                    multiDevice = null;
                }

                // now get the actual device list
                HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo = new HashMap<>();

                Encoded[] encodedDevices = map.get(new DictionaryKey("dev")).decodeList();
                for (Encoded encodedDevice : encodedDevices) {
                    HashMap<DictionaryKey, Encoded> deviceMap = encodedDevice.decodeDictionary();
                    UID deviceUid = deviceMap.get(new DictionaryKey("uid")).decodeUid();

                    Encoded encodedExpiration = deviceMap.get(new DictionaryKey("exp"));
                    Long expirationTimestamp = encodedExpiration == null ? null : encodedExpiration.decodeLong();

                    Encoded encodedRegistration = deviceMap.get(new DictionaryKey("reg"));
                    Long lastRegistrationTimestamp = encodedRegistration == null ? null : encodedRegistration.decodeLong();

                    Encoded encodedName = deviceMap.get(new DictionaryKey("name"));
                    String deviceName = null;
                    if (encodedName != null) {
                        try {
                            byte[] plaintext = identityManager.decrypt(engineSession.session, encodedName.decodeEncryptedData(), ownedIdentity);
                            byte[] bytesDeviceName = new Encoded(plaintext).decodeListWithPadding()[0].decodeBytes();
                            if (bytesDeviceName.length != 0) {
                                deviceName = new String(bytesDeviceName, StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    deviceUidsAndServerInfo.put(new ObvBytesKey(deviceUid.getBytes()), new ObvOwnedDevice.ServerDeviceInfo(deviceName, expirationTimestamp, lastRegistrationTimestamp));
                }

                return new ObvDeviceList(multiDevice, deviceUidsAndServerInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void refreshOwnedDeviceList(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            protocolManager.startOwnedDeviceDiscoveryProtocol(ownedIdentity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void recreateOwnedDeviceChannel(byte[] bytesOwnedIdentity, byte[] bytesDeviceUid) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            UID deviceUid = new UID(bytesDeviceUid);
            // simply start the channel creation protocol: this deletes any channel and aborts any ongoing instance
            protocolManager.startChannelCreationWithOwnedDeviceProtocol(ownedIdentity, deviceUid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void resynchronizeAllOwnedDevices(byte[] bytesOwnedIdentity) {
//        try (EngineSession engineSession = getSession()) {
//            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
//            protocolManager.triggerOwnedDevicesSync(engineSession.session, ownedIdentity);
//            engineSession.session.commit();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    // endregion

    // region Managing Contact Identities

    @Override
    public ObvIdentity[] getContactsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity[] identities = identityManager.getContactsOfOwnedIdentity(engineSession.session, ownedIdentity);
            ObvIdentity[] contactIdentities = new ObvIdentity[identities.length];

            for (int i = 0; i < identities.length; i++) {
                contactIdentities[i] = new ObvIdentity(engineSession.session, identityManager, identities[i], ownedIdentity);
            }
            return contactIdentities;
        }
    }


    @Override
    public EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            return identityManager.getContactActiveOrInactiveReasons(engineSession.session, ownedIdentity, contactIdentity);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean forcefullyUnblockContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            boolean success = identityManager.forcefullyUnblockContact(engineSession.session, ownedIdentity, contactIdentity);
            engineSession.session.commit();
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean reBlockForcefullyUnblockedContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            boolean success = identityManager.reBlockForcefullyUnblockedContact(engineSession.session, ownedIdentity, contactIdentity);
            engineSession.session.commit();
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isContactOneToOne(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.isIdentityAOneToOneContactOfOwnedIdentity(engineSession.session, ownedIdentity, contactIdentity);
        }
    }

    @Override
    public int getContactDeviceCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            return identityManager.getDeviceUidsOfContactIdentity(engineSession.session, ownedIdentity, contactIdentity).length;
        }
    }

    @Override
    public int getContactEstablishedChannelsCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return channelManager.getConfirmedObliviousChannelDeviceUids(engineSession.session, ownedIdentity, contactIdentity).length;
        }
    }

    @Override
    public String getContactTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getContactTrustedDetailsPhotoUrl(engineSession.session, ownedIdentity, contactIdentity);
        }
    }

    @Override
    public JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getContactPublishedAndTrustedDetails(engineSession.session, ownedIdentity, contactIdentity);
        }
    }

    @Override
    public void trustPublishedContactDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            JsonIdentityDetailsWithVersionAndPhoto details = identityManager.trustPublishedContactDetails(engineSession.session, contactIdentity, ownedIdentity);
            if (details != null) {
                propagateEngineSyncAtomToOtherDevicesIfNeeded(engineSession.session, ownedIdentity, ObvSyncAtom.createTrustContactDetails(contactIdentity, jsonObjectMapper.writeValueAsString(details)));
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ObvTrustOrigin[] getContactTrustOrigins(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        ObvTrustOrigin[] obvTrustOrigins;
        try (EngineSession engineSession = getSession()) {
            TrustOrigin[] trustOrigins = identityManager.getTrustOriginsOfContactIdentity(engineSession.session, ownedIdentity, contactIdentity);
            obvTrustOrigins = new ObvTrustOrigin[trustOrigins.length];
            for (int i=0; i<trustOrigins.length; i++) {
                obvTrustOrigins[i] = new ObvTrustOrigin(engineSession.session, identityManager, trustOrigins[i], ownedIdentity);
            }
        }
        return obvTrustOrigins;
    }

    @Override
    public int getContactTrustLevel(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);

        try (EngineSession engineSession = getSession()) {
            TrustLevel contactTrustLevel = identityManager.getContactTrustLevel(engineSession.session, ownedIdentity, contactIdentity);
            if (contactTrustLevel != null) {
                return contactTrustLevel.major;
            } else {
                return 0;
            }
        }
    }

    // returns null in case of error, empty list if there are no capabilities
    @Override
    public List<ObvCapability> getContactCapabilities(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            return identityManager.getContactCapabilities(ownedIdentity, contactIdentity);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    // endregion
    // region ObvGroup

    @Override
    public ObvGroup[] getGroupsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            GroupWithDetails[] groups = identityManager.getGroupsForOwnedIdentity(engineSession.session, ownedIdentity);
            ObvGroup[] obvGroups = new ObvGroup[groups.length];

            for (int i = 0; i < groups.length; i++) {
                byte[][] byteContactIdentities = new byte[groups[i].getGroupMembers().length][];
                for (int j=0; j<byteContactIdentities.length; j++) {
                    byteContactIdentities[j] = groups[i].getGroupMembers()[j].getBytes();
                }
                ObvIdentity[] pendingMembers = new ObvIdentity[groups[i].getPendingGroupMembers().length];
                for (int j=0; j<pendingMembers.length; j++) {
                    try {
                        JsonIdentityDetails identityDetails = identityManager.getJsonObjectMapper().readValue(groups[i].getPendingGroupMembers()[j].serializedDetails, JsonIdentityDetails.class);
                        pendingMembers[j] = new ObvIdentity(groups[i].getPendingGroupMembers()[j].identity, identityDetails, false, true);
                    } catch (IOException e) {
                        pendingMembers[j] = new ObvIdentity(groups[i].getPendingGroupMembers()[j].identity, null, false, true);
                    }
                }
                byte[][] bytesDeclinesPendingMembers = new byte[groups[i].getDeclinedPendingMembers().length][];
                for (int j=0; j<bytesDeclinesPendingMembers.length; j++) {
                    bytesDeclinesPendingMembers[j] = groups[i].getDeclinedPendingMembers()[j].getBytes();
                }
                if (groups[i].getGroupOwner() == null) {
                    obvGroups[i] = new ObvGroup(
                            groups[i].getGroupOwnerAndUid(),
                            groups[i].getPublishedGroupDetails(),
                            ownedIdentity.getBytes(),
                            byteContactIdentities,
                            pendingMembers,
                            bytesDeclinesPendingMembers,
                            null
                    );
                } else {
                    obvGroups[i] = new ObvGroup(
                            groups[i].getGroupOwnerAndUid(),
                            groups[i].getLatestOrTrustedGroupDetails(),
                            ownedIdentity.getBytes(),
                            byteContactIdentities,
                            pendingMembers,
                            bytesDeclinesPendingMembers,
                            groups[i].getGroupOwner().getBytes()
                    );
                }
            }
            return obvGroups;
        }
    }

    @Override
    public JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getGroupPublishedAndLatestOrTrustedDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
        }
    }

    @Override
    public String getGroupTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getGroupPhotoUrl(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
        }
    }

    @Override
    public void trustPublishedGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            JsonGroupDetailsWithVersionAndPhoto details = identityManager.trustPublishedGroupDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
            if (details != null) {
                propagateEngineSyncAtomToOtherDevicesIfNeeded(engineSession.session, ownedIdentity, ObvSyncAtom.createTrustGroupV1Details(bytesGroupOwnerAndUid, jsonObjectMapper.writeValueAsString(details)));
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            identityManager.updateLatestGroupDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid, jsonGroupDetails);
            engineSession.session.commit();
        }
    }

    @Override
    public void discardLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.discardLatestGroupDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void publishLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            int version = identityManager.publishLatestGroupDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
            if (version != -1) {
                protocolManager.startGroupDetailsPublicationProtocol(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateOwnedGroupPhoto(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String absolutePhotoUrl) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            identityManager.updateOwnedGroupPhoto(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid, absolutePhotoUrl, false);
            engineSession.session.commit();
        }
    }

    // endregion
    // region Groups V2


    @Override
    public List<ObvGroupV2> getGroupsV2OfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        try (EngineSession engineSession = getSession()) {
            return identityManager.getObvGroupsV2ForOwnedIdentity(engineSession.session, ownedIdentity);
        }
    }

    @Override
    public void trustGroupV2PublishedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);
        try (EngineSession engineSession = getSession()) {
            int version = identityManager.trustGroupV2PublishedDetails(engineSession.session, ownedIdentity, groupIdentifier);
            if (version != -1) {
                propagateEngineSyncAtomToOtherDevicesIfNeeded(engineSession.session, ownedIdentity, ObvSyncAtom.createTrustGroupV2Details(groupIdentifier, version));
            }
            engineSession.session.commit();
        }
    }

    @Override
    public String getGroupV2JsonType(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            return null;
        }

        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);

            try (EngineSession engineSession = getSession()) {
                return identityManager.getGroupV2JsonGroupType(engineSession.session, ownedIdentity, groupIdentifier);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ObvGroupV2.ObvGroupV2DetailsAndPhotos getGroupV2DetailsAndPhotos(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            return null;
        }

        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);

            try (EngineSession engineSession = getSession()) {
                return identityManager.getGroupV2DetailsAndPhotos(engineSession.session, ownedIdentity, groupIdentifier);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }


    @Override
    public void initiateGroupV2Update(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet) throws Exception {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);

        protocolManager.initiateGroupV2Update(ownedIdentity, groupIdentifier, changeSet);
    }

    @Override
    public void leaveGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);
        if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            // it is not possible to leave a keycloak group
            return;
        }

        protocolManager.initiateGroupV2Leave(ownedIdentity, groupIdentifier);
    }

    @Override
    public void disbandGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);
        if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
            // it is not possible to leave a keycloak group
            return;
        }

        protocolManager.initiateGroupV2Disband(ownedIdentity, groupIdentifier);
    }

    @Override
    public void reDownloadGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);

        protocolManager.initiateGroupV2ReDownload(ownedIdentity, groupIdentifier);
    }

    @Override
    public Integer getGroupV2Version(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception {
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(bytesGroupIdentifier);

        try (EngineSession engineSession = getSession()) {
            return identityManager.getGroupV2Version(engineSession.session, ownedIdentity, groupIdentifier);
        }
    }

    @Override
    public boolean isGroupV2UpdateInProgress(byte[] bytesOwnedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (bytesOwnedIdentity == null || groupIdentifier == null) {
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

        try (EngineSession engineSession = getSession()) {
            return identityManager.isGroupV2Frozen(engineSession.session, ownedIdentity, groupIdentifier);
        }
    }

    // endregion
    // region ObvDialog

    @Override
    public void deletePersistedDialog(UUID uuid) throws Exception {
        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog dialog = UserInterfaceDialog.get(engineSession, uuid);
            if (dialog != null) {
                dialog.delete();
                engineSession.session.commit();
            }
        }
    }

    @Override
    public Set<UUID> getAllPersistedDialogUuids() throws Exception {
        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog[] dialogs = UserInterfaceDialog.getAll(engineSession);
            Set<UUID> obvDialogUuids = new HashSet<>();
            for (UserInterfaceDialog dialog : dialogs) {
                obvDialogUuids.add(dialog.getUuid());
            }
            return obvDialogUuids;
        }
    }

    @Override
    public void resendAllPersistedDialogs() throws Exception {
        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog[] dialogs = UserInterfaceDialog.getAll(engineSession);
            for (UserInterfaceDialog dialog : dialogs) {
                dialog.resend();
            }
        }
    }

    @Override
    public void respondToDialog(ObvDialog dialog) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(dialog.getBytesOwnedIdentity());
            ChannelDialogResponseMessageToSend responseMessageToSend = new ChannelDialogResponseMessageToSend(
                    dialog.getUuid(),
                    ownedIdentity,
                    dialog.getEncodedResponse(),
                    dialog.getEncodedElements()
            );

            engineSession.session.startTransaction();
            channelManager.post(engineSession.session, responseMessageToSend, prng);
            engineSession.session.commit();
        }
    }

    @Override
    public void abortProtocol(ObvDialog dialog) throws Exception {
        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog userInterfaceDialog = UserInterfaceDialog.get(engineSession, dialog.getUuid());

            UID protocolInstanceUid = dialog.getEncodedElements().decodeList()[1].decodeUid();
            Identity ownedIdentity = Identity.of(dialog.getBytesOwnedIdentity());

            engineSession.session.startTransaction();
            userInterfaceDialog.delete();
            protocolManager.abortProtocol(engineSession.session, protocolInstanceUid, ownedIdentity);
            engineSession.session.commit();
        }
    }
    // endregion


    // region Start protocols

    @Override
    public void startTrustEstablishmentProtocol(byte[] bytesRemoteIdentity, String contactDisplayName, byte[] bytesOwnedIdentity) throws Exception {
        Identity remoteIdentity = Identity.of(bytesRemoteIdentity);
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.startTrustEstablishmentProtocol(ownedIdentity, remoteIdentity, contactDisplayName);
    }

    @Override
    public ObvMutualScanUrl computeMutualScanSignedNonceUrl(byte[] bytesRemoteIdentity, byte[] bytesOwnedIdentity, String ownDisplayName) throws Exception {
        Identity contactIdentity = Identity.of(bytesRemoteIdentity);
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

        try (EngineSession engineSession = getSession()) {
            byte[] signature = identityManager.signIdentities(engineSession.session, Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{contactIdentity, ownedIdentity}, ownedIdentity, prng);
            return new ObvMutualScanUrl(ownedIdentity, ownDisplayName, signature);
        }
    }

    public boolean verifyMutualScanSignedNonceUrl(byte[] bytesOwnedIdentity, ObvMutualScanUrl mutualScanUrl) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

            return Signature.verify(Constants.SignatureContext.MUTUAL_SCAN, new Identity[]{ownedIdentity, mutualScanUrl.identity}, mutualScanUrl.identity, mutualScanUrl.signature);
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public void startMutualScanTrustEstablishmentProtocol(byte[] bytesOwnedIdentity, byte[] bytesRemoteIdentity, byte[] signature) throws Exception {
        Identity contactIdentity = Identity.of(bytesRemoteIdentity);
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.startMutualScanTrustEstablishmentProtocol(ownedIdentity, contactIdentity, signature);
    }

    @Override
    public void startContactMutualIntroductionProtocol(byte[] bytesOwnedIdentity, byte[] bytesContactIdentityA, byte[][] bytesContactIdentities) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentityA = Identity.of(bytesContactIdentityA);
        Identity[] contactIdentities = new Identity[bytesContactIdentities.length];
        for (int i=0; i<bytesContactIdentities.length; i++) {
            contactIdentities[i] = Identity.of(bytesContactIdentities[i]);
            if (contactIdentityA.equals(contactIdentities[i])) {
                throw new Exception();
            }
        }
        protocolManager.startContactMutualIntroductionProtocol(ownedIdentity, contactIdentityA, contactIdentities);
    }

    @Override
    public void startGroupCreationProtocol(String serializedGroupDetailsWithVersionAndPhoto, String absolutePhotoUrl, byte[] bytesOwnedIdentity, byte[][] bytesRemoteIdentities) throws Exception {
        if (bytesOwnedIdentity == null || bytesRemoteIdentities == null) {
            throw new Exception();
        }

        HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndDisplayNames = new HashSet<>();
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

        try (EngineSession engineSession = getSession()) {
            for (byte[] bytesRemoteIdentity : bytesRemoteIdentities) {
                Identity remoteIdentity = Identity.of(bytesRemoteIdentity);
                String serializedDetails = identityManager.getSerializedPublishedDetailsOfContactIdentity(engineSession.session, ownedIdentity, remoteIdentity);
                groupMemberIdentitiesAndDisplayNames.add(new IdentityWithSerializedDetails(remoteIdentity, serializedDetails));
            }
        }
        protocolManager.startGroupCreationProtocol(ownedIdentity, serializedGroupDetailsWithVersionAndPhoto, absolutePhotoUrl, groupMemberIdentitiesAndDisplayNames);
    }


    @Override
    public void startGroupV2CreationProtocol(String serializedGroupDetails, String absolutePhotoUrl, byte[] bytesOwnedIdentity, HashSet<GroupV2.Permission> ownPermissions, HashMap<ObvBytesKey, HashSet<GroupV2.Permission>> otherGroupMembers, String serializedGroupType) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

        HashSet<GroupV2.IdentityAndPermissions> otherGroupMembersSet = new HashSet<>();
        for (Map.Entry<ObvBytesKey, HashSet<GroupV2.Permission>> entry : otherGroupMembers.entrySet()) {
            Identity remoteIdentity = Identity.of(entry.getKey().getBytes());
            otherGroupMembersSet.add(new GroupV2.IdentityAndPermissions(remoteIdentity, entry.getValue()));
        }

        protocolManager.startGroupV2CreationProtocol(ownedIdentity, serializedGroupDetails, absolutePhotoUrl, ownPermissions, otherGroupMembersSet, serializedGroupType);
    }

    @Override
    public void restartAllOngoingChannelEstablishmentProtocols(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            UID[] deviceUids = identityManager.getDeviceUidsOfContactIdentity(engineSession.session, ownedIdentity, contactIdentity);
            HashSet<UID> confirmedDeviceUids = new HashSet<>(Arrays.asList(channelManager.getConfirmedObliviousChannelDeviceUids(engineSession.session, ownedIdentity, contactIdentity)));

            for (UID deviceUid: deviceUids) {
                if (!confirmedDeviceUids.contains(deviceUid)) {
                    identityManager.removeDeviceForContactIdentity(engineSession.session, ownedIdentity, contactIdentity, deviceUid);
                }
            }
            protocolManager.startDeviceDiscoveryProtocolWithinTransaction(engineSession.session, ownedIdentity, contactIdentity);
            engineSession.session.commit();
        }
    }

    @Override
    public void recreateAllChannels(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            channelManager.deleteObliviousChannelsWithContact(engineSession.session, ownedIdentity, contactIdentity);
            identityManager.removeAllDevicesForContactIdentity(engineSession.session, ownedIdentity, contactIdentity);
            protocolManager.startDeviceDiscoveryProtocolWithinTransaction(engineSession.session, ownedIdentity, contactIdentity);
            engineSession.session.commit();
        }
    }

    @Override
    public void inviteContactsToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesNewMemberIdentities) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        HashSet<Identity> newMembersIdentity = new HashSet<>();
        for (byte[] bytesNewMemberIdentity: bytesNewMemberIdentities) {
            newMembersIdentity.add(Identity.of(bytesNewMemberIdentity));
        }
        protocolManager.inviteContactsToGroup(bytesGroupOwnerAndUid, ownedIdentity, newMembersIdentity);
    }

    @Override
    public void removeContactsFromGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesRemovedMemberIdentities) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        HashSet<Identity> removedMemberIdentities = new HashSet<>();
        for (byte[] bytesNewMemberIdentity: bytesRemovedMemberIdentities) {
            removedMemberIdentities.add(Identity.of(bytesNewMemberIdentity));
        }
        protocolManager.removeContactsFromGroup(bytesGroupOwnerAndUid, ownedIdentity, removedMemberIdentities);

    }

    @Override
    public void reinvitePendingToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[] bytesPendingMemberIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity pendingMemberIdentity = Identity.of(bytesPendingMemberIdentity);
        protocolManager.reinvitePendingToGroup(bytesGroupOwnerAndUid, ownedIdentity, pendingMemberIdentity);
    }

    @Override
    public void leaveGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.leaveGroup(bytesGroupOwnerAndUid, ownedIdentity);
    }

    @Override
    public void disbandGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.disbandGroup(bytesGroupOwnerAndUid, ownedIdentity);
    }

    @Override
    public void deleteContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        protocolManager.deleteContact(ownedIdentity, contactIdentity);
    }

    @Override
    public void downgradeOneToOneContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        protocolManager.downgradeOneToOneContact(ownedIdentity, contactIdentity);
    }

    @Override
    public void startOneToOneInvitationProtocol(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        protocolManager.startOneToOneInvitationProtocol(ownedIdentity, contactIdentity);
    }

    @Override
    public void deleteOwnedIdentityAndNotifyContacts(byte[] bytesOwnedIdentity, boolean deleteEverywhere) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

            // now delete contacts and leave/disband groups
            // the protocol will also delete all channels (once they are no longer used) and actually delete the owned identity
            protocolManager.startOwnedIdentityDeletionProtocol(engineSession.session, ownedIdentity, deleteEverywhere);
            engineSession.session.commit();
        }
    }

    @Override
    public void queryGroupOwnerForLatestGroupMembers(byte[] bytesGroupOwnerAndUid, byte[] bytesOwnedIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.queryGroupMembers(bytesGroupOwnerAndUid, ownedIdentity);
    }

    @Override
    public void addKeycloakContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, String signedContactDetails) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        protocolManager.addKeycloakContact(ownedIdentity, contactIdentity, signedContactDetails);
    }

    @Override
    public void initiateOwnedIdentityTransferProtocolOnSourceDevice(byte[] bytesOwnedIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.initiateOwnedIdentityTransferProtocolOnSourceDevice(ownedIdentity);
    }

    @Override
    public void initiateOwnedIdentityTransferProtocolOnTargetDevice(String deviceName) throws Exception {
        protocolManager.initiateOwnedIdentityTransferProtocolOnTargetDevice(deviceName);
    }

    // endregion


    // region Post/receive messages

    @Override
    public byte[] getReturnReceiptNonce() {
        return prng.bytes(Constants.RETURN_RECEIPT_NONCE_LENGTH);
    }

    @Override
    public byte[] getReturnReceiptKey() {
        AuthEncKey authEncKey = Suite.getDefaultAuthEnc(Suite.LATEST_VERSION).generateKey(prng);
        return Encoded.of(authEncKey).getBytes();
    }

    @Override
    public void deleteReturnReceipt(byte[] bytesOwnedIdentity, byte[] serverUid) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.deleteReturnReceipt(ownedIdentity, serverUid);
        } catch (DecodingException e) {
            Logger.w("DecodingException while reconstructing the ownedIdentity in deleteReturnReceipt");
            e.printStackTrace();
        }
    }

    @Override
    public ObvReturnReceipt decryptReturnReceipt(byte[] returnReceiptKey, byte[] encryptedPayload) {
        try {
            AuthEncKey authEncKey = (AuthEncKey) new Encoded(returnReceiptKey).decodeSymmetricKey();
            AuthEnc authEnc = Suite.getAuthEnc(authEncKey);
            if (authEnc != null) {
                byte[] decryptedPayload = authEnc.decrypt(authEncKey, new EncryptedBytes(encryptedPayload));
                if (decryptedPayload != null) {
                    Encoded[] list = new Encoded(decryptedPayload).decodeList();
                    if (list.length == 2) {
                        // this is for a message
                        return new ObvReturnReceipt(
                                list[0].decodeBytes(),
                                (int) list[1].decodeLong()
                        );
                    } else if (list.length == 3) {
                        // this is for an attachment
                        return new ObvReturnReceipt(
                                list[0].decodeBytes(),
                                (int) list[1].decodeLong(),
                                (int) list[2].decodeLong()
                        );
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ObvPostMessageOutput post(byte[] messagePayload, byte[] extendedMessagePayload, ObvOutboundAttachment[] outboundAttachments, List<byte[]> bytesContactIdentities, byte[] bytesOwnedIdentity, boolean hasUserContent, boolean isVoipMessage) {
        // compute contact groups by server
        HashMap<String, HashSet<Identity>> contactServersHashMap = new HashMap<>();
        for (byte[] bytesContactIdentity: bytesContactIdentities) {
            try {
                Identity contactIdentity = Identity.of(bytesContactIdentity);
                HashSet<Identity> list = contactServersHashMap.get(contactIdentity.getServer());
                if (list == null) {
                    list = new HashSet<>();
                    contactServersHashMap.put(contactIdentity.getServer(), list);
                }
                list.add(contactIdentity);
            } catch (DecodingException e) {
                e.printStackTrace();
                Logger.w("Error decoding a bytesContactIdentity while posting a message!");
            }
        }

        HashMap<ObvBytesKey, byte[]> messageIdentifierByContactIdentity = new HashMap<>();
        boolean messageSent = false;

        for (String server: contactServersHashMap.keySet()) {
            HashSet<Identity> contactIdentities = contactServersHashMap.get(server);
            if (contactIdentities == null) {
                continue;
            }
            try {
                ChannelApplicationMessageToSend.Attachment[] attachments = new ChannelApplicationMessageToSend.Attachment[outboundAttachments.length];

                for (int i = 0; i < outboundAttachments.length; i++) {
                    attachments[i] = new ChannelApplicationMessageToSend.Attachment(
                            outboundAttachments[i].getPath(),
                            false,
                            outboundAttachments[i].getAttachmentLength(),
                            outboundAttachments[i].getMetadata()
                    );
                }

                Identity ownedIdentity = Identity.of(bytesOwnedIdentity);


                ChannelApplicationMessageToSend message = new ChannelApplicationMessageToSend(
                        contactIdentities.toArray(new Identity[0]),
                        ownedIdentity,
                        messagePayload,
                        extendedMessagePayload,
                        attachments,
                        hasUserContent,
                        isVoipMessage
                );


                UID messageUid = null;
                try (EngineSession engineSession = getSession()) {
                    try {
                        engineSession.session.startTransaction();
                        messageUid = channelManager.post(engineSession.session, message, prng);
                        engineSession.session.commit();
                    } catch (Exception e) {
                        engineSession.session.rollback();
                    }
                }

                if (messageUid != null) {
                    for (Identity contactIdentity : contactIdentities) {
                        messageIdentifierByContactIdentity.put(new ObvBytesKey(contactIdentity.getBytes()), messageUid.getBytes());
                    }
                } else {
                    for (Identity contactIdentity : contactIdentities) {
                        messageIdentifierByContactIdentity.put(new ObvBytesKey(contactIdentity.getBytes()), null);
                    }
                    continue;
                }

                // message is considered SENT even if a single recipient receives it.
                messageSent = true;
            } catch (Exception e) {
                for (Identity contactIdentity : contactIdentities) {
                    messageIdentifierByContactIdentity.put(new ObvBytesKey(contactIdentity.getBytes()), null);
                }
                e.printStackTrace();
            }
        }

        return new ObvPostMessageOutput(messageSent, messageIdentifierByContactIdentity);
    }

    @Override
    public void sendReturnReceipt(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, int status, byte[] returnReceiptNonce, byte[] returnReceiptKeyBytes, Integer attachmentNumber) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            Identity contactIdentity = Identity.of(bytesContactIdentity);
            AuthEncKey returnReceiptKey = (AuthEncKey) new Encoded(returnReceiptKeyBytes).decodeSymmetricKey();
            // fetch contact deviceUids
            final UID[] deviceUids;
            // To improve: maybe find a way to send the return receipt only to the device that actually sent the message?
            if (Arrays.equals(bytesOwnedIdentity, bytesContactIdentity)) {
                deviceUids = identityManager.getOtherDeviceUidsOfOwnedIdentity(engineSession.session, ownedIdentity);
            } else {
                deviceUids = identityManager.getDeviceUidsOfContactIdentity(engineSession.session, ownedIdentity, contactIdentity);
            }
            if (deviceUids.length != 0) {
                sendManager.sendReturnReceipt(engineSession.session, ownedIdentity, contactIdentity, deviceUids, status, returnReceiptNonce, returnReceiptKey, attachmentNumber);
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isOutboxAttachmentSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int engineNumber) {
        try (EngineSession engineSession = getSession()) {
            return sendManager.isOutboxAttachmentSent(engineSession.session, Identity.of(bytesOwnedIdentity), new UID(engineMessageIdentifier), engineNumber);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isOutboxMessageSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier) {
        try (EngineSession engineSession = getSession()) {
            return sendManager.isOutboxMessageSent(engineSession.session, Identity.of(bytesOwnedIdentity), new UID(engineMessageIdentifier));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void cancelMessageSending(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier) {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            sendManager.cancelMessageSending(engineSession.session, Identity.of(bytesOwnedIdentity), new UID(engineMessageIdentifier));
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isInboxAttachmentReceived(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int attachmentNumber) {
        try (EngineSession engineSession = getSession()) {
            return fetchManager.isInboxAttachmentReceived(engineSession.session, Identity.of(bytesOwnedIdentity), new UID(engineMessageIdentifier), attachmentNumber);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void downloadMessages(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            UID currentDeviceUid = identityManager.getCurrentDeviceUidOfOwnedIdentity(engineSession.session, ownedIdentity);
            fetchManager.downloadMessages(ownedIdentity, currentDeviceUid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void downloadSmallAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        try {
            fetchManager.downloadAttachment(Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber, DownloadAttachmentPriorityCategory.WEIGHT);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.downloadSmallAttachment");
            e.printStackTrace();
        }
    }

    @Override
    public void downloadLargeAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        try {
            fetchManager.downloadAttachment(Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber, DownloadAttachmentPriorityCategory.TIMESTAMP);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.downloadLargeAttachment");
            e.printStackTrace();
        }
    }

    @Override
    public void pauseAttachmentDownload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        try {
            fetchManager.pauseDownloadAttachment(Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.pauseAttachmentDownload");
            e.printStackTrace();
        }
    }

    @Override
    public void markAttachmentForDeletion(ObvAttachment attachment) {
        markAttachmentForDeletion(attachment.getOwnedIdentity(), attachment.getMessageUid(), attachment.getNumber());
    }

    @Override
    public void markAttachmentForDeletion(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        try {
            markAttachmentForDeletion(Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.deleteAttachment");
            e.printStackTrace();
        }
    }

    @Override
    public void deleteMessageAndAttachments(byte[] bytesOwnedIdentity, byte[] messageIdentifier) {
        UID messageUid = new UID(messageIdentifier);
        Identity ownedIdentity;
        try {
            ownedIdentity = Identity.of(bytesOwnedIdentity);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.deleteMessage");
            e.printStackTrace();
            return;
        }
        try (EngineSession engineSession = getSession()) {
            fetchManager.deleteMessageAndAttachments(engineSession.session, ownedIdentity, messageUid);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void markMessageForDeletion(byte[] bytesOwnedIdentity, byte[] messageIdentifier) {
        UID messageUid = new UID(messageIdentifier);
        Identity ownedIdentity;
        try {
            ownedIdentity = Identity.of(bytesOwnedIdentity);
        } catch (DecodingException e) {
            Logger.e("Error parsing bytesOwnedIdentity in Engine.deleteMessage");
            e.printStackTrace();
            return;
        }
        try (EngineSession engineSession = getSession()) {
            fetchManager.deleteMessage(engineSession.session, ownedIdentity, messageUid);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void markAttachmentForDeletion(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        if (ownedIdentity == null || messageUid == null) {
            return;
        }
        try (EngineSession engineSession = getSession()) {
            fetchManager.deleteAttachment(engineSession.session, ownedIdentity, messageUid, attachmentNumber);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cancelAttachmentUpload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        if (bytesOwnedIdentity == null || messageIdentifier == null) {
            return;
        }
        try (EngineSession engineSession = getSession()) {
            sendManager.cancelAttachmentUpload(engineSession.session, Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber);
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resendAllAttachmentNotifications() throws Exception {
        fetchManager.resendAllDownloadedAttachmentNotifications();
    }

    @Override
    public void connectWebsocket(boolean relyOnWebsocketForNetworkDetection, String os, String osVersion, int appBuild, String appVersion) {
        fetchManager.connectWebsockets(relyOnWebsocketForNetworkDetection, os, osVersion, appBuild, appVersion);
    }

    @Override
    public void disconnectWebsocket() {
        fetchManager.disconnectWebsockets();
    }

    @Override
    public void pingWebsocket(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.pingWebsocket(ownedIdentity);
        } catch (Exception ignored) { }
    }

    @Override
    public void retryScheduledNetworkTasks() {
        fetchManager.retryScheduledNetworkTasks();
        sendManager.retryScheduledNetworkTasks();
    }

    // endregion
    // region Backup

    @Override
    public void initiateBackup(boolean forExport) {
        backupManager.initiateBackup(forExport);
    }

    @Override
    public ObvBackupKeyInformation getBackupKeyInformation() throws Exception {
        return backupManager.getBackupKeyInformation();
    }

    @Override
    public void generateBackupKey() {
        backupManager.generateNewBackupKey();
    }

    @Override
    public void setAutoBackupEnabled(boolean enabled, boolean initiateBackupNowIfNeeded) {
        backupManager.setAutoBackupEnabled(enabled, initiateBackupNowIfNeeded);
    }

    @Override
    public void markBackupExported(byte[] backupKeyUid, int version) {
        backupManager.markBackupExported(new UID(backupKeyUid), version);
    }

    @Override
    public void markBackupUploaded(byte[] backupKeyUid, int version) {
        backupManager.markBackupUploaded(new UID(backupKeyUid), version);
    }

    @Override
    public void discardBackup(byte[] backupKeyUid, int version) {
        backupManager.discardBackup(new UID(backupKeyUid), version);
    }

    @Override
    public ObvBackupKeyVerificationOutput validateBackupSeed(String backupSeedString, byte[] backupContent) {
        int status = backupManager.validateBackupSeed(backupSeedString, backupContent);
        switch (status) {
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_SUCCESS:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_SUCCESS);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_TOO_SHORT);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_TOO_LONG);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY:
            default:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_BAD_KEY);
        }
    }

    @Override
    public ObvBackupKeyVerificationOutput verifyBackupSeed(String backupSeedString) {
        int status = backupManager.verifyBackupKey(backupSeedString);
        switch (status) {
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_SUCCESS:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_SUCCESS);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_TOO_SHORT);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_TOO_LONG);
            case BackupManager.BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY:
            default:
                return new ObvBackupKeyVerificationOutput(ObvBackupKeyVerificationOutput.STATUS_BAD_KEY);
        }
    }

    @Override
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String backupSeed, byte[] backupContent, String deviceDisplayName) {
        return backupManager.restoreOwnedIdentitiesFromBackup(backupSeed, backupContent, deviceDisplayName);
    }

    @Override
    public void restoreContactsAndGroupsFromBackup(String backupSeed, byte[] backupContent, ObvIdentity[] restoredOwnedIdentities) {
        backupManager.restoreContactsAndGroupsFromBackup(backupSeed, backupContent, restoredOwnedIdentities);
    }

    @Override
    public String decryptAppDataBackup(String backupSeed, byte[] backupContent) {
        return backupManager.decryptAppDataBackup(backupSeed, backupContent);
    }

    @Override
    public void appBackupSuccess(byte[] bytesBackupKeyUid, int version, String appBackupContent) {
        backupManager.backupSuccess(BackupManager.APP_BACKUP_TAG, new UID(bytesBackupKeyUid), version, appBackupContent);
    }

    @Override
    public void appBackupFailed(byte[] bytesBackupKeyUid, int version) {
        backupManager.backupFailed(BackupManager.APP_BACKUP_TAG, new UID(bytesBackupKeyUid), version);
    }

    // endregion

    // region Upgrade procedures and various DB stuff


    @Override
    public void getTurnCredentials(byte[] bytesOwnedIdentity, UUID callUuid, String callerUsername, String recipientUsername) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.getTurnCredentials(ownedIdentity, callUuid, callerUsername, recipientUsername);
        } catch (DecodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void queryApiKeyStatus(byte[] bytesOwnedIdentity, UUID apiKey) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.queryApiKeyStatus(ownedIdentity, apiKey);
        } catch (DecodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void queryApiKeyStatus(String server, UUID apiKey) {
        // generate a dummy identity to query the server
        EncryptionEciesMDCKeyPair anonAuthKeyPair = EncryptionEciesMDCKeyPair.generate(prng);
        ServerAuthenticationECSdsaMDCKeyPair serverAuthKeyPair = ServerAuthenticationECSdsaMDCKeyPair.generate(prng);
        if (anonAuthKeyPair != null && serverAuthKeyPair != null) {
            Identity dummyIdentity = new Identity(server, serverAuthKeyPair.getPublicKey(), anonAuthKeyPair.getPublicKey());
            fetchManager.queryApiKeyStatus(dummyIdentity, apiKey);
        }
    }

    @Override
    public void queryFreeTrial(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.queryFreeTrial(ownedIdentity);
        } catch (DecodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startFreeTrial(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            // do not allow free trial start for keycloak managed identities
            if (!identityManager.isOwnedIdentityKeycloakManaged(engineSession.session, ownedIdentity)) {
                fetchManager.startFreeTrial(ownedIdentity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void verifyReceipt(byte[] bytesOwnedIdentity, String storeToken) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            // do not allow in-app purchases for keycloak managed identities
            if (!identityManager.isOwnedIdentityKeycloakManaged(engineSession.session, ownedIdentity)) {
                fetchManager.verifyReceipt(ownedIdentity, storeToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void queryServerWellKnown(String server) {
        fetchManager.queryServerWellKnown(server);
    }

    @Override
    public List<JsonOsmStyle> getOsmStyles(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return fetchManager.getOsmStyles(ownedIdentity.getServer());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public String getAddressServerUrl(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return fetchManager.getAddressServerUrl(ownedIdentity.getServer());
        } catch (Exception ignored) {
            return null;
        }
    }


    @Override
    public void propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom obvSyncAtom) throws Exception {
        // the App should never be sending a non-app sync item
        if (!obvSyncAtom.isAppSyncItem()) {
            throw new Exception();
        }

        try (EngineSession engineSession = getSession()) {
            for (Identity ownedIdentity : identityManager.getOwnedIdentities(engineSession.session)) {
                if (identityManager.getOtherDeviceUidsOfOwnedIdentity(engineSession.session, ownedIdentity).length > 0) {
                    protocolManager.initiateSingleItemSync(engineSession.session, ownedIdentity, obvSyncAtom);
                }
            }
            engineSession.session.commit();
        }
    }

    @Override
    public void propagateAppSyncAtomToOtherDevicesIfNeeded(byte[] bytesOwnedIdentity, ObvSyncAtom obvSyncAtom) throws Exception {
        // the App should never be sending a non-app sync item
        if (!obvSyncAtom.isAppSyncItem()) {
            throw new Exception();
        }

        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

            if (identityManager.getOtherDeviceUidsOfOwnedIdentity(engineSession.session, ownedIdentity).length > 0) {
                protocolManager.initiateSingleItemSync(engineSession.session, ownedIdentity, obvSyncAtom);
                engineSession.session.commit();
            }
        }
    }

    private boolean propagateEngineSyncAtomToOtherDevicesIfNeeded(Session session, Identity ownedIdentity, ObvSyncAtom obvSyncAtom) throws Exception {
        // the App should never be sending a non-app sync item
        if (obvSyncAtom.isAppSyncItem()) {
            throw new Exception();
        }

        if (identityManager.getOtherDeviceUidsOfOwnedIdentity(session, ownedIdentity).length > 0) {
            protocolManager.initiateSingleItemSync(session, ownedIdentity, obvSyncAtom);
            return true;
        }
        return false;
    }


    // Run once after you upgrade from a version not handling Contact and ContactGroup UserData to a version able to do so
    // Also run after a backup restore
    @Override
    public void downloadAllUserData() throws Exception {
        try (EngineSession engineSession = getSession()) {
            engineSession.session.startTransaction();
            identityManager.downloadAllUserData(engineSession.session);
            engineSession.session.commit();
        }
    }

    // Run once after the first introduction of device names for multi-device
    @Override
    public void setAllOwnedDeviceNames(String deviceName) {
        try (EngineSession engineSession = getSession()) {
            for (Identity ownedIdentity : identityManager.getOwnedIdentities(engineSession.session)) {
                UID currentDeviceUid = identityManager.getCurrentDeviceUidOfOwnedIdentity(engineSession.session, ownedIdentity);
                protocolManager.processDeviceManagementRequest(engineSession.session, ownedIdentity, ObvDeviceManagementRequest.createSetNicknameRequest(currentDeviceUid.getBytes(), deviceName));
            }
            engineSession.session.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void vacuumDatabase() throws Exception {
        try (EngineSession engineSession = getSession()) {
            Statement statement = engineSession.session.createStatement();
            statement.execute("VACUUM");
        }
    }

    // endregion
    // endregion
}
