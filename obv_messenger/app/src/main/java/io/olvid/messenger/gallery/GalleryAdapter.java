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

package io.olvid.messenger.gallery;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.player.MediaPlayer;
import androidx.media2.widget.VideoView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryImageViewHolder> implements Observer<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> {
    static final int STATUS_CHANGE_MASK = 1;
    static final int PROGRESS_CHANGE_MASK = 2;

    static final int VIEW_TYPE_IMAGE = 1;
    static final int VIEW_TYPE_VIDEO = 2;

    private final LayoutInflater layoutInflater;
    @NonNull
    private final GalleryAdapterCallbacks galleryAdapterCallbacks;
    private final HashMap<GalleryViewModel.MessageAndFyleId, MediaPlayer> preparedMediaPlayers;
    private final List<MediaPlayer> allMediaPlayers;
    List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses;
    GalleryViewModel.MessageAndFyleId messageAndFyleIdToGoTo;
    GalleryViewModel.MessageAndFyleId messageAndFyleIdToPlay;


    public GalleryAdapter(LayoutInflater layoutInflater, @NonNull GalleryAdapterCallbacks galleryAdapterCallbacks) {
        this.layoutInflater = layoutInflater;
        this.galleryAdapterCallbacks = galleryAdapterCallbacks;
        this.setHasStableIds(true);
        this.preparedMediaPlayers = new HashMap<>();
        this.allMediaPlayers = new ArrayList<>();
    }

    public void cleanup() {
        for (MediaPlayer mediaPlayer: allMediaPlayers) {
            Logger.e("Closing media player");
            mediaPlayer.close();
        }
        allMediaPlayers.clear();
    }

    @Override
    public int getItemViewType(int position) {
        if (fyleAndStatuses != null) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndStatuses.get(position);
            if (fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("video/")) {
                return VIEW_TYPE_VIDEO;
            }
        }
        return VIEW_TYPE_IMAGE;
    }

    @NonNull
    @Override
    public GalleryImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_VIDEO: {
                View view = layoutInflater.inflate(R.layout.item_view_gallery_video, parent, false);
                return new GalleryImageViewHolder(view);
            }
            case VIEW_TYPE_IMAGE:
            default: {
                View view = layoutInflater.inflate(R.layout.item_view_gallery_image, parent, false);
                return new GalleryImageViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryImageViewHolder holder, int position) {
        // this is never called as we override the payloads version
    }


    @Override
    public void onBindViewHolder(@NonNull GalleryImageViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (fyleAndStatuses == null || fyleAndStatuses.size() <= position || position < 0) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndStatuses.get(position);
        holder.fyleAndStatus = fyleAndStatus;
        if (holder.imageView != null) {
            App.runThread(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        Drawable drawable = PreviewUtilsWithDrawables.getDrawablePreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus, PreviewUtils.MAX_SIZE);
                        if (holder.fyleAndStatus.equals(fyleAndStatus)) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (drawable == null) {
                                    holder.previewErrorTextView.setVisibility(View.VISIBLE);
                                } else {
                                    holder.previewErrorTextView.setVisibility(View.GONE);
                                }
                                if (drawable instanceof AnimatedImageDrawable) {
                                    ((AnimatedImageDrawable) drawable).start();
                                }
                                holder.imageView.setImageDrawable(drawable);
                            });
                        }
                        return;
                    }
                } catch (Exception e) {
                    // drawable failed fallback to bitmap
                }

                Bitmap bitmap = PreviewUtils.getBitmapPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus, PreviewUtils.MAX_SIZE);
                if (holder.fyleAndStatus.equals(fyleAndStatus)) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (bitmap == null) {
                            holder.previewErrorTextView.setVisibility(View.VISIBLE);
                        } else {
                            holder.previewErrorTextView.setVisibility(View.GONE);
                        }
                        holder.imageView.setImageBitmap(bitmap);
                    });
                }
            });
        } else if (holder.mediaPlayer != null) {
            try {
                MediaItem mediaItem = new FileMediaItem.Builder(ParcelFileDescriptor.open(new File(fyleAndStatus.fyleMessageJoinWithStatus.getAbsoluteFilePath()), ParcelFileDescriptor.MODE_READ_ONLY))
                        .setMetadata(new MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_TITLE, "")
                                .build())
                        .build();
                holder.mediaPlayer.setPlaylist(Collections.singletonList(mediaItem), null);
                holder.prepareResult = holder.mediaPlayer.prepare();
                holder.prepareResult.addListener(() -> {
                    holder.prepareResult = null;
                    GalleryViewModel.MessageAndFyleId messageAndFyleId = new GalleryViewModel.MessageAndFyleId(fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                    preparedMediaPlayers.put(messageAndFyleId, holder.mediaPlayer);
                    if (messageAndFyleId.equals(messageAndFyleIdToPlay)) {
                        holder.mediaPlayer.play();
                        messageAndFyleIdToPlay = null;
                    }
                }, ContextCompat.getMainExecutor(holder.videoView.getContext()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int getItemCount() {
        if (fyleAndStatuses != null) {
            return fyleAndStatuses.size();
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        if (fyleAndStatuses != null) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndStatuses.get(position);
            return fyleAndStatus.fyleMessageJoinWithStatus.messageId << 32 + fyleAndStatus.fyleMessageJoinWithStatus.fyleId;
        }
        return -1;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onChanged(List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses) {
        if (fyleAndStatuses == null || this.fyleAndStatuses == null) {
            this.fyleAndStatuses = fyleAndStatuses;
            notifyDataSetChanged();
            if (messageAndFyleIdToGoTo != null) {
                goToFyle(messageAndFyleIdToGoTo);
            }
            return;
        }
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            final int[] payloadCache = new int[fyleAndStatuses.size()];
            final boolean[] payloadComputed = new boolean[payloadCache.length];

            @NonNull
            final List<FyleMessageJoinWithStatusDao.FyleAndStatus> oldList = GalleryAdapter.this.fyleAndStatuses;
            @NonNull
            final List<FyleMessageJoinWithStatusDao.FyleAndStatus> newList = fyleAndStatuses;

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
                FyleMessageJoinWithStatusDao.FyleAndStatus oldItem = oldList.get(oldItemPosition);
                FyleMessageJoinWithStatusDao.FyleAndStatus newItem = newList.get(newItemPosition);

                return oldItem.fyleMessageJoinWithStatus.messageId == newItem.fyleMessageJoinWithStatus.messageId
                        && oldItem.fyleMessageJoinWithStatus.fyleId == newItem.fyleMessageJoinWithStatus.fyleId;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return (int) getChangePayload(oldItemPosition, newItemPosition) == 0;
            }

            @Override
            @NonNull
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                if (payloadComputed[newItemPosition]) {
                    return payloadCache[newItemPosition];
                }

                FyleMessageJoinWithStatusDao.FyleAndStatus oldItem = oldList.get(oldItemPosition);
                FyleMessageJoinWithStatusDao.FyleAndStatus newItem = newList.get(newItemPosition);

                int changesMask = 0;
                if (oldItem.fyleMessageJoinWithStatus.status != newItem.fyleMessageJoinWithStatus.status) {
                    changesMask |= STATUS_CHANGE_MASK;
                }

                if (oldItem.fyleMessageJoinWithStatus.progress != newItem.fyleMessageJoinWithStatus.progress) {
                    changesMask |= PROGRESS_CHANGE_MASK;
                }

                payloadCache[newItemPosition] = changesMask;
                payloadComputed[newItemPosition] = true;
                return changesMask;
            }

        });
        this.fyleAndStatuses = fyleAndStatuses;
        result.dispatchUpdatesTo(this);
    }

    public void goToFyle(@NonNull GalleryViewModel.MessageAndFyleId messageAndFyleId) {
        messageAndFyleIdToGoTo = messageAndFyleId;
        if (fyleAndStatuses != null) {
            for (int i=0; i<fyleAndStatuses.size(); i++) {
                FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndStatuses.get(i);
                if (fyleAndStatus.fyleMessageJoinWithStatus.messageId == messageAndFyleIdToGoTo.messageId
                    && fyleAndStatus.fyleMessageJoinWithStatus.fyleId == messageAndFyleIdToGoTo.fyleId) {

                    galleryAdapterCallbacks.setCurrentItem(i);
                    if (fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("video/")) {
                        MediaPlayer mediaPlayer = preparedMediaPlayers.get(messageAndFyleId);
                        if (mediaPlayer != null) {
                            mediaPlayer.play();
                        } else {
                            messageAndFyleIdToPlay = messageAndFyleId;
                        }
                    }
                    break;
                }
            }
            messageAndFyleIdToGoTo = null;
        }
    }

    @Nullable
    public FyleMessageJoinWithStatusDao.FyleAndStatus getItemAt(int position) {
        if (fyleAndStatuses != null && position < fyleAndStatuses.size() && position >= 0) {
            return fyleAndStatuses.get(position);
        }
        return null;
    }

    @Override
    public void onViewRecycled(@NonNull GalleryImageViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.mediaPlayer != null) {
            preparedMediaPlayers.remove(new GalleryViewModel.MessageAndFyleId(holder.fyleAndStatus.fyleMessageJoinWithStatus.messageId, holder.fyleAndStatus.fyleMessageJoinWithStatus.fyleId));
            holder.mediaPlayer.reset();
        }
        if (holder.imageView != null) {
            holder.imageView.setImageDrawable(null);
        }
    }

    public void pauseVideos() {
        for (MediaPlayer mediaPlayer : preparedMediaPlayers.values()) {
            mediaPlayer.pause();
        }
    }


    public class GalleryImageViewHolder extends RecyclerView.ViewHolder {
        final GalleryImageView imageView;
        final VideoView videoView;
        final TextView previewErrorTextView;
        final MediaPlayer mediaPlayer;
        ListenableFuture<SessionPlayer.PlayerResult> prepareResult;
        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;

        public GalleryImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
            videoView = itemView.findViewById(R.id.video_view);
            previewErrorTextView = itemView.findViewById(R.id.preview_error_text_view);
            if (imageView != null) {
                imageView.setParentViewPagerUserInputController(galleryAdapterCallbacks::setViewPagerUserInputEnabled);
                imageView.setSingleTapUpCallback(galleryAdapterCallbacks::singleTapUp);
            }
            if (videoView != null) {
                mediaPlayer = new MediaPlayer(itemView.getContext());
                mediaPlayer.setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
                        .build());
                videoView.setPlayer(mediaPlayer);
                allMediaPlayers.add(mediaPlayer);
            } else {
                mediaPlayer = null;
            }
        }
    }

    public interface GalleryAdapterCallbacks {
        void singleTapUp();
        void setCurrentItem(int position);
        void setViewPagerUserInputEnabled(boolean enabled);
    }
}
