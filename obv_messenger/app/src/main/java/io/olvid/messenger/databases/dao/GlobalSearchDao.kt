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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RoomWarnings
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndOrigin
import io.olvid.messenger.databases.dao.MessageDao.DiscussionAndMessage
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.discussion.linkpreview.OpenGraph

@Dao
interface GlobalSearchDao {

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query(
        "SELECT m.id, NULL AS fyleId, m." + Message.TIMESTAMP + ", m." + Message.SORT_INDEX + "  FROM " + Message.TABLE_NAME + " AS m " +
                " JOIN " + Message.FTS_TABLE_NAME + " ON m.id = " + Message.FTS_TABLE_NAME + ".rowid" +
                " WHERE m." + Message.MESSAGE_TYPE + " <= " + Message.TYPE_OUTBOUND_MESSAGE +
                " AND m." + Message.DISCUSSION_ID + " = :discussionId" +
                " AND " + Message.FTS_TABLE_NAME + " MATCH :query" +
                " UNION " +
                " SELECT m.id, FMjoin.fyle_id AS fyleId, m." + Message.TIMESTAMP + ", m." + Message.SORT_INDEX + " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
                " INNER JOIN " + Message.TABLE_NAME + " AS m " +
                " ON m.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
                " AND m." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
                " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
                " ON disc.id = m." + Message.DISCUSSION_ID +
                " AND disc.id = :discussionId " +
                " JOIN " + FyleMessageJoinWithStatus.FTS_TABLE_NAME +
                " ON FMJoin.rowid = " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + ".rowid " +
                " WHERE " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " MATCH :query" +
                " ORDER BY m." + Message.SORT_INDEX + " DESC"
    )
    fun discussionSearch(discussionId: Long, query: String): List<MessageIdAndTimestamp>

    data class MessageIdAndTimestamp(val id: Long, val fyleId: Long, val timestamp: Long)

//    @Query(
//        "SELECT mess.id " +
//                " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
//                " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
//                " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
//                " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
//                " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
//                " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
//                " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
//                " ON disc.id = mess." + Message.DISCUSSION_ID +
//                " AND disc.id = :discussionId " +
//                " JOIN " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " ON FMJoin.rowid = " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + ".rowid " +
//                " WHERE " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " MATCH :query ORDER BY mess.timestamp DESC"
//    )
//    fun discussionAttachmentsSearch(
//        discussionId: Long,
//        query: String,
//    ): List<Long>


    @Query(
        "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ",m.* FROM " + Message.TABLE_NAME + " AS m " +
                " INNER JOIN " + Discussion.TABLE_NAME + " AS disc ON disc.id = m." + Message.DISCUSSION_ID +
                " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " JOIN " + Message.FTS_TABLE_NAME + " ON m.id = " + Message.FTS_TABLE_NAME + ".rowid" +
                " WHERE m." + Message.MESSAGE_TYPE + " <= " + Message.TYPE_OUTBOUND_MESSAGE +
                " AND " + Message.FTS_TABLE_NAME + " MATCH :query ORDER BY m." + Message.TIMESTAMP + " DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun messageGlobalSearch(
        bytesOwnedIdentity: ByteArray,
        query: String,
        limit: Int,
        offset: Int
    ): List<DiscussionAndMessage>

    @Query(
        "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
                " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
                " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
                " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
                " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " != '" + OpenGraph.MIME_TYPE + "' " +
                " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
                " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
                " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
                " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
                " ON disc.id = mess." + Message.DISCUSSION_ID +
                " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " JOIN " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " ON FMJoin.rowid = " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + ".rowid " +
                " WHERE " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " MATCH :filter ORDER BY mess.timestamp DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun attachmentsGlobalSearch(
        bytesOwnedIdentity: ByteArray,
        filter: String,
        limit: Int,
        offset: Int
    ): List<FyleAndOrigin>

    @Query(
        "SELECT " + DiscussionDao.PREFIX_DISCUSSION_COLUMNS + ", " + MessageDao.PREFIX_MESSAGE_COLUMNS + ", fyle.*, FMjoin.* " +
                " FROM " + FyleMessageJoinWithStatus.TABLE_NAME + " AS FMjoin " +
                " INNER JOIN " + Fyle.TABLE_NAME + " AS fyle " +
                " ON fyle.id = FMjoin." + FyleMessageJoinWithStatus.FYLE_ID +
                " AND FMjoin." + FyleMessageJoinWithStatus.MIME_TYPE + " = '" + OpenGraph.MIME_TYPE + "' " +
                " INNER JOIN " + Message.TABLE_NAME + " AS mess " +
                " ON mess.id = FMjoin." + FyleMessageJoinWithStatus.MESSAGE_ID +
                " AND mess." + Message.MESSAGE_TYPE + " != " + Message.TYPE_INBOUND_EPHEMERAL_MESSAGE +
                " INNER JOIN " + Discussion.TABLE_NAME + " AS disc " +
                " ON disc.id = mess." + Message.DISCUSSION_ID +
                " AND disc." + Discussion.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity " +
                " JOIN " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " ON FMJoin.rowid = " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + ".rowid " +
                " WHERE " + FyleMessageJoinWithStatus.FTS_TABLE_NAME + " MATCH :filter ORDER BY mess.timestamp DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun linksGlobalSearch(
        bytesOwnedIdentity: ByteArray,
        filter: String,
        limit: Int,
        offset: Int
    ): List<FyleAndOrigin>
}
