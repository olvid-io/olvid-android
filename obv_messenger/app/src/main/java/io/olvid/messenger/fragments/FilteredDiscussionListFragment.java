/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;

public class FilteredDiscussionListFragment extends Fragment implements TextWatcher {
    private EditText discussionFilterEditText;
    protected FilteredDiscussionListViewModel filteredDiscussionListViewModel = null;
    private FilteredDiscussionListOnClickDelegate onClickDelegate;
    private LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> unfilteredDiscussions = null;
    private EmptyRecyclerView recyclerView;
    private boolean removeBottomPadding = false;
    private View emptyView;

    protected FilteredDiscussionListAdapter filteredDiscussionListAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filteredDiscussionListViewModel = new ViewModelProvider(this).get(FilteredDiscussionListViewModel.class);

        if (unfilteredDiscussions != null) {
            observeUnfiltered();
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

        recyclerView.addItemDecoration(new DividerItemDecoration(rootView.getContext()));

        return rootView;
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
                if (position == 0) {
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
            if (position == 0) {
                return;
            }
            outRect.top = dividerHeight;
        }
    }


    public void setUnfilteredDiscussions(LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> unfilteredDiscussions) {
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

    private void observeUnfiltered() {
        this.unfilteredDiscussions.observe(this, (List<DiscussionDao.DiscussionAndContactDisplayNames> discussionAndContactDisplayNamesList) -> {
            if (discussionAndContactDisplayNamesList == null) {
                filteredDiscussionListViewModel.setUnfilteredDiscussions(null);
            } else {
                List<FilteredDiscussionListViewModel.SearchableDiscussion> list = new ArrayList<>(discussionAndContactDisplayNamesList.size());
                for (DiscussionDao.DiscussionAndContactDisplayNames discussionAndContactDisplayNames : discussionAndContactDisplayNamesList) {
                    list.add(new FilteredDiscussionListViewModel.SearchableDiscussion(discussionAndContactDisplayNames));
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
            return new FilteredDiscussionListAdapter.DiscussionViewHolder(view, viewType);
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
                    String unaccentTitle = App.unAccent(discussion.title);
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
                            String unaccentGroupMemberNames = App.unAccent(discussion.groupMemberNameList);
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
                    holder.discussionTitleTextView.setText(discussion.title);
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
                if (discussion.isGroupDiscussion) {
                    holder.initialView.setKeycloakCertified(discussion.keycloakManaged);
                    holder.initialView.setInactive(!discussion.active);
                    if (discussion.photoUrl == null) {
                        holder.initialView.setGroup(discussion.byteIdentifier);
                    } else {
                        holder.initialView.setPhotoUrl(discussion.byteIdentifier, discussion.photoUrl);
                    }
                } else if (discussion.byteIdentifier.length != 0){
                    holder.initialView.setKeycloakCertified(discussion.keycloakManaged);
                    holder.initialView.setInactive(!discussion.active);
                    if (discussion.photoUrl == null) {
                        holder.initialView.setInitial(discussion.byteIdentifier, App.getInitial(discussion.title));
                    } else {
                        holder.initialView.setPhotoUrl(discussion.byteIdentifier, discussion.photoUrl);
                    }
                } else {
                    // locked discussion
                    holder.initialView.setLocked(true);
                    if (discussion.photoUrl == null) {
                        holder.initialView.setInitial(discussion.byteIdentifier, "");
                    } else {
                        holder.initialView.setPhotoUrl(discussion.byteIdentifier, discussion.photoUrl);
                    }
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
            final TextView discussionTitleTextView;
            final TextView discussionGroupMembersTextView;
            final InitialView initialView;

            DiscussionViewHolder(View itemView, int viewType) {
                super(itemView);
                itemView.setOnClickListener(this);
                discussionTitleTextView = itemView.findViewById(R.id.discussion_title_text_view);
                discussionGroupMembersTextView = itemView.findViewById(R.id.discussion_group_members_text_view);
                initialView = itemView.findViewById(R.id.discussion_initial_view);
            }

            @Override
            public void onClick(View view) {
                int position = this.getLayoutPosition();
                FilteredDiscussionListFragment.this.onClickDelegate.discussionClicked(view, filteredDiscussions.get(position));
            }
        }
    }

    public interface FilteredDiscussionListOnClickDelegate {
        void discussionClicked(View view, FilteredDiscussionListViewModel.SearchableDiscussion searchableDiscussion);
    }
}
