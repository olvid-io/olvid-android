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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun MainScreenEmptyList(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    iconPadding: Dp = 0.dp,
    @StringRes title: Int,
    @StringRes subtitle: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier
                .size(60.dp)
                .padding(iconPadding)
                .alpha(.8f),
            painter = painterResource(id = icon),
            contentDescription = "",
            colorFilter = ColorFilter.tint(colorResource(id = R.color.greyTint))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = title),
            style = OlvidTypography.body1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colorResource(id = R.color.almostBlack)
        )
        subtitle?.let {
            Text(
                text = stringResource(id = subtitle),
                style = OlvidTypography.subtitle1,
                textAlign = TextAlign.Center,
                color = colorResource(id = R.color.greyTint)
            )
        }
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Preview
@Composable
private fun MainScreenEmptyListPreview() {
    AppCompatTheme {
        MainScreenEmptyList(icon = R.drawable.ic_phone_log, title = R.string.explanation_empty_discussion_list, subtitle = R.string.explanation_empty_discussion_list_sub)
    }
}