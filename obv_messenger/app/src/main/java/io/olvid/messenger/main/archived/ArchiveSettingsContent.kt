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

package io.olvid.messenger.main.archived

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity

@Composable
fun ArchiveSettingsContent(
    onOptionChosen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .padding(16.dp)
            .sizeIn(minWidth = 200.dp, maxWidth = 560.dp),
        propagateMinConstraints = true
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(id = R.color.dialogBackground))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.dialog_unarchive_setting_title),
                color = colorResource(id = R.color.primary700),
                style = OlvidTypography.h2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.dialog_unarchive_setting_message),
                color = colorResource(id = R.color.greyTint),
                style = OlvidTypography.body1
            )
            Spacer(modifier = Modifier.height(24.dp))
            OlvidTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.dialog_unarchive_setting_button_notified),
                large = true,
                onClick = {
                    SettingsActivity.setUnarchiveDiscussionOnNotification(
                        unarchive = true,
                        propagate = true
                    )
                    onOptionChosen()
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            OlvidTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.dialog_unarchive_setting_button_never),
                onClick = {
                    SettingsActivity.setUnarchiveDiscussionOnNotification(
                        unarchive = false,
                        propagate = true
                    )
                    onOptionChosen()
                },
                large = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = stringResource(R.string.dialog_unarchive_setting_explain),
                color = colorResource(id = R.color.greyTint),
                style = OlvidTypography.body2
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ArchiveSettingsContentPreview() {
    AppCompatTheme {
        ArchiveSettingsContent(onOptionChosen = {})
    }
}
