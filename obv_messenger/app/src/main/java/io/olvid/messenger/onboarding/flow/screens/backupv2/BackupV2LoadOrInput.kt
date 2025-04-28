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

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.BackupKeyCheckState
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep


fun NavGraphBuilder.backupV2LoadOrInput(
    credentialManagerAvailable: MutableState<Boolean?>,
    backupKeyState: MutableState<BackupKeyCheckState>,
    onInputKey: () -> Unit,
    onLoadFromBackupManager: () -> Unit,
    onChooseCredentialManager: (() -> Unit)?,
    onLoadFailed: () -> Unit,
    onDeviceBackupLoaded: () -> Unit,
    onCreateNewProfile: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.BACKUP_V2_LOAD_OR_INPUT,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
    ) {
        val context = LocalContext.current
        val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
        var showNoKeyDialog by remember { mutableStateOf(false) }

        var keyLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(backupKeyState.value) {
            when (backupKeyState.value) {
                BackupKeyCheckState.NONE -> Unit
                BackupKeyCheckState.CHECKING -> {
                    keyLoaded = true
                }
                BackupKeyCheckState.DEVICE_KEY -> {
                    if (keyLoaded) {
                        keyLoaded = false
                        onDeviceBackupLoaded.invoke()
                    }
                }
                BackupKeyCheckState.UNKNOWN,
                BackupKeyCheckState.ERROR -> {
                    if (keyLoaded) {
                        keyLoaded = false
                        onLoadFailed.invoke()
                    }
                }
            }
        }

        OnboardingScreen(
            step = OnboardingStep(title = stringResource(id = R.string.onboarding_backup_v2_load_or_input_title),
                actions = mutableListOf(
                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_enter_my_key)),
                        onClick = onInputKey,
                    ),

                    OnboardingAction(
                        label = AnnotatedString(stringResource(id = R.string.button_label_i_dont_have_a_key)),
                    ) {
                        showNoKeyDialog = true
                    },
                ).apply {
                    if (credentialManagerAvailable.value == true) {
                        add(1, OnboardingAction(
                            customContent = {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .widthIn(max = 400.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0x6E111111), RoundedCornerShape(12.dp)),
                                ) {
                                    TextButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RectangleShape,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = colorResource(id = R.color.almostBlack),
                                        ),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            top = 24.dp,
                                            end = 16.dp,
                                            bottom = 24.dp
                                        ),
                                        onClick = onLoadFromBackupManager
                                    ) {
                                        Text(
                                            modifier = Modifier.weight(1f, fill = true),
                                            text = stringResource(id = R.string.button_label_load_from_password_manager),
                                            style = OlvidTypography.body1,
                                        )
                                        Icon(
                                            painter = painterResource(id = R.drawable.pref_widget_chevron_right),
                                            tint = colorResource(id = R.color.almostBlack),
                                            contentDescription = ""
                                        )
                                    }

                                    if (onChooseCredentialManager != null) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = Color(0x6E111111)
                                        )
                                        TextButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp),
                                            onClick = onChooseCredentialManager,
                                            shape = RectangleShape,
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = colorResource(R.color.olvid_gradient_light)
                                            )
                                        ) {
                                            Text(
                                                modifier = Modifier.weight(weight = 1f, fill = true),
                                                text = stringResource(R.string.label_choose_default_password_manager),
                                                style = OlvidTypography.body2,
                                                textAlign = TextAlign.Start,
                                            )
                                        }
                                    }
                                }
                            },
                            label = AnnotatedString(""),
                            onClick = onDeviceBackupLoaded,
                        ))
                    }
                }
            ),
            footer = {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = buildAnnotatedString {
                        append(stringResource(id = R.string.onboarding_managed_profile_question))
                        append(" ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "",
                                styles = TextLinkStyles(SpanStyle(color = colorResource(id = R.color.blueOrWhite))),
                                linkInteractionListener = {
                                    scanLauncher.launch(
                                        Intent(
                                            context,
                                            OnboardingActivity::class.java
                                        )
                                    )
                                }
                            ),
                        ) {
                            append(stringResource(id = R.string.onboarding_managed_profile_hyperlink))
                        }
                    },
                    textAlign = TextAlign.Center,
                    color = colorResource(id = R.color.greyTint),
                    style = OlvidTypography.body2,
                )
            },
            onBack = onBack,
            onClose = onClose
        )

        if (showNoKeyDialog) {
            Dialog(
                onDismissRequest = {
                    showNoKeyDialog = false
                },
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.dialogBackground))
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = spacedBy(8.dp),
                    ) {
                        Image(
                            modifier = Modifier.size(32.dp),
                            painter = painterResource(R.drawable.ic_backup),
                            colorFilter = ColorFilter.tint(colorResource(R.color.orange)),
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.padding(end = 4.dp),
                            text = stringResource(R.string.dialog_message_backup_restore_needs_a_key),
                            style = OlvidTypography.body1,
                            color = colorResource(R.color.greyTint),
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    TextButton(
                        modifier = Modifier.height(40.dp),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorResource(R.color.olvid_gradient_light)
                        ),
                        onClick = {
                            showNoKeyDialog = false
                            onCreateNewProfile.invoke()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_create_new_profile),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        modifier = Modifier.height(40.dp),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorResource(R.color.olvid_gradient_light)
                        ),
                        onClick = {
                            showNoKeyDialog = false
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_ok),
                        )
                    }
                }
            }
        }

        if (backupKeyState.value == BackupKeyCheckState.CHECKING) {
            Dialog(
                onDismissRequest = {},
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.dialogBackground))
                        .padding(32.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 5.dp,
                        color = colorResource(id = R.color.olvid_gradient_light)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ExistingProfilePreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.BACKUP_V2_LOAD_OR_INPUT,
    ) {
        backupV2LoadOrInput(mutableStateOf(true), mutableStateOf(BackupKeyCheckState.NONE), {}, {}, {}, {}, {}, {}, {}, {})
    }
}