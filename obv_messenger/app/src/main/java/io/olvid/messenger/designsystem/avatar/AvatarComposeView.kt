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
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme

/**
 * Hosts the Compose [Avatar] inside a legacy View hierarchy — the bridge for XML/Fragment
 * screens not yet migrated to Compose, replacing the legacy `customClasses.InitialView` View.
 *
 * Callable from Java (`AvatarComposeViewKt.setAvatar(composeView, target)`).
 *
 * RecyclerView-safe: each bind disposes any existing composition before [setContent], so the
 * recycled row composes **fresh** for the new target. Without that, `setContent` reuses the
 * existing composition and its `remember`/async-photo state carries the previous row's avatar over
 * — which showed wrong/stale icons while scrolling.
 */
@JvmOverloads
fun ComposeView.setAvatar(target: AvatarTarget?, showBadges: Boolean = true) {
    pinComposeOwners()
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    disposeComposition()
    setContent {
        AppCompatTheme {
            Avatar(
                target = target,
                modifier = Modifier.fillMaxSize(),
                showBadges = showBadges,
            )
        }
    }
}

/**
 * Pins the host [LifecycleOwner] (and saved-state / view-model owners) onto this very view so the
 * owner survives the view detaching into a RecyclerView recycle pool. It also handles the
 * `PopupWindow`/`Dialog` case: a popup decor view has no [LifecycleOwner], and Compose resolves the
 * window recomposer from that decor (not from this child) and crashes with "ViewTreeLifecycleOwner
 * not found"; when none exists up the tree we give the view an explicit parent composition context
 * tied to the host lifecycle, bypassing the window-recomposer lookup entirely.
 */
private fun ComposeView.pinComposeOwners() {
    val fromTree = findViewTreeLifecycleOwner()
    val owner = fromTree ?: context.findLifecycleOwner() ?: return
    setViewTreeLifecycleOwner(owner)
    (owner as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
    (owner as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
    if (fromTree == null) {
        setParentCompositionContext(createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle))
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
