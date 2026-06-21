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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.liveData
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.UnreadCountsSingleton
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndGroupMembersCount
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.dao.Group2MemberDao.Group2MemberOrPendingForMention
import io.olvid.messenger.databases.dao.MessageDao.UnreadCountAndFirstMessage
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import io.olvid.messenger.databases.tasks.SetDraftReplyTask
import io.olvid.messenger.discussion.DiscussionActivity.ScrollRequest
import io.olvid.messenger.discussion.message.LocationContextMenuState
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min


class DiscussionViewModel : ViewModel(), DiscussionDelegate {
    private val db: AppDatabase = AppDatabase.getInstance()

    var locationContextMenuState by mutableStateOf<LocationContextMenuState?>(null)

    // region select for deletion
    var isSelectingForDeletion: Boolean = false
        private set

    var longClickedFyleAndStatus: FyleAndStatus? = null

    val messageIdsToMarkAsRead: MutableSet<Long> by lazy { HashSet() }
    val editedMessageIdsToMarkAsSeen: MutableSet<Long> by lazy { HashSet() }
    var latestServerTimestampOfMessageToMarkAsRead by mutableLongStateOf(0)

    var retainWipedOutboundMessages = false
    var screenShotBlockedForEphemeral by mutableStateOf(false)

    var markAsReadOnPause by mutableStateOf(true)

    var scrollToMessageRequest by mutableStateOf(ScrollRequest.None)
    var scrollToFirstUnread by mutableStateOf(true)


    var locked by mutableStateOf<Boolean?>(null)
    var canEdit by mutableStateOf<Boolean?>(null)
    var fullScreenPhotoUrl by mutableStateOf<String?>(null)

    var showCalendarView by mutableStateOf(false)
    var calendarInitialDateMillis by mutableStateOf<Long?>(null)

    @JvmField
    var messageIdsToForward: List<Long>? = null
    val discussionIdLiveData = MutableLiveData<Long?>()
    var selectedMessageIds by mutableStateOf(emptyList<Long>())
    var selectedMessageInfo by mutableStateOf<Message?>(null)
    private val nonForwardableSelectedMessageIds = HashSet<Long>()
    private val nonBookmarkableSelectedMessageIds = HashSet<Long>()
    private val nonBookmarkedSelectedMessageIds = HashSet<Long>()
    private val forwardMessageBytesOwnedIdentityLiveData = MutableLiveData<ByteArray>()
    val messageLinkPreviewUrlCache = mutableStateMapOf<Long, String>()
    val remoteDeletedMessageDeleter: MutableMap<Long, ByteArray?> = ConcurrentHashMap()

    val useAnimatedEmojis by lazy { SettingsActivity.useAnimatedEmojis() }
    val loopAnimatedEmojis by lazy { SettingsActivity.loopAnimatedEmojis() }

    val discussion: LiveData<Discussion> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.discussionDao().getByIdAsync(discussionId)
        }
    val discussionGroupMemberCountLiveData: LiveData<DiscussionAndGroupMembersCount> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.discussionDao().getWithGroupMembersCount(discussionId)
        }

    @OptIn(DelicateCoroutinesApi::class)
    val pagedMessages: Flow<PagingData<Message>> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            if (SettingsActivity.hideGroupMemberChanges) {
                return@switchMap getPagedMessagesWithoutGroupMemberChanges(discussionId)
            } else {
                return@switchMap getPagedMessages(discussionId)
            }
        }.asFlow().cachedIn(GlobalScope)

    private fun getPagedMessagesWithoutGroupMemberChanges(discussionId: Long) =
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 300)) {
            db.messageDao().getDiscussionMessagesWithoutGroupMemberChangesPaged(discussionId)
        }.liveData

    private fun getPagedMessages(discussionId: Long) =
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 300)) {
            db.messageDao().getDiscussionMessagesPaged(discussionId)
        }.liveData

    // endregion
    val invitations: LiveData<List<Invitation>> =
        discussion.switchMap { discussion: Discussion? ->
            if (discussion == null) {
                return@switchMap null
            }
            db.invitationDao().getByDiscussionId(discussion.id)
        }
    val discussionContacts: LiveData<List<Contact>?> =
        discussion.switchMap { discussion: Discussion? ->
            if (discussion == null || discussion.isLocked) {
                return@switchMap MutableLiveData<List<Contact>?>(null)
            }
            if (discussion.isNormalOrReadOnly) {
                when (discussion.discussionType) {
                    Discussion.TYPE_CONTACT -> return@switchMap db.contactDao().getAsList(
                        discussion.bytesOwnedIdentity,
                        discussion.bytesDiscussionIdentifier
                    )

                    Discussion.TYPE_GROUP -> return@switchMap db.contactGroupJoinDao()
                        .getGroupContacts(
                            discussion.bytesOwnedIdentity,
                            discussion.bytesDiscussionIdentifier
                        )

                    Discussion.TYPE_GROUP_V2 -> return@switchMap db.group2MemberDao()
                        .getGroupMemberContacts(
                            discussion.bytesOwnedIdentity,
                            discussion.bytesDiscussionIdentifier
                        )
                }
            }
            null
        }
    val mentionCandidatesLiveData: LiveData<List<Contact>> =
        MentionCandidatesLiveData(discussion, AppSingleton.getCurrentIdentityLiveData())

    @JvmField
    val unreadCountAndFirstMessage: LiveData<UnreadCountAndFirstMessage> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.messageDao().getUnreadCountAndFirstMessage(discussionId)
        }
    val discussionCustomization: LiveData<DiscussionCustomization> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.discussionCustomizationDao().getLiveData(discussionId)
        }
    val newDetailsUpdate: LiveData<Int?> = discussion.switchMap { discussion: Discussion? ->
        if (discussion != null && discussion.isNormalOrReadOnly) {
            when (discussion.discussionType) {
                Discussion.TYPE_CONTACT -> return@switchMap AppDatabase.getInstance()
                    .contactDao().getAsync(
                        discussion.bytesOwnedIdentity,
                        discussion.bytesDiscussionIdentifier
                    ).map<Contact, Int?> { contact: Contact? ->
                        if (contact != null) {
                            return@map contact.newPublishedDetails
                        }
                        null
                    }

                Discussion.TYPE_GROUP -> return@switchMap AppDatabase.getInstance().groupDao()
                    .getLiveData(
                        discussion.bytesOwnedIdentity,
                        discussion.bytesDiscussionIdentifier
                    ).map<Group, Int?> { group: Group? ->
                        if (group != null) {
                            return@map group.newPublishedDetails
                        }
                        null
                    }

                Discussion.TYPE_GROUP_V2 -> return@switchMap AppDatabase.getInstance()
                    .group2Dao().getLiveData(
                        discussion.bytesOwnedIdentity,
                        discussion.bytesDiscussionIdentifier
                    ).map<Group2, Int?> { group2: Group2? ->
                        if (group2 != null) {
                            return@map group2.newPublishedDetails
                        }
                        null
                    }
            }
        }
        MutableLiveData(Contact.PUBLISHED_DETAILS_NOTHING_NEW)
    }

    @JvmField
    val forwardMessageOwnedIdentityLiveData: LiveData<OwnedIdentity> =
        forwardMessageBytesOwnedIdentityLiveData.switchMap { bytesOwnedIdentity: ByteArray? ->
            if (bytesOwnedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().ownedIdentityDao().getLiveData(bytesOwnedIdentity)
        }
    val currentlySharingLocationMessagesLiveData: LiveData<List<Message>> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.messageDao()
                .getCurrentlySharingLocationMessagesInDiscussionLiveData(discussionId)
        }

    val galleryMediasGroupedByDate: LiveData<Map<Long, FyleAndStatus>> = discussionIdLiveData.switchMap { discussionId: Long? ->
        if (discussionId == null) {
            return@switchMap null
        }
        db.fyleMessageJoinWithStatusDao().getGalleryMediasForCalendar(discussionId).map { fyles ->
            HashMap<Long, FyleAndStatus>().also {
                fyles.forEach { fyleAndStatusTimestamped ->
                    val localDate = java.time.Instant.ofEpochMilli(fyleAndStatusTimestamped.timestamp).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                    val startOfDayUtc = localDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                    it[startOfDayUtc] = fyleAndStatusTimestamped.fyleAndStatus
                }
            }
        }
    }

    fun scrollToDate(dateMillis: Long) {
        val discussionId = this.discussionId ?: return
        App.runThread {
            val messageId = AppDatabase.getInstance().messageDao()
                .getFirstMessageIdOnOrAfterTimestamp(discussionId, dateMillis)
                ?: AppDatabase.getInstance().messageDao()
                    .getLastMessageIdOnOrBeforeTimestamp(discussionId, dateMillis)
            if (messageId != null) {
                Handler(Looper.getMainLooper()).post {
                    scrollToMessageRequest = ScrollRequest(messageId = messageId, highlight = true, triggeredBySearch = true)
                }
            }
        }
    }

    var discussionId: Long?
        get() = discussionIdLiveData.value
        set(discussionId) {
            if (discussionId != discussionIdLiveData.value) {
                remoteDeletedMessageDeleter.clear()
            }
            discussionIdLiveData.postValue(discussionId)
        }


    class MentionCandidatesLiveData(
        discussionLiveData: LiveData<Discussion>,
        ownedIdentityLiveData: LiveData<OwnedIdentity>
    ) : MediatorLiveData<List<Contact>>() {
        private var contactList: List<Contact>? = null
        private var ownedIdentityContact: Contact? = null

        init {
            val db = AppDatabase.getInstance()
            val discussionContactsAndPending =
                discussionLiveData.switchMap<Discussion, List<Contact>> { discussion: Discussion? ->
                    if (discussion == null || discussion.isLocked) {
                        return@switchMap MutableLiveData(ArrayList())
                    }
                    if (discussion.isNormal) {
                        when (discussion.discussionType) {
                            Discussion.TYPE_CONTACT -> return@switchMap db.contactDao().getAsList(
                                discussion.bytesOwnedIdentity,
                                discussion.bytesDiscussionIdentifier
                            )

                            Discussion.TYPE_GROUP -> return@switchMap db.contactGroupJoinDao()
                                .getGroupContacts(
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )

                            Discussion.TYPE_GROUP_V2 -> return@switchMap db.group2MemberDao()
                                .getGroupMembersAndPendingForMention(
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                                .map<List<Group2MemberOrPendingForMention>, List<Contact>> { group2MemberOrPendingsForMention: List<Group2MemberOrPendingForMention>? ->
                                    val contacts: MutableList<Contact> = ArrayList()
                                    if (group2MemberOrPendingsForMention == null) {
                                        return@map contacts
                                    }
                                    for (member in group2MemberOrPendingsForMention) {
                                        if (member.contact != null) {
                                            contacts.add(member.contact)
                                        } else {
                                            val contact = Contact.createFake(
                                                member.bytesContactIdentity,
                                                discussion.bytesOwnedIdentity,
                                                member.sortDisplayName,
                                                member.fullSearchDisplayName,
                                                member.identityDetails
                                            )
                                            if (contact != null) {
                                                contacts.add(contact)
                                            }
                                        }
                                    }
                                    contacts
                                }
                        }
                    }
                    MutableLiveData(ArrayList())
                }
            addSource(discussionContactsAndPending) { contactList: List<Contact> ->
                this.onContactListChanged(
                    contactList
                )
            }
            addSource(
                ownedIdentityLiveData
            ) { ownedIdentity: OwnedIdentity? ->
                this.onOwnedIdentityChanged(ownedIdentity)
            }
        }

        private fun onContactListChanged(contactList: List<Contact>) {
            this.contactList = contactList
            merge()
        }

        private fun onOwnedIdentityChanged(ownedIdentity: OwnedIdentity?) {
            if (ownedIdentity == null) {
                this.ownedIdentityContact = null
            } else {
                this.ownedIdentityContact = Contact.createFakeFromOwnedIdentity(ownedIdentity)
            }
            merge()
        }


        private fun merge() {
            if (ownedIdentityContact == null && contactList != null) {
                value = contactList!!
            } else if (contactList == null) {
                value = listOf(
                    ownedIdentityContact!!
                )
            } else {
                val mergedList: MutableList<Contact> = ArrayList()
                for (i in contactList!!.indices) {
                    val contact = contactList!![i]
                    if (firstIsLarger(
                            contact.sortDisplayName,
                            ownedIdentityContact!!.sortDisplayName
                        )
                    ) {
                        // we have reached the spot where ownedIdentity should be added
                        mergedList.add(ownedIdentityContact!!)
                        mergedList.addAll(contactList!!.subList(i, contactList!!.size))
                        value = mergedList
                        return
                    }
                    mergedList.add(contact)
                }
                // if we reach this point, it means we have not yet added our ownedIdentity --> add it now
                mergedList.add(ownedIdentityContact!!)
                value = mergedList
            }
        }

        /**
         * method used to compare two sortDisplayNames (see [java.text.CollationKey.toByteArray])
         */
        private fun firstIsLarger(
            sortDisplayName1: ByteArray,
            sortDisplayName2: ByteArray
        ): Boolean {
            val len = min(sortDisplayName1.size.toDouble(), sortDisplayName2.size.toDouble())
                .toInt()
            for (i in 0 until len) {
                if (sortDisplayName1[i] > sortDisplayName2[i]) {
                    return true
                } else if (sortDisplayName1[i] < sortDisplayName2[i]) {
                    return false
                }
            }
            return sortDisplayName1.size > sortDisplayName2.size
        }
    }

    /////
    // bookmarked == null means the message is not bookmarkable
    fun selectMessageId(messageId: Long, forwardable: Boolean, bookmarked: Boolean?) {
        val ids = selectedMessageIds.toMutableList()

        if (ids.remove(messageId)) {
            nonForwardableSelectedMessageIds.remove(messageId)
            nonBookmarkedSelectedMessageIds.remove(messageId)
            nonBookmarkableSelectedMessageIds.remove(messageId)
            if (ids.isEmpty()) {
                isSelectingForDeletion = false
            }
        } else {
            ids.add(messageId)
            if (!forwardable) {
                nonForwardableSelectedMessageIds.add(messageId)
            }
            if (bookmarked == null) {
                nonBookmarkableSelectedMessageIds.add(messageId)
            } else if (!bookmarked) {
                nonBookmarkedSelectedMessageIds.add(messageId)
            }
            isSelectingForDeletion = true
        }
        selectedMessageIds = ids
    }

    fun unselectMessageId(messageId: Long) {
        selectedMessageIds.toMutableList().apply {
            remove(messageId)
            nonForwardableSelectedMessageIds.remove(messageId)
            nonBookmarkableSelectedMessageIds.remove(messageId)
            nonBookmarkedSelectedMessageIds.remove(messageId)
            selectedMessageIds = this
        }
    }

    fun areAllSelectedMessagesForwardable(): Boolean {
        return nonForwardableSelectedMessageIds.isEmpty()
    }

    fun areAllSelectedMessagesBookmarked(): Boolean {
        return nonBookmarkedSelectedMessageIds.isEmpty()
    }

    fun areAllSelectedMessagesBookmarkable(): Boolean {
        return nonBookmarkableSelectedMessageIds.isEmpty()
    }

    fun deselectAll() {
        isSelectingForDeletion = false
        nonForwardableSelectedMessageIds.clear()
        nonBookmarkedSelectedMessageIds.clear()
        nonBookmarkableSelectedMessageIds.clear()
        selectedMessageIds = emptyList()
    }

    fun setForwardMessageBytesOwnedIdentity(bytesOwnedIdentity: ByteArray) {
        forwardMessageBytesOwnedIdentityLiveData.postValue(bytesOwnedIdentity)
    }

    fun handleBookmarkMessages(bookmark: Boolean) {
        App.runThread {
            for (messageId in selectedMessageIds) {
                AppDatabase.getInstance().messageDao()
                    .updateBookmarked(bookmark, messageId)
                val message = AppDatabase.getInstance().messageDao()[messageId]
                val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity()
                if (message != null && bytesOwnedIdentity != null) {
                    PropagateBookmarkedMessageChangeTask(
                        bytesOwnedIdentity,
                        message,
                        bookmark
                    ).run()
                }
            }
            deselectAll()
        }
    }

    fun handleCopyMessages(context: Context) {
        if (selectedMessageIds.isEmpty()) return

        val ids = ArrayList(selectedMessageIds)
        deselectAll()

        App.runThread {
            val messages = ids.mapNotNull { db.messageDao().get(it) }.sortedBy { it.sortIndex }
            val sb = StringBuilder()
            for (message in messages) {
                val text = message.getStringContent(context, true)
                val dateStr = DateFormat.format(
                    DateFormat.getBestDateTimePattern(context.resources.configuration.locale, "yyyyMMdd jmm"),
                    message.timestamp
                ).toString()
                val senderName = ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    ?: context.getString(R.string.text_deleted_contact)
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(context.getString(R.string.template_copy_multiple_messages, dateStr, senderName, text))
            }
            if (sb.isEmpty()) return@runThread
            val clipData = ClipData.newPlainText(
                context.getString(R.string.label_text_copied_from_olvid),
                sb.toString()
            )
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(clipData)
            App.toast(context.resources.getQuantityString(R.plurals.toast_message_messages_copied_to_clipboard, messages.size, messages.size), Toast.LENGTH_SHORT, Gravity.BOTTOM)
        }
    }

    fun handleForwardMessages(context: Context) {
        if (selectedMessageIds.isNotEmpty()) {
            messageIdsToForward = ArrayList(selectedMessageIds)
            (context as? FragmentActivity)?.let {
                Utils.openForwardMessageDialog(
                    it,
                    selectedMessageIds
                ) { deselectAll() }
            }
        }
    }

    fun handleDeleteMessages(context: Context) {
        val discussion = discussion.value
        if (discussion != null && selectedMessageIds.isNotEmpty()) {
            App.runThread {
                var allMessagesAreOutbound = true
                var remoteDeletingMakesSense = true
                for (messageId in selectedMessageIds) {
                    val message =
                        AppDatabase.getInstance().messageDao()[messageId]
                            ?: continue
                    if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                        remoteDeletingMakesSense = false
                        break
                    }
                    if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE) {
                        allMessagesAreOutbound = false
                    }
                    if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                        remoteDeletingMakesSense = false
                        break
                    }
                }
                val offerToRemoteDeleteEverywhere: Boolean
                if (remoteDeletingMakesSense) {
                    when (discussion.discussionType) {
                        Discussion.TYPE_GROUP_V2 -> {
                            val group2 = AppDatabase.getInstance()
                                .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                            offerToRemoteDeleteEverywhere = if (group2 != null) {
                                (AppDatabase.getInstance().group2MemberDao()
                                    .groupHasMembers(
                                        discussion.bytesOwnedIdentity,
                                        discussion.bytesDiscussionIdentifier
                                    )
                                        && ((allMessagesAreOutbound && group2.ownPermissionEditOrRemoteDeleteOwnMessages)
                                        || group2.ownPermissionRemoteDeleteAnything))
                            } else {
                                false
                            }
                        }

                        Discussion.TYPE_GROUP -> {
                            offerToRemoteDeleteEverywhere =
                                AppDatabase.getInstance().contactGroupJoinDao()
                                    .groupHasMembers(
                                        discussion.bytesOwnedIdentity,
                                        discussion.bytesDiscussionIdentifier
                                    ) && (allMessagesAreOutbound && discussion.isNormal)
                        }

                        else -> {
                            offerToRemoteDeleteEverywhere =
                                allMessagesAreOutbound && discussion.isNormal
                        }
                    }
                } else {
                    offerToRemoteDeleteEverywhere = false
                }

                val builder: AlertDialog.Builder = SecureDeleteEverywhereDialogBuilder(
                    context,
                    SecureDeleteEverywhereDialogBuilder.Type.MESSAGE,
                    selectedMessageIds.size,
                    offerToRemoteDeleteEverywhere,
                    remoteDeletingMakesSense
                )
                    .setDeleteCallback { deletionChoice: SecureDeleteEverywhereDialogBuilder.DeletionChoice? ->
                        App.runThread(
                            DeleteMessagesTask(
                                selectedMessageIds,
                                deletionChoice
                            )
                        )

                        deselectAll()
                    }
                Handler(Looper.getMainLooper()).post { builder.create().show() }
            }
        }
    }


    fun markMessagesRead(wipeReadOnceMessages: Boolean) {
        val messageIds = messageIdsToMarkAsRead.toTypedArray<Long>()
        val editedMessageIds = editedMessageIdsToMarkAsSeen.toTypedArray<Long>()

        if (messageIds.isNotEmpty() || editedMessageIds.isNotEmpty()) {
            val latestTimestamp = latestServerTimestampOfMessageToMarkAsRead
            val discussion = discussion.value
            val discussionId = discussionId

            editedMessageIdsToMarkAsSeen.clear()
            if (wipeReadOnceMessages) {
                // we keep the list if messages are not wiped yet
                messageIdsToMarkAsRead.clear()
                latestServerTimestampOfMessageToMarkAsRead = 0
            }


            App.runThread {
                val db = AppDatabase.getInstance()
                if (discussion != null && AppDatabase.getInstance().ownedDeviceDao()
                        .doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)
                ) {
                    Message.postDiscussionReadMessage(discussion, latestTimestamp)
                }
                db.messageDao().markMessagesRead(messageIds)
                db.messageDao().markEditedMessagesSeen(editedMessageIds)
                UnreadCountsSingleton.markMessagesRead(discussionId, messageIds)

                if (wipeReadOnceMessages) {
                    for (message in db.messageDao().getWipeOnReadSubset(messageIds)) {
                        db.runInTransaction {
                            if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && retainWipedOutboundMessages) {
                                message.wipe(db)
                                message.deleteAttachments(db)
                            } else {
                                message.delete(db)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun markMessagesRead() {
        markMessagesRead(false)
    }

    override fun doNotMarkAsReadOnPause() {
        markAsReadOnPause = false
    }

    override fun scrollToMessage(messageId: Long) {
        scrollToMessageRequest = ScrollRequest(messageId)
    }

    override fun replyToMessage(messageId: Long, currentComposeMessageTextToSaveToDraft: String) {
        App.runThread(
            SetDraftReplyTask(
                discussionId,
                messageId,
                currentComposeMessageTextToSaveToDraft
            )
        )
    }

    override fun initiateMessageForward(
        activity: FragmentActivity,
        messageId: Long,
        openDialogCallback: Runnable?
    ) {
        messageIdsToForward = listOf(messageId)
        Utils.openForwardMessageDialog(
            activity,
            listOf(messageId),
            openDialogCallback
        )
    }

    override fun selectMessage(messageId: Long, forwardable: Boolean, bookmarked: Boolean?) {
        selectMessageId(messageId, forwardable, bookmarked)
    }

    override fun messageWasSent() {
        scrollToMessageRequest = ScrollRequest.ToBottom
    }
}
