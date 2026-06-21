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

package io.olvid.messenger.storage_manager

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.sqlite.db.SimpleSQLiteQuery
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.DiscussionAndUsage
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndOrigin
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.OwnedIdentity

class StorageManagerViewModel : ViewModel() {

    // ---- Legacy sort (used in detail screen) ----

    var currentSortOrder: SortOrder = SortOrder(SortKey.SIZE, false)
    val sortOrderMutableLiveData: MutableLiveData<SortOrder> = MutableLiveData(currentSortOrder)
    val selectedFyles = mutableStateSetOf<FyleAndStatus>()
    val selectedCountLiveData: MutableLiveData<Int> = MutableLiveData(0)
    val selectedSizeLiveData: MutableLiveData<Long> = MutableLiveData(0L)

    fun setSortOrder(sortOrder: SortOrder) {
        currentSortOrder = sortOrder
        sortOrderMutableLiveData.postValue(sortOrder)
    }

    fun selectFyle(fyleAndStatus: FyleAndStatus) {
        if (!selectedFyles.remove(fyleAndStatus)) {
            selectedFyles.add(fyleAndStatus)
        }
        selectedCountLiveData.postValue(selectedFyles.size)
        selectedSizeLiveData.postValue(selectedFyles.sumOf { it.fyleMessageJoinWithStatus.size })
    }

    fun clearSelectedFyles() {
        if (selectedFyles.isNotEmpty()) {
            selectedFyles.clear()
            selectedCountLiveData.postValue(0)
            selectedSizeLiveData.postValue(0L)
        }
    }

    fun isSelected(fyleAndStatus: FyleAndStatus): Boolean = selectedFyles.contains(fyleAndStatus)

    fun isSelecting(): Boolean = selectedFyles.isNotEmpty()

    fun selectAllFyles(fyleAndOrigins: List<FyleAndOrigin>) {
        for (fyleAndOrigin in fyleAndOrigins) {
            selectedFyles.add(fyleAndOrigin.fyleAndStatus)
        }
        selectedCountLiveData.postValue(fyleAndOrigins.size)
        selectedSizeLiveData.postValue(selectedFyles.sumOf { it.fyleMessageJoinWithStatus.size })
    }

    // ---- Discussion sort ----

    enum class DiscussionSortKey { SIZE, DATE }

    val discussionSortKeyLiveData: MutableLiveData<DiscussionSortKey> =
        MutableLiveData(DiscussionSortKey.SIZE)

    fun setDiscussionSortKey(key: DiscussionSortKey) {
        discussionSortKeyLiveData.postValue(key)
    }

    // ---- Bucket navigation ----

    sealed class BucketDestination {
        object SentByMe : BucketDestination()
        data class LargeFiles(val minSize: Long = 5_242_880L) : BucketDestination()
        data class ByDiscussion(val discussionId: Long) : BucketDestination()
        object AllFiles : BucketDestination()
    }

    val bucketDestinationLiveData: MutableLiveData<BucketDestination?> = MutableLiveData(null)

    // ---- Discussions with usage ----

    val discussionsWithUsage: LiveData<List<DiscussionAndUsage>> =
        MediatorLiveData<List<DiscussionAndUsage>>().also { mediator ->
            var identity: OwnedIdentity? = null
            var sortKey: DiscussionSortKey = DiscussionSortKey.SIZE
            var currentSource: LiveData<List<DiscussionAndUsage>>? = null

            fun rebuild() {
                val ident = identity ?: return
                currentSource?.let { mediator.removeSource(it) }
                val newSource = when (sortKey) {
                    DiscussionSortKey.SIZE ->
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                            .getDiscussionsWithUsageSizeDesc(ident.bytesOwnedIdentity)
                    DiscussionSortKey.DATE ->
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                            .getDiscussionsWithUsageDateDesc(ident.bytesOwnedIdentity)
                }
                currentSource = newSource
                mediator.addSource(newSource) { mediator.value = it }
            }

            mediator.addSource(AppSingleton.getCurrentIdentityLiveData()) {
                identity = it
                rebuild()
            }
            mediator.addSource(discussionSortKeyLiveData) {
                sortKey = it ?: DiscussionSortKey.SIZE
                rebuild()
            }
        }

    // ---- Sent by me bucket ----

    val sentByMePreview: LiveData<List<FyleAndOrigin>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(emptyList())
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getSentByMeFylePreview(identity.bytesOwnedIdentity)
        }

    val sentByMeCount: LiveData<Int> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(0)
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getSentByMeFyleCount(identity.bytesOwnedIdentity)
        }

    // ---- Large files bucket ----

    val largeFilesPreview: LiveData<List<FyleAndOrigin>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(emptyList())
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getLargeFileFylePreview(identity.bytesOwnedIdentity, 5_242_880L)
        }

    val largeFilesCount: LiveData<Int> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(0)
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getLargeFileFyleCount(identity.bytesOwnedIdentity, 5_242_880L)
        }

    // ---- All files bucket ----
    val allFilesCount: LiveData<Int> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(0)
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getAllFilesFyleCount(identity.bytesOwnedIdentity)
        }

    private val discussionPalette = listOf(
        Color(0xFF1976D2),
        Color(0xFF388E3C),
        Color(0xFFD32F2F),
        Color(0xFFF57C00),
        Color(0xFF7B1FA2),
        Color(0xFF0097A7),
        Color(0xFF5D4037),
        Color(0xFF455A64),
        )

    val discussionColorMap: LiveData<Map<Long, Color>> =
        MediatorLiveData<Map<Long, Color>>().also { mediator ->
            mediator.addSource(discussionsWithUsage) { list ->
                mediator.value = list?.take(discussionPalette.size)
                    ?.mapIndexed { index, item -> item.discussion.id to discussionPalette[index] }
                    ?.toMap()
                    ?: emptyMap()
            }
        }

    // ---- Total storage size for current identity (fully deduplicated) ----

    val currentIdentityTotalSize: LiveData<Long> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { identity ->
            if (identity == null) MutableLiveData(0L)
            else AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getTotalStorageSizeForIdentity(identity.bytesOwnedIdentity)
        }

    // ---- Other identities (for profile switcher dropdown) ----

    val otherIdentities: LiveData<List<OwnedIdentity>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { current ->
            AppDatabase.getInstance().ownedIdentityDao()
                .getAllNotHiddenExceptOne(current?.bytesOwnedIdentity ?: ByteArray(0))
        }

    // Map of hex-encoded bytesOwnedIdentity → total storage size
    val otherIdentitySizes: MediatorLiveData<Map<String, Long>> =
        MediatorLiveData<Map<String, Long>>(emptyMap()).also { mediator ->
            val currentSources = mutableMapOf<String, LiveData<Long>>()
            val currentValues = mutableMapOf<String, Long>()

            fun rebuildSources(identities: List<OwnedIdentity>) {
                // Remove sources that are no longer needed
                val newKeys = identities.map { Logger.toHexString(it.bytesOwnedIdentity) }.toSet()
                val toRemove = currentSources.keys - newKeys
                for (key in toRemove) {
                    currentSources.remove(key)?.let { mediator.removeSource(it) }
                    currentValues.remove(key)
                }
                // Add sources for new identities
                for (identity in identities) {
                    val key = Logger.toHexString(identity.bytesOwnedIdentity)
                    if (key !in currentSources) {
                        val source = AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                            .getTotalStorageSizeForIdentity(identity.bytesOwnedIdentity)
                        currentSources[key] = source
                        mediator.addSource(source) { size ->
                            currentValues[key] = size ?: 0L
                            mediator.value = currentValues.toMap()
                        }
                    }
                }
            }

            mediator.addSource(otherIdentities) { identities ->
                rebuildSources(identities ?: emptyList())
            }
        }

    // ---- Bucket screen fyle lists (keyed on destination + sortOrder + identity) ----

    fun setBucketDestination(destination: BucketDestination) {
        bucketDestinationLiveData.value = destination
    }

    @Suppress("ArrayInDataClass")
    private data class BucketQueryParams(
        val destination: BucketDestination,
        val sortOrder: SortOrder,
        val bytesOwnedIdentity: ByteArray?,
    )

    private val bucketQueryParams: MediatorLiveData<BucketQueryParams> =
        MediatorLiveData<BucketQueryParams>().also { mediator ->
            var destination: BucketDestination? = null
            var sortOrder = SortOrder(SortKey.SIZE, false)
            var identity: ByteArray? = null

            fun update() {
                val dest = destination ?: return
                mediator.value = BucketQueryParams(dest, sortOrder, identity)
            }

            mediator.addSource(bucketDestinationLiveData) { destination = it; update() }
            mediator.addSource(sortOrderMutableLiveData) { sortOrder = it ?: SortOrder(SortKey.SIZE, false); update() }
            mediator.addSource(AppSingleton.getCurrentIdentityLiveData()) { identity = it?.bytesOwnedIdentity; update() }
        }

    val bucketMediaFyles: LiveData<List<FyleAndOrigin>?> = bucketFylesFor(BucketType.MEDIA)
    val bucketFileFyles: LiveData<List<FyleAndOrigin>?> = bucketFylesFor(BucketType.FILE)
    val bucketAudioFyles: LiveData<List<FyleAndOrigin>?> = bucketFylesFor(BucketType.AUDIO)
    val bucketAllFyles: LiveData<List<FyleAndOrigin>?> = bucketFylesFor(BucketType.ALL)


    // Resets to null (shimmer) only when BucketDestination changes.
    // Sort order / identity changes keep stale data visible while the new query runs.
    private fun bucketFylesFor(type: BucketType): LiveData<List<FyleAndOrigin>?> =
        MediatorLiveData<List<FyleAndOrigin>?>().also { mediator ->
            var currentSource: LiveData<List<FyleAndOrigin>>? = null
            var lastDestination: BucketDestination? = null
            var lastBytesOwnedIdentity: ByteArray? = null
            mediator.addSource(bucketQueryParams) { params ->
                if (params.destination != lastDestination || !params.bytesOwnedIdentity.contentEquals(lastBytesOwnedIdentity)) {
                    mediator.value = null
                    lastDestination = params.destination
                    lastBytesOwnedIdentity = params.bytesOwnedIdentity
                }
                currentSource?.let { mediator.removeSource(it) }
                val newSource = buildFyleList(params, type)
                currentSource = newSource
                mediator.addSource(newSource) { mediator.value = it }
            }
        }

    private fun buildFyleList(params: BucketQueryParams, type: BucketType): LiveData<List<FyleAndOrigin>> {
        val dao = AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
        val typeCondition = when (type) {
            BucketType.MEDIA -> FyleMessageJoinWithStatusDao.MEDIA_TYPE_CONDITION
            BucketType.AUDIO -> FyleMessageJoinWithStatusDao.AUDIO_TYPE_CONDITION
            BucketType.FILE -> FyleMessageJoinWithStatusDao.FILE_TYPE_CONDITION
            BucketType.ALL -> ""
        }
        val sortColumn = when (params.sortOrder.sortKey) {
            SortKey.SIZE -> FyleMessageJoinWithStatusDao.SORT_COLUMN_SIZE
            SortKey.DATE -> FyleMessageJoinWithStatusDao.SORT_COLUMN_DATE
            SortKey.NAME -> FyleMessageJoinWithStatusDao.SORT_COLUMN_NAME
        }
        val sortDir = if (params.sortOrder.ascending) " ASC " else " DESC "
        val groupAndOrder = FyleMessageJoinWithStatusDao.GROUP_BY_SHA256 + " ORDER BY $sortColumn $sortDir "

        return when (val destination = params.destination) {
            is BucketDestination.SentByMe -> {
                val bytes = params.bytesOwnedIdentity ?: return MutableLiveData()
                val sql = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE +
                        FyleMessageJoinWithStatusDao.SENT_BY_ME_CONDITION + typeCondition + groupAndOrder
                dao.getFyleAndOriginRaw(SimpleSQLiteQuery(sql, arrayOf(bytes)))
            }
            is BucketDestination.LargeFiles -> {
                val bytes = params.bytesOwnedIdentity ?: return MutableLiveData()
                val sql = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE +
                        FyleMessageJoinWithStatusDao.LARGE_FILE_RAW_CONDITION + typeCondition + groupAndOrder
                dao.getFyleAndOriginRaw(SimpleSQLiteQuery(sql, arrayOf(bytes, destination.minSize)))
            }
            is BucketDestination.ByDiscussion -> {
                val sql = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_FOR_DISCUSSION_RAW_BASE +
                        typeCondition + groupAndOrder
                dao.getFyleAndOriginRaw(SimpleSQLiteQuery(sql, arrayOf(destination.discussionId)))
            }
            is BucketDestination.AllFiles -> {
                val bytes = params.bytesOwnedIdentity ?: return MutableLiveData()
                val sql = FyleMessageJoinWithStatusDao.FYLE_AND_ORIGIN_RAW_BASE +
                        typeCondition + groupAndOrder
                dao.getFyleAndOriginRaw(SimpleSQLiteQuery(sql, arrayOf(bytes)))
            }
        }
    }

    // ---- Enums ----

    enum class BucketType { MEDIA, FILE, AUDIO, ALL }
    enum class SortKey { SIZE, DATE, NAME }

    data class SortOrder(val sortKey: SortKey, val ascending: Boolean)
}
