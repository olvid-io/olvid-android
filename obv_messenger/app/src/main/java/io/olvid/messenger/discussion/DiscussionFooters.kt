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

package io.olvid.messenger.discussion

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.DotAnimation
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun DiscussionNoChannel(modifier: Modifier = Modifier, messageRes: Int?) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotAnimation(
            modifier = Modifier.padding(start = 17.dp, end = 17.dp),
            dotSize = 4.dp,
            spacing = 5.dp
        )
        if (messageRes != null) {
            Text(
                text = stringResource(messageRes),
                style = OlvidTypography.body1.copy(fontStyle = FontStyle.Italic, color = colorResource(R.color.almostBlack)),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun DiscussionLocked(modifier: Modifier = Modifier, state: DiscussionActivity.LockedState) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier.padding(12.dp).size(24.dp),
            painter = painterResource(id = state.icon),
            contentDescription = null,
            tint = colorResource(id = R.color.grey)
        )
        Text(
            text = stringResource(id = state.message),
            color = colorResource(id = R.color.grey),
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp) // To balance the icon padding
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LockedAndNoChannelPreview() {
    Column(modifier = Modifier.fillMaxSize()) {
        DiscussionLocked(state =
            DiscussionActivity.LockedState(
                R.drawable.ic_lock,
                R.string.message_discussion_locked
            )
        )
        DiscussionNoChannel(messageRes = R.string.message_discussion_no_channel)
    }
}