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
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactGroup;
import io.olvid.engine.identity.databases.ContactGroupDetails;
import io.olvid.engine.identity.databases.ContactGroupMembersJoin;
import io.olvid.engine.identity.databases.PendingGroupMember;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupV1SyncSnapshot implements ObvSyncSnapshotNode {
    public static final String PUBLISHED_DETAILS = "published_details";
    public static final String TRUSTED_DETAILS = "trusted_details";
    public static final String GROUP_MEMBERS_VERSION = "group_members_version";
    public static final String MEMBERS = "members";
    public static final String PENDING_MEMBERS = "pending_members";
    static HashSet<String> DEFAULT_JOINED_DOMAIN = new HashSet<>(Arrays.asList(PUBLISHED_DETAILS, TRUSTED_DETAILS, GROUP_MEMBERS_VERSION, MEMBERS, PENDING_MEMBERS));
    static HashSet<String> DEFAULT_OWNED_DOMAIN = new HashSet<>(Arrays.asList(PUBLISHED_DETAILS, GROUP_MEMBERS_VERSION, MEMBERS, PENDING_MEMBERS));


    public GroupDetailsSyncSnapshot published_details;
    public GroupDetailsSyncSnapshot trusted_details; // only for groups you do not own, null if same as published
    public Long group_members_version;
    @JsonSerialize(contentUsing = ObvBytesKey.Serializer.class)
    @JsonDeserialize(contentUsing = ObvBytesKey.Deserializer.class)
    public HashSet<ObvBytesKey> members;
    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV1PendingMember> pending_members;
    public HashSet<String> domain;

    public static GroupV1SyncSnapshot of(IdentityManagerSession identityManagerSession, ContactGroup group) throws SQLException {
        GroupV1SyncSnapshot groupV1SyncSnapshot = new GroupV1SyncSnapshot();

        groupV1SyncSnapshot.published_details = GroupDetailsSyncSnapshot.of(identityManagerSession, group.getPublishedDetails());
        if (group.getGroupOwner() == null) {
            groupV1SyncSnapshot.domain = DEFAULT_OWNED_DOMAIN;
        } else {
            if (group.getPublishedDetailsVersion() != group.getLatestOrTrustedDetailsVersion()) {
                groupV1SyncSnapshot.trusted_details = GroupDetailsSyncSnapshot.of(identityManagerSession, group.getLatestOrTrustedDetails());
            }
            groupV1SyncSnapshot.domain = DEFAULT_JOINED_DOMAIN;
        }

        groupV1SyncSnapshot.group_members_version = group.getGroupMembersVersion();

        groupV1SyncSnapshot.members = new HashSet<>();
        for (Identity memberIdentity : ContactGroupMembersJoin.getContactIdentitiesInGroup(identityManagerSession, group.getGroupOwnerAndUid(), group.getOwnedIdentity())) {
            groupV1SyncSnapshot.members.add(new ObvBytesKey(memberIdentity.getBytes()));
        }

        groupV1SyncSnapshot.pending_members = new HashMap<>();
        for (PendingGroupMember pendingGroupMember : PendingGroupMember.getAllInGroup(identityManagerSession, group.getGroupOwnerAndUid(), group.getOwnedIdentity())) {
            groupV1SyncSnapshot.pending_members.put(new ObvBytesKey(pendingGroupMember.getContactIdentity().getBytes()), GroupV1PendingMember.of(pendingGroupMember));
        }

        return groupV1SyncSnapshot;
    }

    @JsonIgnore
    public ContactGroup restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwnerIdentity, byte[] groupOwnerAndUid) throws Exception {
        if (!domain.contains(GROUP_MEMBERS_VERSION) || !domain.contains(MEMBERS) || !domain.contains(PENDING_MEMBERS) || !domain.contains(PUBLISHED_DETAILS)) {
            Logger.e("Trying to restore an incomplete GroupV1SyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        ContactGroup contactGroup;
        if (groupOwnerIdentity.equals(ownedIdentity)) {
            ContactGroupDetails publishedDetails = published_details.restoreGroup(identityManagerSession, ownedIdentity, groupOwnerIdentity, groupOwnerAndUid);

            contactGroup = new ContactGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity, null, publishedDetails.getVersion());
            contactGroup.groupMembersVersion = group_members_version;
            contactGroup.insert();
        } else {
            ContactGroupDetails trustedDetails;
            ContactGroupDetails publishedDetails = published_details.restoreGroup(identityManagerSession, ownedIdentity, groupOwnerIdentity, groupOwnerAndUid);
            if (domain.contains(TRUSTED_DETAILS) && trusted_details != null && !Objects.equals(trusted_details.version, published_details.version)) {
                trustedDetails = trusted_details.restoreGroup(identityManagerSession, ownedIdentity, groupOwnerIdentity, groupOwnerAndUid);
            } else {
                trustedDetails = null;
            }
            contactGroup = new ContactGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity, groupOwnerIdentity, publishedDetails.getVersion());
            if (trustedDetails != null) {
                contactGroup.latestOrTrustedDetailsVersion = trustedDetails.getVersion();
            }
            contactGroup.groupMembersVersion = group_members_version;
            contactGroup.insert();
        }

        // restore members
        for (ObvBytesKey member : members) {
            Identity memberIdentity = Identity.of(member.getBytes());
            ContactGroupMembersJoin.create(identityManagerSession, groupOwnerAndUid, ownedIdentity, memberIdentity);
        }

        // restore pending members
        for (Map.Entry<ObvBytesKey, GroupV1PendingMember> pendingMemberEntry : pending_members.entrySet()) {
            Identity pendingMemberIdentity = Identity.of(pendingMemberEntry.getKey().getBytes());
            PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, groupOwnerAndUid, ownedIdentity, pendingMemberIdentity, pendingMemberEntry.getValue().serialized_details);
            pendingGroupMember.declined = pendingMemberEntry.getValue().declined != null && pendingMemberEntry.getValue().declined;
            pendingGroupMember.insert();
        }

        return contactGroup;
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
    public static class GroupV1PendingMember {
        public String serialized_details;
        public Boolean declined;

        private static GroupV1PendingMember of(PendingGroupMember pendingGroupMember) {
            GroupV1PendingMember groupV1PendingMember = new GroupV1PendingMember();
            groupV1PendingMember.serialized_details = pendingGroupMember.getContactSerializedDetails();
            groupV1PendingMember.declined = pendingGroupMember.isDeclined() ? true : null;
            return groupV1PendingMember;
        }
    }
}
