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

package io.olvid.messenger.databases.entity.sync;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String OWNED_IDENTITY = "owned_identity";
    public static final String SETTINGS = "settings";
    public static final String OWNED_IDENTITY_NODE = "owned_identity_node";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(OWNED_IDENTITY, SETTINGS, OWNED_IDENTITY_NODE));

    public byte[] owned_identity;
    public SettingsSyncSnapshot settings;
    public OwnedIdentitySyncSnapshot owned_identity_node;
    public HashSet<String> domain;

    public static AppSyncSnapshot of(AppDatabase db, @NonNull byte[] bytesOwnedIdentity) {
        AppSyncSnapshot appSyncSnapshot = new AppSyncSnapshot();
        appSyncSnapshot.owned_identity = bytesOwnedIdentity;
        OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
        if (ownedIdentity != null) {
            appSyncSnapshot.settings = SettingsSyncSnapshot.of(ownedIdentity);
            appSyncSnapshot.owned_identity_node = OwnedIdentitySyncSnapshot.of(db, ownedIdentity);
        }
        appSyncSnapshot.domain = DEFAULT_DOMAIN;
        return appSyncSnapshot;
    }

    @JsonIgnore
    public void restore(AppDatabase db) throws Exception {
        if (!domain.contains(OWNED_IDENTITY) || !domain.contains(OWNED_IDENTITY_NODE)) {
            Logger.e("Trying to restore an incomplete IdentityManagerSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }
        if (db.ownedIdentityDao().get(owned_identity) == null) {
            Logger.e("Trying to restore a snapshot of an unknown owned identity");
            throw new Exception();
        }
        db.runInTransaction(() -> {
            if (domain.contains(SETTINGS) && settings != null) {
                settings.restore(db, owned_identity);
            }
            owned_identity_node.restore(db, owned_identity);
        });
    }



    @JsonIgnore
    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof AppSyncSnapshot)) {
            return false;
        }
        AppSyncSnapshot other = (AppSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case OWNED_IDENTITY: {
                    if (!Arrays.equals(owned_identity, other.owned_identity)) {
                        return false;
                    }
                    break;
                }
                case SETTINGS: {
                    if (!settings.areContentsTheSame(other.settings)) {
                        return false;
                    }
                    break;
                }
                case OWNED_IDENTITY_NODE: {
                    if (!owned_identity_node.areContentsTheSame(other.owned_identity_node)) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @JsonIgnore
    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        if (!(otherSnapshotNode instanceof AppSyncSnapshot)) {
            throw new Exception();
        }
        AppSyncSnapshot other = (AppSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        List<ObvSyncDiff> diffs = new ArrayList<>();
        for (String item : domainIntersection) {
            switch (item) {
                case OWNED_IDENTITY: {
                    if (!Arrays.equals(owned_identity, other.owned_identity)) {
                        throw new Exception();
                    }
                    break;
                }
                case SETTINGS: {
                    diffs.addAll(settings.computeDiff(other.settings));
                    break;
                }
                case OWNED_IDENTITY_NODE: {
                    diffs.addAll(owned_identity_node.computeDiff(other.owned_identity_node));
                    break;
                }
            }
        }
        return diffs;
    }
}
