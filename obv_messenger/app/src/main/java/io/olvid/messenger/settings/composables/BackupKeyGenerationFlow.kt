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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.settings.BackupsHeader
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.ShowCurrentSeed
import io.olvid.messenger.settings.SuccessFailureDialog
import kotlinx.coroutines.launch


@Composable
fun BackupKeyGenerationFlow(
    credentialsManagerAvailable: State<Boolean?>,
    onToggleUseCredentialManager: (Boolean) -> Unit,
    onChooseCredentialManager: (() -> Unit)? = null,
    onGenerateBackupSeed: () -> String?,
    onSaveBackupSeedToCredentialsManager: (backupSeed: String, onSuccess: () -> Unit, onError: () -> Unit) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
) {
    var stepToShow by remember { mutableIntStateOf(0) }
    val backupKind = remember(credentialsManagerAvailable.value) { mutableStateOf(if (credentialsManagerAvailable.value == true) BackupKind.MANAGER else BackupKind.MANUAL) }
    var showSuccessOrFailureDialog by remember { mutableStateOf(false) }
    var savingBackupSeed by remember { mutableStateOf(false) }
    var generateFailed by remember { mutableStateOf(false) }
    var passwordManagerFailed by remember { mutableStateOf(false) }
    var backupSeed: String? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    var saveToCredentialManager: (Boolean) -> Unit = { useCredentialManager ->
        coroutineScope.launch {
            onToggleUseCredentialManager.invoke(useCredentialManager)
            backupSeed = onGenerateBackupSeed()
            backupSeed?.let {
                SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                generateFailed = false
                if (credentialsManagerAvailable.value == true && useCredentialManager) {
                    savingBackupSeed = true
                    showSuccessOrFailureDialog = true
                    onSaveBackupSeedToCredentialsManager(
                        it,
                        {
                            savingBackupSeed = false
                        },
                        {
                            onToggleUseCredentialManager.invoke(false)
                            SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_KEY_REMINDER
                            backupKind.value = BackupKind.MANUAL
                            passwordManagerFailed = true
                            savingBackupSeed = false
                        }
                    )
                } else {
                    SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_KEY_REMINDER
                    stepToShow = 2
                }
            } ifNull {
                generateFailed = true
                showSuccessOrFailureDialog = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(align = Alignment.CenterHorizontally)
            .width(480.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.dialogBackground)),
    ) {
        AnimatedVisibility(
            visible = stepToShow == 0,
            enter = EnterTransition.None,
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            BackupKeyGenerationWelcome(
                credentialsManagerAvailable = credentialsManagerAvailable,
                backupKind = backupKind,
                onChooseCredentialManager = onChooseCredentialManager,
                onAdvanced = {
                    stepToShow = 1
                },
                onValidate = {
                    if (backupKind.value != BackupKind.NONE) {
                        saveToCredentialManager.invoke(backupKind.value == BackupKind.MANAGER)
                    } else {
                        SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                        onClose.invoke()
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = stepToShow == 1,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(all = 16.dp),
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(R.string.label_advanced),
                    style = OlvidTypography.h2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )

                BackupKeyGenerationChoice(
                    credentialsManagerAvailable = credentialsManagerAvailable,
                    backupKind = backupKind,
                    onChooseCredentialManager = onChooseCredentialManager,
                )

                Spacer(modifier = Modifier
                    .weight(weight = 1f, fill = true))

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors().copy(
                        contentColor = colorResource(R.color.alwaysWhite),
                        containerColor = colorResource(R.color.olvid_gradient_light)
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    onClick = {
                        if (backupKind.value != BackupKind.NONE) {
                            saveToCredentialManager.invoke(backupKind.value == BackupKind.MANAGER)
                        } else {
                            SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                            onClose.invoke()
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.button_label_confirm),
                        style = OlvidTypography.body1,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = stepToShow == 2,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = ExitTransition.None,
        ) {
            backupSeed?.let {
                ShowCurrentSeed(
                    backupSeed = it,
                    savedInPasswordManager = backupKind.value == BackupKind.MANAGER,
                    followingGenerate = true,
                    onCopy = {
                        backupSeed?.let {
                            onCopy(it)
                        }
                    },
                    onClose = {
                        onClose.invoke()
                    },
                )
            }
        }

        AnimatedVisibility(visible = showSuccessOrFailureDialog) {
            SuccessFailureDialog(
                generateFailed = generateFailed,
                savingBackupSeed = savingBackupSeed,
                passwordManagerFailed = passwordManagerFailed,
                onDismiss = {
                    showSuccessOrFailureDialog = false
                    if (passwordManagerFailed) {
                        stepToShow = 2
                    } else {
                        onClose.invoke()
                    }
                }
            )
        }
    }
}


@Preview
@Composable
fun FullFragmentPreview() {
    BackupKeyGenerationFlow(
        credentialsManagerAvailable = remember { mutableStateOf(true) },
        onToggleUseCredentialManager = { },
        onChooseCredentialManager = {},
        onGenerateBackupSeed = {
            "ABCD EFGH IJKL MNOP QRST UVWX YZ12 3456"
        },
        onSaveBackupSeedToCredentialsManager = {a, b, c ->
        },
        onCopy = { },
        onClose = { },
    )
}

@Composable
fun BackupKeyGenerationWelcome(
    credentialsManagerAvailable: State<Boolean?>,
    backupKind: MutableState<BackupKind>,
    onChooseCredentialManager: (()->Unit)? = null,
    onAdvanced: () -> Unit,
    onValidate: () -> Unit,
) {
    Column (
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(all = 16.dp),
    ) {
        BackupsHeader()

        if (credentialsManagerAvailable.value == true) {
            Spacer(Modifier.height(16.dp))

            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.lighterGrey))
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.explanation_backup_v2_default_to_password_manager),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = colorResource(R.color.lightGrey)
                )

                TextButton(
                    modifier = Modifier.height(48.dp),
                    onClick = onAdvanced,
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.almostBlack)
                    )
                ) {
                    Text(
                        modifier = Modifier.weight(weight = 1f, fill = true),
                        text = stringResource(R.string.label_advanced),
                        style = OlvidTypography.body1,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(R.drawable.pref_widget_chevron_right),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        contentDescription = null
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (credentialsManagerAvailable.value == false) {
            BackupKeyGenerationChoice(
                credentialsManagerAvailable = credentialsManagerAvailable,
                backupKind = backupKind,
                onChooseCredentialManager = onChooseCredentialManager,
            )
        } else {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally),
                color = colorResource(id = R.color.olvid_gradient_light),
                strokeWidth = 5.dp,
                strokeCap = StrokeCap.Round,
            )
        }

        Spacer(modifier = Modifier
            .weight(weight = 1f, fill = true))

        if (credentialsManagerAvailable.value != null) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    contentColor = colorResource(R.color.alwaysWhite),
                    containerColor = colorResource(R.color.olvid_gradient_light)
                ),
                elevation = null,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                onClick = onValidate,
            ) {
                Text(
                    text = if (credentialsManagerAvailable.value == true && (backupKind.value == BackupKind.MANAGER)) {
                        stringResource(R.string.button_label_enable_automatic_backups)
                    } else {
                        stringResource(R.string.button_label_confirm)
                    },
                    style = OlvidTypography.body1,
                )
            }
        }
    }
}


@Composable
fun BackupKeyGenerationChoice(
    credentialsManagerAvailable: State<Boolean?>,
    backupKind: MutableState<BackupKind>,
    onChooseCredentialManager: (()->Unit)?,
) {
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp),
        verticalArrangement = spacedBy(16.dp)
    ) {
        if (credentialsManagerAvailable.value == true) {
            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.lighterGrey))
                    .border(BorderStroke(1.dp,
                        if (backupKind.value == BackupKind.MANAGER)
                            colorResource(R.color.olvid_gradient_light)
                        else
                            colorResource(R.color.greyTint)),
                        RoundedCornerShape(12.dp)
                    ),
            ) {
                TextButton(
                    shape = RectangleShape,
                    elevation = null,
                    contentPadding = PaddingValues(16.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light),
                    ),
                    onClick = {
                        backupKind.value = BackupKind.MANAGER
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = backupKind.value == BackupKind.MANAGER,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(R.color.olvid_gradient_light),
                                unselectedColor = colorResource(R.color.almostBlack),
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f, true)
                                .padding(top = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f, true),
                                    text = stringResource(R.string.label_backup_mode_title_keychain),
                                    style = OlvidTypography.body1.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colorResource(R.color.almostBlack),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    modifier = Modifier
                                        .padding(top = 1.dp)
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
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.label_backup_mode_body_keychain),
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                            )
                        }
                    }
                }


                if (onChooseCredentialManager != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = colorResource(R.color.lightGrey)
                    )

                    TextButton(
                        modifier = Modifier.height(48.dp),
                        onClick = onChooseCredentialManager,
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Text(
                            modifier = Modifier.weight(weight = 1f, fill = true),
                            text = stringResource(R.string.label_choose_default_password_manager),
                            style = OlvidTypography.body2,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Image(
                            modifier = Modifier
                                .size(24.dp),
                            painter = painterResource(R.drawable.pref_widget_chevron_right),
                            colorFilter = ColorFilter.tint(LocalContentColor.current),
                            contentDescription = null
                        )
                    }
                }
            }
        }

        OutlinedButton(
            shape = RoundedCornerShape(12.dp),
            elevation = null,
            contentPadding = PaddingValues(16.dp),
            border = BorderStroke(
                1.dp,
                if (backupKind.value == BackupKind.MANUAL)
                    colorResource(R.color.olvid_gradient_light)
                else
                    colorResource(R.color.greyTint)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colorResource(R.color.lighterGrey),
                contentColor = colorResource(R.color.olvid_gradient_light),
            ),
            onClick = {
                backupKind.value = BackupKind.MANUAL
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                RadioButton(
                    selected = backupKind.value == BackupKind.MANUAL,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.olvid_gradient_light),
                        unselectedColor = colorResource(R.color.almostBlack),
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(top = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_backup_mode_title_manual),
                        style = OlvidTypography.body1.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.label_backup_mode_body_manual),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }
            }
        }


        OutlinedButton(
            shape = RoundedCornerShape(12.dp),
            elevation = null,
            contentPadding = PaddingValues(16.dp),
            border = BorderStroke(
                1.dp,
                if (backupKind.value == BackupKind.NONE)
                    colorResource(R.color.olvid_gradient_light)
                else
                    colorResource(R.color.greyTint)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colorResource(R.color.lighterGrey),
                contentColor = colorResource(R.color.olvid_gradient_light),
            ),
            onClick = {
                backupKind.value = BackupKind.NONE
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                RadioButton(
                    selected = backupKind.value == BackupKind.NONE,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorResource(R.color.olvid_gradient_light),
                        unselectedColor = colorResource(R.color.almostBlack),
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(top = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_backup_mode_title_no_backup),
                        style = OlvidTypography.body1.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorResource(R.color.almostBlack),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.label_backup_mode_body_no_backup),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun BackupKindChoicePreview() {
    Box(
        modifier = Modifier
            .background(colorResource(R.color.alwaysWhite))
            .padding(16.dp)
    ) {
        BackupKeyGenerationChoice(
            credentialsManagerAvailable = remember { mutableStateOf(true) },
            backupKind = remember { mutableStateOf(BackupKind.MANAGER) },
            onChooseCredentialManager = {}
        )
    }
}


enum class BackupKind {
    MANAGER,
    MANUAL,
    NONE,
}

