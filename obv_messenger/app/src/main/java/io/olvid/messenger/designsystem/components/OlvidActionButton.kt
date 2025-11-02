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

package io.olvid.messenger.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun OlvidActionButton(
    modifier: Modifier = Modifier,
    containerColor: Color = colorResource(R.color.olvid_gradient_light),
    contentColor: Color = colorResource(R.color.alwaysWhite),
    @DrawableRes icon: Int? = null,
    text: String,
    outlinedColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        border = outlinedColor?.let {
            BorderStroke(
                width = 1.dp,
                brush = SolidColor(outlinedColor.copy(
                    alpha = if (enabled) outlinedColor.alpha else .5f*outlinedColor.alpha
                )),
            )
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = .5f*containerColor.alpha),
            disabledContentColor = contentColor.copy(alpha = .5f*contentColor.alpha),
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            modifier = Modifier.weight(1f, false),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = OlvidTypography.body2.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun OlvidTextButton(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    text: String,
    contentColor: Color = colorResource(R.color.olvid_gradient_light),
    enabled: Boolean = true,
    large: Boolean = false,
    fill: Boolean = false,
    allowTwoLines: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = contentPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            modifier = Modifier.weight(1f, fill),
            text = text,
            textAlign = TextAlign.Center,
            maxLines = if (allowTwoLines) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            style = if (large)
                OlvidTypography.body1.copy(fontWeight = FontWeight.Medium)
            else
                OlvidTypography.body2.copy(fontWeight = FontWeight.Medium)
        )
    }
}


@Preview
@Composable
fun OlvidActionButtonPreview() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.dialogBackground))
            .padding(16.dp),
        verticalArrangement = spacedBy(16.dp)
    ) {
        OlvidActionButton(
            icon = R.drawable.ic_message,
            text = stringResource(R.string.button_label_discuss)
        ) {}

        OlvidTextButton(
            text = stringResource(R.string.button_label_update)
        ) { }


        OlvidTextButton(
            enabled = false,
            text = stringResource(R.string.button_label_update)
        ) { }
    }
}