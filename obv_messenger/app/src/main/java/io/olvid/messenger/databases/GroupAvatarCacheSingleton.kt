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

package io.olvid.messenger.databases

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.dao.Group2MemberDao.GroupMemberAvatarInfo
import io.olvid.messenger.databases.entity.OwnedIdentity

// Ranked group members for the group avatar cluster (for main thread access).
// Mirrors ContactCacheSingleton: only ordered member identity bytes + total count are
// cached; each member's photo/initial is resolved at render time from ContactCacheSingleton.
object GroupAvatarCacheSingleton {
    private val groupMembersCache: MutableLiveData<MutableMap<BytesKey, GroupAvatarCacheEntry>> = MutableLiveData()

    // the cache map is mutated in place and re-posted as the same instance, which
    // Compose cannot observe; this version counter is bumped on every mutation instead
    private val mutableCacheVersion = MutableLiveData(0L)
    val cacheVersion: LiveData<Long> = mutableCacheVersion

    private fun bumpCacheVersion() {
        mutableCacheVersion.postValue((mutableCacheVersion.value ?: 0L) + 1)
    }

    fun getGroupAvatarInfo(bytesGroupIdentifier: ByteArray?): GroupAvatarCacheEntry? {
        return groupMembersCache.value?.get(BytesKey(bytesGroupIdentifier))
    }

    fun reload() {
        reload(AppSingleton.getCurrentIdentityLiveData().value)
    }

    fun reload(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) {
            groupMembersCache.postValue(mutableMapOf())
            bumpCacheVersion()
            return
        }

        val groupMembersHashMap = mutableMapOf<BytesKey, GroupAvatarCacheEntry>()
        getInstance().group2MemberDao().getAllGroupMembersAvatarInfoSync(ownedIdentity.bytesOwnedIdentity)
            .groupBy { BytesKey(it.bytesGroupIdentifier) } // rows are already ranked by the query, groupBy preserves their order
            .forEach { (key, rows) ->
                groupMembersHashMap[key] = rows.toCacheEntry()
            }

        groupMembersCache.postValue(groupMembersHashMap)
        bumpCacheVersion()
    }

    fun updateGroup(bytesOwnedIdentity: ByteArray, bytesGroupIdentifier: ByteArray) {
        if (!bytesOwnedIdentity.contentEquals(AppSingleton.getBytesCurrentIdentity())) {
            return
        }
        groupMembersCache.value?.let { mutableMap ->
            val rows = getInstance().group2MemberDao()
                .getGroupMembersAvatarInfoSync(bytesOwnedIdentity, bytesGroupIdentifier)
            val key = BytesKey(bytesGroupIdentifier)
            if (rows.isEmpty()) {
                mutableMap.remove(key)
            } else {
                mutableMap[key] = rows.toCacheEntry()
            }
            groupMembersCache.postValue(mutableMap)
            bumpCacheVersion()
        }
    }

    private fun List<GroupMemberAvatarInfo>.toCacheEntry() = GroupAvatarCacheEntry(
        orderedMemberBytes = take(3).map { it.bytesContactIdentity },
        // members + pending members + yourself, matching the count shown on the group screen
        totalCount = size + 1,
    )
}

data class GroupAvatarCacheEntry(
    val orderedMemberBytes: List<ByteArray>,
    val totalCount: Int,
)
