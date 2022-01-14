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


import java.util.List;

import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.settings.SettingsActivity;

public class ApplyDiscussionRetentionPoliciesTask implements Runnable {
    final Long discussionId;

    public ApplyDiscussionRetentionPoliciesTask(Long discussionId) { // use null to clean all discussions
        this.discussionId = discussionId;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();

        Long defaultCount = SettingsActivity.getDefaultDiscussionRetentionCount();
        Long defaultDuration = SettingsActivity.getDefaultDiscussionRetentionDuration();

        long timestamp = System.currentTimeMillis();

        List<DiscussionDao.DiscussionAndCustomization> discussionAndCustomizations;
        if (discussionId == null) {
            discussionAndCustomizations = db.discussionDao().getAllDiscussionAndCustomizations();
        } else {
            discussionAndCustomizations = db.discussionDao().getDiscussionAndCustomization(discussionId);
        }

        for (DiscussionDao.DiscussionAndCustomization discussionAndCustomization : discussionAndCustomizations) {
            // date-based retention
            Long minTimestamp = null;
            if (discussionAndCustomization.discussionCustomization == null || discussionAndCustomization.discussionCustomization.prefDiscussionRetentionDuration == null) {
                if (defaultDuration != null) {
                    minTimestamp = timestamp - 1_000L * defaultDuration;
                }
            } else if (discussionAndCustomization.discussionCustomization.prefDiscussionRetentionDuration != 0) {
                minTimestamp = timestamp - 1_000L * discussionAndCustomization.discussionCustomization.prefDiscussionRetentionDuration;
            }

            if (minTimestamp != null) {
                List<Long> messageIds = db.messageDao().getOldDiscussionMessages(discussionAndCustomization.discussion.id, minTimestamp);
                new DeleteMessagesTask(discussionAndCustomization.discussion.bytesOwnedIdentity, messageIds, false).run();
            }

            // count-based retention
            Long maxMessages = null;
            if (discussionAndCustomization.discussionCustomization == null || discussionAndCustomization.discussionCustomization.prefDiscussionRetentionCount == null) {
                if (defaultCount != null) {
                    maxMessages = defaultCount;
                }
            } else if (discussionAndCustomization.discussionCustomization.prefDiscussionRetentionCount != 0) {
                maxMessages = discussionAndCustomization.discussionCustomization.prefDiscussionRetentionCount;
            }

            if (maxMessages != null) {
                int count = db.messageDao().countExpirableMessagesInDiscussion(discussionAndCustomization.discussion.id);
                if ((long) count > maxMessages) {
                    int toDelete = (int) ((long) count - maxMessages);
                    List<Long> messageIds = db.messageDao().getExcessiveDiscussionMessages(discussionAndCustomization.discussion.id, toDelete);
                    new DeleteMessagesTask(discussionAndCustomization.discussion.bytesOwnedIdentity, messageIds, false).run();
                }
            }
        }
    }
}
