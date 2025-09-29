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
package io.olvid.messenger.group

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2ChangeSet
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.tasks.UpdateGroupV2CustomNameAndPhotoTask

class OwnedGroupDetailsViewModel : ViewModel() {
    private var oldDetails: JsonGroupDetails? = null
    private var oldPhotoUrl: String? = null
    private var oldPersonalNote: String? = null
    private var groupV2 = false
    lateinit var bytesOwnedIdentity: ByteArray
    private var bytesGroupOwnerAndUidOrIdentifier: ByteArray? = null
    var initialized = false

    var cloning = mutableStateOf(false)
    var groupName = mutableStateOf<String?>(null)
    var groupDescription: String? by mutableStateOf(null)
    var personalNote: String? by mutableStateOf(null)
    // absolute path photoUrl
    private var absPhotoUrl: String? by mutableStateOf(null)

    var takePictureUri: Uri? = null
    val valid = MutableLiveData(false)
    private val initialViewContent = MutableLiveData<InitialViewContent?>(null)

    fun getBytesGroupOwnerAndUidOrIdentifier() : ByteArray? {
        return bytesGroupOwnerAndUidOrIdentifier
    }
    fun setBytesGroupOwnerAndUidOrIdentifier(bytesGroupOwnerAndUidOrIdentifier: ByteArray) {
        this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier
        initialViewContent.postValue(
            InitialViewContent(
                bytesGroupOwnerAndUidOrIdentifier,
                absPhotoUrl
            )
        )
    }

    fun isGroupV2(): Boolean {
        return groupV2
    }

    fun setGroupV2(groupV2: Boolean) {
        this.groupV2 = groupV2
        checkValid()
    }

    fun setOwnedGroupDetails(
        groupDetails: JsonGroupDetailsWithVersionAndPhoto,
        personalNote: String?
    ) {
        if (initialized) return
        initialized = true
        oldDetails = groupDetails.groupDetails
        oldPhotoUrl = groupDetails.photoUrl
        oldPersonalNote = personalNote
        setAbsolutePhotoUrl(App.absolutePathFromRelative(groupDetails.photoUrl))
        setGroupName(groupDetails.groupDetails.name)
        groupDescription = groupDetails.groupDetails.description
        this.personalNote = personalNote
        checkValid()
    }

    fun setOwnedGroupDetailsV2(
        groupDetails: JsonGroupDetails,
        photoUrl: String?,
        personalNote: String?
    ) {
        if (initialized) return
        initialized = true
        oldDetails = groupDetails
        oldPhotoUrl = photoUrl
        oldPersonalNote = personalNote
        setAbsolutePhotoUrl(App.absolutePathFromRelative(photoUrl))
        setGroupName(groupDetails.name)
        groupDescription = groupDetails.description
        this.personalNote = personalNote
        checkValid()
    }

    fun getValid(): LiveData<Boolean> {
        return valid
    }

    fun getInitialViewContent(): LiveData<InitialViewContent?> {
        return initialViewContent
    }

    fun setGroupName(groupName: String?) {
        this.groupName.value = groupName
        checkValid()
    }

    fun getAbsolutePhotoUrl(): String? {
        return absPhotoUrl
    }

    fun setAbsolutePhotoUrl(absolutePhotoUrl: String?) {
        if (absolutePhotoUrl != null) {
            initialViewContent.postValue(
                InitialViewContent(
                    bytesGroupOwnerAndUidOrIdentifier,
                    absolutePhotoUrl
                )
            )
        } else if (this.absPhotoUrl != null) {
            initialViewContent.postValue(
                InitialViewContent(
                    bytesGroupOwnerAndUidOrIdentifier,
                    null
                )
            )
        }
        this.absPhotoUrl = absolutePhotoUrl
    }

    fun publish() {
        App.runThread {
            var changed = false

            val obvChangeSet = ObvGroupV2ChangeSet()
            if (detailsChanged()) {
                try {
                    obvChangeSet.updatedSerializedGroupDetails =
                        AppSingleton.getJsonObjectMapper().writeValueAsString(
                            jsonGroupDetails
                        )
                    changed = true
                } catch (_: Exception) { }
            }
            if (photoChanged()) {
                obvChangeSet.updatedPhotoUrl = getAbsolutePhotoUrl().orEmpty()
                changed = true
            }
            if (changed) {
                runCatching {
                    AppSingleton.getEngine().initiateGroupV2Update(
                        bytesOwnedIdentity,
                        getBytesGroupOwnerAndUidOrIdentifier(),
                        obvChangeSet
                    )
                    UpdateGroupV2CustomNameAndPhotoTask(
                        bytesOwnedIdentity,
                        getBytesGroupOwnerAndUidOrIdentifier() ?: ByteArray(0),
                        null,
                        null,
                        personalNote?.trim().takeUnless { it.isNullOrEmpty() },
                        false
                    ).run()
                }.onFailure {
                    it.printStackTrace()
                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                }
            } else if (personalNoteChanged()) {
                UpdateGroupV2CustomNameAndPhotoTask(
                    bytesOwnedIdentity,
                    getBytesGroupOwnerAndUidOrIdentifier() ?: ByteArray(0),
                    null,
                    null,
                    personalNote?.trim().takeUnless { it.isNullOrEmpty() },
                    false
                ).run()
            }
        }
    }

    private fun checkValid() {
        val newValid = groupV2 || groupName.value?.trim().isNullOrEmpty().not()
        val oldValid = valid.value
        if (oldValid == null || oldValid xor newValid) {
            valid.postValue(newValid)
        }
    }

    fun detailsChanged(): Boolean {
        return jsonGroupDetails != oldDetails
    }

    fun photoChanged(): Boolean {
        return absPhotoUrl != App.absolutePathFromRelative(oldPhotoUrl)
    }

    fun personalNoteChanged(): Boolean {
        return personalNote?.trim().takeUnless { it.isNullOrEmpty() } != oldPersonalNote
    }

    val jsonGroupDetails: JsonGroupDetails
        get() = JsonGroupDetails(
            groupName.value?.trim(),
            groupDescription?.trim()
        )

    class InitialViewContent(bytesGroupOwnerAndUid: ByteArray?, absolutePhotoUrl: String?) {
        val bytesGroupOwnerAndUid: ByteArray
        val absolutePhotoUrl: String?

        init {
            if (bytesGroupOwnerAndUid == null) {
                this.bytesGroupOwnerAndUid = ByteArray(0)
            } else {
                this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid
            }
            this.absolutePhotoUrl = absolutePhotoUrl
        }
    }
}