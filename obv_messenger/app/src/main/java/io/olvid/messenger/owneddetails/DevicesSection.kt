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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun DevicesSection(
    devices: List<OwnedDevice>,
    currentDeviceIsActive: Boolean,
    showRefreshSpinner: Boolean,
    onTrust: (OwnedDevice) -> Unit,
    onRename: (OwnedDevice) -> Unit,
    onRemoveExpiration: (OwnedDevice) -> Unit,
    onRefresh: (OwnedDevice) -> Unit,
    onRecreateChannel: (OwnedDevice) -> Unit,
    onRemove: (OwnedDevice) -> Unit,
    onAddDevice: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
            text = stringResource(id = R.string.label_my_devices),
            style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(R.color.lighterGrey),
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                devices.forEachIndexed { index, device ->
                    OwnedDeviceItem(
                        device = device,
                        currentDeviceIsActive = currentDeviceIsActive,
                        onTrust = { onTrust(device) },
                        onRename = { onRename(device) },
                        onRemoveExpiration = { onRemoveExpiration(device) },
                        onRefresh = { onRefresh(device) },
                        onRecreateChannel = { onRecreateChannel(device) },
                        onRemove = { onRemove(device) }
                    )
                    if (index < devices.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colorResource(R.color.lightGrey)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(id = R.string.button_label_add_device),
                        onClick = onAddDevice
                    )
                }
            }

            if (showRefreshSpinner) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(colorResource(id = R.color.whiteOverlay)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = colorResource(id = R.color.olvid_gradient_light)
                    )
                }
            }
        }
    }
}
