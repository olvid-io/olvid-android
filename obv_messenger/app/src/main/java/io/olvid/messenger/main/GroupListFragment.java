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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.databases.dao.GroupDao;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;


public class GroupListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener {
    private FragmentActivity activity;
    private GroupListViewModel groupListViewModel;
    private GroupListAdapter adapter;
    private Long engineNotificationListenerRegistrationNumber;
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


        adapter = new GroupListAdapter();
        groupListViewModel.getGroups().observe(activity, adapter);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(rootView.getContext()));

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

    public class DividerItemDecoration extends RecyclerView.ItemDecoration {
        private final int dividerHeight;
        private final int marginLeft;
        private final int marginRight;
        private final int backgroundColor;
        private final int foregroundColor;

        DividerItemDecoration(Context context) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            dividerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
            marginLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 68, metrics);
            marginRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
            backgroundColor = ContextCompat.getColor(context, R.color.almostWhite);
            foregroundColor = ContextCompat.getColor(context, R.color.lightGrey);
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int childCount = parent.getChildCount();
            for (int i=0; i<childCount; i++) {
                View child = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(child);
                if (position <= 0 || (adapter.groups.get(position).group.bytesGroupOwnerIdentity != null && adapter.groups.get(position-1).group.bytesGroupOwnerIdentity == null)) {
                    continue;
                }
                Rect childRect = new Rect();
                parent.getDecoratedBoundsWithMargins(child, childRect);
                canvas.save();
                canvas.clipRect(childRect.left, childRect.top, childRect.right, childRect.top + dividerHeight);
                canvas.drawColor(backgroundColor);
                canvas.clipRect(childRect.left + marginLeft, childRect.top, childRect.right - marginRight, childRect.top + dividerHeight);
                canvas.drawColor(foregroundColor);
                canvas.restore();
            }
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            if (position == 0 || (adapter.groups.get(position).group.bytesGroupOwnerIdentity != null && adapter.groups.get(position-1).group.bytesGroupOwnerIdentity == null)) {
                return;
            }
            outRect.top = dividerHeight;
        }
    }

    private void groupClicked(GroupDao.GroupAndContactDisplayNames group) {
        App.openGroupDetailsActivity(requireContext(), group.group.bytesOwnedIdentity, group.group.bytesGroupOwnerAndUid);
    }

    public class GroupListAdapter extends LoadAwareAdapter<GroupListAdapter.ViewHolder> implements Observer<List<GroupDao.GroupAndContactDisplayNames>> {
        private List<GroupDao.GroupAndContactDisplayNames> groups = null;

        private final LayoutInflater inflater;

        public GroupListAdapter() {
            inflater = LayoutInflater.from(GroupListFragment.this.getContext());
        }

        @Override
        public boolean isLoadingDone() {
            return groups != null;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = inflater.inflate(R.layout.item_view_group, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (groups == null) {
                return;
            }
            GroupDao.GroupAndContactDisplayNames group = groups.get(position);

            if (position == 0 ||
                    (groups.get(position-1).group.bytesGroupOwnerIdentity == null && group.group.bytesGroupOwnerIdentity != null)) {
                // show the header
                holder.groupSectionHeader.setVisibility(View.VISIBLE);
                if (group.group.bytesGroupOwnerIdentity == null) {
                    holder.groupSectionHeader.setText(R.string.label_groups_created);
                } else {
                    holder.groupSectionHeader.setText(R.string.label_groups_joined);
                }
            } else {
                holder.groupSectionHeader.setVisibility(View.GONE);
            }

            holder.initialView.setGroup(group.group);
            holder.groupName.setText(group.group.getCustomName());
            if (group.contactDisplayNames == null || group.contactDisplayNames.length() == 0) {
                StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                SpannableString spannableString = new SpannableString(getString(R.string.text_no_members));
                spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.groupMembers.setText(spannableString);
            } else {
                holder.groupMembers.setText(group.contactDisplayNames);
            }
            switch (group.group.newPublishedDetails) {
                case Group.PUBLISHED_DETAILS_NOTHING_NEW:
                    holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                    break;
                case Group.PUBLISHED_DETAILS_NEW_SEEN:
                    holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                    holder.newUnseedPublishedDetailsDot.setVisibility(View.GONE);
                    break;
                case Group.PUBLISHED_DETAILS_NEW_UNSEEN:
                case Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW:
                    holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                    holder.newUnseedPublishedDetailsDot.setVisibility(View.VISIBLE);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            if (groups != null) {
                return groups.size();
            }
            return 0;
        }

        @Override
        public void onChanged(List<GroupDao.GroupAndContactDisplayNames> groups) {
            this.groups = groups;
            notifyDataSetChanged();
        }


        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final TextView groupSectionHeader;
            final TextView groupName;
            final TextView groupMembers;
            final InitialView initialView;
            final View newPublishedDetailsGroup;
            final View newUnseedPublishedDetailsDot;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                groupSectionHeader = itemView.findViewById(R.id.group_section_header);
                groupName = itemView.findViewById(R.id.group_name_text_view);
                groupMembers = itemView.findViewById(R.id.group_members_text_view);
                initialView = itemView.findViewById(R.id.initial_view);
                newPublishedDetailsGroup = itemView.findViewById(R.id.new_published_details_group);
                newUnseedPublishedDetailsDot = itemView.findViewById(R.id.new_unseen_published_details_dot);
            }
            @Override
            public void onClick(View view) {
                int position = getLayoutPosition();
                if (groups != null && position >= 0 && groups.size() > position && view != null) {
                    GroupListFragment.this.groupClicked(groups.get(position));
                }
            }
        }

    }
}
