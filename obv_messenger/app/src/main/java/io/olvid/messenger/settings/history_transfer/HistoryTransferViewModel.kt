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

package io.olvid.messenger.settings.history_transfer

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.databases.entity.OwnedIdentity


class HistoryTransferViewModel: ViewModel() {
    val ownedIdentityListLiveData = object : MediatorLiveData<List<OwnedIdentity>>() {
        var nonHidden: List<OwnedIdentity> = emptyList()
        var current: OwnedIdentity? = null

        init {
            addSource(AppDatabase.getInstance().ownedIdentityDao().allNotHiddenLiveData) { nonHidden ->
                this.nonHidden = nonHidden.sortedBy { it.displayName }
                update()
            }
            addSource(AppSingleton.getCurrentIdentityLiveData()) { current ->
                this.current = current
                update()
            }
        }

        fun update() {
            postValue(
                nonHidden.toMutableList().apply {
                    current?.takeIf { it.isHidden }?.let {
                        // if current identity is hidden, add it as first identity
                        add(0, it)
                    }
                }
            )
        }
    }

    var importMode: MutableState<Boolean> = mutableStateOf(false)
    var selectedOwnedIdentity: MutableState<OwnedIdentity?> = mutableStateOf(null)
    var selectedWebRTCTargetOwnedDevice: MutableState<OwnedDevice?> = mutableStateOf(null)
    var selectedOwnedIdentityLiveData = snapshotFlow { selectedOwnedIdentity.value?.bytesOwnedIdentity }.asLiveData()
    val transferMethod: MutableState<TransferMethod> = mutableStateOf(TransferMethod.WEBRTC)
    val pickingFile: MutableState<Boolean> = mutableStateOf(false)
    val selectedZipUri: MutableState<Uri?> = mutableStateOf(null)
    val exportScope: MutableState<ExportScope> = mutableStateOf(ExportScope.EVERYTHING)
    var zipExportPassword: String? = null

    val discussionCountLiveData = selectedOwnedIdentityLiveData.switchMap { bytesOwnedIdentity ->
        return@switchMap bytesOwnedIdentity?.let {
            AppDatabase.getInstance().discussionDao().countForOwnedIdentity(bytesOwnedIdentity)
        } ?: MutableLiveData(-1)
    }

    val messageCountLiveData = selectedOwnedIdentityLiveData.switchMap { bytesOwnedIdentity ->
        return@switchMap bytesOwnedIdentity?.let {
            AppDatabase.getInstance().messageDao().countAllTransferableForOwnedIdentity(bytesOwnedIdentity)
        } ?: MutableLiveData(-1)
    }

    val sha256sMapLiveData: LiveData<Map<BytesKey, Long>?> = selectedOwnedIdentityLiveData.switchMap { bytesOwnedIdentity ->
        return@switchMap bytesOwnedIdentity?.let {
            AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAllTransferableForOwnedIdentity(bytesOwnedIdentity).map { fyleAndStatuses ->
                fyleAndStatuses.mapNotNull { fyleAndStatus ->
                    fyleAndStatus.fyle.sha256?.let {
                        BytesKey(it) to fyleAndStatus.fyleMessageJoinWithStatus.size
                    }
                }.toMap() // we convert the list to a map to get rid of duplicate sha256
            }
        } ?: MutableLiveData<Map<BytesKey, Long>?>(null)
    }

    val deviceListLiveData: LiveData<List<OwnedDevice>> = selectedOwnedIdentityLiveData.switchMap { bytesOwnedIdentity ->
        return@switchMap bytesOwnedIdentity?.let {
            AppDatabase.getInstance().ownedDeviceDao().getAllSorted(it)
        } ?: MutableLiveData<List<OwnedDevice>>(null)
    }
}

enum class TransferMethod {
    ZIP,
    WEBRTC,
}

enum class ExportScope {
    MESSAGES_ONLY,
    EVERYTHING,
}