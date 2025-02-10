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
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.layout
import io.olvid.messenger.R.string
import io.olvid.messenger.R.style
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.settings.SettingsActivity

class ContactIntroductionDialogFragment : DialogFragment() {
    private val bytesOwnedIdentity by lazy { arguments?.getByteArray(BYTES_OWNED_IDENTITY_KEY) }
    private val bytesContactIdentityA by lazy { arguments?.getByteArray(BYTES_CONTACT_IDENTITY_KEY) }
    private val displayNameA by lazy { arguments?.getString(DISPLAY_NAME_KEY) }
    private var selectedContacts: List<Contact>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView =
            inflater.inflate(layout.dialog_fragment_pick_multiple_contacts, container, false)
        val dialogContactNameFilter =
            dialogView.findViewById<EditText>(R.id.dialog_discussion_filter)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle.text = resources.getString(string.dialog_title_introduce_contact, displayNameA)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener { dismiss() }
        val filteredContactListFragment = FilteredContactListFragment()
        filteredContactListFragment.removeBottomPadding()
        filteredContactListFragment.setSelectable(true)
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter)
        filteredContactListFragment.setUnfilteredContacts(
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap null
                }
                AppDatabase.getInstance().contactDao()
                    .getAllOneToOneForOwnedIdentityWithChannelExcludingOne(
                        ownedIdentity.bytesOwnedIdentity,
                        bytesContactIdentityA ?: ByteArray(0)
                    )
            })
        val okButton = dialogView.findViewById<Button>(R.id.button_ok)
        okButton.setOnClickListener { view: View ->
            dismiss()
            if (selectedContacts.isNullOrEmpty()) {
                return@setOnClickListener
            }
            val bytesNewMemberIdentities = selectedContacts!!.map { it.bytesContactIdentity }.toTypedArray()
            val introducedDisplayNames = StringUtils.joinContactDisplayNames(selectedContacts!!.map { it.getCustomDisplayName() }.toTypedArray())

            val builder = SecureAlertDialogBuilder(view.context, style.CustomAlertDialog)
                .setTitle(string.dialog_title_contact_introduction)
                .setPositiveButton(string.button_label_ok) { _, _ ->
                    try {
                        AppSingleton.getEngine().startContactMutualIntroductionProtocol(
                            bytesOwnedIdentity,
                            bytesContactIdentityA,
                            bytesNewMemberIdentities
                        )
                        App.toast(
                            string.toast_message_contacts_introduction_started,
                            Toast.LENGTH_SHORT
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton(string.button_label_cancel, null)
            if (bytesNewMemberIdentities.size == 1) {
                builder.setMessage(
                    getString(
                        string.dialog_message_contact_introduction,
                        displayNameA,
                        introducedDisplayNames
                    )
                )
            } else {
                builder.setMessage(
                    getString(
                        string.dialog_message_contact_introduction_multiple,
                        displayNameA,
                        bytesNewMemberIdentities.size,
                        introducedDisplayNames
                    )
                )
            }
            builder.create().show()
        }
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
        private const val BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"
        private const val DISPLAY_NAME_KEY = "display_name"
        @JvmStatic
        fun newInstance(
            bytesOwnedIdentity: ByteArray?,
            bytesContactIdentityA: ByteArray?,
            displayNameA: String?
        ): ContactIntroductionDialogFragment {
            val fragment = ContactIntroductionDialogFragment()
            val args = Bundle()
            args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity)
            args.putByteArray(BYTES_CONTACT_IDENTITY_KEY, bytesContactIdentityA)
            args.putString(DISPLAY_NAME_KEY, displayNameA)
            fragment.arguments = args
            return fragment
        }
    }
}