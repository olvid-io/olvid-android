/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;

public class UpdateGroupNameAndPhotoTask implements Runnable {
    private final byte[] bytesGroupUid;
    private final byte[] bytesOwnedIdentity;
    private final String newName;
    private final String photoUrl;

    public UpdateGroupNameAndPhotoTask(byte[] bytesGroupUid, byte[] bytesOwnedIdentity, String newName, String photoUrl) {
        this.bytesGroupUid = bytesGroupUid;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.newName = newName;
        this.photoUrl = photoUrl;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
        // both for groups you own and do not own
        if (group != null) {
            group.name = newName;
            group.photoUrl = photoUrl;
            db.groupDao().updateNameAndPhoto(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.name, group.photoUrl);

            // rename the corresponding one-to-one discussion
            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            discussion.title = group.getCustomName();
            discussion.photoUrl = group.getCustomPhotoUrl();
            db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

            ShortcutActivity.updateShortcut(discussion);
        }
    }
}
