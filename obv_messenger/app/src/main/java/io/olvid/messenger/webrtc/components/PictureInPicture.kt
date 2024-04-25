/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.webrtc.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import io.olvid.messenger.R

@Suppress("DEPRECATION")
internal fun enterPictureInPicture(context: Context) {
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(pipAspect).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(false)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setTitle(context.getString(R.string.app_name))
                        setSeamlessResizeEnabled(true)
                    }
                }
            context.findActivity()?.enterPictureInPictureMode(params.build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.findActivity()?.enterPictureInPictureMode()
        }
    }
}

internal var pipAspect = Rational(9, 16)

internal fun setPictureInPictureAspectRatio(context: Context, width: Int, height: Int) {
    pipAspect = Rational(width, height)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.findActivity()?.setPictureInPictureParams(PictureInPictureParams.Builder()
            .setAspectRatio(pipAspect)
            .build())
    }
}

internal val Context.isInPictureInPictureMode: Boolean
    get() {
        val currentActivity = findActivity()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            currentActivity?.isInPictureInPictureMode == true
        } else {
            false
        }
    }

internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
