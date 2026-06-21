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
package io.olvid.messenger.storage_manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.lock_screen.LockScreenOrNotActivity
import io.olvid.messenger.main.MainActivity

class StorageManagerActivity : LockScreenOrNotActivity() {
    private val viewModel: StorageManagerViewModel by viewModels()
    private var audioAttachmentServiceBinding: AudioAttachmentServiceBinding? = null

    private val saveSelectedAttachmentsLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { activityResult: ActivityResult? ->
        if (activityResult?.data == null || activityResult.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }
        val folderUri = activityResult.data!!.data
        if (StringUtils.validateUri(folderUri) && viewModel.selectedFyles.isNotEmpty()) {
            val selectedAttachments = ArrayList(viewModel.selectedFyles)
            viewModel.clearSelectedFyles()
            App.runThread(
                SaveMultipleAttachmentsTask(this, folderUri, selectedAttachments)
            )
        }
    }

    private val ensureMainActivityInBackstack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            startActivity(Intent(this@StorageManagerActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, ensureMainActivityInBackstack)
    }

    override fun notLockedOnCreate() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                ContextCompat.getColor(this, R.color.blackOverlay)
            )
        )

        // if the task is root (no MainActivity running) enable the callback to recreate the MainActivity
        ensureMainActivityInBackstack.isEnabled = isTaskRoot

        runCatching {
            audioAttachmentServiceBinding = AudioAttachmentServiceBinding(this)
        }.onFailure {
            finish()
            return
        }

        val binding = audioAttachmentServiceBinding!!

        setContent {
            val destination by viewModel.bucketDestinationLiveData.observeAsState()

            BackHandler(enabled = destination != null) {
                viewModel.clearSelectedFyles()
                viewModel.bucketDestinationLiveData.value = null
            }

            if (destination == null) {
                StorageManagerScreen(
                    viewModel = viewModel,
                    onBackPressed = { onBackPressedDispatcher.onBackPressed() }
                )
            } else {
                StorageBucketScreen(
                    viewModel = viewModel,
                    destination = destination!!,
                    audioAttachmentServiceBinding = binding,
                    onGoToMessage = {
                        viewModel.selectedFyles.takeIf { it.size == 1 }?.first()?.let { fyleAndStatus ->
                            App.runThread {
                                val message = AppDatabase.getInstance().messageDao().get(fyleAndStatus.fyleMessageJoinWithStatus.messageId)
                                message?.let {
                                    val intent = Intent(this, DiscussionActivity::class.java)
                                    intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, message.discussionId)
                                    intent.putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, message.id)
                                    startActivity(intent)
                                }
                            }
                        }
                    },
                    onNavigateUp = {
                        viewModel.clearSelectedFyles()
                        viewModel.bucketDestinationLiveData.value = null
                    },
                    onSaveSelected = {
                        App.prepareForStartActivityForResult(this)
                        saveSelectedAttachmentsLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioAttachmentServiceBinding?.release()
    }
}
