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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.openid.KeycloakManager;

public class ContactListViewModel extends ViewModel {
    public static final long KEYCLOAK_SEARCH_DELAY_MILLIS = 300L;

    private List<Contact> unfilteredContacts;
    private List<Contact> unfilteredNotOneToOneContacts;
    private final MutableLiveData<List<ContactOrKeycloakDetails>> filteredContacts = new MutableLiveData<>();
    private String filter;
    private List<Pattern> filterPatterns;
    private final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("ContactListViewModelExecutor");

    private boolean keycloakSearchInProgress = false;
    private byte[] keycloakSearchBytesOwnedIdentity = null;
    private String keycloakSearchResultsFilter = null;
    private List<JsonKeycloakUserDetails> keycloakSearchResults = null;
    private boolean keycloakSearchAdditionalResults = false;

    public void setUnfilteredContacts(@Nullable List<Contact> unfilteredContacts) {
        this.unfilteredContacts = unfilteredContacts;
        setFilter(filter);
    }

    public void setUnfilteredNotOneToOneContacts(List<Contact> unfilteredNotOneToOneContacts) {
        this.unfilteredNotOneToOneContacts = unfilteredNotOneToOneContacts;
        setFilter(filter);
    }

    public void setFilter(final String filter) {
        this.filter = filter;
        if (filter == null) {
            filterPatterns = null;
        } else {
            String[] parts = filter.trim().split("\\s+");
            filterPatterns = new ArrayList<>(parts.length);
            for (String part: parts) {
                if (part.length() > 0) {
                    filterPatterns.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))));
                }
            }

            OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
            if (filterPatterns.size() > 0 && ownedIdentity != null && ownedIdentity.keycloakManaged) {
                if (!filter.equals(keycloakSearchResultsFilter) || !Arrays.equals(ownedIdentity.bytesOwnedIdentity, keycloakSearchBytesOwnedIdentity)) {
                    keycloakSearchInProgress = true;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> searchKeycloak(ownedIdentity.bytesOwnedIdentity, filter), KEYCLOAK_SEARCH_DELAY_MILLIS);
                }
            }
        }

        executor.execute(new FilterContactListTask(filterPatterns, filteredContacts, unfilteredContacts, unfilteredNotOneToOneContacts, keycloakSearchInProgress, keycloakSearchResults, keycloakSearchAdditionalResults));
    }

    public List<Pattern> getFilterPatterns() {
        return filterPatterns;
    }

    public String getFilter() {
        return filter;
    }

    public LiveData<List<ContactOrKeycloakDetails>> getFilteredContacts() {
        return filteredContacts;
    }

    private static class FilterContactListTask implements Runnable {
        private final List<Pattern> filterPatterns;
        private final MutableLiveData<List<ContactOrKeycloakDetails>> filteredContacts;
        private final List<Contact> unfilteredContacts;
        private final List<Contact> unfilteredNotOneToOneContacts;
        private final boolean keycloakSearchInProgress;
        private final List<JsonKeycloakUserDetails> keycloakSearchResults;
        private final boolean keycloakSearchAdditionalResults;

        FilterContactListTask(List<Pattern> filterPatterns, MutableLiveData<List<ContactOrKeycloakDetails>> filteredContacts, List<Contact> unfilteredContacts, List<Contact> unfilteredNotOneToOneContacts, boolean keycloakSearchInProgress, List<JsonKeycloakUserDetails> keycloakSearchResults, boolean keycloakSearchAdditionalResults) {
            if (filterPatterns == null) {
                this.filterPatterns = new ArrayList<>(0);
            } else {
                this.filterPatterns = filterPatterns;
            }
            this.filteredContacts = filteredContacts;
            this.unfilteredContacts = unfilteredContacts;
            this.unfilteredNotOneToOneContacts = unfilteredNotOneToOneContacts;
            this.keycloakSearchInProgress = keycloakSearchInProgress;
            this.keycloakSearchResults = keycloakSearchResults;
            this.keycloakSearchAdditionalResults = keycloakSearchAdditionalResults;
        }

        @Override
        public void run() {
            if (filterPatterns.size() == 0) {
                if (unfilteredContacts == null) {
                    filteredContacts.postValue(null);
                    return;
                }
                List<ContactOrKeycloakDetails> list = new ArrayList<>();
                for (Contact contact: unfilteredContacts) {
                    list.add(new ContactOrKeycloakDetails(contact));
                }
                filteredContacts.postValue(list);
            } else {
                if (unfilteredContacts == null && unfilteredNotOneToOneContacts == null) {
                    filteredContacts.postValue(null);
                    return;
                }
                List<ContactOrKeycloakDetails> list = new ArrayList<>();

                if (unfilteredContacts != null) {
                    for (Contact contact : unfilteredContacts) {
                        boolean matches = true;
                        for (Pattern pattern : filterPatterns) {
                            Matcher matcher = pattern.matcher(contact.fullSearchDisplayName);
                            if (!matcher.find()) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            list.add(new ContactOrKeycloakDetails(contact));
                        }
                    }
                }

                if (unfilteredNotOneToOneContacts != null) {
                    for (Contact contact : unfilteredNotOneToOneContacts) {
                        boolean matches = true;
                        for (Pattern pattern : filterPatterns) {
                            Matcher matcher = pattern.matcher(contact.fullSearchDisplayName);
                            if (!matcher.find()) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            list.add(new ContactOrKeycloakDetails(contact));
                        }
                    }
                }

                if (keycloakSearchInProgress) {
                    list.add(new ContactOrKeycloakDetails(true));
                } else {
                    if (keycloakSearchResults != null) {
                        for (JsonKeycloakUserDetails keycloakUserDetails : keycloakSearchResults) {
                            // check if the we know the contact by querying the cache (note that this also filters out our ownedIdentity)
                            String displayName = AppSingleton.getContactCustomDisplayName(keycloakUserDetails.getIdentity());
                            if (displayName == null && keycloakUserDetails.getIdentity() != null) {
                                // unknown contact --> add them to the list
                                list.add(new ContactOrKeycloakDetails(keycloakUserDetails));
                            }
                        }

                        if (keycloakSearchAdditionalResults) {
                            list.add(new ContactOrKeycloakDetails(false));
                        }
                    }
                }

                filteredContacts.postValue(list);
            }
        }
    }

    private void searchKeycloak(@NonNull byte[] bytesOwnedIdentity, @NonNull String filter) {
        OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();

        if (ownedIdentity == null
                || !Arrays.equals(bytesOwnedIdentity, ownedIdentity.bytesOwnedIdentity)
                || !filter.equals(this.filter)) {
            // something changed during the KEYCLOAK_SEARCH_DELAY_MILLIS --> abort
            return;
        }


        KeycloakManager.getInstance().search(bytesOwnedIdentity, filter, new KeycloakManager.KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Integer>>() {
            @Override
            public void success(Pair<List<JsonKeycloakUserDetails>, Integer> searchResult) {
                OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
                if (!Objects.equals(ContactListViewModel.this.filter, filter)
                        || ownedIdentity == null
                        || !Arrays.equals(ownedIdentity.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    // something changed while we were searching --> abort
                    return;
                }

                keycloakSearchInProgress = false;
                keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity;
                keycloakSearchResultsFilter = filter;
                keycloakSearchResults = searchResult.first;

                if (searchResult.second == null) {
                    keycloakSearchAdditionalResults = false;
                } else {
                    if (searchResult.first == null) {
                        keycloakSearchAdditionalResults = searchResult.second > 0;
                    } else {
                        keycloakSearchAdditionalResults = searchResult.second > searchResult.first.size();
                    }
                }

                // re-filter to add keycloak search results
                setFilter(filter);
            }

            @Override
            public void failed(int rfc) {
                OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
                if (!Objects.equals(ContactListViewModel.this.filter, filter)
                        || ownedIdentity == null
                        || !Arrays.equals(ownedIdentity.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    return;
                }

                keycloakSearchInProgress = false;
                keycloakSearchBytesOwnedIdentity = bytesOwnedIdentity;
                keycloakSearchResultsFilter = filter;
                keycloakSearchResults = null;
                keycloakSearchAdditionalResults = false;

                // re-filter anyway to force refresh and remove the "searching" spinner
                setFilter(filter);
            }
        });
    }


    enum ContactType {
        CONTACT,
        KEYCLOAK,
        KEYCLOAK_SEARCHING,
        KEYCLOAK_MORE_RESULTS,
    }

    static class ContactOrKeycloakDetails {
        @NonNull final ContactType contactType;
        @Nullable final Contact contact;
        @Nullable final JsonKeycloakUserDetails keycloakUserDetails;

        public ContactOrKeycloakDetails(@NonNull Contact contact) {
            this.contactType = ContactType.CONTACT;
            this.contact = contact;
            this.keycloakUserDetails = null;
        }

        public ContactOrKeycloakDetails(@NonNull JsonKeycloakUserDetails keycloakUserDetails) {
            this.contactType = ContactType.KEYCLOAK;
            this.contact = null;
            this.keycloakUserDetails = keycloakUserDetails;
        }

        public ContactOrKeycloakDetails(boolean searching) {
            this.contactType = searching ? ContactType.KEYCLOAK_SEARCHING : ContactType.KEYCLOAK_MORE_RESULTS;
            this.contact = null;
            this.keycloakUserDetails = null;
        }
    }
}
