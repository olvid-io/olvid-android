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
package io.olvid.messenger.discussion

import androidx.compose.runtime.mutableStateMapOf
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
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
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
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class DiscussionViewModel : ViewModel() {
    private val db: AppDatabase = AppDatabase.getInstance()

    // region select for deletion
    var isSelectingForDeletion: Boolean = false
        private set

    var longClickedFyleAndStatus: FyleAndStatus? = null

    @JvmField
    var messageIdsToForward: List<Long>? = null
    val discussionIdLiveData = MutableLiveData<Long?>()
    val selectedMessageIds = MutableLiveData<MutableList<Long>>()
    private val nonForwardableSelectedMessageIds = HashSet<Long>()
    private val nonBookmarkableSelectedMessageIds = HashSet<Long>()
    private val nonBookmarkedSelectedMessageIds = HashSet<Long>()
    private val forwardMessageBytesOwnedIdentityLiveData = MutableLiveData<ByteArray>()
    val messageLinkPreviewUrlCache = mutableStateMapOf<Long, String>()
    val remoteDeletedMessageDeleter: MutableMap<Long, ByteArray?> = ConcurrentHashMap()

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
                        return@switchMap MutableLiveData<List<Contact>>(ArrayList<Contact>())
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
        val ids: MutableList<Long>
        if (selectedMessageIds.value == null) {
            ids = ArrayList()
        } else {
            ids = ArrayList(selectedMessageIds.value!!.size)
            ids.addAll(selectedMessageIds.value!!)
        }
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
        selectedMessageIds.postValue(ids)
    }

    fun unselectMessageId(messageId: Long) {
        selectedMessageIds.value?.let {
            it.remove(messageId)
            nonForwardableSelectedMessageIds.remove(messageId)
            nonBookmarkableSelectedMessageIds.remove(messageId)
            nonBookmarkedSelectedMessageIds.remove(messageId)
            selectedMessageIds.postValue(it)
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
        selectedMessageIds.postValue(ArrayList())
    }

    fun setForwardMessageBytesOwnedIdentity(bytesOwnedIdentity: ByteArray) {
        forwardMessageBytesOwnedIdentityLiveData.postValue(bytesOwnedIdentity)
    }
}
