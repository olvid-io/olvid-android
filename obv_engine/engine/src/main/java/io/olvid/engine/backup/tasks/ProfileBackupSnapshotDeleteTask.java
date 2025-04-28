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


import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.backup.datatypes.BackupTaskStatus;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.datatypes.BackupSeed;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.operations.StandaloneServerQueryOperation;

public class ProfileBackupSnapshotDeleteTask {
    private final String server;
    private final BackupSeed profileBackupSeed;
    private final UID backupThreadId;
    private final long version;
    private final PRNGService prng;
    private final SSLSocketFactory sslSocketFactory;

    public ProfileBackupSnapshotDeleteTask(String server, BackupSeed profileBackupSeed, UID backupThreadId, long version, PRNGService prng, SSLSocketFactory sslSocketFactory) {
        this.server = server;
        this.profileBackupSeed = profileBackupSeed;
        this.backupThreadId = backupThreadId;
        this.version = version;
        this.prng = prng;
        this.sslSocketFactory = sslSocketFactory;
    }

    public BackupTaskStatus execute() {
        BackupSeed.DerivedKeysV2 derivedKeysV2 = profileBackupSeed.deriveKeysV2();

        byte[] signaturePayload = Encoded.of(new Encoded[]{
                Encoded.of(derivedKeysV2.backupKeyUid),
                Encoded.of(backupThreadId),
                Encoded.of(version),
        }).getBytes();
        byte[] signature = Signature.sign(
                Constants.SignatureContext.BACKUP_DELETE,
                signaturePayload,
                derivedKeysV2.authenticationKeyPair.getPrivateKey().getSignaturePrivateKey(),
                prng
        );

        StandaloneServerQueryOperation standaloneServerQueryOperation = new StandaloneServerQueryOperation(new ServerQuery(null, null, new ServerQuery.BackupsV2DeleteBackupQuery(server, derivedKeysV2.backupKeyUid, backupThreadId, version, signature)), sslSocketFactory);
        OperationQueue queue = new OperationQueue();
        queue.queue(standaloneServerQueryOperation);
        queue.execute(1, "Engine-ProfileBackupSnapshotDeleteTask");
        queue.join();

        if (!standaloneServerQueryOperation.isFinished()) {
            // can be: general error, server parsing error, unknown backup uid, unknown threadId, unknown version, invalid signature
            return BackupTaskStatus.PERMANENT_FAILURE;
        }

        return BackupTaskStatus.SUCCESS;
    }
}

