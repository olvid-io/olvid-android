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

package io.olvid.messenger.databases

import androidx.lifecycle.MutableLiveData
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Group2PendingMember
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.settings.SettingsActivity


// Contact names and info caches (for main thread access)
object ContactCacheSingleton {
    private val contactNamesCache: MutableLiveData<MutableMap<BytesKey, ContactCacheDisplayNames>> = MutableLiveData() // the first element of the pair is the full display name, the second the first name (or custom name for both, if set)
    private val contactHuesCache: MutableLiveData<MutableMap<BytesKey, Int>> = MutableLiveData()
    private val contactPhotoUrlsCache: MutableLiveData<MutableMap<BytesKey, String>> = MutableLiveData()
    private val contactInfoCache: MutableLiveData<MutableMap<BytesKey, ContactCacheInfo>> = MutableLiveData()


    fun reloadCachedDisplayNamesAndHues() {
        reloadCachedDisplayNamesAndHues(AppSingleton.getCurrentIdentityLiveData().value)
    }

    fun reloadCachedDisplayNamesAndHues(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) {
            contactNamesCache.postValue(mutableMapOf())
            contactHuesCache.postValue(mutableMapOf())
            contactPhotoUrlsCache.postValue(mutableMapOf())
            contactInfoCache.postValue(mutableMapOf())
            return
        }

        val contactNamesHashMap = mutableMapOf<BytesKey, ContactCacheDisplayNames>()
        val contactHuesHashMap = mutableMapOf<BytesKey, Int>()
        val contactPhotoUrlsHashMap = mutableMapOf<BytesKey, String>()
        val contactCacheInfoHashMap = mutableMapOf<BytesKey, ContactCacheInfo>()

        getInstance().contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity).forEach { contact ->
            val key = BytesKey(contact.bytesContactIdentity)
            contactNamesHashMap[key] =
                ContactCacheDisplayNames.of(
                    displayName = contact.displayName,
                    customDisplayName = contact.customDisplayName,
                    firstName = contact.firstName,
                    identityDetails = contact.getIdentityDetails(),
                )
            contact.customNameHue?.let {
                contactHuesHashMap[key] = it
            }
            if (contact.getCustomPhotoUrl() != null) {
                contactPhotoUrlsHashMap[key] = contact.getCustomPhotoUrl()
            }
            contactCacheInfoHashMap[key] = ContactCacheInfo(
                contact.keycloakManaged,
                contact.active,
                contact.oneToOne,
                contact.recentlyOnline,
                contact.trustLevel
            )
        }

        val ownKey = BytesKey(ownedIdentity.bytesOwnedIdentity)
        contactNamesHashMap[ownKey] = ContactCacheDisplayNames.of(
            displayName = ownedIdentity.displayName,
            customDisplayName = ownedIdentity.customDisplayName,
            firstName = null,
            identityDetails = ownedIdentity.getIdentityDetails(),
            isOwnedIdentity = true
        )
        ownedIdentity.photoUrl?.let {
            contactPhotoUrlsHashMap[ownKey] = it
        }
        contactCacheInfoHashMap[ownKey] =
            ContactCacheInfo(
                keycloakManaged = ownedIdentity.keycloakManaged,
                active = ownedIdentity.active,
                oneToOne = true,
                recentlyOnline = true,
                trustLevel = -1
            )

        getInstance().group2PendingMemberDao().getAll(ownedIdentity.bytesOwnedIdentity).forEach { pendingMember ->
            val key = BytesKey(pendingMember.bytesContactIdentity)
            if (!contactNamesHashMap.containsKey(key)) {
                contactNamesHashMap[key] = ContactCacheDisplayNames.of(
                    displayName = pendingMember.displayName,
                    customDisplayName = null,
                    firstName = pendingMember.getNonNullFirstName(),
                    identityDetails = kotlin.runCatching { AppSingleton.getJsonObjectMapper().readValue(pendingMember.identityDetails, JsonIdentityDetails::class.java) }.getOrNull(),
                )
            }
        }

        contactNamesCache.postValue(contactNamesHashMap)
        contactHuesCache.postValue(contactHuesHashMap)
        contactPhotoUrlsCache.postValue(contactPhotoUrlsHashMap)
        contactInfoCache.postValue(contactCacheInfoHashMap)
    }

    fun getContactCustomDisplayName(bytesContactIdentity: ByteArray?): String? {
        return contactNamesCache.value?.get(BytesKey(bytesContactIdentity))?.displayName
    }

    fun getContactFirstName(bytesContactIdentity: ByteArray?): String? {
        return contactNamesCache.value?.get(BytesKey(bytesContactIdentity))?.firstName
    }

    fun getContactDetailsFirstLine(bytesContactIdentity: ByteArray?): String? {
        return contactNamesCache.value?.get(BytesKey(bytesContactIdentity))?.detailsFirstLine
    }

    fun getContactDetailsSecondLine(bytesContactIdentity: ByteArray?): String? {
        return contactNamesCache.value?.get(BytesKey(bytesContactIdentity))?.detailsSecondLine
    }

    fun getContactDetailsSingleLine(bytesContactIdentity: ByteArray?): String? {
        return contactNamesCache.value?.get(BytesKey(bytesContactIdentity))?.detailsSingleLine
    }

    fun getContactCustomHue(bytesContactIdentity: ByteArray?): Int? {
        return contactHuesCache.value?.get(BytesKey(bytesContactIdentity))
    }

    fun getContactPhotoUrl(bytesContactIdentity: ByteArray?): String? {
        return contactPhotoUrlsCache.value?.get(BytesKey(bytesContactIdentity))
    }

    fun getContactCacheInfo(bytesContactIdentity: ByteArray?): ContactCacheInfo? {
        return contactInfoCache.value?.get(BytesKey(bytesContactIdentity))
    }

    fun updateCachedCustomDisplayName(
        contact: Contact
    ) {
        contactNamesCache.value?.let { mutableMap ->
            mutableMap[BytesKey(contact.bytesContactIdentity)] = ContactCacheDisplayNames.of(
                displayName = contact.displayName,
                customDisplayName = contact.customDisplayName,
                firstName = contact.firstName,
                identityDetails = contact.getIdentityDetails(),
            )
            contactNamesCache.postValue(mutableMap)
        }
    }

    fun updateCachedCustomDisplayName(
        pendingMember: Group2PendingMember,
    ) {
        contactNamesCache.value?.let { mutableMap ->
            mutableMap[BytesKey(pendingMember.bytesContactIdentity)] = ContactCacheDisplayNames.of(
                displayName = pendingMember.displayName,
                customDisplayName = null,
                firstName = pendingMember.getNonNullFirstName(),
                identityDetails = kotlin.runCatching { AppSingleton.getJsonObjectMapper().readValue(pendingMember.identityDetails, JsonIdentityDetails::class.java) }.getOrNull(),
            )
            contactNamesCache.postValue(mutableMap)
        }
    }

    fun updateCachedCustomHue(bytesContactIdentity: ByteArray, customHue: Int?) {
        contactHuesCache.value?.let { mutableMap ->
            if (customHue == null) {
                mutableMap.remove(BytesKey(bytesContactIdentity))
            } else {
                mutableMap[BytesKey(bytesContactIdentity)] = customHue
            }
            contactHuesCache.postValue(mutableMap)
        }
    }

    fun updateCachedPhotoUrl(bytesContactIdentity: ByteArray, photoUrl: String?) {
        contactPhotoUrlsCache.value?.let { mutableMap ->
            if (photoUrl == null) {
                mutableMap.remove(BytesKey(bytesContactIdentity))
            } else {
                mutableMap[BytesKey(bytesContactIdentity)] = photoUrl
            }
            contactPhotoUrlsCache.postValue(mutableMap)
        }
    }

    fun updateContactCachedInfo(contact: Contact) {
        contactInfoCache.value?.let { mutableMap ->
            mutableMap[BytesKey(contact.bytesContactIdentity)] = ContactCacheInfo(
                keycloakManaged = contact.keycloakManaged,
                active = contact.active,
                oneToOne = contact.oneToOne,
                recentlyOnline = contact.recentlyOnline,
                trustLevel = contact.trustLevel
            )
            contactInfoCache.postValue(mutableMap)
        }
    }

    fun updateContactCachedInfo(ownedIdentity: OwnedIdentity) {
        contactInfoCache.value?.let { mutableMap ->
            mutableMap[BytesKey(ownedIdentity.bytesOwnedIdentity)] = ContactCacheInfo(
                keycloakManaged = ownedIdentity.keycloakManaged,
                active = ownedIdentity.active,
                oneToOne = true,
                recentlyOnline = true,
                trustLevel = 0
            )
            contactInfoCache.postValue(mutableMap)
        }
    }

    fun updateCacheContactDeleted(bytesContactIdentity: ByteArray) {
        val key = BytesKey(bytesContactIdentity)
        contactNamesCache.value?.let {
            it.remove(key)
            contactNamesCache.postValue(it)
        }
        contactHuesCache.value?.let {
            it.remove(key)
            contactHuesCache.postValue(it)
        }
        contactPhotoUrlsCache.value?.let {
            it.remove(key)
            contactPhotoUrlsCache.postValue(it)
        }
        contactInfoCache.value?.let {
            it.remove(key)
            contactInfoCache.postValue(it)
        }
    }
}

data class ContactCacheInfo(
    val keycloakManaged: Boolean,
    val active: Boolean,
    val oneToOne: Boolean,
    val recentlyOnline: Boolean,
    val trustLevel: Int,
)

data class ContactCacheDisplayNames(
    val displayName: String,
    val firstName: String,
    val detailsFirstLine: String,
    val detailsSecondLine: String?,
    val detailsSingleLine: String,
) {
    companion object {
        fun of(
            displayName: String,
            customDisplayName: String?,
            firstName: String?,
            identityDetails: JsonIdentityDetails?,
            isOwnedIdentity: Boolean = false,
        ) : ContactCacheDisplayNames {
            return identityDetails?.let {
                val firstAndLast = it.formatFirstAndLastName(SettingsActivity.contactDisplayNameFormat, SettingsActivity.uppercaseLastName)
                ContactCacheDisplayNames(
                    displayName = if (isOwnedIdentity) App.getContext().getString(R.string.text_you) else customDisplayName ?: displayName,
                    firstName = if (isOwnedIdentity) App.getContext().getString(R.string.text_you) else customDisplayName ?: firstName ?: displayName,
                    detailsFirstLine = ((if (isOwnedIdentity) null else customDisplayName) ?: firstAndLast)
                            + if (isOwnedIdentity) " (${App.getContext().getString(R.string.text_you)})" else "",
                    detailsSecondLine = if (isOwnedIdentity || customDisplayName == null)
                        it.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                    else
                        it.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.uppercaseLastName),
                    detailsSingleLine = if (isOwnedIdentity)
                        "$firstAndLast (${App.getContext().getString(R.string.text_you)})"
                    else if (customDisplayName != null)
                        "$customDisplayName ($firstAndLast)"
                    else
                        firstAndLast
                )
            } ?: ContactCacheDisplayNames(
                displayName = if (isOwnedIdentity) App.getContext().getString(R.string.text_you) else customDisplayName ?: displayName,
                firstName = if (isOwnedIdentity) App.getContext().getString(R.string.text_you) else customDisplayName ?: firstName ?: displayName,
                detailsFirstLine = (customDisplayName ?: displayName)
                        + if (isOwnedIdentity) " (${App.getContext().getString(R.string.text_you)})" else "",
                detailsSecondLine = if (customDisplayName == null)
                    null
                else
                    displayName,
                detailsSingleLine = if (isOwnedIdentity)
                    "$displayName (${App.getContext().getString(R.string.text_you)})"
                else if (customDisplayName != null)
                    "$customDisplayName ($displayName)"
                else
                    displayName
            )
        }
    }
}