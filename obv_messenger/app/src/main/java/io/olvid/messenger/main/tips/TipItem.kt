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

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.components.StarRatingBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.google_services.GooglePlay
import io.olvid.messenger.main.tips.TipsViewModel.Tip
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.ShowCurrentSeed
import io.olvid.messenger.troubleshooting.TroubleshootingActivity


@Composable
fun TipItem(
    refreshTipState: () -> Unit,
    tipToShow: Tip,
    expirationDays: Int = 0,
) {
    val context = LocalContext.current

    when (tipToShow) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                            val clip = ClipData.newPlainText(
                                                context.getString(R.string.label_text_copied_from_olvid),
                                                backupSeed
                                            )
                                            if (clipboard != null) {
                                                clipboard.setPrimaryClip(clip)
                                                App.toast(
                                                    R.string.toast_message_key_copied_to_clipboard,
                                                    Toast.LENGTH_SHORT
                                                )
                                            }
                                        } catch (_: Exception) {
                                        }
                                    },
                                    onClose = {
                                        refreshTipState()
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
                    SettingsActivity.lastTroubleshootingTipTimestamp = System.currentTimeMillis()
                    refreshTipState()
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
                        intent.data = "mailto:lang@olvid.io?subject=${subject}".toUri()
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                },
                onDismiss = {
                    SettingsActivity.muteNewTranslationsTip = true
                    refreshTipState()
                },
            )
        }

        Tip.EXPIRING_DEVICE -> {
            TipBubble(
                icon = R.drawable.ic_device,
                title = R.string.tip_expiring_device_title,
                messageString = pluralStringResource(R.plurals.tip_expiring_device_message, expirationDays, expirationDays),
                action = R.string.tip_expiring_device_action,
                onAction = {
                    App.openCurrentOwnedIdentityDetails(context)
                },
                onDismiss = {
                    SettingsActivity.lastExpiringDeviceTipTimestamp = System.currentTimeMillis()
                    refreshTipState()
                }
            )
        }

        Tip.OFFLINE_DEVICE -> {
            TipBubble(
                icon = R.drawable.ic_tip_snooze,
                title = R.string.tip_offline_device_title,
                message = R.string.tip_offline_device_message,
                action = R.string.tip_offline_device_action,
                onAction = {
                    App.openCurrentOwnedIdentityDetails(context)
                },
                onDismiss = {
                    SettingsActivity.lastOfflineDeviceTipTimestamp = System.currentTimeMillis()
                    refreshTipState()
                }
            )
        }

        Tip.PLAY_STORE_REVIEW -> {
            fun applyRating(rating: Int) {
                SettingsActivity.lastRating = rating
                SettingsActivity.lastRatingTipTimestamp = System.currentTimeMillis()
                AppSingleton.getEngine()
                    .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                        ObvSyncAtom.createSettingLastRating(
                            SettingsActivity.lastRating,
                            SettingsActivity.lastRatingTipTimestamp
                        )
                    )
                refreshTipState()
            }
            var rating by rememberSaveable { mutableIntStateOf(-1) }
            TipBubble(
                icon = R.drawable.ic_star,
                title = R.string.tip_playstore_review_title,
                onDismiss = {
                    SettingsActivity.lastRatingTipTimestamp = System.currentTimeMillis()
                    SettingsActivity.lastRating = -1
                    refreshTipState()
                }
            ) {
                StarRatingBar(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                ) { newRating ->
                    rating = newRating
                }
                AnimatedVisibility(
                    visible = rating != -1
                ) {
                    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
                    Crossfade(targetState = rating == 5 && BuildConfig.USE_GOOGLE_LIBS) { fiveStars ->
                        if (fiveStars) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = AnnotatedString(stringResource(R.string.tip_playstore_review_message)).formatMarkdown(),
                                    style = OlvidTypography.body2,
                                    color = colorResource(R.color.greyTint),
                                )
                                OlvidTextButton(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .height(40.dp),
                                    text = stringResource(R.string.tip_playstore_review_action),
                                    onClick = {
                                        GooglePlay.launchReviewFlow(context as Activity)
                                        applyRating(rating)
                                    }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = AnnotatedString(stringResource(R.string.tip_playstore_email_message)).formatMarkdown(),
                                    style = OlvidTypography.body2,
                                    color = colorResource(R.color.greyTint),
                                )
                                OlvidTextButton(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .height(40.dp),
                                    text = stringResource(R.string.button_label_send_us_a_mail),
                                    onClick = {
                                        try {
                                            val subject = context.getString(R.string.tip_playstore_email_subject, rating)
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            intent.data = "mailto:feedback@olvid.io?subject=${subject}".toUri()
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Logger.x(e)
                                        }
                                        applyRating(rating)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Tip.PROMPT_FOR_READ_RECEIPTS -> {
            TipBubble(
                icon = R.drawable.ic_message_status_delivered_all_read_one,
                title = R.string.dialog_title_prompt_user_for_read_receipts,
                message = R.string.dialog_message_prompt_user_for_read_receipts,
                onDismiss = {
                    SettingsActivity.lastReadReceiptTipTimestamp = System.currentTimeMillis()
                    refreshTipState()
                }

            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                ) {
                    OlvidTextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        text = stringResource(R.string.button_label_send_read_receipts),
                        onClick = {
                            SettingsActivity.defaultSendReadReceipt = true
                            SettingsActivity.lastReadReceiptTipTimestamp = -1
                            try {
                                AppSingleton.getEngine()
                                    .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                                        ObvSyncAtom.createSettingDefaultSendReadReceipts(true)
                                    )
                                AppSingleton.getEngine().deviceBackupNeeded()
                            } catch (_: Exception) {
                            }
                            refreshTipState()
                        }
                    )
                    OlvidTextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        text = stringResource(R.string.button_label_do_not_send),
                        onClick = {
                            SettingsActivity.defaultSendReadReceipt = false
                            SettingsActivity.lastReadReceiptTipTimestamp = -1
                            try {
                                AppSingleton.getEngine()
                                    .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                                        ObvSyncAtom.createSettingDefaultSendReadReceipts(false)
                                    )
                                AppSingleton.getEngine().deviceBackupNeeded()
                            } catch (_: Exception) {
                            }
                            refreshTipState()
                        }
                    )
                }
            }
        }

        Tip.AUTHENTICATION_REQUIRED -> {
            AppSingleton.getBytesCurrentIdentity()?.takeIf { bytesOwnedIdentity ->
                KeycloakManager.getAuthenticationRequiredOwnedIdentities()
                    .contains(BytesKey(bytesOwnedIdentity))
            }?.let { bytesOwnedIdentity ->
                TipBubble(
                    icon = R.drawable.ic_lock,
                    title = R.string.text_notification_keycloak_authentication_required_title,
                    message = R.string.text_notification_keycloak_authentication_required_message,
                    action = R.string.notification_action_authenticate,
                    onAction = {
                        KeycloakManager.forceSelfTestAndReauthentication(
                            bytesOwnedIdentity
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TipBubble(
    @StringRes title: Int,
    @StringRes message: Int? = null,
    messageString: String? = null,
    @StringRes action: Int? = null,
    @DrawableRes icon: Int? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
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
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(it),
                    tint = colorResource(R.color.olvid_gradient_light),
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

                (message?.let { stringResource(message) } ?: messageString)
                    ?.let {
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = AnnotatedString(it).formatMarkdown(),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                }
            }
        }
        action?.let {
            OlvidTextButton(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 4.dp)
                    .height(40.dp),
                text = stringResource(action),
                onClick = { onAction?.invoke() }
            )
        }
        content?.let{
            content()
        }
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TipPreview2() {
    Column {
        TipItem(refreshTipState = {}, tipToShow = Tip.EXPIRING_DEVICE, expirationDays = 6)
        TipItem(refreshTipState = {}, tipToShow = Tip.OFFLINE_DEVICE)
//        TipItem(refreshTipState = {}, tipToShow = Tip.AUTHENTICATION_REQUIRED)
        TipItem(refreshTipState = {}, tipToShow = Tip.PROMPT_FOR_READ_RECEIPTS)
        TipItem(refreshTipState = {}, tipToShow = Tip.PLAY_STORE_REVIEW)
    }
}