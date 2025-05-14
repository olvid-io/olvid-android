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

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.ObvBackupKeyInformation
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ComposeViewPreference
import io.olvid.messenger.customClasses.NoClickSwitchPreference
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.services.BackupCloudProviderService.CloudProviderConfiguration
import io.olvid.messenger.settings.SettingsActivity.Companion.automaticBackupConfiguration
import io.olvid.messenger.settings.SettingsActivity.Companion.useAutomaticBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupPreferenceFragment : PreferenceFragmentCompat(), EngineNotificationListener {
    private val viewModel: BackupPreferenceViewModel by activityViewModels()

    private var engineRegistrationNumber: Long? = null

    var reloadBackupConfigurationPreference: Preference? = null
    var generateBackupKeyPreference: ComposeViewPreference? = null
    var manualBackupPreference: Preference? = null
    var enableAutomaticBackupPreference: NoClickSwitchPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_backup, rootKey)

        val screen = preferenceScreen

        reloadBackupConfigurationPreference =
            screen.findPreference<Preference?>(SettingsActivity.PREF_KEY_RELOAD_BACKUP_CONFIGURATION)
        generateBackupKeyPreference =
            screen.findPreference<ComposeViewPreference?>(SettingsActivity.PREF_KEY_GENERATE_NEW_BACKUP_KEY)
        manualBackupPreference =
            screen.findPreference<Preference?>(SettingsActivity.PREF_KEY_MANUAL_BACKUP)
        enableAutomaticBackupPreference =
            screen.findPreference<NoClickSwitchPreference?>(SettingsActivity.PREF_KEY_ENABLE_AUTOMATIC_BACKUP)

        if (generateBackupKeyPreference != null && manualBackupPreference != null && enableAutomaticBackupPreference != null && reloadBackupConfigurationPreference != null) {
            refreshBackupPreferences()

            reloadBackupConfigurationPreference!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    refreshBackupPreferences()
                    true
                }

            generateBackupKeyPreference!!.setContent {
                EnableBackupsV2Card(
                    hasLegacyBackups = true,
                    onClick = {
                        activity?.supportFragmentManager?.let {
                            BackupV2KeyGenerationDialogFragment().apply {
                                onDismissListener = {
                                    if (AppSingleton.getEngine().deviceBackupSeed != null) {
                                        // delete the legacy backup keys
                                        AppSingleton.getEngine().stopLegacyBackups()

                                        // switch to the backup v2 setting fragment
                                        activity?.apply {
                                            supportFragmentManager.popBackStackImmediate()
                                            navigateToSettingsFragment(BackupV2PreferenceFragment::class.java.name)
                                        }
                                    }
                                }
                                show(it, null)
                            }
                        }
                    }
                )
            }


            manualBackupPreference!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    App.runThread(Runnable { AppSingleton.getEngine().initiateBackup(true) })
                    true
                }

            enableAutomaticBackupPreference!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { pref: Preference? ->
                    automaticBackupClicked()
                    true
                }
        }

        val manageCloudBackupsPreference = screen.findPreference<Preference?>(SettingsActivity.PREF_KEY_MANAGE_CLOUD_BACKUPS)
        manageCloudBackupsPreference?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                val manageCloudBackupsDialogFragment = ManageCloudBackupsDialogFragment.newInstance()
                manageCloudBackupsDialogFragment.show(
                    getChildFragmentManager(),
                    "ManageCloudBackupsDialogFragment"
                )
                true
            }

        engineRegistrationNumber = null
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, this)
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, this)
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL, this)
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, this)
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, this)
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, this)
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL,
            this
        )
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, this)
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, this)
    }

    fun refreshBackupPreferences() {
        if (generateBackupKeyPreference != null && manualBackupPreference != null && enableAutomaticBackupPreference != null && reloadBackupConfigurationPreference != null) {
            var backupKeyInformation: ObvBackupKeyInformation?
            var deviceBackupSeed: String?
            try {
                backupKeyInformation = AppSingleton.getEngine().backupKeyInformation
                deviceBackupSeed = AppSingleton.getEngine().deviceBackupSeed
            } catch (e: Exception) {
                e.printStackTrace()
                reloadBackupConfigurationPreference!!.isVisible = true
                generateBackupKeyPreference!!.isEnabled = false
                manualBackupPreference!!.isEnabled = false
                enableAutomaticBackupPreference!!.isEnabled = false

                failedLoads++
                if (failedLoads <= 3) {
                    App.toast(
                        R.string.toast_message_unable_to_load_backup_configuration,
                        Toast.LENGTH_SHORT
                    )
                } else {
                    val builder = SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unable_to_load_backup_configuration)
                        .setMessage(R.string.dialog_message_unable_to_load_backup_configuration)
                        .setPositiveButton(
                            R.string.button_label_generate_new_backup_key,
                            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                                failedLoads = 0
                                AppSingleton.getEngine()
                                    .generateDeviceBackupSeed(BuildConfig.SERVER_NAME)
                            })
                        .setNegativeButton(R.string.button_label_cancel, null)
                    requireActivity().runOnUiThread(Runnable { builder.create().show() })
                }
                return
            }

            viewModel.backupKeyInformation = backupKeyInformation
            viewModel.deviceBackupSeed = deviceBackupSeed

            reloadBackupConfigurationPreference!!.isVisible = false
            failedLoads = 0
            if (backupKeyInformation == null) {
                generateBackupKeyPreference!!.setTitle(R.string.pref_generate_new_backup_key_title)
                val sb = SpannableStringBuilder()
                sb.append(getString(R.string.pref_generate_new_backup_key_summary_no_key))
                generateBackupKeyPreference!!.setSummary(sb)
                manualBackupPreference!!.isEnabled = false
                manualBackupPreference!!.setSummary(R.string.pref_manual_backup_summary)
                enableAutomaticBackupPreference!!.isEnabled = false
                enableAutomaticBackupPreference!!.setSummary(R.string.pref_enable_automatic_backup_summary)
            } else {
                generateBackupKeyPreference!!.setTitle(R.string.pref_generate_new_backup_key_title_or_verify)
                val sb = SpannableStringBuilder()
                if (backupKeyInformation.lastSuccessfulKeyVerificationTimestamp == 0L) {
                    sb.append(
                        getString(
                            R.string.pref_generate_new_backup_key_summary_never_verified,
                            StringUtils.getLongNiceDateString(
                                requireActivity(),
                                backupKeyInformation.keyGenerationTimestamp
                            )
                        )
                    )
                } else {
                    sb.append(
                        getString(
                            R.string.pref_generate_new_backup_key_summary,
                            StringUtils.getLongNiceDateString(
                                requireActivity(),
                                backupKeyInformation.keyGenerationTimestamp
                            ),
                            backupKeyInformation.successfulVerificationCount,
                            StringUtils.getLongNiceDateString(
                                requireActivity(),
                                backupKeyInformation.lastSuccessfulKeyVerificationTimestamp
                            )
                        )
                    )
                }
                generateBackupKeyPreference!!.setSummary(sb)

                manualBackupPreference!!.isEnabled = true
                if (backupKeyInformation.lastBackupExport == 0L) {
                    manualBackupPreference!!.setSummary(R.string.pref_manual_backup_summary)
                } else {
                    manualBackupPreference!!.setSummary(
                        getString(
                            R.string.pref_manual_backup_summary_time,
                            StringUtils.getLongNiceDateString(
                                requireActivity(),
                                backupKeyInformation.lastBackupExport
                            )
                        )
                    )
                }
                enableAutomaticBackupPreference!!.isEnabled = true
                var summaryString = getString(R.string.pref_enable_automatic_backup_summary)
                if (useAutomaticBackup()) {
                    val configuration = automaticBackupConfiguration
                    if (configuration != null && configuration.provider != null) {
                        when (configuration.provider) {
                            CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE -> summaryString += getString(
                                R.string.pref_enable_automatic_backup_summary_google_drive_account,
                                configuration.account
                            )

                            CloudProviderConfiguration.PROVIDER_WEBDAV, CloudProviderConfiguration.PROVIDER_WEBDAV_WRITE_ONLY -> summaryString += getString(
                                R.string.pref_enable_automatic_backup_summary_webdav_account,
                                configuration.account,
                                configuration.serverUrl
                            )
                        }
                    }
                    if (backupKeyInformation.lastBackupUpload != 0L) {
                        summaryString += getString(
                            R.string.pref_enable_automatic_backup_summary_time,
                            StringUtils.getLongNiceDateString(
                                requireActivity(),
                                backupKeyInformation.lastBackupUpload
                            )
                        )
                    }
                }
                enableAutomaticBackupPreference!!.setSummary(summaryString)
            }
        }
    }

    fun automaticBackupClicked() {
        enableAutomaticBackupPreference?.let {
            if (it.isChecked) {
                val builder = SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_backup_choose_deactivate_or_change_account)
                    .setNegativeButton(
                        R.string.button_label_deactivate_auto_backup,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            // clear any stored webdav configuration
                            try {
                                automaticBackupConfiguration = null
                            } catch (_: Exception) {
                            }
                            GoogleServicesUtils.requestGoogleSignOut(activity)
                            deactivateAutomaticBackups()
                        })
                    .setPositiveButton(
                        R.string.button_label_switch_account,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            openSignInDialog(true)
                        })
                builder.create().show()
            } else {
                openSignInDialog(false)
            }
        }
    }

    private fun openSignInDialog(switchingAccount: Boolean) {
        val cloudProviderSignInDialogFragment = CloudProviderSignInDialogFragment.newInstance()
        cloudProviderSignInDialogFragment.setSignInContext(CloudProviderSignInDialogFragment.SignInContext.ACTIVATE_AUTOMATIC_BACKUPS)
        cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(object :
            OnCloudProviderConfigurationCallback {
            override fun onCloudProviderConfigurationSuccess(configuration: CloudProviderConfiguration?) {
                try {
                    automaticBackupConfiguration = configuration
                    activity!!.runOnUiThread(Runnable { activateAutomaticBackups() })
                } catch (e: Exception) {
                    e.printStackTrace()
                    onCloudProviderConfigurationFailed()
                }
            }

            override fun onCloudProviderConfigurationFailed() {
                if (!switchingAccount) {
                    App.toast(
                        R.string.toast_message_error_selecting_automatic_backup_account,
                        Toast.LENGTH_SHORT
                    )
                    activity!!.runOnUiThread(Runnable { deactivateAutomaticBackups() })
                }
            }
        })
        cloudProviderSignInDialogFragment.show(
            getChildFragmentManager(),
            "CloudProviderSignInDialogFragment"
        )
    }

    fun activateAutomaticBackups() {
        if (enableAutomaticBackupPreference != null) {
            if (enableAutomaticBackupPreference!!.callChangeListener(true)) {
                enableAutomaticBackupPreference!!.setChecked(true)
                AppSingleton.getEngine().setAutoBackupEnabled(true, true)
            }
            refreshBackupPreferences()
        }
    }

    fun deactivateAutomaticBackups() {
        if (enableAutomaticBackupPreference != null) {
            if (enableAutomaticBackupPreference!!.callChangeListener(false)) {
                enableAutomaticBackupPreference!!.setChecked(false)
                AppSingleton.getEngine().setAutoBackupEnabled(false, true)
            }
            refreshBackupPreferences()
        }
    }


    @Suppress("deprecation")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SAVE_BACKUP) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { uri ->
                    val encryptedContent = viewModel.exportBackupContent
                    if (StringUtils.validateUri(uri)) {
                        App.runThread {
                            try {
                                requireActivity().contentResolver.openOutputStream(uri)
                                    .use { os ->
                                        if (os == null) {
                                            throw Exception("Unable to write to provided Uri")
                                        }
                                        os.write(encryptedContent)
                                        App.toast(
                                            R.string.toast_message_backup_saved,
                                            Toast.LENGTH_SHORT
                                        )
                                        AppSingleton.getEngine().markBackupExported(
                                            viewModel.exportBackupKeyUid,
                                            viewModel.exportBackupVersion
                                        )
                                        requireActivity().runOnUiThread(Runnable { this.refreshBackupPreferences() })
                                        return@runThread
                                    }
                            } catch (_: Exception) {
                                App.toast(
                                    R.string.toast_message_failed_to_save_backup,
                                    Toast.LENGTH_SHORT
                                )
                            }
                            AppSingleton.getEngine().discardBackup(
                                viewModel.exportBackupKeyUid,
                                viewModel.exportBackupVersion
                            )
                        }
                        return
                    }
                }
                AppSingleton.getEngine().discardBackup(
                    viewModel.exportBackupKeyUid,
                    viewModel.exportBackupVersion
                )
            }
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String?, Any?>) {
        when (notificationName) {
            EngineNotifications.NEW_BACKUP_SEED_GENERATED -> {
                requireActivity().runOnUiThread(Runnable {
                    val backupSeed =
                        userInfo.get(EngineNotifications.NEW_BACKUP_SEED_GENERATED_SEED_KEY) as String?
                    if (backupSeed != null) {
                        val dialogView =
                            getLayoutInflater().inflate(R.layout.dialog_view_new_backup_key, null)
                        val backupSeedTextView =
                            dialogView.findViewById<TextView>(R.id.backup_seed_text_view)
                        backupSeedTextView.text = backupSeed

                        val builder =
                            SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_new_backup_key)
                                .setView(dialogView)
                                .setPositiveButton(
                                    R.string.button_label_key_copied_close_window,
                                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                                        if (!useAutomaticBackup()) {
                                            val secondDialogView = getLayoutInflater().inflate(
                                                R.layout.dialog_view_backup_choices,
                                                null
                                            )
                                            val fileButton =
                                                secondDialogView.findViewById<Button>(R.id.button_file)
                                            val cloudButton =
                                                secondDialogView.findViewById<Button>(R.id.button_cloud)

                                            val secondDialog = SecureAlertDialogBuilder(
                                                requireActivity(),
                                                R.style.CustomAlertDialog
                                            )
                                                .setTitle(R.string.dialog_title_backup_choices)
                                                .setView(secondDialogView)
                                                .setNegativeButton(
                                                    R.string.button_label_cancel,
                                                    null
                                                )
                                                .create()

                                            fileButton.setOnClickListener(View.OnClickListener { v: View? ->
                                                App.runThread(Runnable {
                                                    AppSingleton.getEngine().initiateBackup(true)
                                                })
                                                secondDialog.dismiss()
                                            })
                                            cloudButton.setOnClickListener(View.OnClickListener { v: View? ->
                                                automaticBackupClicked()
                                                secondDialog.dismiss()
                                            })
                                            secondDialog.show()
                                        }
                                    })

                        val dialog: Dialog = builder.create()
                        dialog.window?.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                        dialog.show()
                    }
                    refreshBackupPreferences()
                })
            }

            EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL -> {
                requireActivity().runOnUiThread(Runnable { this.refreshBackupPreferences() })
            }

            EngineNotifications.BACKUP_SEED_GENERATION_FAILED -> {
                App.toast(R.string.toast_message_backup_seed_generation_failed, Toast.LENGTH_SHORT)
            }

            EngineNotifications.BACKUP_FOR_EXPORT_FINISHED -> {
                val bytesBackupKeyUid =
                    userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_BYTES_BACKUP_KEY_UID_KEY) as ByteArray?
                val version =
                    userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY) as Int?
                val encryptedContent =
                    userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY) as ByteArray?

                if (encryptedContent == null || version == null || bytesBackupKeyUid == null) {
                    return
                }

                viewModel.exportBackupContent = encryptedContent
                viewModel.exportBackupKeyUid = bytesBackupKeyUid
                viewModel.exportBackupVersion = version
                val backupFileName = "Olvid backup - " + SimpleDateFormat(
                    App.TIMESTAMP_FILE_NAME_FORMAT,
                    Locale.ENGLISH
                ).format(Date()) + ".olvidbackup"

                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_TITLE, backupFileName)
                App.startActivityForResult(this, intent, REQUEST_CODE_SAVE_BACKUP)
            }

            EngineNotifications.BACKUP_FOR_EXPORT_FAILED -> {
                App.toast(R.string.toast_message_backup_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.engineRegistrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return engineRegistrationNumber!!
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return engineRegistrationNumber != null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.almostWhite))
    }

    companion object {
        const val REQUEST_CODE_SAVE_BACKUP: Int = 10089

        private var failedLoads = 0
    }
}
