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

package io.olvid.messenger.olvid_plus

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun OlvidPlusDetails(
    modifier: Modifier = Modifier,
    price: String = "",
    showStoreLink: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.olvid_plus_upgrade_title),
            style = OlvidTypography.h2,
            color = colorResource(R.color.almostBlack),
            textAlign = TextAlign.Center
        )
        if (price.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.olvid_plus_upgrade_renew, price),
                style = OlvidTypography.body1,
                color = colorResource(R.color.greyTint),
                textAlign = TextAlign.Center
            )
        }
        if (showStoreLink) {
            Spacer(modifier = Modifier.height(16.dp))
            OlvidStoreLinkChip()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = spacedBy(12.dp)) {
            OlvidPlusItem(
                icon = { OlvidPlusMultiDevice(modifier = it) },
                title = R.string.olvid_plus_offer_multi_device_title,
                subtitle = R.string.olvid_plus_offer_multi_device_subtitle
            ) { onDismiss ->
                OlvidPlusDialogContent(
                    icon = { OlvidPlusMultiDevice(modifier = Modifier.size(64.dp)) },
                    onDismiss = onDismiss,
                    title = R.string.olvid_plus_dialog_multi_device_title,
                    subtitle = intArrayOf(
                        R.string.olvid_plus_dialog_multi_device_subtitle,
                        R.string.olvid_plus_dialog_multi_device_subtitle_1,
                                R.string.olvid_plus_dialog_multi_device_subtitle_2
                    )
                )
            }
            OlvidPlusItem(
                icon = { OlvidPlusCall(modifier = it) },
                title = R.string.olvid_plus_offer_call_title,
                subtitle = R.string.olvid_plus_offer_call_subtitle
            ) { onDismiss ->
                OlvidPlusDialogContent(
                    icon = { OlvidPlusCall(modifier = Modifier.size(64.dp)) },
                    onDismiss = onDismiss,
                    title = R.string.olvid_plus_dialog_call_title,
                    subtitle = intArrayOf(
                        R.string.olvid_plus_dialog_call_subtitle,
                        R.string.olvid_plus_dialog_call_subtitle_1,
                        R.string.olvid_plus_dialog_call_subtitle_2
                    )
                )
            }
            OlvidPlusItem(
                icon = { OlvidPlusSupport(modifier = it) },
                title = R.string.olvid_plus_offer_support_title,
                subtitle = R.string.olvid_plus_offer_support_subtitle
            ) { onDismiss ->
                OlvidPlusDialogContent(
                    icon = { OlvidPlusSupport(modifier = Modifier.size(64.dp)) },
                    onDismiss = onDismiss,
                    title = R.string.olvid_plus_dialog_support_title,
                    subtitle = intArrayOf(
                        R.string.olvid_plus_dialog_support_subtitle,
                        R.string.olvid_plus_dialog_support_subtitle_1,
                        R.string.olvid_plus_dialog_support_subtitle_2,
                        R.string.olvid_plus_dialog_support_subtitle_3,
                    )
                )
            }
        }
    }
}

@Composable
fun OlvidPlusItem(
    icon: @Composable (modifier : Modifier) -> Unit,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    dialogContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
) {
    var showDialogDetail by rememberSaveable { mutableStateOf(false) }
    if (showDialogDetail) {
        Dialog(
            onDismissRequest = { showDialogDetail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            dialogContent?.invoke { showDialogDetail = false }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.backgroundOverDialogBackground),
            contentColor = colorResource(R.color.almostBlack)
        ),
        onClick = {
            if (dialogContent != null) {
                showDialogDetail = true
            }
        }
    ) {
        Row(
            modifier = Modifier
                .padding(all = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            icon(Modifier.align(Alignment.Top))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row() {
                    Text(
                        modifier = Modifier.weight(1f, true),
                        text = stringResource(title),
                        style = OlvidTypography.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.almostBlack)
                    )
                    if (dialogContent != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = painterResource(id = R.drawable.ic_info),
                            tint = colorResource(R.color.greyTint),
                            contentDescription = null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(subtitle),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint)
                )
            }
        }
    }
}

@Composable
fun OlvidPlusDialogContent(
    icon: @Composable () -> Unit,
    onDismiss: () -> Unit,
    @StringRes title: Int,
    @StringRes vararg subtitle: Int
) {
    Card(
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 360.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.newDialogBackground),
            contentColor = colorResource(R.color.almostBlack),
        ),
        border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                IconButton(
                    modifier = Modifier.padding(end = 8.dp).align(Alignment.TopEnd),
                    onClick = onDismiss
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = colorResource(R.color.almostBlack),
                        contentDescription = stringResource(R.string.content_description_close_button)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                    icon()
                }
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                text = stringResource(title),
                textAlign = TextAlign.Center,
                style = OlvidTypography.h2,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = spacedBy(12.dp)
            ) {
                subtitle.forEach { text ->
                    Text(
                        text = stringResource(text),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.darkGrey)
                    )
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OlvidPlusDetailsPreview() {
    OlvidPlusDetails(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .padding(16.dp),
        price = "$4.99 monthly"
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OlvidPlusDetailsDialogPreview() {
    OlvidPlusDialogContent(
        icon = { OlvidPlusMultiDevice(modifier = Modifier.size(64.dp)) },
        onDismiss = {},
        title = R.string.olvid_plus_dialog_multi_device_title,
        subtitle = intArrayOf(
            R.string.olvid_plus_dialog_multi_device_subtitle,
            R.string.olvid_plus_dialog_multi_device_subtitle_1
        )
    )
}