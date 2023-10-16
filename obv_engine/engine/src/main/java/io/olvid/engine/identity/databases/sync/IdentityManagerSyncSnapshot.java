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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityManagerSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String OWNED_IDENTITY = "owned_identity";
    public static final String OWNED_IDENTITY_NODE = "owned_identity_node";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(OWNED_IDENTITY, OWNED_IDENTITY_NODE));

    public byte[] owned_identity;
    public OwnedIdentitySyncSnapshot owned_identity_node;
    public HashSet<String> domain;

    public static IdentityManagerSyncSnapshot of(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        IdentityManagerSyncSnapshot identityManagerSyncSnapshot = new IdentityManagerSyncSnapshot();
        identityManagerSyncSnapshot.owned_identity = ownedIdentity.getBytes();
        OwnedIdentity ownedIdentityObject = OwnedIdentity.get(identityManagerSession, ownedIdentity);
        if (ownedIdentityObject != null) {
            identityManagerSyncSnapshot.owned_identity_node = OwnedIdentitySyncSnapshot.of(identityManagerSession, ownedIdentityObject);
        }
        identityManagerSyncSnapshot.domain = DEFAULT_DOMAIN;
        return identityManagerSyncSnapshot;
    }

    @JsonIgnore
    public void restore(IdentityManagerSession identityManagerSession, ProtocolStarterDelegate protocolStarterDelegate) throws Exception {
        if (!domain.contains(OWNED_IDENTITY) || !domain.contains(OWNED_IDENTITY_NODE)) {
            Logger.e("Trying to restore an incomplete IdentityManagerSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }
        Identity ownedIdentity = Identity.of(owned_identity);
        if (!identityManagerSession.identityDelegate.isOwnedIdentity(identityManagerSession.session, ownedIdentity)) {
            Logger.e("Trying to restore a snapshot of an unknown owned identity");
            throw new Exception();
        }

        owned_identity_node.restore(identityManagerSession, protocolStarterDelegate, ownedIdentity);
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof IdentityManagerSyncSnapshot)) {
            return false;
        }

        IdentityManagerSyncSnapshot other = (IdentityManagerSyncSnapshot) otherSnapshotNode;
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

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        if (!(otherSnapshotNode instanceof IdentityManagerSyncSnapshot)) {
            throw new Exception();
        }
        IdentityManagerSyncSnapshot other = (IdentityManagerSyncSnapshot) otherSnapshotNode;
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
                case OWNED_IDENTITY_NODE: {
                    diffs.addAll(owned_identity_node.computeDiff(other.owned_identity_node));
                    break;
                }
            }
        }
        return diffs;
    }
}
