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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R

@Composable
fun SwipeActionBackground(
    modifier: Modifier = Modifier.fillMaxSize(),
    label: String,
    @DrawableRes icon: Int,
    @ColorRes backgroundColor: Int,
    progress: Float,
    fromStartToEnd: Boolean = true
) {
    Row(
        modifier = modifier
            .background(
                colorResource(backgroundColor).copy(
                    alpha = progress
                )
            ),
        horizontalArrangement = if (fromStartToEnd) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fromStartToEnd) {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Icon(
            modifier = Modifier
                .size(40.dp),
            painter = painterResource(icon),
            tint = Color.White,
            contentDescription = label
        )
        if (!fromStartToEnd) {
            Spacer(modifier = Modifier.width(20.dp))
        }
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ArchiveSwipeActionBackgroundPreview() {
    Column(
        verticalArrangement = spacedBy(8.dp)
    ) {
        SwipeActionBackground(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            label = "Archive",
            icon = R.drawable.ic_archive,
            backgroundColor = R.color.olvid_gradient_dark,
            progress = 0.5f
        )


        SwipeActionBackground(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            label = "Archive",
            icon = R.drawable.ic_action_mark_unread,
            backgroundColor = R.color.golden,
            progress = .5f,
            fromStartToEnd = true
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ArchiveSwipeActionBackgroundNightPreview() {
    Column(
        verticalArrangement = spacedBy(8.dp)
    ) {
        SwipeActionBackground(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            label = "Archive",
            icon = R.drawable.ic_archive,
            backgroundColor = R.color.olvid_gradient_dark,
            progress = 1f,
            fromStartToEnd = false
        )

        SwipeActionBackground(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            label = "Archive",
            icon = R.drawable.ic_action_mark_read,
            backgroundColor = R.color.green,
            progress = 1f,
            fromStartToEnd = true
        )
    }
}