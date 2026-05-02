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

package io.olvid.messenger.settings.history_transfer.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.constantSp
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes


fun NavGraphBuilder.pickProfileScreen(
    onProfileSelected: (OwnedIdentity) -> Unit,
    importMode: State<Boolean>,
    ownedIdentityList: State<List<OwnedIdentity>?>,
) {
    composable(
        HistoryTransferRoutes.PICK_PROFILE_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lightGrey))
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.history_transfer_pick_profile_title),
                style = OlvidTypography.h1.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (importMode.value)
                        R.string.history_transfer_pick_profile_text_import
                    else
                        R.string.history_transfer_pick_profile_text_export
                ).formatMarkdownToAnnotatedString(),
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            ownedIdentityList.value?.takeIf { it.isNotEmpty() }?.let {
                it.forEach { ownedIdentity ->
                    val ownedDetails = remember(ownedIdentity.identityDetails) {
                        ownedIdentity.getIdentityDetails()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorResource(R.color.almostWhite))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                            ) {
                                onProfileSelected.invoke(ownedIdentity)
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Color(
                                            InitialView.getLightColor(
                                                context = context,
                                                bytes = ownedIdentity.bytesOwnedIdentity
                                            )
                                        )
                                    ),
                                text = StringUtils.getInitial(
                                    ownedIdentity.getCustomDisplayName()
                                ),
                                color = Color(
                                    InitialView.getDarkColor(
                                        context = context,
                                        bytes = ownedIdentity.bytesOwnedIdentity
                                    )
                                ),
                                textAlign = TextAlign.Center,
                                fontSize = constantSp(26),
                                fontWeight = FontWeight.Medium,
                                lineHeight = constantSp(40),
                            )

                            ownedIdentity.photoUrl?.let { photoUrl ->
                                AsyncImage(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    imageLoader = App.imageLoader,
                                    model = App.absolutePathFromRelative(photoUrl),
                                    contentDescription = null,
                                )
                            }

                            if (ownedIdentity.keycloakManaged) {
                                Image(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(4.dp, (-3).dp),
                                    painter = painterResource(R.drawable.ic_keycloak_certified),
                                    contentDescription = null,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f, true)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceAround,
                        ) {
                            Text(
                                text = ownedIdentity.customDisplayName
                                    ?: ownedDetails?.formatFirstAndLastName(
                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                        false
                                    ) ?: ownedIdentity.displayName,
                                style = OlvidTypography.body1,
                                color = colorResource(R.color.almostBlack),
                                maxLines = 1,
                            )
                            (if (ownedIdentity.customDisplayName == null)
                                ownedDetails?.formatPositionAndCompany(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY
                                )
                            else
                                ownedDetails?.formatDisplayName(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                    false
                                ) ?: ownedIdentity.displayName
                                    )?.let { displayName ->
                                    Text(
                                        text = displayName,
                                        style = OlvidTypography.body2,
                                        color = colorResource(R.color.greyTint),
                                        maxLines = 1,
                                    )
                                }
                        }

                        Icon(
                            painter = painterResource(R.drawable.pref_widget_chevron_right),
                            tint = colorResource(R.color.almostBlack),
                            contentDescription = null,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } ?: run {
                OlvidCircularProgress()
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun PickProfileScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.PICK_PROFILE_SCREEN,
    ) {
        pickProfileScreen(
            onProfileSelected = {},
            importMode = mutableStateOf(true),
            ownedIdentityList = mutableStateOf(
                listOf(
                    OwnedIdentity(
                        ByteArray(2),
                        "Lisa Martin",
                        null,
                        0,
                        0,
                        null,
                        0,
                        null,
                        true,
                        true,
                        "Lisa 💗",
                        null,
                        null,
                        false,
                        false,
                        null,
                        false,
                        true,
                        true,
                        true
                    ),

                    OwnedIdentity(
                        ByteArray(2),
                        "Marie Boulier",
                        null,
                        0,
                        0,
                        null,
                        0,
                        null,
                        false,
                        true,
                        null,
                        null,
                        null,
                        false,
                        false,
                        null,
                        false,
                        true,
                        true,
                        true
                    )
                )
            )
        )
    }
}