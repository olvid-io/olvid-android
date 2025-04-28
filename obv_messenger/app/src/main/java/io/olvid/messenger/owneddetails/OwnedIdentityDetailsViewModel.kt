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
package io.olvid.messenger.owneddetails

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.PUBLISH
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.SAVE

class OwnedIdentityDetailsViewModel : ViewModel() {
    private var oldDetails: JsonIdentityDetailsWithVersionAndPhoto? = null
    private var oldNickname: String? = null
    private var oldProfileHidden = false
    private var _bytesOwnedIdentity: ByteArray? = null
    var bytesOwnedIdentity: ByteArray?
        get() = _bytesOwnedIdentity
        set(value) {
            initialViewContent.postValue(
                InitialViewContent(
                    value,
                    StringUtils.getInitial(
                        firstName?.trim().orEmpty() + lastName?.trim().orEmpty()
                    ),
                    absolutePhotoUrl
                )
            )
            _bytesOwnedIdentity = value
        }

    var firstName: String? = null
        set(value) {
            field = value
            if (absolutePhotoUrl == null && nickname?.trim().isNullOrEmpty()) {
                if (firstName.isNullOrEmpty().not()) {
                    val newInitial = StringUtils.getInitial(firstName?.trim())
                    val oldInitial = this.firstName?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { StringUtils.getInitial(it) }
                    if (newInitial != oldInitial) {
                        initialViewContent.postValue(
                            InitialViewContent(
                                _bytesOwnedIdentity,
                                newInitial,
                                null
                            )
                        )
                    }
                } else if (this.firstName?.trim().isNullOrEmpty().not()) {
                    if (lastName != null && lastName!!.trim().isNotEmpty()) {
                        initialViewContent.postValue(
                            InitialViewContent(
                                _bytesOwnedIdentity,
                                StringUtils.getInitial(
                                    lastName!!.trim()
                                ),
                                null
                            )
                        )
                    } else {
                        initialViewContent.postValue(
                            InitialViewContent(
                                _bytesOwnedIdentity,
                                " ",
                                null
                            )
                        )
                    }
                }
            }
            checkValid()
        }
    var lastName: String? = null
        set(value) {
            field = value
            if (absolutePhotoUrl == null && (firstName == null || firstName!!.trim()
                    .isEmpty())
            ) {
                if (lastName?.trim().isNullOrEmpty().not()) {
                    val newInitial = StringUtils.getInitial(lastName?.trim())
                    val oldInitial = if ((this.lastName == null || this.lastName!!.trim()
                            .isEmpty())
                    ) null else StringUtils.getInitial(
                        this.lastName!!.trim()
                    )
                    if (newInitial != oldInitial) {
                        initialViewContent.postValue(
                            InitialViewContent(
                                _bytesOwnedIdentity,
                                newInitial,
                                null
                            )
                        )
                    }
                } else if (this.lastName?.trim().isNullOrEmpty().not()) {
                    initialViewContent.postValue(InitialViewContent(_bytesOwnedIdentity, " ", null))
                }
            }
            checkValid()
        }
    var company: String? = null
        set(value) {
            field = value
            checkValid()
        }
    var position: String? = null
        set(value) {
            field = value
            checkValid()
        }
    var nickname: String? = null
        get() = nullOrTrim(field)
        set(value) { field = value
            if (this.absolutePhotoUrl == null) {
                val name =
                    nickname?.trim().orEmpty() + firstName?.trim().orEmpty() + lastName?.trim()
                        .orEmpty()
                if (name.isNotEmpty()) {
                    initialViewContent.postValue(
                        InitialViewContent(
                            _bytesOwnedIdentity,
                            StringUtils.getInitial(name),
                            null
                        )
                    )
                } else {
                    initialViewContent.postValue(InitialViewContent(_bytesOwnedIdentity, " ", null))
                }
            }
        }
    var isProfileHidden = false
        set(value) {
            field = value
            if (!isProfileHidden) {
                password = null
                salt = null
            }
        }

    var password: ByteArray? = null
        private set
    var salt: ByteArray? = null
        private set

    var absolutePhotoUrl: String? = null // absolute path
        set(value) {
            field = value
            if (absolutePhotoUrl != null) {
                initialViewContent.postValue(
                    InitialViewContent(
                        _bytesOwnedIdentity,
                        null,
                        absolutePhotoUrl
                    )
                )
            } else if (this.absolutePhotoUrl != null) {
                val name =
                    nickname?.trim().orEmpty() + firstName?.trim().orEmpty() + lastName?.trim()
                        .orEmpty()
                if (name.isNotEmpty()) {
                    initialViewContent.postValue(
                        InitialViewContent(
                            _bytesOwnedIdentity,
                            StringUtils.getInitial(name),
                            null
                        )
                    )
                } else {
                    initialViewContent.postValue(InitialViewContent(_bytesOwnedIdentity, " ", null))
                }
            }
            checkValid()
        }

    var takePictureUri: Uri? = null
    var pictureLocked: Boolean = false
    var detailsLocked: Boolean = false
    var isIdentityInactive: Boolean = false

    val valid = MutableLiveData(INVALID)
    val initialViewContent = MutableLiveData<InitialViewContent?>(null)

    enum class ValidStatus {
        INVALID,
        PUBLISH,
        SAVE,
    }

    fun setOwnedIdentityDetails(
        identityDetails: JsonIdentityDetailsWithVersionAndPhoto,
        nickname: String?,
        profileHidden: Boolean
    ) {
        oldDetails = identityDetails
        oldNickname = nickname
        oldProfileHidden = profileHidden

        absolutePhotoUrl = App.absolutePathFromRelative(identityDetails.photoUrl)
        firstName = identityDetails.identityDetails.firstName
        lastName = identityDetails.identityDetails.lastName
        company = identityDetails.identityDetails.company
        position = identityDetails.identityDetails.position
        this.nickname = nickname
        this.isProfileHidden = profileHidden
        checkValid()
    }

    fun setPasswordAndSalt(password: ByteArray?, salt: ByteArray?) {
        this.password = password
        this.salt = salt
    }

    private fun checkValid() {
        val oldValid = valid.value
        val newValid: ValidStatus
        val valid =
            firstName?.trim().isNullOrEmpty().not() || lastName?.trim().isNullOrEmpty().not()
        newValid = if (valid) {
            if (detailsChanged() || photoChanged()) {
                PUBLISH
            } else {
                SAVE
            }
        } else {
            INVALID
        }
        if (oldValid == null || (oldValid != newValid)) {
            this.valid.postValue(newValid)
        }
    }

    fun detailsChanged(): Boolean {
        if (detailsLocked) {
            return false
        }
        if (oldDetails == null) {
            return true
        }
        return jsonIdentityDetails != oldDetails!!.identityDetails
    }

    fun photoChanged(): Boolean {
        return if (absolutePhotoUrl == null) {
            oldDetails != null && oldDetails!!.photoUrl != null
        } else {
            oldDetails == null || absolutePhotoUrl != App.absolutePathFromRelative(
                oldDetails!!.photoUrl
            )
        }
    }

    fun nicknameChanged(): Boolean {
        return nullOrTrim(oldNickname) != nullOrTrim(nickname)
    }

    // this method returns true if the profile switched from hidden to not hidden (or the other way), but also if the profile remains hidden and the password was changed
    fun profileHiddenChanged(): Boolean {
        return (oldProfileHidden xor isProfileHidden) || (password != null)
    }

    val jsonIdentityDetails: JsonIdentityDetails
        get() = JsonIdentityDetails(firstName, lastName, company, position)

    class InitialViewContent(
        bytesOwnedIdentity: ByteArray?,
        initial: String?,
        absolutePhotoUrl: String?
    ) {
        val bytesOwnedIdentity: ByteArray
        val initial: String?
        val absolutePhotoUrl: String?

        init {
            if (bytesOwnedIdentity == null) {
                this.bytesOwnedIdentity = ByteArray(0)
            } else {
                this.bytesOwnedIdentity = bytesOwnedIdentity
            }
            this.initial = initial
            this.absolutePhotoUrl = absolutePhotoUrl
        }
    }

    companion object {
        private fun nullOrTrim(`in`: String?): String? {
            if (`in` == null) {
                return null
            }
            val out = `in`.trim()
            if (out.isEmpty()) {
                return null
            }
            return out
        }
    }
}
