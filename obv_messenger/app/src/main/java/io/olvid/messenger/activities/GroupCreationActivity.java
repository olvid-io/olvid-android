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

package io.olvid.messenger.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.owneddetails.OwnedGroupDetailsFragment;
import io.olvid.messenger.owneddetails.OwnedGroupDetailsViewModel;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.GroupCreationViewModel;


public class GroupCreationActivity extends LockableActivity implements View.OnClickListener {
    private ViewPager viewPager;
    private GroupCreationViewModel groupCreationViewModel;
    private OwnedGroupDetailsViewModel groupDetailsViewModel;
    private Button nextButton;
    private Button previousButton;
    private Button confirmationButton;
    private TextView subtitleTextView;

    // this boolean controls whether groups are created in v2 format or not
    public static final boolean groupV2 = true;

    public static final int CONTACTS_SELECTION_TAB = 0;
    public static final int GROUP_NAME_TAB = 1;

    public static final String ABSOLUTE_PHOTO_URL_INTENT_EXTRA = "photo_url"; // String with absolute path to photo
    public static final String SERIALIZED_GROUP_DETAILS_INTENT_EXTRA = "serialized_group_details"; // String with serialized JsonGroupDetails
    public static final String PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA = "preselected_group_members"; // Array of BytesKey

    ContactsSelectionFragment contactsSelectionFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        groupCreationViewModel = new ViewModelProvider(this).get(GroupCreationViewModel.class);

        groupDetailsViewModel = new ViewModelProvider(this).get(OwnedGroupDetailsViewModel.class);
        groupDetailsViewModel.getValid().observe(this, ready -> {
            if (confirmationButton != null) {
                if (ready != null) {
                    confirmationButton.setEnabled(ready);
                } else {
                    confirmationButton.setEnabled(false);
                }
            }
        });


        if (savedInstanceState == null) {
            groupDetailsViewModel.setBytesGroupOwnerAndUidOrIdentifier(new byte[0]);

            // only look at intent when first creating the activity
            Intent intent = getIntent();
            groupDetailsViewModel.setGroupV2(groupV2);

            if (intent.hasExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA)) {
                try {
                    JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(intent.getStringExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA), JsonGroupDetails.class);
                    if (groupDetails.getName() != null && groupDetails.getName().length() > 0) {
                        groupDetailsViewModel.setGroupName(getString(R.string.text_copy_of_prefix) + groupDetails.getName());
                    }
                    groupDetailsViewModel.setGroupDescription(groupDetails.getDescription());
                } catch (Exception ignored) {
                }
            }
            if (intent.hasExtra(ABSOLUTE_PHOTO_URL_INTENT_EXTRA)) {
                groupDetailsViewModel.setAbsolutePhotoUrl(intent.getStringExtra(ABSOLUTE_PHOTO_URL_INTENT_EXTRA));
            }
            if (intent.hasExtra(PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA)) {
                ArrayList<BytesKey> preselectedContactBytesKeys = intent.getParcelableArrayListExtra(PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA);
                if (preselectedContactBytesKeys != null) {
                    App.runThread(() -> {
                        List<Contact> preselectedContacts = new ArrayList<>();
                        for (BytesKey bytesKey : preselectedContactBytesKeys) {
                            Contact contact = AppDatabase.getInstance().contactDao().get(AppSingleton.getBytesCurrentIdentity(), bytesKey.bytes);
                            if (contact != null) {
                                preselectedContacts.add(contact);
                            }
                        }

                        if (!preselectedContacts.isEmpty()) {
                            runOnUiThread(() -> {
                                groupCreationViewModel.setSelectedContacts(preselectedContacts);
                                if (contactsSelectionFragment != null) {
                                    contactsSelectionFragment.setInitiallySelectedContacts(preselectedContacts);
                                }
                            });
                        }
                    });
                }
            }
        }
        setContentView(R.layout.activity_group_creation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        subtitleTextView = toolbar.findViewById(R.id.subtitle);
        View groupV2WarningMessage = findViewById(R.id.group_v2_warning_message);
        groupCreationViewModel.getShowGroupV2WarningLiveData().observe(this, (Boolean showGroupV2WarningMessage) -> groupV2WarningMessage.setVisibility((showGroupV2WarningMessage != null && showGroupV2WarningMessage) ? View.VISIBLE : View.GONE));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
        }

        groupCreationViewModel.getSubtitleLiveData().observe(this, (Pair<Integer, Integer> tabAndSelectedContactCount) -> {
            if (subtitleTextView == null || tabAndSelectedContactCount == null || tabAndSelectedContactCount.first == null || tabAndSelectedContactCount.second == null) {
                return;
            }
            switch (tabAndSelectedContactCount.first) {
                case CONTACTS_SELECTION_TAB:
                    if (tabAndSelectedContactCount.second == 0) {
                        subtitleTextView.setText(getString(R.string.subtitle_select_group_members));
                    } else {
                        subtitleTextView.setText(getResources().getQuantityString(R.plurals.other_members_count, tabAndSelectedContactCount.second, tabAndSelectedContactCount.second));
                    }
                    break;
                case GROUP_NAME_TAB:
                    subtitleTextView.setText(getString(R.string.subtitle_choose_group_name));
                    break;
            }
        });

        //noinspection deprecation
        FragmentPagerAdapter fragmentPagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
            @Override
            @NonNull
            public Fragment getItem(int position) {
                switch (position) {
                    case CONTACTS_SELECTION_TAB:
                        return new ContactsSelectionFragment();
                    case GROUP_NAME_TAB:
                    default:
                        return new GroupNameFragment();
                }
            }

            @Override
            @NonNull
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                Fragment fragment = (Fragment) super.instantiateItem(container, position);
                switch (position) {
                    case CONTACTS_SELECTION_TAB:
                        contactsSelectionFragment = (ContactsSelectionFragment) fragment;
                        contactsSelectionFragment.setGroupV2(groupDetailsViewModel.isGroupV2());
                        contactsSelectionFragment.setInitiallySelectedContacts(groupCreationViewModel.getSelectedContacts());
                        break;
                    case GROUP_NAME_TAB:
                        break;
                }
                return fragment;
            }

            @Override
            public int getCount() {
                return 2;
            }
        };


        viewPager = findViewById(R.id.group_creation_view_pager);
        viewPager.setAdapter(fragmentPagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        TabLayout tabLayout = findViewById(R.id.group_creation_tab_dots);
        tabLayout.setupWithViewPager(viewPager, true);

        nextButton = findViewById(R.id.button_next_tab);
        nextButton.setOnClickListener(this);
        previousButton = findViewById(R.id.button_previous_tab);
        previousButton.setOnClickListener(this);
        confirmationButton = findViewById(R.id.button_confirmation);
        confirmationButton.setOnClickListener(this);

        ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageScrollStateChanged(int state) {}

            @Override
            public void onPageSelected(int position) {
                groupCreationViewModel.setSelectedTab(position);
                switch (position) {
                    case CONTACTS_SELECTION_TAB:
                        previousButton.setVisibility(View.GONE);
                        nextButton.setVisibility(View.VISIBLE);
                        confirmationButton.setVisibility(View.GONE);
                        break;
                    case GROUP_NAME_TAB:
                        previousButton.setVisibility(View.VISIBLE);
                        nextButton.setVisibility(View.GONE);
                        confirmationButton.setVisibility(View.VISIBLE);
                        break;
                }
                invalidateOptionsMenu();
            }
        };
        viewPager.addOnPageChangeListener(pageChangeListener);
        pageChangeListener.onPageSelected(0);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        if (viewPager.getCurrentItem() == CONTACTS_SELECTION_TAB) {
            getMenuInflater().inflate(R.menu.menu_group_creation_contact_selection, menu);
            final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
            searchView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    groupCreationViewModel.setSearchOpened(true);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    groupCreationViewModel.setSearchOpened(false);
                }
            });
            searchView.setQueryHint(getString(R.string.hint_search_contact_name));
            if (SettingsActivity.useKeyboardIncognitoMode()) {
                searchView.setImeOptions(searchView.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
            searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                final EditText editText = new AppCompatEditText(searchView.getContext());

                {
                    contactsSelectionFragment.setContactFilterEditText(editText);
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    editText.setText(newText);
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        int position = viewPager.getCurrentItem();
        if (position > 0) {
            viewPager.setCurrentItem(position-1);
        } else {
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_next_tab) {
            int position = viewPager.getCurrentItem();
            if (position < 2) {
                viewPager.setCurrentItem(position + 1);
            }
        } else if (id == R.id.button_previous_tab) {
            int position = viewPager.getCurrentItem();
            if (position > 0) {
                viewPager.setCurrentItem(position - 1);
            }
        } else if (id == R.id.button_confirmation) {
            if (groupCreationViewModel.getSelectedContacts() == null || groupCreationViewModel.getSelectedContacts().size() == 0) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_create_empty_group)
                        .setMessage(R.string.dialog_message_create_empty_group)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                            confirmationButton.setEnabled(false);
                            initiateGroupCreationProtocol();
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            } else {
                confirmationButton.setEnabled(false);
                initiateGroupCreationProtocol();
            }
        }
    }

    private void initiateGroupCreationProtocol() {
        if (groupDetailsViewModel.isGroupV2()) {
            byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            if (bytesOwnedIdentity == null) {
                return;
            }
            List<Contact> selectedContacts = groupCreationViewModel.getSelectedContacts();
            if (selectedContacts == null) {
                selectedContacts = new ArrayList<>();
            }
            String groupAbsolutePhotoUrl = groupDetailsViewModel.getAbsolutePhotoUrl();
            String groupName = groupDetailsViewModel.getGroupName();
            String groupDescription = groupDetailsViewModel.getGroupDescription();
            if (groupName == null) {
                groupName = "";
            }
            if (groupDescription != null) {
                groupDescription = groupDescription.trim();
            }

            JsonGroupDetails jsonGroupDetails = new JsonGroupDetails(groupName.trim(), groupDescription);
            HashMap<ObvBytesKey, HashSet<GroupV2.Permission>> otherGroupMembers = new HashMap<>();
            for (Contact contact : selectedContacts) {
                HashSet<GroupV2.Permission> permissions = new HashSet<>(Arrays.asList(GroupV2.Permission.DEFAULT_MEMBER_PERMISSIONS));
                otherGroupMembers.put(new ObvBytesKey(contact.bytesContactIdentity), permissions);
            }

            try {
                String serializedGroupDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonGroupDetails);
                AppSingleton.getEngine().startGroupV2CreationProtocol(serializedGroupDetails, groupAbsolutePhotoUrl, bytesOwnedIdentity, new HashSet<>(Arrays.asList(GroupV2.Permission.DEFAULT_ADMIN_PERMISSIONS)), otherGroupMembers);

                AppSingleton.getEngine().addNotificationListener(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED, new EngineNotificationListener() {
                    private Long registrationNumber = null;
                    private final EngineNotificationListener _this = this;

                    {
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                Logger.i("Group creation listener timer interrupted");
                            }
                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.GROUP_CREATED, _this);
                        }).start();
                    }

                    @Override
                    public void callback(String notificationName, HashMap<String, Object> userInfo) {
                        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED, this);
                        final ObvGroupV2 group = (ObvGroupV2) userInfo.get(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY);
                        if (group != null) {
                            runOnUiThread(() -> {
                                App.openGroupV2DetailsActivity(GroupCreationActivity.this, group.bytesOwnedIdentity, group.groupIdentifier.getBytes());
                                finish();
                            });
                        }
                    }

                    @Override
                    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                        this.registrationNumber = registrationNumber;
                    }

                    @Override
                    public long getEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber;
                    }

                    @Override
                    public boolean hasEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber != null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            if (bytesOwnedIdentity == null) {
                return;
            }
            List<Contact> selectedContacts = groupCreationViewModel.getSelectedContacts();
            if (selectedContacts == null) {
                selectedContacts = new ArrayList<>();
            }
            String groupAbsolutePhotoUrl = groupDetailsViewModel.getAbsolutePhotoUrl();
            String groupName = groupDetailsViewModel.getGroupName();
            String groupDescription = groupDetailsViewModel.getGroupDescription();
            if (groupName == null || groupName.trim().length() == 0) {
                return;
            }
            if (groupDescription != null) {
                groupDescription = groupDescription.trim();
            }

            JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto = new JsonGroupDetailsWithVersionAndPhoto();
            jsonGroupDetailsWithVersionAndPhoto.setVersion(0);
            jsonGroupDetailsWithVersionAndPhoto.setGroupDetails(new JsonGroupDetails(groupName.trim(), groupDescription));

            byte[][] bytesContactIdentities = new byte[selectedContacts.size()][];
            int i = 0;
            for (Contact contact : selectedContacts) {
                bytesContactIdentities[i] = contact.bytesContactIdentity;
                i++;
            }

            try {
                String serializedGroupDetailsWithVersionAndPhoto = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonGroupDetailsWithVersionAndPhoto);
                AppSingleton.getEngine().startGroupCreationProtocol(serializedGroupDetailsWithVersionAndPhoto, groupAbsolutePhotoUrl, bytesOwnedIdentity, bytesContactIdentities);

                AppSingleton.getEngine().addNotificationListener(EngineNotifications.GROUP_CREATED, new EngineNotificationListener() {
                    private Long registrationNumber = null;
                    private final EngineNotificationListener _this = this;

                    {
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                Logger.i("Group creation listener timer interrupted");
                            }
                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.GROUP_CREATED, _this);
                        }).start();
                    }

                    @Override
                    public void callback(String notificationName, HashMap<String, Object> userInfo) {
                        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.GROUP_CREATED, this);
                        final ObvGroup group = (ObvGroup) userInfo.get(EngineNotifications.GROUP_CREATED_GROUP_KEY);
                        if (group != null) {
                            runOnUiThread(() -> {
                                App.openGroupDetailsActivity(GroupCreationActivity.this, group.getBytesOwnedIdentity(), group.getBytesGroupOwnerAndUid());
                                finish();
                            });
                        }
                    }

                    @Override
                    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                        this.registrationNumber = registrationNumber;
                    }

                    @Override
                    public long getEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber;
                    }

                    @Override
                    public boolean hasEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber != null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class ContactsSelectionFragment extends Fragment {
        FilteredContactListFragment filteredContactListFragment;
        private List<Contact> initiallySelectedContacts;
        private boolean groupV2 = false;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            filteredContactListFragment = new FilteredContactListFragment();
            if (initiallySelectedContacts != null) {
                filteredContactListFragment.setInitiallySelectedContacts(initiallySelectedContacts);
                initiallySelectedContacts = null;
            }
            filteredContactListFragment.setSelectable(true);
            if (groupV2) {
                filteredContactListFragment.setUnfilteredContacts(Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                    if (ownedIdentity == null) {
                        return null;
                    }
                    return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannelAndGroupV2Capability(ownedIdentity.bytesOwnedIdentity);
                }));
            } else {
                filteredContactListFragment.setUnfilteredContacts(Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                    if (ownedIdentity == null) {
                        return null;
                    }
                    return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannel(ownedIdentity.bytesOwnedIdentity);
                }));
            }
            filteredContactListFragment.setSelectedContactsObserver(contacts -> {
                GroupCreationActivity activity = (GroupCreationActivity) getActivity();
                if (activity != null) {
                    activity.groupCreationViewModel.setSelectedContacts(contacts);
                }
            });
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_group_creation_contact_selection, container, false);

            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.replace(R.id.filtered_contact_list_placeholder, filteredContactListFragment);
            transaction.commit();

            return rootView;
        }

        void setInitiallySelectedContacts(List<Contact> selectedContacts) {
            if (selectedContacts != null && filteredContactListFragment != null) {
                filteredContactListFragment.setInitiallySelectedContacts(selectedContacts);
            } else {
                initiallySelectedContacts = selectedContacts;
            }
        }

        void setContactFilterEditText(EditText contactNameFilter) {
            filteredContactListFragment.setContactFilterEditText(contactNameFilter);
        }

        public void setGroupV2(boolean groupV2) {
            this.groupV2 = groupV2;
        }
    }


    public static class GroupNameFragment extends Fragment {

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_group_creation_group_name, container, false);

            OwnedGroupDetailsFragment ownedGroupDetailsFragment = new OwnedGroupDetailsFragment();
            ownedGroupDetailsFragment.setHidePersonalDetails(true);

            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_group_details_placeholder, ownedGroupDetailsFragment);
            transaction.commit();

            return rootView;
        }
    }

}
