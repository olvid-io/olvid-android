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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.composables.ReadOnlyBackupKeyTextField


class BackupV2ShowKeyFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val backupSeed : String? = AppSingleton.getEngine().deviceBackupSeed

        if (backupSeed == null) {
            activity?.supportFragmentManager?.popBackStack()
        }

        return ComposeView(layoutInflater.context).apply {
            setContent {
                if (backupSeed != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorResource(R.color.almostWhite))
                    ) {
                        ShowCurrentSeed(
                            backupSeed = backupSeed,
                            savedInPasswordManager = SettingsActivity.useCredentialsManagerForBackups,
                            onCopy = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                    val clip = ClipData.newPlainText(context.getString(R.string.label_text_copied_from_olvid), backupSeed)
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(clip)
                                        App.toast(R.string.toast_message_key_copied_to_clipboard, Toast.LENGTH_SHORT)
                                    }
                                } catch (_: Exception) {
                                }
                            },
                            onClose = {
                                activity?.supportFragmentManager?.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}





@Composable
fun ShowCurrentSeed(
    backupSeed: String,
    savedInPasswordManager: Boolean,
    followingGenerate: Boolean = false,
    fillScreen: Boolean = true,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (fillScreen)
                    Modifier.fillMaxHeight()
                else
                    Modifier
            )
            .verticalScroll(rememberScrollState())
            .padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackupKeyHeader(
            savedInPasswordManager = savedInPasswordManager,
            followingKeyGeneration = followingGenerate,
        )

        Spacer(Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorResource(R.color.lighterGrey))
                    .padding(
                        top = 8.dp,
                        start = 4.dp,
                        bottom = 16.dp,
                        end = 4.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                ReadOnlyBackupKeyTextField(backupSeed = backupSeed)
            }
            TextButton(
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.olvid_gradient_light)
                ),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(4.dp),
                onClick = onCopy,
            ) {
                Row {
                    Image(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_swipe_copy),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.button_label_copy_key),
                        style = OlvidTypography.body2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (fillScreen) {
            Spacer(modifier = Modifier.weight(1f, true))
        }

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
                SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                onClose.invoke()
            },
        ) {
            Text(
                text = stringResource(R.string.button_label_i_wrote_my_key),
                style = OlvidTypography.body1,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors().copy(
                contentColor = colorResource(R.color.almostBlack),
            ),
            elevation = null,
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder().copy(
                brush = SolidColor(colorResource(R.color.greyTint))
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            onClick = {
                SettingsActivity.backupsV2Status = SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_KEY_REMINDER
                onClose.invoke()
            },
        ) {
            Text(
                text = stringResource(R.string.button_label_remind_me_later),
                style = OlvidTypography.body1,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSeed() {
    ShowCurrentSeed(
        backupSeed = "RW35 F1QH KB9V E59B WEYM KQRG UFDR 94UM",
        savedInPasswordManager = true,
        followingGenerate = true,
        onCopy = {},
        onClose = {},
    )
}


@Composable
fun BackupKeyHeader(
    savedInPasswordManager: Boolean,
    followingKeyGeneration: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.lighterGrey))
            .padding(all = 16.dp),
        verticalArrangement = spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier.size(64.dp),
            painter = painterResource(R.drawable.ic_backup_key),
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.pref_your_backup_key_title),
            style = OlvidTypography.h1.copy(
                fontWeight = FontWeight.Medium
            ),
            color = colorResource(R.color.almostBlack),
        )
        Text(
            text = stringResource(
                if (savedInPasswordManager)
                    R.string.explanation_backup_key_password_manager
                else
                    R.string.explanation_backup_key_no_password_manager
            ),
            style = OlvidTypography.body1,
            color = colorResource(R.color.almostBlack),
            textAlign = TextAlign.Center,
        )
        if (followingKeyGeneration) {
            Text(
                text = stringResource(R.string.explanation_backup_key_found_in_settings),
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun KeyHeaderPreview() {
    BackupKeyHeader(true, true)
}
