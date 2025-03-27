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

package io.olvid.messenger

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message


object UnreadCountsSingleton {
    private val unreadMessageIdsByDiscussionId: MutableMap<Long, MutableMap<Long, Long>> = mutableMapOf()
    private val mentionMessageIdsByDiscussionId: MutableMap<Long, MutableSet<Long>> = mutableMapOf()
    private val locationsSharedMessageIdsByDiscussionId: MutableMap<Long, MutableSet<Long>> = mutableMapOf()

    private val unreadMessageCountsByDiscussionLiveData: MutableLiveData<Map<Long, Int>> = MutableLiveData(emptyMap())
    private val mentionsDiscussionsLiveData: MutableLiveData<Set<Long>> = MutableLiveData(emptySet())
    private val locationsSharedDiscussionsLiveData: MutableLiveData<Set<Long>> = MutableLiveData(emptySet())

    fun getUnreadMessageCountsByDiscussionLiveData(): LiveData<Map<Long, Int>> {
        return unreadMessageCountsByDiscussionLiveData
    }

    fun getMentionsDiscussionsLiveData(): LiveData<Set<Long>> {
        return mentionsDiscussionsLiveData
    }

    fun getLocationsSharedDiscussionsLiveData(): LiveData<Set<Long>> {
        return locationsSharedDiscussionsLiveData
    }

    fun initialize() {
        synchronized(UnreadCountsSingleton) {
            val unreadMessages = AppDatabase.getInstance().messageDao().getAllUnreadMessageStubs()
            unreadMessages.forEach {
                unreadMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableMapOf() }[it.messageId] = it.timestamp
                if (it.mentioned) {
                    mentionMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableSetOf() }
                        .add(it.messageId)
                }
            }

            val locationMessages = AppDatabase.getInstance().messageDao().getAllLocationMessageStubs()
            locationMessages.forEach {
                locationsSharedMessageIdsByDiscussionId.getOrPut(it.discussionId) { mutableSetOf() }
                    .add(it.messageId)
            }

            unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })
            mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
        }
    }

    fun newUnreadMessage(discussionId: Long, messageId: Long, mentioned: Boolean, timestamp: Long) {
        synchronized(UnreadCountsSingleton) {
            unreadMessageIdsByDiscussionId.getOrPut(discussionId) { mutableMapOf() }[messageId] = timestamp
            unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })

            if (mentioned) {
                mentionMessageIdsByDiscussionId.getOrPut(discussionId) { mutableSetOf() }
                    .add(messageId)
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
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

            if (unreadRemoved) {
                unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })
            }
            if (mentionRemoved) {
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
        }
    }

    fun markDiscussionRead(discussionId: Long, upToTimestamp: Long?) {
        synchronized(UnreadCountsSingleton) {
            if (upToTimestamp == null) {
                if (unreadMessageIdsByDiscussionId.remove(discussionId) != null) {
                    unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })

                    if (mentionMessageIdsByDiscussionId.remove(discussionId) != null) {
                        mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
                    }
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
                        unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })

                        if (mentionMessageIds?.isEmpty() == true) {
                            mentionMessageIdsByDiscussionId.remove(discussionId)
                            mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
                        }
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

            if (unreadChanged) {
                unreadMessageCountsByDiscussionLiveData.postValue(unreadMessageIdsByDiscussionId.mapValues { it.value.size })
            }
            if (mentionsChanged) {
                mentionsDiscussionsLiveData.postValue(mentionMessageIdsByDiscussionId.keys)
            }
            if (locationsSharedChanged) {
                locationsSharedDiscussionsLiveData.postValue(locationsSharedMessageIdsByDiscussionId.keys)
            }
        }
    }
}