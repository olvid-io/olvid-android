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

package io.olvid.messenger.discussion.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.discussion.message.reactions.emoji.rememberEmojiSearchState
import io.olvid.messenger.discussion.message.reactions.emoji.SearchableEmojiPicker

@Composable
fun EmojiKeyboard(
    insertEmoji: (String) -> Unit,
    onBackSpace: () -> Unit,
    onDismiss: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    focusState: MutableState<Boolean>,
    height: Dp
) {
    val emojiSearchState = rememberEmojiSearchState()
    val emojiKeyboardViewModel: EmojiKeyboardViewModel = viewModel()

    // reload the recents column once per keyboard opening: its order stays frozen while open
    LaunchedEffect(Unit) {
        emojiKeyboardViewModel.refreshRecentEmojis()
    }

    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(LocalDensity.current).toDp()
    }
    val showBottomRow = statusBarHeight < 40.dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.almostWhite),
            contentColor = colorResource(R.color.almostBlack),
        ),
        shape = RectangleShape,
    ) {
        Column(
            Modifier
                .cutoutHorizontalPadding()
                .systemBarsHorizontalPadding()
                .then(
                    if (showBottomRow)
                        Modifier
                            .consumeWindowInsets(PaddingValues(bottom = 40.dp))
                            .then(
                                if (focusState.value) {
                                    Modifier.imePadding()
                                } else {
                                    Modifier
                                }
                            )
                            .height(height - 40.dp)
                    else
                        Modifier
                            .navigationBarsPadding()
                            .then(
                                if (focusState.value) {
                                    Modifier.imePadding()
                                } else {
                                    Modifier
                                }
                            )
                            .height(height - statusBarHeight)
                )
        ) {
            SearchableEmojiPicker(
                emojiSearchState = emojiSearchState,
                focusState = focusState,
                searchBarModifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .height(40.dp),
                fillRemainingHeight = true,
                searchOnFocus = false,
                onReact = { emoji ->
                    insertEmoji(emoji)
                    emojiKeyboardViewModel.onEmojiInserted(emoji)
                },
                recentEmojis = emojiKeyboardViewModel.recentEmojis,
                recentsAsColumn = true,
                onBackSpace = onBackSpace
            )
        }
        if (showBottomRow) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .cutoutHorizontalPadding()
                    .systemBarsHorizontalPadding()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    modifier = Modifier.size(width = 80.dp, height = 40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colorResource(R.color.greyTint)
                    ),
                    onClick = onDismiss
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                    )
                }

                IconButton(
                    modifier = Modifier.size(width = 80.dp, height = 40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colorResource(R.color.greyTint)
                    ),
                    onClick = onSwitchToKeyboard
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_keyboard),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
