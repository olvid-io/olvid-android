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
package io.olvid.messenger.main.discussions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.customClasses.formatSingleLineMarkdown
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndLastMessage
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiscussionListViewModel : ViewModel() {

    private val _selection = mutableSetOf<Long>()
    var selection by mutableStateOf<List<DiscussionAndLastMessage>>(emptyList())
        private set

    fun isSelected(discussion: Discussion): Boolean = _selection.contains(discussion.id)

    fun clearSelection() {
        viewModelScope.launch(Dispatchers.IO) {
            _selection.clear()
            selection = emptyList()
        }
    }

    fun enableSelection(discussion: Discussion) {
        _selection.add(discussion.id)
        refreshSelection()
    }

    fun toggleSelection(discussion: Discussion) {
        if (_selection.remove(discussion.id).not()) {
            _selection.add(discussion.id)
        }
        refreshSelection()
    }

    fun refreshSelection() {
        selection = discussions.value?.filter { _selection.contains(it.discussion.id) }.orEmpty()
    }


    val discussions: LiveData<List<DiscussionAndLastMessage>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            } else {
                return@switchMap AppDatabase.getInstance().discussionDao()
                    .getNonDeletedDiscussionAndLastMessages(ownedIdentity.bytesOwnedIdentity)
            }
        }

    var reorderList by mutableStateOf<List<DiscussionAndLastMessage>?>(null)

    fun pinSelectedDiscussions() {
        reorderList = reorderList?.toMutableList()?.onEach {
            if (_selection.contains(it.discussion.id)) {
                it.apply { discussion.pinned = 1 }
            }
        }
        syncPinnedDiscussions()
    }

    fun unpinSelectedDiscussions() {
        reorderList = reorderList?.toMutableList()?.onEach {
            if (_selection.contains(it.discussion.id)) {
                it.apply { discussion.pinned = 0 }
            }
        }
        syncPinnedDiscussions()
    }

    fun syncPinnedDiscussions() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance()
            val oldPinnedDiscussions =
                db.discussionDao().getAllPinned(AppSingleton.getBytesCurrentIdentity())
            val pinnedDiscussionsMap =
                HashMap(oldPinnedDiscussions.associateBy { discussion -> discussion.id })
            val pinnedDiscussions = reorderList?.filter { it.discussion.pinned != 0 }?.map { it.discussion }

            // before actually changing anything in db, check if the order actually changed
            if (pinnedDiscussions?.map { it.id } == oldPinnedDiscussions.map { it.id }) {
                return@launch
            }

            var pinnedIndex = 1
            pinnedDiscussions?.forEach {
                // never pin a pre-discussion
                if (it.status != Discussion.STATUS_PRE_DISCUSSION) {
                    pinnedDiscussionsMap.remove(it.id)
                    // update ordered index
                    db.discussionDao().updatePinned(it.id, pinnedIndex++)
                }
            }
            // unpin any discussion that is not in the old list
            pinnedDiscussionsMap.values.forEach {
                db.discussionDao().updatePinned(it.id, 0)
            }
            AppSingleton.getBytesCurrentIdentity()?.let { PropagatePinnedDiscussionsChangeTask(it).run() }
        }
    }
}

fun Discussion.getAnnotatedTitle(context: Context): AnnotatedString {
    return buildAnnotatedString {
        if (title.isNullOrEmpty()) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(context.getString(string.text_unnamed_discussion))
            }
        } else {
            append(title)
            if (isLocked) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, length)
            }
        }
    }
}

fun Discussion.getAnnotatedDate(context: Context, message: Message?): AnnotatedString {
    return buildAnnotatedString {
        append(
            message?.timestamp?.let {
                StringUtils.getLongNiceDateString(context, it) as String
            } ?: ""
        )
        if (isLocked) {
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, length)
        }
    }
}

fun Discussion.getAnnotatedBody(context: Context, message: Message?): AnnotatedString {
    return buildAnnotatedString {
        message?.let { message ->
            when (message.messageType) {
                Message.TYPE_OUTBOUND_MESSAGE -> {
                    val body = message.getStringContent(context)
                    if (message.status == Message.STATUS_DRAFT) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(
                                context.getString(
                                    string.text_draft_message_prefix,
                                    ""
                                )
                            )
                            append(body.formatSingleLineMarkdown())
                        }
                    } else if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                        || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                    ) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(AnnotatedString(body).formatMarkdown())
                        }
                    } else {
                        if (message.isLocationMessage) {
                            append(
                                context.getString(
                                    string.text_outbound_message_prefix,
                                    body
                                )
                            )
                            addStyle(
                                SpanStyle(fontStyle = FontStyle.Italic),
                                0,
                                length - body.length
                            )
                        } else {
                            append(
                                context.getString(
                                    string.text_outbound_message_prefix,
                                    ""
                                )
                            )
                            append(body.formatSingleLineMarkdown())
                        }
                    }
                }

                Message.TYPE_GROUP_MEMBER_JOINED -> {
                    val displayName =
                        AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (displayName != null) context.getString(
                                string.text_joined_the_group,
                                displayName
                            ) else context.getString(string.text_unknown_member_joined_the_group)
                        )
                    }
                }

                Message.TYPE_GROUP_MEMBER_LEFT -> {
                    val displayName =
                        AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (displayName != null) context.getString(
                                string.text_left_the_group,
                                displayName
                            ) else context.getString(string.text_unknown_member_left_the_group)
                        )
                    }
                }

                Message.TYPE_DISCUSSION_REMOTELY_DELETED -> {
                    val displayName =
                        AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (displayName != null)
                                context.getString(
                                    string.text_discussion_remotely_deleted_by,
                                    displayName
                                )
                            else
                                context.getString(string.text_discussion_remotely_deleted)
                        )
                    }
                }

                Message.TYPE_LEFT_GROUP -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append((context.getString(string.text_group_left)))
                    }
                }

                Message.TYPE_CONTACT_INACTIVE_REASON -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (Message.NOT_ACTIVE_REASON_REVOKED == message.contentBody)
                                context.getString(string.text_contact_was_blocked_revoked)
                            else
                                context.getString(string.text_contact_was_blocked)
                        )
                    }
                }

                Message.TYPE_PHONE_CALL -> {
                    var callStatus = CallLogItem.STATUS_MISSED
                    try {
                        val statusAndCallLogItemId =
                            message.contentBody?.split(":".toRegex())
                                ?.dropLastWhile { it.isEmpty() }
                                ?.toTypedArray()
                        statusAndCallLogItemId?.firstOrNull()?.let {
                            callStatus = it.toInt()
                        }
                    } catch (e: Exception) {
                        // do nothing
                    }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {

                        append(
                            when (callStatus) {
                                -CallLogItem.STATUS_BUSY -> {
                                    context.getString(string.text_busy_outgoing_call)
                                }

                                -CallLogItem.STATUS_REJECTED, CallLogItem.STATUS_REJECTED, CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE -> {
                                    context.getString(string.text_rejected_call)
                                }

                                -CallLogItem.STATUS_MISSED -> {
                                    context.getString(string.text_unanswered_call)
                                }

                                -CallLogItem.STATUS_FAILED, CallLogItem.STATUS_FAILED -> {
                                    context.getString(string.text_failed_call)
                                }

                                -CallLogItem.STATUS_SUCCESSFUL, CallLogItem.STATUS_SUCCESSFUL, CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE -> {
                                    context.getString(string.text_successful_call)
                                }

                                CallLogItem.STATUS_BUSY -> {
                                    context.getString(string.text_busy_call)
                                }

                                CallLogItem.STATUS_MISSED -> {
                                    context.getString(string.text_missed_call)
                                }

                                else -> {
                                    context.getString(string.text_successful_call)
                                }
                            }
                        )
                    }
                }

                Message.TYPE_NEW_PUBLISHED_DETAILS -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (discussionType == Discussion.TYPE_CONTACT)
                                context.getString(string.text_contact_details_updated)
                            else
                                context.getString(string.text_group_details_updated)
                        )
                    }
                }

                Message.TYPE_CONTACT_DELETED -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_user_removed_from_contacts))
                    }
                }

                Message.TYPE_DISCUSSION_SETTINGS_UPDATE -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_discussion_shared_settings_updated))
                    }
                }

                Message.TYPE_CONTACT_RE_ADDED -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_user_added_to_contacts))
                    }
                }

                Message.TYPE_RE_JOINED_GROUP -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_group_re_joined))
                    }
                }

                Message.TYPE_JOINED_GROUP -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_group_joined))
                    }
                }

                Message.TYPE_GAINED_GROUP_ADMIN -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_you_became_admin))
                    }
                }

                Message.TYPE_LOST_GROUP_ADMIN -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_you_are_no_longer_admin))
                    }
                }

                Message.TYPE_GAINED_GROUP_SEND_MESSAGE -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_you_became_writer))
                    }
                }

                Message.TYPE_LOST_GROUP_SEND_MESSAGE -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(context.getString(string.text_you_are_no_longer_writer))
                    }
                }

                Message.TYPE_SCREEN_SHOT_DETECTED -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (message.senderIdentifier.contentEquals(AppSingleton.getBytesCurrentIdentity())
                            ) {
                                context.getString(string.text_you_captured_sensitive_message)
                            } else {
                                val displayName =
                                    AppSingleton.getContactCustomDisplayName(
                                        message.senderIdentifier
                                    )
                                if (displayName != null) {
                                    context.getString(
                                        string.text_xxx_captured_sensitive_message,
                                        displayName
                                    )
                                } else {
                                    context.getString(string.text_unknown_member_captured_sensitive_message)
                                }
                            }
                        )
                    }
                }

                Message.TYPE_INBOUND_EPHEMERAL_MESSAGE -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(AnnotatedString(message.getStringContent(context)).formatMarkdown())
                    }
                }

                Message.TYPE_MEDIATOR_INVITATION_SENT -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            context.getString(
                                string.invitation_status_mediator_invite_information_sent,
                                message.contentBody
                            )
                        )
                    }
                }

                Message.TYPE_MEDIATOR_INVITATION_ACCEPTED -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            context.getString(
                                string.invitation_status_mediator_invite_information_accepted,
                                message.contentBody
                            )
                        )
                    }
                }

                Message.TYPE_MEDIATOR_INVITATION_IGNORED -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            context.getString(
                                string.invitation_status_mediator_invite_information_ignored,
                                message.contentBody
                            )
                        )
                    }
                }

                else -> {
                    if (discussionType == Discussion.TYPE_GROUP || discussionType == Discussion.TYPE_GROUP_V2) {
                        (if (SettingsActivity.getAllowContactFirstName())
                            AppSingleton.getContactFirstName(message.senderIdentifier)
                        else
                            AppSingleton.getContactCustomDisplayName(message.senderIdentifier)
                                )?.let {
                                append(
                                    context.getString(
                                        string.text_inbound_group_message_prefix,
                                        it
                                    )
                                )
                            }
                    }
                    val body = message.getStringContent(context)
                    if (message.wipeStatus == Message.WIPE_STATUS_WIPED || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED || message.isLocationMessage) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(AnnotatedString(body))
                        }
                    } else {
                        append(body.formatSingleLineMarkdown())
                    }
                }
            }
            // locked discussion is in italic
            if (isLocked) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, length)
            }
        } ifNull {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(context.getString(string.text_no_messages))
            }
        }
    }
}