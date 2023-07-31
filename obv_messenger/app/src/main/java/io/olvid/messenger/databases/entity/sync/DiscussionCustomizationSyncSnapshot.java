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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;

public class DiscussionCustomizationSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String SEND_READ_RECEIPT = "send_read_receipt";

    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(List.of(SEND_READ_RECEIPT));

    public Boolean send_read_receipt;
    public HashSet<String> domain;

    public static DiscussionCustomizationSyncSnapshot of(AppDatabase db, DiscussionCustomization discussionCustomization) {
        DiscussionCustomizationSyncSnapshot discussionCustomizationSyncSnapshot = new DiscussionCustomizationSyncSnapshot();
        discussionCustomizationSyncSnapshot.send_read_receipt = discussionCustomization.prefSendReadReceipt;
        discussionCustomizationSyncSnapshot.domain = DEFAULT_DOMAIN;
        return discussionCustomizationSyncSnapshot;
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof DiscussionCustomizationSyncSnapshot)) {
            return false;
        }

        DiscussionCustomizationSyncSnapshot other = (DiscussionCustomizationSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case SEND_READ_RECEIPT: {
                    if (!Objects.equals(send_read_receipt, other.send_read_receipt)) {
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
        // TODO
        return null;
    }
}
