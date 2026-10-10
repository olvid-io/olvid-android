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

package io.olvid.messenger.discussion.message.reactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.Reaction
import io.olvid.messenger.designsystem.components.AnimatedCounter
import io.olvid.messenger.databases.tasks.UpdateReactionsTask
import io.olvid.messenger.designsystem.components.AnimatedEmoji
import io.olvid.messenger.designsystem.dampened
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.main.contacts.ContactListItem

data class ReactionItem(val emoji: String = "", val isMine: Boolean = false, val count: Int = 0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Reactions(
    modifier: Modifier = Modifier,
    message: Message,
    scale: Float = 1f,
    useAnimatedEmojis: Boolean = true,
    loopAnimatedEmojis: Boolean = true,
    blockClicks: Boolean,
) {
    val dampenedScale = scale.dampened()

    val reactionItems: List<ReactionItem> = remember(message.reactions) {
        emptyList<ReactionItem>().toMutableList().apply {
            message.reactions?.takeIf { it.isNotEmpty() }
                ?.split(":")?.filter { it.isNotEmpty() }?.takeIf { it.size % 2 == 0 }
                ?.let { splitReactions ->
                    for (i in splitReactions.indices step 2) {
                        add(
                            ReactionItem(
                                splitReactions[i].removePrefix("|"),
                                splitReactions[i].startsWith("|"),
                                splitReactions[i + 1].toInt()
                            )
                        )
                    }
                }
        }
            .apply { if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) reverse() }
    }

    // keep the last non-empty content so the row still has something to show while animating out
    var displayedItems by remember { mutableStateOf(reactionItems) }
    if (reactionItems.isNotEmpty() && reactionItems != displayedItems) {
        displayedItems = reactionItems
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = reactionItems.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = .6f) + expandVertically(),
        // scale/fade the pill out in place first, only then collapse its space: a simultaneous
        // shrink clips the (barely 20dp tall) row away before the scale/fade can be noticed
        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                scaleOut(targetScale = .6f, animationSpec = tween(durationMillis = 150)) +
                shrinkVertically(animationSpec = tween(durationMillis = 150, delayMillis = 150)),
    ) {
        var openReactionPopup by remember {
            mutableStateOf(false)
        }
        Box(
            modifier = Modifier
                .requiredHeightIn(min = 20.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(
                        placeable.width,
                        (placeable.height - 4.dp.roundToPx())
                    ) { placeable.place(0, -4.dp.roundToPx()) }
                }
        ) {
            if (openReactionPopup) {
                val reactionsDetail by AppDatabase.getInstance().reactionDao()
                    .getAllNonNullForMessageSortedByTimestampLiveData(message.id)
                    .observeAsState()
                val reactionViewModel: ReactionViewModel = viewModel()
                val preferredReactions by reactionViewModel.preferredReactions.observeAsState()
                reactionsDetail?.let {
                    if (it.isEmpty()) {
                        openReactionPopup = false
                    }
                    ModalBottomSheet(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        containerColor = colorResource(id = R.color.dialogBackground),
                        onDismissRequest = { openReactionPopup = false }) {
                        ReactionsDetail(
                            reactions = it,
                            useAnimatedEmojis = useAnimatedEmojis,
                            loopAnimatedEmojis = loopAnimatedEmojis,
                            preferredReactions = preferredReactions.orEmpty(),
                            onRemoveMyReaction = {
                                App.runThread(
                                    UpdateReactionsTask(
                                        message.id,
                                        null,
                                        null,
                                        System.currentTimeMillis(),
                                        true
                                    )
                                )
                            },
                            onToggleFavorite = reactionViewModel::toggleFavorite
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier
                    .animateContentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        color = colorResource(id = R.color.lighterGrey),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = colorResource(id = R.color.almostWhite),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(1.dp)
                    .wrapContentWidth(if (message.isInbound) Alignment.Start else Alignment.End)
                    .then(
                        if (blockClicks)
                            Modifier
                        else
                            Modifier.clickable { openReactionPopup = true }
                    ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                displayedItems.forEach {
                    key(it.emoji) {
                        Row(
                            modifier = if (it.isMine) Modifier
                                .background(
                                    color = colorResource(id = R.color.blueOverlay),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = colorResource(id = R.color.olvid_gradient_light),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                            else Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (useAnimatedEmojis) {
                                AnimatedEmoji(
                                    modifier = Modifier.widthIn(max = 40.dp * dampenedScale),
                                    shortEmoji = it.emoji,
                                    loop = loopAnimatedEmojis,
                                    onClick = { openReactionPopup = true },
                                    size = 14f * dampenedScale
                                )
                            } else {
                                Text(
                                    modifier = Modifier.widthIn(max = 40.dp * dampenedScale),
                                    text = it.emoji,
                                    maxLines = 1,
                                    fontSize = 14.sp * dampenedScale,
                                    color = colorResource(
                                        id = R.color.greyTint
                                    )
                                )
                            }
                            AnimatedCounter(
                                count = it.count,
                                style = TextStyle(fontSize = 10.sp * dampenedScale),
                                color = colorResource(
                                    id = R.color.almostBlack
                                ),
                                minCountToShow = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionsDetail(
    reactions: List<Reaction>,
    useAnimatedEmojis: Boolean = true,
    loopAnimatedEmojis: Boolean = true,
    preferredReactions: List<String> = emptyList(),
    onRemoveMyReaction: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
) {
    val reactionTabs = remember(reactions) {
        reactions.groupBy(keySelector = { it.emoji }).map {
            ReactionItem(
                emoji = it.key ?: "",
                count = it.value.size
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        var selectedTab: String? by remember {
            mutableStateOf(null)
        }
        LaunchedEffect(reactions, selectedTab) {
            if (selectedTab != null && reactions.none { it.emoji == selectedTab }) {
                selectedTab = null
            }
        }
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        LazyColumn(modifier = Modifier.height((24 + 48 * reactions.size).coerceAtMost(300).dp)) {
            items(items = reactions.filter { if (selectedTab != null) it.emoji == selectedTab else true }, key = {it -> BytesKey(it.bytesIdentity) }) { reaction ->
                val contactName = remember(reaction.bytesIdentity) {
                    ContactCacheSingleton.getContactDetailsSingleLine(
                        reaction.bytesIdentity
                            ?: AppSingleton.getBytesCurrentIdentity()
                    )
                }

                Box(modifier = Modifier.animateItem()) {
                    var menuOpened by remember { mutableStateOf(false) }
                    reaction.emoji?.let { emoji ->
                        ReactionMenu(
                            menuOpened = menuOpened,
                            onDismissRequest = { menuOpened = false },
                            isMine = reaction.bytesIdentity == null,
                            isFavorite = preferredReactions.contains(emoji),
                            onRemoveMyReaction = onRemoveMyReaction,
                            onToggleFavorite = { onToggleFavorite(emoji) },
                        )
                    }
                    ContactListItem(
                        padding = PaddingValues(horizontal = 8.dp),
                        title = AnnotatedString(contactName ?: stringResource(R.string.text_deleted_contact)),
                        body = AnnotatedString(
                            StringUtils.getNiceDateString(
                                context,
                                reaction.timestamp
                            ).toString()
                        ),
                        endContent = reaction.emoji?.let {
                            {
                                if (useAnimatedEmojis) {
                                    AnimatedEmoji(shortEmoji = it,
                                        loop = loopAnimatedEmojis,
                                        size = 32f,
                                        ignoreClicks = true)
                                } else {
                                    Text(text = it, fontSize = 32.sp)
                                }
                            }
                        },
                        onClick = {
                            // only make the item clickable if a contact actually exists
                            if (contactName != null) {
                                reaction.bytesIdentity?.let { contactBytes ->
                                    AppSingleton.getBytesCurrentIdentity()?.let { ownBytes ->
                                        App.openContactDetailsActivity(context, ownBytes, contactBytes)
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpened = true
                        },
                        avatar = AvatarSource.fromCache(
                            reaction.bytesIdentity
                                ?: AppSingleton.getBytesCurrentIdentity()
                                ?: byteArrayOf()
                        ),
                    )
                }
            }
        }
        LazyRow(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(reactionTabs, key = { it.emoji }) { reactionTab ->
                Row(
                    modifier = Modifier
                        .background(
                            color = colorResource(
                                id =
                                    if (selectedTab == reactionTab.emoji)
                                        R.color.greyOverlay
                                    else
                                        R.color.dialogBackground
                            ),
                            shape = RoundedCornerShape(40.dp)
                        )
                        .clip(RoundedCornerShape(40.dp))
                        .clickable {
                            selectedTab =
                                reactionTab.emoji.takeUnless { selectedTab == reactionTab.emoji }
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (useAnimatedEmojis) {
                        AnimatedEmoji(shortEmoji = reactionTab.emoji,
                            loop = loopAnimatedEmojis,
                            size = 24f,
                            ignoreClicks = true)
                    } else {
                        Text(
                            text = reactionTab.emoji,
                            style = OlvidTypography.h2
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    AnimatedCounter(
                        count = reactionTab.count,
                        style = OlvidTypography.h2,
                        color = colorResource(
                            id = R.color.primary700
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionMenu(
    menuOpened: Boolean,
    onDismissRequest: () -> Unit,
    isMine: Boolean,
    isFavorite: Boolean,
    onRemoveMyReaction: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    OlvidDropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
        if (isMine) {
            OlvidDropdownMenuItem(
                text = stringResource(id = R.string.menu_action_remove_your_reaction),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        tint = colorResource(id = R.color.red),
                    )
                },
                textColor = colorResource(id = R.color.red),
                onClick = {
                    onRemoveMyReaction()
                    onDismissRequest()
                }
            )
        }
        OlvidDropdownMenuItem(
            text = stringResource(
                id = if (isFavorite)
                    R.string.menu_action_remove_from_favorites
                else
                    R.string.menu_action_add_to_favorites
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = if (isFavorite) R.drawable.ic_star_off else R.drawable.ic_star),
                    contentDescription = null,
                    tint = colorResource(id = R.color.almostBlack),
                )
            },
            onClick = {
                onToggleFavorite()
                onDismissRequest()
            }
        )
    }
}