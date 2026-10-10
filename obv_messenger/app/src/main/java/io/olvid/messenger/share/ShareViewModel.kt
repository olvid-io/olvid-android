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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask
import io.olvid.messenger.databases.tasks.ReplaceDiscussionDraftTask
import io.olvid.messenger.databases.tasks.SetDraftJsonExpirationTask
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareViewModel : ViewModel() {

    /** Plain text payload from EXTRA_TEXT; empty when no text was shared. */
    var sharedText: String = ""
        private set

    /** content:// URIs from the share intent. */
    var sharedUris: List<Uri> by mutableStateOf(emptyList())
        private set

    /** Resolved attachment metadata for [sharedUris]. */
    var attachments: List<ShareAttachment> by mutableStateOf(emptyList())
        private set

    /** Body the user is composing in the share sheet; seeded from [sharedText] when no files are attached. */
    var draftText: String by mutableStateOf("")

    /** Targets the user has picked. Order-preserved for chip rendering. */
    var selectedDiscussions: List<SearchableDiscussion> by mutableStateOf(emptyList())
        private set

    /** True once a "send" has been kicked off; used to disable the send button. */
    var sending: Boolean by mutableStateOf(false)
        private set

    /**
     * URL the user explicitly closed the preview card for. While the draft still contains this
     * URL the share sheet skips calling LinkPreviewViewModel.findLinkPreview; typing a different
     * URL clears the flag so a fresh preview can show up.
     */
    var dismissedLinkPreviewUrl: String? by mutableStateOf(null)
        private set

    private var initialized: Boolean = false

    fun initFromIntent(
        resolver: ContentResolver,
        text: String?,
        uris: List<Uri>,
        intentType: String? = null
    ) {
        // ShareActivity lacks `android:configChanges`, so rotation/locale change re-runs
        // notLockedOnCreate → initFromIntent on the same ViewModel. Skip re-init or the user's
        // typed draftText and any attachments they removed would be silently reverted.
        if (initialized) return
        initialized = true
        sharedText = text.orEmpty()
        sharedUris = uris
        // Pre-fill the compose field with whatever caption the sender attached (text-only share,
        // or image+caption from Photos/browsers). User can edit/clear before sending.
        draftText = sharedText
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                uris.map { resolveShareAttachment(resolver, it, intentType) }
            }
            // drop any attachment the user removed while metadata was being resolved
            attachments = resolved.filter { it.uri in sharedUris }
        }
    }

    fun markLinkPreviewDismissed(url: String?) {
        dismissedLinkPreviewUrl = url
    }

    fun clearLinkPreviewDismissal() {
        dismissedLinkPreviewUrl = null
    }

    fun replaceSelectedDiscussions(discussions: List<SearchableDiscussion>) {
        selectedDiscussions = discussions.distinctBy { it.discussionId }
    }

    fun removeAttachment(uri: Uri) {
        attachments = attachments.filterNot { it.uri == uri }
        sharedUris = sharedUris.filterNot { it == uri }
        if (sharedUris.isEmpty() && draftText.isBlank()) {
            draftText = sharedText
        }
    }

    fun canSend(): Boolean =
        !sending &&
                selectedDiscussions.isNotEmpty() &&
                (draftText.isNotBlank() || attachments.isNotEmpty())

    /** Fan-out the share to every selected discussion via the existing draft+post pipeline. */
    fun send(context: Context, openGraph: OpenGraph?, ephemeralSettings: JsonExpiration?, onDone: () -> Unit) {
        // Synchronously gate against double-tap: canSend's `!sending` check would otherwise race
        // with Compose recomposition (two clicks in the same frame both see sending=false).
        synchronized(this) {
            if (!canSend()) return
            sending = true
        }
        val targets = selectedDiscussions.toList()
        val body = draftText
        val uris = sharedUris.toList()
        App.runThread {
            try {
                var successCount = 0
                for (target in targets) {
                    val ok = runCatching {
                        ReplaceDiscussionDraftTask(target.discussionId, body, uris).run()
                        ephemeralSettings?.let {
                            SetDraftJsonExpirationTask(target.discussionId, it).run()
                        }
                        PostMessageInDiscussionTask(
                            /* body = */ body,
                            /* discussionId = */ target.discussionId,
                            /* showToast = */ false,
                            /* openGraph = */ openGraph,
                            /* mentions = */ null,
                            /* poll = */ null,
                        ).run()
                    }.isSuccess
                    if (ok) successCount++
                }
                if (targets.size > 1 && successCount > 0) {
                    App.toast(
                        context.resources.getQuantityString(
                            R.plurals.toast_message_shared_to_recipients,
                            successCount,
                            successCount,
                        ),
                        Toast.LENGTH_SHORT,
                        Gravity.BOTTOM,
                    )
                } else if (successCount == 0) {
                    App.toast(
                        R.string.toast_message_sharing_failed,
                        Toast.LENGTH_SHORT,
                        Gravity.BOTTOM
                    )
                }
                // onDone usually calls Activity.finish() — must run on the UI thread.
                Handler(Looper.getMainLooper()).post { onDone() }
            } finally {
                sending = false
            }
        }
    }
}
