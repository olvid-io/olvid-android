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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.dao.DiscussionDao;


public class FilteredDiscussionListViewModel extends ViewModel {
    private final MutableLiveData<List<SearchableDiscussion>> filteredDiscussions = new MutableLiveData<>();
    private List<SearchableDiscussion> unfilteredDiscussions;
    private String filter;
    private List<Pattern> filterPatterns;


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
                filterPatterns.add(Pattern.compile(Pattern.quote(App.unAccent(part))));
            }
        }
        if (unfilteredDiscussions != null) {
            App.runThread(new FilterDiscussionListTask(filterPatterns, filteredDiscussions, unfilteredDiscussions));
        }
    }

    public List<Pattern> getFilterPatterns() {
        return filterPatterns;
    }

    public LiveData<List<SearchableDiscussion>> getFilteredDiscussions() {
        return filteredDiscussions;
    }

    private static class FilterDiscussionListTask implements Runnable {
        private final List<Pattern> filterPatterns;
        private final MutableLiveData<List<SearchableDiscussion>> liveFilteredDiscussions;
        private final List<SearchableDiscussion> unfilteredDiscussions;


        FilterDiscussionListTask(List<Pattern> filterPatterns, MutableLiveData<List<SearchableDiscussion>> liveFilteredDiscussions, List<SearchableDiscussion> unfilteredDiscussions) {
            if (filterPatterns == null) {
                this.filterPatterns = new ArrayList<>(0);
            } else {
                this.filterPatterns = filterPatterns;
            }
            this.liveFilteredDiscussions = liveFilteredDiscussions;
            this.unfilteredDiscussions = unfilteredDiscussions;
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
                    list.add(searchableDiscussion);
                }
            }
            liveFilteredDiscussions.postValue(list);
        }
    }



    public static class SearchableDiscussion {
        public final long discussionId;
        public final boolean isGroupDiscussion;
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

        public SearchableDiscussion(@NonNull DiscussionDao.DiscussionAndContactDisplayNames discussionAndContactDisplayNames) {
            this.discussionId = discussionAndContactDisplayNames.discussion.id;
            if (discussionAndContactDisplayNames.discussion.bytesGroupOwnerAndUid != null) {
                this.isGroupDiscussion = true;
                this.byteIdentifier = discussionAndContactDisplayNames.discussion.bytesGroupOwnerAndUid;
            } else if (discussionAndContactDisplayNames.discussion.bytesContactIdentity != null) {
                this.isGroupDiscussion = false;
                this.byteIdentifier = discussionAndContactDisplayNames.discussion.bytesContactIdentity;
            } else {
                this.isGroupDiscussion = false;
                this.byteIdentifier = new byte[0];
            }
            this.title = discussionAndContactDisplayNames.discussion.title;
            this.groupMemberNameList = discussionAndContactDisplayNames.groupContactDisplayNames == null ? "" : discussionAndContactDisplayNames.groupContactDisplayNames;
            this.patternMatchingField = App.unAccent(title + "\n" + groupMemberNameList);
            this.photoUrl = discussionAndContactDisplayNames.discussion.photoUrl;
            this.keycloakManaged = discussionAndContactDisplayNames.discussion.keycloakManaged;
            this.active = discussionAndContactDisplayNames.discussion.active;
        }
    }
}
