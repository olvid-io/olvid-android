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

package io.olvid.messenger.fragments.dialog;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.tasks.UpdateContactCustomDisplayNameAndPhotoTask;
import io.olvid.messenger.databases.tasks.UpdateLockedDiscussionTitleAndPhotoTask;
import io.olvid.messenger.databases.tasks.UpdateGroupCustomNameAndPhotoTask;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoActivity;
import io.olvid.messenger.settings.SettingsActivity;

public class EditNameAndPhotoDialogFragment extends DialogFragment implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private static final int REQUEST_CODE_CHOOSE_IMAGE = 8696;
    private static final int REQUEST_CODE_TAKE_PICTURE = 8697;
    private static final int REQUEST_CODE_SELECT_ZONE = 8698;

    private EditNameAndPhotoViewModel viewModel;

    private Button okButton;
    private InitialView initialView;
    private TextInputEditText nameEditText;
    private CheckBox customNameHueCheckbox;
    private SeekBar customNameHueSeekbar;

    final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    takePicture();
                } else {
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT);
                }
            });


    public static EditNameAndPhotoDialogFragment newInstance(FragmentActivity parentActivity, Contact contact) {
        EditNameAndPhotoDialogFragment fragment = new EditNameAndPhotoDialogFragment();
        EditNameAndPhotoViewModel viewModel = new ViewModelProvider(parentActivity).get(EditNameAndPhotoViewModel.class);
        viewModel.setContact(contact);
        return fragment;
    }

    public static EditNameAndPhotoDialogFragment newInstance(FragmentActivity parentActivity, Group group) {
        EditNameAndPhotoDialogFragment fragment = new EditNameAndPhotoDialogFragment();
        EditNameAndPhotoViewModel viewModel = new ViewModelProvider(parentActivity).get(EditNameAndPhotoViewModel.class);
        viewModel.setGroup(group);
        return fragment;
    }

    public static EditNameAndPhotoDialogFragment newInstance(FragmentActivity parentActivity, Discussion lockedDiscussion) {
        EditNameAndPhotoDialogFragment fragment = new EditNameAndPhotoDialogFragment();
        EditNameAndPhotoViewModel viewModel = new ViewModelProvider(parentActivity).get(EditNameAndPhotoViewModel.class);
        viewModel.setLockedDiscussion(lockedDiscussion);
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(EditNameAndPhotoViewModel.class);
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
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_edit_name_and_photo, container, false);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(R.string.dialog_title_edit_identity_details);
        dialogView.findViewById(R.id.button_cancel).setOnClickListener(this);
        Button resetButton = dialogView.findViewById(R.id.button_reset);
        resetButton.setOnClickListener(this);
        okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);

        switch (viewModel.getType()) {
            case GROUP:
                resetButton.setVisibility(View.VISIBLE);
                dialogTitle.setText(R.string.dialog_title_rename_group);
                dialogView.findViewById(R.id.custom_hue_group).setVisibility(View.GONE);
                dialogView.findViewById(R.id.personal_note_group).setVisibility(View.VISIBLE);
                break;
            case CONTACT:
                resetButton.setVisibility(View.VISIBLE);
                dialogTitle.setText(R.string.dialog_title_rename_contact);
                dialogView.findViewById(R.id.custom_hue_group).setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.personal_note_group).setVisibility(View.VISIBLE);
            break;
            case LOCKED_DISCUSSION:
                resetButton.setVisibility(View.GONE);
                dialogTitle.setText(R.string.dialog_title_rename_discussion);
                dialogView.findViewById(R.id.custom_hue_group).setVisibility(View.GONE);
                dialogView.findViewById(R.id.personal_note_group).setVisibility(View.GONE);
                break;
        }

        initialView = dialogView.findViewById(R.id.initial_view);
        initialView.setOnClickListener(this);

        nameEditText = dialogView.findViewById(R.id.edit_name_text_view);
        nameEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setNickname(s.toString());
            }
        });
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            nameEditText.setImeOptions(nameEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        nameEditText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                updateAndDismiss();
                return true;
            }
            return false;
        });
        nameEditText.setText(viewModel.getNickname());
        nameEditText.requestFocus();

        customNameHueCheckbox = dialogView.findViewById(R.id.custom_hue_checkbox);
        customNameHueCheckbox.setOnClickListener(this);

        customNameHueSeekbar = dialogView.findViewById(R.id.custom_hue_seekbar);
        if (viewModel.getCustomNameHue() != null) {
            customNameHueSeekbar.setProgress(viewModel.getCustomNameHue());
        }
        customNameHueSeekbar.setOnSeekBarChangeListener(this);

        EditText personalNoteEditText = dialogView.findViewById(R.id.personal_note_edit_text);
        personalNoteEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setPersonalNote(s.toString());
            }
        });
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            personalNoteEditText.setImeOptions(personalNoteEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        personalNoteEditText.setText(viewModel.getPersonalNote());

        viewModel.getValid().observe(this, valid -> okButton.setEnabled(valid != null && valid));
        viewModel.getInitialViewLiveData().observe(this, initialViewContent -> {
            if (initialViewContent == null) {
                initialView.setInitial(new byte[0], "");
            } else {
                if (initialViewContent.absolutePhotoUrl != null) {
                    initialView.setLocked(initialViewContent.type == EditNameAndPhotoViewModel.TYPE.LOCKED_DISCUSSION);
                    initialView.setAbsolutePhotoUrl(initialViewContent.bytesInitial, initialViewContent.absolutePhotoUrl);
                } else {
                    switch (initialViewContent.type) {
                        case GROUP:
                            initialView.setLocked(false);
                            initialView.setGroup(initialViewContent.bytesInitial);
                            break;
                        case CONTACT:
                            initialView.setLocked(false);
                            initialView.setInitial(initialViewContent.bytesInitial, initialViewContent.initial);
                            break;
                        case LOCKED_DISCUSSION:
                            initialView.setLocked(true);
                            initialView.setInitial(new byte[0], "");
                            break;
                    }
                }
            }
        });

        viewModel.getCustomNameHueLiveData().observe(this, (Integer hue) -> {
            if (hue == null) {
                customNameHueCheckbox.setChecked(false);
                nameEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.greyTint));
            } else {
                customNameHueCheckbox.setChecked(true);
                nameEditText.setTextColor(InitialView.getTextColor(requireContext(), new byte[1], hue));
            }
        });

        return dialogView;
    }

    private void updateAndDismiss() {
        switch (viewModel.getType()) {
            case GROUP: {
                Group group = viewModel.getGroup();
                if (group == null) {
                    break;
                }
                final String customName;
                final String absoluteCustomPhotoUrl;
                final String personalNote;
                if (Objects.equals(group.name, viewModel.getNickname())) {
                    // nickname was reset
                    customName = null;
                } else {
                    customName = viewModel.getNickname();
                }

                if (Objects.equals(App.absolutePathFromRelative(group.photoUrl), viewModel.getAbsolutePhotoUrl())) {
                    // photo was reset
                    absoluteCustomPhotoUrl = null;
                } else if (viewModel.getAbsolutePhotoUrl() == null && group.photoUrl != null) {
                    // photo was removed
                    absoluteCustomPhotoUrl = "";
                } else {
                    // new photo or still the same custom photo
                    absoluteCustomPhotoUrl = viewModel.getAbsolutePhotoUrl();
                }
                personalNote = viewModel.getPersonalNote();

                App.runThread(new UpdateGroupCustomNameAndPhotoTask(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, customName, absoluteCustomPhotoUrl, personalNote));
                break;
            }
            case CONTACT: {
                Contact contact = viewModel.getContact();
                if (contact == null) {
                    break;
                }
                final String customName;
                final String absoluteCustomPhotoUrl;
                final Integer customNameHue;
                final String personalNote;
                if (Objects.equals(contact.displayName, viewModel.getNickname())) {
                    // nickname was reset
                    customName = null;
                } else {
                    customName = viewModel.getNickname();
                }

                if (Objects.equals(App.absolutePathFromRelative(contact.photoUrl), viewModel.getAbsolutePhotoUrl())) {
                    // photo was reset
                    absoluteCustomPhotoUrl = null;
                } else if (viewModel.getAbsolutePhotoUrl() == null && contact.photoUrl != null) {
                    // photo was removed
                    absoluteCustomPhotoUrl = "";
                } else {
                    // new photo or still the same custom photo
                    absoluteCustomPhotoUrl = viewModel.getAbsolutePhotoUrl();
                }

                customNameHue = viewModel.getCustomNameHue();
                personalNote = viewModel.getPersonalNote();
                App.runThread(new UpdateContactCustomDisplayNameAndPhotoTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity, customName, absoluteCustomPhotoUrl, customNameHue, personalNote));
                break;
            }
            case LOCKED_DISCUSSION:
                Discussion discussion = viewModel.getDiscussion();
                if (discussion == null) {
                    break;
                }
                final String customName = viewModel.getNickname();
                final String absoluteCustomPhotoUrl;
                if (Objects.equals(App.absolutePathFromRelative(discussion.photoUrl), viewModel.getAbsolutePhotoUrl())) {
                    // photo did not change
                    absoluteCustomPhotoUrl = "";
                } else {
                    // new photo (can be null if it was removed)
                    absoluteCustomPhotoUrl = viewModel.getAbsolutePhotoUrl();
                }
                App.runThread(new UpdateLockedDiscussionTitleAndPhotoTask(discussion.id, customName, absoluteCustomPhotoUrl));
                break;
        }
        dismiss();
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.button_ok) {
            updateAndDismiss();
        } else if (viewId == R.id.button_cancel) {
            dismiss();
        } else if (viewId == R.id.button_reset) {
            customNameHueSeekbar.setProgress(180);
            viewModel.reset();
            nameEditText.setText(viewModel.getNickname());
            nameEditText.setSelection(0, viewModel.getNickname().length());
        } else if (viewId == R.id.initial_view) {
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
        } else if (viewId == R.id.custom_hue_checkbox) {
            if (viewModel.getCustomNameHue() != null) {
                viewModel.setCustomNameHue(null);
            } else {
                viewModel.setCustomNameHue(customNameHueSeekbar.getProgress());
            }
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
                nameEditText.requestFocus();
                break;
            }
        }
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        viewModel.clearData();
    }

    private void takePicture() throws IllegalStateException {
        if (viewModel == null) {
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

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        viewModel.setCustomNameHue(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}


    private static class InitialViewContent {
        public final byte[] bytesInitial;
        public final EditNameAndPhotoViewModel.TYPE type;
        public final String initial;
        public final String absolutePhotoUrl;

        public InitialViewContent(@NonNull byte[] bytesInitial, EditNameAndPhotoViewModel.TYPE type, @Nullable String initial, @Nullable String absolutePhotoUrl) {
            this.bytesInitial = bytesInitial;
            this.type = type;
            this.initial = initial;
            this.absolutePhotoUrl = absolutePhotoUrl;
        }
    }


    public static class EditNameAndPhotoViewModel extends ViewModel {
        enum TYPE {
            GROUP,
            CONTACT,
            LOCKED_DISCUSSION,
        }
        private TYPE type;
        private Contact contact;
        private Group group;
        private Discussion discussion;

        private String nickname;
        private byte[] initialBytes;
        private String absolutePhotoUrl;
        private Uri takePictureUri;
        private Integer customNameHue;
        private String personalNote;

        private final MutableLiveData<InitialViewContent> initialViewLiveData = new MutableLiveData<>(null);
        private final MutableLiveData<Boolean> validLiveData = new MutableLiveData<>(false);
        private final MutableLiveData<Integer> customNameHueLiveData = new MutableLiveData<>(null);

        public void setContact(Contact contact) {
            this.type = TYPE.CONTACT;
            this.contact = contact;
            this.group = null;
            this.discussion = null;

            nickname = contact.getCustomDisplayName();
            initialBytes = contact.bytesContactIdentity;
            absolutePhotoUrl = App.absolutePathFromRelative(contact.getCustomPhotoUrl());
            setCustomNameHue(contact.customNameHue);
            personalNote = contact.personalNote;

            updateInitialViewLiveData();
        }

        public void setGroup(Group group) {
            this.type = TYPE.GROUP;
            this.contact = null;
            this.group = group;
            this.discussion = null;

            nickname = group.getCustomName();
            initialBytes = group.bytesGroupOwnerAndUid;
            absolutePhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl());
            setCustomNameHue(null);
            personalNote = group.personalNote;

            updateInitialViewLiveData();
        }

        public void setLockedDiscussion(Discussion discussion) {
            this.type = TYPE.LOCKED_DISCUSSION;
            this.contact = null;
            this.group = null;
            this.discussion = discussion;

            nickname = discussion.title;
            initialBytes = new byte[0];
            absolutePhotoUrl = App.absolutePathFromRelative(discussion.photoUrl);
            setCustomNameHue(null);
            personalNote = null;

            updateInitialViewLiveData();
        }

        public void setNickname(String nickname) {
            // check if initial changed
            boolean shouldUpdateInitialViewLiveData = false;
            if (!App.getInitial(nickname).equals(App.getInitial(this.nickname))) {
                shouldUpdateInitialViewLiveData = true;
            }

            this.nickname = nickname;
            validLiveData.postValue(nickname != null && nickname.trim().length() > 0);
            if (shouldUpdateInitialViewLiveData) {
                updateInitialViewLiveData();
            }
        }

        public void setAbsolutePhotoUrl(String absolutePhotoUrl) {
            this.absolutePhotoUrl = absolutePhotoUrl;
            updateInitialViewLiveData();
        }

        public Uri getTakePictureUri() {
            return takePictureUri;
        }

        public void setTakePictureUri(Uri takePictureUri) {
            this.takePictureUri = takePictureUri;
        }

        public void reset() {
            switch (type) {
                case GROUP:
                    nickname = group.name;
                    absolutePhotoUrl = App.absolutePathFromRelative(group.photoUrl);
                    break;
                case CONTACT:
                    nickname = contact.displayName;
                    absolutePhotoUrl = App.absolutePathFromRelative(contact.photoUrl);
                    break;
                case LOCKED_DISCUSSION:
                    nickname = discussion.title;
                    absolutePhotoUrl = App.absolutePathFromRelative(discussion.photoUrl);
                    break;
            }
            validLiveData.postValue(true);
            setCustomNameHue(null);
            updateInitialViewLiveData();
        }



        public LiveData<InitialViewContent> getInitialViewLiveData() {
            return initialViewLiveData;
        }

        public LiveData<Boolean> getValid() {
            return validLiveData;
        }

        public String getAbsolutePhotoUrl() {
            return absolutePhotoUrl;
        }

        public String getNickname() {
            return (nickname == null || nickname.trim().length() == 0) ? null : nickname.trim();
        }

        public void setCustomNameHue(Integer customNameHue) {
            this.customNameHue = customNameHue;
            customNameHueLiveData.postValue(customNameHue);
        }

        public Integer getCustomNameHue() {
            return customNameHue;
        }

        public MutableLiveData<Integer> getCustomNameHueLiveData() {
            return customNameHueLiveData;
        }

        public String getPersonalNote() {
            return (personalNote == null || personalNote.trim().length() == 0) ? null : personalNote.trim();
        }

        public void setPersonalNote(String personalNote) {
            this.personalNote = personalNote;
        }

        private void updateInitialViewLiveData() {
            initialViewLiveData.postValue(new InitialViewContent(
                    initialBytes,
                    type,
                    App.getInitial(getNickname()),
                    absolutePhotoUrl
            ));
        }

        public void clearData() {
            this.contact = null;
            this.group = null;
            this.nickname = null;
            this.initialBytes = null;
            this.absolutePhotoUrl = null;
            this.takePictureUri = null;
            this.personalNote = null;
            initialViewLiveData.postValue(null);
            validLiveData.postValue(false);
        }

        public TYPE getType() {
            return type;
        }

        public Group getGroup() {
            return group;
        }

        public Contact getContact() {
            return contact;
        }

        public Discussion getDiscussion() {
            return discussion;
        }
    }
}
