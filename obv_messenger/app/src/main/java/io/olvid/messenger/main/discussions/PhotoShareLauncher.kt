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

package io.olvid.messenger.main.discussions

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.CameraCapture
import io.olvid.messenger.databases.tasks.sortedByDisplayName
import io.olvid.messenger.share.ShareActivity

/**
 * "Send a photo" entry point of the Discussions tab: take a picture or pick images, then hand
 * them to [ShareActivity] so the user picks the destination discussions there.
 * Must be instantiated during activity construction so the result launchers are registered in time.
 */
class PhotoShareLauncher(private val activity: AppCompatActivity) {

    private var pendingCaptureUri: Uri? = null

    private val cameraPermissionLauncher =
        activity.registerForActivityResult(RequestPermission()) { granted ->
            if (granted) takePicture()
        }

    private val takePictureLauncher =
        activity.registerForActivityResult(StartActivityForResult()) { result ->
            val uri = pendingCaptureUri ?: return@registerForActivityResult
            pendingCaptureUri = null
            if (result.resultCode == Activity.RESULT_OK) {
                openShareSheet(listOf(uri), "image/jpeg", null)
            }
        }

    private val pickImagesLauncher =
        activity.registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val clipData = data.clipData
            val uris = if (clipData != null) {
                (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
            } else {
                listOfNotNull(data.data)
            }
            if (uris.isNotEmpty()) {
                openShareSheet(uris.sortedByDisplayName(), data.type ?: "image/*", clipData)
            }
        }

    fun showMenu(anchor: View) {
        PopupMenu(activity, anchor).apply {
            inflate(R.menu.popup_main_photo)
            setForceShowIcon(true)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.popup_action_take_picture -> onTakePicture()
                    R.id.popup_action_choose_image -> onChooseImage()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }.show()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(PENDING_CAPTURE_URI_KEY, pendingCaptureUri)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        pendingCaptureUri = savedInstanceState?.let {
            BundleCompat.getParcelable(it, PENDING_CAPTURE_URI_KEY, Uri::class.java)
        }
    }

    private fun onTakePicture() {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT)
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            takePicture()
        }
    }

    private fun takePicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(activity.packageManager) == null) return
        val (_, uri) = CameraCapture.createOutputFile(activity, "jpg") ?: return
        pendingCaptureUri = uri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        App.startActivityForResult(activity, takePictureLauncher, intent)
    }

    private fun onChooseImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        App.startActivityForResult(activity, pickImagesLauncher, intent)
    }

    private fun openShareSheet(uris: List<Uri>, type: String, clipData: ClipData?) {
        val intent = Intent(activity, ShareActivity::class.java)
        if (uris.size == 1) {
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            intent.action = Intent.ACTION_SEND_MULTIPLE
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = type
        intent.clipData = clipData
        activity.startActivity(intent)
    }

    companion object {
        private const val PENDING_CAPTURE_URI_KEY = "photo_share_pending_capture_uri"
    }
}
