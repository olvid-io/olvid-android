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

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.activities.DiscussionSettingsActivity;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.DynamicFlow;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.dao.CallLogItemDao;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.ReactionListBottomSheetFragment;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask;
import io.olvid.messenger.databases.tasks.InboundEphemeralMessageClicked;
import io.olvid.messenger.databases.tasks.CreateReadMessageMetadata;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.databases.tasks.DeleteMessagesTask;
import io.olvid.messenger.databases.tasks.ReplaceDiscussionDraftTask;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.customClasses.MessageAttachmentAdapter;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.tasks.SaveDraftTask;
import io.olvid.messenger.databases.tasks.SetDraftReplyTask;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.customClasses.SizeAwareCardView;
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment;

public class DiscussionActivity extends LockableActivity implements View.OnClickListener, MessageAttachmentAdapter.AttachmentLongClickListener, PopupMenu.OnMenuItemClickListener {
    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";

    public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id";
    public static final String MESSAGE_ID_INTENT_EXTRA = "message_id";
    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY_INTENT_EXTRA = "bytes_contact_identity";
    public static final String BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA = "bytes_group_uid";

    private enum ViewType {
        DISCLAIMER,
        INBOUND,
        INBOUND_WITH_ATTACHMENT,
        INBOUND_EPHEMERAL,
        INBOUND_EPHEMERAL_WITH_ATTACHMENT,
        OUTBOUND_EPHEMERAL,
        OUTBOUND_EPHEMERAL_WITH_ATTACHMENT,
        OUTBOUND,
        OUTBOUND_WITH_ATTACHMENT,
        INFO,
        INFO_GROUP_MEMBER,
        SETTINGS_UPDATE,
        PHONE_CALL,
        NEW_PUBLISHED_DETAILS,
    }

    enum MenuButtonType {
        SHARE,
        FORWARD,
        COPY,
        DETAILS,
        DELETE,
        EDIT,
    }


    private DiscussionViewModel discussionViewModel;
    private ComposeMessageViewModel composeMessageViewModel;
    private AudioAttachmentServiceBinding audioAttachmentServiceBinding;
    private Toolbar toolBar;
    private InitialView toolBarInitialView;
    private TextView toolBarTitle;
    private TextView toolBarSubtitle;

    private boolean scrolling = false;
    private MessageListAdapter messageListAdapter;
    private NewMessagesItemDecoration newMessagesItemDecoration;
    private MessageDateItemDecoration messageDateItemDecoration;
    private EmptyRecyclerView messageRecyclerView;
    private LinearLayoutManager messageListLinearLayoutManager;
    private FloatingActionButton scrollDownFab;
    private ImageView rootBackgroundImageView;
    private DiscussionDelegate discussionDelegate;

    private ComposeMessageFragment composeMessageFragment;
    private ComposeMessageFragment.ComposeMessageDelegate composeMessageDelegate;

    private View spacer;

    private View discussionLockedGroup;
    private ImageView discussionLockedImage;
    private TextView discussionLockedMessage;

    private View discussionNoChannelGroup;
    private ImageView discussionNoChannelImageView;
    private TextView discussionNoChannelMessage;


    private ActionMode actionMode;
    private ActionMode.Callback actionModeCallback;

    private Boolean locked = null;

    private List<Long> messageIdsToMarkAsRead;
    private List<Long> editedMessageIdsToMarkAsSeen;
    private Runnable toolbarClickedCallback;

    private static final int REQUEST_CODE_SAVE_ATTACHMENT = 5;
    private static final int REQUEST_CODE_SAVE_ALL_ATTACHMENTS = 6;


    public static final String SHORTCUT_PREFIX = "discussion_";

    private boolean sendReadReceipt = false;
    private boolean retainWipedOutboundMessages = false;

    private MessageListAdapter.SwipeCallback swipeCallback = null;

    private boolean screenShotBlockedForEphemeral = false;

    private boolean animateLayoutChanges = false;
    private int attachmentRecyclerViewWidth;
    private int attachmentSpace;
    private int attachmentFileHeight;

    private float statusIconWidth = 0;
    private float noStatusIconWidth = 0;

    private final ViewModelProvider.Factory FACTORY = new ViewModelProvider.Factory() {
        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (ComposeMessageViewModel.class.isAssignableFrom(modelClass) && discussionViewModel != null) {
                try {
                    return modelClass.getConstructor(LiveData.class, LiveData.class).newInstance(discussionViewModel.getDiscussionIdLiveData(), discussionViewModel.getDiscussionCustomization());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                return modelClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
    };


    @SuppressLint({"UnsupportedChromeOsCameraSystemFeature", "ClickableViewAccessibility", "NotifyDataSetChanged"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        discussionViewModel = new ViewModelProvider(this, FACTORY).get(DiscussionViewModel.class);
        composeMessageViewModel = new ViewModelProvider(this,  FACTORY).get(ComposeMessageViewModel.class);

        try {
            audioAttachmentServiceBinding = new AudioAttachmentServiceBinding(this);
        } catch (Exception e) {
            finishAndClearViewModel();
            return;
        }

        setContentView(R.layout.activity_discussion);

        toolBar = findViewById(R.id.discussion_toolbar);
        setSupportActionBar(toolBar);

        toolBar.setOnClickListener(view -> {
            if (toolbarClickedCallback != null) {
                toolbarClickedCallback.run();
            }
        });

        toolBarInitialView = toolBar.findViewById(R.id.title_bar_initial_view);
        toolBarTitle = toolBar.findViewById(R.id.title_bar_title);
        toolBarSubtitle = toolBar.findViewById(R.id.title_bar_subtitle);
        View toolBarBackButtonBackdrop = toolBar.findViewById(R.id.back_button_backdrop);
        View toolBarBackButton = toolBar.findViewById(R.id.back_button);

        toolBarBackButtonBackdrop.setOnClickListener(this);
        toolBarBackButton.setOnClickListener(this);
        toolBarInitialView.setOnClickListener(this);

        rootBackgroundImageView = findViewById(R.id.discussion_root_background_imageview);


        messageIdsToMarkAsRead = new ArrayList<>();
        editedMessageIdsToMarkAsSeen = new ArrayList<>();

        composeMessageFragment = new ComposeMessageFragment();
        composeMessageDelegate = composeMessageFragment.getComposeMessageDelegate();
        composeMessageDelegate.setAnimateLayoutChanges(false);
        composeMessageDelegate.addComposeMessageHeightListener(composeMessageHeightListener);
        composeMessageDelegate.setEmojiKeyboardAttachDelegate(new ComposeMessageFragment.EmojiKeyboardAttachDelegate() {
            @Override
            public void attachKeyboardFragment(@NonNull Fragment fragment) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.emoji_keyboard_placeholder, fragment);
                transaction.commit();
            }

            @Override
            public void detachKeyboardFragment(@NonNull Fragment fragment) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(fragment);
                transaction.commit();
            }
        });
        discussionDelegate = new DiscussionDelegate() {
            @Override
            public void markMessagesRead() {
                DiscussionActivity.this.markMessagesRead(false);
            }

            @Override
            public void doNotMarkAsReadOnPause() {
                markAsReadOnPause = false;
            }

            @Override
            public void scrollToMessage(long messageId) {
                if (messageListAdapter != null) {
                    messageListAdapter.requestScrollToMessageId(messageId, false, true);
                }
            }

            @Override
            public void replyToMessage(long discussionId, long messageId) {
                if (composeMessageViewModel != null) {
                    App.runThread(new SetDraftReplyTask(discussionId, messageId, composeMessageViewModel.getRawNewMessageText() == null ? null : composeMessageViewModel.getRawNewMessageText().toString()));
                    if (composeMessageDelegate != null) {
                        composeMessageDelegate.showSoftInputKeyboard();
                    }
                }
            }

            @Override
            public void initiateMessageForward(long messageId, Runnable openDialogCallback) {
                if (discussionViewModel != null) {
                    discussionViewModel.setMessageIdsToForward(Collections.singletonList(messageId));
                    Utils.openForwardMessageDialog(DiscussionActivity.this, Collections.singletonList(messageId), openDialogCallback);
                }
            }

            @Override
            public void selectMessage(long messageId, boolean forwardable) {
                if (discussionViewModel != null) {
                    discussionViewModel.selectMessageId(messageId, forwardable);
                }
            }

            @Override
            public void setAdditionalBottomPadding(int paddingPx) {
                additionalHeightForPopup = paddingPx;
                composeMessageHeightListener.onNewComposeMessageHeight(-1);
            }
        };
        composeMessageFragment.setDiscussionDelegate(discussionDelegate);
        composeMessageFragment.setAudioAttachmentServiceBinding(audioAttachmentServiceBinding);

        spacer = findViewById(R.id.spacer);

        discussionLockedGroup = findViewById(R.id.discussion_locked_group);
        discussionLockedImage = discussionLockedGroup.findViewById(R.id.discussion_locked_icon);
        discussionLockedMessage = discussionLockedGroup.findViewById(R.id.discussion_locked_message);
        discussionLockedGroup.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> recomputeLockAndNoChannelHeight());

        discussionNoChannelGroup = findViewById(R.id.discussion_no_channel_group);
        discussionNoChannelImageView = findViewById(R.id.discussion_no_channel_image_view);
        discussionNoChannelMessage = findViewById(R.id.discussion_no_channel_message);
        discussionNoChannelGroup.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> recomputeLockAndNoChannelHeight());

        // messages recycler view
        messageRecyclerView = findViewById(R.id.message_list_recycler_view);
        messageRecyclerView.setItemAnimator(null);
        new Handler(Looper.getMainLooper()).postDelayed(() -> messageRecyclerView.setItemAnimator(new DefaultItemAnimator()), 1000);

        messageListAdapter = new MessageListAdapter(this);

        messageRecyclerView.setAdapter(messageListAdapter);
        messageListLinearLayoutManager = new LinearLayoutManager(this);
        messageListLinearLayoutManager.setStackFromEnd(true);
        messageRecyclerView.setLayoutManager(messageListLinearLayoutManager);

        View loadingSpinner = findViewById(R.id.message_loading_spinner);
        messageRecyclerView.setLoadingSpinner(loadingSpinner);

        MessageListScrollListener messageListScrollListener = new MessageListScrollListener();
        messageRecyclerView.addOnScrollListener(messageListScrollListener);

        messageRecyclerView.addOnScrollStateChangedListener(state -> {
            boolean wasScrolling = scrolling;
            scrolling = (state == RecyclerView.SCROLL_STATE_DRAGGING) || (state == RecyclerView.SCROLL_STATE_SETTLING);
            if (scrolling ^ wasScrolling) {
                messageRecyclerView.invalidate();
            }
            messageDateItemDecoration.setScrolling(scrolling);
            if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
                if (composeMessageDelegate != null) {
                    composeMessageDelegate.hideSoftInputKeyboard();
                }
            }
        });

        // enable Message swiping
        MessageListAdapter.SwipeCallback swipeCallback = messageListAdapter.getSwipeCallback();
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(messageRecyclerView);


        scrollDownFab = findViewById(R.id.discussion_scroll_down_fab);
        scrollDownFab.setOnClickListener(this);



        // when a cached name or photo changes --> reload the messages
        AppSingleton.getContactNamesCache().observe(this, cache -> messageListAdapter.notifyDataSetChanged());
        AppSingleton.getContactHuesCache().observe(this, cache -> messageListAdapter.notifyDataSetChanged());
        AppSingleton.getContactPhotoUrlsCache().observe(this, cache -> messageListAdapter.notifyDataSetChanged());
        AppSingleton.getContactKeycloakManagedCache().observe(this, cache -> messageListAdapter.notifyDataSetChanged());

        actionModeCallback = new ActionMode.Callback() {
            private MenuInflater inflater;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                inflater = mode.getMenuInflater();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                List<Long> messages = discussionViewModel.getSelectedMessageIds().getValue();
                if (messages == null) {
                    return false;
                }
                menu.clear();
                inflater.inflate(R.menu.action_menu_discussion_multiple_selection, menu);
                if (discussionViewModel.areAllSelectedMessagesForwardable()) {
                    inflater.inflate(R.menu.action_menu_discussion_forward, menu);
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.action_delete_messages) {
                    final List<Long> selectedMessageIds = discussionViewModel.getSelectedMessageIds().getValue();
                    if (selectedMessageIds != null) {
                        Discussion discussion = discussionViewModel.getDiscussion().getValue();
                        if (discussion != null) {
                            if (locked != null && !locked) {
                                final SecureDeleteEverywhereDialogBuilder builder = new SecureDeleteEverywhereDialogBuilder(DiscussionActivity.this, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_confirm_deletion)
                                        .setMessage(getResources().getQuantityString(R.plurals.dialog_message_delete_messages, selectedMessageIds.size(), selectedMessageIds.size()))
                                        .setDeleteCallback(deleteEverywhere -> {
                                            App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, selectedMessageIds, deleteEverywhere));
                                            discussionViewModel.deselectAll();
                                        });
                                if (selectedMessageIds.size() == 1) {
                                    builder.setType(SecureDeleteEverywhereDialogBuilder.TYPE.SINGLE_MESSAGE);
                                } else {
                                    builder.setType(SecureDeleteEverywhereDialogBuilder.TYPE.MULTIPLE_MESSAGE);
                                }
                                builder.create().show();
                            } else {
                                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(DiscussionActivity.this, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_confirm_deletion)
                                        .setMessage(getResources().getQuantityString(R.plurals.dialog_message_delete_messages, selectedMessageIds.size(), selectedMessageIds.size()))
                                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                            App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, selectedMessageIds, false));
                                            discussionViewModel.deselectAll();
                                        })
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                builder.create().show();
                            }
                        }
                    }
                    return true;
                } else if (item.getItemId() == R.id.action_forward_messages) {
                    final List<Long> selectedMessageIds = discussionViewModel.getSelectedMessageIds().getValue();
                    if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
                        discussionViewModel.setMessageIdsToForward(new ArrayList<>(selectedMessageIds));
                        Utils.openForwardMessageDialog(DiscussionActivity.this, selectedMessageIds, discussionViewModel::deselectAll);
                    }
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                discussionViewModel.deselectAll();
                actionMode = null;
            }
        };




        discussionViewModel.getSelectedMessageIds().observe(this, messageListAdapter.selectedMessageIdsObserver);

        discussionViewModel.getDiscussion().observe(this, discussion -> {
            if (discussion == null) {
                toolBarTitle.setText(null);
                toolBarSubtitle.setVisibility(View.GONE);
                toolBarInitialView.setUnknown();
                toolbarClickedCallback = null;
                finishAndClearViewModel();
                return;
            }
            invalidateOptionsMenu();
            toolBarInitialView.setDiscussion(discussion);
            if (discussion.bytesGroupOwnerAndUid != null) {
                toolBarTitle.setText(discussion.title);
                toolbarClickedCallback = () -> App.openGroupDetailsActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
                discussionNoChannelMessage.setText(R.string.message_discussion_no_channel_group);
                if (discussion.active) {
                    setLocked(false, false);
                } else {
                    setLocked(true, true);
                }
            } else if (discussion.bytesContactIdentity != null) {
                // don't set the title yet, it will be set once we get the contact
                toolbarClickedCallback = () -> App.openContactDetailsActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
                discussionNoChannelMessage.setText(R.string.message_discussion_no_channel);
                if (discussion.active) {
                    setLocked(false, false);
                } else {
                    setLocked(true, true);
                }
            } else {
                toolBarTitle.setText(discussion.title);
                toolbarClickedCallback = null;
                discussionNoChannelMessage.setText(null);
                setLocked(true, false);
            }
            // display sender name for group discussions or locked discussions
            messageListAdapter.setShowInboundSenderName(discussion.bytesContactIdentity == null);

            if (discussion.unread) {
                App.runThread(() -> AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(discussion.id, false));
            }
        });

        discussionViewModel.getDiscussionContacts().observe(this, (List<Contact> contacts) -> {
            if (contacts != null) {
                if (messageListAdapter != null) {
                    messageListAdapter.notifyDataSetChanged();
                }

                // only called if discussion is not locked
                Discussion discussion = discussionViewModel.getDiscussion().getValue();
                if (discussion != null) {
                    if (discussion.bytesGroupOwnerAndUid != null) {
                        StringBuilder sb = new StringBuilder();
                        String joiner = getString(R.string.text_contact_names_separator);
                        boolean first = true;
                        for (Contact contact : contacts) {
                            if (!first) {
                                sb.append(joiner);
                            }
                            first = false;
                            sb.append(contact.getCustomDisplayName());
                        }
                        toolBarSubtitle.setVisibility(View.VISIBLE);
                        toolBarSubtitle.setText(sb.toString());
                    } else if (discussion.bytesContactIdentity != null && contacts.size() == 1) {
                        Contact contact = contacts.get(0);
                        JsonIdentityDetails identityDetails = contact.getIdentityDetails();
                        if (identityDetails != null) {
                            if (contact.customDisplayName != null) {
                                toolBarTitle.setText(contact.customDisplayName);
                                toolBarSubtitle.setVisibility(View.VISIBLE);
                                toolBarSubtitle.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
                            } else {
                                toolBarTitle.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                                String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                                if (posComp != null) {
                                    toolBarSubtitle.setVisibility(View.VISIBLE);
                                    toolBarSubtitle.setText(posComp);
                                } else {
                                    toolBarSubtitle.setVisibility(View.GONE);
                                }
                            }
                        } else {
                            toolBarTitle.setText(discussion.title);
                            toolBarSubtitle.setVisibility(View.GONE);
                        }
                    } else {
                        toolBarSubtitle.setVisibility(View.GONE);
                    }

                    boolean hasChannel = contacts.size() == 0;
                    for (Contact contact : contacts) {
                        if (contact.establishedChannelCount > 0) {
                            hasChannel = true;
                            break;
                        }
                    }
                    if (hasChannel || !discussion.active) {
                        if (discussionNoChannelGroup.getVisibility() != View.GONE) {
                            discussionNoChannelGroup.setVisibility(View.GONE);
                            discussionNoChannelImageView.setImageDrawable(null);
                            recomputeLockAndNoChannelHeight();
                        }
                    } else {
                        if (discussionNoChannelGroup.getVisibility() != View.VISIBLE) {
                            discussionNoChannelGroup.setVisibility(View.VISIBLE);
                            final AnimatedVectorDrawableCompat animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots);
                            if (animated != null) {
                                animated.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        new Handler(Looper.getMainLooper()).post(animated::start);
                                    }
                                });
                                discussionNoChannelImageView.setImageDrawable(animated);
                                animated.start();
                            }
                            recomputeLockAndNoChannelHeight();
                        }
                    }
                }
            }
        });

        discussionViewModel.getMessages().observe(this, messageListAdapter);


        discussionViewModel.getDiscussionCustomization().observe(this, discussionCustomization -> {
            // reload menu to show notification muted icon if needed
            invalidateOptionsMenu();

            // background color and image
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
                        new Handler(Looper.getMainLooper()).post(()->rootBackgroundImageView.setImageBitmap(finalBitmap));
                    });
                    rootBackgroundImageView.setBackgroundColor(0x00ffffff);
                } else {
                    rootBackgroundImageView.setImageDrawable(null);
                    DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                    if (colorJson != null) {
                        int color = colorJson.color + ((int) (colorJson.alpha * 255) << 24);
                        rootBackgroundImageView.setBackgroundColor(color);
                    } else {
                        rootBackgroundImageView.setBackgroundColor(ContextCompat.getColor(DiscussionActivity.this, R.color.almostWhite));
                    }
                }
            } else {
                rootBackgroundImageView.setImageDrawable(null);
                rootBackgroundImageView.setBackgroundColor(ContextCompat.getColor(DiscussionActivity.this, R.color.almostWhite));
            }

            // readReceipt
            boolean sendReadReceipt;
            if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
                sendReadReceipt = discussionCustomization.prefSendReadReceipt;
            } else {
                sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
            }
            if (sendReadReceipt && !DiscussionActivity.this.sendReadReceipt) {
                // receipts were just switched to true, or settings were loaded --> send read receipts for all messages already ready for notification
                DiscussionActivity.this.sendReadReceipt = true;
                Discussion discussion = discussionViewModel.getDiscussion().getValue();
                App.runThread(()-> {
                    for (Long messageId: messageIdsToMarkAsRead) {
                        if (messageId != null) {
                            Message message = AppDatabase.getInstance().messageDao().get(messageId);
                            if (message != null && message.messageType == Message.TYPE_INBOUND_MESSAGE) {
                                message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_READ);
                            }
                        }
                    }
                });
            } else {
                DiscussionActivity.this.sendReadReceipt = sendReadReceipt;
            }

            boolean retainWipedOutboundMessages;
            if (discussionCustomization != null && discussionCustomization.prefRetainWipedOutboundMessages != null) {
                retainWipedOutboundMessages = discussionCustomization.prefRetainWipedOutboundMessages;
            } else {
                retainWipedOutboundMessages = SettingsActivity.getDefaultRetainWipedOutboundMessages();
            }
            DiscussionActivity.this.retainWipedOutboundMessages = retainWipedOutboundMessages;
        });


        messageDateItemDecoration = new MessageDateItemDecoration(this, messageRecyclerView, messageListAdapter);
        newMessagesItemDecoration = new NewMessagesItemDecoration(this, messageListAdapter, messageDateItemDecoration);
        messageRecyclerView.addItemDecoration(newMessagesItemDecoration);
        messageRecyclerView.addItemDecoration(messageDateItemDecoration);

        messagesWithTimerMap = new ConcurrentHashMap<>();

        // compute first reactions dimensions
        computeDimensions();

        handleIntent(getIntent());
    }

    private void computeDimensions() {
        // attachments dimensions
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        attachmentSpace = 2 * (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MessageAttachmentAdapter.AttachmentSpaceItemDecoration.attachmentDpSpace, metrics);
        attachmentRecyclerViewWidth = Math.min(
                metrics.widthPixels - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88, metrics),
                Math.max(
                        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 392, metrics), // for screens larger than 480dp, the width of a cell if 400dp
                        (int) (.6* metrics.widthPixels - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, metrics))
                )
        );
        attachmentFileHeight = getResources().getDimensionPixelSize(R.dimen.attachment_small_preview_size);
        statusIconWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26, metrics);
        noStatusIconWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, metrics);
    }

    private int currentComposeMessageHeight = 0;
    private int currentSpacerBottomMargin = 0;
    private int currentLockHeight = 0;
    private int currentNoChannelHeight = 0;
    private int additionalHeightForPopup = 0;
    AnimatorSet animatorSet = null;


    ComposeMessageFragment.ComposeMessageHeightListener composeMessageHeightListener = (int heightPixels) -> {
        if (heightPixels != -1) {
            currentComposeMessageHeight = heightPixels;
        }
        ConstraintLayout.LayoutParams spacerLayoutParams = (ConstraintLayout.LayoutParams) spacer.getLayoutParams();

        if (animateLayoutChanges) {
            ValueAnimator spacerAnimator = ValueAnimator.ofInt(currentSpacerBottomMargin, currentComposeMessageHeight);
            spacerAnimator.addUpdateListener((ValueAnimator animation) -> {
                currentSpacerBottomMargin = (int) animation.getAnimatedValue();
                spacerLayoutParams.bottomMargin = currentSpacerBottomMargin;
                spacer.setLayoutParams(spacerLayoutParams);
            });
            spacerAnimator.setDuration(150);

            ValueAnimator recyclerAnimator = ValueAnimator.ofInt(messageRecyclerView.getPaddingBottom(), additionalHeightForPopup + currentComposeMessageHeight + currentLockHeight + currentNoChannelHeight);
            recyclerAnimator.addUpdateListener((ValueAnimator animation) -> messageRecyclerView.setPadding(0, messageRecyclerView.getPaddingTop(), 0, (int) animation.getAnimatedValue()));
            recyclerAnimator.setDuration(150);

            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.play(spacerAnimator).with(recyclerAnimator);
            animatorSet.start();
        } else {
            currentSpacerBottomMargin = currentComposeMessageHeight;
            spacerLayoutParams.bottomMargin = currentSpacerBottomMargin;
            spacer.setLayoutParams(spacerLayoutParams);

            messageRecyclerView.setPadding(0, messageRecyclerView.getPaddingTop(), 0, additionalHeightForPopup + currentComposeMessageHeight + currentLockHeight + currentNoChannelHeight);
        }
    };

    private void recomputeLockAndNoChannelHeight() {
        int lockHeight;
        int noChannelHeight;
        if (discussionLockedGroup.getVisibility() == View.VISIBLE) {
            lockHeight = discussionLockedGroup.getHeight();
        } else {
            lockHeight = 0;
        }
        if (discussionNoChannelGroup.getVisibility() == View.VISIBLE) {
            noChannelHeight = discussionNoChannelGroup.getHeight();
        } else {
            noChannelHeight = 0;
        }
        if (lockHeight != currentLockHeight || noChannelHeight != currentNoChannelHeight) {
            currentLockHeight = lockHeight;
            currentNoChannelHeight = noChannelHeight;
            new Handler(Looper.getMainLooper()).post(() -> composeMessageHeightListener.onNewComposeMessageHeight(-1));
        }
    }


    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        if (intent.hasExtra(DISCUSSION_ID_INTENT_EXTRA)) {
            final long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, 0);
            final long messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1);
            messageListAdapter.onChanged(null);
            App.runThread(() -> {
                Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
                runOnUiThread(() -> {
                    if (discussion == null) {
                        discussionNotFound();
                    } else {
                        setDiscussionId(discussion.id, intent);
                        if (messageId != -1) {
                            messageListAdapter.requestScrollToMessageId(messageId, true, true);
                        }
                    }
                });
            });
        } else if (intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA) && intent.hasExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA)) {
            final byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
            final byte[] bytesContactIdentity = intent.getByteArrayExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA);
            messageListAdapter.onChanged(null);
            App.runThread(() -> {
                Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                runOnUiThread(() -> {
                    if (discussion == null) {
                        discussionNotFound();
                    } else {
                        setDiscussionId(discussion.id, intent);
                    }
                });
            });
        } else if (intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA) && intent.hasExtra(BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA)) {
            final byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
            final byte[] bytesGroupOwnerAndUid = intent.getByteArrayExtra(BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA);
            messageListAdapter.onChanged(null);
            App.runThread(() -> {
                Discussion discussion = AppDatabase.getInstance().discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                runOnUiThread(() -> {
                    if (discussion == null) {
                        discussionNotFound();
                    } else {
                        setDiscussionId(discussion.id, intent);
                    }
                });
            });
        } else {
            finishAndClearViewModel();
            Logger.w("Missing discussion extras in intent.");
        }
    }

    private void discussionNotFound() {
        finishAndClearViewModel();
        App.toast(R.string.toast_message_discussion_not_found, Toast.LENGTH_SHORT);
    }

    private void finishAndClearViewModel() {
        saveDraft();
        discussionViewModel.deselectAll();
        discussionViewModel.setDiscussionId(null);
        finish();
    }

    private void setDiscussionId(long discussionId, Intent intent) {
        AndroidNotificationManager.setCurrentShowingDiscussionId(discussionId);
        AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
        AndroidNotificationManager.clearGroupNotification(discussionId);
        AndroidNotificationManager.clearMissedCallNotification(discussionId);
        AndroidNotificationManager.clearNeutralNotification();

        remoteDeletedMessageDeleter.clear();

        if ((discussionViewModel.getDiscussionId() != null) && (discussionId != discussionViewModel.getDiscussionId())) {
            markMessagesRead(true);
            saveDraft();
        }

        discussionViewModel.setDiscussionId(discussionId);
        if (composeMessageDelegate != null) {
            composeMessageDelegate.setDiscussionId(discussionId);
        }

        String remoteInputDraftText = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            remoteInputDraftText = intent.getStringExtra(Notification.EXTRA_REMOTE_INPUT_DRAFT);
        }
        if (remoteInputDraftText != null) {
            App.runThread(new ReplaceDiscussionDraftTask(discussionId, remoteInputDraftText, null));
        }
    }

    private final Map<Long, byte[]> remoteDeletedMessageDeleter = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, MessageListAdapter.MessageViewHolder> messagesWithTimerMap;
    private Timer updateTimersTimer = null;
    private boolean markAsReadOnPause = true;

    @Override
    protected void onResume() {
        super.onResume();
        if (screenShotBlockedForEphemeral) {
            Window window = getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        markAsReadOnPause = true;

        if (discussionViewModel.getDiscussionId() != null) {
            AndroidNotificationManager.setCurrentShowingDiscussionId(discussionViewModel.getDiscussionId());
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionViewModel.getDiscussionId());
        }
        updateTimersTimer = new Timer("DiscussionActivity-expirationTimers");
        updateTimersTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    long timestamp = System.currentTimeMillis();
                    for (MessageListAdapter.MessageViewHolder viewHolder : new ArrayList<>(messagesWithTimerMap.values())) {
                        if (viewHolder.expirationTimestamp == null) {
                            messagesWithTimerMap.remove(viewHolder.messageId);
                        } else {
                            viewHolder.updateTimerTextView(timestamp);
                        }
                    }
                });
            }
        }, 1000, 1000);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioAttachmentServiceBinding != null) {
            audioAttachmentServiceBinding.release();
        }
        if (composeMessageDelegate != null) {
            composeMessageDelegate.removeComposeMessageHeightListener(composeMessageHeightListener);
        }
        if (discussionViewModel != null && discussionViewModel.getDiscussionId() != null) {
            App.runThread(new ApplyDiscussionRetentionPoliciesTask(discussionViewModel.getDiscussionId()));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AndroidNotificationManager.setCurrentShowingDiscussionId(null);
        if (markAsReadOnPause) {
            markMessagesRead(true);
            saveDraft();
        }
        if (updateTimersTimer != null) {
            updateTimersTimer.cancel();
            updateTimersTimer = null;
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
            if (fullScreenImageFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(fullScreenImageFragment)
                        .commit();
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
        if (fullScreenImageFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit();
            return;
        }
        if (composeMessageDelegate != null && composeMessageDelegate.stopVoiceRecorderIfRecording()) {
            // do nothing --> recording was stopped by on back pressed
            return;
        }
        finishAndClearViewModel();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (messageRecyclerView != null && messageListAdapter != null) {
            View child = messageRecyclerView.findChildViewUnder(messageRecyclerView.getWidth()/2.f, messageRecyclerView.getHeight()/2.f);
            if (child != null) {
                int pos = messageRecyclerView.getChildAdapterPosition(child);
                messageRecyclerView.setAdapter(messageListAdapter);
                messageRecyclerView.scrollToPosition(pos);
            }
        }
        computeDimensions();
        if (newMessagesItemDecoration != null) {
            newMessagesItemDecoration.resetHeaderBitmapCache();
        }
        if (messageDateItemDecoration != null) {
            messageDateItemDecoration.resetHeaderBitmapCache();
        }
        if (messageRecyclerView != null) {
            messageRecyclerView.invalidateItemDecorations();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        Discussion discussion = discussionViewModel.getDiscussion().getValue();
        if (discussion != null) {
            getMenuInflater().inflate(R.menu.menu_discussion, menu);

            MenuItem searchItem = menu.findItem(R.id.action_search);
            new DiscussionSearch(this, menu, searchItem, messageListAdapter, messageListLinearLayoutManager);

            if (discussion.bytesGroupOwnerAndUid != null) {
                getMenuInflater().inflate(R.menu.menu_discussion_group, menu);
                if (discussion.active) {
                    getMenuInflater().inflate(R.menu.menu_discussion_group_call, menu);
                }
            } else if (discussion.bytesContactIdentity != null) {
                getMenuInflater().inflate(R.menu.menu_discussion_one_to_one, menu);
                if (discussion.active) {
                    getMenuInflater().inflate(R.menu.menu_discussion_one_to_one_call, menu);
                }
            }

            if (!discussion.active) {
                getMenuInflater().inflate(R.menu.menu_discussion_unblock, menu);
            }

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                getMenuInflater().inflate(R.menu.menu_discussion_shortcut, menu);
            }

            if (!discussion.isLocked()) {
                DiscussionCustomization discussionCustomization = discussionViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                    getMenuInflater().inflate(R.menu.menu_discussion_muted, menu);
                }
            }

            MenuItem deleteItem = menu.findItem(R.id.action_delete_discussion);
            if (deleteItem != null) {
                SpannableString spannableString = new SpannableString(deleteItem.getTitle());
                spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                deleteItem.setTitle(spannableString);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_call) {
            Discussion discussion = discussionViewModel.getDiscussion().getValue();
            if (discussion != null) {
                if (discussion.bytesContactIdentity != null) {
                    List<Contact> contacts = discussionViewModel.getDiscussionContacts().getValue();
                    if (contacts != null && contacts.size() > 0 && contacts.get(0).establishedChannelCount > 0) {
                        App.startWebrtcCall(this, discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
                    }
                } else if (discussion.bytesGroupOwnerAndUid != null) {
                    List<Contact> contacts = discussionViewModel.getDiscussionContacts().getValue();
                    if (contacts != null) {
                        List<byte[]> bytesContactIdentities = new ArrayList<>(contacts.size());
                        for (Contact contact : contacts) {
                            bytesContactIdentities.add(contact.bytesContactIdentity);
                        }
                        MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid, bytesContactIdentities);
                        multiCallStartDialogFragment.show(getSupportFragmentManager(), "dialog");
                    }
                }
            }
            return true;
        } else if (itemId == R.id.action_details) {
            if (toolbarClickedCallback != null) {
                toolbarClickedCallback.run();
            }
            return true;
        } else if (itemId == R.id.action_settings) {
            if (discussionViewModel.getDiscussionId() != null) {
                markAsReadOnPause = false;
                Discussion discussion = discussionViewModel.getDiscussion().getValue();
                if (discussion != null) {
                    Intent intent = new Intent(this, DiscussionSettingsActivity.class);
                    intent.putExtra(DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
                    intent.putExtra(DiscussionSettingsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, discussion.bytesOwnedIdentity);
                    intent.putExtra(DiscussionSettingsActivity.LOCKED_INTENT_EXTRA, discussion.isLocked());
                    if (discussion.bytesGroupOwnerAndUid != null) {
                        intent.putExtra(DiscussionSettingsActivity.BYTES_GROUP_OWNED_AND_UID_INTENT_EXTRA, discussion.bytesGroupOwnerAndUid);
                    }
                    startActivity(intent);
                }
            }
            return true;
        } else if (itemId == R.id.action_shortcut) {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                final Discussion discussion = discussionViewModel.getDiscussion().getValue();
                if (discussion != null) {
                    App.runThread(() -> {
                        ShortcutInfoCompat.Builder builder = ShortcutActivity.getShortcutInfo(discussion.id, discussion.title);
                        if (builder != null) {
                            try {
                                ShortcutInfoCompat shortcutInfo = builder.build();
                                ShortcutManagerCompat.requestPinShortcut(DiscussionActivity.this, shortcutInfo, null);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } else {
                App.toast(R.string.toast_message_shortcut_not_supported, Toast.LENGTH_SHORT);
            }
            return true;
        } else if (itemId == R.id.action_delete_discussion) {
            final Discussion discussion = discussionViewModel.getDiscussion().getValue();
            if (discussion != null) {
                if (locked != null && !locked) {
                    final SecureDeleteEverywhereDialogBuilder builder = new SecureDeleteEverywhereDialogBuilder(DiscussionActivity.this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_discussion)
                            .setMessage(getString(R.string.dialog_message_delete_discussion, discussion.title))
                            .setType(SecureDeleteEverywhereDialogBuilder.TYPE.DISCUSSION)
                            .setDeleteCallback(deleteEverywhere -> {
                                App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, discussion.id, deleteEverywhere, false));
                                finishAndClearViewModel();
                            });
                    builder.create().show();
                } else {
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_discussion)
                            .setMessage(getString(R.string.dialog_message_delete_discussion, discussion.title))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, discussion.id, false, false));
                                finishAndClearViewModel();
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                }
            }
            return true;
        } else if (itemId == R.id.action_unmute) {
            final DiscussionCustomization discussionCustomization = discussionViewModel.getDiscussionCustomization().getValue();
            if (discussionCustomization != null) {
                if (discussionCustomization.shouldMuteNotifications()) {
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_unmute_notifications)
                            .setPositiveButton(R.string.button_label_unmute_notifications, (dialog, which) -> App.runThread(() -> {
                                DiscussionCustomization reDiscussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionCustomization.discussionId);
                                reDiscussionCustomization.prefMuteNotifications = false;
                                AppDatabase.getInstance().discussionCustomizationDao().update(reDiscussionCustomization);
                            }))
                            .setNegativeButton(R.string.button_label_cancel, null);

                    if (discussionCustomization.prefMuteNotificationsTimestamp == null) {
                        builder.setMessage(R.string.dialog_message_unmute_notifications);
                    } else {
                        builder.setMessage(getString(R.string.dialog_message_unmute_notifications_muted_until, StringUtils.getLongNiceDateString(this, discussionCustomization.prefMuteNotificationsTimestamp)));
                    }
                    builder.create().show();
                }
            }
            return true;
        } else if (itemId == R.id.action_unblock) {
            final Discussion discussion = discussionViewModel.getDiscussion().getValue();
            if (discussion != null) {
                if (discussion.bytesContactIdentity != null) {
                    EnumSet<ObvContactActiveOrInactiveReason> notActiveReasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
                    if (notActiveReasons != null) {
                        if (notActiveReasons.contains(ObvContactActiveOrInactiveReason.REVOKED)
                                && !notActiveReasons.contains(ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED)) {
                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
                            builder.setTitle(R.string.dialog_title_unblock_revoked_contact_discussion)
                                    .setMessage(R.string.dialog_message_unblock_revoked_contact_discussion)
                                    .setNegativeButton(R.string.button_label_cancel, null)
                                    .setPositiveButton(R.string.button_label_unblock, (dialog, which) -> {
                                        if (!AppSingleton.getEngine().forcefullyUnblockContact(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity)) {
                                            App.toast(R.string.toast_message_failed_to_unblock_contact, Toast.LENGTH_SHORT);
                                        }
                                    });
                            builder.create().show();
                            return true;
                        }
                    }

                    if (!AppSingleton.getEngine().forcefullyUnblockContact(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity)) {
                        App.toast(R.string.toast_message_failed_to_unblock_contact, Toast.LENGTH_SHORT);
                    }
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.back_button || id == R.id.back_button_backdrop) {
            onBackPressed();
        } else if (id == R.id.title_bar_initial_view) {
            Fragment alreadyShownFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
            if (alreadyShownFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(alreadyShownFragment)
                        .commit();
            } else {
                if (view instanceof InitialView) {
                    String photoUrl = ((InitialView) view).getPhotoUrl();
                    if (photoUrl != null) {
                        FullScreenImageFragment fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl);
                        getSupportFragmentManager().beginTransaction()
                                .setCustomAnimations(R.anim.fade_in, 0)
                                .replace(R.id.overlay, fullScreenImageFragment, FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                                .commit();
                    }
                }
            }
        } else if (id == R.id.discussion_scroll_down_fab) {
            if ((messageListAdapter != null) && (messageListAdapter.messages) != null && (messageListAdapter.messages.size() > 0)) {
                if (messageRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    messageRecyclerView.smoothScrollToPosition(messageListAdapter.messages.size());
                } else {
                    messageRecyclerView.scrollToPosition(messageListAdapter.messages.size());
                }
            }
        }
    }





    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_SAVE_ATTACHMENT: {
                if (resultCode == RESULT_OK && data != null) {
                    final Uri uri = data.getData();
                    if (uri != null) {
                        App.runThread(() -> {
                            try (OutputStream os = DiscussionActivity.this.getContentResolver().openOutputStream(uri)) {
                                if (os == null) {
                                    throw new Exception("Unable to write to provided Uri");
                                }
                                if (longClickedFyleAndStatus == null) {
                                    throw new Exception();
                                }
                                // attachment was saved --> mark it as opened
                                longClickedFyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
                                try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(longClickedFyleAndStatus.fyle.filePath))) {
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
                break;
            }
            case REQUEST_CODE_SAVE_ALL_ATTACHMENTS: {
                if (resultCode == RESULT_OK && data != null && longClickedFyleAndStatus != null) {
                    Uri folderUri = data.getData();
                    if (folderUri != null && longClickedFyleAndStatus != null) {
                        long messageId = longClickedFyleAndStatus.fyleMessageJoinWithStatus.messageId;
                        App.runThread(new SaveMultipleAttachmentsTask(this, folderUri, messageId));
                    }
                }
                break;
            }
        }
    }

    private void markMessagesRead(final boolean wipeReadOnceMessages) {
        final Long[] messageIds = messageIdsToMarkAsRead.toArray(new Long[0]);
        final Long[] editedMessageIds = editedMessageIdsToMarkAsSeen.toArray(new Long[0]);
        editedMessageIdsToMarkAsSeen.clear();
        if (wipeReadOnceMessages) {
            // we keep the list if messages are not wiped yet
            messageIdsToMarkAsRead.clear();
        }
        App.runThread(() -> {
            AppDatabase db = AppDatabase.getInstance();
            db.messageDao().markMessagesRead(messageIds);
            db.messageDao().markEditedMessagesSeen(editedMessageIds);

            if (wipeReadOnceMessages) {
                for (Message message : db.messageDao().getWipeOnReadSubset(messageIds)) {
                    db.runInTransaction(() -> {
                        if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && retainWipedOutboundMessages) {
                            message.wipe(db);
                            message.deleteAttachments(db);
                        } else {
                            message.delete(db);
                        }
                    });
                }
            }
        });
    }

    private void saveDraft() {
        if (discussionViewModel.getDiscussionId() != null) {
            App.runThread(new SaveDraftTask(discussionViewModel.getDiscussionId(), composeMessageViewModel.getTrimmedNewMessageText(), composeMessageViewModel.getDraftMessage().getValue()));
        }
    }

    public void setLocked(boolean locked, boolean lockedAsInactive) {
        if (locked) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(composeMessageFragment);
            transaction.commit();
            composeMessageHeightListener.onNewComposeMessageHeight(0);

            discussionNoChannelGroup.setVisibility(View.GONE);
            discussionLockedGroup.setVisibility(View.VISIBLE);
            recomputeLockAndNoChannelHeight();
            if (lockedAsInactive) {
                discussionLockedImage.setImageResource(R.drawable.ic_block);
                discussionLockedMessage.setText(R.string.message_discussion_blocked);
            } else {
                discussionLockedImage.setImageResource(R.drawable.ic_lock);
                discussionLockedMessage.setText(R.string.message_discussion_locked);
            }
        } else if (this.locked == null || this.locked) {
            discussionLockedGroup.setVisibility(View.GONE);
            recomputeLockAndNoChannelHeight();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.compose_message_placeholder, composeMessageFragment);
            transaction.commit();
        }
        this.locked = locked;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            animateLayoutChanges = true;
            if (composeMessageFragment != null) {
                composeMessageDelegate.setAnimateLayoutChanges(true);
            }
        }, 500);
    }

    private void messageLongClicked(Message message, View messageView) {
        if (discussionViewModel.isSelectingForDeletion()) {
            discussionViewModel.selectMessageId(message.id, (message.messageType == Message.TYPE_OUTBOUND_MESSAGE || message.messageType == Message.TYPE_INBOUND_MESSAGE) && message.wipeStatus == Message.WIPE_STATUS_NONE);
        } else {
            if (discussionDelegate != null) {
                int[] posRecyclerView = new int[2];
                int[] posMessageView = new int[2];

                messageRecyclerView.getLocationInWindow(posRecyclerView);
                messageView.getLocationInWindow(posMessageView);
                new MessageLongPressPopUp(DiscussionActivity.this, discussionDelegate, messageRecyclerView, posMessageView[0] - posRecyclerView[0] + messageView.getWidth() / 2, posMessageView[1] - posRecyclerView[1], posRecyclerView[1] + messageRecyclerView.getHeight() - posMessageView[1] - messageView.getHeight(), message.id);
            }
        }
    }

    private void messageClicked(Message message) {
        if (discussionViewModel.isSelectingForDeletion()) {
            discussionViewModel.selectMessageId(message.id, (message.messageType == Message.TYPE_OUTBOUND_MESSAGE || message.messageType == Message.TYPE_INBOUND_MESSAGE) && message.wipeStatus == Message.WIPE_STATUS_NONE);
        }
    }


    // region Implement TextWatcher


    private FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus;

    @Override
    public void attachmentLongClicked(FyleMessageJoinWithStatusDao.FyleAndStatus longClickedFyleAndStatus, View clickedView, MessageAttachmentAdapter.Visibility visibility, boolean readOnce, boolean multipleAttachments) {
        App.runThread(() -> {
            Message message = AppDatabase.getInstance().messageDao().get(longClickedFyleAndStatus.fyleMessageJoinWithStatus.messageId);
            if (message == null) {
                return;
            }

            this.longClickedFyleAndStatus = longClickedFyleAndStatus;
            runOnUiThread(() -> {
                PopupMenu popup = new PopupMenu(this, clickedView);
                if (visibility == MessageAttachmentAdapter.Visibility.HIDDEN || readOnce) {
                    popup.inflate(R.menu.popup_attachment_delete);
                } else if (longClickedFyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) {
                    popup.inflate(R.menu.popup_attachment_incomplete_or_draft);
                } else if (longClickedFyleAndStatus.fyle.isComplete()) {
                    popup.inflate(R.menu.popup_attachment_complete);
                    if (message.status != Message.STATUS_UNPROCESSED
                            && message.status != Message.STATUS_COMPUTING_PREVIEW
                            && message.status != Message.STATUS_PROCESSING) {
                        popup.inflate(R.menu.popup_attachment_delete);
                    }
                    if (multipleAttachments) {
                        popup.inflate(R.menu.popup_attachment_save_all);
                    }
                } else {
                    if (longClickedFyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
                        popup.inflate(R.menu.popup_attachment_delete);
                    } else {
                        popup.inflate(R.menu.popup_attachment_incomplete_or_draft);
                    }
                }
                popup.setOnMenuItemClickListener(this);
                popup.show();
            });
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.popup_action_delete_attachment) {
            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(getString(R.string.dialog_message_delete_attachment, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fileName))
                    .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteAttachmentTask(longClickedFyleAndStatus)))
                    .setNegativeButton(R.string.button_label_cancel, null);
            builder.create().show();
            return true;
        } else if (itemId == R.id.popup_action_open_attachment) {
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(longClickedFyleAndStatus.fyleMessageJoinWithStatus.mimeType, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fileName)) && SettingsActivity.useInternalImageViewer()) {
                // we do not mark as opened here as this is done in the gallery activity
                App.openDiscussionGalleryActivity(this, discussionViewModel.getDiscussionId(), longClickedFyleAndStatus.fyleMessageJoinWithStatus.messageId, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fyleId);
                markAsReadOnPause = false;
            } else {
                App.openFyleInExternalViewer(this, longClickedFyleAndStatus, () -> {
                    markAsReadOnPause = false;
                    longClickedFyleAndStatus.fyleMessageJoinWithStatus.markAsOpened();
                });
            }
            return true;
        } else if (itemId == R.id.popup_action_share_attachment) {
            if (longClickedFyleAndStatus != null) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, longClickedFyleAndStatus.getContentUri());
                intent.setType(longClickedFyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                startActivity(Intent.createChooser(intent, getString(R.string.title_sharing_chooser)));
            }
            return true;
        } else if (itemId == R.id.popup_action_save_attachment) {
            if (longClickedFyleAndStatus != null && longClickedFyleAndStatus.fyle.isComplete()) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(longClickedFyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())
                        .putExtra(Intent.EXTRA_TITLE, longClickedFyleAndStatus.fyleMessageJoinWithStatus.fileName);
                App.startActivityForResult(this, intent, DiscussionActivity.REQUEST_CODE_SAVE_ATTACHMENT);
            }
            return true;
        } else if (itemId == R.id.popup_action_save_all_attachments) {
            if (longClickedFyleAndStatus != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(DiscussionActivity.this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_save_all_attachments)
                        .setMessage(R.string.dialog_message_save_all_attachments)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> App.startActivityForResult(this, new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), DiscussionActivity.REQUEST_CODE_SAVE_ALL_ATTACHMENTS))
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        }
        return false;
    }
    // endregion

    public class MessageListScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            boolean lastMessageVisible = messageListAdapter.messages == null || messageListLinearLayoutManager.findLastVisibleItemPosition() == (messageListAdapter.messages.size());
            if (lastMessageVisible) {
                if (scrollDownFab.getVisibility() == View.VISIBLE) {
                    scrollDownFab.hide();
                }
            } else {
                if (scrollDownFab.getVisibility() != View.VISIBLE) {
                    scrollDownFab.show();
                }
            }
        }
    }


    public class MessageListAdapter extends LoadAwareAdapter<MessageListAdapter.MessageViewHolder> implements Observer<List<Message>> {
        List<Message> messages;
        private List<Long> selectedMessageIds;
        private boolean selectingForDeletion;
        private boolean showInboundSenderName;
        private long requestedScrollToMessageId;
        private boolean requestedScrollToWithOffset;
        private boolean requestedScrollFlash;
        private long highlightOnBindMessageId;
        private DiscussionSearch.MessageHighlightInfo messageHighlightInfo;

        private final LayoutInflater inflater;
        private final RotateAnimation rotateAnimation;
        final Observer<List<Long>> selectedMessageIdsObserver;

        static final int COMPACT_MESSAGE_LINE_COUNT = 18;

        static final int STATUS_CHANGE_MASK = 1;
        static final int SELECTED_CHANGE_MASK = 2;
        static final int OUTBOUND_SENT_CHANGE_MASK = 4;
        static final int SHOW_TOP_HEADER_CHANGE_MASK = 8;
        static final int BODY_OR_HIGHLIGHT_CHANGE_MASK = 16;
        static final int MESSAGE_EXPAND_CHANGE_MASK = 32;
        static final int ATTACHMENT_COUNT_CHANGE_MASK = 64;
        static final int EDITED_CHANGE_MASK = 128;
        static final int REACTIONS_CHANGE_MASK = 256;
        static final int MISSED_COUNT_CHANGE_MASK = 512;
        static final int FORWARDED_CHANGE_MASK = 1024;

        @SuppressLint("NotifyDataSetChanged")
        MessageListAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
            setHasStableIds(true);
            rotateAnimation = new RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setInterpolator(new LinearInterpolator());
            rotateAnimation.setRepeatCount(Animation.INFINITE);
            rotateAnimation.setDuration(700);
            selectingForDeletion = false;
            selectedMessageIdsObserver = selectedMessageIds -> {
                boolean wasSelectingForDeletion = selectingForDeletion;
                MessageListAdapter.this.selectedMessageIds = selectedMessageIds;
                selectingForDeletion = discussionViewModel.isSelectingForDeletion();
                if (!wasSelectingForDeletion && !selectingForDeletion) {
                    return;
                } else if (!wasSelectingForDeletion) {
                    // if selection for deletion just started, create the actionMode
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                    actionMode = startSupportActionMode(actionModeCallback);
                } else if (!selectingForDeletion) {
                    // if selection for deletion just ended, finish the actionMode
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                } else {
                    // a message was (de-)selected, we simply check if the list size was or becomes 1 to update the menu
                    if (actionMode != null) {
                        actionMode.invalidate();
                    }
                }
                notifyDataSetChanged();
                if ((actionMode != null) && (selectedMessageIds != null)) {
                    actionMode.setTitle(getResources().getQuantityString(R.plurals.action_mode_title_discussion, selectedMessageIds.size(), selectedMessageIds.size()));
                }
            };
            requestedScrollToMessageId = -1;
            showInboundSenderName = false;
        }

        SwipeCallback getSwipeCallback() {
            if (swipeCallback == null) {
                swipeCallback = new SwipeCallback();
            }
            return swipeCallback;
        }

        void requestScrollToMessageId(long messageId, boolean withOffset, boolean flash) {
            this.requestedScrollToMessageId = messageId;
            this.requestedScrollToWithOffset = withOffset;
            this.requestedScrollFlash = flash;
            if (messages != null) {
                doScrollToMessageId();
            }
        }

        private void doScrollToMessageId() {
            if (requestedScrollToMessageId == -1) {
                return;
            }
            int position = messages.size();
            ListIterator<Message> iterator = messages.listIterator(position);
            while (iterator.hasPrevious()) {
                position--;
                Message message = iterator.previous();
                if (message.id == requestedScrollToMessageId) {
                    if (requestedScrollToWithOffset) {
                        RecyclerView.LayoutManager layoutManager = messageRecyclerView.getLayoutManager();
                        if (layoutManager instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position + 1, getResources().getDimensionPixelSize(R.dimen.new_message_scroll_offset));
                        } else {
                            messageRecyclerView.smoothScrollToPosition(position + 1);
                        }
                    } else {
                        messageRecyclerView.smoothScrollToPosition(position + 1);
                    }
                    if (requestedScrollFlash) {
                        View view = messageListLinearLayoutManager.findViewByPosition(position + 1);
                        if (view != null) {
                            highlightView(view);
                        } else {
                            highlightOnBindMessageId = requestedScrollToMessageId;
                        }
                    }
                    break;
                }
            }
            this.requestedScrollToMessageId = -1;
        }

        private void highlightView(View view) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Drawable foreground = view.getForeground();
                if (foreground instanceof RippleDrawable) {
                    final RippleDrawable rippleDrawable = (RippleDrawable) foreground;
                    rippleDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                    new Handler(Looper.getMainLooper()).postDelayed(() -> rippleDrawable.setState(new int[]{}), 1000);
                }
            }
            highlightOnBindMessageId = -1;
        }

        void setShowInboundSenderName(boolean showInboundSenderName) {
            if (this.showInboundSenderName ^ showInboundSenderName) {
                this.showInboundSenderName = showInboundSenderName;
                notifyDataSetChanged();
            }
        }


        public void setMessageHighlightInfo(@Nullable DiscussionSearch.MessageHighlightInfo messageHighlightInfo) {
            Integer previousPosition = null;
            if (this.messageHighlightInfo != null && (messageHighlightInfo == null || messageHighlightInfo.messageId != this.messageHighlightInfo.messageId)) {
                for (int i = 0; i < messageListLinearLayoutManager.getChildCount(); i++) {
                    View view = messageListLinearLayoutManager.getChildAt(i);
                    if (view != null) {
                        MessageViewHolder viewHolder = (MessageViewHolder) messageRecyclerView.getChildViewHolder(view);
                        if (viewHolder.messageId == this.messageHighlightInfo.messageId) {
                            previousPosition = viewHolder.getLayoutPosition();
                        }
                    }
                }
            }
            this.messageHighlightInfo = messageHighlightInfo;
            if (previousPosition != null) {
                notifyItemChanged(previousPosition, BODY_OR_HIGHLIGHT_CHANGE_MASK);
            }
        }


        @Override
        public void onChanged(@Nullable final List<Message> messages) {
            boolean scroll = false;
            int lastVisible = messageListLinearLayoutManager.findLastVisibleItemPosition();
            if (((messages != null) && (this.messages != null) && (lastVisible >= this.messages.size() - 1) && (messages.size() != this.messages.size()))
                    || (messages != null && this.messages == null)) {
                scroll = true;
            }
            if ((this.messages != null) && (messages != null)) {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    final int[] payloadCache = new int[messages.size()];
                    final boolean[] payloadComputed = new boolean[payloadCache.length];

                    @NonNull
                    final List<Message> oldList = MessageListAdapter.this.messages;
                    @NonNull
                    final List<Message> newList = messages;

                    @Override
                    public int getOldListSize() {
                        return oldList.size() + 1;
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size() + 1;
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        if (oldItemPosition == 0) {
                            return newItemPosition == 0;
                        }
                        if (newItemPosition == 0) {
                            return false;
                        }
                        return oldList.get(oldItemPosition - 1).id == newList.get(newItemPosition - 1).id;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        //noinspection ConstantConditions
                        return (int) getChangePayload(oldItemPosition, newItemPosition) == 0;
                    }

                    @Override
                    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                        if (newItemPosition == 0) {
                            return 0;
                        }
                        oldItemPosition--;
                        newItemPosition--;
                        if (payloadComputed[newItemPosition]) {
                            return payloadCache[newItemPosition];
                        }

                        Message oldItem = oldList.get(oldItemPosition);
                        Message newItem = newList.get(newItemPosition);
                        int changesMask = 0;

                        if (oldItem.messageType != newItem.messageType) {
                            // message type changed (limited visibility message was clicked) --> rebind everything
                            return -1;
                        }

                        if (oldItem.wipeStatus != newItem.wipeStatus) {
                            // wipe status changed --> rebind everything
                            return -1;
                        }

                        if (oldItem.status != newItem.status && (
                                (oldItem.status != Message.STATUS_UNPROCESSED && oldItem.status != Message.STATUS_COMPUTING_PREVIEW)
                                        || (newItem.status != Message.STATUS_COMPUTING_PREVIEW && newItem.status != Message.STATUS_PROCESSING)
                        )) {
                            changesMask |= STATUS_CHANGE_MASK;
                            if (newItem.status == Message.STATUS_SENT
                                    || newItem.status == Message.STATUS_UNDELIVERED) {
                                changesMask |= OUTBOUND_SENT_CHANGE_MASK;
                            }
                        }

                        if ((selectedMessageIds != null) && (selectedMessageIds.contains(oldItem.id) ^ selectedMessageIds.contains(newItem.id))) {
                            changesMask |= SELECTED_CHANGE_MASK;
                        }

                        if ((oldItemPosition == 0 || Utils.notTheSameDay(oldItem.timestamp, oldList.get(oldItemPosition - 1).timestamp)) ^
                                (newItemPosition == 0 || Utils.notTheSameDay(newItem.timestamp, newList.get(newItemPosition - 1).timestamp))) {
                            changesMask |= SHOW_TOP_HEADER_CHANGE_MASK;
                        }

                        if (!Objects.equals(oldItem.contentBody, newItem.contentBody)) {
                            changesMask |= BODY_OR_HIGHLIGHT_CHANGE_MASK;
                        }

                        if (oldItem.totalAttachmentCount != newItem.totalAttachmentCount ||
                                oldItem.imageCount != newItem.imageCount ||
                                !Objects.equals(oldItem.imageResolutions, newItem.imageResolutions)) {
                            changesMask |= ATTACHMENT_COUNT_CHANGE_MASK;
                        }

                        if (oldItem.edited != newItem.edited) {
                            changesMask |= EDITED_CHANGE_MASK;
                        }

                        if (oldItem.forwarded != newItem.forwarded) {
                            changesMask |= FORWARDED_CHANGE_MASK;
                        }

                        if (!Objects.equals(oldItem.reactions, newItem.reactions)) {
                            changesMask |= REACTIONS_CHANGE_MASK;
                        }

                        if (oldItem.missedMessageCount != newItem.missedMessageCount) {
                            changesMask |= MISSED_COUNT_CHANGE_MASK;
                        }

                        payloadCache[newItemPosition] = changesMask;
                        payloadComputed[newItemPosition] = true;
                        return changesMask;
                    }
                });
                this.messages = messages;
                result.dispatchUpdatesTo(this);
            } else {
                this.messages = messages;
                notifyDataSetChanged();
            }
            if (requestedScrollToMessageId != -1 && messages != null) {
                messageRecyclerView.scrollToPosition(messages.size());
                doScrollToMessageId();
            } else if (scroll) {
                if (messages.size() > 0) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        messageRecyclerView.scrollToPosition(messages.size());
                        if (scrollDownFab.getVisibility() == View.VISIBLE) {
                            scrollDownFab.hide();
                        }
                    }, 200);
                }
            }

        }

        @Override
        public int getItemViewType(int position) {
            if (messages == null || position == 0) {
                return ViewType.DISCLAIMER.ordinal();
            }
            Message message = messages.get(position - 1);
            switch (message.messageType) {
                case Message.TYPE_OUTBOUND_MESSAGE:
                    if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                        if (message.hasAttachments()) {
                            return ViewType.OUTBOUND_EPHEMERAL_WITH_ATTACHMENT.ordinal();
                        } else {
                            return ViewType.OUTBOUND_EPHEMERAL.ordinal();
                        }
                    } else {
                        if (message.hasAttachments()) {
                            return ViewType.OUTBOUND_WITH_ATTACHMENT.ordinal();
                        } else {
                            return ViewType.OUTBOUND.ordinal();
                        }
                    }
                case Message.TYPE_INBOUND_EPHEMERAL_MESSAGE:
                    if (message.hasAttachments()) {
                        return ViewType.INBOUND_EPHEMERAL_WITH_ATTACHMENT.ordinal();
                    } else {
                        return ViewType.INBOUND_EPHEMERAL.ordinal();
                    }
                case Message.TYPE_INBOUND_MESSAGE:
                    if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                        if (message.hasAttachments()) {
                            return ViewType.INBOUND_EPHEMERAL_WITH_ATTACHMENT.ordinal();
                        } else {
                            return ViewType.INBOUND_EPHEMERAL.ordinal();
                        }
                    } else {
                        if (message.hasAttachments()) {
                            return ViewType.INBOUND_WITH_ATTACHMENT.ordinal();
                        } else {
                            return ViewType.INBOUND.ordinal();
                        }
                    }
                case Message.TYPE_DISCUSSION_SETTINGS_UPDATE:
                    return ViewType.SETTINGS_UPDATE.ordinal();
                case Message.TYPE_PHONE_CALL:
                    return ViewType.PHONE_CALL.ordinal();
                case Message.TYPE_NEW_PUBLISHED_DETAILS:
                    return ViewType.NEW_PUBLISHED_DETAILS.ordinal();
                case Message.TYPE_GROUP_MEMBER_JOINED:
                case Message.TYPE_GROUP_MEMBER_LEFT:
                    return ViewType.INFO_GROUP_MEMBER.ordinal();
                case Message.TYPE_LEFT_GROUP:
                case Message.TYPE_CONTACT_DELETED:
                case Message.TYPE_DISCUSSION_REMOTELY_DELETED:
                case Message.TYPE_CONTACT_INACTIVE_REASON:
                default:
                    return ViewType.INFO.ordinal();
            }
        }

        @NonNull
        @Override
        public MessageListAdapter.MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            ViewType viewType;
            try {
                viewType = ViewType.values()[vt];
            } catch (Exception e) {
                viewType = ViewType.INBOUND;
            }
            switch (viewType) {
                case DISCLAIMER: {
                    View view = inflater.inflate(R.layout.item_view_message_disclaimer, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case OUTBOUND_EPHEMERAL:
                case OUTBOUND: {
                    View view = inflater.inflate(R.layout.item_view_message_outbound, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case OUTBOUND_EPHEMERAL_WITH_ATTACHMENT:
                case OUTBOUND_WITH_ATTACHMENT: {
                    View view = inflater.inflate(R.layout.item_view_message_outbound_with_attachments, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case INBOUND_WITH_ATTACHMENT:
                case INBOUND_EPHEMERAL_WITH_ATTACHMENT: {
                    View view = inflater.inflate(R.layout.item_view_message_inbound_with_attachments, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case INFO:
                case INFO_GROUP_MEMBER: {
                    View view = inflater.inflate(R.layout.item_view_message_info, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case SETTINGS_UPDATE: {
                    View view = inflater.inflate(R.layout.item_view_message_settings_update, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case PHONE_CALL: {
                    View view = inflater.inflate(R.layout.item_view_message_phone_call, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case NEW_PUBLISHED_DETAILS: {
                    View view = inflater.inflate(R.layout.item_view_message_new_published_details, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
                case INBOUND:
                case INBOUND_EPHEMERAL:
                default: {
                    View view = inflater.inflate(R.layout.item_view_message_inbound, parent, false);
                    return new MessageViewHolder(view, viewType);
                }
            }
        }

        @Override
        public boolean isLoadingDone() {
            return messages != null;
        }

        @Override
        public void onViewAttachedToWindow(@NonNull final MessageViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.shouldAnimateStatusImageView) {
                holder.messageStatusImageView.startAnimation(rotateAnimation);
            }
            if (holder.messageId == highlightOnBindMessageId) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> highlightView(holder.messageRootView), 400);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull MessageListAdapter.MessageViewHolder holder, int position) {
            Logger.e("The no-payload onBindViewHolder should never get called!");
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull final MessageViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (messages == null || position == 0) {
                return;
            }
            position--;

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

            final Message message = messages.get(position);

            if (selectingForDeletion) {
                holder.messageSelectionCheckbox.setVisibility(View.VISIBLE);
                if (holder.messageCheckboxCompensator != null && !showInboundSenderName) {
                    holder.messageCheckboxCompensator.setVisibility(View.GONE);
                    if (holder.senderInitialView != null) {
                        holder.senderInitialView.setVisibility(View.GONE);
                    }
                }
            } else {
                holder.messageSelectionCheckbox.setVisibility(View.GONE);
                if (holder.messageCheckboxCompensator != null) {
                    if (showInboundSenderName) {
                        holder.messageCheckboxCompensator.setVisibility(View.GONE);
                    } else {
                        holder.messageCheckboxCompensator.setVisibility(View.VISIBLE);
                    }
                }
                if (holder.senderInitialViewCompensator != null) {
                    holder.senderInitialViewCompensator.setVisibility(showInboundSenderName ? View.VISIBLE : View.GONE);
                }
            }

            if ((changesMask & SELECTED_CHANGE_MASK) != 0) {
                if (selectedMessageIds != null && selectedMessageIds.contains(message.id)) {
                    holder.messageRootView.setBackgroundColor(ContextCompat.getColor(DiscussionActivity.this, R.color.olvid_gradient_light));
                    holder.messageSelectionCheckbox.setChecked(true);
                } else {
                    holder.messageRootView.setBackground(null);
                    holder.messageSelectionCheckbox.setChecked(false);
                }
            }

            if ((changesMask & SHOW_TOP_HEADER_CHANGE_MASK) != 0 &&
                    (message.messageType == Message.TYPE_INBOUND_MESSAGE || message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)) {
                boolean showSender;

                if (!showInboundSenderName) {
                    showSender = false;
                } else if (position == 0) {
                    showSender = true;
                } else if (Utils.notTheSameDay(message.timestamp, messages.get(position - 1).timestamp)) {
                    showSender = true;
                } else if (!message.senderThreadIdentifier.equals(messages.get(position - 1).senderThreadIdentifier)) {
                    showSender = true;
                } else if (!message.isTextOnly()) {
                    showSender = true;
                } else {
                    showSender = message.status != messages.get(position - 1).status;
                }

                if (showSender) {
                    String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                    if (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                        holder.ephemeralSenderTextView.setVisibility(View.VISIBLE);
                        if (displayName != null) {
                            holder.ephemeralSenderTextView.setText(displayName);
                        } else {
                            holder.ephemeralSenderTextView.setText(R.string.text_deleted_contact);
                        }
                        int color = InitialView.getTextColor(DiscussionActivity.this, message.senderIdentifier, AppSingleton.getContactCustomHue(message.senderIdentifier));
                        holder.ephemeralSenderTextView.setTextColor(color);
                        holder.ephemeralSenderTextView.setMinWidth(0);
                    } else {
                        holder.messageSenderTextView.setVisibility(View.VISIBLE);
                        if (displayName != null) {
                            holder.messageSenderTextView.setText(displayName);
                        } else {
                            holder.messageSenderTextView.setText(R.string.text_deleted_contact);
                        }
                        int color = InitialView.getTextColor(DiscussionActivity.this, message.senderIdentifier, AppSingleton.getContactCustomHue(message.senderIdentifier));
                        holder.messageSenderTextView.setTextColor(color);
                        holder.messageSenderTextView.setMinWidth(0);
                    }
                    if (holder.senderInitialView != null) {
                        if (!selectingForDeletion && showInboundSenderName) {
                            holder.senderInitialView.setVisibility(View.VISIBLE);
                            holder.senderInitialView.setFromCache(message.senderIdentifier);
                        } else {
                            holder.senderInitialView.setVisibility(View.GONE);
                        }
                    }
                } else {
                    if (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                        holder.ephemeralSenderTextView.setVisibility(View.GONE);
                    } else {
                        holder.messageSenderTextView.setVisibility(View.GONE);
                    }
                    if (holder.senderInitialView != null) {
                        holder.senderInitialView.setVisibility(View.GONE);
                    }
                }
            }

            if (message.messageType == Message.TYPE_INBOUND_MESSAGE ||
                    message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {

                if ((changesMask & STATUS_CHANGE_MASK) != 0) {
                    if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                        switch (message.status) {
                            case Message.STATUS_SENT: {
                                holder.shouldAnimateStatusImageView = false;
                                holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_message_status_sent));
                                holder.messageStatusImageView.clearAnimation();
                                break;
                            }
                            case Message.STATUS_DELIVERED: {
                                holder.shouldAnimateStatusImageView = false;
                                holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_message_status_delivered));
                                holder.messageStatusImageView.clearAnimation();
                                break;
                            }
                            case Message.STATUS_DELIVERED_AND_READ: {
                                holder.shouldAnimateStatusImageView = false;
                                holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_message_status_delivered_and_read));
                                holder.messageStatusImageView.clearAnimation();
                                break;
                            }
                            case Message.STATUS_UNDELIVERED: {
                                holder.shouldAnimateStatusImageView = false;
                                holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_message_status_undelivered));
                                holder.messageStatusImageView.clearAnimation();
                                break;
                            }
                            case Message.STATUS_UNPROCESSED:
                            case Message.STATUS_COMPUTING_PREVIEW:
                            case Message.STATUS_PROCESSING:
                            default: {
                                if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                                    holder.shouldAnimateStatusImageView = false;
                                    holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_message_status_sent));
                                    holder.messageStatusImageView.clearAnimation();
                                } else {
                                    holder.shouldAnimateStatusImageView = true;
                                    holder.messageStatusImageView.setImageDrawable(ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_sync));
                                }
                            }
                        }
                    }
                }

                if ((changesMask & MESSAGE_EXPAND_CHANGE_MASK) != 0) {
                    if (holder.expandMessage) {
                        holder.messageContentTextView.setMaxLines(Integer.MAX_VALUE);
                        holder.messageContentTextView.setEllipsize(null);
                        if (message.messageType == Message.TYPE_INBOUND_MESSAGE) {
                            holder.messageContentExpander.setImageResource(R.drawable.ic_expander_inbound_close);
                        } else {
                            holder.messageContentExpander.setImageResource(R.drawable.ic_expander_outbound_close);
                        }
                    } else {
                        holder.messageContentTextView.setMaxLines(COMPACT_MESSAGE_LINE_COUNT);
                        holder.messageContentTextView.setEllipsize(TextUtils.TruncateAt.END);
                        if (holder.contentTruncated) {
                            if (message.messageType == Message.TYPE_INBOUND_MESSAGE) {
                                holder.messageContentExpander.setImageResource(R.drawable.ic_expander_inbound);
                            } else {
                                holder.messageContentExpander.setImageResource(R.drawable.ic_expander_outbound);
                            }
                            holder.messageContentExpander.setVisibility(View.VISIBLE);
                        }
                    }
                }

                if ((changesMask & ATTACHMENT_COUNT_CHANGE_MASK) != 0) {
                    if (message.hasAttachments()) {
                        int attachmentsHeight = 0;

                        PreviewUtils.ImageResolution[] imageResolutions;
                        try {
                            imageResolutions = PreviewUtils.ImageResolution.parseMultiple(message.imageResolutions);
                        } catch (Exception e) {
                            imageResolutions = null;
                        }

                        if (imageResolutions != null && imageResolutions.length == message.imageCount) {
                            switch (imageResolutions.length) {
                                case 1: {
                                    attachmentsHeight += imageResolutions[0].getPreferredHeight(attachmentRecyclerViewWidth, false);
                                    break;
                                }
                                case 2: {
                                    attachmentsHeight += imageResolutions[0].getPreferredHeight(attachmentRecyclerViewWidth, true);
                                    attachmentsHeight += imageResolutions[1].getPreferredHeight(attachmentRecyclerViewWidth, true);
                                    attachmentsHeight += attachmentSpace;
                                    break;
                                }
                                default: {
                                    for (int i = 0; i < imageResolutions.length - 1; i += 2) {
                                        attachmentsHeight += Math.max(
                                                imageResolutions[i].getPreferredHeight((attachmentRecyclerViewWidth - attachmentSpace) / 2, false),
                                                imageResolutions[i + 1].getPreferredHeight((attachmentRecyclerViewWidth - attachmentSpace) / 2, false)
                                        );
                                        attachmentsHeight += attachmentSpace;
                                    }
                                    if ((imageResolutions.length & 1) != 0) {
                                        attachmentsHeight += imageResolutions[imageResolutions.length -1].getPreferredHeight(attachmentRecyclerViewWidth, true);
                                    } else {
                                        attachmentsHeight -= attachmentSpace;
                                    }
                                }
                            }
                        } else {
                            // images
                            if (message.imageCount == 1) {
                                //noinspection SuspiciousNameCombination
                                attachmentsHeight += attachmentRecyclerViewWidth;
                            } else if (message.imageCount == 2) {
                                attachmentsHeight += attachmentRecyclerViewWidth + attachmentSpace;
                            } else if (message.imageCount > 2) {
                                if ((message.imageCount & 1) != 0) {
                                    attachmentsHeight += ((attachmentRecyclerViewWidth + attachmentSpace) / 2) * (message.imageCount / 2);
                                    attachmentsHeight += attachmentRecyclerViewWidth / 2;
                                } else {
                                    attachmentsHeight += ((attachmentRecyclerViewWidth + attachmentSpace) / 2) * (message.imageCount / 2);
                                    attachmentsHeight -= attachmentSpace;
                                }
                            }
                        }

                        // files
                        attachmentsHeight += (attachmentFileHeight + attachmentSpace) * (message.totalAttachmentCount - message.imageCount);
                        if (message.imageCount == 0) {
                            attachmentsHeight -= attachmentSpace;
                        }

                        holder.attachmentsRecyclerView.setMinimumHeight(attachmentsHeight);
                    } else if (holder.attachmentsRecyclerView != null) {
                        holder.attachmentsRecyclerView.setMinimumHeight(0);
                    }
                }
            }

            if ((changesMask & OUTBOUND_SENT_CHANGE_MASK) != 0) {
                // this is done the first time the message is bound (changesMask == -1), but also when an outbound message is sent to start any newly created timer
                holder.setMessageId(message.id, message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ);
            }

            if ((changesMask & BODY_OR_HIGHLIGHT_CHANGE_MASK) != 0) {
                if (message.messageType == Message.TYPE_INBOUND_MESSAGE
                        || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    String body = message.getStringContent(DiscussionActivity.this);

                    if (holder.wipedAttachmentCountTextView != null) {
                        holder.wipedAttachmentCountTextView.setVisibility(View.GONE);
                        holder.directDeleteImageView.setVisibility(View.GONE);
                    }

                    if (body.length() == 0) {
                        holder.messageContentTextView.setVisibility(View.GONE);
                    } else if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                            || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                        holder.directDeleteImageView.setVisibility(View.VISIBLE);

                        holder.messageContentTextView.setVisibility(View.VISIBLE);
                        holder.messageContentTextView.setMinWidth(0);
                        holder.messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);

                        SpannableString text = new SpannableString(body);
                        StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                        text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.messageContentTextView.setText(text);
                        if (holder.wipedAttachmentCountTextView != null && message.wipedAttachmentCount != 0) {
                            holder.wipedAttachmentCountTextView.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, message.wipedAttachmentCount, message.wipedAttachmentCount));
                            holder.wipedAttachmentCountTextView.setVisibility(View.VISIBLE);
                        }

                        if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                            final long messageId = holder.messageId;
                            byte[] deleter = remoteDeletedMessageDeleter.get(messageId);
                            if (deleter != null) {
                                byte[] bytesOwnedIdentity = discussionViewModel.getDiscussion().getValue() == null ? null : discussionViewModel.getDiscussion().getValue().bytesOwnedIdentity;
                                if (Arrays.equals(bytesOwnedIdentity, deleter)) {
                                    text = new SpannableString(DiscussionActivity.this.getString(R.string.text_message_content_remote_deleted_by_you));
                                    text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    holder.messageContentTextView.setText(text);
                                } else {
                                    String displayName = AppSingleton.getContactCustomDisplayName(deleter);
                                    if (displayName != null) {
                                        text = new SpannableString(DiscussionActivity.this.getString(R.string.text_message_content_remote_deleted_by, displayName));
                                        text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        holder.messageContentTextView.setText(text);
                                    }
                                }
                            } else {
                                // we need to fetch the identity of the deleter of this message
                                App.runThread(() -> {
                                    MessageMetadata messageMetadata = AppDatabase.getInstance().messageMetadataDao().getByKind(messageId, MessageMetadata.KIND_REMOTE_DELETED);
                                    if (messageMetadata != null && messageMetadata.bytesRemoteIdentity != null) {
                                        // we found the deleter for this message --> cache it
                                        remoteDeletedMessageDeleter.put(messageId, messageMetadata.bytesRemoteIdentity);

                                        byte[] bytesOwnedIdentity = discussionViewModel.getDiscussion().getValue() == null ? null : discussionViewModel.getDiscussion().getValue().bytesOwnedIdentity;
                                        if (Arrays.equals(bytesOwnedIdentity, messageMetadata.bytesRemoteIdentity)) {
                                            String body1 = DiscussionActivity.this.getString(R.string.text_message_content_remote_deleted_by_you);
                                            SpannableString text1 = new SpannableString(body1);
                                            StyleSpan styleSpan1 = new StyleSpan(Typeface.ITALIC);
                                            text1.setSpan(styleSpan1, 0, text1.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            DiscussionActivity.this.runOnUiThread(() -> {
                                                if (holder.messageId == messageId) {
                                                    holder.messageContentTextView.setText(text1);
                                                }
                                            });
                                        } else {
                                            String displayName = AppSingleton.getContactCustomDisplayName(messageMetadata.bytesRemoteIdentity);
                                            if (displayName != null) {
                                                String body1 = DiscussionActivity.this.getString(R.string.text_message_content_remote_deleted_by, displayName);
                                                SpannableString text1 = new SpannableString(body1);
                                                StyleSpan styleSpan1 = new StyleSpan(Typeface.ITALIC);
                                                text1.setSpan(styleSpan1, 0, text1.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                                DiscussionActivity.this.runOnUiThread(() -> {
                                                    if (holder.messageId == messageId) {
                                                        holder.messageContentTextView.setText(text1);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    } else {
                        if (messageHighlightInfo != null && messageHighlightInfo.messageId == message.id) {
                            holder.messageContentTextView.setText(DiscussionSearch.highlightString(body, messageHighlightInfo.patterns));
                        } else {
                            holder.messageContentTextView.setText(body);
                        }
                        if (StringUtils.isShortEmojiString(body, 5)) {
                            holder.messageContentTextView.setVisibility(View.VISIBLE);
                            holder.messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.single_line_emoji_size));
                            holder.messageContentTextView.setMinWidth((int) (getResources().getDimensionPixelSize(R.dimen.single_line_emoji_size) * 1.25));
                            holder.messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        } else {
                            holder.messageContentTextView.setVisibility(View.VISIBLE);
                            holder.messageContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                            holder.messageContentTextView.setMinWidth(0);
                            holder.messageContentTextView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                            holder.messageContentTextView.setMovementMethod(null);
                            holder.messageContentTextView.setOnTouchListener((v, event) -> {
                                final TextView messageContentTextView = holder.messageContentTextView;
                                CharSequence text = messageContentTextView.getText();
                                if (text instanceof Spanned) {
                                    Spanned buffer = (Spanned) text;
                                    int action = event.getAction();
                                    if (action == MotionEvent.ACTION_UP
                                            || action == MotionEvent.ACTION_DOWN) {
                                        int x = (int) event.getX() - messageContentTextView.getTotalPaddingLeft() + messageContentTextView.getScrollX();
                                        int y = (int) event.getY() - messageContentTextView.getTotalPaddingTop() + messageContentTextView.getScrollY();

                                        Layout layout = messageContentTextView.getLayout();
                                        int line = layout.getLineForVertical(y);
                                        int off = layout.getOffsetForHorizontal(line, x);

                                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                                        if (link.length != 0) {
                                            if (action == MotionEvent.ACTION_UP) {
                                                link[0].onClick(messageContentTextView);
                                            }
                                            return true;
                                        }
                                    }
                                }
                                return false;
                            });
                        }
                    }
                    holder.recomputeLayout();
                }
            }

            if ((changesMask & EDITED_CHANGE_MASK) != 0) {
                if (message.messageType == Message.TYPE_INBOUND_MESSAGE
                        || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    switch (message.edited) {
                        case Message.EDITED_SEEN: {
                            holder.editedBadge.setVisibility(View.VISIBLE);
                            holder.editedBadge.setBackgroundResource(R.drawable.background_green_badge_border);
                            holder.editedBadge.setTextColor(ContextCompat.getColor(DiscussionActivity.this, R.color.green));
                            break;
                        }
                        case Message.EDITED_UNSEEN: {
                            editedMessageIdsToMarkAsSeen.add(message.id);
                            holder.editedBadge.setVisibility(View.VISIBLE);
                            holder.editedBadge.setBackgroundResource(R.drawable.background_green_badge);
                            holder.editedBadge.setTextColor(ContextCompat.getColor(DiscussionActivity.this, R.color.almostWhite));
                            break;
                        }
                        case Message.EDITED_NONE:
                        default: {
                            holder.editedBadge.setVisibility(View.GONE);
                            holder.recomputeLayout();
                            break;
                        }
                    }
                }
            }

            if ((changesMask & FORWARDED_CHANGE_MASK) != 0) {
                if (message.messageType == Message.TYPE_INBOUND_MESSAGE
                        || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    if (message.forwarded) {
                        holder.forwardedBadge.setVisibility(View.VISIBLE);
                    } else {
                        holder.forwardedBadge.setVisibility(View.GONE);
                    }
                }
            }

            if ((changesMask & REACTIONS_CHANGE_MASK) != 0) {
                if (holder.reactionsDynamicFlow != null) {
                    if (message.reactions == null || message.reactions.equals("")) {
                        holder.reactionsDynamicFlow.setVisibility(View.GONE);
                    } else {
                        holder.reactionsDynamicFlow.setVisibility(View.VISIBLE);
                        holder.reactionsDynamicFlow.removeAllViews();
                        String[] splitReactions = message.reactions.split(":");
                        if (splitReactions.length == 0 || splitReactions.length % 2 != 0) {
                            Logger.e("message.reactions encoding is invalid");
                            holder.reactionsDynamicFlow.setVisibility(View.GONE);
                        } else {
                            List<View> reactionsViewToAdd = new ArrayList<>(splitReactions.length / 2);
                            // deserialize reactions from db
                            for (int i = 0; i < splitReactions.length; i += 2) {
                                boolean isMine = false;
                                // parse reaction, count and detect if your it is own reaction
                                String emoji;
                                // own reaction is using |emoji:count format when serialized, mark emoji as mine, and ignore | separator
                                if (splitReactions[i].charAt(0) == '|') {
                                    isMine = true;
                                    emoji = splitReactions[i].substring(1);
                                }
                                else {
                                    emoji = splitReactions[i];
                                }
                                // inflate and set layout for this reaction
                                int count = Integer.parseInt(splitReactions[i + 1]);
                                @SuppressLint("InflateParams")
                                LinearLayout reactionView = (LinearLayout) inflater.inflate(R.layout.item_view_reaction, null, false);
                                TextView emojiView = reactionView.findViewById(R.id.emoji);
                                TextView countView = reactionView.findViewById(R.id.count);
                                //  change default background if own reaction
                                if (isMine) {
                                    reactionView.setBackgroundResource(R.drawable.background_own_reaction);
                                }
                                emojiView.setText(emoji);
                                if (count > 1) {
                                    countView.setText(String.valueOf(count));
                                } else {
                                    countView.setVisibility(View.GONE);
                                }
                                reactionView.setId(View.generateViewId());
                                reactionView.setOnClickListener((e) -> {
                                    ReactionListBottomSheetFragment bottomSheetFragment = ReactionListBottomSheetFragment.newInstance(message.id);
                                    bottomSheetFragment.show(getSupportFragmentManager(), "ReactionsBottomSheet");
                                });
                                reactionsViewToAdd.add(reactionView);
                            }
                            // if message is outbound, insert reactions in reversed order (most popular reaction in center)
                            if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                                Collections.reverse(reactionsViewToAdd);
                            }
                            for (View reactionView : reactionsViewToAdd) {
                                holder.reactionsDynamicFlow.addView(reactionView);
                            }
                        }
                    }
                }
            }

            if ((changesMask & MISSED_COUNT_CHANGE_MASK) != 0) {
                if (holder.missedMessagesTextView != null) {
                    if (message.missedMessageCount == 0) {
                        holder.missedMessagesTextView.setVisibility(View.GONE);
                    } else {
                        holder.missedMessagesTextView.setVisibility(View.VISIBLE);
                        holder.missedMessagesTextView.setText(getResources().getQuantityString(R.plurals.text_message_possibly_missing, (int) message.missedMessageCount, (int) message.missedMessageCount));
                    }
                }
            }

            if (changesMask == -1) {
                // here we do everything that only needs to be set once for a message
                if (message.status == Message.STATUS_UNREAD
                        || message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ) {
                    messageIdsToMarkAsRead.add(message.id);

                    if ((message.isInbound() && message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ)
                            || message.messageType == Message.TYPE_INBOUND_MESSAGE && message.status == Message.STATUS_UNREAD) {
                        // only send the read receipt if the content of the message was actually displayed
                        if (sendReadReceipt) {
                            App.runThread(() -> message.sendMessageReturnReceipt(discussionViewModel.getDiscussion().getValue(), Message.RETURN_RECEIPT_STATUS_READ));
                        }
                        App.runThread(new CreateReadMessageMetadata(message.id));
                    }
                }

                if (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                    holder.standardHeaderView.setVisibility(View.GONE);
                    holder.ephemeralHeaderView.setVisibility(View.VISIBLE);

                    holder.ephemeralTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));

                    Message.JsonExpiration expiration = message.getJsonMessage().getJsonExpiration();
                    boolean readOnce = expiration.getReadOnce() != null && expiration.getReadOnce();

                    // Check for auto-open
                    DiscussionCustomization discussionCustomization = discussionViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        // check the ephemeral settings match
                        if (readOnce == discussionCustomization.settingReadOnce
                                && Objects.equals(expiration.getVisibilityDuration(), discussionCustomization.settingVisibilityDuration)
                                && Objects.equals(expiration.getExistenceDuration(), discussionCustomization.settingExistenceDuration)) {
                            // settings are the default, verify if auto-open
                            boolean autoOpen = discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages != null ? discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages : SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages();
                            if (autoOpen) {
                                App.runThread(new InboundEphemeralMessageClicked(message.id));
                            }
                        }
                    }


                    if (message.isWithoutText()) {
                        holder.ephemeralExplanationTextView.setVisibility(View.GONE);
                        holder.ephemeralTimerTextView.setVisibility(View.GONE);
                    } else {
                        holder.ephemeralExplanationTextView.setVisibility(View.VISIBLE);
                        holder.ephemeralTimerTextView.setVisibility(View.VISIBLE);
                        if (readOnce) {
                            holder.ephemeralTimerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_burn, 0, 0, 0);
                            holder.ephemeralTimerTextView.setTextColor(ContextCompat.getColor(DiscussionActivity.this, R.color.red));
                            if (expiration.getVisibilityDuration() == null) {
                                holder.ephemeralTimerTextView.setText(R.string.text_visible_once);
                            } else if (expiration.getVisibilityDuration() < 60L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_s_once, expiration.getVisibilityDuration()));
                            } else if (expiration.getVisibilityDuration() < 3_600L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_m_once, expiration.getVisibilityDuration() / 60L));
                            } else if (expiration.getVisibilityDuration() < 86_400L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_h_once, expiration.getVisibilityDuration() / 3_600L));
                            } else if (expiration.getVisibilityDuration() < 31_536_000L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_d_once, expiration.getVisibilityDuration() / 86_400L));
                            } else {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_y_once, expiration.getVisibilityDuration() / 31_536_000L));
                            }
                        } else {
                            holder.ephemeralTimerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_eye, 0, 0, 0);
                            holder.ephemeralTimerTextView.setTextColor(ContextCompat.getColor(DiscussionActivity.this, R.color.orange));
                            if (expiration.getVisibilityDuration() == null) {
                                // this should never happen, the jsonExpiration should have been wiped when received
                                App.runThread(new InboundEphemeralMessageClicked(message.id));
                            } else if (expiration.getVisibilityDuration() < 60L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_s, expiration.getVisibilityDuration()));
                            } else if (expiration.getVisibilityDuration() < 3_600L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_m, expiration.getVisibilityDuration() / 60L));
                            } else if (expiration.getVisibilityDuration() < 86_400L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_h, expiration.getVisibilityDuration() / 3_600L));
                            } else if (expiration.getVisibilityDuration() < 31_536_000L) {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_d, expiration.getVisibilityDuration() / 86_400L));
                            } else {
                                holder.ephemeralTimerTextView.setText(getString(R.string.text_visible_timer_y, expiration.getVisibilityDuration() / 31_536_000L));
                            }
                        }
                    }

                    if (message.hasAttachments()) {
                        if (holder.attachmentFyles != null) {
                            holder.attachmentFyles.removeObservers(DiscussionActivity.this);
                        }
                        holder.attachmentFyles = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFylesAndStatusForMessage(message.id);
                        holder.adapter.setHidden(expiration.getVisibilityDuration(), readOnce, true);
                        holder.attachmentFyles.observe(DiscussionActivity.this, holder.adapter);
                    }
                } else if (message.messageType == Message.TYPE_INBOUND_MESSAGE
                        || message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                    if (holder.standardHeaderView != null && holder.ephemeralHeaderView != null) { // true for inbound messages
                        holder.standardHeaderView.setVisibility(View.VISIBLE);
                        holder.ephemeralHeaderView.setVisibility(View.GONE);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                    holder.messageBottomTimestampTextView.setMinWidth(0);
                    final Message.JsonMessage jsonMessage = message.getJsonMessage();

                    if (jsonMessage.getJsonReply() == null) {
                        holder.messageReplyGroup.setVisibility(View.GONE);
                        holder.messageReplyBody.setText(null);
                    } else {
                        holder.messageReplyGroup.setVisibility(View.VISIBLE);
                        Message.JsonMessageReference jsonReply = jsonMessage.getJsonReply();
                        holder.repliedToMessage = AppDatabase.getInstance().messageDao().getBySenderSequenceNumberAsync(jsonReply.getSenderSequenceNumber(), jsonReply.getSenderThreadIdentifier(), jsonReply.getSenderIdentifier(), message.discussionId);
                        holder.repliedToMessage.observe(DiscussionActivity.this, replyMessage -> {
                            if (replyMessage == null) {
                                holder.replyMessageId = -1;
                                holder.messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                                holder.messageReplyBody.setMinWidth(0);
                                holder.messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                                holder.messageReplyAttachmentCount.setVisibility(View.GONE);

                                StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                                SpannableString spannableString = new SpannableString(getString(R.string.text_original_message_not_found));
                                spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                holder.messageReplyBody.setText(spannableString);
                            } else {
                                holder.replyMessageId = replyMessage.id;
                                if (replyMessage.totalAttachmentCount > 0) {
                                    holder.messageReplyAttachmentCount.setVisibility(View.VISIBLE);
                                    holder.messageReplyAttachmentCount.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, replyMessage.totalAttachmentCount, replyMessage.totalAttachmentCount));
                                } else {
                                    holder.messageReplyAttachmentCount.setVisibility(View.GONE);
                                }
                                if (replyMessage.getStringContent(DiscussionActivity.this).length() == 0) {
                                    holder.messageReplyBody.setVisibility(View.GONE);
                                } else {
                                    holder.messageReplyBody.setVisibility(View.VISIBLE);
                                    holder.messageReplyBody.setText(replyMessage.getStringContent(DiscussionActivity.this));
                                    if (StringUtils.isShortEmojiString(replyMessage.getStringContent(DiscussionActivity.this), 5)) {
                                        holder.messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.single_line_emoji_reply_size));
                                        holder.messageReplyBody.setMinWidth((int) (getResources().getDimensionPixelSize(R.dimen.single_line_emoji_reply_size) * 1.25));
                                        holder.messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                                    } else {
                                        holder.messageReplyBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                                        holder.messageReplyBody.setMinWidth(0);
                                        holder.messageReplyBody.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                                    }
                                }
                            }
                        });

                        String displayName = AppSingleton.getContactCustomDisplayName(jsonMessage.getJsonReply().getSenderIdentifier());
                        if (displayName != null) {
                            holder.messageReplySenderName.setText(displayName);
                        } else {
                            holder.messageReplySenderName.setText(R.string.text_deleted_contact);
                        }
                        int color = InitialView.getTextColor(DiscussionActivity.this, jsonMessage.getJsonReply().getSenderIdentifier(), AppSingleton.getContactCustomHue(jsonMessage.getJsonReply().getSenderIdentifier()));
                        holder.messageReplySenderName.setTextColor(color);

                        Drawable drawable = ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.background_reply_white);
                        if (drawable instanceof LayerDrawable) {
                            Drawable border = ((LayerDrawable) drawable).findDrawableByLayerId(R.id.reply_color_border);
                            border.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                            ((LayerDrawable) drawable).setDrawableByLayerId(R.id.reply_color_border, border);
                            holder.messageReplyGroup.setBackground(drawable);
                        }
                    }

                    if (message.hasAttachments()) {
                        if (holder.attachmentFyles != null) {
                            holder.attachmentFyles.removeObservers(DiscussionActivity.this);
                        }
                        holder.attachmentFyles = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFylesAndStatusForMessage(message.id);
                        holder.adapter.setVisible(message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ); // if the message is "wipe on read", clicking an attachment should mark the markAsReadOnPause as false so as not to delete the message
                        holder.attachmentFyles.observe(DiscussionActivity.this, holder.adapter);
                    }
                } else if (message.messageType == Message.TYPE_GROUP_MEMBER_JOINED) {
                    String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                    if (displayName != null) {
                        holder.messageInfoTextView.setText(getString(R.string.text_joined_the_group, displayName));
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_unknown_member_joined_the_group);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_GROUP_MEMBER_LEFT) {
                    String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                    if (displayName != null) {
                        holder.messageInfoTextView.setText(getString(R.string.text_left_the_group, displayName));
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_unknown_member_left_the_group);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_DISCUSSION_REMOTELY_DELETED) {
                    String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                    if (displayName != null) {
                        holder.messageInfoTextView.setText(getString(R.string.text_discussion_remotely_deleted_by, displayName));
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_discussion_remotely_deleted);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_PHONE_CALL) {
                    int callStatus = CallLogItem.STATUS_MISSED;
                    Long callLogItemId = null;
                    try {
                        String[] statusAndCallLogItemId = message.contentBody.split(":");
                        if (statusAndCallLogItemId.length > 0) {
                            callStatus = Integer.parseInt(statusAndCallLogItemId[0]);
                        }
                        if (statusAndCallLogItemId.length > 1) {
                            callLogItemId = Long.parseLong(statusAndCallLogItemId[1]);
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                    // callStatus is positive for incoming calls
                    switch(callStatus) {
                        case -CallLogItem.STATUS_BUSY:  {
                            holder.messageInfoTextView.setText(R.string.text_busy_outgoing_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_busy_out);
                            break;
                        }
                        case -CallLogItem.STATUS_REJECTED:  {
                            holder.messageInfoTextView.setText(R.string.text_rejected_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_rejected_out);
                            break;
                        }
                        case -CallLogItem.STATUS_MISSED:
                        case -CallLogItem.STATUS_FAILED:  {
                            holder.messageInfoTextView.setText(R.string.text_failed_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_failed_out);
                            break;
                        }
                        case -CallLogItem.STATUS_SUCCESSFUL:  {
                            holder.messageInfoTextView.setText(R.string.text_successful_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_success_out);
                            break;
                        }
                        case CallLogItem.STATUS_BUSY:  {
                            holder.messageInfoTextView.setText(R.string.text_busy_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_busy_in);
                            break;
                        }
                        case CallLogItem.STATUS_FAILED:  {
                            holder.messageInfoTextView.setText(R.string.text_failed_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_failed_in);
                            break;
                        }
                        case CallLogItem.STATUS_SUCCESSFUL:  {
                            holder.messageInfoTextView.setText(R.string.text_successful_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_success_in);
                            break;
                        }
                        case CallLogItem.STATUS_REJECTED:  {
                            holder.messageInfoTextView.setText(R.string.text_rejected_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_rejected_in);
                            break;
                        }
                        case CallLogItem.STATUS_MISSED:
                        default: {
                            holder.messageInfoTextView.setText(R.string.text_missed_call);
                            holder.callBackButton.setImageResource(R.drawable.ic_phone_missed_in);
                            break;
                        }
                    }
                    holder.callLogItemId = callLogItemId;
                    if (callLogItemId != null) {
                        if (holder.callCachedString != null) {
                            holder.messageInfoTextView.setText(holder.callCachedString);
                        } else {
                            final long finalCallLogItemId = callLogItemId;
                            App.runThread(() -> {
                                CallLogItemDao.CallLogItemAndContacts callLogItemAndContacts = AppDatabase.getInstance().callLogItemDao().get(finalCallLogItemId);

                                if (callLogItemAndContacts != null) {
                                    runOnUiThread(() -> {
                                        if (Objects.equals(holder.callLogItemId, finalCallLogItemId) && callLogItemAndContacts.oneContact != null) {
                                            // the holder was not recycled, we save in cache the "improved" string and display it
                                            if (callLogItemAndContacts.callLogItem.callType == CallLogItem.TYPE_OUTGOING) {
                                                switch (callLogItemAndContacts.callLogItem.callStatus) {
                                                    case CallLogItem.STATUS_BUSY:
                                                        if (callLogItemAndContacts.contacts.size() < 2) {
                                                            holder.callCachedString = getString(R.string.text_busy_outgoing_call_with_contacts, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        } else {
                                                            holder.callCachedString = getResources().getQuantityString(R.plurals.text_busy_outgoing_group_call_with_contacts, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        }
                                                        break;
                                                    case CallLogItem.STATUS_REJECTED:
                                                        if (callLogItemAndContacts.contacts.size() < 2) {
                                                            holder.callCachedString = getString(R.string.text_rejected_outgoing_call_with_contacts, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        } else {
                                                            holder.callCachedString = getResources().getQuantityString(R.plurals.text_rejected_outgoing_group_call_with_contacts, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        }
                                                        break;
                                                    case CallLogItem.STATUS_MISSED:
                                                    case CallLogItem.STATUS_FAILED:
                                                        if (callLogItemAndContacts.contacts.size() < 2) {
                                                            holder.callCachedString = getString(R.string.text_failed_outgoing_call_with_contacts, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        } else {
                                                            holder.callCachedString = getResources().getQuantityString(R.plurals.text_failed_outgoing_group_call_with_contacts, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        }
                                                        break;
                                                    case CallLogItem.STATUS_SUCCESSFUL:
                                                    default:
                                                        if (callLogItemAndContacts.contacts.size() < 2) {
                                                            holder.callCachedString = getString(R.string.text_outgoing_call_with_contacts, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        } else {
                                                            holder.callCachedString = getResources().getQuantityString(R.plurals.text_outgoing_group_call_with_contacts, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        }
                                                        break;
                                                }
                                            } else {
                                                // first, find the caller
                                                String callerDisplayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                                                if (callerDisplayName == null) {
                                                    callerDisplayName = callLogItemAndContacts.oneContact.getCustomDisplayName();
                                                }

                                                switch (callLogItemAndContacts.callLogItem.callStatus) {
                                                    case CallLogItem.STATUS_BUSY:
                                                        holder.callCachedString = getString(R.string.text_busy_incoming_call_with_contacts, callerDisplayName);
                                                        break;
                                                    case CallLogItem.STATUS_MISSED:
                                                        holder.callCachedString = getString(R.string.text_missed_incoming_call_with_contacts, callerDisplayName);
                                                        break;
                                                    case CallLogItem.STATUS_REJECTED:
                                                        holder.callCachedString = getString(R.string.text_rejected_incoming_call_with_contacts, callerDisplayName);
                                                        break;
                                                    case CallLogItem.STATUS_FAILED:
                                                        holder.callCachedString = getString(R.string.text_failed_incoming_call_with_contacts, callerDisplayName);
                                                        break;
                                                    case CallLogItem.STATUS_SUCCESSFUL:
                                                    default:
                                                        if (callLogItemAndContacts.contacts.size() < 2) {
                                                            holder.callCachedString = getString(R.string.text_incoming_call_with_contacts, callLogItemAndContacts.oneContact.getCustomDisplayName());
                                                        } else {
                                                            holder.callCachedString = getResources().getQuantityString(R.plurals.text_incoming_group_call_with_contacts, callLogItemAndContacts.contacts.size() - 1, callLogItemAndContacts.contacts.size() - 1, callerDisplayName);
                                                        }
                                                        break;
                                                }
                                            }

                                            holder.messageInfoTextView.setText(holder.callCachedString);
                                        }
                                    });
                                }
                            });
                        }
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_NEW_PUBLISHED_DETAILS) {
                    discussionViewModel.getNewDetailsUpdate().observe(DiscussionActivity.this, holder.newDetailsObserver);
                    Discussion discussion = discussionViewModel.getDiscussion().getValue();
                    if (discussion != null && discussion.bytesGroupOwnerAndUid != null) {
                        holder.messageInfoTextView.setText(R.string.text_group_details_updated);
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_contact_details_updated);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_DISCUSSION_SETTINGS_UPDATE) {
                    if (message.status == Message.STATUS_READ) {
                        String displayName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                        if (displayName != null) {
                            holder.messageInfoTextView.setText(getString(R.string.text_updated_shared_settings, displayName));
                        } else {
                            holder.messageInfoTextView.setText(R.string.text_discussion_shared_settings_updated);
                        }
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_updated_shared_settings_you);
                    }
                    try {
                        boolean ephemeral = false;
                        DiscussionCustomization.JsonSharedSettings jsonSharedSettings = AppSingleton.getJsonObjectMapper().readValue(message.contentBody, DiscussionCustomization.JsonSharedSettings.class);
                        if (jsonSharedSettings.getJsonExpiration() != null) {
                            if (jsonSharedSettings.getJsonExpiration().getReadOnce() != null && jsonSharedSettings.getJsonExpiration().getReadOnce()) {
                                holder.settingsUpdateReadOnce.setVisibility(View.VISIBLE);
                                ephemeral = true;
                            } else {
                                holder.settingsUpdateReadOnce.setVisibility(View.GONE);
                            }
                            if (jsonSharedSettings.getJsonExpiration().getVisibilityDuration() != null) {
                                holder.settingsUpdateVisibilityDuration.setVisibility(View.VISIBLE);
                                long duration = jsonSharedSettings.getJsonExpiration().getVisibilityDuration();
                                if (duration < 60) {
                                    holder.settingsUpdateVisibilityDuration.setText(getString(R.string.text_visible_timer_s, duration));
                                } else if (duration < 3600) {
                                    holder.settingsUpdateVisibilityDuration.setText(getString(R.string.text_visible_timer_m, duration / 60));
                                } else if (duration < 86400) {
                                    holder.settingsUpdateVisibilityDuration.setText(getString(R.string.text_visible_timer_h, duration / 3600));
                                } else if (duration < 31536000) {
                                    holder.settingsUpdateVisibilityDuration.setText(getString(R.string.text_visible_timer_d, duration / 86400));
                                } else {
                                    holder.settingsUpdateVisibilityDuration.setText(getString(R.string.text_visible_timer_y, duration / 31536000));
                                }
                                ephemeral = true;
                            } else {
                                holder.settingsUpdateVisibilityDuration.setVisibility(View.GONE);
                            }
                            if (jsonSharedSettings.getJsonExpiration().getExistenceDuration() != null) {
                                holder.settingsUpdateExistenceDuration.setVisibility(View.VISIBLE);
                                long duration = jsonSharedSettings.getJsonExpiration().getExistenceDuration();
                                if (duration < 60) {
                                    holder.settingsUpdateExistenceDuration.setText(getString(R.string.text_existence_timer_s, duration));
                                } else if (duration < 3600) {
                                    holder.settingsUpdateExistenceDuration.setText(getString(R.string.text_existence_timer_m, duration / 60));
                                } else if (duration < 86400) {
                                    holder.settingsUpdateExistenceDuration.setText(getString(R.string.text_existence_timer_h, duration / 3600));
                                } else if (duration < 31536000) {
                                    holder.settingsUpdateExistenceDuration.setText(getString(R.string.text_existence_timer_d, duration / 86400));
                                } else {
                                    holder.settingsUpdateExistenceDuration.setText(getString(R.string.text_existence_timer_y, duration / 31536000));
                                }
                                ephemeral = true;
                            } else {
                                holder.settingsUpdateExistenceDuration.setVisibility(View.GONE);
                            }
                        }
                        if (!ephemeral) {
                            holder.settingsUpdateNotEphemeral.setVisibility(View.VISIBLE);
                        } else {
                            holder.settingsUpdateNotEphemeral.setVisibility(View.GONE);
                        }
                    } catch (Exception e) {
                        holder.settingsUpdateReadOnce.setVisibility(View.GONE);
                        holder.settingsUpdateVisibilityDuration.setVisibility(View.GONE);
                        holder.settingsUpdateExistenceDuration.setVisibility(View.GONE);
                        holder.settingsUpdateNotEphemeral.setVisibility(View.GONE);
                    }
                } else if (message.messageType == Message.TYPE_LEFT_GROUP) {
                    holder.messageInfoTextView.setText(R.string.text_group_left);
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_CONTACT_DELETED) {
                    holder.messageInfoTextView.setText(R.string.text_user_removed_from_contacts);
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                } else if (message.messageType == Message.TYPE_CONTACT_INACTIVE_REASON) {
                    if (Message.NOT_ACTIVE_REASON_REVOKED.equals(message.contentBody)) {
                        holder.messageInfoTextView.setText(R.string.text_contact_was_blocked_revoked);
                    } else {
                        holder.messageInfoTextView.setText(R.string.text_contact_was_blocked);
                    }
                    holder.messageBottomTimestampTextView.setText(StringUtils.getLongNiceDateString(getApplicationContext(), message.timestamp));
                }
            }
        }

        @Override
        public int getItemCount() {
            if (messages != null) {
                return messages.size() + 1;
            }
            return 0;
        }

        @Override
        public long getItemId(int position) {
            if (messages != null && position != 0) {
                return messages.get(position - 1).id;
            }
            return -1;
        }

        @Override
        public void onViewRecycled(@NonNull MessageViewHolder holder) {
            super.onViewRecycled(holder);
            holder.expandMessage = false;
            holder.contentTruncated = false;
            holder.readOnce = false;
            holder.wipeOnly = false;
            holder.callCachedString = null;
            if (holder.expirationTimestamp != null) {
                holder.expirationTimestamp = null;
            }
            if (holder.timerTextView != null) {
                holder.timerTextView.setVisibility(View.GONE);
            }
            if (holder.repliedToMessage != null) {
                holder.repliedToMessage.removeObservers(DiscussionActivity.this);
                holder.repliedToMessage = null;
            }
            if (holder.messageReplyBody != null) {
                holder.messageReplyBody.setText("");
            }
            if (holder.messageContentTextView != null) {
                holder.messageContentTextView.setText("");
            }
            if (holder.messageContentExpander != null) {
                holder.messageContentExpander.setVisibility(View.GONE);
            }
            if (holder.timestampSpacer != null) {
                holder.timestampSpacer.setVisibility(View.VISIBLE);
            }
            if (holder.attachmentsRecyclerView != null) {
                holder.attachmentsRecyclerView.setMinimumHeight(0);
            }
            if (holder.ephemeralHeaderView != null) {
                holder.ephemeralHeaderView.setVisibility(View.GONE);
            }
            if (holder.standardHeaderView != null) {
                holder.standardHeaderView.setVisibility(View.VISIBLE);
            }
            if (holder.attachmentFyles != null) {
                holder.attachmentFyles.removeObservers(DiscussionActivity.this);
                holder.attachmentFyles = null;
            }
            if (holder.adapter != null) {
                holder.adapter.onChanged(null);
            }
            discussionViewModel.getNewDetailsUpdate().removeObserver(holder.newDetailsObserver);
        }

        private class SwipeCallback extends ItemTouchHelper.SimpleCallback implements MessageAttachmentAdapter.BlockMessageSwipeListener {
            private final Bitmap replyIcon;
            private final int replyIconHeight;
            private final int maxReplySwipePx;

            private boolean swipeBlocked = false; // use by audio attachments to allow scrolling

            private Long currentlySwipingMessageId = null;
            private boolean swiping = false;
            private boolean swiped = false;


            private SwipeCallback() {
                super(0, ItemTouchHelper.RIGHT);

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                this.replyIconHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, metrics);
                this.maxReplySwipePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, metrics);

                View iconReply = LayoutInflater.from(DiscussionActivity.this).inflate(R.layout.view_swipe_reply, messageRecyclerView, false);
                iconReply.measure(View.MeasureSpec.makeMeasureSpec(maxReplySwipePx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(replyIconHeight, View.MeasureSpec.EXACTLY));
                iconReply.layout(0, 0, iconReply.getMeasuredWidth(), iconReply.getMeasuredHeight());
                Bitmap bitmap = Bitmap.createBitmap(iconReply.getMeasuredWidth(), iconReply.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                iconReply.draw(canvas);
                replyIcon = bitmap;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder vh) {
                if (selectingForDeletion || swipeBlocked || locked) {
                    return 0;
                }
                if (vh instanceof MessageViewHolder) {
                    MessageViewHolder messageViewHolder = (MessageViewHolder) vh;
                    switch (messageViewHolder.viewType) {
                        case DISCLAIMER:
                        case INFO:
                        case INFO_GROUP_MEMBER:
                        case PHONE_CALL:
                        case NEW_PUBLISHED_DETAILS:
                        case SETTINGS_UPDATE:
                        case INBOUND_EPHEMERAL:
                        case INBOUND_EPHEMERAL_WITH_ATTACHMENT:
                        case OUTBOUND_EPHEMERAL:
                        case OUTBOUND_EPHEMERAL_WITH_ATTACHMENT:
                            return 0;
                        case INBOUND:
                        case INBOUND_WITH_ATTACHMENT:
                        case OUTBOUND:
                        case OUTBOUND_WITH_ATTACHMENT:
                            break;
                    }
                }
                return super.getMovementFlags(recyclerView, vh);
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    if (viewHolder instanceof MessageViewHolder) {
                        swiping = true;
                        swiped = false;
                        currentlySwipingMessageId = ((MessageViewHolder) viewHolder).messageId;
                    }
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    if (swiping && swiped && currentlySwipingMessageId != null) {
                        final long messageId = currentlySwipingMessageId;
                        final long discussionId = discussionViewModel.getDiscussionId();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            App.runThread(new SetDraftReplyTask(discussionId, messageId, composeMessageViewModel.getRawNewMessageText() == null ? null : composeMessageViewModel.getRawNewMessageText().toString()));
                            if (composeMessageDelegate != null) {
                                composeMessageDelegate.showSoftInputKeyboard();
                            }
                        }, 100);
                    }
                    swiping = false;
                    currentlySwipingMessageId = null;
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (!(viewHolder instanceof MessageViewHolder)) {
                    return;
                }
                View itemView = viewHolder.itemView;
                int adjustedTop = Math.max(itemView.getTop(), toolBar.getBottom());
                int adjustedBottom = Math.min(itemView.getBottom(), messageRecyclerView.getBottom() - currentComposeMessageHeight);
                float offsetY;

                if (dX > 0) {
                    Paint opacityPaint = new Paint();
                    opacityPaint.setAlpha((int) (255f * Math.min(dX, maxReplySwipePx) / maxReplySwipePx));
                    offsetY = (adjustedBottom + adjustedTop - replyIconHeight) / 2f;
                    if (offsetY < adjustedTop) {
                        if (adjustedTop == toolBar.getBottom()) {
                            offsetY = adjustedBottom - replyIconHeight;
                        } else {
                            offsetY = adjustedTop;
                        }
                    }
                    c.save();
                    c.drawBitmap(replyIcon, itemView.getLeft(), offsetY, opacityPaint);
                    c.restore();
                }

                if (swiping) {
                    swiped = dX >= maxReplySwipePx;
                }

                super.onChildDraw(c, recyclerView, viewHolder, Math.min(dX, maxReplySwipePx), dY, actionState, isCurrentlyActive);
            }

            // the next two methods make sure the message is never fully "swiped"
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 2;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return Float.MAX_VALUE;
            }


            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void blockMessageSwipe(boolean block) {
                this.swipeBlocked = block;
            }
        }


        class MessageViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
            private final ViewType viewType;
            private final ConstraintLayout messageRootView;
            private final SizeAwareCardView messageContentCardView;
            private final TextView messageContentTextView;
            private final TextView editedBadge;
            private final TextView forwardedBadge;
            private final ImageView directDeleteImageView;
            private final TextView wipedAttachmentCountTextView;
            private final ImageView messageContentExpander;
            private final TextView messageBottomTimestampTextView;
            private final View timestampSpacer;
            private final ImageView messageStatusImageView;
            private boolean shouldAnimateStatusImageView;
            private final CheckBox messageSelectionCheckbox;
            private final View messageCheckboxCompensator;
            private final InitialView senderInitialView;
            private final View senderInitialViewCompensator;
            private final RecyclerView attachmentsRecyclerView;
            private final TextView messageSenderTextView;
            private final MessageAttachmentAdapter adapter;
            private final TextView messageInfoTextView;
            private final ViewGroup messageReplyGroup;
            private final TextView messageReplySenderName;
            private final TextView messageReplyBody;
            private final TextView messageReplyAttachmentCount;

            private final TextView settingsUpdateReadOnce;
            private final TextView settingsUpdateVisibilityDuration;
            private final TextView settingsUpdateExistenceDuration;
            private final TextView settingsUpdateNotEphemeral;

            private final ImageView callBackButton;
            private final ImageView newDetailsIcon;
            private final ImageView newDetailsRedDot;
            private final Observer<Integer> newDetailsObserver;

            private final View standardHeaderView;
            private final View ephemeralHeaderView;
            private final TextView ephemeralExplanationTextView;
            private final TextView ephemeralTimerTextView;
            private final TextView ephemeralSenderTextView;
            private final TextView ephemeralTimestampTextView;

            private final TextView timerTextView;
            private final TextView missedMessagesTextView;

            private final DynamicFlow reactionsDynamicFlow;

            private LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> attachmentFyles;
            private LiveData<Message> repliedToMessage;

            private boolean expandMessage;
            private boolean contentTruncated;
            private long messageId;
            private long replyMessageId;
            private Long expirationTimestamp;
            private boolean wipeOnly;
            private boolean readOnce;
            private long lastRemainingDisplayed;
            private Long callLogItemId;
            private String callCachedString;

            @SuppressLint("ClickableViewAccessibility")
            MessageViewHolder(View itemView, ViewType viewType) {
                super(itemView);
                this.viewType = viewType;
                statusWidth = (viewType == ViewType.OUTBOUND_EPHEMERAL
                        || viewType == ViewType.OUTBOUND
                        || viewType == ViewType.OUTBOUND_EPHEMERAL_WITH_ATTACHMENT
                        || viewType == ViewType.OUTBOUND_WITH_ATTACHMENT) ? statusIconWidth : noStatusIconWidth;

                if (viewType != ViewType.DISCLAIMER) {
                    itemView.setOnLongClickListener(this);
                    itemView.setOnClickListener(this);
                }
                messageRootView = (ConstraintLayout) itemView;
                messageContentCardView = itemView.findViewById(R.id.message_content_card);
                if (messageContentCardView != null) {
                    GestureDetector gestureDetector = new GestureDetector(DiscussionActivity.this, new GestureDetector.SimpleOnGestureListener() {
                        private final int[] posRecyclerView = new int[2];
                        private final int[] posCardView = new int[2];

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            Utils.launchModifyMessagePopup(DiscussionActivity.this, messages.get(getBindingAdapterPosition() - 1));
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            MessageViewHolder.this.onClick(messageContentCardView);
                            return true;
                        }

                        @Override
                        public void onLongPress(MotionEvent e) {
                            if (discussionDelegate != null) {
                                messageRecyclerView.getLocationInWindow(posRecyclerView);
                                messageContentCardView.getLocationInWindow(posCardView);
                                new MessageLongPressPopUp(DiscussionActivity.this, discussionDelegate, messageRecyclerView, posCardView[0] - posRecyclerView[0] + (int) e.getX(), posCardView[1] - posRecyclerView[1] + (int) e.getY(),
                                        posRecyclerView[1] + messageRecyclerView.getHeight() - posCardView[1] - messageContentCardView.getHeight(),
                                        messageId);
                            }
                        }
                    });
                    messageContentCardView.setOnTouchListener((View v, MotionEvent event) -> gestureDetector.onTouchEvent(event));
                }
                messageContentTextView = itemView.findViewById(R.id.message_content_text_view);
                editedBadge = itemView.findViewById(R.id.edited_text_view);
                forwardedBadge = itemView.findViewById(R.id.message_forwarded_badge);
                directDeleteImageView = itemView.findViewById(R.id.direct_delete_image_view);
                if (directDeleteImageView != null) {
                    directDeleteImageView.setOnClickListener(this);
                }
                wipedAttachmentCountTextView = itemView.findViewById(R.id.wiped_attachment_count);
                messageReplyGroup = itemView.findViewById(R.id.message_content_reply_group);
                messageReplySenderName = itemView.findViewById(R.id.message_content_reply_sender_name);
                messageReplyBody = itemView.findViewById(R.id.message_content_reply_body);
                messageReplyAttachmentCount = itemView.findViewById(R.id.message_content_reply_attachment_count);
                if (messageReplyGroup != null) {
                    messageReplyGroup.setOnClickListener(this);
                }

                messageContentExpander = itemView.findViewById(R.id.message_content_expander);
                messageBottomTimestampTextView = itemView.findViewById(R.id.message_timestamp_bottom_text_view);
                timestampSpacer = itemView.findViewById(R.id.timestamp_spacer);
                messageStatusImageView = itemView.findViewById(R.id.message_status_image_view);
                shouldAnimateStatusImageView = false;

                if (messageContentCardView != null && messageContentTextView != null && messageBottomTimestampTextView != null) {
                    messageContentTextView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        if (right - left != oldRight - oldLeft || top - bottom != oldTop - oldBottom) {
                            recomputeLayout();
                        }
                    });
                    messageBottomTimestampTextView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        if (right - left != oldRight - oldLeft || top - bottom != oldTop - oldBottom) {
                            recomputeLayout();
                        }
                    });
                    messageContentCardView.setSizeChangeListener((w, h, oldw, oldh) -> recomputeLayout());
                }

                messageSenderTextView = itemView.findViewById(R.id.message_sender_text_view);
                messageSelectionCheckbox = itemView.findViewById(R.id.message_selection_checkbox);
                messageCheckboxCompensator = itemView.findViewById(R.id.message_checkbox_compensator);
                senderInitialView = itemView.findViewById(R.id.sender_initial_view);
                senderInitialViewCompensator = itemView.findViewById(R.id.sender_initial_view_compensator);
                attachmentsRecyclerView = itemView.findViewById(R.id.attachments_recycler_view);
                messageInfoTextView = itemView.findViewById(R.id.message_info_text_view);

                View messageInfoBackground = itemView.findViewById(R.id.message_info_background);
                if (messageInfoBackground != null) {
                    messageInfoBackground.setClipToOutline(true);
                    messageInfoBackground.setOnClickListener(this);
                    messageInfoBackground.setOnLongClickListener(this);
                }

                standardHeaderView = itemView.findViewById(R.id.standard_header);
                ephemeralHeaderView = itemView.findViewById(R.id.ephemeral_header);
                ephemeralSenderTextView = itemView.findViewById(R.id.ephemeral_message_sender_text_view);
                ephemeralExplanationTextView = itemView.findViewById(R.id.ephemeral_explanation_text_view);
                ephemeralTimerTextView = itemView.findViewById(R.id.ephemeral_timer);
                ephemeralTimestampTextView = itemView.findViewById(R.id.ephemeral_message_timestamp_bottom_text_view);


                timerTextView = itemView.findViewById(R.id.message_timer_textview);
                missedMessagesTextView = itemView.findViewById(R.id.missing_message_textview);
                if (missedMessagesTextView != null) {
                    missedMessagesTextView.setOnClickListener(v -> {
                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(DiscussionActivity.this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_missing_messages)
                                .setMessage(R.string.dialog_message_missing_messages)
                                .setPositiveButton(R.string.button_label_ok, null);
                        builder.create().show();
                    });
                }
                // reactions
                reactionsDynamicFlow = itemView.findViewById(R.id.reactions_dynamic_flow);

                expandMessage = false;
                contentTruncated = false;
                replyMessageId = -1;

                if (viewType == ViewType.INBOUND_WITH_ATTACHMENT ||
                        viewType == ViewType.OUTBOUND_WITH_ATTACHMENT ||
                        viewType == ViewType.OUTBOUND_EPHEMERAL_WITH_ATTACHMENT ||
                        viewType == ViewType.INBOUND_EPHEMERAL_WITH_ATTACHMENT) {
                    adapter = new MessageAttachmentAdapter(DiscussionActivity.this, audioAttachmentServiceBinding, discussionViewModel.getDiscussionId());
                    adapter.setAttachmentLongClickListener(DiscussionActivity.this);
                    adapter.setNoWipeListener(() -> markAsReadOnPause = false);
                    adapter.setBlockMessageSwipeListener(swipeCallback);
                    GridLayoutManager attachmentsLayoutManager = new GridLayoutManager(DiscussionActivity.this, adapter.getColumnCount());
                    attachmentsLayoutManager.setOrientation(RecyclerView.VERTICAL);
                    attachmentsLayoutManager.setSpanSizeLookup(adapter.getSpanSizeLookup());

                    attachmentsRecyclerView.setLayoutManager(attachmentsLayoutManager);
                    attachmentsRecyclerView.setAdapter(adapter);
                    attachmentsRecyclerView.addItemDecoration(adapter.getItemDecoration());
                    attachmentFyles = null;
                } else {
                    adapter = null;
                }

                if (senderInitialView != null) {
                    senderInitialView.setOnClickListener(this);
                }

                if (viewType == ViewType.SETTINGS_UPDATE) {
                    settingsUpdateReadOnce = itemView.findViewById(R.id.read_once);
                    settingsUpdateVisibilityDuration = itemView.findViewById(R.id.visibility);
                    settingsUpdateExistenceDuration = itemView.findViewById(R.id.existence);
                    settingsUpdateNotEphemeral = itemView.findViewById(R.id.not_ephemeral);
                } else {
                    settingsUpdateReadOnce = null;
                    settingsUpdateVisibilityDuration = null;
                    settingsUpdateExistenceDuration = null;
                    settingsUpdateNotEphemeral = null;
                }

                if (viewType == ViewType.PHONE_CALL) {
                    callBackButton = itemView.findViewById(R.id.call_back_button);
                    callBackButton.setOnClickListener(this);
                } else {
                    callBackButton = null;
                }

                if (viewType == ViewType.NEW_PUBLISHED_DETAILS) {
                    newDetailsIcon = itemView.findViewById(R.id.new_published_details_icon);
                    newDetailsRedDot = itemView.findViewById(R.id.new_published_details_red_dot);
                    newDetailsObserver = (Integer publishedDetailsStatus) -> {
                        if (publishedDetailsStatus != null) {
                            switch (publishedDetailsStatus) {
                                case Group.PUBLISHED_DETAILS_NEW_UNSEEN: // same as Contact.PUBLISHED_DETAILS_NEW_UNSEEN
                                    newDetailsIcon.setVisibility(View.VISIBLE);
                                    newDetailsRedDot.setVisibility(View.VISIBLE);
                                    return;
                                case Group.PUBLISHED_DETAILS_NEW_SEEN: // same as Contact.PUBLISHED_DETAILS_NEW_SEEN
                                    newDetailsIcon.setVisibility(View.VISIBLE);
                                    newDetailsRedDot.setVisibility(View.GONE);
                                    return;
                            }
                        }
                        newDetailsIcon.setVisibility(View.GONE);
                        newDetailsRedDot.setVisibility(View.GONE);
                    };
                } else {
                    newDetailsIcon = null;
                    newDetailsRedDot = null;
                    newDetailsObserver = null;
                }
            }

            public void setMessageId(final long messageId, final boolean readOnce) {
                if (readOnce && !screenShotBlockedForEphemeral) {
                    screenShotBlockedForEphemeral = true;
                    Window window = getWindow();
                    if (window != null) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                    }
                }
                this.messageId = messageId;
                App.runThread(() -> {
                    MessageExpiration expiration = AppDatabase.getInstance().messageExpirationDao().get(messageId);
                    if (expiration != null) {
                        this.expirationTimestamp = expiration.expirationTimestamp;
                        this.wipeOnly = expiration.wipeOnly;
                        this.lastRemainingDisplayed = -1;
                        messagesWithTimerMap.put(messageId, this);
                    } else {
                        this.expirationTimestamp = null;
                        this.wipeOnly = false;
                    }
                    this.readOnce = readOnce;
                    runOnUiThread(() -> {
                        if (wipeOnly && !screenShotBlockedForEphemeral) {
                            screenShotBlockedForEphemeral = true;
                            Window window = getWindow();
                            if (window != null) {
                                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                            }
                        }
                        updateTimerTextView(System.currentTimeMillis());
                    });
                });
            }

            private void updateTimerTextView(long timestamp) {
                if (timerTextView == null) {
                    return;
                }
                if (expirationTimestamp != null) {
                    if (timerTextView.getVisibility() != View.VISIBLE) {
                        LayoutTransition lt = messageRootView.getLayoutTransition();
                        messageRootView.setLayoutTransition(null);
                        timerTextView.setVisibility(View.VISIBLE);
                        messageRootView.setLayoutTransition(lt);
                    }
                    long remaining = (expirationTimestamp - timestamp) / 1000;
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
                                color = ContextCompat.getColor(DiscussionActivity.this, R.color.red);
                            }
                        } else if (remaining < 3600) {
                            if (remaining / 60 == lastRemainingDisplayed / 60) {
                                return;
                            }
                            timerTextView.setText(getString(R.string.text_timer_m, remaining / 60));
                            if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 3600) {
                                color = ContextCompat.getColor(DiscussionActivity.this, R.color.orange);
                            }
                        } else if (remaining < 86400) {
                            if (remaining / 3600 == lastRemainingDisplayed / 3600) {
                                return;
                            }
                            timerTextView.setText(getString(R.string.text_timer_h, remaining / 3600));
                            if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 86400) {
                                color = ContextCompat.getColor(DiscussionActivity.this, R.color.greyTint);
                            }
                        } else if (remaining < 31536000) {
                            if (remaining / 86400 == lastRemainingDisplayed / 86400) {
                                return;
                            }
                            timerTextView.setText(getString(R.string.text_timer_d, remaining / 86400));
                            if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 31536000) {
                                color = ContextCompat.getColor(DiscussionActivity.this, R.color.lightGrey);
                            }
                        } else {
                            if (remaining / 31536000 == lastRemainingDisplayed / 31536000) {
                                return;
                            }
                            timerTextView.setText(getString(R.string.text_timer_y, remaining / 31536000));
                            if (lastRemainingDisplayed < 0) {
                                color = ContextCompat.getColor(DiscussionActivity.this, R.color.lightGrey);
                            }
                        }
                    } finally {
                        lastRemainingDisplayed = remaining;
                        if (color != 0) {
                            if (readOnce) {
                                timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_burn_small, 0, 0);
                                timerTextView.setTextColor(ContextCompat.getColor(DiscussionActivity.this, R.color.red));
                            } else {
                                timerTextView.setTextColor(color);
                                Drawable drawable;
                                if (wipeOnly) {
                                    drawable = ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_eye_small);
                                } else {
                                    drawable = ContextCompat.getDrawable(DiscussionActivity.this, R.drawable.ic_timer_small);
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
                        LayoutTransition lt = messageRootView.getLayoutTransition();
                        messageRootView.setLayoutTransition(null);
                        timerTextView.setVisibility(View.VISIBLE);
                        messageRootView.setLayoutTransition(lt);
                    }
                    timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_burn_small, 0, 0);
                    timerTextView.setText(null);
                } else {
                    if (timerTextView.getVisibility() != View.GONE) {
                        LayoutTransition lt = messageRootView.getLayoutTransition();
                        messageRootView.setLayoutTransition(null);
                        timerTextView.setVisibility(View.GONE);
                        messageRootView.setLayoutTransition(lt);
                    }
                }
            }

            final Rect rect = new Rect();
            final float statusWidth;

            private void recomputeLayout() {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Layout messageTextLayout = messageContentTextView.getLayout();
                    Layout timestampLayout = messageBottomTimestampTextView.getLayout();
                    if (messageTextLayout != null) {
                        int lineCount = messageTextLayout.getLineCount();
                        if (lineCount > COMPACT_MESSAGE_LINE_COUNT || (lineCount == COMPACT_MESSAGE_LINE_COUNT &&
                                messageTextLayout.getEllipsisCount(COMPACT_MESSAGE_LINE_COUNT - 1) > 0)) {
                            // text is ellipsized
                            contentTruncated = true;
                            if (messageContentExpander.getVisibility() != View.VISIBLE) {
                                if (viewType == ViewType.INBOUND || viewType == ViewType.INBOUND_WITH_ATTACHMENT) {
                                    messageContentExpander.setImageResource(R.drawable.ic_expander_inbound);
                                } else {
                                    messageContentExpander.setImageResource(R.drawable.ic_expander_outbound);
                                }
                            }
                            messageContentExpander.setVisibility(View.VISIBLE);
                        } else {
                            messageContentExpander.setVisibility(View.GONE);
                        }

                        if (timestampLayout != null) {
                            float timestampWidth = timestampLayout.getLineMax(0) + statusWidth;
                            float lastMessageLineWidth = messageTextLayout.getLineMax(lineCount - 1);
                            messageTextLayout.getLineBounds(lineCount - 1, rect);
                            int messageTotalWidth = rect.right - rect.left;
                            if (messageTextLayout.getAlignment().equals(Layout.Alignment.ALIGN_CENTER)) {
                                lastMessageLineWidth += (messageTotalWidth - lastMessageLineWidth) / 2;
                            }
                            if (timestampWidth + lastMessageLineWidth < messageTotalWidth) {
                                // set VISIBLE, then GONE to force a redraw if it was already GONE
                                timestampSpacer.setVisibility(View.VISIBLE);
                                timestampSpacer.setVisibility(View.GONE);
                            } else {
                                // set GONE, then VISIBLE to force a redraw if it was already VISIBLE
                                timestampSpacer.setVisibility(View.GONE);
                                timestampSpacer.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });
            }

            @Override
            public boolean onLongClick(View messageView) {
                Message message = messages.get(this.getLayoutPosition() - 1);
                DiscussionActivity.this.messageLongClicked(message, messageView);
                return true;
            }

            @Override
            public void onClick(View view) {
                int id = view.getId();
                if (id == R.id.direct_delete_image_view) {
                    if (selectingForDeletion) {
                        discussionViewModel.unselectMessageId(messageId);
                    }
                    Discussion discussion = discussionViewModel.getDiscussion().getValue();
                    if (discussion != null) {
                        App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, Collections.singletonList(messageId), false));
                    }
                } else if (id == R.id.sender_initial_view) {
                    Discussion discussion = discussionViewModel.getDiscussion().getValue();
                    if (discussion != null) {
                        App.runThread(() -> {
                            byte[] senderBytes = messages.get(this.getLayoutPosition() - 1).senderIdentifier;
                            Contact contact = AppDatabase.getInstance().contactDao().get(discussion.bytesOwnedIdentity, senderBytes);
                            if (contact != null) {
                                if (contact.oneToOne) {
                                    runOnUiThread(() -> App.openOneToOneDiscussionActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, messages.get(this.getLayoutPosition() - 1).senderIdentifier, false));
                                } else {
                                    runOnUiThread(() -> App.openContactDetailsActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, messages.get(this.getLayoutPosition() - 1).senderIdentifier));
                                }
                            }
                        });
                    }
                } else if (id == R.id.call_back_button) {
                    if (callLogItemId != null) {
                        App.runThread(() -> {
                            CallLogItemDao.CallLogItemAndContacts callLogItem = AppDatabase.getInstance().callLogItemDao().get(callLogItemId);
                            if (callLogItem != null) {
                                if (callLogItem.contacts.size() == 1 && callLogItem.callLogItem.bytesGroupOwnerAndUid == null) {
                                    if (callLogItem.oneContact != null && callLogItem.oneContact.establishedChannelCount > 0) {
                                        App.startWebrtcCall(DiscussionActivity.this, callLogItem.oneContact.bytesOwnedIdentity, callLogItem.oneContact.bytesContactIdentity);
                                    }
                                } else {
                                    List<byte[]> bytesContactIdentities = new ArrayList<>(callLogItem.contacts.size());
                                    for (CallLogItemContactJoin callLogItemContactJoin : callLogItem.contacts) {
                                        bytesContactIdentities.add(callLogItemContactJoin.bytesContactIdentity);
                                    }
                                    MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(callLogItem.callLogItem.bytesOwnedIdentity, callLogItem.callLogItem.bytesGroupOwnerAndUid, bytesContactIdentities);
                                    multiCallStartDialogFragment.show(getSupportFragmentManager(), "dialog");
                                }
                            }
                        });
                    } else {
                        Discussion discussion = discussionViewModel.getDiscussion().getValue();
                        if (discussion != null && discussion.bytesContactIdentity != null) {
                            List<Contact> contacts = discussionViewModel.getDiscussionContacts().getValue();
                            if (contacts != null && contacts.size() > 0 && contacts.get(0).establishedChannelCount > 0) {
                                App.startWebrtcCall(DiscussionActivity.this, discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
                            }
                        }
                    }
                } else if (id == R.id.message_content_reply_group) {
                    if (replyMessageId != -1) {
                        messageListAdapter.requestScrollToMessageId(replyMessageId, false, true);
                    }
                } else if (id == R.id.message_root_constraint_layout || id == R.id.message_content_card || id == R.id.message_info_background) {
                    if (!selectingForDeletion) {
                        if (viewType == ViewType.NEW_PUBLISHED_DETAILS) {
                            Discussion discussion = discussionViewModel.getDiscussion().getValue();
                            if (discussion != null) {
                                if (discussion.bytesGroupOwnerAndUid != null) {
                                    App.openGroupDetailsActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
                                } else if (discussion.bytesContactIdentity != null) {
                                    App.openContactDetailsActivity(DiscussionActivity.this, discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
                                }
                            }
                        } else if (viewType == ViewType.INBOUND_EPHEMERAL || viewType == ViewType.INBOUND_EPHEMERAL_WITH_ATTACHMENT) {
                            if (getLayoutPosition() > 0 && id == R.id.message_content_card) {
                                App.runThread(new InboundEphemeralMessageClicked(messages.get(getLayoutPosition() - 1).id));
                            }
                        } else if (viewType == ViewType.SETTINGS_UPDATE) {
                            Discussion discussion = discussionViewModel.getDiscussion().getValue();
                            if (discussion != null) {
                                markAsReadOnPause = false;
                                Intent intent = new Intent(DiscussionActivity.this, DiscussionSettingsActivity.class);
                                intent.putExtra(DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
                                intent.putExtra(DiscussionSettingsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, discussion.bytesOwnedIdentity);
                                intent.putExtra(DiscussionSettingsActivity.LOCKED_INTENT_EXTRA, discussion.isLocked());
                                intent.putExtra(DiscussionSettingsActivity.SCROLL_TO_EPHEMERAL, true);
                                if (discussion.bytesGroupOwnerAndUid != null) {
                                    intent.putExtra(DiscussionSettingsActivity.BYTES_GROUP_OWNED_AND_UID_INTENT_EXTRA, discussion.bytesGroupOwnerAndUid);
                                }
                                startActivity(intent);
                            }
                        } else if (viewType == ViewType.INFO_GROUP_MEMBER && id == R.id.message_info_background) {
                            Discussion discussion = discussionViewModel.getDiscussion().getValue();
                            if (discussion != null) {
                                App.runThread(() -> {
                                    Message message = AppDatabase.getInstance().messageDao().get(messageId);
                                    if (message != null &&
                                            (message.messageType == Message.TYPE_GROUP_MEMBER_JOINED || message.messageType == Message.TYPE_GROUP_MEMBER_LEFT)) {
                                        Contact contact = AppDatabase.getInstance().contactDao().get(discussion.bytesOwnedIdentity, message.senderIdentifier);
                                        if (contact != null) {
                                            // we found a contact --> open their details
                                            runOnUiThread(() -> App.openContactDetailsActivity(DiscussionActivity.this, contact.bytesOwnedIdentity, contact.bytesContactIdentity));
                                        }
                                    }
                                });
                            }
                        } else {
                            expandMessage = !expandMessage;
                            notifyItemChanged(getLayoutPosition(), MESSAGE_EXPAND_CHANGE_MASK);
                        }
                    } else {
                        if (getLayoutPosition() > 0) {
                            DiscussionActivity.this.messageClicked(messages.get(getLayoutPosition() - 1));
                        }
                    }
                } else {
                    if (selectingForDeletion && getLayoutPosition() > 0) {
                        DiscussionActivity.this.messageClicked(messages.get(getLayoutPosition() - 1));
                    }
                }
            }
        }
    }

    interface DiscussionDelegate {
        void markMessagesRead();
        void doNotMarkAsReadOnPause();
        void scrollToMessage(long messageId);
        void replyToMessage(long discussionId, long messageId);
        void initiateMessageForward(long messageId, Runnable openDialogCallback);
        void selectMessage(long messageId, boolean forwardable);
        void setAdditionalBottomPadding(int paddingPx);
    }
}
