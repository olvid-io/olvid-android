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

package io.olvid.messenger.storage_manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun TextChip(modifier: Modifier = Modifier, text: String) {
    Text(
        modifier = modifier
            .padding(2.dp)
            .background(
                color = colorResource(R.color.blackDarkOverlay),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp),
        text = text,
        style = OlvidTypography.subtitle1,
        fontWeight = FontWeight.SemiBold,
        color = colorResource(R.color.alwaysWhite),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}


@Preview
@Composable
private fun TextChipPreview() {
    Box(modifier = Modifier.background(colorResource(R.color.alwaysWhite))) {
        TextChip(text = "Test text")
    }
}