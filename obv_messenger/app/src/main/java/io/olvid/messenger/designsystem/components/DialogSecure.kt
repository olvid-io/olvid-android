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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity

@Composable
fun DialogSecure(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val securePolicy = if (SettingsActivity.preventScreenCapture()) {
        SecureFlagPolicy.SecureOn
    } else {
        SecureFlagPolicy.Inherit
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = securePolicy,
            usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
            decorFitsSystemWindows = properties.decorFitsSystemWindows,
            windowTitle = properties.windowTitle,
        ),
        content = content
    )
}

@Composable
fun BaseDialogContent(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.newDialogBackground),
            contentColor = colorResource(R.color.almostBlack),
        ),
        border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder))
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                text = title,
                style = OlvidTypography.h6,
                color = colorResource(R.color.almostBlack),
            )

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp)
                    .weight(1f, false)
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
            ) {
                content()
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                actions()
            }
        }
    }
}

@Composable
fun CustomDialogContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.newDialogBackground),
            contentColor = colorResource(R.color.almostBlack),
        ),
        border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder))
    ) {
        content()
    }
}

