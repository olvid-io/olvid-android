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

package io.olvid.messenger.onboarding.flow

import android.content.DialogInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.engine.engine.types.identities.ObvIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.R.id
import io.olvid.messenger.R.layout
import io.olvid.messenger.R.string
import io.olvid.messenger.R.style
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment.SignInContext.RESTORE_BACKUP
import io.olvid.messenger.services.BackupCloudProviderService
import io.olvid.messenger.services.BackupCloudProviderService.BackupItem
import io.olvid.messenger.services.BackupCloudProviderService.CloudProviderConfiguration
import io.olvid.messenger.services.BackupCloudProviderService.OnBackupDownloadCallback
import io.olvid.messenger.services.BackupCloudProviderService.OnBackupsListCallback
import net.openid.appauth.AuthState
import java.io.ByteArrayOutputStream
import java.util.UUID




class OnboardingFlowViewModel : ViewModel() {
    companion object {
        val BACKUP_TYPE_FILE = 1
        val BACKUP_TYPE_CLOUD = 2
    }

    var firstName by mutableStateOf("")
        private set
    fun updateFirstName(input: String) {
        firstName = input
    }
    var lastName by mutableStateOf("")
        private set
    fun updateLastName(input: String) {
        lastName = input
    }
    var deviceName by mutableStateOf("")
        private set
    fun updateDeviceName(input: String) {
        deviceName = input
    }
    var sessionNumber by mutableStateOf("")
        private set
    fun updateSessionNumber(input: String) {
        validationError = false
        sessionNumber = input
    }
    var validationInProgress by mutableStateOf(false)
        private set
    fun updateValidationInProgress(input: Boolean) {
        validationInProgress = input
    }
    var validationError by mutableStateOf(false)
        private set
    fun updateValidationError(input: Boolean) {
        validationError = input
    }

    var creatingSimpleIdentity by mutableStateOf(false)

    var capturedImageUri by mutableStateOf<Uri>(Uri.EMPTY)

    var absolutePhotoUrl by mutableStateOf<String?>(null)

    // region Transfer

    var sas by mutableStateOf("")
        private set
    fun updateSas(input: String) {
        validationError = false
        sas = input
    }

    var correctSas by mutableStateOf<String?>(null)
        private set
    fun updateCorrectSas(input: String?) {
        correctSas = input
    }

    var transferSelectedDevice: Device? by mutableStateOf(null)
        private set

    fun updateTransferSelectedDevice(selectedDevice: Device?) {
        transferSelectedDevice = selectedDevice
    }

    var transferMultiDevice: Boolean by mutableStateOf(false)
        private set

    fun updateTransferMultiDevice(multiDevice: Boolean) {
        transferMultiDevice = multiDevice
    }

    var transferRestricted: Boolean by mutableStateOf(false)
        private set

    fun updateTransferRestricted(restricted: Boolean) {
        transferRestricted = restricted
    }

    var keycloakServerUrl: String? = null
        private set
    var keycloakClientId: String? = null
        private set
    var keycloakSas: String? = null
        private set
    var keycloakClientSecret: String? = null
        private set
    var keycloakSessionNumber: Long? = null
        private set
    fun setTransferKeycloakParameters(keycloakServerUrl: String, keycloakClientId: String, keycloakClientSecret: String?, fullSas: String, sessionNumber: Long) {
        this.keycloakServerUrl = keycloakServerUrl
        this.keycloakClientId = keycloakClientId
        this.keycloakClientSecret = keycloakClientSecret
        this.keycloakSas = fullSas
        this.keycloakSessionNumber = sessionNumber
    }

    var transferKeycloakAuthState: AuthState? = null
        private set

    fun saveTransferKeycloakAuthState(authState: AuthState) {
        transferKeycloakAuthState = authState
    }

    fun abortTransfer() {
        try {
            dialog?.let {
                it.setAbortTransfer()
                AppSingleton.getEngine().respondToDialog(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            dialog = null
        }
    }
    fun finalizeTransfer() {
        try {
            dialog?.let {
                it.setTransferSasAndDeviceUid(correctSas, transferSelectedDevice?.uid)
                AppSingleton.getEngine().respondToDialog(it)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
    fun createSimpleIdentity(onSuccess: (ObvIdentity?) -> Unit) {
        creatingSimpleIdentity = true
        val apiKey: UUID?
        @Suppress("SENSELESS_COMPARISON")
        if (BuildConfig.HARDCODED_API_KEY != null) {
            apiKey = UUID.fromString(BuildConfig.HARDCODED_API_KEY)
        } else {
            apiKey = null
        }
        AppSingleton.getInstance().generateIdentity(
            BuildConfig.SERVER_NAME,
            apiKey,
            JsonIdentityDetails(
                firstName,
                lastName,
                null,
                null
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            onSuccess
        ) { creatingSimpleIdentity = false }
    }


    var dialog : ObvDialog? by mutableStateOf(null)

    // endregion


    // region Backup File
    var backupType: Int = 0
    var backupName by mutableStateOf<String?>(null)
        private set
    var backupContent by mutableStateOf<ByteArray?>(null)
        private set
    var backupReady by mutableStateOf(false)
        private set

    var backupSeed by mutableStateOf<String?>(null)
        private set
    var backupKeyValid by mutableStateOf(false)
        private set

    fun clearSelectedBackup() {
        backupName = null
        backupContent= null
        backupReady = false
    }

    fun selectBackupFile(backupFileUri: Uri?, fileName: String?) {
        val contentResolver = App.getContext().contentResolver
        contentResolver.openInputStream(backupFileUri!!).use { `is` ->
            if (`is` == null) {
                throw Exception("Unable to read from provided Uri")
            }
            ByteArrayOutputStream().use { baos ->
                val buffer = ByteArray(4096)
                var c: Int
                while (`is`.read(buffer).also { c = it } != -1) {
                    baos.write(buffer, 0, c)
                }
                backupContent = baos.toByteArray()
                backupName = fileName
                backupType = BACKUP_TYPE_FILE
                backupReady = true
            }
        }
    }


    fun setBackupCloud(
        backupContent: ByteArray?,
        configuration: CloudProviderConfiguration,
        device: String?,
        timestamp: String?
    ) {
        this.backupContent = backupContent
        when (configuration.provider) {
            CloudProviderConfiguration.PROVIDER_WEBDAV -> backupName =
                App.getContext().getString(
                    R.string.text_description_webdav_backup,
                    configuration.account + " @ " + configuration.serverUrl,
                    device,
                    timestamp
                )

            CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE -> backupName =
                App.getContext().getString(
                    R.string.text_description_google_drive_backup,
                    configuration.account,
                    device,
                    timestamp
                )
        }
        backupType = BACKUP_TYPE_CLOUD
        backupReady = true
    }

    fun selectBackupCloud(activity: AppCompatActivity, onSuccess: () -> Unit) {
        val cloudProviderSignInDialogFragment = CloudProviderSignInDialogFragment.newInstance()
        cloudProviderSignInDialogFragment.setSignInContext(RESTORE_BACKUP)
        cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(object :
            OnCloudProviderConfigurationCallback {
            override fun onCloudProviderConfigurationSuccess(configuration: CloudProviderConfiguration) {
                BackupCloudProviderService.listBackups(
                    configuration,
                    object : OnBackupsListCallback {
                        override fun onListSuccess(backupTimestampAndNames: List<BackupItem>) {
                            if (backupTimestampAndNames.isEmpty()) {
                                App.toast(
                                    string.toast_message_error_no_backup_on_account,
                                    Toast.LENGTH_SHORT
                                )
                                return
                            }
                            Handler(Looper.getMainLooper()).post {
                                val builder =
                                    SecureAlertDialogBuilder(activity, style.CustomAlertDialog)
                                        .setTitle(string.dialog_title_select_cloud_backup_file)
                                        .setAdapter(
                                            object : ArrayAdapter<BackupItem?>(
                                                activity,
                                                layout.item_view_cloud_backup_item,
                                                backupTimestampAndNames
                                            ) {
                                                val layoutInflater =
                                                    LayoutInflater.from(activity)

                                                override fun getView(
                                                    position: Int,
                                                    convertView: View?,
                                                    parent: ViewGroup
                                                ): View {
                                                    return viewFromResource(
                                                        position,
                                                        convertView,
                                                        parent
                                                    )
                                                }

                                                private fun viewFromResource(
                                                    position: Int,
                                                    convertView: View?,
                                                    parent: ViewGroup
                                                ): View {
                                                    val view: View = convertView
                                                        ?: layoutInflater.inflate(
                                                            layout.item_view_cloud_backup_item,
                                                            parent,
                                                            false
                                                        )
                                                    val deviceTextView =
                                                        view.findViewById<TextView>(id.backup_device_text_view)
                                                    val timestampTextView =
                                                        view.findViewById<TextView>(id.backup_timestamp_text_view)
                                                    val backupItem = getItem(position) ?: return view
                                                    deviceTextView.text = backupItem.deviceName
                                                    timestampTextView.text =
                                                        StringUtils.getLongNiceDateString(
                                                            activity,
                                                            backupItem.timestamp
                                                        )
                                                    return view
                                                }
                                            }
                                        ) { dialog: DialogInterface?, which: Int ->
                                            clearSelectedBackup()
                                            val backupItem =
                                                backupTimestampAndNames[which]
                                            BackupCloudProviderService.downloadBackup(
                                                configuration,
                                                backupItem,
                                                object : OnBackupDownloadCallback {
                                                    override fun onDownloadSuccess(backupContent: ByteArray) {
                                                        setBackupCloud(
                                                            backupContent,
                                                            configuration,
                                                            backupItem.deviceName,
                                                            StringUtils.getLongNiceDateString(
                                                                activity,
                                                                backupItem.timestamp
                                                            ).toString()
                                                        )
                                                        onSuccess.invoke()
                                                    }

                                                    override fun onDownloadFailure(error: Int) {
                                                        App.toast(
                                                            string.toast_message_error_while_downloading_selected_backup,
                                                            Toast.LENGTH_SHORT
                                                        )
                                                    }
                                                })
                                        }
                                builder.create().show()
                            }
                            Logger.e(backupTimestampAndNames.toString())
                        }

                        override fun onListFailure(error: Int) {
                            when (error) {
                                BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED -> {
                                    App.toast(
                                        string.toast_message_error_selecting_automatic_backup_account,
                                        Toast.LENGTH_SHORT
                                    )
                                }

                                BackupCloudProviderService.ERROR_UNKNOWN -> {
                                    App.toast(
                                        string.toast_message_error_while_searching_for_backup_on_account,
                                        Toast.LENGTH_SHORT
                                    )
                                }

                                else -> {
                                    App.toast(
                                        string.toast_message_error_while_searching_for_backup_on_account,
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            }
                        }
                    })
            }

            override fun onCloudProviderConfigurationFailed() {
                // do nothing
            }
        })
        cloudProviderSignInDialogFragment.show(
            activity.supportFragmentManager,
            "CloudProviderSignInDialogFragment"
        )
    }

    // endregion


    // region Backup Key
    fun updateBackupSeed(backupSeed: String?) {
        this.backupSeed = backupSeed
        backupKeyValid = false
    }
    fun validateBackupSeed(): Int {
        return if (backupSeed == null || backupContent == null) {
            this.backupKeyValid = false
            -1
        } else {
            val verificationOutput =
                AppSingleton.getEngine().validateBackupSeed(backupSeed, backupContent)
            backupKeyValid = verificationOutput.verificationStatus == ObvBackupKeyVerificationOutput.STATUS_SUCCESS
            verificationOutput.verificationStatus
        }
    }

    // endregion
}

data class Device(
    val name: String,
    val uid: ByteArray?,
    val lastRegistrationTimestamp: Long? = null,
    val expirationTimestamp: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Device

        if (name != other.name) return false
        if (!uid.contentEquals(other.uid)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + uid.contentHashCode()
        return result
    }
}