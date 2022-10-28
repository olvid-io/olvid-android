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

package io.olvid.messenger.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.DeleteMessagesTask;
import io.olvid.messenger.discussion.settings.DiscussionSettingsActivity;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.notifications.NotificationActionService;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel;

public class DiscussionListFragment extends Fragment implements PopupMenu.OnMenuItemClickListener, SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener {
    DiscussionListAdapter adapter;
    private DiscussionListViewModel discussionListViewModel;
    private Long engineNotificationListenerRegistrationNumber;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FragmentActivity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        discussionListViewModel = new ViewModelProvider(this).get(DiscussionListViewModel.class);
        engineNotificationListenerRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_main_fragment_discussion_list, container, false);

        EmptyRecyclerView recyclerView = rootView.findViewById(R.id.discussion_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        View recyclerEmptyView = rootView.findViewById(R.id.discussion_list_empty_view);
        recyclerView.setEmptyView(recyclerEmptyView);

        View loadingSpinner = rootView.findViewById(R.id.loading_spinner);
        recyclerView.setLoadingSpinner(loadingSpinner);

        adapter = new DiscussionListAdapter();
        if (discussionListViewModel.getDiscussions() != null) {
            discussionListViewModel.getDiscussions().observe(activity, adapter);
        }
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new ItemDecorationSimpleDivider(rootView.getContext(), 84, 12));

        swipeRefreshLayout = rootView.findViewById(R.id.discussion_list_swipe_refresh_layout);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.dialogBackground));
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary700);
        return rootView;
    }

    @Override
    public void onRefresh() {
        if (AppSingleton.getBytesCurrentIdentity() != null) {
            AppSingleton.getEngine().downloadMessages(AppSingleton.getBytesCurrentIdentity());
            App.runThread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                    }
                });
            });
        } else {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (EngineNotifications.SERVER_POLLED.equals(notificationName)) {
            byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY);
            final Boolean success = (Boolean) userInfo.get(EngineNotifications.SERVER_POLLED_SUCCESS_KEY);
            if (success != null
                    && Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        if (!success) {
                            App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationListenerRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber != null;
    }

    public void discussionClicked(Discussion discussion, View view) {
        App.openDiscussionActivity(view.getContext(), discussion.id);
    }

    private DiscussionDao.DiscussionAndLastMessage longClickedDiscussion;

    private void discussionLongClicked(DiscussionDao.DiscussionAndLastMessage discussion, View view) {
        this.longClickedDiscussion = discussion;
        PopupMenu popup = new PopupMenu(view.getContext(), view);

        // inflate pin or unpin button depending on discussion state
        if (discussion.discussion.pinned) {
            popup.inflate(R.menu.popup_discussion_unpin);
        } else {
            popup.inflate(R.menu.popup_discussion_pin);
        }
        // inflate mark as read or unread button depending on discussion state
        if (discussion.discussion.unread || discussion.unreadCount > 0) {
            popup.inflate(R.menu.popup_discussion_mark_as_read);
        } else {
            popup.inflate(R.menu.popup_discussion_mark_as_unread);
        }
        if (discussion.discussion.canPostMessages()) {
            switch (discussion.discussion.discussionType) {
                case Discussion.TYPE_CONTACT:
                    popup.inflate(R.menu.popup_discussion_one_to_one);
                    break;
                case Discussion.TYPE_GROUP:
                case Discussion.TYPE_GROUP_V2:
                    popup.inflate(R.menu.popup_discussion_group);
                    break;
            }
        } else {
            popup.inflate(R.menu.popup_discussion_locked);
        }
        popup.inflate(R.menu.popup_discussion);
        popup.setOnMenuItemClickListener(this);

        MenuItem deleteItem = popup.getMenu().findItem(R.id.popup_action_delete_discussion);
        if (deleteItem != null) {
            SpannableString spannableString = new SpannableString(deleteItem.getTitle());
            spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteItem.setTitle(spannableString);
        }
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.popup_action_discussion_mark_as_read) {
            // mark as read implies: change discussion unread, mark message as read, send read receipts if needed
            App.runThread(() -> NotificationActionService.markAllDiscussionMessagesRead(longClickedDiscussion.discussion.id));
        } else if (itemId == R.id.popup_action_discussion_mark_as_unread) {
            App.runThread(() -> AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(longClickedDiscussion.discussion.id, true));
        } else if (itemId == R.id.popup_action_discussion_pin) {
            // mark as read implies: change discussion unread, mark message as read, send read receipts if needed
            App.runThread(() -> AppDatabase.getInstance().discussionDao().updatePinned(longClickedDiscussion.discussion.id, true));
        } else if (itemId == R.id.popup_action_discussion_unpin) {
            App.runThread(() -> AppDatabase.getInstance().discussionDao().updatePinned(longClickedDiscussion.discussion.id, false));
        } else if (itemId == R.id.popup_action_delete_discussion) {
            App.runThread(() -> {
                final String title;
                if (longClickedDiscussion.discussion.title.length() == 0) {
                    title = getString(R.string.text_unnamed_discussion);
                } else {
                    title = longClickedDiscussion.discussion.title;
                }
                boolean canRemoteDelete;
                if (longClickedDiscussion.discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                    Group2 group2 = AppDatabase.getInstance().group2Dao().get(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                    if (group2 != null) {
                        canRemoteDelete = group2.ownPermissionRemoteDeleteAnything;
                    } else {
                        canRemoteDelete = false;
                    }
                } else {
                    canRemoteDelete = longClickedDiscussion.discussion.canPostMessages();
                }
                final AlertDialog.Builder builder;
                if (canRemoteDelete) {
                    builder = new SecureDeleteEverywhereDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_discussion)
                            .setMessage(getString(R.string.dialog_message_delete_discussion, title))
                            .setType(SecureDeleteEverywhereDialogBuilder.TYPE.DISCUSSION)
                            .setDeleteCallback(deleteEverywhere -> App.runThread(new DeleteMessagesTask(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.id, deleteEverywhere, false)));
                } else {
                    builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_discussion)
                            .setMessage(getString(R.string.dialog_message_delete_discussion, title))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteMessagesTask(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.id, false, false)))
                            .setNegativeButton(R.string.button_label_cancel, null);
                }
                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
            });
            return true;
        } else if (itemId == R.id.popup_action_rename_discussion) {
            if (longClickedDiscussion.discussion.canPostMessages()) {
                switch (longClickedDiscussion.discussion.discussionType) {
                    case Discussion.TYPE_CONTACT: {
                        App.runThread(() -> {
                            final Contact contact = AppDatabase.getInstance().contactDao().get(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                            if (contact != null) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, contact);
                                    editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
                                });
                            }
                        });
                        break;
                    }
                    case Discussion.TYPE_GROUP: {
                        App.runThread(() -> {
                            final Group group = AppDatabase.getInstance().groupDao().get(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                            if (group != null) {
                                if (group.bytesGroupOwnerIdentity == null) {
                                    // you own the group --> show group details and open edit details
                                    App.openGroupDetailsActivityForEditDetails(activity, longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                                } else {
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, group);
                                        editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
                                    });
                                }
                            }
                        });
                        break;
                    }
                    case Discussion.TYPE_GROUP_V2: {
                        App.runThread(() -> {
                            final Group2 group2 = AppDatabase.getInstance().group2Dao().get(longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                            if (group2 != null) {
                                if (group2.ownPermissionAdmin) {
                                    // you own the group --> show group details and open edit details
                                    App.openGroupV2DetailsActivityForEditDetails(activity, longClickedDiscussion.discussion.bytesOwnedIdentity, longClickedDiscussion.discussion.bytesDiscussionIdentifier);
                                } else {
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, group2);
                                        editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
                                    });
                                }
                            }
                        });
                        break;
                    }
                }
            } else {
                // locked discussion
                EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, longClickedDiscussion.discussion);
                editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.popup_action_discussion_settings) {
            Intent intent = new Intent(getContext(), DiscussionSettingsActivity.class);
            intent.putExtra(DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA, longClickedDiscussion.discussion.id);
            startActivity(intent);
            return true;
        }
        return false;
    }


    public class DiscussionListAdapter extends LoadAwareAdapter<DiscussionListAdapter.DiscussionViewHolder> implements Observer<List<DiscussionDao.DiscussionAndLastMessage>> {
        private List<DiscussionDao.DiscussionAndLastMessage> discussionsAndLastMessages = null;
        private final LayoutInflater inflater;

        private static final int NORMAL_VIEWTYPE = 0;
        private static final int LOCKED_VIEWTYPE = 1;

        DiscussionListAdapter() {
            inflater = LayoutInflater.from(activity);
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if (discussionsAndLastMessages != null) {
                return discussionsAndLastMessages.get(position).discussion.id;
            }
            return 0;
        }


        @Override
        public boolean isLoadingDone() {
            return discussionsAndLastMessages != null;
        }

        @Override
        public int getItemViewType(int position) {
            if (discussionsAndLastMessages != null) {
                DiscussionDao.DiscussionAndLastMessage discussion = discussionsAndLastMessages.get(position);
                switch (discussion.discussion.status) {
                    case Discussion.STATUS_LOCKED:
                        return LOCKED_VIEWTYPE;
                    case Discussion.STATUS_NORMAL:
                    default:
                        return NORMAL_VIEWTYPE;
                }
            }
            return NORMAL_VIEWTYPE;
        }


        @NonNull
        @Override
        public DiscussionListAdapter.DiscussionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case LOCKED_VIEWTYPE: {
                    View discussionRootView = inflater.inflate(R.layout.item_view_discussion_locked, parent, false);
                    return new DiscussionViewHolder(discussionRootView);
                }
                case NORMAL_VIEWTYPE:
                default: {
                    View discussionRootView = inflater.inflate(R.layout.item_view_discussion, parent, false);
                    return new DiscussionViewHolder(discussionRootView);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final DiscussionListAdapter.DiscussionViewHolder holder, int position) {
            if (discussionsAndLastMessages == null) {
                return;
            }
            final DiscussionDao.DiscussionAndLastMessage discussionAndLastMessage = discussionsAndLastMessages.get(position);
            if (discussionAndLastMessage.discussion.title.length() == 0) {
                SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_discussion));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.nameTextView.setText(spannableString);
            } else {
                holder.nameTextView.setText(discussionAndLastMessage.discussion.title);
            }

            if (discussionAndLastMessage.message != null) {
                switch (discussionAndLastMessage.message.messageType) {
                    case Message.TYPE_OUTBOUND_MESSAGE: {
                        String body = discussionAndLastMessage.message.getStringContent(activity);
                        if (discussionAndLastMessage.message.status == Message.STATUS_DRAFT) {
                            SpannableString text = new SpannableString(getString(R.string.text_draft_message_prefix, body));
                            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                            text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.lastMessageContentTextView.setText(text);
                        } else if (discussionAndLastMessage.message.wipeStatus == Message.WIPE_STATUS_WIPED
                                || discussionAndLastMessage.message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                            SpannableString text = new SpannableString(body);
                            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                            text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.lastMessageContentTextView.setText(text);
                        } else {
                            if (discussionAndLastMessage.message.isLocationMessage()) {
                                SpannableString text = new SpannableString(getString(R.string.text_outbound_message_prefix, body));
                                StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                                text.setSpan(styleSpan, text.length() - body.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                holder.lastMessageContentTextView.setText(text);
                            } else {
                                holder.lastMessageContentTextView.setText(getString(R.string.text_outbound_message_prefix, body));
                            }
                        }
                        break;
                    }
                    case Message.TYPE_GROUP_MEMBER_JOINED: {
                        String displayName = AppSingleton.getContactCustomDisplayName(discussionAndLastMessage.message.senderIdentifier);
                        SpannableString spannableString;
                        if (displayName != null) {
                            spannableString = new SpannableString(getString(R.string.text_joined_the_group, displayName));
                        } else {
                            spannableString = new SpannableString(getString(R.string.text_unknown_member_joined_the_group));
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_GROUP_MEMBER_LEFT: {
                        String displayName = AppSingleton.getContactCustomDisplayName(discussionAndLastMessage.message.senderIdentifier);
                        SpannableString spannableString;
                        if (displayName != null) {
                            spannableString = new SpannableString(getString(R.string.text_left_the_group, displayName));
                        } else {
                            spannableString = new SpannableString(getString(R.string.text_unknown_member_left_the_group));
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_DISCUSSION_REMOTELY_DELETED: {
                        String displayName = AppSingleton.getContactCustomDisplayName(discussionAndLastMessage.message.senderIdentifier);
                        SpannableString spannableString;
                        if (displayName != null) {
                            spannableString = new SpannableString(getString(R.string.text_discussion_remotely_deleted_by, displayName));
                        } else {
                            spannableString = new SpannableString(getString(R.string.text_discussion_remotely_deleted));
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_LEFT_GROUP: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_group_left));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_CONTACT_INACTIVE_REASON: {
                        SpannableString spannableString;
                        if (Message.NOT_ACTIVE_REASON_REVOKED.equals(discussionAndLastMessage.message.contentBody)) {
                            spannableString = new SpannableString(getString(R.string.text_contact_was_blocked_revoked));
                        } else {
                            spannableString = new SpannableString(getString(R.string.text_contact_was_blocked));
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_PHONE_CALL: {
                        final SpannableString spannableString;

                        int callStatus = CallLogItem.STATUS_MISSED;
                        try {
                            //noinspection ConstantConditions
                            String[] statusAndCallLogItemId = discussionAndLastMessage.message.contentBody.split(":");
                            if (statusAndCallLogItemId.length > 0) {
                                callStatus = Integer.parseInt(statusAndCallLogItemId[0]);
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                        // callStatus is positive for incoming calls
                        switch(callStatus) {
                            case -CallLogItem.STATUS_BUSY:  {
                                spannableString = new SpannableString(getString(R.string.text_busy_outgoing_call));
                                break;
                            }
                            case -CallLogItem.STATUS_REJECTED:
                            case CallLogItem.STATUS_REJECTED: {
                                spannableString = new SpannableString(getString(R.string.text_rejected_call));
                                break;
                            }
                            case -CallLogItem.STATUS_MISSED:
                            case -CallLogItem.STATUS_FAILED:
                            case CallLogItem.STATUS_FAILED: {
                                spannableString = new SpannableString(getString(R.string.text_failed_call));
                                break;
                            }
                            case -CallLogItem.STATUS_SUCCESSFUL:
                            case CallLogItem.STATUS_SUCCESSFUL: {
                                spannableString = new SpannableString(getString(R.string.text_successful_call));
                                break;
                            }
                            case CallLogItem.STATUS_BUSY:  {
                                spannableString = new SpannableString(getString(R.string.text_busy_call));
                                break;
                            }
                            case CallLogItem.STATUS_MISSED:
                            default: {
                                spannableString = new SpannableString(getString(R.string.text_missed_call));
                                break;
                            }
                        }

                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_NEW_PUBLISHED_DETAILS: {
                        final SpannableString spannableString;
                        if (discussionAndLastMessage.discussion.discussionType == Discussion.TYPE_CONTACT) {
                            spannableString = new SpannableString(getString(R.string.text_contact_details_updated));
                        } else {
                            spannableString = new SpannableString(getString(R.string.text_group_details_updated));
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_CONTACT_DELETED: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_user_removed_from_contacts));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_DISCUSSION_SETTINGS_UPDATE: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_discussion_shared_settings_updated));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_CONTACT_RE_ADDED: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_user_added_to_contacts));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_RE_JOINED_GROUP: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_group_re_joined));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_JOINED_GROUP: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_group_joined));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_GAINED_GROUP_ADMIN: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_you_became_admin));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_LOST_GROUP_ADMIN: {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_you_are_no_longer_admin));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_SCREEN_SHOT_DETECTED: {
                        SpannableString spannableString;
                        if (Arrays.equals(discussionAndLastMessage.message.senderIdentifier, AppSingleton.getBytesCurrentIdentity())) {
                            spannableString = new SpannableString(getString(R.string.text_you_captured_sensitive_message));
                        } else {
                            String displayName = AppSingleton.getContactCustomDisplayName(discussionAndLastMessage.message.senderIdentifier);
                            if (displayName != null) {
                                spannableString = new SpannableString(getString(R.string.text_xxx_captured_sensitive_message, displayName));
                            } else {
                                spannableString = new SpannableString(getString(R.string.text_unknown_member_captured_sensitive_message));
                            }
                        }
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    }
                    case Message.TYPE_INBOUND_EPHEMERAL_MESSAGE:
                        SpannableString spannableString = new SpannableString(discussionAndLastMessage.message.getStringContent(activity));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.lastMessageContentTextView.setText(spannableString);
                        break;
                    case Message.TYPE_INBOUND_MESSAGE:
                    default: {
                        String body = discussionAndLastMessage.message.getStringContent(activity);
                        if (discussionAndLastMessage.message.wipeStatus == Message.WIPE_STATUS_WIPED
                                || discussionAndLastMessage.message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                                || discussionAndLastMessage.message.isLocationMessage()) {
                            SpannableString text = new SpannableString(body);
                            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                            text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.lastMessageContentTextView.setText(text);
                        } else {
                            holder.lastMessageContentTextView.setText(body);
                        }
                    }
                }
                holder.dateTextView.setText(StringUtils.getLongNiceDateString(getContext(), discussionAndLastMessage.message.timestamp));
            } else {
                SpannableString text = new SpannableString(getString(R.string.text_no_messages));
                StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                text.setSpan(styleSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.lastMessageContentTextView.setText(text);
                holder.dateTextView.setText(null);
            }

            if (discussionAndLastMessage.message != null && discussionAndLastMessage.message.totalAttachmentCount > 0) {
                holder.attachmentCountTextView.setVisibility(View.VISIBLE);
                holder.attachmentCountTextView.setText(getResources().getQuantityString(R.plurals.text_reply_attachment_count, discussionAndLastMessage.message.totalAttachmentCount, discussionAndLastMessage.message.totalAttachmentCount));
            } else {
                holder.attachmentCountTextView.setVisibility(View.GONE);
            }


            holder.initialView.setDiscussion(discussionAndLastMessage.discussion);

            if (discussionAndLastMessage.unreadCount > 0) {
                holder.lastMessageUnreadImageView.setVisibility(View.VISIBLE);
                holder.unreadMessageCountTextView.setVisibility(View.VISIBLE);
                holder.unreadMessageCountTextView.setText(String.format(Locale.ENGLISH, "%d", discussionAndLastMessage.unreadCount));
            } else if (discussionAndLastMessage.discussion.unread) {
                holder.lastMessageUnreadImageView.setVisibility(View.VISIBLE);
                holder.unreadMessageCountTextView.setVisibility(View.GONE);
            } else {
                holder.lastMessageUnreadImageView.setVisibility(View.GONE);
                holder.unreadMessageCountTextView.setVisibility(View.GONE);
            }

            if (discussionAndLastMessage.discussion.pinned) {
                holder.pinnedImageView.setVisibility(View.VISIBLE);
            } else {
                holder.pinnedImageView.setVisibility(View.GONE);
            }

            if (discussionAndLastMessage.discussionCustomization != null) {
                DiscussionCustomization.ColorJson colorJson = discussionAndLastMessage.discussionCustomization.getColorJson();
                if (colorJson != null) {
                    holder.customColorView.setBackgroundColor(colorJson.color + 0xff000000);
                } else {
                    holder.customColorView.setBackgroundColor(0x00ffffff);
                }
                if (discussionAndLastMessage.discussionCustomization.backgroundImageUrl != null) {
                    App.runThread(() -> {
                        String backgroundImageAbsolutePath = App.absolutePathFromRelative(discussionAndLastMessage.discussionCustomization.backgroundImageUrl);
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
                        new Handler(Looper.getMainLooper()).post(() -> holder.backgroundImageView.setImageBitmap(finalBitmap));
                    });
                } else {
                    holder.backgroundImageView.setImageDrawable(null);
                }

                if (holder.notificationsMutedImageView != null) {
                    if (discussionAndLastMessage.discussionCustomization.shouldMuteNotifications()) {
                        holder.notificationsMutedImageView.setVisibility(View.VISIBLE);
                    } else {
                        holder.notificationsMutedImageView.setVisibility(View.GONE);
                    }
                }
            } else {
                holder.customColorView.setBackgroundColor(0x00ffffff);
                holder.backgroundImageView.setImageDrawable(null);
                if (holder.notificationsMutedImageView != null) {
                    holder.notificationsMutedImageView.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            if (discussionsAndLastMessages != null) {
                return discussionsAndLastMessages.size();
            }
            return 0;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(@Nullable List<DiscussionDao.DiscussionAndLastMessage> discussionsAndLastMessages) {
            this.discussionsAndLastMessages = discussionsAndLastMessages;
            notifyDataSetChanged();
        }

        class DiscussionViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            final ImageView backgroundImageView;
            final InitialView initialView;
            final TextView nameTextView;
            final TextView dateTextView;
            final TextView lastMessageContentTextView;
            final ImageView lastMessageUnreadImageView;
            final TextView unreadMessageCountTextView;
            final TextView attachmentCountTextView;
            final ImageView notificationsMutedImageView;
            final View customColorView;
            final ImageView pinnedImageView;

            DiscussionViewHolder(View discussionRootView) {
                super(discussionRootView);
                discussionRootView.setOnClickListener(this);
                discussionRootView.setOnLongClickListener(this);
                backgroundImageView = discussionRootView.findViewById(R.id.discussion_background_image);
                nameTextView = discussionRootView.findViewById(R.id.discussion_title);
                dateTextView = discussionRootView.findViewById(R.id.discussion_date);
                lastMessageContentTextView = discussionRootView.findViewById(R.id.discussion_last_message_content);
                customColorView = discussionRootView.findViewById(R.id.custom_color_view);
                initialView = discussionRootView.findViewById(R.id.initial_view);
                lastMessageUnreadImageView = discussionRootView.findViewById(R.id.last_message_unread_image_view);
                unreadMessageCountTextView = discussionRootView.findViewById(R.id.discussion_unread_message_count_text_view);
                pinnedImageView = discussionRootView.findViewById(R.id.discussion_pinned_image_view);
                attachmentCountTextView = discussionRootView.findViewById(R.id.discussion_last_message_attachment_count_text_view);
                notificationsMutedImageView = discussionRootView.findViewById(R.id.notifications_muted_image_view);
            }

            @Override
            public void onClick(View view) {
                DiscussionListFragment.this.discussionClicked(discussionsAndLastMessages.get(this.getLayoutPosition()).discussion, view);
            }

            @Override
            public boolean onLongClick(View view) {
                DiscussionListFragment.this.discussionLongClicked(discussionsAndLastMessages.get(this.getLayoutPosition()), view);
                return true;
            }
        }
    }


}

