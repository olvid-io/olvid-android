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
package io.olvid.messenger.designsystem.avatar

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration

fun hueFromBytes(bytes: ByteArray?): Int {
    if (bytes == null) {
        return 0
    }
    var result = 1
    for (element in bytes) {
        result = 31 * result + element
    }
    return (result and 0xff) * 360 / 256
}

fun avatarLightColor(bytes: ByteArray, darkMode: Boolean): Color {
    val hue = hueFromBytes(bytes).toFloat()
    return if (bytes.isEmpty()) {
        if (darkMode) Color.hsl(hue, 0f, 0.5f) else Color.hsl(hue, 0f, 0.9f)
    } else {
        if (darkMode) Color.hsl(hue, 0.6f, 0.7f) else Color.hsl(hue, 0.8f, 0.9f)
    }
}

fun avatarDarkColor(bytes: ByteArray, darkMode: Boolean): Color {
    val hue = hueFromBytes(bytes).toFloat()
    return if (bytes.isEmpty()) {
        if (darkMode) Color.hsl(hue, 0f, 0.4f) else Color.hsl(hue, 0f, 0.7f)
    } else {
        if (darkMode) Color.hsl(hue, 0.5f, 0.5f) else Color.hsl(hue, 0.7f, 0.7f)
    }
}

fun avatarTextColor(bytes: ByteArray, darkMode: Boolean, hue: Int? = null): Color {
    val computedHue = (hue ?: hueFromBytes(bytes)).toFloat()
    return if (bytes.isEmpty()) {
        Color.hsl(computedHue, 0f, 0.4f)
    } else {
        if (darkMode) Color.hsl(computedHue, 0.5f, 0.4f) else Color.hsl(computedHue, 0.7f, 0.4f)
    }
}

// Match the legacy night detection (resources uiMode, not just the system setting)
// so avatar colors stay consistent with the rest of the UI.
@get:Composable
@get:ReadOnlyComposable
val isDarkTheme: Boolean
    get() = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@Composable
@ReadOnlyComposable
fun avatarLightColor(bytes: ByteArray): Color = avatarLightColor(bytes, isDarkTheme)

@Composable
@ReadOnlyComposable
fun avatarDarkColor(bytes: ByteArray): Color = avatarDarkColor(bytes, isDarkTheme)

@Composable
@ReadOnlyComposable
fun avatarTextColor(bytes: ByteArray, hue: Int? = null): Color =
    avatarTextColor(bytes, isDarkTheme, hue)

// ARGB-int variants for non-Compose / Java callers. Night mode is read from the context resources.
fun avatarLightColorArgb(context: Context, bytes: ByteArray): Int =
    avatarLightColor(bytes, context.isNightMode()).toArgb()

fun avatarDarkColorArgb(context: Context, bytes: ByteArray): Int =
    avatarDarkColor(bytes, context.isNightMode()).toArgb()

fun avatarTextColorArgb(context: Context, bytes: ByteArray, hue: Int?): Int =
    avatarTextColor(bytes, context.isNightMode(), hue).toArgb()
