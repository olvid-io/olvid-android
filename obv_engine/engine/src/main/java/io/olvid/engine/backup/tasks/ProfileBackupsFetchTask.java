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


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.backup.databases.ProfileBackupThreadId;
import io.olvid.engine.backup.datatypes.BackupManagerSession;
import io.olvid.engine.backup.datatypes.BackupManagerSessionFactory;
import io.olvid.engine.backup.datatypes.BackupTaskStatus;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.BackupsV2ListItem;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvDeviceList;
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvProfileBackupSnapshot;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.sync.IdentityManagerSyncSnapshot;
import io.olvid.engine.identity.databases.sync.OwnedIdentitySyncSnapshot;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;

public class ProfileBackupsFetchTask {
    private final String server;
    private final BackupSeed profileBackupSeed;
    private final BackupManagerSessionFactory backupManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private ObvProfileBackupsForRestore obvProfileBackupsForRestore;

    public ProfileBackupsFetchTask(String server, BackupSeed profileBackupSeed, BackupManagerSessionFactory backupManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.server = server;
        this.profileBackupSeed = profileBackupSeed;
        this.backupManagerSessionFactory = backupManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
    }

    public BackupTaskStatus execute() {
        BackupSeed.DerivedKeysV2 derivedKeysV2 = profileBackupSeed.deriveKeysV2();

        ///////
        // 1. list profile backups
        StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2ListBackupsQuery(server, derivedKeysV2.backupKeyUid)), sslSocketFactory);
        OperationQueue queue = new OperationQueue();
        queue.queue(standaloneServerQueryOperation);
        queue.execute(1, "Engine-ProfileBackupsFetchTask");
        queue.join();

        List<BackupsV2ListItem> profilesToDownload = new ArrayList<>();

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
                    continue;
                }
                profilesToDownload.add(backupsV2ListItem);
            }
        } else {
            Integer rfc = standaloneServerQueryOperation.getReasonForCancel();
            if (rfc == null) {
                return BackupTaskStatus.RETRIABLE_FAILURE;
            } else if (rfc == StandaloneServerQueryOperation.RFC_NETWORK_ERROR) {
                obvProfileBackupsForRestore = new ObvProfileBackupsForRestore(ObvProfileBackupsForRestore.Status.NETWORK_ERROR, null, null);
                return BackupTaskStatus.RETRIABLE_FAILURE;
            } else if (rfc != StandaloneServerQueryOperation.RFC_UNKNOWN_BACKUP_UID) {
                // can be: general error, server parsing error
                return BackupTaskStatus.RETRIABLE_FAILURE;
            }
        }


        final IdentityDelegate identityDelegate;
        final ObvBackupAndSyncDelegate appBackupAndSyncDelegates;
        final HashMap<Identity, UID> thisDeviceThreadIds = new HashMap<>();

        try (BackupManagerSession backupManagerSession = backupManagerSessionFactory.getSession()) {
            identityDelegate = backupManagerSession.identityDelegate;
            appBackupAndSyncDelegates = backupManagerSession.appBackupAndSyncDelegate;

            for (ProfileBackupThreadId profileBackupThreadId : ProfileBackupThreadId.getAll(backupManagerSession)) {
                thisDeviceThreadIds.put(profileBackupThreadId.getOwnedIdentity(), profileBackupThreadId.getThreadId());
            }
        } catch (Exception e) {
            Logger.x(e);
            return BackupTaskStatus.RETRIABLE_FAILURE;
        }


        //////
        // 3. download and decrypt the backup files
        List<ObvProfileBackupsForRestore.ObvProfileBackupForRestore> profileBackupsForRestore = new ArrayList<>();
        boolean truncated = false;
        OwnedIdentitySyncSnapshot.PrivateIdentity privateIdentity = null;
        byte[] bytesIdentity = null;

        for (BackupsV2ListItem profileToDownload : profilesToDownload) {
            ////////
            // 3.1. download
            DownloadBackupFromServer serverMethod = new DownloadBackupFromServer(profileToDownload.downloadUrl);
            if (!serverMethod.execute()) {
                truncated = true;
                continue;
            }

            ////////
            // 3.2. decrypt the encrypted profile backup
            ObvProfileBackupSnapshot obvProfileBackupSnapshot;
            try {
                EncryptedBytes encryptedBackup = new EncryptedBytes(serverMethod.getEncryptedBackup());

                // decrypt
                AuthEnc authEnc = Suite.getAuthEnc(derivedKeysV2.encryptionKey);
                byte[] paddedPlaintext = authEnc.decrypt(derivedKeysV2.encryptionKey, encryptedBackup);

                // decode
                HashMap<DictionaryKey, Encoded> encodedDictionary = new Encoded(paddedPlaintext).decodeDictionaryWithPadding();

                obvProfileBackupSnapshot = ObvProfileBackupSnapshot.fromEncodedDictionary(encodedDictionary, identityDelegate.getSyncDelegate(), appBackupAndSyncDelegates);
            } catch (Exception e) {
                // if the backup cannot be decrypted or decoded, no need to retry
                Logger.x(e);
                truncated = true;
                continue;
            }
            if (obvProfileBackupSnapshot == null) {
                truncated = true;
                continue;
            }

            //////////
            // 3.3. convert the ObvProfileBackupSnapshot to an ObvProfileBackupForRestore
            ObvProfileBackupsForRestore.ObvProfileBackupForRestore obvProfileBackupForRestore = new ObvProfileBackupsForRestore.ObvProfileBackupForRestore();
            obvProfileBackupForRestore.bytesBackupThreadId = profileToDownload.threadId.getBytes();
            obvProfileBackupForRestore.version = profileToDownload.version;
            obvProfileBackupForRestore.timestamp = obvProfileBackupSnapshot.getTimestamp();
            obvProfileBackupForRestore.additionalInfo = obvProfileBackupSnapshot.getAdditionalInfo();
            obvProfileBackupForRestore.snapshot = obvProfileBackupSnapshot.getSnapshot();

            ObvSyncSnapshotNode obvSyncSnapshotNode = obvProfileBackupForRestore.snapshot.getSnapshotNode(identityDelegate.getSyncDelegate().getTag());
            if (obvSyncSnapshotNode instanceof IdentityManagerSyncSnapshot) {
                OwnedIdentitySyncSnapshot ownedIdentityNode = ((IdentityManagerSyncSnapshot) obvSyncSnapshotNode).owned_identity_node;

                if (privateIdentity == null) {
                    privateIdentity = ownedIdentityNode.private_identity;
                    bytesIdentity = ((IdentityManagerSyncSnapshot) obvSyncSnapshotNode).owned_identity;
                }

                obvProfileBackupForRestore.contactCount = ownedIdentityNode.contacts.size();
                obvProfileBackupForRestore.groupCount = ownedIdentityNode.groups.size() + ownedIdentityNode.groups2.size();

                try {
                    obvProfileBackupForRestore.fromThisDevice = Objects.equals(profileToDownload.threadId, thisDeviceThreadIds.get(Identity.of(((IdentityManagerSyncSnapshot) obvSyncSnapshotNode).owned_identity)));
                } catch (Exception e) {
                    Logger.x(e);
                    obvProfileBackupForRestore.fromThisDevice = false;
                }
                if (ownedIdentityNode.keycloak != null) {
                    if (ownedIdentityNode.keycloak.transfer_restricted) {
                        obvProfileBackupForRestore.keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.TRANSFER_RESTRICTED;
                    } else {
                        obvProfileBackupForRestore.keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.MANAGED;
                    }
                    obvProfileBackupForRestore.keycloakServerUrl = ownedIdentityNode.keycloak.server_url;
                    obvProfileBackupForRestore.keycloakClientId = ownedIdentityNode.keycloak.client_id;
                    obvProfileBackupForRestore.keycloakClientSecret = ownedIdentityNode.keycloak.client_secret;
                } else {
                    obvProfileBackupForRestore.keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.UNMANAGED;
                    obvProfileBackupForRestore.keycloakServerUrl = null;
                    obvProfileBackupForRestore.keycloakClientId = null;
                    obvProfileBackupForRestore.keycloakClientSecret = null;
                }
            } else {
                truncated = true;
                continue;
            }

            profileBackupsForRestore.add(obvProfileBackupForRestore);
        }

        profileBackupsForRestore.sort(Comparator.comparingLong(o -> -o.timestamp));

        ObvDeviceList deviceList = null;
        if (privateIdentity != null) {
            try {
                Identity identity = Identity.of(bytesIdentity);
                EncryptionPrivateKey privateKey = (EncryptionPrivateKey) new Encoded(privateIdentity.encryption_private_key).decodePrivateKey();

                standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, identity, new ServerQuery.OwnedDeviceDiscoveryQuery(identity)), sslSocketFactory);

                queue = new OperationQueue();
                queue.queue(standaloneServerQueryOperation);
                queue.execute(1, "Engine-ProfileBackupsFetchTask");
                queue.join();

                if (standaloneServerQueryOperation.isFinished() && standaloneServerQueryOperation.getServerResponse() != null) {
                    deviceList = ObvDeviceList.of(
                            standaloneServerQueryOperation.getServerResponse().decodeEncryptedData(),
                            privateKey
                    );
                }
            } catch (Exception e) {
                Logger.x(e);
            }
        }

        obvProfileBackupsForRestore = new ObvProfileBackupsForRestore(
                truncated ? ObvProfileBackupsForRestore.Status.TRUNCATED : ObvProfileBackupsForRestore.Status.SUCCESS,
                profileBackupsForRestore,
                deviceList
        );

        return BackupTaskStatus.SUCCESS;
    }


    public ObvProfileBackupsForRestore getObvProfileBackupsForRestore() {
        return obvProfileBackupsForRestore;
    }
}
