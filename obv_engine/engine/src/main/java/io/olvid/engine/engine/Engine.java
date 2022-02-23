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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.BackupManager;
import io.olvid.engine.channel.ChannelManager;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCKeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaMDCKeyPair;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.notifications.BackupNotifications;
import io.olvid.engine.datatypes.notifications.ChannelNotifications;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelApplicationMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelDialogResponseMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
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
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.ObvReturnReceipt;
import io.olvid.engine.engine.types.identities.ObvTrustOrigin;
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.identity.IdentityManager;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.networkfetch.FetchManager;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.DownloadAttachmentPriorityCategory;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.networksend.SendManager;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.notification.NotificationManager;
import io.olvid.engine.protocol.ProtocolManager;

public class Engine implements NotificationListener, UserInterfaceDialogListener, EngineSessionFactory, EngineAPI {
    // region fields

    private long instanceCounter;
    private final HashMap<String, HashMap<Long, WeakReference<EngineNotificationListener>>> listeners;
    private final ReentrantLock listenersLock;
    private final BlockingQueue<EngineNotification> notificationQueue;

    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;


    private final String dbPath;
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // will be used once we use SQLcipher
    private final String dbKey;
    private final CreateSessionDelegate createSessionDelegate;

    private final ChannelManager channelManager;
    private final IdentityManager identityManager;
    private final FetchManager fetchManager;
    private final SendManager sendManager;
    private final NotificationManager notificationManager;
    private final ProtocolManager protocolManager;
    private final BackupManager backupManager;
    private final NotificationWorker notificationWorker;

    // endregion

    public Engine(File baseDirectory, String dbKey, SSLSocketFactory sslSocketFactory, Logger.LogOutputter logOutputter, int logLevel) throws Exception {
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

        try (EngineSession engineSession = getSession()) {
            UserInterfaceDialog.createTable(engineSession.session);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create engine databases");
        }

        this.channelManager = new ChannelManager(metaManager);
        this.identityManager = new IdentityManager(metaManager, baseDirectoryPath, jsonObjectMapper);
        this.fetchManager = new FetchManager(metaManager, sslSocketFactory, baseDirectoryPath, prng, jsonObjectMapper);
        this.sendManager = new SendManager(metaManager, sslSocketFactory, baseDirectoryPath, prng);
        this.notificationManager = new NotificationManager(metaManager);
        this.protocolManager = new ProtocolManager(metaManager, baseDirectoryPath, prng, jsonObjectMapper);
        this.backupManager = new BackupManager(metaManager, prng, jsonObjectMapper);

        registerToInternalNotifications();
        metaManager.initializationComplete();
    }

    private static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        UserInterfaceDialog.upgradeTable(session, oldVersion, newVersion);
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


    private void postEngineNotification(String notificationName, HashMap<String, Object> userInfo) {
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
                                listener.callback(engineNotification.notificationName, engineNotification.userInfo);
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

    private void registerToInternalNotifications() {
        for (String notificationName : new String[]{
                ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG,
                ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED,
                ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED,
                DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED,
                DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED,
                DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED,
//                DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER,
                DownloadNotifications.NOTIFICATION_SERVER_POLLED,
                DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED,
                DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED,
                DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED,
                DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS,
                DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED,
                DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED,
                DownloadNotifications.NOTIFICATION_PING_LOST,
                DownloadNotifications.NOTIFICATION_PING_RECEIVED,
                DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED,
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED,
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY,
                IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE,
                IdentityNotifications.NOTIFICATION_GROUP_CREATED,
                IdentityNotifications.NOTIFICATION_GROUP_DELETED,
                IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED,
                IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS,
                IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED,
                IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED,
                IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS,
                IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET,
                IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED,
                IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED,
                IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED,
                IdentityNotifications.NOTIFICATION_CONTACT_REVOKED,
                IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS,
                IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED,
                IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED,
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS,
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED,
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED,
                UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED,
                BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED,
                BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED,
                BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED,
                BackupNotifications.NOTIFICATION_BACKUP_FINISHED,
                BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL,
                BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED,
                BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST,
                BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED,
                ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED,
        }) {
            this.notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG: {
                try {
                    Session session = (Session) userInfo.get(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_SESSION_KEY);
                    ChannelDialogMessageToSend channelDialogMessageToSend = (ChannelDialogMessageToSend) userInfo.get(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_CHANNEL_DIALOG_MESSAGE_TO_SEND_KEY);
                    if (channelDialogMessageToSend == null) {
                        break;
                    }
                    // check whether it is a new/updated dialog, or a delete dialog
                    if (channelDialogMessageToSend.getSendChannelInfo().getDialogType().id == DialogType.DELETE_DIALOG_ID) {
                        UserInterfaceDialog userInterfaceDialog = UserInterfaceDialog.get(wrapSession(session), channelDialogMessageToSend.getSendChannelInfo().getDialogUuid());
                        if (userInterfaceDialog != null) {
                            userInterfaceDialog.delete();
                        }
                    } else {
                        UserInterfaceDialog.createOrReplace(wrapSession(session), createDialog(channelDialogMessageToSend));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_UID_KEY);

                ObvMessage message = new ObvMessage(fetchManager, ownedIdentity, messageUid);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_MESSAGE_RECEIVED_MESSAGE_KEY, message);

                postEngineNotification(EngineNotifications.NEW_MESSAGE_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_UID_KEY);
                byte[] extendedPayload = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY, extendedPayload);
                postEngineNotification(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED:
                // nothing to do, attachment status is already updated in app
                break;
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_ATTACHMENT_NUMBER_KEY);

                ObvAttachment attachment = ObvAttachment.create(fetchManager, ownedIdentity, messageUid, attachmentNumber);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOADED_ATTACHMENT_KEY, attachment);

                postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOADED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                float progress = (float) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY);
                if (messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY, progress);

                postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS, engineInfo);
                break;
            }
//            case DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER: {
//                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER_IDENTITY_KEY);
//                if (ownedIdentity == null) {
//                    break;
//                }
//
//                HashMap<String, Object> engineInfo = new HashMap<>();
//                engineInfo.put(EngineNotifications.API_KEY_REJECTED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
//                postEngineNotification(EngineNotifications.API_KEY_REJECTED, engineInfo);
//                break;
//            }
            case DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
                ServerSession.ApiKeyStatus apiKeyStatus = (ServerSession.ApiKeyStatus) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_STATUS_KEY);
                //noinspection unchecked
                List<ServerSession.Permission> permissions = (List<ServerSession.Permission>) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_PERMISSIONS_KEY);
                long apiKeyExpirationTimestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (ownedIdentity == null || apiKeyStatus == null || permissions == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                switch (apiKeyStatus) {
                    case VALID:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.VALID);
                        break;
                    case UNKNOWN:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.UNKNOWN);
                        break;
                    case LICENSES_EXHAUSTED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.LICENSES_EXHAUSTED);
                        break;
                    case EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.EXPIRED);
                        break;
                    case OPEN_BETA_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.OPEN_BETA_KEY);
                        break;
                    case FREE_TRIAL_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.FREE_TRIAL_KEY);
                        break;
                    case AWAITING_PAYMENT_GRACE_PERIOD:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD);
                        break;
                    case AWAITING_PAYMENT_ON_HOLD:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD);
                        break;
                    case FREE_TRIAL_KEY_EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED);
                        break;
                }
                List<ApiKeyPermission> enginePermissions = new ArrayList<>();
                for (ServerSession.Permission permission: permissions) {
                    switch (permission) {
                        case CALL:
                            enginePermissions.add(ApiKeyPermission.CALL);
                            break;
                        case WEB_CLIENT:
                            enginePermissions.add(ApiKeyPermission.WEB_CLIENT);
                            break;
                    }
                }
                engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_PERMISSIONS_KEY, enginePermissions);
                if (apiKeyExpirationTimestamp != 0) {
                    engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
                }
                postEngineNotification(EngineNotifications.API_KEY_ACCEPTED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_SERVER_POLLED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLLED_OWNED_IDENTITY_KEY);
                boolean success = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLLED_SUCCESS_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.SERVER_POLLED_SUCCESS_KEY, success);
                postEngineNotification(EngineNotifications.SERVER_POLLED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_OWNED_IDENTITY_KEY);
                byte[] serverUid = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY);
                byte[] nonce = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_NONCE_KEY);
                byte[] encryptedPayload = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY);
                long timestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY, serverUid);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_NONCE_KEY, nonce);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY, encryptedPayload);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY, timestamp);
                postEngineNotification(EngineNotifications.RETURN_RECEIPT_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY);
                UUID callUuid = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY);
                String username1 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY);
                String password1 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY);
                String username2 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY);
                String password2 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY);
                //noinspection unchecked
                List<String> turnServers = (List<String>) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_SERVERS_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY, callUuid);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY, username1);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY, password1);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY, username2);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY, password2);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_SERVERS_KEY, turnServers);
                postEngineNotification(EngineNotifications.TURN_CREDENTIALS_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY);
                UUID callUuid = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_CALL_UUID_KEY);
                DownloadNotifications.TurnCredentialsFailedReason rfc = (DownloadNotifications.TurnCredentialsFailedReason) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_REASON_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_CALL_UUID_KEY, callUuid);
                switch (rfc) {
                    case PERMISSION_DENIED:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.PERMISSION_DENIED);
                        break;
                    case BAD_SERVER_SESSION:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.BAD_SERVER_SESSION);
                        break;
                    case UNABLE_TO_CONTACT_SERVER:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.UNABLE_TO_CONTACT_SERVER);
                        break;
                    case CALLS_NOT_SUPPORTED_ON_SERVER:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.CALLS_NOT_SUPPORTED_ON_SERVER);
                        break;
                }
                postEngineNotification(EngineNotifications.TURN_CREDENTIALS_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY);
                ServerSession.ApiKeyStatus apiKeyStatus = (ServerSession.ApiKeyStatus) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY);
                //noinspection unchecked
                List<ServerSession.Permission> permissions = (List<ServerSession.Permission>) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY);
                long apiKeyExpirationTimestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (ownedIdentity == null || apiKey == null || apiKeyStatus == null || permissions == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY, apiKey);
                switch (apiKeyStatus) {
                    case VALID:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.VALID);
                        break;
                    case UNKNOWN:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.UNKNOWN);
                        break;
                    case LICENSES_EXHAUSTED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.LICENSES_EXHAUSTED);
                        break;
                    case EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.EXPIRED);
                        break;
                    case OPEN_BETA_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.OPEN_BETA_KEY);
                        break;
                    case FREE_TRIAL_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.FREE_TRIAL_KEY);
                        break;
                    case AWAITING_PAYMENT_GRACE_PERIOD:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD);
                        break;
                    case AWAITING_PAYMENT_ON_HOLD:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD);
                        break;
                    case FREE_TRIAL_KEY_EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED);
                        break;
                }
                List<ApiKeyPermission> enginePermissions = new ArrayList<>();
                for (ServerSession.Permission permission: permissions) {
                    switch (permission) {
                        case CALL:
                            enginePermissions.add(ApiKeyPermission.CALL);
                            break;
                        case WEB_CLIENT:
                            enginePermissions.add(ApiKeyPermission.WEB_CLIENT);
                            break;
                    }
                }
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY, enginePermissions);
                if (apiKeyExpirationTimestamp != 0) {
                    engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
                }
                postEngineNotification(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY);
                if (ownedIdentity == null || apiKey == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY, apiKey);
                postEngineNotification(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_OWNED_IDENTITY_KEY);
                boolean available = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY, available);
                postEngineNotification(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                postEngineNotification(EngineNotifications.FREE_TRIAL_QUERY_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                postEngineNotification(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                postEngineNotification(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_OWNED_IDENTITY_KEY);
                String storeToken = (String) userInfo.get(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY);
                if (ownedIdentity == null || storeToken == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.VERIFY_RECEIPT_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY, storeToken);
                postEngineNotification(EngineNotifications.VERIFY_RECEIPT_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_KEY);
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_APP_INFO_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY, server);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY, appInfo);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY, true);
                postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY);
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY, server);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY, appInfo);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY, false);
                postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY, server);
                postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PING_LOST: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                postEngineNotification(EngineNotifications.PING_LOST, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PING_RECEIVED: {
                Long delay = (Long) userInfo.get(DownloadNotifications.NOTIFICATION_PING_RECEIVED_DELAY_KEY);
                if (delay == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PING_RECEIVED_DELAY_KEY, delay);

                postEngineNotification(EngineNotifications.PING_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED: {
                Integer state = (Integer) userInfo.get(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY);
                if (state == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, state);

                postEngineNotification(EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY: {
                try (EngineSession engineSession = getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_CONTACT_IDENTITY_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_OWNED_IDENTITY_KEY);
                    boolean keycloakManaged = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_KEYCLOAK_MANAGED_KEY);
                    boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ACTIVE_KEY);
                    if (contactIdentity == null || ownedIdentity == null) {
                        break;
                    }

                    protocolManager.startDeviceDiscoveryProtocol(contactIdentity, ownedIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    JsonIdentityDetails contactDetails = identityManager.getContactIdentityTrustedDetails(engineSession.session, ownedIdentity, contactIdentity);

                    engineInfo.put(EngineNotifications.NEW_CONTACT_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.NEW_CONTACT_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, contactDetails, keycloakManaged, active));
                    engineInfo.put(EngineNotifications.NEW_CONTACT_HAS_UNTRUSTED_PUBLISHED_DETAILS_KEY, identityManager.contactHasUntrustedPublishedDetails(engineSession.session, ownedIdentity, contactIdentity));
                    postEngineNotification(EngineNotifications.NEW_CONTACT, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED: {
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_CONTACT_IDENTITY_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_OWNED_IDENTITY_KEY);
                if (contactIdentity == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_DELETED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_DELETED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                postEngineNotification(EngineNotifications.CONTACT_DELETED, engineInfo);
                break;
            }
            case ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED:
                try (EngineSession engineSession = getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_REMOTE_IDENTITY_KEY);
                    UID currentDeviceUid = (UID) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_CURRENT_DEVICE_UID_KEY);
                    if (contactIdentity == null || currentDeviceUid == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    Identity ownedIdentity = identityManager.getOwnedIdentityForDeviceUid(engineSession.session, currentDeviceUid);
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                    postEngineNotification(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED:
                try (EngineSession engineSession = getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_REMOTE_IDENTITY_KEY);
                    UID currentDeviceUid = (UID) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_CURRENT_DEVICE_UID_KEY);
                    if (contactIdentity == null || currentDeviceUid == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    Identity ownedIdentity = identityManager.getOwnedIdentityForDeviceUid(engineSession.session, currentDeviceUid);
                    if (ownedIdentity == null) {
                        break;
                    }
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                    postEngineNotification(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY);
                if (contactIdentity == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                postEngineNotification(EngineNotifications.NEW_CONTACT_DEVICE, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED: {
                postEngineNotification(EngineNotifications.OWNED_IDENTITY_LIST_UPDATED, new HashMap<>());
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_IDENTITY_DETAILS_KEY);
                if (ownedIdentity == null || identityDetails == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_IDENTITY_DETAILS_KEY, identityDetails.getIdentityDetails());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_PHOTO_URL_KEY, identityDetails.getPhotoUrl());

                postEngineNotification(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_CREATED:
                try (EngineSession engineSession = getSession()) {
                    byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_CREATED_GROUP_OWNER_AND_UID_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_CREATED_OWNED_IDENTITY_KEY);
                    if (groupOwnerAndUid == null || ownedIdentity == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    GroupWithDetails group = identityManager.getGroupWithDetails(engineSession.session, ownedIdentity, groupOwnerAndUid);
                    if (group == null) {
                        break;
                    }

                    byte[][] bytesContactIdentities = new byte[group.getGroupMembers().length][];
                    for (int j = 0; j < bytesContactIdentities.length; j++) {
                        bytesContactIdentities[j] = group.getGroupMembers()[j].getBytes();
                    }
                    ObvIdentity[] pendingMembers = new ObvIdentity[group.getPendingGroupMembers().length];
                    for (int j = 0; j < pendingMembers.length; j++) {
                        try {
                            JsonIdentityDetails identityDetails = identityManager.getJsonObjectMapper().readValue(group.getPendingGroupMembers()[j].serializedDetails, JsonIdentityDetails.class);
                            pendingMembers[j] = new ObvIdentity(group.getPendingGroupMembers()[j].identity, identityDetails, false, true);
                        } catch (IOException e) {
                            pendingMembers[j] = new ObvIdentity(group.getPendingGroupMembers()[j].identity, null, false, true);
                        }
                    }
                    byte[][] bytesDeclinesPendingMembers = new byte[group.getDeclinedPendingMembers().length][];
                    for (int j = 0; j < bytesDeclinesPendingMembers.length; j++) {
                        bytesDeclinesPendingMembers[j] = group.getDeclinedPendingMembers()[j].getBytes();
                    }
                    ObvGroup obvGroup;
                    if (group.getGroupOwner() == null) {
                        obvGroup = new ObvGroup(
                                group.getGroupOwnerAndUid(),
                                group.getPublishedGroupDetails(),
                                ownedIdentity.getBytes(),
                                bytesContactIdentities,
                                pendingMembers,
                                bytesDeclinesPendingMembers,
                                null
                        );
                    } else {
                        obvGroup = new ObvGroup(
                                group.getGroupOwnerAndUid(),
                                group.getLatestOrTrustedGroupDetails(),
                                ownedIdentity.getBytes(),
                                bytesContactIdentities,
                                pendingMembers,
                                bytesDeclinesPendingMembers,
                                group.getGroupOwner().getBytes()
                        );
                    }

                    String photoUrl = identityManager.getGroupPhotoUrl(engineSession.session, ownedIdentity, groupOwnerAndUid);

                    engineInfo.put(EngineNotifications.GROUP_CREATED_GROUP_KEY, obvGroup);
                    engineInfo.put(EngineNotifications.GROUP_CREATED_HAS_MULTIPLE_DETAILS_KEY, group.hasMultipleDetails());
                    engineInfo.put(EngineNotifications.GROUP_CREATED_PHOTO_URL_KEY, photoUrl);
                    postEngineNotification(EngineNotifications.GROUP_CREATED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case IdentityNotifications.NOTIFICATION_GROUP_DELETED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_DELETED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_DELETED_OWNED_IDENTITY_KEY);
                if (groupUid == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_DELETED_BYTES_GROUP_OWNER_AND_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_DELETED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());

                postEngineNotification(EngineNotifications.GROUP_DELETED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetailsWithVersionAndPhoto = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || groupDetailsWithVersionAndPhoto == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY, groupDetailsWithVersionAndPhoto);

                postEngineNotification(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                postEngineNotification(EngineNotifications.GROUP_MEMBER_ADDED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                postEngineNotification(EngineNotifications.GROUP_MEMBER_REMOVED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetails = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || groupDetails == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY, groupDetails);
                postEngineNotification(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY);
                String contactSerializedDetails = (String) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_SERIALIZED_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null || contactSerializedDetails == null) {
                    break;
                }

                JsonIdentityDetails identityDetails;
                try {
                    identityDetails = jsonObjectMapper.readValue(contactSerializedDetails, JsonIdentityDetails.class);
                } catch (Exception e) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, identityDetails, false, true));

                postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_ADDED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY);
                String contactSerializedDetails = (String) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_SERIALIZED_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null || contactSerializedDetails == null) {
                    break;
                }

                JsonIdentityDetails identityDetails;
                try {
                    identityDetails = jsonObjectMapper.readValue(contactSerializedDetails, JsonIdentityDetails.class);
                } catch (Exception e) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, identityDetails, false, true));

                postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_CONTACT_IDENTITY_KEY);
                boolean declined = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_DECLINED_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_DECLINED_KEY, declined);

                postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                postEngineNotification(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET: {
                byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_OWNED_IDENTITY_KEY);
                int version = (int) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_VERSION_KEY);
                boolean isTrusted = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_IS_TRUSTED_KEY);
                if (ownedIdentity == null || groupOwnerAndUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_IS_TRUSTED_KEY, isTrusted);

                postEngineNotification(EngineNotifications.NEW_GROUP_PHOTO, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_CONTACT_IDENTITY_KEY);
                int version = (int) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_VERSION_KEY);
                boolean isTrusted = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_IS_TRUSTED_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY, isTrusted);

                postEngineNotification(EngineNotifications.NEW_CONTACT_PHOTO, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_CONTACT_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY, identityDetails);

                postEngineNotification(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_CONTACT_IDENTITY_KEY);
                boolean keycloakManaged = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY, keycloakManaged);

                postEngineNotification(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_CONTACT_IDENTITY_KEY);
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_ACTIVE_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                if (active) {
                    try {
                        protocolManager.startDeviceDiscoveryProtocol(contactIdentity, ownedIdentity);
                    } catch (Exception ignored) {}
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_ACTIVE_KEY, active);

                postEngineNotification(EngineNotifications.CONTACT_ACTIVE_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_REVOKED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_REVOKED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_REVOKED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                postEngineNotification(EngineNotifications.CONTACT_REVOKED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                boolean hasUnpublished = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY, hasUnpublished);

                postEngineNotification(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY, active);

                postEngineNotification(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }
                try {
                    List<ObvCapability> capabilities = identityManager.getContactCapabilities(ownedIdentity, contactIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_CAPABILITIES, capabilities);

                    postEngineNotification(EngineNotifications.CONTACT_CAPABILITIES_UPDATED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                try {
                    List<ObvCapability> capabilities = identityManager.getOwnCapabilities(ownedIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.OWN_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.OWN_CAPABILITIES_UPDATED_CAPABILITIES, capabilities);

                    postEngineNotification(EngineNotifications.OWN_CAPABILITIES_UPDATED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                float progress = (float) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY, progress);

                postEngineNotification(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_ATTACHMENT_NUMBER_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                postEngineNotification(EngineNotifications.ATTACHMENT_UPLOADED, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                postEngineNotification(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_UID_KEY);
                Long timestampFromServer = (Long) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER);
                if (ownedIdentity == null || messageUid == null || timestampFromServer == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER, timestampFromServer);

                postEngineNotification(EngineNotifications.MESSAGE_UPLOADED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY);
                byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_GROUP_OWNER_AND_UID_KEY);
                if (ownedIdentity == null || groupOwnerAndUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);

                postEngineNotification(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED: {
                String seed = (String) userInfo.get(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED_SEED_KEY);
                if (seed == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_BACKUP_SEED_GENERATED_SEED_KEY, seed);

                postEngineNotification(EngineNotifications.NEW_BACKUP_SEED_GENERATED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED: {
                postEngineNotification(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, new HashMap<>());
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid == null || encryptedContent == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY, encryptedContent);

                postEngineNotification(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FINISHED: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid == null || encryptedContent == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY, encryptedContent);

                postEngineNotification(EngineNotifications.BACKUP_FINISHED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                postEngineNotification(EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                postEngineNotification(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_VERSION_KEY);
                if (backupKeyUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.APP_BACKUP_REQUESTED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.APP_BACKUP_REQUESTED_VERSION_KEY, version);

                postEngineNotification(EngineNotifications.APP_BACKUP_REQUESTED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED: {
                postEngineNotification(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, new HashMap<>());
                break;
            }
            case ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED: {
                Identity ownedIdentity = (Identity) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_OWNED_IDENTITIY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_CONTACT_IDENTITIY_KEY);
                byte[] nonce = (byte[]) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY);

                if (ownedIdentity == null || contactIdentity == null || nonce == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_NONCE_KEY, nonce);

                postEngineNotification(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, engineInfo);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }

    // endregion

    // region EngineSessionFactory

    @Override
    public EngineSession getSession() throws SQLException {
        return new EngineSession(createSessionDelegate.getSession(), this, jsonObjectMapper);
    }

    private EngineSession wrapSession(Session session) {
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

    private ObvDialog createDialog(ChannelDialogMessageToSend channelDialogMessageToSend) {
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
            case DialogType.MUTUAL_TRUST_CONFIRMED_DIALOG_ID: {
                category = ObvDialog.Category.createMutualTrustConfirmed(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails);
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
            case DialogType.INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_ID: {
                byte[] bytesMediatorIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesMediatorIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
                }
                category = ObvDialog.Category.createIncreaseMediatorTrustLevel(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, bytesMediatorIdentity, dialogType.serverTimestamp);
                break;
            }
            case DialogType.INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_ID: {
                byte[] bytesGroupOwnerIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesGroupOwnerIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
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
                category = ObvDialog.Category.createIncreaseGroupOwnerTrustLevel(dialogType.serializedGroupDetails, dialogType.groupUid.getBytes(), bytesGroupOwnerIdentity, pendingGroupMemberIdentities, dialogType.serverTimestamp);
                break;
            }
            case DialogType.AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_ID: {
                byte[] bytesMediatorIdentity = null;
                if (dialogType.mediatorOrGroupOwnerIdentity != null) {
                    bytesMediatorIdentity = dialogType.mediatorOrGroupOwnerIdentity.getBytes();
                }
                category = ObvDialog.Category.createAutoConfirmedContactIntroduction(dialogType.contactIdentity.getBytes(), dialogType.contactDisplayNameOrSerializedDetails, bytesMediatorIdentity);
                break;
            }
            case DialogType.GROUP_JOINED_DIALOG_ID: {
                category = ObvDialog.Category.createGroupJoined(dialogType.serializedGroupDetails, dialogType.groupUid.getBytes(), dialogType.mediatorOrGroupOwnerIdentity.getBytes());
                break;
            }
            default:
                Logger.w("Unknown DialogType " + dialogType.id);
                return null;
        }
        return new ObvDialog(channelDialogMessageToSend.getUuid(), channelDialogMessageToSend.getEncodedElements(), ownedIdentity.getBytes(), category);
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
    public ObvIdentity generateOwnedIdentity(String server, JsonIdentityDetails identityDetails, UUID apiKey, ObvKeycloakState keycloakState) {
        try (EngineSession engineSession = getSession()) {
            if (server == null) {
                server = "";
            }
            Identity identity = identityManager.generateOwnedIdentity(engineSession.session, server, identityDetails, apiKey, keycloakState, prng);
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
    public UUID getApiKeyForOwnedIdentity(byte[] bytesOwnedIdentity) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            return identityManager.getApiKey(ownedIdentity);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean updateApiKeyForOwnedIdentity(byte[] bytesOwnedIdentity, UUID apiKey) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            identityManager.updateApiKeyOfOwnedIdentity(engineSession.session, ownedIdentity, apiKey);
            fetchManager.deleteExistingServerSessionAndCreateANewOne(engineSession.session, ownedIdentity);
            engineSession.session.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
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

    @Override
    public void recreateServerSession(byte[] bytesOwnedIdentity) {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.deleteExistingServerSessionAndCreateANewOne(engineSession.session, ownedIdentity);
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
            protocolManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);
            sendManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);
            fetchManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);

            for (UserInterfaceDialog userInterfaceDialog: UserInterfaceDialog.getAll(engineSession)) {
                ObvDialog obvDialog = userInterfaceDialog.getObvDialog();
                if (Arrays.equals(obvDialog.getBytesOwnedIdentity(), bytesOwnedIdentity)) {
                    userInterfaceDialog.delete();
                }
            }
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
    public List<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(String pushTopic) throws Exception {
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
    public void registerToPushNotification(byte[] bytesOwnedIdentity, String firebaseToken, boolean kickOtherDevices, boolean useMultidevice) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            UID currentDeviceUid = identityManager.getCurrentDeviceUidOfOwnedIdentity(engineSession.session, ownedIdentity);

            PushNotificationTypeAndParameters pushNotificationTypeAndParameters;
            if (firebaseToken == null) {
                pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createWebsocketOnly(kickOtherDevices, useMultidevice);
            } else {
                // We pick a random identityMaskingUid in case we need to register (only useful when configuration changed)
                UID identityMaskingUid = new UID(prng);
                byte[] firebaseTokenBytes = firebaseToken.getBytes(StandardCharsets.UTF_8);
                pushNotificationTypeAndParameters = PushNotificationTypeAndParameters.createFirebaseAndroid(firebaseTokenBytes, identityMaskingUid, kickOtherDevices, useMultidevice);
            }
            engineSession.session.startTransaction();
            fetchManager.registerPushNotificationIfConfigurationChanged(engineSession.session, ownedIdentity, currentDeviceUid, pushNotificationTypeAndParameters);
            engineSession.session.commit();
        }
    }


    @Override
    public void unregisterToPushNotification(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            fetchManager.unregisterPushNotification(engineSession.session, ownedIdentity);
            engineSession.session.commit();
        }
    }

    @Override
    public void processAndroidPushNotification(String maskingUidString) {
        fetchManager.processAndroidPushNotification(maskingUidString);
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
    public String serverForIdentity(byte[] bytesIdentity) {
        try {
            return Identity.of(bytesIdentity).getServer();
        } catch (DecodingException e) {
            return null;
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
            identityManager.trustPublishedContactDetails(engineSession.session, contactIdentity, ownedIdentity);
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
    public boolean doesContactHaveAutoAcceptTrustLevel(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception {
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        Identity contactIdentity = Identity.of(bytesContactIdentity);
        try (EngineSession engineSession = getSession()) {
            return (identityManager.getContactIdentityTrustLevel(engineSession.session, ownedIdentity, contactIdentity).compareTo(Constants.AUTO_ACCEPT_TRUST_LEVEL_THRESHOLD)) >= 0;
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
            identityManager.trustPublishedGroupDetails(engineSession.session, ownedIdentity, bytesGroupOwnerAndUid);
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
        protocolManager.startTrustEstablishmentProtocol(remoteIdentity, contactDisplayName, ownedIdentity);
    }

    @Override
    public ObvMutualScanUrl computeMutualScanSignedNonceUrl(byte[] bytesRemoteIdentity, byte[] bytesOwnedIdentity, String ownDisplayName) throws Exception {
        Identity contactIdentity = Identity.of(bytesRemoteIdentity);
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

        try (EngineSession engineSession = getSession()) {
            byte[] signature = identityManager.signIdentities(engineSession.session, Constants.MUTUAL_SCAN_SIGNATURE_CHALLENGE_PREFIX, new Identity[]{contactIdentity, ownedIdentity}, ownedIdentity, prng);
            return new ObvMutualScanUrl(ownedIdentity, ownDisplayName, signature);
        }
    }

    public boolean verifyMutualScanSignedNonceUrl(byte[] bytesOwnedIdentity, ObvMutualScanUrl mutualScanUrl) {
        try {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);

            return identityManager.verifyIdentitiesSignature(Constants.MUTUAL_SCAN_SIGNATURE_CHALLENGE_PREFIX, new Identity[]{ownedIdentity, mutualScanUrl.identity}, mutualScanUrl.identity, mutualScanUrl.signature);
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public void startMutualScanTrustEstablishmentProtocol(byte[] bytesOwnedIdentity, byte[] bytesRemoteIdentity, byte[] signature) throws Exception {
        Identity contactIdentity = Identity.of(bytesRemoteIdentity);
        Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
        protocolManager.startMutualScanTrustEstablishmentProtocol(contactIdentity, signature, ownedIdentity);
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
        protocolManager.startContactMutualIntroductionProtocol(contactIdentityA, contactIdentities, ownedIdentity);
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

        protocolManager.startGroupCreationProtocol(serializedGroupDetailsWithVersionAndPhoto, absolutePhotoUrl, groupMemberIdentitiesAndDisplayNames, ownedIdentity);
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
            protocolManager.startDeviceDiscoveryProtocolWithinTransaction(engineSession.session, contactIdentity, ownedIdentity);
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
            protocolManager.startDeviceDiscoveryProtocolWithinTransaction(engineSession.session, contactIdentity, ownedIdentity);
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
        protocolManager.deleteContact(contactIdentity, ownedIdentity);
    }

    @Override
    public void deleteOwnedIdentityAndNotifyContacts(byte[] bytesOwnedIdentity) throws Exception {
        try (EngineSession engineSession = getSession()) {
            Identity ownedIdentity = Identity.of(bytesOwnedIdentity);
            engineSession.session.startTransaction();

            // before starting the protocol in charge of notifying the contacts, delete everything that will no longer be used
            protocolManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);
            sendManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);
            fetchManager.deleteOwnedIdentity(engineSession.session, ownedIdentity);

            for (UserInterfaceDialog userInterfaceDialog: UserInterfaceDialog.getAll(engineSession)) {
                ObvDialog obvDialog = userInterfaceDialog.getObvDialog();
                if (Arrays.equals(obvDialog.getBytesOwnedIdentity(), bytesOwnedIdentity)) {
                    userInterfaceDialog.delete();
                }
            }

            // now delete contacts and leave/disband groups
            // the protocol will also delete all channels (once they are no longer used) and actually delete the owned identity
            protocolManager.deleteOwnedIdentityAndNotifyContacts(engineSession.session, ownedIdentity);
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
        HashMap<String, List<Identity>> contactServersHashMap = new HashMap<>();
        for (byte[] bytesContactIdentity: bytesContactIdentities) {
            try {
                Identity contactIdentity = Identity.of(bytesContactIdentity);
                List<Identity> list = contactServersHashMap.get(contactIdentity.getServer());
                if (list == null) {
                    list = new ArrayList<>();
                    contactServersHashMap.put(contactIdentity.getServer(), list);
                }
                list.add(contactIdentity);
            } catch (DecodingException e) {
                e.printStackTrace();
                Logger.w("Error decoding a bytesContactIdentity while posting a message!");
            }
        }

        HashMap<ObvPostMessageOutput.BytesKey, byte[]> messageIdentifierByContactIdentity = new HashMap<>();
        boolean messageSent = false;

        for (String server: contactServersHashMap.keySet()) {
            List<Identity> contactIdentities = contactServersHashMap.get(server);
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
                        messageIdentifierByContactIdentity.put(new ObvPostMessageOutput.BytesKey(contactIdentity.getBytes()), messageUid.getBytes());
                    }
                } else {
                    for (Identity contactIdentity : contactIdentities) {
                        messageIdentifierByContactIdentity.put(new ObvPostMessageOutput.BytesKey(contactIdentity.getBytes()), null);
                    }
                    continue;
                }

                // message is considered SENT even if a single recipient receives it.
                messageSent = true;
            } catch (Exception e) {
                for (Identity contactIdentity : contactIdentities) {
                    messageIdentifierByContactIdentity.put(new ObvPostMessageOutput.BytesKey(contactIdentity.getBytes()), null);
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
            UID[] contactDeviceUids = identityManager.getDeviceUidsOfContactIdentity(engineSession.session, ownedIdentity, contactIdentity);
            if (contactDeviceUids.length != 0) {
                sendManager.sendReturnReceipt(engineSession.session, ownedIdentity, contactIdentity, contactDeviceUids, status, returnReceiptNonce, returnReceiptKey, attachmentNumber);
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
    public void deleteAttachment(ObvAttachment attachment) {
        deleteAttachment(attachment.getOwnedIdentity(), attachment.getMessageUid(), attachment.getNumber());
    }

    @Override
    public void deleteAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
        try {
            deleteAttachment(Identity.of(bytesOwnedIdentity), new UID(messageIdentifier), attachmentNumber);
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
    public void deleteMessage(byte[] bytesOwnedIdentity, byte[] messageIdentifier) {
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

    private void deleteAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        try (EngineSession engineSession = getSession()) {
            fetchManager.deleteAttachment(engineSession.session, ownedIdentity, messageUid, attachmentNumber);
            engineSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cancelAttachmentUpload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber) {
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
    public void connectWebsocket(String os, String osVersion, int appBuild, String appVersion) {
        fetchManager.connectWebsockets(os, osVersion, appBuild, appVersion);
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
    public void setAutoBackupEnabled(boolean enabled) {
        backupManager.setAutoBackupEnabled(enabled);
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
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String backupSeed, byte[] backupContent) {
        return backupManager.restoreOwnedIdentitiesFromBackup(backupSeed, backupContent);
    }

    @Override
    public void restoreContactsAndGroupsFromBackup(String backupSeed, byte[] backupContent, ObvIdentity[] restoredOwnedIdentities) {
        Identity[] restoredIdentities = new Identity[restoredOwnedIdentities.length];
        for (int i=0; i<restoredOwnedIdentities.length; i++) {
            try {
                restoredIdentities[i] = Identity.of(restoredOwnedIdentities[i].getBytesIdentity());
            } catch (DecodingException ignored) {
                // nothing we can do here...
            }
        }
        backupManager.restoreContactsAndGroupsFromBackup(backupSeed, backupContent, restoredIdentities);
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
