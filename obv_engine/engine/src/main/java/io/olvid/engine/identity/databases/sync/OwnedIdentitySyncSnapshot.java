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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvGroupOwnerAndUidKey;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactGroup;
import io.olvid.engine.identity.databases.ContactGroupV2;
import io.olvid.engine.identity.databases.ContactIdentity;
import io.olvid.engine.identity.databases.KeycloakServer;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedIdentitySyncSnapshot implements ObvSyncSnapshotNode {
    public static final String PUBLISHED_DETAILS = "published_details";
    public static final String KEYCLOAK = "keycloak";
    public static final String CONTACTS = "contacts";
    public static final String GROUPS = "groups";
    public static final String GROUPS2 = "groups2";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(PUBLISHED_DETAILS, KEYCLOAK, CONTACTS, GROUPS, GROUPS2));


    public IdentityDetailsSyncSnapshot published_details;
    public KeycloakSyncSnapshot keycloak;
    @JsonSerialize(keyUsing = ObvBytesKey.Serializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, ContactSyncSnapshot> contacts;

    @JsonSerialize(keyUsing = ObvGroupOwnerAndUidKey.Serializer.class)
    @JsonDeserialize(keyUsing = ObvGroupOwnerAndUidKey.Deserializer.class)
    public HashMap<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groups;

    @JsonSerialize(keyUsing = ObvBytesKey.Serializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV2SyncSnapshot> groups2;
    public HashSet<String> domain;


    public static OwnedIdentitySyncSnapshot of(IdentityManagerSession identityManagerSession, OwnedIdentity ownedIdentity) throws SQLException {
        OwnedIdentitySyncSnapshot ownedIdentitySyncSnapshot = new OwnedIdentitySyncSnapshot();

        OwnedIdentityDetails publishedDetails = OwnedIdentityDetails.get(identityManagerSession, ownedIdentity.getOwnedIdentity(), ownedIdentity.getPublishedDetailsVersion());
        if (publishedDetails != null) {
            ownedIdentitySyncSnapshot.published_details = IdentityDetailsSyncSnapshot.of(identityManagerSession, publishedDetails);
        }

        if (ownedIdentity.isKeycloakManaged()) {
            KeycloakServer keycloakServer = KeycloakServer.get(identityManagerSession, ownedIdentity.getKeycloakServerUrl(), ownedIdentity.getOwnedIdentity());
            if (keycloakServer != null) {
                ownedIdentitySyncSnapshot.keycloak = KeycloakSyncSnapshot.of(identityManagerSession, keycloakServer);
            }
        }

        ownedIdentitySyncSnapshot.contacts = new HashMap<>();
        for (ContactIdentity contact : ContactIdentity.getAll(identityManagerSession, ownedIdentity.getOwnedIdentity())) {
            ownedIdentitySyncSnapshot.contacts.put(new ObvBytesKey(contact.getContactIdentity().getBytes()), ContactSyncSnapshot.of(identityManagerSession, contact));
        }

        ownedIdentitySyncSnapshot.groups = new HashMap<>();
        for (ContactGroup group : ContactGroup.getAllForIdentity(identityManagerSession, ownedIdentity.getOwnedIdentity())) {
            ownedIdentitySyncSnapshot.groups.put(new ObvGroupOwnerAndUidKey(group.getGroupOwnerAndUid()), GroupV1SyncSnapshot.of(identityManagerSession, group));
        }

        ownedIdentitySyncSnapshot.groups2 = new HashMap<>();
        for (ContactGroupV2 group2 : ContactGroupV2.getAllForIdentity(identityManagerSession, ownedIdentity.getOwnedIdentity())) {
            ownedIdentitySyncSnapshot.groups2.put(new ObvBytesKey(group2.getGroupIdentifier().getBytes()), GroupV2SyncSnapshot.of(identityManagerSession, group2));
        }

        ownedIdentitySyncSnapshot.domain = DEFAULT_DOMAIN;
        return ownedIdentitySyncSnapshot;
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        // TODO
        return false;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO
        return null;
    }
}
