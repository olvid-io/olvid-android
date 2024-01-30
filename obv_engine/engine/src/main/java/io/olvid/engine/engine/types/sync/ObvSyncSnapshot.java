/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvIdentity;

public class ObvSyncSnapshot {
    private final HashMap<String, ObvSyncSnapshotNode> snapshotMap;

    private ObvSyncSnapshot(HashMap<String, ObvSyncSnapshotNode> snapshotMap) {
        this.snapshotMap = snapshotMap;
    }

    public static ObvSyncSnapshot get(Identity ownedIdentity, ObvBackupAndSyncDelegate... delegates) {
        HashMap<String, ObvSyncSnapshotNode> snapshotMap = new HashMap<>();
        for (ObvBackupAndSyncDelegate delegate : delegates) {
            snapshotMap.put(delegate.getTag(), delegate.getSyncSnapshot(ownedIdentity));
        }
        return new ObvSyncSnapshot(snapshotMap);
    }


    public List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> restoreOwnedIdentity(ObvIdentity obvOwnedIdentity, ObvBackupAndSyncDelegate... delegates) throws Exception {
        List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> callbacks = new ArrayList<>();
        try {
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                ObvSyncSnapshotNode node = snapshotMap.get(delegate.getTag());
                if (node == null) {
                    throw new Exception();
                }
                ObvBackupAndSyncDelegate.RestoreFinishedCallback callback = delegate.restoreOwnedIdentity(obvOwnedIdentity, node);
                if (callback != null) {
                    callbacks.add(callback);
                }
            }
            return callbacks;
        } catch (Exception e) {
            // if an exception occurs, call the onRestoreFailure of all callbacks we already got (typically to rollback transactions)
            for (ObvBackupAndSyncDelegate.RestoreFinishedCallback callback : callbacks) {
                try {
                    callback.onRestoreFailure();
                } catch (Exception ignored) { }
            }
            throw e;
        }
    }

    public List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> restore(ObvBackupAndSyncDelegate... delegates) throws Exception {
        List<ObvBackupAndSyncDelegate.RestoreFinishedCallback> callbacks = new ArrayList<>();
        try {
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                ObvSyncSnapshotNode node = snapshotMap.get(delegate.getTag());
                if (node == null) {
                    throw new Exception();
                }
                ObvBackupAndSyncDelegate.RestoreFinishedCallback callback = delegate.restoreSyncSnapshot(node);
                if (callback != null) {
                    callbacks.add(callback);
                }
            }
            return callbacks;
        } catch (Exception e) {
            // if an exception occurs, call the onRestoreFailure of all callbacks we already got (typically to rollback transactions)
            for (ObvBackupAndSyncDelegate.RestoreFinishedCallback callback : callbacks) {
                try {
                    callback.onRestoreFailure();
                } catch (Exception ignored) { }
            }
            throw e;
        }
    }

    public HashMap<DictionaryKey, Encoded> toEncodedDictionary(ObvBackupAndSyncDelegate... delegates) {
        try {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                ObvSyncSnapshotNode node = snapshotMap.get(delegate.getTag());
                if (node == null) {
                    return null;
                }
                map.put(new DictionaryKey(delegate.getTag()), Encoded.of(delegate.serialize(node)));
            }
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public static ObvSyncSnapshot fromEncodedDictionary(HashMap<DictionaryKey, Encoded> map, ObvBackupAndSyncDelegate... delegates) {
        try {
            HashMap<String, ObvSyncSnapshotNode> snapshotMap = new HashMap<>();
            for (ObvBackupAndSyncDelegate delegate : delegates) {
                Encoded encodedNode = map.get(new DictionaryKey(delegate.getTag()));
                if (encodedNode == null) {
                    return null;
                }
                snapshotMap.put(delegate.getTag(), delegate.deserialize(encodedNode.decodeBytes()));
            }
            return new ObvSyncSnapshot(snapshotMap);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean areContentsTheSame(ObvSyncSnapshot otherSnapshot) {
        if (otherSnapshot == null) {
            return false;
        }
        if (!Objects.equals(snapshotMap.keySet(), otherSnapshot.snapshotMap.keySet())) {
            return false;
        }
        for (Map.Entry<String, ObvSyncSnapshotNode> entry : snapshotMap.entrySet()) {
            if (!entry.getValue().areContentsTheSame(otherSnapshot.snapshotMap.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshot otherSnapshot) throws Exception {
        if (otherSnapshot == null || !Objects.equals(snapshotMap.keySet(), otherSnapshot.snapshotMap.keySet())) {
            throw new Exception();
        }

        List<ObvSyncDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, ObvSyncSnapshotNode> entry : snapshotMap.entrySet()) {
            diffs.addAll(entry.getValue().computeDiff(otherSnapshot.snapshotMap.get(entry.getKey())));
        }

        return diffs;
    }

    public ObvSyncSnapshotNode getSnapshotNode(String tag) {
        return snapshotMap.get(tag);
    }
}
