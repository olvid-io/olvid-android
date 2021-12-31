/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.media.session.MediaSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.settings.SettingsActivity;

public class IncomingCallRinger {
    private final Context context;
    private final Vibrator vibrator;
    private CameraManager cameraManager;
    private final Set<String> cameraIdsToFlash;
    private final HashMap<String, Thread> cameraIdsFlashThreads;

    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;

    public IncomingCallRinger(Context context) {
        this.context = context;
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        cameraIdsToFlash = new HashSet<>();
        cameraIdsFlashThreads = new HashMap<>();
        cameraManager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (SettingsActivity.useFlashOnIncomingCall()) {
                cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    try {
                        for (String cameraId : cameraManager.getCameraIdList()) {
                            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                            Boolean hasFlash = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                            if (hasFlash != null && hasFlash) {
                                cameraIdsToFlash.add(cameraId);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void ring(UUID callIdentifier) {
        int ringerMode = AudioManager.RINGER_MODE_NORMAL;
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            ringerMode = audioManager.getRingerMode();
        }

        cleanup();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, SettingsActivity.getCallRingtone());
            mediaPlayer.setLooping(true);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("☎️ Error initializing media player for incoming call ringing");
            mediaPlayer = null;
        }


        // play sound
        if (mediaPlayer != null) {
            mediaSession = new MediaSession(context, "Olvid incoming ringer");
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
                        Intent answerCallIntent = new Intent(context, WebrtcCallService.class);
                        answerCallIntent.setAction(WebrtcCallService.ACTION_ANSWER_CALL);
                        answerCallIntent.putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, callIdentifier.toString());
                        context.startService(answerCallIntent);
                        return true;
                    }
                    return false;
                }
            }, new Handler(Looper.getMainLooper()));
            mediaSession.setActive(true);

            try {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                }
            } catch (IllegalStateException | IOException e) {
                e.printStackTrace();
                mediaPlayer = null;
            }
        }

        // vibrate
        long[] vibrationPattern = SettingsActivity.getCallVibrationPattern();
        if (vibrator != null && vibrationPattern.length > 0 && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, 1));
            } else {
                vibrator.vibrate(vibrationPattern, 1);
            }
        }

        // flash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (Thread thread : cameraIdsFlashThreads.values()) {
                thread.interrupt();
            }
            cameraIdsFlashThreads.clear();
            for (String cameraId : cameraIdsToFlash) {
                CameraFlashThread cameraFlashThread = new CameraFlashThread(cameraId);
                cameraFlashThread.start();
                cameraIdsFlashThreads.put(cameraId, cameraFlashThread);
            }
        }
    }

    public void stop() {
        if (vibrator != null) {
            vibrator.cancel();
        }
        for (Thread thread : cameraIdsFlashThreads.values()) {
            thread.interrupt();
        }
        cleanup();
    }

    private void cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private class CameraFlashThread extends Thread {
        @NonNull final String cameraId;

        public CameraFlashThread(@NonNull String cameraId) {
            this.cameraId = cameraId;
        }

        @SuppressWarnings("BusyWait")
        @Override
        public void run() {
            while (true) {
                torch(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    torch(false);
                    return;
                }
                torch(false);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
                torch(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    torch(false);
                    return;
                }
                torch(false);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private void torch(boolean enabled) {
            try {
                cameraManager.setTorchMode(cameraId, enabled);
            } catch (CameraAccessException e) {
                // Nothing special to do here
            }
        }
    }
}
