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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import java.io.FileInputStream

/** ACTION_CREATE_DOCUMENT contract that hands the picked Uri back together with the attachment to save. */
class GetAttachmentSaveUri : ActivityResultContract<FyleAndStatus, Pair<Uri, FyleAndStatus>?>() {
    private var fyleAndStatus: FyleAndStatus? = null

    override fun createIntent(context: Context, input: FyleAndStatus): Intent {
        fyleAndStatus = input
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.fyleMessageJoinWithStatus.nonNullMimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fyleMessageJoinWithStatus.fileName)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<Uri, FyleAndStatus>? {
        val uri = intent?.data
        val target = fyleAndStatus
        return if (resultCode == Activity.RESULT_OK && uri != null && target != null) uri to target else null
    }
}

/** Copies the attachment's file to [uri] off the main thread and toasts the outcome. */
fun saveAttachmentTo(context: Context, uri: Uri, fyleAndStatus: FyleAndStatus) {
    if (!StringUtils.validateUri(uri)) return
    App.runThread {
        runCatching {
            context.contentResolver.openOutputStream(uri).use { os ->
                os ?: throw Exception("Unable to write to provided Uri")
                FileInputStream(App.absolutePathFromRelative(fyleAndStatus.fyle.filePath)).use { fis ->
                    fis.copyTo(os, 262144)
                }
            }
        }.onSuccess {
            App.toast(R.string.toast_message_attachment_saved, Toast.LENGTH_SHORT)
        }.onFailure {
            App.toast(R.string.toast_message_failed_to_save_attachment, Toast.LENGTH_SHORT)
        }
    }
}

/**
 * A "save this attachment" action backed by a document picker registered in the current
 * composition, shared by every host of [GalleryScreen].
 */
@Composable
fun rememberSaveAttachmentAction(): (FyleAndStatus) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(GetAttachmentSaveUri()) { result ->
        result?.let { (uri, fyleAndStatus) -> saveAttachmentTo(context, uri, fyleAndStatus) }
    }
    return remember(launcher, activity) {
        { fyleAndStatus ->
            (activity as? FragmentActivity)?.let { App.prepareForStartActivityForResult(it) }
            launcher.launch(fyleAndStatus)
        }
    }
}
