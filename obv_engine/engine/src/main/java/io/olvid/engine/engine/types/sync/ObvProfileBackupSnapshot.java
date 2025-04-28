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

import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;

public class ObvProfileBackupSnapshot {
    public static final String SNAPSHOT = "snapshot";
    public static final String ADDITIONAL_INFO = "additional_info";
    public static final String TIMESTAMP = "timestamp";

    public static final String INFO_PLATFORM = "platform";
    public static final String INFO_DEVICE_NAME = "device_name";

    private final ObvSyncSnapshot snapshot;
    private final Map<String, String> additional_info;
    private final long timestamp;


    private ObvProfileBackupSnapshot(ObvSyncSnapshot snapshot, long timestamp, Map<String, String> additional_info) {
        this.snapshot = snapshot;
        this.timestamp = timestamp;
        this.additional_info = additional_info;
    }

    public Map<String, String> getAdditionalInfo() {
        return additional_info;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ObvSyncSnapshot getSnapshot() {
        return snapshot;
    }

    public static ObvProfileBackupSnapshot get(Identity ownedIdentity, ObvBackupAndSyncDelegate... delegates) {
        ObvSyncSnapshot obvSyncSnapshot = ObvSyncSnapshot.get(ownedIdentity, delegates);
        HashMap<String, String> additionalProfileInfo = new HashMap<>();
        for (ObvBackupAndSyncDelegate delegate : delegates) {
            additionalProfileInfo.putAll(delegate.getAdditionalProfileInfo(ownedIdentity));
        }
        return new ObvProfileBackupSnapshot(
                obvSyncSnapshot,
                System.currentTimeMillis(),
                additionalProfileInfo
        );
    }


    public HashMap<DictionaryKey, Encoded> toEncodedDictionary(ObvBackupAndSyncDelegate... delegates) {
        try {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            map.put(new DictionaryKey(SNAPSHOT), Encoded.of(snapshot.toEncodedDictionary(delegates)));
            map.put(new DictionaryKey(ADDITIONAL_INFO), Encoded.of(additional_info));
            map.put(new DictionaryKey(TIMESTAMP), Encoded.of(timestamp));

            return map;
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }

    public static ObvProfileBackupSnapshot fromEncodedDictionary(HashMap<DictionaryKey, Encoded> map, ObvBackupAndSyncDelegate... delegates) {
        try {
            Encoded encodedSnapshot = map.get(new DictionaryKey(SNAPSHOT));
            Encoded encodedTimestamp = map.get(new DictionaryKey(TIMESTAMP));
            Encoded encodedAdditionalInfo = map.get(new DictionaryKey(ADDITIONAL_INFO));

            if (encodedSnapshot == null || encodedTimestamp == null) {
                return null;
            }

            return new ObvProfileBackupSnapshot(
                    ObvSyncSnapshot.fromEncodedDictionary(encodedSnapshot.decodeDictionary(), delegates),
                    encodedTimestamp.decodeLong(),
                    encodedAdditionalInfo == null ? new HashMap<>() : encodedAdditionalInfo.decodeStringMap()
            );
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }
    }
}
