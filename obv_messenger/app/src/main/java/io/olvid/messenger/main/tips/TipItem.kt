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

package io.olvid.messenger.main.tips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.cutoutHorizontalPadding
import io.olvid.messenger.main.tips.TipsViewModel.Tip
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.ShowCurrentSeed
import io.olvid.messenger.troubleshooting.TroubleshootingActivity


@Composable
fun TipItem(
    refreshTipState: () -> Unit,
    tipToShow: Tip
) {
    val context = LocalContext.current

    when(tipToShow) {
        Tip.CONFIGURE_BACKUPS -> {
            TipBubble(
                icon = R.drawable.ic_backup,
                title = R.string.tip_setup_new_backups_title,
                message = R.string.tip_setup_new_backups_message,
                action = R.string.button_label_configure_backups,
                onAction = {
                    val intent = Intent(context, SettingsActivity::class.java)
                    intent.putExtra(
                        SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA,
                        SettingsActivity.PREF_HEADER_KEY_BACKUP
                    )
                    context.startActivity(intent)
                },
            )
        }

        Tip.WRITE_BACKUP_KEY -> {
            var showSeed by remember { mutableStateOf(false) }
            TipBubble(
                icon = R.drawable.ic_backup,
                title = R.string.tip_write_backup_key_title,
                message = R.string.tip_write_backup_key_message,
                action = R.string.button_label_show_my_backup_key,
                onAction = {
                    showSeed = true
                },
            ) {
                AppSingleton.getEngine().deviceBackupSeed?.let { backupSeed ->
                    if (showSeed) {
                        Dialog(
                            onDismissRequest = {
                                showSeed = false
                            },
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colorResource(R.color.dialogBackground))
                            ) {
                                ShowCurrentSeed(
                                    backupSeed = backupSeed,
                                    savedInPasswordManager = SettingsActivity.useCredentialsManagerForBackups,
                                    followingGenerate = false,
                                    fillScreen = false,
                                    onCopy = {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                            val clip = ClipData.newPlainText(context.getString(R.string.label_text_copied_from_olvid), backupSeed)
                                            if (clipboard != null) {
                                                clipboard.setPrimaryClip(clip)
                                                App.toast(R.string.toast_message_key_copied_to_clipboard, Toast.LENGTH_SHORT)
                                            }
                                        } catch (_: Exception) { }
                                    },
                                    onClose = {
                                        refreshTipState.invoke()
                                        showSeed = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Tip.TROUBLESHOOTING -> {
            TipBubble(
                icon = R.drawable.ic_tip_bulb,
                title = R.string.tip_troubleshooting_title,
                message = R.string.tip_troubleshooting_message,
                action = R.string.button_label_this_way,
                onAction = {
                    val intent = Intent(context, TroubleshootingActivity::class.java)
                    context.startActivity(intent)
                },
                onDismiss = {
                    SettingsActivity.muteTroubleshootingTipUntil = System.currentTimeMillis() + 7 * 86_400_000L // mute the tip for 1 week
                    refreshTipState.invoke()
                },
            )
        }

        Tip.NEW_TRANSLATIONS -> {
            val subject = stringResource(R.string.mail_subject_olvid_translation)
            TipBubble(
                icon = R.drawable.ic_tip_translate,
                title = R.string.tip_new_translations_title,
                message = R.string.tip_new_translations_message,
                action = R.string.button_label_send_us_a_mail,
                onAction = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(
                            Uri.parse("mailto:lang@olvid.io?subject=${subject}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                },
                onDismiss = {
                    SettingsActivity.muteNewTranslationsTip = true
                    refreshTipState.invoke()
                },
            )
        }
    }
}

@Composable
private fun TipBubble(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes action: Int,
    @DrawableRes icon: Int? = null,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cutoutHorizontalPadding()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(R.color.lighterGrey)),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = spacedBy(8.dp)
        ) {
            icon?.let {
                Image(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(it),
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier.weight(1f, true),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 8.dp)
                            .weight(1f, true),
                        text = stringResource(title),
                        style = OlvidTypography.body1.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorResource(R.color.almostBlack),
                    )
                    onDismiss?.let {
                        IconButton(
                            modifier = Modifier
                                .size(40.dp)
                                .offset(x = 8.dp, y = (-8).dp),
                            onClick = it,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                tint = colorResource(R.color.almostBlack),
                                contentDescription = stringResource(R.string.content_description_close_button)
                            )
                        }
                    }
                }

                Text(
                    text = AnnotatedString(stringResource(message)).formatMarkdown(),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
            }
        }
        TextButton(
            modifier = Modifier
                .padding(end = 8.dp, bottom = 4.dp)
                .height(40.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colorResource(R.color.olvid_gradient_light),
            ),
            elevation = null,
            onClick = onAction
        ) {
            Text(
                text = stringResource(action),
                style = OlvidTypography.body2
            )
        }
        content?.invoke()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TipPreview() {
    Column {
        TipItem(refreshTipState = {}, tipToShow = Tip.TROUBLESHOOTING)
        TipItem(refreshTipState = {}, tipToShow = Tip.CONFIGURE_BACKUPS)
//        TipItem(refreshTipState = {}, tipToShow = Tip.WRITE_BACKUP_KEY)
        TipItem(refreshTipState = {}, tipToShow = Tip.NEW_TRANSLATIONS)
    }
}