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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.olvid.engine.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.Group2Dao;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.tasks.GroupCloningTasks;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment;


public class GroupListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener, PopupMenu.OnMenuItemClickListener {
    private FragmentActivity activity;
    private GroupListViewModel groupListViewModel;
    private SwipeRefreshLayout swipeRefreshLayout;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        groupListViewModel = new ViewModelProvider(this).get(GroupListViewModel.class);
        engineNotificationListenerRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_main_fragment_group_list, container, false);

        EmptyRecyclerView recyclerView = rootView.findViewById(R.id.group_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        View recyclerEmptyView = rootView.findViewById(R.id.group_list_empty_view);
        recyclerView.setEmptyView(recyclerEmptyView);

        View loadingSpinner = rootView.findViewById(R.id.loading_spinner);
        recyclerView.setLoadingSpinner(loadingSpinner);


        GroupListAdapter adapter = new GroupListAdapter();
        groupListViewModel.getGroups().observe(activity, adapter);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new ItemDecorationSimpleDivider(rootView.getContext(), 68, 12));

        swipeRefreshLayout = rootView.findViewById(R.id.discussion_list_swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary700);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.dialogBackground));

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

    private void newGroupClicked() {
        App.openGroupCreationActivity(activity);
    }

    private void groupClicked(Group2Dao.GroupOrGroup2 group) {
        if (group.group != null) {
            App.openGroupDetailsActivity(requireContext(), group.group.bytesOwnedIdentity, group.group.bytesGroupOwnerAndUid);
        } else if (group.group2 != null){
            App.openGroupV2DetailsActivity(requireContext(), group.group2.bytesOwnedIdentity, group.group2.bytesGroupIdentifier);
        }
    }

    private Group2Dao.GroupOrGroup2 longClickedGroupOrGroup2;

    private void groupLongClicked(View view, Group2Dao.GroupOrGroup2 group) {
        this.longClickedGroupOrGroup2 = group;
        if (group != null && group.group != null) {
            // group v1
            if (group.group.bytesGroupOwnerIdentity == null) {
                // owned group
                PopupMenu popup = new PopupMenu(view.getContext(), view);
                popup.inflate(R.menu.popup_group_owned);
                popup.setOnMenuItemClickListener(this);

                MenuItem disbandGroupItem = popup.getMenu().findItem(R.id.popup_action_disband_group);
                if (disbandGroupItem != null) {
                    SpannableString spannableString = new SpannableString(disbandGroupItem.getTitle());
                    spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    disbandGroupItem.setTitle(spannableString);
                }

                popup.show();
            } else {
                // joined group
                PopupMenu popup = new PopupMenu(view.getContext(), view);
                popup.inflate(R.menu.popup_group_joined);
                popup.setOnMenuItemClickListener(this);

                MenuItem leaveGroupItem = popup.getMenu().findItem(R.id.popup_action_leave_group);
                if (leaveGroupItem != null) {
                    SpannableString spannableString = new SpannableString(leaveGroupItem.getTitle());
                    spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    leaveGroupItem.setTitle(spannableString);
                }

                popup.show();
            }
        } else if (group != null && group.group2 != null) {
            // group v2
            if (group.group2.ownPermissionAdmin) {
                // I am admin
                PopupMenu popup = new PopupMenu(view.getContext(), view);
                popup.inflate(R.menu.popup_group_v2_admin);
                popup.setOnMenuItemClickListener(this);

                MenuItem leaveGroupItem = popup.getMenu().findItem(R.id.popup_action_leave_group);
                if (leaveGroupItem != null) {
                    SpannableString spannableString = new SpannableString(leaveGroupItem.getTitle());
                    spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    leaveGroupItem.setTitle(spannableString);
                }
                MenuItem disbandGroupItem = popup.getMenu().findItem(R.id.popup_action_disband_group);
                if (disbandGroupItem != null) {
                    SpannableString spannableString = new SpannableString(disbandGroupItem.getTitle());
                    spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    disbandGroupItem.setTitle(spannableString);
                }

                popup.show();
            } else {
                // not admin
                PopupMenu popup = new PopupMenu(view.getContext(), view);
                popup.inflate(R.menu.popup_group_v2_joined);
                popup.setOnMenuItemClickListener(this);

                MenuItem leaveGroupItem = popup.getMenu().findItem(R.id.popup_action_leave_group);
                if (leaveGroupItem != null) {
                    SpannableString spannableString = new SpannableString(leaveGroupItem.getTitle());
                    spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    leaveGroupItem.setTitle(spannableString);
                }

                popup.show();
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.popup_action_leave_group) {
            if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group != null) {
                Group group = longClickedGroupOrGroup2.group;

                if (group.bytesGroupOwnerIdentity == null) {
                    return true;
                }

                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_leave_group)
                        .setMessage(getString(R.string.dialog_message_leave_group, group.getCustomName()))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            try {
                                AppSingleton.getEngine().leaveGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                App.toast(R.string.toast_message_leaving_group, Toast.LENGTH_SHORT);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            } else if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group2 != null){
                Group2 group2 = longClickedGroupOrGroup2.group2;

                App.runThread(() -> {
                    if (group2.ownPermissionAdmin) {
                        // check you are not the only admin (among members only, as pending members could decline)
                        boolean otherAdmin = false;
                        List<Group2Member> group2Members = AppDatabase.getInstance().group2MemberDao().getGroupMembers(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                        for (Group2Member group2Member : group2Members) {
                            if (group2Member.permissionAdmin) {
                                otherAdmin = true;
                                break;
                            }
                        }
                        if (!otherAdmin) {
                            // you are the only admin --> cannot leave the group
                            // check if there is a pending admin to change the error message
                            boolean pendingAdmin = false;
                            List<Group2PendingMember> group2PendingMembers = AppDatabase.getInstance().group2PendingMemberDao().getGroupPendingMembers(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                            for (Group2PendingMember group2Member : group2PendingMembers) {
                                if (group2Member.permissionAdmin) {
                                    pendingAdmin = true;
                                    break;
                                }
                            }
                            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_unable_to_leave_group)
                                    .setPositiveButton(R.string.button_label_ok, null);
                            if (pendingAdmin) {
                                builder.setMessage(R.string.dialog_message_unable_to_leave_group_pending_admin);
                            } else {
                                builder.setMessage(R.string.dialog_message_unable_to_leave_group);
                            }
                            new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                            return;
                        }
                    }

                    final String groupName;
                    if (group2.getCustomName().length() == 0) {
                        groupName = getString(R.string.text_unnamed_group);
                    } else {
                        groupName = group2.getCustomName();
                    }

                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_leave_group)
                            .setMessage(getString(R.string.dialog_message_leave_group, groupName))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                try {
                                    AppSingleton.getEngine().leaveGroupV2(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                                    App.toast(R.string.toast_message_leaving_group_v2, Toast.LENGTH_SHORT);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                });
            }
            return true;
        } else if (itemId == R.id.popup_action_disband_group) {
            if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group != null) {
                Group group = longClickedGroupOrGroup2.group;
                if (group.bytesGroupOwnerIdentity != null) {
                    return true;
                }
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_disband_group)
                        .setMessage(getString(R.string.dialog_message_disband_group, group.getCustomName()))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(() -> {
                            List<Contact> groupMembers = AppDatabase.getInstance().contactGroupJoinDao().getGroupContactsSync(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                            List<PendingGroupMember> pendingGroupMembers = AppDatabase.getInstance().pendingGroupMemberDao().getGroupPendingMembers(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);

                            if (groupMembers.isEmpty() && pendingGroupMembers.isEmpty()) {
                                try {
                                    AppSingleton.getEngine().disbandGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                    App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_disband_group)
                                        .setMessage(getString(R.string.dialog_message_disband_non_empty_group_confirmation, group.getCustomName(), groupMembers.size(), pendingGroupMembers.size()))
                                        .setPositiveButton(R.string.button_label_ok, (dialog12, which1) -> {
                                            // delete group
                                            try {
                                                AppSingleton.getEngine().disbandGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                                App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        })
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                new Handler(Looper.getMainLooper()).post(() -> confirmationBuilder.create().show());
                            }
                        }))
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            } else if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group2 != null){
                Group2 group2 = longClickedGroupOrGroup2.group2;

                final String groupName;
                if (group2.getCustomName().length() == 0) {
                    groupName = getString(R.string.text_unnamed_group);
                } else {
                    groupName = group2.getCustomName();
                }
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_disband_group)
                        .setMessage(getString(R.string.dialog_message_disband_group, groupName))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(() -> {
                            List<Group2MemberDao.Group2MemberOrPending> groupMembers = AppDatabase.getInstance().group2MemberDao().getGroupMembersAndPendingSync(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                            if (groupMembers.isEmpty()) {
                                // group is empty, just delete it
                                try {
                                    AppSingleton.getEngine().disbandGroupV2(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                                    App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                // group is not empty, second confirmation
                                final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_disband_group)
                                        .setMessage(getString(R.string.dialog_message_disband_non_empty_group_v2_confirmation,
                                                groupName,
                                                groupMembers.size()))
                                        .setPositiveButton(R.string.button_label_ok, (dialog12, which1) -> {
                                            // disband group
                                            try {
                                                AppSingleton.getEngine().disbandGroupV2(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                                                App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        })
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                new Handler(Looper.getMainLooper()).post(() -> confirmationBuilder.create().show());
                            }
                        }))
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.popup_action_rename_group) {
            if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group != null) {
                Group group = longClickedGroupOrGroup2.group;
                if (group.bytesGroupOwnerIdentity == null) {
                    // you own the group --> show group details and open edit details
                    App.openGroupDetailsActivityForEditDetails(activity, group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                } else {
                    EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, group);
                    editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
                }
            } else if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group2 != null){
                Group2 group2 = longClickedGroupOrGroup2.group2;
                if (group2.ownPermissionAdmin) {
                    // you own the group --> show group details and open edit details
                    App.openGroupV2DetailsActivityForEditDetails(activity, group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                } else {
                    EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, group2);
                    editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
                }
            }
            return true;
        } else if (itemId == R.id.popup_action_call_group) {
            if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group != null) {
                Group group = longClickedGroupOrGroup2.group;
                App.runThread(() -> {
                    List<Contact> groupMembers = AppDatabase.getInstance().contactGroupJoinDao().getGroupContactsSync(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                    ArrayList<BytesKey> bytesContactIdentities = new ArrayList<>();
                    for (Contact contact : groupMembers) {
                        bytesContactIdentities.add(new BytesKey(contact.bytesContactIdentity));
                    }
                    MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, bytesContactIdentities);
                    new Handler(Looper.getMainLooper()).post(() -> multiCallStartDialogFragment.show(getChildFragmentManager(), "dialog"));
                });
            } else if (longClickedGroupOrGroup2 != null && longClickedGroupOrGroup2.group2 != null) {
                Group2 group2 = longClickedGroupOrGroup2.group2;
                App.runThread(() -> {
                    List<Contact> contacts = AppDatabase.getInstance().group2MemberDao().getGroupMemberContactsSync(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                    ArrayList<BytesKey> bytesContactIdentities = new ArrayList<>();
                    for (Contact contact : contacts) {
                        bytesContactIdentities.add(new BytesKey(contact.bytesContactIdentity));
                    }
                    MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier, bytesContactIdentities);
                    new Handler(Looper.getMainLooper()).post(() -> multiCallStartDialogFragment.show(getChildFragmentManager(), "dialog"));
                });
            }
            return true;
        } else if (itemId == R.id.popup_action_clone_group) {
            if (longClickedGroupOrGroup2 != null) {
                App.runThread(() -> {
                    GroupCloningTasks.ClonabilityOutput clonabilityOutput = GroupCloningTasks.getClonability(longClickedGroupOrGroup2);
                    new Handler(Looper.getMainLooper()).post(() -> GroupCloningTasks.initiateGroupCloningOrWarnUser(activity, clonabilityOutput));
                });
            }
            return true;
        }
        return false;
    }


    public class GroupListAdapter extends LoadAwareAdapter<GroupListAdapter.ViewHolder> implements Observer<List<Group2Dao.GroupOrGroup2>> {
        private List<Group2Dao.GroupOrGroup2> groups = null;

        private static final int VIEW_TYPE_DEFAULT = 0;
        private static final int VIEW_TYPE_NEW_GROUP = 1;


        private final LayoutInflater inflater;

        public GroupListAdapter() {
            inflater = LayoutInflater.from(GroupListFragment.this.getContext());
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_NEW_GROUP;
            }
            return VIEW_TYPE_DEFAULT;
        }

        @Override
        public boolean isLoadingDone() {
            return groups != null;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView;
            switch (viewType) {
                case VIEW_TYPE_NEW_GROUP:
                    itemView = inflater.inflate(R.layout.item_view_new_group, parent, false);
                    break;
                case VIEW_TYPE_DEFAULT:
                default:
                    itemView = inflater.inflate(R.layout.item_view_group, parent, false);
                    break;
            }
            return new ViewHolder(itemView, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (groups == null || position == 0) {
                return;
            }

            Group2Dao.GroupOrGroup2 groupOrGroup2 = groups.get(position - 1);
            if (groupOrGroup2.group != null) {
                Group group = groupOrGroup2.group;

                holder.initialView.setGroup(group);
                holder.groupName.setText(group.getCustomName());
                holder.groupName.setMaxLines(1);
                holder.groupMembers.setVisibility(View.VISIBLE);
                if (group.groupMembersNames.length() == 0) {
                    StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                    SpannableString spannableString = new SpannableString(getString(R.string.text_empty_group));
                    spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.groupMembers.setText(spannableString);
                } else {
                    holder.groupMembers.setText(group.groupMembersNames);
                }
                if (group.bytesGroupOwnerIdentity == null) {
                    holder.adminIndicatorImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.adminIndicatorImageView.setVisibility(View.GONE);
                }
                switch (group.newPublishedDetails) {
                    case Group.PUBLISHED_DETAILS_NOTHING_NEW:
                        holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                        break;
                    case Group.PUBLISHED_DETAILS_NEW_SEEN:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.GONE);
                        break;
                    case Group.PUBLISHED_DETAILS_NEW_UNSEEN:
                    case Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.VISIBLE);
                        break;
                }
            } else if (groupOrGroup2.group2 != null) {
                Group2 group2 = groupOrGroup2.group2;

                holder.initialView.setGroup2(group2);
                String groupName = group2.getCustomName();
                if (groupName.length() == 0) {
                    SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_group));
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.groupName.setText(spannableString);
                    holder.groupMembers.setVisibility(View.GONE);
                } else {
                    holder.groupName.setText(groupName);
                    if (group2.name == null && group2.customName == null) { // in this case, getCustomName() returns the group members --> no need to display them again
                        holder.groupName.setMaxLines(2);
                        holder.groupMembers.setVisibility(View.GONE);
                    } else {
                        holder.groupName.setMaxLines(1);
                        holder.groupMembers.setVisibility(View.VISIBLE);
                        if (group2.groupMembersNames.length() == 0) {
                            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                            SpannableString spannableString = new SpannableString(getString(R.string.text_empty_group));
                            spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.groupMembers.setText(spannableString);
                        } else {
                            holder.groupMembers.setText(group2.groupMembersNames);
                        }
                    }
                }
                if (group2.ownPermissionAdmin) {
                    holder.adminIndicatorImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.adminIndicatorImageView.setVisibility(View.GONE);
                }
                switch (group2.newPublishedDetails) {
                    case Group2.PUBLISHED_DETAILS_NOTHING_NEW:
                        holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                        break;
                    case Group2.PUBLISHED_DETAILS_NEW_SEEN:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.GONE);
                        break;
                    case Group2.PUBLISHED_DETAILS_NEW_UNSEEN:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.VISIBLE);
                        break;
                }
            } else {
                Logger.e("GroupOrGroup2 with both Group and Group2 null");
            }
        }

        @Override
        public int getItemCount() {
            if (groups != null) {
                return groups.size() + 1;
            }
            return 1;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<Group2Dao.GroupOrGroup2> groups) {
            if (this.groups == null) {
                this.groups = groups;
                notifyDataSetChanged();
            } else {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return 1 + (GroupListAdapter.this.groups == null ? 0 : GroupListAdapter.this.groups.size());
                    }

                    @Override
                    public int getNewListSize() {
                        return 1 + (groups == null ? 0 : groups.size());
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        if (oldItemPosition == 0) {
                            return newItemPosition == 0;
                        } else if (newItemPosition == 0) {
                            return false;
                        }

                        Group2Dao.GroupOrGroup2 oldItem = GroupListAdapter.this.groups.get(oldItemPosition - 1);
                        Group2Dao.GroupOrGroup2 newItem = groups.get(newItemPosition - 1);
                        if (oldItem.group != null && newItem.group != null) {
                            return Arrays.equals(oldItem.group.bytesOwnedIdentity, newItem.group.bytesOwnedIdentity) && Arrays.equals(oldItem.group.bytesGroupOwnerAndUid, newItem.group.bytesGroupOwnerAndUid);
                        } else if (oldItem.group2 != null && newItem.group2 != null) {
                            return Arrays.equals(oldItem.group2.bytesOwnedIdentity, newItem.group2.bytesOwnedIdentity) && Arrays.equals(oldItem.group2.bytesGroupIdentifier, newItem.group2.bytesGroupIdentifier);
                        }
                        return false;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        return false; // lazy, for now, we always return false, triggering a rebind
                    }
                });
                this.groups = groups;
                result.dispatchUpdatesTo(this);
            }
        }


        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            final TextView groupName;
            final TextView groupMembers;
            final InitialView initialView;
            final ImageView adminIndicatorImageView;
            final View newPublishedDetailsGroup;
            final View newUnseenPublishedDetailsDot;

            ViewHolder(@NonNull View itemView, int viewType) {
                super(itemView);
                switch (viewType) {
                    case VIEW_TYPE_NEW_GROUP:
                        itemView.findViewById(R.id.button_new_group).setOnClickListener((View view) -> newGroupClicked());
                        break;
                    case VIEW_TYPE_DEFAULT:
                    default:
                        itemView.setOnClickListener(this);
                        itemView.setOnLongClickListener(this);
                        break;
                }
                groupName = itemView.findViewById(R.id.group_name_text_view);
                groupMembers = itemView.findViewById(R.id.group_members_text_view);
                initialView = itemView.findViewById(R.id.initial_view);
                adminIndicatorImageView = itemView.findViewById(R.id.admin_indicator_image_view);
                newPublishedDetailsGroup = itemView.findViewById(R.id.new_published_details_group);
                newUnseenPublishedDetailsDot = itemView.findViewById(R.id.new_unseen_published_details_dot);
            }

            @Override
            public void onClick(View view) {
                int position = getLayoutPosition() - 1;
                if (groups != null && position >= 0 && groups.size() > position && view != null) {
                    groupClicked(groups.get(position));
                }
            }


            @Override
            public boolean onLongClick(View view) {
                int position = getLayoutPosition() - 1;
                if (groups != null && position >= 0 && groups.size() > position && view != null) {
                    groupLongClicked(view, groups.get(position));
                    return true;
                }
                return true;
            }
        }
    }

    private Long engineNotificationListenerRegistrationNumber;

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
}
