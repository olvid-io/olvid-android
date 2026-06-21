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
package io.olvid.messenger.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.sqlite.db.SimpleSQLiteQuery
import io.olvid.messenger.App
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DISCUSSION
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DRAFT
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.LARGE_FILES
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.MESSAGE
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.OWNED_IDENTITY
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.SENT_BY_ME
import java.util.Objects


class GalleryViewModel : ViewModel() {

    val mediaPlayer: ExoPlayer? = runCatching {
        ExoPlayer.Builder(App.getContext()).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
        }
    }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }

    private val discussionId: MutableLiveData<Long?> = MutableLiveData(null)
    private val messageId: MutableLiveData<Long?> = MutableLiveData(null)
    private val ownedIdentitySortOrder: MutableLiveData<String?> = MutableLiveData(null)
    private val ownedIdentitySortOrderAscending: MutableLiveData<Boolean?> = MutableLiveData(null)
    private val bytesOwnedIdentity: MutableLiveData<ByteArray?> = MutableLiveData(null)

    private val _tripleSource: TripleLiveData = TripleLiveData(
        discussionId,
        messageId,
        ownedIdentitySortOrder,
        ownedIdentitySortOrderAscending,
        bytesOwnedIdentity
    )

    val imageAndVideoFyleAndStatusList: MediatorLiveData<List<FyleAndStatus>?> =
        MediatorLiveData<List<FyleAndStatus>?>().also { m ->
            m.addSource(_tripleSource) {
                m.value = it
            }
        }
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
        SENT_BY_ME,
        LARGE_FILES,
        LINK_PREVIEW,
    }

    var galleryType: GalleryType? = null
        private set

    fun setDiscussionId(discussionId: Long, sortOrder: String?, ascending: Boolean?, fromStorageManager: Boolean = false) {
        galleryType = DISCUSSION
        if (fromStorageManager) {
            setSortedRawSource(
                base = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_FOR_DISCUSSION_RAW_BASE,
                extraCondition = "",
                args = arrayOf(discussionId),
                sortOrder = sortOrder,
                ascending = ascending == true,
            )
        } else {
            ownedIdentitySortOrder.postValue(sortOrder)
            ownedIdentitySortOrderAscending.postValue(ascending)
            this.discussionId.postValue(discussionId)
        }
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
        galleryType = OWNED_IDENTITY
        ownedIdentitySortOrder.postValue(sortOrder)
        ownedIdentitySortOrderAscending.postValue(ascending)
        this.bytesOwnedIdentity.postValue(bytesOwnedIdentity)
    }

    private var _directSource: LiveData<List<FyleAndStatus>>? = null

    private fun setDirectSource(source: LiveData<List<FyleAndStatus>>) {
        imageAndVideoFyleAndStatusList.removeSource(_tripleSource)
        _directSource?.let { imageAndVideoFyleAndStatusList.removeSource(it) }
        _directSource = source
        imageAndVideoFyleAndStatusList.addSource(source) { imageAndVideoFyleAndStatusList.value = it }
    }

    private fun setSortedRawSource(
        base: String,
        extraCondition: String,
        args: Array<Any>,
        sortOrder: String?,
        ascending: Boolean,
    ) {
        val sortCol = when (sortOrder) {
            "size" -> FyleMessageJoinWithStatusDao.SORT_COLUMN_SIZE
            "name" -> FyleMessageJoinWithStatusDao.SORT_COLUMN_NAME
            else -> FyleMessageJoinWithStatusDao.SORT_COLUMN_DATE
        }
        val sortDir = if (ascending) "ASC" else "DESC"
        val sql = base + extraCondition +
                FyleMessageJoinWithStatusDao.MEDIA_TYPE_CONDITION +
                FyleMessageJoinWithStatusDao.GROUP_BY_SHA256 +
                " ORDER BY $sortCol $sortDir"
        val dao = getInstance().fyleMessageJoinWithStatusDao()
        setDirectSource(dao.getImageAndVideoFylesRaw(SimpleSQLiteQuery(sql, args)))
    }

    fun setSentByMeGallery(bytesOwnedIdentity: ByteArray, sortOrder: String?, ascending: Boolean) {
        galleryType = SENT_BY_ME
        setSortedRawSource(
            base = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE,
            extraCondition = FyleMessageJoinWithStatusDao.SENT_BY_ME_CONDITION,
            args = arrayOf(bytesOwnedIdentity),
            sortOrder = sortOrder,
            ascending = ascending,
        )
    }

    fun setLargeFilesGallery(bytesOwnedIdentity: ByteArray, minSize: Long, sortOrder: String?, ascending: Boolean) {
        galleryType = LARGE_FILES
        setSortedRawSource(
            base = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE,
            extraCondition = FyleMessageJoinWithStatusDao.LARGE_FILE_RAW_CONDITION,
            args = arrayOf(bytesOwnedIdentity, minSize),
            sortOrder = sortOrder,
            ascending = ascending,
        )
    }

    var linkPreviewOpenGraph: OpenGraph? = null

    fun setLinkPreview(openGraph: OpenGraph) {
        galleryType = GalleryType.LINK_PREVIEW
        linkPreviewOpenGraph = openGraph
        _tripleSource.showLinkPreview()
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

    inner class TripleLiveData(
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

        fun showLinkPreview() {
            updateLinkPreview()
        }

        private fun updateLinkPreview() {
            if (galleryType == GalleryType.LINK_PREVIEW) {
                source?.let {
                    removeSource(it)
                }
                source = null

                // Fake FyleAndStatus to make the adapter display one item.
                val fakeFyleAndStatus = FyleAndStatus().apply {
                    fyle = Fyle().apply { id = -1 }
                    fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                        -1, -1, byteArrayOf(), "", "", OpenGraph.MIME_TYPE,
                        FyleMessageJoinWithStatus.STATUS_COMPLETE, 0, null, null, null
                    )
                }

                value = listOf(fakeFyleAndStatus)
            }
        }

        private fun onDiscussionChanged(discussionId: Long?) {
            this.discussionId = discussionId
            updateDiscussion(discussionId, sortOrder, ascending)
        }

        private fun updateDiscussion(discussionId: Long?, sortOrder: String?, ascending: Boolean?) {
            if (galleryType != DISCUSSION) return
            val newSource = if (discussionId != null) {
                val orderClause = when (sortOrder) {
                    "size" -> " ORDER BY ${FyleMessageJoinWithStatusDao.SORT_COLUMN_SIZE} ${if (ascending == true) "ASC" else "DESC"}"
                    "name" -> " ORDER BY ${FyleMessageJoinWithStatusDao.SORT_COLUMN_NAME} ${if (ascending != false) "ASC" else "DESC"}"
                    else -> {
                        val dir = if (ascending != false) "ASC" else "DESC"
                        " ORDER BY ${FyleMessageJoinWithStatusDao.SORT_COLUMN_SORT_INDEX} $dir, ${FyleMessageJoinWithStatusDao.SORT_COLUMN_ENGINE_NUMBER} $dir "
                    }
                }
                val sql =
                    FyleMessageJoinWithStatusDao.IMAGE_AND_VIDEO_FOR_DISCUSSION_RAW_BASE + orderClause
                db.fyleMessageJoinWithStatusDao()
                    .getImageAndVideoFylesRaw(SimpleSQLiteQuery(sql, arrayOf(discussionId)))
            } else {
                null
            }
            source?.let {
                removeSource(it)
            }
            source = newSource
            source?.let {
                addSource(it) { value: List<FyleAndStatus>? ->
                    this.value = value
                }
            }
        }

        private fun onMessageChanged(messageId: Long?) {
            if (galleryType != MESSAGE && galleryType != DRAFT) return

            val newSource = if (messageId != null) {
                db.fyleMessageJoinWithStatusDao()
                    .getImageAndVideoFylesAndStatusForMessage(messageId)
            } else {
                null
            }
            source?.let {
                removeSource(it)
            }
            source = newSource
            source?.let {
                addSource(it) { value: List<FyleAndStatus>? ->
                    this.value = value
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
            updateDiscussion(discussionId, sortOrder, ascending)
        }

        private fun onAscendingChanged(ascending: Boolean?) {
            this.ascending = ascending
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
            updateDiscussion(discussionId, sortOrder, ascending)
        }

        private fun updateOwnedIdentity(
            bytesOwnedIdentity: ByteArray?,
            sortOrder: String?,
            ascending: Boolean?
        ) {
            if (galleryType != OWNED_IDENTITY) return

            val newSource = if (bytesOwnedIdentity != null) {
                val sortCol = when (sortOrder) {
                    "size" -> FyleMessageJoinWithStatusDao.SORT_COLUMN_SIZE
                    "name" -> FyleMessageJoinWithStatusDao.SORT_COLUMN_NAME
                    else -> FyleMessageJoinWithStatusDao.SORT_COLUMN_DATE
                }
                val sortDir = if (ascending == true) "ASC" else "DESC"
                val sql = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE +
                        FyleMessageJoinWithStatusDao.MEDIA_TYPE_CONDITION +
                        FyleMessageJoinWithStatusDao.GROUP_BY_SHA256 +
                        " ORDER BY $sortCol $sortDir"
                db.fyleMessageJoinWithStatusDao()
                    .getImageAndVideoFylesRaw(SimpleSQLiteQuery(sql, arrayOf(bytesOwnedIdentity)))
            } else {
                null
            }
            source?.let {
                removeSource(it)
            }
            source = newSource
            source?.let {
                addSource(it) { value: List<FyleAndStatus>? ->
                    this.value = value
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
            addSource(currentPagerPosition) { position: Int? ->
                this.onPositionChanged(position)
            }
            addSource<List<FyleAndStatus>>(imageAndVideoFyleAndStatusList) { list: List<FyleAndStatus> ->
                this.onListChanged(list)
            }
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
