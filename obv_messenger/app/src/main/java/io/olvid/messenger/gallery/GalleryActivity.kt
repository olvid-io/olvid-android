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
package io.olvid.messenger.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Pair
import android.view.WindowManager.LayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.lock_screen.LockableActivity
import java.io.FileInputStream

class GalleryActivity : LockableActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    private val saveAttachmentLauncher =
        registerForActivityResult(GetAttachmentSaveUri()) { result: Pair<Uri, FyleAndStatus>? ->
            saveCallback(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        super.onCreate(savedInstanceState)

        val intent = intent
        if (intent == null) {
            finish()
            return
        }

        var initialMessageId = -1L
        var initialFyleId = -1L
        var showTextBlocks = false
        var fromStorageManager = false

        if (viewModel.getCurrentPagerPosition() == null) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val sortOrder = intent.getStringExtra(BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA)
            val ascending = intent.getBooleanExtra(ASCENDING_INTENT_EXTRA, true)
            val discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1)
            val draft = intent.getBooleanExtra(DRAFT_INTENT_EXTRA, false)
            val messageId = intent.getLongExtra(INITIAL_MESSAGE_ID_INTENT_EXTRA, -1)
            val fyleId = intent.getLongExtra(INITIAL_FYLE_ID_INTENT_EXTRA, -1)
            val sentByMe = intent.getBooleanExtra(SENT_BY_ME_INTENT_EXTRA, false)
            val minFileSize = intent.getLongExtra(MIN_FILE_SIZE_INTENT_EXTRA, -1L)
            val fromStorageManagerExtra = intent.getBooleanExtra(FROM_STORAGE_MANAGER_INTENT_EXTRA, false)
            showTextBlocks = intent.getBooleanExtra(SHOW_TEXT_BLOCKS_INTENT_EXTRA, false)
            val fromLinkPreview = intent.getBooleanExtra(LOAD_FROM_OPEN_GRAPH_INTENT_EXTRA, false)

            when {
                discussionId != -1L -> viewModel.setDiscussionId(discussionId, sortOrder, ascending, fromStorageManagerExtra)
                sentByMe && bytesOwnedIdentity != null -> viewModel.setSentByMeGallery(bytesOwnedIdentity, sortOrder, ascending)
                minFileSize != -1L && bytesOwnedIdentity != null -> viewModel.setLargeFilesGallery(bytesOwnedIdentity, minFileSize, sortOrder, ascending)
                bytesOwnedIdentity != null -> viewModel.setBytesOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
                messageId != -1L -> viewModel.setMessageId(messageId, draft)
                fromLinkPreview && openGraphToShow != null -> viewModel.setLinkPreview(openGraphToShow!!)
                else -> {
                    finish()
                    return
                }
            }

            fromStorageManager = fromStorageManagerExtra || sentByMe || minFileSize != -1L ||
                    (bytesOwnedIdentity != null && discussionId == -1L && messageId == -1L)
            initialMessageId = messageId
            initialFyleId = fyleId
        }

        val capturedInitialMessageId = initialMessageId
        val capturedInitialFyleId = initialFyleId
        val capturedShowTextBlocks = showTextBlocks
        val capturedFromStorageManager = fromStorageManager

        setContent {
            GalleryScreen(
                viewModel = viewModel,
                initialMessageId = capturedInitialMessageId,
                initialFyleId = capturedInitialFyleId,
                showTextBlocksInitially = capturedShowTextBlocks,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                onSave = { fyleAndStatus ->
                    App.prepareForStartActivityForResult(this)
                    saveAttachmentLauncher.launch(
                        Pair(fyleAndStatus.fyleMessageJoinWithStatus.fileName, fyleAndStatus)
                    )
                },
                onOpen = { fyleAndStatus ->
                    App.openFyleViewer(this, fyleAndStatus) {
                        fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                    }
                },
                onFinish = {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_down)
                },
                onFinishUp = {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_up)
                },
                applyFlagSecure = {
                    window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
                },
                onGoToDiscussion = if (capturedFromStorageManager) { discussionId, messageId ->
                    val intent = Intent(this, DiscussionActivity::class.java)
                    intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId)
                    intent.putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, messageId)
                    startActivity(intent)
                } else null
            )
        }
    }

    private fun saveCallback(result: Pair<Uri, FyleAndStatus>?) {
        result ?: return
        val uri = result.first
        val fyleAndStatus = result.second
        if (StringUtils.validateUri(uri) && fyleAndStatus != null) {
            App.runThread {
                try {
                    contentResolver.openOutputStream(uri).use { os ->
                        os ?: throw Exception("Unable to write to provided Uri")
                        FileInputStream(
                            App.absolutePathFromRelative(fyleAndStatus.fyle.filePath)
                        ).use { fis ->
                            val buffer = ByteArray(262144)
                            var c: Int
                            while (fis.read(buffer).also { c = it } != -1) {
                                os.write(buffer, 0, c)
                            }
                        }
                        App.toast(R.string.toast_message_attachment_saved, android.widget.Toast.LENGTH_SHORT)
                    }
                } catch (_: Exception) {
                    App.toast(R.string.toast_message_failed_to_save_attachment, android.widget.Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private class GetAttachmentSaveUri :
        ActivityResultContract<Pair<String, FyleAndStatus>, Pair<Uri, FyleAndStatus>?>() {
        private var fyleAndStatus: FyleAndStatus? = null

        override fun createIntent(context: Context, input: Pair<String, FyleAndStatus>): Intent {
            fyleAndStatus = input.second
            return Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(input.second.fyleMessageJoinWithStatus.nonNullMimeType)
                .putExtra(Intent.EXTRA_TITLE, input.first)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Pair<Uri, FyleAndStatus>? {
            if (intent != null && resultCode == RESULT_OK) {
                return Pair(intent.data, fyleAndStatus)
            }
            return null
        }
    }

    companion object {
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "bytes_owned_identity"
        const val BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA: String = "sort_order"
        const val ASCENDING_INTENT_EXTRA: String = "ascending"
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
        const val DRAFT_INTENT_EXTRA: String = "draft"
        const val INITIAL_MESSAGE_ID_INTENT_EXTRA: String = "initial_message_id"
        const val INITIAL_FYLE_ID_INTENT_EXTRA: String = "initial_fyle_id"
        const val SHOW_TEXT_BLOCKS_INTENT_EXTRA: String = "show_text_blocks"
        const val SENT_BY_ME_INTENT_EXTRA: String = "sent_by_me"
        const val MIN_FILE_SIZE_INTENT_EXTRA: String = "min_file_size"
        const val FROM_STORAGE_MANAGER_INTENT_EXTRA: String = "from_storage_manager"

        const val LOAD_FROM_OPEN_GRAPH_INTENT_EXTRA = "link_preview_url_intent_extra"
        var openGraphToShow: OpenGraph? = null
    }
}
