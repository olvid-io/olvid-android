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

package io.olvid.messenger.share

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.lock_screen.LockScreenOrNotActivity
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel

class ShareActivity : LockScreenOrNotActivity() {

    private val viewModel: ShareViewModel by viewModels()
    private val linkPreviewViewModel: LinkPreviewViewModel by viewModels()

    override fun notLockedOnCreate() {
        val intent = intent
        if (intent?.action == null) {
            intentFail()
            return
        }

        val (sharedText, sharedUris) = parseIntent(intent) ?: run {
            intentFail()
            return
        }

        // Direct-route via system share-target shortcut: skip the picker entirely and replay the
        // legacy behaviour — write a draft into the targeted discussion and open it. Validate the
        // target exists first; if it's a stale shortcut, fall through to the picker so the user
        // doesn't lose their attachments to a silent dismissal.
        val shortcutId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        val shortcutDiscussionId = shortcutId
            ?.takeIf { it.startsWith(DiscussionActivity.SHORTCUT_PREFIX) }
            ?.removePrefix(DiscussionActivity.SHORTCUT_PREFIX)
            ?.toLongOrNull()
        if (shortcutDiscussionId != null) {
            App.runThread {
                val discussion =
                    AppDatabase.getInstance().discussionDao().getByIdWithGroupMembersNamesIfWritable(shortcutDiscussionId)
                runOnUiThread {
                    if (discussion != null) {
                        viewModel.replaceSelectedDiscussions(listOf(FilteredDiscussionListViewModel.SearchableDiscussion(discussion)))
                    }
                    showPicker(sharedText, sharedUris)
                }
            }
            return
        }

        showPicker(sharedText, sharedUris)
    }

    private fun showPicker(sharedText: String?, sharedUris: List<Uri>) {
        viewModel.initFromIntent(contentResolver, sharedText, sharedUris, resolveTypeHint(intent))

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

        setContent {
            // Provide the Material ripple through LocalIndication once, here at the content root,
            // so every bare Modifier.clickable / combinedClickable below (the whole share UI and
            // its bottom sheets, which inherit this composition) gets a ripple without each call
            // site passing indication = ripple(). This screen is not wrapped in a MaterialTheme,
            // and the foundation default LocalIndication draws no ripple.
            CompositionLocalProvider(LocalIndication provides remember { ripple() }) {
                ShareSheetScreen(
                    viewModel = viewModel,
                    linkPreviewViewModel = linkPreviewViewModel,
                    onClose = { finish() },
                )
            }
        }
    }

    private fun parseIntent(intent: Intent): Pair<String?, List<Uri>>? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedUri: Uri? =
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedUri != null) {
                    // Android allows ACTION_SEND to carry both EXTRA_STREAM and EXTRA_TEXT (e.g. a
                    // photo with a caption / source URL); preserve the text instead of dropping it.
                    (text ?: "") to filterUris(listOf(sharedUri))
                } else {
                    (text ?: return null) to emptyList()
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = IntentCompat
                    .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { filterUris(it) }
                    ?: return null
                (intent.getStringExtra(Intent.EXTRA_TEXT) ?: "") to uris
            }

            else -> null
        }
    }

    private fun filterUris(uris: List<Uri>): List<Uri> =
        uris.filter { it.scheme == "content" }

    /**
     * Resolve the best mime hint for the shared URI(s). `intent.type` is the Sharesheet's overall
     * routing hint and reflects what the sender considers the primary content — for an image with
     * a caption that's often `text/plain`, which is useless (or misleading) for the URI. The
     * sender's `ClipData.ClipDescription` typically carries the per-item mime that the Sharesheet
     * itself uses to render its rich preview, so prefer it when available and it describes a
     * non-text, non-wildcard concrete type.
     */
    private fun resolveTypeHint(intent: Intent): String? {
        intent.clipData?.description?.let { description ->
            for (i in 0 until description.mimeTypeCount) {
                val mime = description.getMimeType(i)
                if (!mime.isNullOrBlank()
                    && mime.contains('/')
                    && !mime.endsWith("/*")
                    && !mime.startsWith("text/")
                ) {
                    return mime
                }
            }
        }
        return intent.type
    }

    private fun intentFail() {
        App.toast(R.string.toast_message_sharing_failed, Toast.LENGTH_SHORT)
        finish()
    }
}

