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

package io.olvid.messenger

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.OwnedIdentityDao
import io.olvid.messenger.databases.entity.Message
import java.util.UUID


data class OwnedIdentityUnreadCounts(
    // Unread messages excluding archived discussions — drives the discussions tab dot.
    val unreadMessageCountNonArchived: Int = 0,
    // Unread messages with mentions — drives the MainIdentity profile picture dot for muted profiles.
    val mentionMessageCountNonArchived: Int = 0,
    val unreadDiscussionCount: Int = 0,
    val invitationCount: Int = 0,
) {
    fun hasNotificationDot(): Boolean =
        unreadMessageCountNonArchived > 0 || unreadDiscussionCount > 0 || invitationCount > 0
}

internal data class DiscussionOwnerMetadata(val bytesOwnedIdentity: BytesKey, val archived: Boolean)

internal fun isMuted(
    prefMuteNotifications: Boolean,
    prefMuteNotificationsTimestamp: Long?,
    now: Long,
): Boolean =
    prefMuteNotifications && (prefMuteNotificationsTimestamp == null || prefMuteNotificationsTimestamp > now)

internal fun computeOwnedIdentityCounts(
    unreadMessageCountsByDiscussion: Map<Long, Int>,
    mentionMessageCountsByDiscussion: Map<Long, Int>,
    discussionMetadata: Map<Long, DiscussionOwnerMetadata>,
    unreadDiscussionsByOwnedIdentity: Map<BytesKey, Set<Long>>,
    invitationsByOwnedIdentity: Map<BytesKey, Set<UUID>>,
): Map<BytesKey, OwnedIdentityUnreadCounts> {
    val nonArchived = HashMap<BytesKey, Int>()
    val mention = HashMap<BytesKey, Int>()
    unreadMessageCountsByDiscussion.forEach { (discussionId, count) ->
        val meta = discussionMetadata[discussionId] ?: return@forEach
        if (!meta.archived) nonArchived.merge(meta.bytesOwnedIdentity, count, Int::plus)
    }
    mentionMessageCountsByDiscussion.forEach { (discussionId, count) ->
        val meta = discussionMetadata[discussionId] ?: return@forEach
        if (!meta.archived) mention.merge(meta.bytesOwnedIdentity, count, Int::plus)
    }
    val owners = nonArchived.keys + unreadDiscussionsByOwnedIdentity.keys + invitationsByOwnedIdentity.keys
    if (owners.isEmpty()) return emptyMap()
    return owners.associateWith { owner ->
        OwnedIdentityUnreadCounts(
            unreadMessageCountNonArchived = nonArchived[owner] ?: 0,
            mentionMessageCountNonArchived = mention[owner] ?: 0,
            unreadDiscussionCount = unreadDiscussionsByOwnedIdentity[owner]?.size ?: 0,
            invitationCount = invitationsByOwnedIdentity[owner]?.size ?: 0,
        )
    }
}


object UnreadCountsSingleton {
    // Categories of invitations that should drive a notification dot/counter.
    // Keep aligned with Invitation.requiresAction() — anything that warrants a dot is in here.
    private val INVITATION_NOTIFICATION_CATEGORIES = setOf(
        ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY,
        ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY,
        ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY,
        ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY,
        ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY,
        ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY,
        ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY,
    )

    private val unreadMessageIdsByDiscussionId: MutableMap<Long, MutableMap<Long, Long>> = mutableMapOf()
    private val mentionMessageIdsByDiscussionId: MutableMap<Long, MutableSet<Long>> = mutableMapOf()
    private val locationsSharedMessageIdsByDiscussionId: MutableMap<Long, MutableSet<Long>> = mutableMapOf()

    private val unreadMessageCountsByDiscussionLiveData: MutableLiveData<Map<Long, Int>> = MutableLiveData(emptyMap())
    private var lastEmittedUnreadCountsByDiscussion: Map<Long, Int> = emptyMap()
    private val mentionsDiscussionsLiveData: MutableLiveData<Set<Long>> = MutableLiveData(emptySet())
    private val locationsSharedDiscussionsLiveData: MutableLiveData<Set<Long>> = MutableLiveData(emptySet())

    private val discussionMetadata: MutableMap<Long, DiscussionOwnerMetadata> = mutableMapOf()
    // discussions explicitly marked as unread (Discussion.UNREAD = 1)
    private val unreadDiscussionsByOwnedIdentity: MutableMap<BytesKey, MutableSet<Long>> = mutableMapOf()

    private val invitationsByOwnedIdentity: MutableMap<BytesKey, MutableSet<UUID>> = mutableMapOf()
    // dialogUuid -> bytesOwnedIdentity, so deletes can find the right bucket without the category info
    private val invitationOwners: MutableMap<UUID, BytesKey> = mutableMapOf()

    private val unreadCountsByOwnedIdentityLiveData: MutableLiveData<Map<BytesKey, OwnedIdentityUnreadCounts>> = MutableLiveData(emptyMap())
    private var lastEmittedOwnedIdentityCounts: Map<BytesKey, OwnedIdentityUnreadCounts> = emptyMap()

    // bytesOwnedIdentity -> pair of "do not mute mentions" and "mute expiration timestamp" (null means "muted indefinitely")
    private val mutedOwnedIdentities: MutableMap<BytesKey, Pair<Boolean, Long?>> = mutableMapOf()
    private val mutedOwnedIdentitiesLiveData: MutableLiveData<Set<BytesKey>> = MutableLiveData(emptySet())
    private val mutedExceptMentionOwnedIdentitiesLiveData: MutableLiveData<Set<BytesKey>> = MutableLiveData(emptySet())
    private var lastEmittedMutedSet: Pair<Set<BytesKey>, Set<BytesKey>> = Pair(emptySet(), emptySet())
    private val muteHandler = Handler(Looper.getMainLooper())
    private val muteExpiryRunnable = Runnable { recomputeMutedSet() }

    fun getUnreadMessageCountsByDiscussionLiveData(): LiveData<Map<Long, Int>> =
        unreadMessageCountsByDiscussionLiveData

    fun getMentionsDiscussionsLiveData(): LiveData<Set<Long>> = mentionsDiscussionsLiveData

    fun getLocationsSharedDiscussionsLiveData(): LiveData<Set<Long>> = locationsSharedDiscussionsLiveData

    fun getUnreadCountsByOwnedIdentityLiveData(): LiveData<Map<BytesKey, OwnedIdentityUnreadCounts>> =
        unreadCountsByOwnedIdentityLiveData

    fun getMutedOwnedIdentitiesLiveData(): LiveData<Set<BytesKey>> = mutedOwnedIdentitiesLiveData
    fun getMutedExceptMentionOwnedIdentitiesLiveData(): LiveData<Set<BytesKey>> = mutedExceptMentionOwnedIdentitiesLiveData


    private var initialized = false

    fun initialize() {
        synchronized(UnreadCountsSingleton) {
            if (initialized) return
            initialized = true
            val db = AppDatabase.getInstance()

            val discussionStubs = db.discussionDao().getAllDiscussionStubs()
            discussionStubs.forEach { stub ->
                val owner = BytesKey(stub.bytesOwnedIdentity)
                discussionMetadata[stub.id] = DiscussionOwnerMetadata(owner, stub.archived)
                if (stub.unread) {
                    unreadDiscussionsByOwnedIdentity.getOrPut(owner) { mutableSetOf() }.add(stub.id)
                }
            }

            val unreadMessages = db.messageDao().getAllUnreadMessageStubs()
            unreadMessages.forEach {
                unreadMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableMapOf() }[it.messageId] = it.timestamp
                if (it.mentioned) {
                    mentionMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableSetOf() }
                        .add(it.messageId)
                }
            }

            val locationMessages = db.messageDao().getAllLocationMessageStubs()
            locationMessages.forEach {
                locationsSharedMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableSetOf() }
                    .add(it.messageId)
            }

            // Pending invitations in the "right" categories (see INVITATION_NOTIFICATION_CATEGORIES).
            val invitationStubs = db.invitationDao().getAllStubs()
            invitationStubs.forEach { stub ->
                if (stub.categoryId in INVITATION_NOTIFICATION_CATEGORIES) {
                    val owner = BytesKey(stub.bytesOwnedIdentity)
                    invitationsByOwnedIdentity.getOrPut(owner) { mutableSetOf() }.add(stub.dialogUuid)
                    invitationOwners[stub.dialogUuid] = owner
                }
            }

            // Mute timestamps in the past mean "no longer muted".
            db.ownedIdentityDao().muteStateStubs.forEach { refreshMuteStateLocked(it) }
            scheduleNextMuteExpiryLocked()

            postUnreadMessageCountsByDiscussionLocked()
            mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
            postOwnedIdentityCountsLocked()
            postMutedSetLocked()
        }

        // observeForever() requires the main thread. Posted after initial values so the singleton
        // is fully initialized before downstream observers can subscribe.
        muteHandler.post {
            AppDatabase.getInstance().ownedIdentityDao().muteStateStubsLiveData.observeForever(muteStateObserver)
        }
    }

    private val muteStateObserver = Observer<List<OwnedIdentityDao.MuteStateStub>?> { stubs ->
        stubs ?: return@Observer
        synchronized(UnreadCountsSingleton) {
            val seenKeys = HashSet<BytesKey>(stubs.size)
            stubs.forEach {
                seenKeys.add(BytesKey(it.bytesOwnedIdentity))
                refreshMuteStateLocked(it)
            }
            // An owned identity may be referenced by any of our per-owner maps without ever
            // being muted — when it's deleted (cascade-deletes wipe its invitations and
            // discussions in DB without firing our hooks), we need to garbage-collect all of
            // them here.
            val knownKeys = mutedOwnedIdentities.keys +
                    invitationsByOwnedIdentity.keys +
                    unreadDiscussionsByOwnedIdentity.keys
            val staleKeys = knownKeys.filter { it !in seenKeys }
            if (staleKeys.isNotEmpty()) {
                staleKeys.forEach { key ->
                    mutedOwnedIdentities.remove(key)
                    invitationsByOwnedIdentity.remove(key)?.forEach { invitationOwners.remove(it) }
                    unreadDiscussionsByOwnedIdentity.remove(key)
                }
                // Also drop any discussion metadata pointing at a deleted owner; otherwise its
                // unread messages would still be aggregated against it.
                val staleSet = staleKeys.toHashSet()
                discussionMetadata.entries.removeAll { it.value.bytesOwnedIdentity in staleSet }
                postOwnedIdentityCountsLocked()
            }
            scheduleNextMuteExpiryLocked()
            postMutedSetLocked()
        }
    }

    // Discussions are auto-registered via newUnreadMessage and the self-registering hooks below;
    // there is no `discussionCreated` hook — startup loads the full picture, and we only need to
    // track changes after that.
    fun discussionDeleted(discussionId: Long) {
        synchronized(UnreadCountsSingleton) {
            val meta = discussionMetadata.remove(discussionId)
            var ownedIdentityCountsChanged = false

            unreadMessageIdsByDiscussionId.remove(discussionId)?.let {
                postUnreadMessageCountsByDiscussionLocked()
                ownedIdentityCountsChanged = true
            }
            mentionMessageIdsByDiscussionId.remove(discussionId)?.let {
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
            locationsSharedMessageIdsByDiscussionId.remove(discussionId)?.let {
                locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
            }
            meta?.let {
                unreadDiscussionsByOwnedIdentity[it.bytesOwnedIdentity]?.let { set ->
                    if (set.remove(discussionId)) {
                        if (set.isEmpty()) unreadDiscussionsByOwnedIdentity.remove(it.bytesOwnedIdentity)
                        ownedIdentityCountsChanged = true
                    }
                }
            }
            if (ownedIdentityCountsChanged) postOwnedIdentityCountsLocked()
        }
    }

    fun discussionArchivedChanged(discussionId: Long, archived: Boolean) {
        synchronized(UnreadCountsSingleton) {
            val meta = discussionMetadata[discussionId] ?: return
            if (meta.archived == archived) return
            discussionMetadata[discussionId] = meta.copy(archived = archived)
            // Only the "non-archived" half of the per-owner counts depends on archived state.
            if (unreadMessageIdsByDiscussionId[discussionId]?.isNotEmpty() == true) {
                postOwnedIdentityCountsLocked()
            }
        }
    }

    fun discussionUnreadFlagChanged(discussionId: Long, unread: Boolean) {
        synchronized(UnreadCountsSingleton) {
            val owner = discussionMetadata[discussionId]?.bytesOwnedIdentity ?: return
            val set = unreadDiscussionsByOwnedIdentity.getOrPut(owner) { mutableSetOf() }
            val changed = if (unread) set.add(discussionId) else set.remove(discussionId)
            if (set.isEmpty()) unreadDiscussionsByOwnedIdentity.remove(owner)
            if (changed) postOwnedIdentityCountsLocked()
        }
    }

    fun newUnreadMessage(
        bytesOwnedIdentity: ByteArray,
        discussionId: Long,
        messageId: Long,
        mentioned: Boolean,
        timestamp: Long,
    ) {
        synchronized(UnreadCountsSingleton) {
            // Make sure we have an owner mapping. Callers may legitimately fire this before the
            // discussion's own creation hook (e.g. when batching new messages), so register on
            // demand using the bytesOwnedIdentity carried by the call.
            if (!discussionMetadata.containsKey(discussionId)) {
                discussionMetadata[discussionId] = DiscussionOwnerMetadata(BytesKey(bytesOwnedIdentity), archived = false)
            }
            unreadMessageIdsByDiscussionId.getOrPut(discussionId) { mutableMapOf() }[messageId] = timestamp
            postUnreadMessageCountsByDiscussionLocked()

            if (mentioned) {
                mentionMessageIdsByDiscussionId.getOrPut(discussionId) { mutableSetOf() }
                    .add(messageId)
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }

            postOwnedIdentityCountsLocked()
        }
    }

    fun newLocationSharingMessage(discussionId: Long, messageId: Long) {
        synchronized(UnreadCountsSingleton) {
            locationsSharedMessageIdsByDiscussionId.getOrPut(discussionId) { mutableSetOf() }
                .add(messageId)
            locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
        }
    }

    fun removeLocationSharingMessage(discussionId: Long, messageId: Long) {
        synchronized(UnreadCountsSingleton) {
            locationsSharedMessageIdsByDiscussionId[discussionId]?.let { messageIds ->
                if (messageIds.remove(messageId)) {
                    if (messageIds.isEmpty()) {
                        locationsSharedMessageIdsByDiscussionId.remove(discussionId)
                        locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
                    }
                }
            }
        }
    }

    fun markMessagesRead(discussionId: Long?, messageIdArray: Array<Long>) {
        synchronized(UnreadCountsSingleton) {
            var mentionRemoved = false
            var unreadRemoved = false

            if (discussionId != null) {
                messageIdArray.forEach { messageId ->
                    unreadMessageIdsByDiscussionId[discussionId]?.let { messageIds ->
                        if (messageIds.remove(messageId) != null) {
                            if (messageIds.isEmpty()) {
                                unreadMessageIdsByDiscussionId.remove(discussionId)
                            }
                            unreadRemoved = true

                            mentionMessageIdsByDiscussionId[discussionId]?.let { mentionMessageIds ->
                                if (mentionMessageIds.remove(messageId)
                                    && mentionMessageIds.isEmpty()
                                ) {
                                    mentionMessageIdsByDiscussionId.remove(discussionId)
                                    mentionRemoved = true
                                }
                            }
                        }
                    }
                }
            } else {
                // if no discussionId was given (this should never be the case), search all discussion sets and remove if needed
                messageIdArray.forEach { messageId ->
                    var emptyUnreadDiscussion : Long? = null
                    for (entry in unreadMessageIdsByDiscussionId) {
                        if (entry.value.remove(messageId) != null) {
                            if (entry.value.isEmpty()) {
                                emptyUnreadDiscussion = entry.key
                            }
                            unreadRemoved = true

                            mentionMessageIdsByDiscussionId[entry.key]?.let { mentionMessageIds ->
                                if (mentionMessageIds.remove(messageId)
                                    && mentionMessageIds.isEmpty()
                                ) {
                                    mentionMessageIdsByDiscussionId.remove(entry.key)
                                    mentionRemoved = true
                                }
                            }
                            break
                        }
                    }
                    emptyUnreadDiscussion?.let {
                        unreadMessageIdsByDiscussionId.remove(emptyUnreadDiscussion)
                    }
                }
            }

            if (mentionRemoved) {
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
            if (unreadRemoved) {
                postUnreadMessageCountsByDiscussionLocked()
                postOwnedIdentityCountsLocked()
            }
        }
    }

    fun markDiscussionRead(discussionId: Long, upToTimestamp: Long?) {
        synchronized(UnreadCountsSingleton) {
            if (upToTimestamp == null) {
                if (unreadMessageIdsByDiscussionId.remove(discussionId) != null) {
                    postUnreadMessageCountsByDiscussionLocked()
                    if (mentionMessageIdsByDiscussionId.remove(discussionId) != null) {
                        mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
                    }

                    postOwnedIdentityCountsLocked()
                }
            } else {
                unreadMessageIdsByDiscussionId[discussionId]?.let { messageIds ->
                    val mentionMessageIds = mentionMessageIdsByDiscussionId[discussionId]
                    var unreadRemoved = false
                    val filtered = mutableMapOf<Long, Long>()
                    messageIds.forEach {
                        if (it.value <= upToTimestamp) {
                            unreadRemoved = true
                            mentionMessageIds?.remove(it.key)
                        } else {
                            filtered[it.key] = it.value
                        }
                    }

                    if (unreadRemoved) {
                        if (filtered.isEmpty()) {
                            unreadMessageIdsByDiscussionId.remove(discussionId)
                        } else {
                            unreadMessageIdsByDiscussionId[discussionId] = filtered
                        }
                        postUnreadMessageCountsByDiscussionLocked()
                        if (mentionMessageIds?.isEmpty() == true) {
                            mentionMessageIdsByDiscussionId.remove(discussionId)
                            mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
                        }

                        postOwnedIdentityCountsLocked()
                    }
                }
            }
        }
    }


    fun messageDeleted(message: Message) {
        messageBatchDeleted(listOf(message))
    }

    fun messageBatchDeleted(messages: List<Message>) {
        synchronized(UnreadCountsSingleton) {
            var unreadChanged = false
            var mentionsChanged = false
            var locationsSharedChanged = false
            messages.forEach { message ->
                unreadMessageIdsByDiscussionId[message.discussionId]?.let { messageIds ->
                    if (messageIds.remove(message.id) != null) {
                        if (messageIds.isEmpty()) {
                            unreadMessageIdsByDiscussionId.remove(message.discussionId)
                        }
                        unreadChanged = true

                        mentionMessageIdsByDiscussionId[message.discussionId]?.let { messageIds ->
                            if (messageIds.remove(message.id) && messageIds.isEmpty()) {
                                mentionMessageIdsByDiscussionId.remove(message.discussionId)
                                mentionsChanged = true
                            }
                        }
                    }
                }

                locationsSharedMessageIdsByDiscussionId[message.discussionId]?.let { messageIds ->
                    if (messageIds.remove(message.id) && messageIds.isEmpty()) {
                        locationsSharedMessageIdsByDiscussionId.remove(message.discussionId)
                        locationsSharedChanged = true
                    }
                }
            }


            if (mentionsChanged) {
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
            if (unreadChanged) {
                postUnreadMessageCountsByDiscussionLocked()
                postOwnedIdentityCountsLocked()
            }
            if (locationsSharedChanged) {
                locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
            }
        }
    }

    fun invitationCreated(dialogUuid: UUID, bytesOwnedIdentity: ByteArray, categoryId: Int) {
        synchronized(UnreadCountsSingleton) {
            // Remove any stale mapping first — an invitation can transition through categories
            // (e.g. SAS_EXCHANGE -> SAS_CONFIRMED) and we want to reflect the new category exactly.
            invitationOwners.remove(dialogUuid)?.let { previousOwner ->
                invitationsByOwnedIdentity[previousOwner]?.let { set ->
                    if (set.remove(dialogUuid) && set.isEmpty()) {
                        invitationsByOwnedIdentity.remove(previousOwner)
                    }
                }
            }
            if (categoryId in INVITATION_NOTIFICATION_CATEGORIES) {
                val owner = BytesKey(bytesOwnedIdentity)
                invitationsByOwnedIdentity.getOrPut(owner) { mutableSetOf() }.add(dialogUuid)
                invitationOwners[dialogUuid] = owner
            }
            postOwnedIdentityCountsLocked()
        }
    }

    fun invitationDeleted(dialogUuid: UUID) {
        synchronized(UnreadCountsSingleton) {
            val owner = invitationOwners.remove(dialogUuid) ?: return
            invitationsByOwnedIdentity[owner]?.let { set ->
                if (set.remove(dialogUuid) && set.isEmpty()) {
                    invitationsByOwnedIdentity.remove(owner)
                }
            }
            postOwnedIdentityCountsLocked()
        }
    }

    private fun refreshMuteStateLocked(stub: OwnedIdentityDao.MuteStateStub) {
        val key = BytesKey(stub.bytesOwnedIdentity)
        if (isMuted(stub.prefMuteNotifications, stub.prefMuteNotificationsTimestamp, System.currentTimeMillis())) {
            mutedOwnedIdentities[key] = stub.prefMuteNotificationsExceptMentioned to stub.prefMuteNotificationsTimestamp
        } else {
            mutedOwnedIdentities.remove(key)
        }
    }

    private fun recomputeMutedSet() {
        synchronized(UnreadCountsSingleton) {
            val now = System.currentTimeMillis()
            val expired = mutedOwnedIdentities.filterValues { it.second != null && it.second!! <= now }.keys.toList()
            if (expired.isNotEmpty()) {
                expired.forEach { mutedOwnedIdentities.remove(it) }
                postMutedSetLocked()
            }
            scheduleNextMuteExpiryLocked()
        }
    }

    private fun scheduleNextMuteExpiryLocked() {
        muteHandler.removeCallbacks(muteExpiryRunnable)
        val now = System.currentTimeMillis()
        val nextExpiry = mutedOwnedIdentities.values.mapNotNull { it.second }
            .filter { it > now }
            .minOrNull() ?: return
        muteHandler.postDelayed(muteExpiryRunnable, nextExpiry - now)
    }

    private fun postMutedSetLocked() {
        val snapshot = Pair(mutedOwnedIdentities.keys.toSet(), mutedOwnedIdentities.filter { it.value.first }.keys)
        if (snapshot == lastEmittedMutedSet) return
        lastEmittedMutedSet = snapshot
        mutedOwnedIdentitiesLiveData.postValue(snapshot.first)
        mutedExceptMentionOwnedIdentitiesLiveData.postValue(snapshot.second)
    }

    private fun postUnreadMessageCountsByDiscussionLocked() {
        val snapshot = unreadMessageIdsByDiscussionId.mapValues { it.value.size }
        if (snapshot == lastEmittedUnreadCountsByDiscussion) return
        lastEmittedUnreadCountsByDiscussion = snapshot
        unreadMessageCountsByDiscussionLiveData.postValue(snapshot)
    }

    private fun postOwnedIdentityCountsLocked() {
        val snapshot = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = unreadMessageIdsByDiscussionId.mapValues { it.value.size },
            mentionMessageCountsByDiscussion = mentionMessageIdsByDiscussionId.mapValues { it.value.size },
            discussionMetadata = discussionMetadata,
            unreadDiscussionsByOwnedIdentity = unreadDiscussionsByOwnedIdentity,
            invitationsByOwnedIdentity = invitationsByOwnedIdentity,
        )
        if (snapshot == lastEmittedOwnedIdentityCounts) return
        lastEmittedOwnedIdentityCounts = snapshot
        unreadCountsByOwnedIdentityLiveData.postValue(snapshot)
    }
}
