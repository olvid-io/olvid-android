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

package io.olvid.messenger.designsystem

import android.content.Context
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.olvid.engine.Logger


@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        top = this.calculateTopPadding() + other.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
    )
}

@Composable
operator fun PaddingValues.minus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = (this.calculateStartPadding(layoutDirection) - other.calculateStartPadding(layoutDirection)).coerceAtLeast(0.dp),
        top = (this.calculateTopPadding() - other.calculateTopPadding()).coerceAtLeast(0.dp),
        end = (this.calculateEndPadding(layoutDirection) - other.calculateEndPadding(layoutDirection)).coerceAtLeast(0.dp),
        bottom = (this.calculateBottomPadding() - other.calculateBottomPadding()).coerceAtLeast(0.dp)
    )
}

@Composable
fun Modifier.cutoutHorizontalPadding() = then(
    Modifier.windowInsetsPadding(
        WindowInsets.displayCutout.only(
            WindowInsetsSides.Horizontal))
)

@Composable
fun Modifier.systemBarsHorizontalPadding() = then(
    Modifier.windowInsetsPadding(WindowInsets.Companion.systemBars.only(WindowInsetsSides.Companion.Horizontal))
)

@Composable
fun constantSp(value: Int): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun constantSp(value: Float): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun scaledDp(value: Int): Dp = with(LocalDensity.current) { (value * fontScale).dp }


fun Context.showDialog(content: @Composable (onDismiss: () -> Unit) -> Unit) {
    runCatching {
        val activity = getActivity(this)
        if (activity is ComponentActivity) {
            val decorView = activity.window.decorView as ViewGroup
            val view = ComposeView(activity)

            if (decorView.findViewTreeLifecycleOwner() == null) {
                decorView.setViewTreeLifecycleOwner(activity)
            }
            if (decorView.findViewTreeViewModelStoreOwner() == null) {
                decorView.setViewTreeViewModelStoreOwner(activity)
            }
            if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
                decorView.setViewTreeSavedStateRegistryOwner(activity)
            }

            view.setViewTreeLifecycleOwner(activity)
            view.setViewTreeViewModelStoreOwner(activity)
            view.setViewTreeSavedStateRegistryOwner(activity)

            view.setContent {
                val showDialog = remember { mutableStateOf(true) }
                if (showDialog.value) {
                    content {
                        showDialog.value = false
                        decorView.removeView(view)
                    }
                }
            }
            decorView.addView(view)
        }
    }.onFailure {
        Logger.x(it)
    }
}

private fun getActivity(context: Context?): Context? {
    if (context == null) return null
    if (context is android.app.Activity) return context
    if (context is android.content.ContextWrapper) return getActivity(context.baseContext)
    return null
}
