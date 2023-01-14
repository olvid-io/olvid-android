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

package io.olvid.messenger.databases.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;

@Dao
public interface ActionShortcutConfigurationDao {
    @Insert
    void insert(ActionShortcutConfiguration callLogItem);

    @Query("DELETE FROM " + ActionShortcutConfiguration.TABLE_NAME +
            " WHERE " + ActionShortcutConfiguration.APP_WIDGET_ID + " = :appWidgetId")
    void delete(int appWidgetId);

    @Update
    void update(ActionShortcutConfiguration callLogItem);

    @Query("SELECT COUNT(*) FROM " + ActionShortcutConfiguration.TABLE_NAME)
    long countAll();

    @Query("SELECT * FROM " + ActionShortcutConfiguration.TABLE_NAME +
            " WHERE " + ActionShortcutConfiguration.APP_WIDGET_ID + " = :appWidgetId")
    ActionShortcutConfiguration getByAppWidgetId(int appWidgetId);

    @Query("SELECT * FROM " + ActionShortcutConfiguration.TABLE_NAME +
            " WHERE " + ActionShortcutConfiguration.DISCUSSION_ID + " = :discussionId")
    ActionShortcutConfiguration getByDiscussionId(long discussionId);

    @Query("SELECT * FROM " + ActionShortcutConfiguration.TABLE_NAME)
    LiveData<List<ActionShortcutConfiguration>> getAll();
}
