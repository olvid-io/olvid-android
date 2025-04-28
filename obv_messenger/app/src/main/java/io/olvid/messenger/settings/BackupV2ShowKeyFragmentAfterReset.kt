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

package io.olvid.messenger.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.CreateCredentialException
import androidx.fragment.app.Fragment
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R


class BackupV2ShowKeyFragmentAfterReset : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val backupSeed : String? = AppSingleton.getEngine().deviceBackupSeed

        if (backupSeed == null) {
            activity?.supportFragmentManager?.popBackStack()
            return null
        }

        val showSuccessFailureDialog = mutableStateOf(false)
        val savingBackupSeed =  mutableStateOf(false)
        val passwordManagerFailed =  mutableStateOf(false)


        // if keys should be saved to credential manager, do it
        if (SettingsActivity.useCredentialsManagerForBackups) {
            val activity = activity
            if (activity != null) {
                savingBackupSeed.value = true
                passwordManagerFailed.value = false
                showSuccessFailureDialog.value = true

                val request = CreatePasswordRequest(
                    id = SettingsActivity.credentialManagerDeviceId,
                    password = backupSeed,
                )
                val credentialManager = CredentialManager.create(App.getContext())

                credentialManager.createCredentialAsync(activity, request, null, { runnable -> activity.runOnUiThread(runnable) },
                    object : CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                        override fun onResult(result: CreateCredentialResponse) {
                            savingBackupSeed.value = false
                        }

                        override fun onError(e: CreateCredentialException) {
                            Logger.x(e)
                            SettingsActivity.useCredentialsManagerForBackups = false
                            passwordManagerFailed.value = true
                            savingBackupSeed.value = false
                        }
                    }
                )
            }
        }

        return ComposeView(layoutInflater.context).apply {
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.almostWhite))
                ) {
                    ShowCurrentSeed(
                        backupSeed = backupSeed,
                        savedInPasswordManager = SettingsActivity.useCredentialsManagerForBackups,
                        followingGenerate = true,
                        onCopy = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                val clip = ClipData.newPlainText(context.getString(R.string.label_text_copied_from_olvid), backupSeed)
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(clip)
                                    App.toast(R.string.toast_message_key_copied_to_clipboard, Toast.LENGTH_SHORT)
                                }
                            } catch (_: Exception) {
                            }
                        },
                        onClose = {
                            activity?.supportFragmentManager?.popBackStack()
                        }
                    )
                }

                if (showSuccessFailureDialog.value) {
                    SuccessFailureDialog(
                        generateFailed = false,
                        savingBackupSeed = savingBackupSeed.value,
                        passwordManagerFailed = passwordManagerFailed.value,
                        onDismiss = {
                            showSuccessFailureDialog.value = false
                        }
                    )
                }
            }
        }
    }
}
