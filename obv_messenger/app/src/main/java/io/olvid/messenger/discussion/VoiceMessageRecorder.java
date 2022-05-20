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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask;

class VoiceMessageRecorder implements View.OnTouchListener {
    @NonNull private final FragmentActivity activity;
    @NonNull private final View recordingOverlay;
    @NonNull private final RequestAudioPermissionDelegate requestAudioPermissionDelegate;
    private final ImageView recordingInitializationSpinner;
    private final View recordingRedButton;
    private final TextView recordingStopTextView;
    private final ComposeMessageViewModel composeMessageViewModel;


    private boolean recordPermission;
    private MediaRecorder mediaRecorder;
    private File audioFile;
    boolean recording;

    private final Timer timer;
    private TimerTask startRecordTask;
    private TimerTask sampleTask;
    private PowerManager.WakeLock wakeLock;

    public VoiceMessageRecorder(@NonNull FragmentActivity activity, @NonNull View recordingOverlay, @NonNull RequestAudioPermissionDelegate requestAudioPermissionDelegate) {
        this.activity = activity;
        this.recordingOverlay = recordingOverlay;
        this.requestAudioPermissionDelegate = requestAudioPermissionDelegate;
        this.recordingInitializationSpinner = recordingOverlay.findViewById(R.id.recording_initialization_spinner);
        this.recordingRedButton = recordingOverlay.findViewById(R.id.recording_red_button);
        this.recordingStopTextView = recordingOverlay.findViewById(R.id.recording_stop_label);
        this.composeMessageViewModel = new ViewModelProvider(activity).get(ComposeMessageViewModel.class);


        recordPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        mediaRecorder = null;
        audioFile = null;
        setRecording(false);
        timer = new Timer("DiscussionActivity-VoiceMessageTimer");
    }

    public void setRecordPermission(boolean recordPermission) {
        this.recordPermission = recordPermission;
    }

    // return false if permissions are wrong
    boolean startRecord() {
        if (!recordPermission) {
            return false;
        }
        startRecordTask = new TimerTask() {
            @SuppressLint("WakelockTimeout")
            @Override
            public void run() {
                if (recording) {
                    return;
                }
                activity.runOnUiThread(() -> {
                    recordingOverlay.setVisibility(View.VISIBLE);
                    recordingRedButton.setVisibility(View.GONE);
                    recordingInitializationSpinner.setVisibility(View.VISIBLE);
                    Drawable spinner = recordingInitializationSpinner.getDrawable();
                    if (spinner instanceof AnimatedVectorDrawable) {
                        ((AnimatedVectorDrawable) spinner).start();
                    }
                });
                setRecording(true);
                if (mediaRecorder != null) {
                    mediaRecorder.release();
                    mediaRecorder = null;
                }

                audioFile = new File(new File(activity.getCacheDir(), App.CAMERA_PICTURE_FOLDER), new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + ".m4a");

                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioChannels(1);
                mediaRecorder.setAudioEncodingBitRate(48_000);
                mediaRecorder.setAudioSamplingRate(44_100);
                mediaRecorder.setOutputFile(audioFile.getPath());
                try {
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                } catch (Exception e) {
                    App.toast(R.string.toast_message_voice_message_recording_failed, Toast.LENGTH_SHORT);
                    mediaRecorder.release();
                    mediaRecorder = null;
                    activity.runOnUiThread(() -> recordingOverlay.setVisibility(View.GONE));
                    return;
                }

                // prevent app from sleeping and screen from turning off
                new Handler(Looper.getMainLooper()).post(() -> {
                            Window window = activity.getWindow();
                            if (window != null) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        });
                PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Olvid:VoiceMessageRecording");
                    wakeLock.acquire();
                }

                sampleTask = new TimerTask() {
                    boolean started = false;

                    @Override
                    public void run() {
                        if (mediaRecorder == null || !recording) {
                            cancel();
                        }
                        try {
                            int amplitude = mediaRecorder.getMaxAmplitude();
                            if (!started && amplitude > 0) {
                                started = true;
                                activity.runOnUiThread(() -> {
                                    recordingInitializationSpinner.setVisibility(View.GONE);
                                    recordingRedButton.setVisibility(View.VISIBLE);
                                });
                            }
                            float log = (float) (Math.log(amplitude) / Math.log(2));
                            log = Math.max(0, log - 8);
                            float scale = 0.5f + log / 4;
                            activity.runOnUiThread(() -> {
                                recordingRedButton.setScaleX(scale);
                                recordingRedButton.setScaleY(scale);
                            });
                        } catch (Exception e) {
                            cancel();
                        }
                    }
                };
                timer.scheduleAtFixedRate(sampleTask, 0, 100);
            }
        };
        timer.schedule(startRecordTask, 250);
        return true;
    }

    void stopRecord(boolean discard) {
        // release wake locks
        new Handler(Looper.getMainLooper()).post(() -> {
            Window window = activity.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        if (startRecordTask != null) {
            startRecordTask.cancel();
            startRecordTask = null;
        }
        if (!recording) {
            activity.runOnUiThread(() -> recordingOverlay.setVisibility(View.GONE));
        } else {
            TimerTask stopTask = new TimerTask() {
                @Override
                public void run() {
                    if (!recording) {
                        return;
                    }
                    setRecording(false);
                    activity.runOnUiThread(() -> recordingOverlay.setVisibility(View.GONE));
                    if (mediaRecorder != null) {
                        try {
                            mediaRecorder.stop();
                            mediaRecorder.release();
                            mediaRecorder = null;
                            if (!discard && audioFile.length() > 0) {
                                Long discussionId = composeMessageViewModel.getDiscussionId();
                                if (discussionId != null) {
                                    new AddFyleToDraftFromUriTask(audioFile, audioFile.getName(), "audio/x-m4a", discussionId).run();
                                }
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                }
            };
            timer.schedule(stopTask, 300);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                startRecord();
                recordingStopTextView.setText(R.string.label_stop_recording_release);
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (recording) {
                    stopRecord(false);
                } else {
                    if (!startRecord()) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_voice_message_explanation)
                                    .setMessage(R.string.dialog_message_voice_message_explanation)
                                    .setPositiveButton(R.string.button_label_ok, null)
                                    .setOnDismissListener((DialogInterface dialog) -> requestAudioPermissionDelegate.requestAudioPermission(true));

                            builder.create().show();
                        } else {
                            requestAudioPermissionDelegate.requestAudioPermission(false);
                        }
                    } else {
                        // recording start success
                        recordingStopTextView.setText(R.string.label_stop_recording_tap);
                        v.performClick();
                    }
                }
                break;
            }
        }
        return true;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
        this.composeMessageViewModel.setRecording(recording);
    }

    interface RequestAudioPermissionDelegate {
        void requestAudioPermission(boolean rationaleWasShown);
    }
}
