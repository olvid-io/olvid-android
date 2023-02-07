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

package io.olvid.messenger.customClasses;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.services.MediaPlayerService;

public class AudioAttachmentServiceBinding implements MediaPlayerService.PlaybackListener {
    private final AppCompatActivity activity;
    private final Handler uiThreadHandler;
    private final NoExceptionSingleThreadExecutor executor;
    private final Set<BytesKey> loadingAttachments;
    private final Map<BytesKey, List<WeakReference<AudioServiceBindableViewHolder>>> viewHolderAssociation;
    private final Map<BytesKey, AudioInfo> loadedAttachments;
    private final MediaPlayerServiceConnection serviceConnection;

    private MediaPlayerService mediaPlayerService;
    private BytesKey nowPlaying;
    private boolean playing;
    private long playTimeMs;
    private MediaPlayerService.AudioOutput audioOutput = MediaPlayerService.AudioOutput.LOUDSPEAKER;


    public AudioAttachmentServiceBinding(AppCompatActivity activity) {
        this.activity = activity;
        this.uiThreadHandler = new Handler(Looper.getMainLooper());
        this.executor = new NoExceptionSingleThreadExecutor("AudioAttachmentServiceBinding-Executor");
        this.loadingAttachments = new HashSet<>();
        this.viewHolderAssociation = new HashMap<>();
        this.loadedAttachments = new HashMap<>();
        this.serviceConnection = new MediaPlayerServiceConnection();

        this.nowPlaying = null;
        this.playing = false;
        this.playTimeMs = 0;
        this.mediaPlayerService = null;

        Intent serviceBindIntent = new Intent(activity, MediaPlayerService.class);
        activity.startService(serviceBindIntent);
        activity.bindService(serviceBindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }


    public void release() {
        serviceConnection.unBind();
        executor.shutdownNow();
        viewHolderAssociation.clear();
        loadedAttachments.clear();
    }



    public void loadAudioAttachment(final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, final AudioServiceBindableViewHolder viewHolder) {
        executor.execute(() -> {
            BytesKey key = new BytesKey(fyleAndStatus.fyle.sha256);
            List<WeakReference<AudioServiceBindableViewHolder>> viewHolders = viewHolderAssociation.get(key);
            if (viewHolders == null) {
                viewHolders = new ArrayList<>();
                viewHolders.add(new WeakReference<>(viewHolder));
                viewHolderAssociation.put(key, viewHolders);
            } else {
                // existing the list -> remove any expired WeakReference, check this ViewHolder is not already present
                List<WeakReference<AudioServiceBindableViewHolder>> refreshedViewHolders = new ArrayList<>();
                refreshedViewHolders.add(new WeakReference<>(viewHolder));
                for (WeakReference<AudioServiceBindableViewHolder> weakReference : viewHolders) {
                    AudioServiceBindableViewHolder vh = weakReference.get();
                    if (vh == null || vh.equals(viewHolder)) {
                        continue;
                    }
                    refreshedViewHolders.add(weakReference);
                }
                viewHolderAssociation.put(key, refreshedViewHolders);
            }

            AudioInfo audioInfo = loadedAttachments.get(key);
            if (audioInfo != null) {
                uiThreadHandler.post(() -> {
                    viewHolder.bindAudioInfo(audioInfo, audioOutput);
                    if (key.equals(nowPlaying)) {
                        viewHolder.updatePlayTimeMs(audioInfo, playTimeMs, playing);
                    }
                });
            } else {
                if (!loadingAttachments.contains(key)) {
                    // in case this was never loaded
                    loadingAttachments.add(key);
                    App.runThread(new LoadAudioAttachmentTask(key, fyleAndStatus));
                }
            }
        });
    }

    public void seekAudioAttachment(@NonNull FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, int progress) {
        final BytesKey key = new BytesKey(fyleAndStatus.fyle.sha256);
        executor.execute(() -> {
            AudioInfo audioInfo = loadedAttachments.get(key);
            if (audioInfo == null || audioInfo.durationMs == null) {
                return;
            }
            long timeMs = (progress * audioInfo.durationMs)/1000L;
            if (key.equals(nowPlaying)) {
                if (mediaPlayerService != null) {
                    mediaPlayerService.seekMedia(timeMs);
                }
            } else {
                updatePlayTimeMs(key, timeMs, false);
            }
        });
    }

    public void playPause(@NonNull FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, Long discussionId) {
        final BytesKey key = new BytesKey(fyleAndStatus.fyle.sha256);
        executor.execute(() -> {
            if (mediaPlayerService != null) {
                if (key.equals(nowPlaying)) {
                    mediaPlayerService.playPause();
                } else {
                    long seekTimeMs = 0;
                    AudioInfo audioInfo = loadedAttachments.get(key);
                    if (audioInfo != null) {
                        seekTimeMs = audioInfo.seekTimeMs;
                        // if duration failed loading, retry loading it
                        if (audioInfo.durationMs == null) {
                            loadingAttachments.add(key);
                            App.runThread(new LoadAudioAttachmentTask(key, fyleAndStatus));
                        }
                    }
                    mediaPlayerService.loadMedia(fyleAndStatus, discussionId, seekTimeMs);
                }
            }
        });
    }


    @Override
    public void onMediaChanged(BytesKey bytesKey) {
        long timeMs = playTimeMs;
        executor.execute(() -> {
            updatePlayTimeMs(timeMs, false); // pause the previously playing media
            nowPlaying = bytesKey;
        });
    }

    @Override
    public void onPause() {
        executor.execute(() -> updatePlayTimeMs(playTimeMs, false));
    }

    @Override
    public void onPlay() {
        executor.execute(() -> updatePlayTimeMs(playTimeMs, true));
    }

    @Override
    public void onProgress(long timeMs) {
        executor.execute(() -> updatePlayTimeMs(timeMs, this.playing));
    }

    @Override
    public void onStop() {
        executor.execute(() -> {
            updatePlayTimeMs(0, false);
            nowPlaying = null;
        });
    }

    @Override
    public void onFail(@NonNull final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
        BytesKey key = new BytesKey(fyleAndStatus.fyle.sha256);
        executor.execute(() -> {
            AudioInfo audioInfo = loadedAttachments.get(key);
            if (audioInfo != null) {
                audioInfo.failed = true;
                App.toast(activity.getString(R.string.toast_message_unable_to_play_audio, fyleAndStatus.fyleMessageJoinWithStatus.fileName), Toast.LENGTH_SHORT, Gravity.BOTTOM);

                List<WeakReference<AudioServiceBindableViewHolder>> associatedViewHolders = viewHolderAssociation.get(key);
                if (associatedViewHolders == null) {
                    return;
                }
                List<AudioServiceBindableViewHolder> viewHolders = new ArrayList<>();
                for (WeakReference<AudioServiceBindableViewHolder> weakReference : associatedViewHolders) {
                    AudioServiceBindableViewHolder vh = weakReference.get();
                    if (vh != null) {
                        viewHolders.add(vh);
                    }
                }
                uiThreadHandler.post(() -> {
                    for (AudioServiceBindableViewHolder viewHolder : viewHolders) {
                        viewHolder.setFailed(true);
                        viewHolder.bindAudioInfo(audioInfo, audioOutput);
                    }
                });
            }
        });
    }

    @Override
    public void onSpeakerOutputChange(MediaPlayerService.AudioOutput audioOutput) {
        executor.execute(() -> {
            this.audioOutput = audioOutput;

            // notify all view holders of output change
            List<AudioServiceBindableViewHolder> viewHolders = new ArrayList<>();
            for (List<WeakReference<AudioServiceBindableViewHolder>> viewHolderWeakReferences : viewHolderAssociation.values()) {
                for (WeakReference<AudioServiceBindableViewHolder> viewHolderWeakReference : viewHolderWeakReferences) {
                    AudioServiceBindableViewHolder vh = viewHolderWeakReference.get();
                    if (vh != null) {
                        viewHolders.add(vh);
                    }
                }
            }

            uiThreadHandler.post(() -> {
                for (AudioServiceBindableViewHolder viewHolder : viewHolders) {
                    viewHolder.setAudioOutput(this.audioOutput, nowPlaying != null && this.playing);
                }
            });
        });
    }

    public void toggleSpeakerOutput() {
        executor.execute(() -> {
            if (mediaPlayerService != null) {
                mediaPlayerService.toggleAudioOutput();
            }
        });
    }

    private class LoadAudioAttachmentTask implements Runnable {
        private final BytesKey key;
        private final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;

        public LoadAudioAttachmentTask(BytesKey key, FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
            this.key = key;
            this.fyleAndStatus = fyleAndStatus;
        }

        @Override
        public void run() {
            final AudioInfo audioInfo = new AudioInfo();
            try {
                // Do not use "close with resource" as this only works on API 29 or higher...
                //noinspection resource
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(App.absolutePathFromRelative(fyleAndStatus.fyle.filePath));
                String durationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                mediaMetadataRetriever.release();
                if (durationString != null) {
                    audioInfo.durationMs = Long.parseLong(durationString);
                }
            } catch (Exception e) {
                // do nothing
            }
            executor.execute(() -> {
                loadingAttachments.remove(key);
                loadedAttachments.put(key, audioInfo);
                List<AudioServiceBindableViewHolder> viewHolders = new ArrayList<>();
                List<WeakReference<AudioServiceBindableViewHolder>> associatedViewHolders = viewHolderAssociation.get(key);
                if (associatedViewHolders == null) {
                    return;
                }
                for (WeakReference<AudioServiceBindableViewHolder> weakReference : associatedViewHolders) {
                    AudioServiceBindableViewHolder vh = weakReference.get();
                    if (vh != null) {
                        viewHolders.add(vh);
                    }
                }
                uiThreadHandler.post(() -> {
                    for (AudioServiceBindableViewHolder viewHolder : viewHolders) {
                        viewHolder.bindAudioInfo(audioInfo, audioOutput);
                        if (key.equals(nowPlaying)) {
                            viewHolder.updatePlayTimeMs(audioInfo, playTimeMs, playing);
                        }
                    }
                });
            });
        }
    }

    // should always be called from executor
    private void updatePlayTimeMs(long playTimeMs, boolean playing) {
        if (nowPlaying == null) {
            return;
        }
        this.playTimeMs = playTimeMs;
        if (this.playing != playing) {
            // refresh the output each time the media play/pause changes --> this allows updating the default volume control stream
            this.playing = playing;
            onSpeakerOutputChange(audioOutput);
        }
        updatePlayTimeMs(nowPlaying, playTimeMs, playing);
    }

    private void updatePlayTimeMs(@NonNull BytesKey key, long playTimeMs, boolean playing) {
        AudioInfo audioInfo = loadedAttachments.get(key);
        List<WeakReference<AudioServiceBindableViewHolder>> weakReferences = viewHolderAssociation.get(key);
        if (weakReferences == null || audioInfo == null || audioInfo.failed) {
            return;
        }
        audioInfo.seekTimeMs = playTimeMs;

        List<AudioServiceBindableViewHolder> viewHolders = new ArrayList<>();
        for (WeakReference<AudioServiceBindableViewHolder> weakReference : weakReferences) {
            AudioServiceBindableViewHolder viewHolder = weakReference.get();
            if (viewHolder != null) {
                if (viewHolder.getFyleAndStatus() != null && new BytesKey(viewHolder.getFyleAndStatus().fyle.sha256).equals(key)) {
                    viewHolders.add(viewHolder);
                }
            }
        }
        if (viewHolders.isEmpty()) {
            viewHolderAssociation.remove(key);
        } else {
            uiThreadHandler.post(() -> {
                for (AudioServiceBindableViewHolder viewHolder : viewHolders) {
                    viewHolder.updatePlayTimeMs(audioInfo, playTimeMs, playing);
                }
            });
        }
    }

    public static String timeFromMs(long timeMs) {
        long hours = timeMs / 3_600_000L;
        timeMs -= hours*3_600_000L;
        long minutes = timeMs / 60_000L;
        timeMs -= minutes*60_000L;
        long seconds = timeMs / 1_000L;

        if (hours == 0) {
            return String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds);
        } else {
            return String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    private class MediaPlayerServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof MediaPlayerService.MediaPlayerServiceBinder) {
                executor.execute(() -> {
                    mediaPlayerService = ((MediaPlayerService.MediaPlayerServiceBinder) service).getService();
                    mediaPlayerService.addPlaybackListener(AudioAttachmentServiceBinding.this);
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            executor.execute(() -> mediaPlayerService = null);
        }

        public void unBind() {
            if (mediaPlayerService != null) {
                mediaPlayerService.removePlaybackListener(AudioAttachmentServiceBinding.this);
            }
            activity.unbindService(this);
            mediaPlayerService = null;
        }
    }

    public static class AudioInfo {
        public Long durationMs = null;
        public long seekTimeMs = 0;
        public boolean failed = false;
    }

    public interface AudioServiceBindableViewHolder {
        void updatePlayTimeMs(AudioInfo audioInfo, long playTimeMs, boolean playing);
        void bindAudioInfo(AudioInfo audioInfo, MediaPlayerService.AudioOutput audioOutput);
        void setFailed(boolean failed);
        void setAudioOutput(MediaPlayerService.AudioOutput audioOutput, boolean somethingPlaying);
        FyleMessageJoinWithStatusDao.FyleAndStatus getFyleAndStatus();
    }
}
