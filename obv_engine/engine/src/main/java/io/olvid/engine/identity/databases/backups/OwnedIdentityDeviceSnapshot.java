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

package io.olvid.engine.identity.databases.backups;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.databases.sync.IdentityDetailsSyncSnapshot;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedIdentityDeviceSnapshot implements ObvSyncSnapshotNode {
    public static final String PUBLISHED_DETAILS = "published_details";
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";
    public static final String BACKUP_SEED = "backup_seed";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(PUBLISHED_DETAILS, KEYCLOAK_MANAGED, BACKUP_SEED));


    public IdentityDetailsSyncSnapshot published_details;
    public Boolean keycloak_managed;
    public byte[] backup_seed;
    public HashSet<String> domain;


    public static OwnedIdentityDeviceSnapshot of(IdentityManagerSession identityManagerSession, OwnedIdentity ownedIdentity) throws SQLException {
        OwnedIdentityDeviceSnapshot ownedIdentityDeviceSnapshot = new OwnedIdentityDeviceSnapshot();

        OwnedIdentityDetails publishedDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity.getOwnedIdentity(), ownedIdentity.getPublishedDetailsVersion());
        if (publishedDetails != null) {
            ownedIdentityDeviceSnapshot.published_details = IdentityDetailsSyncSnapshot.of(identityManagerSession, publishedDetails);
        }

        ownedIdentityDeviceSnapshot.keycloak_managed = ownedIdentity.isKeycloakManaged();
        ownedIdentityDeviceSnapshot.backup_seed = ownedIdentity.getBackupSeed().getBackupSeedBytes();

        ownedIdentityDeviceSnapshot.domain = DEFAULT_DOMAIN;
        return ownedIdentityDeviceSnapshot;
    }

    public boolean validate() {
        return domain.containsAll(DEFAULT_DOMAIN)
                && backup_seed != null
                && keycloak_managed != null
                && published_details != null;
    }



    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        return false;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        return null;
    }
}
