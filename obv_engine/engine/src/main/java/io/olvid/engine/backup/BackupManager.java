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

package io.olvid.engine.backup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.databases.Backup;
import io.olvid.engine.backup.databases.BackupKey;
import io.olvid.engine.backup.databases.DeviceBackupSeed;
import io.olvid.engine.backup.databases.ProfileBackupThreadId;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.backup.datatypes.BackupManagerSessionFactory;
import io.olvid.engine.backup.tasks.DeviceBackupDeleteTask;
import io.olvid.engine.backup.tasks.DeviceBackupFetchTask;
import io.olvid.engine.backup.tasks.DeviceBackupUploadTask;
import io.olvid.engine.backup.tasks.ProfileBackupSnapshotDeleteTask;
import io.olvid.engine.backup.tasks.ProfileBackupUploadTask;
import io.olvid.engine.backup.tasks.ProfileBackupsFetchTask;
import io.olvid.engine.crypto.MAC;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.datatypes.notifications.BackupNotifications;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvDeviceBackupForRestore;
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.metamanager.BackupDelegate;
import io.olvid.engine.metamanager.BackupV2Delegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;


public class BackupManager implements BackupDelegate, BackupV2Delegate, BackupManagerSessionFactory, ObvManager, NotificationListener {
    private final ObvBackupAndSyncDelegate appBackupAndSyncDelegates;
    private final SSLSocketFactory sslSocketFactory;
    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;

    private CreateSessionDelegate createSessionDelegate;
    private IdentityDelegate identityDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    private final NoExceptionSingleThreadExecutor executor;
    private final ScheduledExecutorService autoBackupScheduler;

    // for legacy backups
    private boolean autoBackupEnabled;
    private boolean autoBackupIsScheduled;
    private ScheduledFuture<?> scheduledAutoBackupTask;
    private final Object autoBackupSchedulerLock;
    private final Map<UidAndVersion, Map<String, String>> ongoingBackupMap;
    private final Map<UidAndVersion, ScheduledFuture<?>> ongoingBackupTimeoutMap;

    public static final String IDENTITY_BACKUP_TAG = "identity";
    public static final String APP_BACKUP_TAG = "app";

    public static final String[] ALL_BACKUP_TAGS = new String[]{IDENTITY_BACKUP_TAG, APP_BACKUP_TAG};

    public static final int BACKUP_SEED_VERIFICATION_STATUS_SUCCESS = 0;
    public static final int BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT = 1;
    public static final int BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG = 2;
    public static final int BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY = 3;

    // for backup v2
    private boolean deviceBackupsActive;
    private final Set<ScheduledBackup> scheduledBackups;
    private Long nextScheduledBackupTimestamp;

    public BackupManager(MetaManager metaManager, ObvBackupAndSyncDelegate appBackupAndSyncDelegates, SSLSocketFactory sslSocketFactory, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.appBackupAndSyncDelegates = appBackupAndSyncDelegates;
        this.sslSocketFactory = sslSocketFactory;
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;
        this.executor = new NoExceptionSingleThreadExecutor("BackupManager executor");
        this.autoBackupScheduler = Executors.newScheduledThreadPool(1);
        this.autoBackupEnabled = false;
        this.autoBackupIsScheduled = false;
        this.autoBackupSchedulerLock = new Object();
        this.ongoingBackupMap = new HashMap<>();
        this.ongoingBackupTimeoutMap = new HashMap<>();

        this.deviceBackupsActive = false;
        this.scheduledBackups = new HashSet<>();
        this.nextScheduledBackupTimestamp = null;

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.requestDelegate(this, NotificationListeningDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    @Override
    public void initialisationComplete() {
        // clear obsolete backups
        try (BackupManagerSession backupManagerSession = getSession()) {
            for (BackupKey backupKey : BackupKey.getAll(backupManagerSession)) {
                Backup.cleanup(backupManagerSession, backupKey.getUid(), backupKey.getUploadedBackupVersion(), backupKey.getExportedBackupVersion(), backupKey.getLatestBackupVersion());
            }
            backupManagerSession.session.commit();
        } catch (Exception e) {
            Logger.x(e);
        }

        // backups v2
        try (BackupManagerSession backupManagerSession = getSession()) {
            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
            if (deviceBackupSeed != null) {
                scheduleDeviceAndAllProfilesBackup(backupManagerSession, deviceBackupSeed);
            }

            for (DeviceBackupSeed inactiveDeviceBackupSeed: DeviceBackupSeed.getAllInactive(backupManagerSession)) {
                cleanUpDeviceBackups(inactiveDeviceBackupSeed.getBackupSeed());
            }
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (BackupManagerSession backupManagerSession = getSession()) {
            Backup.createTable(backupManagerSession.session);
            BackupKey.createTable(backupManagerSession.session);
            DeviceBackupSeed.createTable(backupManagerSession.session);
            ProfileBackupThreadId.createTable(backupManagerSession.session);
            backupManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
            throw new RuntimeException("Unable to create backup databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        Backup.upgradeTable(session, oldVersion, newVersion);
        BackupKey.upgradeTable(session, oldVersion, newVersion);
        DeviceBackupSeed.upgradeTable(session, oldVersion, newVersion);
        ProfileBackupThreadId.upgradeTable(session, oldVersion, newVersion);
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED, this);
        notificationListeningDelegate.addListener(BackupNotifications.NOTIFICATION_DEVICE_BACKUP_NEEDED, this);
        notificationListeningDelegate.addListener(BackupNotifications.NOTIFICATION_PROFILE_BACKUP_NEEDED, this);
    }

    @Override
    public BackupManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in BackupManager.");
        }
        return new BackupManagerSession(createSessionDelegate.getSession(), notificationPostingDelegate, identityDelegate, appBackupAndSyncDelegates, jsonObjectMapper, prng);
    }

    public ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }




    // region backup v2 scheduling

    public void retryScheduledNetworkTasks() {
        synchronized (scheduledBackups) {
            for (ScheduledBackup scheduledBackup : scheduledBackups) {
                scheduledBackup.clearFailedAttemptCounts();
            }

            executeScheduledBackupsThatReachedTheirTimestamp();
        }
    }

    private void scheduleDeviceAndAllProfilesBackup(BackupManagerSession backupManagerSession, DeviceBackupSeed deviceBackupSeed) throws SQLException {
        deviceBackupsActive = true;
        scheduleDeviceBackup(deviceBackupSeed.getNextBackupTimestamp());

        boolean commitNeeded = false;
        // first make sure all owned identities have a ProfileBackupThreadId
        List<ProfileBackupThreadId> profileBackupThreadIds = ProfileBackupThreadId.getAll(backupManagerSession);
        HashSet<Identity> ownedIdentities = new HashSet<>(Arrays.asList(identityDelegate.getOwnedIdentities(backupManagerSession.session)));
        for (ProfileBackupThreadId profileBackupThreadId : profileBackupThreadIds) {
            if (!ownedIdentities.remove(profileBackupThreadId.getOwnedIdentity())) {
                Logger.w("Found a ProfileBackupThreadId for an unknown OwnedIdentity --> cleaning it up!");
                profileBackupThreadId.delete();
                commitNeeded = true;
            }
        }

        // left over ownedIdentities are missing ProfileBackupThreadId --> create them
        for (Identity ownedIdentity : ownedIdentities) {
            Logger.i("Found an ownedIdentity without a ProfileBackupThreadId --> creating one!");
            ProfileBackupThreadId profileBackupThreadId = ProfileBackupThreadId.create(backupManagerSession, ownedIdentity, prng);
            if (profileBackupThreadId != null) {
                profileBackupThreadIds.add(profileBackupThreadId);
                commitNeeded = true;
            }
        }

        if (commitNeeded) {
            backupManagerSession.session.commit();
        }

        for (ProfileBackupThreadId profileBackupThreadId : profileBackupThreadIds) {
            scheduleProfileBackup(profileBackupThreadId.getOwnedIdentity(), profileBackupThreadId.getNextBackupTimestamp());
        }
    }


    private void scheduleDeviceBackup(long timestamp) {
        synchronized (scheduledBackups) {
            for (ScheduledBackup scheduledBackup : scheduledBackups) {
                if (scheduledBackup.ownedIdentity == null) {
                    if (scheduledBackup.timestamp < timestamp) {
                        // we already planned a backup sooner --> nothing to do
                        return;
                    } else {
                        // the new timestamp comes sooner --> remove old ScheduledBackup
                        scheduledBackups.remove(scheduledBackup);
                        break;
                    }
                }
            }

            // insert the ScheduledBackup
            insertScheduledBackup(new ScheduledBackup(null, timestamp));
        }
    }

    private void scheduleProfileBackup(Identity ownedIdentity, long timestamp) {
        synchronized (scheduledBackups) {
            for (ScheduledBackup scheduledBackup : scheduledBackups) {
                if (Objects.equals(scheduledBackup.ownedIdentity, ownedIdentity)) {
                    if (scheduledBackup.timestamp < timestamp) {
                        // we already planned a backup sooner --> nothing to do
                        return;
                    } else {
                        // the new timestamp comes sooner --> remove old ScheduledBackup
                        scheduledBackups.remove(scheduledBackup);
                        break;
                    }
                }
            }

            // insert the ScheduledBackup
            insertScheduledBackup(new ScheduledBackup(ownedIdentity, timestamp));
        }
    }

    private void cancelScheduledDeviceAndProfileBackups() {
        synchronized (scheduledBackups) {
            scheduledBackups.clear();
        }
    }

    private void insertScheduledBackup(ScheduledBackup scheduledBackup) {
        synchronized (scheduledBackups) {
            scheduledBackups.add(scheduledBackup);

            if (scheduledBackup.scheduledTimestamp > System.currentTimeMillis()) {
                // this backup should be scheduled in the future
                if (nextScheduledBackupTimestamp == null || scheduledBackup.scheduledTimestamp < nextScheduledBackupTimestamp) {
                    // this will be the next backup --> schedule a task
                    nextScheduledBackupTimestamp = scheduledBackup.scheduledTimestamp;
                    autoBackupScheduler.schedule(this::executeScheduledBackupsThatReachedTheirTimestamp, scheduledBackup.scheduledTimestamp - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                }
            } else {
                executeScheduledBackupsThatReachedTheirTimestamp();
            }
        }
    }

    private void executeScheduledBackupsThatReachedTheirTimestamp() {
        synchronized (scheduledBackups) {
            long now = System.currentTimeMillis();
            Long nextTimestamp = null;
            for (ScheduledBackup scheduledBackup : new ArrayList<>(scheduledBackups)) {
                if (scheduledBackup.scheduledTimestamp < now) {
                    scheduledBackups.remove(scheduledBackup);
                    initiateBackup(scheduledBackup);
                } else {
                    if (nextTimestamp == null || scheduledBackup.scheduledTimestamp < nextTimestamp) {
                        nextTimestamp = scheduledBackup.scheduledTimestamp;
                    }
                }
            }
            if (nextTimestamp != null) {
                nextScheduledBackupTimestamp = nextTimestamp;
                autoBackupScheduler.schedule(this::executeScheduledBackupsThatReachedTheirTimestamp, nextTimestamp - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private void initiateBackup(ScheduledBackup scheduledBackup) {
        executor.execute(() -> {
            if (scheduledBackup.ownedIdentity == null) {
                DeviceBackupUploadTask deviceBackupUploadTask = new DeviceBackupUploadTask(this, sslSocketFactory);
                switch (deviceBackupUploadTask.execute()) {
                    case SUCCESS -> {
                        // backup successful --> update nextBackupTimestamp and reschedule
                        try (BackupManagerSession backupManagerSession = getSession()) {
                            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
                            if (deviceBackupSeed != null) {
                                long nextBackupTimestamp = System.currentTimeMillis() + Constants.DEVICE_BACKUP_INTERVAL;
                                deviceBackupSeed.setNextBackupTimestamp(nextBackupTimestamp);
                                backupManagerSession.session.commit();
                                scheduleDeviceBackup(nextBackupTimestamp);
                            }
                        } catch (Exception e) {
                            Logger.x(e);
                        }
                    }
                    case RETRIABLE_FAILURE -> {
                        synchronized (scheduledBackups) {
                            scheduledBackup.rescheduleAfterRetriableFailure();
                            insertScheduledBackup(scheduledBackup);
                        }
                    }
                    case PERMANENT_FAILURE -> {
                        // nothing to do, but this should only happen if the device backupSeed was cleared
                    }
                }
            } else {
                ProfileBackupUploadTask profileBackupUploadTask = new ProfileBackupUploadTask(this, sslSocketFactory, scheduledBackup.ownedIdentity);
                switch (profileBackupUploadTask.execute()) {
                    case SUCCESS -> {
                        // backup successful --> update nextBackupTimestamp and reschedule
                        try (BackupManagerSession backupManagerSession = getSession()) {
                            ProfileBackupThreadId profileBackupThreadId = ProfileBackupThreadId.get(backupManagerSession, scheduledBackup.ownedIdentity);
                            if (profileBackupThreadId != null) {
                                long nextBackupTimestamp = System.currentTimeMillis() + Constants.PROFILE_BACKUP_INTERVAL;
                                profileBackupThreadId.setNextBackupTimestamp(nextBackupTimestamp);
                                backupManagerSession.session.commit();
                                scheduleProfileBackup(scheduledBackup.ownedIdentity, nextBackupTimestamp);
                            }
                        } catch (Exception e) {
                            Logger.x(e);
                        }
                    }
                    case RETRIABLE_FAILURE -> {
                        synchronized (scheduledBackups) {
                            scheduledBackup.rescheduleAfterRetriableFailure();
                            insertScheduledBackup(scheduledBackup);
                        }
                    }
                    case PERMANENT_FAILURE -> {
                        // nothing to do, but this should only happen if the profile no longer exists on the device or if the device backupSeed was cleared
                    }
                }
            }
        });
    }


    private void cleanUpDeviceBackups(BackupSeed backupSeed) {
        executor.execute(() -> {
            DeviceBackupDeleteTask deviceBackupDeleteTask = new DeviceBackupDeleteTask(this, sslSocketFactory, backupSeed);
            switch (deviceBackupDeleteTask.execute()) {
                case SUCCESS -> {
                    // delete successful --> delete the DeviceBackupSeed (if indeed inactive !)
                    try (BackupManagerSession backupManagerSession = getSession()) {
                        DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.get(backupManagerSession, backupSeed);
                        if (deviceBackupSeed != null && !deviceBackupSeed.isActive()) {
                            deviceBackupSeed.delete();
                            backupManagerSession.session.commit();
                        }
                    } catch (Exception e) {
                        Logger.x(e);
                    }
                }

                // retry in 5 minutes, no need for exponential backoff or retry on internet connection found
                case RETRIABLE_FAILURE -> autoBackupScheduler.schedule(() -> cleanUpDeviceBackups(backupSeed), 5, TimeUnit.MINUTES);

                case PERMANENT_FAILURE -> {
                    // nothing to do
                }
            }
        });
    }


    // endregion




    // region implement BackupV2Delegate

    @Override
    public String generateDeviceBackupSeed(String server) throws Exception {
        try (BackupManagerSession backupManagerSession = getSession()) {
            synchronized (this) {
                DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
                if (deviceBackupSeed != null) {
                    throw new Exception("A DeviceBackupSeed already exists");
                }
                deviceBackupSeed = DeviceBackupSeed.create(backupManagerSession, BackupSeed.generate(prng), server);
                if (deviceBackupSeed != null) {
                    // also reset the nextBackupTimestamp of all ProfileBackupThreadId
                    for (ProfileBackupThreadId profileBackupThreadId : ProfileBackupThreadId.getAll(backupManagerSession)) {
                        profileBackupThreadId.setNextBackupTimestamp(0);
                    }
                    backupManagerSession.session.commit();
                    scheduleDeviceAndAllProfilesBackup(backupManagerSession, deviceBackupSeed);
                    return deviceBackupSeed.getBackupSeed().toString();
                }
            }
        }
        return null;
    }

    @Override
    public String getCurrentDeviceBackupSeed() throws Exception {
        try (BackupManagerSession backupManagerSession = getSession()) {
            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
            if (deviceBackupSeed != null) {
                return deviceBackupSeed.getBackupSeed().toString();
            }
        }
        return null;
    }

    @Override
    public void deleteDeviceBackupSeed(BackupSeed backupSeed) throws Exception {
        try (BackupManagerSession backupManagerSession = getSession()) {
            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
            if (deviceBackupSeed != null && deviceBackupSeed.getBackupSeed().equals(backupSeed)) {
                deviceBackupSeed.markBackupKeyInactive();
                backupManagerSession.session.commit();
                cancelScheduledDeviceAndProfileBackups();
                cleanUpDeviceBackups(deviceBackupSeed.getBackupSeed());
            }
        }
    }

    @Override
    public boolean backupDeviceAndProfilesNow() {
        try (BackupManagerSession backupManagerSession = getSession()) {
            if (DeviceBackupSeed.getActive(backupManagerSession) != null) {
                scheduleDeviceBackup(0);

                HashSet<Identity> ownedIdentities = new HashSet<>();
                for (Identity ownedIdentity : backupManagerSession.identityDelegate.getOwnedIdentities(backupManagerSession.session)) {
                    scheduleProfileBackup(ownedIdentity, 0);
                    ownedIdentities.add(ownedIdentity);
                }

                final Object lock = new Object();
                final AtomicBoolean success = new AtomicBoolean(false);
                // we post this on the executor so it is executed once all backups are finished
                // if no backup was re-queued with a failed attempt count, everything went as expected
                executor.execute(() -> {
                    try {
                        boolean allGood = true;
                        synchronized (scheduledBackups) {
                            for (ScheduledBackup scheduledBackup : scheduledBackups) {
                                if (scheduledBackup.failedAttemptCounts != 0
                                        && (scheduledBackup.ownedIdentity == null || ownedIdentities.contains(scheduledBackup.ownedIdentity))) {
                                    allGood = false;
                                    break;
                                }
                            }
                        }
                        success.set(allGood);
                    } finally {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                });
                // wait for the check to execute
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                return success.get();
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
        return false;
    }

    @Override
    public ObvDeviceBackupForRestore fetchDeviceBackup(String server, BackupSeed backupSeed) {
        DeviceBackupFetchTask deviceBackupFetchTask = new DeviceBackupFetchTask(server, backupSeed, this, sslSocketFactory);
        switch (deviceBackupFetchTask.execute()) {
            case SUCCESS -> {
                // download successful
                return deviceBackupFetchTask.getObvDeviceBackupForRestore();
            }

            // failed to download
            case RETRIABLE_FAILURE -> {
                if (deviceBackupFetchTask.getObvDeviceBackupForRestore() != null) {
                    return deviceBackupFetchTask.getObvDeviceBackupForRestore();
                }
            }
            case PERMANENT_FAILURE -> {
                if (deviceBackupFetchTask.getObvDeviceBackupForRestore() != null) {
                    return deviceBackupFetchTask.getObvDeviceBackupForRestore();
                }
                return new ObvDeviceBackupForRestore(ObvDeviceBackupForRestore.Status.PERMANENT_ERROR, null, null);
            }
        }
        return new ObvDeviceBackupForRestore(ObvDeviceBackupForRestore.Status.ERROR, null, null);
    }

    @Override
    public ObvProfileBackupsForRestore fetchProfileBackups(String server, BackupSeed backupSeed) {
        ProfileBackupsFetchTask profileBackupsFetchTask = new ProfileBackupsFetchTask(server, backupSeed, this, sslSocketFactory);
        switch (profileBackupsFetchTask.execute()) {
            case SUCCESS -> {
                // download successful
                return profileBackupsFetchTask.getObvProfileBackupsForRestore();
            }

            // failed to download
            case RETRIABLE_FAILURE -> {
                if (profileBackupsFetchTask.getObvProfileBackupsForRestore() != null) {
                    return profileBackupsFetchTask.getObvProfileBackupsForRestore();
                }
            }
            case PERMANENT_FAILURE -> {
                if (profileBackupsFetchTask.getObvProfileBackupsForRestore() != null) {
                    return profileBackupsFetchTask.getObvProfileBackupsForRestore();
                }
                return new ObvProfileBackupsForRestore(ObvProfileBackupsForRestore.Status.PERMANENT_ERROR, null, null);
            }
        }
        return new ObvProfileBackupsForRestore(ObvProfileBackupsForRestore.Status.ERROR, null, null);
    }

    @Override
    public boolean deleteProfileBackupSnapshot(String server, BackupSeed backupSeed, UID backupThreadId, long version) {
        ProfileBackupSnapshotDeleteTask profileBackupSnapshotDeleteTask = new ProfileBackupSnapshotDeleteTask(server, backupSeed, backupThreadId, version, prng, sslSocketFactory);
        switch (profileBackupSnapshotDeleteTask.execute()) {
            case SUCCESS -> {
                return true;
            }
            case RETRIABLE_FAILURE, PERMANENT_FAILURE -> {
                return  false;
            }
        }
        return false;
    }

    // endregion














    // region implement BackupDelegate

//    @Override
//    public void generateNewBackupKey() {
//        try {
//            BackupSeed backupSeed = BackupSeed.generate(prng);
//            if (backupSeed == null) {
//                throw new Exception("Failed to generate BackupSeed");
//            }
//            BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();
//            if (derivedKeys == null) {
//                throw new Exception("Failed to derive keys from BackupSeed");
//            }
//            try (BackupManagerSession backupManagerSession = getSession()) {
//                backupManagerSession.session.startTransaction();
//                BackupKey.deleteAll(backupManagerSession);
//                BackupKey.create(backupManagerSession, derivedKeys.backupKeyUid, derivedKeys.encryptionKeyPair.getPublicKey(), derivedKeys.macKey);
//                backupManagerSession.session.commit();
//
//                // if autobackup is active --> immediately backup
//                if (autoBackupEnabled) {
//                    initiateBackup(false);
//                }
//
//                HashMap<String, Object> userInfo = new HashMap<>();
//                userInfo.put(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED_SEED_KEY, backupSeed.toString());
//                notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED, userInfo);
//            } catch (SQLException e) {
//                Logger.x(e);
//                throw new Exception("Failed to save new BackupKey to database");
//            }
//        } catch (Exception e) {
//            Logger.x(e);
//            notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED, new HashMap<>());
//        }
//    }

//    @Override
//    public int verifyBackupKey(String seedString) {
//        try (BackupManagerSession backupManagerSession = getSession()) {
//            BackupKey[] backupKeys = BackupKey.getAll(backupManagerSession);
//            if (backupKeys.length == 0) {
//                throw new Exception("No BackupKey generated!");
//            } else if (backupKeys.length > 1) {
//                throw new Exception("Multiple BackupKey generated, this should never occur!");
//            }
//            BackupKey backupKey = backupKeys[0];
//
//            BackupSeed backupSeed = new BackupSeed(seedString);
//            BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();
//
//            if (derivedKeys.macKey.equals(backupKey.getMacKey()) &&
//                    derivedKeys.encryptionKeyPair.getPublicKey().equals(backupKey.getEncryptionPublicKey())) {
//                // we have the same keys, everything is fine
//                backupKey.addSuccessfulVerification();
//
//                notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL, Collections.emptyMap());
//                return BACKUP_SEED_VERIFICATION_STATUS_SUCCESS;
//            }
//            return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
//        } catch (BackupSeed.SeedTooShortException e) {
//            return BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT;
//        } catch (BackupSeed.SeedTooLongException e) {
//            return BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG;
//        } catch (Exception e) {
//            return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
//        }
//    }


    @Override
    public void stopLegacyBackups() {
        autoBackupEnabled = false;
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey.deleteAll(backupManagerSession);
            backupManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void setAutoBackupEnabled(boolean enabled, boolean initiateBackupNowIfNeeded) {
        autoBackupEnabled = enabled;
        if (!enabled || !initiateBackupNowIfNeeded) {
            return;
        }

        // check the last time a backup was uploaded and initiate one if this was long ago
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey[] backupKeys = BackupKey.getAll(backupManagerSession);
            if (backupKeys.length == 1) {
                BackupKey backupKey = backupKeys[0];
                Backup lastUploadedBackup = backupKey.getUploadedBackup();
                if (lastUploadedBackup == null
                        || (backupKey.getLatestBackupVersion() != null && backupKey.getLatestBackupVersion() > lastUploadedBackup.getVersion())
                        || ((System.currentTimeMillis() - lastUploadedBackup.getStatusChangeTimestamp()) > Constants.AUTOBACKUP_MAX_INTERVAL)
                        || lastUploadedBackup.getBackupJsonVersion() != Constants.CURRENT_BACKUP_JSON_VERSION) {
                    scheduleBackupForUploadIfNeeded(true);
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void initiateBackup(boolean forExpert) {
        executor.execute(() -> {
            try (BackupManagerSession backupManagerSession = getSession()) {
                BackupKey[] backupKeys = BackupKey.getAll(backupManagerSession);
                if (backupKeys.length == 0) {
                    throw new Exception("No BackupKey generated!");
                } else if (backupKeys.length > 1) {
                    throw new Exception("Multiple BackupKey generated, this should never occur!");
                }
                BackupKey backupKey = backupKeys[0];

                Logger.d("Initiating a backup");
                backupManagerSession.session.startTransaction();
                Integer newVersion = backupKey.getLatestBackupVersion();
                if (newVersion == null) {
                    newVersion = 0;
                } else {
                    newVersion++;
                }
                Backup backup = Backup.createOngoingBackup(backupManagerSession, backupKey.getUid(), newVersion, forExpert);
                if (backup == null) {
                    throw new Exception("BackupManager failed to create ongoing backup in DB");
                }
                backupKey.setLatestBackupVersion(newVersion);
                backupManagerSession.session.commit();

                UidAndVersion uidAndVersion = new UidAndVersion(backupKey.getUid(), newVersion);
                ongoingBackupMap.remove(uidAndVersion);
                ongoingBackupMap.put(uidAndVersion, new HashMap<>());

                ScheduledFuture<?> previousTimeout = ongoingBackupTimeoutMap.remove(uidAndVersion);
                if (previousTimeout != null) {
                    previousTimeout.cancel(false);
                }
                int finalNewVersion = newVersion;
                ongoingBackupTimeoutMap.put(
                        uidAndVersion,
                        autoBackupScheduler.schedule(() -> backupFailed("TIMEOUT", backupKey.getUid(), finalNewVersion), 30_000, TimeUnit.MILLISECONDS)
                );

                // request backup from identityManager
                identityDelegate.initiateBackup(this, IDENTITY_BACKUP_TAG, backupKey.getUid(), newVersion);

                // request backup from App (through notification)
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_BACKUP_KEY_UID_KEY, backupKey.getUid());
                userInfo.put(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_VERSION_KEY, newVersion);
                notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST, userInfo);
            } catch (Exception e) {
                Logger.x(e);
                // nothing to do...
            }
        });
    }

    @Override
    public void backupFailed(String tag, UID backupKeyUid, int version) {
        executor.execute(() -> {
            Logger.w("Backup failed for tag: " + tag);

            UidAndVersion uidAndVersion = new UidAndVersion(backupKeyUid, version);
            ongoingBackupMap.remove(uidAndVersion);
            ScheduledFuture<?> timeout = ongoingBackupTimeoutMap.remove(uidAndVersion);
            if (timeout != null) {
                timeout.cancel(false);
            }

            try (BackupManagerSession backupManagerSession = getSession()) {
                Backup backup = Backup.get(backupManagerSession, backupKeyUid, version);
                if (backup != null && backup.getStatus() == Backup.STATUS_ONGOING) {
                    backup.setFailed();
                    if (backup.isForExport()) {
                        notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED, Collections.emptyMap());
                    }
                }
            } catch (SQLException e) {
                // nothing to do
            }
        });
    }

    @Override
    public void backupSuccess(String tag, UID backupKeyUid, int version, String backupContent) {
        executor.execute(() -> {
            try (BackupManagerSession backupManagerSession = getSession()) {
                BackupKey backupKey = BackupKey.get(backupManagerSession, backupKeyUid);
                if (backupKey == null) {
                    throw new Exception("BackupKey not found");
                }
                Backup backup = Backup.get(backupManagerSession, backupKeyUid, version);
                if (backup == null || backup.getStatus() != Backup.STATUS_ONGOING) {
                    throw new Exception("Ongoing Backup not found");
                }

                UidAndVersion uidAndVersion = new UidAndVersion(backupKeyUid, version);
                Map<String, String> backupParts = ongoingBackupMap.get(uidAndVersion);
                if (backupParts == null) {
                    // this should never happen!
                    throw new Exception("Unable to find ongoing backup parts map");
                }

                if (backupParts.containsKey(tag)) {
                    // this should never happen!
                    throw new Exception("Received 2 backups for the same tag!");
                }

                // store the backup content in memory
                backupParts.put(tag, backupContent);

                // check if all parts of the backup have been received
                boolean complete = true;
                for (String backupTag : ALL_BACKUP_TAGS) {
                    if (!backupParts.containsKey(backupTag)) {
                        complete = false;
                        break;
                    }
                }

                if (complete) {
                    // all parts of the backup have been received --> finalize the backup
                    Pojo_0 pojo = new Pojo_0();

                    EnginePojo_0 enginePojo = new EnginePojo_0();
                    enginePojo.identity_manager = backupParts.get(IDENTITY_BACKUP_TAG);
                    pojo.engine = enginePojo;

                    pojo.app = backupParts.get(APP_BACKUP_TAG);

                    pojo. backup_json_version = backup.getBackupJsonVersion();
                    pojo.backup_timestamp = System.currentTimeMillis();
                    String fullBackupContent = jsonObjectMapper.writeValueAsString(pojo);

                    EncryptionPublicKey encryptionPublicKey = backupKey.getEncryptionPublicKey();
                    MACKey macKey = backupKey.getMacKey();

                    EncryptedBytes encryptedBackup = Suite.getPublicKeyEncryption(encryptionPublicKey).encrypt(encryptionPublicKey, fullBackupContent.getBytes(StandardCharsets.UTF_8), prng);
                    byte[] mac = Suite.getMAC(macKey).digest(macKey, encryptedBackup.getBytes());

                    byte[] macedEncryptedBackup = new byte[encryptedBackup.getBytes().length + mac.length];
                    System.arraycopy(encryptedBackup.getBytes(), 0, macedEncryptedBackup, 0, encryptedBackup.getBytes().length);
                    System.arraycopy(mac, 0, macedEncryptedBackup, encryptedBackup.getBytes().length, mac.length);

                    backup.setReady(macedEncryptedBackup);

                    // cleanup the maps
                    ongoingBackupMap.remove(uidAndVersion);
                    ScheduledFuture<?> timeout = ongoingBackupTimeoutMap.remove(uidAndVersion);
                    if (timeout != null) {
                        timeout.cancel(false);
                    }

                    if (backup.isForExport()) {
                        HashMap<String, Object> userInfo = new HashMap<>();
                        userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_BACKUP_KEY_UID_KEY, backupKeyUid);
                        userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY, version);
                        userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY, macedEncryptedBackup);
                        notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED, userInfo);
                    }

                    HashMap<String, Object> userInfo = new HashMap<>();
                    userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_BACKUP_KEY_UID_KEY, backupKeyUid);
                    userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_VERSION_KEY, version);
                    userInfo.put(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY, macedEncryptedBackup);
                    notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_FINISHED, userInfo);
                }
            } catch (Exception e) {
                Logger.x(e);
                backupFailed(tag, backupKeyUid, version);
            }
        });
    }

    @Override
    public ObvBackupKeyInformation getBackupKeyInformation() throws Exception {
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey[] backupKeys = BackupKey.getAll(backupManagerSession);
            if (backupKeys.length == 0) {
                Logger.d("No BackupKey generated!");
                return null;
            } else if (backupKeys.length > 1) {
                Logger.e("Multiple BackupKey generated, this should never occur!");
                return null;
            }
            BackupKey backupKey = backupKeys[0];

            Backup exportedBackup = backupKey.getExportedBackup();
            Backup uploadedBackup = backupKey.getUploadedBackup();
            return new ObvBackupKeyInformation(
                    backupKey.getKeyGenerationTimestamp(),
                    backupKey.getLastSuccessfulKeyVerificationTimestamp(),
                    backupKey.getSuccessfulVerificationCount(),
                    exportedBackup == null ? 0L : exportedBackup.getStatusChangeTimestamp(),
                    uploadedBackup == null ? 0L : uploadedBackup.getStatusChangeTimestamp()
            );
        }
    }

    @Override
    public void markBackupExported(UID backupKeyUid, int version) {
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey backupKey = BackupKey.get(backupManagerSession, backupKeyUid);
            if (backupKey == null) {
                return;
            }
            Backup backup = Backup.get(backupManagerSession, backupKeyUid, version);
            if (backup == null || backup.getStatus() != Backup.STATUS_READY || !backup.isForExport()) {
                return;
            }
            backupManagerSession.session.startTransaction();
            backup.setUploadedOrExported();
            if (backupKey.getExportedBackupVersion() == null || backupKey.getExportedBackupVersion() < version) {
                backupKey.setExportedBackupVersion(version);
            }
            backupManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void markBackupUploaded(UID backupKeyUid, int version) {
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey backupKey = BackupKey.get(backupManagerSession, backupKeyUid);
            if (backupKey == null) {
                return;
            }
            Backup backup = Backup.get(backupManagerSession, backupKeyUid, version);
            if (backup == null || (backup.getStatus() != Backup.STATUS_READY && backup.getStatus() != Backup.STATUS_UPLOADED_OR_EXPORTED)) {
                return;
            }
            backupManagerSession.session.startTransaction();
            backup.setUploadedOrExported();
            if (backupKey.getUploadedBackupVersion() == null || backupKey.getUploadedBackupVersion() < version) {
                backupKey.setUploadedBackupVersion(version);
            }
            backupManagerSession.session.commit();
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public void discardBackup(UID backupKeyUid, int version) {
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey backupKey = BackupKey.get(backupManagerSession, backupKeyUid);
            if (backupKey == null) {
                return;
            }
            Backup backup = Backup.get(backupManagerSession, backupKeyUid, version);
            if (backup == null || backup.getStatus() != Backup.STATUS_READY) {
                return;
            }
            backup.setFailed();
            Logger.d("Backup discarded.");
        } catch (SQLException e) {
            Logger.x(e);
        }
    }

    @Override
    public int validateBackupSeed(String seedString, byte[] backupContent) {
        try {
            BackupSeed backupSeed = new BackupSeed(seedString);
            BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();
            if (derivedKeys == null) {
                return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
            }
            MAC mac = Suite.getMAC(derivedKeys.macKey);
            byte[] ciphertext = Arrays.copyOfRange(backupContent, 0, backupContent.length - mac.outputLength());
            byte[] expectedMacOutput = Arrays.copyOfRange(backupContent, backupContent.length - mac.outputLength(), backupContent.length);
            if (!mac.verify(derivedKeys.macKey, ciphertext, expectedMacOutput)) {
                return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
            }
            PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(derivedKeys.encryptionKeyPair.getPrivateKey());
            publicKeyEncryption.decrypt(derivedKeys.encryptionKeyPair.getPrivateKey(), new EncryptedBytes(ciphertext));
            return BACKUP_SEED_VERIFICATION_STATUS_SUCCESS;
        } catch (BackupSeed.SeedTooShortException e) {
            return BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT;
        } catch (BackupSeed.SeedTooLongException e) {
            return BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG;
        } catch (Exception e) {
            return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
        }
    }

    private static class BackupContentAndDerivedKeys {
        private final Pojo_0 pojo;
        private final BackupSeed.DerivedKeys derivedKeys;

        public BackupContentAndDerivedKeys(Pojo_0 pojo, BackupSeed.DerivedKeys derivedKeys) {
            this.pojo = pojo;
            this.derivedKeys = derivedKeys;
        }
    }

    private BackupContentAndDerivedKeys decryptBackupContent(String seedString, byte[] backupContent, ObjectMapper jsonObjectMapper) throws Exception {
        BackupSeed backupSeed = new BackupSeed(seedString);
        BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();
        if (derivedKeys == null) {
            return null;
        }
        MAC mac = Suite.getMAC(derivedKeys.macKey);
        byte[] ciphertext = Arrays.copyOfRange(backupContent, 0, backupContent.length - mac.outputLength());
        byte[] expectedMacOutput = Arrays.copyOfRange(backupContent, backupContent.length - mac.outputLength(), backupContent.length);
        if (!mac.verify(derivedKeys.macKey, ciphertext, expectedMacOutput)) {
            return null;
        }
        PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(derivedKeys.encryptionKeyPair.getPrivateKey());
        byte[] plaintext = publicKeyEncryption.decrypt(derivedKeys.encryptionKeyPair.getPrivateKey(), new EncryptedBytes(ciphertext));

        // If we reach this point, decryption was successful --> we need to distinguish between a compressed backup (legacy) and an uncompressed backup
        try {
            // first, try to directly parse our pojo (will fail for compressed backups
            Pojo_0 pojo = jsonObjectMapper.readValue(plaintext, Pojo_0.class);
            return new BackupContentAndDerivedKeys(pojo, derivedKeys);
        } catch (Exception ignored) { }

        // if direct parsing failed, we have a compressed backup -> deflate and parse
        try (ByteArrayInputStream bais = new ByteArrayInputStream(plaintext);
             InflaterInputStream inflater = new InflaterInputStream(bais, new Inflater(true));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int c;
            while ((c = inflater.read(buffer)) != -1) {
                baos.write(buffer, 0, c);
            }

            Pojo_0 pojo = jsonObjectMapper.readValue(baos.toByteArray(), Pojo_0.class);
            return new BackupContentAndDerivedKeys(pojo, derivedKeys);
        }
    }

    @Override
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String seedString, byte[] backupContent, String deviceDisplayName) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent, jsonObjectMapper);
            if (backupContentAndDerivedKeys == null) {
                return null;
            }

            if (backupContentAndDerivedKeys.pojo.backup_json_version != Constants.CURRENT_BACKUP_JSON_VERSION) {
                // do an upgrade when needed
                Logger.e("Restoring ownedIdentity with a different backup JSON version:" + backupContentAndDerivedKeys.pojo.backup_json_version + " (expecting " + Constants.CURRENT_BACKUP_JSON_VERSION + ").");
                return null;
            }

            // store the key used to restore the backup as the backup key (if no BackupKey exist)
            try (BackupManagerSession backupManagerSession = getSession()) {
                if (BackupKey.getAll(backupManagerSession).length == 0) {
                    BackupKey backupKey = BackupKey.create(backupManagerSession, backupContentAndDerivedKeys.derivedKeys.backupKeyUid, backupContentAndDerivedKeys.derivedKeys.encryptionKeyPair.getPublicKey(), backupContentAndDerivedKeys.derivedKeys.macKey);
                    backupKey.addSuccessfulVerification();
                    backupManagerSession.session.commit();
                }
            }

            return identityDelegate.restoreOwnedIdentitiesFromBackup(backupContentAndDerivedKeys.pojo.engine.identity_manager, deviceDisplayName, prng);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }


    @Override
    public void restoreContactsAndGroupsFromBackup(String seedString, byte[] backupContent, ObvIdentity[] restoredOwnedIdentities) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent, jsonObjectMapper);
            if (backupContentAndDerivedKeys == null) {
                return;
            }

            if (backupContentAndDerivedKeys.pojo.backup_json_version != Constants.CURRENT_BACKUP_JSON_VERSION) {
                // do an upgrade when needed
                Logger.e("Restoring contacts and groups with a different backup JSON version:" + backupContentAndDerivedKeys.pojo.backup_json_version + " (expecting " + Constants.CURRENT_BACKUP_JSON_VERSION + ").");
                return;
            }

            identityDelegate.restoreContactsAndGroupsFromBackup(backupContentAndDerivedKeys.pojo.engine.identity_manager, restoredOwnedIdentities, backupContentAndDerivedKeys.pojo.backup_timestamp);

            notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED, Collections.emptyMap());
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    public String decryptAppDataBackup(String seedString, byte[] backupContent) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent, jsonObjectMapper);
            if (backupContentAndDerivedKeys == null) {
                return null;
            }

            if (backupContentAndDerivedKeys.pojo != null) {
                return backupContentAndDerivedKeys.pojo.app;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // endregion

    // region NotificationListener

    @Override
    public void callback(String notificationName, Map<String, Object> userInfo) {
        switch (notificationName) {
            case IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED: {
                if (autoBackupEnabled) {
                    scheduleBackupForUploadIfNeeded(false);
                }
                break;
            }
            case BackupNotifications.NOTIFICATION_DEVICE_BACKUP_NEEDED: {
                if (deviceBackupsActive) {
                    long targetTimestamp = System.currentTimeMillis() + Constants.BACKUP_START_DELAY;
                    synchronized (scheduledBackups) {
                        boolean doBackup = true;
                        for (ScheduledBackup scheduledBackup : scheduledBackups) {
                            if (scheduledBackup.ownedIdentity == null) {
                                if (scheduledBackup.timestamp < targetTimestamp) {
                                    doBackup = false;
                                }
                                break;
                            }
                        }
                        if (doBackup) {
                            try (BackupManagerSession backupManagerSession = getSession()) {
                                DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
                                if (deviceBackupSeed != null && deviceBackupSeed.getNextBackupTimestamp() > targetTimestamp) {
                                    deviceBackupSeed.setNextBackupTimestamp(targetTimestamp);
                                    backupManagerSession.session.commit();
                                    scheduleDeviceBackup(targetTimestamp);
                                }
                            } catch (Exception e) {
                                Logger.x(e);
                            }
                        }
                    }
                }
                break;
            }
            case BackupNotifications.NOTIFICATION_PROFILE_BACKUP_NEEDED: {
                if (deviceBackupsActive) {
                    Identity ownedIdentity = (Identity) userInfo.get(BackupNotifications.NOTIFICATION_PROFILE_BACKUP_NEEDED_OWNED_IDENTITY);
                    if (ownedIdentity == null) {
                        break;
                    }
                    long targetTimestamp = System.currentTimeMillis() + Constants.BACKUP_START_DELAY;
                    synchronized (scheduledBackups) {
                        boolean doBackup = true;
                        for (ScheduledBackup scheduledBackup : scheduledBackups) {
                            if (ownedIdentity.equals(scheduledBackup.ownedIdentity)) {
                                if (scheduledBackup.timestamp < targetTimestamp) {
                                    doBackup = false;
                                }
                                break;
                            }
                        }
                        if (doBackup) {
                            try (BackupManagerSession backupManagerSession = getSession()) {
                                ProfileBackupThreadId profileBackupThreadId = ProfileBackupThreadId.get(backupManagerSession, ownedIdentity);
                                if (profileBackupThreadId != null && profileBackupThreadId.getNextBackupTimestamp() > targetTimestamp) {
                                    profileBackupThreadId.setNextBackupTimestamp(targetTimestamp);
                                    backupManagerSession.session.commit();
                                    scheduleProfileBackup(ownedIdentity, targetTimestamp);
                                }
                            } catch (Exception e) {
                                Logger.x(e);
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    private void scheduleBackupForUploadIfNeeded(boolean immediately) {
        synchronized (autoBackupSchedulerLock) {
            if (immediately) {
                if (autoBackupIsScheduled && scheduledAutoBackupTask != null) {
                    scheduledAutoBackupTask.cancel(true);
                    scheduledAutoBackupTask = null;
                }
                Logger.d("Immediately running a backup upload to the cloud");
                autoBackupScheduler.submit(() -> initiateBackup(false));
            } else {
                if (autoBackupIsScheduled) {
                    return;
                }
                Logger.d("Scheduling a backup upload to the cloud");
                autoBackupIsScheduled = true;
                scheduledAutoBackupTask = autoBackupScheduler.schedule(() -> {
                    synchronized (autoBackupSchedulerLock) {
                        autoBackupIsScheduled = false;
                        scheduledAutoBackupTask = null;
                    }
                    initiateBackup(false);
                }, Constants.AUTOBACKUP_START_DELAY, TimeUnit.MILLISECONDS);
            }
        }
    }

    // endregion

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Pojo_0 {
        public EnginePojo_0 engine;
        public String app;
        public int backup_json_version;
        public long backup_timestamp;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EnginePojo_0 {
        public String identity_manager;
    }


    private static final class UidAndVersion {
        public final UID uid;
        public final int version;

        public UidAndVersion(UID uid, int version) {
            this.uid = uid;
            this.version = version;
        }

        @Override
        public int hashCode() {
            return uid.hashCode() * 31 + version;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UidAndVersion o)) {
                return false;
            }
            return Objects.equals(uid, o.uid) && version == o.version;
        }
    }

    private static final class ScheduledBackup {
        final Identity ownedIdentity; // null for device backups
        final long timestamp;
        int failedAttemptCounts;
        long scheduledTimestamp;

        public ScheduledBackup(Identity ownedIdentity, long timestamp) {
            this.ownedIdentity = ownedIdentity;
            this.timestamp = timestamp;
            this.failedAttemptCounts = 0;
            this.scheduledTimestamp = timestamp;
        }

        void rescheduleAfterRetriableFailure() {
            failedAttemptCounts++;
            long base = Constants.BASE_RESCHEDULING_TIME << Math.min(failedAttemptCounts, 32);
            scheduledTimestamp = System.currentTimeMillis() + (long) (base * (1 + new Random().nextFloat()));
        }

        void clearFailedAttemptCounts() {
            failedAttemptCounts = 0;
            scheduledTimestamp = timestamp;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ScheduledBackup o)) {
                return false;
            }
            return timestamp == o.timestamp && Objects.equals(ownedIdentity, o.ownedIdentity);
        }

        @Override
        public int hashCode() {
            if (ownedIdentity == null) {
                return Long.hashCode(timestamp);
            }
            return ownedIdentity.hashCode() * 31 + Long.hashCode(timestamp);
        }
    }
}
