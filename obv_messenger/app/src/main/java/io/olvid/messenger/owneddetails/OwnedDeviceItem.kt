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

package io.olvid.messenger.owneddetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.EstablishingChannel
import io.olvid.messenger.main.tips.TipsViewModel

@Composable
fun OwnedDeviceItem(
    modifier: Modifier = Modifier,
    device: OwnedDevice,
    currentDeviceIsActive: Boolean,
    onTrust: () -> Unit,
    onRename: () -> Unit,
    onRemoveExpiration: () -> Unit,
    onRefresh: () -> Unit,
    onRecreateChannel: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(24.dp),
            painter = painterResource(id = R.drawable.ic_device),
            contentDescription = null,
            tint = colorResource(R.color.almostBlack)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = device.getDisplayNameOrDeviceHexName(context),
                    style = OlvidTypography.body2,
                    fontWeight = FontWeight.Medium,
                    color = colorResource(R.color.almostBlack),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!device.trusted && !device.currentDevice) {
                    Text(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(colorResource(R.color.red), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        text = stringResource(id = R.string.label_untrusted_device),
                        style = OlvidTypography.caption,
                        color = colorResource(R.color.alwaysWhite)
                    )
                }

                val isOld =
                    device.lastRegistrationTimestamp?.takeIf { !device.currentDevice }?.let {
                        it < (System.currentTimeMillis() - TipsViewModel.OFFLINE_DEVICE_ALERT_THRESHOLD)
                    } ?: false

                if (isOld) {
                    Text(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(colorResource(R.color.orange), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        text = stringResource(id = R.string.label_offline),
                        style = OlvidTypography.caption, color = colorResource(R.color.alwaysWhite)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val statusText = if (device.currentDevice) {
                stringResource(R.string.text_this_device)
            } else {
                device.lastRegistrationTimestamp?.let {
                    stringResource(
                        R.string.text_last_online,
                        StringUtils.getLongNiceDateString(context, it)
                    )
                }
            }

            statusText?.let {
                Text(
                    text = it,
                    style = OlvidTypography.subtitle1, color = colorResource(R.color.grey),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (device.currentDevice && !currentDeviceIsActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(id = R.drawable.ic_device_inactive),
                        contentDescription = null,
                        tint = colorResource(R.color.red)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.text_device_is_inactive),
                        style = OlvidTypography.subtitle1, color = colorResource(R.color.grey)
                    )
                }
            } else if (device.expirationTimestamp != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(id = R.drawable.ic_device_expiration),
                        contentDescription = null,
                        tint = colorResource(R.color.orange)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.text_deactivates_on,
                            StringUtils.getPreciseAbsoluteDateString(
                                context,
                                device.expirationTimestamp!!,
                                stringResource(R.string.text_date_time_separator)
                            )
                        ),
                        style = OlvidTypography.subtitle1, color = colorResource(R.color.grey),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedVisibility(visible = currentDeviceIsActive && !device.channelConfirmed && !device.currentDevice) {
            EstablishingChannel()
        }

        if (currentDeviceIsActive) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(id = R.drawable.ic_three_dots_always_white),
                        contentDescription = null,
                        tint = colorResource(R.color.almostBlack)
                    )
                }
                OlvidDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (!device.currentDevice && !device.trusted) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_trust_device),
                            onClick = {
                                expanded = false
                                onTrust()
                            }
                        )
                    }
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_rename_device),
                        onClick = {
                            expanded = false
                            onRename()
                        }
                    )
                    if (device.expirationTimestamp != null) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_remove_expiration),
                            onClick = {
                                expanded = false
                                onRemoveExpiration()
                            }
                        )
                    }
                    if (device.currentDevice) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_refresh_device_list),
                            onClick = {
                                expanded = false
                                onRefresh()
                            }
                        )
                    } else {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_recreate_channel),
                            onClick = {
                                expanded = false
                                onRecreateChannel()
                            }
                        )
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_remove_device),
                            textColor = colorResource(R.color.red),
                            onClick = {
                                expanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}
