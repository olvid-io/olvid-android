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

package io.olvid.messenger.databases.tasks;

import java.io.File;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;

public class RemoveDiscussionBackgroundImageTask implements Runnable {
    private final long discussionId;

    public RemoveDiscussionBackgroundImageTask(long discussionId) {
        this.discussionId = discussionId;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization == null) {
            return;
        }

        if (discussionCustomization.backgroundImageUrl != null) {
            String pathToRemove = App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl);
            discussionCustomization.backgroundImageUrl = null;
            db.discussionCustomizationDao().update(discussionCustomization);
            File fileToRemove = new File(pathToRemove);
            //noinspection ResultOfMethodCallIgnored
            fileToRemove.delete();
        }
    }
}
