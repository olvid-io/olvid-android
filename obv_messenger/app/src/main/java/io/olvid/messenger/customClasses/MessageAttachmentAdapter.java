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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked;
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask;
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;
import io.olvid.messenger.services.MediaPlayerService;
import io.olvid.messenger.settings.SettingsActivity;


public class MessageAttachmentAdapter extends RecyclerView.Adapter<MessageAttachmentAdapter.AttachmentViewHolder> implements Observer<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> {
    @NonNull private final Activity activity;
    @Nullable private final Long discussionId;
    @NonNull private final AudioAttachmentServiceBinding audioAttachmentServiceBinding;

    private final LayoutInflater inflater;
    private final GridLayoutManager.SpanSizeLookup spanSizeLookup;
    private static final int filePreviewPixelSize;
    private static final int largeImagePreviewPixelSize;
    private static final int smallImagePreviewPixelSize;
    static {
        DisplayMetrics metrics = App.getContext().getResources().getDisplayMetrics();
        filePreviewPixelSize = App.getContext().getResources().getDimensionPixelSize(R.dimen.attachment_small_preview_size);
        largeImagePreviewPixelSize = metrics.widthPixels - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, metrics);
        smallImagePreviewPixelSize = (metrics.widthPixels - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 84, metrics))/2;
    }

    // custom click listeners are used to handle clicks on location messages preview
    public void setCustomOnLongClickListener(View.OnLongClickListener customOnLongClickListener) {
        this.customOnLongClickListener = customOnLongClickListener;
    }
    // custom click listener is only called if attachment had been downloaded and is visible (else it follow normal attachment click process)
    // used for location messages: replace open preview by open in integrated map if selected an integration
    public void setCustomOnClickListener(View.OnClickListener customOnClickListener) {
        this.customOnClickListener = customOnClickListener;
    }


    public enum Visibility {
        VISIBLE,
        HIDDEN,
    }

    private FyleMessageJoinWithStatusDao.FyleAndStatus[] attachmentFyles;
    private Long visibilityDuration = null;
    private boolean readOnce = false;
    private boolean openOnClick = true;
    private Visibility visibility = Visibility.VISIBLE;
    private int imageAndVideoCount = 0;
    private AttachmentLongClickListener attachmentLongClickListener;
    private NoWipeListener noWipeListener;
    private BlockMessageSwipeListener blockMessageSwipeListener;
    private final AttachmentSpaceItemDecoration itemDecoration;

    private View.OnClickListener customOnClickListener = null;
    private View.OnLongClickListener customOnLongClickListener = null;

    private static final int TYPE_BIG_IMAGE = 0;
    private static final int TYPE_WIDE_IMAGE = 1;
    private static final int TYPE_SMALL_IMAGE = 2;
    private static final int TYPE_ATTACHMENT = 3;
    private static final int TYPE_AUDIO = 4;

    static final int STATUS_CHANGE_MASK = 1;
    static final int PROGRESS_CHANGE_MASK = 2;
    static final int POSITION_CHANGE_MASK = 4;
    static final int RESOLUTION_CHANGE_MASK = 8;
    static final int MINI_PREVIEW_CHANGE_MASK = 16;
    static final int WAS_OPENED_CHANGE_MASK = 32;
    static final int RECEPTION_STATUS_CHANGE_MASK = 64;

    public MessageAttachmentAdapter(@NonNull Activity activity, @NonNull AudioAttachmentServiceBinding audioAttachmentServiceBinding, @Nullable Long discussionId) {
        this.activity = activity;
        this.discussionId = discussionId;
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding;

        inflater = LayoutInflater.from(activity);
        setHasStableIds(true);
        spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position >= imageAndVideoCount) {
                    return 2;
                }
                if ((imageAndVideoCount <= 2) || (position == (imageAndVideoCount-1) && ((imageAndVideoCount & 1) != 0))) {
                    return 2;
                }
                return 1;
            }
        };
        itemDecoration = new AttachmentSpaceItemDecoration(activity);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setHidden(Long visibilityDuration, boolean readOnce, boolean openOnClick) {
        this.visibility = Visibility.HIDDEN;
        this.visibilityDuration = visibilityDuration;
        this.readOnce = readOnce;
        this.openOnClick = openOnClick;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setVisible(boolean readOnce) {
        this.visibility = Visibility.VISIBLE;
        this.visibilityDuration = null;
        this.readOnce = readOnce;
        notifyDataSetChanged();
    }

    public void setAttachmentLongClickListener(AttachmentLongClickListener attachmentLongClickListener) {
        this.attachmentLongClickListener = attachmentLongClickListener;
    }

    public void setNoWipeListener(NoWipeListener noWipeListener) {
        this.noWipeListener = noWipeListener;
    }

    public void setBlockMessageSwipeListener(BlockMessageSwipeListener blockMessageSwipeListener) {
        this.blockMessageSwipeListener = blockMessageSwipeListener;
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return spanSizeLookup;
    }

    public int getColumnCount() {
        return 2;
    }

    public AttachmentSpaceItemDecoration getItemDecoration() {
        return itemDecoration;
    }

    @Override
    public long getItemId(int position) {
        if (attachmentFyles != null) {
            return attachmentFyles[position].fyle.id;
        }
        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= imageAndVideoCount) {
            if (attachmentFyles[position].fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("audio/")) {
                return TYPE_AUDIO;
            }
            return TYPE_ATTACHMENT;
        }
        if (imageAndVideoCount == 1) {
            return TYPE_BIG_IMAGE;
        }
        if (imageAndVideoCount == 2 || (position == (imageAndVideoCount-1) && ((imageAndVideoCount & 1) != 0))) {
            return TYPE_WIDE_IMAGE;
        }
        return TYPE_SMALL_IMAGE;
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case TYPE_BIG_IMAGE:
            case TYPE_SMALL_IMAGE:
                view = inflater.inflate(R.layout.item_view_attachment_image, parent, false);
                break;
            case TYPE_WIDE_IMAGE:
                view = inflater.inflate(R.layout.item_view_attachment_image_wide, parent, false);
                break;
            case TYPE_AUDIO:
                view = inflater.inflate(R.layout.item_view_attachment_audio, parent, false);
                break;
            case TYPE_ATTACHMENT:
            default:
                view = inflater.inflate(R.layout.item_view_attachment_file, parent, false);
                break;
        }
        return new AttachmentViewHolder(view, viewType);
    }


    @Override
    public void onViewRecycled(@NonNull AttachmentViewHolder holder) {
        super.onViewRecycled(holder);
        holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.attachmentImageView.setImageDrawable(null);
        holder.musicFailed = false;
        holder.audioInfoBound = false;
        holder.unregisterProgressNotifications();

        if (holder.type == TYPE_BIG_IMAGE || holder.type == TYPE_SMALL_IMAGE) {
            ConstraintSet cloned = new ConstraintSet();
            cloned.clone(holder.rootView);
            cloned.setDimensionRatio(R.id.attachment_image_view, "1");
            cloned.applyTo(holder.rootView);
        } else if (holder.type == TYPE_WIDE_IMAGE) {
            ConstraintSet cloned = new ConstraintSet();
            cloned.clone(holder.rootView);
            cloned.setDimensionRatio(R.id.attachment_image_view, "2");
            cloned.applyTo(holder.rootView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        Logger.e("The no-payload onBindViewHolder should never get called!");
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (attachmentFyles == null) {
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

        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = attachmentFyles[position];
        final String mimeType = fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
        holder.fyleAndStatus = fyleAndStatus;

        if ((changesMask & (STATUS_CHANGE_MASK | MINI_PREVIEW_CHANGE_MASK | RECEPTION_STATUS_CHANGE_MASK)) != 0) {
            if (fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UPLOADING) {
                holder.registerProgressNotifications(true);
            } else if (fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DOWNLOADING) {
                holder.registerProgressNotifications(false);
            } else {
                holder.unregisterProgressNotifications();
            }

            switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                    holder.hiddenContentGroup.setVisibility(View.GONE);
                    holder.attachmentProgress.setProgress((int) (fyleAndStatus.fyleMessageJoinWithStatus.progress * 100));
                    holder.attachmentProgress.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setText(R.string.label_upload);
                    if (fyleAndStatus.fyleMessageJoinWithStatus.progress < .01f) {
                        final AnimatedVectorDrawableCompat zeroProgressSpinner = AnimatedVectorDrawableCompat.create(activity, R.drawable.file_progress_zero_spinner);
                        if (zeroProgressSpinner != null) {
                            zeroProgressSpinner.start();
                            holder.attachmentStatusIconImageView.setImageDrawable(zeroProgressSpinner);
                            holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                        } else {
                            holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                        }
                    } else {
                        holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                    }
                    break;
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                    holder.hiddenContentGroup.setVisibility(View.GONE);
                    holder.attachmentProgress.setProgress((int) (fyleAndStatus.fyleMessageJoinWithStatus.progress * 100));
                    holder.attachmentProgress.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setText(R.string.label_download);
                    if (fyleAndStatus.fyleMessageJoinWithStatus.progress < .01f) {
                        final AnimatedVectorDrawableCompat zeroProgressSpinner = AnimatedVectorDrawableCompat.create(activity, R.drawable.file_progress_zero_spinner);
                        if (zeroProgressSpinner != null) {
                            zeroProgressSpinner.start();
                            holder.attachmentStatusIconImageView.setImageDrawable(zeroProgressSpinner);
                            holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                        } else {
                            holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                        }
                    } else {
                        holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                    }
                    break;
                case FyleMessageJoinWithStatus.STATUS_COPYING:
                    holder.hiddenContentGroup.setVisibility(View.GONE);
                    holder.attachmentProgress.setProgress((int) (fyleAndStatus.fyleMessageJoinWithStatus.progress * 100));
                    holder.attachmentProgress.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setText(R.string.label_copy);
                    if (fyleAndStatus.fyleMessageJoinWithStatus.progress < .01f) {
                        final AnimatedVectorDrawableCompat zeroProgressSpinner = AnimatedVectorDrawableCompat.create(activity, R.drawable.file_progress_zero_spinner);
                        if (zeroProgressSpinner != null) {
                            zeroProgressSpinner.start();
                            holder.attachmentStatusIconImageView.setImageDrawable(zeroProgressSpinner);
                            holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                        } else {
                            holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                        }
                    } else {
                        holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                    }
                    break;
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                    holder.hiddenContentGroup.setVisibility(View.GONE);
                    holder.attachmentProgress.setVisibility(View.GONE);
                    holder.attachmentProgressLabel.setVisibility(View.GONE);
                    holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                    holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_file_download);
                    break;
                case FyleMessageJoinWithStatus.STATUS_FAILED:
                    holder.hiddenContentGroup.setVisibility(View.GONE);
                    holder.attachmentProgress.setVisibility(View.GONE);
                    holder.attachmentProgressLabel.setVisibility(View.VISIBLE);
                    holder.attachmentProgressLabel.setText(R.string.label_no_longer_available);
                    holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                    //noinspection DuplicateExpressions
                    if (holder.type == TYPE_BIG_IMAGE || holder.type == TYPE_SMALL_IMAGE || holder.type == TYPE_WIDE_IMAGE) {
                        holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_failed_for_image);
                    } else {
                        holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_failed);
                    }
                    break;
                case FyleMessageJoinWithStatus.STATUS_COMPLETE:
                case FyleMessageJoinWithStatus.STATUS_DRAFT:
                default:
                    switch (visibility) {
                        case VISIBLE:
                            holder.hiddenContentGroup.setVisibility(View.GONE);
                            break;
                        case HIDDEN:
                            holder.hiddenContentGroup.setVisibility(View.VISIBLE);
                            if (readOnce) {
                                holder.hiddenContentTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_burn, 0, 0, 0);
                                holder.hiddenContentTextView.setTextColor(ContextCompat.getColor(activity, R.color.red));
                                if (visibilityDuration == null) {
                                    holder.hiddenContentTextView.setText(R.string.text_visible_once);
                                } else if (visibilityDuration < 60L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_s_once_compact, visibilityDuration));
                                } else if (visibilityDuration < 3_600L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_m_once_compact, visibilityDuration / 60L));
                                } else if (visibilityDuration < 86_400L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_h_once_compact, visibilityDuration / 3_600L));
                                } else if (visibilityDuration < 31_536_000L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_d_once_compact, visibilityDuration / 86_400L));
                                } else {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_y_once_compact, visibilityDuration / 31_536_000L));
                                }
                            } else {
                                holder.hiddenContentTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_eye, 0, 0, 0);
                                holder.hiddenContentTextView.setTextColor(ContextCompat.getColor(activity, R.color.orange));
                                if (visibilityDuration == null) {
                                    holder.hiddenContentTextView.setText(null); // this should never happen
                                } else if (visibilityDuration < 60L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_s, visibilityDuration));
                                } else if (visibilityDuration < 3_600L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_m, visibilityDuration / 60L));
                                } else if (visibilityDuration < 86_400L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_h, visibilityDuration / 3_600L));
                                } else if (visibilityDuration < 31_536_000L) {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_d, visibilityDuration / 86_400L));
                                } else {
                                    holder.hiddenContentTextView.setText(activity.getString(R.string.text_visible_timer_y, visibilityDuration / 31_536_000L));
                                }
                            }
                            break;
                    }
                    holder.attachmentProgress.setVisibility(View.GONE);
                    holder.attachmentProgressLabel.setVisibility(View.GONE);
                    switch (fyleAndStatus.fyleMessageJoinWithStatus.receptionStatus) {
                        case FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED_AND_READ:
                            holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                            //noinspection DuplicateExpressions
                            if (holder.type == TYPE_BIG_IMAGE || holder.type == TYPE_SMALL_IMAGE || holder.type == TYPE_WIDE_IMAGE) {
                                holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_read_for_image);
                            } else {
                                holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_read);
                            }
                            break;
                        case FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED:
                            holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                            //noinspection DuplicateExpressions
                            if (holder.type == TYPE_BIG_IMAGE || holder.type == TYPE_SMALL_IMAGE || holder.type == TYPE_WIDE_IMAGE) {
                                holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_delivered_for_image);
                            } else {
                                holder.attachmentStatusIconImageView.setImageResource(R.drawable.ic_attachment_status_delivered);
                            }
                            break;
                        case FyleMessageJoinWithStatus.RECEPTION_STATUS_NONE:
                        default:
                            holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                            break;
                    }
                    break;
            }
            switch (visibility) {
                case VISIBLE:
                    if (PreviewUtils.canGetPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus)) {
                        int previewSize;
                        switch (holder.type) {
                            case TYPE_BIG_IMAGE:
                            case TYPE_WIDE_IMAGE:
                                previewSize = largeImagePreviewPixelSize;
                                break;
                            case TYPE_SMALL_IMAGE:
                                previewSize = smallImagePreviewPixelSize;
                                break;
                            case TYPE_ATTACHMENT:
                            case TYPE_AUDIO:
                            default:
                                previewSize = filePreviewPixelSize;
                                break;
                        }
                        App.runThread(new ShowPreviewTask(fyleAndStatus, holder, previewSize));
                    } else {
                        if (holder.type == TYPE_ATTACHMENT) {
                            holder.attachmentImageView.setImageResource(getDrawableResourceForMimeType(mimeType));
                        } else if (holder.type == TYPE_AUDIO) {
                            if (!holder.audioInfoBound) {
                                holder.attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio);
                            }
                        } else {
                            holder.attachmentImageView.setImageDrawable(null);
                        }
                    }
                    break;
                case HIDDEN:
                    holder.attachmentImageView.setImageResource(R.drawable.ic_incognito);
                    break;
            }

            // display the audio player if needed
            if (holder.type == TYPE_AUDIO && !holder.audioInfoBound) {
                holder.attachmentMimeType.setVisibility(View.VISIBLE);
                holder.attachmentSize.setVisibility(View.VISIBLE);
                holder.audioPlayerGroup.setVisibility(View.GONE);
                holder.speakerOutputImageView.setVisibility(View.GONE);

                if (visibility == Visibility.VISIBLE
                        && fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COMPLETE) {
                    if (holder.musicFailed) {
                        holder.attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio_failed);
                    } else {
                        audioAttachmentServiceBinding.loadAudioAttachment(fyleAndStatus, holder);
                        holder.attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio);
                    }
                }
            }
        } else if ((changesMask & PROGRESS_CHANGE_MASK) != 0) {
            holder.attachmentProgress.setProgress((int) (fyleAndStatus.fyleMessageJoinWithStatus.progress * 100));
            if (fyleAndStatus.fyleMessageJoinWithStatus.progress < .01f) {
                if (holder.attachmentStatusIconImageView.getVisibility() == View.GONE) {
                    final AnimatedVectorDrawableCompat zeroProgressSpinner = AnimatedVectorDrawableCompat.create(activity, R.drawable.file_progress_zero_spinner);
                    if (zeroProgressSpinner != null) {
                        zeroProgressSpinner.start();
                        holder.attachmentStatusIconImageView.setImageDrawable(zeroProgressSpinner);
                        holder.attachmentStatusIconImageView.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                if (holder.attachmentStatusIconImageView.getVisibility() == View.VISIBLE) {
                    holder.attachmentStatusIconImageView.setVisibility(View.GONE);
                }
            }

            if (visibility == Visibility.VISIBLE
                    && PreviewUtils.canGetPreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus)) {
                int previewSize;
                switch (holder.type) {
                    case TYPE_BIG_IMAGE:
                    case TYPE_WIDE_IMAGE:
                        previewSize = largeImagePreviewPixelSize;
                        break;
                    case TYPE_SMALL_IMAGE:
                        previewSize = smallImagePreviewPixelSize;
                        break;
                    case TYPE_ATTACHMENT:
                    case TYPE_AUDIO:
                    default:
                        previewSize = filePreviewPixelSize;
                        break;
                }
                App.runThread(new ShowPreviewTask(fyleAndStatus, holder, previewSize));
            }
        }

        if ((changesMask & RESOLUTION_CHANGE_MASK) != 0) {
            try {
                PreviewUtils.ImageResolution imageResolution = new PreviewUtils.ImageResolution(fyleAndStatus.fyleMessageJoinWithStatus.imageResolution);
                if (imageResolution.kind == PreviewUtils.ImageResolution.KIND.ANIMATED) {
                    if (imageResolution.height < imageResolution.width) {
                        ConstraintSet relaxed = new ConstraintSet();
                        relaxed.clone(holder.rootView);
                        relaxed.setDimensionRatio(R.id.attachment_image_view, Float.toString((float) imageResolution.width / imageResolution.height));
                        relaxed.applyTo(holder.rootView);
                    } else if (holder.type == TYPE_WIDE_IMAGE) {
                        ConstraintSet relaxed = new ConstraintSet();
                        relaxed.clone(holder.rootView);
                        relaxed.setDimensionRatio(R.id.attachment_image_view, "1");
                        relaxed.applyTo(holder.rootView);
                    }
                    holder.attachmentImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            } catch (Exception e) {
                holder.attachmentImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }

        if ((changesMask & POSITION_CHANGE_MASK) != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                switch (holder.type) {
                    case TYPE_SMALL_IMAGE: {
                        if (position == 0) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_top_left_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_top_left_rounded));
                        } else if (position == 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_top_right_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_top_right_rounded));
                        } else if (position == attachmentFyles.length - 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_bottom_right_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_bottom_right_rounded));
                        } else if (position == attachmentFyles.length - 2 && (position & 1) == 0) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_bottom_left_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_bottom_left_rounded));
                        } else {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment));
                        }
                        break;
                    }
                    case TYPE_WIDE_IMAGE: {
                        if (position == 0) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_top_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_top_rounded));
                        } else if (position == attachmentFyles.length - 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_bottom_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_bottom_rounded));
                        } else {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment));
                        }
                        break;
                    }
                    case TYPE_BIG_IMAGE: {
                        if (attachmentFyles.length == 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_all_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_all_rounded));
                        } else {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_top_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_top_rounded));
                        }
                        break;
                    }
                    case TYPE_ATTACHMENT:
                    case TYPE_AUDIO: {
                        if (attachmentFyles.length == 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_all_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_all_rounded));
                        } else if (position == 0) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_top_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_top_rounded));
                        } else if (position == attachmentFyles.length - 1) {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border_bottom_rounded));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_bottom_rounded));
                        } else {
                            holder.rootView.setForeground(ContextCompat.getDrawable(activity, R.drawable.background_attachment_border));
                            holder.rootView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_attachment));
                        }
                    }
                }
            }
        }

        if ((changesMask & WAS_OPENED_CHANGE_MASK) != 0) {
            if (holder.type == TYPE_AUDIO) {
                if (fyleAndStatus.fyleMessageJoinWithStatus.wasOpened || visibility == Visibility.HIDDEN) {
                    holder.audioAttachmentNotPlayed.setVisibility(View.GONE);
                } else {
                    holder.audioAttachmentNotPlayed.setVisibility(View.VISIBLE);
                }
            }
        }

        if (changesMask == -1) {
            switch (holder.type) {
                case TYPE_AUDIO: {
                    if (holder.audioInfoBound) {
                        break;
                    }
                    // we deliberately fall through if audio info is not yet bound
                }
                //noinspection fallthrough
                case TYPE_ATTACHMENT: {
                    if (visibility == Visibility.HIDDEN) {
                        holder.attachmentFileName.setMaxLines(1);
                        holder.attachmentFileName.setText(R.string.text_filename_hidden);
                        holder.attachmentFileName.setTypeface(holder.attachmentFileName.getTypeface(), Typeface.ITALIC);
                    } else {
                        holder.attachmentFileName.setMaxLines(2);
                        holder.attachmentFileName.setText(fyleAndStatus.fyleMessageJoinWithStatus.fileName);
                        holder.attachmentFileName.setTypeface(holder.attachmentFileName.getTypeface(), Typeface.NORMAL);
                    }
                    if (mimeType != null) {
                        holder.attachmentMimeType.setText(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                        holder.attachmentMimeType.setVisibility(View.VISIBLE);
                    } else {
                        holder.attachmentMimeType.setVisibility(View.GONE);
                    }
                    holder.attachmentSize.setText(Formatter.formatShortFileSize(App.getContext(), fyleAndStatus.fyleMessageJoinWithStatus.size));
                    holder.attachmentSize.setVisibility(View.VISIBLE);
                    break;
                }
                case TYPE_BIG_IMAGE:
                case TYPE_SMALL_IMAGE:
                case TYPE_WIDE_IMAGE: {
                    if (mimeType.startsWith("video/")) {
                        holder.attachmentOverlay.setVisibility(View.VISIBLE);
                        holder.attachmentOverlay.setImageResource(R.drawable.overlay_video);
                    } else {
                        holder.attachmentOverlay.setVisibility(View.GONE);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        if (attachmentFyles != null) {
            return attachmentFyles.length;
        }
        return 0;
    }


    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onChanged(List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses) {
        if (fyleAndStatuses == null) {
            this.attachmentFyles = new FyleMessageJoinWithStatusDao.FyleAndStatus[0];
            notifyDataSetChanged();
        } else {
            final FyleMessageJoinWithStatusDao.FyleAndStatus[] orderedFyles = new FyleMessageJoinWithStatusDao.FyleAndStatus[fyleAndStatuses.size()];
            imageAndVideoCount = 0;
            int otherCount = 0;
            for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())) {
                    orderedFyles[imageAndVideoCount] = fyleAndStatus;
                    imageAndVideoCount++;
                } else {
                    orderedFyles[orderedFyles.length - 1 - otherCount] = fyleAndStatus;
                    otherCount++;
                }
            }
            for (int i = 0; i < otherCount / 2; i++) {
                FyleMessageJoinWithStatusDao.FyleAndStatus tmp = orderedFyles[imageAndVideoCount + i];
                orderedFyles[imageAndVideoCount + i] = orderedFyles[orderedFyles.length - 1 - i];
                orderedFyles[orderedFyles.length - 1 - i] = tmp;
            }
            if (this.attachmentFyles != null) {
                final FyleMessageJoinWithStatusDao.FyleAndStatus[] finalAttachmentFyles = this.attachmentFyles;
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    final int[] payloadCache = new int[orderedFyles.length];
                    final boolean[] payloadComputed = new boolean[payloadCache.length];

                    final FyleMessageJoinWithStatusDao.FyleAndStatus[] oldArray = finalAttachmentFyles;
                    final FyleMessageJoinWithStatusDao.FyleAndStatus[] newArray = orderedFyles;

                    @Override
                    public int getOldListSize() {
                        return oldArray.length;
                    }

                    @Override
                    public int getNewListSize() {
                        return newArray.length;
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return oldArray[oldItemPosition].fyle.id == newArray[newItemPosition].fyle.id;
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

                        FyleMessageJoinWithStatusDao.FyleAndStatus oldItem = oldArray[oldItemPosition];
                        FyleMessageJoinWithStatusDao.FyleAndStatus newItem = newArray[newItemPosition];

                        int changesMask = 0;
                        if (oldItem.fyleMessageJoinWithStatus.status != newItem.fyleMessageJoinWithStatus.status) {
                            changesMask |= STATUS_CHANGE_MASK;
                        }

                        if (oldItem.fyleMessageJoinWithStatus.progress != newItem.fyleMessageJoinWithStatus.progress) {
                            changesMask |= PROGRESS_CHANGE_MASK;
                        }

                        if (oldArray.length != newArray.length || oldItemPosition != newItemPosition) {
                            changesMask |= POSITION_CHANGE_MASK;
                        }


                        if (!Objects.equals(oldItem.fyleMessageJoinWithStatus.imageResolution, newItem.fyleMessageJoinWithStatus.imageResolution)) {
                            changesMask |= RESOLUTION_CHANGE_MASK;
                        }

                        if ((oldItem.fyleMessageJoinWithStatus.miniPreview == null) ^ (newItem.fyleMessageJoinWithStatus.miniPreview == null)) {
                            changesMask |= MINI_PREVIEW_CHANGE_MASK;
                        }

                        if (oldItem.fyleMessageJoinWithStatus.wasOpened ^ newItem.fyleMessageJoinWithStatus.wasOpened) {
                            changesMask |= WAS_OPENED_CHANGE_MASK;
                        }

                        if (oldItem.fyleMessageJoinWithStatus.receptionStatus != newItem.fyleMessageJoinWithStatus.receptionStatus) {
                            changesMask |= RECEPTION_STATUS_CHANGE_MASK;
                        }

                        payloadCache[newItemPosition] = changesMask;
                        payloadComputed[newItemPosition] = true;
                        return changesMask;
                    }
                });
                this.attachmentFyles = orderedFyles;
                result.dispatchUpdatesTo(this);
            } else {
                this.attachmentFyles = orderedFyles;
                notifyDataSetChanged();
            }
        }
    }


    private void attachmentClicked(int position, int viewType, boolean musicFailed, View view) {
        if (attachmentFyles == null || position >= attachmentFyles.length) {
            return;
        }
        final FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = attachmentFyles[position];
        switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
            case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                App.runThread(new StartAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus));
                break;
            case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                App.runThread(new StopAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus));
                break;
            case FyleMessageJoinWithStatus.STATUS_DRAFT:
            case FyleMessageJoinWithStatus.STATUS_UPLOADING:
            case FyleMessageJoinWithStatus.STATUS_COMPLETE:
                switch (visibility) {
                    case VISIBLE:
                        if (customOnClickListener != null) {
                            customOnClickListener.onClick(view);
                            return;
                        }
                        if (viewType == TYPE_AUDIO && !musicFailed) {
                            audioAttachmentServiceBinding.playPause(fyleAndStatus, discussionId);
                            fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
                        } else if ((viewType == TYPE_BIG_IMAGE || viewType == TYPE_SMALL_IMAGE || viewType == TYPE_WIDE_IMAGE)
                                && SettingsActivity.useInternalImageViewer()
                                && (!fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("video/") || !"".equals(fyleAndStatus.fyleMessageJoinWithStatus.imageResolution))) {
                            if (noWipeListener != null) {
                                noWipeListener.doNotWipeOnPause();
                            }
                            // we do not mark as opened here as this is done in the gallery activity
                            if (discussionId != null){
                                App.openDiscussionGalleryActivity(activity, discussionId, fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                            } else {
                                App.openMessageGalleryActivity(activity, fyleAndStatus.fyleMessageJoinWithStatus.messageId, fyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                            }
                        } else {
                            App.openFyleInExternalViewer(activity, fyleAndStatus, () -> {
                                if (noWipeListener != null) {
                                    noWipeListener.doNotWipeOnPause();
                                }
                                fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
                            });
                        }
                        break;
                    case HIDDEN:
                        if (openOnClick) {
                            App.runThread(new InboundEphemeralMessageClicked(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.messageId));
                        }
                        break;
                }
                break;
            case FyleMessageJoinWithStatus.STATUS_COPYING:
            case FyleMessageJoinWithStatus.STATUS_FAILED:
                break;
        }
    }

    protected void attachmentLongClicked(int position, View clickedView) {
        if (attachmentFyles == null) {
            return;
        }
        FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus = attachmentFyles[position];
        if (attachmentLongClickListener != null) {
            attachmentLongClickListener.attachmentLongClicked(longClickedFyleAndStatus, clickedView, visibility, readOnce, attachmentFyles.length > 1);
        }
    }


    class AttachmentViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener, SeekBar.OnSeekBarChangeListener, AudioAttachmentServiceBinding.AudioServiceBindableViewHolder, EngineNotificationListener {
        final ConstraintLayout rootView;
        final int type;
        final ImageView attachmentImageView;
        final ImageView attachmentStatusIconImageView;
        final ProgressBar attachmentProgress;
        final TextView attachmentProgressLabel;
        final TextView attachmentFileName;
        final TextView attachmentMimeType;
        final TextView attachmentSize;
        final ImageView attachmentOverlay;
        final View hiddenContentGroup;
        final TextView hiddenContentTextView;
        final View audioPlayerGroup;
        final SeekBar audioPlayerSeekBar;
        final TextView audioPlayerCurrentTime;
        final TextView audioPlayerTotalTime;
        final TextView audioAttachmentNotPlayed;
        final ImageView speakerOutputImageView;
        final LinearLayout etaGroup;
        final TextView etaSpeed;
        final TextView etaEta;

        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;
        private boolean musicFailed;
        private boolean audioInfoBound;

        @SuppressLint("ClickableViewAccessibility")
        AttachmentViewHolder(View itemView, int viewType) {
            super(itemView);
            rootView = (ConstraintLayout) itemView;
            rootView.setClipToOutline(true);

            type = viewType;
            attachmentImageView = itemView.findViewById(R.id.attachment_image_view);
            attachmentStatusIconImageView = itemView.findViewById(R.id.attachment_status_icon_image_view);
            attachmentProgress = itemView.findViewById(R.id.attachment_progress);
            attachmentProgressLabel = itemView.findViewById(R.id.attachment_progress_label);
            attachmentFileName = itemView.findViewById(R.id.attachment_file_name);
            attachmentMimeType = itemView.findViewById(R.id.attachment_mime_type);
            attachmentSize = itemView.findViewById(R.id.attachment_size);
            attachmentOverlay = itemView.findViewById(R.id.attachment_overlay);
            hiddenContentGroup = itemView.findViewById(R.id.attachment_hidden_group);
            hiddenContentTextView = itemView.findViewById(R.id.attachment_hidden_label);
            audioPlayerGroup = itemView.findViewById(R.id.audio_player_group);
            audioPlayerSeekBar = itemView.findViewById(R.id.seek_bar);
            audioPlayerCurrentTime = itemView.findViewById(R.id.current_time_text_view);
            audioPlayerTotalTime = itemView.findViewById(R.id.total_time_text_view);
            audioAttachmentNotPlayed = itemView.findViewById(R.id.audio_attachment_unplayed);
            speakerOutputImageView = itemView.findViewById(R.id.speaker_output_image_view);
            etaGroup = itemView.findViewById(R.id.eta_group);
            etaSpeed = itemView.findViewById(R.id.eta_speed);
            etaEta = itemView.findViewById(R.id.eta_eta);

            if (audioPlayerSeekBar != null) {
                audioPlayerSeekBar.setOnSeekBarChangeListener(this);
                audioPlayerSeekBar.setOnTouchListener((v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            if (blockMessageSwipeListener != null) {
                                blockMessageSwipeListener.blockMessageSwipe(true);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (blockMessageSwipeListener != null) {
                                blockMessageSwipeListener.blockMessageSwipe(false);
                            }
                            break;
                    }
                    return false;
                });
            }
            if (speakerOutputImageView  != null) {
                speakerOutputImageView.setOnClickListener(v -> audioAttachmentServiceBinding.toggleSpeakerOutput());
            }
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            fyleAndStatus = null;
            musicFailed = false;
            audioInfoBound = false;
        }

        @Override
        public void onClick(View view) {
            if (attachmentFyles != null) {
                attachmentClicked(this.getLayoutPosition(), type, musicFailed, view);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (customOnLongClickListener != null) {
                if (customOnLongClickListener.onLongClick(view)) {
                    return true;
                }
            }
            if (attachmentFyles != null) {
                attachmentLongClicked(this.getLayoutPosition(), view);
                return true;
            }
            return false;
        }


        // for music player
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                audioAttachmentServiceBinding.seekAudioAttachment(fyleAndStatus, progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // tracking is ignored
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // tracking is ignored
        }

        @Override
        public void setFailed(boolean failed) {
            musicFailed = failed;
        }

        @Override
        public void updatePlayTimeMs(AudioAttachmentServiceBinding.AudioInfo audioInfo, long playTimeMs, boolean playing) {
            audioPlayerCurrentTime.setText(AudioAttachmentServiceBinding.timeFromMs(playTimeMs));
            if (audioInfo.durationMs != null) {
                audioPlayerSeekBar.setProgress((int) (1_000 * (double) playTimeMs / audioInfo.durationMs));
            }
            if (playing) {
                audioPlayerCurrentTime.setTextColor(ContextCompat.getColor(activity, R.color.olvid_gradient_light));
                attachmentImageView.setImageResource(R.drawable.ic_pause);
            } else {
                audioPlayerCurrentTime.setTextColor(ContextCompat.getColor(activity, R.color.grey));
                attachmentImageView.setImageResource(R.drawable.ic_play);
            }
        }

        @Override
        public void bindAudioInfo(AudioAttachmentServiceBinding.AudioInfo audioInfo, MediaPlayerService.AudioOutput audioOutput) {
            audioInfoBound = true;
            if (audioInfo.failed) {
                audioPlayerGroup.setVisibility(View.GONE);
                speakerOutputImageView.setVisibility(View.GONE);
                attachmentMimeType.setVisibility(View.VISIBLE);
                attachmentSize.setVisibility(View.VISIBLE);
                attachmentImageView.setImageResource(R.drawable.mime_type_icon_audio_failed);
                musicFailed = true;
            } else {
                speakerOutputImageView.setVisibility(View.VISIBLE);
                switch (audioOutput) {
                    case PHONE:
                        speakerOutputImageView.setImageResource(R.drawable.ic_speaker_light_grey);
                        break;
                    case HEADSET:
                        speakerOutputImageView.setImageResource(R.drawable.ic_headset_grey);
                        break;
                    case LOUDSPEAKER:
                        speakerOutputImageView.setImageResource(R.drawable.ic_speaker_blue);
                        break;
                    case BLUETOOTH:
                        speakerOutputImageView.setImageResource(R.drawable.ic_speaker_bluetooth_grey);
                        break;
                }
                audioPlayerGroup.setVisibility(View.VISIBLE);
                attachmentMimeType.setVisibility(View.INVISIBLE);
                attachmentSize.setVisibility(View.INVISIBLE);
                if (audioInfo.durationMs == null) {
                    audioPlayerSeekBar.setEnabled(false);
                    audioPlayerSeekBar.setProgress(0);
                    audioPlayerTotalTime.setText(R.string.text_duration_indeterminate);
                } else {
                    audioPlayerSeekBar.setEnabled(true);
                    audioPlayerTotalTime.setText(AudioAttachmentServiceBinding.timeFromMs(audioInfo.durationMs));
                }

                audioPlayerCurrentTime.setText(AudioAttachmentServiceBinding.timeFromMs(0));
                audioPlayerSeekBar.setProgress(0);

                attachmentImageView.setImageResource(R.drawable.ic_play);
            }
        }

        @Override
        public void setAudioOutput(MediaPlayerService.AudioOutput audioOutput, boolean somethingPlaying) {
            switch (audioOutput) {
                case PHONE:
                    speakerOutputImageView.setImageResource(R.drawable.ic_speaker_light_grey);
                    break;
                case HEADSET:
                    speakerOutputImageView.setImageResource(R.drawable.ic_headset_grey);
                    break;
                case LOUDSPEAKER:
                    speakerOutputImageView.setImageResource(R.drawable.ic_speaker_blue);
                    break;
                case BLUETOOTH:
                    speakerOutputImageView.setImageResource(R.drawable.ic_speaker_bluetooth_grey);
                    break;
            }
            if ((somethingPlaying && (audioOutput == MediaPlayerService.AudioOutput.PHONE)) != (activity.getVolumeControlStream() == AudioManager.STREAM_VOICE_CALL)) {
                activity.setVolumeControlStream((somethingPlaying && (audioOutput == MediaPlayerService.AudioOutput.PHONE)) ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        }

        @Override
        public FyleMessageJoinWithStatusDao.FyleAndStatus getFyleAndStatus() {
            return fyleAndStatus;
        }


        // region EngineNotificationListener
        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            byte[] ownedIdentity = null;
            byte[] messageIdentifier = null;
            Integer attachmentNumber = null;
            Float speed = null;
            Integer eta = null;
            if (notificationName.equals(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS)) {
                ownedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                speed = (Float) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY);
                eta = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY);
            } else if (notificationName.equals(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS)) {
                ownedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                speed = (Float) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY);
                eta = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY);
            }
            if (fyleAndStatus != null && ownedIdentity != null && messageIdentifier != null && attachmentNumber != null && speed != null && eta != null) {
                if (Arrays.equals(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, ownedIdentity)
                        && Arrays.equals(fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, messageIdentifier)
                        && Objects.equals(fyleAndStatus.fyleMessageJoinWithStatus.engineNumber, attachmentNumber)) {
                    float finalSpeed = speed;
                    int finalEta = eta;
                    activity.runOnUiThread(() -> {
                        if (finalSpeed >= 10_000_000_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_gbps, String.format(Locale.ENGLISH, "%d", (int) (finalSpeed / 1_000_000_000f))));
                        } else if (finalSpeed >= 1_000_000_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_gbps, String.format(Locale.ENGLISH, "%1.1f", finalSpeed / 1_000_000_000f)));
                        } else if (finalSpeed >= 10_000_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_mbps, String.format(Locale.ENGLISH, "%d", (int) (finalSpeed / 1_000_000f))));
                        } else if (finalSpeed >= 1_000_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_mbps, String.format(Locale.ENGLISH, "%1.1f", finalSpeed / 1_000_000f)));
                        } else if (finalSpeed >= 10_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_kbps, String.format(Locale.ENGLISH, "%d", (int) (finalSpeed / 1_000f))));
                        } else if (finalSpeed >= 1_000f) {
                            etaSpeed.setText(activity.getString(R.string.xx_kbps, String.format(Locale.ENGLISH, "%1.1f", finalSpeed / 1_000f)));
                        } else {
                            etaSpeed.setText(activity.getString(R.string.xx_bps, String.format(Locale.ENGLISH, "%d", Math.round(finalSpeed))));
                        }

                        if (finalEta > 5_940) {
                            etaEta.setText(activity.getString(R.string.text_timer_h, finalEta/3_600));
                        } else if (finalEta > 99) {
                            etaEta.setText(activity.getString(R.string.text_timer_m, finalEta / 60));
                        } else if (finalEta > 0) {
                            etaEta.setText(activity.getString(R.string.text_timer_s, finalEta));
                        } else {
                            etaEta.setText("-");
                        }
                        if (etaGroup.getVisibility() != View.VISIBLE) {
                            etaGroup.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }

        @Override
        public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
            this.registrationNumber = registrationNumber;
        }

        @Override
        public long getEngineNotificationListenerRegistrationNumber() {
            return registrationNumber;
        }

        @Override
        public boolean hasEngineNotificationListenerRegistrationNumber() {
            return registrationNumber != null;
        }


        private boolean upload = false;
        private Long registrationNumber = null;

        void registerProgressNotifications(boolean upload) {
            if (hasEngineNotificationListenerRegistrationNumber()) {
                return;
            }
            this.upload = upload;
            if (upload) {
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS, this);
            } else {
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS, this);
            }
        }

        void unregisterProgressNotifications() {
            if (hasEngineNotificationListenerRegistrationNumber()) {
                if (upload) {
                    AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS, this);
                } else {
                    AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS, this);
                }
                this.registrationNumber = null;
            }
            if (etaGroup.getVisibility() != View.GONE) {
                etaGroup.setVisibility(View.GONE);
            }
        }
        // endregion
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
                        oldTask.interrupt = true;
                        runningPreviews.put(fyleAndStatus, this);
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
            if (interrupt || holder == null || !fyleAndStatus.equals(holder.fyleAndStatus)) {
                return;
            }
            if (bitmap == null) {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageResource(getDrawableResourceForMimeType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())));
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
            if (interrupt || holder == null || !fyleAndStatus.equals(holder.fyleAndStatus)) {
                return;
            }
            if (drawable == null) {
                new Handler(Looper.getMainLooper()).post(() -> holder.attachmentImageView.setImageResource(getDrawableResourceForMimeType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())));
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


    public class AttachmentSpaceItemDecoration extends RecyclerView.ItemDecoration {
        public static final int attachmentDpSpace = 2;
        final int space;

        public AttachmentSpaceItemDecoration(Context context) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            space = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, attachmentDpSpace, metrics);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            if (position >= imageAndVideoCount) {
                if (position > 0) {
                    outRect.top = 2*space;
                }
                return;
            }

            if (imageAndVideoCount == 1) {
                return;
            }

            if (imageAndVideoCount == 2 || (position == (imageAndVideoCount-1) && ((imageAndVideoCount & 1) != 0))) {
                if (position > 0) {
                    outRect.top = 2*space;
                }
                return;
            }
            if ((position & 1) != 0) {
                outRect.left = space;
            } else {
                outRect.right = space;
            }
            if (position > 1) {
                outRect.top = 2*space;
            }
        }
    }

    public interface AttachmentLongClickListener {
        void attachmentLongClicked(FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus, View clickedView, Visibility visibility, boolean readOnce, boolean multipleAttachments);
    }

    public interface NoWipeListener {
        void doNotWipeOnPause();
    }

    public interface BlockMessageSwipeListener {
        void blockMessageSwipe(boolean block);
    }

    public static int getDrawableResourceForMimeType(@NonNull String mimeType) {
        if (mimeType.startsWith("audio/")) {
            return R.drawable.mime_type_icon_audio;
        } else if (mimeType.startsWith("image/")) {
            return R.drawable.mime_type_icon_image;
        } else if (mimeType.startsWith("video/")) {
            return R.drawable.mime_type_icon_video;
        } else if (mimeType.startsWith("text/")) {
            return R.drawable.mime_type_icon_text;
        } else {
            switch (mimeType) {
                case OpenGraph.MIME_TYPE:
                    return R.drawable.mime_type_icon_link;
                case "application/zip":
                case "application/gzip":
                case "application/x-bzip":
                case "application/x-bzip2":
                case "application/x-7z-compressed":
                    return R.drawable.mime_type_icon_zip;
                default:
                    return R.drawable.mime_type_icon_file;
            }
        }
    }
}
