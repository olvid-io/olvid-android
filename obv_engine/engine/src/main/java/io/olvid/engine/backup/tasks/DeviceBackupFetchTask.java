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

package io.olvid.engine.backup.tasks;


import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.backup.datatypes.BackupManagerSessionFactory;
import io.olvid.engine.backup.datatypes.BackupTaskStatus;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.containers.BackupsV2ListItem;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvDeviceBackupForRestore;
import io.olvid.engine.engine.types.sync.ObvDeviceBackupSnapshot;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;

public class DeviceBackupFetchTask {
    private final String server;
    private final BackupSeed deviceBackupSeed;
    private final BackupManagerSessionFactory backupManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private ObvDeviceBackupForRestore obvDeviceBackupForRestore;

    public DeviceBackupFetchTask(String server, BackupSeed deviceBackupSeed, BackupManagerSessionFactory backupManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.server = server;
        this.deviceBackupSeed = deviceBackupSeed;
        this.backupManagerSessionFactory = backupManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
    }

    public BackupTaskStatus execute() {
        BackupSeed.DerivedKeysV2 derivedKeysV2 = deviceBackupSeed.deriveKeysV2();

        ///////
        // 1. list device backups
        StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2ListBackupsQuery(server, derivedKeysV2.backupKeyUid)), sslSocketFactory);
        OperationQueue queue = new OperationQueue();
        queue.queue(standaloneServerQueryOperation);
        queue.execute(1, "Engine-DeviceBackupFetchTask");
        queue.join();

        String deviceBackupDownloadUrl = null;

        if (standaloneServerQueryOperation.isFinished()) {
            List<BackupsV2ListItem> backupsV2ListItems;
            try {
                backupsV2ListItems = BackupsV2ListItem.manyOf(standaloneServerQueryOperation.getServerResponse().decodeList());
            } catch (Exception e) {
                Logger.x(e);
                return BackupTaskStatus.RETRIABLE_FAILURE;
            }

            for (BackupsV2ListItem backupsV2ListItem : backupsV2ListItems) {
                if (Objects.equals(backupsV2ListItem.threadId, Constants.DEVICE_BACKUP_THREAD_ID)) {
                    deviceBackupDownloadUrl = backupsV2ListItem.downloadUrl;
                    break;
                }
            }
        } else {
            Integer rfc = standaloneServerQueryOperation.getReasonForCancel();
            if (rfc == null) {
                return BackupTaskStatus.RETRIABLE_FAILURE;
            } else if (rfc == StandaloneServerQueryOperation.RFC_NETWORK_ERROR) {
                obvDeviceBackupForRestore = new ObvDeviceBackupForRestore(ObvDeviceBackupForRestore.Status.NETWORK_ERROR, null, null);
                return BackupTaskStatus.RETRIABLE_FAILURE;
            } else if (rfc != StandaloneServerQueryOperation.RFC_UNKNOWN_BACKUP_UID) {
                // can be: general error, server parsing error
                return BackupTaskStatus.RETRIABLE_FAILURE;
            }
        }

        ///////
        // 2. fail if no device backup was found
        if (deviceBackupDownloadUrl == null) {
            return BackupTaskStatus.PERMANENT_FAILURE;
        }

        //////
        // 3. download the backup file
        DownloadBackupFromServer serverMethod = new DownloadBackupFromServer(deviceBackupDownloadUrl);
        if (!serverMethod.execute()) {
            obvDeviceBackupForRestore = new ObvDeviceBackupForRestore(ObvDeviceBackupForRestore.Status.NETWORK_ERROR, null, null);
            return BackupTaskStatus.RETRIABLE_FAILURE;
        }

        try (BackupManagerSession backupManagerSession = backupManagerSessionFactory.getSession()) {
            ////////
            // 4. decrypt the encrypted device backup
            ObvDeviceBackupSnapshot obvDeviceBackupSnapshot;
            try {
                EncryptedBytes encryptedBackup = new EncryptedBytes(serverMethod.getEncryptedBackup());

                // decrypt
                AuthEnc authEnc = Suite.getAuthEnc(derivedKeysV2.encryptionKey);
                byte[] paddedPlaintext = authEnc.decrypt(derivedKeysV2.encryptionKey, encryptedBackup);

                // decode
                HashMap<DictionaryKey, Encoded> encodedDictionary = new Encoded(paddedPlaintext).decodeDictionaryWithPadding();

                obvDeviceBackupSnapshot = ObvDeviceBackupSnapshot.fromEncodedDictionary(encodedDictionary, backupManagerSession.identityDelegate.getSyncDelegate(), backupManagerSession.appBackupAndSyncDelegate);
            } catch (Exception e) {
                // if the backup cannot be decrypted or decoded, no need to retry
                Logger.x(e);
                return BackupTaskStatus.PERMANENT_FAILURE;
            }
            if (obvDeviceBackupSnapshot == null) {
                return BackupTaskStatus.PERMANENT_FAILURE;
            }


            //////////
            // 5. convert the ObvDeviceBackupSnapshot to an ObvDeviceBackupForRestore
            List<ObvDeviceBackupForRestore.ObvDeviceBackupProfile> profiles = backupManagerSession.identityDelegate.getDeviceBackupProfileListFromDeviceBackup(
                    backupManagerSession.session,
                    obvDeviceBackupSnapshot.getSnapshotNode(backupManagerSession.identityDelegate.getSyncDelegate().getTag())
            );

            obvDeviceBackupForRestore = new ObvDeviceBackupForRestore(
                    ObvDeviceBackupForRestore.Status.SUCCESS,
                    profiles,
                    obvDeviceBackupSnapshot.getSnapshotNode(backupManagerSession.appBackupAndSyncDelegate.getTag())
            );
        } catch (Exception e) {
            Logger.x(e);
            return BackupTaskStatus.PERMANENT_FAILURE;
        }
        return BackupTaskStatus.SUCCESS;
    }

    public ObvDeviceBackupForRestore getObvDeviceBackupForRestore() {
        return obvDeviceBackupForRestore;
    }
}
