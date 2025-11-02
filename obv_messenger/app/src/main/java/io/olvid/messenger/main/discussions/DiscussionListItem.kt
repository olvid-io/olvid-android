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

package io.olvid.messenger.main.discussions

import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.scaledDp
import io.olvid.messenger.main.InitialView
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscussionListItem(
    modifier: Modifier = Modifier,
    title: AnnotatedString,
    body: AnnotatedString,
    date: AnnotatedString?,
    initialViewSetup: (initialView: InitialView) -> Unit,
    @ColorInt customColor: Int = 0x00ffffff,
    backgroundImageUrl: String? = null,
    unread: Boolean,
    unreadCount: Int,
    muted: Boolean,
    locked: Boolean,
    mentioned: Boolean,
    pinned: Boolean,
    locationsShared: Boolean,
    pendingContact: Boolean,
    attachmentCount: Int,
    imageAndVideoCount: Int,
    videoCount: Int,
    audioCount: Int,
    firstFileName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    reorderableScope: ReorderableCollectionItemScope? = null,
    onDragStopped: () -> Unit,
    lastOutboundMessageStatus: @Composable (() -> Unit)? = null,
    lastOutboundMessageStatusWidth: TextUnit = 0.sp,
) {
    Box(
        modifier = modifier
    ) {
        // custom background
        backgroundImageUrl?.let { model ->
            AsyncImage(
                modifier = Modifier.matchParentSize(),
                model = model,
                alpha = 0.15f,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        }

        Row(
            modifier = Modifier
                .height(Min)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = ripple().takeIf { selected.not() }
                )
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // custom color
            Box(
                modifier = Modifier
                    .requiredWidth(8.dp)
                    .fillMaxHeight()
                    .background(color = Color(customColor))
            )

            // InitialView
            InitialView(
                modifier = Modifier
                    .padding(
                        top = 12.dp,
                        start = 8.dp,
                        end = 12.dp,
                        bottom = 12.dp
                    )
                    .requiredSize(56.dp),
                initialViewSetup = initialViewSetup,
                selected = selected,
                unreadMessages = unreadCount > 0 || unread,
                locked = locked,
            )

            // content
            Column(
                modifier = Modifier
                    .weight(1f, true)
                    .padding(vertical = 12.dp)
                    .padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    // Title
                    Text(
                        modifier = Modifier.weight(1f, true),
                        text = title,
                        color = colorResource(id = R.color.almostBlack),
                        style = OlvidTypography.h3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // information
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        AnimatedVisibility(visible = pinned) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                painter = painterResource(id = R.drawable.ic_pinned_filled),
                                contentDescription = stringResource(R.string.content_description_pinned_discussion),
                                tint = colorResource(id = R.color.greyTint),
                            )
                        }

                        AnimatedVisibility(visible = locationsShared) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(id = R.drawable.ic_location_blue_32dp),
                                contentDescription = "location",
                                tint = colorResource(id = R.color.greyTint)
                            )
                        }
                        AnimatedVisibility(visible = muted) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                painter = painterResource(id = R.drawable.ic_notification_muted_filled),
                                contentDescription = stringResource(R.string.content_description_notification_muted_indicator),
                                tint = colorResource(R.color.greyTint)
                            )
                        }
                        AnimatedVisibility(visible = mentioned) {
                            Icon(
                                modifier = Modifier.padding(horizontal = 2.dp).size(20.dp),
                                painter = painterResource(id = R.drawable.ic_mentioned),
                                contentDescription = "mentioned",
                                tint = colorResource(R.color.red)
                            )
                        }
                        AnimatedVisibility(visible = pendingContact) {
                            Text(
                                modifier = Modifier
                                    .padding(start = 2.dp, end = if (unreadCount > 0) 2.dp else 0.dp)
                                    .background(
                                        color = colorResource(id = R.color.olvid_gradient_light),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2f.dp),
                                text = stringResource(R.string.label_pending),
                                style = OlvidTypography.body2,
                                color = colorResource(id = R.color.alwaysWhite)
                            )
                        }
                        AnimatedVisibility(visible = unreadCount > 0) {
                            Text(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .background(
                                        color = colorResource(id = R.color.red),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2f.dp),
                                text = "$unreadCount",
                                style = OlvidTypography.body2,
                                color = colorResource(id = R.color.alwaysWhite)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f, true))

                // Message content
                Row(
                    modifier = Modifier.heightIn(min = scaledDp(34)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(end = 8.dp)
                            .weight(1f, true),
                    ) {
                        if (body.isEmpty().not()) {
                            val inlineMap = mutableMapOf<String, InlineTextContent>()
                            val statusAndBody = lastOutboundMessageStatus?.let { status ->
                                inlineMap["status_tag"] = InlineTextContent(
                                    placeholder = Placeholder(
                                        width = lastOutboundMessageStatusWidth,
                                        height = 14.sp,
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                    ),
                                    children = {
                                        status.invoke()
                                    }
                                )
                                buildAnnotatedString {
                                    appendInlineContent("status_tag", "status")
                                    append(body)
                                }
                            } ?: body
                            Text(
                                text = statusAndBody,
                                color = colorResource(id = R.color.greyTint),
                                style = OlvidTypography.body2,
                                maxLines = if (attachmentCount > 0) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                inlineContent = inlineMap,
                            )
                        }

                        AnimatedVisibility(visible = attachmentCount > 0) {
                            Row(
                                modifier = Modifier.padding(top = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (body.isEmpty()) {
                                    LastMessageAttachments(
                                        fileCount = attachmentCount - imageAndVideoCount - audioCount,
                                        imageCount = imageAndVideoCount - videoCount,
                                        videoCount = videoCount,
                                        audioCount = audioCount,
                                        firstFileName = firstFileName,
                                        useTwoLines = body.isEmpty(),
                                        lastOutboundMessageStatus = lastOutboundMessageStatus,
                                        lastOutboundMessageStatusWidth = lastOutboundMessageStatusWidth,
                                    )
                                } else {
                                    LastMessageAttachments(
                                        fileCount = attachmentCount - imageAndVideoCount - audioCount,
                                        imageCount = imageAndVideoCount - videoCount,
                                        videoCount = videoCount,
                                        audioCount = audioCount,
                                        firstFileName = firstFileName,
                                        useTwoLines = body.isEmpty(),
                                    )
                                }
                            }
                        }
                    }

                    // Date
                    date?.let {
                        Text(
                            modifier = Modifier.align(Alignment.Bottom),
                            text = date,
                            color = colorResource(id = R.color.grey),
                            style = OlvidTypography.subtitle1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

            }

            val animatedWidth by animateDpAsState(
                targetValue = if (reorderableScope != null) 40.dp else 0.dp,
                label = "animatedWidth"
            )
            IconButton(
                modifier =
                    Modifier.width(animatedWidth)
                        .then(
                            if (reorderableScope != null)
                                with(reorderableScope) {
                                    Modifier.draggableHandle(onDragStopped = {
                                        onDragStopped()
                                    })
                                }
                            else
                                Modifier
                        ),
                onClick = {},
            ) {
                Icon(
                    modifier = Modifier.requiredSize(24.dp),
                    painter = painterResource(id = R.drawable.ic_drag_handle),
                    tint = colorResource(R.color.almostBlack),
                    contentDescription = "Reorder"
                )
            }
        }
    }
}

val inlineMap: Map<String, InlineTextContent> by lazy {
    mapOf(
        "file" to InlineTextContent(
            placeholder = Placeholder(
                width = 16.sp,
                height = 16.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            ),
            children = {
                Icon(
                    modifier = Modifier.fillMaxSize().padding(end = scaledDp(4)).offset(y = scaledDp(-1)),
                    painter = painterResource(R.drawable.attachment_file),
                    contentDescription = null,
                    tint = colorResource(R.color.greyTint),
                )
            }
        ),
        "audio" to InlineTextContent(
            placeholder = Placeholder(
                width = 16.sp,
                height = 16.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            ),
            children = {
                Icon(
                    modifier = Modifier.fillMaxSize().padding(end = scaledDp(4)),
                    painter = painterResource(R.drawable.attachment_audio),
                    contentDescription = null,
                    tint = colorResource(R.color.greyTint),
                )
            }
        ),
        "image" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 14.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            ),
            children = {
                Icon(
                    modifier = Modifier.fillMaxSize().padding(end = scaledDp(4)),
                    painter = painterResource(R.drawable.attachment_photo),
                    contentDescription = null,
                    tint = colorResource(R.color.greyTint),
                )
            }
        ),
        "video" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 12.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            ),
            children = {
                Icon(
                    modifier = Modifier.fillMaxSize().padding(end = scaledDp(4)),
                    painter = painterResource(R.drawable.attachment_video),
                    contentDescription = null,
                    tint = colorResource(R.color.greyTint),
                )
            }
        ),
    )
}


@Composable
fun LastMessageAttachments(
    fileCount: Int,
    imageCount: Int,
    videoCount: Int,
    audioCount: Int,
    firstFileName: String?,
    useTwoLines: Boolean,
    lastOutboundMessageStatus: @Composable (() -> Unit)? = null,
    lastOutboundMessageStatusWidth: TextUnit = 0.sp,
) {
    val localInlineMap = inlineMap.toMutableMap()
    val text = buildAnnotatedString {
        lastOutboundMessageStatus?.let { status ->
            localInlineMap["status_tag"] = InlineTextContent(
                placeholder = Placeholder(
                    width = lastOutboundMessageStatusWidth,
                    height = 14.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                ),
                children = {
                    status.invoke()
                }
            )
            appendInlineContent("status_tag", "status")
        }

        if (fileCount > 0) {
            appendInlineContent("file", "file")
            append("\u2060")
            if (firstFileName != null) {
                append(firstFileName)
                if (fileCount > 1) {
                    append(stringResource(R.string.attachments_files_joiner))
                    append(pluralStringResource(R.plurals.text_others, fileCount - 1, fileCount - 1))
                }
            } else {
                append(pluralStringResource(R.plurals.text_files, fileCount, fileCount))
            }
        }
        if (audioCount > 0) {
            if (fileCount > 0) {
                append(stringResource(R.string.attachments_joiner))
            }
            appendInlineContent("audio", "audio")
            append("\u2060")
            append(pluralStringResource(R.plurals.text_audios, audioCount, audioCount))
        }
        if (imageCount > 0) {
            if (fileCount > 0 || audioCount > 0) {
                append(stringResource(R.string.attachments_joiner))
            }
            appendInlineContent("image", "photo")
            append("\u2060")
            append(pluralStringResource(R.plurals.text_images, imageCount, imageCount))
        }
        if (videoCount > 0) {
            if (fileCount > 0  || audioCount > 0|| imageCount > 0) {
                append(stringResource(R.string.attachments_joiner))
            }
            appendInlineContent("video", "video")
            append("\u2060")
            append(pluralStringResource(R.plurals.text_videos, videoCount, videoCount))
        }
    }
    Text(
        text = text,
        color = colorResource(id = R.color.greyTint),
        style = OlvidTypography.body2,
        maxLines = if (useTwoLines) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        inlineContent = localInlineMap,
    )
}


@Preview
@Composable
private fun DiscussionListItemPreview() {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState
    ) { from, to -> }
    LazyColumn(
        state = lazyListState
    ) {
        item {
            ReorderableItem(
                state = reorderableState,
                enabled = true,
                key = 1,
            ) {
                DiscussionListItem(
                    modifier = Modifier.background(colorResource(R.color.almostWhite)),
                    title = AnnotatedString("Discussion Title"),
                    body = AnnotatedString("Latest message content wrapping on 2 lines"),
                    date = AnnotatedString("17:25"),
                    initialViewSetup = {},
                    unread = true,
                    unreadCount = 0,
                    muted = true,
                    mentioned = false,
                    locked = false,
                    pinned = true,
                    locationsShared = false,
                    pendingContact = true,
                    attachmentCount = 0,
                    imageAndVideoCount =  0,
                    videoCount = 0,
                    audioCount = 0,
                    firstFileName = null,
                    onClick = {},
                    onLongClick = {},
                    selected = true,
                    reorderableScope = this@ReorderableItem,
                    onDragStopped = {},
                )
            }
        }

        item {
            ReorderableItem(
                state = reorderableState,
                enabled = true,
                key = 1,
            ) {
                DiscussionListItem(
                    modifier = Modifier.background(colorResource(R.color.almostWhite)),
                    title = AnnotatedString("Discussion Title"),
                    body = AnnotatedString("Latest message content wrapping on 2 lines"),
                    date = AnnotatedString("17:25"),
                    initialViewSetup = {},
                    unread = true,
                    unreadCount = 120,
                    muted = true,
                    mentioned = true,
                    locked = false,
                    pinned = true,
                    locationsShared = true,
                    pendingContact = false,
                    attachmentCount = 0,
                    imageAndVideoCount =  0,
                    videoCount = 0,
                    audioCount = 0,
                    firstFileName = null,
                    onClick = {},
                    onLongClick = {},
                    selected = true,
                    onDragStopped = {},
                )
            }
        }
        item {
            ReorderableItem(
                state = reorderableState,
                enabled = true,
                key = 1,
            ) {
                DiscussionListItem(
                    modifier = Modifier.background(colorResource(R.color.almostWhite)),
                    title = AnnotatedString("Discussion Title"),
                    body = AnnotatedString(""),
                    date = AnnotatedString(StringUtils.getCompactDateString(LocalContext.current, 1724654987654)),
                    initialViewSetup = {},
                    unread = false,
                    unreadCount = 2,
                    muted = false,
                    mentioned = false,
                    locked = false,
                    pinned = false,
                    locationsShared = false,
                    pendingContact = false,
                    attachmentCount = 4,
                    imageAndVideoCount =  2,
                    videoCount = 0,
                    audioCount = 1,
                    firstFileName = "Business PLan.xlsx",
                    onClick = {},
                    onLongClick = {},
                    selected = true,
                    onDragStopped = {},
                )
            }
        }
    }
}