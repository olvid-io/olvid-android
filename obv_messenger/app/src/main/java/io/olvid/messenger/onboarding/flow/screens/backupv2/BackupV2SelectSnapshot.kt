/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.onboarding.flow.screens.backupv2

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore
import io.olvid.engine.engine.types.sync.ObvSyncSnapshot
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ProfileBackupSnapshot
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.onboarding.flow.BackupSnapshotsFetchState
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.settings.composables.getPlatformResource


fun NavGraphBuilder.backupV2SelectSnapshot(
    backupSnapshotsFetchState: MutableState<BackupSnapshotsFetchState>,
    profileSnapshots: MutableState<List<ProfileBackupSnapshot>>,
    onRetry: () -> Unit,
    onRestore: (ProfileBackupSnapshot) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_SELECT_SNAPSHOT,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        val context = LocalContext.current
        var selectedProfile : ProfileBackupSnapshot? by remember { mutableStateOf(null) }

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(id = R.string.onboarding_backup_v2_select_snapshot_title),
            ),
            footer = {
                Button(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = colorResource(R.color.alwaysWhite),
                        containerColor = colorResource(R.color.olvid_gradient_light),
                    ),
                    onClick = {
                        selectedProfile?.let {
                            onRestore.invoke(it)
                        }
                    },
                    enabled = selectedProfile != null
                ) {
                    Text(
                        text = stringResource(R.string.button_label_restore),
                    )
                }
            },
            onBack = onBack,
            onClose = onClose,
        ) {
            if (backupSnapshotsFetchState.value == BackupSnapshotsFetchState.FETCHING) {
                Spacer(Modifier.height(64.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp,
                    color = colorResource(id = R.color.olvid_gradient_light)
                )
            } else if (backupSnapshotsFetchState.value == BackupSnapshotsFetchState.ERROR
                || (backupSnapshotsFetchState.value == BackupSnapshotsFetchState.TRUNCATED
                        && profileSnapshots.value.isEmpty())) {

                Text(
                    modifier = Modifier.widthIn(max = 400.dp),
                    text = AnnotatedString(stringResource(R.string.explanation_unable_to_fetch_snapshots)).formatMarkdown(),
                    color = colorResource(R.color.red),
                    style = OlvidTypography.body2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colorResource(R.color.olvid_gradient_light)),
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorResource(id = R.color.blueOrWhite),
                    )
                ) {
                    Text(
                        text = stringResource(R.string.button_label_retry),
                    )
                }
            } else  {
                if (profileSnapshots.value.isEmpty()) {
                    Text(
                        modifier = Modifier.widthIn(max = 400.dp),
                        text = AnnotatedString(stringResource(R.string.explanation_no_snapshots_for_profile)).formatMarkdown(),
                        color = colorResource(R.color.greyTint),
                        style = OlvidTypography.body2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, colorResource(R.color.olvid_gradient_light)),
                        onClick = onBack,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorResource(id = R.color.blueOrWhite),
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_choose_another_profile),
                        )
                    }
                } else {
                    if (backupSnapshotsFetchState.value == BackupSnapshotsFetchState.TRUNCATED) {
                        Text(
                            modifier = Modifier.widthIn(max = 400.dp),
                            text = stringResource(R.string.label_backups_truncated),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.greyTint),
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            elevation = null,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, colorResource(R.color.olvid_gradient_light)),
                            onClick = onRetry,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorResource(id = R.color.blueOrWhite),
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.button_label_retry),
                            )
                        }
                    } else {
                        Text(
                            text = pluralStringResource(R.plurals.label_backups_found, profileSnapshots.value.size, profileSnapshots.value.size),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.greyTint),
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    profileSnapshots.value.forEachIndexed { index, profileBackupSnapshot ->
                        key(profileBackupSnapshot.threadId) {
                            Button(
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                elevation = null,
                                border = if (selectedProfile == profileBackupSnapshot)
                                    BorderStroke(1.dp, colorResource(R.color.greyTint))
                                else
                                    null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(R.color.lightGrey),
                                    contentColor = colorResource(R.color.almostBlack),
                                ),
                                onClick = {
                                    selectedProfile = profileBackupSnapshot
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Image(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colorResource(R.color.almostWhite))
                                            .padding(all = 10.dp),
                                        painter = painterResource(profileBackupSnapshot.platform.getPlatformResource()),
                                        colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                                        contentDescription = null,
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f, true)
                                            .padding(start = 16.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.SpaceAround,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = spacedBy(8.dp)
                                        ) {
                                            Text(
                                                modifier = Modifier.weight(1f, true),
                                                text = StringUtils.getDateString(context, profileBackupSnapshot.timestamp),
                                                style = OlvidTypography.body1,
                                                color = colorResource(R.color.almostBlack),
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1,
                                            )
                                            if (index == 0) {
                                                Text(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(colorResource(R.color.greyTint))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                    text = stringResource(R.string.label_recommended),
                                                    fontWeight = FontWeight.Normal,
                                                    lineHeight = constantSp(10),
                                                    fontSize = constantSp(10),
                                                    color = colorResource(R.color.alwaysWhite),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        profileBackupSnapshot.deviceName?.let {
                                            Text(
                                                text = stringResource(R.string.label_backup_from, it),
                                                style = OlvidTypography.body2,
                                                color = colorResource(R.color.greyTint),
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1,
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.label_backup_groups_and_contacts, pluralStringResource(R.plurals.label_backup_groups, profileBackupSnapshot.groupCount, profileBackupSnapshot.groupCount), pluralStringResource(R.plurals.label_backup_contacts, profileBackupSnapshot.contactCount, profileBackupSnapshot.contactCount)),
                                            style = OlvidTypography.body2,
                                            color = colorResource(R.color.greyTint),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SelectSnapshotPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_SELECT_SNAPSHOT,
    ) {
        backupV2SelectSnapshot(
            mutableStateOf(BackupSnapshotsFetchState.NONE),
            mutableStateOf(listOf(
                ProfileBackupSnapshot(
                    threadId = ByteArray(5),
                    version = 2,
                    timestamp = 1647465878365,
                    thisDevice = false,
                    deviceName = "Lapin un nom très long pour le couper",
                    platform = "android",
                    contactCount = 77,
                    groupCount = 12,
                    keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.UNMANAGED,
                    keycloakInfo = null,
                    snapshot = ObvSyncSnapshot.get(null)
                ),
                ProfileBackupSnapshot(
                    threadId = ByteArray(5),
                    version = 2,
                    timestamp = 1644655621432,
                    thisDevice = false,
                    deviceName = "Lapin 2",
                    platform = null,
                    contactCount = 6,
                    groupCount = 0,
                    keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.UNMANAGED,
                    keycloakInfo = null,
                    snapshot = ObvSyncSnapshot.get(null)
                ),
                ProfileBackupSnapshot(
                    threadId = ByteArray(5),
                    version = 6,
                    timestamp = 1624655621456,
                    thisDevice = true,
                    deviceName = "Lapin 2",
                    platform = "windows",
                    contactCount = 0,
                    groupCount = 10,
                    keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.UNMANAGED,
                    keycloakInfo = null,
                    snapshot = ObvSyncSnapshot.get(null)
                ),
            )),
            {},
            {},
            {},
            {}
        )
    }
}