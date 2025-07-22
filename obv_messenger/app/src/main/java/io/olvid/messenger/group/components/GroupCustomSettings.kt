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

package io.olvid.messenger.group.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.GroupTypeModel
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.ADMINS
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.EVERYONE
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.NOBODY


@Composable
fun GroupCustomSettings(
    modifier: Modifier = Modifier,
    groupType: GroupTypeModel,
    updateReadOnly: (Boolean) -> Unit,
    updateRemoteDelete: (RemoteDeleteSetting) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        ReadOnlySetting(
            isReadOnly = groupType.readOnlySetting,
            updateReadOnly = updateReadOnly
        )
        RemoteDeleteSetting(
            remoteDeleteSetting = groupType.remoteDeleteSetting,
            updateRemoteDeleteSetting = updateRemoteDelete
        )
    }
}

@Composable
fun ReadOnlySetting(isReadOnly: Boolean, updateReadOnly: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .clickable { updateReadOnly(!isReadOnly) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pref_use_lock_screen),
            tint = colorResource(R.color.greyTint),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pref_read_only_title), style = OlvidTypography.body1
            )
            Text(
                text = stringResource(R.string.pref_read_only_summary),
                style = OlvidTypography.subtitle1.copy(color = colorResource(R.color.greyTint))
            )
        }
        Switch(
            checked = isReadOnly, onCheckedChange = updateReadOnly, colors = SwitchDefaults.colors(
                checkedTrackColor = colorResource(R.color.olvid_gradient_light)
            )
        )
    }
}


@Composable
fun RemoteDeleteSetting(
    remoteDeleteSetting: RemoteDeleteSetting,
    updateRemoteDeleteSetting: (RemoteDeleteSetting) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable { menuExpanded = true }
            .padding(top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pref_remote_delete),
            tint = colorResource(R.color.greyTint),
            contentDescription = null
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.pref_discussion_remote_delete_title),
                style = OlvidTypography.body1
            )
            Text(
                text = when (remoteDeleteSetting) {
                    EVERYONE -> stringResource(R.string.text_group_remote_delete_setting_everyone)
                    ADMINS -> stringResource(R.string.text_group_remote_delete_setting_admins)
                    NOBODY -> stringResource(R.string.text_group_remote_delete_setting_nobody)
                }, style = OlvidTypography.subtitle1.copy(color = colorResource(R.color.greyTint))
            )
            OlvidDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                OlvidDropdownMenuItem(
                    text = stringResource(R.string.text_group_remote_delete_setting_everyone),
                    onClick = {
                        updateRemoteDeleteSetting(EVERYONE)
                        menuExpanded = false
                    })
                OlvidDropdownMenuItem(
                    text = stringResource(R.string.text_group_remote_delete_setting_admins),
                    onClick = {
                        updateRemoteDeleteSetting(ADMINS)
                        menuExpanded = false
                    })
                OlvidDropdownMenuItem(
                    text = stringResource(R.string.text_group_remote_delete_setting_nobody),
                    onClick = {
                        updateRemoteDeleteSetting(NOBODY)
                        menuExpanded = false
                    })
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GroupCustomSettingsPreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(color = colorResource(R.color.almostWhite))
    ) {
        ReadOnlySetting(true)
        RemoteDeleteSetting(ADMINS)
    }
}