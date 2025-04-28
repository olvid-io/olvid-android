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
package io.olvid.messenger.fragments.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture
import io.olvid.messenger.webrtc.WebrtcCallService

class MultiCallStartDialogFragment : DialogFragment() {
    private lateinit var filteredContactListFragment: FilteredContactListFragment
    private var bytesOwnedIdentity: ByteArray? = null
    private var bytesGroupOwnerAndUidOrIdentifier: ByteArray? = null
    private var groupV2 = false
    private val bytesContactIdentitiesHashSet: MutableSet<BytesKey> = HashSet()

    private var selectedContacts: MutableList<Contact?> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            bytesOwnedIdentity = args.getByteArray(BYTES_OWNED_IDENTITY_KEY)
            bytesGroupOwnerAndUidOrIdentifier =
                args.getByteArray(BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER_KEY)
            groupV2 = false

            args.getParcelableArrayList<BytesKey?>(BYTES_KEY_CONTACT_IDENTITIES_KEY)?.let {
                bytesContactIdentitiesHashSet.addAll(it)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (preventScreenCapture()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView =
            inflater.inflate(R.layout.dialog_fragment_pick_multiple_contacts, container, false)
        val dialogContactNameFilter =
            dialogView.findViewById<EditText?>(R.id.dialog_discussion_filter)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle.setText(R.string.dialog_title_start_conference_call)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener { dismiss() }
        val okButton = dialogView.findViewById<Button>(R.id.button_ok)
        okButton.setText(R.string.button_label_call)
        okButton.setOnClickListener(View.OnClickListener {
            val context = context
            if (context == null || selectedContacts.isEmpty()) {
                return@OnClickListener
            }
            if (bytesContactIdentitiesHashSet.size <= WebrtcCallService.MAX_PEERS_TO_START_A_CALL && selectedContacts.size > WebrtcCallService.MAX_PEERS_TO_START_A_CALL) {
                val builder = SecureAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog)
                builder.setTitle(R.string.dialog_title_call_blocked)
                    .setMessage(
                        getString(
                            R.string.dialog_message_call_blocked,
                            WebrtcCallService.MAX_PEERS_TO_START_A_CALL
                        )
                    )
                    .setPositiveButton(R.string.button_label_ok, null)
                builder.create().show()
                return@OnClickListener
            }

            dismiss()
            bytesOwnedIdentity?.let {
                App.startWebrtcMultiCall(
                    context,
                    it,
                    selectedContacts,
                    bytesGroupOwnerAndUidOrIdentifier,
                    groupV2
                )
            }
        })

        filteredContactListFragment = FilteredContactListFragment().apply {
            setContactFilterEditText(dialogContactNameFilter)
            setSelectable(true)
            val contactListLivedata: LiveData<MutableList<Contact?>?>
            val bytesIdentities: MutableList<ByteArray?> =
                ArrayList(bytesContactIdentitiesHashSet.size)
            for (bytesKey in bytesContactIdentitiesHashSet) {
                bytesIdentities.add(bytesKey.bytes)
            }

            if (bytesGroupOwnerAndUidOrIdentifier != null) {
                contactListLivedata =
                    getInstance().discussionDao().getByGroupOwnerAndUidOrIdentifierLiveData(
                        bytesOwnedIdentity!!, bytesGroupOwnerAndUidOrIdentifier!!
                    ).switchMap<Discussion?, MutableList<Contact?>?> { discussion: Discussion? ->
                        if (discussion != null && !discussion.isLocked && !discussion.isPreDiscussion) {
                            when (discussion.discussionType) {
                                Discussion.TYPE_GROUP -> {
                                    groupV2 = false
                                    return@switchMap getInstance().contactGroupJoinDao()
                                        .getGroupContactsAndMore(
                                            bytesOwnedIdentity!!,
                                            bytesGroupOwnerAndUidOrIdentifier!!,
                                            bytesIdentities
                                        )
                                }

                                Discussion.TYPE_GROUP_V2 -> {
                                    groupV2 = true
                                    return@switchMap getInstance().group2MemberDao()
                                        .getGroupMemberContactsAndMore(
                                            bytesOwnedIdentity!!,
                                            bytesGroupOwnerAndUidOrIdentifier!!,
                                            bytesIdentities
                                        )
                                }
                            }
                        }
                        getInstance().contactDao()
                            .getWithChannelAsList(bytesOwnedIdentity!!, bytesIdentities)
                    }
            } else {
                contactListLivedata = getInstance().contactDao()
                    .getWithChannelAsList(bytesOwnedIdentity!!, bytesIdentities)
            }
            contactListLivedata.observe(
                this@MultiCallStartDialogFragment,
                object : Observer<MutableList<Contact?>?> {
                    var initialized = false

                    override fun onChanged(value: MutableList<Contact?>?) {
                        if (!initialized && value != null) {
                            val initialContacts: MutableList<Contact?> =
                                ArrayList(bytesContactIdentitiesHashSet.size)
                            for (contact in value) {
                                contact?.let {
                                    if (bytesContactIdentitiesHashSet.contains(BytesKey(contact.bytesContactIdentity))) {
                                        initialContacts.add(contact)
                                    }
                                }
                            }
                            filteredContactListFragment.setInitiallySelectedContacts(initialContacts)
                            initialized = true
                        }
                    }
                })
            setUnfilteredContacts(contactListLivedata)

            val selectedContactsObserver = Observer { contacts: MutableList<Contact?>? ->
                selectedContacts = contacts ?: mutableListOf()
            }
            setSelectedContactsObserver(selectedContactsObserver)
        }

        val transaction = getChildFragmentManager().beginTransaction()
        transaction.replace(
            R.id.dialog_filtered_contact_list_placeholder,
            filteredContactListFragment
        )
        transaction.commit()

        return dialogView
    }

    companion object {
        private const val BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity_key"
        private const val BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER_KEY =
            "bytes_group_owner_and_uid_or_identifier_key"
        private const val BYTES_KEY_CONTACT_IDENTITIES_KEY = "bytes_key_contact_identities_key"

        fun newInstance(
            bytesOwnedIdentity: ByteArray,
            bytesGroupOwnerAndUidOrIdentifier: ByteArray?,
            bytesKeysContactIdentities: ArrayList<BytesKey>?
        ): MultiCallStartDialogFragment {
            val fragment = MultiCallStartDialogFragment()
            val args = Bundle()
            args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity)
            if (bytesGroupOwnerAndUidOrIdentifier != null) {
                args.putByteArray(
                    BYTES_GROUP_OWNER_AND_UID_OR_IDENTIFIER_KEY,
                    bytesGroupOwnerAndUidOrIdentifier
                )
            }
            args.putParcelableArrayList(
                BYTES_KEY_CONTACT_IDENTITIES_KEY,
                bytesKeysContactIdentities
            )
            fragment.setArguments(args)

            return fragment
        }
    }
}
