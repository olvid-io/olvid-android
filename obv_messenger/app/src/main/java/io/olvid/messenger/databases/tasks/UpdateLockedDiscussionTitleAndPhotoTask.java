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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;

public class UpdateLockedDiscussionTitleAndPhotoTask implements Runnable {
    private final long discussionId;
    private final String title;
    private final String absolutePhotoUrl;

    public UpdateLockedDiscussionTitleAndPhotoTask(long discussionId, String title, String absolutePhotoUrl) {
        // absolutePhotoUrl equal to "" means not to change the photo
        this.discussionId = discussionId;
        this.title = title;
        this.absolutePhotoUrl = absolutePhotoUrl;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);
        if (discussion == null || !discussion.isLocked()) {
            // this method should only be used to rename locked discussions
            return;
        }

        if (title != null) {
            discussion.title = title;
        }

        if (!"".equals(absolutePhotoUrl)) {
            // delete the old photo if there is one
            if (discussion.photoUrl != null) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    new File(App.absolutePathFromRelative(discussion.photoUrl)).delete();
                } catch (Exception e) {
                    Logger.d("Failed to delete old locked discussion photoUrl " + discussion.photoUrl);
                }
            }

            if (absolutePhotoUrl == null) {
                discussion.photoUrl = null;
            } else {
                try {
                    int i = 0;
                    String relativeOutputPath;
                    do {
                        relativeOutputPath = AppSingleton.CUSTOM_PHOTOS_DIRECTORY + File.separator + Logger.getUuidString(UUID.randomUUID());
                        i++;
                    } while (i < 10 && new File(App.absolutePathFromRelative(relativeOutputPath)).exists());

                    // move or copy file
                    File oldFile = new File(absolutePhotoUrl);
                    File newFile = new File(App.absolutePathFromRelative(relativeOutputPath));
                    if (!oldFile.renameTo(newFile)) {
                        // rename failed --> maybe on 2 different partitions
                        // fallback to a copy.
                        try (FileInputStream fileInputStream = new FileInputStream(oldFile); FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                            byte[] buffer = new byte[262_144];
                            int c;
                            while ((c = fileInputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, c);
                            }
                        }

                        //noinspection ResultOfMethodCallIgnored
                        oldFile.delete();
                    }
                    discussion.photoUrl = relativeOutputPath;
                } catch (Exception e) {
                    e.printStackTrace();
                    discussion.photoUrl = null;
                }
            }
        }

        db.discussionDao().updateTitleAndPhotoUrl(discussionId, discussion.title, discussion.photoUrl);

        ShortcutActivity.updateShortcut(discussion);
    }
}
