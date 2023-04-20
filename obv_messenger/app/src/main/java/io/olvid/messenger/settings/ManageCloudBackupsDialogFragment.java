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

package io.olvid.messenger.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.fragments.dialog.CloudProviderSignInDialogFragment;
import io.olvid.messenger.google_services.GoogleServicesUtils;
import io.olvid.messenger.services.BackupCloudProviderService;

public class ManageCloudBackupsDialogFragment extends DialogFragment implements View.OnClickListener, BackupCloudProviderService.OnBackupsListCallback, BackupCloudProviderService.OnBackupDownloadCallback, BackupCloudProviderService.OnBackupDeleteCallback {
    public static final int REQUEST_CODE_SAVE_BACKUP = 1824;

    private FragmentActivity activity;

    private ImageView cloudProviderLogo;
    private TextView cloudProviderName;
    private TextView cloudProviderAccount;
    private TextView emptyView;

    private BackupCloudProviderService.CloudProviderConfiguration cloudProviderConfiguration;
    private BackupsManagerAdapter adapter;
    private BackupCloudProviderService.BackupItem backupItemToDownload = null;
    private byte[] backupItemContent = null;


    public static ManageCloudBackupsDialogFragment newInstance() {
        return new ManageCloudBackupsDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = requireActivity();
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
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                // make the dialog background transparent to have the rounded corners of the layout
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_manage_cloud_backups, container, false);

        cloudProviderLogo = dialogView.findViewById(R.id.cloud_provider_logo);
        cloudProviderName = dialogView.findViewById(R.id.cloud_provider_name);
        cloudProviderAccount = dialogView.findViewById(R.id.cloud_provider_account);

        TextView switchAccountButton = dialogView.findViewById(R.id.button_switch_account);
        switchAccountButton.setOnClickListener(this);
        TextView doneButton = dialogView.findViewById(R.id.button_done);
        doneButton.setOnClickListener(this);

        EmptyRecyclerView backupsRecyclerView = dialogView.findViewById(R.id.backup_recycler_view);
        backupsRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        emptyView = dialogView.findViewById(R.id.empty_view);
        backupsRecyclerView.setEmptyView(emptyView);
        View loadingSpinner = dialogView.findViewById(R.id.loading_spinner);
        backupsRecyclerView.setLoadingSpinner(loadingSpinner, 0, 150);
        backupsRecyclerView.addItemDecoration(new ItemDecorationSimpleDivider(activity, 52, 8, R.color.dialogBackground));

        adapter = new BackupsManagerAdapter();
        backupsRecyclerView.setAdapter(adapter);

        cloudProviderConfiguration = SettingsActivity.getAutomaticBackupConfiguration();
        listBackups();
        return dialogView;
    }

    private void displayNoAccount() {
        cloudProviderLogo.setImageResource(R.drawable.ic_question_mark);
        cloudProviderName.setText(R.string.label_no_account_selected);
        cloudProviderName.setTypeface(cloudProviderAccount.getTypeface(), Typeface.ITALIC);
        cloudProviderAccount.setVisibility(View.GONE);

    }

    private void displayGoogleDriveAccount(String email) {
        cloudProviderLogo.setImageResource(R.drawable.cloud_provider_google_drive);

        cloudProviderName.setText(R.string.cloud_provider_name_google_drive);
        cloudProviderName.setTypeface(cloudProviderAccount.getTypeface(), Typeface.NORMAL);

        cloudProviderAccount.setVisibility(View.VISIBLE);
        cloudProviderAccount.setText(email);
    }

    @SuppressLint("SetTextI18n")
    private void displayWebDAVAccount(String account, String serverUrl) {
        cloudProviderLogo.setImageResource(R.drawable.cloud_provider_webdav);

        cloudProviderName.setText(R.string.cloud_provider_name_webdav);
        cloudProviderName.setTypeface(cloudProviderAccount.getTypeface(), Typeface.NORMAL);

        cloudProviderAccount.setVisibility(View.VISIBLE);
        cloudProviderAccount.setText(account + " @ " + serverUrl);
    }

    private void listBackups() {
        if (cloudProviderConfiguration != null && cloudProviderConfiguration.provider != null) {
            switch (cloudProviderConfiguration.provider) {
                case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_WEBDAV:
                    if (cloudProviderConfiguration.account != null && cloudProviderConfiguration.serverUrl != null && cloudProviderConfiguration.password != null) {
                        displayWebDAVAccount(cloudProviderConfiguration.account, cloudProviderConfiguration.serverUrl);

                        emptyView.setText(R.string.label_no_backup_found);
                        adapter.setBackupItems(null, true);
                        BackupCloudProviderService.listBackups(cloudProviderConfiguration, this);
                        return;
                    }
                    break;
                case BackupCloudProviderService.CloudProviderConfiguration.PROVIDER_GOOGLE_DRIVE:
                    String email = GoogleServicesUtils.getSignInEmail(App.getContext());
                    if (email != null && Objects.equals(email, cloudProviderConfiguration.account)) {
                        displayGoogleDriveAccount(email);

                        emptyView.setText(R.string.label_no_backup_found);
                        adapter.setBackupItems(null, true);
                        BackupCloudProviderService.listBackups(cloudProviderConfiguration, this);
                        return;
                    }
                    break;
            }
        }
        displayNoAccount();
        emptyView.setText(R.string.label_tap_to_select_cloud_account);
        adapter.setBackupItems(null, false);
    }

    @Override
    public void onListSuccess(List<BackupCloudProviderService.BackupItem> backupItems) {
        activity.runOnUiThread(() -> adapter.setBackupItems(backupItems, false));
    }

    @Override
    public void onListFailure(int error) {
        switch (error) {
            case BackupCloudProviderService.ERROR_BAD_CONFIGURATION:
            case BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED:
                activity.runOnUiThread(() -> {
                    displayNoAccount();

                    emptyView.setText(R.string.label_tap_to_select_cloud_account);
                    adapter.setBackupItems(null, false);
                });
                break;
            case BackupCloudProviderService.ERROR_UNKNOWN:
            default:
                activity.runOnUiThread(() -> {
                    emptyView.setText(R.string.label_error_connecting_to_cloud);
                    adapter.setBackupItems(null, false);
                });
                break;
        }
    }


    private void downloadBackup(@Nullable BackupCloudProviderService.BackupItem backupItem) {
        if (backupItem == null || cloudProviderConfiguration == null) {
            return;
        }
        this.backupItemToDownload = backupItem;
        BackupCloudProviderService.downloadBackup(cloudProviderConfiguration, backupItem, this);
    }

    @Override
    public void onDownloadSuccess(byte[] backupContent) {
        if (this.backupItemToDownload == null) {
            return;
        }
        String backupFileName = "Olvid backup - " + backupItemToDownload.deviceName + " - " + new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(backupItemToDownload.timestamp) + ".olvidbackup";
        this.backupItemToDownload = null;

        this.backupItemContent = backupContent;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, backupFileName);
        App.startActivityForResult(this, intent, REQUEST_CODE_SAVE_BACKUP);
    }

    @Override
    public void onDownloadFailure(int error) {
        App.toast(R.string.toast_message_error_while_downloading_selected_backup, Toast.LENGTH_SHORT);
    }


    private void deleteBackup(@Nullable BackupCloudProviderService.BackupItem backupItem) {
        if (backupItem == null || cloudProviderConfiguration == null) {
            return;
        }
        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_delete_cloud_backup)
                .setMessage(R.string.dialog_message_delete_cloud_backup)
                .setNegativeButton(R.string.button_label_cancel, null)
                .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> BackupCloudProviderService.deleteBackup(cloudProviderConfiguration, backupItem, this));
        builder.create().show();
    }

    @Override
    public void onDeleteSuccess() {
        activity.runOnUiThread(this::listBackups);
    }

    @Override
    public void onDeleteFailure(int error) {
        App.toast(R.string.toast_message_error_while_deleting_selected_backup, Toast.LENGTH_SHORT);
    }

    private class BackupsManagerAdapter extends LoadAwareAdapter<BackupViewHolder> {
        List<BackupCloudProviderService.BackupItem> backupItems;
        boolean loading = false;

        @SuppressLint("NotifyDataSetChanged")
        void setBackupItems(List<BackupCloudProviderService.BackupItem> backupItems, boolean loading) {
            this.backupItems = backupItems;
            this.loading = loading;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            if (backupItems != null) {
                return backupItems.size();
            }
            return 0;
        }

        @Override
        public boolean isLoadingDone() {
            return !loading;
        }

        @NonNull
        @Override
        public BackupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new BackupViewHolder(getLayoutInflater().inflate(R.layout.item_view_cloud_backup_item, parent, false));
        }

        @Override
        public void onViewRecycled(@NonNull BackupViewHolder holder) {
            super.onViewRecycled(holder);
            holder.backupItem = null;
        }

        @Override
        public void onBindViewHolder(@NonNull BackupViewHolder holder, int position) {
            BackupCloudProviderService.BackupItem backupItem = backupItems.get(position);

            holder.backupItem = backupItem;
            holder.deviceTextView.setText(backupItem.deviceName);
            holder.dateTextView.setText(StringUtils.getLongNiceDateString(activity, backupItem.timestamp));
        }
    }

    private class BackupViewHolder extends RecyclerView.ViewHolder {
        @Nullable
        BackupCloudProviderService.BackupItem backupItem = null;
        @NonNull
        final TextView deviceTextView;
        @NonNull
        final TextView dateTextView;

        public BackupViewHolder(@NonNull View itemView) {
            super(itemView);

            deviceTextView = itemView.findViewById(R.id.backup_device_text_view);
            dateTextView = itemView.findViewById(R.id.backup_timestamp_text_view);

            ImageView downloadButton = itemView.findViewById(R.id.button_download);
            downloadButton.setVisibility(View.VISIBLE);
            downloadButton.setOnClickListener(v -> downloadBackup(backupItem));

            ImageView deleteButton = itemView.findViewById(R.id.button_delete);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(v -> deleteBackup(backupItem));
        }
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_done) {
            dismiss();
        } else if (id == R.id.button_switch_account) {
            CloudProviderSignInDialogFragment cloudProviderSignInDialogFragment = CloudProviderSignInDialogFragment.newInstance();
            cloudProviderSignInDialogFragment.setSignInContext(CloudProviderSignInDialogFragment.SignInContext.MANAGE_BACKUPS);
            cloudProviderSignInDialogFragment.setOnCloudProviderConfigurationCallback(new CloudProviderSignInDialogFragment.OnCloudProviderConfigurationCallback() {
                @Override
                public void onCloudProviderConfigurationSuccess(BackupCloudProviderService.CloudProviderConfiguration configuration) {
                    cloudProviderConfiguration = configuration;
                    listBackups();
                }

                @Override
                public void onCloudProviderConfigurationFailed() {
                    // do nothing
                }
            });
            cloudProviderSignInDialogFragment.show(getChildFragmentManager(), "CloudProviderSignInDialogFragment");
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SAVE_BACKUP
                && resultCode == Activity.RESULT_OK
                && data != null
                && StringUtils.validateUri(data.getData())
                && activity != null
                && backupItemContent != null) {
            final byte[] bytesToWrite = backupItemContent;
            App.runThread(() -> {
                //noinspection ConstantConditions
                try (OutputStream os = activity.getContentResolver().openOutputStream(data.getData())) {
                    if (os == null) {
                        throw new Exception("Unable to write to provided Uri");
                    }
                    os.write(bytesToWrite);
                    App.toast(R.string.toast_message_backup_saved, Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    App.toast(R.string.toast_message_failed_to_save_backup, Toast.LENGTH_SHORT);
                }
            });
        }
        backupItemContent = null;
    }
}
