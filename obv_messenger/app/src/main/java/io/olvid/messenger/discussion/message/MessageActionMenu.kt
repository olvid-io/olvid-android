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
package io.olvid.messenger.discussion.message

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.UpdateReactionsTask
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.discussion.DiscussionActivity.DiscussionDelegate
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.message.reactions.ReactionBar
import io.olvid.messenger.discussion.message.reactions.ReactionViewModel
import io.olvid.messenger.discussion.message.reactions.emoji.EmojiPicker
import io.olvid.messenger.discussion.message.reactions.emoji.EmojiSearchViewModel
import io.olvid.messenger.settings.SettingsActivity.Companion.loopAnimatedEmojis
import io.olvid.messenger.settings.SettingsActivity.Companion.useAnimatedEmojis
import kotlinx.coroutines.delay


@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MessageActionMenu(
    message: Message,
    discussion: Discussion,
    discussionDelegate: DiscussionDelegate,
    discussionViewModel: DiscussionViewModel?,
    linkPreviewViewModel: LinkPreviewViewModel?,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope
) {
    val haptic = LocalHapticFeedback.current
    var currentReaction by remember { mutableStateOf<String?>(null) }
    var canEdit by remember { mutableStateOf(false) }

    val emojiSearchViewModel: EmojiSearchViewModel = viewModel()
    val reactionViewModel: ReactionViewModel = viewModel()
    val preferredReactions by reactionViewModel.preferredReactions.observeAsState(emptyList())
    val recentReactions by reactionViewModel.recentReactions.observeAsState(emptyList())


    val emojiPickerGridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    }

    var showEmojiPicker: Boolean? by remember { mutableStateOf(null) }
    LaunchedEffect(message.id) {
        delay(100)
        showEmojiPicker = false
    }

    LaunchedEffect(message.id) {
        App.runThread {
            val db = AppDatabase.getInstance()
            currentReaction = db.reactionDao().getMyReactionForMessage(message.id)?.emoji
            canEdit = discussion.isNormal &&
                    (discussion.discussionType != Discussion.TYPE_GROUP_V2 ||
                            db.group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                                ?.ownPermissionEditOrRemoteDeleteOwnMessages == true)
        }
    }

    val onReact = { emoji: String? ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        App.runThread(
            UpdateReactionsTask(
                message.id,
                emoji,
                null,
                System.currentTimeMillis(),
                true
            )
        )
        if (emoji != null) {
            reactionViewModel.onReact(emoji)
        }
        onDismiss()
    }

    val onToggleFavorite = { emoji: String ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        reactionViewModel.toggleFavorite(emoji)
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {
                        emojiSearchViewModel.shownEmojiVariants.value?.let {
                            emojiSearchViewModel.shownEmojiVariants.value = null
                        } ?: run {
                            onDismiss()
                        }
                    }
                ),
        ) {
            var visible by remember { mutableStateOf(false) }
            val animatedBackgroundColor by animateColorAsState(
                targetValue = if (visible) colorResource(R.color.blackDarkOverlay) else Color.Transparent
            )
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 44.dp), // compose_message_edit_text
                shape = RectangleShape,
                color = animatedBackgroundColor,
                contentColor = colorResource(R.color.almostBlack)
            ) {
                LaunchedEffect(Unit) {
                    visible = true
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxWidthDp = maxWidth
                    val largeScreen =
                        maxWidth > 600.dp && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                    val showSender = message.isInbound && discussion.discussionType != Discussion.TYPE_CONTACT

                    val messageContent: @Composable (((LayoutCoordinates) -> Unit)?) -> Unit = @Composable { onMessageGloballyPositioned ->
                        Column(
                            modifier = Modifier.heightIn(max = 280.dp),
                            horizontalAlignment = if (message.isInbound) Alignment.Start else Alignment.End
                        ) {
                            var showReactionBar by remember { mutableStateOf(false) }
                            AnimatedVisibility(
                                visible = showReactionBar,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ReactionBar(
                                    modifier = Modifier
                                        .cutoutHorizontalPadding()
                                        .systemBarsHorizontalPadding(),
                                    preferredReactions = preferredReactions,
                                    currentReaction = currentReaction,
                                    onReact = onReact,
                                    onToggleEmojiPicker = {
                                        showEmojiPicker = !(showEmojiPicker ?: false)
                                    },
                                    onToggleFavorite = onToggleFavorite,
                                    useAnimatedEmojis = remember { useAnimatedEmojis() },
                                    loopAnimatedEmojis = remember { loopAnimatedEmojis() },
                                )
                            }
                            LaunchedEffect(message.isReactable, discussion.isNormalOrReadOnly) {
                                showReactionBar = message.isReactable && discussion.isNormalOrReadOnly
                            }
                            val messageExpiration by AppDatabase.getInstance()
                                .messageExpirationDao().getLive(message.id)
                                .observeAsState()
                            Message(
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .sharedElementWithCallerManagedVisibility(
                                        visible = true,
                                        sharedContentState = rememberSharedContentState(message.id)
                                    )
                                    .padding(8.dp)
                                    .cutoutHorizontalPadding()
                                    .systemBarsHorizontalPadding(),
                                message = message,
                                discussionViewModel = discussionViewModel,
                                messageExpiration = messageExpiration,
                                linkPreviewViewModel = linkPreviewViewModel,
                                showSender = showSender,
                                lastFromSender = true,
                                openOnClick = false,
                                blockSwipe = true,
                                blockClicks = true,
                                fullWidth = true,
                                useAnimatedEmojis = remember { useAnimatedEmojis() },
                                loopAnimatedEmojis = remember { loopAnimatedEmojis() },
                                onMessageGloballyPositioned = onMessageGloballyPositioned
                            )
                        }
                    }

                    val actionsAndPicker = @Composable {
                        var minHeight by remember(largeScreen) { mutableIntStateOf(0) }
                        val density = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .clipToBounds()
                                .heightIn(min = (minHeight / density.density)
                                    .coerceAtLeast(if (message.isReactable) 368f else 0f).dp) // make sure the minHeight is at least the height of the emoji picker
                                .cutoutHorizontalPadding()
                                .systemBarsHorizontalPadding(),
                            contentAlignment = if (message.isInbound) Alignment.TopStart else Alignment.TopEnd
                        ) {
                            AnimatedVisibility(
                                visible = showEmojiPicker == true,
                                enter = fadeIn()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .sharedBounds(
                                            rememberSharedContentState(key = "actions-picker"),
                                            animatedVisibilityScope = this,
                                        )
                                        .clickable(
                                            interactionSource = null,
                                            indication = null,
                                            onClick = {}
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colorResource(R.color.dialogBackground),
                                        contentColor = colorResource(R.color.almostBlack)
                                    ),
                                ) {
                                    Column {
                                        SearchBar(
                                            modifier = Modifier.padding(8.dp),
                                            placeholderText = stringResource(R.string.hint_search_emoji),
                                            searchText = emojiSearchViewModel.searchText,
                                            onSearchTextChanged = {
                                                emojiSearchViewModel.onSearchTextChanged(
                                                    it
                                                )
                                            },
                                            onFocus = {
                                                emojiSearchViewModel.shownEmojiVariants.value?.let {
                                                    emojiSearchViewModel.shownEmojiVariants.value = null
                                                }
                                            },
                                            onClearClick = { emojiSearchViewModel.reset() },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colorResource(R.color.olvid_gradient_light),
                                                unfocusedBorderColor = colorResource(R.color.mediumGrey),
                                                cursorColor = colorResource(R.color.olvid_gradient_light)
                                            )
                                        )
                                        EmojiPicker(
                                            modifier = Modifier
                                                .height(280.dp),
                                            gridState = emojiPickerGridState,
                                            onReact = onReact,
                                            onToggleFavorite = onToggleFavorite,
                                            isSearch = emojiSearchViewModel.searchText.isNotEmpty(),
                                            recentEmojis = recentReactions,
                                            emojis = emojiSearchViewModel.emojis,
                                            shownEmojiVariants = emojiSearchViewModel.shownEmojiVariants
                                        )
                                    }
                                }
                            }
                            // first animation should expand vertically, the following ones should fadeIn
                            var enterAnim by remember { mutableStateOf(expandVertically()) }
                            AnimatedVisibility(
                                visible = showEmojiPicker == false,
                                enter = enterAnim,
                                exit = fadeOut()
                            ) {
                                emojiSearchViewModel.reset()
                                Card(
                                    modifier = Modifier
                                        .onGloballyPositioned { layoutCoordinates ->
                                            if (minHeight == 0) {
                                                minHeight = layoutCoordinates.size.height
                                            }
                                        }
                                        .padding(8.dp)
                                        .sharedBounds(
                                            rememberSharedContentState(key = "actions-picker"),
                                            animatedVisibilityScope = this,
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colorResource(R.color.dialogBackground),
                                        contentColor = colorResource(R.color.almostBlack)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    MessageActionItemsList(
                                        canEdit = canEdit,
                                        message = message,
                                        discussion = discussion,
                                        discussionDelegate = discussionDelegate,
                                        onDismiss = onDismiss,
                                    )
                                }
                            }
                            LaunchedEffect(Unit) {
                                delay(500)
                                enterAnim = fadeIn()
                            }
                        }
                    }

                    if (largeScreen && (message.isInbound || message.messageType == Message.TYPE_OUTBOUND_MESSAGE)) { // never use landscape layout for system messages
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = if (message.isInbound) Alignment.CenterStart else Alignment.CenterEnd,
                        ) {
                            var messageLayout: LayoutCoordinates? by remember { mutableStateOf(null) }
                            val onMessageGloballyPositioned: ((LayoutCoordinates) -> Unit)? = { layout ->
                                messageLayout = layout
                            }
                            messageContent(onMessageGloballyPositioned)

                            messageLayout?.size?.width?.let { messageWidth ->
                                val density = LocalDensity.current.density
                                val availableWidth = maxWidthDp - // total screen width
                                        8.dp - // message outer padding
                                        (messageWidth / density + if (showSender) 40 else 0 // account for the initial view
                                                ).coerceAtLeast(380f).dp - // message width, with a minimum equal to reaction bar width
                                        WindowInsets.safeDrawing.asPaddingValues().let { // also remove insets
                                            it.calculateStartPadding(LayoutDirection.Ltr) + it.calculateEndPadding(LayoutDirection.Ltr)
                                        }
                                Box(
                                    modifier = Modifier
                                        .cutoutHorizontalPadding()
                                        .systemBarsHorizontalPadding()
                                        .width(availableWidth)
                                        .align(if (message.isInbound) Alignment.CenterEnd else Alignment.CenterStart),
                                    contentAlignment = if (message.isInbound) Alignment.CenterStart else Alignment.CenterEnd,
                                ) {
                                    actionsAndPicker()
                                }
                            }
                        }
                    } else { // Portrait
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = if (message.isInbound) Alignment.Start else Alignment.End,
                            verticalArrangement = Arrangement.Center
                        ) {
                            messageContent(null)
                            actionsAndPicker()
                        }
                    }
                }
            }
        }
    }
}