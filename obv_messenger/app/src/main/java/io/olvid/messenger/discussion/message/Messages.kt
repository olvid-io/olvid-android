/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons.Rounded
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.text.util.LinkifyCompat
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.datatypes.ObvBase64
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.DeletionChoice.LOCAL
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionSearch
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.linkpreview.LinkPreview
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.PublishedDetails
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity
import io.olvid.messenger.settings.SettingsActivity
import java.util.UUID

@Composable
fun MessageDisclaimer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = 50.dp)
            .background(
                color = colorResource(id = R.color.green),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(4.dp),
        verticalAlignment = CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lock_white),
            tint = Color.White,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            modifier = Modifier.weight(.2f, false),
            text = stringResource(id = R.string.text_discussion_end2end_disclaimer),
            style = OlvidTypography.subtitle1,
            color = Color.White
        )
    }
}

@Preview(widthDp = 300)
@Composable
private fun MessageDisclaimerPreview(modifier: Modifier = Modifier) {
    AppCompatTheme {
        MessageDisclaimer(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ScrollDownButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        modifier = modifier
            .background(
                color = colorResource(id = R.color.accent),
                shape = CircleShape
            )
            .requiredSize(40.dp),
        onClick = onClick
    ) {
        Icon(
            modifier = Modifier.requiredSize(32.dp),
            painter = painterResource(id = R.drawable.ic_down),
            tint = Color.White,
            contentDescription = null
        )
    }
}

@Preview
@Composable
private fun ScrollDownButtonPreview() {
    AppCompatTheme {
        ScrollDownButton {
        }
    }
}

@Composable
fun DateHeader(
    date: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier
                .background(
                    color = colorResource(id = R.color.primary400_90),
                    shape = CircleShape
                )
                .padding(vertical = 4.dp, horizontal = 8.dp),
            textAlign = TextAlign.Center,
            text = date,
            color = Color.White
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Message(
    modifier: Modifier = Modifier,
    message: Message,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onLocationClick: () -> Unit = {},
    onLocationLongClick: () -> Unit = {},
    onAttachmentLongClick: (fyleAndStatus: FyleAndStatus) -> Unit = {},
    onCallBackButtonClicked: (callLogId: Long) -> Unit = {},
    scrollToMessage: (messageId: Long) -> Unit,
    replyAction: (() -> Unit)?,
    menuAction: () -> Unit,
    editedSeen: () -> Unit,
    showSender: Boolean,
    lastFromSender: Boolean,
    scale: Float,
    linkPreviewViewModel: LinkPreviewViewModel? = null,
    messageExpiration: MessageExpiration? = null,
    discussionViewModel: DiscussionViewModel? = null,
    discussionSearch: DiscussionSearch? = null,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding?,
    openDiscussionDetailsCallback: (() -> Unit)? = null,
    openOnClick: Boolean = true,
    openViewerCallback: (() -> Unit)? = null,
) {
    Box {
        val maxWidth = 400.dp
        SwipeForActionBox(
            modifier = modifier,
            enabledFromStartToEnd = discussionViewModel?.isSelectingForDeletion != true && replyAction != null,
            enabledFromEndToStart = discussionViewModel?.isSelectingForDeletion != true,
            callbackStartToEnd = replyAction,
            callbackEndToStart = menuAction,
            backgroundContentFromStartToEnd = {
                Image(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            color = colorResource(id = R.color.olvid_gradient_light),
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    painter = painterResource(id = R.drawable.ic_reply),
                    contentDescription = stringResource(id = R.string.label_swipe_reply)
                )
            },
            backgroundContentFromEndToStart = {
                Image(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            color = colorResource(id = R.color.green),
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    imageVector = Rounded.MoreVert,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "more"
                )
            },
            maxOffset = 64.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) Arrangement.End else Arrangement.Start
            ) {
                val context = LocalContext.current
                if (showSender) {
                    if (lastFromSender) {
                        InitialView(
                            modifier = Modifier.size(32.dp),
                            initialViewSetup = { it.setFromCache(message.senderIdentifier) }
                        ) {
                            val discussion = discussionViewModel?.discussion?.value
                            if (discussion != null) {
                                App.runThread {
                                    val senderBytes = message.senderIdentifier
                                    val contact = AppDatabase.getInstance()
                                        .contactDao()[discussion.bytesOwnedIdentity, senderBytes]
                                    if (contact != null) {
                                        if (contact.oneToOne) {
                                            App.openOneToOneDiscussionActivity(
                                                context,
                                                discussion.bytesOwnedIdentity,
                                                message.senderIdentifier,
                                                false
                                            )
                                        } else {
                                            App.openContactDetailsActivity(
                                                context,
                                                discussion.bytesOwnedIdentity,
                                                message.senderIdentifier
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    Spacer(modifier = Modifier.requiredWidth(40.dp))
                    if (messageExpiration != null || message.limitedVisibility || message.bookmarked) {
                        EphemeralTimer(
                            expiration = messageExpiration,
                            readOnce = message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ || message.jsonMessage.jsonExpiration?.readOnce == true,
                            bookmarked = message.bookmarked
                        )
                    } else {
                        Spacer(modifier = Modifier.requiredWidth(32.dp))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val interactionSource = remember { MutableInteractionSource() }

                    MessageInnerStack(
                        modifier = Modifier
                            .background(
                                color = if (message.isInbound) colorResource(id = R.color.lighterGrey)
                                else if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) colorResource(
                                    id = R.color.primary100
                                )
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = { },
                                onLongClick = if (message.isLocationMessage) onLocationLongClick else onLongClick
                            )
                            .then(
                                when (message.messageType) {
                                    Message.TYPE_OUTBOUND_MESSAGE -> Modifier
                                        .widthIn(max = maxWidth)
                                        .align(Alignment.End)

                                    Message.TYPE_INBOUND_MESSAGE, Message.TYPE_INBOUND_EPHEMERAL_MESSAGE -> Modifier
                                        .widthIn(max = maxWidth)

                                    else -> Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = maxWidth)
                                        .padding(2.dp)
                                        .background(
                                            color = colorResource(id = R.color.almostWhite),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = colorResource(
                                                id = if (message.messageType == Message.TYPE_SCREEN_SHOT_DETECTED) R.color.red else R.color.primary400_90
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                }
                            )
                            .padding(6.dp),
                        noAutoWidth = message.hasAttachments() || message.isLocationMessage
                    ) {

                        // Sender
                        if (showSender && lastFromSender) {
                            val displayName =
                                AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                                    ?: stringResource(
                                        id = R.string.text_deleted_contact
                                    )
                            Text(
                                modifier = Modifier.padding(bottom = 2.dp),
                                text = displayName,
                                style = OlvidTypography.h3,
                                color = Color(
                                    InitialView.getTextColor(
                                        context,
                                        message.senderIdentifier,
                                        AppSingleton.getContactCustomHue(message.senderIdentifier)
                                    )
                                )
                            )
                        }

                        // reply
                        Replied(
                            message = message,
                            context = context,
                            discussionViewModel = discussionViewModel,
                            scrollToMessage = scrollToMessage,
                        )

                        // forwarded
                        Forwarded(message)

                        // content body
                        MessageBody(
                            message = message,
                            onClick = if (message.isLocationMessage) onLocationClick else onClick,
                            onDoubleClick = if (message.isLocationMessage) onLocationClick else onDoubleClick,
                            onLongClick = if (message.isLocationMessage) onLocationLongClick else onLongClick,
                            onCallBackButtonClicked = onCallBackButtonClicked,
                            discussionViewModel = discussionViewModel,
                            discussionSearch = discussionSearch,
                            scale = scale,
                            openDiscussionDetailsCallback = openDiscussionDetailsCallback,
                            messageBubbleInteractionSource = interactionSource
                        )

                        // message footer
                        MessageFooter(
                            message = message,
                            editedSeen = editedSeen,
                            directDelete = { messageId ->
                                if (discussionViewModel?.isSelectingForDeletion == true) {
                                    discussionViewModel.unselectMessageId(messageId)
                                }
                                App.runThread { DeleteMessagesTask(listOf(messageId), LOCAL).run() }
                            },
                            context = context
                        )

                        // LinkPreview
                        LinkPreview(
                            message = message,
                            discussionViewModel = discussionViewModel,
                            linkPreviewViewModel = linkPreviewViewModel,
                            onLongClick = onLongClick
                        )

                        // Attachments
                        if (message.hasAttachments() && message.isLocationMessage.not()) {
                            BoxWithConstraints {
                                Attachments(
                                    message = message,
                                    audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                                    maxWidth = this.maxWidth,
                                    openOnClick = openOnClick,
                                    onAttachmentLongClick = onAttachmentLongClick,
                                    openViewerCallback = openViewerCallback,
                                )
                            }
                        }
                    }
                    // reactions
                    Reactions(
                        modifier = if (message.isInbound.not()) Modifier.align(Alignment.End) else Modifier,
                        message = message
                    )
                }

                if (message.isInbound) {
                    if (messageExpiration != null || message.bookmarked || message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ) {
                        EphemeralTimer(
                            modifier = Modifier.requiredWidth(32.dp),
                            expiration = messageExpiration,
                            readOnce = message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ,
                            bookmarked = message.bookmarked
                        )
                    } else {
                        Spacer(modifier = Modifier.requiredWidth(32.dp))
                    }
                    if (!showSender) {
                        Spacer(modifier = Modifier.requiredWidth(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInnerStack(
    modifier: Modifier = Modifier,
    noAutoWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val preferredWidth = if (noAutoWidth) constraints.maxWidth else measurables.maxOf {
            it.maxIntrinsicWidth(constraints.maxHeight)
        }.coerceAtMost(constraints.maxWidth).coerceAtLeast(constraints.minWidth)
        val adjustedConstraints = constraints.copy(maxWidth = preferredWidth)

        val placeables = measurables.map { measurable ->
            measurable.measure(adjustedConstraints)
        }

        val totalHeight =
            placeables.map { it.height }.reduce { acc, v -> acc + v + (2 * density).toInt() }

        layout(preferredWidth, totalHeight) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x = 0, y = y)
                y += placeable.height + (2 * density).toInt()
            }
        }
    }
}


@Composable
private fun Forwarded(message: Message) {
    if (message.forwarded) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_forwarded),
                contentDescription = stringResource(id = R.string.label_forwarded)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(id = R.string.label_forwarded),
                maxLines = 1,
                style = OlvidTypography.subtitle1,
                fontStyle = FontStyle.Italic,
                color = colorResource(id = R.color.greyTint)
            )
        }
    }
}

@Composable
private fun MessageFooter(
    message: Message,
    editedSeen: () -> Unit,
    directDelete: (Long) -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = CenterVertically
    ) {
        // direct delete
        if (message.wipeStatus == Message.WIPE_STATUS_WIPED || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
            IconButton(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .requiredSize(22.dp),
                onClick = { directDelete(message.id) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_direct_delete),
                    tint = colorResource(
                        id = R.color.darkGrey
                    ),
                    contentDescription = null
                )
            }
        }

        // edited
        if (message.edited > 0) {
            LaunchedEffect(message.edited) {
                editedSeen()
            }
            Text(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .then(
                        if (message.edited == Message.EDITED_SEEN) {
                            Modifier
                                .border(
                                    width = 1.dp,
                                    color = colorResource(id = R.color.green),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        } else {
                            Modifier
                                .background(
                                    color = colorResource(id = R.color.green),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        }
                    ),
                text = stringResource(id = R.string.text_edited).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (message.edited == Message.EDITED_SEEN) colorResource(id = R.color.green) else colorResource(
                    id = R.color.almostWhite
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f, true))

        // timestamp
        Text(
            text = StringUtils.getLongNiceDateString(context, message.timestamp).toString(),
            style = OlvidTypography.subtitle1,
            color = Color(0xCC7D7D7D)
        )
        // outbound status
        getOutboundStatusIcon(message)?.let {
            Image(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(height = 16.dp)
                    .width(width = (16 * getOutboundStatusIconAspectRation(message)).dp),
                painter = painterResource(id = it),
                contentDescription = stringResource(
                    id = R.string.content_description_message_status
                )
            )
        }
    }
}

@Composable
private fun Replied(
    message: Message,
    context: Context,
    discussionViewModel: DiscussionViewModel? = null,
    scrollToMessage: (messageId: Long) -> Unit
) {
    val repliedToMessage: State<Message?>? = message.jsonMessage.jsonReply?.let { jsonReply ->
        AppDatabase.getInstance().messageDao()
            .getBySenderSequenceNumberAsync(
                jsonReply.getSenderSequenceNumber(),
                jsonReply.getSenderThreadIdentifier(),
                jsonReply.getSenderIdentifier(),
                message.discussionId
            )
    }?.observeAsState(Message.emptyMessage())
    AnimatedVisibility(
        modifier = Modifier.padding(bottom = 4.dp),
        visible = message.jsonMessage.jsonReply != null
    ) {
        val color = remember {
            Color(
                InitialView.getTextColor(
                    context = context,
                    message.jsonMessage?.jsonReply?.senderIdentifier
                        ?: byteArrayOf(),
                    AppSingleton.getContactCustomHue(message.jsonMessage?.jsonReply?.senderIdentifier)
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = color,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable {
                    repliedToMessage?.value?.id?.let { scrollToMessage(it) }
                }
                .padding(start = 4.dp)
                .background(color = colorResource(id = R.color.almostWhite))
                .padding(4.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = message.jsonMessage?.jsonReply?.senderIdentifier?.let {
                AppSingleton.getContactCustomDisplayName(it)
            } ?: stringResource(id = R.string.text_deleted_contact),
                style = OlvidTypography.body2,
                fontWeight = FontWeight.Medium,
                color = color)
            repliedToMessage?.value?.let { repliedToMessage ->
                if (repliedToMessage == Message.emptyMessage()) {
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = "",
                        color = colorResource(id = R.color.greyTint),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    repliedToMessage.contentBody?.let { replyBody ->
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = AnnotatedString(replyBody).formatMarkdown(
                                complete = true,
                                context = context,
                                message = repliedToMessage,
                                bytesOwnedIdentity = discussionViewModel?.discussion?.value?.bytesOwnedIdentity,
                                backgroundColor = Color(
                                    ContextCompat.getColor(
                                        context,
                                        R.color.greySubtleOverlay
                                    )
                                )
                            ),
                            style = OlvidTypography.body2,
                            color = colorResource(id = R.color.greyTint),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            inlineContent = inlineContentMap(.9f)
                        )
                    }
                    if (repliedToMessage.hasAttachments()) {
                        AttachmentCount(repliedToMessage.totalAttachmentCount)
                    }
                }
            } ?: Text(
                text = stringResource(id = R.string.text_original_message_not_found),
                style = OlvidTypography.body2,
                fontStyle = FontStyle.Italic,
                color = colorResource(id = R.color.greyTint),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttachmentCount(attachmentCount: Int) {
    Text(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(
                color = colorResource(id = R.color.grey),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        text = pluralStringResource(
            id = R.plurals.text_reply_attachment_count,
            count = attachmentCount,
            attachmentCount
        ),
        style = OlvidTypography.subtitle1,
        color = colorResource(id = R.color.almostWhite),
        fontWeight = FontWeight.Medium
    )
}

const val LINK_ANNOTATION_TAG = "LINK"
const val MENTION_ANNOTATION_TAG = "MENTION"
const val INLINE_CONTENT_TAG =
    "androidx.compose.foundation.text.inlineContent" // WARNING: this string is copied from the InlineTextContent source and may change without notice....
const val QUOTE_BLOCK_START_ANNOTATION = "quote"


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBody(
    message: Message,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: () -> Unit,
    onCallBackButtonClicked: (callLogId: Long) -> Unit,
    scale: Float,
    discussionViewModel: DiscussionViewModel? = null,
    discussionSearch: DiscussionSearch? = null,
    openDiscussionDetailsCallback: (() -> Unit)? = null,
    messageBubbleInteractionSource: MutableInteractionSource,
) {
    val context = LocalContext.current
    if (message.wipeStatus == Message.WIPE_STATUS_WIPED || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
        var text by remember {
            mutableStateOf(message.getStringContent(context))
        }
        if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
            val deleter = discussionViewModel?.remoteDeletedMessageDeleter?.get(message.id)
            if (deleter != null) {
                val bytesOwnedIdentity =
                    if (discussionViewModel.discussion.value == null) null else discussionViewModel.discussion.value!!.bytesOwnedIdentity
                if (bytesOwnedIdentity.contentEquals(deleter)) {
                    text = stringResource(R.string.text_message_content_remote_deleted_by_you)
                } else {
                    val displayName =
                        AppSingleton.getContactCustomDisplayName(deleter)
                    if (displayName != null) {
                        text = stringResource(
                            R.string.text_message_content_remote_deleted_by,
                            displayName
                        )
                    }
                }
            } else {
                // we need to fetch the identity of the deleter of this message
                App.runThread {
                    val messageMetadata =
                        AppDatabase.getInstance().messageMetadataDao().getByKind(
                            message.id,
                            MessageMetadata.KIND_REMOTE_DELETED
                        )
                    if (messageMetadata?.bytesRemoteIdentity != null) {
                        // we found the deleter for this message --> cache it
                        discussionViewModel?.remoteDeletedMessageDeleter?.set(
                            message.id,
                            messageMetadata.bytesRemoteIdentity
                        )

                        if (discussionViewModel?.discussion?.value?.bytesOwnedIdentity.contentEquals(
                                messageMetadata.bytesRemoteIdentity
                            )
                        ) {
                            text =
                                context.getString(R.string.text_message_content_remote_deleted_by_you)
                        } else {
                            val displayName =
                                AppSingleton.getContactCustomDisplayName(
                                    messageMetadata.bytesRemoteIdentity
                                )
                            if (displayName != null) {
                                context.getString(
                                    R.string.text_message_content_remote_deleted_by,
                                    displayName
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = text,
            color = if (message.isInbound) colorResource(id = R.color.inboundMessageBody) else colorResource(
                id = R.color.primary700
            ),
            style = if (message.isInbound || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) OlvidTypography.body1.copy(
                fontSize = (16 * scale).sp,
                lineHeight = (16 * scale).sp
            ) else OlvidTypography.body2
        )
        if (message.wipedAttachmentCount > 0) {
            AttachmentCount(attachmentCount = message.wipedAttachmentCount)
        }
    } else if (message.isLocationMessage) {
        LocationMessage(
            message = message,
            discussionId = discussionViewModel?.discussionId,
            scale = scale,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        when (message.messageType) {

            Message.TYPE_GROUP_MEMBER_JOINED -> {
                MessageInfoWithSenderDisplayName(
                    message.senderIdentifier,
                    R.string.text_joined_the_group,
                    R.string.text_unknown_member_joined_the_group
                )
            }

            Message.TYPE_GROUP_MEMBER_LEFT -> {
                MessageInfoWithSenderDisplayName(
                    message.senderIdentifier,
                    R.string.text_left_the_group,
                    R.string.text_unknown_member_left_the_group
                )
            }

            Message.TYPE_DISCUSSION_REMOTELY_DELETED -> {
                MessageInfoWithSenderDisplayName(
                    message.senderIdentifier,
                    R.string.text_discussion_remotely_deleted_by,
                    R.string.text_discussion_remotely_deleted
                )
            }

            Message.TYPE_SCREEN_SHOT_DETECTED -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        modifier = Modifier
                            .size(32.dp),
                        painter = painterResource(id = R.drawable.ic_screenshot_detected),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier
                            .weight(1f, true)
                            .padding(start = 8.dp),
                        textAlign = TextAlign.Center,
                        text = if (AppSingleton.getBytesCurrentIdentity()
                                ?.contentEquals(message.senderIdentifier) == true
                        ) {
                            stringResource(id = R.string.text_you_captured_sensitive_message)
                        } else {
                            AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                                ?.let {
                                    stringResource(
                                        id = R.string.text_xxx_captured_sensitive_message,
                                        it
                                    )
                                }
                        } ?: "",
                        color = colorResource(id = R.color.red))
                }
            }

            Message.TYPE_INBOUND_EPHEMERAL_MESSAGE -> {
                val expiration = message.jsonMessage.jsonExpiration
                val readOnce = expiration.readOnce == true

                // Check for auto-open
                discussionViewModel?.discussionCustomization?.value?.let { discussionCustomization ->
                    // check the ephemeral settings match
                    if (readOnce == discussionCustomization.settingReadOnce && expiration.getVisibilityDuration() == discussionCustomization.settingVisibilityDuration && expiration.getExistenceDuration() == discussionCustomization.settingExistenceDuration) {
                        // settings are the default, verify if auto-open
                        val autoOpen =
                            if (discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages != null) discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages == true else SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages()
                        if (autoOpen) {
                            App.runThread {
                                discussionViewModel.discussion.value?.bytesOwnedIdentity?.let {
                                    InboundEphemeralMessageClicked(
                                        it,
                                        message.id
                                    ).run()
                                }
                            }
                        }
                    }
                }

                if (message.isWithoutText.not()) {
                    EphemeralVisibilityExplanation(
                        duration = expiration.visibilityDuration,
                        readOnce = readOnce,
                    ) {
                        App.runThread {
                            discussionViewModel?.discussion?.value?.bytesOwnedIdentity?.let {
                                InboundEphemeralMessageClicked(
                                    it,
                                    message.id
                                ).run()
                            }
                        }
                    }
                }
            }

            Message.TYPE_NEW_PUBLISHED_DETAILS -> {
                val newDetailsUpdate = discussionViewModel?.newDetailsUpdate?.observeAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = messageBubbleInteractionSource,
                            indication = null,
                            onClick = {
                                openDiscussionDetailsCallback?.invoke()
                            }
                        )
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = if (discussionViewModel?.discussion?.value?.discussionType == Discussion.TYPE_CONTACT) {
                            stringResource(id = R.string.text_contact_details_updated)
                        } else {
                            stringResource(id = R.string.text_group_details_updated)
                        }, color = colorResource(id = R.color.primary700),
                        textAlign = TextAlign.Center
                    )
                    AnimatedVisibility(visible = newDetailsUpdate?.value != null) {
                        PublishedDetails(
                            modifier = Modifier.padding(end = 4.dp),
                            notification = newDetailsUpdate?.value == Group.PUBLISHED_DETAILS_NEW_UNSEEN
                        )
                    }
                }

            }

            Message.TYPE_DISCUSSION_SETTINGS_UPDATE -> {
                Column {
                    Text(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                        maxLines = 3,
                        textAlign = TextAlign.Center,
                        text = if (message.status == Message.STATUS_READ) {
                            AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                                ?.let {
                                    stringResource(id = R.string.text_updated_shared_settings, it)
                                }
                                ?: stringResource(id = R.string.text_discussion_shared_settings_updated)
                        } else {
                            stringResource(
                                id = R.string.text_updated_shared_settings_you
                            )
                        }, color = colorResource(id = R.color.primary700))

                    EphemeralJsonSharedSettings(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        message = message
                    )
                }
            }

            Message.TYPE_PHONE_CALL -> {
                PhoneCallInfo(message, onCallBackButtonClicked)
            }

            // Standard message
            else -> {
                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                val linkUrl by remember {
                    derivedStateOf { discussionViewModel?.messageLinkPreviewUrlCache?.get(message.id) }
                }
                val text = remember(linkUrl, discussionSearch?.viewModel?.filterRegexes, message.contentBody) {
                    getAnnotatedStringContent(
                        context,
                        message,
                        linkUrl,
                        discussionViewModel?.discussion?.value?.bytesOwnedIdentity,
                        discussionSearch?.viewModel
                    )
                }
                val uriHandler = LocalUriHandler.current
                if (text.isNotBlank()) {
                    val shortEmoji = StringUtils.isShortEmojiString(text.text, 5)
                    val textSize = if (shortEmoji) 48.sp * scale else 16.sp * scale
                    val textAlign = if (shortEmoji) TextAlign.Center else TextAlign.Start
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(message) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val press = PressInteraction.Press(offset)
                                        messageBubbleInteractionSource.emit(press)
                                        tryAwaitRelease()
                                        messageBubbleInteractionSource.emit(
                                            PressInteraction.Release(
                                                press
                                            )
                                        )
                                    },
                                    onLongPress = { onLongClick() },
                                    onDoubleTap = { onDoubleClick?.invoke() }) { offset ->
                                    layoutResult?.let { layoutResult ->
                                        val position = layoutResult.getOffsetForPosition(offset)
                                        text
                                            .getStringAnnotations(
                                                tag = MENTION_ANNOTATION_TAG,
                                                start = position,
                                                end = position,
                                            )
                                            .firstOrNull()
                                            ?.let { annotation ->
                                                val bytesOwnedIdentity =
                                                    AppSingleton.getBytesCurrentIdentity()
                                                        ?: byteArrayOf()
                                                val userIdentifier =
                                                    ObvBase64.decode(annotation.item)
                                                if (bytesOwnedIdentity.contentEquals(userIdentifier)) {
                                                    startActivity(
                                                        context,
                                                        Intent(
                                                            context,
                                                            OwnedIdentityDetailsActivity::class.java
                                                        ),
                                                        null
                                                    )
                                                } else {
                                                    // check that there is indeed a contact
                                                    App.runThread {
                                                        if (AppDatabase
                                                                .getInstance()
                                                                .contactDao()
                                                                .get(
                                                                    AppSingleton.getBytesCurrentIdentity(),
                                                                    userIdentifier
                                                                ) != null
                                                        ) {
                                                            App.openContactDetailsActivity(
                                                                context,
                                                                AppSingleton.getBytesCurrentIdentity(),
                                                                userIdentifier
                                                            )
                                                        }
                                                    }
                                                }
                                                return@detectTapGestures
                                            }
                                        text
                                            .getStringAnnotations(
                                                tag = LINK_ANNOTATION_TAG,
                                                start = position,
                                                end = position,
                                            )
                                            .firstOrNull()
                                            ?.let { annotation ->
                                                uriHandler.openUri(annotation.item)
                                                return@detectTapGestures
                                            }
                                        onClick()
                                    }
                                }
                            },
                        text = text,
                        textAlign = if (message.isInbound || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) textAlign else TextAlign.Center,
                        onTextLayout = { layoutResult = it },
                        overflow = TextOverflow.Visible,
                        color = if (message.isInbound) colorResource(id = R.color.inboundMessageBody) else colorResource(
                            id = R.color.primary700
                        ),
                        style = if (message.isInbound || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) OlvidTypography.body1.copy(
                            fontSize = textSize,
                            lineHeight = 1.1.em
                        ) else OlvidTypography.body2,
                        inlineContent = inlineContentMap(scale = scale)
                    )
                }
            }
        }
    }
}

@Composable
fun inlineContentMap(scale: Float = 1f) = mapOf(
    "quote" to InlineTextContent(
        placeholder = Placeholder(0.em, 0.em, PlaceholderVerticalAlign.Top)
    ) {
        val quoteImage = ImageVector.vectorResource(id = R.drawable.quote)
        val quotePainter = rememberVectorPainter(image = quoteImage)
        val fontScale = with(LocalDensity.current) { fontScale }
        Box(modifier = Modifier
            .offset(x = (-26 * fontScale * scale).dp, y = 0.dp)
            .drawBehind {
                with(quotePainter) {
                    draw(
                        size = Size(
                            20 * density * fontScale * scale,
                            16.6f * density * fontScale * scale
                        )
                    )
                }
            })
    }
)


@Composable
private fun MessageInfoWithSenderDisplayName(
    senderIdentifier: ByteArray,
    senderStringResource: Int,
    unknownStringResource: Int
) {
    Text(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
        maxLines = 3,
        textAlign = TextAlign.Center,
        text =
        AppSingleton.getContactCustomDisplayName(senderIdentifier)
            ?.let {
                stringResource(
                    id = senderStringResource,
                    it
                )
            }
            ?: stringResource(id = unknownStringResource),
        color = colorResource(id = R.color.primary700))
}

fun getAnnotatedStringContent(
    context: Context,
    message: Message,
    linkPreviewUrl: String? = null,
    bytesOwnedIdentity: ByteArray? = null,
    discussionSearchViewModel: DiscussionSearch.SearchViewModel?,
): AnnotatedString {
    return buildAnnotatedString {
        var stringContent = message.getStringContent(context)
        linkPreviewUrl?.let {
            if (SettingsActivity.truncateMessageBodyTrailingLinks() && stringContent.endsWith(
                    linkPreviewUrl,
                    true
                )
            ) {
                stringContent =
                    stringContent.substring(0, stringContent.length - linkPreviewUrl.length).trim()
            }
        }
        // Markdown
        val annotatedStringContent =
            AnnotatedString(stringContent).formatMarkdown(
                complete = true,
                context = context,
                message = message,
                bytesOwnedIdentity = bytesOwnedIdentity,
                backgroundColor = Color(ContextCompat.getColor(context, R.color.greySubtleOverlay))
            )
        append(annotatedStringContent)
        // Linkify
        val spannableString = SpannableString(annotatedStringContent)
        LinkifyCompat.addLinks(
            spannableString,
            Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS,
        )
        spannableString.getSpans(0, spannableString.length, URLSpan::class.java).onEach { urlSpan ->
            val start = spannableString.getSpanStart(urlSpan)
            val end = spannableString.getSpanEnd(urlSpan)
            addStyle(
                style = SpanStyle(
                    color = Color(ContextCompat.getColor(context, R.color.olvid_gradient_light)),
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = LINK_ANNOTATION_TAG,
                annotation = urlSpan.url,
                start = start,
                end = end
            )
        }
    }.run {
        if (message.messageType == Message.TYPE_INBOUND_MESSAGE || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
            discussionSearchViewModel?.highlight(
                content = this,
                context = context
            ) ?: this
        } else {
            this
        }
    }
}

@DrawableRes
fun getOutboundStatusIcon(message: Message): Int? =
    if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
        when (message.status) {
            Message.STATUS_SENT -> {
                R.drawable.ic_message_status_sent
            }

            Message.STATUS_DELIVERED -> {
                R.drawable.ic_message_status_delivered_one
            }

            Message.STATUS_DELIVERED_AND_READ -> {
                R.drawable.ic_message_status_delivered_and_read_one
            }

            Message.STATUS_DELIVERED_ALL -> {
                R.drawable.ic_message_status_delivered_all
            }

            Message.STATUS_DELIVERED_ALL_READ_ONE -> {
                R.drawable.ic_message_status_delivered_all_read_one
            }

            Message.STATUS_DELIVERED_ALL_READ_ALL -> {
                R.drawable.ic_message_status_delivered_all_read_all
            }

            Message.STATUS_UNDELIVERED -> {
                R.drawable.ic_message_status_undelivered
            }

            Message.STATUS_SENT_FROM_ANOTHER_DEVICE -> {
                R.drawable.ic_message_status_sent_from_other_device
            }

            Message.STATUS_UNPROCESSED, Message.STATUS_COMPUTING_PREVIEW, Message.STATUS_PROCESSING -> {
                if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                    R.drawable.ic_message_status_sent
                } else {
                    R.drawable.ic_message_status_processing
                }
            }

            else -> {
                if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                    R.drawable.ic_message_status_sent
                } else {
                    R.drawable.ic_message_status_processing
                }
            }
        }
    } else {
        null
    }

fun getOutboundStatusIconAspectRation(message: Message): Float =
    if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
        when (message.status) {
            Message.STATUS_SENT,
            Message.STATUS_DELIVERED,
            Message.STATUS_DELIVERED_AND_READ,
            Message.STATUS_UNDELIVERED,
            Message.STATUS_SENT_FROM_ANOTHER_DEVICE -> 1f

            Message.STATUS_DELIVERED_ALL,
            Message.STATUS_DELIVERED_ALL_READ_ONE,
            Message.STATUS_DELIVERED_ALL_READ_ALL -> 1.45f

            else -> if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                1f
            } else {
                1.45f
            }
        }
    } else {
        1f
    }

@PreviewScreenSizes
@PreviewLightDark
@Composable
private fun MessagePreview() {
    AppCompatTheme {
        Column(
            modifier = Modifier
                .background(colorResource(id = R.color.almostWhite))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Message(
                message = messageInbound,
                replyAction = null,
                menuAction = {},
                editedSeen = {},
                scrollToMessage = {},
                showSender = false,
                lastFromSender = true,
                scale = 1f,
                audioAttachmentServiceBinding = null,
            )
            Message(
                message = messageSystem,
                replyAction = null,
                menuAction = {},
                editedSeen = {},
                scrollToMessage = {},
                showSender = false,
                lastFromSender = true,
                scale = 1f,
                audioAttachmentServiceBinding = null,
            )
            Message(
                message = messageOutbound,
                replyAction = null,
                menuAction = {},
                editedSeen = {},
                scrollToMessage = {},
                showSender = false,
                lastFromSender = true,
                scale = 1f,
                audioAttachmentServiceBinding = null,
            )
        }

    }
}

@Composable
fun MissedMessageCount(modifier: Modifier = Modifier, missedMessageCount: Int) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .background(
                color = colorResource(id = R.color.lighterGrey),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val builder =
                    SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_missing_messages)
                        .setMessage(R.string.dialog_message_missing_messages)
                        .setPositiveButton(R.string.button_label_ok, null)
                builder
                    .create()
                    .show()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_question_mark),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            modifier = Modifier,
            text = pluralStringResource(
                R.plurals.text_message_possibly_missing,
                missedMessageCount,
                missedMessageCount
            ),
            style = OlvidTypography.subtitle1.copy(fontStyle = FontStyle.Italic),
            color = colorResource(id = R.color.greyOverlay)
        )
    }


}

@PreviewLightDark
@Composable
fun MissedMessageCountPreview() {
    AppCompatTheme {
        MissedMessageCount(missedMessageCount = 1)
    }
}

val messageInbound = Message(
    0,
    LoremIpsum(words = 20).values.joinToString(" "),
    null,
    null,
    null,
    null,
    0,
    0.0,
    System.currentTimeMillis(),
    Message.STATUS_DELIVERED_ALL_READ_ALL,
    Message.WIPE_STATUS_NONE,
    Message.TYPE_INBOUND_MESSAGE,
    0,
    byteArrayOf(),
    byteArrayOf(),
    UUID.randomUUID(),
    0,
    0,
    0,
    Message.EDITED_UNSEEN,
    false,
    "\uD83D\uDE02:1:",
    null,
    0,
    0,
    false,
    null,
    null,
    false,
).apply {
    bookmarked = true
}
val messageOutboundLocation = Message(
    0,
    LoremIpsum(words = 20).values.joinToString(" "),
    null,
    null,
    null,
    "{\"alt\":77.4000015258789,\"c\":12,\"lat\":47.2679561,\"long\":-1.4497401,\"prec\":100.0,\"q\":2,\"se\":1719851979937,\"ts\":1719849295503,\"t\":2}",
    Message.LOCATION_TYPE_SHARE,
    0.0,
    System.currentTimeMillis(),
    Message.STATUS_DELIVERED_ALL_READ_ALL,
    Message.WIPE_STATUS_NONE,
    Message.TYPE_OUTBOUND_MESSAGE,
    0,
    byteArrayOf(),
    byteArrayOf(),
    UUID.randomUUID(),
    0,
    0,
    0,
    Message.EDITED_NONE,
    false,
    null,
    null,
    0,
    0,
    false,
    null,
    null,
    false,
)
val messageOutbound = Message(
    0,
    LoremIpsum(words = 20).values.joinToString(" "),
    null,
    null,
    null,
    null,
    0,
    0.0,
    System.currentTimeMillis(),
    Message.STATUS_DELIVERED_ALL_READ_ALL,
    Message.WIPE_STATUS_NONE,
    Message.TYPE_OUTBOUND_MESSAGE,
    0,
    byteArrayOf(),
    byteArrayOf(),
    UUID.randomUUID(),
    0,
    0,
    0,
    Message.EDITED_NONE,
    true,
    "\uD83D\uDE2E:1:|\uD83D\uDE02:1:\uD83D\uDC4D:2:",
    null,
    0,
    0,
    false,
    null,
    null,
    false,
)
val messageSystem = Message(
    0,
    LoremIpsum(words = 4).values.joinToString(" "),
    null,
    null,
    null,
    null,
    0,
    0.0,
    System.currentTimeMillis(),
    Message.STATUS_DELIVERED_ALL_READ_ALL,
    Message.WIPE_STATUS_NONE,
    Message.TYPE_GAINED_GROUP_SEND_MESSAGE,
    0,
    byteArrayOf(),
    byteArrayOf(),
    UUID.randomUUID(),
    0,
    0,
    0,
    Message.EDITED_NONE,
    false,
    null,
    null,
    0,
    0,
    false,
    null,
    null,
    false,
)