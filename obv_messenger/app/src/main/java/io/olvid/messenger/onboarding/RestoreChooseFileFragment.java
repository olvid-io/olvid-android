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

package io.olvid.messenger.onboarding;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment;
import io.olvid.messenger.services.BackupCloudProviderService;


public class RestoreChooseFileFragment extends Fragment {
    private static final int REQUEST_CODE_ATTACH_SELECT_BACKUP_FILE = 36004;

    private OnboardingViewModel viewModel;
    private Button proceedButton;
    private CardView selectedBackupCardview;
    private TextView selectedBackupTitle;
    private TextView selectedBackupFile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_restore_choose_file, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Activity activity = requireActivity();
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_light)).start();
            }
        }


        viewModel = new ViewModelProvider(requireActivity()).get(OnboardingViewModel.class);

        view.findViewById(R.id.back_button).setOnClickListener(v -> activity.onBackPressed());

        proceedButton = view.findViewById(R.id.button_proceed);
        proceedButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(RestoreChooseFileFragmentDirections.actionProceedToEnterKey()));

        selectedBackupCardview = view.findViewById(R.id.selected_backup_cardview);
        selectedBackupTitle = view.findViewById(R.id.selected_backup_title);
        selectedBackupFile = view.findViewById(R.id.selected_backup_file);

        Button fromFileButton = view.findViewById(R.id.button_restore_from_file);
        fromFileButton.setOnClickListener(v -> selectBackupFile());

        Button fromCloudButton = view.findViewById(R.id.button_restore_from_cloud);
        fromCloudButton.setOnClickListener(v -> selectBackupCloud());

        viewModel.getBackupReady().observe(getViewLifecycleOwner(), ready -> {
            if (selectedBackupCardview == null || selectedBackupFile == null || selectedBackupTitle == null || proceedButton == null) {
                return;
            }
            if (ready != null && ready) {
                selectedBackupCardview.setVisibility(View.VISIBLE);
                selectedBackupFile.setText(viewModel.getBackupName().getValue());
                switch (viewModel.getBackupType()) {
                    case OnboardingViewModel.BACKUP_TYPE_FILE: {
                        selectedBackupTitle.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        selectedBackupTitle.setText(R.string.text_title_backup_file_selected);
                        selectedBackupTitle.invalidate();
                        break;
                    }
                    case OnboardingViewModel.BACKUP_TYPE_CLOUD: {
                        selectedBackupTitle.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                        selectedBackupTitle.setText(R.string.text_title_backup_cloud_account_selected);
                        selectedBackupTitle.invalidate();
                        break;
                    }
                }
                proceedButton.setEnabled(true);
            } else {
                selectedBackupCardview.setVisibility(View.GONE);
                proceedButton.setEnabled(false);
            }
        });
    }

    private void selectBackupFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        App.startActivityForResult(this, intent, REQUEST_CODE_ATTACH_SELECT_BACKUP_FILE);
    }

    private void selectBackupCloud() {
        CloudProviderSignInDialogFragment cloudProviderSignInDialogFragment = CloudProviderSignInDialogFragment.newInstance();
        cloudProviderSignInDialogFragment.setSignInContext(CloudProviderSignInDialogFragment.SignInContext.RESTORE_BACKUP);
        cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(new CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback() {
            @Override
            public void onCloudProviderConfigurationSuccess(BackupCloudProviderService.CloudProviderConfiguration configuration) {
                BackupCloudProviderService.listBackups(configuration, new BackupCloudProviderService.OnBackupsListCallback() {
                    @Override
                    public void onListSuccess(List<BackupCloudProviderService.BackupItem> backupTimestampAndNames) {
                        if (backupTimestampAndNames.size() == 0) {
                            App.toast(R.string.toast_message_error_no_backup_on_account, Toast.LENGTH_SHORT);
                            return;
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Activity activity = getActivity();
                            if (activity == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_select_cloud_backup_file)
                                    .setAdapter(new ArrayAdapter<BackupCloudProviderService.BackupItem>(activity, R.layout.item_view_cloud_backup_item, backupTimestampAndNames) {
                                        final LayoutInflater layoutInflater = LayoutInflater.from(activity);

                                        @NonNull
                                        @Override
                                        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                            return viewFromResource(position, convertView, parent);
                                        }

                                        private View viewFromResource(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                            final View view;
                                            if (convertView == null) {
                                                view = layoutInflater.inflate(R.layout.item_view_cloud_backup_item, parent, false);
                                            } else {
                                                view = convertView;
                                            }
                                            TextView deviceTextView = view.findViewById(R.id.backup_device_text_view);
                                            TextView timestampTextView = view.findViewById(R.id.backup_timestamp_text_view);
                                            BackupCloudProviderService.BackupItem backupItem = getItem(position);
                                            if (backupItem == null) {
                                                return view;
                                            }
                                            deviceTextView.setText(backupItem.deviceName);
                                            timestampTextView.setText(StringUtils.getLongNiceDateString(activity, backupItem.timestamp));
                                            return view;
                                        }
                                    }, (DialogInterface dialog, int which) -> {
                                        viewModel.clearSelectedBackup();
                                        BackupCloudProviderService.BackupItem backupItem = backupTimestampAndNames.get(which);
                                        BackupCloudProviderService.downloadBackup(configuration, backupItem, new BackupCloudProviderService.OnBackupDownloadCallback() {
                                            @Override
                                            public void onDownloadSuccess(byte[] backupContent) {
                                                viewModel.setBackupCloud(backupContent, configuration, backupItem.deviceName, StringUtils.getLongNiceDateString(activity, backupItem.timestamp).toString());
                                            }

                                            @Override
                                            public void onDownloadFailure(int error) {
                                                App.toast(R.string.toast_message_error_while_downloading_selected_backup, Toast.LENGTH_SHORT);
                                            }
                                        });
                                    });
                            builder.create().show();
                        });
                        Logger.e(backupTimestampAndNames.toString());
                    }

                    @Override
                    public void onListFailure(int error) {
                        switch (error) {
                            case BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED: {
                                App.toast(R.string.toast_message_error_selecting_automatic_backup_account, Toast.LENGTH_SHORT);
                                break;
                            }
                            case BackupCloudProviderService.ERROR_UNKNOWN:
                            default: {
                                App.toast(R.string.toast_message_error_while_searching_for_backup_on_account, Toast.LENGTH_SHORT);
                                break;
                            }
                        }
                    }
                });
            }

            @Override
            public void onCloudProviderConfigurationFailed() {
                // do nothing
            }
        });
        cloudProviderSignInDialogFragment.show(getChildFragmentManager(), "CloudProviderSignInDialogFragment");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_ATTACH_SELECT_BACKUP_FILE
                && resultCode == Activity.RESULT_OK) {
            viewModel.clearSelectedBackup();
            final Uri backupFileUri = data.getData();
            if (backupFileUri != null) {
                App.runThread(() -> {
                    ContentResolver contentResolver = App.getContext().getContentResolver();
                    String fileName = null;
                    String[] projection = {OpenableColumns.DISPLAY_NAME};
                    try (Cursor cursor = contentResolver.query(backupFileUri, projection, null, null, null)) {
                        if ((cursor != null) && cursor.moveToFirst()) {
                            int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (displayNameIndex >= 0) {
                                fileName = cursor.getString(displayNameIndex);
                            }
                        }
                    }
                    try {
                        viewModel.selectBackupFile(backupFileUri, fileName);
                    } catch (Exception e) {
                        e.printStackTrace();
                        App.toast(R.string.toast_message_error_opening_backup_file, Toast.LENGTH_SHORT);
                    }
                });
            }
        }
    }
}
