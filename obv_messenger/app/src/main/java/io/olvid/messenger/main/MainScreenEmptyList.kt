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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.dashedBorder
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun MainScreenEmptyList(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    iconPadding: Dp = 0.dp,
    bottomPadding: Dp = 64.dp,
    @StringRes title: Int,
    @StringRes subtitle: Int? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier
                .size(60.dp)
                .padding(iconPadding)
                .alpha(.8f)
                .then(
                    if (onClick != null) Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .dashedBorder(
                            brush = SolidColor(colorResource(id = R.color.greyTint)),
                            shape = RoundedCornerShape(size = 16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = colorResource(R.color.almostBlack),
                            )
                        ) { onClick() }
                        .padding(8.dp)
                         else Modifier),
            painter = painterResource(id = icon),
            contentDescription = "",
            colorFilter = ColorFilter.tint(colorResource(id = if (onClick == null) R.color.greyTint else R.color.darkGrey))
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
        Spacer(modifier = Modifier.height(bottomPadding))
    }
}

@Preview
@Composable
private fun MainScreenEmptyListPreview() {
    Surface(
        color = colorResource(R.color.almostWhite)
    ) {
        MainScreenEmptyList(
            icon = R.drawable.tab_discussions,
            title = R.string.explanation_empty_discussion_list,
            subtitle = R.string.explanation_empty_discussion_list_sub,
            onClick = {})
    }
}