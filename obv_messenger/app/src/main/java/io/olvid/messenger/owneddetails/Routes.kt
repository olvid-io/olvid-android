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

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.databases.entity.OwnedDevice

object Routes {
    const val OWNED_IDENTITY_DETAILS = "owned_identity_details"
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.ownedIdentityDetails(
    ownedDetailsViewModel: OwnedIdentityDetailsViewModel,
    imageClick: (String?) -> Unit = {},
    onReactivate: () -> Unit = {},
    onPublish: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onAddDevice: () -> Unit = {},
    onTrustDevice: (OwnedDevice) -> Unit = {},
    onRenameDevice: (OwnedDevice) -> Unit = {},
    onRemoveDeviceExpiration: (OwnedDevice) -> Unit = {},
    onRefreshDeviceList: (OwnedDevice) -> Unit = {},
    onRecreateDeviceChannel: (OwnedDevice) -> Unit = {},
    onRemoveDevice: (OwnedDevice) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
) {
    composable(
        Routes.OWNED_IDENTITY_DETAILS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        OwnedIdentityDetailsScreen(
            ownedDetailsViewModel = ownedDetailsViewModel,
            imageClick = imageClick,
            onReactivate = onReactivate,
            onPublish = onPublish,
            onDiscard = onDiscard,
            onAddDevice = onAddDevice,
            onTrustDevice = onTrustDevice,
            onRenameDevice = onRenameDevice,
            onRemoveDeviceExpiration = onRemoveDeviceExpiration,
            onRefreshDeviceList = onRefreshDeviceList,
            onRecreateDeviceChannel = onRecreateDeviceChannel,
            onRemoveDevice = onRemoveDevice,
            sharedTransitionScope = sharedTransitionScope
        )
    }
}
