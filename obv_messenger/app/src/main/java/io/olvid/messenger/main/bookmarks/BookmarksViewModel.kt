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

package io.olvid.messenger.main.bookmarks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.MessageDao.DiscussionAndMessage
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarksViewModel : ViewModel() {
    private val _selection = mutableSetOf<Long>()
    var selection by mutableStateOf<List<Message>>(emptyList())
        private set

    fun clearSelection() {
        viewModelScope.launch(Dispatchers.IO) {
            _selection.clear()
            selection = emptyList()
        }
    }

    fun enableSelection(message: Message) {
        _selection.add(message.id)
        refreshSelection()
    }

    fun toggleSelection(message: Message) {
        if (_selection.remove(message.id).not()) {
            _selection.add(message.id)
        }
        refreshSelection()
    }

    fun refreshSelection() {
        selection =
            bookmarkedMessages.value?.filter { _selection.contains(it.message.id) }.orEmpty()
                .map { it.message }
    }

    val bookmarkedMessages: LiveData<List<DiscussionAndMessage>?> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            } else {
                return@switchMap AppDatabase.getInstance().messageDao()
                    .getAllBookmarkedLiveData(ownedIdentity.bytesOwnedIdentity)
            }
        }

    var cancelableBookmarkedMessages by mutableStateOf<List<Message>>(emptyList())

    fun bookmarkMessage(vararg message: Message, bookmarked: Boolean, cancelable: Boolean) {
        clearSelection()
        if (cancelable) {
            cancelableBookmarkedMessages = cancelableBookmarkedMessages + message
        }
        viewModelScope.launch(Dispatchers.IO) {
            message.asList().onEach {
                AppDatabase.getInstance().messageDao().updateBookmarked(bookmarked, it.id)
                AppSingleton.getBytesCurrentIdentity()
                    ?.let { bytesOwnedIdentity ->
                        PropagateBookmarkedMessageChangeTask(
                            bytesOwnedIdentity,
                            it,
                            false
                        ).run()
                    }
            }
        }
    }

}