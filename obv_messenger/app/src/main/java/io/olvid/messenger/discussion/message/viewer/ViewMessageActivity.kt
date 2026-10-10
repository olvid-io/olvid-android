/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.discussion.message.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureUriHandler
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.designsystem.theme.ProvideOlvidRipple
import io.olvid.messenger.lock_screen.LockableActivity

class ViewMessageActivity : LockableActivity() {

    companion object {
        const val MESSAGE_ID_INTENT_EXTRA: String = "message_id"

        fun open(context: Context, messageId: Long) {
            context.startActivity(
                Intent(context, ViewMessageActivity::class.java)
                    .putExtra(MESSAGE_ID_INTENT_EXTRA, messageId)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)
        val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1L)

        setContent {
            ProvideOlvidRipple {
                CompositionLocalProvider(LocalUriHandler provides SecureUriHandler(this)) {
                    val message by remember {
                        AppDatabase.getInstance().messageDao().getLive(messageId)
                    }.observeAsState()
                    message?.let {
                        ViewMessageScreen(
                            message = it,
                            onBackPressed = { finish() }
                        )
                    }
                }
            }
        }
    }
}
