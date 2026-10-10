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
package io.olvid.messenger.main.contacts

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.switchMap
import io.olvid.messenger.main.contacts.suggested.SuggestedContactsSheet
import io.olvid.messenger.main.invitations.InvitationRepository
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.main.RefreshingFragment
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.GROUP
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback

class ContactListFragment : RefreshingFragment(), ContactMenu {

    companion object {
        const val RED_DOT_THRESHOLD = 3
    }

    private val contactListViewModel: ContactListViewModel by activityViewModels()
    private var searchView: SearchView? = null
    private var lastObservedBytesOwnedIdentity: ByteArray? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // when the profile changes, clear any cached keycloak directory search so the plus-button
        // "Directory" tab does not show the previous profile's results (see #1257)
        AppSingleton.getCurrentIdentityLiveData()
            .observe(viewLifecycleOwner) { ownedIdentity: OwnedIdentity? ->
                val previous = lastObservedBytesOwnedIdentity
                val current = ownedIdentity?.bytesOwnedIdentity
                if (previous != null && !previous.contentEquals(current)) {
                    contactListViewModel.clearKeycloakSearch()
                }
                lastObservedBytesOwnedIdentity = current
            }
        val unfilteredContacts =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap null
                }
                contactListViewModel.keycloakManaged.value = ownedIdentity.keycloakManaged
                AppDatabase.getInstance().contactDao()
                    .getAllOneToOneForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
            }
        unfilteredContacts.observe(viewLifecycleOwner) { contacts: List<Contact>? ->
            contactListViewModel.setUnfilteredContacts(
                contacts
            )
        }
        val unfilteredNotOneToOneContacts =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap null
                }
                AppDatabase.getInstance().contactDao()
                    .getAllNotOneToOneForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
            }
        unfilteredNotOneToOneContacts.observe(viewLifecycleOwner) { contacts: List<Contact>? ->
            contactListViewModel.setUnfilteredNotOneToOneContacts(
                contacts
            )
        }
        // reuse the shared invitations source (already scoped to the current identity)
        InvitationRepository.invitations.observe(viewLifecycleOwner) { invitations ->
            contactListViewModel.setReceivedInvitations(invitations)
        }
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                val refreshing by refreshingViewModel.isRefreshing.collectAsStateWithLifecycle()
                val suggestedContacts by contactListViewModel.suggestedContacts.observeAsState(emptyList())
                val newSuggestedContactsCount by contactListViewModel.newSuggestedContactsCount.observeAsState(0)
                var showSuggestedContacts by rememberSaveable { mutableStateOf(false) }
                ContactListScreen(
                    contactListViewModel = contactListViewModel,
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onClick = ::contactClicked,
                    onInvite = ::inviteClicked,
                    onScrollStart = ::dismissKeyboard,
                    contactMenu = this@ContactListFragment,
                    addPlusButtonBottomPadding = true,
                    suggestedContactsCount = suggestedContacts.size,
                    newSuggestedContactsCount = newSuggestedContactsCount,
                    onOpenSuggestedContacts = { showAllContacts ->
                        contactListViewModel.snapshotSuggestedContacts(showAllContacts)
                        contactListViewModel.markSuggestedContactsSeen(contactListViewModel.suggestedContactsSnapshot)
                        showSuggestedContacts = true
                    },
                )
                if (showSuggestedContacts) {
                    SuggestedContactsSheet(
                        items = contactListViewModel.suggestedContactsSnapshot,
                        onDismiss = { showSuggestedContacts = false },
                    )
                }
            }
        }
    }

    private fun contactClicked(contactOrKeycloakDetails: ContactOrKeycloakDetails?) {
        if (contactOrKeycloakDetails != null) {
            val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value ?: return
            when (contactOrKeycloakDetails.contactType) {
                CONTACT -> if (contactOrKeycloakDetails.contact != null) {
                    App.openContactDetailsActivity(
                        requireContext(),
                        contactOrKeycloakDetails.contact.bytesOwnedIdentity,
                        contactOrKeycloakDetails.contact.bytesContactIdentity
                    )
                }

                KEYCLOAK -> contactOrKeycloakDetails.keycloakUserDetails?.let { keycloakUserDetails ->
                    val contactCacheInfo =
                        ContactCacheSingleton.getContactCacheInfo(keycloakUserDetails.identity)
                    when {
                        contactCacheInfo == null -> runCatching {
                            val name = contactOrKeycloakDetails.getAnnotatedName()
                            val builder: Builder = SecureAlertDialogBuilder(
                                requireActivity(), R.style.CustomAlertDialog
                            )
                            builder.setTitle(R.string.dialog_title_add_keycloak_user)
                                .setMessage(
                                    getString(
                                        R.string.dialog_message_add_keycloak_user,
                                        contactOrKeycloakDetails.getAnnotatedName()
                                    )
                                )
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .setPositiveButton(R.string.button_label_add_contact) { _, _ ->
                                    KeycloakManager.addContact(
                                        ownedIdentity.bytesOwnedIdentity,
                                        keycloakUserDetails.id,
                                        keycloakUserDetails.identity,
                                        object : KeycloakCallback<Void?> {
                                            override fun success(result: Void?) {
                                                App.toast(
                                                    getString(
                                                        R.string.toast_message_contact_added,
                                                        name
                                                    ), Toast.LENGTH_SHORT, Gravity.BOTTOM
                                                )
                                            }

                                            override fun failed(rfc: Int) {
                                                App.toast(
                                                    R.string.toast_message_error_retry,
                                                    Toast.LENGTH_SHORT
                                                )
                                            }
                                        })
                                }
                            builder.create().show()
                        }

                        !contactCacheInfo.oneToOne -> keycloakUserDetails.identity?.let { contactBytes ->
                            App.runThread {
                                AppDatabase.getInstance().contactDao().get(
                                    ownedIdentity.bytesOwnedIdentity,
                                    contactBytes
                                )?.let { contact ->
                                    activity?.runOnUiThread { inviteClicked(contact) }
                                }
                            }
                        }

                        else -> keycloakUserDetails.identity?.let { contactBytes ->
                            App.openContactDetailsActivity(
                                requireContext(),
                                ownedIdentity.bytesOwnedIdentity,
                                contactBytes
                            )
                        }
                    }
                }

                // groups are never displayed in the main contact list tab
                GROUP -> {}

                KEYCLOAK_MORE_RESULTS -> {}
            }
        }
    }

    private fun inviteClicked(contact: Contact) {
        val builder: Builder = SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_invite_contact)
            .setMessage(getString(R.string.dialog_message_invite_contact, contact.getCustomDisplayName()))
            .setNegativeButton(R.string.button_label_cancel, null)
            .setPositiveButton(R.string.button_label_invite) { _, _ ->
                try {
                    if (contact.hasChannelOrPreKey()) {
                        AppSingleton.getEngine().startOneToOneInvitationProtocol(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity
                        )
                    }
                    if (contact.keycloakManaged) {
                        try {
                            val jsonIdentityDetails = contact.getIdentityDetails()
                            if (jsonIdentityDetails != null && jsonIdentityDetails.signedUserDetails != null) {
                                AppSingleton.getEngine().addKeycloakContact(
                                    contact.bytesOwnedIdentity,
                                    contact.bytesContactIdentity,
                                    jsonIdentityDetails.signedUserDetails
                                )
                            }
                        } catch (e : Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        builder.create().show()
    }

    private fun dismissKeyboard() {
        searchView?.let {
            (requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun rename(contact: Contact) {
        activity?.let {
            val editNameAndPhotoDialogFragment =
                EditNameAndPhotoDialogFragment.newInstance(it, contact)
            editNameAndPhotoDialogFragment.show(childFragmentManager, "dialog")
        }
    }

    override fun call(contact: Contact) {
        context?.let {
            App.startWebrtcCall(
                it,
                contact.bytesOwnedIdentity,
                contact.bytesContactIdentity
            )
        }
    }

    override fun delete(contact: Contact) {
        context?.let {
            App.runThread(
                PromptToDeleteContactTask(
                    it,
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity,
                    null
                )
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        contactListViewModel.setFilter(null)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            context.contactListFragment = this
        }
    }

    fun bindToSearchView(searchView: SearchView?) {
        this.searchView = searchView
        if (searchView != null) {
            searchView.setOnQueryTextListener(object : OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    contactListViewModel.setFilter(newText)
                    return true
                }
            })
            searchView.setOnCloseListener(SearchView.OnCloseListener {
                contactListViewModel.setFilter(null)
                false
            })
        }
    }
}