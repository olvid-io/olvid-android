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

package io.olvid.messenger.discussion.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatusTimestamped
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle.FULL
import java.util.Locale

class MediaGalleryViewModel : ViewModel() {
    private val db = AppDatabase.getInstance()
    fun getMedias(discussionId: Long) =
        db.fyleMessageJoinWithStatusDao().getGalleryMediasForDiscussion(discussionId)
            .groupByDateHeader()

    fun getAudios(discussionId: Long) =
        db.fyleMessageJoinWithStatusDao().getGalleryAudiosForDiscussion(discussionId)
            .groupByDateHeader()

    fun getDocuments(discussionId: Long) = db.fyleMessageJoinWithStatusDao()
        .getGalleryDocumentsForDiscussion(discussionId).groupByDateHeader()

    fun getLinks(discussionId: Long) =
        db.fyleMessageJoinWithStatusDao().getGalleryLinksForDiscussion(discussionId)
            .groupByDateHeader()

}

private fun LiveData<List<FyleAndStatusTimestamped>>.groupByDateHeader(): LiveData<Map<String, List<FyleAndStatusTimestamped>>> =
    map {
        it.groupBy {
            getDateHeader(it.timestamp)
        }
    }


private fun getDateHeader(timestamp: Long): String {
    val date = LocalDate.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )
    val currentYear = LocalDate.now().year
    return when (date.year) {
        currentYear -> {
            date.month.getDisplayName(FULL, Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        currentYear - 1 -> {
            date.month.getDisplayName(FULL, Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + " " + date.year.toString()
        }
        else -> {
            date.year.toString()
        }
    }
}