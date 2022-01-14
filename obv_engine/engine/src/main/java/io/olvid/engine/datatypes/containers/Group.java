/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.engine.datatypes.containers;

import io.olvid.engine.datatypes.Identity;

public class Group {
    private final byte[] groupOwnerAndUid;
    private final Identity ownedIdentity;
    private final Identity[] groupMembers;
    private final IdentityWithSerializedDetails[] pendingGroupMembers;
    private final Identity[] declinedPendingMembers;
    private final Identity groupOwner; // NULL for groups where you are the owner
    private final long groupMembersVersion;

    public Group(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers, Identity[] declinedPendingMembers, Identity groupOwner, long groupMembersVersion) {
        this.groupOwnerAndUid = groupOwnerAndUid;
        this.ownedIdentity = ownedIdentity;
        this.groupMembers = groupMembers;
        this.pendingGroupMembers = pendingGroupMembers;
        this.declinedPendingMembers = declinedPendingMembers;
        this.groupOwner = groupOwner;
        this.groupMembersVersion = groupMembersVersion;
    }

    public byte[] getGroupOwnerAndUid() {
        return groupOwnerAndUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity[] getGroupMembers() {
        return groupMembers;
    }

    public IdentityWithSerializedDetails[] getPendingGroupMembers() {
        return pendingGroupMembers;
    }

    public Identity[] getDeclinedPendingMembers() {
        return declinedPendingMembers;
    }

    public Identity getGroupOwner() {
        return groupOwner;
    }

    public long getGroupMembersVersion() {
        return groupMembersVersion;
    }

    public boolean isMember(Identity contactIdentity) {
        for (Identity memberIdentity : groupMembers) {
            if (memberIdentity.equals(contactIdentity)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPendingMember(Identity contactIdentity) {
        for (IdentityWithSerializedDetails pendingMember : pendingGroupMembers) {
            if (pendingMember.identity.equals(contactIdentity)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeclinedPendingMember(Identity contactIdentity) {
        for (Identity pendingMember : declinedPendingMembers) {
            if (pendingMember.equals(contactIdentity)) {
                return true;
            }
        }
        return false;
    }
}
