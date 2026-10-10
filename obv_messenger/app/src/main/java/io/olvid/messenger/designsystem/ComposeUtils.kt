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
import android.os.Build
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.olvid.engine.Logger


@Composable
fun Modifier.cutoutHorizontalPadding() = then(
    Modifier.windowInsetsPadding(
        WindowInsets.displayCutout.only(
            WindowInsetsSides.Horizontal))
)

@Composable
fun Modifier.systemBarsHorizontalPadding() = then(
    Modifier.windowInsetsPadding(WindowInsets.stableSystemBars.only(WindowInsetsSides.Horizontal))
)

/**
 * System bars and display cutout, as if the bars were always visible. Use it instead of
 * [WindowInsets.systemBars] / [WindowInsets.safeDrawing] on screens that host a full-screen overlay
 * hiding the bars (the in-discussion media viewer): the content underneath then keeps its layout
 * while the bars come and go, which the viewer's exit morph relies on.
 *
 * Before API 30 the bar animations are emulated by androidx, and the insets it hands Compose while
 * (and after) a bar hides report no "ignoring visibility" values any more; the root view's insets
 * stay right, so they are used instead, re-read whenever the visible bars move.
 */
val WindowInsets.Companion.stableSystemBars: WindowInsets
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return systemBarsIgnoringVisibility.union(displayCutout)
        }
        val view = LocalView.current
        val density = LocalDensity.current
        val bars = systemBars
        val rootBars = remember(
            view,
            bars.getLeft(density, LayoutDirection.Ltr),
            bars.getTop(density),
            bars.getRight(density, LayoutDirection.Ltr),
            bars.getBottom(density),
        ) {
            ViewCompat.getRootWindowInsets(view)
                ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                ?.let { WindowInsets(it.left, it.top, it.right, it.bottom) }
        }
        return (rootBars ?: systemBarsIgnoringVisibility).union(displayCutout)
    }

@Composable
fun constantSp(value: Int): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun constantSp(value: Float): TextUnit = with(LocalDensity.current) { (value / fontScale).sp }
@Composable
fun scaledDp(value: Int): Dp = with(LocalDensity.current) { (value * fontScale).dp }

/**
 * Slower zoom rate for secondary discussion elements (timestamps, reactions, sender names…)
 * so they grow half as fast as the message body when pinch-zooming.
 */
fun Float.dampened(): Float = (this + 1) / 2

fun TextStyle.scaled(scale: Float): TextStyle = if (scale == 1f) this else copy(
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)

/**
 * Like [scaled], but for styles whose [fontSize] is unspecified (e.g. the ambient
 * `LocalTextStyle`, which the app never assigns a size). Multiplying an unspecified size is a
 * no-op, so we pin Compose's own 14.sp fallback before scaling — at scale 1f the style is
 * returned untouched, so the rendering is identical to before.
 */
fun TextStyle.scaledFromDefault(scale: Float): TextStyle =
    if (scale == 1f) this else copy(
        fontSize = fontSize.takeOrElse { 14.sp } * scale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )


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
