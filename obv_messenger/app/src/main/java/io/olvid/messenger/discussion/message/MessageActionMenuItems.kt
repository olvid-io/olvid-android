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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.CopySelectedMessageTask
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import io.olvid.messenger.databases.tasks.ShareSelectedMessageTask
import io.olvid.messenger.discussion.DiscussionActivity.DiscussionDelegate

enum class MessageActionType(val stringRes: Int) {
    REPLY(R.string.label_swipe_reply),
    SHARE(R.string.menu_action_share),
    FORWARD(R.string.menu_action_forward),
    COPY(R.string.label_swipe_copy),
    SELECT(R.string.label_swipe_select),
    DETAILS(R.string.label_swipe_details),
    EDIT(R.string.label_swipe_edit),
    BOOKMARK(R.string.menu_action_bookmark),
    DELETE(R.string.menu_action_delete)
}

@Composable
fun MessageActionItemsList(
    canEdit: Boolean,
    message: Message,
    discussion: Discussion,
    discussionDelegate: DiscussionDelegate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val onAction = { actionType: MessageActionType ->
        when (actionType) {
            MessageActionType.REPLY -> discussionDelegate.replyToMessage(
                message.discussionId,
                message.id
            )

            MessageActionType.SHARE -> App.runThread(
                ShareSelectedMessageTask(
                    context as FragmentActivity?,
                    message.id
                )
            )

            MessageActionType.FORWARD -> discussionDelegate.initiateMessageForward(
                message.id,
                onDismiss
            )

            MessageActionType.COPY -> {
                val copyAttachments = message.hasAttachments()
                App.runThread(
                    CopySelectedMessageTask(
                        context as FragmentActivity?,
                        message.id,
                        copyAttachments
                    )
                )
            }

            MessageActionType.SELECT -> discussionDelegate.selectMessage(
                message.id,
                message.isForwardable,
                if (message.isBookmarkableAndDetailable) message.bookmarked else null
            )

            MessageActionType.DETAILS -> {
                discussionDelegate.doNotMarkAsReadOnPause()
                App.openMessageDetails(
                    context,
                    message.id,
                    message.isInbound,
                    message.status == Message.STATUS_SENT_FROM_ANOTHER_DEVICE
                )
            }

            MessageActionType.EDIT -> discussionDelegate.editMessage(message)
            MessageActionType.BOOKMARK -> {
                App.runThread {
                    val newBookmarkState = !message.bookmarked
                    AppDatabase.getInstance().messageDao()
                        .updateBookmarked(newBookmarkState, message.id)
                    PropagateBookmarkedMessageChangeTask(
                        discussion.bytesOwnedIdentity,
                        message,
                        newBookmarkState
                    ).run()
                }
            }

            MessageActionType.DELETE -> SecureDeleteEverywhereDialogBuilder.openForSingleMessage(
                context,
                message,
                discussion
            )
        }
        onDismiss()
    }
    val actions = remember(
        canEdit,
        message.isForwardable,
        message.isBookmarkableAndDetailable,
        message.bookmarked,
        discussion.isNormal
    ) {
        mutableListOf<MessageActionType>().apply {
            if (canEdit && message.isEditable) add(MessageActionType.EDIT)
            if (discussion.isNormal && message.isReplyable) add(MessageActionType.REPLY)
            if (message.isForwardable) {
                add(MessageActionType.SHARE)
                add(MessageActionType.FORWARD)
                add(MessageActionType.COPY)
            } else if (message.isPollMessage) {
                add(MessageActionType.COPY)
            }
            add(MessageActionType.SELECT)
            if (message.isBookmarkableAndDetailable) {
                add(MessageActionType.DETAILS)
                add(MessageActionType.BOOKMARK)
            }
            add(MessageActionType.DELETE)
        }
    }
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .verticalScroll(state = rememberScrollState())
    ) {
        actions.forEach { action ->
            ActionMenuItem(
                action = action,
                message = message,
                onClick = { onAction(action) }
            )
        }
    }
}

@Composable
private fun ActionMenuItem(
    action: MessageActionType,
    message: Message,
    onClick: () -> Unit
) {

    val contentColor =
        if (action == MessageActionType.DELETE) colorResource(R.color.red) else LocalContentColor.current

    DropdownMenuItem(
        modifier = Modifier.fillMaxWidth(),
        text = {
            val text = when {
                action == MessageActionType.BOOKMARK && message.bookmarked -> stringResource(R.string.menu_action_unbookmark)
                else -> stringResource(action.stringRes)
            }
            Text(text, color = contentColor)
        },
        onClick = onClick,
        leadingIcon = {
            val iconInfo = remember(action, message.bookmarked) {
                when (action) {
                    MessageActionType.REPLY -> R.drawable.ic_swipe_reply
                    MessageActionType.SHARE -> R.drawable.ic_swipe_share
                    MessageActionType.FORWARD -> R.drawable.ic_swipe_forward
                    MessageActionType.COPY -> R.drawable.ic_swipe_copy
                    MessageActionType.SELECT -> R.drawable.ic_swipe_select
                    MessageActionType.DETAILS -> R.drawable.ic_swipe_info
                    MessageActionType.EDIT -> R.drawable.ic_swipe_edit
                    MessageActionType.BOOKMARK -> if (message.bookmarked) R.drawable.ic_star_off else R.drawable.ic_star
                    MessageActionType.DELETE -> R.drawable.ic_delete
                }
            }
            Icon(
                painter = painterResource(id = iconInfo),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    )
}