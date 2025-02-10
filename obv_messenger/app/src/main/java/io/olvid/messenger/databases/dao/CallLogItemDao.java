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

package io.olvid.messenger.databases.dao;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Relation;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group;

@Dao
public interface CallLogItemDao {
    @Insert
    long insert(@NonNull CallLogItem callLogItem);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(@NonNull CallLogItemContactJoin... callLogItemContactJoins);

    @Delete
    void delete(@NonNull CallLogItem callLogItem);

    @Update
    void update(@NonNull CallLogItem callLogItem);

    String PREFIX_CALL_LOG_COLUMNS = "log.id AS log_id, " +
            " log." + CallLogItem.BYTES_OWNED_IDENTITY + " AS log_" + CallLogItem.BYTES_OWNED_IDENTITY + ", " +
            " log." + CallLogItem.BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER + " AS log_" + CallLogItem.BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER + ", " +
            " log." + CallLogItem.TIMESTAMP + " AS log_" + CallLogItem.TIMESTAMP + ", " +
            " log." + CallLogItem.CALL_TYPE + " AS log_" + CallLogItem.CALL_TYPE + ", " +
            " log." + CallLogItem.CALL_STATUS + " AS log_" + CallLogItem.CALL_STATUS + ", " +
            " log." + CallLogItem.DURATION + " AS log_" + CallLogItem.DURATION;


    @Transaction
    @Query("SELECT contact.*, " +
            GroupDao.PREFIX_GROUP_COLUMNS + ", " +
            PREFIX_CALL_LOG_COLUMNS + " FROM " + CallLogItem.TABLE_NAME + " AS log " +
            " INNER JOIN (" +
            " SELECT MIN(" + CallLogItemContactJoin.BYTES_CONTACT_IDENTITY + ") AS contactBytes, " + CallLogItemContactJoin.CALL_LOG_ITEM_ID +
            " FROM " + CallLogItemContactJoin.TABLE_NAME +
            " GROUP BY " + CallLogItemContactJoin.CALL_LOG_ITEM_ID +
            " ) AS clicj ON clicj." + CallLogItemContactJoin.CALL_LOG_ITEM_ID + " = log.id " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON log." + CallLogItem.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " AND clicj.contactBytes " + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " LEFT JOIN " + Group.TABLE_NAME + " AS groop " +
            " ON groop." + Group.BYTES_GROUP_OWNER_AND_UID + " = log." + CallLogItem.BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER +
            " AND groop." + Group.BYTES_OWNED_IDENTITY + " = log." + CallLogItem.BYTES_OWNED_IDENTITY +
            " WHERE log." + CallLogItem.BYTES_OWNED_IDENTITY + " = :ownedIdentityBytes " +
            " ORDER BY log." + CallLogItem.TIMESTAMP + " DESC")
    LiveData<List<CallLogItemAndContacts>> getWithContactForOwnedIdentity(@NonNull byte[] ownedIdentityBytes);

    @Query("DELETE FROM " + CallLogItem.TABLE_NAME +
            " WHERE " + CallLogItem.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void deleteAll(@NonNull byte[] bytesOwnedIdentity);

    @Transaction
    @Query("SELECT contact.*, " +
            GroupDao.PREFIX_GROUP_COLUMNS + ", " +
            PREFIX_CALL_LOG_COLUMNS + " FROM " + CallLogItem.TABLE_NAME + " AS log " +
            " INNER JOIN (" +
            " SELECT MIN(" + CallLogItemContactJoin.BYTES_CONTACT_IDENTITY + ") AS contactBytes, " + CallLogItemContactJoin.CALL_LOG_ITEM_ID +
            " FROM " + CallLogItemContactJoin.TABLE_NAME +
            " GROUP BY " + CallLogItemContactJoin.CALL_LOG_ITEM_ID +
            " ) AS clicj ON clicj." + CallLogItemContactJoin.CALL_LOG_ITEM_ID + " = log.id " +
            " INNER JOIN " + Contact.TABLE_NAME + " AS contact " +
            " ON log." + CallLogItem.BYTES_OWNED_IDENTITY + " = contact." + Contact.BYTES_OWNED_IDENTITY +
            " AND clicj.contactBytes " + " = contact." + Contact.BYTES_CONTACT_IDENTITY +
            " LEFT JOIN " + Group.TABLE_NAME + " AS groop " +
            " ON groop." + Group.BYTES_GROUP_OWNER_AND_UID + " = log." + CallLogItem.BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER +
            " AND groop." + Group.BYTES_OWNED_IDENTITY + " = log." + CallLogItem.BYTES_OWNED_IDENTITY +
            " WHERE log.id = :callLogItemId ")
    CallLogItemAndContacts get(long callLogItemId);


    class CallLogItemAndContacts {
        @Embedded(prefix = "log_")
        public CallLogItem callLogItem;

        @Relation(
                entity = CallLogItemContactJoin.class,
                parentColumn = "log_id",
                entityColumn = CallLogItemContactJoin.CALL_LOG_ITEM_ID
        )
        public List<CallLogItemContactJoin> contacts;

        @Embedded
        public Contact oneContact;

        @Embedded(prefix = "group_")
        public Group group;
    }
}
