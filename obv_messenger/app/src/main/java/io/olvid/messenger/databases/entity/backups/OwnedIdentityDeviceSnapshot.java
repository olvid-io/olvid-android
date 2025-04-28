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

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvGroupOwnerAndUidKey;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.sync.ContactSyncSnapshot;
import io.olvid.messenger.databases.entity.sync.GroupV1SyncSnapshot;
import io.olvid.messenger.databases.entity.sync.GroupV2SyncSnapshot;
import io.olvid.messenger.databases.tasks.UpdateAllGroupMembersNames;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedIdentityDeviceSnapshot implements ObvSyncSnapshotNode {
    public static final String CUSTOM_NAME = "custom_name";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(List.of(CUSTOM_NAME));

    public String custom_name;
    public HashSet<String> domain;

    public static OwnedIdentityDeviceSnapshot of(@NonNull AppDatabase db, @NonNull OwnedIdentity ownedIdentity) {
        OwnedIdentityDeviceSnapshot ownedIdentityDeviceSnapshot = new OwnedIdentityDeviceSnapshot();
        ownedIdentityDeviceSnapshot.custom_name = ownedIdentity.customDisplayName;
        ownedIdentityDeviceSnapshot.domain = DEFAULT_DOMAIN;
        return ownedIdentityDeviceSnapshot;
    }

    @Override
    @JsonIgnore
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        return false;
    }

    @Override
    @JsonIgnore
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        return null;
    }
}
