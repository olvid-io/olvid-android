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

package io.olvid.messenger.databases.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.MessageMetadata;

@Dao
public interface MessageMetadataDao {
    @Insert
    long insert(MessageMetadata messageMetadata);

    @Query("SELECT EXISTS(SELECT * FROM " + MessageMetadata.TABLE_NAME +
            " WHERE " + MessageMetadata.MESSAGE_ID + " = :messageId " +
            " AND " + MessageMetadata.KIND + " = :kind)")
    boolean messageMetadataExists(long messageId, int kind);

    @Query("SELECT * FROM " + MessageMetadata.TABLE_NAME +
            " WHERE " + MessageMetadata.MESSAGE_ID + " = :messageId " +
            " AND " + MessageMetadata.KIND + " = :kind")
    MessageMetadata getByKind(long messageId, int kind);

    @Query("SELECT * FROM " + MessageMetadata.TABLE_NAME +
            " WHERE " + MessageMetadata.MESSAGE_ID + " = :messageId " +
            " ORDER BY " + MessageMetadata.TIMESTAMP + " ASC")
    LiveData<List<MessageMetadata>> getAllForMessage(long messageId);
}
