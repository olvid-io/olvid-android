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
package io.olvid.messenger.group

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.messenger.App

class OwnedGroupDetailsViewModel : ViewModel() {
    private var oldDetails: JsonGroupDetails? = null
    private var oldPhotoUrl: String? = null
    private var oldPersonalNote: String? = null
    private var groupV2 = false
    lateinit var bytesOwnedIdentity: ByteArray
    private var bytesGroupOwnerAndUidOrIdentifier: ByteArray? = null

    private var groupName: String? = null
    var groupDescription: String? = null
    var personalNote: String? = null
    private var absolutePhotoUrl // absolute path photoUrl
            : String? = null
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
                absolutePhotoUrl
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

    fun getGroupName(): String? {
        return groupName
    }

    fun setGroupName(groupName: String?) {
        this.groupName = groupName
        checkValid()
    }

    fun getAbsolutePhotoUrl(): String? {
        return absolutePhotoUrl
    }

    fun setAbsolutePhotoUrl(absolutePhotoUrl: String?) {
        if (absolutePhotoUrl != null) {
            initialViewContent.postValue(
                InitialViewContent(
                    bytesGroupOwnerAndUidOrIdentifier,
                    absolutePhotoUrl
                )
            )
        } else if (this.absolutePhotoUrl != null) {
            initialViewContent.postValue(
                InitialViewContent(
                    bytesGroupOwnerAndUidOrIdentifier,
                    null
                )
            )
        }
        this.absolutePhotoUrl = absolutePhotoUrl
    }

    private fun checkValid() {
        val newValid = groupV2 || groupName?.trim().isNullOrEmpty().not()
        val oldValid = valid.value
        if (oldValid == null || oldValid xor newValid) {
            valid.postValue(newValid)
        }
    }

    fun detailsChanged(): Boolean {
        return jsonGroupDetails != oldDetails
    }

    fun photoChanged(): Boolean {
        return absolutePhotoUrl != App.absolutePathFromRelative(oldPhotoUrl)
    }

    fun personalNoteChanged(): Boolean {
        return personalNote != oldPersonalNote
    }

    val jsonGroupDetails: JsonGroupDetails
        get() = JsonGroupDetails(
            if (groupName == null) null else groupName!!.trim(),
            groupDescription
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