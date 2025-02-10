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
package io.olvid.messenger.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DISCUSSION
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DRAFT
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.MESSAGE
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.OWNED_IDENTITY
import java.util.Objects


class GalleryViewModel : ViewModel() {
    private val discussionId: MutableLiveData<Long?> = MutableLiveData(null)
    private val messageId: MutableLiveData<Long?> = MutableLiveData(null)
    private val ownedIdentitySortOrder: MutableLiveData<String?> = MutableLiveData(null)
    private val ownedIdentitySortOrderAscending: MutableLiveData<Boolean?> = MutableLiveData(null)
    private val bytesOwnedIdentity: MutableLiveData<ByteArray?> = MutableLiveData(null)

    val imageAndVideoFyleAndStatusList: LiveData<List<FyleAndStatus>?> = TripleLiveData(
        discussionId,
        messageId,
        ownedIdentitySortOrder,
        ownedIdentitySortOrderAscending,
        bytesOwnedIdentity
    )
    private val currentPagerPosition: MutableLiveData<Int?> = MutableLiveData(null)

    val currentFyleAndStatus: LiveData<FyleAndStatus?> =
        PositionAndListLiveData(currentPagerPosition, imageAndVideoFyleAndStatusList)

    val currentAssociatedMessage: LiveData<Message?> =
        currentFyleAndStatus.switchMap { fyleAndStatus: FyleAndStatus? ->
            if (fyleAndStatus != null) {
                return@switchMap getInstance().messageDao()
                    .getLive(fyleAndStatus.fyleMessageJoinWithStatus.messageId)
            }
            null
        }

    val currentAssociatedTextBlocks: LiveData<List<TextBlock>?> =
        currentFyleAndStatus.switchMap { fyleAndStatus: FyleAndStatus? ->
            if (fyleAndStatus != null) {
                return@switchMap getInstance().fyleMessageTextBlockDao()
                    .getAllLive(
                        fyleAndStatus.fyleMessageJoinWithStatus.messageId,
                        fyleAndStatus.fyle.id
                    )
            }
            null
        }

    val currentAssociatedMessageExpiration: LiveData<MessageExpiration?> =
        currentFyleAndStatus.switchMap { fyleAndStatus: FyleAndStatus? ->
            if (fyleAndStatus != null) {
                return@switchMap getInstance().messageExpirationDao()
                    .getLive(fyleAndStatus.fyleMessageJoinWithStatus.messageId)
            }
            null
        }

    enum class GalleryType {
        DISCUSSION,
        DRAFT,
        MESSAGE,
        OWNED_IDENTITY,
    }

    var galleryType: GalleryType? = null
        private set

    fun setDiscussionId(discussionId: Long, ascending: Boolean?) {
        ownedIdentitySortOrderAscending.postValue(ascending)
        this.discussionId.postValue(discussionId)
        galleryType = DISCUSSION
    }

    fun setMessageId(messageId: Long, draft: Boolean) {
        this.messageId.postValue(messageId)
        galleryType = if (draft) {
            DRAFT
        } else {
            MESSAGE
        }
    }

    fun setBytesOwnedIdentity(
        bytesOwnedIdentity: ByteArray?,
        sortOrder: String?,
        ascending: Boolean?
    ) {
        ownedIdentitySortOrder.postValue(sortOrder)
        ownedIdentitySortOrderAscending.postValue(ascending)
        this.bytesOwnedIdentity.postValue(bytesOwnedIdentity)
        galleryType = OWNED_IDENTITY
    }

    fun getCurrentPagerPosition(): Int? {
        return currentPagerPosition.value
    }

    fun setCurrentPagerPosition(position: Int) {
        currentPagerPosition.postValue(position)
    }

    class MessageAndFyleId(@JvmField val messageId: Long, @JvmField val fyleId: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as MessageAndFyleId
            return messageId == that.messageId &&
                    fyleId == that.fyleId
        }

        override fun hashCode(): Int {
            return Objects.hash(messageId, fyleId)
        }
    }

    class TripleLiveData(
        discussionId: MutableLiveData<Long?>,
        messageId: MutableLiveData<Long?>,
        ownedIdentitySortOrder: MutableLiveData<String?>,
        ascending: MutableLiveData<Boolean?>,
        bytesOwnedIdentity: MutableLiveData<ByteArray?>
    ) : MediatorLiveData<List<FyleAndStatus>?>() {
        private val db = getInstance()
        private var discussionId: Long? = null
        private var bytesOwnedIdentity: ByteArray? = null
        private var sortOrder: String? = null
        private var ascending: Boolean? = null

        private var source: LiveData<List<FyleAndStatus>>? = null

        init {
            addSource(discussionId) { this.onDiscussionChanged(it) }
            addSource(messageId) { this.onMessageChanged(it) }
            addSource(ownedIdentitySortOrder) { this.onSortOrderChanged(it) }
            addSource(ascending) { this.onAscendingChanged(it) }
            addSource(bytesOwnedIdentity) { this.onOwnedIdentityChanged(it) }
        }

        private fun onDiscussionChanged(discussionId: Long?) {
            this.discussionId = discussionId
            updateDiscussion(discussionId, ascending)
        }

        private fun updateDiscussion(discussionId: Long?, ascending: Boolean?) {
            val newSource = if (discussionId != null) {
                if (ascending == null || ascending) {
                    db.fyleMessageJoinWithStatusDao()
                        .getImageAndVideoFylesAndStatusesForDiscussion(discussionId)
                } else {
                    db.fyleMessageJoinWithStatusDao()
                        .getImageAndVideoFylesAndStatusesForDiscussionDescending(discussionId)
                }
            } else {
                null
            }
            if (newSource === source) {
                return
            }
            if (source != null) {
                removeSource(source!!)
            }
            source = newSource
            if (source != null) {
                addSource(
                    source!!
                ) { value: List<FyleAndStatus>? ->
                    this.value =
                        value
                }
            }
        }

        private fun onMessageChanged(messageId: Long?) {
            val newSource = if (messageId != null) {
                db.fyleMessageJoinWithStatusDao()
                    .getImageAndVideoFylesAndStatusForMessage(messageId)
            } else {
                null
            }
            if (newSource === source) {
                return
            }
            if (source != null) {
                removeSource(source!!)
            }
            source = newSource
            if (source != null) {
                addSource(
                    source!!
                ) { value: List<FyleAndStatus>? ->
                    this.value =
                        value
                }
            }
        }

        private fun onOwnedIdentityChanged(bytesOwnedIdentity: ByteArray?) {
            this.bytesOwnedIdentity = bytesOwnedIdentity
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
        }

        private fun onSortOrderChanged(sortOrder: String?) {
            this.sortOrder = sortOrder
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
        }

        private fun onAscendingChanged(ascending: Boolean?) {
            this.ascending = ascending
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
            updateDiscussion(discussionId, ascending)
        }

        private fun updateOwnedIdentity(
            bytesOwnedIdentity: ByteArray?,
            sortOrder: String?,
            ascending: Boolean?
        ) {
            val newSource = if (bytesOwnedIdentity != null) {
                if ("size" == sortOrder) {
                    if (ascending != null && ascending) {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentityBySizeAscending(
                                bytesOwnedIdentity
                            )
                    } else {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentityBySize(
                                bytesOwnedIdentity
                            )
                    }
                } else if ("name" == sortOrder) {
                    if (ascending != null && !ascending) {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentityByName(
                                bytesOwnedIdentity
                            )
                    } else {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentityByNameAscending(
                                bytesOwnedIdentity
                            )
                    }
                } else {
                    if (ascending != null && ascending) {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentityAscending(
                                bytesOwnedIdentity
                            )
                    } else {
                        db.fyleMessageJoinWithStatusDao()
                            .getImageAndVideoFylesAndStatusesForOwnedIdentity(bytesOwnedIdentity)
                    }
                }
            } else {
                null
            }
            if (newSource === source) {
                return
            }
            if (source != null) {
                removeSource(source!!)
            }
            source = newSource
            if (source != null) {
                addSource(
                    source!!
                ) { value: List<FyleAndStatus>? ->
                    this.value =
                        value
                }
            }
        }
    }

    class PositionAndListLiveData(
        currentPagerPosition: LiveData<Int?>,
        imageAndVideoFyleAndStatusList: LiveData<List<FyleAndStatus>?>
    ) :
        MediatorLiveData<FyleAndStatus?>() {
        private var position: Int? = null
        private var list: List<FyleAndStatus>? = null

        init {
            addSource(
                currentPagerPosition
            ) { position: Int? -> this.onPositionChanged(position) }
            addSource<List<FyleAndStatus>>(
                imageAndVideoFyleAndStatusList
            ) { list: List<FyleAndStatus> -> this.onListChanged(list) }
        }

        private fun onPositionChanged(position: Int?) {
            this.position = position
            updateValue()
        }

        private fun onListChanged(list: List<FyleAndStatus>) {
            this.list = list
            updateValue()
        }

        private fun updateValue() {
            value =
                if (position != null && list != null && position!! >= 0 && position!! < list!!.size) {
                    list!![position!!]
                } else {
                    null
                }
        }
    }
}
