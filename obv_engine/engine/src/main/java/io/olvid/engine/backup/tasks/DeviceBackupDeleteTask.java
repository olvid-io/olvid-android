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


import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.databases.DeviceBackupSeed;
import io.olvid.engine.backup.databases.ProfileBackupThreadId;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.backup.datatypes.BackupManagerSessionFactory;
import io.olvid.engine.backup.datatypes.BackupTaskStatus;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.containers.BackupsV2ListItem;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;

public class DeviceBackupDeleteTask {
    private final BackupManagerSessionFactory backupManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final BackupSeed deviceBackupSeed;

    public DeviceBackupDeleteTask(BackupManagerSessionFactory backupManagerSessionFactory, SSLSocketFactory sslSocketFactory, BackupSeed deviceBackupSeed) {
        this.backupManagerSessionFactory = backupManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.deviceBackupSeed = deviceBackupSeed;
    }

    public BackupTaskStatus execute() {
        try (BackupManagerSession backupManagerSession = backupManagerSessionFactory.getSession()) {
            DeviceBackupSeed deviceBackupSeed = DeviceBackupSeed.get(backupManagerSession, this.deviceBackupSeed);
            if (deviceBackupSeed == null) {
                // seed was already deleted, everything is fine
                return BackupTaskStatus.PERMANENT_FAILURE;
            }

            String deviceServer = deviceBackupSeed.getServer();
            BackupSeed.DerivedKeysV2 deviceDerivedKeysV2 = deviceBackupSeed.getBackupSeed().deriveKeysV2();

            ///////
            // 1. list device backups
            StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2ListBackupsQuery(deviceServer, deviceDerivedKeysV2.backupKeyUid)), sslSocketFactory);
            OperationQueue queue = new OperationQueue();
            queue.queue(standaloneServerQueryOperation);
            queue.execute(1, "Engine-DeviceBackupDeleteTask");
            queue.join();

            Long deviceVersion = null;

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
                        deviceVersion = backupsV2ListItem.version;
                        break;
                    }
                }
            } else {
                Integer rfc = standaloneServerQueryOperation.getReasonForCancel();
                if (rfc == null || rfc != StandaloneServerQueryOperation.RFC_UNKNOWN_BACKUP_UID) {
                    // can be: general error, server parsing error
                    return BackupTaskStatus.RETRIABLE_FAILURE;
                }
            }

            //////
            // 2. delete the device backup if one was found
            if (deviceVersion != null) {
                // compute the signature
                byte[] signaturePayload = Encoded.of(new Encoded[]{
                        Encoded.of(deviceDerivedKeysV2.backupKeyUid),
                        Encoded.of(Constants.DEVICE_BACKUP_THREAD_ID),
                        Encoded.of(deviceVersion),
                }).getBytes();
                byte[] signature = Signature.sign(
                        Constants.SignatureContext.BACKUP_DELETE,
                        signaturePayload,
                        deviceDerivedKeysV2.authenticationKeyPair.getPrivateKey().getSignaturePrivateKey(),
                        backupManagerSession.prng
                );

                standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2DeleteBackupQuery(deviceServer, deviceDerivedKeysV2.backupKeyUid, Constants.DEVICE_BACKUP_THREAD_ID, deviceVersion, signature)), sslSocketFactory);
                queue = new OperationQueue();
                queue.queue(standaloneServerQueryOperation);
                queue.execute(1, "Engine-DeviceBackupDeleteTask");
                queue.join();

                if (!standaloneServerQueryOperation.isFinished()) {
                    // can be: general error, server parsing error, unknown backup uid, unknown threadId, unknown version, invalid signature
                    return BackupTaskStatus.RETRIABLE_FAILURE;
                }
            }


            ////////
            // 3. delete all profile backups (if there is no active backup key)
            if (DeviceBackupSeed.getActive(backupManagerSession) == null) {
                for (ProfileBackupThreadId profileBackupThreadId : ProfileBackupThreadId.getAll(backupManagerSession)) {
                    String server = profileBackupThreadId.getOwnedIdentity().getServer();
                    BackupSeed backupSeed = backupManagerSession.identityDelegate.getOwnedIdentityBackupSeed(backupManagerSession.session, profileBackupThreadId.getOwnedIdentity());
                    if (backupSeed == null) {
                        // profile has no backup seed, nothing to delete
                        continue;
                    }
                    BackupSeed.DerivedKeysV2 derivedKeysV2 = backupSeed.deriveKeysV2();


                    ////////
                    // 3.1. list profile backups
                    standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2ListBackupsQuery(server, derivedKeysV2.backupKeyUid)), sslSocketFactory);
                    queue = new OperationQueue();
                    queue.queue(standaloneServerQueryOperation);
                    queue.execute(1, "Engine-DeviceBackupDeleteTask");
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
                            if (Objects.equals(backupsV2ListItem.threadId, profileBackupThreadId.getThreadId())) {
                                version = backupsV2ListItem.version;
                                break;
                            }
                        }
                    } else {
                        Integer rfc = standaloneServerQueryOperation.getReasonForCancel();
                        if (rfc == null || rfc != StandaloneServerQueryOperation.RFC_UNKNOWN_BACKUP_UID) {
                            // can be: general error, server parsing error
                            return BackupTaskStatus.RETRIABLE_FAILURE;
                        }
                    }

                    //////
                    // 3.2. delete the profile backup if one was found
                    if (version != null) {
                        byte[] signaturePayload = Encoded.of(new Encoded[]{
                                Encoded.of(derivedKeysV2.backupKeyUid),
                                Encoded.of(profileBackupThreadId.getThreadId()),
                                Encoded.of(version),
                        }).getBytes();
                        byte[] signature = Signature.sign(
                                Constants.SignatureContext.BACKUP_DELETE,
                                signaturePayload,
                                derivedKeysV2.authenticationKeyPair.getPrivateKey().getSignaturePrivateKey(),
                                backupManagerSession.prng
                        );

                        standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2DeleteBackupQuery(server, derivedKeysV2.backupKeyUid, profileBackupThreadId.getThreadId(), version, signature)), sslSocketFactory);
                        queue = new OperationQueue();
                        queue.queue(standaloneServerQueryOperation);
                        queue.execute(1, "Engine-DeviceBackupDeleteTask");
                        queue.join();

                        if (!standaloneServerQueryOperation.isFinished()) {
                            // can be: general error, server parsing error, unknown backup uid, unknown threadId, unknown version, invalid signature
                            return BackupTaskStatus.RETRIABLE_FAILURE;
                        }
                    }
                }
            }

            return BackupTaskStatus.SUCCESS;
        } catch (Exception e) {
            Logger.x(e);
            return BackupTaskStatus.RETRIABLE_FAILURE;
        }
    }
}
