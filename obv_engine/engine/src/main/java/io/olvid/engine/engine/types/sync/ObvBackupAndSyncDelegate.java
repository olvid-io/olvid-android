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

package io.olvid.engine.engine.types.sync;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.identities.ObvIdentity;

public interface ObvBackupAndSyncDelegate {
    //////
    // Return a tag corresponding to this delegate
    String getTag();

    //////
    // This method computes a snapshot of the data to sync
    ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity); // TODO: we probably need to add a context as we do not want to include the same elements for a backup or a sync

    //////
    // This method allows each delegate to crate an owned identity base on the ObvIdentity the engine has restored
    RestoreFinishedCallback restoreOwnedIdentity(ObvIdentity ownedIdentity, ObvSyncSnapshotNode node) throws Exception;
    //////
    // This method restores a Snapshot, assuming the owned identity already exists in db.
    // - it may return a callback that will only get called if the restore was successful for all delegates.
    // - this callback can be used to commit a transaction on app side, only if the engine restore is successful, and roll it back otherwise
    RestoreFinishedCallback restoreSyncSnapshot(ObvSyncSnapshotNode node) throws Exception;

    //////
    // Method used to deserialize a node that was serialized with ObvSyncSnapshotNode.serialize(ObjectMapper jsonObjectMapper)
    byte[] serialize(ObvSyncSnapshotNode snapshotNode) throws Exception;
    //////
    // Method used to deserialize a node that was serialized with ObvSyncSnapshotNode.serialize(ObjectMapper jsonObjectMapper)
    ObvSyncSnapshotNode deserialize(byte[] serializedSnapshotNode) throws Exception;


    interface RestoreFinishedCallback {
        void onRestoreSuccess();
        void onRestoreFailure();
    }
}
