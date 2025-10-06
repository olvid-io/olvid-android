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
package io.olvid.messenger.appdialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.text.util.LinkifyCompat
import com.fasterxml.jackson.core.type.TypeReference
import io.olvid.engine.engine.types.EngineAPI.ApiKeyPermission
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionPurchaseViewModel
import io.olvid.messenger.billing.SubscriptionUpdatedDialog
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity
import io.olvid.messenger.services.AvailableSpaceHelper
import io.olvid.messenger.services.BackupCloudProviderService.CloudProviderConfiguration
import io.olvid.messenger.settings.PrivacyPreferenceFragment
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.Companion.automaticBackupConfiguration

class AppDialogShowActivity : LockableActivity() {
    val appDialogShowViewModel by viewModels<AppDialogShowViewModel>()

    private val storageManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        AvailableSpaceHelper.refreshAvailableSpace(
            true
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showNextDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        App.releaseAppDialogShowing()
    }

    private fun showNextDialog() {
        var dialogTag = appDialogShowViewModel.currentlyShowingDialogTag
        if (dialogTag == null) {
            dialogTag = App.getNextDialogTag()
        }
        if (dialogTag == null) {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, R.anim.fade_out)
            }
            return
        }

        appDialogShowViewModel.currentlyShowingDialogTag = dialogTag

        val dialogParameters = App.getDialogParameters(dialogTag)
        if (dialogParameters == null) {
            continueWithNextDialog()
            return
        }

        when (dialogTag.dialogTag) {
            DIALOG_IDENTITY_DEACTIVATED -> {
                val ownedIdentityObject = dialogParameters.get(
                    DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY
                )
                if (ownedIdentityObject !is OwnedIdentity) {
                    continueWithNextDialog()
                } else {
                    val spinnerView =
                        layoutInflater.inflate(R.layout.dialog_view_deactivated_spinner, null)

                    val spinnerDialog = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_identity_deactivated)
                        .setView(spinnerView)
                        .setNegativeButton(
                            R.string.button_label_cancel
                        ) { dialog: DialogInterface?, which: Int -> continueWithNextDialog() }
                        .create()
                    spinnerDialog.show()

                    App.runThread {
                        val deviceList =
                            AppSingleton.getEngine().queryRegisteredOwnedDevicesFromServer(
                                ownedIdentityObject.bytesOwnedIdentity
                            )
                        runOnUiThread {
                            if (spinnerDialog.isShowing) {
                                spinnerDialog.dismiss()
                                val dialogFragment = IdentityDeactivatedDialogFragment.newInstance(
                                    ownedIdentityObject,
                                    deviceList
                                ) { this.continueWithNextDialog() }
                                dialogFragment.show(
                                    supportFragmentManager,
                                    DIALOG_IDENTITY_DEACTIVATED
                                )
                            }
                        }
                    }
                }
            }

            DIALOG_IDENTITY_ACTIVATED -> {
                val ownedIdentityObject = dialogParameters.get(
                    DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY
                )
                if (ownedIdentityObject !is OwnedIdentity) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    builder.setMessage(R.string.dialog_message_identity_activated)
                        .setTitle(R.string.dialog_title_identity_activated)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    builder.create().show()
                }
            }

            DIALOG_SUBSCRIPTION_UPDATED -> {
                val ownedIdentityObject = dialogParameters.get(
                    DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY
                )
                if (ownedIdentityObject !is OwnedIdentity) {
                    continueWithNextDialog()
                } else {
                    val ownedIdentity = ownedIdentityObject
                    if (ownedIdentity.keycloakManaged) {
                        continueWithNextDialog()
                    } else {
                        val purchaseViewModel : SubscriptionPurchaseViewModel by viewModels()
                        purchaseViewModel.updateBytesOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
                        setContentView(
                            ComposeView(this).apply {
                                setContent {
                                    SubscriptionUpdatedDialog(
                                        onDismissRequest = { continueWithNextDialog() },
                                        viewModel = purchaseViewModel,
                                        ownedIdentity = ownedIdentity
                                    )
                                }
                            }
                        )
                    }
                }
            }

            DIALOG_SUBSCRIPTION_REQUIRED -> {
                val featureObject = dialogParameters.get(DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY)
                if (featureObject !is ApiKeyPermission) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_subscription_required)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    val dialogView = layoutInflater.inflate(
                        R.layout.dialog_view_subscription_required,
                        null
                    )
                    val subscriptionMessageTextView =
                        dialogView.findViewById<TextView>(R.id.subscription_required_text_view)
                    builder.setView(dialogView)
                    val dialog: Dialog = builder.create()
                    dialogView.findViewById<View?>(R.id.check_subscription_button)
                        ?.setOnClickListener { v: View? ->
                            dialog.dismiss()
                            startActivity(
                                Intent(
                                    this,
                                    OwnedIdentityDetailsActivity::class.java
                                )
                            )
                        }
                    when (featureObject) {
                        ApiKeyPermission.CALL -> subscriptionMessageTextView.setText(R.string.dialog_message_subscription_required_call)
                        ApiKeyPermission.WEB_CLIENT -> subscriptionMessageTextView.setText(R.string.dialog_message_subscription_required_web_client)
                        ApiKeyPermission.MULTI_DEVICE -> subscriptionMessageTextView.setText(R.string.dialog_message_subscription_required_multi_device)
                    }
                    dialog.show()
                }
            }

            DIALOG_CALL_INITIATION_NOT_SUPPORTED -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_call_initiation_not_supported)
                    .setMessage(R.string.dialog_message_call_initiation_not_supported)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY
                )
                val clientIdObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY
                )
                val clientSecretObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY
                )
                val serverUrlObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY
                )
                if ((bytesOwnedIdentityObject !is ByteArray) || (clientIdObject !is String) || (clientSecretObject != null && clientSecretObject !is String)
                    || (serverUrlObject !is String)
                ) {
                    continueWithNextDialog()
                } else {
                    val dialogFragment = KeycloakAuthenticationRequiredDialogFragment.newInstance(
                        KeycloakAuthenticationRequiredDialogFragment.REASON.TOKEN_EXPIRED,
                        bytesOwnedIdentityObject,
                        serverUrlObject,
                        clientIdObject,
                        clientSecretObject
                    ) { this.continueWithNextDialog() }
                    dialogFragment.show(
                        supportFragmentManager,
                        DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED
                    )
                }
            }

            DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY
                )
                val serverUrlObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY
                )
                val clientSecretObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY
                )
                val serializedAuthStateObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY
                )
                if ((bytesOwnedIdentityObject !is ByteArray) || (serverUrlObject !is String) || (clientSecretObject != null && clientSecretObject !is String)
                    || (serializedAuthStateObject !is String)
                ) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_keycloak_identity_replacement)
                        .setMessage(R.string.dialog_message_keycloak_identity_replacement)
                        .setPositiveButton(R.string.button_label_revoke, null)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    val dialog = builder.create()

                    dialog.setOnShowListener { dialogInterface: DialogInterface? ->
                        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        button.setOnClickListener { v: View? ->
                            KeycloakManager.uploadOwnIdentity(
                                bytesOwnedIdentityObject,
                                object : KeycloakCallback<Void?> {
                                    override fun success(result: Void?) {
                                        App.toast(
                                            R.string.toast_message_keycloak_revoke_successful,
                                            Toast.LENGTH_SHORT,
                                            Gravity.CENTER
                                        )
                                        dialog.dismiss()
                                    }

                                    override fun failed(rfc: Int) {
                                        if (rfc == KeycloakTasks.RFC_AUTHENTICATION_REQUIRED
                                            || rfc == KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED
                                        ) {
                                            // in these cases, another app dialog has to be shown
                                            dialog.dismiss()
                                        } else if (rfc == KeycloakTasks.RFC_IDENTITY_REVOKED) {
                                            KeycloakManager.forceSelfTestAndReauthentication(
                                                bytesOwnedIdentityObject
                                            )
                                            dialog.dismiss()
                                        }

                                        App.toast(
                                            R.string.toast_message_unable_to_keycloak_revoke,
                                            Toast.LENGTH_SHORT,
                                            Gravity.CENTER
                                        )
                                    }
                                }
                            )
                        }
                    }
                    dialog.show()
                    return
                }
            }

            DIALOG_KEYCLOAK_USER_ID_CHANGED -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY
                )
                val clientIdObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY
                )
                val clientSecretObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY
                )
                val serverUrlObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY
                )
                if ((bytesOwnedIdentityObject !is ByteArray) || (clientIdObject !is String) || (clientSecretObject != null && clientSecretObject !is String)
                    || (serverUrlObject !is String)
                ) {
                    continueWithNextDialog()
                } else {
                    val dialogFragment = KeycloakAuthenticationRequiredDialogFragment.newInstance(
                        KeycloakAuthenticationRequiredDialogFragment.REASON.USER_ID_CHANGED,
                        bytesOwnedIdentityObject,
                        serverUrlObject,
                        clientIdObject,
                        clientSecretObject
                    ) { this.continueWithNextDialog() }
                    dialogFragment.show(
                        supportFragmentManager,
                        DIALOG_KEYCLOAK_USER_ID_CHANGED
                    )
                }
            }

            DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY
                )
                if (bytesOwnedIdentityObject !is ByteArray) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_keycloak_signature_key_changed)
                        .setMessage(R.string.dialog_message_keycloak_signature_key_changed)
                        .setPositiveButton(R.string.button_label_update_key, null)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    val dialog = builder.create()

                    dialog.setOnShowListener { dialogInterface: DialogInterface? ->
                        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        button.setOnClickListener { v: View? ->
                            runCatching {
                                AppSingleton.getEngine()
                                    .setOwnedIdentityKeycloakSignatureKey(
                                        bytesOwnedIdentityObject, null
                                    )
                                KeycloakManager.resetLatestGroupDownloadTimestamp(
                                    bytesOwnedIdentityObject
                                )
                                KeycloakManager.forceSyncManagedIdentity(
                                    bytesOwnedIdentityObject
                                )
                                runOnUiThread { dialog.dismiss() }
                            }.onFailure { e ->
                                App.toast(
                                    R.string.toast_message_unable_to_update_key,
                                    Toast.LENGTH_SHORT
                                )
                                e.printStackTrace()
                            }
                        }
                    }
                    dialog.show()
                }
            }

            DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY
                )
                if (bytesOwnedIdentityObject !is ByteArray) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_keycloak_identity_replacement_forbidden)
                        .setMessage(R.string.dialog_message_keycloak_identity_replacement_forbidden)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    builder.create().show()
                }
            }

            DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED -> {
                val bytesOwnedIdentityObject = dialogParameters.get(
                    DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY
                )
                if (bytesOwnedIdentityObject !is ByteArray) {
                    continueWithNextDialog()
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_keycloak_identity_was_revoked)
                        .setMessage(R.string.dialog_message_keycloak_identity_was_revoked)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                    builder.create().show()
                }
            }

            DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9 -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_sd_card_ringtone_bugged_android_9)
                    .setMessage(R.string.dialog_message_sd_card_ringtone_bugged_android_9)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_AVAILABLE_SPACE_LOW -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_available_space_low)
                    .setMessage(R.string.dialog_message_available_space_low)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? ->
                        AvailableSpaceHelper.acknowledgeWarning()
                        continueWithNextDialog()
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    builder.setNeutralButton(
                        R.string.button_label_manage_storage
                    ) { dialog: DialogInterface?, which: Int ->
                        val storageIntent = Intent()
                        storageIntent.action = StorageManager.ACTION_MANAGE_STORAGE
                        storageManagerLauncher.launch(storageIntent)
                    }
                }
                builder.create().show()
            }

            DIALOG_BACKUP_REQUIRES_SIGN_IN -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_backup_requires_sign_in)
                    .setMessage(R.string.dialog_message_backup_requires_sign_in)
                    .setPositiveButton(
                        R.string.button_label_sign_in
                    ) { dialog: DialogInterface?, which: Int ->
                        val cloudProviderSignInDialogFragment =
                            CloudProviderSignInDialogFragment.newInstance()
                        cloudProviderSignInDialogFragment.setSignInContext(
                            CloudProviderSignInDialogFragment.SignInContext.AUTOMATIC_BACKUP_SIGN_IN_REQUIRED
                        )
                        cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(
                            object : OnCloudProviderConfigurationCallback {
                                override fun onCloudProviderConfigurationSuccess(configuration: CloudProviderConfiguration?) {
                                    runCatching {
                                        automaticBackupConfiguration = configuration
                                        // notify the engine that auto-backup is set to true to initiate an immediate backup/upload
                                        AppSingleton.getEngine()
                                            .setAutoBackupEnabled(true, true)
                                    }.onFailure {
                                        onCloudProviderConfigurationFailed()
                                    }
                                }

                                override fun onCloudProviderConfigurationFailed() {
                                    App.toast(
                                        R.string.toast_message_error_selecting_automatic_backup_account,
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            })
                        cloudProviderSignInDialogFragment.show(
                            supportFragmentManager,
                            "CloudProviderSignInDialogFragment"
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setNeutralButton(
                        R.string.button_label_app_settings
                    ) { dialog: DialogInterface?, which: Int ->
                        val intent = Intent(this, SettingsActivity::class.java)
                        intent.putExtra(
                            SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA,
                            SettingsActivity.PREF_HEADER_KEY_BACKUP
                        )
                        startActivity(intent)
                    }
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }

                builder.create().show()
            }

            DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_hidden_profile_but_no_policy)
                    .setMessage(R.string.dialog_message_hidden_profile_but_no_policy)
                    .setPositiveButton(
                        R.string.button_label_ok
                    ) { dialogInterface: DialogInterface?, which: Int ->
                        (dialogInterface as AlertDialog).setOnDismissListener(null)
                        PrivacyPreferenceFragment.showHiddenProfileClosePolicyChooserDialog(
                            this
                        ) { this.continueWithNextDialog() }
                    }
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_INTRODUCING_MULTI_PROFILE -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_introducing_multi_profile)
                    .setMessage(R.string.dialog_message_introducing_multi_profile)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_INTRODUCING_GROUPS_V2 -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_introducing_groups_v2)
                    .setMessage(R.string.dialog_message_introducing_groups_v2)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_INTRODUCING_MENTIONS -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_introducing_mentions)
                    .setMessage(R.string.dialog_message_introducing_mentions)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                builder.create().show()
            }

            DIALOG_INTRODUCING_MARKDOWN -> {
                val message = SpannableString(getText(R.string.dialog_message_introducing_markdown))
                LinkifyCompat.addLinks(message, Linkify.WEB_URLS)
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_introducing_markdown)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                val dialog = builder.create()
                dialog.setOnShowListener { dialogInterface: DialogInterface? ->
                    val messageView = dialog.findViewById<View?>(android.R.id.message)
                    if (messageView is TextView) {
                        messageView.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
                dialog.show()
            }

            DIALOG_INTRODUCING_MULTI_DEVICE -> {
                val message =
                    SpannableString(getText(R.string.dialog_message_introducing_multi_device))
                LinkifyCompat.addLinks(message, Linkify.WEB_URLS)
                val formattedMessage = message.formatMarkdown(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_introducing_multi_device)
                    .setMessage(formattedMessage)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                val dialog = builder.create()
                dialog.setOnShowListener { dialogInterface: DialogInterface? ->
                    val messageView = dialog.findViewById<View?>(android.R.id.message)
                    if (messageView is TextView) {
                        messageView.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
                dialog.show()
            }

            else -> {
                if (dialogTag.dialogTag.startsWith(DIALOG_CERTIFICATE_CHANGED)) {
                    val untrustedCertificateIdObject = dialogParameters.get(
                        DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY
                    )
                    val lastTrustedCertificateIdObject = dialogParameters.get(
                        DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY
                    )
                    if (untrustedCertificateIdObject !is Long || (lastTrustedCertificateIdObject != null && lastTrustedCertificateIdObject !is Long)) {
                        continueWithNextDialog()
                    } else {
                        AndroidNotificationManager.clearConnectionBlockedNotification(
                            untrustedCertificateIdObject
                        )

                        App.runThread {
                            val untrustedCertificate = getInstance().knownCertificateDao().get(
                                untrustedCertificateIdObject
                            )
                            if (untrustedCertificate == null || untrustedCertificate.isTrusted) {
                                continueWithNextDialog()
                                return@runThread
                            }
                            val lastTrustedCertificate =
                                if (lastTrustedCertificateIdObject == null) null else getInstance().knownCertificateDao()
                                    .get(
                                        lastTrustedCertificateIdObject
                                    )


                            val dialogView = layoutInflater.inflate(
                                R.layout.dialog_view_untrusted_certificate,
                                null
                            )
                            (dialogView.findViewById<View?>(R.id.domain_name_text_view) as TextView).text =
                                untrustedCertificate.domainName

                            runCatching {
                                val issuers = AppSingleton.getJsonObjectMapper()
                                    .readValue(
                                        untrustedCertificate.issuers,
                                        object : TypeReference<Array<String>>() {
                                        })
                                val sb = StringBuilder()
                                var i = 0
                                while (i < issuers.size) {
                                    if (i != 0) {
                                        sb.append("\n")
                                    }
                                    sb.append(i + 1).append(": ").append(issuers[i])
                                    i++
                                }
                                (dialogView.findViewById<View?>(R.id.new_cert_issuers_text_view) as TextView?)?.text =
                                    sb.toString()
                            }.onFailure {
                                (dialogView.findViewById<View?>(R.id.new_cert_issuers_text_view) as TextView?)?.setText(
                                    R.string.error_text_issuers
                                )
                            }

                            (dialogView.findViewById<View?>(R.id.new_cert_expiration_text_view) as TextView?)?.text =
                                StringUtils.getPreciseAbsoluteDateString(
                                    this,
                                    untrustedCertificate.expirationTimestamp,
                                    getString(R.string.text_date_time_separator)
                                )

                            dialogView.findViewById<View?>(R.id.new_cert_group)?.clipToOutline =
                                true
                            dialogView.findViewById<View?>(R.id.new_cert_group)
                                ?.setOnClickListener { v: View? ->
                                    val clipboard =
                                        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(
                                        getString(R.string.text_certificate_chain),
                                        untrustedCertificate.encodedFullChain
                                    )
                                    clipboard.setPrimaryClip(clip)
                                    App.toast(
                                        R.string.toast_message_certificate_chain_copied,
                                        Toast.LENGTH_SHORT
                                    )
                                }

                            if (lastTrustedCertificate == null) {
                                // this case should never happen, still, we handle it :)
                                (dialogView.findViewById<View?>(R.id.risk_level_explanation_text_view) as TextView?)?.setText(
                                    R.string.text_explanation_no_trusted_certificate
                                )
                                dialogView.findViewById<View?>(R.id.trusted_cert_group)?.visibility =
                                    View.GONE
                            } else {
                                if (untrustedCertificate.issuers == lastTrustedCertificate.issuers) {
                                    // the issuers are the same --> probably a renewal
                                    (dialogView.findViewById<View?>(R.id.risk_level_explanation_text_view) as TextView).setText(
                                        R.string.text_explanation_certificate_renewal
                                    )
                                } else {
                                    // the issuers changed --> be careful
                                    (dialogView.findViewById<View?>(R.id.risk_level_image_view) as ImageView?)?.setImageResource(
                                        R.drawable.ic_error_outline
                                    )
                                    dialogView.findViewById<View?>(R.id.new_cert_group)
                                        ?.setBackgroundResource(R.drawable.background_error_message)

                                    (dialogView.findViewById<View?>(R.id.risk_level_explanation_text_view) as TextView?)?.setText(
                                        R.string.text_explanation_certificate_issuers_changed
                                    )
                                }

                                runCatching {
                                    val issuers = AppSingleton.getJsonObjectMapper()
                                        .readValue(
                                            lastTrustedCertificate.issuers,
                                            object : TypeReference<Array<String>>() {
                                            })
                                    val sb = StringBuilder()
                                    var i = 0
                                    while (i < issuers.size) {
                                        if (i != 0) {
                                            sb.append("\n")
                                        }
                                        sb.append(i + 1).append(": ").append(issuers[i])
                                        i++
                                    }
                                    (dialogView.findViewById<View?>(R.id.trusted_cert_issuers_text_view) as TextView?)?.text =
                                        sb.toString()
                                }.onFailure {
                                    (dialogView.findViewById<View?>(R.id.trusted_cert_issuers_text_view) as TextView?)?.setText(
                                        R.string.error_text_issuers
                                    )
                                }

                                (dialogView.findViewById<View?>(R.id.trusted_cert_expiration_text_view) as TextView).text =
                                    StringUtils.getPreciseAbsoluteDateString(
                                        this,
                                        lastTrustedCertificate.expirationTimestamp,
                                        getString(R.string.text_date_time_separator)
                                    )

                                dialogView.findViewById<View?>(R.id.trusted_cert_group)?.clipToOutline =
                                    true
                                dialogView.findViewById<View?>(R.id.trusted_cert_group)
                                    ?.setOnClickListener { v: View? ->
                                        val clipboard =
                                            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            getString(R.string.text_certificate_chain),
                                            lastTrustedCertificate.encodedFullChain
                                        )
                                        clipboard.setPrimaryClip(clip)
                                        App.toast(
                                            R.string.toast_message_certificate_chain_copied,
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                            }

                            val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setView(dialogView)
                                .setPositiveButton(
                                    R.string.button_label_trust_certificate
                                ) { dialog: DialogInterface?, which: Int ->
                                    App.runThread {
                                        AppSingleton.getSslSocketFactory()
                                            .trustCertificateInDb(untrustedCertificate)
                                    }
                                }
                                .setNegativeButton(R.string.button_label_do_not_trust_yet, null)
                                .setOnDismissListener { dialog: DialogInterface? -> continueWithNextDialog() }
                            runOnUiThread { builder.create().show() }
                        }
                    }
                } else {
                    continueWithNextDialog()
                }
            }
        }
    }

    private fun continueWithNextDialog() {
        val dialogTag = appDialogShowViewModel.currentlyShowingDialogTag
        dialogTag?.let {
            App.removeDialog(dialogTag)
            appDialogShowViewModel.currentlyShowingDialogTag = null
        }
        showNextDialog()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        const val DIALOG_IDENTITY_DEACTIVATED: String = "identity_deactivated"
        const val DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY: String =
            "owned_identity" // OwnedIdentity

        const val DIALOG_IDENTITY_ACTIVATED: String = "identity_activated"
        const val DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY: String =
            "owned_identity" // OwnedIdentity

        const val DIALOG_SUBSCRIPTION_UPDATED: String = "subscription_updated"
        const val DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY: String =
            "owned_identity" // OwnedIdentity

        const val DIALOG_SUBSCRIPTION_REQUIRED: String = "subscription_required"
        const val DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY: String =
            "feature" // EngineAPI.ApiKeyPermission

        const val DIALOG_CALL_INITIATION_NOT_SUPPORTED: String = "call_initiation_not_supported"

        const val DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED: String =
            "keycloak_authentication_required"
        const val DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]
        const val DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY: String =
            "client_id" // String
        const val DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY: String =
            "client_secret" // String (nullable)
        const val DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY: String =
            "server_url" // String

        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT: String = "keycloak_identity_replacement"
        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]
        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY: String =
            "server_url" // String
        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY: String =
            "client_secret" // String (nullable)
        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY: String =
            "serialized_auth_state" // String

        const val DIALOG_KEYCLOAK_USER_ID_CHANGED: String = "keycloak_user_id_changed"
        const val DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]
        const val DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY: String = "client_id" // String
        const val DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY: String =
            "client_secret" // String (nullable)
        const val DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY: String = "server_url" // String

        const val DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED: String = "keycloak_signature_key_changed"
        const val DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]

        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN: String =
            "keycloak_identity_replacement_forbidden"
        const val DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]

        const val DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED: String = "keycloak_identity_was_revoked"
        const val DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY: String =
            "owned_identity" // byte[]

        const val DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9: String =
            "sd_card_ringtone_bugged_android_9"

        const val DIALOG_CERTIFICATE_CHANGED: String = "certificate_changed"
        const val DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY: String =
            "untrusted_certificate_id"
        const val DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY: String =
            "last_trusted_certificate_id"

        const val DIALOG_AVAILABLE_SPACE_LOW: String = "available_space_low"

        const val DIALOG_BACKUP_REQUIRES_SIGN_IN: String = "backup_requires_sign_in"

        const val DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY: String =
            "configure_hidden_profile_close_policy"


        const val DIALOG_INTRODUCING_MULTI_PROFILE: String = "introducing_multi_profile"
        const val DIALOG_INTRODUCING_GROUPS_V2: String = "introducing_groups_v2"
        const val DIALOG_INTRODUCING_MENTIONS: String = "introducing_mentions"
        const val DIALOG_INTRODUCING_MARKDOWN: String = "introducing_markdown"
        const val DIALOG_INTRODUCING_MULTI_DEVICE: String = "introducing_multi_device"
    }
}