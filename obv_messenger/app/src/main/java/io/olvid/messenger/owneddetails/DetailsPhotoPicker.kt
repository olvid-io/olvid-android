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

package io.olvid.messenger.owneddetails

import android.Manifest.permission
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Camera + gallery photo picking, cropping the chosen image via [SelectDetailsPhotoActivity]. */
class DetailsPhotoPicker internal constructor(
    val takePhoto: () -> Unit,
    val chooseImage: () -> Unit,
    val chooseFromOlvid: (bytesOwnedIdentity: ByteArray) -> Unit,
)

/**
 * Shared photo picker used by every "edit details" surface (owned identity edit/creation, contact &
 * group rename). Creates a stable FileProvider capture URI, wires the gallery/camera/permission
 * launchers, delegates cropping to [SelectDetailsPhotoActivity], and reports the cropped absolute path
 * via [onCropped]. Camera capture only proceeds on a successful result (cancelling does nothing).
 */
@Composable
fun rememberDetailsPhotoPicker(onCropped: (absolutePhotoUrl: String) -> Unit): DetailsPhotoPicker {
    val context = LocalContext.current
    val photoUri = rememberSaveable {
        val photoDir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
        val photoFile = File(
            photoDir,
            SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(Date()) + ".jpg"
        )
        photoDir.mkdirs()
        FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
            photoFile
        )
    }
    val cropLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            result.data?.getStringExtra(SelectDetailsPhotoActivity.CROPPED_JPEG_RETURN_INTENT_EXTRA)
                ?.let(onCropped)
        }
    }
    val getContent = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (StringUtils.validateUri(uri)) {
            cropLauncher.launch(Intent(null, uri, context, SelectDetailsPhotoActivity::class.java))
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            cropLauncher.launch(Intent(null, photoUri, context, SelectDetailsPhotoActivity::class.java))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(photoUri)
        } else {
            App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT)
        }
    }
    val olvidImagePickerLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            if (StringUtils.validateUri(uri)) {
                cropLauncher.launch(Intent(null, uri, context, SelectDetailsPhotoActivity::class.java))
            }
        }
    }

    return remember {
        DetailsPhotoPicker(
            takePhoto = {
                if (ContextCompat.checkSelfPermission(context, permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(photoUri)
                } else {
                    permissionLauncher.launch(permission.CAMERA)
                }
            },
            chooseImage = { getContent.launch("image/*") },
            chooseFromOlvid = { bytesOwnedIdentity ->
                olvidImagePickerLauncher.launch(Intent(context, ImagePickerActivity::class.java).apply {
                    putExtra(ImagePickerActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity)
                })
            }
        )
    }
}
