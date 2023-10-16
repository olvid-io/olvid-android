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

package io.olvid.messenger.databases.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;

public class UpdateAllGroupMembersNames implements Runnable {
    @Nullable
    private final byte[] bytesOwnedIdentity;
    @Nullable private final byte[] bytesContactIdentity;

    public UpdateAllGroupMembersNames() {
        bytesOwnedIdentity = null;
        bytesContactIdentity = null;
    }

    public UpdateAllGroupMembersNames(@NonNull byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = null;
    }

    public UpdateAllGroupMembersNames(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
    }


    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        for (Group group : ((bytesOwnedIdentity == null) ? db.groupDao().getAll() : (bytesContactIdentity == null) ? db.groupDao().getAllForOwnedIdentity(bytesOwnedIdentity) : db.groupDao().getAllForContact(bytesOwnedIdentity, bytesContactIdentity))) {
            group.groupMembersNames = StringUtils.joinContactDisplayNames(db.groupDao().getGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid));
            db.groupDao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.groupMembersNames);
        }

        for (Group2 group : ((bytesOwnedIdentity == null) ? db.group2Dao().getAll() : (bytesContactIdentity == null) ? db.group2Dao().getAllForOwnedIdentity(bytesOwnedIdentity) : db.group2Dao().getAllForContact(bytesOwnedIdentity, bytesContactIdentity))) {
            group.groupMembersNames = StringUtils.joinContactDisplayNames(db.group2Dao().getGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupIdentifier));
            db.group2Dao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.groupMembersNames);

            if (group.name == null && group.customName == null) {
                Discussion discussion = db.discussionDao().getByGroupIdentifier(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                if (discussion != null) {
                    discussion.title = group.getCustomName();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                    ShortcutActivity.updateShortcut(discussion);
                }
            }
        }
    }
}
