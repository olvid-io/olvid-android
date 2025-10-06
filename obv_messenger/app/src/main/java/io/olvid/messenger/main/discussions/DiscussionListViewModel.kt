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

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog.Builder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.string
import io.olvid.messenger.UnreadCountsSingleton
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.Type.DISCUSSION
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.customClasses.formatSingleLineMarkdown
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndLastMessage
import io.olvid.messenger.databases.dao.DiscussionDao.SimpleDiscussionAndLastMessage
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.PropagateArchivedDiscussionsChangeTask
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask
import io.olvid.messenger.databases.tasks.propagateMuteSettings
import io.olvid.messenger.notifications.NotificationActionService
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiscussionListViewModel : ViewModel() {

    private val _selection = mutableSetOf<Long>()
    var selection by mutableStateOf<List<DiscussionAndLastMessage>>(emptyList())
        private set

    var cancelableArchivedDiscussions by mutableStateOf<List<Discussion>>(emptyList())

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
        selection = discussions.value?.filter { _selection.contains(it.discussion.id) }
            .orEmpty() +
                archivedDiscussions.value?.filter { _selection.contains(it.discussion.id) }
                    .orEmpty()
    }


    val discussions: LiveData<List<DiscussionAndLastMessage>?> = DiscussionAndLastMessageLiveData(
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            } else {
                return@switchMap AppDatabase.getInstance().discussionDao()
                    .getNonDeletedDiscussionAndLastMessages(ownedIdentity.bytesOwnedIdentity, false)
            }
        },
        UnreadCountsSingleton.getUnreadMessageCountsByDiscussionLiveData(),
        UnreadCountsSingleton.getMentionsDiscussionsLiveData(),
        UnreadCountsSingleton.getLocationsSharedDiscussionsLiveData()
    )
    val archivedDiscussions: LiveData<List<DiscussionAndLastMessage>?> =
        DiscussionAndLastMessageLiveData(
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap null
                } else {
                    return@switchMap AppDatabase.getInstance().discussionDao()
                        .getNonDeletedDiscussionAndLastMessages(
                            ownedIdentity.bytesOwnedIdentity,
                            true
                        )
                }
            },
            UnreadCountsSingleton.getUnreadMessageCountsByDiscussionLiveData(),
            UnreadCountsSingleton.getMentionsDiscussionsLiveData(),
            UnreadCountsSingleton.getLocationsSharedDiscussionsLiveData()
        )

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
                AppSingleton.getBytesCurrentIdentity()?.let { bytesOwnedIdentity ->
                    db.discussionDao().getAllPinned(bytesOwnedIdentity)
                } ?: emptyList()
            val pinnedDiscussionsMap =
                HashMap(oldPinnedDiscussions.associateBy { discussion -> discussion.id })
            val pinnedDiscussions =
                reorderList?.filter { it.discussion.pinned != 0 }?.map { it.discussion }

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
            AppSingleton.getBytesCurrentIdentity()
                ?.let { PropagatePinnedDiscussionsChangeTask(it).run() }
        }
    }

    fun archiveSelectedDiscussions(archived: Boolean) {
        archiveDiscussion(
            *selection.map { it.discussion }.toTypedArray(),
            archived = archived,
            cancelable = true
        )
    }

    fun archiveDiscussion(vararg discussion: Discussion, archived: Boolean, cancelable: Boolean) {
        if (cancelable) {
            cancelableArchivedDiscussions = cancelableArchivedDiscussions + discussion
        }
        viewModelScope.launch(Dispatchers.IO) {
            discussion.asList().apply {
                forEach {
                    AppDatabase.getInstance().discussionDao().updateArchived(archived, it.id)
                    AppSingleton.getEngine().profileBackupNeeded(it.bytesOwnedIdentity)
                }
                propagateArchivedDiscussions(this, archived)
            }
        }
    }

    private fun propagateArchivedDiscussions(discussions: List<Discussion>, archived: Boolean) {
        AppSingleton.getBytesCurrentIdentity()
            ?.let { PropagateArchivedDiscussionsChangeTask(it, discussions, archived).run() }
    }


    fun markAllDiscussionMessagesRead(discussionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            NotificationActionService.markAllDiscussionMessagesRead(
                discussionId
            )
        }
    }

    fun markDiscussionAsUnread(discussionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(
                discussionId, true
            )
        }
    }

    fun deleteDiscussions(discussions: List<Discussion>, context: Context, onDelete: () -> Unit) {
        if (discussions.isEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // canRemoteDelete if all discussion are locked, have no members, or groupV2 with correct permission
            // couldOfferToRemoteDelete if at least one discussion has members (only relevant if canRemoteDelete is true)
            var canRemoteDelete = true
            var couldOfferToRemoteDelete = false
            for (discussion in discussions) {
                if (discussion.isNormalOrReadOnly) {
                    if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                        val group2 = AppDatabase.getInstance()
                            .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                        if (group2 != null) {
                            if (AppDatabase.getInstance().group2MemberDao().groupHasMembers(
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            ) {
                                couldOfferToRemoteDelete = true
                                if (!group2.ownPermissionRemoteDeleteAnything) {
                                    canRemoteDelete = false
                                    break
                                }
                            }
                        }
                    } else if (discussion.discussionType == Discussion.TYPE_GROUP) {
                        if (AppDatabase.getInstance().contactGroupJoinDao().groupHasMembers(
                                discussion.bytesOwnedIdentity,
                                discussion.bytesDiscussionIdentifier
                            )
                        ) {
                            canRemoteDelete = false
                            break
                        }
                    } else {
                        canRemoteDelete = false
                        break
                    }
                }
            }

            val builder = SecureDeleteEverywhereDialogBuilder(
                context,
                DISCUSSION,
                discussions.size,
                canRemoteDelete && couldOfferToRemoteDelete,
                true
            )
                .setDeleteCallback { deletionChoice: SecureDeleteEverywhereDialogBuilder.DeletionChoice ->
                    onDelete()
                    App.runThread {
                        discussions.forEach { discussion ->
                            DeleteMessagesTask(
                                discussion.id,
                                deletionChoice,
                                false
                            ).run()
                        }
                    }
                }
            Handler(Looper.getMainLooper()).post { builder.create().show() }
        }
    }

    fun muteSelectedDiscussions(
        context: Context,
        discussionsAndLastMessage: List<DiscussionAndLastMessage>,
        muted: Boolean,
        onActionDone: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (muted) {
                Handler(Looper.getMainLooper()).post {
                    val muteNotificationDialog = MuteNotificationDialog(
                        context,
                        { muteExpirationTimestamp: Long?, _: Boolean, muteExceptMentioned: Boolean ->
                            onActionDone()
                            viewModelScope.launch(Dispatchers.IO) {
                                discussionsAndLastMessage.forEach { selected ->
                                    AppDatabase.getInstance()
                                        .discussionCustomizationDao()[selected.discussion.id]?.let {
                                        AppDatabase.getInstance().discussionCustomizationDao()
                                            .update(it.apply {
                                                prefMuteNotifications = true
                                                prefMuteNotificationsTimestamp =
                                                    muteExpirationTimestamp
                                                prefMuteNotificationsExceptMentioned =
                                                    muteExceptMentioned
                                            })
                                    } ifNull {
                                        AppDatabase.getInstance().discussionCustomizationDao()
                                            .insert(DiscussionCustomization(selected.discussion.id).apply {
                                                prefMuteNotifications = true
                                                prefMuteNotificationsTimestamp =
                                                    muteExpirationTimestamp
                                                prefMuteNotificationsExceptMentioned =
                                                    muteExceptMentioned
                                            })
                                    }
                                    AppSingleton.getEngine()
                                        .profileBackupNeeded(selected.discussion.bytesOwnedIdentity)
                                }
                                discussionsAndLastMessage.map { it.discussion }
                                    .propagateMuteSettings(
                                        true,
                                        muteExpirationTimestamp,
                                        muteExceptMentioned
                                    )
                            }
                        },
                        MuteNotificationDialog.MuteType.DISCUSSIONS,
                        discussionsAndLastMessage.firstOrNull()?.discussionCustomization?.prefMuteNotificationsExceptMentioned != false
                    )
                    muteNotificationDialog.show()
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    val builder: Builder =
                        SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                            .setTitle(string.dialog_title_unmute_notifications)
                            .setPositiveButton(string.button_label_unmute_notifications) { _, _ ->
                                onActionDone()
                                viewModelScope.launch(Dispatchers.IO) {
                                    discussionsAndLastMessage.forEach { selected ->
                                        AppDatabase.getInstance()
                                            .discussionCustomizationDao()[selected.discussion.id]?.let {
                                            AppDatabase.getInstance().discussionCustomizationDao()
                                                .update(it.apply {
                                                    prefMuteNotifications = false
                                                })
                                        }
                                        AppSingleton.getEngine()
                                            .profileBackupNeeded(selected.discussion.bytesOwnedIdentity)
                                    }
                                    discussionsAndLastMessage.map { it.discussion }
                                        .propagateMuteSettings(false, null, false)
                                }
                            }
                            .setNegativeButton(string.button_label_cancel, null)
                    builder.setMessage(string.dialog_message_unmute_notifications)
                    builder.create().show()
                }
            }
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

fun Discussion.getAnnotatedDate(context: Context, message: Message?): AnnotatedString? {
    return message?.timestamp?.let {
        buildAnnotatedString {
            append(StringUtils.getCompactDateString(context, message.timestamp) as String)
            if (isLocked) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, length)
            }
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
                            append(context.getString(string.text_draft_message_prefix))
                            append(body.formatSingleLineMarkdown())
                        }
                    } else if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                        || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                        || message.isLocationMessage
                    ) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(AnnotatedString(body).formatMarkdown())
                        }
                    } else {
                        append(body.formatSingleLineMarkdown())
                    }
                }

                Message.TYPE_GROUP_MEMBER_JOINED -> {
                    val byYou = bytesOwnedIdentity.contentEquals(message.senderIdentifier)
                    var displayName =
                        ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    var mentionCount = 0
                    val mention = message.jsonMentions?.let {
                        message.mentions?.let { mentions ->
                            mentionCount = mentions.size
                            when (mentions.size) {
                                0 -> null
                                1 -> ContactCacheSingleton.getContactCustomDisplayName(mentions.first().userIdentifier) ?: run {
                                    displayName = null // reset the displayName to null too
                                    null
                                }
                                else -> context.resources.getQuantityString(R.plurals.other_members_count, mentions.size, mentions.size)
                            }
                        } ?: run {
                            displayName = null // reset the displayName to null too
                            null
                        }
                    }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (mention != null) {
                                if (displayName != null) {
                                    if (byYou) {
                                        context.getString(
                                            string.text_joined_the_group_by_you,
                                            displayName,
                                            mention
                                        )
                                    } else {
                                        context.getString(
                                            string.text_joined_the_group_by,
                                            displayName,
                                            mention
                                        )
                                    }
                                } else {
                                    context.resources.getQuantityString(
                                        R.plurals.text_joined_the_group,
                                        mentionCount,
                                        mention
                                    )
                                }
                            } else if (displayName != null) {
                                context.resources.getQuantityString(
                                    R.plurals.text_joined_the_group,
                                    1,
                                    displayName
                                )
                            } else {
                                context.getString(string.text_unknown_member_joined_the_group)
                            }
                        )
                    }
                }

                Message.TYPE_GROUP_MEMBER_LEFT -> {
                    val byYou = bytesOwnedIdentity.contentEquals(message.senderIdentifier)
                    var displayName =
                        ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    var mentionCount = 0
                    val mention = message.jsonMentions?.let {
                        message.mentions?.let { mentions ->
                            mentionCount = mentions.size
                            when (mentions.size) {
                                0 -> null
                                1 -> ContactCacheSingleton.getContactCustomDisplayName(mentions.first().userIdentifier) ?: run {
                                    displayName = null // reset the displayName to null too
                                    null
                                }
                                else -> context.resources.getQuantityString(R.plurals.other_members_count, mentions.size, mentions.size)
                            }
                        } ?: run {
                            displayName = null // reset the displayName to null too
                            null
                        }
                    }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(
                            if (mention != null) {
                                if (displayName != null) {
                                    if (byYou) {
                                        context.getString(
                                            string.text_left_the_group_by_you,
                                            displayName,
                                            mention
                                        )
                                    } else {
                                        context.getString(
                                            string.text_left_the_group_by,
                                            displayName,
                                            mention
                                        )
                                    }
                                } else {
                                    context.resources.getQuantityString(
                                        R.plurals.text_left_the_group,
                                        mentionCount,
                                        mention
                                    )
                                }
                            } else if (displayName != null) {
                                context.resources.getQuantityString(
                                    R.plurals.text_left_the_group,
                                    1,
                                    displayName
                                )
                            } else {
                                context.getString(string.text_unknown_member_left_the_group)
                            }
                        )
                    }
                }

                Message.TYPE_DISCUSSION_REMOTELY_DELETED -> {
                    val displayName =
                        ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
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
                                    ContactCacheSingleton.getContactCustomDisplayName(
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
                        (if (SettingsActivity.allowContactFirstName)
                            ContactCacheSingleton.getContactFirstName(message.senderIdentifier)
                        else
                            ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
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

private class DiscussionAndLastMessageLiveData(
    discussionsLiveData: LiveData<List<SimpleDiscussionAndLastMessage>>,
    unreadCountsLiveData: LiveData<Map<Long, Int>>,
    mentionsLiveData: LiveData<Set<Long>>,
    locationsSharedLiveData: LiveData<Set<Long>>
) : MediatorLiveData<List<DiscussionAndLastMessage>?>() {
    private var unreadCounts: Map<Long, Int> = emptyMap()
    private var mentions: Set<Long> = emptySet()
    private var locationsShared: Set<Long> = emptySet()
    private var discussions: List<SimpleDiscussionAndLastMessage>? = null

    init {
        addSource(discussionsLiveData) {
            discussions = it
            merge()
        }
        addSource(unreadCountsLiveData) {
            unreadCounts = it
            merge()
        }
        addSource(mentionsLiveData) {
            mentions = it
            merge()
        }
        addSource(locationsSharedLiveData) {
            locationsShared = it
            merge()
        }
    }

    fun merge() {
        value = discussions?.map {
            return@map DiscussionAndLastMessage().apply {
                discussion = it.discussion
                message = it.message
                discussionCustomization = it.discussionCustomization
                unreadCount = unreadCounts[it.discussion.id] ?: 0
                unreadMention = mentions.contains(it.discussion.id)
                locationsShared =
                    this@DiscussionAndLastMessageLiveData.locationsShared.contains(it.discussion.id)
            }
        }
    }
}
