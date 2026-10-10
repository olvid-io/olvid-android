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

package io.olvid.messenger.group.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.ExistingDiscussion
import io.olvid.messenger.group.GroupCreationViewModel
import io.olvid.messenger.main.discussions.LastMessageAttachments
import io.olvid.messenger.main.discussions.getAnnotatedBody

fun NavGraphBuilder.existingDiscussions(
    groupCreationViewModel: GroupCreationViewModel,
    onOpenDiscussion: (Discussion) -> Unit,
    onCreateNewGroup: () -> Unit,
    onBack: () -> Unit,
) {
    composable(
        Routes.EXISTING_DISCUSSIONS,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) },
    ) {
        BackHandler {
            onBack()
        }
        ExistingDiscussionsScreen(
            selectedMembers = groupCreationViewModel.selectedContacts.value.map { it.toGroupMember() },
            existingDiscussions = groupCreationViewModel.existingDiscussions.value,
            onOpenDiscussion = onOpenDiscussion,
            onCreateNewGroup = onCreateNewGroup,
        )
    }
}

@Composable
fun ExistingDiscussionsScreen(
    selectedMembers: List<GroupMember>,
    existingDiscussions: List<ExistingDiscussion>,
    onOpenDiscussion: (Discussion) -> Unit,
    onCreateNewGroup: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                .asPaddingValues() + PaddingValues(top = 16.dp, bottom = 64.dp)
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.lighterGrey))
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        text = pluralStringResource(R.plurals.explanation_existing_discussions, existingDiscussions.size, existingDiscussions.size),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint)
                    )
                    MembersRow(members = selectedMembers)
                }
            }
            itemsIndexed(
                items = existingDiscussions,
                key = { _, existingDiscussion -> existingDiscussion.discussion.id }
            ) { index, existingDiscussion ->
                Column(
                    modifier = Modifier
                        .then(
                            if (index == 0) {
                                if (existingDiscussions.size == 1) {
                                    Modifier.clip(RoundedCornerShape(16.dp))
                                } else {
                                    Modifier.clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp
                                        )
                                    )
                                }
                            } else if (index == existingDiscussions.size - 1) {
                                Modifier.clip(
                                    RoundedCornerShape(
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                            } else {
                                Modifier
                            }
                        )
                        .background(colorResource(R.color.lighterGrey))
                ) {
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                            thickness = 1.dp,
                            color = colorResource(R.color.lightGrey)
                        )
                    }
                    ExistingDiscussionItem(
                        existingDiscussion = existingDiscussion,
                        onOpenDiscussion = onOpenDiscussion
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colorResource(R.color.whiteOverlay)),
            contentAlignment = Alignment.BottomCenter
        ) {
            OlvidActionButton(
                modifier = Modifier
                    .safeDrawingPadding()
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(R.string.button_label_create_a_new_group),
                onClick = onCreateNewGroup
            )
        }
    }
}

@Composable
private fun ExistingDiscussionItem(
    existingDiscussion: ExistingDiscussion,
    onOpenDiscussion: (Discussion) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colorResource(R.color.greyOverlay)),
                onClick = { onOpenDiscussion(existingDiscussion.discussion) },
            )
            .padding(4.dp)
            .height(Min)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .requiredSize(48.dp),
            target = existingDiscussion.discussion,
        )
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .padding(start = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = existingDiscussion.discussion.title.orEmpty(),
                color = colorResource(R.color.primary700),
                style = OlvidTypography.h3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            existingDiscussion.lastMessage?.let { lastMessage ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        painter = painterResource(R.drawable.ic_time),
                        tint = colorResource(R.color.greyTint),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.label_last_message_date,
                            StringUtils.getDateString(context, lastMessage.timestamp)
                        ),
                        color = colorResource(R.color.greyTint),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val lastMessage = existingDiscussion.lastMessage
            val body = existingDiscussion.discussion.getAnnotatedBody(context, lastMessage)
            val preview = buildAnnotatedString {
                // getAnnotatedBody only prefixes the sender for inbound group messages — mirror it for own messages
                if (lastMessage?.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(
                            stringResource(
                                R.string.text_inbound_group_message_prefix,
                                stringResource(R.string.text_you)
                            )
                        )
                    }
                }
                append(body)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (preview.isEmpty().not()) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = preview,
                        color = colorResource(R.color.greyTint),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // attachments summary on the same line, same logic as the discussion list (location messages render as text)
                lastMessage?.takeIf { it.isLocationMessage.not() && it.totalAttachmentCount > 0 }?.let { message ->
                    LastMessageAttachments(
                        fileCount = message.totalAttachmentCount - message.imageAndVideoCount - message.audioCount,
                        imageCount = message.imageAndVideoCount - message.videoCount,
                        videoCount = message.videoCount,
                        audioCount = message.audioCount,
                        firstFileName = message.firstAttachmentName,
                        useTwoLines = false,
                    )
                }
            }
        }
        OlvidTextButton(
            text = stringResource(R.string.button_label_open),
            onClick = { onOpenDiscussion(existingDiscussion.discussion) }
        )
    }
}
