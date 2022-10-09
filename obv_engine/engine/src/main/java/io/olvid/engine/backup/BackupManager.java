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

package io.olvid.engine.backup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.databases.Backup;
import io.olvid.engine.backup.databases.BackupKey;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
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
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.metamanager.BackupDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;


public class BackupManager implements BackupDelegate, ObvManager, NotificationListener {
    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;

    private CreateSessionDelegate createSessionDelegate;
    private IdentityDelegate identityDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;
    private NotificationListeningDelegate notificationListeningDelegate;

    private final NoExceptionSingleThreadExecutor executor;
    private final ScheduledExecutorService autoBackupScheduler;
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


    public BackupManager(MetaManager metaManager, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;
        this.executor = new NoExceptionSingleThreadExecutor("BackupManager executor");
        this.autoBackupScheduler = Executors.newScheduledThreadPool(1);
        this.autoBackupEnabled = false;
        this.autoBackupIsScheduled = false;
        this.autoBackupSchedulerLock = new Object();
        this.ongoingBackupMap = new HashMap<>();
        this.ongoingBackupTimeoutMap = new HashMap<>();

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
            e.printStackTrace();
        }
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (BackupManagerSession backupManagerSession = getSession()) {
            Backup.createTable(backupManagerSession.session);
            BackupKey.createTable(backupManagerSession.session);
            backupManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create backup databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        Backup.upgradeTable(session, oldVersion, newVersion);
        BackupKey.upgradeTable(session, oldVersion, newVersion);
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void setDelegate(NotificationListeningDelegate notificationListeningDelegate) {
        this.notificationListeningDelegate = notificationListeningDelegate;
        notificationListeningDelegate.addListener(IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED, this);
    }

    public BackupManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in BackupManager.");
        }
        return new BackupManagerSession(createSessionDelegate.getSession(), notificationPostingDelegate, jsonObjectMapper);
    }

    private BackupManagerSession wrapSession(Session session) {
        return new BackupManagerSession(session, notificationPostingDelegate, jsonObjectMapper);
    }

    public ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

    // region implement BackupDelegate

    @Override
    public void generateNewBackupKey() {
        try {
            BackupSeed backupSeed = BackupSeed.generate(prng);
            if (backupSeed == null) {
                throw new Exception("Failed to generate BackupSeed");
            }
            BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();
            if (derivedKeys == null) {
                throw new Exception("Failed to derive keys from BackupSeed");
            }
            try (BackupManagerSession backupManagerSession = getSession()) {
                backupManagerSession.session.startTransaction();
                BackupKey.deleteAll(backupManagerSession);
                BackupKey.create(backupManagerSession, derivedKeys.backupKeyUid, derivedKeys.encryptionKeyPair.getPublicKey(), derivedKeys.macKey);
                backupManagerSession.session.commit();

                // if autobackup is active --> immediately backup
                if (autoBackupEnabled) {
                    initiateBackup(false);
                }

                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED_SEED_KEY, backupSeed.toString());
                notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED, userInfo);
            } catch (SQLException e) {
                e.printStackTrace();
                throw new Exception("Failed to save new BackupKey to database");
            }
        } catch (Exception e) {
            e.printStackTrace();
            notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED, new HashMap<>());
        }
    }

    @Override
    public int verifyBackupKey(String seedString) {
        try (BackupManagerSession backupManagerSession = getSession()) {
            BackupKey[] backupKeys = BackupKey.getAll(backupManagerSession);
            if (backupKeys.length == 0) {
                throw new Exception("No BackupKey generated!");
            } else if (backupKeys.length > 1) {
                throw new Exception("Multiple BackupKey generated, this should never occur!");
            }
            BackupKey backupKey = backupKeys[0];

            BackupSeed backupSeed = new BackupSeed(seedString);
            BackupSeed.DerivedKeys derivedKeys = backupSeed.deriveKeys();

            if (derivedKeys.backupKeyUid.equals(backupKey.getUid()) &&
                    derivedKeys.macKey.equals(backupKey.getMacKey()) &&
                    derivedKeys.encryptionKeyPair.getPublicKey().equals(backupKey.getEncryptionPublicKey())) {
                // we have the same keys, everything is fine
                backupKey.addSuccessfulVerification();

                HashMap<String, Object> userInfo = new HashMap<>();
                notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL, userInfo);
                return BACKUP_SEED_VERIFICATION_STATUS_SUCCESS;
            }
            return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
        } catch (BackupSeed.SeedTooShortException e) {
            return BACKUP_SEED_VERIFICATION_STATUS_TOO_SHORT;
        } catch (BackupSeed.SeedTooLongException e) {
            return BACKUP_SEED_VERIFICATION_STATUS_TOO_LONG;
        } catch (Exception e) {
            return BACKUP_SEED_VERIFICATION_STATUS_BAD_KEY;
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
            e.printStackTrace();
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
                e.printStackTrace();
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
                        HashMap<String, Object> userInfo = new HashMap<>();
                        notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED, userInfo);
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

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DeflaterOutputStream deflater = new DeflaterOutputStream(baos, new Deflater(5, true));
                    deflater.write(fullBackupContent.getBytes(StandardCharsets.UTF_8));
                    deflater.close();
                    byte[] compressedBackup = baos.toByteArray();
                    baos.close();

                    EncryptedBytes encryptedBackup = Suite.getPublicKeyEncryption(encryptionPublicKey).encrypt(encryptionPublicKey, compressedBackup, prng);
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
                e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
        private final byte[] decryptedBackupContent;
        private final BackupSeed.DerivedKeys derivedKeys;

        public BackupContentAndDerivedKeys(byte[] decryptedBackupContent, BackupSeed.DerivedKeys derivedKeys) {
            this.decryptedBackupContent = decryptedBackupContent;
            this.derivedKeys = derivedKeys;
        }
    }

    private BackupContentAndDerivedKeys decryptBackupContent(String seedString, byte[] backupContent) throws Exception {
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

        try (ByteArrayInputStream bais = new ByteArrayInputStream(plaintext);
             InflaterInputStream inflater = new InflaterInputStream(bais, new Inflater(true));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int c;
            while ((c = inflater.read(buffer)) != -1) {
                baos.write(buffer, 0, c);
            }
            return new BackupContentAndDerivedKeys(baos.toByteArray(), derivedKeys);
        }
    }

    @Override
    public ObvIdentity[] restoreOwnedIdentitiesFromBackup(String seedString, byte[] backupContent) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent);
            if (backupContentAndDerivedKeys == null) {
                return null;
            }

            Pojo_0 pojo = jsonObjectMapper.readValue(backupContentAndDerivedKeys.decryptedBackupContent, Pojo_0.class);
            if (pojo.backup_json_version != Constants.CURRENT_BACKUP_JSON_VERSION) {
                // do an upgrade when needed
                Logger.e("Restoring ownedIdentity with a different backup JSON version:" + pojo.backup_json_version + " (expecting " + Constants.CURRENT_BACKUP_JSON_VERSION + ").");
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
            return identityDelegate.restoreOwnedIdentitiesFromBackup(pojo.engine.identity_manager, prng);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void restoreContactsAndGroupsFromBackup(String seedString, byte[] backupContent, Identity[] restoredOwnedIdentities) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent);
            if (backupContentAndDerivedKeys == null) {
                return;
            }

            Pojo_0 pojo = jsonObjectMapper.readValue(backupContentAndDerivedKeys.decryptedBackupContent, Pojo_0.class);
            if (pojo.backup_json_version != Constants.CURRENT_BACKUP_JSON_VERSION) {
                // do an upgrade when needed
                Logger.e("Restoring contacts and groups with a different backup JSON version:" + pojo.backup_json_version + " (expecting " + Constants.CURRENT_BACKUP_JSON_VERSION + ").");
                return;
            }

            identityDelegate.restoreContactsAndGroupsFromBackup(pojo.engine.identity_manager, restoredOwnedIdentities, pojo.backup_timestamp);

            notificationPostingDelegate.postNotification(BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED, new HashMap<>());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String decryptAppDataBackup(String seedString, byte[] backupContent) {
        try {
            BackupContentAndDerivedKeys backupContentAndDerivedKeys = decryptBackupContent(seedString, backupContent);
            if (backupContentAndDerivedKeys == null) {
                return null;
            }

            Pojo_0 pojo = jsonObjectMapper.readValue(backupContentAndDerivedKeys.decryptedBackupContent, Pojo_0.class);
            if (pojo != null) {
                return pojo.app;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // endregion

    // region NotificationListener

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case IdentityNotifications.NOTIFICATION_DATABASE_CONTENT_CHANGED: {
                if (autoBackupEnabled) {
                    scheduleBackupForUploadIfNeeded(false);
                }
                break;
            }
        }
    }

    private void scheduleBackupForUploadIfNeeded(boolean immediately) {
        synchronized (autoBackupSchedulerLock) {
            if (immediately) {
                if (autoBackupIsScheduled) {
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
            if (!(obj instanceof UidAndVersion)) {
                return false;
            }
            UidAndVersion o = (UidAndVersion) obj;
            return Objects.equals(uid, o.uid) && version == o.version;
        }
    }
}
