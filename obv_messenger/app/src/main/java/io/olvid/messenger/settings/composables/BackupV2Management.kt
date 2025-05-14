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


package io.olvid.messenger.settings.composables

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore
import io.olvid.engine.engine.types.sync.ObvSyncSnapshot
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DeviceBackupProfile
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.ProfileBackupSnapshot
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.settings.BackupV2ViewModel


@SuppressLint("RestrictedApi")
@Composable
fun ManageBackupsDialog(
    navController: NavHostController,
    deviceBackupSeed: MutableState<String?>,
    backupSeedError: MutableState<Boolean>,
    showingBackupsForOtherKey: MutableState<Boolean>,
    selectedDeviceBackupProfile: MutableState<DeviceBackupProfile?>,
    fetchingDeviceBackup: MutableState<Boolean>,
    fetchingProfileBackups: MutableState<Boolean>,
    fetchError: MutableState<BackupV2ViewModel.FetchError>,
    deviceBackupProfiles: List<DeviceBackupProfile>?,
    profileBackupSnapshots: List<ProfileBackupSnapshot>?,
    onFetchForDevice: () -> Unit,
    onFetchForProfile: () -> Unit,
    onCancelCurrentFetch: () -> Unit,
    onLoadFromCredentialsManager: (((String) -> Unit) -> Unit)?,
    onValidateSeed: () -> Boolean,
    onRestoreSnapshot: (ProfileBackupSnapshot) -> Unit,
    onDeleteSnapshot: (ProfileBackupSnapshot) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(8.dp),
        ) {
            val backStackSize = navController.currentBackStack.collectAsState().value.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {

                IconButton(
                    modifier = Modifier.size(32.dp),
                    enabled = backStackSize > 2,
                    onClick = {
                        navController.popBackStack()
                    }) {
                    AnimatedVisibility(
                        visible = backStackSize > 2,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            tint = colorResource(id = R.color.almostBlack),
                            contentDescription = stringResource(R.string.content_description_back_button)
                        )
                    }
                }

                Text(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f, true),
                    text = stringResource(R.string.dialog_title_your_online_backups),
                    style = OlvidTypography.h2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )

                IconButton(
                    modifier = Modifier.size(32.dp),
                    onClick = onDismiss,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = colorResource(id = R.color.almostBlack),
                        contentDescription = stringResource(R.string.content_description_close_button)
                    )
                }
            }


            NavHost(
                navController = navController,
                startDestination = "profiles",
            ) {
                deviceProfiles(
                    showingBackupsForOtherKey = showingBackupsForOtherKey.value,
                    fetchingDeviceBackup = fetchingDeviceBackup,
                    fetchError = fetchError,
                    deviceBackupProfiles = deviceBackupProfiles,
                    onFetchRetry = onFetchForDevice,
                    onSelectOtherKey = {
                        onCancelCurrentFetch.invoke()
                        deviceBackupSeed.value = null
                        showingBackupsForOtherKey.value = true
                        navController.navigate(
                            route = "key",
                            navOptions = NavOptions.Builder()
                                .setPopUpTo(route = "profiles", inclusive = true, saveState = false)
                                .build()
                        )
                    },
                    onProfileSelected = { selectedProfile ->
                        selectedDeviceBackupProfile.value = selectedProfile
                        onFetchForProfile.invoke()
                        navController.navigate("snapshots")
                    },
                    onDismiss = onDismiss
                )
                manuallyEnterKey(
                    backupSeed = deviceBackupSeed,
                    backupSeedError = backupSeedError,
                    onLoadFromCredentialsManager = onLoadFromCredentialsManager,
                    onValidateSeed = {
                        if (onValidateSeed.invoke()) {
                            onCancelCurrentFetch.invoke()
                            onFetchForDevice.invoke()
                            navController.navigate("profiles")
                        }
                    },
                )
                profileBackups(
                    fetchingProfileBackups = fetchingProfileBackups,
                    fetchError = fetchError,
                    profileExistsOnDevice = selectedDeviceBackupProfile.value?.profileAlreadyPresent == true,
                    profileDisplayName = selectedDeviceBackupProfile.value?.let { it.nickName ?: it.identityDetails.formatFirstAndLastName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST, false) } ?: "",
                    profileBackupSnapshots = profileBackupSnapshots,
                    onRestoreSnapshot = onRestoreSnapshot,
                    onDeleteSnapshot = onDeleteSnapshot,
                    onFetchRetry = onFetchForProfile,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

fun NavGraphBuilder.manuallyEnterKey(
    backupSeed: MutableState<String?>,
    backupSeedError: MutableState<Boolean>,
    onLoadFromCredentialsManager: (((String) -> Unit) -> Unit)?,
    onValidateSeed: () -> Unit,
) {
    composable(
        "key",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .heightIn(min = 160.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BackupKeyTextField(
                backupSeed = backupSeed,
                backupSeedError = backupSeedError,
                onValidateSeed = onValidateSeed,
            )

            onLoadFromCredentialsManager?.let {
                OutlinedButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    border = BorderStroke(width = 1.dp, color = colorResource(R.color.olvid_gradient_light)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light),
                    ),
                    onClick = {
                        it.invoke({ seed ->
                            backupSeed.value = seed
                        })
                    },
                ) {
                    Text(
                        text = stringResource(R.string.button_label_load_from_password_manager),
                    )
                }
            }

            Button(
                modifier = Modifier.height(40.dp),
                elevation = null,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = colorResource(R.color.olvid_gradient_light),
                    contentColor = colorResource(R.color.alwaysWhite)
                ),
                onClick = onValidateSeed,
            ) {
                Text(
                    text = stringResource(R.string.button_label_validate),
                )
            }
        }
    }
}

fun NavGraphBuilder.deviceProfiles(
    showingBackupsForOtherKey: Boolean,
    fetchingDeviceBackup: MutableState<Boolean>,
    fetchError: MutableState<BackupV2ViewModel.FetchError>,
    deviceBackupProfiles: List<DeviceBackupProfile>?,
    onFetchRetry: () -> Unit,
    onSelectOtherKey: () -> Unit,
    onProfileSelected: (DeviceBackupProfile) -> Unit,
    onDismiss: () -> Unit
) {
    composable(
        "profiles",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .heightIn(min = 160.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val context = LocalContext.current

            if (fetchingDeviceBackup.value) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp,
                    color = colorResource(id = R.color.olvid_gradient_light)
                )
            } else if (fetchError.value != BackupV2ViewModel.FetchError.NONE) {
                ErrorMessage(
                    fetchError = fetchError.value,
                    onRetry = onFetchRetry,
                    onDismiss = onDismiss,
                )
            } else {
                deviceBackupProfiles?.let { backups ->
                    Text(
                        text = pluralStringResource(R.plurals.label_profiles_found, backups.size, backups.size),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                    backups.forEach { deviceBackupProfile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorResource(R.color.lightGrey))
                                .clickable {
                                    onProfileSelected.invoke(deviceBackupProfile)
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                ),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                            ) {
                                Text(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Color(
                                                InitialView.getLightColor(
                                                    context = context,
                                                    bytes = deviceBackupProfile.bytesProfileIdentity
                                                )
                                            )
                                        ),
                                    text = StringUtils.getInitial(
                                        deviceBackupProfile.nickName
                                            ?: deviceBackupProfile.identityDetails.formatFirstAndLastName(
                                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                                false
                                            )
                                    ),
                                    color = Color(
                                        InitialView.getDarkColor(
                                            context = context,
                                            bytes = deviceBackupProfile.bytesProfileIdentity
                                        )
                                    ),
                                    textAlign = TextAlign.Center,
                                    fontSize = constantSp(26),
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = constantSp(40),
                                )

                                if (deviceBackupProfile.photo != null) {
                                    AsyncImage(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        imageLoader = App.imageLoader,
                                        model = deviceBackupProfile.photo,
                                        contentDescription = null,
                                    )
                                }

                                if (deviceBackupProfile.keycloakManaged) {
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
                                    text = deviceBackupProfile.nickName
                                        ?: deviceBackupProfile.identityDetails.formatFirstAndLastName(
                                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                                            false
                                        ),
                                    style = OlvidTypography.body1,
                                    color = colorResource(R.color.almostBlack),
                                    maxLines = 1,
                                )
                                (if (deviceBackupProfile.nickName == null)
                                    deviceBackupProfile.identityDetails.formatPositionAndCompany(
                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY
                                    )
                                else
                                    deviceBackupProfile.identityDetails.formatDisplayName(
                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                        false
                                    )
                                        )?.let {
                                        Text(
                                            text = it,
                                            style = OlvidTypography.body2,
                                            color = colorResource(R.color.greyTint),
                                            maxLines = 1,
                                        )
                                    }
                            }
                            if (showingBackupsForOtherKey && deviceBackupProfile.profileAlreadyPresent) {
                                Text(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(CircleShape)
                                        .background(colorResource(R.color.greyTint))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    text = stringResource(R.string.label_already_active),
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = constantSp(10),
                                    fontSize = constantSp(10),
                                    color = colorResource(R.color.alwaysWhite),
                                    maxLines = 1,
                                )
                            }
                            Image(
                                painter = painterResource(R.drawable.pref_widget_chevron_right),
                                colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                                contentDescription = null,
                            )
                        }
                    }

                    if (!showingBackupsForOtherKey) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectOtherKey.invoke()
                                },
                            text = buildAnnotatedString {
                                append(stringResource(id = R.string.label_manage_backups_for_another_key))
                                append(" ")
                                withStyle(SpanStyle(color = colorResource(id = R.color.blueOrWhite))) {
                                    append(stringResource(id = R.string.label_manage_backups_for_another_key_blue_part))
                                }
                            },
                            style = OlvidTypography.body2,
                            color = colorResource(R.color.greyTint),
                        )
                    }
                } ?: ErrorMessage(
                    fetchError = BackupV2ViewModel.FetchError.RETRIABLE,
                    onRetry = onFetchRetry,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

fun NavGraphBuilder.profileBackups(
    fetchingProfileBackups: MutableState<Boolean>,
    fetchError: MutableState<BackupV2ViewModel.FetchError>,
    profileDisplayName: String,
    profileBackupSnapshots: List<ProfileBackupSnapshot>?,
    profileExistsOnDevice: Boolean,
    onRestoreSnapshot: (ProfileBackupSnapshot) -> Unit,
    onDeleteSnapshot: (ProfileBackupSnapshot) -> Unit,
    onFetchRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    composable(
        "snapshots",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .heightIn(min = 160.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val context = LocalContext.current

            if (fetchingProfileBackups.value) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp,
                    color = colorResource(id = R.color.olvid_gradient_light)
                )
            } else if (fetchError.value != BackupV2ViewModel.FetchError.NONE) {
                ErrorMessage(
                    fetchError = fetchError.value,
                    onRetry = onFetchRetry,
                    onDismiss = onDismiss,
                )
            } else {
                profileBackupSnapshots?.let { snapshots ->
                    Text(
                        text = pluralStringResource(R.plurals.label_backups_found, snapshots.size, snapshots.size),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                    snapshots.forEach { profileBackupSnapshot ->
                        key(profileBackupSnapshot.threadId) {
                            var showContextMenu by remember { mutableStateOf(false) }
                            var showDeleteConfirmation by remember { mutableStateOf(false) }
                            var showRestoreConfirmation by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorResource(R.color.lightGrey))
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
                                        .padding(horizontal = 16.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.SpaceAround,
                                ) {
                                    Text(
                                        text = StringUtils.getDateString(context, profileBackupSnapshot.timestamp),
                                        style = OlvidTypography.body1,
                                        color = colorResource(R.color.almostBlack),
                                        maxLines = 1,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = spacedBy(8.dp)
                                    ) {
                                        profileBackupSnapshot.deviceName?.let {
                                            Text(
                                                modifier = Modifier
                                                    .weight(1f, false),
                                                text = stringResource(R.string.label_backup_from, it),
                                                style = OlvidTypography.body2,
                                                color = colorResource(R.color.greyTint),
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1,
                                            )
                                        }
                                        if (profileBackupSnapshot.thisDevice) {
                                            Text(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(colorResource(R.color.greyTint))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                text = stringResource(R.string.label_this_device),
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = constantSp(10),
                                                fontSize = constantSp(10),
                                                color = colorResource(R.color.alwaysWhite),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.label_backup_groups_and_contacts, pluralStringResource(R.plurals.label_backup_groups, profileBackupSnapshot.groupCount, profileBackupSnapshot.groupCount), pluralStringResource(R.plurals.label_backup_contacts, profileBackupSnapshot.contactCount, profileBackupSnapshot.contactCount)),
                                        style = OlvidTypography.body2,
                                        color = colorResource(R.color.greyTint),
                                        maxLines = 1,
                                    )
                                }
                                IconButton(
                                    modifier = Modifier.size(24.dp),
                                    onClick = {
                                        showContextMenu = true
                                    }
                                ) {
                                    Image(
                                        modifier = Modifier
                                            .size(24.dp),
                                        painter = painterResource(R.drawable.ic_three_dots_grey),
                                        colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                                        contentDescription = stringResource(R.string.content_description_contextual_actions),
                                    )

                                    ProfileSnapshotDropdownManu(
                                        showContextMenu = showContextMenu,
                                        onDismiss = { showContextMenu = false },
                                        onRestore = if (profileExistsOnDevice) {
                                            null
                                        } else {
                                            {
                                                showContextMenu = false
                                                showRestoreConfirmation = true
                                            }
                                        },
                                        onDelete = {
                                            showContextMenu = false
                                            showDeleteConfirmation = true
                                        }
                                    )
                                }
                            }


                            if (showRestoreConfirmation) {
                                RestoreSnapshotConfirmationDialog(
                                    displayName = profileDisplayName,
                                    onRestore = {
                                        onRestoreSnapshot.invoke(profileBackupSnapshot)
                                        showRestoreConfirmation = false
                                    },
                                    onDismiss = {
                                        showRestoreConfirmation = false
                                    },
                                )
                            }

                            if (showDeleteConfirmation) {
                                DeleteSnapshotConfirmationDialog(
                                    fromThisDevice = profileBackupSnapshot.thisDevice,
                                    onDelete = {
                                        onDeleteSnapshot.invoke(profileBackupSnapshot)
                                        showDeleteConfirmation = false
                                    },
                                    onDismiss = {
                                        showDeleteConfirmation = false
                                    },
                                )
                            }
                        }
                    }
                } ?: ErrorMessage(
                    fetchError = BackupV2ViewModel.FetchError.RETRIABLE,
                    onRetry = onFetchRetry,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}


@Composable
private fun ProfileSnapshotDropdownManu(
    showContextMenu: Boolean,
    onDismiss: () -> Unit,
    onRestore: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = colorResource(R.color.almostWhite),
    ) {
        onRestore?.let {
            // restore snapshot
            DropdownMenuItem(
                onClick = onRestore,
                text = {
                    Row(
                        modifier = Modifier.widthIn(min = 180.dp),
                        horizontalArrangement = spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, true),
                            text = stringResource(id = R.string.menu_action_restore),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.almostBlack)
                        )
                        Image(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_backup),
                            colorFilter = ColorFilter.tint(colorResource(R.color.almostBlack)),
                            contentDescription = null,
                        )
                    }
                }
            )

            HorizontalDivider(color = colorResource(R.color.lightGrey))
        }

        // delete snapshot
        DropdownMenuItem(
            onClick = onDelete,
            text = {
                Row(
                    modifier = Modifier.widthIn(min = 180.dp),
                    horizontalArrangement = spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f, true),
                        text = stringResource(id = R.string.menu_action_delete),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.red)
                    )
                    Image(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_delete_red),
                        colorFilter = ColorFilter.tint(colorResource(R.color.red)),
                        contentDescription = null,
                    )
                }
            }
        )
    }
}



@DrawableRes
fun String?.getPlatformResource() : Int {
    return when(this) {
        "android", "iphone", "ipad" -> {
            R.drawable.ic_device_phone
        }
        "mac", "linux", "windows" -> {
            R.drawable.ic_device_computer
        }
        "unknown", "bot", null -> {
            R.drawable.ic_device
        }
        else -> {
            R.drawable.ic_device
        }
    }
}

@Composable
fun ErrorMessage(fetchError: BackupV2ViewModel.FetchError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Text(
        text = stringResource(
            when (fetchError) {
                BackupV2ViewModel.FetchError.NETWORK -> R.string.error_text_backup_network_error
                BackupV2ViewModel.FetchError.NONE,
                BackupV2ViewModel.FetchError.RETRIABLE -> R.string.error_text_backup_retriable_error

                BackupV2ViewModel.FetchError.PERMANENT -> R.string.error_text_backup_permanent_error
            }
        ),
        textAlign = TextAlign.Center,
        style = OlvidTypography.body1,
        color = colorResource(R.color.greyTint),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        modifier = Modifier.height(40.dp),
        elevation = null,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = colorResource(R.color.olvid_gradient_light),
            contentColor = colorResource(R.color.alwaysWhite)
        ),
        onClick = if (fetchError != BackupV2ViewModel.FetchError.PERMANENT) onRetry else onDismiss,
    ) {
        Text(
            text = stringResource(if (fetchError != BackupV2ViewModel.FetchError.PERMANENT) R.string.button_label_retry else R.string.button_label_ok),
        )
    }
}


@Preview(device = "spec:width=720px,height=1080px,dpi=300")
@Composable
fun ManageDialogKeyPreview() {
    NavHost(
        modifier = Modifier.background(colorResource(R.color.dialogBackground)),
        navController = rememberNavController(),
        startDestination = "key",
    ) {
        manuallyEnterKey(
            backupSeed = mutableStateOf("ABCD 6534 76PL 43TR ASDF TRG5 TRGH SADF"),
            backupSeedError = mutableStateOf(true),
            onLoadFromCredentialsManager = {},
            onValidateSeed = {},
        )
    }
}

@Preview(device = "spec:width=720px,height=1080px,dpi=300")
@Composable
fun ManageDialogProfilesPreview() {
    NavHost(
        modifier = Modifier.background(colorResource(R.color.dialogBackground)),
        navController = rememberNavController(),
        startDestination = "profiles",
    ) {
        deviceProfiles(
            showingBackupsForOtherKey = false,
            fetchingDeviceBackup = mutableStateOf(false),
            fetchError = mutableStateOf(BackupV2ViewModel.FetchError.NONE),
            deviceBackupProfiles = listOf(
                DeviceBackupProfile(
                    bytesProfileIdentity = ByteArray(5),
                    nickName = null,
                    identityDetails = JsonIdentityDetails("Lola", null, null, null),
                    keycloakManaged = true,
                    photo = null,
                    profileAlreadyPresent = true,
                    profileBackupSeed = "1234 4567 8910 1234 1234 4567 8910 1234"
                ),
                DeviceBackupProfile(
                    bytesProfileIdentity = ByteArray(7),
                    nickName = null,
                    identityDetails = JsonIdentityDetails("Ariana", "de Palma", "Figma", "Lead Designer"),
                    keycloakManaged = false,
                    photo = null,
                    profileAlreadyPresent = true,
                    profileBackupSeed = "1234 4567 8910 1234 1234 4567 8910 1234"
                )
            ),
            onFetchRetry = { },
            onSelectOtherKey = { },
            onProfileSelected = { },
            onDismiss = { },
        )
    }
}

@Preview(device = "spec:width=720px,height=1080px,dpi=300")
@Composable
fun ManageDialogSnapshotsPreview() {
    NavHost(
        modifier = Modifier.background(colorResource(R.color.dialogBackground)),
        navController = rememberNavController(),
        startDestination = "snapshots",
    ) {
        profileBackups(
            fetchingProfileBackups = mutableStateOf(false),
            fetchError = mutableStateOf(BackupV2ViewModel.FetchError.NONE),
            profileExistsOnDevice = false,
            profileDisplayName = "Laura F.",
            profileBackupSnapshots = listOf(
                ProfileBackupSnapshot(
                    threadId = ByteArray(5),
                    version = 2,
                    timestamp = 1739350021,
                    thisDevice = true,
                    deviceName = "Lapin avec un nom très long pour le couper",
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
                    timestamp = 1644655621,
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
                    timestamp = 1624655621,
                    thisDevice = true,
                    deviceName = "Lapin 2",
                    platform = "windows",
                    contactCount = 0,
                    groupCount = 10,
                    keycloakStatus = ObvProfileBackupsForRestore.KeycloakStatus.UNMANAGED,
                    keycloakInfo = null,
                    snapshot = ObvSyncSnapshot.get(null)
                ),
            ),
            onRestoreSnapshot = {},
            onDeleteSnapshot = {},
            onFetchRetry = {},
            onDismiss = {},
        )
    }
}


