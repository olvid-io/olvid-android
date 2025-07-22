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

package io.olvid.messenger.discussion.poll

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity

class PollCreationActivity : LockableActivity() {

    companion object {
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1)
        val pollViewModel by viewModels<PollCreationViewModel>()
        pollViewModel.discussionId = discussionId
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        setContent {
            PollCreationScreen(pollViewModel = pollViewModel, onBackPressed = { finish() })
        }
    }
}