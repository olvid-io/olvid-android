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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.ExportScope
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes
import io.olvid.messenger.settings.history_transfer.TransferMethod


fun NavGraphBuilder.exportScopeScreen(
    onChooseExportScope: (ExportScope) -> Unit,
    transferMethod: MutableState<TransferMethod>,
) {
    composable(
        HistoryTransferRoutes.EXPORT_SCOPE_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lightGrey))
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (transferMethod.value == TransferMethod.ZIP)
                        R.string.history_transfer_export_scope_title_export
                    else
                        R.string.history_transfer_export_scope_title_transfer
                ),
                style = OlvidTypography.h1.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))


            // MESSAGES ONLY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        onChooseExportScope.invoke(ExportScope.MESSAGES_ONLY)
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
                        .width(40.dp)
                        .height(32.dp),
                    painter = painterResource(R.drawable.ic_bubbles),
                    tint = colorResource(R.color.olvid_gradient_light),
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
                            if (transferMethod.value == TransferMethod.ZIP)
                            R.string.history_transfer_scope_messages_export_title
                            else
                                R.string.history_transfer_scope_messages_transfer_title
                        ),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (transferMethod.value == TransferMethod.ZIP)
                                R.string.history_transfer_scope_messages_export_message
                            else
                                R.string.history_transfer_scope_messages_transfer_message
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
            Spacer(Modifier.height(16.dp))

            // EVERYTHING
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        onChooseExportScope.invoke(ExportScope.EVERYTHING)
                    }
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        modifier = Modifier
                            .width(40.dp)
                            .height(32.dp),
                        painter = painterResource(R.drawable.ic_bubbles),
                        tint = colorResource(R.color.olvid_gradient_light),
                        contentDescription = null
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp),
                        painter = painterResource(R.drawable.ic_attach_file),
                        tint = colorResource(R.color.greyTint),
                        contentDescription = null
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    Text(
                        text = stringResource(
                            if (transferMethod.value == TransferMethod.ZIP)
                                R.string.history_transfer_scope_everything_export_title
                            else
                                R.string.history_transfer_scope_everything_transfer_title
                        ),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (transferMethod.value == TransferMethod.ZIP)
                                R.string.history_transfer_scope_everything_export_message
                            else
                                R.string.history_transfer_scope_everything_transfer_message
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
private fun ExportScopeScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.EXPORT_SCOPE_SCREEN,
    ) {
        exportScopeScreen(
            onChooseExportScope = {},
            transferMethod = mutableStateOf(TransferMethod.ZIP),
        )
    }
}