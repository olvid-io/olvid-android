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

package io.olvid.messenger.contact

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.map
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.databases.entity.Contact

object Routes {
    const val CONTACT_DETAILS = "contact_details"
    const val FULL_GROUPS_LIST = "full_groups_list"
    const val TRUST_ORIGINS = "trust_origins"
    const val CONTACT_INTRODUCTION = "contact_introduction"
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.contactDetails(
    contactDetailsViewModel: ContactDetailsViewModel,
    imageClick: (String?) -> Unit = {},
    onFullGroupsList: () -> Unit = {},
    onIntroduce: () -> Unit = {},
    onTrustOrigins: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
) {
    composable(
        Routes.CONTACT_DETAILS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        ContactDetailsScreen(
            contactDetailsViewModel = contactDetailsViewModel,
            imageClick = imageClick,
            onIntroduce = onIntroduce,
            onFullGroupsList = onFullGroupsList,
            onTrustOrigins = onTrustOrigins,
            sharedTransitionScope = sharedTransitionScope
        )
    }
}

fun NavGraphBuilder.fullGroupsList(
    contactDetailsViewModel: ContactDetailsViewModel,
) {
    composable(
        Routes.FULL_GROUPS_LIST,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        FullGroupsListScreen(
            contactDetailsViewModel = contactDetailsViewModel,
        )
    }
}

fun NavGraphBuilder.trustOrigins(
    contactDetailsViewModel: ContactDetailsViewModel,
    onBack: () -> Unit,
) {
    composable(
        Routes.TRUST_ORIGINS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        TrustOriginsScreen(
            contactDetailsViewModel = contactDetailsViewModel,
            onBack = onBack
        )
    }
}

fun NavGraphBuilder.contactIntroduction(
    contactDetailsViewModel: ContactDetailsViewModel,
    onDone: () -> Unit,
) {
    composable(
        Routes.CONTACT_INTRODUCTION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        val contact: Contact? by contactDetailsViewModel.contactAndInvitation?.map { it?.contact }?.observeAsState() ?: remember { mutableStateOf(null) }
        contact?.let {
            ContactIntroductionScreen(
                bytesOwnedIdentity = it.bytesOwnedIdentity,
                bytesContactIdentity = it.bytesContactIdentity,
                displayName = it.getCustomDisplayName(),
                onDone = onDone
            )
        }
    }
}
