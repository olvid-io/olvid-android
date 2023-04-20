/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.util.Pair
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_SEARCHING
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.settings.SettingsActivity
import java.util.*
import java.util.regex.Pattern

class ContactListViewModel : ViewModel() {

    private var unfilteredContacts: List<Contact>? = null
    private var unfilteredNotOneToOneContacts: List<Contact>? = null
    internal val filteredContacts = MutableLiveData<List<ContactOrKeycloakDetails>?>()
    private var _filter: String? = null
    var filterPatterns: MutableList<Pattern>? = null
    private val executor = NoExceptionSingleThreadExecutor("ContactListViewModelExecutor")
    private var keycloakSearchInProgress = false
    private var keycloakSearchBytesOwnedIdentity: ByteArray? = null
    private var keycloakSearchResultsFilter: String? = null
    private var keycloakSearchResults: List<JsonKeycloakUserDetails>? = null
    private var keycloakSearchAdditionalResults = false

    fun setUnfilteredContacts(unfilteredContacts: List<Contact>?) {
        this.unfilteredContacts = unfilteredContacts
        setFilter(_filter)
    }

    fun setUnfilteredNotOneToOneContacts(unfilteredNotOneToOneContacts: List<Contact>?) {
        this.unfilteredNotOneToOneContacts = unfilteredNotOneToOneContacts
        setFilter(_filter)
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
            if (filterPatterns.isNullOrEmpty()
                    .not() && ownedIdentity != null && ownedIdentity.keycloakManaged
            ) {
                if (filter != keycloakSearchResultsFilter || !Arrays.equals(
                        ownedIdentity.bytesOwnedIdentity,
                        keycloakSearchBytesOwnedIdentity
                    )
                ) {
                    keycloakSearchInProgress = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        searchKeycloak(
                            ownedIdentity.bytesOwnedIdentity,
                            filter
                        )
                    }, KEYCLOAK_SEARCH_DELAY_MILLIS)
                }
            }
        }
        executor.execute(
            FilterContactListTask(
                filterPatterns,
                filteredContacts,
                unfilteredContacts,
                unfilteredNotOneToOneContacts,
                keycloakSearchInProgress,
                keycloakSearchResults,
                keycloakSearchAdditionalResults
            )
        )
    }

    fun getFilter(): String? {
        return _filter
    }

    fun isFiltering(): Boolean {
        return _filter?.trim()?.isNotEmpty() == true
    }


    private class FilterContactListTask(
        filterPatterns: List<Pattern>?,
        filteredContacts: MutableLiveData<List<ContactOrKeycloakDetails>?>,
        unfilteredContacts: List<Contact>?,
        unfilteredNotOneToOneContacts: List<Contact>?,
        keycloakSearchInProgress: Boolean,
        keycloakSearchResults: List<JsonKeycloakUserDetails>?,
        keycloakSearchAdditionalResults: Boolean
    ) : Runnable {
        private val filterPatterns: List<Pattern> by lazy { filterPatterns ?: arrayListOf() }
        private val filteredContacts: MutableLiveData<List<ContactOrKeycloakDetails>?>
        private val unfilteredContacts: List<Contact>?
        private val unfilteredNotOneToOneContacts: List<Contact>?
        private val keycloakSearchInProgress: Boolean
        private val keycloakSearchResults: List<JsonKeycloakUserDetails>?
        private val keycloakSearchAdditionalResults: Boolean

        init {
            this.filteredContacts = filteredContacts
            this.unfilteredContacts = unfilteredContacts
            this.unfilteredNotOneToOneContacts = unfilteredNotOneToOneContacts
            this.keycloakSearchInProgress = keycloakSearchInProgress
            this.keycloakSearchResults = keycloakSearchResults
            this.keycloakSearchAdditionalResults = keycloakSearchAdditionalResults
        }

        override fun run() {
            if (filterPatterns.isEmpty()) {
                if (unfilteredContacts == null) {
                    filteredContacts.postValue(null)
                    return
                }
                val list: MutableList<ContactOrKeycloakDetails> = ArrayList()
                for (contact in unfilteredContacts) {
                    list.add(ContactOrKeycloakDetails(contact))
                }
                filteredContacts.postValue(list)
            } else {
                if (unfilteredContacts == null && unfilteredNotOneToOneContacts == null) {
                    filteredContacts.postValue(null)
                    return
                }
                val list: MutableList<ContactOrKeycloakDetails> = ArrayList()
                unfilteredContacts?.let {
                    for (contact in unfilteredContacts) {
                        var matches = true
                        for (pattern in filterPatterns) {
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
                }
                unfilteredNotOneToOneContacts?.let {
                    for (contact in unfilteredNotOneToOneContacts) {
                        var matches = true
                        for (pattern in filterPatterns) {
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
                }
                if (keycloakSearchInProgress) {
                    list.add(ContactOrKeycloakDetails(true))
                } else {
                    keycloakSearchResults?.let {
                        for (keycloakUserDetails in keycloakSearchResults) {
                            // check if the we know the contact by querying the cache (note that this also filters out our ownedIdentity)
                            val displayName =
                                AppSingleton.getContactCustomDisplayName(keycloakUserDetails.identity)
                            if (displayName == null && keycloakUserDetails.identity != null) {
                                // unknown contact --> add them to the list
                                list.add(ContactOrKeycloakDetails(keycloakUserDetails))
                            }
                        }
                        if (keycloakSearchAdditionalResults) {
                            list.add(ContactOrKeycloakDetails(false))
                        }
                    }
                }
                filteredContacts.postValue(list)
            }
        }
    }

    private fun searchKeycloak(bytesOwnedIdentity: ByteArray, filter: String) {
        val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
        if (ownedIdentity == null || !bytesOwnedIdentity.contentEquals(ownedIdentity.bytesOwnedIdentity)
            || filter != this._filter
        ) {
            // something changed during the KEYCLOAK_SEARCH_DELAY_MILLIS --> abort
            return
        }
        KeycloakManager.getInstance().search(
            bytesOwnedIdentity,
            filter,
            object : KeycloakCallback<Pair<List<JsonKeycloakUserDetails>?, Int?>?> {
                override fun success(searchResult: Pair<List<JsonKeycloakUserDetails>?, Int?>?) {
                    val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                    if (this@ContactListViewModel._filter != filter || ownedIdentity == null || !ownedIdentity.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        // something changed while we were searching --> abort
                        return
                    }
                    keycloakSearchInProgress = false
                    keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity
                    keycloakSearchResultsFilter = filter
                    keycloakSearchResults = searchResult?.first
                    keycloakSearchAdditionalResults = if (searchResult?.second == null) {
                        false
                    } else {
                        if (searchResult.first == null) {
                            searchResult.second!! > 0
                        } else {
                            searchResult.second!! > searchResult.first!!.size
                        }
                    }

                    // re-filter to add keycloak search results
                    setFilter(filter)
                }

                override fun failed(rfc: Int) {
                    val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                    if (this@ContactListViewModel._filter != filter || ownedIdentity == null || !ownedIdentity.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        return
                    }
                    keycloakSearchInProgress = false
                    keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity
                    keycloakSearchResultsFilter = filter
                    keycloakSearchResults = null
                    keycloakSearchAdditionalResults = false

                    // re-filter anyway to force refresh and remove the "searching" spinner
                    setFilter(filter)
                }
            })
    }

    enum class ContactType {
        CONTACT, KEYCLOAK, KEYCLOAK_SEARCHING, KEYCLOAK_MORE_RESULTS
    }

    class ContactOrKeycloakDetails {
        val contactType: ContactType
        val contact: Contact?
        val keycloakUserDetails: JsonKeycloakUserDetails?

        constructor(contact: Contact) {
            contactType = CONTACT
            this.contact = contact
            keycloakUserDetails = null
        }

        constructor(keycloakUserDetails: JsonKeycloakUserDetails) {
            contactType = KEYCLOAK
            contact = null
            this.keycloakUserDetails = keycloakUserDetails
        }

        constructor(searching: Boolean) {
            contactType = if (searching) KEYCLOAK_SEARCHING else KEYCLOAK_MORE_RESULTS
            contact = null
            keycloakUserDetails = null
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
                                SettingsActivity.getContactDisplayNameFormat(),
                                SettingsActivity.getUppercaseLastName()
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
                        SettingsActivity.getContactDisplayNameFormat(),
                        SettingsActivity.getUppercaseLastName()
                    )
                )
            }
            KEYCLOAK_SEARCHING, KEYCLOAK_MORE_RESULTS -> {}
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
                                SettingsActivity.getUppercaseLastName()
                            )
                        )
                    } else {
                        identityDetails.formatPositionAndCompany(
                            SettingsActivity.getContactDisplayNameFormat()
                        )?.let { append(it) }
                    }
                }
            }
            KEYCLOAK -> {
                keycloakUserDetails?.getIdentityDetails(null)
                    ?.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat())
                    ?.let {
                        append(it)
                    }
            }
            KEYCLOAK_SEARCHING, KEYCLOAK_MORE_RESULTS -> {}
        }
    }.takeIf { it.isNotEmpty() }
}

fun AnnotatedString.highlight(
    spanStyle: SpanStyle,
    patterns: List<Pattern>?,
): AnnotatedString {
    val unAccented = StringUtils.unAccent(text)
    return buildAnnotatedString {
        append(this@highlight)
        patterns?.let {
            for (pattern in patterns) {
                val matcher = pattern.matcher(unAccented)
                if (matcher.find()) {
                    addStyle(
                        spanStyle, StringUtils.unaccentedOffsetToActualOffset(
                            text,
                            matcher.start()
                        ),
                        StringUtils.unaccentedOffsetToActualOffset(text, matcher.end())
                    )
                }
            }
        }
    }
}
