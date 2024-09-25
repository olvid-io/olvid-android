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

package io.olvid.messenger.main.bookmarks

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.search.GlobalSearchScreen
import io.olvid.messenger.main.search.GlobalSearchViewModel

class BookmarksActivity : LockableActivity() {

    private val globalSearchViewModel by viewModels<GlobalSearchViewModel>()
    private val linkPreviewViewModel by viewModels<LinkPreviewViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContent {
            val bookmarks by AppDatabase.getInstance().messageDao()
                .getAllBookmarkedLiveData(AppSingleton.getBytesCurrentIdentity() ?: byteArrayOf())
                .observeAsState(null)
            AppCompatTheme {
                GlobalSearchScreen(
                    globalSearchViewModel = globalSearchViewModel,
                    linkPreviewViewModel = linkPreviewViewModel,
                    bookmarks
                )
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}