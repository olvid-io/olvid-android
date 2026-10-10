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

package io.olvid.messenger.designsystem.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.colorResource
import io.olvid.messenger.R

/**
 * Makes a Material ripple the default [LocalIndication] for everything in [content].
 *
 * The app declares no theme-level indication, so a bare `Modifier.clickable { }`
 * shows no ripple by default — each call site otherwise has to pass
 * `indication = ripple()` explicitly. Wrapping a screen root in this gives every
 * `clickable`/`selectable`/`toggleable` below it a ripple for free.
 *
 * The ripple color is fixed to `almostBlack` (dark in light mode, light in dark
 * mode) rather than following `LocalContentColor`: most screens apply no Material
 * theme, so the content color defaults to black and a default ripple would be
 * invisible on dark surfaces. Material3 components keep their own ripple.
 *
 * Call sites keep full control: passing an explicit `indication` (including
 * `indication = null` for tap-to-dismiss surfaces that should not ripple) still
 * wins over this default.
 */
@Composable
fun ProvideOlvidRipple(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides ripple(color = colorResource(R.color.almostBlack)),
        content = content,
    )
}
