/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.main.contacts

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun TabBaselineLayout(
    text: @Composable (() -> Unit)
) {
    Box(
        Modifier.layoutId("text").padding(horizontal = 4.dp)
    ) { text() }
}


@Composable
fun CustomTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor.copy(alpha = ContentAlpha.medium),
    horizontalTextPadding: Dp = 16.dp
) {
    val styledText = @Composable {
        val style = MaterialTheme.typography.button.copy(textAlign = TextAlign.Center)
        ProvideTextStyle(style, content = text)
    }

    Tab(
        selected,
        onClick,
        modifier,
        enabled,
        interactionSource,
        selectedContentColor,
        unselectedContentColor
    ) {
        Layout({
            Box(
                Modifier.layoutId("text").padding(horizontal = horizontalTextPadding)
            ) { styledText.invoke() }
        }) { measurables, constraints ->
            val textPlaceable = measurables.first { it.layoutId == "text" }.measure(
                // Measure with loose constraints for height as we don't want the text to take up more
                // space than it needs
                constraints.copy(minHeight = 0)
            )

            val tabHeight = 48.dp.roundToPx()
            layout(textPlaceable.width, tabHeight) {
                val contentY = (tabHeight - textPlaceable.height) / 2
                textPlaceable.placeRelative(0, contentY)
            }
        }
    }
}
