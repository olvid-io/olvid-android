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

package io.olvid.messenger.webrtc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;

public class WebrtcCallActivity extends AppCompatActivity implements View.OnClickListener, FragmentOnAttachListener {
    private static final int PERMISSIONS_REQUEST_CODE = 632;
    private static final int PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE = 633;

    public static final String CALL_BACK_ACTION = "call_back";
    public static final String CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String CALL_BACK_EXTRA_DISCUSSION_ID = "discussion_id";

    public static final String ANSWER_CALL_ACTION = "answer_call";
    public static final String ANSWER_CALL_EXTRA_CALL_IDENTIFIER = "call_identifier";

    WebrtcServiceConnection webrtcServiceConnection;
    WebrtcCallService webrtcCallService = null;
    MicrophoneMutedObserver microphoneMutedObserver;
    SelectedAudioOutputObserver selectedAudioOutputObserver;
    WebrtcCallService.AudioOutput selectedAudioOutput = null;
    List<WebrtcCallService.AudioOutput> availableAudioOutputs = null;
    AudioOutputsObserver audioOutputsObserver;
    CallStateObserver callStateObserver;
    CallDurationObserver callDurationObserver;
    ShowDiscussionButtonObserver showDiscussionButtonObserver;
    MotionLayoutObserver motionLayoutObserver;
    MediaSession mediaSession;

    View singleContactViewGroup;
    InitialView singleContactInitialView;
    TextView singleContactNameTextView;
    TextView singlePeerStatusTextView;
    EmptyRecyclerView multipleContactsRecyclerView;
    ViewGroup rootLayout;

    CallParticipantsAdapter callParticipantsAdapter;

    TextView callStatusTextView;
    View endCallButton;
    View addParticipantButton;
    ImageView singlePeerIsMutedImageView;
    ImageView toggleMicrophoneButton;
    View toggleAudioOutputDropdown;
    ImageView toggleAudioOutputButton;
    View openDiscussionButton;

    boolean foreground = false;
    boolean outputDialogOpen = false;
    boolean addParticipantDialogOpen = false;
    boolean loudSpeakerOn = false;
    boolean callEnded = false;

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (CALL_BACK_ACTION.equals(intent.getAction())) {
            final byte[] bytesOwnedIdentity = intent.getByteArrayExtra(CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY);
            final byte[] bytesContactIdentity = intent.getByteArrayExtra(CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY);
            final long discussionId = intent.getLongExtra(CALL_BACK_EXTRA_DISCUSSION_ID, -1);

            if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                Intent serviceIntent = new Intent(this, WebrtcCallService.class);
                serviceIntent.setAction(WebrtcCallService.ACTION_START_CALL);
                Bundle bytesContactIdentitiesBundle = new Bundle();
                bytesContactIdentitiesBundle.putByteArray(WebrtcCallService.SINGLE_CONTACT_IDENTITY_BUNDLE_KEY, bytesContactIdentity);
                serviceIntent.putExtra(WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA, bytesContactIdentitiesBundle);
                serviceIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
                startService(serviceIntent);
            } else {
                closeActivity();
            }

            if (discussionId != -1) {
                AndroidNotificationManager.clearMissedCallNotification(discussionId);
            }
        } else if (ANSWER_CALL_ACTION.equals(intent.getAction())) {
            final String callIdentifier = intent.getStringExtra(ANSWER_CALL_EXTRA_CALL_IDENTIFIER);

            if (callIdentifier != null) {
                Intent serviceIntent = new Intent(this, WebrtcCallService.class);
                serviceIntent.setAction(WebrtcCallService.ACTION_ANSWER_CALL);
                serviceIntent.putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, callIdentifier);
                startService(serviceIntent);
            } else {
                closeActivity();
            }
        }

        webrtcServiceConnection = new WebrtcServiceConnection();
        Intent serviceBindIntent = new Intent(this, WebrtcCallService.class);
        bindService(serviceBindIntent, webrtcServiceConnection, 0);

        setContentView(R.layout.activity_webrtc_call);

        addParticipantButton = findViewById(R.id.add_participant_button);
        addParticipantButton.setOnClickListener(this);

        endCallButton = findViewById(R.id.end_call_button);
        endCallButton.setOnClickListener(this);

        toggleMicrophoneButton = findViewById(R.id.toggle_mute_microphone_button);
        toggleMicrophoneButton.setOnClickListener(this);

        toggleAudioOutputDropdown = findViewById(R.id.toggle_speakerphone_dropdown);
        toggleAudioOutputButton = findViewById(R.id.toggle_speakerphone_button);
        toggleAudioOutputButton.setOnClickListener(this);

        openDiscussionButton = findViewById(R.id.open_discussion_button);
        if (openDiscussionButton != null) {
            openDiscussionButton.setOnClickListener(this);
        }

        callStatusTextView = findViewById(R.id.call_status_text_view);

        singleContactViewGroup = findViewById(R.id.single_contact_group);
        singlePeerIsMutedImageView = findViewById(R.id.peer_is_muted_image_view);
        singleContactInitialView = findViewById(R.id.contact_initial_view);
        singleContactInitialView.setOnClickListener(this);
        singleContactNameTextView = findViewById(R.id.contact_name_text_view);
        singlePeerStatusTextView = findViewById(R.id.peer_status_text_view);

        multipleContactsRecyclerView = findViewById(R.id.multiple_contacts_recycler_view);
        multipleContactsRecyclerView.setHideIfEmpty(true);
        multipleContactsRecyclerView.setEmptyThreshold(1);
        multipleContactsRecyclerView.setEmptyView(singleContactViewGroup);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        multipleContactsRecyclerView.setLayoutManager(layoutManager);

        callParticipantsAdapter = new CallParticipantsAdapter(this);
        multipleContactsRecyclerView.setAdapter(callParticipantsAdapter);

        microphoneMutedObserver = new MicrophoneMutedObserver();
        selectedAudioOutputObserver = new SelectedAudioOutputObserver();
        callStateObserver = new CallStateObserver();
        callDurationObserver = new CallDurationObserver();
        audioOutputsObserver = new AudioOutputsObserver();
        showDiscussionButtonObserver = new ShowDiscussionButtonObserver();
        motionLayoutObserver = new MotionLayoutObserver();

        rootLayout = findViewById(R.id.root_layout);
        if (rootLayout instanceof MotionLayout) {
            rootLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> motionLayoutObserver.sizeChanged());
        }

        mediaSession = new MediaSession(this, "Olvid ongoing call");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
                KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null
                        && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                        && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                    if (webrtcCallService != null) {
                        webrtcCallService.toggleMuteMicrophone();
                    }
                    return true;
                }
                return false;
            }
        }, new Handler(Looper.getMainLooper()));
        mediaSession.setActive(true);

        getSupportFragmentManager().addFragmentOnAttachListener(this);

        if (!SettingsActivity.wasFirstCallAudioPermissionRequested() && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SettingsActivity.setFirstCallAudioPermissionRequested(true);
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_webrtc_audio_permission)
                    .setMessage(R.string.dialog_message_webrtc_audio_permission)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .setOnDismissListener((DialogInterface dialogInterface) -> requestPermissionsIfNeeded(true));
            builder.create().show();
        } else {
            requestPermissionsIfNeeded(false);
        }
    }


    private void requestPermissionsIfNeeded(boolean rationaleWasShown) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE},
                    rationaleWasShown ? PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE : PERMISSIONS_REQUEST_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (!SettingsActivity.wasFirstCallBluetoothPermissionRequested() || shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                SettingsActivity.setFirstCallBluetoothPermissionRequested(true);
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_android_12_bluetooth_access)
                        .setMessage(R.string.dialog_message_android_12_bluetooth_access)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialogInterface, int i) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSIONS_REQUEST_CODE));
                builder.create().show();
            }
        }
    }

    private void refreshProximityLockStatus() {
        if (webrtcCallService != null) {
            if (foreground && !outputDialogOpen && !addParticipantDialogOpen && !loudSpeakerOn) {
                webrtcCallService.acquireWakeLock(WebrtcCallService.WakeLock.PROXIMITY);
            } else {
                webrtcCallService.releaseWakeLocks(WebrtcCallService.WakeLock.PROXIMITY);
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(webrtcServiceConnection);
        setWebrtcCallService(null);
        if (mediaSession != null) {
            mediaSession.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        refreshProximityLockStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        foreground = false;
        refreshProximityLockStatus();
    }



    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment instanceof CallParticipantAdditionDialogFragment) {
            addParticipantDialogOpen = true;
            refreshProximityLockStatus();
            ((CallParticipantAdditionDialogFragment) fragment).setDialogClosedListener(() -> {
                addParticipantDialogOpen = false;
                refreshProximityLockStatus();
            });
        }

    }



    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.end_call_button) {
            if (webrtcCallService != null) {
                webrtcCallService.hangUpCall();
            } else {
                finishAndRemoveTask();
            }
        } else if (id == R.id.add_participant_button) {
            if (webrtcCallService != null) {
                CallParticipantAdditionDialogFragment callParticipantAdditionDialogFragment = CallParticipantAdditionDialogFragment.newInstance(webrtcCallService.bytesOwnedIdentity);
                callParticipantAdditionDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
        } else if (id == R.id.toggle_mute_microphone_button) {
            if (webrtcCallService != null) {
                webrtcCallService.toggleMuteMicrophone();
            }
        } else if (id == R.id.toggle_speakerphone_button) {
            if (webrtcCallService != null && availableAudioOutputs != null) {
                if (availableAudioOutputs.size() == 1) {
                    webrtcCallService.selectAudioOutput(availableAudioOutputs.get(0));
                } else if (availableAudioOutputs.size() == 2
                        && availableAudioOutputs.contains(WebrtcCallService.AudioOutput.PHONE)
                        && availableAudioOutputs.contains(WebrtcCallService.AudioOutput.LOUDSPEAKER)) {
                    if (availableAudioOutputs.get(0) == selectedAudioOutput) {
                        webrtcCallService.selectAudioOutput(availableAudioOutputs.get(1));
                    } else {
                        webrtcCallService.selectAudioOutput(availableAudioOutputs.get(0));
                    }
                } else {
                    AlertDialog alertDialog = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_select_audio_output)
                            .setAdapter(new ArrayAdapter<WebrtcCallService.AudioOutput>(this, R.layout.item_view_webrtc_audio_output, availableAudioOutputs) {
                                final LayoutInflater layoutInflater = getLayoutInflater();

                                @NonNull
                                @Override
                                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                    return viewFromResource(position, convertView, parent);
                                }

                                private View viewFromResource(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                    final View view;
                                    if (convertView == null) {
                                        view = layoutInflater.inflate(R.layout.item_view_webrtc_audio_output, parent, false);
                                    } else {
                                        view = convertView;
                                    }
                                    TextView outputName = view.findViewById(R.id.audio_output_name);
                                    ImageView outputImage = view.findViewById(R.id.audio_output_icon);
                                    WebrtcCallService.AudioOutput audioOutput = availableAudioOutputs.get(position);
                                    switch (audioOutput) {
                                        case PHONE:
                                            outputImage.setImageResource(R.drawable.ic_phone_grey);
                                            outputName.setText(R.string.text_audio_output_phone);
                                            break;
                                        case HEADSET:
                                            outputImage.setImageResource(R.drawable.ic_headset_grey);
                                            outputName.setText(R.string.text_audio_output_headset);
                                            break;
                                        case LOUDSPEAKER:
                                            outputImage.setImageResource(R.drawable.ic_speaker_grey);
                                            outputName.setText(R.string.text_audio_output_loudspeaker);
                                            break;
                                        case BLUETOOTH:
                                            outputImage.setImageResource(R.drawable.ic_speaker_bluetooth_grey);
                                            outputName.setText(R.string.text_audio_output_bluetooth);
                                            break;
                                    }
                                    return view;
                                }
                            }, (dialog, which) -> {
                                WebrtcCallService.AudioOutput audioOutput = availableAudioOutputs.get(which);
                                if (webrtcCallService != null) {
                                    webrtcCallService.selectAudioOutput(audioOutput);
                                }
                            })
                            .setOnDismissListener((DialogInterface dialog) -> {
                                outputDialogOpen = false;
                                refreshProximityLockStatus();
                            })
                            .create();
                    outputDialogOpen = true;
                    refreshProximityLockStatus();
                    alertDialog.show();
                }
            }
        } else if (id == R.id.contact_initial_view) {
            if (webrtcCallService != null) {
                List<WebrtcCallService.CallParticipantPojo> callParticipants = webrtcCallService.getCallParticipantsLiveData().getValue();
                if (webrtcCallService.bytesOwnedIdentity != null && callParticipants != null) {
                    if (callParticipants.size() == 1) {
                        Contact contact = callParticipants.get(0).contact;
                        if (contact != null && contact.oneToOne) {
                            Intent discussionIntent = new Intent(App.getContext(), MainActivity.class);
                            discussionIntent.setAction(MainActivity.FORWARD_ACTION);
                            discussionIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                            discussionIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, callParticipants.get(0).bytesContactIdentity);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                                if (km != null && km.isDeviceLocked()) {
                                    km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                                        @Override
                                        public void onDismissSucceeded() {
                                            startActivity(discussionIntent);
                                        }
                                    });
                                    return;
                                }
                            }
                            startActivity(discussionIntent);
                        }
                    }
                }
            }
        } else if (id == R.id.open_discussion_button) {
            if (webrtcCallService != null) {
                List<WebrtcCallService.CallParticipantPojo> callParticipants = webrtcCallService.getCallParticipantsLiveData().getValue();
                if (webrtcCallService.bytesOwnedIdentity != null && callParticipants != null) {
                    @NonNull final Intent discussionIntent;
                    switch (webrtcCallService.discussionType) {
                        case Discussion.TYPE_GROUP: {
                            if (webrtcCallService.bytesGroupOwnerAndUidOrIdentifier == null) {
                                return;
                            }

                            discussionIntent = new Intent(this, MainActivity.class);
                            discussionIntent.setAction(MainActivity.FORWARD_ACTION);
                            discussionIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                            discussionIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, webrtcCallService.bytesGroupOwnerAndUidOrIdentifier);
                            break;
                        }
                        case Discussion.TYPE_GROUP_V2: {
                            if (webrtcCallService.bytesGroupOwnerAndUidOrIdentifier == null) {
                                return;
                            }

                            discussionIntent = new Intent(this, MainActivity.class);
                            discussionIntent.setAction(MainActivity.FORWARD_ACTION);
                            discussionIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                            discussionIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_GROUP_IDENTIFIER_INTENT_EXTRA, webrtcCallService.bytesGroupOwnerAndUidOrIdentifier);
                            break;
                        }
                        default: {
                            if (callParticipants.size() != 1) {
                                return;
                            }
                            Contact contact = callParticipants.get(0).contact;
                            if (contact == null || !contact.oneToOne) {
                                return;
                            }

                            discussionIntent = new Intent(this, MainActivity.class);
                            discussionIntent.setAction(MainActivity.FORWARD_ACTION);
                            discussionIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                            discussionIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, webrtcCallService.bytesOwnedIdentity);
                            discussionIntent.putExtra(DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, callParticipants.get(0).bytesContactIdentity);
                            break;
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null && km.isDeviceLocked()) {
                            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                                @Override
                                public void onDismissSucceeded() {
                                    startActivity(discussionIntent);
                                }
                            });
                            return;
                        }
                    }
                    startActivity(discussionIntent);
                }
            }
        }
    }


    private class CallStateObserver implements Observer<WebrtcCallService.State> {
        @Override
        public void onChanged(WebrtcCallService.State state) {
            if (webrtcCallService != null
                    && webrtcCallService.isCaller()
                    && addParticipantButton.getVisibility() != View.VISIBLE) {
                addParticipantButton.setVisibility(View.VISIBLE);
            }

            switch (state) {
                case WAITING_FOR_AUDIO_PERMISSION:
                    callStatusTextView.setText(R.string.webrtc_status_waiting_for_permission);
                    break;
                case GETTING_TURN_CREDENTIALS:
                    callStatusTextView.setText(R.string.webrtc_status_verifying_credentials);
                    break;
                case INITIAL:
                case INITIALIZING_CALL:
                    callStatusTextView.setText(R.string.webrtc_status_initializing_call);
                    break;
                case RINGING:
                    callStatusTextView.setText(R.string.webrtc_status_ringing);
                    break;
                case BUSY:
                    callStatusTextView.setText(R.string.webrtc_status_contact_busy);
                    break;
                case CALL_IN_PROGRESS:
                    break;
                case CALL_ENDED:
                    callEnded = true;
                    callStatusTextView.setText(R.string.webrtc_status_ending_call);
                    break;
                case FAILED:
                    callEnded = true;
                    if (webrtcCallService == null) {
                        break;
                    }
                    switch (webrtcCallService.getFailReason()) {
                        case NONE:
                        case CONTACT_NOT_FOUND:
                        case INTERNAL_ERROR:
                        case ICE_SERVER_CREDENTIALS_CREATION_ERROR:
                        case COULD_NOT_SEND:
                            callStatusTextView.setText(R.string.webrtc_failed_internal);
                            break;
                        case SERVER_UNREACHABLE:
                        case PEER_CONNECTION_CREATION_ERROR:
                        case SERVER_AUTHENTICATION_ERROR:
                            callStatusTextView.setText(R.string.webrtc_failed_network_error);
                            break;
                        case PERMISSION_DENIED:
                        case CALL_INITIATION_NOT_SUPPORTED:
                            callStatusTextView.setText(R.string.webrtc_failed_no_call_permission);
                            break;
                        case ICE_CONNECTION_ERROR:
                            callStatusTextView.setText(R.string.webrtc_failed_connection_to_contact_lost);
                            break;
                        case KICKED:
                            callStatusTextView.setText(R.string.webrtc_failed_kicked);
                            break;
                    }
                    break;
            }
        }
    }

    private class MicrophoneMutedObserver implements Observer<Boolean> {
        @Override
        public void onChanged(Boolean microphoneMuted) {
            if (microphoneMuted == null || !microphoneMuted) {
                toggleMicrophoneButton.setImageResource(R.drawable.button_micro);
            } else {
                toggleMicrophoneButton.setImageResource(R.drawable.button_no_micro_red_circle);
            }
        }
    }

    private class SelectedAudioOutputObserver implements Observer<WebrtcCallService.AudioOutput> {
        @Override
        public void onChanged(WebrtcCallService.AudioOutput audioOutput) {
            if ((audioOutput == WebrtcCallService.AudioOutput.LOUDSPEAKER) != (selectedAudioOutput == WebrtcCallService.AudioOutput.LOUDSPEAKER)) {
                loudSpeakerOn = audioOutput == WebrtcCallService.AudioOutput.LOUDSPEAKER;
                refreshProximityLockStatus();
            }

            selectedAudioOutput = audioOutput;
            refresh();
        }

        void refresh() {
            if (availableAudioOutputs != null &&
                    (availableAudioOutputs.size() == 1 || (availableAudioOutputs.size() == 2
                    && availableAudioOutputs.contains(WebrtcCallService.AudioOutput.PHONE)
                    && availableAudioOutputs.contains(WebrtcCallService.AudioOutput.LOUDSPEAKER)))) {
                toggleAudioOutputDropdown.setVisibility(View.GONE);
                switch (selectedAudioOutput) {
                    case PHONE:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_speaker);
                        break;
                    case HEADSET:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_headset);
                        break;
                    case LOUDSPEAKER:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_speaker_active);
                        break;
                    case BLUETOOTH:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_speaker_bluetooth);
                        break;
                }
            } else {
                toggleAudioOutputDropdown.setVisibility(View.VISIBLE);
                switch (selectedAudioOutput) {
                    case PHONE:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_phone);
                        break;
                    case HEADSET:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_headset);
                        break;
                    case LOUDSPEAKER:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_speaker_active);
                        break;
                    case BLUETOOTH:
                        toggleAudioOutputButton.setImageResource(R.drawable.button_speaker_bluetooth);
                        break;
                }
            }
        }
    }

    private class AudioOutputsObserver implements Observer<List<WebrtcCallService.AudioOutput>> {
        @Override
        public void onChanged(List<WebrtcCallService.AudioOutput> audioOutputs) {
            availableAudioOutputs = audioOutputs;
            selectedAudioOutputObserver.refresh();
        }
    }

    private class CallDurationObserver implements Observer<Integer> {
        @Override
        public void onChanged(Integer duration) {
            if (callStatusTextView != null && duration != null && !callEnded) {
                callStatusTextView.setText(String.format(Locale.ENGLISH, "%02d:%02d", duration / 60, duration % 60));
            }
        }
    }

    private class MotionLayoutObserver implements Observer<List<WebrtcCallService.CallParticipantPojo>> {
        boolean compact = false;
        int threshold = -1;
        int callParticipantCount = 0;

        @Override
        public void onChanged(List<WebrtcCallService.CallParticipantPojo> callParticipants) {
            if (callParticipants != null) {
                callParticipantCount = callParticipants.size();
            } else {
                callParticipantCount = 0;
            }
            transitionLayoutIfNeeded();
        }

        public void sizeChanged() {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float windowHeightDp = rootLayout.getHeight() / metrics.density;
            threshold = (int) (windowHeightDp - 288) / 80;
            if (threshold < 0) {
                threshold = 0;
            }
            transitionLayoutIfNeeded();
        }

        public void transitionLayoutIfNeeded() {
            if (rootLayout instanceof MotionLayout) {
                boolean newCompact = threshold != -1 && callParticipantCount > threshold;

                if (newCompact != compact) {
                    compact = newCompact;
                    if (compact) {
                        ((MotionLayout) rootLayout).transitionToEnd();
                    } else {
                        ((MotionLayout) rootLayout).transitionToStart();
                    }
                }
            }
        }
    }

    private class ShowDiscussionButtonObserver implements Observer<List<WebrtcCallService.CallParticipantPojo>> {
        @Override
        public void onChanged(List<WebrtcCallService.CallParticipantPojo> callParticipantPojos) {
            if (openDiscussionButton != null) {
                final boolean show;
                if (callParticipantPojos == null || callParticipantPojos.size() == 0) {
                    show = false;
                } else if (webrtcCallService.bytesGroupOwnerAndUidOrIdentifier != null) {
                    show = true;
                } else if (callParticipantPojos.size() == 1) {
                    Contact contact = callParticipantPojos.get(0).contact;
                    show = contact != null && contact.oneToOne; // in single participant, only show button if participant is indeed a oneToOne contact
                } else {
                    show = false;
                }

                if (show && openDiscussionButton.getVisibility() != View.VISIBLE) {
                    openDiscussionButton.setVisibility(View.VISIBLE);
                } else if (!show && openDiscussionButton.getVisibility() == View.VISIBLE) {
                    openDiscussionButton.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE ||
                requestCode == PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE) {
            boolean audioPermissionGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    audioPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    if (audioPermissionGranted && webrtcCallService != null) {
                        webrtcCallService.audioPermissionGranted();
                    }
                } else if (Manifest.permission.BLUETOOTH_CONNECT.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED && webrtcCallService != null) {
                        webrtcCallService.bluetoothPermissionGranted();
                    }
                } else if (Manifest.permission.READ_PHONE_STATE.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED && webrtcCallService != null) {
                        webrtcCallService.readCallStatePermissionGranted();
                    }
                }
            }

            if (!audioPermissionGranted) {
                if (requestCode == PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE) {
                    // user was prompted for permission, and he denied it --> hangup
                    App.toast(R.string.toast_message_audio_permission_denied, Toast.LENGTH_SHORT);
                    if (webrtcCallService != null) {
                        webrtcCallService.hangUpCall();
                    }
                } else {
                    // user was not prompted --> show dialog explaining that audio permission was permanently denied
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_webrtc_permissions_blocked)
                            .setMessage(R.string.dialog_message_webrtc_permissions_blocked)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setNeutralButton(R.string.button_label_app_settings, null)
                            .setOnDismissListener((DialogInterface dialog) -> requestPermissionsIfNeeded(true));

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(alertDialog -> {
                        Button button = ((AlertDialog) alertDialog).getButton(DialogInterface.BUTTON_NEUTRAL);
                        if (button != null) {
                            button.setOnClickListener(v -> {
                                Intent settingsIntent = new Intent();
                                settingsIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                settingsIntent.setData(uri);
                                startActivity(settingsIntent);
                            });
                        }
                    });
                    dialog.show();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void closeActivity() {
        new Handler(Looper.getMainLooper()).postDelayed(this::finishAndRemoveTask, 3000);
    }

    private void setWebrtcCallService(WebrtcCallService webrtcCallService) {
        if (webrtcCallService != null) {
            this.webrtcCallService = webrtcCallService;
            if (callStateObserver != null && callDurationObserver != null) {
                this.webrtcCallService.getState().observeForever(callStateObserver);
                this.webrtcCallService.getCallDuration().observeForever(callDurationObserver);
            }
            this.webrtcCallService.getCallParticipantsLiveData().observe(this, callParticipantsAdapter);
            this.webrtcCallService.getCallParticipantsLiveData().observe(this, showDiscussionButtonObserver);
            this.webrtcCallService.getCallParticipantsLiveData().observe(this, motionLayoutObserver);
            this.webrtcCallService.getMicrophoneMuted().observe(this, microphoneMutedObserver);
            this.webrtcCallService.getSelectedAudioOutput().observe(this, selectedAudioOutputObserver);
            this.webrtcCallService.getAvailableAudioOutputs().observe(this, audioOutputsObserver);
            refreshProximityLockStatus();
        } else {
            if (this.webrtcCallService != null) {
                // remove observers
                if (callStateObserver != null && callDurationObserver != null) {
                    this.webrtcCallService.getState().removeObserver(callStateObserver);
                    this.webrtcCallService.getCallDuration().removeObserver(callDurationObserver);
                }
                this.webrtcCallService.getCallParticipantsLiveData().removeObservers(this);
                this.webrtcCallService.getMicrophoneMuted().removeObservers(this);
                this.webrtcCallService.getSelectedAudioOutput().removeObservers(this);
                this.webrtcCallService.getAvailableAudioOutputs().removeObservers(this);
            }
            this.webrtcCallService = null;
        }
    }

    private class WebrtcServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof WebrtcCallService.WebrtcCallServiceBinder)) {
                Logger.e("☎ WebrtcCallActivity bound to bad service!!!");
                closeActivity();
                return;
            }
            WebrtcCallService.WebrtcCallServiceBinder binder = (WebrtcCallService.WebrtcCallServiceBinder) service;
            setWebrtcCallService(binder.getService());
        }

        @Override
        public void onNullBinding(ComponentName name) {
            closeActivity();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            setWebrtcCallService(null);
            closeActivity();
        }
    }


    class CallParticipantsAdapter extends RecyclerView.Adapter<CallParticipantsAdapter.CallParticipantViewHolder> implements Observer<List<WebrtcCallService.CallParticipantPojo>> {
        private final LayoutInflater inflater;

        List<WebrtcCallService.CallParticipantPojo> callParticipants;
        byte[] singleBytesContactIdentity = null;

        public static final int PEER_STATE_CHANGED = 1;
        public static final int MUTED_CHANGED = 2;

        public CallParticipantsAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<WebrtcCallService.CallParticipantPojo> callParticipants) {
            if (callParticipants == null || callParticipants.size() == 0) {
                // no one in the call --> should never happen
                singlePeerIsMutedImageView.setVisibility(View.GONE);
                singleContactInitialView.setUnknown();
                singleContactNameTextView.setText(null);
                singlePeerStatusTextView.setText(null);

                this.callParticipants = null;
                this.singleBytesContactIdentity = null;
                notifyDataSetChanged();
            } else if (callParticipants.size() == 1) {
                // single person in the call --> only bind the singleContactViewGroup elements
                WebrtcCallService.CallParticipantPojo callParticipant = callParticipants.get(0);
                boolean fullBind = !Arrays.equals(singleBytesContactIdentity, callParticipant.bytesContactIdentity);

                if (fullBind) {
                    if (callParticipant.contact != null) {
                        singleContactInitialView.setContact(callParticipant.contact);
                    } else {
                        singleContactInitialView.setKeycloakCertified(false);
                        singleContactInitialView.setInactive(false);
                        singleContactInitialView.setNotOneToOne();
                        singleContactInitialView.setNullTrustLevel();
                        singleContactInitialView.setInitial(callParticipant.bytesContactIdentity, StringUtils.getInitial(callParticipant.displayName));
                    }
                    singleContactNameTextView.setText(callParticipant.displayName);
                }

                boolean mutedChanged = fullBind || (this.callParticipants.get(0).peerIsMuted ^ callParticipant.peerIsMuted);
                if (mutedChanged) {
                    if (callParticipant.peerIsMuted) {
                        singlePeerIsMutedImageView.setVisibility(View.VISIBLE);
                    } else {
                        singlePeerIsMutedImageView.setVisibility(View.GONE);
                    }
                }

                boolean stateChanged = fullBind || (this.callParticipants.get(0).peerState != callParticipant.peerState);
                if (stateChanged) {
                    setPeerStateText(callParticipant.peerState, singlePeerStatusTextView, true);
                }

                this.callParticipants = callParticipants;
                this.singleBytesContactIdentity = callParticipant.bytesContactIdentity;
                notifyDataSetChanged();
            } else {
                // sort the call participants in alphabetical order
                Collections.sort(callParticipants);


                this.singleBytesContactIdentity = null;

                if (this.callParticipants != null && this.callParticipants.size() > 1) {
                    DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                        final List<WebrtcCallService.CallParticipantPojo> oldList = CallParticipantsAdapter.this.callParticipants;
                        final List<WebrtcCallService.CallParticipantPojo> newList = callParticipants;

                        final int[] payloadCache = new int[newList.size()];
                        final boolean[] payloadComputed = new boolean[newList.size()];

                        @Override
                        public int getOldListSize() {
                            return oldList.size();
                        }

                        @Override
                        public int getNewListSize() {
                            return newList.size();
                        }

                        @Override
                        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                            return Arrays.equals(oldList.get(oldItemPosition).bytesContactIdentity, newList.get(newItemPosition).bytesContactIdentity);
                        }

                        @Override
                        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                            return (int) getChangePayload(oldItemPosition, newItemPosition) == 0;
                        }

                        @Override
                        @NonNull
                        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                            if (payloadComputed[newItemPosition]){
                                return payloadCache[newItemPosition];
                            }

                            WebrtcCallService.CallParticipantPojo oldItem = oldList.get(oldItemPosition);
                            WebrtcCallService.CallParticipantPojo newItem = newList.get(newItemPosition);
                            int changesMask = 0;

                            if (oldItem.peerState != newItem.peerState) {
                                changesMask |= PEER_STATE_CHANGED;
                            }

                            if (oldItem.peerIsMuted ^ newItem.peerIsMuted) {
                                changesMask |= MUTED_CHANGED;
                            }

                            payloadCache[newItemPosition] = changesMask;
                            payloadComputed[newItemPosition] = true;
                            return changesMask;
                        }
                    });
                    this.callParticipants = callParticipants;
                    result.dispatchUpdatesTo(this);
                } else {
                    this.callParticipants = callParticipants;
                    notifyDataSetChanged();
                }
            }
        }


        private void setPeerStateText(WebrtcCallService.PeerState peerState, TextView peerStatusTextView, boolean singleContact) {
            switch (peerState) {
                case BUSY:
                    if (singleContact) {
                        peerStatusTextView.setText(null);
                    } else {
                        peerStatusTextView.setText(R.string.webrtc_status_contact_busy);
                    }
                    break;
                case CALL_REJECTED:
                    peerStatusTextView.setText(R.string.webrtc_status_call_rejected);
                    break;
                case CONNECTING_TO_PEER:
                    if (singleContact) {
                        peerStatusTextView.setText(null);
                    } else {
                        peerStatusTextView.setText(R.string.webrtc_status_connecting_to_peer);
                    }
                    break;
                case CONNECTED:
                    peerStatusTextView.setText(R.string.webrtc_status_call_in_progress);
                    break;
                case RECONNECTING:
                    peerStatusTextView.setText(R.string.webrtc_status_reconnecting);
                    break;
                case RINGING:
                    if (singleContact) {
                        peerStatusTextView.setText(null);
                    } else {
                        peerStatusTextView.setText(R.string.webrtc_status_ringing);
                    }
                    break;
                case HANGED_UP:
                    peerStatusTextView.setText(R.string.webrtc_status_contact_hanged_up);
                    break;
                case KICKED:
                    peerStatusTextView.setText(R.string.webrtc_status_contact_kicked);
                    break;
                case FAILED:
                    peerStatusTextView.setText(R.string.webrtc_status_contact_failed);
                    break;
                case INITIAL:
                case START_CALL_MESSAGE_SENT:
                    if (singleContact) {
                        peerStatusTextView.setText(null);
                    } else {
                        peerStatusTextView.setText(R.string.webrtc_status_initializing_call);
                    }
                    break;
            }
        }

        @NonNull
        @Override
        public CallParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_call_participant, parent, false);
            return new CallParticipantViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CallParticipantViewHolder holder, int position) {
            Logger.e("SHOULD NEVER BE CALLED");
        }

        @Override
        public void onBindViewHolder(@NonNull CallParticipantViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (callParticipants == null) {
                return;
            }

            int changesMask = 0;
            if (payloads.size() == 0) {
                changesMask = -1;
            } else {
                for (Object payload : payloads) {
                    if (payload instanceof Integer) {
                        changesMask |= (int) payload;
                    }
                }
            }

            WebrtcCallService.CallParticipantPojo callParticipant = callParticipants.get(position);

            if (changesMask == -1) {
                holder.bytesContactIdentity = callParticipant.bytesContactIdentity;
                holder.contact = callParticipant.contact;

                if (callParticipant.contact != null) {
                    holder.initialView.setContact(callParticipant.contact);
                } else {
                    holder.initialView.setKeycloakCertified(false);
                    holder.initialView.setInactive(false);
                    holder.initialView.setNotOneToOne();
                    holder.initialView.setNullTrustLevel();
                    holder.initialView.setInitial(callParticipant.bytesContactIdentity, StringUtils.getInitial(callParticipant.displayName));
                }
                holder.contactNameTextView.setText(callParticipant.displayName);

                if (webrtcCallService != null && webrtcCallService.isCaller()) {
                    holder.kickPeerButton.setVisibility(View.VISIBLE);
                } else {
                    holder.kickPeerButton.setVisibility(View.GONE);
                }
            }

            if ((changesMask & MUTED_CHANGED) != 0) {
                if (callParticipant.peerIsMuted) {
                    holder.peerIsMutedImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.peerIsMutedImageView.setVisibility(View.GONE);
                }
            }

            if ((changesMask & PEER_STATE_CHANGED) != 0) {
                setPeerStateText(callParticipant.peerState, holder.peerStatusTextView, false);
            }
        }

        @Override
        public int getItemCount() {
            if (callParticipants != null) {
                return callParticipants.size();
            }
            return 0;
        }

        class CallParticipantViewHolder extends RecyclerView.ViewHolder {
            private final InitialView initialView;
            private final TextView contactNameTextView;
            private final ImageView peerIsMutedImageView;
            private final TextView peerStatusTextView;
            private final View kickPeerButton;
            private byte[] bytesContactIdentity;
            private Contact contact;

            public CallParticipantViewHolder(@NonNull View itemView) {
                super(itemView);
                initialView = itemView.findViewById(R.id.initial_view);
                contactNameTextView = itemView.findViewById(R.id.contact_name_text_view);
                peerIsMutedImageView = itemView.findViewById(R.id.peer_is_muted_image_view);
                peerStatusTextView = itemView.findViewById(R.id.peer_status_text_view);
                kickPeerButton = itemView.findViewById(R.id.kick_peer_button);
                bytesContactIdentity = null;
                contact = null;

                kickPeerButton.setOnClickListener((View v) -> {
                    if (webrtcCallService != null && bytesContactIdentity != null) {
                        AlertDialog alertDialog = new SecureAlertDialogBuilder(WebrtcCallActivity.this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_webrtc_kick_participant)
                                .setMessage(getString(R.string.dialog_message_webrtc_kick_participant, contactNameTextView.getText()))
                                .setPositiveButton(R.string.button_label_ok, (dialog, which) -> webrtcCallService.callerKickParticipant(bytesContactIdentity))
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .setOnDismissListener((DialogInterface dialog) -> {
                                    if (foreground && webrtcCallService != null) {
                                        webrtcCallService.acquireWakeLock(WebrtcCallService.WakeLock.PROXIMITY);
                                    }
                                })
                                .create();
                        if (webrtcCallService != null) {
                            webrtcCallService.releaseWakeLocks(WebrtcCallService.WakeLock.PROXIMITY);
                        }
                        alertDialog.show();
                    }
                });

                initialView.setOnClickListener((View v) -> {
                    if (contact != null) {
                        Intent discussionIntent = new Intent(App.getContext(), MainActivity.class);
                        discussionIntent.setAction(MainActivity.FORWARD_ACTION);
                        discussionIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                        discussionIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, contact.bytesOwnedIdentity);
                        discussionIntent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, contact.bytesOwnedIdentity);
                        discussionIntent.putExtra(DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, contact.bytesContactIdentity);
                        startActivity(discussionIntent);
                    }
                });
            }
        }
    }
}
