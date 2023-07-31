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

public interface ObvBackupAndSyncDelegate {
    //////
    // Return a tag corresponding to this delegate
    String getTag();

    //////
    // This method computes a snapshot of the data to sync
    ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity);


    //////
    // Method used to deserialize a node that was serialized with ObvSyncSnapshotNode.serialize(ObjectMapper jsonObjectMapper)
    byte[] serialize(ObvSyncSnapshotNode snapshotNode) throws Exception;
    //////
    // Method used to deserialize a node that was serialized with ObvSyncSnapshotNode.serialize(ObjectMapper jsonObjectMapper)
    ObvSyncSnapshotNode deserialize(byte[] serializedSnapshotNode) throws Exception;

}
