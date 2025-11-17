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

package io.olvid.messenger.discussion.message.reactions

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.AnimatedEmoji
import io.olvid.messenger.discussion.message.attachments.constantSp

@Composable
fun ReactionBar(
    modifier: Modifier = Modifier,
    preferredReactions: List<String>,
    currentReaction: String?,
    onReact: (String?) -> Unit,
    onToggleEmojiPicker: () -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    useAnimatedEmojis: Boolean = false,
    loopAnimatedEmojis: Boolean = false,
) {
    val displayReactions = remember(currentReaction, preferredReactions) {
        (listOfNotNull(currentReaction) + preferredReactions).distinct()
    }

    val listState = rememberLazyListState()
    var snapshot by remember { mutableStateOf(preferredReactions.toSet()) }
    val density = LocalDensity.current.density
    LaunchedEffect(preferredReactions) {
        if (snapshot.isNotEmpty() && snapshot.size < preferredReactions.size) {
            preferredReactions.indexOfFirst { snapshot.contains(it).not() }.takeIf { it != -1 }?.let {
                listState.animateScrollToItem((it - 2).coerceAtLeast(0), (-20 * density).toInt())
            }
        }
        snapshot = preferredReactions.toSet()
    }

    Box(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .background(
                color = colorResource(R.color.dialogBackground),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp)),
    ) {
        LazyRow(
            modifier = Modifier.widthIn(max = 380.dp),
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            items(items = displayReactions, key = { it }) { emoji ->
                Box(
                    modifier = Modifier.animateItem()
                        .width(50.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false,
                                color = colorResource(R.color.almostBlack),
                                radius = 16.dp
                            ),
                            onClick = { onReact(if (emoji == currentReaction) null else emoji) },
                            onLongClick = { onToggleFavorite(emoji) }
                        )
                        .background(
                            color = if (emoji == currentReaction) colorResource(R.color.blueOverlay) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (emoji == currentReaction) colorResource(R.color.olvid_gradient_light) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(6.dp)
                ) {
                    if (useAnimatedEmojis) {
                        AnimatedEmoji(
                            size = 32f,
                            scaleFont = false,
                            loop = loopAnimatedEmojis,
                            shortEmoji = emoji,
                            ignoreClicks = true,
                        )
                    } else {
                        Text(
                            text = emoji,
                            maxLines = 1,
                            fontSize = constantSp(32)
                        )
                    }
                }
            }
        }
        IconButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colorResource(R.color.dialogBackground),
                            colorResource(R.color.dialogBackground)
                        )
                    )
                )
                .padding(2.dp)
                .background(
                    color = colorResource(R.color.dialogBackgroundOverlay),
                    shape = RoundedCornerShape(14.dp)
                ),
            onClick = onToggleEmojiPicker,
        ) {
            Icon(
                Icons.Default.Add,
                tint = colorResource(R.color.almostBlack),
                contentDescription = "Toggle emoji picker"
            )
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun ReactionBarPreview() {
    ReactionBar(
        preferredReactions = listOf("👍", "👎", "🫥", "😶‍🌫️", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "🫨"),
        currentReaction = "👍",
        onReact = {},
        onToggleEmojiPicker = {}
    )
}