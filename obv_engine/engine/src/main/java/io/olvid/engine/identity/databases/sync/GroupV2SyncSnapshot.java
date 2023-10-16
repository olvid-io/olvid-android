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
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactGroupV2;
import io.olvid.engine.identity.databases.ContactGroupV2Details;
import io.olvid.engine.identity.databases.ContactGroupV2Member;
import io.olvid.engine.identity.databases.ContactGroupV2PendingMember;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupV2SyncSnapshot implements ObvSyncSnapshotNode {
    public static final String PERMISSIONS = "permissions";
    public static final String VERSION = "version";
    public static final String DETAILS = "details";
    public static final String TRUSTED_DETAILS = "trusted_details";
    public static final String VERIFIED_ADMIN_CHAIN = "verified_admin_chain";
    public static final String MAIN_SEED = "main_seed";
    public static final String VERSION_SEED= "version_seed";
    public static final String ENCODED_ADMIN_KEY = "encoded_admin_key";
    public static final String INVITATION_NONCE = "invitation_nonce";
    public static final String LAST_MODIFICATION_TIMESTAMP = "last_modification_timestamp";
    public static final String PUSH_TOPIC = "push_topic";
    public static final String SERIALIZED_SHARED_SETTINGS = "serialized_shared_settings";
    public static final String SERIALIZED_GROUP_TYPE = "serialized_group_type";
    public static final String MEMBERS = "members";
    public static final String PENDING_MEMBERS = "pending_members";
    static HashSet<String> DEFAULT_SERVER_DOMAIN = new HashSet<>(Arrays.asList(PERMISSIONS, VERSION, DETAILS, TRUSTED_DETAILS, VERIFIED_ADMIN_CHAIN, MAIN_SEED, VERSION_SEED, ENCODED_ADMIN_KEY, INVITATION_NONCE, SERIALIZED_GROUP_TYPE, MEMBERS, PENDING_MEMBERS));
    static HashSet<String> DEFAULT_KEYCLOAK_DOMAIN = new HashSet<>(Arrays.asList(PERMISSIONS, DETAILS, INVITATION_NONCE, LAST_MODIFICATION_TIMESTAMP, PUSH_TOPIC, SERIALIZED_SHARED_SETTINGS, MEMBERS, PENDING_MEMBERS));



    public HashSet<String> permissions;
    public Integer version;
    public GroupDetailsSyncSnapshot details;
    public GroupDetailsSyncSnapshot trusted_details; // on restore use version - 1 as a version number for this
    public byte[] verified_admin_chain;
    public byte[] main_seed;
    public byte[] version_seed;
    public byte[] encoded_admin_key;
    public byte[] invitation_nonce;
    public Long last_modification_timestamp;
    public String push_topic;
    public String serialized_shared_settings;
    public String serialized_group_type;

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV2Member> members;

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV2PendingMember> pending_members;
    public HashSet<String> domain;


    public static GroupV2SyncSnapshot of(IdentityManagerSession identityManagerSession, ContactGroupV2 group2) throws SQLException {
        GroupV2SyncSnapshot groupV2SyncSnapshot = new GroupV2SyncSnapshot();

        groupV2SyncSnapshot.permissions = new HashSet<>(group2.getOwnPermissionStrings());

        ContactGroupV2Details publishedDetails = ContactGroupV2Details.get(identityManagerSession, group2.getOwnedIdentity(), group2.getGroupIdentifier(), group2.getVersion());
        if (publishedDetails != null) {
            groupV2SyncSnapshot.details = GroupDetailsSyncSnapshot.of(identityManagerSession, publishedDetails);
        }
        groupV2SyncSnapshot.invitation_nonce = group2.getOwnGroupInvitationNonce();

        if (group2.getGroupIdentifier().category == GroupV2.Identifier.CATEGORY_SERVER) {
            // normal group v2
            groupV2SyncSnapshot.version = group2.getVersion();
            if (group2.getTrustedDetailsVersion() != group2.getVersion()) {
                ContactGroupV2Details trustedDetails = ContactGroupV2Details.get(identityManagerSession, group2.getOwnedIdentity(), group2.getGroupIdentifier(), group2.getTrustedDetailsVersion());
                if (trustedDetails != null) {
                    groupV2SyncSnapshot.trusted_details = GroupDetailsSyncSnapshot.of(identityManagerSession, trustedDetails);
                }
            }
            groupV2SyncSnapshot.verified_admin_chain = group2.getVerifiedAdministratorsChain();
            groupV2SyncSnapshot.main_seed = group2.getBlobMainSeed().getBytes();
            groupV2SyncSnapshot.version_seed = group2.getBlobVersionSeed().getBytes();
            if (group2.getGroupAdminServerAuthenticationPrivateKey() != null) {
                groupV2SyncSnapshot.encoded_admin_key = Encoded.of(group2.getGroupAdminServerAuthenticationPrivateKey()).getBytes();
            }
            groupV2SyncSnapshot.serialized_group_type = group2.getSerializedJsonGroupType();
            groupV2SyncSnapshot.last_modification_timestamp = null;
            groupV2SyncSnapshot.domain = DEFAULT_SERVER_DOMAIN;
        } else {
            // keycloak group v2
            groupV2SyncSnapshot.last_modification_timestamp = group2.getLastModificationTimestamp();
            groupV2SyncSnapshot.push_topic = group2.getPushTopic();
            groupV2SyncSnapshot.serialized_shared_settings = group2.getSerializedSharedSettings();
            groupV2SyncSnapshot.domain = DEFAULT_KEYCLOAK_DOMAIN;
        }

        groupV2SyncSnapshot.members = new HashMap<>();
        for (ContactGroupV2Member groupV2Member : ContactGroupV2Member.getAll(identityManagerSession, group2.getOwnedIdentity(), group2.getGroupIdentifier())) {
            groupV2SyncSnapshot.members.put(new ObvBytesKey(groupV2Member.getContactIdentity().getBytes()), GroupV2Member.of(groupV2Member));
        }

        groupV2SyncSnapshot.pending_members = new HashMap<>();
        for (ContactGroupV2PendingMember groupV2PendingMember : ContactGroupV2PendingMember.getAll(identityManagerSession, group2.getOwnedIdentity(), group2.getGroupIdentifier())) {
            groupV2SyncSnapshot.pending_members.put(new ObvBytesKey(groupV2PendingMember.getContactIdentity().getBytes()), GroupV2PendingMember.of(groupV2PendingMember));
        }

        return groupV2SyncSnapshot;
    }

    @JsonIgnore
    public ContactGroupV2 restore(IdentityManagerSession identityManagerSession, ProtocolStarterDelegate protocolStarterDelegate, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception {
        if (!domain.contains(PERMISSIONS) || !domain.contains(DETAILS) || !domain.contains(INVITATION_NONCE) || !domain.contains(MEMBERS) || !domain.contains(PENDING_MEMBERS)
                || (groupIdentifier.category == GroupV2.Identifier.CATEGORY_SERVER && (!domain.contains(VERSION) || !domain.contains(VERIFIED_ADMIN_CHAIN) || !domain.contains(MAIN_SEED) || !domain.contains(VERSION_SEED) || !domain.contains(ENCODED_ADMIN_KEY)))) {
            Logger.e("Trying to restore an incomplete GroupV2SyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        // restore the details
        ContactGroupV2Details contactGroupV2Details = details.restoreGroupV2(identityManagerSession, ownedIdentity, groupIdentifier, version == null ? 0 : version);
        ContactGroupV2Details trustedDetails;
        if (domain.contains(TRUSTED_DETAILS) && trusted_details != null && groupIdentifier.category == GroupV2.Identifier.CATEGORY_SERVER) {
            trustedDetails = trusted_details.restoreGroupV2(identityManagerSession, ownedIdentity, groupIdentifier, version == null ? -1 : version - 1);
        } else {
            trustedDetails = null;
        }

        // restore the group
        GroupV2.BlobKeys blobKeys;
        if (groupIdentifier.category == GroupV2.Identifier.CATEGORY_SERVER) {
            ServerAuthenticationPrivateKey serverAuthenticationPrivateKey;
            if (encoded_admin_key != null) {
                serverAuthenticationPrivateKey = (ServerAuthenticationPrivateKey) new Encoded(encoded_admin_key).decodePrivateKey();
            } else {
                serverAuthenticationPrivateKey = null;
            }
            blobKeys = ((main_seed == null) || (version_seed == null)) ? null : new GroupV2.BlobKeys(new Seed(main_seed), new Seed(version_seed), serverAuthenticationPrivateKey);
        } else {
            blobKeys = null;
        }

        ContactGroupV2 groupV2 = new ContactGroupV2(
                identityManagerSession,
                groupIdentifier.groupUid,
                groupIdentifier.serverUrl,
                groupIdentifier.category,
                ownedIdentity,
                GroupV2.Permission.serializePermissionStrings(permissions),
                contactGroupV2Details.getVersion(),
                verified_admin_chain,
                blobKeys,
                invitation_nonce,
                false,
                (domain.contains(LAST_MODIFICATION_TIMESTAMP) && last_modification_timestamp != null) ? last_modification_timestamp : System.currentTimeMillis(),
                domain.contains(PUSH_TOPIC) ? push_topic : null,
                domain.contains(SERIALIZED_SHARED_SETTINGS) ? serialized_shared_settings : null,
                serialized_group_type
        );
        if (trustedDetails != null) {
            groupV2.trustedDetailsVersion = trustedDetails.getVersion();
        }
        groupV2.insert();

        // restore members
        for (Map.Entry<ObvBytesKey, GroupV2Member> memberEntry : members.entrySet()) {
            Identity memberIdentity = Identity.of(memberEntry.getKey().getBytes());
            ContactGroupV2Member.create(identityManagerSession, ownedIdentity, groupIdentifier, memberIdentity, memberEntry.getValue().permissions, memberEntry.getValue().invitation_nonce);
        }

        // restore pending members
        for (Map.Entry<ObvBytesKey, GroupV2PendingMember> pendingMemberEntry : pending_members.entrySet()) {
            Identity pendingMemberIdentity = Identity.of(pendingMemberEntry.getKey().getBytes());
            ContactGroupV2PendingMember.create(identityManagerSession, ownedIdentity, groupIdentifier, pendingMemberIdentity, pendingMemberEntry.getValue().serialized_details, pendingMemberEntry.getValue().permissions, pendingMemberEntry.getValue().invitation_nonce);
        }

        try {
            protocolStarterDelegate.initiateGroupV2ReDownloadWithinTransaction(identityManagerSession.session, ownedIdentity, groupIdentifier);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return groupV2;
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
    public static class GroupV2Member {
        public HashSet<String> permissions;
        public byte[] invitation_nonce;

        private static GroupV2Member of(ContactGroupV2Member groupMember) {
            GroupV2Member groupV2Member = new GroupV2Member();
            groupV2Member.permissions = new HashSet<>(GroupV2.Permission.deserializePermissions(groupMember.getSerializedPermissions()));
            groupV2Member.invitation_nonce = groupMember.getGroupInvitationNonce();
            return groupV2Member;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupV2PendingMember {
        public String serialized_details;
        public HashSet<String> permissions;
        public byte[] invitation_nonce;

        private static GroupV2PendingMember of(ContactGroupV2PendingMember pendingGroupMember) {
            GroupV2PendingMember groupV2PendingMember = new GroupV2PendingMember();
            groupV2PendingMember.serialized_details = pendingGroupMember.getSerializedContactDetails();
            groupV2PendingMember.permissions = new HashSet<>(GroupV2.Permission.deserializePermissions(pendingGroupMember.getSerializedPermissions()));
            groupV2PendingMember.invitation_nonce = pendingGroupMember.getGroupInvitationNonce();
            return groupV2PendingMember;
        }
    }
}
