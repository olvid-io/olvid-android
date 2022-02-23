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

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.fragments.dialog.HiddenProfilePasswordCreationDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.customClasses.InitialView;


public class OwnedIdentityDetailsFragment extends Fragment {
    private static final int REQUEST_CODE_CHOOSE_IMAGE = 7596;
    private static final int REQUEST_CODE_TAKE_PICTURE = 7597;
    private static final int REQUEST_CODE_SELECT_ZONE = 7598;

    private OwnedIdentityDetailsViewModel viewModel;

    private TextInputLayout firstNameLayout;
    private TextInputLayout lastNameLayout;
    private TextView errorTextView;
    private InitialView initialView;
    private CheckBox hiddenProfileCheckbox;

    private boolean useDialogBackground = false;
    private JsonKeycloakUserDetails lockedUserDetails = null;
    private boolean lockPicture = false;
    private boolean showNicknameAndHidden = false;
    private boolean disableHidden = false;

    final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    takePicture();
                } else {
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owned_identity_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(OwnedIdentityDetailsViewModel.class);

        firstNameLayout = view.findViewById(R.id.identity_details_first_name_layout);
        lastNameLayout =  view.findViewById(R.id.identity_details_last_name_layout);
        errorTextView = view.findViewById(R.id.identity_details_error);
        EditText firstNameEditText = view.findViewById(R.id.identity_details_first_name);
        EditText lastNameEditText = view.findViewById(R.id.identity_details_last_name);
        EditText companyEditText = view.findViewById(R.id.identity_details_company);
        EditText positionEditText = view.findViewById(R.id.identity_details_position);
        EditText nicknameEditText = view.findViewById(R.id.identity_details_nickname);
        hiddenProfileCheckbox = view.findViewById(R.id.hidden_profile_checkbox);
        ImageView cameraIcon = view.findViewById(R.id.camera_icon);

        if (useDialogBackground) {
            cameraIcon.setImageResource(R.drawable.ic_camera_bordered_dialog);
        }
        if (lockedUserDetails != null) {
            viewModel.setPictureLocked(lockPicture);
            viewModel.setDetailsLocked(true);
            viewModel.setFirstName(lockedUserDetails.getFirstName());
            viewModel.setLastName(lockedUserDetails.getLastName());
            viewModel.setCompany(lockedUserDetails.getCompany());
            viewModel.setPosition(lockedUserDetails.getPosition());
        } else {
            // do not setDetailsLock(false) as details may be locked voluntarily (typically when editing keycloak managed details)
            viewModel.setPictureLocked(false);
        }
        if (showNicknameAndHidden) {
            nicknameEditText.setVisibility(View.VISIBLE);
            hiddenProfileCheckbox.setVisibility(View.VISIBLE);
        } else {
            nicknameEditText.setVisibility(View.GONE);
            hiddenProfileCheckbox.setVisibility(View.GONE);
        }
        if (disableHidden) {
            hiddenProfileCheckbox.setOnClickListener(v -> {
                if (!viewModel.isProfileHidden()) {
                    App.toast(R.string.toast_message_must_have_one_visible_profile, Toast.LENGTH_SHORT, Gravity.CENTER);
                }
                hiddenProfileCheckbox.setChecked(false);
                viewModel.setProfileHidden(false);
            });
        } else {
            hiddenProfileCheckbox.setOnClickListener(this::hiddenCheckboxClicked);
        }

        if (viewModel.getPictureLocked()) {
            cameraIcon.setVisibility(View.GONE);
        } else {
            cameraIcon.setVisibility(View.VISIBLE);
        }
        if (viewModel.getDetailsLocked()) {
            firstNameEditText.setEnabled(false);
            lastNameEditText.setEnabled(false);
            companyEditText.setEnabled(false);
            positionEditText.setEnabled(false);
        } else {
            firstNameEditText.setEnabled(true);
            lastNameEditText.setEnabled(true);
            companyEditText.setEnabled(true);
            positionEditText.setEnabled(true);
        }
        initialView = view.findViewById(R.id.identity_details_initial_view);
        initialView.setKeycloakCertified(viewModel.getDetailsLocked());
        initialView.setInactive(viewModel.isIdentityInactive());

        if (SettingsActivity.useKeyboardIncognitoMode()) {
            firstNameEditText.setImeOptions(firstNameEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            lastNameEditText.setImeOptions(lastNameEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            companyEditText.setImeOptions(companyEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            positionEditText.setImeOptions(positionEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            nicknameEditText.setImeOptions(positionEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }

        viewModel.getValid().observe(getViewLifecycleOwner(), new Observer<OwnedIdentityDetailsViewModel.ValidStatus>() {
            boolean first = true;

            @Override
            public void onChanged(OwnedIdentityDetailsViewModel.ValidStatus validStatus) {
                if (first) {
                    first = false;
                    return;
                }
                if (validStatus == null || validStatus == OwnedIdentityDetailsViewModel.ValidStatus.INVALID) {
                    firstNameLayout.setError(" ");
                    lastNameLayout.setError(" ");
                    errorTextView.setText(R.string.message_error_first_or_last_name_needed);
                } else {
                    firstNameLayout.setError(null);
                    lastNameLayout.setError(null);
                    errorTextView.setText(null);
                }
            }
        });

        firstNameEditText.setText(viewModel.getFirstName());
        if (viewModel.getDetailsLocked() && (viewModel.getFirstName() == null || viewModel.getFirstName().length() == 0)) {
            firstNameEditText.setText(" ");
        }
        firstNameEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setFirstName(s.toString());
            }
        });

        lastNameEditText.setText(viewModel.getLastName());
        if (viewModel.getDetailsLocked() && (viewModel.getLastName() == null || viewModel.getLastName().length() == 0)) {
            lastNameEditText.setText(" ");
        }
        lastNameEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setLastName(s.toString());
            }
        });

        companyEditText.setText(viewModel.getCompany());
        if (viewModel.getDetailsLocked() && (viewModel.getCompany() == null || viewModel.getCompany().length() == 0)) {
            companyEditText.setText(" ");
        }
        companyEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setCompany(s.toString());
            }
        });

        positionEditText.setText(viewModel.getPosition());
        if (viewModel.getDetailsLocked() && (viewModel.getPosition() == null || viewModel.getPosition().length() == 0)) {
            positionEditText.setText(" ");
        }
        positionEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setPosition(s.toString());
            }
        });

        nicknameEditText.setText(viewModel.getNickname());
        nicknameEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setNickname(s.toString());
            }
        });
        hiddenProfileCheckbox.setChecked(viewModel.isProfileHidden());

        viewModel.getInitialViewContent().observe(getViewLifecycleOwner(), initialViewContent -> {
            if (initialViewContent == null) {
                return;
            }
            if (initialViewContent.absolutePhotoUrl != null) {
                initialView.setAbsolutePhotoUrl(initialViewContent.bytesOwnedIdentity, initialViewContent.absolutePhotoUrl);
            } else {
                initialView.setInitial(initialViewContent.bytesOwnedIdentity, initialViewContent.initial);
            }
        });

        initialView.setOnClickListener(v -> {
            if (viewModel.getPictureLocked()) {
                return;
            }

            PopupMenu popup = new PopupMenu(initialView.getContext(), initialView);
            if (viewModel.getAbsolutePhotoUrl() != null) {
                popup.inflate(R.menu.popup_details_photo_with_clear);
            } else {
                popup.inflate(R.menu.popup_details_photo);
            }
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.popup_action_remove_image) {
                    viewModel.setAbsolutePhotoUrl(null);
                } else if (itemId == R.id.popup_action_choose_image) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                            .setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE);
                    App.startActivityForResult(this, intent, REQUEST_CODE_CHOOSE_IMAGE);
                } else if (itemId == R.id.popup_action_take_picture) {
                    try {
                        if (v.getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                            } else {
                                takePicture();
                            }
                        } else {
                            App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT);
                        }
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            });
            popup.show();
        });
    }

    private void hiddenCheckboxClicked(View checkbox) {
        if (viewModel.isProfileHidden()) {
            viewModel.setProfileHidden(false);
            hiddenProfileCheckbox.setChecked(false);
        } else {
            hiddenProfileCheckbox.setChecked(false);
            HiddenProfilePasswordCreationDialogFragment hiddenProfilePasswordCreationDialogFragment = HiddenProfilePasswordCreationDialogFragment.newInstance();
            hiddenProfilePasswordCreationDialogFragment.setOnPasswordSetCallback((String password) -> {
                byte[] salt = new byte[SettingsActivity.PIN_SALT_LENGTH];
                new SecureRandom().nextBytes(salt);
                byte[] hash = SettingsActivity.computePINHash(password, salt);
                if (hash != null) {
                    viewModel.setPasswordAndSalt(hash, salt);
                    viewModel.setProfileHidden(true);
                    hiddenProfileCheckbox.setChecked(true);
                    App.toast(R.string.toast_message_hidden_profile_password_set, Toast.LENGTH_SHORT, Gravity.CENTER);
                } else {
                    viewModel.setProfileHidden(false);
                    hiddenProfileCheckbox.setChecked(false);
                    App.toast(R.string.toast_message_hidden_profile_password_failed, Toast.LENGTH_SHORT, Gravity.CENTER);
                }
            });
            hiddenProfilePasswordCreationDialogFragment.show(getChildFragmentManager(), "dialog");
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_CHOOSE_IMAGE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startActivityForResult(new Intent(null, data.getData(), App.getContext(), SelectDetailsPhotoActivity.class), REQUEST_CODE_SELECT_ZONE);
                }
                break;
            }
            case REQUEST_CODE_TAKE_PICTURE: {
                if (resultCode == Activity.RESULT_OK && viewModel.getTakePictureUri() != null) {
                    startActivityForResult(new Intent(null, viewModel.getTakePictureUri(), App.getContext(), SelectDetailsPhotoActivity.class), REQUEST_CODE_SELECT_ZONE);
                }
                break;
            }
            case REQUEST_CODE_SELECT_ZONE: {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        String absolutePhotoUrl = data.getStringExtra(SelectDetailsPhotoActivity.CROPPED_JPEG_RETURN_INTENT_EXTRA);
                        if (absolutePhotoUrl != null) {
                            viewModel.setAbsolutePhotoUrl(absolutePhotoUrl);
                        }
                    }
                }
                break;
            }
        }
    }

    private void takePicture() throws IllegalStateException {
        if (viewModel == null || viewModel.getPictureLocked()) {
            return;
        }
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        viewModel.setTakePictureUri(null);

        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoDir = new File(requireActivity().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
            File photoFile = new File(photoDir, new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + ".jpg");
            try {
                //noinspection ResultOfMethodCallIgnored
                photoDir.mkdirs();
                if (!photoFile.createNewFile()) {
                    return;
                }
                Uri photoUri = FileProvider.getUriForFile(requireActivity(),
                        BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                        photoFile);
                viewModel.setTakePictureUri(photoUri);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                App.startActivityForResult(this, takePictureIntent, REQUEST_CODE_TAKE_PICTURE);
            } catch (IOException e) {
                Logger.w("Error creating photo capture file " + photoFile.toString());
            }
        }
    }

    public void setUseDialogBackground(boolean useDialogBackground) {
        this.useDialogBackground = useDialogBackground;
    }
    public void setLockedUserDetails(JsonKeycloakUserDetails lockedUserDetails, boolean lockPicture) {
        this.lockedUserDetails = lockedUserDetails;
        this.lockPicture = lockPicture;
    }

    public void setShowNicknameAndHidden(boolean showNicknameAndHidden) {
        this.showNicknameAndHidden = showNicknameAndHidden;
    }

    public void setDisableHidden(boolean disableHidden) {
        this.disableHidden = disableHidden;
    }
}