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
package io.olvid.messenger.owneddetails

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.PUBLISH
import io.olvid.messenger.settings.SettingsActivity.Companion.isHiddenProfileClosePolicyDefined
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture

class EditOwnedIdentityDetailsDialogFragment :
    DialogFragment() {
    private val viewModel: OwnedIdentityDetailsViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        val window = dialog.window
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (preventScreenCapture()) {
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
            window.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val window = dialog.window
            window?.setLayout(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(
            R.layout.dialog_fragment_publish_cancel_with_placeholder,
            container,
            false
        )
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle.setText(R.string.dialog_title_edit_identity_details)
        val cancelButton = dialogView.findViewById<Button>(R.id.button_cancel)
        cancelButton.setOnClickListener { view: View ->
            this.cancelClicked(
                view
            )
        }
        val publishButton = dialogView.findViewById<Button>(R.id.button_publish)
        publishButton.setOnClickListener { view: View ->
            this.publishClicked(
                view
            )
        }

        viewModel.valid.observe(
            this
        ) { validStatus: ValidStatus? ->
            when (validStatus) {
                null, INVALID -> {
                    publishButton.isEnabled = false
                    publishButton.setText(R.string.button_label_publish)
                }

                PUBLISH -> {
                    publishButton.isEnabled = true
                    publishButton.setText(R.string.button_label_publish)
                }

                else -> {
                    publishButton.isEnabled = true
                    publishButton.setText(R.string.button_label_save)
                }
            }
        }

        val ownedIdentityDetailsFragment = OwnedIdentityDetailsFragment()
        ownedIdentityDetailsFragment.setUseDialogBackground(true)
        ownedIdentityDetailsFragment.setShowNicknameAndHidden(true)
        App.runThread {
            ownedIdentityDetailsFragment.setDisableHidden(
                AppDatabase.getInstance().ownedIdentityDao().countNotHidden() <= 1
            )
            Handler(Looper.getMainLooper()).post {
                val transaction = childFragmentManager.beginTransaction()
                transaction.replace(R.id.fragment_placeholder, ownedIdentityDetailsFragment)
                transaction.commit()
            }
        }

        return dialogView
    }

    private fun cancelClicked(view: View) {
        if (viewModel.profileHiddenChanged() && viewModel.isProfileHidden) {
            // the user just hid a profile or changed the password of an already hidden profile, ask for confirmation that he wants to discard is password
            val builder: Builder = SecureAlertDialogBuilder(view.context, R.style.CustomAlertDialog)
            builder.setTitle(R.string.dialog_title_cancel_hide_profile)
                .setMessage(R.string.dialog_message_cancel_hide_profile)
                .setNegativeButton(R.string.button_label_cancel, null)
                .setPositiveButton(
                    R.string.button_label_proceed
                ) { _, _ -> dismiss() }
            builder.create().show()
        } else {
            dismiss()
        }
    }

    private fun publishClicked(view: View) {
        if (viewModel.profileHiddenChanged() && !viewModel.isProfileHidden) {
            // the user just un-hid a profile, ask for confirmation
            val builder: Builder = SecureAlertDialogBuilder(view.context, R.style.CustomAlertDialog)
            builder.setTitle(R.string.dialog_title_unhide_profile)
                .setMessage(R.string.dialog_message_unhide_profile)
                .setNegativeButton(R.string.button_label_cancel, null)
                .setPositiveButton(R.string.button_label_proceed) { _, _ ->
                    dismiss()
                    doPublish()
                }
            builder.create().show()
        } else if (viewModel.profileHiddenChanged() && viewModel.isProfileHidden) {
            // the user added or changed a password to hide a profile
            val builder: Builder = SecureAlertDialogBuilder(view.context, R.style.CustomAlertDialog)
            builder.setTitle(R.string.dialog_title_hide_profile)
                .setMessage(R.string.dialog_message_hide_profile)
                .setNegativeButton(R.string.button_label_cancel, null)
                .setPositiveButton(R.string.button_label_proceed) { _, _ ->
                    dismiss()
                    doPublish()
                }
            builder.create().show()
        } else {
            dismiss()
            doPublish()
        }
    }

    private fun doPublish() {
        App.runThread {
            var publishedChanged = false
            if (viewModel.detailsChanged()) {
                val newDetails = viewModel.jsonIdentityDetails
                try {
                    AppSingleton.getEngine()
                        .updateLatestIdentityDetails(viewModel.bytesOwnedIdentity, newDetails)
                    publishedChanged = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    App.toast(
                        R.string.toast_message_error_publishing_details,
                        Toast.LENGTH_SHORT
                    )
                }
            }
            if (viewModel.photoChanged()) {
                val absolutePhotoUrl = viewModel.absolutePhotoUrl
                try {
                    AppSingleton.getEngine()
                        .updateOwnedIdentityPhoto(viewModel.bytesOwnedIdentity, absolutePhotoUrl)
                    publishedChanged = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    App.toast(
                        R.string.toast_message_error_publishing_details,
                        Toast.LENGTH_SHORT
                    )
                }
            }
            if (publishedChanged) {
                AppSingleton.getEngine()
                    .publishLatestIdentityDetails(viewModel.bytesOwnedIdentity)
            }
            if (viewModel.nicknameChanged()) {
                viewModel.bytesOwnedIdentity?.let {
                    AppDatabase.getInstance().ownedIdentityDao()
                        .updateCustomDisplayName(it, viewModel.nickname)
                }
                try {
                    AppSingleton.getEngine()
                        .propagateAppSyncAtomToOtherDevicesIfNeeded(
                            viewModel.bytesOwnedIdentity,
                            ObvSyncAtom.createOwnProfileNicknameChange(
                                viewModel.nickname
                            )
                        )
                    AppSingleton.getEngine().deviceBackupNeeded()
                    AppSingleton.getEngine().profileBackupNeeded(viewModel.bytesOwnedIdentity)
                } catch (e: Exception) {
                    Logger.w("Failed to propagate own profile nickname change to other devices")
                    e.printStackTrace()
                }
            }
            if (viewModel.profileHiddenChanged()) {
                viewModel.bytesOwnedIdentity?.let {
                    AppDatabase.getInstance().ownedIdentityDao()
                        .updateUnlockPasswordAndSalt(
                            it,
                            viewModel.password,
                            viewModel.salt
                        )
                }

                if (viewModel.password != null) {
                    // profile became hidden
                    if (!isHiddenProfileClosePolicyDefined) {
                        App.openAppDialogConfigureHiddenProfileClosePolicy()
                    }
                    viewModel.bytesOwnedIdentity?.let {
                        AppSingleton.getInstance()
                            .ownedIdentityBecameHidden(it)
                    }
                } else {
                    // profile became un-hidden --> reselect it to memorize as latest identity
                    AppSingleton.getInstance().selectIdentity(viewModel.bytesOwnedIdentity, null)
                }
            }
            (activity as? OwnedIdentityDetailsActivity?)?.let { activity: OwnedIdentityDetailsActivity ->
                Handler(Looper.getMainLooper()).post { activity.reloadIdentity() }
            }
        }
    }
}
