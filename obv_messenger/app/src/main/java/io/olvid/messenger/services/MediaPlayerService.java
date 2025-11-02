/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.services;


import static androidx.media3.common.C.WAKE_MODE_LOCAL;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.os.HandlerCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.LockScreenOrNotActivity;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webrtc.WebrtcCallActivity;
import io.olvid.messenger.webrtc.WebrtcIncomingCallActivity;

public class MediaPlayerService extends Service {
    private static final int SERVICE_ID = 9005;
    public static final String ACTION_PAUSE = "action_pause";
    private static final String ACTION_PLAY = "action_play";
    private static final String ACTION_STOP = "action_stop";

    private static final long PROGRESS_UPDATE_TIME_MS = 33;

    private final MediaPlayerServiceBinder serviceBinder = new MediaPlayerServiceBinder();
    private final Set<PlaybackListener> playbackListeners = new HashSet<>();
    private AudioManager audioManager = null;
    private AudioFocusRequestCompat audioFocusRequest = null;

    private long mediaTimeMs = 0;

    private final Timer timer = new Timer("MediaPlayerService-Timer");

    private final Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private ConnectedDevicesCallback connectedDevicesCallback = null;

    TimerTask timerTask = null;
    private Long discussionId;
    private FyleMessageJoinWithStatusDao.FyleAndStatus loadedMedia;
    private boolean mediaPlaying = false;
    private boolean useSpeakerOutput = false;
    private float playbackSpeed = 1f; // one of 1f, 1.5f or 2f
    private boolean wiredHeadsetConnected = false;
    private boolean bluetoothHeadsetConnected = false;
    Player mediaPlayer = null;
    boolean mediaPlayerPrepared = false;

    @Override
    public void onCreate() {
        super.onCreate();
        this.useSpeakerOutput = SettingsActivity.getUseSpeakerOutputForMediaPlayer();
        this.playbackSpeed = SettingsActivity.getPlaybackSpeedForMediaPlayer();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PAUSE:
                    pausePlayback();
                    break;
                case ACTION_PLAY:
                    unPausePlayback();
                    break;
                case ACTION_STOP:
                    stopPlayback();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayerPrepared = false;
            mediaPlayer.release();
        }
        stopTimer();
        timer.cancel();
        unregisterReceivers();
    }

    public void loadMedia(@NonNull FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, @Nullable Long discussionId, long seekTimeMs) {
        this.discussionId = discussionId;
        this.loadedMedia = fyleAndStatus;
        this.mediaPlaying = false;
        this.mediaTimeMs = 0;

        for (PlaybackListener playbackListener : playbackListeners) {
            playbackListener.onMediaChanged(new BytesKey(loadedMedia.fyle.sha256));
            playbackListener.onProgress(seekTimeMs);
        }

        startPlayback(seekTimeMs);
    }

    public void setUseSpeakerOutput(boolean useSpeakerOutput) {
        if (useSpeakerOutput != this.useSpeakerOutput) {
            this.useSpeakerOutput = useSpeakerOutput;

            if (mediaPlayer != null) {
                // a media is currently playing (or paused) --> restart the playback from the same point
                if (audioFocusRequest != null) {
                    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
                    audioFocusRequest = null;
                }
                internalRequestAudioFocus();

                // re-prepare and re-start the playback
                try {
                    long seekTimeMs = mediaPlayer.getCurrentPosition();

                    mediaPlayerPrepared = false;

                    if (useSpeakerOutput) {
                        mediaPlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_MEDIA)
                                .build(), false);
                    } else {
                        mediaPlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                                .build(), false);
                    }

                    mediaPlayer.setMediaItem(MediaItem.fromUri(App.absolutePathFromRelative(loadedMedia.fyle.filePath)));

                    mediaPlayer.prepare();
                    mediaPlayer.seekTo((int) seekTimeMs);
                    mediaPlayerPrepared = true;
                    if (mediaPlaying) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ignored) {
                        }
                        internalSetPlaybackSpeed(false);
                        mediaPlayer.play();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void internalSetPlaybackSpeed(boolean showToast) {
        if (mediaPlayer.getAvailableCommands().contains(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            mediaPlayer.setPlaybackSpeed(playbackSpeed);
            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onPlaybackSpeedChange(playbackSpeed);
            }
        } else {
            if (showToast) {
                App.toast(R.string.toast_message_playback_speed_not_supported_on_device, Toast.LENGTH_SHORT);
            }

            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onPlaybackSpeedChange(0);
            }
        }
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        if (playbackSpeed != this.playbackSpeed) {
            this.playbackSpeed = playbackSpeed;
            SettingsActivity.setPlaybackSpeedForMediaPlayer(playbackSpeed);

            if (mediaPlayer != null) {
                internalSetPlaybackSpeed(true);
            }
        }
    }

    public float getPlaybackSpeed() {
        if (mediaPlayer != null && mediaPlayer.getAvailableCommands().contains(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            return playbackSpeed;
        } else {
            return 0;
        }
    }


    @SuppressLint("ForegroundServiceType")
    private void internalStartForeground(boolean paused) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AndroidNotificationManager.MEDIA_PLAYER_SERVICE_NOTIFICATION_CHANNEL_ID);
        builder.setContentTitle(getString(R.string.notification_title_now_playing))
                .setContentText(loadedMedia.fyleMessageJoinWithStatus.fileName)
                .setSmallIcon(R.drawable.ic_o)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setSilent(true);

        if (paused) {
            Intent playIntent = new Intent(this, MediaPlayerService.class);
            playIntent.setAction(ACTION_PLAY);
            PendingIntent playPendingIntent = PendingIntent.getService(App.getContext(), 564, playIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_play, getString(R.string.notification_action_play), playPendingIntent);
        } else {
            Intent pauseIntent = new Intent(this, MediaPlayerService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            PendingIntent pausePendingIntent = PendingIntent.getService(App.getContext(), 565, pauseIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_pause, getString(R.string.notification_action_pause), pausePendingIntent);
        }

        Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
        if (discussionId != null && loadedMedia != null) {
            contentIntent.setAction(MainActivity.FORWARD_ACTION);
            contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
            contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
            contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, loadedMedia.fyleMessageJoinWithStatus.bytesOwnedIdentity);
        }
        PendingIntent contentPendingIntent = PendingIntent.getActivity(App.getContext(), 566, contentIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(contentPendingIntent);

        Intent stopIntent = new Intent(this, MediaPlayerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(App.getContext(), 567, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.setDeleteIntent(stopPendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(SERVICE_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(SERVICE_ID, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void seekMedia(long timeMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo((int) timeMs);
                for (PlaybackListener playbackListener : playbackListeners) {
                    playbackListener.onProgress(timeMs);
                }
            } catch (Exception e) {
                // nothing, ignore the seek
            }
        }
    }

    public void playPause() {
        if (mediaPlaying) {
            pausePlayback();
        } else {
            unPausePlayback();
        }
    }

    private void internalRequestAudioFocus() {
        if (audioManager != null && audioFocusRequest == null) {
            AudioFocusRequestCompat.Builder builder = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN);
            if (useSpeakerOutput) {
                builder.setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setFlags(AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build());
            } else {
                builder.setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setFlags(AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build());
            }
            builder.setOnAudioFocusChangeListener(focusChange -> {
                Logger.d("Audiofocus changed " + focusChange);
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        unPausePlayback();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        pausePlayback();
                        break;
                }
            });
            audioFocusRequest = builder.build();
            AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
        }
    }

    private void startPlayback(long seekTimeMs) {
        if (loadedMedia == null) {
            stopPlayback();
            return;
        }

        internalStartForeground(false);
        internalRequestAudioFocus();

        try {
            if (mediaPlayer == null) {
                mediaPlayer = new ExoPlayer.Builder(this)
                        .setWakeMode(WAKE_MODE_LOCAL)
                        .build();
                mediaPlayer.addListener(
                        new Player.Listener() {
                            @Override
                            public void onPlaybackStateChanged(int playbackState) {
                                Player.Listener.super.onPlaybackStateChanged(playbackState);
                                if (playbackState == Player.STATE_ENDED) {
                                    //player back ended
                                    stopPlayback();
                                }
                            }
                        });
            }
            mediaPlayerPrepared = false;

            if (useSpeakerOutput) {
                mediaPlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(), false);
            } else {
                mediaPlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_VOICE_COMMUNICATION)
                        .build(), false);
            }

            mediaPlayer.setMediaItem(MediaItem.fromUri(App.absolutePathFromRelative(loadedMedia.fyle.filePath)));
            mediaPlayer.prepare();
            mediaPlayer.seekTo((int) seekTimeMs);
            mediaPlayerPrepared = true;
            internalSetPlaybackSpeed(false);
            mediaPlayer.play();
            startTimer();
            mediaPlaying = true;
            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onPlay();
            }
        } catch (Exception e) {
            e.printStackTrace();
            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onFail(loadedMedia);
            }
            stopPlayback();
        }
    }

    private void startTimer() {
        if (timerTask == null) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    mainThreadHandler.post(() -> {
                        if (mediaPlayer != null && mediaPlayerPrepared) {
                            try {
                                long timeMs = mediaPlayer.getCurrentPosition();
                                for (PlaybackListener playbackListener : playbackListeners) {
                                    playbackListener.onProgress(timeMs);
                                }
                            } catch (Exception e) {
                                // do nothing
                            }
                        }
                    });
                }
            };
            timer.schedule(timerTask, PROGRESS_UPDATE_TIME_MS, PROGRESS_UPDATE_TIME_MS);
        }
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayerPrepared) {
            internalStartForeground(true);
            stopForeground(false);
            mediaPlayer.pause();
            stopTimer();
            mediaPlaying = false;
            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onPause();
            }
        }
    }

    private void unPausePlayback() {
        if (mediaPlayer != null && mediaPlayerPrepared) {
            if (!mediaPlaying) {
                internalStartForeground(false);
                internalSetPlaybackSpeed(false);
                mediaPlayer.play();
                startTimer();
                mediaPlaying = true;
                for (PlaybackListener playbackListener : playbackListeners) {
                    playbackListener.onPlay();
                }
            }
        }
    }

    private void stopPlayback() {
        for (PlaybackListener playbackListener : playbackListeners) {
            playbackListener.onStop();
        }
        loadedMedia = null;
        stopTimer();
        if (mediaPlayer != null) {
            mediaPlayerPrepared = false;
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (audioFocusRequest != null) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
            audioFocusRequest = null;
        }
        stopForeground(true);
        if (playbackListeners.isEmpty()) {
            stopSelf();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (UnifiedForegroundService.removingExtraTasks) {
            return;
        }

        ComponentName intentComponent = rootIntent.getComponent();
        if (intentComponent != null) {
            if (WebrtcCallActivity.class.getName().equals(intentComponent.getClassName())
                    || WebrtcIncomingCallActivity.class.getName().equals(intentComponent.getClassName())) {
                return;
            }

            try {
                if (LockScreenOrNotActivity.class.isAssignableFrom(Class.forName(intentComponent.getClassName()))) {
                    return;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        stopPlayback();
    }

    public void addPlaybackListener(@NonNull PlaybackListener playbackListener) {
        this.playbackListeners.add(playbackListener);

        updateAudioOutputs();
        if (loadedMedia != null) {
            playbackListener.onMediaChanged(new BytesKey(loadedMedia.fyle.sha256));
            if (mediaPlaying) {
                playbackListener.onPlay();
            } else {
                playbackListener.onPause();
            }
            Logger.e("addPlaybackListener " + mediaTimeMs);
            playbackListener.onProgress(mediaTimeMs);
        }
    }

    public void removePlaybackListener(@NonNull PlaybackListener playbackListener) {
        this.playbackListeners.remove(playbackListener);
        if (!mediaPlaying && this.playbackListeners.isEmpty()) {
            stopPlayback();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    public class MediaPlayerServiceBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    public interface PlaybackListener {
        void onMediaChanged(BytesKey bytesKey);

        void onPause();

        void onPlay();

        void onProgress(long timeMs);

        void onStop();

        void onFail(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus);

        void onSpeakerOutputChange(AudioOutput audioOutput);

        // when called with playbackSpeed == 0, this means the current mediaplayer does not support playback speed change
        void onPlaybackSpeedChange(float playbackSpeed);
    }


    private void registerReceivers() {
        connectedDevicesCallback = new ConnectedDevicesCallback();
        audioManager.registerAudioDeviceCallback(connectedDevicesCallback, null);
    }

    private void unregisterReceivers() {
        if (connectedDevicesCallback != null) {
            audioManager.unregisterAudioDeviceCallback(connectedDevicesCallback);
        }
    }

    private class ConnectedDevicesCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            super.onAudioDevicesAdded(addedDevices);
            refreshDevices();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            super.onAudioDevicesRemoved(removedDevices);
            refreshDevices();
        }

        private void refreshDevices() {
            wiredHeadsetConnected = false;
            bluetoothHeadsetConnected = false;
            AudioDeviceInfo[] devices;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                devices = audioManager.getAvailableCommunicationDevices().toArray(new AudioDeviceInfo[0]);
            } else {
                devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            }
            for (AudioDeviceInfo audioDeviceInfo : devices) {
                if (audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    wiredHeadsetConnected = true;
                } else if (audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_BROADCAST
                        || audioDeviceInfo.getType() == AudioDeviceInfo.TYPE_HEARING_AID
                ) {
                    bluetoothHeadsetConnected = true;
                }
            }
            updateAudioOutputs();
        }
    }

    public enum AudioOutput {
        PHONE,
        HEADSET,
        LOUDSPEAKER,
        BLUETOOTH
    }

    private void updateAudioOutputs() {
        mainThreadHandler.post(() -> {
            if (wiredHeadsetConnected) {
                setUseSpeakerOutput(true);
                for (PlaybackListener playbackListener : playbackListeners) {
                    playbackListener.onSpeakerOutputChange(AudioOutput.HEADSET);
                }
            } else if (bluetoothHeadsetConnected) {
                setUseSpeakerOutput(true);
                for (PlaybackListener playbackListener : playbackListeners) {
                    playbackListener.onSpeakerOutputChange(AudioOutput.BLUETOOTH);
                }
            } else {
                boolean useSpeakerOutput = SettingsActivity.getUseSpeakerOutputForMediaPlayer();
                for (PlaybackListener playbackListener : playbackListeners) {
                    playbackListener.onSpeakerOutputChange(useSpeakerOutput ? AudioOutput.LOUDSPEAKER : AudioOutput.PHONE);
                }
                setUseSpeakerOutput(useSpeakerOutput);
            }
        });
    }


    public void toggleAudioOutput() {
        // if a wired/bluetooth headset is connected, toggling the output has no effect
        if (!wiredHeadsetConnected && !bluetoothHeadsetConnected) {
            SettingsActivity.setUseSpeakerOutputForMediaPlayer(!useSpeakerOutput);
            for (PlaybackListener playbackListener : playbackListeners) {
                playbackListener.onSpeakerOutputChange(!useSpeakerOutput ? AudioOutput.LOUDSPEAKER : AudioOutput.PHONE);
            }
            setUseSpeakerOutput(!useSpeakerOutput);
        }
    }
}
