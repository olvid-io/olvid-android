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

@file:JvmName("OverlayWindows")

package io.olvid.messenger.customClasses

import android.os.Build
import android.view.Window
import io.olvid.messenger.settings.SettingsActivity

/**
 * Hides other apps' overlay windows (tapjacking protection) unless the user allowed them in the privacy settings.
 * Sensitive screens pass `sensitive = true` to hide them regardless of that setting.
 */
@JvmOverloads
fun Window.hideOverlayWindows(sensitive: Boolean = false) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setHideOverlayWindows(sensitive || !SettingsActivity.allowOverlayWindows())
    }
}
