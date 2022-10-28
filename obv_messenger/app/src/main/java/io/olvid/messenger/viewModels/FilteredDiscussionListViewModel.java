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

package io.olvid.messenger.viewModels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.Discussion;


public class FilteredDiscussionListViewModel extends ViewModel {
    private final MutableLiveData<List<SearchableDiscussion>> filteredDiscussions = new MutableLiveData<>();
    private final MutableLiveData<List<Long>> selectedDiscussionIds = new MutableLiveData<>();
    private List<SearchableDiscussion> unfilteredDiscussions;
    private String filter;
    private List<Pattern> filterPatterns;
    private final HashSet<Long> selectedDiscussionIdsHashSet = new HashSet<>();


    public FilteredDiscussionListViewModel() {
    }

    public void setUnfilteredDiscussions(@Nullable List<SearchableDiscussion> unfilteredDiscussions) {
        this.unfilteredDiscussions = unfilteredDiscussions;
        setFilter(filter);
    }

    public void setFilter(String filter) {
        this.filter = filter;
        if (filter == null) {
            filterPatterns = null;
        } else {
            String[] parts = filter.trim().split("\\s+");
            filterPatterns = new ArrayList<>(parts.length);
            for (String part: parts) {
                filterPatterns.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))));
            }
        }
        if (unfilteredDiscussions != null) {
            App.runThread(new FilterDiscussionListTask(filterPatterns, filteredDiscussions, unfilteredDiscussions, selectedDiscussionIdsHashSet));
        }
    }

    public List<Pattern> getFilterPatterns() {
        return filterPatterns;
    }

    public LiveData<List<SearchableDiscussion>> getFilteredDiscussions() {
        return filteredDiscussions;
    }

    public LiveData<List<Long>> getSelectedDiscussionIds() {
        return selectedDiscussionIds;
    }

    public void selectedDiscussionId(long discussionId) {
        if (!selectedDiscussionIdsHashSet.remove(discussionId)) {
            selectedDiscussionIdsHashSet.add(discussionId);
        }
        selectedDiscussionIds.postValue(new ArrayList<>(selectedDiscussionIdsHashSet));
        refreshSelectedDiscussionIds();
    }

    private void refreshSelectedDiscussionIds() {
        List<SearchableDiscussion> filteredDiscussions = this.filteredDiscussions.getValue();
        if (filteredDiscussions != null) {
            List<SearchableDiscussion> updatedList = new ArrayList<>(filteredDiscussions.size());
            for (SearchableDiscussion discussion: filteredDiscussions) {
                discussion.selected = selectedDiscussionIdsHashSet.contains(discussion.discussionId);
                updatedList.add(discussion);
            }
            this.filteredDiscussions.postValue(updatedList);
        }
    }

    public void deselectAll() {
        selectedDiscussionIdsHashSet.clear();
        selectedDiscussionIds.postValue(new ArrayList<>());
        refreshSelectedDiscussionIds();
    }

    private static class FilterDiscussionListTask implements Runnable {
        private final List<Pattern> filterPatterns;
        private final MutableLiveData<List<SearchableDiscussion>> liveFilteredDiscussions;
        private final List<SearchableDiscussion> unfilteredDiscussions;
        private final HashSet<Long> selectedDiscussionIds;

        FilterDiscussionListTask(List<Pattern> filterPatterns, MutableLiveData<List<SearchableDiscussion>> liveFilteredDiscussions, List<SearchableDiscussion> unfilteredDiscussions, HashSet<Long> selectedDiscussionIds) {
            if (filterPatterns == null) {
                this.filterPatterns = new ArrayList<>(0);
            } else {
                this.filterPatterns = filterPatterns;
            }
            this.liveFilteredDiscussions = liveFilteredDiscussions;
            this.unfilteredDiscussions = unfilteredDiscussions;
            this.selectedDiscussionIds = new HashSet<>(selectedDiscussionIds); // copy the set to avoid concurrent modification
        }

        @Override
        public void run() {
            if (unfilteredDiscussions == null) {
                liveFilteredDiscussions.postValue(null);
                return;
            }
            List<SearchableDiscussion> list = new ArrayList<>();

            for (SearchableDiscussion searchableDiscussion: unfilteredDiscussions) {
                boolean matches = true;
                for (Pattern pattern: filterPatterns) {
                    Matcher matcher = pattern.matcher(searchableDiscussion.patternMatchingField);
                    if (!matcher.find()) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    searchableDiscussion.selected = selectedDiscussionIds.contains(searchableDiscussion.discussionId);
                    list.add(searchableDiscussion);
                }
            }
            liveFilteredDiscussions.postValue(list);
        }
    }



    public static class SearchableDiscussion {
        public final long discussionId;
        public final boolean isGroupDiscussion;
        @NonNull
        public final byte[] byteIdentifier;
        @NonNull
        public final String title;
        @NonNull
        public final String groupMemberNameList;
        @NonNull
        public final String patternMatchingField;
        public final String photoUrl;
        public final boolean keycloakManaged;
        public final boolean active;
        public final boolean pinned;

        public boolean selected;

        public SearchableDiscussion(@NonNull DiscussionDao.DiscussionAndGroupMembersNames discussionAndGroupMembersNames) {
            this.discussionId = discussionAndGroupMembersNames.discussion.id;
            switch (discussionAndGroupMembersNames.discussion.status) {
                case Discussion.STATUS_LOCKED: {
                    this.byteIdentifier = new byte[0];
                    this.isGroupDiscussion = false;
                    break;
                }
                case Discussion.STATUS_NORMAL:
                default: {
                    this.byteIdentifier = discussionAndGroupMembersNames.discussion.bytesDiscussionIdentifier;
                    this.isGroupDiscussion = discussionAndGroupMembersNames.discussion.discussionType != Discussion.TYPE_CONTACT;
                    break;
                }
            }
            this.title = discussionAndGroupMembersNames.discussion.title;
            this.groupMemberNameList = discussionAndGroupMembersNames.groupMemberNames == null ? "" : discussionAndGroupMembersNames.groupMemberNames;
            this.patternMatchingField = StringUtils.unAccent(title + "\n" + groupMemberNameList);
            this.photoUrl = discussionAndGroupMembersNames.discussion.photoUrl;
            this.keycloakManaged = discussionAndGroupMembersNames.discussion.keycloakManaged;
            this.active = discussionAndGroupMembersNames.discussion.active;
            this.pinned = discussionAndGroupMembersNames.discussion.pinned;

            this.selected = false;
        }
    }
}
