/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.databases.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.olvid.messenger.databases.entity.TextBlock


@Dao
interface FyleMessageTextBlockDao {
    @Insert
    fun insert(textBlock: TextBlock): Long

    @Insert
    fun insertAll(vararg textBlocks: TextBlock)

    @Delete
    fun delete(textBlock: TextBlock)

    @Query("DELETE FROM ${TextBlock.TABLE_NAME} WHERE ${TextBlock.MESSAGE_ID} = :messageId")
    fun deleteAllForMessage(messageId: Long)

    @Query("DELETE FROM ${TextBlock.TABLE_NAME} WHERE ${TextBlock.FYLE_ID} = :fyleId")
    fun deleteAllForFyle(fyleId: Long)

    @Query("DELETE FROM ${TextBlock.TABLE_NAME} WHERE ${TextBlock.MESSAGE_ID} = :messageId AND ${TextBlock.FYLE_ID} = :fyleId")
    fun deleteAllForMessageAndFyle(messageId: Long, fyleId: Long)

    @Query("SELECT * FROM ${TextBlock.TABLE_NAME} WHERE ${TextBlock.MESSAGE_ID} = :messageId AND ${TextBlock.FYLE_ID} = :fyleId")
    fun getAll(messageId: Long, fyleId: Long): List<TextBlock>

    @Query("SELECT * FROM ${TextBlock.TABLE_NAME} WHERE ${TextBlock.MESSAGE_ID} = :messageId AND ${TextBlock.FYLE_ID} = :fyleId")
    fun getAllLive(messageId: Long, fyleId: Long): LiveData<List<TextBlock>?>
}