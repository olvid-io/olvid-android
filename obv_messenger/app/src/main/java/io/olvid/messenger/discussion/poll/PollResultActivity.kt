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

import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity

class PollResultActivity : LockableActivity() {

    companion object {
        const val MESSAGE_ID_INTENT_EXTRA: String = "mess_id"
    }

    private lateinit var exportPollCsvLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1L)
        val viewModel: PollResultViewModel by viewModels {
            viewModelFactory {
                initializer {
                    PollResultViewModel(messageId)
                }
            }
        }
        exportPollCsvLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri: Uri? ->
            viewModel.exportPollCsv(uri, contentResolver)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        setContent {
            val message by viewModel.message.observeAsState()
            val pollResults by viewModel.pollResults.observeAsState()
            val pollParticipants by viewModel.pollParticipants.observeAsState()
            val poll = message?.getPoll()
            if (poll != null && pollResults != null && pollParticipants != null) {
                PollResultScreen(
                    poll = poll,
                    pollParticipants = pollParticipants.orEmpty(),
                    pollResults = pollResults.orEmpty(),
                    onBackPressed = { finish() },
                    onExportCsv = {
                        val suggestedFileName = viewModel.generateCsvDataForExport(
                            poll = poll,
                            pollVotes = pollResults.orEmpty()
                        )
                        exportPollCsvLauncher.launch(suggestedFileName)
                    })
            }
        }
    }
}


