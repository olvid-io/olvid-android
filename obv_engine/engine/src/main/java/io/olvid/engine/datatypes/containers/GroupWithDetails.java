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
import io.olvid.engine.engine.types.JsonGroupDetails;

public class GroupWithDetails extends Group {
    private final JsonGroupDetails publishedGroupDetails;
    private final JsonGroupDetails latestOrTrustedGroupDetails;
    private final boolean hasMultipleDetails;

    public GroupWithDetails(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers, Identity[] declinedPendingMembers, Identity groupOwner, long groupMembersVersion, JsonGroupDetails publishedGroupDetails, JsonGroupDetails latestOrTrustedGroupDetails, boolean hasMultipleDetails) {
        super(groupOwnerAndUid, ownedIdentity, groupMembers, pendingGroupMembers, declinedPendingMembers, groupOwner, groupMembersVersion);
        this.publishedGroupDetails = publishedGroupDetails;
        this.latestOrTrustedGroupDetails = latestOrTrustedGroupDetails;
        this.hasMultipleDetails = hasMultipleDetails;
    }

    public JsonGroupDetails getPublishedGroupDetails() {
        return publishedGroupDetails;
    }

    public JsonGroupDetails getLatestOrTrustedGroupDetails() {
        return latestOrTrustedGroupDetails;
    }

    public boolean hasMultipleDetails() {
        return hasMultipleDetails;
    }
}
