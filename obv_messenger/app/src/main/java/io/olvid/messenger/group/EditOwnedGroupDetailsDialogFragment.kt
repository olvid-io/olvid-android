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
package io.olvid.messenger.group

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2ChangeSet
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.layout
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.tasks.UpdateGroupCustomNameAndPhotoTask
import io.olvid.messenger.databases.tasks.UpdateGroupV2CustomNameAndPhotoTask
import io.olvid.messenger.settings.SettingsActivity


class EditOwnedGroupDetailsDialogFragment : DialogFragment() {
    private val viewModel: OwnedGroupDetailsViewModel by activityViewModels()
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by activityViewModels()

    private var onOkCallback: Runnable? = null
    private var doNotRollbackOnDismiss = false
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
            window.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()         
        dialog?.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(
            layout.dialog_fragment_publish_cancel_with_placeholder,
            container,
            false
        )
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle.setText(string.dialog_title_edit_group_details)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener {
           if (groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.value == true) {
                groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.value = false
                groupV2DetailsViewModel.oldCustomGroupType?.let { groupV2DetailsViewModel.setGroupType(it) }
            } else {
                dismiss()
            }
        }
        val publishButton = dialogView.findViewById<Button>(R.id.button_publish)
        publishButton.setOnClickListener {
            if (groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.value == true) {
                groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.value = false
            } else {
                doNotRollbackOnDismiss = true
                dismiss()
                if (viewModel.isGroupV2()) {
                    App.runThread {
                        var changed = false
                        val obvChangeSet : ObvGroupV2ChangeSet
                        if (groupV2DetailsViewModel.groupTypeChanged()) {
                            groupV2DetailsViewModel.createGroupeTypeChangeSet(groupV2DetailsViewModel.getGroupTypeLiveData().value?.toJsonGroupType())
                            obvChangeSet = groupV2DetailsViewModel.getObvChangeSet()
                        } else {
                            obvChangeSet = ObvGroupV2ChangeSet()
                        }
                        if (viewModel.detailsChanged()) {
                            try {
                                obvChangeSet.updatedSerializedGroupDetails =
                                    AppSingleton.getJsonObjectMapper().writeValueAsString(
                                        viewModel.jsonGroupDetails
                                    )
                                changed = true
                            } catch (ignored: Exception) {
                            }
                        }
                        if (viewModel.photoChanged()) {
                            obvChangeSet.updatedPhotoUrl =
                                if (viewModel.getAbsolutePhotoUrl() == null) "" else viewModel.getAbsolutePhotoUrl()
                            changed = true
                        }
                        if (changed) {
                            try {
                                AppSingleton.getEngine().initiateGroupV2Update(
                                    viewModel.bytesOwnedIdentity,
                                    viewModel.getBytesGroupOwnerAndUidOrIdentifier(),
                                    obvChangeSet
                                )
                                UpdateGroupV2CustomNameAndPhotoTask(
                                    viewModel.bytesOwnedIdentity,
                                    viewModel.getBytesGroupOwnerAndUidOrIdentifier(),
                                    null,
                                    null,
                                    viewModel.personalNote,
                                     false
                                ).run()
                            } catch (e: Exception) {
                                App.toast(string.toast_message_error_retry, Toast.LENGTH_SHORT)
                            }
                        } else if (viewModel.personalNoteChanged()) {
                            UpdateGroupV2CustomNameAndPhotoTask(
                                viewModel.bytesOwnedIdentity,
                                viewModel.getBytesGroupOwnerAndUidOrIdentifier(),
                                null,
                                null,
                                viewModel.personalNote,
                                 false
                            ).run()
                        }
                        if (onOkCallback != null) {
                            onOkCallback!!.run()
                        }
                    }
                } else {
                    App.runThread {
                        var changed = false
                        if (viewModel.detailsChanged()) {
                            val newDetails = viewModel.jsonGroupDetails
                            try {
                                AppSingleton.getEngine().updateLatestGroupDetails(
                                    viewModel.bytesOwnedIdentity,
                                    viewModel.getBytesGroupOwnerAndUidOrIdentifier(),
                                    newDetails
                                )
                                changed = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                App.toast(
                                    string.toast_message_error_publishing_group_details,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                        if (viewModel.photoChanged()) {
                            val absolutePhotoUrl = viewModel.getAbsolutePhotoUrl()
                            try {
                                AppSingleton.getEngine().updateOwnedGroupPhoto(
                                    viewModel.bytesOwnedIdentity,
                                    viewModel.getBytesGroupOwnerAndUidOrIdentifier(),
                                    absolutePhotoUrl
                                )
                                changed = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                App.toast(
                                    string.toast_message_error_publishing_group_details,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                        if (viewModel.personalNoteChanged()) {
                            UpdateGroupCustomNameAndPhotoTask(viewModel.bytesOwnedIdentity, viewModel.getBytesGroupOwnerAndUidOrIdentifier(), null, null, viewModel.personalNote, false).run()
                        }
                        if (changed) {
                            AppSingleton.getEngine().publishLatestGroupDetails(
                                viewModel.bytesOwnedIdentity,
                                viewModel.getBytesGroupOwnerAndUidOrIdentifier()
                            )
                        }
                        if (onOkCallback != null) {
                            onOkCallback!!.run()
                        }
                    }
                }
            }
        }
        viewModel.getValid()
            .observe(this) { valid: Boolean? -> publishButton.isEnabled = valid != null && valid }

        val ownedGroupDetailsFragment = OwnedGroupDetailsFragment()
        ownedGroupDetailsFragment.setUseDialogBackground(true)
        ownedGroupDetailsFragment.setInitialGroupType(groupV2DetailsViewModel.initialGroupType ?: GroupTypeModel.SimpleGroup)
        val groupCustomSettingsFragment = GroupCustomSettingsPreferenceFragment(isGroupCreation = false)


        groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.observe(this) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.replace(
                R.id.fragment_placeholder,
                if (it) groupCustomSettingsFragment else ownedGroupDetailsFragment
            )
            transaction.commit()
            publishButton.text = if (it) getString(string.button_label_ok) else getString(string.button_label_publish)
            if (it) {
                groupV2DetailsViewModel.oldCustomGroupType = (groupV2DetailsViewModel.getGroupTypeLiveData().value as? GroupTypeModel.CustomGroup)?.let {
                    GroupTypeModel.CustomGroup(readOnlySetting = it.readOnlySetting, remoteDeleteSetting = it.remoteDeleteSetting)
                }
            }
        }

        return dialogView
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.value = false
        if (doNotRollbackOnDismiss.not()) {
            groupV2DetailsViewModel.initialGroupType?.let { groupV2DetailsViewModel.setGroupType(it) }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            parentActivity: AppCompatActivity?,
            byteOwnedIdentity: ByteArray,
            bytesGroupOwnerAndUid: ByteArray,
            groupDetails: JsonGroupDetailsWithVersionAndPhoto,
            personalNote: String?,
            onOkCallback: Runnable?
        ): EditOwnedGroupDetailsDialogFragment {
            val fragment = EditOwnedGroupDetailsDialogFragment()
            fragment.onOkCallback = onOkCallback
            val viewModel = ViewModelProvider(parentActivity!!)[OwnedGroupDetailsViewModel::class.java]
            viewModel.setGroupV2(false)
            viewModel.bytesOwnedIdentity = byteOwnedIdentity
            viewModel.setBytesGroupOwnerAndUidOrIdentifier(bytesGroupOwnerAndUid)
            viewModel.setOwnedGroupDetails(groupDetails, personalNote)
            return fragment
        }

        @JvmStatic
        fun newInstanceV2(
            parentActivity: AppCompatActivity,
            byteOwnedIdentity: ByteArray,
            bytesGroupIdentifier: ByteArray,
            groupDetails: JsonGroupDetails,
            photoUrl: String?,
            personalNote: String?,
            onOkCallback: Runnable?
        ): EditOwnedGroupDetailsDialogFragment {
            val fragment = EditOwnedGroupDetailsDialogFragment()
            fragment.onOkCallback = onOkCallback
            val viewModel = ViewModelProvider(parentActivity)[OwnedGroupDetailsViewModel::class.java]
            viewModel.setGroupV2(true)
            viewModel.bytesOwnedIdentity = byteOwnedIdentity
            viewModel.setBytesGroupOwnerAndUidOrIdentifier(bytesGroupIdentifier)
            viewModel.setOwnedGroupDetailsV2(groupDetails, photoUrl, personalNote)
            return fragment
        }
    }
}