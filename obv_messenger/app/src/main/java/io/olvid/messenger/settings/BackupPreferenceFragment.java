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

package io.olvid.messenger.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment;
import io.olvid.messenger.services.BackupCloudProviderService;

public class BackupPreferenceFragment extends PreferenceFragmentCompat implements EngineNotificationListener {
    static final int REQUEST_CODE_SAVE_BACKUP = 10089;

    private BackupPreferenceViewModel viewModel;
    private static int failedLoads = 0;
    private Long engineRegistrationNumber;

    Preference reloadBackupConfigurationPreference;
    Preference generateBackupKeyPreference;
    Preference manualBackupPreference;
    NoClickSwitchPreference enableAutomaticBackupPreference;
    FragmentActivity activity;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_backup, rootKey);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(BackupPreferenceViewModel.class);

        PreferenceScreen screen = getPreferenceScreen();

        reloadBackupConfigurationPreference = screen.findPreference(SettingsActivity.PREF_KEY_RELOAD_BACKUP_CONFIGURATION);
        generateBackupKeyPreference = screen.findPreference(SettingsActivity.PREF_KEY_GENERATE_NEW_BACKUP_KEY);
        manualBackupPreference = screen.findPreference(SettingsActivity.PREF_KEY_MANUAL_BACKUP);
        enableAutomaticBackupPreference = screen.findPreference(SettingsActivity.PREF_KEY_ENABLE_AUTOMATIC_BACKUP);

        if (generateBackupKeyPreference != null && manualBackupPreference != null && enableAutomaticBackupPreference != null && reloadBackupConfigurationPreference != null) {
            refreshBackupPreferences();

            reloadBackupConfigurationPreference.setOnPreferenceClickListener(preference -> {
                refreshBackupPreferences();
                return true;
            });

            generateBackupKeyPreference.setOnPreferenceClickListener(preference -> {
                if (viewModel.getBackupKeyInformation() == null) {
                    AppSingleton.getEngine().generateBackupKey();
                } else {
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_verify_backup_key, null);
                    final EditText backupSeedEditText = dialogView.findViewById(R.id.backup_key_edittext);
                    final View errorSuccessLayout = dialogView.findViewById(R.id.error_success_layout);
                    final ImageView errorSuccessImageView = dialogView.findViewById(R.id.error_success_image);
                    final TextView errorSuccessTextView = dialogView.findViewById(R.id.error_success_text);
                    final Button generateNewKeyButton = dialogView.findViewById(R.id.button_generate_new_key);

                    final AlertDialog alertDialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_verify_backup_key)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_verify, null)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .create();

                    generateNewKeyButton.setOnClickListener(v -> {
                        AlertDialog confirmationDialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_confirm_backup_key_generation)
                                .setMessage(R.string.dialog_message_confirm_backup_key_generation)
                                .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(() -> AppSingleton.getEngine().generateBackupKey()))
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .create();
                        alertDialog.dismiss();
                        confirmationDialog.show();
                    });

                    alertDialog.setOnShowListener(dialog -> {
                        Window window = ((AlertDialog) dialog).getWindow();
                        if (window != null) {
                            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                            // always prevent screen capture of the backup key!
                            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        final Button button = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setOnClickListener(v -> {
                                ObvBackupKeyVerificationOutput backupKeyVerificationOutput = AppSingleton.getEngine().verifyBackupSeed(backupSeedEditText.getText().toString());
                                errorSuccessLayout.setVisibility(View.VISIBLE);
                                switch (backupKeyVerificationOutput.verificationStatus) {
                                    case ObvBackupKeyVerificationOutput.STATUS_SUCCESS: {
                                        errorSuccessImageView.setImageResource(R.drawable.ic_ok_green);
                                        errorSuccessTextView.setText(R.string.text_backup_key_verification_success);
                                        button.setText(R.string.button_label_ok);
                                        button.setOnClickListener(view -> alertDialog.dismiss());
                                        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
                                        generateNewKeyButton.setVisibility(View.GONE);
                                        break;
                                    }
                                    case ObvBackupKeyVerificationOutput.STATUS_TOO_SHORT: {
                                        errorSuccessImageView.setImageResource(R.drawable.ic_error_outline);
                                        errorSuccessTextView.setText(R.string.text_backup_key_verification_failed_short);
                                        Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                                        backupSeedEditText.startAnimation(shakeAnimation);
                                        break;
                                    }
                                    case ObvBackupKeyVerificationOutput.STATUS_TOO_LONG: {
                                        errorSuccessImageView.setImageResource(R.drawable.ic_error_outline);
                                        errorSuccessTextView.setText(R.string.text_backup_key_verification_failed_long);
                                        Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                                        backupSeedEditText.startAnimation(shakeAnimation);
                                        break;
                                    }
                                    case ObvBackupKeyVerificationOutput.STATUS_BAD_KEY: {
                                        errorSuccessImageView.setImageResource(R.drawable.ic_error_outline);
                                        errorSuccessTextView.setText(R.string.text_backup_key_verification_failed);
                                        Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                                        backupSeedEditText.startAnimation(shakeAnimation);
                                        break;
                                    }
                                }
                            });
                        }
                    });
                    alertDialog.show();
                }
                return true;
            });

            manualBackupPreference.setOnPreferenceClickListener(preference -> {
                App.runThread(() -> AppSingleton.getEngine().initiateBackup(true));
                return true;
            });

            enableAutomaticBackupPreference.setOnPreferenceClickListener(pref -> {
                automaticBackupClicked();
                return true;
            });
        }

        Preference manageCloudBackupsPreference = screen.findPreference(SettingsActivity.PREF_KEY_MANAGE_CLOUD_BACKUPS);
        if (manageCloudBackupsPreference != null) {
            manageCloudBackupsPreference.setOnPreferenceClickListener(preference -> {
                ManageCloudBackupsDialogFragment manageCloudBackupsDialogFragment = ManageCloudBackupsDialogFragment.newInstance();
                manageCloudBackupsDialogFragment.show(getChildFragmentManager(), "ManageCloudBackupsDialogFragment");
                return true;
            });
        }

        engineRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, this);
    }

    void refreshBackupPreferences() {
        if (generateBackupKeyPreference != null && manualBackupPreference != null && enableAutomaticBackupPreference != null && reloadBackupConfigurationPreference != null) {
            ObvBackupKeyInformation backupKeyInformation;
            try {
                backupKeyInformation = AppSingleton.getEngine().getBackupKeyInformation();
            } catch (Exception e) {
                e.printStackTrace();
                reloadBackupConfigurationPreference.setVisible(true);
                generateBackupKeyPreference.setEnabled(false);
                manualBackupPreference.setEnabled(false);
                enableAutomaticBackupPreference.setEnabled(false);

                failedLoads ++;
                if (failedLoads <= 3) {
                    App.toast(R.string.toast_message_unable_to_load_backup_configuration, Toast.LENGTH_SHORT);
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_unable_to_load_backup_configuration)
                            .setMessage(R.string.dialog_message_unable_to_load_backup_configuration)
                            .setPositiveButton(R.string.button_label_generate_new_backup_key, (dialog, which) -> {
                                failedLoads = 0;
                                AppSingleton.getEngine().generateBackupKey();
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    activity.runOnUiThread(() -> builder.create().show());
                }
                return;
            }

            viewModel.setBackupKeyInformation(backupKeyInformation);
            reloadBackupConfigurationPreference.setVisible(false);
            failedLoads = 0;
            if (backupKeyInformation == null) {
                generateBackupKeyPreference.setTitle(R.string.pref_generate_new_backup_key_title);
                generateBackupKeyPreference.setSummary(R.string.pref_generate_new_backup_key_summary_no_key);
                manualBackupPreference.setEnabled(false);
                manualBackupPreference.setSummary(R.string.pref_manual_backup_summary);
                enableAutomaticBackupPreference.setEnabled(false);
                enableAutomaticBackupPreference.setSummary(R.string.pref_enable_automatic_backup_summary);
            } else {
                generateBackupKeyPreference.setTitle(R.string.pref_generate_new_backup_key_title_or_verify);
                if (backupKeyInformation.lastSuccessfulKeyVerificationTimestamp == 0) {
                    generateBackupKeyPreference.setSummary(getString(R.string.pref_generate_new_backup_key_summary_never_verified,
                            StringUtils.getLongNiceDateString(getActivity(), backupKeyInformation.keyGenerationTimestamp)));
                } else {
                    generateBackupKeyPreference.setSummary(getString(R.string.pref_generate_new_backup_key_summary,
                            StringUtils.getLongNiceDateString(getActivity(), backupKeyInformation.keyGenerationTimestamp),
                            backupKeyInformation.successfulVerificationCount,
                            StringUtils.getLongNiceDateString(getActivity(), backupKeyInformation.lastSuccessfulKeyVerificationTimestamp)));
                }
                manualBackupPreference.setEnabled(true);
                if (backupKeyInformation.lastBackupExport == 0) {
                    manualBackupPreference.setSummary(R.string.pref_manual_backup_summary);
                } else {
                    manualBackupPreference.setSummary(getString(R.string.pref_manual_backup_summary_time,
                            StringUtils.getLongNiceDateString(getActivity(), backupKeyInformation.lastBackupExport)));
                }
                enableAutomaticBackupPreference.setEnabled(true);
                String summaryString = getString(R.string.pref_enable_automatic_backup_summary);
                if (SettingsActivity.useAutomaticBackup()) {
                    BackupCloudProviderService.CloudProviderConfiguration configuration = SettingsActivity.getAutomaticBackupConfiguration();
                    if (configuration != null && configuration.provider != null) {
                        switch (configuration.provider) {
                            case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                                summaryString += getString(R.string.pref_enable_automatic_backup_summary_google_drive_account, configuration.account);
                                break;
                            case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_WEBDAV:
                                summaryString += getString(R.string.pref_enable_automatic_backup_summary_webdav_account, configuration.account, configuration.serverUrl);
                                break;
                        }
                    }
                    if (backupKeyInformation.lastBackupUpload != 0) {
                        summaryString += getString(R.string.pref_enable_automatic_backup_summary_time, StringUtils.getLongNiceDateString(getActivity(), backupKeyInformation.lastBackupUpload));
                    }
                }
                enableAutomaticBackupPreference.setSummary(summaryString);
            }
        }
    }

    void automaticBackupClicked() {
        if (enableAutomaticBackupPreference != null) {
            if (enableAutomaticBackupPreference.isChecked()) {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_backup_choose_deactivate_or_change_account)
                        .setNegativeButton(R.string.button_label_deactivate_auto_backup, (DialogInterface dialog, int which) -> {
                            // clear any stored webdav configuration
                            try {
                                SettingsActivity.setAutomaticBackupConfiguration(null);
                            } catch (Exception ignored) {}
                            // sign out any google drive
                            GoogleSignIn.getClient(activity, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                            deactivateAutomaticBackups();
                        })
                        .setPositiveButton(R.string.button_label_switch_account, (DialogInterface dialog, int which) -> openSignInDialog());
                builder.create().show();
            } else {
                openSignInDialog();
            }
        }
    }

    private void openSignInDialog() {
        CloudProviderSignInDialogFragment cloudProviderSignInDialogFragment = CloudProviderSignInDialogFragment.newInstance();
        cloudProviderSignInDialogFragment.setSignInContext(CloudProviderSignInDialogFragment.SignInContext.ACTIVATE_AUTOMATIC_BACKUPS);
        cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(new CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback() {
            @Override
            public void onCloudProviderConfigurationSuccess(BackupCloudProviderService.CloudProviderConfiguration configuration) {
                try {
                    SettingsActivity.setAutomaticBackupConfiguration(configuration);
                    activity.runOnUiThread(() -> activateAutomaticBackups());
                } catch (Exception e) {
                    e.printStackTrace();
                    onCloudProviderConfigurationFailed();
                }
            }

            @Override
            public void onCloudProviderConfigurationFailed() {
                App.toast(R.string.toast_message_error_selecting_automatic_backup_account, Toast.LENGTH_SHORT);
                activity.runOnUiThread(() -> deactivateAutomaticBackups());
            }
        });
        cloudProviderSignInDialogFragment.show(getChildFragmentManager(), "CloudProviderSignInDialogFragment");
    }

    void activateAutomaticBackups() {
        if (enableAutomaticBackupPreference != null) {
            if (enableAutomaticBackupPreference.callChangeListener(true)) {
                enableAutomaticBackupPreference.setChecked(true);
                AppSingleton.getEngine().setAutoBackupEnabled(true);
            }
            refreshBackupPreferences();
        }
    }

    void deactivateAutomaticBackups() {
        if (enableAutomaticBackupPreference != null) {
            if (enableAutomaticBackupPreference.callChangeListener(false)) {
                enableAutomaticBackupPreference.setChecked(false);
                AppSingleton.getEngine().setAutoBackupEnabled(false);
            }
            refreshBackupPreferences();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SAVE_BACKUP
                && viewModel != null) {
            if (resultCode == Activity.RESULT_OK) {
                final Uri uri = data.getData();
                final byte[] encryptedContent = viewModel.getExportBackupContent();
                if (uri != null) {
                    App.runThread(() -> {
                        try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                            if (os == null) {
                                throw new Exception("Unable to write to provided Uri");
                            }
                            os.write(encryptedContent);
                            App.toast(R.string.toast_message_backup_saved, Toast.LENGTH_SHORT);
                            AppSingleton.getEngine().markBackupExported(viewModel.getExportBackupKeyUid(), viewModel.getExportBackupVersion());
                            activity.runOnUiThread(this::refreshBackupPreferences);
                            return;
                        } catch (Exception e) {
                            App.toast(R.string.toast_message_failed_to_save_backup, Toast.LENGTH_SHORT);
                        }
                        AppSingleton.getEngine().discardBackup(viewModel.getExportBackupKeyUid(), viewModel.getExportBackupVersion());
                    });
                    return;
                }
            }
            AppSingleton.getEngine().discardBackup(viewModel.getExportBackupKeyUid(), viewModel.getExportBackupVersion());
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.NEW_BACKUP_SEED_GENERATED: {
                activity.runOnUiThread(() -> {
                    String backupSeed = (String) userInfo.get(EngineNotifications.NEW_BACKUP_SEED_GENERATED_SEED_KEY);
                    if (backupSeed != null) {
                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_new_backup_key, null);
                        TextView backupSeedTextView = dialogView.findViewById(R.id.backup_seed_text_view);
                        backupSeedTextView.setText(backupSeed);

                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_new_backup_key)
                                .setView(dialogView)
                                .setPositiveButton(R.string.button_label_key_copied_close_window, (dialog, which) -> {
                                    if (!SettingsActivity.useAutomaticBackup()) {
                                        View secondDialogView = getLayoutInflater().inflate(R.layout.dialog_view_backup_choices, null);
                                        final Button fileButton = secondDialogView.findViewById(R.id.button_file);
                                        final Button cloudButton = secondDialogView.findViewById(R.id.button_cloud);

                                        final AlertDialog secondDialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                                .setTitle(R.string.dialog_title_backup_choices)
                                                .setView(secondDialogView)
                                                .setNegativeButton(R.string.button_label_cancel, null)
                                                .create();

                                        fileButton.setOnClickListener(v -> {
                                            App.runThread(() -> AppSingleton.getEngine().initiateBackup(true));
                                            secondDialog.dismiss();
                                        });
                                        cloudButton.setOnClickListener(v -> {
                                            automaticBackupClicked();
                                            secondDialog.dismiss();
                                        });
                                        secondDialog.show();
                                    }
                                });

                        Dialog dialog = builder.create();
                        Window window = dialog.getWindow();
                        if (window != null) {
                            // always prevent screen capture of the backup key!
                            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        dialog.show();
                    }
                    refreshBackupPreferences();
                });
                break;
            }
            case EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL: {
                activity.runOnUiThread(this::refreshBackupPreferences);
                break;
            }
            case EngineNotifications.BACKUP_SEED_GENERATION_FAILED: {
                App.toast(R.string.toast_message_backup_seed_generation_failed, Toast.LENGTH_SHORT);
                break;
            }
            case EngineNotifications.BACKUP_FOR_EXPORT_FINISHED: {
                byte[] bytesBackupKeyUid = (byte[]) userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_BYTES_BACKUP_KEY_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (encryptedContent == null || version == null || bytesBackupKeyUid == null) {
                    break;
                }

                if (viewModel != null) {
                    viewModel.setExportBackupContent(encryptedContent);
                    viewModel.setExportBackupKeyUid(bytesBackupKeyUid);
                    viewModel.setExportBackupVersion(version);
                    String backupFileName = "Olvid backup - "  + new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + ".olvidbackup";

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/octet-stream")
                            .putExtra(Intent.EXTRA_TITLE, backupFileName);
                    App.startActivityForResult(this, intent, REQUEST_CODE_SAVE_BACKUP);
                }
                break;
            }
            case EngineNotifications.BACKUP_FOR_EXPORT_FAILED: {
                App.toast(R.string.toast_message_backup_failed, Toast.LENGTH_SHORT);
                break;
            }
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.engineRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineRegistrationNumber != null;
    }
}
