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

package io.olvid.messenger.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.Reaction;

public class ReactionListBottomSheetFragment extends BottomSheetDialogFragment {
    RecyclerView reactionsRecyclerView;
    ReactionListAdapter reactionsAdapter;
    RecyclerView tabsRecyclerView;
    ReactionTabAdapter tabAdapter;

    private long messageId = -1;
    private OwnedIdentity currentOwnedIdentity;

    private static final String MESSAGE_ID = "messageId";

    public static ReactionListBottomSheetFragment newInstance(long messageId) {
        ReactionListBottomSheetFragment fragment = new ReactionListBottomSheetFragment();

        // store arguments in a bundle to let bottom sheet reload correctly in some cases
        Bundle bundle = new Bundle();
        bundle.putLong(MESSAGE_ID, messageId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // check that messageId was correctly set
        if (this.getArguments() != null) {
            messageId = this.getArguments().getLong(MESSAGE_ID, -1);
        }
        // make bottom sheet transparent
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // dismiss if required arguments are not set
        if (messageId == -1 || AppSingleton.getCurrentIdentityLiveData().getValue() == null) {
            this.dismiss();
            return null;
        }
        currentOwnedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();

        View view = inflater.inflate(R.layout.bottom_sheet_fragment_reactions_list, container, false);

        // create reactions recycler view required elements
        LiveData<List<Reaction>> reactionsLiveData = AppDatabase.getInstance().reactionDao().getAllNonNullForMessageSortedByTimestampLiveData(messageId);
        reactionsRecyclerView = view.findViewById(R.id.reactions_list);
        reactionsAdapter = new ReactionListAdapter();
        reactionsLiveData.observe(this, this.reactionsAdapter);
        reactionsRecyclerView.setAdapter(reactionsAdapter);
        reactionsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        LiveData<List<ReactionAndCount>> tabsLiveData = Transformations.map(reactionsLiveData, (List<Reaction> reactions) -> {
            Map<String, ReactionAndCount> tabsMap = new HashMap<>();
            // add a tab for every different emoji
            for (Reaction reaction : reactions) {
                ReactionAndCount reactionAndCount = tabsMap.get(reaction.emoji);
                if (reactionAndCount == null) {
                    reactionAndCount = new ReactionAndCount(reaction.emoji);
                    tabsMap.put(reaction.emoji, reactionAndCount);
                }
                reactionAndCount.count++;
            }

            List<ReactionAndCount> tabs = new ArrayList<>(tabsMap.values());
            Collections.sort(tabs);
            return tabs;
        });
        tabsRecyclerView = view.findViewById(R.id.tab_list);
        tabAdapter = new ReactionTabAdapter(reactionsAdapter);
        tabsLiveData.observe(this, this.tabAdapter);
        tabsRecyclerView.setAdapter(tabAdapter);
        // set recycler view as horizontal bar
        LinearLayoutManager horizontalLinearLayoutManager = new LinearLayoutManager(getActivity());
        horizontalLinearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        tabsRecyclerView.setLayoutManager(horizontalLinearLayoutManager);

        // set constraint layout height to half screen size (only set min cause recycler view will never grow cause it is scrollable)
        DisplayMetrics displayMetrics = requireContext().getResources().getDisplayMetrics();
        ConstraintLayout constraintLayout = view.findViewById(R.id.constraintLayout);
        constraintLayout.setMinHeight(displayMetrics.heightPixels/2);

        return view;
    }

    private class ReactionListAdapter extends RecyclerView.Adapter<ReactionListAdapter.ReactionListViewHolder> implements Observer<List<Reaction>> {
        // store all reactions list to be able to swap between filters
        private List<Reaction> originalReactionsList = null;
        // store reactions to show, after filter had been applied
        private List<Reaction> filteredReactionsList = null;
        // filter to apply to reactions list depending on tab selected (updated by ReactionTabViewHolder when clicked)
        private String filter = null;

        @NonNull
        @Override
        public ReactionListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.item_view_bottom_sheet_reaction, parent, false);
            return new ReactionListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReactionListViewHolder holder, int position) {
            if (filteredReactionsList != null && filteredReactionsList.get(position) != null) {
                holder.update(filteredReactionsList.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return filteredReactionsList == null ? 0 : filteredReactionsList.size();
        }

        // only keep reactions with a given emoji and create a list with it
        private List<Reaction> filterReactions(List<Reaction> reactionsList, String filter) {
            if (reactionsList == null || filter == null) {
                return reactionsList;
            }

            List<Reaction> filteredReactions = new ArrayList<>();
            for (Reaction reaction : reactionsList) {
                if (Objects.equals(reaction.emoji, filter)) {
                    filteredReactions.add(reaction);
                }
            }
            // only use filtered list if it is not empty (when not filtered)
            if (filteredReactions.size() > 0) {
                reactionsList = filteredReactions;
            }
            return reactionsList;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<Reaction> reactions) {
            if (filteredReactionsList == null || originalReactionsList == null || reactions == null) {
                originalReactionsList = reactions;
                filteredReactionsList = filterReactions(originalReactionsList, this.filter);
                notifyDataSetChanged();
                return;
            }

            List<Reaction> newFilteredReactionsList = filterReactions(reactions, this.filter);

            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return filteredReactionsList.size();
                }

                @Override
                public int getNewListSize() {
                    return newFilteredReactionsList == null ? 0 : newFilteredReactionsList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return filteredReactionsList.get(oldItemPosition).id == newFilteredReactionsList.get(newItemPosition).id;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return Objects.equals(filteredReactionsList.get(oldItemPosition).emoji, newFilteredReactionsList.get(newItemPosition).emoji) &&
                            filteredReactionsList.get(oldItemPosition).timestamp == newFilteredReactionsList.get(newItemPosition).timestamp;
                }
            });
            filteredReactionsList = newFilteredReactionsList;
            result.dispatchUpdatesTo(this);
        }

        void updateFilter(String filter) {
            this.filter = filter;
            // force list update
            onChanged(this.originalReactionsList);
        }

        private class ReactionListViewHolder extends RecyclerView.ViewHolder {
            final TextView contactName;
            final TextView reactionDate;
            final InitialView initialView;
            final TextView reactionContent;

            public ReactionListViewHolder(@NonNull View itemView) {
                super(itemView);
                contactName = itemView.findViewById(R.id.name);
                reactionDate = itemView.findViewById(R.id.date);
                initialView = itemView.findViewById(R.id.initial_view);
                reactionContent = itemView.findViewById(R.id.reaction_content);
            }

            public void update(@NonNull Reaction reaction) {
                byte[] bytesIdentity = reaction.bytesIdentity == null ? currentOwnedIdentity.bytesOwnedIdentity : reaction.bytesIdentity;
                String displayName = AppSingleton.getContactCustomDisplayName(bytesIdentity);
                initialView.setFromCache(bytesIdentity);
                contactName.setText(displayName);
                reactionDate.setText(StringUtils.getNiceDateString(getContext(), reaction.timestamp));
                reactionContent.setText(reaction.emoji);
            }
        }
    }

    private static class ReactionTabAdapter extends RecyclerView.Adapter<ReactionTabAdapter.ReactionTabViewHolder> implements Observer<List<ReactionAndCount>> {
        private List<ReactionAndCount> reactionAndCounts = null;
        private final ReactionListAdapter reactionListAdapter;

        private static final int NO_SELECTED_TAB = -1;
        private int selectedPosition = NO_SELECTED_TAB;

        // need ReactionListAdapter to give to view holders (need it to update filter and to show count in tabs)
        public ReactionTabAdapter(ReactionListAdapter reactionListAdapter) {
            this.reactionListAdapter = reactionListAdapter;
        }

        @NonNull
        @Override
        public ReactionTabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.item_view_bottom_sheet_reaction_tab, parent, false);
            return new ReactionTabViewHolder(view, reactionListAdapter);
        }

        @Override
        public void onBindViewHolder(@NonNull ReactionTabViewHolder holder, int position) {
            if (reactionAndCounts != null && reactionAndCounts.get(position) != null) {
                holder.update(reactionAndCounts.get(position));
                if (selectedPosition == NO_SELECTED_TAB) {
                    holder.itemView.setSelected(false);
                }
                else {
                    holder.itemView.setSelected(selectedPosition == position);
                }
            }
        }

        @Override
        public int getItemCount() {
            return reactionAndCounts == null ? 0 : reactionAndCounts.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<ReactionAndCount> reactionAndCounts) {
            this.reactionAndCounts = reactionAndCounts;
            notifyDataSetChanged();
        }

        private class ReactionTabViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final TextView emojiTextView;
            final TextView countTextView;
            // need ReactionListAdapter to update filter and to show count in tabs
            private final ReactionListAdapter reactionListAdapter;

            public ReactionTabViewHolder(@NonNull View itemView, ReactionListAdapter reactionListAdapter) {
                super(itemView);
                emojiTextView = itemView.findViewById(R.id.tab_emoji);
                countTextView = itemView.findViewById(R.id.tab_count);
                this.reactionListAdapter = reactionListAdapter;
                itemView.setOnClickListener(this);
            }

            public void update(ReactionAndCount reactionAndCount) {
                emojiTextView.setText(reactionAndCount.emoji);
                countTextView.setText((reactionAndCount.count == 0) ? "" : Integer.toString(reactionAndCount.count));
            }

            @Override
            public void onClick(View v) {
                // select or unselect if clicked on previously selected item
                int oldSelectedPosition = selectedPosition;
                if (getLayoutPosition() == selectedPosition) {
                    selectedPosition = NO_SELECTED_TAB;
                    if (reactionListAdapter != null) {
                        reactionListAdapter.updateFilter(null);
                    }
                }
                else {
                   selectedPosition = getLayoutPosition();
                    if (reactionListAdapter != null) {
                        reactionListAdapter.updateFilter(reactionAndCounts.get(selectedPosition).emoji);
                    }
                }
                if (oldSelectedPosition != NO_SELECTED_TAB) {
                    notifyItemChanged(oldSelectedPosition);
                }
                if (selectedPosition != NO_SELECTED_TAB) {
                    notifyItemChanged(selectedPosition);
                }
            }
        }
    }

    private static class ReactionAndCount implements Comparable<ReactionAndCount> {
        final String emoji;
        int count;

        public ReactionAndCount(String emoji) {
            this.emoji = emoji;
            this.count = 0;
        }

        @Override
        public int compareTo(ReactionAndCount o) {
            return count - o.count;
        }
    }
}
