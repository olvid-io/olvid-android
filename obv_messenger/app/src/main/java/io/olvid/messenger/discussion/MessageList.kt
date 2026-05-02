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

package io.olvid.messenger.discussion

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.tasks.CreateReadMessageMetadata
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.compose.ComposeMessageViewModel
import io.olvid.messenger.discussion.compose.MessageEditHandler
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.message.DateHeader
import io.olvid.messenger.discussion.message.LocationContextMenu
import io.olvid.messenger.discussion.message.LocationContextMenuState
import io.olvid.messenger.discussion.message.LocationSharing
import io.olvid.messenger.discussion.message.MessageDisclaimer
import io.olvid.messenger.discussion.message.MissedMessageCount
import io.olvid.messenger.discussion.message.ScrollDownButton
import io.olvid.messenger.discussion.search.DiscussionSearchViewModel
import io.olvid.messenger.main.invitations.InvitationListItem
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.invitations.getAnnotatedDate
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.webrtc.CallNotificationManager
import io.olvid.messenger.webrtc.components.CallNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    lazyListState: LazyListState,
    discussionViewModel: DiscussionViewModel,
    discussionSearchViewModel: DiscussionSearchViewModel,
    invitationViewModel: InvitationListViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    composeMessageViewModel: ComposeMessageViewModel,
    callHandler: CallHandler,
    locationMessageHandler: LocationMessageHandler,
    messageEditHandler: MessageEditHandler,
    sendReadReceipt: Boolean,
    messageClicked: (Message) -> Unit,
    saveAttachment: () -> Unit,
    saveAllAttachments: () -> Unit,
    openMap: () -> Unit,
    openDiscussionDetailsCallback: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
) {
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current as DiscussionActivity
    val keyboardController = LocalSoftwareKeyboardController.current
    var scrollToFirstUnread by rememberSaveable { mutableStateOf(true) }

    val unreadCountAndFirstMessage by discussionViewModel.unreadCountAndFirstMessage.observeAsState()
    var startCollectingMessagesToMarkAsRead by rememberSaveable { mutableStateOf(false) }
    var searchInProgress by rememberSaveable {
        mutableStateOf(false)
    }

    val messages = discussionViewModel.pagedMessages.collectAsLazyPagingItems()
    val invitations by discussionViewModel.invitations.observeAsState()

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress && !searchInProgress) {
            keyboardController?.hide()
        }
    }
    var highlightMessageId by remember {
        mutableLongStateOf(-1L)
    }

    // this method returns true if the messageId was found and a scroll was indeed initiated
    // if it returns false, it attempts to load more messages
    suspend fun scrollTo(scrollRequest: DiscussionActivity.ScrollRequest): Boolean {
        try {
            if (scrollRequest.triggeredBySearch) {
                searchInProgress = true
            }
            val snapshot = messages.itemSnapshotList
            val pos = snapshot.indexOfFirst { it?.id == scrollRequest.messageId }

            if (pos != -1) {
                if (searchInProgress) {
                    lazyListState.scrollToItem(
                        index = 1 + pos,
                    )
                } else {
                    coroutineScope.launch {
                        delay(300)
                        startCollectingMessagesToMarkAsRead = true
                    }
                    lazyListState.scrollToItem(
                        index = 2 + pos,
                        scrollOffset = -2 * resources.displayMetrics.heightPixels / 3
                    )
                }
                highlightMessageId =
                    if (scrollRequest.highlight) scrollRequest.messageId else -1L
            } else {
                val firstNull = snapshot.indexOfFirst { it == null }
                if (firstNull != -1) {
                    // access the last snapshot message to force fetching more messages
                    messages[firstNull]
                    return false
                }
            }
        } finally {
            if (scrollRequest.triggeredBySearch) {
                searchInProgress = false
            }
        }
        return true
    }

    LaunchedEffect(discussionSearchViewModel.initialFoundItem) {
        if (discussionSearchViewModel.initialFoundItem != null) {
            discussionViewModel.scrollToMessageRequest = DiscussionActivity.ScrollRequest(messageId = discussionSearchViewModel.initialFoundItem!!, highlight = true, triggeredBySearch = true)
            discussionSearchViewModel.initialFoundItem = null
        }
    }

    LaunchedEffect(
        discussionViewModel.scrollToMessageRequest,
        messages.itemCount,
        messages.itemSnapshotList.indexOfFirst { it == null }) {
        when (discussionViewModel.scrollToMessageRequest) {
            DiscussionActivity.ScrollRequest.None -> Unit
            DiscussionActivity.ScrollRequest.ToBottom -> {
                lazyListState.animateScrollToItem(0)
                discussionViewModel.scrollToMessageRequest = DiscussionActivity.ScrollRequest.None
            }

            else -> {
                if (messages.itemCount > 0 && scrollTo(discussionViewModel.scrollToMessageRequest)) {
                    // if the scroll was successful, reset the scroll request
                    discussionViewModel.scrollToMessageRequest = DiscussionActivity.ScrollRequest.None
                }
            }
        }
    }
    LaunchedEffect(scrollToFirstUnread, unreadCountAndFirstMessage?.messageId) {
        if (scrollToFirstUnread) {
            unreadCountAndFirstMessage?.messageId?.let {
                if (it > 0) {
                    discussionViewModel.scrollToMessageRequest =
                        DiscussionActivity.ScrollRequest(messageId = it, highlight = false)
                    scrollToFirstUnread = false
                } else {
                    startCollectingMessagesToMarkAsRead = true
                }
            }
        }
    }
    val showScrollDownButton by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 1
        }
    }
    val stickyDate by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?.takeIf { it.contentType != "DateHeader" }?.key?.run {
                    messages.itemSnapshotList.items.find { it.id == this }?.timestamp?.let {
                        StringUtils.getDayOfDateString(context, it)
                    }
                }
        }
    }

    with(sharedTransitionScope) {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = lazyListState,
                reverseLayout = true
            ) {
                // dummy item for animation
                item(key = Long.MAX_VALUE) {
                    Spacer(
                        modifier = Modifier
                            .height(1.dp)
                    )
                }
                // invitations
                invitations?.reversed()?.let { invites ->
                    items(
                        items = invites,
                        key = { invitation -> invitation.dialogUuid.leastSignificantBits }) { invitation ->
                        LaunchedEffect(invitation.dialogUuid) {
                            AndroidNotificationManager.clearInvitationNotification(
                                invitation.dialogUuid
                            )
                        }
                        InvitationListItem(
                            modifier = Modifier.animateItem(),
                            invitationListViewModel = invitationViewModel,
                            invitation = invitation,
                            title = AnnotatedString(invitation.statusText),
                            date = invitation.getAnnotatedDate(context = context),
                            initialViewSetup = { initialView ->
                                invitationViewModel.initialViewSetup(
                                    initialView,
                                    invitation
                                )
                            },
                            onClick = { action, invite, lastSAS ->
                                invitationViewModel.invitationClicked(
                                    action,
                                    invite,
                                    lastSAS,
                                    context
                                )
                            }
                        )
                    }
                }
                // messages
                items(
                    count = messages.itemCount,
                    key = messages.itemKey { it.id },
                    contentType = messages.itemContentType { it.messageType }
                ) { index ->
                    val message = messages[index]
                    message?.let {
                        LaunchedEffect(
                            startCollectingMessagesToMarkAsRead,
                            message.id,
                            message.status,
                            message.messageType
                        ) {
                            if (startCollectingMessagesToMarkAsRead) {
                                if (message.status == Message.STATUS_UNREAD
                                    || message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ
                                ) {
                                    discussionViewModel.messageIdsToMarkAsRead.add(message.id)
                                    if (discussionViewModel.latestServerTimestampOfMessageToMarkAsRead < message.timestamp) {
                                        discussionViewModel.latestServerTimestampOfMessageToMarkAsRead =
                                            message.timestamp
                                    }

                                    if ((message.isInbound && message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ)
                                        || message.messageType == Message.TYPE_INBOUND_MESSAGE && message.status == Message.STATUS_UNREAD
                                    ) {
                                        // only send the read receipt if the content of the message was actually displayed
                                        App.runThread {
                                            if (sendReadReceipt) {
                                                message.sendMessageReturnReceipt(
                                                    discussionViewModel.discussion.value,
                                                    Message.RETURN_RECEIPT_STATUS_READ
                                                )
                                            }
                                            CreateReadMessageMetadata(message.id).run()
                                        }
                                    }
                                }
                            }
                        }
                        LaunchedEffect(message.wipeStatus) {
                            if (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ && !discussionViewModel.screenShotBlockedForEphemeral) {
                                discussionViewModel.screenShotBlockedForEphemeral = true
                                activity.window?.setFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }
                        }
                        Column(modifier = Modifier.animateItem()) {
                            // unread count
                            (unreadCountAndFirstMessage?.unreadCount ?: 0)
                                .let { unreadCount ->
                                    if (unreadCount > 0) {
                                        if (message.id == unreadCountAndFirstMessage?.messageId) {
                                            Text(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .fillMaxWidth()
                                                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                                                    .background(
                                                        color = colorResource(id = R.color.red),
                                                        shape = RoundedCornerShape(
                                                            14.dp
                                                        )
                                                    )
                                                    .padding(
                                                        horizontal = 18.dp,
                                                        vertical = 6.dp
                                                    ),
                                                text = pluralStringResource(
                                                    id = R.plurals.text_unread_message_count,
                                                    unreadCount,
                                                    unreadCount
                                                ),
                                                style = OlvidTypography.body2,
                                                color = colorResource(id = R.color.alwaysWhite)
                                            )
                                        }
                                    }
                                }

                            val messageExpiration by AppDatabase.getInstance()
                                .messageExpirationDao().getLive(message.id)
                                .observeAsState()
                            var offset by remember {
                                mutableStateOf(Offset.Zero)
                            }
                            if (message.id == discussionViewModel.locationContextMenuState?.message?.id) {
                                LocationContextMenu(
                                    discussionViewModel = discussionViewModel,
                                    locationMessageHandler = locationMessageHandler
                                )
                            }
                            // missed count
                            if (message.isInbound && message.missedMessageCount > 0) {
                                MissedMessageCount(
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .padding(
                                            start =
                                                if (discussionViewModel.discussion.value?.discussionType != Discussion.TYPE_CONTACT) 48.dp else 8.dp
                                        ),
                                    missedMessageCount = message.missedMessageCount.toInt()
                                )
                            }
                            // message
                            val interactionSource =
                                remember { MutableInteractionSource() }
                            LaunchedEffect(highlightMessageId) {
                                if (message.id == highlightMessageId) {
                                    val press = PressInteraction.Press(offset)
                                    try {
                                        interactionSource.emit(press)
                                        delay(500)
                                        highlightMessageId = -1
                                    } finally {
                                        interactionSource.emit(
                                            PressInteraction.Release(
                                                press
                                            )
                                        )
                                    }
                                }
                            }
                            io.olvid.messenger.discussion.message.Message(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sharedElementWithCallerManagedVisibility(
                                        visible = discussionViewModel.selectedMessageInfo == null,
                                        sharedContentState = rememberSharedContentState(
                                            message.id
                                        )
                                    )
                                    .then(
                                        if (!discussionViewModel.isSelectingForDeletion)
                                            Modifier.combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    color = colorResource(
                                                        id = R.color.blueOrWhiteOverlay
                                                    )
                                                ),
                                                onDoubleClick = {
                                                    messageEditHandler.enterEditModeIfAllowed(
                                                        message
                                                    )
                                                },
                                                onLongClick = {
                                                    if (discussionViewModel.isSelectingForDeletion) {
                                                        messageClicked(message)
                                                    } else {
                                                        keyboardController?.hide()
                                                        discussionViewModel.selectedMessageInfo = message
                                                    }
                                                }) { messageClicked(message) }
                                        else
                                            Modifier.clickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    color = colorResource(
                                                        id = R.color.blueOrWhiteOverlay
                                                    )
                                                )
                                            ) { messageClicked(message) }
                                    )
                                    .background(
                                        if (discussionViewModel.selectedMessageIds.contains(message.id))
                                            colorResource(id = R.color.olvid_gradient_light)
                                        else
                                            Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .cutoutHorizontalPadding()
                                    .systemBarsHorizontalPadding()
                                    .onGloballyPositioned {
                                        offset = it.positionOnScreen()
                                    },
                                message = it,
                                onClick = { messageClicked(it) },
                                onLongClick = {
                                    if (discussionViewModel.isSelectingForDeletion) {
                                        messageClicked(message)
                                    } else {
                                        keyboardController?.hide()
                                        discussionViewModel.selectedMessageInfo = message
                                    }
                                },
                                onDoubleClick = {
                                    messageEditHandler.enterEditModeIfAllowed(message)
                                },
                                onLocationClick = {
                                    locationMessageHandler.onLocationClick(it)
                                },
                                 onLocationLongClick = {
                                    message.jsonLocation?.let {
                                        AppSingleton.getJsonObjectMapper()
                                            .readValue(it, JsonLocation::class.java)
                                    }?.let { jsonLocation ->
                                        discussionViewModel.locationContextMenuState =
                                            LocationContextMenuState(
                                            message = message,
                                            truncatedLatitude = jsonLocation.truncatedLatitudeString,
                                            truncatedLongitude = jsonLocation.truncatedLongitudeString,
                                        )
                                    }
                                },
                                onAttachmentLongClick = { fyleAndStatus ->
                                    discussionViewModel.longClickedFyleAndStatus =
                                        fyleAndStatus
                                },
                                onCallButtonClicked = { callLogId ->
                                    callHandler.onCallButtonClicked(
                                        callLogId
                                    )
                                },
                                scrollToMessage = { messageId ->
                                    discussionViewModel.scrollToMessageRequest =
                                        DiscussionActivity.ScrollRequest(messageId)
                                },
                                scale = fontScale,
                                useAnimatedEmojis = discussionViewModel.useAnimatedEmojis,
                                loopAnimatedEmojis = discussionViewModel.loopAnimatedEmojis,
                                replyAction = {
                                    if (!messageEditHandler.isEditMode()) {
                                        keyboardController?.show()
                                        discussionViewModel.replyToMessage(
                                            message.id,
                                            composeMessageViewModel.rawNewMessageText.toString().trim()
                                        )
                                    }
                                }.takeIf {
                                    message.messageType in listOf(
                                        Message.TYPE_INBOUND_MESSAGE,
                                        Message.TYPE_OUTBOUND_MESSAGE
                                    ) && discussionViewModel.locked != true
                                },
                                editedSeen = {
                                    if (message.edited == Message.EDITED_UNSEEN) {
                                        discussionViewModel.editedMessageIdsToMarkAsSeen.add(
                                            message.id
                                        )
                                    }
                                },
                                menuAction = {
                                    keyboardController?.hide()
                                    discussionViewModel.selectedMessageInfo = message
                                },
                                showSender = message.isInbound && discussionViewModel.discussion.value?.discussionType != Discussion.TYPE_CONTACT,
                                lastFromSender = messages.itemSnapshotList.getOrNull(
                                    index + 1
                                )?.let {
                                    !it.senderIdentifier.contentEquals(message.senderIdentifier)
                                            || (it.messageType != Message.TYPE_INBOUND_MESSAGE && it.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
                                            || Utils.notTheSameDay(
                                        message.timestamp,
                                        it.timestamp
                                    )
                                            || !message.isTextOnly
                                            || (message.status == Message.STATUS_UNREAD && it.status != Message.STATUS_UNREAD)
                                } != false,
                                linkPreviewViewModel = linkPreviewViewModel,
                                messageExpiration = messageExpiration,
                                discussionViewModel = discussionViewModel,
                                discussionSearchViewModel = discussionSearchViewModel,
                                audioAttachmentServiceBinding = activity.audioAttachmentServiceBinding,
                                openDiscussionDetailsCallback = {
                                    openDiscussionDetailsCallback()
                                },
                                openViewerCallback = {
                                    discussionViewModel.markAsReadOnPause = false
                                },
                                saveAttachment = { saveAttachment() },
                                saveAllAttachments = { saveAllAttachments() },
                            )
                        }
                        // date header
                        if (Utils.notTheSameDay(
                                message.timestamp,
                                messages.itemSnapshotList.getOrNull(
                                    index + 1
                                )?.timestamp ?: 0
                            )
                        ) {
                            val date = StringUtils.getDayOfDateString(
                                context,
                                message.timestamp
                            ).toString()
                            DateHeader(
                                modifier = Modifier.animateItem(),
                                date = date
                            )
                        }
                    }
                    // disclaimer
                    if (discussionViewModel.discussion.value?.isPreDiscussion != true && index == messages.itemCount - 1) {
                        MessageDisclaimer(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .padding(8.dp)
                                .widthIn(max = 400.dp)
                        )
                    }
                }
                // disclaimer when no messages
                if (discussionViewModel.discussion.value?.isPreDiscussion != true && messages.loadState.source.isIdle && messages.itemCount == 0) {
                    item(key = -1L) {
                        MessageDisclaimer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .widthIn(max = 400.dp)
                        )
                    }
                }
            }
            val locationMessages by discussionViewModel.currentlySharingLocationMessagesLiveData.observeAsState()
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var cachedCallData by remember {
                    mutableStateOf(
                        CallNotificationManager.currentCallData
                    )
                }
                LaunchedEffect(CallNotificationManager.currentCallData) {
                    if (CallNotificationManager.currentCallData != null) {
                        cachedCallData = CallNotificationManager.currentCallData
                    } else {
                        delay(500)
                        cachedCallData = null
                    }
                }
                AnimatedVisibility(
                    // visibility is the && otherwise when currentCallData becomes non-null,
                    // cachedCallData is still null and the animation applies to a empty CallNotification
                    visible = cachedCallData != null && CallNotificationManager.currentCallData != null
                ) {
                    cachedCallData?.let {
                        CallNotification(callData = it)
                    }
                }


                AnimatedVisibility(
                    visible = locationMessages.isNullOrEmpty()
                        .not() && lazyListState.isScrollInProgress.not()
                ) {
                    val isDiscussionSharingLocation =
                        discussionViewModel.discussionId?.let {
                            LocationSharingSubService.isDiscussionSharingLocation(
                                it
                            )
                        } == true
                    locationMessages?.let { messages ->
                        LocationSharing(
                            messages = messages,
                            isDiscussionSharingLocation = isDiscussionSharingLocation,
                            onGotoMessage = { messageId ->
                                discussionViewModel.scrollToMessageRequest =
                                    DiscussionActivity.ScrollRequest(messageId)
                            },
                            onStopSharingLocation = {
                                discussionViewModel.discussionId?.let {
                                    LocationSharingSubService.stopSharingInDiscussion(
                                        it, false
                                    )
                                }
                            },
                            onOpenMap = { openMap() }
                        )
                    }
                }
            }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .cutoutHorizontalPadding()
                    .systemBarsHorizontalPadding()
                    .padding(
                        end = 12.dp,
                        bottom = 12.dp),
                visible = showScrollDownButton,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                ScrollDownButton {
                    discussionViewModel.scrollToMessageRequest = DiscussionActivity.ScrollRequest.ToBottom
                }
            }
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(delayMillis = 500)),
                visible = stickyDate != null && lazyListState.isScrollInProgress
            ) {
                stickyDate?.let {
                    DateHeader(date = it.toString())
                }
            }
        }
    }
}
