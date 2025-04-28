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

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.fragment.app.DialogFragment
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture
import io.olvid.messenger.settings.composables.BackupKeyGenerationFlow
import java.util.concurrent.Executor


class BackupV2KeyGenerationDialogFragment : DialogFragment() {
    private lateinit var credentialManager: CredentialManager
    private lateinit var executor: Executor

    var onDismissListener: (() -> Unit)? = null

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
        credentialManager = CredentialManager.create(App.getContext())
        executor = Executor { runnable -> activity?.runOnUiThread(runnable) }

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val credentialManagerAvailable = mutableStateOf<Boolean?>(null)
        credentialManager.checkIfAvailable(executor, credentialManagerAvailable)



        return ComposeView(layoutInflater.context).apply {
            setContent {
                BackupKeyGenerationFlow(
                    credentialsManagerAvailable = credentialManagerAvailable,
                    onToggleUseCredentialManager = { useCredentialsManager ->
                        SettingsActivity.useCredentialsManagerForBackups = useCredentialsManager
                    },
                    onChooseCredentialManager = onChooseCredentialManager(credentialManager, context),
                    onGenerateBackupSeed = this@BackupV2KeyGenerationDialogFragment::generateBackupSeed,
                    onSaveBackupSeedToCredentialsManager = this@BackupV2KeyGenerationDialogFragment::saveBackupSeedToCredentialsManager,
                    onCopy = {backupSeed ->
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                            val clip = ClipData.newPlainText(context.getString(R.string.label_text_copied_from_olvid), backupSeed)
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip)
                                App.toast(R.string.toast_message_key_copied_to_clipboard, Toast.LENGTH_SHORT)
                            }
                        } catch (_: Exception) { }
                    },
                    onClose = {
                        dismiss()
                    }
                )
            }
        }
    }

    private fun generateBackupSeed(): String? {
        return AppSingleton.getEngine().generateDeviceBackupSeed(BuildConfig.SERVER_NAME)
    }

    private fun saveBackupSeedToCredentialsManager(backupSeed: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val request = CreatePasswordRequest(
            id = SettingsActivity.credentialManagerDeviceId,
            password = backupSeed,
        )

        activity?.let {
            credentialManager.createCredentialAsync(it, request, null, { runnable -> activity?.runOnUiThread(runnable) },
                object : CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                    override fun onResult(result: CreateCredentialResponse) {
                        onSuccess.invoke()
                    }

                    override fun onError(e: CreateCredentialException) {
                        Logger.x(e)
                        SettingsActivity.useCredentialsManagerForBackups = false
                        onError.invoke()
                    }
                }
            )
        } ?: onError.invoke()
    }

    companion object {
        fun onChooseCredentialManager(credentialManager: CredentialManager, context: Context) : (()->Unit)? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                {
                    credentialManager.createSettingsPendingIntent().send(context, 0, null)
                }
            } else {
                null
            }
        }
    }
}

// used to check if a credentialManger is indeed available with a test request
fun CredentialManager.checkIfAvailable(executor: Executor, state: MutableState<Boolean?>) {
    clearCredentialStateAsync(
        request = ClearCredentialStateRequest(),
        cancellationSignal = null,
        executor = executor,
        callback = object : CredentialManagerCallback<Void?, ClearCredentialException> {
            override fun onResult(result: Void?) {
                state.value = true
            }
            override fun onError(e: ClearCredentialException) {
                state.value = false
                Logger.x(e)
            }
        }
    )
}




@Composable
fun SuccessFailureDialog(
    generateFailed: Boolean,
    savingBackupSeed: Boolean,
    passwordManagerFailed: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column (
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .width(280.dp)
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (savingBackupSeed) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp,
                    color = colorResource(id = R.color.olvid_gradient_light)
                )
            } else if (generateFailed) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_error_outline),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(R.string.label_failed_to_generate_backup_seed),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                }
            } else if (passwordManagerFailed) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_error_outline),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = AnnotatedString(stringResource(R.string.label_failed_to_save_backup_seed)).formatMarkdown(),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_ok_outline),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(R.string.label_saved_to_password_manager),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                }
            }
            if (!savingBackupSeed) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorResource(R.color.olvid_gradient_light)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_ok),
                        )
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun DialogPreview1() {
    SuccessFailureDialog(
        generateFailed = true,
        savingBackupSeed = false,
        passwordManagerFailed = false,
        onDismiss = {}
    )
}
@Preview
@Composable
fun DialogPreview2() {
    SuccessFailureDialog(
        generateFailed = false,
        savingBackupSeed = true,
        passwordManagerFailed = false,
        onDismiss = {}
    )
}
@Preview
@Composable
fun DialogPreview3() {
    SuccessFailureDialog(
        generateFailed = false,
        savingBackupSeed = false,
        passwordManagerFailed = true,
        onDismiss = {}
    )
}
@Preview
@Composable
fun DialogPreview4() {
    SuccessFailureDialog(
        generateFailed = false,
        savingBackupSeed = false,
        passwordManagerFailed = false,
        onDismiss = {}
    )
}
