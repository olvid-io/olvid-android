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

package io.olvid.messenger.owneddetails;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.settings.SettingsActivity;


public class EditOwnedIdentityDetailsDialogFragment extends DialogFragment {
    private OwnedIdentityDetailsViewModel viewModel;
    private Runnable onOkCallback;

    public static EditOwnedIdentityDetailsDialogFragment newInstance(AppCompatActivity parentActivity, byte[] bytesOwnedIdentity, JsonIdentityDetailsWithVersionAndPhoto identityDetails, String customDisplayName, boolean hidden, boolean keycloakManaged, boolean identityActive, Runnable onOkCallback) {
        EditOwnedIdentityDetailsDialogFragment fragment = new EditOwnedIdentityDetailsDialogFragment();
        fragment.onOkCallback = onOkCallback;
        OwnedIdentityDetailsViewModel viewModel = new ViewModelProvider(parentActivity).get(OwnedIdentityDetailsViewModel.class);
        viewModel.setBytesOwnedIdentity(bytesOwnedIdentity);
        viewModel.setOwnedIdentityDetails(identityDetails, customDisplayName, hidden);
        viewModel.setDetailsLocked(keycloakManaged);
        viewModel.setIdentityInactive(!identityActive);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(OwnedIdentityDetailsViewModel.class);
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
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_publish_cancel_with_placeholder, container, false);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(R.string.dialog_title_edit_identity_details);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this::cancelClicked);
        Button publishButton = dialogView.findViewById(R.id.button_publish);
        publishButton.setOnClickListener(this::publishClicked);

        viewModel.getValid().observe(this, validStatus -> {
            if (validStatus == null || validStatus == OwnedIdentityDetailsViewModel.ValidStatus.INVALID) {
                publishButton.setEnabled(false);
                publishButton.setText(R.string.button_label_publish);
            } else if (validStatus == OwnedIdentityDetailsViewModel.ValidStatus.PUBLISH) {
                publishButton.setEnabled(true);
                publishButton.setText(R.string.button_label_publish);
            } else {
                publishButton.setEnabled(true);
                publishButton.setText(R.string.button_label_save);
            }
        });

        OwnedIdentityDetailsFragment ownedIdentityDetailsFragment = new OwnedIdentityDetailsFragment();
        ownedIdentityDetailsFragment.setUseDialogBackground(true);
        ownedIdentityDetailsFragment.setShowNicknameAndHidden(true);
        App.runThread(() -> {
            ownedIdentityDetailsFragment.setDisableHidden(AppDatabase.getInstance().ownedIdentityDao().countNotHidden() <= 1);
            new Handler(Looper.getMainLooper()).post(() -> {
                FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_placeholder, ownedIdentityDetailsFragment);
                transaction.commit();
            });
        });

        return dialogView;
    }

    private void cancelClicked(View view) {
        if (viewModel.profileHiddenChanged() && viewModel.isProfileHidden()) {
            // the user just hid a profile or changed the password of an already hidden profile, ask for confirmation that he wants to discard is password
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(view.getContext(), R.style.CustomAlertDialog);
            builder.setTitle(R.string.dialog_title_cancel_hide_profile)
                    .setMessage(R.string.dialog_message_cancel_hide_profile)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialog, int which) -> dismiss());
            builder.create().show();
        } else {
            dismiss();
        }
    }

    private void publishClicked(View view) {
        if (viewModel.profileHiddenChanged() && !viewModel.isProfileHidden()) {
            // the user just un-hid a profile, ask for confirmation
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(view.getContext(), R.style.CustomAlertDialog);
            builder.setTitle(R.string.dialog_title_unhide_profile)
                    .setMessage(R.string.dialog_message_unhide_profile)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialog, int which) -> {
                        dismiss();
                        doPublish();
                    });
            builder.create().show();
        } else if (viewModel.profileHiddenChanged() && viewModel.isProfileHidden()) {
            // the user added or changed a password to hide a profile
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(view.getContext(), R.style.CustomAlertDialog);
            builder.setTitle(R.string.dialog_title_hide_profile)
                    .setMessage(R.string.dialog_message_hide_profile)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialog, int which) -> {
                        dismiss();
                        doPublish();
                    });
            builder.create().show();
        } else {
            dismiss();
            doPublish();
        }
    }

    private void doPublish() {
        App.runThread(() -> {
            boolean publishedChanged = false;
            if (viewModel.detailsChanged()) {
                JsonIdentityDetails newDetails = viewModel.getJsonIdentityDetails();
                try {
                    AppSingleton.getEngine().updateLatestIdentityDetails(viewModel.getBytesOwnedIdentity(), newDetails);
                    publishedChanged = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    App.toast(R.string.toast_message_error_publishing_details, Toast.LENGTH_SHORT);
                }
            }
            if (viewModel.photoChanged()) {
                String absolutePhotoUrl = viewModel.getAbsolutePhotoUrl();
                try {
                    AppSingleton.getEngine().updateOwnedIdentityPhoto(viewModel.getBytesOwnedIdentity(), absolutePhotoUrl);
                    publishedChanged = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    App.toast(R.string.toast_message_error_publishing_details, Toast.LENGTH_SHORT);
                }
            }
            if (publishedChanged) {
                AppSingleton.getEngine().publishLatestIdentityDetails(viewModel.getBytesOwnedIdentity());
            }
            if (viewModel.nicknameChanged()) {
                AppDatabase.getInstance().ownedIdentityDao().updateCustomDisplayName(viewModel.getBytesOwnedIdentity(), viewModel.getNickname());
            }
            if (viewModel.profileHiddenChanged()) {
                AppDatabase.getInstance().ownedIdentityDao().updateUnlockPasswordAndSalt(viewModel.getBytesOwnedIdentity(), viewModel.getPassword(), viewModel.getSalt());

                if (viewModel.getPassword() != null) {
                    // profile became hidden
                    if (!SettingsActivity.isHiddenProfileClosePolicyDefined()) {
                        App.openAppDialogConfigureHiddenProfileClosePolicy();
                    }
                    AppSingleton.getInstance().ownedIdentityBecameHidden(viewModel.getBytesOwnedIdentity());
                } else {
                    // profile became un-hidden --> reselect it to memorize as latest identity
                    AppSingleton.getInstance().selectIdentity(viewModel.getBytesOwnedIdentity(), null);
                }
            }

            if (onOkCallback != null) {
                new Handler(Looper.getMainLooper()).post(onOkCallback);
            }
        });
    }
}
