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

package io.olvid.messenger.main.groups

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.Group2Dao.GroupOrGroup2
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.OwnedIdentity

class GroupListViewModel : ViewModel() {
    val groups: LiveData<List<GroupOrGroup2>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().group2Dao()
                .getAllGroupOrGroup2(ownedIdentity.bytesOwnedIdentity)
        }
}

fun GroupOrGroup2.getAnnotatedName(context: Context): AnnotatedString {
    return buildAnnotatedString {
        group?.let {
            append(group.getCustomName())
        }
        group2?.let {
            if (group2.getCustomName().isNotEmpty()) {
                append(group2.getCustomName())
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(context.getString(R.string.text_unnamed_group))
                }
            }
        }
    }
}

fun GroupOrGroup2.getAnnotatedMembers(context: Context): AnnotatedString {
    return buildAnnotatedString {
        group?.let {
            if (group.groupMembersNames.isNotEmpty()) {
                append(group.groupMembersNames)
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(context.getString(R.string.text_empty_group))
                }
            }
        }
        group2?.let {
            if (group2.groupMembersNames.isNotEmpty()) {
                append(group2.groupMembersNames)
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(context.getString(R.string.text_empty_group))
                }
            }
        }
    }
}

fun GroupOrGroup2.showPublishedDetails(): Boolean {
    return (group?.newPublishedDetails ?: group2?.newPublishedDetails ?: 0) > 0
}

fun GroupOrGroup2.showPublishedDetailsNotification(): Boolean {
    return listOf(
        Group.PUBLISHED_DETAILS_NEW_UNSEEN,
        Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW
    ).contains(group?.newPublishedDetails) ||
            Group2.PUBLISHED_DETAILS_NEW_UNSEEN == group2?.newPublishedDetails
}

