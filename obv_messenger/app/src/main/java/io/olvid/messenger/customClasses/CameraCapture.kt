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

package io.olvid.messenger.customClasses

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CameraCapture {
    /**
     * Creates an empty file in the camera cache folder and returns it with the FileProvider Uri
     * to hand to the system camera through MediaStore.EXTRA_OUTPUT.
     */
    fun createOutputFile(context: Context, extension: String): Pair<File, Uri>? {
        val dir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
        val file = File(
            dir,
            SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(Date()) + "." + extension
        )
        return runCatching {
            dir.mkdirs()
            if (!file.createNewFile()) return null
            file to FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                file
            )
        }.onFailure {
            Logger.w("Error creating camera capture file $file")
        }.getOrNull()
    }
}
