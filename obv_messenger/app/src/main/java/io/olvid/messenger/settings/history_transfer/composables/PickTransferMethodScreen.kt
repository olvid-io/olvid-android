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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes
import io.olvid.messenger.settings.history_transfer.TransferMethod


fun NavGraphBuilder.pickTransferMethodScreen(
    onMethodSelected: (TransferMethod) -> Unit,
    importMode: State<Boolean>,
    selectedOwnedIdentity: State<OwnedIdentity?>,
) {
    composable(
        HistoryTransferRoutes.PICK_TRANSFER_METHOD_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        val isImport = remember {
            importMode.value
        }
        val displayName = remember {
            val details = selectedOwnedIdentity.value?.getIdentityDetails()

            return@remember selectedOwnedIdentity.value?.customDisplayName
                ?: details?.formatFirstAndLastName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                    false
                )
                ?: selectedOwnedIdentity.value?.displayName
                ?: ""
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lightGrey))
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.history_transfer_pick_method_title),
                style = OlvidTypography.h1.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (isImport)
                        R.string.history_transfer_pick_method_text_import
                    else
                        R.string.history_transfer_pick_method_text_export
                    , displayName)
                    .formatMarkdownToAnnotatedString(),
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // WEBRTC
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        onMethodSelected.invoke(TransferMethod.WEBRTC)
                    }
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorResource(R.color.olvid_gradient_light))
                        .padding(8.dp),
                    painter = painterResource(R.drawable.ic_wifi),
                    tint = colorResource(R.color.almostWhite),
                    contentDescription = null
                )

                Column(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    Text(
                        text = stringResource(R.string.history_transfer_via_wifi_title),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.history_transfer_via_wifi_message),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.pref_widget_chevron_right),
                    tint = colorResource(R.color.almostBlack),
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ZIP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        onMethodSelected.invoke(TransferMethod.ZIP)
                    }
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorResource(R.color.orange))
                        .padding(8.dp),
                    painter = painterResource(R.drawable.ic_zip),
                    tint = colorResource(R.color.almostWhite),
                    contentDescription = null
                )

                Column(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    Text(
                        text = stringResource(
                            if (isImport)
                                R.string.history_transfer_via_zip_title_import
                            else
                                R.string.history_transfer_via_zip_title_export
                        ),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (isImport)
                                R.string.history_transfer_via_zip_message_import
                            else
                                R.string.history_transfer_via_zip_message_export
                        ),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.pref_widget_chevron_right),
                    tint = colorResource(R.color.almostBlack),
                    contentDescription = null,
                )
            }

        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun PickTransferMethodScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.PICK_TRANSFER_METHOD_SCREEN,
    ) {
        pickTransferMethodScreen(
            onMethodSelected = {},
            importMode = mutableStateOf(true),
            selectedOwnedIdentity = mutableStateOf(
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
                    null,
                    false,
                    true,
                    true,
                    true
                )
            ),
        )
    }
}