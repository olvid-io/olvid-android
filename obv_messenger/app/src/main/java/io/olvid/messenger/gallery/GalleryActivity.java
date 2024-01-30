/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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


import static androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE;
import static androidx.media3.common.C.USAGE_MEDIA;
import static io.olvid.messenger.gallery.GalleryAdapter.VIEW_TYPE_VIDEO;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;

@OptIn(markerClass = UnstableApi.class)
public class GalleryActivity extends LockableActivity {
    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity";
    public static final String BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA = "sort_order";
    public static final String ASCENDING_INTENT_EXTRA = "ascending";
    public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id";
    public static final String DRAFT_INTENT_EXTRA = "draft";
    public static final String INITIAL_MESSAGE_ID_INTENT_EXTRA = "initial_message_id";
    public static final String INITIAL_FYLE_ID_INTENT_EXTRA = "initial_fyle_id";

    public static final String CONTROLS_SHOWN_INSTANCE_STATE_EXTRA = "controls_shown";

    private GalleryViewModel viewModel;

    private ViewPager2.OnPageChangeCallback onPageChangeCallback;
    private GalleryAdapter galleryAdapter;


    private ViewPager2 viewPager;
    private MotionLayout galleryMotionLayout;
    private View bottomBar;
    private TextView fileNameTextView;
    private TextView fileSizeTextView;
    private TextView mimeTypeTextView;
    private TextView resolutionTextView;
    private boolean isShowingVideo;
    private TextView timerTextView;
    private TextView emptyView;

    private Timer expirationTimer = null;
    private Long expirationTimestamp = null;
    private boolean visibilityExpiration = false;
    private boolean readOnce = false;

    private GestureDetector gestureDetector;
    private boolean screenShotBlockedForEphemeral = false;


    private enum MENU_TYPE {
        STANDARD,
        INCOMPLETE_OR_DRAFT,
        UPLOADING,
        DELETE_ONLY,
        NONE,
    }

    @NonNull
    private MENU_TYPE currentMenuType = MENU_TYPE.NONE;


    private final ActivityResultLauncher<Pair<String, FyleMessageJoinWithStatusDao.FyleAndStatus>> saveAttachmentLauncher = registerForActivityResult(new GetAttachmentSaveUri(), this::saveCallback);

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gallery);

        Toolbar topBar = findViewById(R.id.top_bar);
        setSupportActionBar(topBar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(GalleryViewModel.class);


        //noinspection unused // this useless line is here to prevent the linter from removing the id (causing a crash)
        View blackOverlay = findViewById(R.id.black_overlay);

        galleryMotionLayout = findViewById(R.id.gallery_motion_layout);
        bottomBar = findViewById(R.id.bottom_bar);
        fileNameTextView = findViewById(R.id.file_name_text_view);
        fileSizeTextView = findViewById(R.id.file_size_text_view);
        mimeTypeTextView = findViewById(R.id.mime_type_text_view);
        resolutionTextView = findViewById(R.id.resolution_text_view);
        timerTextView = findViewById(R.id.message_timer_textview);
        emptyView = findViewById(R.id.empty_view);
        emptyView.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_down);
        });

        viewPager = findViewById(R.id.gallery_pager);
        ExoPlayer mplayer;
        try {
            mplayer = new ExoPlayer.Builder(this).build();
            mplayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(USAGE_MEDIA)
                            .setContentType(AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                    true);
        } catch (NoClassDefFoundError e) {
            mplayer = null;
        }
        final ExoPlayer mediaPlayer = mplayer;

        galleryAdapter = new GalleryAdapter(getLayoutInflater(), mediaPlayer, new GalleryAdapter.GalleryAdapterCallbacks() {
            @Override
            public void singleTapUp() {
                toggleControlsAndUi();
            }

            @Override
            public void setCurrentItem(int position) {
                if (viewPager != null) {
                    viewPager.setCurrentItem(position, false);
                }
            }

            @Override
            public void setViewPagerUserInputEnabled(boolean enabled) {
                if (viewPager != null) {
                    viewPager.setUserInputEnabled(enabled);
                }
            }
        });
        viewPager.setAdapter(galleryAdapter);
        viewPager.setOffscreenPageLimit(1);
        viewPager.setPageTransformer(new MarginPageTransformer(getResources().getDimensionPixelSize(R.dimen.main_activity_page_margin)));

        if (savedInstanceState != null) {
            controlsShown = savedInstanceState.getBoolean(CONTROLS_SHOWN_INSTANCE_STATE_EXTRA, false);
        }

        onPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            int currentPosition;
            PlayerView oldPlayerView = null;

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                GalleryAdapter.GalleryImageViewHolder newVideoViewHolder;
                try {
                    GalleryAdapter.GalleryImageViewHolder oldVideoViewHolder = ((GalleryAdapter.GalleryImageViewHolder) galleryAdapter.recyclerView.findViewHolderForAdapterPosition(currentPosition));
                    if (oldVideoViewHolder != null && oldVideoViewHolder.playerView != null && oldVideoViewHolder.playerView.getPlayer() != null) {
                        oldPlayerView = oldVideoViewHolder.playerView;
                    }
                    newVideoViewHolder = ((GalleryAdapter.GalleryImageViewHolder) galleryAdapter.recyclerView.findViewHolderForAdapterPosition(position));
                } catch (Exception e) {
                    e.printStackTrace();
                    newVideoViewHolder = null;
                }

                currentPosition = position;

                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.clearMediaItems();

                    if (galleryAdapter.getItemViewType(position) == VIEW_TYPE_VIDEO) {
                        if (newVideoViewHolder != null && newVideoViewHolder.playerView != null) {
                            PlayerView.switchTargetView(mediaPlayer, oldPlayerView, newVideoViewHolder.playerView);
                        }
                        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = galleryAdapter.getItemAt(position);
                        String filePath = App.absolutePathFromRelative(fyleAndStatus.fyle.filePath);
                        if (filePath == null) {
                            filePath = fyleAndStatus.fyleMessageJoinWithStatus.getAbsoluteFilePath();
                        }
                        MediaItem mediaItem = new MediaItem.Builder().setUri(filePath).setMediaMetadata(new MediaMetadata.Builder().setTitle("").build()).build();
                        mediaPlayer.setMediaItem(mediaItem);
                        mediaPlayer.setPlayWhenReady(true);
                        mediaPlayer.prepare();
                    }
                }

                if (viewModel != null) {
                    viewModel.setCurrentPagerPosition(currentPosition);
                }
            }
        };

        viewModel.getImageAndVideoFyleAndStatusList().observe(this, galleryAdapter);
        viewModel.getImageAndVideoFyleAndStatusList().observe(this, (List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses) -> {
            if (fyleAndStatuses != null && fyleAndStatuses.size() == 0) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        });

        viewModel.getCurrentFyleAndStatus().observe(this, this::bindFileInfo);
        viewModel.getCurrentAssociatedMessage().observe(this, this::updateMenuForMessage);
        viewModel.getCurrentAssociatedMessage().observe(this, this::blockScreenshotsIfMessageIsEphemeral);
        viewModel.getCurrentAssociatedMessageExpiration().observe(this, this::updateMessageExpiration);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        if (viewModel.getCurrentPagerPosition() == null) {
            byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
            String sortOrder = intent.getStringExtra(BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA);
            Boolean ascending = intent.getBooleanExtra(ASCENDING_INTENT_EXTRA, true);
            long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
            boolean draft = intent.getBooleanExtra(DRAFT_INTENT_EXTRA, false);
            long messageId = intent.getLongExtra(INITIAL_MESSAGE_ID_INTENT_EXTRA, -1);
            long fyleId = intent.getLongExtra(INITIAL_FYLE_ID_INTENT_EXTRA, -1);


            if (discussionId != -1) {
                viewModel.setDiscussionId(discussionId, ascending);
            } else if (bytesOwnedIdentity != null) {
                viewModel.setBytesOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending);
            } else if (messageId != -1) {
                viewModel.setMessageId(messageId, draft);
            } else {
                finish();
                return;
            }

            if (messageId != -1 && fyleId != -1) {
                GalleryViewModel.MessageAndFyleId messageAndFyleId = new GalleryViewModel.MessageAndFyleId(messageId, fyleId);
                galleryAdapter.goToFyle(messageAndFyleId);
            }
        }

        viewPager.setOnApplyWindowInsetsListener(this::onApplyWindowInsets);

        Window window = getWindow();
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (window != null) {
            // wait 1s before adding the handler, so that systemUiVisibility stabilizes
            new Handler(Looper.getMainLooper()).postDelayed(() -> window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    if (!controlsShown) {
                        showControlsAndUi();
                    }
                }
            }), 1000);
        }

        int DENSITY_INDEPENDENT_THRESHOLD = 200;
        int SWIPE_THRESHOLD_VELOCITY = (int) (DENSITY_INDEPENDENT_THRESHOLD * getResources().getDisplayMetrics().density);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isShowingVideo && controlsShown && e.getY() > topBar.getBottom()) {
                    hideControlsAndUi();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isShowingVideo) {
                    toggleControlsAndUi();
                }
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (4 * Math.abs(velocityX) < Math.abs(velocityY)
                        && (velocityY > SWIPE_THRESHOLD_VELOCITY || velocityY < -SWIPE_THRESHOLD_VELOCITY)
                        && viewPager.isUserInputEnabled()
                        && e1.getY() > 100 // no fling if starting from top of screen (like to show status bar)
                ) {
                    finish();
                    if (velocityY < 0) {
                        overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_up);
                    } else {
                        overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_down);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void blockScreenshotsIfMessageIsEphemeral(Message message) {
        if (message != null && !screenShotBlockedForEphemeral) {
            if (message.wipeStatus != Message.WIPE_STATUS_WIPE_ON_READ) {
                try {
                    JsonExpiration expiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, JsonExpiration.class);
                    if (expiration.getVisibilityDuration() == null) {
                        return;
                    }
                } catch (Exception e) {
                    return;
                }
            }

            // message is readOnce or has limited visibility duration --> prevent screenshot
            Window window = getWindow();
            if (window != null) {
                screenShotBlockedForEphemeral = true;
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }


    private void updateMenuForMessage(Message message) {
        final MENU_TYPE newMenuType;
        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = viewModel.getCurrentFyleAndStatus().getValue();
        if (fyleAndStatus == null || message == null || fyleAndStatus.fyleMessageJoinWithStatus.messageId != message.id) {
            newMenuType = MENU_TYPE.NONE;
        } else {
            if (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ || fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
                newMenuType = MENU_TYPE.DELETE_ONLY;
            } else if (message.status == Message.STATUS_DRAFT || !fyleAndStatus.fyle.isComplete()) {
                newMenuType = MENU_TYPE.INCOMPLETE_OR_DRAFT;
            } else if (message.status == Message.STATUS_UNPROCESSED || message.status == Message.STATUS_PROCESSING) {
                newMenuType = MENU_TYPE.UPLOADING;
            } else {
                newMenuType = MENU_TYPE.STANDARD;
            }
        }

        if (newMenuType != currentMenuType) {
            currentMenuType = newMenuType;
            invalidateOptionsMenu();
        }

        if (message == null) {
            if (readOnce) {
                readOnce = false;
                updateExpirationTimer(true);
            }
        } else {
            if (readOnce != (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ)) {
                readOnce = message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ;
                updateExpirationTimer(true);
            }
        }
    }

    long lastMessageExpirationMessageId = -1;

    private void updateMessageExpiration(MessageExpiration messageExpiration) {
        if (messageExpiration != null) {
            if (lastMessageExpirationMessageId != messageExpiration.messageId) {
                expirationTimestamp = messageExpiration.expirationTimestamp;
                visibilityExpiration = messageExpiration.wipeOnly;
                lastRemainingDisplayed = -1;
                lastMessageExpirationMessageId = messageExpiration.messageId;
                updateExpirationTimer(true);
            }
        } else {
            if (lastMessageExpirationMessageId != -1) {
                expirationTimestamp = null;
                visibilityExpiration = false;
                lastMessageExpirationMessageId = -1;
                updateExpirationTimer(true);
            }
        }
    }


    private long lastRemainingDisplayed = -1;

    private void updateExpirationTimer(boolean force) {
        if (timerTextView == null) {
            return;
        }
        if (expirationTimestamp != null) {
            if (timerTextView.getVisibility() != View.VISIBLE) {
                timerTextView.setVisibility(View.VISIBLE);
            }
            long remaining = (expirationTimestamp - System.currentTimeMillis()) / 1000;
            if (force) {
                lastRemainingDisplayed = -1;
            }
            int color = 0;
            try {
                if (remaining < 0) {
                    remaining = 0;
                }
                if (remaining < 60) {
                    if (remaining == lastRemainingDisplayed) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_s, remaining));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 60) {
                        color = ContextCompat.getColor(this, R.color.red);
                    }
                } else if (remaining < 3600) {
                    if (remaining / 60 == lastRemainingDisplayed / 60) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_m, remaining / 60));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 3600) {
                        color = ContextCompat.getColor(this, R.color.orange);
                    }
                } else if (remaining < 86400) {
                    if (remaining / 3600 == lastRemainingDisplayed / 3600) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_h, remaining / 3600));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 86400) {
                        color = ContextCompat.getColor(this, R.color.greyTint);
                    }
                } else if (remaining < 31536000) {
                    if (remaining / 86400 == lastRemainingDisplayed / 86400) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_d, remaining / 86400));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 31536000) {
                        color = ContextCompat.getColor(this, R.color.greyOverlay);
                    }
                } else {
                    if (remaining / 31536000 == lastRemainingDisplayed / 31536000) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_y, remaining / 31536000));
                    if (lastRemainingDisplayed < 0) {
                        color = ContextCompat.getColor(this, R.color.greyOverlay);
                    }
                }
            } finally {
                lastRemainingDisplayed = remaining;
                if (color != 0) {
                    if (readOnce) {
                        timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_burn_small, 0, 0);
                        timerTextView.setTextColor(ContextCompat.getColor(this, R.color.red));
                    } else {
                        timerTextView.setTextColor(color);
                        Drawable drawable;
                        if (visibilityExpiration) {
                            drawable = ContextCompat.getDrawable(this, R.drawable.ic_eye_small);
                        } else {
                            drawable = ContextCompat.getDrawable(this, R.drawable.ic_timer_small);
                        }
                        if (drawable != null) {
                            drawable = drawable.mutate();
                            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                            timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null);
                        }
                    }
                }
            }
        } else if (readOnce) {
            if (timerTextView.getVisibility() != View.VISIBLE) {
                timerTextView.setVisibility(View.VISIBLE);
            }
            timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_burn_small, 0, 0);
            timerTextView.setText(null);
        } else {
            if (timerTextView.getVisibility() != View.GONE) {
                timerTextView.setVisibility(View.GONE);
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (galleryAdapter != null) {
            galleryAdapter.cleanup();
        }
    }

    private void bindFileInfo(FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) {
        if (fyleAndStatus == null) {
            fileNameTextView.setText(null);
            fileSizeTextView.setText(null);
            mimeTypeTextView.setText(null);
            resolutionTextView.setText(null);
            isShowingVideo = false;
        } else {
            fileNameTextView.setText(fyleAndStatus.fyleMessageJoinWithStatus.fileName);
            fileSizeTextView.setText(Formatter.formatShortFileSize(GalleryActivity.this, fyleAndStatus.fyleMessageJoinWithStatus.size));
            mimeTypeTextView.setText(fyleAndStatus.fyleMessageJoinWithStatus.mimeType);
            String imageResolution = fyleAndStatus.fyleMessageJoinWithStatus.imageResolution;
            if (imageResolution != null) {
                if (imageResolution.startsWith("a") || imageResolution.startsWith("v")) {
                    imageResolution = imageResolution.substring(1);
                }
            }
            resolutionTextView.setText(imageResolution);
            bottomBar.invalidate();
            isShowingVideo = fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("video/");
            fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        invalidateOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(CONTROLS_SHOWN_INSTANCE_STATE_EXTRA, controlsShown);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        switch (currentMenuType) {
            case STANDARD:
                getMenuInflater().inflate(R.menu.menu_gallery, menu);
                break;
            case INCOMPLETE_OR_DRAFT:
                getMenuInflater().inflate(R.menu.menu_gallery_incomplete, menu);
                break;
            case UPLOADING:
                getMenuInflater().inflate(R.menu.menu_gallery_uploading, menu);
                break;
            case DELETE_ONLY:
                getMenuInflater().inflate(R.menu.menu_gallery_delete_only, menu);
                break;
            case NONE:
                break;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (item.getItemId() == R.id.action_open) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = galleryAdapter.getItemAt(viewPager.getCurrentItem());
            if (fyleAndStatus != null) {
                App.openFyleInExternalViewer(this, fyleAndStatus, null);
            }
        } else if (item.getItemId() == R.id.action_delete) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = galleryAdapter.getItemAt(viewPager.getCurrentItem());
            if (fyleAndStatus != null) {
                switch (viewModel.getGalleryType()) {
                    case DRAFT: {
                        App.runThread(new DeleteAttachmentTask(fyleAndStatus));
                        break;
                    }
                    case MESSAGE:
                    case DISCUSSION:
                    case OWNED_IDENTITY:
                    default: {
                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_delete_attachment)
                                .setMessage(getString(R.string.dialog_message_delete_attachment_gallery, fyleAndStatus.fyleMessageJoinWithStatus.fileName))
                                .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteAttachmentTask(fyleAndStatus)))
                                .setNegativeButton(R.string.button_label_cancel, null);
                        builder.create().show();
                    }
                }
            }
        } else if (item.getItemId() == R.id.action_save) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = galleryAdapter.getItemAt(viewPager.getCurrentItem());
            if (fyleAndStatus != null && fyleAndStatus.fyle.isComplete()) {
                App.prepareForStartActivityForResult(this);
                saveAttachmentLauncher.launch(new Pair<>(fyleAndStatus.fyleMessageJoinWithStatus.fileName, fyleAndStatus));
            }
        } else if (item.getItemId() == R.id.action_share) {
            FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = galleryAdapter.getItemAt(viewPager.getCurrentItem());
            if (fyleAndStatus != null) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, fyleAndStatus.getContentUriForExternalSharing());
                intent.setType(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                startActivity(Intent.createChooser(intent, getString(R.string.title_sharing_chooser)));
            }
        }
        return true;
    }


    private void saveCallback(Pair<Uri, FyleMessageJoinWithStatusDao.FyleAndStatus> result) {
        if (result == null) {
            return;
        }
        Uri uri = result.first;
        FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = result.second;
        if (StringUtils.validateUri(uri) && fyleAndStatus != null) {
            App.runThread(() -> {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) {
                        throw new Exception("Unable to write to provided Uri");
                    }
                    try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(fyleAndStatus.fyle.filePath))) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, c);
                        }
                    }
                    App.toast(R.string.toast_message_attachment_saved, Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    App.toast(R.string.toast_message_failed_to_save_attachment, Toast.LENGTH_SHORT);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screenShotBlockedForEphemeral) {
            Window window = getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        if (viewPager != null && onPageChangeCallback != null) {
            viewPager.registerOnPageChangeCallback(onPageChangeCallback);
        }
        expirationTimer = new Timer("DiscussionActivity-expirationTimers");
        expirationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> updateExpirationTimer(false));
            }
        }, 1000, 1000);

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewPager != null && onPageChangeCallback != null) {
            viewPager.unregisterOnPageChangeCallback(onPageChangeCallback);
        }
        if (expirationTimer != null) {
            expirationTimer.cancel();
            expirationTimer = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (controlsShown) {
                showControlsAndUi();
            } else {
                hideControlsAndUi();
            }
        }
    }


    private boolean controlsShown = false;

    private void hideControlsAndUi() {
        controlsShown = false;
        if (galleryMotionLayout != null) {
            galleryMotionLayout.transitionToStart();
        }
        Window window = getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            // Set the content to appear under the system bars so that the
                            // content doesn't resize when the system bars hide and show.
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blackOverlay));
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void showControlsAndUi() {
        controlsShown = true;
        if (galleryMotionLayout != null) {
            galleryMotionLayout.transitionToEnd();
        }
        Window window = getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.blackOverlay));
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void toggleControlsAndUi() {
        if (controlsShown) {
            hideControlsAndUi();
        } else {
            showControlsAndUi();
        }
    }

    private WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        if (galleryMotionLayout != null) {
            ConstraintSet endConstraintSet = galleryMotionLayout.getConstraintSet(R.id.end);
            endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.TOP, insets.getSystemWindowInsetTop());
            endConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.BOTTOM, insets.getSystemWindowInsetBottom());
            endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight());
            endConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight());
            endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft());
            endConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft());

            ConstraintSet startConstraintSet = galleryMotionLayout.getConstraintSet(R.id.start);
            startConstraintSet.setMargin(R.id.top_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight());
            startConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight());
            startConstraintSet.setMargin(R.id.top_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft());
            startConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout displayCutout = insets.getDisplayCutout();
                if (displayCutout != null) {
                    if (displayCutout.getSafeInsetTop() == 0) {
                        endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft() - displayCutout.getSafeInsetLeft());
                        endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight() - displayCutout.getSafeInsetRight());
                        startConstraintSet.setMargin(R.id.top_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft() - displayCutout.getSafeInsetLeft());
                        startConstraintSet.setMargin(R.id.top_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight() - displayCutout.getSafeInsetRight());
                    }
                    if (displayCutout.getSafeInsetBottom() == 0) {
                        endConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft() - displayCutout.getSafeInsetLeft());
                        endConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight() - displayCutout.getSafeInsetRight());
                        startConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.LEFT, insets.getSystemWindowInsetLeft() - displayCutout.getSafeInsetLeft());
                        startConstraintSet.setMargin(R.id.bottom_bar, ConstraintSet.RIGHT, insets.getSystemWindowInsetRight() - displayCutout.getSafeInsetRight());
                    }
                }
            }
        }
        return insets;
    }

    private static class GetAttachmentSaveUri extends ActivityResultContract<Pair<String, FyleMessageJoinWithStatusDao.FyleAndStatus>, Pair<Uri, FyleMessageJoinWithStatusDao.FyleAndStatus>> {
        private FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus;

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Pair<String, FyleMessageJoinWithStatusDao.FyleAndStatus> input) {
            fyleAndStatus = input.second;
            return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_TITLE, input.first);
        }

        @Nullable
        @Override
        public final Pair<Uri, FyleMessageJoinWithStatusDao.FyleAndStatus> parseResult(int resultCode, @Nullable Intent intent) {
            if (intent != null && resultCode == Activity.RESULT_OK) {
                return new Pair<>(intent.getData(), fyleAndStatus);
            }
            return null;
        }
    }
}
