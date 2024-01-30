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

package io.olvid.messenger.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.MessageAttachmentAdapter;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.SizeAwareCardView;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference;
import io.olvid.messenger.discussion.compose.EphemeralViewModel;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel;
import io.olvid.messenger.viewModels.MessageDetailsViewModel;


public class MessageDetailsActivity extends LockableActivity {

    public static final String MESSAGE_ID_INTENT_EXTRA = "message_id";
    public static final String HAS_ATTACHMENT_INTENT_EXTRA = "has_attachments";
    public static final String IS_INBOUND_INTENT_EXTRA = "is_inbound";
    public static final String SENT_FROM_OTHER_DEVICE_INTENT_EXTRA = "sent_from_other_device";

    MessageDetailsViewModel messageDetailsViewModel;
    AudioAttachmentServiceBinding audioAttachmentServiceBinding;

    RecyclerView recipientInfosRecyclerView;
    RecipientInfosAdapter recipientInfosAdapter;
    EmptyRecyclerView metadataRecyclerView;
    MessageMetadataAdapter messageMetadataAdapter;
    View messageDetailsActivityRoot;
    boolean hasAttachments;
    boolean isInbound;
    boolean sendFromOtherDevice;

    // message views
    View messageScrollView;
    ImageView discussionBackground;
    ConstraintLayout messageRootConstrainLayout;
    TextView messageTopTimestamp;
    SizeAwareCardView messageCardView;
    TextView messageSenderTextView;
    TextView ephemeralSenderTextView;
    View ephemeralHeaderView;
    View standardHeaderView;
    TextView ephemeralExplanationTextView;
    TextView ephemeralTimerTextView;
    TextView ephemeralTimestampTextView;
    TextView messageContentTextView;
    TextView wipedAttachmentCountTextView;
    TextView messageBottomTimestampTextView;
    TextView messageForwardedTextView;
    View timestampSpacer;
    ImageView messageStatusImageView;
    RotateAnimation rotateAnimation;
    TextView timerTextView;
    ViewGroup messageReplyGroup;
    TextView messageReplySenderName;
    TextView messageReplyBody;
    TextView messageReplyAttachmentCount;
    CardView attachmentsCard;
    RecyclerView attachmentsRecyclerView;
    MessageAttachmentAdapter attachmentAdapter;
    LiveData<Message> repliedToMessage;

    private Timer updateTimer;
    private Long expirationTimestamp;
    private boolean readOnce;
    private long lastRemainingDisplayed;

    float statusWidth;
    private boolean messageIsUndelivered = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        messageDetailsViewModel = new ViewModelProvider(this).get(MessageDetailsViewModel.class);
        try {
            audioAttachmentServiceBinding = new AudioAttachmentServiceBinding(this);
        } catch (Exception e) {
            e.printStackTrace();
            finish();
            return;
        }
        hasAttachments  = getIntent().getBooleanExtra(HAS_ATTACHMENT_INTENT_EXTRA, false);
        isInbound = getIntent().getBooleanExtra(IS_INBOUND_INTENT_EXTRA, false);
        sendFromOtherDevice = getIntent().getBooleanExtra(SENT_FROM_OTHER_DEVICE_INTENT_EXTRA, false);

        if (hasAttachments) {
            if (isInbound) {
                setContentView(R.layout.activity_message_details_inbound_with_attachments);
            } else {
                setContentView(R.layout.activity_message_details_with_attachments);
            }
        } else {
            if (isInbound) {
                setContentView(R.layout.activity_message_details_inbound);
            } else {
                setContentView(R.layout.activity_message_details);
            }
        }
        messageDetailsActivityRoot = findViewById(R.id.message_details_root);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // message
        messageRootConstrainLayout = findViewById(R.id.message_root_constraint_layout);

        messageScrollView = findViewById(R.id.message_scrollview);
        discussionBackground = findViewById(R.id.discussion_background);
        messageTopTimestamp = findViewById(R.id.message_timestamp_top_text_view);
        messageCardView = findViewById(R.id.message_content_card);
        messageSenderTextView = findViewById(R.id.message_sender_text_view);
        messageContentTextView = findViewById(R.id.message_content_text_view);
        wipedAttachmentCountTextView = findViewById(R.id.wiped_attachment_count);
        messageBottomTimestampTextView = findViewById(R.id.message_timestamp_bottom_text_view);
        messageForwardedTextView = findViewById(R.id.message_forwarded_badge);
        timestampSpacer = findViewById(R.id.timestamp_spacer);
        messageStatusImageView = findViewById(R.id.message_status_image_view);
        rotateAnimation = new RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        rotateAnimation.setDuration(700);
        timerTextView = findViewById(R.id.message_timer_textview);

        standardHeaderView = findViewById(R.id.standard_header);
        ephemeralHeaderView = findViewById(R.id.ephemeral_header);
        ephemeralSenderTextView = findViewById(R.id.ephemeral_message_sender_text_view);
        ephemeralExplanationTextView = findViewById(R.id.ephemeral_explanation_text_view);
        ephemeralTimerTextView = findViewById(R.id.ephemeral_timer);
        ephemeralTimestampTextView = findViewById(R.id.ephemeral_message_timestamp_bottom_text_view);

        // reply
        messageReplyGroup = findViewById(R.id.message_content_reply_group);
        messageReplySenderName = findViewById(R.id.message_content_reply_sender_name);
        messageReplyBody = findViewById(R.id.message_content_reply_body);
        messageReplyAttachmentCount = findViewById(R.id.message_content_reply_attachment_count);

        // attachments
        if (hasAttachments) {
            attachmentsCard = findViewById(R.id.attachments_card);
            attachmentsRecyclerView = findViewById(R.id.attachments_recycler_view);
            attachmentAdapter = new MessageAttachmentAdapter(this, audioAttachmentServiceBinding, null);
            attachmentAdapter.setAttachmentLongClickListener(null);

            GridLayoutManager attachmentsLayoutManager = new GridLayoutManager(this, attachmentAdapter.getColumnCount());
            attachmentsLayoutManager.setOrientation(RecyclerView.VERTICAL);
            attachmentsLayoutManager.setSpanSizeLookup(attachmentAdapter.getSpanSizeLookup());

            attachmentsRecyclerView.setLayoutManager(attachmentsLayoutManager);
            attachmentsRecyclerView.setAdapter(attachmentAdapter);
            attachmentsRecyclerView.addItemDecoration(attachmentAdapter.getItemDecoration());
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        statusWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, metrics) + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, metrics);
        messageCardView.setSizeChangeListener(this::recomputeMessageLayout);


        // metadata
        metadataRecyclerView = findViewById(R.id.message_metadata_recycler_view);
        if (isInbound || sendFromOtherDevice) {
            metadataRecyclerView.setEmptyView(findViewById(R.id.empty_metadata_textview));
        }
        messageMetadataAdapter = new MessageMetadataAdapter(this);

        metadataRecyclerView.setAdapter(messageMetadataAdapter);
        metadataRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        metadataRecyclerView.addItemDecoration(new MetadataHeaderAndSeparatorDecoration());

        messageDetailsViewModel.getMessageMetadata().observe(this, messageMetadataAdapter);

        // recipients
        if (!isInbound) {
            TextView recipientsStatusTextView = findViewById(R.id.recipient_status_text_view);
            TextView otherDeviceExplanationTextView = findViewById(R.id.sent_from_other_device_text_view);
            recipientInfosRecyclerView = findViewById(R.id.recipient_infos_recycler_view);
            if (sendFromOtherDevice) {
                recipientsStatusTextView.setVisibility(View.GONE);
                recipientInfosRecyclerView.setVisibility(View.GONE);
                otherDeviceExplanationTextView.setVisibility(View.VISIBLE);
            } else {
                recipientsStatusTextView.setVisibility(View.VISIBLE);
                recipientInfosRecyclerView.setVisibility(View.VISIBLE);
                otherDeviceExplanationTextView.setVisibility(View.GONE);

                recipientInfosAdapter = new RecipientInfosAdapter(this);

                recipientInfosRecyclerView.setAdapter(recipientInfosAdapter);
                recipientInfosRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                recipientInfosRecyclerView.addItemDecoration(new RecipientInfoHeaderAndSeparatorDecoration());
                messageDetailsViewModel.getMessageRecipientInfos().observe(this, recipientInfosAdapter);
            }
        }


        messageDetailsViewModel.getDiscussionCustomization().observe(this, discussionCustomization -> {
            if (discussionCustomization != null) {
                if (discussionCustomization.backgroundImageUrl != null) {
                    App.runThread(()->{
                        String backgroundImageAbsolutePath = App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl);
                        Bitmap bitmap = BitmapFactory.decodeFile(backgroundImageAbsolutePath);
                        if (bitmap.getByteCount() > SelectDetailsPhotoViewModel.MAX_BITMAP_SIZE) {
                            return;
                        }
                        try {
                            ExifInterface exifInterface = new ExifInterface(backgroundImageAbsolutePath);
                            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
                        } catch (IOException e) {
                            Logger.d("Error creating ExifInterface for file " + backgroundImageAbsolutePath);
                        }
                        final Bitmap finalBitmap = bitmap;
                        new Handler(Looper.getMainLooper()).post(()->discussionBackground.setImageBitmap(finalBitmap));
                    });
                    discussionBackground.setBackgroundColor(0x00ffffff);
                } else {
                    discussionBackground.setImageDrawable(null);
                    DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                    if (colorJson != null) {
                        int color = colorJson.color + ((int) (colorJson.alpha * 255) << 24);
                        discussionBackground.setBackgroundColor(color);
                    } else {
                        discussionBackground.setBackgroundColor(ContextCompat.getColor(MessageDetailsActivity.this, R.color.almostWhite));
                    }
                }
            } else {
                discussionBackground.setBackgroundColor(ContextCompat.getColor(MessageDetailsActivity.this, R.color.almostWhite));
            }
        });

        messageDetailsViewModel.getMessage().observe(this, this::displayMessage);

        if (hasAttachments) {
            messageDetailsViewModel.getAttachmentFyles().observe(this, attachmentAdapter);
        }

        updateTimer = new Timer("MessageDetails-expirationTimer");
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> updateTimerTextView(System.currentTimeMillis()));
            }
        }, 1000, 1000);

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(MESSAGE_ID_INTENT_EXTRA)) {
            long messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1);
            messageDetailsViewModel.setMessageId(messageId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioAttachmentServiceBinding.release();
        if (updateTimer != null) {
            updateTimer.cancel();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    boolean messageWasNonNull = false;

    private void displayMessage(Message message) {
        if (message == null) {
            if (messageWasNonNull) {
                // message became null --> was deleted or expired, close the message details
                finish();
                return;
            }
            messageTopTimestamp.setVisibility(View.GONE);
            messageCardView.setVisibility(View.GONE);
            return;
        }
        messageWasNonNull = true;
        messageTopTimestamp.setVisibility(View.VISIBLE);
        messageCardView.setVisibility(View.VISIBLE);

        messageTopTimestamp.setText(StringUtils.getDayOfDateString(this, message.timestamp));

        messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(this, message.timestamp));

        if (message.messageType == Message.TYPE_INBOUND_MESSAGE
                || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
            if (message.forwarded) {
                messageForwardedTextView.setVisibility(View.VISIBLE);
            } else {
                messageForwardedTextView.setVisibility(View.GONE);
            }
        }

        if (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            standardHeaderView.setVisibility(View.GONE);
            ephemeralHeaderView.setVisibility(View.VISIBLE);

            ephemeralTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));

            JsonExpiration expiration = message.getJsonMessage().getJsonExpiration();
            boolean readOnce = expiration.getReadOnce() != null && expiration.getReadOnce();

            if (message.isWithoutText()) {
                ephemeralExplanationTextView.setVisibility(View.GONE);
                ephemeralTimerTextView.setVisibility(View.GONE);
            } else {
                ephemeralExplanationTextView.setVisibility(View.VISIBLE);
                ephemeralTimerTextView.setVisibility(View.VISIBLE);
                if (readOnce) {
                    ephemeralTimerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_burn, 0, 0, 0);
                    ephemeralTimerTextView.setTextColor(ContextCompat.getColor(this, R.color.red));
                    if (expiration.getVisibilityDuration() == null) {
                        ephemeralTimerTextView.setText(R.string.text_visible_once);
                    } else if (expiration.getVisibilityDuration() < 60L) {
                        ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_s_once, expiration.getVisibilityDuration()));
                    } else if (expiration.getVisibilityDuration() < 3_600L) {
                        ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_m_once, expiration.getVisibilityDuration() / 60L));
                    } else if (expiration.getVisibilityDuration() < 86_400L) {
                        ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_h_once, expiration.getVisibilityDuration() / 3_600L));
                    } else if (expiration.getVisibilityDuration() < 31_536_000L) {
                        ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_d_once, expiration.getVisibilityDuration() / 86_400L));
                    } else {
                        ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_y_once, expiration.getVisibilityDuration() / 31_536_000L));
                    }
                } else {
                    ephemeralTimerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_eye, 0, 0, 0);
                    ephemeralTimerTextView.setTextColor(ContextCompat.getColor(this, R.color.orange));
                    if (expiration.getVisibilityDuration() == null) {
                        // this should never happen, the jsonExpiration should have been wiped when received
                        Logger.w("Weird, message has expiration and is neither read once nor limited visibility");
                    } else if (expiration.getVisibilityDuration() < 60L) {
                        ephemeralTimerTextView.setText(EphemeralViewModel.Companion.visibilitySetting(expiration.getVisibilityDuration()));
                    }
                }
            }

            if (hasAttachments) {
                attachmentAdapter.setHidden(expiration.getVisibilityDuration(), readOnce, false);
            }
        }

        if (isInbound) {
            messageMetadataAdapter.setSentTimestamp(message.timestamp, true);
            String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
            if (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                ephemeralSenderTextView.setVisibility(View.VISIBLE);
                if (displayName != null) {
                    ephemeralSenderTextView.setText(displayName);
                } else {
                    ephemeralSenderTextView.setText(R.string.text_deleted_contact);
                }
                int color = InitialView.getTextColor(this, message.senderIdentifier, AppSingleton.getContactCustomHue(message.senderIdentifier));
                ephemeralSenderTextView.setTextColor(color);
                ephemeralSenderTextView.setMinWidth(0);
            } else {
                messageSenderTextView.setVisibility(View.VISIBLE);
                if (displayName != null) {
                    messageSenderTextView.setText(displayName);
                } else {
                    messageSenderTextView.setText(R.string.text_deleted_contact);
                }
                int color = InitialView.getTextColor(this, message.senderIdentifier, AppSingleton.getContactCustomHue(message.senderIdentifier));
                messageSenderTextView.setTextColor(color);
                messageSenderTextView.setMinWidth(0);
            }
        } else {
            if (!sendFromOtherDevice) {
                messageMetadataAdapter.setSentTimestamp(message.timestamp, false);
            }
            switch (message.status) {
                case Message.STATUS_SENT: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_message_status_sent));
                    messageStatusImageView.clearAnimation();
                    break;
                }
                case Message.STATUS_DELIVERED: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_message_status_delivered));
                    messageStatusImageView.clearAnimation();
                    break;
                }
                case Message.STATUS_DELIVERED_AND_READ: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_message_status_delivered_and_read));
                    messageStatusImageView.clearAnimation();
                    break;
                }
                case Message.STATUS_UNDELIVERED: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_message_status_undelivered));
                    messageStatusImageView.clearAnimation();
                    break;
                }
                case Message.STATUS_SENT_FROM_ANOTHER_DEVICE: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_message_status_sent_from_other_device));
                    messageStatusImageView.clearAnimation();
                    break;
                }
                default: {
                    messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_sync));
                    messageStatusImageView.startAnimation(rotateAnimation);
                }
            }
            if ((message.status == Message.STATUS_UNDELIVERED) != this.messageIsUndelivered) {
                this.messageIsUndelivered = message.status == Message.STATUS_UNDELIVERED;
                recipientInfosRecyclerView.invalidate();
            }
        }

        JsonMessage jsonMessage = message.getJsonMessage();
        String body = message.getStringContent(this);

        if (wipedAttachmentCountTextView != null) {
            wipedAttachmentCountTextView.setVisibility(View.GONE);
        }

        if (body.length() == 0) {
            messageContentTextView.setVisibility(View.GONE);
        } else if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
            messageContentTextView.setVisibility(View.VISIBLE);
            messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            messageContentTextView.setMinWidth(0);
            messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);

            SpannableString text = new SpannableString(body);
            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
            text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            messageContentTextView.setText(text);
            if (wipedAttachmentCountTextView != null && message.wipedAttachmentCount != 0){
                wipedAttachmentCountTextView.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, message.wipedAttachmentCount, message.wipedAttachmentCount));
                wipedAttachmentCountTextView.setVisibility(View.VISIBLE);
            }
        } else if (StringUtils.isShortEmojiString(body, 5)) {
            messageContentTextView.setVisibility(View.VISIBLE);
            messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.single_line_emoji_size));
            messageContentTextView.setMinWidth((int) (getResources().getDimensionPixelSize(R.dimen.single_line_emoji_size) * 1.25));
            messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            messageContentTextView.setText(body);
        } else if (message.isLocationMessage()) {
            // do nothing
            messageContentTextView.setVisibility(View.VISIBLE);
            messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            messageContentTextView.setMinWidth(0);
            messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            messageContentTextView.setText(message.contentBody);
        } else {
            messageContentTextView.setVisibility(View.VISIBLE);
            messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            messageContentTextView.setMinWidth(0);
            messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            messageContentTextView.setText(body);
        }

        if (jsonMessage.getJsonReply() == null) {
            messageReplyGroup.setVisibility(View.GONE);
            messageReplyBody.setText(null);
        } else {
            messageReplyGroup.setVisibility(View.VISIBLE);

            String displayName = AppSingleton.getContactCustomDisplayName(jsonMessage.getJsonReply().getSenderIdentifier());
            if (displayName != null) {
                messageReplySenderName.setText(displayName);
            } else {
                messageReplySenderName.setText(R.string.text_deleted_contact);
            }
            int color = InitialView.getTextColor(this, jsonMessage.getJsonReply().getSenderIdentifier(), AppSingleton.getContactCustomHue(jsonMessage.getJsonReply().getSenderIdentifier()));
            messageReplySenderName.setTextColor(color);

            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.background_reply_white);
            if (drawable instanceof LayerDrawable) {
                Drawable border = ((LayerDrawable) drawable).findDrawableByLayerId(R.id.reply_color_border);
                border.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                ((LayerDrawable) drawable).setDrawableByLayerId(R.id.reply_color_border, border);
                messageReplyGroup.setBackground(drawable);
            }

            JsonMessageReference jsonReply = jsonMessage.getJsonReply();
            repliedToMessage = AppDatabase.getInstance().messageDao().getBySenderSequenceNumberAsync(jsonReply.getSenderSequenceNumber(), jsonReply.getSenderThreadIdentifier(), jsonReply.getSenderIdentifier(), message.discussionId);
            repliedToMessage.observe(this, replyMessage -> {
                if (replyMessage == null) {
                    messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    messageReplyBody.setMinWidth(0);
                    messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    messageReplyAttachmentCount.setVisibility(View.GONE);

                    StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                    SpannableString spannableString = new SpannableString(getString(R.string.text_original_message_not_found));
                    spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    messageReplyBody.setText(spannableString);
                } else {
                    if (replyMessage.totalAttachmentCount > 0) {
                        messageReplyAttachmentCount.setVisibility(View.VISIBLE);
                        messageReplyAttachmentCount.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, replyMessage.totalAttachmentCount, replyMessage.totalAttachmentCount));
                    } else {
                        messageReplyAttachmentCount.setVisibility(View.GONE);
                    }
                    if (replyMessage.getStringContent(this).length() == 0) {
                        messageReplyBody.setVisibility(View.GONE);
                    } else {
                        messageReplyBody.setVisibility(View.VISIBLE);
                        messageReplyBody.setText(replyMessage.getStringContent(this));
                        if (StringUtils.isShortEmojiString(replyMessage.getStringContent(this), 5)) {
                            messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.single_line_emoji_reply_size));
                            messageReplyBody.setMinWidth((int) (getResources().getDimensionPixelSize(R.dimen.single_line_emoji_reply_size) * 1.25));
                            messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        } else {
                            messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                            messageReplyBody.setMinWidth(0);
                            messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        }
                    }
                }
            });
        }

        App.runThread(() -> {
            MessageExpiration expiration = AppDatabase.getInstance().messageExpirationDao().get(message.id);
            if (expiration != null) {
                this.expirationTimestamp = expiration.expirationTimestamp;
                this.lastRemainingDisplayed = -1;
            } else {
                this.expirationTimestamp = null;
            }
            this.readOnce = message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ;
            if (this.expirationTimestamp != null || this.readOnce) {
                Window window = getWindow();
                if (window != null) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                }
                runOnUiThread(() -> updateTimerTextView(System.currentTimeMillis()));
            }
        });
    }

    private void recomputeMessageLayout() {
        final Rect rect = new Rect();

        new Handler(Looper.getMainLooper()).post(()-> {
            Layout messageTextLayout = messageContentTextView.getLayout();
            Layout timestampLayout = messageBottomTimestampTextView.getLayout();
            if (messageTextLayout != null) {
                int lineCount = messageTextLayout.getLineCount();
                if (timestampLayout != null) {
                    // first check if the first character of the last message line matches LTR/RTL of layout
                    int pos = messageContentTextView.getText().toString().lastIndexOf("\n") + 1;
                    boolean forceSpacerRtl = pos < messageContentTextView.getText().length() && ((timestampSpacer.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) != messageTextLayout.isRtlCharAt(pos));

                    float timestampWidth = timestampLayout.getLineMax(0) + statusWidth;
                    float lastMessageLineWidth = messageTextLayout.getLineMax(lineCount - 1);
                    messageTextLayout.getLineBounds(lineCount - 1, rect);
                    int messageTotalWidth = rect.right - rect.left;
                    if (messageTextLayout.getAlignment().equals(Layout.Alignment.ALIGN_CENTER)) {
                        lastMessageLineWidth += (messageTotalWidth-lastMessageLineWidth)/2;
                    }
                    if (!forceSpacerRtl && (timestampWidth + lastMessageLineWidth < messageTotalWidth)) {
                        timestampSpacer.setVisibility(View.GONE);
                    } else {
                        timestampSpacer.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void updateTimerTextView(long timestamp) {
        if (timerTextView == null) {
            return;
        }
        if (expirationTimestamp != null) {
            timerTextView.setVisibility(View.VISIBLE);
            long remaining = (expirationTimestamp - timestamp)/1000;
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
                } else  if (remaining < 31536000) {
                    if (remaining / 86400 == lastRemainingDisplayed / 86400) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_d, remaining / 86400));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 86400) {
                        color = ContextCompat.getColor(this, R.color.lightGrey);
                    }
                } else {
                    if (remaining / 31536000 == lastRemainingDisplayed / 31536000) {
                        return;
                    }
                    timerTextView.setText(getString(R.string.text_timer_y, remaining / 31536000));
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 31536000) {
                        color = ContextCompat.getColor(this, R.color.lightGrey);
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
                        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_timer_small);
                        if (drawable != null) {
                            drawable = drawable.mutate();
                            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                            timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null);
                        }
                    }
                }
            }
        } else {
            timerTextView.setVisibility(View.GONE);
        }
    }

    public class RecipientInfosAdapter extends RecyclerView.Adapter<RecipientInfosAdapter.ViewHolder> implements Observer<List<MessageRecipientInfo>> {
        private List<MessageRecipientInfo> messageRecipientInfos;
        private final LayoutInflater inflater;

        public RecipientInfosAdapter(Context context) {
            inflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        @Override
        public void onChanged(List<MessageRecipientInfo> messageRecipientInfos) {
            this.messageRecipientInfos = messageRecipientInfos;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            if (messageRecipientInfos != null) {
                return Arrays.hashCode(messageRecipientInfos.get(position).bytesContactIdentity);
            }
            return -1;
        }

        @NonNull
        @Override
        public RecipientInfosAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_message_recipient_info, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecipientInfosAdapter.ViewHolder holder, int position) {
            if (messageRecipientInfos == null) {
                return;
            }
            MessageRecipientInfo messageRecipientInfo = messageRecipientInfos.get(position);
            holder.recipientNameTextView.setText(AppSingleton.getContactCustomDisplayName(messageRecipientInfo.bytesContactIdentity));
            if (messageRecipientInfo.timestampRead != null) {
                holder.recipientInfoTimestampTextView.setText(StringUtils.getPreciseAbsoluteDateString(MessageDetailsActivity.this, messageRecipientInfo.timestampRead));
            } else if (messageRecipientInfo.timestampDelivered != null) {
                holder.recipientInfoTimestampTextView.setText(StringUtils.getPreciseAbsoluteDateString(MessageDetailsActivity.this, messageRecipientInfo.timestampDelivered));
            } else if (messageRecipientInfo.timestampSent != null && messageRecipientInfo.timestampSent != 0) {
                holder.recipientInfoTimestampTextView.setText(StringUtils.getPreciseAbsoluteDateString(MessageDetailsActivity.this, messageRecipientInfo.timestampSent));
            } else {
                holder.recipientInfoTimestampTextView.setText(R.string.text_null_timestamp);
            }
        }

        @Override
        public int getItemCount() {
            if (messageRecipientInfos != null) {
                return messageRecipientInfos.size();
            }
            return 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            private final TextView recipientNameTextView;
            private final TextView recipientInfoTimestampTextView;

            public ViewHolder(@NonNull View rootView) {
                super(rootView);
                this.recipientNameTextView = rootView.findViewById(R.id.recipient_name_text_view);
                this.recipientInfoTimestampTextView = rootView.findViewById(R.id.recipient_info_timestamp_text_view);
                rootView.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                int position = getLayoutPosition();
                if (messageRecipientInfos != null) {
                    String format = "yyyy-MM-dd" + MessageDetailsActivity.this.getString(R.string.text_date_time_separator) + "HH:mm:ss";

                    MessageRecipientInfo messageRecipientInfo = messageRecipientInfos.get(position);
                    String nullString = getString(R.string.text_null_timestamp);
                    String sentTime = messageRecipientInfo.timestampSent == null || messageRecipientInfo.timestampSent == 0 ? nullString : (String) DateFormat.format(format, messageRecipientInfo.timestampSent);
                    String deliveredTime = messageRecipientInfo.timestampDelivered == null || messageRecipientInfo.timestampDelivered == 0 ? nullString : (String) DateFormat.format(format, messageRecipientInfo.timestampDelivered);
                    String readTime = messageRecipientInfo.timestampRead == null || messageRecipientInfo.timestampRead == 0 ? nullString : (String) DateFormat.format(format, messageRecipientInfo.timestampRead);

                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(MessageDetailsActivity.this, R.style.CustomAlertDialog)
                            .setTitle(recipientNameTextView.getText())
                            .setMessage(getString(R.string.dialog_message_recipient_details, sentTime, deliveredTime, readTime))
                            .setPositiveButton(R.string.button_label_ok, null);
                    builder.create().show();
                }
            }
        }
    }


    public class RecipientInfoHeaderAndSeparatorDecoration extends RecyclerView.ItemDecoration {
        private final int headerHeight;
        private final int separatorHeight;
        private final Rect itemRect;
        private final Bitmap[] bitmapCache;

        RecipientInfoHeaderAndSeparatorDecoration() {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, metrics);
            separatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
            itemRect = new Rect();
            bitmapCache = new Bitmap[6];
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION) {
                    continue;
                }
                if (position == 0 || (recipientInfosAdapter.messageRecipientInfos.get(position).status() != recipientInfosAdapter.messageRecipientInfos.get(position-1).status())) {
                    int status = recipientInfosAdapter.messageRecipientInfos.get(position).status();
                    if (status < MessageRecipientInfo.RECIPIENT_STATUS_SENT && MessageDetailsActivity.this.messageIsUndelivered) {
                        status = 5;
                    }
                    // check the cache, and compute it if needed
                    if (bitmapCache[status] == null) {
                        View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_message_details_header, parent, false);
                        TextView textView = headerView.findViewById(R.id.header_status_text);
                        ImageView imageView = headerView.findViewById(R.id.header_status_image);
                        switch (status) {
                            case MessageRecipientInfo.RECIPIENT_STATUS_NOT_SENT_YET:
                                textView.setText(R.string.text_not_sent_yet);
                                imageView.setImageResource(R.drawable.ic_message_status_not_sent_yet);
                                break;
                            case MessageRecipientInfo.RECIPIENT_STATUS_PROCESSING:
                                textView.setText(R.string.text_processing);
                                imageView.setImageResource(R.drawable.ic_sync);
                                break;
                            case MessageRecipientInfo.RECIPIENT_STATUS_SENT:
                                textView.setText(R.string.text_sent);
                                imageView.setImageResource(R.drawable.ic_message_status_sent);
                                break;
                            case MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED:
                                textView.setText(R.string.text_delivered);
                                imageView.setImageResource(R.drawable.ic_message_status_delivered);
                                break;
                            case MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED_AND_READ:
                                textView.setText(R.string.text_read);
                                imageView.setImageResource(R.drawable.ic_message_status_delivered_and_read);
                                break;
                            case 5: // 5 is for undelivered message
                                textView.setText(R.string.text_undelivered);
                                imageView.setImageResource(R.drawable.ic_message_status_undelivered);
                                break;
                        }
                        headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                        headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                        Bitmap headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                        Canvas bitmapCanvas = new Canvas(headerBitmap);
                        headerView.draw(bitmapCanvas);
                        bitmapCache[status] = headerBitmap;
                    }
                    canvas.save();
                    parent.getDecoratedBoundsWithMargins(child, itemRect);
                    itemRect.top += child.getTranslationY();
                    itemRect.bottom += child.getTranslationY();
                    canvas.drawBitmap(bitmapCache[status], itemRect.left, itemRect.top, null);
                    canvas.restore();
                }
            }
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            if (position == 0 || (recipientInfosAdapter.messageRecipientInfos.get(position).status() != recipientInfosAdapter.messageRecipientInfos.get(position-1).status())) {
                outRect.top += headerHeight;
            } else {
                outRect.top += separatorHeight;
            }
        }
    }

    class MessageMetadataAdapter extends RecyclerView.Adapter<MessageMetadataAdapter.ViewHolder> implements Observer<List<MessageMetadata>> {
        private List<MessageMetadata> messageMetadatas;
        private final LayoutInflater inflater;
        private boolean hasUploadedMetadata;
        private Long sentTimestamp;
        private boolean inbound;

        public MessageMetadataAdapter(Context context) {
            inflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        public void setSentTimestamp(Long sentTimestamp, boolean inbound) {
            this.sentTimestamp = sentTimestamp;
            this.inbound = inbound;
            notifyDataSetChanged();
        }

        @Override
        public void onChanged(List<MessageMetadata> messageMetadatas) {
            // check if a messageMetadata is of kind KIND_UPLOADED
            hasUploadedMetadata = false;
            for (MessageMetadata messageMetadata : messageMetadatas) {
                if (messageMetadata.kind == MessageMetadata.KIND_UPLOADED) {
                    hasUploadedMetadata = true;
                    break;
                }
            }
            this.messageMetadatas = messageMetadatas;
            notifyDataSetChanged();
        }


        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_message_metadata, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (messageMetadatas != null) {
                MessageMetadata metadata;
                if (sentTimestamp == null || (hasUploadedMetadata && inbound)) {
                    metadata = messageMetadatas.get(position);
                } else {
                    if (position == 0) {
                        holder.metadataTimestampDateTextView.setText(StringUtils.getPreciseAbsoluteDateString(MessageDetailsActivity.this, sentTimestamp));
                        if (inbound) {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_uploaded);
                        } else {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_sent);
                        }
                        return;
                    } else {
                        metadata = messageMetadatas.get(position - 1);
                    }
                }

                holder.metadataTimestampDateTextView.setText(StringUtils.getPreciseAbsoluteDateString(MessageDetailsActivity.this, metadata.timestamp));
                switch (metadata.kind) {
                    case MessageMetadata.KIND_UPLOADED: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_uploaded);
                        break;
                    }
                    case MessageMetadata.KIND_DELIVERED: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_delivered);
                        break;
                    }
                    case MessageMetadata.KIND_READ: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_read);
                        break;
                    }
                    case MessageMetadata.KIND_WIPED: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_wiped);
                        break;
                    }
                    case MessageMetadata.KIND_EDITED: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_edited);
                        break;
                    }
                    case MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_location_sharing_latest_update);
                        break;
                    }
                    case MessageMetadata.KIND_LOCATION_SHARING_END: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_location_sharing_end);
                        break;
                    }
                    case MessageMetadata.KIND_REMOTE_DELETED: {
                        byte[] bytesCurrentIdentity = AppSingleton.getBytesCurrentIdentity();
                        if (Arrays.equals(bytesCurrentIdentity, metadata.bytesRemoteIdentity)) {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_remote_deleted_by_you);
                        } else {
                            String contactName = AppSingleton.getContactCustomDisplayName(metadata.bytesRemoteIdentity);
                            if (contactName == null) {
                                holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_remote_deleted);
                            } else {
                                holder.metadataDescriptionTextView.setText(getString(R.string.label_metadata_kind_remote_deleted_by, contactName));
                            }
                        }
                        break;
                    }
                    case MessageMetadata.KIND_UNDELIVERED: {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_undelivered);
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            int count = (messageMetadatas == null) ? 0 : messageMetadatas.size();
            if (sentTimestamp == null || (hasUploadedMetadata && inbound)) {
                    return count;
            } else {
                return count + 1;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView metadataDescriptionTextView;
            private final TextView metadataTimestampDateTextView;

            public ViewHolder(@NonNull View rootView) {
                super(rootView);
                metadataDescriptionTextView = rootView.findViewById(R.id.metadata_description_text_view);
                metadataTimestampDateTextView = rootView.findViewById(R.id.metadata_timestamp_date_text_view);
            }
        }
    }

    public class MetadataHeaderAndSeparatorDecoration extends RecyclerView.ItemDecoration {
        private final int headerHeight;
        private final int separatorHeight;
        private final Rect itemRect;
        private Bitmap bitmapCache;

        MetadataHeaderAndSeparatorDecoration() {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, metrics);
            separatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
            itemRect = new Rect();
            bitmapCache = null;
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION) {
                    continue;
                }
                if (position == 0) {
                    if (bitmapCache == null) {
                        View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_message_details_header, parent, false);
                        TextView textView = headerView.findViewById(R.id.header_status_text);
                        ImageView imageView = headerView.findViewById(R.id.header_status_image);

                        textView.setText(R.string.text_message_timeline);
                        imageView.setImageResource(R.drawable.ic_timer);

                        headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                        headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                        Bitmap headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                        Canvas bitmapCanvas = new Canvas(headerBitmap);
                        headerView.draw(bitmapCanvas);
                        bitmapCache = headerBitmap;
                    }
                    canvas.save();
                    parent.getDecoratedBoundsWithMargins(child, itemRect);
                    itemRect.top += child.getTranslationY();
                    itemRect.bottom += child.getTranslationY();
                    canvas.drawBitmap(bitmapCache, itemRect.left, itemRect.top, null);
                    canvas.restore();
                }
            }
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            if (position == 0) {
                outRect.top += headerHeight;
            } else {
                outRect.top += separatorHeight;
            }
        }
    }
}
