/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.sync.AppSyncSnapshot;

public class AppBackupAndSyncDelegate implements ObvBackupAndSyncDelegate {
    @Override
    public String getTag() {
        return "app";
    }

    @Override
    public ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity) {
        return AppSyncSnapshot.of(AppDatabase.getInstance(), ownedIdentity.getBytes());
    }

    @Override
    public byte[] serialize(ObvSyncSnapshotNode snapshotNode) throws Exception {
        if (!(snapshotNode instanceof AppSyncSnapshot)) {
            throw new Exception("AppBackupDelegate can only serialize AppSyncSnapshot");
        }
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(snapshotNode);
    }

    @Override
    public ObvSyncSnapshotNode deserialize(byte[] serializedSnapshotNode) throws Exception {
        return AppSingleton.getJsonObjectMapper().readValue(serializedSnapshotNode, AppSyncSnapshot.class);
    }
}
