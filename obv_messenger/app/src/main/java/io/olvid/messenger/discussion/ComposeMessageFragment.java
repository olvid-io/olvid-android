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

package io.olvid.messenger.discussion;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.DiscussionInputEditText;
import io.olvid.messenger.customClasses.DraftAttachmentAdapter;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.JpegUtils;
import io.olvid.messenger.customClasses.MessageAttachmentAdapter;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask;
import io.olvid.messenger.databases.tasks.ClearDraftReplyTask;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask;
import io.olvid.messenger.databases.tasks.SaveDraftTask;
import io.olvid.messenger.settings.SettingsActivity;

public class ComposeMessageFragment extends Fragment implements View.OnClickListener, MessageAttachmentAdapter.AttachmentLongClickListener, PopupMenu.OnMenuItemClickListener {
    public static final int ICON_EPHEMERAL_SETTINGS = 1;
    public static final int ICON_ATTACH_FILE = 2;
    public static final int ICON_ATTACH_PICTURE = 3;
    public static final int ICON_TAKE_PICTURE = 4;
    public static final int ICON_TAKE_VIDEO = 5;

    public static final Integer[] DEFAULT_ICON_ORDER = {ICON_EPHEMERAL_SETTINGS, ICON_ATTACH_FILE, ICON_ATTACH_PICTURE, ICON_TAKE_PICTURE, ICON_TAKE_VIDEO};


    private FragmentActivity activity;
    private DiscussionViewModel discussionViewModel;
    private ComposeMessageViewModel composeMessageViewModel;
    private AudioAttachmentServiceBinding audioAttachmentServiceBinding;
    private InitialView ownedIdentityInitialView;

    private DiscussionActivity.DiscussionDelegate discussionDelegate;

    private VoiceMessageRecorder voiceMessageRecorder;

    private DiscussionInputEditText newMessageEditText;
    private ImageButton sendButton;

    private boolean hasCamera;
    private boolean animateLayoutChanges;

    private ImageView attachStuffPlus;
    private ImageView attachStuffPlusGoldenDot;
    private LinearLayout attachIconsGroup;
    private ImageView directAttachVoiceMessageImageView;

    private ViewGroup composeMessageReplyGroup;
    private long composeMessageReplyMessageId;
    private TextView composeMessageReplySenderName;
    private TextView composeMessageReplyBody;
    private TextView composeMessageReplyAttachmentCount;

    private DraftAttachmentAdapter newMessageAttachmentAdapter;

    private final ActivityResultLauncher<String> requestPermissionForPictureLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    takePicture();
                } else {
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionForVideoLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    takeVideo();
                } else {
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionForAudioLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    voiceMessageRecorder.setRecordPermission(true);
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_voice_message_explanation)
                            .setMessage(R.string.dialog_message_voice_message_explanation_blocked)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setNeutralButton(R.string.button_label_app_settings, (DialogInterface dialog, int which) -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            });
                    builder.create().show();
                }
            });


    private final ActivityResultLauncher<String> requestPermissionForAudioAfterRationaleLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    voiceMessageRecorder.setRecordPermission(true);
                } else {
                    App.toast(R.string.toast_message_audio_permission_denied, Toast.LENGTH_SHORT);
                }
            });


    private final ActivityResultLauncher<Intent> attachFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult activityResult) -> {
                if (activityResult == null || activityResult.getData() == null || activityResult.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri dataUri = activityResult.getData().getData();
                if (dataUri != null) {
                    App.runThread(new AddFyleToDraftFromUriTask(dataUri, discussionViewModel.getDiscussionId()));
                } else {
                    ClipData clipData = activityResult.getData().getClipData();
                    if (clipData != null) {
                        Set<Uri> uris = new HashSet<>();
                        // Samsung Android 7.0 bug --> different files may return the same uri!
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            ClipData.Item item = clipData.getItemAt(i);
                            final Uri uri = item.getUri();
                            uris.add(uri);
                        }
                        if (uris.size() != clipData.getItemCount()) {
                            App.toast(R.string.toast_message_android_bug_attach_duplicate_uri, Toast.LENGTH_LONG);
                        }
                        for (Uri uri : uris) {
                            App.runThread(new AddFyleToDraftFromUriTask(uri, discussionViewModel.getDiscussionId()));
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult activityResult) -> {
                if (activityResult == null ||activityResult.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                final Uri photoUri = composeMessageViewModel.getPhotoOrVideoUri();
                final File photoFile = composeMessageViewModel.getPhotoOrVideoFile();
                final long discussionId = discussionViewModel.getDiscussionId();
                if (photoUri != null && photoFile != null) {
                    App.runThread(() -> {
                        int cameraResolutionSetting = SettingsActivity.getCameraResolution();
                        if (cameraResolutionSetting != -1) {
                            JpegUtils.resize(photoFile, cameraResolutionSetting);
                        }
                        new AddFyleToDraftFromUriTask(photoUri, photoFile, discussionId).run();
                    });
                }
            });

    private final ActivityResultLauncher<Intent> takeVideoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult activityResult) -> {
                if (activityResult == null ||activityResult.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                final Uri videoUri = composeMessageViewModel.getPhotoOrVideoUri();
                final File videoFile = composeMessageViewModel.getPhotoOrVideoFile();
                final long discussionId = discussionViewModel.getDiscussionId();
                if (videoUri != null && videoFile != null) {
                    App.runThread(new AddFyleToDraftFromUriTask(videoUri, videoFile, discussionId));
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        discussionViewModel = new ViewModelProvider(activity).get(DiscussionViewModel.class);
        composeMessageViewModel = new ViewModelProvider(activity).get(ComposeMessageViewModel.class);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        View rootView = inflater.inflate(R.layout.fragment_discussion_compose, null);
        ConstraintLayout rootConstraintLayout = rootView.findViewById(R.id.root_constraint_layout);

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        newMessageEditText = rootConstraintLayout.findViewById(R.id.compose_message_edit_text);
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            newMessageEditText.setImeOptions(newMessageEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        newMessageEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                composeMessageViewModel.setNewMessageText(editable);
                hasText = editable != null && editable.length() > 0;
                if (hasText) {
                    if (!setShowAttachIcons(false, false)) {
                        updateComposeAreaLayout();
                    }
                } else {
                    updateComposeAreaLayout();
                }
            }
        });
        newMessageEditText.setOnClickListener(v -> {
            if (discussionDelegate != null) {
                discussionDelegate.closeSwipeMenu();
            }
            setShowAttachIcons(false, true);
        });
        newMessageEditText.requestFocus();
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        newMessageEditText.setImeContentCommittedHandler((contentUri, fileName, mimeType, callMeWhenDone) -> {
            new SaveDraftTask(discussionViewModel.getDiscussionId(), composeMessageViewModel.getTrimmedNewMessageText(), composeMessageViewModel.getDraftMessage().getValue()).run();
            new AddFyleToDraftFromUriTask(contentUri, fileName, mimeType, discussionViewModel.getDiscussionId()).run();
            if (callMeWhenDone != null) {
                callMeWhenDone.run();
            }
        });
        newMessageEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        if (SettingsActivity.getSendWithHardwareEnter()) {
            newMessageEditText.setOnKeyListener((View v, int keyCode, KeyEvent event) -> {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN && !event.isShiftPressed()) {
                    sendMessage();
                    return true;
                }
                return false;
            });
        }

        ownedIdentityInitialView = rootView.findViewById(R.id.owned_identity_initial_view);
        AppSingleton.getCurrentIdentityLiveData().observe(this, (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return;
            }
            ownedIdentityInitialView.setLocked(false);
            ownedIdentityInitialView.setInactive(!ownedIdentity.active);
            ownedIdentityInitialView.setKeycloakCertified(ownedIdentity.keycloakManaged);
            if (ownedIdentity.photoUrl != null) {
                ownedIdentityInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, ownedIdentity.photoUrl);
            } else {
                ownedIdentityInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, App.getInitial(ownedIdentity.getCustomDisplayName()));
            }
        });
        ownedIdentityInitialView.setOnClickListener(v -> {
            Discussion discussion = discussionViewModel.getDiscussion().getValue();
            if (discussion != null) {
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                DiscussionOwnedIdentityPopupWindow discussionOwnedIdentityPopupWindow = new DiscussionOwnedIdentityPopupWindow(activity, ownedIdentityInitialView, discussion.bytesContactIdentity, discussion.bytesGroupOwnerAndUid);
                new Handler(Looper.getMainLooper()).postDelayed(discussionOwnedIdentityPopupWindow::open, 100);
            }
        });


        rootView.findViewById(R.id.white_bottom_mask).addOnLayoutChangeListener(composeMessageSizeChangeListener);

        sendButton = rootConstraintLayout.findViewById(R.id.compose_message_send_button);
        sendButton.setOnClickListener(this);

        hasCamera = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);

        attachStuffPlus = rootConstraintLayout.findViewById(R.id.attach_stuff_plus);
        attachStuffPlus.setOnClickListener(this);
        attachStuffPlusGoldenDot = rootConstraintLayout.findViewById(R.id.golden_dot);

        View recordingOverlay = rootConstraintLayout.findViewById(R.id.recording_overlay);
        recordingOverlay.setOnClickListener(this);
        voiceMessageRecorder = new VoiceMessageRecorder(activity, recordingOverlay, (boolean rationaleWasShown) -> {
            if (rationaleWasShown) {
                requestPermissionForAudioAfterRationaleLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                requestPermissionForAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });
        directAttachVoiceMessageImageView = rootConstraintLayout.findViewById(R.id.direct_attach_voice_message_button);
        directAttachVoiceMessageImageView.setOnTouchListener(voiceMessageRecorder);

        attachIconsGroup = rootConstraintLayout.findViewById(R.id.attach_icons_group);

        ViewGroup composeMessageCard = rootConstraintLayout.findViewById(R.id.compose_message_card);

        composeMessageReplyGroup = rootConstraintLayout.findViewById(R.id.compose_message_reply_group);
        composeMessageReplySenderName = rootConstraintLayout.findViewById(R.id.compose_message_reply_sender_name);
        composeMessageReplyBody = rootConstraintLayout.findViewById(R.id.compose_message_reply_body);
        composeMessageReplyAttachmentCount = rootConstraintLayout.findViewById(R.id.compose_message_reply_attachment_count);
        ImageView composeMessageReplyClear = rootConstraintLayout.findViewById(R.id.compose_message_reply_clear);

        composeMessageReplyClear.setOnClickListener(v -> App.runThread(new ClearDraftReplyTask(discussionViewModel.getDiscussionId())));
        composeMessageReplyGroup.setOnClickListener(v -> {
            if (discussionDelegate != null) {
                discussionDelegate.scrollToMessage(composeMessageReplyMessageId);
            }
        });

        // attachments recycler view
        EmptyRecyclerView newMessageAttachmentRecyclerView = composeMessageCard.findViewById(R.id.attachments_recycler_view);
        newMessageAttachmentAdapter = new DraftAttachmentAdapter(activity, audioAttachmentServiceBinding);
        newMessageAttachmentAdapter.setAttachmentLongClickListener(this);

        LinearLayoutManager attachmentListLinearLayoutManager = new LinearLayoutManager(activity);
        attachmentListLinearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        newMessageAttachmentRecyclerView.setLayoutManager(attachmentListLinearLayoutManager);
        newMessageAttachmentRecyclerView.setAdapter(newMessageAttachmentAdapter);
        newMessageAttachmentRecyclerView.setHideIfEmpty(true);
        newMessageAttachmentRecyclerView.addItemDecoration(new DraftAttachmentAdapter.AttachmentSpaceItemDecoration(activity));

        composeMessageViewModel.getDraftMessageFyles().observe(this, newMessageAttachmentAdapter);
        composeMessageViewModel.getDraftMessageFyles().observe(this, (List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses) -> {
            hasAttachments = fyleAndStatuses != null && fyleAndStatuses.size() > 0;
            updateComposeAreaLayout();
        });

        composeMessageViewModel.getDraftMessage().observe(this, new Observer<Message>() {
            private Message message = null;

            @Override
            public void onChanged(Message message) {

                if (message != null && this.message != null) {
                    if (message.id == this.message.id) {
                        String messageEditText = newMessageEditText.getText() == null ? "" : newMessageEditText.getText().toString();
                        String oldMessageText = this.message.contentBody == null ? "" : this.message.contentBody;
                        String newMessageText = message.contentBody == null ? "" : message.contentBody;

                        if (messageEditText.equals(oldMessageText) && !oldMessageText.equals(newMessageText)) {
                            // the message text changed, but the input did not --> probably an external modification so we load the body from the new draft
                            loadDraft(message);
                        }
                        this.message = message;
                        return;
                    }
                }
                this.message = message;
                loadDraft(message);
            }

            private void loadDraft(@Nullable final Message draftMessage) {
                if (draftMessage != null && draftMessage.contentBody != null) {
                    try {
                        newMessageEditText.setText(draftMessage.contentBody);
                        newMessageEditText.setSelection(draftMessage.contentBody.length());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        composeMessageViewModel.getDraftMessageReply().observe(this, draftReplyMessage -> {
            if (draftReplyMessage == null) {
                composeMessageReplyGroup.setVisibility(View.GONE);
                composeMessageReplyMessageId = -1;
            } else {
                composeMessageReplyGroup.setVisibility(View.VISIBLE);
                composeMessageReplyMessageId = draftReplyMessage.id;

                String displayName = AppSingleton.getContactCustomDisplayName(draftReplyMessage.senderIdentifier);
                if (displayName != null) {
                    composeMessageReplySenderName.setText(displayName);
                } else {
                    composeMessageReplySenderName.setText(R.string.text_deleted_contact);
                }
                int color = InitialView.getTextColor(activity, draftReplyMessage.senderIdentifier, AppSingleton.getContactCustomHue(draftReplyMessage.senderIdentifier));
                composeMessageReplySenderName.setTextColor(color);

                Drawable drawable = ContextCompat.getDrawable(activity, R.drawable.background_reply_white);
                if (drawable instanceof LayerDrawable) {
                    Drawable border = ((LayerDrawable) drawable).findDrawableByLayerId(R.id.reply_color_border);
                    border.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    ((LayerDrawable) drawable).setDrawableByLayerId(R.id.reply_color_border, border);
                    composeMessageReplyGroup.setBackground(drawable);
                }

                if (draftReplyMessage.totalAttachmentCount > 0) {
                    composeMessageReplyAttachmentCount.setVisibility(View.VISIBLE);
                    composeMessageReplyAttachmentCount.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, draftReplyMessage.totalAttachmentCount, draftReplyMessage.totalAttachmentCount));
                } else {
                    composeMessageReplyAttachmentCount.setVisibility(View.GONE);
                }
                if (draftReplyMessage.getStringContent(activity).length() == 0) {
                    composeMessageReplyBody.setVisibility(View.GONE);
                } else {
                    composeMessageReplyBody.setVisibility(View.VISIBLE);
                    composeMessageReplyBody.setText(draftReplyMessage.getStringContent(activity));
                }
            }
        });


        composeMessageViewModel.getEphemeralSettingsChanged().observe(this, (Boolean changed) -> hideOrShowEphemeralMarker(changed != null && changed));

        composeMessageViewModel.getRecordingLiveData().observe(this, recording -> this.recording = recording);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        updateIconsToShow(metrics.widthPixels);

        return rootView;
    }

    public void setDiscussionDelegate(DiscussionActivity.DiscussionDelegate discussionDelegate) {
        this.discussionDelegate = discussionDelegate;
    }

    public void setAudioAttachmentServiceBinding(AudioAttachmentServiceBinding audioAttachmentServiceBinding) {
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding;
    }

    private void sendMessage() {
        if (discussionViewModel.getDiscussionId() != null) {
            if (composeMessageViewModel.getTrimmedNewMessageText() != null || composeMessageViewModel.hasAttachments()) {
                if (discussionDelegate != null) {
                    discussionDelegate.markMessagesRead();
                }
                App.runThread(new PostMessageInDiscussionTask(
                        composeMessageViewModel.getTrimmedNewMessageText(),
                        discussionViewModel.getDiscussionId(),
                        true
                ));
                newMessageEditText.setText("");
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        if (newMessageEditText != null) {
            try {
                if (composeMessageViewModel.getRawNewMessageText() != null) {
                    try {
                        newMessageEditText.setText(composeMessageViewModel.getRawNewMessageText());
                        newMessageEditText.setSelection(composeMessageViewModel.getRawNewMessageText().length());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    newMessageEditText.setText("");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        updateComposeAreaLayout();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (voiceMessageRecorder != null) {
            voiceMessageRecorder.stopRecord(true);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (voiceMessageRecorder.recording) {
            voiceMessageRecorder.stopRecord(false);
        } else if (id == R.id.attach_timer) {
            SingleMessageEphemeralSettingsDialogFragment dialogFragment = SingleMessageEphemeralSettingsDialogFragment.newInstance(discussionViewModel.getDiscussionId());
            dialogFragment.show(getChildFragmentManager(), "dialog");
        } else if (id == R.id.compose_message_send_button) {
            sendMessage();
        } else if (id == R.id.attach_configure) {
            showIconOrderSelector();
        } else if (id == R.id.attach_file) {
            if (discussionDelegate != null) {
                discussionDelegate.doNotMarkAsReadOnPause();
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            App.prepareForStartActivityForResult(this);
            attachFileLauncher.launch(intent);
        } else if (id == R.id.attach_image) {
            if (discussionDelegate != null) {
                discussionDelegate.doNotMarkAsReadOnPause();
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            App.prepareForStartActivityForResult(this);
            attachFileLauncher.launch(intent);
        } else if (id == R.id.attach_camera) {
            if (hasCamera) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionForPictureLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    takePicture();
                }
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT);
            }
        } else if (id == R.id.attach_video) {
            if (hasCamera) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionForVideoLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    takeVideo();
                }
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT);
            }
        } else if (id == R.id.attach_stuff_plus) {
            if (showAttachIcons || neverOverflow) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(newMessageEditText.getWindowToken(), 0);
                }
                new Handler(Looper.getMainLooper()).postDelayed(this::showOverflowedPopupMenu, 100);
            } else {
                setShowAttachIcons(true, true);
            }
        }
    }

    private void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        composeMessageViewModel.setPhotoOrVideoUri(null);
        composeMessageViewModel.setPhotoOrVideoFile(null);

        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoDir = new File(activity.getCacheDir(), App.CAMERA_PICTURE_FOLDER);
            File photoFile = new File(photoDir, new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + ".jpg");
            try {
                //noinspection ResultOfMethodCallIgnored
                photoDir.mkdirs();
                if (!photoFile.createNewFile()) {
                    return;
                }
                composeMessageViewModel.setPhotoOrVideoFile(photoFile);
                Uri photoUri = FileProvider.getUriForFile(activity,
                        BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                        photoFile);
                composeMessageViewModel.setPhotoOrVideoUri(photoUri);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                if (discussionDelegate != null) {
                    discussionDelegate.doNotMarkAsReadOnPause();
                }
                App.prepareForStartActivityForResult(this);
                takePictureLauncher.launch(takePictureIntent);
            } catch (IOException e) {
                Logger.w("Error creating photo capture file " + photoFile.toString());
            }
        }
    }

    private void takeVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        composeMessageViewModel.setPhotoOrVideoUri(null);
        composeMessageViewModel.setPhotoOrVideoFile(null);

        if (takeVideoIntent.resolveActivity(activity.getPackageManager()) != null) {
            File videoDir = new File(activity.getCacheDir(), App.CAMERA_PICTURE_FOLDER);
            File videoFile = new File(videoDir, new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + ".mp4");
            try {
                //noinspection ResultOfMethodCallIgnored
                videoDir.mkdirs();
                if (!videoFile.createNewFile()) {
                    return;
                }
                composeMessageViewModel.setPhotoOrVideoFile(videoFile);
                Uri photoUri = FileProvider.getUriForFile(activity,
                        BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                        videoFile);
                composeMessageViewModel.setPhotoOrVideoUri(photoUri);
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                if (discussionDelegate != null) {
                    discussionDelegate.doNotMarkAsReadOnPause();
                }
                App.prepareForStartActivityForResult(this);
                takeVideoLauncher.launch(takeVideoIntent);
            } catch (IOException e) {
                Logger.w("Error creating video capture file " + videoFile.toString());
            }
        }
    }


    private FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus;

    @Override
    public void attachmentLongClicked(FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus, View clickedView, MessageAttachmentAdapter.Visibility visibility, boolean readOnce, boolean multipleAttachments) {
        this.longClickedFyleAndStatus = longClickedFyleAndStatus;

        PopupMenu popup = new PopupMenu(activity, clickedView);
        popup.inflate(R.menu.popup_attachment_incomplete_or_draft);
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.popup_action_delete_attachment) {
            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(getString(R.string.dialog_message_delete_attachment, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fileName))
                    .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteAttachmentTask(longClickedFyleAndStatus)))
                    .setNegativeButton(R.string.button_label_cancel, null);
            builder.create().show();
            return true;
        } else if (itemId == R.id.popup_action_open_attachment) {
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(longClickedFyleAndStatus.fyleMessageJoinWithStatus.mimeType, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fileName)) && SettingsActivity.useInternalImageViewer()) {
                // we do not mark as opened here as this is done in the gallery activity
                App.openDiscussionGalleryActivity(activity, discussionViewModel.getDiscussionId(), longClickedFyleAndStatus.fyleMessageJoinWithStatus.messageId, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                if (discussionDelegate != null) {
                    discussionDelegate.doNotMarkAsReadOnPause();
                }
            } else {
                App.openFyleInExternalViewer(activity, longClickedFyleAndStatus, () -> {
                    if (discussionDelegate != null) {
                        discussionDelegate.doNotMarkAsReadOnPause();
                    }
                    longClickedFyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
                });
            }
            return true;
        }
        return false;
    }


    // region implement ComposeMessageDelegate

    interface ComposeMessageDelegate {
        void setDiscussionId(long discussionId);
        void hideSoftInputKeyboard();
        void showSoftInputKeyboard();
        boolean stopVoiceRecorderIfRecording(); // returns true if a recording was indeed in progress
        void addComposeMessageHeightListener(ComposeMessageHeightListener composeMessageHeightListener);
        void removeComposeMessageHeightListener(ComposeMessageHeightListener composeMessageHeightListener);
        void setAnimateLayoutChanges(boolean animateLayoutChanges);
    }

    @NonNull
    private final ComposeMessageDelegate composeMessageDelegate = new ComposeMessageDelegate() {
        @Override
        public void setDiscussionId(long discussionId) {
            if (newMessageEditText != null) {
                newMessageEditText.setText("");
            }
            if (newMessageAttachmentAdapter != null) {
                newMessageAttachmentAdapter.setDiscussionId(discussionId);
            }
        }

        @Override
        public void hideSoftInputKeyboard() {
            if (activity != null) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && newMessageEditText != null) {
                    imm.hideSoftInputFromWindow(newMessageEditText.getWindowToken(), 0);
                }
                setShowAttachIcons(true, true);
            }
        }

        @Override
        public void showSoftInputKeyboard() {
            if (activity != null) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && newMessageEditText != null) {
                    imm.showSoftInput(newMessageEditText, InputMethodManager.SHOW_IMPLICIT);
                }
                setShowAttachIcons(false, true);
            }
        }

        @Override
        public boolean stopVoiceRecorderIfRecording() {
            if (voiceMessageRecorder != null && voiceMessageRecorder.recording) {
                voiceMessageRecorder.stopRecord(true);
                return true;
            }
            return false;
        }

        @Override
        public void addComposeMessageHeightListener(ComposeMessageHeightListener composeMessageHeightListener) {
            synchronized (composeMessageHeightListeners) {
                composeMessageHeightListeners.add(composeMessageHeightListener);
            }
        }

        @Override
        public void removeComposeMessageHeightListener(ComposeMessageHeightListener composeMessageHeightListener) {
            synchronized (composeMessageHeightListeners) {
                composeMessageHeightListeners.remove(composeMessageHeightListener);
            }
        }

        @Override
        public void setAnimateLayoutChanges(boolean animateLayoutChanges) {
            ComposeMessageFragment.this.animateLayoutChanges = animateLayoutChanges;
        }
    };

    @NonNull
    ComposeMessageDelegate getComposeMessageDelegate() {
        return composeMessageDelegate;
    }

    // endregion

    // region icon order selector

    private static final int VIEW_TYPE_ICON = 1;
    private static final int VIEW_TYPE_SEPARATOR = 2;

    private List<Integer> adapterIcons;
    private ItemTouchHelper itemTouchHelper = null;

    private void showIconOrderSelector() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        RecyclerView iconOrderRecyclerView = new RecyclerView(activity);
        iconOrderRecyclerView.setPadding(0, 2*fourDp, 0, 0);
        iconOrderRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        DiffUtil.ItemCallback<Integer> diffUtilCallback = new DiffUtil.ItemCallback<Integer>() {
            @Override
            public boolean areItemsTheSame(@NonNull Integer oldItem, @NonNull Integer newItem) {
                return Objects.equals(oldItem, newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Integer oldItem, @NonNull Integer newItem) {
                return Objects.equals(oldItem, newItem);
            }
        };

        ListAdapter<Integer, IconOrderViewHolder> adapter = new ListAdapter<Integer, IconOrderViewHolder>(diffUtilCallback) {
            @NonNull
            @Override
            public IconOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == VIEW_TYPE_ICON) {
                    return new IconOrderViewHolder(inflater.inflate(R.layout.item_view_attach_icon_orderer, parent, false), iconOrderViewHolder -> {
                        if (itemTouchHelper != null) {
                            itemTouchHelper.startDrag(iconOrderViewHolder);
                        }
                    });
                } else {
                    return new IconOrderViewHolder(inflater.inflate(R.layout.item_view_attach_icon_orderer_separator, parent, false), null);
                }
            }

            @Override
            public void onBindViewHolder(@NonNull IconOrderViewHolder holder, int position) {
                Integer icon = adapterIcons.get(position);
                if (icon == null) {
                    return;
                }

                if (icon != -1) {
                    holder.textView.setText(getStringResourceForIcon(icon));
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(getImageResourceForIcon(icon), 0, 0, 0);
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (adapterIcons.get(position) == -1) {
                    return VIEW_TYPE_SEPARATOR;
                } else {
                    return VIEW_TYPE_ICON;
                }
            }
        };
        iconOrderRecyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAbsoluteAdapterPosition();
                int to = target.getAbsoluteAdapterPosition();

                Integer fromVal = adapterIcons.get(from);
                adapterIcons.set(from, adapterIcons.get(to));
                adapterIcons.set(to, fromVal);

                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int pos = viewHolder.getAbsoluteAdapterPosition();
                if (adapterIcons.get(pos) == -1) {
                    return 0;
                }
                return super.getDragDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);

                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setAlpha(.5f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                viewHolder.itemView.setAlpha(1f);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // no swipe here
            }
        };
        itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(iconOrderRecyclerView);

        adapterIcons = SettingsActivity.getComposeMessageIconPreferredOrder();
        if (adapterIcons == null) {
            adapterIcons = new ArrayList<>(Arrays.asList(DEFAULT_ICON_ORDER));
            adapterIcons.add(-1);
        } else if (adapterIcons.size() < DEFAULT_ICON_ORDER.length) {
            adapterIcons.add(-1);
            for (int icon : DEFAULT_ICON_ORDER) {
                if (!adapterIcons.contains(icon)) {
                    adapterIcons.add(icon);
                }
            }
        } else {
            adapterIcons.add(-1);
        }
        adapter.submitList(adapterIcons);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_choose_icon_order)
                .setView(iconOrderRecyclerView)
                .setPositiveButton(R.string.button_label_save, (dialog, which) -> {
                    int end = adapterIcons.indexOf(-1);
                    SettingsActivity.setComposeMessageIconPreferredOrder(end == -1 ? adapterIcons : adapterIcons.subList(0, end));
                    updateIconsToShow(previousWidth);
                })
                .setNegativeButton(R.string.button_label_cancel, null);

        builder.create().show();
    }

    private interface OnHandlePressedListener {
        void onHandlePressed(IconOrderViewHolder iconOrderViewHolder);
    }

    private static class IconOrderViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final View handle;

        @SuppressLint("ClickableViewAccessibility")
        public IconOrderViewHolder(@NonNull View itemView, @Nullable OnHandlePressedListener onHandlePressedListener) {
            super(itemView);
            textView = itemView.findViewById(R.id.icon_text_view);
            handle = itemView.findViewById(R.id.handle);
            if (handle != null && onHandlePressedListener != null) {
                handle.setOnTouchListener((View v, MotionEvent event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        onHandlePressedListener.onHandlePressed(this);
                    }
                    return true;
                });
            }
        }
    }

    // endregion

    // region Compose area layout

    private int iconSize;
    private int fourDp;

    private int previousWidth = 0;
    private final List<Integer> iconsShown = new ArrayList<>();
    private final List<Integer> iconsOverflow = new ArrayList<>(Arrays.asList(DEFAULT_ICON_ORDER));
    private boolean neverOverflow = false;


    private boolean recording = false;
    private boolean hasAttachments = false;
    private boolean hasText = false;
    private boolean showAttachIcons = true;
    private boolean showEphemeralMarker = false;
    private int previousSelectionStart = -1;
    private int previousSelectionEnd = -1;


    // return true if an updateComposeAreaLayout was triggered
    boolean setShowAttachIcons(boolean show, boolean preserveOldSelection) {
        if (show == showAttachIcons) {
            return false;
        }
        showAttachIcons = show;
        updateComposeAreaLayout();
        if (show) {
            previousSelectionStart = newMessageEditText.getSelectionStart();
            previousSelectionEnd = newMessageEditText.getSelectionEnd();
        } else if (preserveOldSelection) {
            int messageLength = newMessageEditText.getText() == null ? 0 : newMessageEditText.getText().length();
            if (previousSelectionStart >= 0 && previousSelectionEnd >= 0 && previousSelectionStart <= messageLength && previousSelectionEnd <= messageLength) {
                newMessageEditText.setSelection(previousSelectionStart, previousSelectionEnd);
            } else {
                newMessageEditText.setSelection(messageLength);
            }
        }
        return true;
    }

    void updateIconsToShow(int widthPixels) {
        previousWidth = widthPixels;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = (float) widthPixels / metrics.density;
        iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, metrics);
        fourDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);

        List<Integer> icons = SettingsActivity.getComposeMessageIconPreferredOrder();
        if (icons == null) {
            icons = new ArrayList<>(Arrays.asList(DEFAULT_ICON_ORDER));
        }

        if (!hasCamera) {
            icons.remove(ICON_TAKE_PICTURE);
            icons.remove(ICON_TAKE_VIDEO);
        }

        // Compose area layout
        // 4 + 36 + (36 x icon_count) + 4 || 6 + 24 + 2 + [ text ] + 32 || 4 = 112 + [ text ] + (36 x icon_count)
        // min text width --> 160dp
        // text width > 400dp with all icons --> don't overflow
        iconsShown.clear();
        iconsOverflow.clear();
        if (widthDp > (512 + 36 * icons.size())) {
            neverOverflow = true;
            iconsShown.addAll(icons);
        } else {
            neverOverflow = false;
            int iconsToShow = Math.min((int) (widthDp - 272) / 36, icons.size());
            iconsShown.addAll(icons.subList(0, iconsToShow));
            iconsOverflow.addAll(icons.subList(iconsToShow, icons.size()));
        }

        if (icons.size() < DEFAULT_ICON_ORDER.length) {
            for (int icon : DEFAULT_ICON_ORDER) {
                if (!icons.contains(icon)) {
                    iconsOverflow.add(icon);
                }
            }
        }

        attachIconsGroup.removeAllViews();
        for (Integer icon : iconsShown) {
            ImageView imageView = new ImageView(new ContextThemeWrapper(activity, R.style.SubtleBlueRipple));
            imageView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            imageView.setPadding(fourDp, fourDp, fourDp, fourDp);
            imageView.setBackgroundResource( R.drawable.background_circular_ripple);
            imageView.setImageResource(getImageResourceForIcon(icon));
            imageView.setId(getViewIdForIcon(icon));
            imageView.setOnClickListener(this);
            attachIconsGroup.addView(imageView, 0);
        }
        currentLayout = 0; // do this to force relayout
        updateComposeAreaLayout();
    }

    ValueAnimator widthAnimator;
    int currentLayout = 0;

    void updateComposeAreaLayout() {
        if (recording || (!hasAttachments && !hasText)) {
            sendButton.setVisibility(View.GONE);
            directAttachVoiceMessageImageView.setVisibility(View.VISIBLE);
        } else {
            sendButton.setVisibility(View.VISIBLE);
            directAttachVoiceMessageImageView.setVisibility(View.GONE);
            sendButton.setEnabled(hasAttachments || composeMessageViewModel.getTrimmedNewMessageText() != null);
        }

        ConstraintLayout.LayoutParams attachIconsGroupParams = (ConstraintLayout.LayoutParams) attachIconsGroup.getLayoutParams();
        if (showAttachIcons) {
            if (currentLayout != 1) {
                currentLayout = 1;

                attachStuffPlus.setImageResource(R.drawable.ic_attach_add);
                newMessageEditText.setMaxLines(1);
                newMessageEditText.setVerticalScrollBarEnabled(false);
                newMessageEditText.setMovementMethod(null);

                if (widthAnimator != null) {
                    widthAnimator.cancel();
                }
                if (animateLayoutChanges) {
                    widthAnimator = ValueAnimator.ofInt(attachIconsGroupParams.width, attachIconsGroup.getChildCount() * iconSize);
                    widthAnimator.setDuration(200);
                    widthAnimator.addUpdateListener(animation -> {
                        attachIconsGroupParams.width = (int) animation.getAnimatedValue();
                        attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                    });
                    widthAnimator.start();
                } else {
                    attachIconsGroupParams.width = attachIconsGroup.getChildCount() * iconSize;
                    attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                }
            }
        } else {
            if (currentLayout != 2 && currentLayout != 3) {
                newMessageEditText.setMaxLines(6);
                newMessageEditText.setVerticalScrollBarEnabled(true);
                newMessageEditText.setMovementMethod(ArrowKeyMovementMethod.getInstance());
            }
            if (neverOverflow) {
                if (currentLayout != 2) {
                    currentLayout = 2;
                    attachStuffPlus.setImageResource(R.drawable.ic_attach_add);

                    if (widthAnimator != null) {
                        widthAnimator.cancel();
                    }
                    if (animateLayoutChanges) {
                        widthAnimator = ValueAnimator.ofInt(attachIconsGroupParams.width, attachIconsGroup.getChildCount() * iconSize);
                        widthAnimator.setDuration(200);
                        widthAnimator.addUpdateListener(animation -> {
                            attachIconsGroupParams.width = (int) animation.getAnimatedValue();
                            attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                        });
                        widthAnimator.start();
                    } else {
                        attachIconsGroupParams.width = attachIconsGroup.getChildCount() * iconSize;
                        attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                    }
                }
            } else {
                if (currentLayout != 3) {
                    currentLayout = 3;
                    attachStuffPlus.setImageResource(R.drawable.ic_attach_chevron);

                    if (widthAnimator != null) {
                        widthAnimator.cancel();
                    }
                    if (animateLayoutChanges) {
                        widthAnimator = ValueAnimator.ofInt(attachIconsGroupParams.width, -3);
                        widthAnimator.setDuration(200);
                        widthAnimator.addUpdateListener(animation -> {
                            attachIconsGroupParams.width = (int) animation.getAnimatedValue();
                            attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                        });
                        widthAnimator.start();
                    } else {
                        attachIconsGroupParams.width = -3;
                        attachIconsGroup.setLayoutParams(attachIconsGroupParams);
                    }
                }
            }
        }
        hideOrShowEphemeralMarker(showEphemeralMarker);
    }

    void hideOrShowEphemeralMarker(boolean showMarker) {
        showEphemeralMarker = showMarker;
        ImageView attachTimeView = attachIconsGroup.findViewById(R.id.attach_timer);
        if (attachTimeView != null) {
            if (showMarker) {
                attachTimeView.setImageResource(R.drawable.ic_attach_timer_modified);
                attachStuffPlusGoldenDot.setVisibility((showAttachIcons || neverOverflow) ? View.GONE : View.VISIBLE);
            } else {
                attachTimeView.setImageResource(R.drawable.ic_attach_timer);
                attachStuffPlusGoldenDot.setVisibility(View.GONE);
            }
        } else {
            attachStuffPlusGoldenDot.setVisibility(showMarker ? View.VISIBLE : View.GONE);
        }
    }

    void showOverflowedPopupMenu() {
        @SuppressLint("InflateParams")
        View popupView = LayoutInflater.from(activity).inflate(R.layout.popup_discussion_attach_stuff, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_popup_discussion_owned_identity));

        View.OnClickListener onClickListener = (View v) -> {
            popupWindow.dismiss();
            this.onClick(v);
        };

        LinearLayout popupAttachList = popupView.findViewById(R.id.popup_attach_list);
        TextView attachConfigure = popupAttachList.findViewById(R.id.attach_configure);
        attachConfigure.setOnClickListener(onClickListener);

        if (iconsOverflow.size() == 0) {
            popupAttachList.findViewById(R.id.separator).setVisibility(View.GONE);
        } else {
            int greyColor = ContextCompat.getColor(activity, R.color.grey);
            TypedValue backgroundDrawable = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, backgroundDrawable, true);

            for (int icon : iconsOverflow) {
                TextView textView = new AppCompatTextView(new ContextThemeWrapper(activity, R.style.SubtleBlueRipple));
                textView.setId(getViewIdForIcon(icon));
                textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(getImageResourceForIcon(icon), 0, 0, 0);
                textView.setText(getStringResourceForIcon(icon));
                textView.setMaxLines(1);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTextColor(greyColor);
                textView.setGravity(Gravity.CENTER_VERTICAL);
                textView.setPadding(fourDp, fourDp, 2*fourDp, fourDp);
                textView.setCompoundDrawablePadding(fourDp);
                textView.setBackgroundResource(backgroundDrawable.resourceId);
                textView.setOnClickListener(onClickListener);

                popupAttachList.addView(textView);
            }
        }

        int popupHeight = (iconsOverflow.size()+2) * iconSize + fourDp;

        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.showAsDropDown(attachStuffPlus, fourDp/2, -popupHeight, Gravity.TOP | Gravity.START);
    }

    private int getImageResourceForIcon(int icon) {
        switch (icon) {
            case ICON_ATTACH_FILE:
                return R.drawable.ic_attach_file;
            case ICON_ATTACH_PICTURE:
                return R.drawable.ic_attach_image;
            case ICON_EPHEMERAL_SETTINGS:
                if (showEphemeralMarker) {
                    return R.drawable.ic_attach_timer_modified;
                } else {
                    return R.drawable.ic_attach_timer;
                }
            case ICON_TAKE_PICTURE:
                return R.drawable.ic_attach_camera;
            case ICON_TAKE_VIDEO:
                return R.drawable.ic_attach_video;
            default:
                return 0;
        }
    }

    private int getViewIdForIcon(int icon) {
        switch (icon) {
            case ICON_ATTACH_FILE:
                return R.id.attach_file;
            case ICON_ATTACH_PICTURE:
                return R.id.attach_image;
            case ICON_EPHEMERAL_SETTINGS:
                return R.id.attach_timer;
            case ICON_TAKE_PICTURE:
                return R.id.attach_camera;
            case ICON_TAKE_VIDEO:
                return R.id.attach_video;
            default:
                return 0;
        }
    }

    private int getStringResourceForIcon(int icon) {
        switch (icon) {
            case ICON_ATTACH_FILE:
                return R.string.label_attach_file;
            case ICON_ATTACH_PICTURE:
                return R.string.label_attach_image;
            case ICON_EPHEMERAL_SETTINGS:
                    return R.string.label_attach_timer;
            case ICON_TAKE_PICTURE:
                return R.string.label_attach_camera;
            case ICON_TAKE_VIDEO:
                return R.string.label_attach_video;
            default:
                return 0;
        }
    }

    // endregion


    // region ComposeMessageHeightListener
    interface ComposeMessageHeightListener {
        void onNewComposeMessageHeight(int heightPixels);
    }

    private final List<ComposeMessageHeightListener> composeMessageHeightListeners = new ArrayList<>();

    private final View.OnLayoutChangeListener composeMessageSizeChangeListener = (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
        if (previousWidth != right - left) {
            new Handler(Looper.getMainLooper()).post(() -> updateIconsToShow(right - left));
        }

        if (top - bottom != oldTop - oldBottom) {
            List<ComposeMessageHeightListener> listeners;
            synchronized (composeMessageHeightListeners) {
                listeners = new ArrayList<>(composeMessageHeightListeners);
            }
            for (ComposeMessageHeightListener listener : listeners) {
                new Handler(Looper.getMainLooper()).post(() -> listener.onNewComposeMessageHeight(bottom - top));
            }
        }
    };

    // endregion
}
