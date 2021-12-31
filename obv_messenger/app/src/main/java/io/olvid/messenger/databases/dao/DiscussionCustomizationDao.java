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

package io.olvid.messenger.databases.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.DiscussionCustomization;

@Dao
public interface DiscussionCustomizationDao {
    @Insert
    long insert(DiscussionCustomization discussionCustomization);

    @Update
    void update(DiscussionCustomization discussionCustomization);

    @Query("SELECT * FROM " + DiscussionCustomization.TABLE_NAME + " WHERE " + DiscussionCustomization.DISCUSSION_ID + " = :discussionId;")
    DiscussionCustomization get(long discussionId);

    @Query("SELECT * FROM " + DiscussionCustomization.TABLE_NAME + " WHERE " + DiscussionCustomization.DISCUSSION_ID + " = :discussionId;")
    LiveData<DiscussionCustomization> getLiveData(long discussionId);

    @Query("SELECT " + DiscussionCustomization.BACKGROUND_IMAGE_URL + " FROM " + DiscussionCustomization.TABLE_NAME +
            " WHERE " + DiscussionCustomization.BACKGROUND_IMAGE_URL + " IS NOT NULL")
    List<String> getAllBackgroundImageFilePaths();
}
