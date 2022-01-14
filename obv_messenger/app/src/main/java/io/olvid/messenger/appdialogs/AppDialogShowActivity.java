/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.appdialogs;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.view.Gravity;
import android.view.View;
import android.app.Dialog;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

import java.util.HashMap;

import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.OwnedIdentityDetailsActivity;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.KnownCertificate;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.openid.KeycloakTasks;
import io.olvid.messenger.services.AvailableSpaceHelper;
import io.olvid.messenger.settings.PrivacyPreferenceFragment;
import io.olvid.messenger.settings.SettingsActivity;


public class AppDialogShowActivity extends LockableActivity {
    public static final String DIALOG_IDENTITY_DEACTIVATED = "identity_deactivated";
    public static final String DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY = "owned_identity"; // OwnedIdentity

    public static final String DIALOG_IDENTITY_ACTIVATED = "identity_activated";
    public static final String DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY = "owned_identity"; // OwnedIdentity

    public static final String DIALOG_SUBSCRIPTION_UPDATED = "subscription_updated";
    public static final String DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // OwnedIdentity

    public static final String DIALOG_SUBSCRIPTION_REQUIRED = "subscription_required";
    public static final String DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY = "feature"; // EngineAPI.ApiKeyPermission

    public static final String DIALOG_NEW_VERSION_AVAILABLE = "new_version_available";

    public static final String DIALOG_OUTDATED_VERSION = "outdated_version";

    public static final String DIALOG_CALL_INITIATION_NOT_SUPPORTED = "call_initiation_not_supported";

    public static final String DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED = "keycloak_authentication_required";
    public static final String DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY = "client_id"; // String
    public static final String DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY = "client_secret"; // String (nullable)
    public static final String DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY = "server_url"; // String

    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT = "keycloak_identity_replacement";
    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY = "server_url"; // String
    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY = "client_secret"; // String (nullable)
    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY = "serialized_auth_state"; // String

    public static final String DIALOG_KEYCLOAK_USER_ID_CHANGED = "keycloak_user_id_changed";
    public static final String DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY = "client_id"; // String
    public static final String DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY = "client_secret"; // String (nullable)
    public static final String DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY = "server_url"; // String

    public static final String DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED = "keycloak_signature_key_changed";
    public static final String DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]

    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN = "keycloak_identity_replacement_forbidden";
    public static final String DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]

    public static final String DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED = "keycloak_identity_was_revoked";
    public static final String DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]

    public static final String DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9 = "sd_card_ringtone_bugged_android_9";

    public static final String DIALOG_CERTIFICATE_CHANGED = "certificate_changed";
    public static final String DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY = "untrusted_certificate_id";
    public static final String DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY = "last_trusted_certificate_id";

    public static final String DIALOG_AVAILABLE_SPACE_LOW = "available_space_low";

    public static final String DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN = "backup_requires_drive_sign_in";
    public static final int DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN_REQUEST_CODE = 726;

    public static final String DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY = "configure_hidden_profile_close_policy";
    public static final String DIALOG_INTRODUCING_MULTI_PROFILE = "introducing_multi_profile";

    AppDialogShowViewModel appDialogShowViewModel;

    private final ActivityResultLauncher<Intent> storageManagerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult activityResult) -> AvailableSpaceHelper.refreshAvailableSpace(true));


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // no layout here, app is transparent and only shows dialogs

        appDialogShowViewModel = new ViewModelProvider(this).get(AppDialogShowViewModel.class);
        showNextDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.releaseAppDialogShowing();
    }

    private void showNextDialog() {
        AppDialogTag dialogTag = appDialogShowViewModel.getCurrentlyShowingDialogTag();
        if (dialogTag == null) {
            dialogTag = App.getNextDialogTag();
        }
        if (dialogTag == null) {
            finish();
            overridePendingTransition(0, R.anim.fade_out);
            return;
        }

        appDialogShowViewModel.setCurrentlyShowingDialogTag(dialogTag);

        HashMap<String, Object> dialogParameters = App.getDialogParameters(dialogTag);
        if (dialogParameters == null) {
            continueWithNextDialog();
            return;
        }

        switch (dialogTag.dialogTag) {
            case DIALOG_IDENTITY_DEACTIVATED: {
                Object ownedIdentityObject = dialogParameters.get(DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY);
                if (!(ownedIdentityObject instanceof OwnedIdentity)) {
                    continueWithNextDialog();
                } else {
                    IdentityDeactivatedDialogFragment dialogFragment = IdentityDeactivatedDialogFragment.newInstance((OwnedIdentity) ownedIdentityObject, this::continueWithNextDialog);
                    dialogFragment.show(getSupportFragmentManager(), DIALOG_IDENTITY_DEACTIVATED);
                }
                break;
            }
            case DIALOG_IDENTITY_ACTIVATED: {
                Object ownedIdentityObject = dialogParameters.get(DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY);
                if (!(ownedIdentityObject instanceof OwnedIdentity)) {
                    continueWithNextDialog();
                } else {
                    SecureAlertDialogBuilder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
                    builder.setMessage(R.string.dialog_message_identity_activated)
                            .setTitle(R.string.dialog_title_identity_activated)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    builder.create().show();
                }
                break;
            }
            case DIALOG_SUBSCRIPTION_UPDATED: {
                Object ownedIdentityObject = dialogParameters.get(DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY);
                if (!(ownedIdentityObject instanceof OwnedIdentity)) {
                    continueWithNextDialog();
                } else {
                    OwnedIdentity ownedIdentity = (OwnedIdentity) ownedIdentityObject;
                    if (ownedIdentity.keycloakManaged) {
                        continueWithNextDialog();
                    } else {
                        SubscriptionUpdatedDialogFragment dialogFragment = SubscriptionUpdatedDialogFragment.newInstance(ownedIdentity.bytesOwnedIdentity, ownedIdentity.getApiKeyStatus(), ownedIdentity.apiKeyExpirationTimestamp, ownedIdentity.getApiKeyPermissions(), this::continueWithNextDialog);
                        dialogFragment.show(getSupportFragmentManager(), DIALOG_SUBSCRIPTION_UPDATED);
                    }
                }
                break;
            }
            case DIALOG_SUBSCRIPTION_REQUIRED: {
                Object featureObject = dialogParameters.get(DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY);
                if (!(featureObject instanceof EngineAPI.ApiKeyPermission)) {
                    continueWithNextDialog();
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_subscription_required)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_subscription_required, null);
                    TextView subscriptionMessageTextView = dialogView.findViewById(R.id.subscription_required_text_view);
                    builder.setView(dialogView);
                    Dialog dialog = builder.create();
                    dialogView.findViewById(R.id.check_subscription_button).setOnClickListener(v -> {
                        dialog.dismiss();
                        startActivity(new Intent(this, OwnedIdentityDetailsActivity.class));
                    });
                    switch ((EngineAPI.ApiKeyPermission) featureObject) {
                        case CALL:
                            subscriptionMessageTextView.setText(R.string.dialog_message_subscription_required_call);
                            break;
                        case WEB_CLIENT:
                            subscriptionMessageTextView.setText(R.string.dialog_message_subscription_required_web_client);
                            break;
                    }
                    dialog.show();
                }
                break;
            }
            case DIALOG_NEW_VERSION_AVAILABLE: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_update_available)
                        .setMessage(R.string.dialog_message_update_available)
                        .setPositiveButton(R.string.button_label_update, (dialog, which) -> {
                            final String appPackageName = getPackageName();
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                            } catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                            }
                        })
                        .setNegativeButton(R.string.button_label_ignore, null)
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            case DIALOG_OUTDATED_VERSION: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_outdated_version)
                        .setMessage(R.string.dialog_message_outdated_version)
                        .setPositiveButton(R.string.button_label_update, (dialog, which) -> {
                            final String appPackageName = getPackageName();
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                            } catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                            }
                        })
                        .setNegativeButton(R.string.button_label_remind_me_later, null)
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            case DIALOG_CALL_INITIATION_NOT_SUPPORTED: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_call_initiation_not_supported)
                        .setMessage(R.string.dialog_message_call_initiation_not_supported)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            case DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY);
                Object clientIdObject = dialogParameters.get(DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY);
                Object clientSecretObject = dialogParameters.get(DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY);
                Object serverUrlObject = dialogParameters.get(DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])
                        || !(clientIdObject instanceof String)
                        || (clientSecretObject != null && !(clientSecretObject instanceof String))
                        || !(serverUrlObject instanceof String)) {
                    continueWithNextDialog();
                } else {
                    KeycloakAuthenticationRequiredDialogFragment dialogFragment = KeycloakAuthenticationRequiredDialogFragment.newInstance(
                            KeycloakAuthenticationRequiredDialogFragment.REASON.TOKEN_EXPIRED,
                            (byte[]) bytesOwnedIdentityObject,
                            (String) serverUrlObject,
                            (String) clientIdObject,
                            clientSecretObject == null ? null : (String) clientSecretObject,
                            this::continueWithNextDialog);
                    dialogFragment.show(getSupportFragmentManager(), DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED);
                }
                break;
            }
            case DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY);
                Object serverUrlObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY);
                Object clientSecretObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY);
                Object serializedAuthStateObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])
                        || !(serverUrlObject instanceof String)
                        || (clientSecretObject != null && !(clientSecretObject instanceof String))
                        || !(serializedAuthStateObject instanceof String)) {
                    continueWithNextDialog();
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_keycloak_identity_replacement)
                            .setMessage(R.string.dialog_message_keycloak_identity_replacement)
                            .setPositiveButton(R.string.button_label_revoke, null)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    AlertDialog dialog = builder.create();

                    dialog.setOnShowListener((DialogInterface dialogInterface) -> {
                        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        button.setOnClickListener((View v) -> KeycloakManager.getInstance().uploadOwnIdentity(
                                (byte[]) bytesOwnedIdentityObject,
                                new KeycloakManager.KeycloakCallback<Void>() {
                                    @Override
                                    public void success(Void result) {
                                        App.toast(R.string.toast_message_keycloak_revoke_successful, Toast.LENGTH_SHORT, Gravity.CENTER);
                                        dialog.dismiss();
                                    }

                                    @Override
                                    public void failed(int rfc) {
                                        if (rfc == KeycloakTasks.RFC_AUTHENTICATION_REQUIRED
                                                || rfc == KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED) {
                                            // in these cases, another app dialog has to be shown
                                            dialog.dismiss();
                                        } else if (rfc == KeycloakTasks.RFC_IDENTITY_REVOKED) {
                                            KeycloakManager.forceSelfTestAndReauthentication((byte[]) bytesOwnedIdentityObject);
                                            dialog.dismiss();
                                        }

                                        App.toast(R.string.toast_message_unable_to_keycloak_revoke, Toast.LENGTH_SHORT, Gravity.CENTER);
                                    }
                                }
                        ));
                    });
                    dialog.show();
                    break;
                }
                break;
            }
            case DIALOG_KEYCLOAK_USER_ID_CHANGED: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                Object clientIdObject = dialogParameters.get(DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY);
                Object clientSecretObject = dialogParameters.get(DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY);
                Object serverUrlObject = dialogParameters.get(DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])
                        || !(clientIdObject instanceof String)
                        || (clientSecretObject != null && !(clientSecretObject instanceof String))
                        || !(serverUrlObject instanceof String)) {
                    continueWithNextDialog();
                } else {
                    KeycloakAuthenticationRequiredDialogFragment dialogFragment = KeycloakAuthenticationRequiredDialogFragment.newInstance(
                            KeycloakAuthenticationRequiredDialogFragment.REASON.USER_ID_CHANGED,
                            (byte[]) bytesOwnedIdentityObject,
                            (String) serverUrlObject,
                            (String) clientIdObject,
                            clientSecretObject == null ? null : (String) clientSecretObject,
                            this::continueWithNextDialog);
                    dialogFragment.show(getSupportFragmentManager(), DIALOG_KEYCLOAK_USER_ID_CHANGED);
                }
                break;
            }
            case DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])) {
                    continueWithNextDialog();
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_keycloak_signature_key_changed)
                            .setMessage(R.string.dialog_message_keycloak_signature_key_changed)
                            .setPositiveButton(R.string.button_label_update_key,null)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    AlertDialog dialog = builder.create();

                    dialog.setOnShowListener((DialogInterface dialogInterface) -> {
                        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        button.setOnClickListener((View v) -> App.runThread(() -> {
                            try {
                                AppSingleton.getEngine().setOwnedIdentityKeycloakSignatureKey((byte[]) bytesOwnedIdentityObject, null);
                                KeycloakManager.forceSyncManagedIdentity((byte[]) bytesOwnedIdentityObject);
                                runOnUiThread(dialog::dismiss);
                            } catch (Exception e) {
                                App.toast(R.string.toast_message_unable_to_update_key, Toast.LENGTH_SHORT);
                                e.printStackTrace();
                            }
                        }));
                    });
                    dialog.show();
                }
                break;
            }
            case DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])) {
                    continueWithNextDialog();
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_keycloak_identity_replacement_forbidden)
                            .setMessage(R.string.dialog_message_keycloak_identity_replacement_forbidden)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    builder.create().show();
                }
                break;
            }
            case DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED: {
                Object bytesOwnedIdentityObject = dialogParameters.get(DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY);
                if (!(bytesOwnedIdentityObject instanceof byte[])) {
                    continueWithNextDialog();
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_keycloak_identity_was_revoked)
                            .setMessage(R.string.dialog_message_keycloak_identity_was_revoked)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setOnDismissListener(dialog -> continueWithNextDialog());
                    builder.create().show();
                }
                break;
            }
            case DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_sd_card_ringtone_bugged_android_9)
                        .setMessage(R.string.dialog_message_sd_card_ringtone_bugged_android_9)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            case DIALOG_AVAILABLE_SPACE_LOW: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_available_space_low)
                        .setMessage(R.string.dialog_message_available_space_low)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener(dialog -> {
                            AvailableSpaceHelper.acknowledgeWarning();
                            continueWithNextDialog();
                        });

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    builder.setNeutralButton(R.string.button_label_manage_storage, (DialogInterface dialog, int which) -> {
                        Intent storageIntent = new Intent();
                        storageIntent.setAction(StorageManager.ACTION_MANAGE_STORAGE);
                        storageManagerLauncher.launch(storageIntent);
                    });
                }
                builder.create().show();
                break;
            }
            case DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_backup_requires_drive_sign_in)
                        .setMessage(R.string.dialog_message_backup_requires_drive_sign_in)
                        .setPositiveButton(R.string.button_label_sign_in, (dialog, which) -> GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .signOut().addOnCompleteListener(
                                        task -> GoogleSignIn.requestPermissions(
                                                this,
                                                DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN_REQUEST_CODE,
                                                GoogleSignIn.getLastSignedInAccount(this),
                                                new Scope(DriveScopes.DRIVE_APPDATA),
                                                new Scope(Scopes.EMAIL)
                                        )
                        ))
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setNeutralButton(R.string.button_label_app_settings, (DialogInterface dialog, int which) -> {
                            Intent intent = new Intent(this, SettingsActivity.class);
                            intent.putExtra(SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA, SettingsActivity.PREF_HEADER_KEY_BACKUP);
                            startActivity(intent);
                        })
                        .setOnDismissListener(dialog -> continueWithNextDialog());

                builder.create().show();
                break;
            }
            case DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_hidden_profile_but_no_policy)
                        .setMessage(R.string.dialog_message_hidden_profile_but_no_policy)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialogInterface, int which) -> {
                            ((AlertDialog) dialogInterface).setOnDismissListener(null);

                            PrivacyPreferenceFragment.showHiddenProfileClosePolicyChooserDialog(this, this::continueWithNextDialog);
                        })
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            case DIALOG_INTRODUCING_MULTI_PROFILE: {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_introducing_multi_profile)
                        .setMessage(R.string.dialog_message_introducing_multi_profile)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener(dialog -> continueWithNextDialog());
                builder.create().show();
                break;
            }
            default: {
                if (dialogTag.dialogTag.startsWith(DIALOG_CERTIFICATE_CHANGED)) {
                    Object untrustedCertificateIdObject = dialogParameters.get(DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY);
                    Object lastTrustedCertificateIdObject = dialogParameters.get(DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY);
                    if (!(untrustedCertificateIdObject instanceof Long)
                            || (lastTrustedCertificateIdObject != null && !(lastTrustedCertificateIdObject instanceof Long))) {
                        continueWithNextDialog();
                    } else {
                        App.runThread(() -> {
                            KnownCertificate untrustedCertificate = AppDatabase.getInstance().knownCertificateDao().get((Long) untrustedCertificateIdObject);
                            KnownCertificate lastTrustedCertificate = lastTrustedCertificateIdObject == null ? null : AppDatabase.getInstance().knownCertificateDao().get((Long) lastTrustedCertificateIdObject);

                            View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_untrusted_certificate, null);
                            ((TextView) dialogView.findViewById(R.id.domain_name_text_view)).setText(untrustedCertificate.domainName);

                            try {
                                String[] issuers = AppSingleton.getJsonObjectMapper().readValue(untrustedCertificate.issuers, new TypeReference<String[]>() {
                                });
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < issuers.length; i++) {
                                    if (i != 0) {
                                        sb.append("\n");
                                    }
                                    sb.append(i + 1).append(": ").append(issuers[i]);
                                }
                                ((TextView) dialogView.findViewById(R.id.new_cert_issuers_text_view)).setText(sb.toString());
                            } catch (Exception e) {
                                ((TextView) dialogView.findViewById(R.id.new_cert_issuers_text_view)).setText(R.string.error_text_issuers);
                            }

                            ((TextView) dialogView.findViewById(R.id.new_cert_expiration_text_view)).setText(App.getPreciseAbsoluteDateString(this, untrustedCertificate.expirationTimestamp, getString(R.string.text_date_time_separator)));

                            dialogView.findViewById(R.id.new_cert_group).setClipToOutline(true);
                            dialogView.findViewById(R.id.new_cert_group).setOnClickListener((View v) -> {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.text_certificate_chain), untrustedCertificate.encodedFullChain);
                                clipboard.setPrimaryClip(clip);
                                App.toast(R.string.toast_message_certificate_chain_copied, Toast.LENGTH_SHORT);
                            });

                            if (lastTrustedCertificate == null) {
                                // this case should never happen, still, we handle it :)
                                ((TextView) dialogView.findViewById(R.id.risk_level_explanation_text_view)).setText(R.string.text_explanation_no_trusted_certificate);
                                dialogView.findViewById(R.id.trusted_cert_group).setVisibility(View.GONE);
                            } else {

                                if (untrustedCertificate.issuers.equals(lastTrustedCertificate.issuers)) {
                                    // the issuers are the same --> probably a renewal
                                    ((TextView) dialogView.findViewById(R.id.risk_level_explanation_text_view)).setText(R.string.text_explanation_certificate_renewal);
                                } else {
                                    // the issuers changed --> be careful
                                    ((ImageView) dialogView.findViewById(R.id.risk_level_image_view)).setImageResource(R.drawable.ic_error_outline);
                                    dialogView.findViewById(R.id.new_cert_group).setBackgroundResource(R.drawable.background_error_message);

                                    ((TextView) dialogView.findViewById(R.id.risk_level_explanation_text_view)).setText(R.string.text_explanation_certificate_issuers_changed);
                                }

                                try {
                                    String[] issuers = AppSingleton.getJsonObjectMapper().readValue(lastTrustedCertificate.issuers, new TypeReference<String[]>() {
                                    });
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < issuers.length; i++) {
                                        if (i != 0) {
                                            sb.append("\n");
                                        }
                                        sb.append(i + 1).append(": ").append(issuers[i]);
                                    }
                                    ((TextView) dialogView.findViewById(R.id.trusted_cert_issuers_text_view)).setText(sb.toString());
                                } catch (Exception e) {
                                    ((TextView) dialogView.findViewById(R.id.trusted_cert_issuers_text_view)).setText(R.string.error_text_issuers);
                                }

                                ((TextView) dialogView.findViewById(R.id.trusted_cert_expiration_text_view)).setText(App.getPreciseAbsoluteDateString(this, lastTrustedCertificate.expirationTimestamp, getString(R.string.text_date_time_separator)));

                                dialogView.findViewById(R.id.trusted_cert_group).setClipToOutline(true);
                                dialogView.findViewById(R.id.trusted_cert_group).setOnClickListener((View v) -> {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText(getString(R.string.text_certificate_chain), lastTrustedCertificate.encodedFullChain);
                                    clipboard.setPrimaryClip(clip);
                                    App.toast(R.string.toast_message_certificate_chain_copied, Toast.LENGTH_SHORT);
                                });
                            }

                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                    .setView(dialogView)
                                    .setPositiveButton(R.string.button_label_trust_certificate, (DialogInterface dialog, int which) -> App.runThread(() -> {
                                        AppDatabase.getInstance().knownCertificateDao().updateTrustTimestamp(untrustedCertificate.id, System.currentTimeMillis());
                                        AppDatabase.getInstance().knownCertificateDao().deleteExpired(untrustedCertificate.domainName, System.currentTimeMillis());
                                    }))
                                    .setNegativeButton(R.string.button_label_do_not_trust_yet, null)
                                    .setOnDismissListener(dialog -> continueWithNextDialog());

                            runOnUiThread(() -> builder.create().show());
                        });
                    }
                } else {
                    continueWithNextDialog();
                }
                break;
            }
        }
    }

    private void continueWithNextDialog() {
        AppDialogTag dialogTag = appDialogShowViewModel.getCurrentlyShowingDialogTag();
        App.removeDialog(dialogTag);
        appDialogShowViewModel.setCurrentlyShowingDialogTag(null);
        showNextDialog();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    SettingsActivity.setAutomaticBackupAccount(account.getEmail());
                    // notify the engine that auto-backup is set to true to initiate an immediate backup/upload
                    AppSingleton.getEngine().setAutoBackupEnabled(true);
                    return;
                }
            }
            App.toast(R.string.toast_message_error_selecting_automatic_backup_account, Toast.LENGTH_SHORT);
        }
    }
}