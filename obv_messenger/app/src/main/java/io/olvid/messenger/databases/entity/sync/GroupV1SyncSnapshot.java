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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupV1SyncSnapshot implements ObvSyncSnapshotNode {
    public static final String CUSTOM_NAME = "custom_name";
    public static final String PERSONAL_NOTE = "personal_note";
    public static final String DISCUSSION_CUSTOMIZATION = "discussion_customization";

    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(CUSTOM_NAME, PERSONAL_NOTE, DISCUSSION_CUSTOMIZATION));

    public String custom_name;
    public String personal_note;
    public DiscussionCustomizationSyncSnapshot discussion_customization;
    public HashSet<String> domain;

    public static GroupV1SyncSnapshot of(AppDatabase db, Group group) {
        GroupV1SyncSnapshot groupV1SyncSnapshot = new GroupV1SyncSnapshot();
        groupV1SyncSnapshot.custom_name = group.customName;
        groupV1SyncSnapshot.personal_note = group.personalNote;

        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
        if (discussion != null) {
            DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
            groupV1SyncSnapshot.discussion_customization = DiscussionCustomizationSyncSnapshot.of(db, discussionCustomization);
        }

        groupV1SyncSnapshot.domain = DEFAULT_DOMAIN;
        return groupV1SyncSnapshot;
    }

    @Override
    @JsonIgnore
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof GroupV1SyncSnapshot)) {
            return false;
        }

        GroupV1SyncSnapshot other = (GroupV1SyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case CUSTOM_NAME: {
                    if (!Objects.equals(custom_name, other.custom_name)) {
                        return false;
                    }
                    break;
                }
                case PERSONAL_NOTE: {
                    if (!Objects.equals(personal_note, other.personal_note)) {
                        return false;
                    }
                    break;
                }
                case DISCUSSION_CUSTOMIZATION: {
                    if ((discussion_customization == null && other.discussion_customization != null)
                            || (discussion_customization != null && discussion_customization.areContentsTheSame(other.discussion_customization))) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    @JsonIgnore
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO
        return null;
    }
}
