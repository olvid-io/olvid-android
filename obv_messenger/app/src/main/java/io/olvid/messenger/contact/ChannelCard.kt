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

package io.olvid.messenger.contact

import android.content.DialogInterface
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.designsystem.components.DotAnimation
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
internal fun ChannelCard(
    onRestartEstablishment: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .background(
                color = colorResource(R.color.lighterGrey),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotAnimation(modifier = Modifier.width(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_establishing_secure_connection),
                style = OlvidTypography.h3,
                color = colorResource(R.color.almostBlack)
            )
        }
        Text(
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            text = stringResource(R.string.explanation_no_channel),
            style = OlvidTypography.subtitle1,
            color = colorResource(R.color.greyTint)
        )
        OlvidTextButton(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            text = stringResource(R.string.button_restart_channel_establishment)
        ) {
            val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_restart_channel_establishment)
                .setMessage(R.string.dialog_message_restart_channel_establishment)
                .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                    onRestartEstablishment()
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChannelCardPreview() {
    ChannelCard(onRestartEstablishment = {})
}
