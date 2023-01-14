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

package io.olvid.messenger.fragments.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.google_services.GoogleServicesUtils;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.settings.SettingsActivity;


public class CloudProviderSignInDialogFragment extends DialogFragment implements View.OnClickListener {
    private static final int REQUEST_CODE_AUTHORIZE_DRIVE = 1823;

    private FragmentActivity activity;
    private CloudProviderSignInViewModel viewModel;

    private TextView googleDriveButton;
    private TextView webdavButton;
    private View webdavConfigGroup;
    private TextView okButton;
    private View webdavLoadingSpinner;

    private OnCloudProviderConfigurationCallback onCloudProviderConfigurationCallback = null;
    private SignInContext signInContext = null;

    public static CloudProviderSignInDialogFragment newInstance() {
        return new CloudProviderSignInDialogFragment();
    }

    public void setSignInContext(SignInContext signInContext) {
        if (viewModel != null) {
            viewModel.signInContext = signInContext;
        }
        this.signInContext = signInContext;
    }

    public void setOnCloudProviderConfigurationCallback(OnCloudProviderConfigurationCallback onCloudProviderConfigurationCallback) {
        if (viewModel != null) {
            viewModel.onCloudProviderConfigurationCallback = onCloudProviderConfigurationCallback;
        }
        this.onCloudProviderConfigurationCallback = onCloudProviderConfigurationCallback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(CloudProviderSignInViewModel.class);

        if (onCloudProviderConfigurationCallback != null) {
            viewModel.onCloudProviderConfigurationCallback = onCloudProviderConfigurationCallback;
        }
        if (signInContext != null) {
            viewModel.signInContext = signInContext;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                // make the dialog background transparent to have the rounded corners of the layout
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_cloud_provider_sign_in, container, false);

        TextView dialogMessage = dialogView.findViewById(R.id.dialog_message);
        switch (viewModel.signInContext) {
            case ACTIVATE_AUTOMATIC_BACKUPS:
            case AUTOMATIC_BACKUP_SIGN_IN_REQUIRED:
                if (viewModel.googleDriveAvailable) {
                    dialogMessage.setText(R.string.dialog_message_cloud_provider_sign_in);
                } else {
                    dialogMessage.setText(R.string.dialog_message_cloud_provider_sign_in_no_google);
                }
                break;
            case RESTORE_BACKUP:
                if (viewModel.googleDriveAvailable) {
                    dialogMessage.setText(R.string.dialog_message_cloud_provider_sign_in_restore);
                } else {
                    dialogMessage.setText(R.string.dialog_message_cloud_provider_sign_in_restore_no_google);
                }
                break;
            case MANAGE_BACKUPS:
                dialogMessage.setText(R.string.dialog_message_cloud_provider_sign_in_manage);
                break;
        }

        googleDriveButton = dialogView.findViewById(R.id.provider_google_drive_button);
        googleDriveButton.setOnClickListener(this);

        webdavButton = dialogView.findViewById(R.id.provider_webdav_button);
        webdavButton.setOnClickListener(this);

        TextView cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);

        okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);

        webdavConfigGroup = dialogView.findViewById(R.id.webdav_config_group);
        webdavLoadingSpinner = dialogView.findViewById(R.id.webdav_loading_spinner);
        EditText webdavServerUrlEditText = dialogView.findViewById(R.id.webdav_server_url_edit_text);
        webdavServerUrlEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (viewModel != null && s != null) {
                    viewModel.webdavConfiguration.serverUrl = s.toString();
                }
            }
        });
        EditText webdavUsernameEditText = dialogView.findViewById(R.id.webdav_username_edit_text);
        webdavUsernameEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (viewModel != null && s != null) {
                    viewModel.webdavConfiguration.account = s.toString();
                }
            }
        });
        EditText webdavPasswordEditText = dialogView.findViewById(R.id.webdav_password_edit_text);
        webdavPasswordEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (viewModel != null && s != null) {
                    viewModel.webdavConfiguration.password = s.toString();
                }
            }
        });


        viewModel.googleDriveAvailable = BuildConfig.USE_GOOGLE_LIBS && GoogleServicesUtils.googleServicesAvailable(activity);

        if (viewModel.webdavConfiguration.serverUrl == null) {
            BackupCloudProviderService.CloudProviderConfiguration configuration = SettingsActivity.getAutomaticBackupConfiguration();
            if (configuration != null && Objects.equals(configuration.provider, BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_WEBDAV)) {
                viewModel.webdavConfiguration = configuration;
                viewModel.webdavDetailsOpened = true;
            }
        }
        webdavServerUrlEditText.setText(viewModel.webdavConfiguration.serverUrl);
        webdavUsernameEditText.setText(viewModel.webdavConfiguration.account);
        webdavPasswordEditText.setText(viewModel.webdavConfiguration.password);

        refreshLayout();
        return dialogView;
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (viewModel != null && viewModel.onCloudProviderConfigurationCallback != null && viewModel.failOnDismiss) {
            onCloudProviderConfigurationCallback.onCloudProviderConfigurationFailed();
        }
    }


    private void refreshLayout() {
        if (viewModel == null) {
            return;
        }

        googleDriveButton.setEnabled(viewModel.googleDriveAvailable);
        if (viewModel.webdavDetailsOpened) {
            webdavButton.setSelected(true);
            webdavConfigGroup.setVisibility(View.VISIBLE);
            okButton.setVisibility(View.VISIBLE);
            if (viewModel.webdavValidationInProgress) {
                webdavLoadingSpinner.setVisibility(View.VISIBLE);
            } else {
                webdavLoadingSpinner.setVisibility(View.GONE);
            }
        } else {
            webdavButton.setSelected(false);
            webdavConfigGroup.setVisibility(View.GONE);
            okButton.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_AUTHORIZE_DRIVE) {
            if (resultCode == Activity.RESULT_OK) {
                String email = GoogleServicesUtils.getSignInEmail(activity);
                if (email != null && viewModel != null && viewModel.onCloudProviderConfigurationCallback != null) {
                    viewModel.onCloudProviderConfigurationCallback.onCloudProviderConfigurationSuccess(BackupCloudProviderService.CloudProviderConfiguration.buildGoogleDrive(email));
                    viewModel.failOnDismiss = false;
                    dismiss();
                    return;
                }
            }
            App.toast(R.string.toast_message_error_selecting_automatic_backup_account, Toast.LENGTH_SHORT);
        }
    }

    private void launchGoogleSignIn() {
        if (viewModel != null && viewModel.webdavDetailsOpened) {
            viewModel.webdavDetailsOpened = false;
        }
        refreshLayout();
        GoogleServicesUtils.requestGoogleSignIn(this, REQUEST_CODE_AUTHORIZE_DRIVE);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.provider_google_drive_button) {
            if (viewModel != null && viewModel.googleDriveAvailable) {
                switch (viewModel.signInContext) {
                    case ACTIVATE_AUTOMATIC_BACKUPS: {
                        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_backup_choose_drive_account)
                                .setMessage(R.string.dialog_message_backup_choose_drive_account)
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> launchGoogleSignIn());
                        builder.create().show();
                        break;
                    }
                    case RESTORE_BACKUP: {
                        AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_backup_choose_drive_account)
                                .setMessage(R.string.dialog_message_backup_choose_drive_account_for_restore)
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> launchGoogleSignIn());
                        builder.create().show();
                        break;
                    }
                    case MANAGE_BACKUPS:
                    case AUTOMATIC_BACKUP_SIGN_IN_REQUIRED: {
                        launchGoogleSignIn();
                        break;
                    }
                }
            }
        } else if (id == R.id.provider_webdav_button) {
            if (viewModel != null) {
                viewModel.webdavDetailsOpened = !viewModel.webdavDetailsOpened;
                refreshLayout();
            }
        } else if (id == R.id.button_cancel) {
            dismiss();
        } else if (id == R.id.button_ok) {
            if (viewModel != null && viewModel.webdavDetailsOpened) {
                viewModel.webdavValidationInProgress = true;
                refreshLayout();

                BackupCloudProviderService.validateConfiguration(viewModel.webdavConfiguration, viewModel.signInContext == SignInContext.ACTIVATE_AUTOMATIC_BACKUPS || viewModel.signInContext == SignInContext.AUTOMATIC_BACKUP_SIGN_IN_REQUIRED,new BackupCloudProviderService.OnValidateCallback() {
                    @Override
                    public void onValidateSuccess() {
                        if (viewModel != null) {
                            viewModel.webdavValidationInProgress = false;
                            activity.runOnUiThread(() -> {
                                refreshLayout();
                                if (viewModel.onCloudProviderConfigurationCallback != null) {
                                    switch (viewModel.signInContext) {
                                        case ACTIVATE_AUTOMATIC_BACKUPS: {
                                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                                    .setTitle(R.string.dialog_title_backup_choose_webdav)
                                                    .setMessage(R.string.dialog_message_backup_choose_webdav)
                                                    .setNegativeButton(R.string.button_label_cancel, null)
                                                    .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                                                        viewModel.onCloudProviderConfigurationCallback.onCloudProviderConfigurationSuccess(viewModel.webdavConfiguration);
                                                        viewModel.failOnDismiss = false;
                                                        dismiss();
                                                    });
                                            builder.create().show();
                                            break;
                                        }
                                        case AUTOMATIC_BACKUP_SIGN_IN_REQUIRED:
                                        case RESTORE_BACKUP:
                                        case MANAGE_BACKUPS:
                                            viewModel.onCloudProviderConfigurationCallback.onCloudProviderConfigurationSuccess(viewModel.webdavConfiguration);
                                            viewModel.failOnDismiss = false;
                                            dismiss();
                                            break;
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onValidateFailure(int error) {
                        if (viewModel != null) {
                            viewModel.webdavValidationInProgress = false;
                            activity.runOnUiThread(CloudProviderSignInDialogFragment.this::refreshLayout);
                        }
                        switch (error) {
                            case BackupCloudProviderService.ERROR_READ_ONLY:
                                App.toast(R.string.toast_message_webdav_read_only_access, Toast.LENGTH_LONG);
                                break;
                            case BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR:
                                App.toast(R.string.toast_message_authentication_failed, Toast.LENGTH_SHORT);
                                break;
                            case BackupCloudProviderService.ERROR_NETWORK_ERROR:
                                App.toast(R.string.toast_message_unable_to_connect_to_server, Toast.LENGTH_SHORT);
                                break;
                            default:
                                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                                break;
                        }
                    }
                });
            }
        }
    }

    public interface OnCloudProviderConfigurationCallback {
        void onCloudProviderConfigurationSuccess(BackupCloudProviderService.CloudProviderConfiguration configuration);
        void onCloudProviderConfigurationFailed();
    }

    public static class CloudProviderSignInViewModel extends ViewModel {
        @NonNull SignInContext signInContext = SignInContext.MANAGE_BACKUPS; // by default, set to manage backup as it does not show a warning
        OnCloudProviderConfigurationCallback onCloudProviderConfigurationCallback = null;
        @NonNull BackupCloudProviderService.CloudProviderConfiguration webdavConfiguration = BackupCloudProviderService.CloudProviderConfiguration.buildWebDAV(null, null, null);

        boolean webdavValidationInProgress = false;
        boolean webdavDetailsOpened = false;
        boolean googleDriveAvailable = true;
        boolean failOnDismiss = true;
    }

    public enum SignInContext {
        ACTIVATE_AUTOMATIC_BACKUPS,
        AUTOMATIC_BACKUP_SIGN_IN_REQUIRED,
        RESTORE_BACKUP,
        MANAGE_BACKUPS,
    }
}
