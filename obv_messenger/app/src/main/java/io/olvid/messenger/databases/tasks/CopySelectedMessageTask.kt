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
package io.olvid.messenger.databases.tasks

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import java.lang.ref.WeakReference


class CopySelectedMessageTask(activity: FragmentActivity?, private val selectedMessageId: Long, private val copyAttachments: Boolean) : Runnable {
    private val activityWeakReference: WeakReference<FragmentActivity?> = WeakReference<FragmentActivity?>(activity)

    override fun run() {
        AppDatabase.getInstance().messageDao().get(selectedMessageId)?.let { message ->
            val clipData: ClipData?

            if (!copyAttachments) {
                clipData = message.poll?.let {
                    ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), it.question)
                } ?: message.contentBody.takeUnless { it.isNullOrEmpty() }?.let {
                    ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), it)
                }
            } else {
                val fyleAndStatuses = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getCompleteFylesAndStatusForMessageSyncWithoutLinkPreview(selectedMessageId)
                if (!fyleAndStatuses.isEmpty()) {
                    val clipItems: MutableList<ClipData.Item?> = ArrayList<ClipData.Item?>()
                    val mimeTypes: MutableList<String?> = ArrayList<String?>()
                    message.contentBody.takeUnless { it.isNullOrEmpty() }?.let {
                        clipItems.add(ClipData.Item(it))
                        mimeTypes.add("text/plain")
                    }
                    for (fyleAndStatus in fyleAndStatuses) {
                        clipItems.add(ClipData.Item(fyleAndStatus.getContentUriForExternalSharing()))
                        mimeTypes.add(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())
                    }
                    val clipDescription = ClipDescription(App.getContext().getString(R.string.label_message_copied_from_olvid), mimeTypes.toTypedArray<String?>())
                    clipData = ClipData(clipDescription, clipItems.get(0))
                    for (item in clipItems.subList(1, clipItems.size)) {
                        clipData.addItem(item)
                    }
                } else {
                    clipData = message.contentBody.takeUnless { it.isNullOrEmpty() }?.let {
                        ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), it)
                    }
                }
            }


            if (clipData != null) {
                activityWeakReference.get()?.let { activity ->
                    activity.runOnUiThread(Runnable {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clipData)
                            if (copyAttachments) {
                                App.toast(R.string.toast_message_complete_message_copied_to_clipboard, Toast.LENGTH_SHORT)
                            } else {
                                App.toast(R.string.toast_message_message_text_copied_to_clipboard, Toast.LENGTH_SHORT)
                            }
                        }
                    })
                }
            }
        }
    }
}
