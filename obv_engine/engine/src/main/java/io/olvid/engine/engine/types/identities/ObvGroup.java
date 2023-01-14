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

package io.olvid.engine.engine.types.identities;

import io.olvid.engine.engine.types.JsonGroupDetails;

public class ObvGroup {
    private final byte[] bytesGroupOwnerAndUid;
    private final JsonGroupDetails groupDetails;
    private final byte[] bytesOwnedIdentity;
    private final byte[][] bytesGroupMembersIdentities;
    private final ObvIdentity[] pendingGroupMembers;
    private final byte[][] bytesDeclinedPendingMembers;
    private final byte[] bytesGroupOwnerIdentity; // NULL for groups where you are the owner

    public ObvGroup(byte[] bytesGroupOwnerAndUid, JsonGroupDetails groupDetails, byte[] bytesOwnedIdentity, byte[][] bytesGroupMembersIdentities, ObvIdentity[] pendingGroupMembers, byte[][] bytesDeclinedPendingMembers, byte[] bytesGroupOwnerIdentity) {
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        this.groupDetails = groupDetails;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupMembersIdentities = bytesGroupMembersIdentities;
        this.pendingGroupMembers = pendingGroupMembers;
        this.bytesDeclinedPendingMembers = bytesDeclinedPendingMembers;
        this.bytesGroupOwnerIdentity = bytesGroupOwnerIdentity;
    }

    public byte[] getBytesGroupOwnerAndUid() {
        return bytesGroupOwnerAndUid;
    }

    public JsonGroupDetails getGroupDetails() {
        return groupDetails;
    }

    public byte[] getBytesOwnedIdentity() {
        return bytesOwnedIdentity;
    }

    public byte[][] getBytesGroupMembersIdentities() {
        return bytesGroupMembersIdentities;
    }

    public ObvIdentity[] getPendingGroupMembers() {
        return pendingGroupMembers;
    }

    public byte[][] getBytesDeclinedPendingMembers() {
        return bytesDeclinedPendingMembers;
    }

    public byte[] getBytesGroupOwnerIdentity() { // NULL for groups where you are the owner
        return bytesGroupOwnerIdentity;
    }
}
