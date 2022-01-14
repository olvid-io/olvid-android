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

package io.olvid.messenger.owneddetails;

import android.app.Dialog;
import android.os.Bundle;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.settings.SettingsActivity;


public class EditOwnedGroupDetailsDialogFragment extends DialogFragment {
    private OwnedGroupDetailsViewModel viewModel;
    private Runnable onOkCallback;

    public static EditOwnedGroupDetailsDialogFragment newInstance(AppCompatActivity parentActivity, byte[] byteOwnedIdentity, byte[] bytesGroupOwnerAndUid, JsonGroupDetailsWithVersionAndPhoto groupDetails, Runnable onOkCallback) {
        EditOwnedGroupDetailsDialogFragment fragment = new EditOwnedGroupDetailsDialogFragment();
        fragment.onOkCallback = onOkCallback;
        OwnedGroupDetailsViewModel viewModel = new ViewModelProvider(parentActivity).get(OwnedGroupDetailsViewModel.class);
        viewModel.setBytesOwnedIdentity(byteOwnedIdentity);
        viewModel.setBytesGroupOwnerAndUid(bytesGroupOwnerAndUid);
        viewModel.setOwnedGroupDetails(groupDetails);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(OwnedGroupDetailsViewModel.class);
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
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_publish_cancel_with_placeholder, container, false);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(R.string.dialog_title_edit_group_details);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button publishButton = dialogView.findViewById(R.id.button_publish);
        publishButton.setOnClickListener(view -> {
            dismiss();
            App.runThread(() -> {
                boolean changed = false;
                if (viewModel.detailsChanged()) {
                    JsonGroupDetails newDetails = viewModel.getJsonGroupDetails();
                    try {
                        AppSingleton.getEngine().updateLatestGroupDetails(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUid(), newDetails);
                        changed = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        App.toast(R.string.toast_message_error_publishing_group_details, Toast.LENGTH_SHORT);
                    }
                }
                if (viewModel.photoChanged()) {
                    String absolutePhotoUrl = viewModel.getAbsolutePhotoUrl();
                    try {
                        AppSingleton.getEngine().updateOwnedGroupPhoto(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUid(), absolutePhotoUrl);
                        changed = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        App.toast(R.string.toast_message_error_publishing_group_details, Toast.LENGTH_SHORT);
                    }
                }
                if (changed) {
                    AppSingleton.getEngine().publishLatestGroupDetails(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUid());
                }
                if (onOkCallback != null) {
                    onOkCallback.run();
                }
            });
        });
        viewModel.getValid().observe(this, valid -> publishButton.setEnabled(valid != null && valid));

        OwnedGroupDetailsFragment ownedGroupDetailsFragment = new OwnedGroupDetailsFragment();
        ownedGroupDetailsFragment.setUseDialogBackground(true);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_placeholder, ownedGroupDetailsFragment);
        transaction.commit();

        return dialogView;
    }
}
