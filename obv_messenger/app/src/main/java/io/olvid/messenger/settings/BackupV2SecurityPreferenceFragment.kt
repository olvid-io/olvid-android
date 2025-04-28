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
package io.olvid.messenger.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.launch


class BackupV2SecurityPreferenceFragment : Fragment() {
    private val viewModel: BackupV2ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(layoutInflater.context).apply {
            setContent {
                var showResetDialog by remember { mutableStateOf(false) }
                var showResetFailedDialog by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.alwaysWhite))
                        .verticalScroll(rememberScrollState())
                        .padding(all = 16.dp),
                    verticalArrangement = spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorResource(R.color.lighterGrey))
                    ) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            onClick = {
                                activity?.navigateToSettingsFragment(BackupV2ShowKeyFragment::class.java.name)
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorResource(R.color.olvid_gradient_light)
                            )
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, true),
                                text = stringResource(R.string.pref_show_your_backup_key_title),
                                textAlign = TextAlign.Start,
                                style = OlvidTypography.body1,
                            )
                            Spacer(Modifier.width(16.dp))
                            Image(
                                painter = painterResource(R.drawable.pref_widget_chevron_right),
                                colorFilter = ColorFilter.tint(LocalContentColor.current),
                                contentDescription = null,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = colorResource(R.color.lightGrey)
                        )

                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            onClick = {
                                showResetDialog = true
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorResource(R.color.olvid_gradient_light)
                            )
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, true),
                                text = stringResource(R.string.pref_compromised_key_title),
                                textAlign = TextAlign.Start,
                                style = OlvidTypography.body1,
                            )
                        }
                    }

                    Column {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorResource(R.color.lighterGrey)),
                            onClick = {
                                viewModel.showResetConfirmationDialog.value = true
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorResource(R.color.red)
                            )
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, true),
                                text = stringResource(R.string.button_label_reset),
                                textAlign = TextAlign.Start,
                                style = OlvidTypography.body1,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.explanation_reset_backups),
                            color = colorResource(R.color.greyTint),
                        )
                    }
                }

                if (viewModel.showResetConfirmationDialog.value) {
                    EraseBackupsDialog(
                        onReset = {
                            try {
                                AppSingleton.getEngine().deviceBackupSeed?.let {
                                    AppSingleton.getEngine().deleteDeviceBackupSeed(it)
                                    if (AppSingleton.getEngine().deviceBackupSeed == null) {
                                        App.toast(R.string.toast_message_backups_reset, Toast.LENGTH_SHORT)
                                        SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                                        activity?.supportFragmentManager?.apply {
                                            viewModel.disableSeedGeneration.value = true
                                            popBackStackImmediate()
                                            popBackStackImmediate()
                                        }
                                    }
                                }
                            } catch (_ : Exception) {}
                        },
                        onDismiss = {
                            viewModel.showResetConfirmationDialog.value = false
                        },
                    )
                }


                if (showResetDialog) {
                    ResetSeedDialog(
                        onReset = {
                            coroutineScope.launch {
                                // delete previous key
                                AppSingleton.getEngine().deviceBackupSeed?.let { seed ->
                                    AppSingleton.getEngine().deleteDeviceBackupSeed(seed)
                                }
                                // generate a new one
                                val newSeed : String? = AppSingleton.getEngine().generateDeviceBackupSeed(BuildConfig.SERVER_NAME)
                                if (newSeed == null) {
                                    showResetFailedDialog = true
                                } else {
                                    SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                                    // switch to the new fragment
                                    activity?.navigateToSettingsFragment(BackupV2ShowKeyFragmentAfterReset::class.java.name)
                                }
                                showResetDialog = false
                            }
                        },
                        onDismiss = {
                            showResetDialog = false
                        },
                    )
                }

                if (showResetFailedDialog) {
                    ResetFailedDialog(
                        onDismiss = {
                            showResetDialog = false
                            activity?.supportFragmentManager?.apply {
                                popBackStackImmediate()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EraseBackupsDialog(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = AnnotatedString(stringResource(R.string.dialog_message_reset_backups)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.button_label_cancel),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors().copy(
                        containerColor = colorResource(R.color.red),
                        contentColor = colorResource(R.color.alwaysWhite)
                    ),
                    onClick = onReset,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_reset),
                    )
                }
            }
        }
    }
}


@Preview
@Composable
fun ResetDialogPreview() {
    EraseBackupsDialog({},{})
}


@Composable
fun ResetSeedDialog(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = AnnotatedString(stringResource(R.string.dialog_message_reset_backup_key)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.greyTint)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.button_label_cancel),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors().copy(
                        containerColor = colorResource(R.color.red),
                        contentColor = colorResource(R.color.alwaysWhite)
                    ),
                    onClick = onReset,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_reset_my_key),
                    )
                }
            }
        }
    }
}

@Composable
fun ResetFailedDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = AnnotatedString(stringResource(R.string.dialog_message_reset_backup_key_failed)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorResource(R.color.olvid_gradient_light)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.button_label_ok),
                    )
                }
            }
        }
    }
}

