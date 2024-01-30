/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;

@Dao
public interface ContactGroupJoinDao {
    @Insert
    void insert(ContactGroupJoin contactGroupJoin);

    @Delete
    void delete(ContactGroupJoin contactGroupJoin);


    @Query("SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS CGjoin " +
            " ON contact." + Contact.BYTES_CONTACT_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND contact." + Contact.BYTES_OWNED_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND CGjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :groupOwnerAndUid")
    List<Contact> getGroupContactsSync(final byte[] bytesOwnedIdentity, final byte[] groupOwnerAndUid);

    @Query("SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS CGjoin " +
            " ON contact." + Contact.BYTES_CONTACT_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND contact." + Contact.BYTES_OWNED_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND CGjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :groupOwnerAndUid " +
            " ORDER BY contact." + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getGroupContacts(final byte[] bytesOwnedIdentity, final byte[] groupOwnerAndUid);

    @Query("SELECT * FROM (SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS CGjoin " +
            " ON contact." + Contact.BYTES_CONTACT_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND contact." + Contact.BYTES_OWNED_IDENTITY + " = CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " WHERE CGjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND CGjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :groupOwnerAndUid " +
            " AND contact." + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0 " +
            " UNION SELECT contact.* FROM " + Contact.TABLE_NAME + " AS contact " +
            " WHERE contact." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND contact." + Contact.BYTES_CONTACT_IDENTITY + " IN ( :bytesContactIdentities )" +
            " AND contact." + Contact.ESTABLISHED_CHANNEL_COUNT + " > 0) " +
            " ORDER BY " + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<Contact>> getGroupContactsAndMore(final byte[] bytesOwnedIdentity, final byte[] groupOwnerAndUid, final List<byte[]> bytesContactIdentities);

    @Query("SELECT contact.*, CDJoin." + ContactGroupJoin.TIMESTAMP + " AS timestamp FROM " + Contact.TABLE_NAME + " AS contact " +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS CDjoin " +
            " ON contact." + Contact.BYTES_CONTACT_IDENTITY + " = CDjoin." + ContactGroupJoin.BYTES_CONTACT_IDENTITY +
            " AND contact." + Contact.BYTES_OWNED_IDENTITY + " = CDjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY +
            " INNER JOIN " + Group.TABLE_NAME + " AS groop " +
            " ON CDjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = groop." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND CDjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = groop." + Group.BYTES_OWNED_IDENTITY +
            " WHERE CDjoin." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :groupOwnerAndUid " +
            " AND CDjoin." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity" +
            " ORDER BY (groop." + Group.BYTES_GROUP_OWNER_IDENTITY + " = contact." + Contact.BYTES_CONTACT_IDENTITY + ") DESC, " +
            " contact." + Contact.SORT_DISPLAY_NAME + " ASC")
    LiveData<List<ContactAndTimestamp>> getGroupContactsWithTimestamp(final byte[] bytesOwnedIdentity, final byte[] groupOwnerAndUid);

    @Query("SELECT * FROM " + ContactGroupJoin.TABLE_NAME + " WHERE " +
            ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid AND " +
            ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity AND " +
            ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    ContactGroupJoin get(byte[] bytesGroupOwnerAndUid, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT COUNT(*) FROM " + ContactGroupJoin.TABLE_NAME +
            " WHERE " + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    int countContactGroups(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT disc.id FROM " + Discussion.TABLE_NAME + " AS disc " +
            " INNER JOIN " + Group.TABLE_NAME + " AS g " +
            " ON disc." + Discussion.BYTES_OWNED_IDENTITY + " = g." + Group.BYTES_OWNED_IDENTITY +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = " + Discussion.TYPE_GROUP +
            " AND disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = g." + Group.BYTES_GROUP_OWNER_AND_UID +
            " INNER JOIN " + ContactGroupJoin.TABLE_NAME + " AS cgj " +
            " ON cgj." + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = g." + Group.BYTES_GROUP_OWNER_AND_UID +
            " AND cgj." + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = g." + Group.BYTES_OWNED_IDENTITY +
            " WHERE g." + Group.BYTES_GROUP_OWNER_IDENTITY + " IS NULL " +
            " AND cgj." + Contact.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND cgj." + Contact.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    List<Long> getAllOwnedGroupDiscussionIdsWithSpecificContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    @Query("SELECT EXISTS (SELECT 1 FROM " + ContactGroupJoin.TABLE_NAME +
            " WHERE " + ContactGroupJoin.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
            " AND " + ContactGroupJoin.BYTES_CONTACT_IDENTITY + " = :bytesContactIdentity " +
            " AND " + ContactGroupJoin.BYTES_GROUP_OWNER_AND_UID + " = :bytesGroupOwnerAndUid)")
    boolean isGroupMember(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, byte[] bytesGroupOwnerAndUid);

    class ContactAndTimestamp {
        @Embedded
        public Contact contact;
        public long timestamp;
    }
}
