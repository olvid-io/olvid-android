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
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.tasks.UpdateGroupV2CustomNameAndPhotoTask;
import io.olvid.messenger.settings.SettingsActivity;


public class EditOwnedGroupDetailsDialogFragment extends DialogFragment {
    private OwnedGroupDetailsViewModel viewModel;
    private Runnable onOkCallback;

    public static EditOwnedGroupDetailsDialogFragment newInstance(AppCompatActivity parentActivity, byte[] byteOwnedIdentity, byte[] bytesGroupOwnerAndUid, JsonGroupDetailsWithVersionAndPhoto groupDetails, String personalNote, Runnable onOkCallback) {
        EditOwnedGroupDetailsDialogFragment fragment = new EditOwnedGroupDetailsDialogFragment();
        fragment.onOkCallback = onOkCallback;
        OwnedGroupDetailsViewModel viewModel = new ViewModelProvider(parentActivity).get(OwnedGroupDetailsViewModel.class);
        viewModel.setGroupV2(false);
        viewModel.setBytesOwnedIdentity(byteOwnedIdentity);
        viewModel.setBytesGroupOwnerAndUidOrIdentifier(bytesGroupOwnerAndUid);
        viewModel.setOwnedGroupDetails(groupDetails, personalNote);
        return fragment;
    }

    public static EditOwnedGroupDetailsDialogFragment newInstanceV2(AppCompatActivity parentActivity, byte[] byteOwnedIdentity, byte[] bytesGroupIdentifier, JsonGroupDetails groupDetails, String photoUrl, String personalNote, Runnable onOkCallback) {
        EditOwnedGroupDetailsDialogFragment fragment = new EditOwnedGroupDetailsDialogFragment();
        fragment.onOkCallback = onOkCallback;
        OwnedGroupDetailsViewModel viewModel = new ViewModelProvider(parentActivity).get(OwnedGroupDetailsViewModel.class);
        viewModel.setGroupV2(true);
        viewModel.setBytesOwnedIdentity(byteOwnedIdentity);
        viewModel.setBytesGroupOwnerAndUidOrIdentifier(bytesGroupIdentifier);
        viewModel.setOwnedGroupDetailsV2(groupDetails, photoUrl, personalNote);
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
        dialogTitle.setText(R.string.dialog_title_edit_group_details);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button publishButton = dialogView.findViewById(R.id.button_publish);
        publishButton.setOnClickListener(view -> {
            dismiss();
            if (viewModel.isGroupV2()) {
                App.runThread(() -> {
                    boolean changed = false;

                    ObvGroupV2.ObvGroupV2ChangeSet obvChangeSet = new ObvGroupV2.ObvGroupV2ChangeSet();
                    if (viewModel.detailsChanged()) {
                        try {
                            obvChangeSet.updatedSerializedGroupDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(viewModel.getJsonGroupDetails());
                            changed = true;
                        } catch (Exception ignored) {}
                    }
                    if (viewModel.photoChanged()) {
                        obvChangeSet.updatedPhotoUrl = viewModel.getAbsolutePhotoUrl() == null ? "" : viewModel.getAbsolutePhotoUrl();
                        changed = true;
                    }
                    if (changed) {
                        try {
                            AppSingleton.getEngine().initiateGroupV2Update(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier(), obvChangeSet);
                            new UpdateGroupV2CustomNameAndPhotoTask(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier(), null, null, viewModel.getPersonalNote()).run();
                            if (onOkCallback != null) {
                                onOkCallback.run();
                            }
                        } catch (Exception e) {
                            App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                        }
                    } else if (viewModel.personalNoteChanged()) {
                        new UpdateGroupV2CustomNameAndPhotoTask(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier(), null, null, viewModel.getPersonalNote()).run();
                    }
                });
            } else {
                App.runThread(() -> {
                    boolean changed = false;
                    if (viewModel.detailsChanged()) {
                        JsonGroupDetails newDetails = viewModel.getJsonGroupDetails();
                        try {
                            AppSingleton.getEngine().updateLatestGroupDetails(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier(), newDetails);
                            changed = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            App.toast(R.string.toast_message_error_publishing_group_details, Toast.LENGTH_SHORT);
                        }
                    }
                    if (viewModel.photoChanged()) {
                        String absolutePhotoUrl = viewModel.getAbsolutePhotoUrl();
                        try {
                            AppSingleton.getEngine().updateOwnedGroupPhoto(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier(), absolutePhotoUrl);
                            changed = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            App.toast(R.string.toast_message_error_publishing_group_details, Toast.LENGTH_SHORT);
                        }
                    }
                    if (changed) {
                        AppSingleton.getEngine().publishLatestGroupDetails(viewModel.getBytesOwnedIdentity(), viewModel.getBytesGroupOwnerAndUidOrIdentifier());
                    }
                    if (onOkCallback != null) {
                        onOkCallback.run();
                    }
                });
            }
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
