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

package io.olvid.messenger.activities.storage_manager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.MessageAttachmentAdapter;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.PreviewUtilsWithDrawables;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.DeleteAttachmentFromAllMessagesTask;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.services.MediaPlayerService;
import io.olvid.messenger.settings.SettingsActivity;

class AttachmentListAdapter extends RecyclerView.Adapter<AttachmentListAdapter.AttachmentViewHolder> implements Observer<List<FyleMessageJoinWithStatusDao.FyleAndOrigin>> {
    @NonNull private final FragmentActivity activity;
    @NonNull private final AudioAttachmentServiceBinding audioAttachmentServiceBinding;
    @NonNull private final StorageManagerViewModel viewModel;
    @NonNull private final LayoutInflater layoutInflater;
    private final int previewPixelSize;
    private final int selectedColor;
    private final int selectableBackgroundResourceId;
    List<FyleMessageJoinWithStatusDao.FyleAndOrigin> fyleAndOrigins;

    private static final int TYPE_IMAGE = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_AUDIO = 2;
    private static final int TYPE_FILE = 3;


    @SuppressLint("NotifyDataSetChanged")
    AttachmentListAdapter(@NonNull FragmentActivity activity, @NonNull AudioAttachmentServiceBinding audioAttachmentServiceBinding) {
        this.activity = activity;
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding;
        this.viewModel = new ViewModelProvider(activity).get(StorageManagerViewModel.class);
        this.layoutInflater = LayoutInflater.from(activity);
        this.previewPixelSize = (int) (activity.getResources().getDisplayMetrics().density * 64);
        this.selectedColor = ContextCompat.getColor(activity, R.color.blueOverlay);
        TypedValue selectableBackground = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true);
        this.selectableBackgroundResourceId = selectableBackground.resourceId;
        fyleAndOrigins = null;
        setHasStableIds(true);
        viewModel.getSelectedCountLiveData().observe(activity, new Observer<Integer>() {
            int previousSelectedCount = 0;

            @Override
            public void onChanged(Integer selectedCount) {
                if (selectedCount == null) {
                    selectedCount = 0;
                }
                if ((selectedCount == 0) != (previousSelectedCount == 0)) {
                    notifyDataSetChanged();
                }
                previousSelectedCount = selectedCount;
            }
        });
    }

    @Override
    public long getItemId(int position) {
        if (fyleAndOrigins != null) {
            FyleMessageJoinWithStatusDao.FyleAndOrigin fyleAndOrigin = fyleAndOrigins.get(position);
            return fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.messageId + (fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.fyleId << 32);
        }
        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        if (fyleAndOrigins != null) {
            FyleMessageJoinWithStatusDao.FyleAndOrigin fyleAndOrigin = fyleAndOrigins.get(position);
            String mimeType = fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) {
                if (mimeType.startsWith("video/")) {
                    return TYPE_VIDEO;
                } else {
                    return TYPE_IMAGE;
                }
            } else if (mimeType.startsWith("audio/")) {
                return TYPE_AUDIO;
            }
        }
        return TYPE_FILE;
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AttachmentViewHolder(layoutInflater.inflate(R.layout.item_view_storage_manager_file, parent, false), viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        if (fyleAndOrigins == null) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndOrigin fyleAndOrigin = fyleAndOrigins.get(position);
        @NonNull final String mimeType = fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
        holder.fyleAndOrigin = fyleAndOrigin;

        if (PreviewUtils.canGetPreview(fyleAndOrigin.fyleAndStatus.fyle, fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus)) {
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }
            App.runThread(new ShowPreviewTask(fyleAndOrigin.fyleAndStatus, holder, previewPixelSize));
        } else {
            holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (mimeType.startsWith("audio/")) {
                if (holder.musicFailed) {
                    holder.attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio_failed);
                } else {
                    audioAttachmentServiceBinding.loadAudioAttachment(fyleAndOrigin.fyleAndStatus, holder);
                }
            } else {
                holder.attachmentImageView.setImageResource(MessageAttachmentAdapter.getDrawableResourceForMimeType(mimeType));
            }
        }

        if (fyleAndOrigin.fyleAndStatus.fyle.isComplete()) {
            holder.progressBar.setVisibility(View.GONE);
            holder.failedImageView.setVisibility(View.GONE);
        } else if (fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
            holder.progressBar.setVisibility(View.GONE);
            holder.failedImageView.setVisibility(View.VISIBLE);
        } else {
            holder.failedImageView.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress((int) (fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.progress * 100));
        }

        if (Arrays.equals(fyleAndOrigin.message.senderIdentifier, AppSingleton.getBytesCurrentIdentity())) {
            final SpannableString spannableString;
            if (fyleAndOrigin.message.status == Message.STATUS_DRAFT) {
                spannableString = new SpannableString(activity.getString(R.string.text_draft_for_xx, fyleAndOrigin.discussion.title));
            } else {
                spannableString = new SpannableString(activity.getString(R.string.text_sent_to_xx, fyleAndOrigin.discussion.title));
            }
            spannableString.setSpan(new StyleSpan(Typeface.BOLD), spannableString.length() - fyleAndOrigin.discussion.title.length(), spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.senderTextView.setText(spannableString);
        } else {
            String displayName = AppSingleton.getContactCustomDisplayName(fyleAndOrigin.message.senderIdentifier);
            if (displayName != null) {
                SpannableString spannableString = new SpannableString(activity.getString(R.string.text_sent_by_xx, displayName));
                spannableString.setSpan(new StyleSpan(Typeface.BOLD), spannableString.length() - displayName.length(), spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableString.setSpan(new ForegroundColorSpan(InitialView.getTextColor(activity, fyleAndOrigin.message.senderIdentifier, AppSingleton.getContactCustomHue(fyleAndOrigin.message.senderIdentifier))), spannableString.length() - displayName.length(), spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.senderTextView.setText(spannableString);
            } else {
                SpannableString spannableString = new SpannableString(activity.getString(R.string.text_unknown_sender));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.senderTextView.setText(spannableString);
            }
        }

        holder.timestampTextView.setText(DateUtils.formatDateTime(activity, fyleAndOrigin.message.timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH));
        holder.filenameTextView.setText(fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.fileName);
        {
            String sizeAndMime = Formatter.formatShortFileSize(activity, fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.size);
            int sizeLength = sizeAndMime.length();
            sizeAndMime += activity.getString(R.string.text_date_time_separator) + fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
            SpannableString spannableString = new SpannableString(sizeAndMime);
            spannableString.setSpan(new StyleSpan(Typeface.BOLD), 0, sizeLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.sizeAndMimeTextView.setText(spannableString);
        }

        if (viewModel.isSelecting()) {
            holder.goButton.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.GONE);
            holder.checkBox.setVisibility(View.VISIBLE);
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.goButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.VISIBLE);
        }
        if (viewModel.isSelected(fyleAndOrigin.fyleAndStatus)) {
            holder.itemView.setBackgroundColor(selectedColor);
            holder.checkBox.setChecked(true);
        } else {
            holder.itemView.setBackgroundResource(selectableBackgroundResourceId);
            holder.checkBox.setChecked(false);
        }
    }

    @Override
    public void onViewRecycled(@NonNull AttachmentViewHolder holder) {
        super.onViewRecycled(holder);
        holder.fyleAndOrigin = null;
        holder.attachmentImageView.setImageDrawable(null);
        holder.musicFailed = false;
    }

    @Override
    public int getItemCount() {
        if (fyleAndOrigins == null) {
            return 0;
        }
        return fyleAndOrigins.size();
    }

    @Override
    public void onChanged(List<FyleMessageJoinWithStatusDao.FyleAndOrigin> fyleAndOrigins) {
        final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {

            @Override
            public int getOldListSize() {
                return AttachmentListAdapter.this.fyleAndOrigins == null ? 0 : AttachmentListAdapter.this.fyleAndOrigins.size();
            }

            @Override
            public int getNewListSize() {
                return fyleAndOrigins == null ? 0 : fyleAndOrigins.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                FyleMessageJoinWithStatusDao.FyleAndOrigin oldItem = AttachmentListAdapter.this.fyleAndOrigins.get(oldItemPosition);
                FyleMessageJoinWithStatusDao.FyleAndOrigin newItem = fyleAndOrigins.get(newItemPosition);
                return oldItem.message.id == newItem.message.id
                        && oldItem.fyleAndStatus.fyle.id == newItem.fyleAndStatus.fyle.id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FyleMessageJoinWithStatus oldStatus = AttachmentListAdapter.this.fyleAndOrigins.get(oldItemPosition).fyleAndStatus.fyleMessageJoinWithStatus;
                FyleMessageJoinWithStatus newStatus = fyleAndOrigins.get(newItemPosition).fyleAndStatus.fyleMessageJoinWithStatus;

                return oldStatus.status == newStatus.status
                        && Objects.equals(oldStatus.filePath, newStatus.filePath)
                        && oldStatus.progress == newStatus.progress
                        && Objects.equals(oldStatus.imageResolution, newStatus.imageResolution)
                        && (oldStatus.miniPreview == null) == (newStatus.miniPreview == null);
            }
        });
        this.fyleAndOrigins = fyleAndOrigins;
        result.dispatchUpdatesTo(this);
    }




    public class AttachmentViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener, AudioAttachmentServiceBinding.AudioServiceBindableViewHolder {
        final int viewType;
        final ImageView attachmentImageView;
        final ImageView attachmentOverlay;
        final ProgressBar progressBar;
        final ImageView failedImageView;
        final TextView senderTextView;
        final TextView timestampTextView;
        final TextView filenameTextView;
        final TextView sizeAndMimeTextView;
        final ImageView deleteButton;
        final ImageView goButton;
        final CheckBox checkBox;

        FyleMessageJoinWithStatusDao.FyleAndOrigin fyleAndOrigin;
        boolean musicFailed;


        public AttachmentViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;

            attachmentImageView = itemView.findViewById(R.id.attachment_image_view);
            attachmentImageView.setClipToOutline(true);
            attachmentOverlay = itemView.findViewById(R.id.attachment_overlay);
            progressBar = itemView.findViewById(R.id.attachment_progress);
            failedImageView = itemView.findViewById(R.id.attachment_failed);
            senderTextView = itemView.findViewById(R.id.attachment_sender);
            timestampTextView = itemView.findViewById(R.id.attachment_timestamp);
            filenameTextView = itemView.findViewById(R.id.attachment_file_name);
            sizeAndMimeTextView = itemView.findViewById(R.id.attachment_size_and_mime);
            deleteButton = itemView.findViewById(R.id.button_delete);
            goButton = itemView.findViewById(R.id.button_go);
            checkBox = itemView.findViewById(R.id.checkbox);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            deleteButton.setOnClickListener(this);
            goButton.setOnClickListener(this);

            if (viewType == TYPE_VIDEO) {
                attachmentOverlay.setVisibility(View.VISIBLE);
                attachmentOverlay.setImageResource(R.drawable.overlay_video_small);
            } else {
                attachmentOverlay.setVisibility(View.GONE);
            }
        }


        @Override
        public void onClick(View view) {
            if (viewModel.isSelecting()) {
                onLongClick(view);
            } else if (view.getId() == R.id.button_delete) {
                App.runThread(() -> {
                    final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndOrigin.fyleAndStatus;
                    Long count = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().countMessageForFyle(fyleAndStatus.fyle.id);
                    if (count != null && count > 1) {
                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_delete_attachment)
                                .setMessage(activity.getString(R.string.dialog_message_delete_attachment_for_many_messages, count.intValue(), fyleAndStatus.fyleMessageJoinWithStatus.fileName))
                                .setNeutralButton(activity.getString(R.string.button_label_delete_all, count.intValue()), (dialog, which) -> App.runThread(new DeleteAttachmentFromAllMessagesTask(fyleAndStatus.fyle.id)))
                                .setPositiveButton(R.string.button_label_delete_one, (dialog, which) -> App.runThread(new DeleteAttachmentTask(fyleAndStatus)))
                                .setNegativeButton(R.string.button_label_cancel, null);
                        activity.runOnUiThread(() -> builder.create().show());
                    } else {
                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_delete_attachment)
                                .setMessage(activity.getString(R.string.dialog_message_delete_attachment_gallery, fyleAndStatus.fyleMessageJoinWithStatus.fileName))
                                .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteAttachmentTask(fyleAndStatus)))
                                .setNegativeButton(R.string.button_label_cancel, null);
                        activity.runOnUiThread(() -> builder.create().show());
                    }
                });
            } else if (view.getId() == R.id.button_go) {
                if (fyleAndOrigin != null) {
                    Intent intent = new Intent(activity, DiscussionActivity.class);
                    intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, fyleAndOrigin.discussion.id);
                    intent.putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, fyleAndOrigin.message.id);
                    activity.startActivity(intent);
                }
            } else if (fyleAndOrigin != null) {
                if (viewType == TYPE_AUDIO && !musicFailed) {
                    audioAttachmentServiceBinding.playPause(fyleAndOrigin.fyleAndStatus, null);
                } else {
                    if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.mimeType, fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.fileName))
                            && SettingsActivity.useInternalImageViewer()
                            && (!fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("video/") || !"".equals(fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.imageResolution))) {
                        if (AppSingleton.getBytesCurrentIdentity() != null) {
                            String sortOrder;
                            switch (viewModel.getCurrentSortOrder().sortKey) {

                                case SIZE:
                                    sortOrder = "size";
                                    break;
                                case NAME:
                                    sortOrder = "name";
                                    break;
                                case DATE:
                                default:
                                    sortOrder = null;
                                    break;
                            }
                            App.openOwnedIdentityGalleryActivity(activity, AppSingleton.getBytesCurrentIdentity(), sortOrder, fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndOrigin.fyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                        }
                    } else {
                        App.openFyleInExternalViewer(activity, fyleAndOrigin.fyleAndStatus, null);
                    }
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (fyleAndOrigin != null) {
                viewModel.selectFyle(fyleAndOrigin.fyleAndStatus);
                notifyItemChanged(getAbsoluteAdapterPosition());
            }
            return true;
        }


        @Override
        public void updatePlayTimeMs(AudioAttachmentServiceBinding.AudioInfo audioInfo, long playTimeMs, boolean playing) {
            if (playing) {
                attachmentImageView.setImageResource(R.drawable.ic_pause);
            } else {
                attachmentImageView.setImageResource(R.drawable.ic_play);
            }
        }

        @Override
        public void bindAudioInfo(AudioAttachmentServiceBinding.AudioInfo audioInfo, MediaPlayerService.AudioOutput audioOutput) {
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
        public FyleMessageJoinWithStatusDao.FyleAndStatus getFyleAndStatus() {
            if (fyleAndOrigin != null) {
                return fyleAndOrigin.fyleAndStatus;
            }
            return null;
        }
    }


    private static class ShowPreviewTask implements Runnable {
        static final Map<FyleMessageJoinWithStatusDao.FyleAndStatus, ShowPreviewTask> runningPreviews = Collections.synchronizedMap(new HashMap<>());

        private final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;
        private final WeakReference<AttachmentViewHolder> holderWeakReference;
        private volatile boolean interrupt;
        private final int previewPixelSize;

        ShowPreviewTask(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, AttachmentViewHolder holder, int previewPixelSize) {
            this.fyleAndStatus = fyleAndStatus;
            this.holderWeakReference = new WeakReference<>(holder);
            this.interrupt = false;
            this.previewPixelSize = previewPixelSize;
        }

        @Override
        public void run() {
            synchronized (runningPreviews) {
                ShowPreviewTask oldTask = runningPreviews.get(fyleAndStatus);
                if (oldTask == null) {
                    runningPreviews.put(fyleAndStatus, this);
                } else {
                    if (fyleAndStatus.fyle.isComplete()) {
                        // only interrupt the current running task if it is for the same view holder
                        AttachmentViewHolder oldHolder = oldTask.holderWeakReference.get();
                        AttachmentViewHolder holder = holderWeakReference.get();
                        if (holder == oldHolder) {
                            oldTask.interrupt = true;
                            runningPreviews.put(fyleAndStatus, this);
                        }
                    } else {
                        return;
                    }
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("image/")) {
                showBitmapPreview();
            } else {
                showDrawablePreview();
            }
        }

        private void showBitmapPreview() {
            final Bitmap bitmap = PreviewUtils.getBitmapPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus, previewPixelSize);
            synchronized (runningPreviews) {
                ShowPreviewTask oldTask = runningPreviews.get(fyleAndStatus);
                if (this.equals(oldTask)) {
                    runningPreviews.remove(fyleAndStatus);
                }
            }
            final AttachmentViewHolder holder = holderWeakReference.get();
            if (interrupt || holder == null || !fyleAndStatus.equals(holder.fyleAndOrigin.fyleAndStatus)) {
                return;
            }
            if (bitmap == null) {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageResource(MessageAttachmentAdapter.getDrawableResourceForMimeType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())));
            } else {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageBitmap(bitmap));
            }
        }


        @RequiresApi(api = Build.VERSION_CODES.P)
        private void showDrawablePreview() {
            final Drawable drawable;
            try {
                drawable = PreviewUtilsWithDrawables.getDrawablePreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus, previewPixelSize);
            } catch (PreviewUtils.DrawablePreviewException e) {
                showBitmapPreview();
                return;
            }
            synchronized (runningPreviews) {
                ShowPreviewTask oldTask = runningPreviews.get(fyleAndStatus);
                if (this.equals(oldTask)) {
                    runningPreviews.remove(fyleAndStatus);
                }
            }
            final AttachmentViewHolder holder = holderWeakReference.get();
            if (interrupt || holder == null || !fyleAndStatus.equals(holder.fyleAndOrigin.fyleAndStatus)) {
                return;
            }
            if (drawable == null) {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageResource(MessageAttachmentAdapter.getDrawableResourceForMimeType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())));
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (drawable instanceof AnimatedImageDrawable) {
                        ((AnimatedImageDrawable) drawable).start();
                    }
                    holder.attachmentImageView.setImageDrawable(drawable);
                });
            }
        }
    }
}
