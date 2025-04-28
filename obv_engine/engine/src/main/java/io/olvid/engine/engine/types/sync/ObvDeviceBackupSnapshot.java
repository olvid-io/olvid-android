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

package io.olvid.engine.engine.types.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvIdentity;

public class ObvDeviceBackupSnapshot {
    private final HashMap<String, ObvSyncSnapshotNode> snapshotMap;

    private ObvDeviceBackupSnapshot(HashMap<String, ObvSyncSnapshotNode> snapshotMap) {
        this.snapshotMap = snapshotMap;
    }

    public static ObvDeviceBackupSnapshot get(ObvBackupAndSyncDelegate... delegates) {
        HashMap<String, ObvSyncSnapshotNode> snapshotMap = new HashMap<>();
        for (ObvBackupAndSyncDelegate delegate : delegates) {
            snapshotMap.put(delegate.getTag(), delegate.getDeviceSnapshot());
        }
        return new ObvDeviceBackupSnapshot(snapshotMap);
    }


    public HashMap<DictionaryKey, Encoded> toEncodedDictionary(ObvBackupAndSyncDelegate... delegates) {
        try {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                ObvSyncSnapshotNode node = snapshotMap.get(delegate.getTag());
                if (node == null) {
                    return null;
                }
                map.put(new DictionaryKey(delegate.getTag()), Encoded.of(delegate.serialize(ObvBackupAndSyncDelegate.SerializationContext.DEVICE, node)));
            }
            return map;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public static ObvDeviceBackupSnapshot fromEncodedDictionary(HashMap<DictionaryKey, Encoded> map, ObvBackupAndSyncDelegate... delegates) {
        try {
            HashMap<String, ObvSyncSnapshotNode> snapshotMap = new HashMap<>();
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                Encoded encodedNode = map.get(new DictionaryKey(delegate.getTag()));
                if (encodedNode == null) {
                    return null;
                }
                snapshotMap.put(delegate.getTag(), delegate.deserialize(ObvBackupAndSyncDelegate.SerializationContext.DEVICE, encodedNode.decodeBytes()));
            }
            return new ObvDeviceBackupSnapshot(snapshotMap);
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public ObvSyncSnapshotNode getSnapshotNode(String tag) {
        return snapshotMap.get(tag);
    }
}
