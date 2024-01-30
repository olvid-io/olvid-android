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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import io.olvid.messenger.R
import io.olvid.messenger.R.layout
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.settings.SettingsActivity

class GroupV2MemberAdditionDialogFragment : DialogFragment() {
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by activityViewModels()
    private var bytesOwnedIdentity: ByteArray? = null
    private var bytesGroupIdentifier: ByteArray? = null
    private var bytesAddedMemberIdentities: MutableList<ByteArray>? = null
    private var bytesRemovedMemberIdentities: MutableList<ByteArray>? = null
    private var selectedContacts: List<Contact>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments
        if (arguments != null) {
            bytesOwnedIdentity = arguments.getByteArray(BYTES_OWNED_IDENTITY_KEY)
            bytesGroupIdentifier = arguments.getByteArray(BYTES_GROUP_IDENTIFIER)
            bytesAddedMemberIdentities = ArrayList()
            bytesRemovedMemberIdentities = ArrayList()
            val addedGroupMembers = arguments.getParcelableArrayList<BytesKey>(ADDED_GROUP_MEMBERS)
            if (addedGroupMembers != null) {
                for (addedGroupMember in addedGroupMembers) {
                    bytesAddedMemberIdentities?.add(addedGroupMember.bytes)
                }
            }
            val removedGroupMembers = arguments.getParcelableArrayList<BytesKey>(
                REMOVED_GROUP_MEMBERS
            )
            if (removedGroupMembers != null) {
                for (removedGroupMember in removedGroupMembers) {
                    bytesRemovedMemberIdentities?.add(removedGroupMember.bytes)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val window = dialog.window
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView =
            inflater.inflate(layout.dialog_fragment_add_group_v2_members, container, false)
        val dialogContactNameFilter =
            dialogView.findViewById<EditText>(R.id.dialog_discussion_filter)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener { dismiss() }
        val okButton = dialogView.findViewById<Button>(R.id.button_ok)
        okButton.setOnClickListener {
            dismiss()
            if (selectedContacts.isNullOrEmpty()) {
                return@setOnClickListener
            }
            selectedContacts?.let { groupV2DetailsViewModel.membersAdded(it) }
        }
        val filteredContactListFragment = FilteredContactListFragment()
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter)
        filteredContactListFragment.setSelectable(true)
        val unfilteredContacts = AppDatabase.getInstance().group2Dao()
            .getAllValidContactsNotInGroup(
                bytesOwnedIdentity,
                bytesGroupIdentifier,
                bytesAddedMemberIdentities,
                bytesRemovedMemberIdentities
            )
        filteredContactListFragment.setUnfilteredContacts(unfilteredContacts)
        val selectedContactsObserver =
            Observer<List<Contact>> { contacts: List<Contact>? -> selectedContacts = contacts }
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver)
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(
            R.id.dialog_filtered_contact_list_placeholder,
            filteredContactListFragment
        )
        transaction.commit()
        return dialogView
    }

    companion object {
        private const val BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"
        private const val BYTES_GROUP_IDENTIFIER = "bytes_group_identifier"
        private const val ADDED_GROUP_MEMBERS = "added_group_members"
        private const val REMOVED_GROUP_MEMBERS = "removed_group_members"
        fun newInstance(
            bytesOwnedIdentity: ByteArray?,
            bytesGroupIdentifier: ByteArray?,
            addedGroupMembers: ArrayList<BytesKey?>?,
            removedGroupMembers: ArrayList<BytesKey?>?
        ): GroupV2MemberAdditionDialogFragment {
            val fragment = GroupV2MemberAdditionDialogFragment()
            val args = Bundle()
            args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity)
            args.putByteArray(BYTES_GROUP_IDENTIFIER, bytesGroupIdentifier)
            args.putParcelableArrayList(ADDED_GROUP_MEMBERS, addedGroupMembers)
            args.putParcelableArrayList(REMOVED_GROUP_MEMBERS, removedGroupMembers)
            fragment.arguments = args
            return fragment
        }
    }
}