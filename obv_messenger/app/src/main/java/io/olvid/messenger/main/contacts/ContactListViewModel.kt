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
package io.olvid.messenger.main.contacts

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.util.Pair
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.StringUtils2
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.util.regex.Pattern

enum class ContactListPage(val labelResId: Int) {
    CONTACTS(R.string.contact_list_tab_contact),
    OTHERS(R.string.contact_list_tab_others),
    DIRECTORY(R.string.contact_list_tab_directory)
}

class ContactListViewModel : ViewModel() {
    private var unfilteredContacts: List<Contact> = emptyList()
    private var unfilteredNotOneToOneContacts: List<Contact> = emptyList()
    internal val filteredContacts = MutableLiveData<List<ContactOrKeycloakDetails>>()
    private var _filter by mutableStateOf<String?>(null)
    var filterPatterns: MutableList<Pattern>? = null
    var keycloakSearchInProgress by mutableStateOf(false)
    private var keycloakSearchBytesOwnedIdentity: ByteArray? = null
    private var keycloakSearchResultsFilter: String? = null
    private var keycloakSearchResults: List<JsonKeycloakUserDetails>? = null
    private var keycloakSearchAdditionalResults = 0

    var keycloakManaged = mutableStateOf(false)
    var selectedContacts = mutableStateListOf<Contact>() // TODO: change this to a BytesKey -> Contact map for better performance
    val filteredPages = mutableStateListOf<ContactListPage>()
    var contactInvitation: ContactOrKeycloakDetails? by mutableStateOf(null)


    fun setUnfilteredContacts(unfilteredContacts: List<Contact>?) {
        this.unfilteredContacts = unfilteredContacts.orEmpty()
        setFilter(_filter)
    }

    fun setUnfilteredNotOneToOneContacts(unfilteredNotOneToOneContacts: List<Contact>?) {
        this.unfilteredNotOneToOneContacts = unfilteredNotOneToOneContacts.orEmpty()
        setFilter(_filter)
    }

    private fun performKeycloakSearch(ownedIdentity: OwnedIdentity, filter: String) {
        keycloakSearchInProgress = true
        Handler(Looper.getMainLooper()).postDelayed({
            searchKeycloak(
                ownedIdentity.bytesOwnedIdentity,
                filter
            )
        }, KEYCLOAK_SEARCH_DELAY_MILLIS)
    }

    fun refreshKeycloakSearch() {
        val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
        if (ownedIdentity != null && !keycloakSearchInProgress && ownedIdentity.bytesOwnedIdentity.contentEquals(keycloakSearchBytesOwnedIdentity)) {
            _filter?.let {
                performKeycloakSearch(ownedIdentity, it)
            }
        }
    }

    fun setFilter(filter: String?) {
        this._filter = filter
        if (filter == null) {
            filterPatterns = null
        } else {
            val parts =
                filter.trim().split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            filterPatterns = ArrayList(parts.size)
            for (part in parts) {
                if (part.isNotEmpty()) {
                    filterPatterns?.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))))
                }
            }
            val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
            if (filterPatterns != null && ownedIdentity != null && keycloakManaged.value) {
                if (filter != keycloakSearchResultsFilter || !ownedIdentity.bytesOwnedIdentity.contentEquals(keycloakSearchBytesOwnedIdentity)) {
                    performKeycloakSearch(ownedIdentity, filter)
                }
            }
        }
        viewModelScope.launch {
            if (filterPatterns == null) {
                val list: MutableList<ContactOrKeycloakDetails> = ArrayList()
                list.addAll(unfilteredContacts.map { ContactOrKeycloakDetails(it) })
                list.addAll(unfilteredNotOneToOneContacts.map { ContactOrKeycloakDetails(it) })
                filteredContacts.postValue(list)
            } else {
                val list: MutableList<ContactOrKeycloakDetails> = ArrayList()

                for (contact in unfilteredContacts) {
                    var matches = true
                    for (pattern in filterPatterns!!) {
                        val matcher = pattern.matcher(contact.fullSearchDisplayName)
                        if (!matcher.find()) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        list.add(ContactOrKeycloakDetails(contact))
                    }
                }

                for (contact in unfilteredNotOneToOneContacts) {
                    var matches = true
                    for (pattern in filterPatterns!!) {
                        val matcher = pattern.matcher(contact.fullSearchDisplayName)
                        if (!matcher.find()) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        list.add(ContactOrKeycloakDetails(contact))
                    }
                }
                keycloakSearchResults?.let {
                    for (keycloakUserDetails in it) {
                        //  filters out our ownedIdentity
                        if (keycloakUserDetails.identity != null && !(keycloakUserDetails.identity contentEquals keycloakSearchBytesOwnedIdentity)) {
                            list.add(ContactOrKeycloakDetails(keycloakUserDetails))
                        }
                    }
                    if (keycloakSearchAdditionalResults > 0) {
                        list.add(ContactOrKeycloakDetails(keycloakSearchAdditionalResults))
                    }
                }
                filteredContacts.postValue(list)
            }
        }
    }

    fun getFilter(): String? {
        return _filter
    }

    fun isFiltering(): Boolean {
        return _filter?.trim()?.isNotEmpty() == true
    }

    private fun searchKeycloak(bytesOwnedIdentity: ByteArray, filter: String) {
        val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
        if (ownedIdentity == null || !bytesOwnedIdentity.contentEquals(ownedIdentity.bytesOwnedIdentity)
            || filter != this._filter
        ) {
            // something changed during the KEYCLOAK_SEARCH_DELAY_MILLIS --> abort
            return
        }
        KeycloakManager.search(
            bytesOwnedIdentity,
            filter,
            object : KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>?> {
                override fun success(result: Pair<List<JsonKeycloakUserDetails>, Int>?) {
                    val reOwnedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                    if (this@ContactListViewModel._filter != filter || reOwnedIdentity == null || !reOwnedIdentity.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        // something changed while we were searching --> abort
                        return
                    }
                    keycloakSearchInProgress = false
                    keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity
                    keycloakSearchResultsFilter = filter
                    keycloakSearchResults = result?.first
                    keycloakSearchAdditionalResults = if (result?.second == null) {
                        0
                    } else if (result.first == null) {
                        result.second!!
                    } else {
                        result.second!! - result.first!!.size
                    }

                    // re-filter to add keycloak search results
                    setFilter(filter)
                }

                override fun failed(rfc: Int) {
                    val reOwnedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                    if (this@ContactListViewModel._filter != filter || reOwnedIdentity == null || !reOwnedIdentity.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        return
                    }
                    keycloakSearchInProgress = false
                    keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity
                    keycloakSearchResultsFilter = filter
                    keycloakSearchResults = null
                    keycloakSearchAdditionalResults = 0

                    // re-filter anyway to force refresh and remove the "searching" spinner
                    setFilter(filter)
                }
            })
    }

    enum class ContactType {
        CONTACT, KEYCLOAK, KEYCLOAK_MORE_RESULTS
    }

    class ContactOrKeycloakDetails {
        val contactType: ContactType
        val contact: Contact?
        val keycloakUserDetails: JsonKeycloakUserDetails?
        val additionalSearchResults: Int?

        constructor(contact: Contact) {
            contactType = CONTACT
            this.contact = contact
            keycloakUserDetails = null
            additionalSearchResults = null
        }

        constructor(keycloakUserDetails: JsonKeycloakUserDetails) {
            contactType = KEYCLOAK
            contact = null
            this.keycloakUserDetails = keycloakUserDetails
            additionalSearchResults = null
        }

        constructor(additionalSearchResults: Int) {
            contactType = KEYCLOAK_MORE_RESULTS
            contact = null
            keycloakUserDetails = null
            this.additionalSearchResults = additionalSearchResults
        }
    }

    companion object {
        const val KEYCLOAK_SEARCH_DELAY_MILLIS = 300L
    }
}

fun ContactOrKeycloakDetails.getAnnotatedName(): AnnotatedString {
    return buildAnnotatedString {
        when (contactType) {
            CONTACT -> {
                val contact = contact ?: return toAnnotatedString()
                val identityDetails = contact.getIdentityDetails()
                if (identityDetails != null) {
                    if (contact.customDisplayName != null) {
                        append(contact.customDisplayName)
                    } else {
                        append(
                            identityDetails.formatFirstAndLastName(
                                SettingsActivity.contactDisplayNameFormat,
                                SettingsActivity.uppercaseLastName
                            )
                        )
                    }
                } else {
                    append(contact.getCustomDisplayName())
                }
            }

            KEYCLOAK -> {
                val keycloakUserDetails = keycloakUserDetails ?: return toAnnotatedString()
                val identityDetails = keycloakUserDetails.getIdentityDetails(null)
                append(
                    identityDetails.formatFirstAndLastName(
                        SettingsActivity.contactDisplayNameFormat,
                        SettingsActivity.uppercaseLastName
                    )
                )
            }

            KEYCLOAK_MORE_RESULTS -> {}
        }
    }
}

fun ContactOrKeycloakDetails.getAnnotatedDescription(): AnnotatedString? {
    return buildAnnotatedString {
        when (contactType) {
            CONTACT -> {
                val contact = contact ?: return toAnnotatedString()
                val identityDetails = contact.getIdentityDetails()
                if (identityDetails != null) {
                    if (contact.customDisplayName != null) {
                        append(
                            identityDetails.formatDisplayName(
                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                SettingsActivity.uppercaseLastName
                            )
                        )
                    } else {
                        identityDetails.formatPositionAndCompany(
                            SettingsActivity.contactDisplayNameFormat
                        )?.let { append(it) }
                    }
                }
            }

            KEYCLOAK -> {
                keycloakUserDetails?.getIdentityDetails(null)
                    ?.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                    ?.let {
                        append(it)
                    }
            }

            KEYCLOAK_MORE_RESULTS -> {}
        }
    }.takeIf { it.isNotEmpty() }
}

fun ContactOrKeycloakDetails.getInitialViewSetup(): (InitialView) -> Unit =
    { initialView ->
        when (contactType) {
            CONTACT -> contact?.let {
                initialView.setContact(
                    it
                )
            }

            KEYCLOAK -> keycloakUserDetails?.let { keycloakUserDetails ->
                val identityDetails =
                    keycloakUserDetails.getIdentityDetails(
                        null
                    )
                val name =
                    identityDetails.formatFirstAndLastName(
                        SettingsActivity.contactDisplayNameFormat,
                        SettingsActivity.uppercaseLastName
                    )
                ContactCacheSingleton.getContactPhotoUrl(keycloakUserDetails.identity)?.let {
                    initialView.setPhotoUrl(keycloakUserDetails.identity, it)
                } ?: initialView.setInitial(
                    keycloakUserDetails.identity,
                    StringUtils.getInitial(
                        name
                    )
                )
                initialView.setKeycloakCertified(true)
            }
            else -> {}
        }
    }


fun AnnotatedString.highlight(
    spanStyle: SpanStyle,
    patterns: List<Pattern>?,
): AnnotatedString {
    return buildAnnotatedString {
        append(this@highlight)
        patterns?.map {
            Regex(it.toString())
        }?.let {
            StringUtils2.computeHighlightRanges(text, it).forEach { range ->
                addStyle(spanStyle, range.first, range.second)
            }
        }
    }
}
