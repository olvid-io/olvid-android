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

package io.olvid.messenger.fragments;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel;

public class FilteredDiscussionListFragment extends Fragment implements TextWatcher {
    private EditText discussionFilterEditText;
    protected FilteredDiscussionListViewModel filteredDiscussionListViewModel = null;
    private FilteredDiscussionListOnClickDelegate onClickDelegate;
    private LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> unfilteredDiscussions = null;
    private EmptyRecyclerView recyclerView;
    private View emptyView;

    private Observer<List<Long>> selectedDiscussionIdsObserver;

    private boolean removeBottomPadding = false;
    private boolean useDialogBackground = false;
    private boolean showPinned = false;
    private boolean selectable = false;

    protected FilteredDiscussionListAdapter filteredDiscussionListAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filteredDiscussionListViewModel = new ViewModelProvider(this).get(FilteredDiscussionListViewModel.class);

        if (unfilteredDiscussions != null) {
            observeUnfiltered();
        }

        if (this.selectedDiscussionIdsObserver != null) {
            filteredDiscussionListViewModel.getSelectedDiscussionIds().observe(this, this.selectedDiscussionIdsObserver);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_filtered_discussion_list, container, false);

        recyclerView = rootView.findViewById(R.id.filtered_discussion_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        filteredDiscussionListAdapter = new FilteredDiscussionListAdapter();
        filteredDiscussionListViewModel.getFilteredDiscussions().observe(getViewLifecycleOwner(), filteredDiscussionListAdapter);
        recyclerView.setAdapter(filteredDiscussionListAdapter);

        if (removeBottomPadding) {
            recyclerView.setPadding(0,0,0,0);
        }
        if (emptyView != null) {
            recyclerView.setEmptyView(emptyView);
        }

        if (useDialogBackground) {
            recyclerView.addItemDecoration(new ItemDecorationSimpleDivider(rootView.getContext(), selectable ? 100 : 68, 12, R.color.dialogBackground));
        } else {
            recyclerView.addItemDecoration(new ItemDecorationSimpleDivider(rootView.getContext(), selectable ? 100 : 68, 12));
        }

        return rootView;
    }


    public void setUnfilteredDiscussions(LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> unfilteredDiscussions) {
        if (this.unfilteredDiscussions != null) {
            this.unfilteredDiscussions.removeObservers(this);
        }
        this.unfilteredDiscussions = unfilteredDiscussions;
        if (filteredDiscussionListViewModel != null) {
            observeUnfiltered();
        }
    }

    public void setEmptyView(View view) {
        if (recyclerView != null) {
            recyclerView.setEmptyView(view);
        }
        this.emptyView = view;
    }


    @SuppressLint("NotifyDataSetChanged")
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
        if (this.filteredDiscussionListAdapter != null && this.filteredDiscussionListAdapter.filteredDiscussions != null) {
            this.filteredDiscussionListAdapter.notifyDataSetChanged();
        }
    }

    public void setSelectedDiscussionIdsObserver(Observer<List<Long>> observer) {
        if (filteredDiscussionListViewModel != null) {
            if (this.selectedDiscussionIdsObserver != null) {
                filteredDiscussionListViewModel.getSelectedDiscussionIds().removeObserver(this.selectedDiscussionIdsObserver);
            }
            filteredDiscussionListViewModel.getSelectedDiscussionIds().observe(this, observer);
        }
        this.selectedDiscussionIdsObserver = observer;
    }

    public void deselectAll() {
        if (filteredDiscussionListViewModel != null) {
            filteredDiscussionListViewModel.deselectAll();
        }
    }

    private void observeUnfiltered() {
        this.unfilteredDiscussions.observe(this, (List<DiscussionDao.DiscussionAndGroupMembersNames> discussionAndGroupMembersNamesList) -> {
            if (discussionAndGroupMembersNamesList == null) {
                filteredDiscussionListViewModel.setUnfilteredDiscussions(null);
            } else {
                List<FilteredDiscussionListViewModel.SearchableDiscussion> list = new ArrayList<>(discussionAndGroupMembersNamesList.size());
                for (DiscussionDao.DiscussionAndGroupMembersNames discussionAndGroupMembersNames : discussionAndGroupMembersNamesList) {
                    list.add(new FilteredDiscussionListViewModel.SearchableDiscussion(discussionAndGroupMembersNames));
                }
                filteredDiscussionListViewModel.setUnfilteredDiscussions(list);
            }
        });
    }

    public void setDiscussionFilterEditText(EditText discussionFilterEditText) {
        if (this.discussionFilterEditText != null) {
            this.discussionFilterEditText.removeTextChangedListener(this);
        }
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            discussionFilterEditText.setImeOptions(discussionFilterEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }

        this.discussionFilterEditText = discussionFilterEditText;
        this.discussionFilterEditText.addTextChangedListener(this);
    }

    public void setOnClickDelegate(FilteredDiscussionListOnClickDelegate onClickDelegate) {
        this.onClickDelegate = onClickDelegate;
    }

    public void removeBottomPadding() {
        removeBottomPadding = true;
    }

    public void setUseDialogBackground(boolean useDialogBackground) {
        this.useDialogBackground = useDialogBackground;
    }

    public void setShowPinned(boolean showPinned) {
        this.showPinned = showPinned;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void afterTextChanged(Editable editable) {
        if (filteredDiscussionListViewModel != null) {
            filteredDiscussionListViewModel.setFilter(editable.toString());
        }
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }


    class FilteredDiscussionListAdapter extends RecyclerView.Adapter<FilteredDiscussionListAdapter.DiscussionViewHolder> implements Observer<List<FilteredDiscussionListViewModel.SearchableDiscussion>> {
        private List<FilteredDiscussionListViewModel.SearchableDiscussion> filteredDiscussions;
        private final LayoutInflater inflater;
        private final BackgroundColorSpan[] highlightedSpans;

        private static final int TYPE_DIRECT = 1;
        private static final int TYPE_GROUP = 2;
        private static final int TYPE_LOCKED = 3;


        FilteredDiscussionListAdapter() {
            this.inflater = LayoutInflater.from(FilteredDiscussionListFragment.this.getContext());
            setHasStableIds(true);
            highlightedSpans = new BackgroundColorSpan[10];
            for (int i=0; i<highlightedSpans.length; i++) {
                highlightedSpans[i] = new BackgroundColorSpan(ContextCompat.getColor(App.getContext(), R.color.accentOverlay));
            }
        }

        @Override
        public void onChanged(@Nullable List<FilteredDiscussionListViewModel.SearchableDiscussion> searchableDiscussions) {
            this.filteredDiscussions = searchableDiscussions;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FilteredDiscussionListAdapter.DiscussionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_GROUP:
                    view = inflater.inflate(R.layout.item_view_searchable_discussion_group, parent, false);
                    break;
                case TYPE_LOCKED:
                    view = inflater.inflate(R.layout.item_view_searchable_discussion_locked, parent, false);
                    break;
                case TYPE_DIRECT:
                default:
                    view = inflater.inflate(R.layout.item_view_searchable_discussion_direct, parent, false);
                    break;
            }
            return new FilteredDiscussionListAdapter.DiscussionViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (filteredDiscussions != null) {
                FilteredDiscussionListViewModel.SearchableDiscussion discussion = filteredDiscussions.get(position);
                if (discussion.isGroupDiscussion) {
                    return TYPE_GROUP;
                } else if (discussion.byteIdentifier.length == 0) {
                    return TYPE_LOCKED;
                }
            }
            return TYPE_DIRECT;
        }

        @Override
        public long getItemId(int position) {
            if (filteredDiscussions != null) {
                FilteredDiscussionListViewModel.SearchableDiscussion discussion = filteredDiscussions.get(position);
                return discussion.discussionId;
            }
            return -1;
        }

        @Override
        public void onBindViewHolder(@NonNull FilteredDiscussionListAdapter.DiscussionViewHolder holder, int position) {
            if (filteredDiscussions != null) {
                FilteredDiscussionListViewModel.SearchableDiscussion discussion = filteredDiscussions.get(position);

                List<Pattern> patterns = FilteredDiscussionListFragment.this.filteredDiscussionListViewModel.getFilterPatterns();
                if (patterns != null) {
                    int i = 0;
                    Spannable highlightedTitle = new SpannableString(discussion.title);
                    String unaccentTitle = StringUtils.unAccent(discussion.title);
                    for (Pattern pattern : patterns) {
                        if (i == highlightedSpans.length) {
                            break;
                        }
                        Matcher matcher = pattern.matcher(unaccentTitle);
                        if (matcher.find()) {
                            highlightedTitle.setSpan(highlightedSpans[i], matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            i++;
                        }
                    }
                    holder.discussionTitleTextView.setText(highlightedTitle);

                    if (discussion.isGroupDiscussion) {
                        if (discussion.groupMemberNameList.length() == 0) {
                            StyleSpan sp = new StyleSpan(Typeface.ITALIC);
                            SpannableString ss = new SpannableString(getString(R.string.text_nobody));
                            ss.setSpan(sp, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.discussionGroupMembersTextView.setText(ss);
                        } else {
                            i = 0;
                            Spannable highlightedGroupMembers = new SpannableString(discussion.groupMemberNameList);
                            String unaccentGroupMemberNames = StringUtils.unAccent(discussion.groupMemberNameList);
                            for (Pattern pattern : patterns) {
                                if (i == highlightedSpans.length) {
                                    break;
                                }
                                Matcher matcher = pattern.matcher(unaccentGroupMemberNames);
                                if (matcher.find()) {
                                    highlightedGroupMembers.setSpan(highlightedSpans[i], matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    i++;
                                }
                            }
                            holder.discussionGroupMembersTextView.setText(highlightedGroupMembers);
                        }
                    }
                } else {
                    if (discussion.title.length() == 0) {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_discussion));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.discussionTitleTextView.setText(spannableString);
                    } else {
                        holder.discussionTitleTextView.setText(discussion.title);
                    }
                    if (discussion.isGroupDiscussion) {
                        if (discussion.groupMemberNameList.length() == 0) {
                            StyleSpan sp = new StyleSpan(Typeface.ITALIC);
                            SpannableString ss = new SpannableString(getString(R.string.text_nobody));
                            ss.setSpan(sp, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            holder.discussionGroupMembersTextView.setText(ss);
                        } else {
                            holder.discussionGroupMembersTextView.setText(discussion.groupMemberNameList);
                        }
                    }
                }
                if (useDialogBackground) {
                    holder.rootView.setBackgroundColor(ContextCompat.getColor(holder.rootView.getContext(), R.color.dialogBackground));
                } else {
                    holder.rootView.setBackgroundColor(ContextCompat.getColor(holder.rootView.getContext(), R.color.almostWhite));
                }
                if (showPinned && discussion.pinned) {
                    holder.pinnedIcon.setVisibility(View.VISIBLE);
                } else {
                    holder.pinnedIcon.setVisibility(View.GONE);
                }
                holder.initialView.setDiscussion(discussion);

                if (selectable) {
                    holder.selectionCheckBox.setVisibility(View.VISIBLE);
                    holder.selectionCheckBox.setChecked(discussion.selected);
                } else {
                    holder.selectionCheckBox.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            if (filteredDiscussions != null) {
                return filteredDiscussions.size();
            }
            return 0;
        }

        class DiscussionViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final View rootView;
            final TextView discussionTitleTextView;
            final TextView discussionGroupMembersTextView;
            final InitialView initialView;
            final CheckBox selectionCheckBox;
            final ImageView pinnedIcon;

            DiscussionViewHolder(View itemView) {
                super(itemView);
                rootView = itemView;
                itemView.setOnClickListener(this);
                discussionTitleTextView = itemView.findViewById(R.id.discussion_title_text_view);
                discussionGroupMembersTextView = itemView.findViewById(R.id.discussion_group_members_text_view);
                initialView = itemView.findViewById(R.id.discussion_initial_view);
                selectionCheckBox = itemView.findViewById(R.id.discussion_selection_check_box);
                pinnedIcon = itemView.findViewById(R.id.discussion_pinned_image_view);
            }

            @Override
            public void onClick(View view) {
                int position = this.getLayoutPosition();
                if (selectable) {
                    filteredDiscussionListViewModel.selectedDiscussionId(filteredDiscussions.get(position).discussionId);
                } else if (FilteredDiscussionListFragment.this.onClickDelegate != null) {
                    FilteredDiscussionListFragment.this.onClickDelegate.discussionClicked(view, filteredDiscussions.get(position));
                }
            }
        }
    }

    public interface FilteredDiscussionListOnClickDelegate {
        void discussionClicked(View view, FilteredDiscussionListViewModel.SearchableDiscussion searchableDiscussion);
    }
}
