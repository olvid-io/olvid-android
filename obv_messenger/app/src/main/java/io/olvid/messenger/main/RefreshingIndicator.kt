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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import io.olvid.messenger.R.color

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BoxScope.RefreshingIndicator(refreshing: Boolean, refreshState: PullRefreshState) {
    androidx.compose.material.pullrefresh.PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter),
        refreshing = refreshing,
        state = refreshState,
        backgroundColor = colorResource(
            id = color.dialogBackground
        ),
        contentColor = colorResource(id = color.primary700)
    )
}