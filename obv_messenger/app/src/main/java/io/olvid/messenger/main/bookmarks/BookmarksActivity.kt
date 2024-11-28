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

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f
        setContent {
            val bookmarks by AppDatabase.getInstance().messageDao()
                .getAllBookmarkedLiveData(AppSingleton.getBytesCurrentIdentity() ?: byteArrayOf())
                .observeAsState(null)
            AppCompatTheme {
                GlobalSearchScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = colorResource(R.color.olvid_gradient_dark))
                        .statusBarsPadding()
                        .background(color = colorResource(R.color.almostWhite))
                        .navigationBarsPadding(),
                    globalSearchViewModel = globalSearchViewModel,
                    linkPreviewViewModel = linkPreviewViewModel,
                    bookmarks = bookmarks
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