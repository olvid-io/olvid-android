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


import java.security.InvalidKeyException;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.databases.DeviceBackupSeed;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.backup.datatypes.BackupManagerSessionFactory;
import io.olvid.engine.backup.datatypes.BackupTaskStatus;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.containers.BackupsV2ListItem;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvDeviceBackupSnapshot;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;

public class DeviceBackupUploadTask {
    private final BackupManagerSessionFactory backupManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;

    public DeviceBackupUploadTask(BackupManagerSessionFactory backupManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.backupManagerSessionFactory = backupManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
    }

    public BackupTaskStatus execute() {
        try (BackupManagerSession backupManagerSession = backupManagerSessionFactory.getSession()) {
            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.getActive(backupManagerSession);
            if (deviceBackupSeed == null) {
                return BackupTaskStatus.PERMANENT_FAILURE;
            }

            ////////
            // 0. Check we have at least one owned identity
            if (backupManagerSession.identityDelegate.getOwnedIdentities(backupManagerSession.session).length == 0) {
                return BackupTaskStatus.PERMANENT_FAILURE;
            }


            String server = deviceBackupSeed.getServer();
            BackupSeed.DerivedKeysV2 derivedKeysV2 = deviceBackupSeed.getBackupSeed().deriveKeysV2();


            ////////
            // 1. list existing backups
            StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2ListBackupsQuery(server, derivedKeysV2.backupKeyUid)), sslSocketFactory);
            OperationQueue queue = new OperationQueue();
            queue.queue(standaloneServerQueryOperation);
            queue.execute(1, "Engine-DeviceBackupUploadTask");
            queue.join();

            Long version = null;

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
                        version = backupsV2ListItem.version;
                        break;
                    }
                }
                if (version == null) {
                    // the backup slot exists, but nothing was ever uploaded
                    version = System.currentTimeMillis();
                }
            } else {
                Integer rfc = standaloneServerQueryOperation.getReasonForCancel();
                if (rfc == null || rfc != StandaloneServerQueryOperation.RFC_UNKNOWN_BACKUP_UID) {
                    // can be: general error, server parsing error
                    return BackupTaskStatus.RETRIABLE_FAILURE;
                }
            }

            ////////
            // 2. if backup UID does not exist yet, create one
            if (version == null) {
                standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2CreateBackupQuery(server, derivedKeysV2.backupKeyUid, derivedKeysV2.authenticationKeyPair.getPublicKey())), sslSocketFactory);
                queue = new OperationQueue();
                queue.queue(standaloneServerQueryOperation);
                queue.execute(1, "Engine-DeviceBackupUploadTask");
                queue.join();

                if (standaloneServerQueryOperation.isFinished()) {
                    // success!
                    version = System.currentTimeMillis();
                } else {
                    // can be: general error, server parsing error, or backup uid already exists
                    return BackupTaskStatus.RETRIABLE_FAILURE;
                }
            }

            /////////
            // 3. increment version number and upload a new device backup
            version++;

            // 3.1 create the snapshot
            ObvDeviceBackupSnapshot deviceBackupSnapshot;
            try {
                backupManagerSession.session.startTransaction();
                ObvBackupAndSyncDelegate identityBackupAndSyncDelegate = backupManagerSession.identityDelegate.getSyncDelegateWithinTransaction(backupManagerSession.session);
                deviceBackupSnapshot = ObvDeviceBackupSnapshot.get(identityBackupAndSyncDelegate, backupManagerSession.appBackupAndSyncDelegate);
            } catch (Exception e) {
                Logger.x(e);
                return BackupTaskStatus.RETRIABLE_FAILURE;
            } finally {
                backupManagerSession.session.rollback();
            }

            // 3.2 encode and encrypt and compute signature
            EncryptedBytes encryptedBackup;
            byte[] signature;
            {
                // encode
                Encoded plaintextContent = Encoded.of(deviceBackupSnapshot.toEncodedDictionary(backupManagerSession.identityDelegate.getSyncDelegate(), backupManagerSession.appBackupAndSyncDelegate));

                // add a padding to obfuscate content length
                byte[] paddedPlaintext = new byte[((plaintextContent.getBytes().length - 1) | 511) + 1];
                System.arraycopy(plaintextContent.getBytes(), 0, paddedPlaintext, 0, plaintextContent.getBytes().length);

                // encrypt
                try {
                    AuthEnc authEnc = Suite.getAuthEnc(derivedKeysV2.encryptionKey);
                    encryptedBackup = authEnc.encrypt(derivedKeysV2.encryptionKey, paddedPlaintext, backupManagerSession.prng);
                } catch (InvalidKeyException e) {
                    // this never happens, but if the backup key does not work, retrying is useless!
                    Logger.x(e);
                    return BackupTaskStatus.PERMANENT_FAILURE;
                }

                // compute the signature
                byte[] signaturePayload = Encoded.of(new Encoded[]{
                        Encoded.of(derivedKeysV2.backupKeyUid),
                        Encoded.of(Constants.DEVICE_BACKUP_THREAD_ID),
                        Encoded.of(version),
                        Encoded.of(encryptedBackup),
                }).getBytes();
                signature = Signature.sign(
                        Constants.SignatureContext.BACKUP_UPLOAD,
                        signaturePayload,
                        derivedKeysV2.authenticationKeyPair.getPrivateKey().getSignaturePrivateKey(),
                        backupManagerSession.prng
                );
            }

            // 3.3 upload the snapshot to the server
            standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2UploadBackupQuery(server, derivedKeysV2.backupKeyUid, Constants.DEVICE_BACKUP_THREAD_ID, version, encryptedBackup, signature)), sslSocketFactory);
            queue = new OperationQueue();
            queue.queue(standaloneServerQueryOperation);
            queue.execute(1, "Engine-DeviceBackupUploadTask");
            queue.join();

            if (!standaloneServerQueryOperation.isFinished()) {
                // can be: general error, server parsing error, unknown backup uid, version too small, invalid signature
                return BackupTaskStatus.RETRIABLE_FAILURE;
            }

            return BackupTaskStatus.SUCCESS;
        } catch (Exception e) {
            Logger.x(e);
            return BackupTaskStatus.RETRIABLE_FAILURE;
        }
    }
}
