/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import io.olvid.messenger.databases.entity.Contact;


public class FilteredContactListViewModel extends ViewModel {
    private final MutableLiveData<List<SelectableContact>> filteredContacts = new MutableLiveData<>();
    private final MutableLiveData<List<Contact>> selectedContacts = new MutableLiveData<>();
    private List<Contact> unfilteredContacts;
    private String filter;
    private List<Pattern> filterPatterns;
    private final HashSet<Contact> selectedContactsHashSet = new HashSet<>();


    public FilteredContactListViewModel() { }

    public void setUnfilteredContacts(@Nullable List<Contact> unfilteredContacts) {
        this.unfilteredContacts = unfilteredContacts;
        setFilter(filter);
    }

    public List<Contact> getUnfilteredContacts() {
        return unfilteredContacts;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        if (filter == null) {
            filterPatterns = null;
        } else {
            String[] parts = filter.trim().split("\\s+");
            filterPatterns = new ArrayList<>(parts.length);
            for (String part: parts) {
                if (!part.isEmpty()) {
                    filterPatterns.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))));
                }
            }
        }
        if (unfilteredContacts != null) {
            App.runThread(new FilterContactListTask(filterPatterns, filteredContacts, unfilteredContacts, selectedContactsHashSet));
        }
    }

    public List<Pattern> getFilterPatterns() {
        return filterPatterns;
    }

    public String getFilter() {
        return filter;
    }

    public LiveData<List<SelectableContact>> getFilteredContacts() {
        return filteredContacts;
    }

    public LiveData<List<Contact>> getSelectedContacts() {
        return selectedContacts;
    }

    public void selectContact(Contact contact) {
        if (!selectedContactsHashSet.remove(contact)) {
            selectedContactsHashSet.add(contact);
        }
        selectedContacts.postValue(new ArrayList<>(selectedContactsHashSet));
        refreshSelectedContacts();
    }

    public void setSelectedContacts(List<Contact> selectedContacts) {
        selectedContactsHashSet.clear();
        selectedContactsHashSet.addAll(selectedContacts);
        this.selectedContacts.postValue(selectedContacts);
        refreshSelectedContacts();
    }

    private void refreshSelectedContacts() {
        List<SelectableContact> filteredContacts = this.filteredContacts.getValue();
        if (filteredContacts != null) {
            List<SelectableContact> updatedList = new ArrayList<>(filteredContacts.size());
            for (SelectableContact filteredContact: filteredContacts) {
                updatedList.add(new SelectableContact(filteredContact.contact, selectedContactsHashSet.contains(filteredContact.contact)));
            }
            this.filteredContacts.postValue(updatedList);
        }
    }


    private static class FilterContactListTask implements Runnable {
        private final List<Pattern> filterPatterns;
        private final MutableLiveData<List<SelectableContact>> filteredContacts;
        private final List<Contact> unfilteredContacts;
        private final HashSet<Contact> selectedContactsHashSet;


        FilterContactListTask(List<Pattern> filterPatterns, MutableLiveData<List<SelectableContact>> filteredContacts, List<Contact> unfilteredContacts, HashSet<Contact> selectedContactsHashSet) {
            if (filterPatterns == null) {
                this.filterPatterns = new ArrayList<>(0);
            } else {
                this.filterPatterns = filterPatterns;
            }
            this.filteredContacts = filteredContacts;
            this.unfilteredContacts = unfilteredContacts;
            this.selectedContactsHashSet = new HashSet<>(selectedContactsHashSet); // copy the set to avoid concurrent modification
        }

        @Override
        public void run() {
            if (unfilteredContacts == null) {
                filteredContacts.postValue(null);
                return;
            }
            List<SelectableContact> list = new ArrayList<>();

            for (Contact contact: unfilteredContacts) {
                boolean matches = true;
                for (Pattern pattern: filterPatterns) {
                    Matcher matcher = pattern.matcher(contact.fullSearchDisplayName);
                    if (!matcher.find()) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    list.add(new SelectableContact(contact, selectedContactsHashSet.contains(contact)));
                }
            }
            filteredContacts.postValue(list);
        }
    }

    public static class SelectableContact {
        public final Contact contact;
        public final boolean selected;

        public SelectableContact(Contact contact, boolean selected) {
            this.contact = contact;
            this.selected = selected;
        }
    }
}
