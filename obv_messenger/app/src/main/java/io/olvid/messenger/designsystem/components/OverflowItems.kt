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

package io.olvid.messenger.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

/**
 * Emits at most [maxVisible] of [items] into the enclosing layout (Row, FlowRow, Column…), the way
 * a `forEach` would, and tells the last visible item how many are left out so it can draw an
 * [OverflowIndicator] on itself.
 *
 * This replaces FlowRow's `overflow` parameter and `ContextualFlowRow`, both deprecated in Compose
 * foundation with no maintained successor. Items past [maxVisible] are never composed, so hidden
 * items cost nothing (no image decoding, no state, no DB observers). Nothing is wrapped: each item
 * stays a direct child of the enclosing layout.
 *
 * @param totalCount total number of items the list stands for, when [items] is already a truncated
 * page (e.g. the first 15 rows of a query while the real count comes from a COUNT query). Defaults
 * to `items.size`.
 * @param itemContent receives `hiddenCount = totalCount - visibleCount` for the last visible item
 * when something is hidden, 0 otherwise.
 */
@Composable
inline fun <T> OverflowItems(
    items: List<T>,
    maxVisible: Int,
    totalCount: Int = items.size,
    itemContent: @Composable (index: Int, item: T, hiddenCount: Int) -> Unit,
) {
    val visibleCount = minOf(items.size, maxVisible)
    val hiddenCount = (totalCount - visibleCount).coerceAtLeast(0)
    for (index in 0 until visibleCount) {
        itemContent(index, items[index], if (index == visibleCount - 1) hiddenCount else 0)
    }
}

/** Scrim of [OverflowIndicator]. */
val OverflowScrimColor = Color.Black.copy(alpha = 0.6f)

/**
 * "+N" overlay for the last item of [OverflowItems]: a translucent scrim filling the parent [Box]
 * with the hidden count centered on it. It has no pointer input, so taps reach the item below.
 */
@Composable
fun BoxScope.OverflowIndicator(
    hiddenCount: Int,
    shape: Shape = RectangleShape,
    textStyle: TextStyle = OlvidTypography.body1,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(OverflowScrimColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.plus_count, hiddenCount),
            style = textStyle,
            color = colorResource(R.color.alwaysWhite),
        )
    }
}

@Preview
@Composable
private fun OverflowItemsPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        OverflowItems(items = List(10) { it }, maxVisible = 4) { index, _, hiddenCount ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (index % 2 == 0) Color.Gray else Color.LightGray)
            ) {
                if (hiddenCount > 0) {
                    OverflowIndicator(hiddenCount = hiddenCount)
                }
            }
        }
    }
}
