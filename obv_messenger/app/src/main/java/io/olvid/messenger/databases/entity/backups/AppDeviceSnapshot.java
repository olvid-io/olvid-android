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

package io.olvid.messenger.databases.entity.backups;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppDeviceSnapshot implements ObvSyncSnapshotNode {
    public static final String OWNED_IDENTITIES = "owned_identities";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(List.of(OWNED_IDENTITIES));

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, OwnedIdentityDeviceSnapshot> owned_identities;
    public HashSet<String> domain;

    public static AppDeviceSnapshot of(AppDatabase db) {
        AppDeviceSnapshot appDeviceSnapshot = new AppDeviceSnapshot();
        appDeviceSnapshot.owned_identities = new HashMap<>();
        for (OwnedIdentity ownedIdentity : db.ownedIdentityDao().getAll()) {
            appDeviceSnapshot.owned_identities.put(
                    new ObvBytesKey(ownedIdentity.bytesOwnedIdentity),
                    OwnedIdentityDeviceSnapshot.of(db, ownedIdentity)
            );
        }
        appDeviceSnapshot.domain = DEFAULT_DOMAIN;
        return appDeviceSnapshot;
    }

    @JsonIgnore
    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
       return false;
    }

    @JsonIgnore
    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        return null;
    }
}
