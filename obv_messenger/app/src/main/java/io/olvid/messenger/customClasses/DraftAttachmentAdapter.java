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

package io.olvid.messenger.customClasses;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.olvid.messenger.App;
import io.olvid.messenger.FyleProgressSingleton;
import io.olvid.messenger.ProgressStatus;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.discussion.message.attachments.Visibility;
import io.olvid.messenger.services.MediaPlayerService;
import io.olvid.messenger.settings.SettingsActivity;

public class DraftAttachmentAdapter extends RecyclerView.Adapter<DraftAttachmentAdapter.AttachmentViewHolder> implements Observer<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> {
    protected List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles;
    private final LayoutInflater inflater;
    private final FragmentActivity activity;
    @NonNull private final AudioAttachmentServiceBinding audioAttachmentServiceBinding;
    private final int previewPixelSize;
    private AttachmentLongClickListener attachmentLongClickListener;
    private long discussionId;

    public DraftAttachmentAdapter(FragmentActivity activity, @NonNull AudioAttachmentServiceBinding audioAttachmentServiceBinding) {
        this.activity = activity;
        this.previewPixelSize = activity.getResources().getDimensionPixelSize(R.dimen.attachment_small_preview_size);
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding;
        inflater = LayoutInflater.from(activity);
        setHasStableIds(true);
    }

    public void setDiscussionId(long discussionId) {
        this.discussionId = discussionId;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onChanged(@Nullable List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles) {
        this.attachmentFyles = attachmentFyles;
        notifyDataSetChanged();
    }


    @Override
    public long getItemId(int position) {
        if (attachmentFyles != null) {
            return attachmentFyles.get(position).fyle.id;
        }
        return 0;
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_view_attachment_draft, parent, false);
        return new AttachmentViewHolder(view);
    }

    @Override
    public void onViewRecycled(@NonNull AttachmentViewHolder holder) {
        super.onViewRecycled(holder);
        holder.fyleAndStatus = null;
        holder.attachmentImageView.setImageDrawable(null);
        if (holder.progressLiveData != null) {
            holder.progressLiveData.removeObserver(holder.progressListener);
            holder.progressLiveData = null;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final AttachmentViewHolder holder, int position) {
        if (attachmentFyles == null) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = attachmentFyles.get(position);
        final String mimeType = fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
        holder.fyleAndStatus = fyleAndStatus;

        if (PreviewUtils.canGetPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus)) {
            if (mimeType.startsWith("image/")) {
                holder.attachmentFileName.setVisibility(View.GONE);
                holder.attachmentMimeType.setVisibility(View.GONE);
                holder.attachmentSize.setVisibility(View.GONE);
                holder.audioPlayTime.setVisibility(View.GONE);
                holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                holder.attachmentFileName.setVisibility(View.VISIBLE);
                holder.attachmentMimeType.setVisibility(View.VISIBLE);
                holder.attachmentSize.setVisibility(View.VISIBLE);
                holder.audioPlayTime.setVisibility(View.GONE);
                if (fyleAndStatus.fyleMessageJoinWithStatus.status != FyleMessageJoinWithStatus.STATUS_DRAFT) {
                    holder.attachmentFileName.setMaxLines(1);
                } else {
                    holder.attachmentFileName.setMaxLines(2);
                }
                holder.attachmentFileName.setText(fyleAndStatus.fyleMessageJoinWithStatus.fileName);
                holder.attachmentMimeType.setText(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                holder.attachmentSize.setText(Formatter.formatShortFileSize(App.getContext(), fyleAndStatus.fyleMessageJoinWithStatus.size));
                if (mimeType.startsWith("video/")) {
                    holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                }
            }

            App.runThread(new ShowPreviewTask(fyleAndStatus, holder, previewPixelSize));
        } else {
            holder.attachmentFileName.setVisibility(View.VISIBLE);
            holder.attachmentMimeType.setVisibility(View.VISIBLE);
            holder.attachmentSize.setVisibility(View.VISIBLE);
            holder.audioPlayTime.setVisibility(View.INVISIBLE);
            if (fyleAndStatus.fyleMessageJoinWithStatus.status != FyleMessageJoinWithStatus.STATUS_DRAFT) {
                holder.attachmentFileName.setMaxLines(1);
            } else {
                holder.attachmentFileName.setMaxLines(2);
            }
            holder.attachmentFileName.setText(fyleAndStatus.fyleMessageJoinWithStatus.fileName);
            holder.attachmentMimeType.setText(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
            holder.attachmentSize.setText(Formatter.formatShortFileSize(App.getContext(), fyleAndStatus.fyleMessageJoinWithStatus.size));
            holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (mimeType.startsWith("audio/")) {
                if (holder.musicFailed) {
                    holder.attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio_failed);
                } else {
                    audioAttachmentServiceBinding.loadAudioAttachment(fyleAndStatus, holder);
                }
            } else {
                holder.attachmentImageView.setImageResource(PreviewUtils.getDrawableResourceForMimeType(mimeType));
            }
        }
        switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
            case FyleMessageJoinWithStatus.STATUS_COPYING:
                holder.attachmentProgress.setVisibility(View.VISIBLE);
                holder.attachmentProgressLabel.setVisibility(View.VISIBLE);
                holder.attachmentProgressLabel.setText(R.string.label_copy);
                if (holder.progressLiveData == null) {
                    final AnimatedVectorDrawableCompat zeroProgressSpinner = AnimatedVectorDrawableCompat.create(activity, R.drawable.file_progress_zero_spinner);
                    if (zeroProgressSpinner != null) {
                        zeroProgressSpinner.start();
                        holder.attachmentStatusIconImageView.setImageDrawable(zeroProgressSpinner);
                        holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                    } else {
                        holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                    }

                    holder.progressLiveData = FyleProgressSingleton.INSTANCE.getProgress(fyleAndStatus.fyleMessageJoinWithStatus.fyleId, fyleAndStatus.fyleMessageJoinWithStatus.messageId);
                    holder.progressLiveData.observe(activity, holder.progressListener);
                }

                holder.attachmentDeleteImageView.setVisibility(View.VISIBLE);
                break;
            case FyleMessageJoinWithStatus.STATUS_DRAFT:
                holder.attachmentProgress.setVisibility(View.GONE);
                holder.attachmentProgressLabel.setVisibility(View.GONE);
                holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                holder.attachmentDeleteImageView.setVisibility(View.VISIBLE);
                break;
            case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
            case FyleMessageJoinWithStatus.STATUS_UPLOADING:
            case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
            case FyleMessageJoinWithStatus.STATUS_COMPLETE:
            default:
                holder.attachmentProgress.setVisibility(View.GONE);
                holder.attachmentProgressLabel.setVisibility(View.GONE);
                holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                holder.attachmentDeleteImageView.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        if (attachmentFyles != null) {
            return attachmentFyles.size();
        }
        return 0;
    }

    private void attachmentClicked(int position) {
        if (attachmentFyles == null) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = attachmentFyles.get(position);
        if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(fyleAndStatus.fyleMessageJoinWithStatus.mimeType, fyleAndStatus.fyleMessageJoinWithStatus.fileName)) && SettingsActivity.useInternalImageViewer()) {
            App.openDraftGalleryActivity(activity, fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId);
        } else {
            App.openFyleViewer(activity, fyleAndStatus, null);
        }
    }

    private void attachmentDeleteClicked(int position) {
        if (attachmentFyles == null) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = attachmentFyles.get(position);
        App.runThread(new DeleteAttachmentTask(fyleAndStatus));
    }

    private void attachmentLongClicked(int position, View view) {
        if (attachmentFyles == null) {
            return;
        }
        FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus = attachmentFyles.get(position);
        if (attachmentLongClickListener != null) {
            attachmentLongClickListener.attachmentLongClicked(longClickedFyleAndStatus, view, Visibility.VISIBLE, false, attachmentFyles.size() > 1);
        }
    }

    public void setAttachmentLongClickListener(AttachmentLongClickListener attachmentLongClickListener) {
        this.attachmentLongClickListener = attachmentLongClickListener;
    }

    public interface AttachmentLongClickListener {
        void attachmentLongClicked(FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus, View clickedView, Visibility visibility, boolean readOnce, boolean multipleAttachments);
    }


    public class AttachmentViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener, AudioAttachmentServiceBinding.AudioServiceBindableViewHolder {
        final ImageView attachmentImageView;
        final ImageView attachmentStatusIconImageView;
        final ImageView attachmentDeleteImageView;
        final ProgressBar attachmentProgress;
        final Observer<ProgressStatus> progressListener;
        LiveData<ProgressStatus> progressLiveData;
        final TextView attachmentProgressLabel;
        final TextView attachmentFileName;
        final TextView attachmentMimeType;
        final TextView attachmentSize;
        final TextView audioPlayTime;
        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;
        boolean musicFailed;

        AttachmentViewHolder(View itemView) {
            super(itemView);
            attachmentImageView = itemView.findViewById(R.id.attachment_image_view);
            attachmentStatusIconImageView = itemView.findViewById(R.id.attachment_status_icon_image_view);
            attachmentDeleteImageView = itemView.findViewById(R.id.attachment_delete_icon_image_view);
            attachmentProgress = itemView.findViewById(R.id.attachment_progress);
            progressListener = progressStatus -> {
                if (progressStatus instanceof ProgressStatus.InProgress) {
                    float progress = ((ProgressStatus.InProgress) progressStatus).getProgress();
                    if (progress > 0.01f) {
                        attachmentProgress.setProgress((int) (progress * 100));
                        if (attachmentDeleteImageView.getVisibility() != View.GONE) {
                            attachmentStatusIconImageView.setVisibility(View.GONE);
                        }
                    }
                } else if (progressStatus == ProgressStatus.Unknown.INSTANCE) {
                    attachmentProgress.setProgress(0);
                }else if (progressStatus == ProgressStatus.Finished.INSTANCE) {
                    attachmentProgress.setProgress(100);
                }
            };
            progressLiveData = null;
            attachmentProgressLabel = itemView.findViewById(R.id.attachment_progress_label);
            attachmentFileName = itemView.findViewById(R.id.attachment_file_name);
            attachmentMimeType = itemView.findViewById(R.id.attachment_mime_type);
            attachmentSize = itemView.findViewById(R.id.attachment_size);
            audioPlayTime = itemView.findViewById(R.id.audio_play_time);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            attachmentDeleteImageView.setOnClickListener(this);
            fyleAndStatus = null;
            musicFailed = false;
        }

        @Override
        public void onClick(View view) {
            if (attachmentFyles != null) {
                if (view.getId() == R.id.attachment_delete_icon_image_view) {
                    attachmentDeleteClicked(this.getLayoutPosition());
                } else {
                    if (fyleAndStatus != null && fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("audio/") && !musicFailed) {
                        audioAttachmentServiceBinding.playPause(fyleAndStatus, discussionId);
                    } else {
                        attachmentClicked(this.getLayoutPosition());
                    }
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (attachmentFyles != null) {
                attachmentLongClicked(this.getLayoutPosition(), view);
            }
            return true;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void updatePlayTimeMs(AudioAttachmentServiceBinding.AudioInfo audioInfo, long playTimeMs, boolean playing) {
            if (playing) {
                attachmentSize.setVisibility(View.INVISIBLE);
                audioPlayTime.setVisibility(View.VISIBLE);
                if (audioInfo.durationMs == null) {
                    audioPlayTime.setText(AudioAttachmentServiceBinding.timeFromMs(playTimeMs));
                } else {
                    audioPlayTime.setText(AudioAttachmentServiceBinding.timeFromMs(playTimeMs) + "/" + AudioAttachmentServiceBinding.timeFromMs(audioInfo.durationMs));
                }
                attachmentImageView.setImageResource(R.drawable.ic_pause);
            } else {
                attachmentSize.setVisibility(View.VISIBLE);
                audioPlayTime.setVisibility(View.INVISIBLE);
                attachmentImageView.setImageResource(R.drawable.ic_play);
            }
        }

        @Override
        public void bindAudioInfo(AudioAttachmentServiceBinding.AudioInfo audioInfo, MediaPlayerService.AudioOutput audioOutput, float playbackSpeed) {
            if (audioInfo.failed) {
                attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio_failed);
            } else {
                attachmentImageView.setImageResource(R.drawable.ic_play);
            }
        }

        @Override
        public void setFailed(boolean failed) {
            musicFailed = failed;
        }

        @Override
        public void setAudioOutput(MediaPlayerService.AudioOutput audioOutput, boolean somethingPlaying) {
            // nothing to display, only switch the default audio output
            if ((somethingPlaying && (audioOutput == MediaPlayerService.AudioOutput.PHONE)) != (activity.getVolumeControlStream() == AudioManager.STREAM_VOICE_CALL)) {
                activity.setVolumeControlStream((somethingPlaying && (audioOutput == MediaPlayerService.AudioOutput.PHONE)) ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        }

        @Override
        public void setPlaybackSpeed(float playbackSpeed) {
            // nothing to do here
        }

        @Override
        public FyleMessageJoinWithStatusDao.FyleAndStatus getFyleAndStatus() {
            return fyleAndStatus;
        }
    }



    private static class ShowPreviewTask implements Runnable {
        static final Map<FyleMessageJoinWithStatusDao.FyleAndStatus, ShowPreviewTask> runningPreviews = Collections.synchronizedMap(new HashMap<>());

        private final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;
        private final WeakReference<AttachmentViewHolder> holderWeakReference;
        private final int previewPixelSize;
        private volatile boolean interrupt;

        ShowPreviewTask(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, AttachmentViewHolder holder, int previewPixelSize) {
            this.fyleAndStatus = fyleAndStatus;
            this.holderWeakReference = new WeakReference<>(holder);
            this.previewPixelSize = previewPixelSize;
            this.interrupt = false;
        }

        @Override
        public void run() {
            synchronized (runningPreviews) {
                ShowPreviewTask oldTask = runningPreviews.get(fyleAndStatus);
                if (oldTask == null) {
                    runningPreviews.put(fyleAndStatus, this);
                } else {
                    if (fyleAndStatus.fyle.isComplete()) {
                        oldTask.interrupt = true;
                        runningPreviews.put(fyleAndStatus, this);
                    } else {
                        return;
                    }
                }
            }
            final Bitmap bitmap = PreviewUtils.getBitmapPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus, previewPixelSize);
            synchronized (runningPreviews) {
                ShowPreviewTask oldTask = runningPreviews.get(fyleAndStatus);
                if (this.equals(oldTask)) {
                    runningPreviews.remove(fyleAndStatus);
                }
            }
            final AttachmentViewHolder holder = holderWeakReference.get();
            if (interrupt || holder == null || !fyleAndStatus.equals(holder.fyleAndStatus)) {
                return;
            }
            if (bitmap == null) {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageResource(PreviewUtils.getDrawableResourceForMimeType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())));
            } else {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageBitmap(bitmap));
            }
        }
    }
    // endregion

    public static class AttachmentSpaceItemDecoration extends RecyclerView.ItemDecoration {
        final int space;

        public AttachmentSpaceItemDecoration(Context context) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            space = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            if (position != 0) {
                outRect.left = space;
            }
        }
    }
}
