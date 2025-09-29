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

package io.olvid.messenger.main

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.designsystem.components.DotAnimation


@Composable
fun EstablishingChannel() {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(id = string.label_creating_channel).uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            lineHeight = 24.sp,
            color = colorResource(
                id = color.greyTint
            )
        )
        DotAnimation()
    }
}

@Preview
@Composable
private fun EstablishingChannelPreview() {
    AppCompatTheme {
        EstablishingChannel()
    }
}