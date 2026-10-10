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

package io.olvid.messenger.discussion.message.reactions.emoji

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.EmojiList
import io.olvid.messenger.designsystem.components.AnimatedEmoji
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.constantSp
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.launch


@DrawableRes
fun EmojiList.EmojiGroup.getIcon(): Int {
    return when (this) {
        EmojiList.EmojiGroup.SMILEYS_EMOTION -> R.drawable.emoji_smileys
        EmojiList.EmojiGroup.PEOPLE_BODY -> R.drawable.emoji_people
        EmojiList.EmojiGroup.ANIMALS_NATURE -> R.drawable.emoji_animals
        EmojiList.EmojiGroup.FOOD_DRINK -> R.drawable.emoji_food
        EmojiList.EmojiGroup.TRAVEL_PLACES -> R.drawable.emoji_transportation
        EmojiList.EmojiGroup.ACTIVITIES -> R.drawable.emoji_activities
        EmojiList.EmojiGroup.OBJECTS -> R.drawable.emoji_objects
        EmojiList.EmojiGroup.SYMBOLS -> R.drawable.emoji_symbols
        EmojiList.EmojiGroup.FLAGS -> R.drawable.emoji_flags
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    },
    currentReaction: String? = null,
    onReact: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    isSearch: Boolean = false,
    recentEmojis: List<String> = emptyList(),
    recentsAsColumn: Boolean = false,
    emojis: List<List<String>> = EmojiList.EMOJIS,
    onBackSpace: (() -> Unit)? = null,
    shownEmojiVariants: MutableState<List<String>?> = remember { mutableStateOf(null) },
) {
    val coroutineScope = rememberCoroutineScope()
    val useAnimatedEmojis = remember { SettingsActivity.useAnimatedEmojis() }
    val loopAnimatedEmojis = remember { SettingsActivity.loopAnimatedEmojis() }

    Box(modifier = modifier) {
        Column {
            AnimatedVisibility(visible = isSearch.not()) {
                val selectedTabIndex by remember {
                    derivedStateOf {
                        EmojiList.EmojiGroup.entries.indexOfLast {
                            it.index <= gridState.firstVisibleItemIndex + 5
                        }.coerceAtLeast(0)
                    }
                }
                PrimaryTabRow(
                    containerColor = Color.Transparent,
                    contentColor = colorResource(R.color.almostBlack),
                    divider = {
                        Spacer(modifier = Modifier.height(4.dp))
                    },
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                selectedTabIndex,
                                matchContentSize = true
                            ),
                            color = colorResource(R.color.olvid_gradient_light)
                        )
                    },
                    selectedTabIndex = selectedTabIndex
                ) {
                    EmojiList.EmojiGroup.entries.forEachIndexed { index, emojiGroup ->
                        Tab(
                            selected = selectedTabIndex == index,
                            selectedContentColor = colorResource(R.color.almostBlack),
                            unselectedContentColor = colorResource(R.color.greyTint),
                            onClick = {
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(
                                        emojiGroup.index
                                    )
                                }
                            })
                        {
                            Icon(
                                modifier = Modifier
                                    .requiredSize(32.dp)
                                    .padding(bottom = 4.dp),
                                painter = painterResource(id = emojiGroup.getIcon()),
                                contentDescription = null
                            )
                        }
                    }
                    onBackSpace?.let { onBackSpace ->
                        Tab(selected = false, onClick = onBackSpace) {
                            Icon(
                                modifier = Modifier
                                    .requiredSize(32.dp)
                                    .padding(
                                        start = 4.dp,
                                        end = 4.dp,
                                        bottom = 4.dp
                                    ),
                                painter = painterResource(id = R.drawable.ic_backspace),
                                contentDescription = "backspace"
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isSearch && emojis.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.explanation_empty_global_search),
                    color = colorResource(R.color.greyTint),
                    textAlign = TextAlign.Center
                )
            } else {
                val itemWidth = 40.dp
                AnimatedVisibility(!recentsAsColumn && recentEmojis.isNotEmpty() && !isSearch) {
                    Column {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(R.string.label_emoji_recents),
                            style = OlvidTypography.body2.copy(fontWeight = FontWeight.Medium),
                            color = colorResource(R.color.almostBlack)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(items = recentEmojis, key = { it }) { recentEmoji ->
                                RecentEmojiItem(
                                    modifier = Modifier.animateItem(),
                                    emoji = recentEmoji,
                                    itemWidth = itemWidth,
                                    useAnimatedEmojis = useAnimatedEmojis,
                                    loopAnimatedEmojis = loopAnimatedEmojis,
                                    onClick = { onReact(recentEmoji) },
                                    onLongClick = { onToggleFavorite(recentEmoji) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(recentsAsColumn && recentEmojis.isNotEmpty() && !isSearch) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    modifier = Modifier
                                        .padding(top = 4.dp, bottom = 8.dp)
                                        .size(20.dp),
                                    painter = painterResource(R.drawable.ic_time),
                                    tint = colorResource(R.color.greyTint),
                                    contentDescription = stringResource(R.string.label_emoji_recents)
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .width(itemWidth + 8.dp)
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(items = recentEmojis, key = { it }) { recentEmoji ->
                                        RecentEmojiItem(
                                            modifier = Modifier.animateItem(),
                                            emoji = recentEmoji,
                                            itemWidth = itemWidth,
                                            useAnimatedEmojis = useAnimatedEmojis,
                                            loopAnimatedEmojis = loopAnimatedEmojis,
                                            onClick = { onReact(recentEmoji) },
                                            onLongClick = { onToggleFavorite(recentEmoji) }
                                        )
                                    }
                                }
                            }
                            VerticalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = colorResource(R.color.lightGrey)
                            )
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val maxItemsInRow = if (itemWidth > 0.dp) (maxWidth / itemWidth).toInt() else 0
                        val filteredEmojis = remember(emojis) { emojis.filter { it.isNotEmpty() } }
                        LazyHorizontalGrid(
                            modifier = Modifier.wrapContentHeight(),
                            state = gridState,
                            rows = if (isSearch && filteredEmojis.isNotEmpty() && filteredEmojis.size <= maxItemsInRow) GridCells.Fixed(
                                1
                            ) else GridCells.Adaptive(itemWidth)
                        ) {
                            items(
                                items = filteredEmojis,
                                key = { it.first() }) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(
                                                bounded = false,
                                                color = colorResource(R.color.almostBlack),
                                                radius = itemWidth
                                            ),
                                            onClick = {
                                                onReact(emoji.first())
                                            },
                                            onLongClick = {
                                                if (emoji.size > 1) {
                                                    shownEmojiVariants.value = emoji
                                                } else {
                                                    onToggleFavorite(emoji.first())
                                                }
                                            }
                                        )
                                        .padding(horizontal = 4.dp)
                                        .animateItem()
                                ) {
                                    // we never animate the full picker, this makes the app lag too much
    //                                if (useAnimatedEmojis) {
    //                                    AnimatedEmoji(
    //                                        size = 32f,
    //                                        scaleFont = false,
    //                                        shortEmoji = emoji.first(),
    //                                        loop = loopAnimatedEmojis,
    //                                        ignoreClicks = true
    //                                    )
    //                                } else {
                                    Text(
                                        text = emoji.first(),
                                        fontSize = constantSp(32)
                                    )
    //                                }
                                    if (emoji.size > 1) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.emoji_variants_corner),
                                            contentDescription = null,
                                            modifier = Modifier.align(
                                                Alignment.TopEnd
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = {
                        shownEmojiVariants.value = null
                    }
                ),
            visible = shownEmojiVariants.value != null,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(R.color.almostWhite)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(8.dp),
                        maxItemsInEachRow = if ((shownEmojiVariants.value?.size ?: 0) > 6) 5 else 6,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        shownEmojiVariants.value?.let {
                            // for emojis with 25 variants, put the default color at the end for better alignment
                            if (it.size > 6) {
                                it.subList(1, it.size) + it[0]
                            } else {
                                it
                            }
                        }?.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(
                                            bounded = false,
                                            color = colorResource(R.color.almostBlack),
                                            radius = 40.dp
                                        ),
                                        onClick = {
                                            shownEmojiVariants.value = null
                                            onReact(emoji)
                                        },
                                        onLongClick = { onToggleFavorite(emoji) }
                                    )
                                    .background(
                                        color = if (emoji == currentReaction) colorResource(R.color.blueOverlay) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (emoji == currentReaction) colorResource(R.color.olvid_gradient_light) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                if (useAnimatedEmojis) {
                                    AnimatedEmoji(
                                        shortEmoji = emoji,
                                        size = 32f,
                                        scaleFont = false,
                                        ignoreClicks = true,
                                        loop = loopAnimatedEmojis,
                                    )
                                } else {
                                    Text(
                                        text = emoji,
                                        fontSize = constantSp(32)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentEmojiItem(
    modifier: Modifier = Modifier,
    emoji: String,
    itemWidth: Dp,
    useAnimatedEmojis: Boolean,
    loopAnimatedEmojis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = false,
                    color = colorResource(R.color.almostBlack),
                    radius = itemWidth
                ),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp)
    ) {
        if (useAnimatedEmojis) {
            AnimatedEmoji(
                size = 32f,
                scaleFont = false,
                shortEmoji = emoji,
                loop = loopAnimatedEmojis,
                ignoreClicks = true
            )
        } else {
            Text(
                text = emoji,
                fontSize = constantSp(32)
            )
        }
    }
}

@Composable
fun SearchableEmojiPicker(
    modifier: Modifier = Modifier,
    emojiSearchState: EmojiSearchState,
    focusState: MutableState<Boolean> = remember { mutableStateOf(false) },
    searchBarModifier: Modifier = Modifier
        .padding(8.dp)
        .height(40.dp),
    fillRemainingHeight: Boolean = false, // picker takes the remaining height instead of a fixed 280.dp
    searchOnFocus: Boolean = true, // switch to search results as soon as the search bar gains focus
    currentReaction: String? = null,
    recentEmojis: List<String> = emptyList(),
    recentsAsColumn: Boolean = false,
    onReact: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onBackSpace: (() -> Unit)? = null,
) {
    LaunchedEffect(focusState.value) {
        if (focusState.value) {
            emojiSearchState.shownEmojiVariants.value = null
        }
    }
    Column(modifier = modifier) {
        SearchBar(
            modifier = searchBarModifier,
            placeholderText = stringResource(R.string.hint_search_emoji),
            searchText = emojiSearchState.searchText,
            onSearchTextChanged = {
                emojiSearchState.onSearchTextChanged(it)
            },
            focusState = focusState,
            onClearClick = { emojiSearchState.reset() },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorResource(R.color.olvid_gradient_light),
                unfocusedBorderColor = colorResource(R.color.mediumGrey),
                cursorColor = colorResource(R.color.olvid_gradient_light)
            )
        )
        EmojiPicker(
            modifier = if (fillRemainingHeight) Modifier.weight(1f, true) else Modifier.height(280.dp),
            currentReaction = currentReaction,
            onReact = onReact,
            onToggleFavorite = onToggleFavorite,
            isSearch = emojiSearchState.searchText.isNotEmpty() || (searchOnFocus && focusState.value),
            recentEmojis = recentEmojis,
            recentsAsColumn = recentsAsColumn,
            emojis = emojiSearchState.emojis,
            onBackSpace = onBackSpace,
            shownEmojiVariants = emojiSearchState.shownEmojiVariants
        )
    }
}

@Composable
@Preview
fun PreviewEmojiPicker() {
    EmojiPicker(
        modifier = Modifier
            .padding(8.dp)
            .height(220.dp)
    )
}