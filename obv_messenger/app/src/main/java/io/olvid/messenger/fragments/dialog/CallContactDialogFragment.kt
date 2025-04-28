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
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.fragments.FilteredContactListFragment.FilteredContactListOnClickDelegate
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture
import io.olvid.messenger.webrtc.WebrtcCallService

class CallContactDialogFragment : DialogFragment(), View.OnClickListener {
    private lateinit var callButton: Button
    private lateinit var filteredContactListFragment: FilteredContactListFragment
    private var selectedContacts: MutableList<Contact?> = mutableListOf()

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
        val dialogView = inflater.inflate(R.layout.dialog_fragment_start_call, container, false)
        val dialogContactNameFilter =
            dialogView.findViewById<EditText?>(R.id.dialog_discussion_filter)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener { _: View? -> dismiss() }
        callButton = dialogView.findViewById(R.id.button_call)
        callButton.setOnClickListener(this)

        val multiCallCheckBox = dialogView.findViewById<CheckBox>(R.id.mulit_call_checkbox)

        filteredContactListFragment = FilteredContactListFragment().apply {
            removeBottomPadding()
            setContactFilterEditText(dialogContactNameFilter)
            setUnfilteredContacts(
                AppSingleton.getCurrentIdentityLiveData()
                    .switchMap<OwnedIdentity?, MutableList<Contact?>?> { ownedIdentity: OwnedIdentity? ->
                        if (ownedIdentity == null) {
                            return@switchMap null
                        }
                        getInstance().contactDao()
                            .getAllForOwnedIdentityWithChannel(ownedIdentity.bytesOwnedIdentity)
                    })
            setOnClickDelegate(object :
                FilteredContactListOnClickDelegate {
                override fun contactClicked(view: View, contact: Contact) {
                    dismiss()
                    App.startWebrtcCall(
                        view.context,
                        contact.bytesOwnedIdentity,
                        contact.bytesContactIdentity
                    )
                }

                override fun contactLongClicked(view: View?, contact: Contact?) {
                }
            })
            setSelectedContactsObserver { selectedContacts: MutableList<Contact?>? ->
                this@CallContactDialogFragment.onContactsSelected(
                    selectedContacts ?: mutableListOf()
                )
            }
        }

        multiCallCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                callButton.visibility = View.VISIBLE
                filteredContactListFragment.setSelectable(true)
            } else {
                callButton.visibility = View.GONE
                filteredContactListFragment.setSelectable(false)
            }
            callButton.isEnabled = selectedContacts.isNotEmpty()
        }


        val transaction = getChildFragmentManager().beginTransaction()
        transaction.replace(
            R.id.dialog_filtered_contact_list_placeholder,
            filteredContactListFragment
        )
        transaction.commit()

        return dialogView
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.button_call) {
            if (selectedContacts.isEmpty()) {
                return
            }
            if (selectedContacts.size > WebrtcCallService.MAX_PEERS_TO_START_A_CALL) {
                context?.let {
                    val builder: Builder = SecureAlertDialogBuilder(it, R.style.CustomAlertDialog)
                    builder.setTitle(R.string.dialog_title_call_blocked)
                        .setMessage(
                            getString(
                                R.string.dialog_message_call_blocked,
                                WebrtcCallService.MAX_PEERS_TO_START_A_CALL
                            )
                        )
                        .setPositiveButton(R.string.button_label_ok, null)
                    builder.create().show()
                }
                return
            }
            dismiss()
            App.startWebrtcMultiCall(
                view.context,
                selectedContacts[0]!!.bytesOwnedIdentity,
                selectedContacts,
                null,
                false
            )
        }
    }

    private fun onContactsSelected(selectedContacts: MutableList<Contact?>) {
        this.selectedContacts = selectedContacts
        callButton.isEnabled = selectedContacts.isNotEmpty()
    }

    companion object {
        fun newInstance(): CallContactDialogFragment {
            return CallContactDialogFragment()
        }
    }
}
