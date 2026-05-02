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
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
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
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes


fun NavGraphBuilder.welcomeScreen(
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    composable(
        HistoryTransferRoutes.WELCOME_SCREEN,
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
            HistoryTransferHeader()

            Spacer(Modifier.height(24.dp))

            // IMPORT
            Text(
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
                text = stringResource(R.string.label_import_your_chat_history),
                style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium, color = colorResource(R.color.almostBlack)),
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.explanation_import_your_chat_history).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Start,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorResource(R.color.mediumGrey)
                )

                TextButton(
                    modifier = Modifier.heightIn(min = 56.dp),
                    onClick = onImport,
                    shape = RectangleShape,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light)
                    )
                ) {
                    Icon(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        modifier = Modifier.weight(weight = 1f, fill = true),
                        text = stringResource(R.string.button_label_import),
                        style = OlvidTypography.body1,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(R.drawable.pref_widget_chevron_right),
                        contentDescription = null
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // EXPORT
            Text(
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
                text = stringResource(R.string.label_export_your_chat_history),
                style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium, color = colorResource(R.color.almostBlack)),
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.explanation_export_your_chat_history).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Start,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorResource(R.color.mediumGrey)
                )

                TextButton(
                    modifier = Modifier.heightIn(min = 56.dp),
                    onClick = onExport,
                    shape = RectangleShape,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light)
                    )
                ) {
                    Icon(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(R.drawable.ic_export),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        modifier = Modifier.weight(weight = 1f, fill = true),
                        text = stringResource(R.string.button_label_export),
                        style = OlvidTypography.body1,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(R.drawable.pref_widget_chevron_right),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTransferHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(R.color.almostWhite))
            .padding(all = 16.dp),
        verticalArrangement = spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier
                .size(80.dp)
                .background(colorResource(R.color.golden), CircleShape)
                .padding(12.dp),
            painter = painterResource(R.drawable.ic_transfer),
            contentDescription = null,
            tint = colorResource(R.color.almostWhite),
        )
        Text(
            text = stringResource(R.string.history_transfer_title),
            style = OlvidTypography.h1.copy(
                fontWeight = FontWeight.Medium
            ),
            color = colorResource(R.color.almostBlack),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.explanation_history_transfer).formatMarkdownToAnnotatedString(),
            style = OlvidTypography.body1,
            color = colorResource(R.color.almostBlack),
            textAlign = TextAlign.Center,
        )
    }
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun WelcomeScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.WELCOME_SCREEN,
    ) {
        welcomeScreen({}, {})
    }
}