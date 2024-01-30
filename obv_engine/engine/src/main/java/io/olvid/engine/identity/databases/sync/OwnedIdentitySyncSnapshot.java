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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvGroupOwnerAndUidKey;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactGroup;
import io.olvid.engine.identity.databases.ContactGroupV2;
import io.olvid.engine.identity.databases.ContactIdentity;
import io.olvid.engine.identity.databases.KeycloakServer;
import io.olvid.engine.identity.databases.OwnedDevice;
import io.olvid.engine.identity.databases.OwnedIdentity;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedIdentitySyncSnapshot implements ObvSyncSnapshotNode {
    public static final String PRIVATE_IDENTITY = "private_identity";
    public static final String PUBLISHED_DETAILS = "published_details";
    public static final String KEYCLOAK = "keycloak";
    public static final String CONTACTS = "contacts";
    public static final String GROUPS = "groups";
    public static final String GROUPS2 = "groups2";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(PRIVATE_IDENTITY, PUBLISHED_DETAILS, KEYCLOAK, CONTACTS, GROUPS, GROUPS2));


    public PrivateIdentity private_identity;
    public IdentityDetailsSyncSnapshot published_details;
    public KeycloakSyncSnapshot keycloak;
    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, ContactSyncSnapshot> contacts;

    @JsonSerialize(keyUsing = ObvGroupOwnerAndUidKey.Serializer.class)
    @JsonDeserialize(keyUsing = ObvGroupOwnerAndUidKey.Deserializer.class)
    public HashMap<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groups;

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV2SyncSnapshot> groups2;
    public HashSet<String> domain;


    public static OwnedIdentitySyncSnapshot of(IdentityManagerSession identityManagerSession, OwnedIdentity ownedIdentity) throws SQLException {

        OwnedIdentitySyncSnapshot ownedIdentitySyncSnapshot = new OwnedIdentitySyncSnapshot();

        ownedIdentitySyncSnapshot.private_identity = PrivateIdentity.of(ownedIdentity.getPrivateIdentity());

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


    @JsonIgnore
    public ObvIdentity restoreOwnedIdentity(IdentityManagerSession identityManagerSession, String deviceName, Identity ownedIdentity) throws Exception {
        if (!domain.contains(PRIVATE_IDENTITY) || !domain.contains(PUBLISHED_DETAILS)) {
            Logger.e("Trying to restore an incomplete OwnedIdentitySyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        // restore the private key
        ServerAuthenticationPrivateKey serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(private_identity.server_authentication_private_key).decodePrivateKey();
        EncryptionPrivateKey encryptionPrivateKey = (EncryptionPrivateKey) new Encoded(private_identity.encryption_private_key).decodePrivateKey();
        MACKey macKey = (MACKey) new Encoded(private_identity.mac_key).decodeSymmetricKey();
        io.olvid.engine.datatypes.PrivateIdentity privateIdentity = new io.olvid.engine.datatypes.PrivateIdentity(ownedIdentity, serverAuthenticationPrivateKey, encryptionPrivateKey, macKey);

        // restore published details
        OwnedIdentityDetails ownedIdentityDetails = published_details.restoreOwned(identityManagerSession, ownedIdentity);

        // create the owned identity in DB
        OwnedIdentity ownedIdentityObject = new OwnedIdentity(identityManagerSession, privateIdentity, ownedIdentityDetails.getVersion());
        ownedIdentityObject.insert();

        // restore keycloak data (if any)
        if (domain.contains(KEYCLOAK) && keycloak != null) {
            KeycloakServer keycloakServer = keycloak.restore(identityManagerSession, ownedIdentity, keycloak);
            if (keycloakServer != null) {
                ownedIdentityObject.setKeycloakServerUrl(keycloakServer.getServerUrl());
            }
        }

        // create the current device with a random deviceUid
        OwnedDevice currentOwnedDevice = OwnedDevice.createCurrentDevice(identityManagerSession, ownedIdentity, deviceName, identityManagerSession.prng);
        currentOwnedDevice.setRawDeviceCapabilities(ObvCapability.capabilityListToStringArray(ObvCapability.currentCapabilities));

        return new ObvIdentity(ownedIdentity, ownedIdentityDetails.getJsonIdentityDetails(), ownedIdentityObject.isKeycloakManaged(), true);
    }

    @JsonIgnore
    public void restore(IdentityManagerSession identityManagerSession, ProtocolStarterDelegate protocolStarterDelegate, Identity ownedIdentity) throws Exception {
        if (!domain.contains(PRIVATE_IDENTITY) || !domain.contains(PUBLISHED_DETAILS)) {
            Logger.e("Trying to restore an incomplete OwnedIdentitySyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        // restore contacts
        if (domain.contains(CONTACTS) && contacts != null) {
            for (Map.Entry<ObvBytesKey, ContactSyncSnapshot> contactEntry : contacts.entrySet()) {
                Identity contactIdentity = Identity.of(contactEntry.getKey().getBytes());
                contactEntry.getValue().restore(identityManagerSession, ownedIdentity, contactIdentity);
            }
        }

        // restore groups v1
        if (domain.contains(GROUPS) && groups != null) {
            for (Map.Entry<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groupEntry : groups.entrySet()) {
                Identity groupOwnerIdentity = Identity.of(groupEntry.getKey().groupOwner);
                groupEntry.getValue().restore(identityManagerSession, ownedIdentity, groupOwnerIdentity, groupEntry.getKey().getGroupOwnerAndUid());
            }
        }

        // restore groups v2
        if (domain.contains(GROUPS2) && groups2 != null) {
            for (Map.Entry<ObvBytesKey, GroupV2SyncSnapshot> group2Entry : groups2.entrySet()) {
                GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(new Encoded(group2Entry.getKey().getBytes()));
                group2Entry.getValue().restore(identityManagerSession, protocolStarterDelegate, ownedIdentity, groupIdentifier);
            }
        }
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        // TODO areContentsTheSame
        return false;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrivateIdentity {
        public byte[] server_authentication_private_key;
        public byte[] encryption_private_key;
        public byte[] mac_key;

        private static PrivateIdentity of (io.olvid.engine.datatypes.PrivateIdentity privateIdentity) {
            PrivateIdentity pi = new PrivateIdentity();
            pi.server_authentication_private_key = Encoded.of(privateIdentity.getServerAuthenticationPrivateKey()).getBytes();
            pi.encryption_private_key = Encoded.of(privateIdentity.getEncryptionPrivateKey()).getBytes();
            pi.mac_key = Encoded.of(privateIdentity.getMacKey()).getBytes();
            return pi;
        }
    }
}
