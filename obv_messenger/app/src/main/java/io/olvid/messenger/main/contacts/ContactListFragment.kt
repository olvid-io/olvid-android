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

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.RefreshingFragment
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_SEARCHING
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.settings.SettingsActivity

class ContactListFragment : RefreshingFragment(), ContactMenu {

    private val contactListViewModel: ContactListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val unfilteredContacts =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap null
                }
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
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                val refreshing by refreshingViewModel.isRefreshing.collectAsStateWithLifecycle()
                ContactListScreen(
                    contactListViewModel = contactListViewModel,
                    refreshing = refreshing,
                    onRefresh = ::onRefresh,
                    onClick = ::contactClicked,
                    contactMenu = this@ContactListFragment
                )
            }
        }
    }

    private fun contactClicked(contactOrKeycloakDetails: ContactOrKeycloakDetails?) {
        if (contactOrKeycloakDetails != null) {
            when (contactOrKeycloakDetails.contactType) {
                CONTACT -> if (contactOrKeycloakDetails.contact != null) {
                    App.openContactDetailsActivity(
                        context,
                        contactOrKeycloakDetails.contact.bytesOwnedIdentity,
                        contactOrKeycloakDetails.contact.bytesContactIdentity
                    )
                }

                KEYCLOAK -> if (contactOrKeycloakDetails.keycloakUserDetails != null && contactOrKeycloakDetails.keycloakUserDetails.identity != null) {
                    val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value ?: return
                    val identityDetails =
                        contactOrKeycloakDetails.keycloakUserDetails.getIdentityDetails(null)
                    val name = identityDetails.formatFirstAndLastName(
                        SettingsActivity.getContactDisplayNameFormat(),
                        SettingsActivity.getUppercaseLastName()
                    )
                    val builder: Builder = SecureAlertDialogBuilder(
                        requireActivity(), R.style.CustomAlertDialog
                    )
                    builder.setTitle(R.string.dialog_title_add_keycloak_user)
                        .setMessage(getString(R.string.dialog_message_add_keycloak_user, name))
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_add_contact) { _, _ ->
                            KeycloakManager.getInstance().addContact(
                                ownedIdentity.bytesOwnedIdentity,
                                contactOrKeycloakDetails.keycloakUserDetails.id,
                                contactOrKeycloakDetails.keycloakUserDetails.identity,
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

                KEYCLOAK_SEARCHING, KEYCLOAK_MORE_RESULTS -> {}
            }
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

    fun bindToSearchView(searchView: SearchView?) {
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
            contactListViewModel.setFilter(null)
        }
    }
}