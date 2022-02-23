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

import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.RecyclerViewDividerDecoration;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.ContactGroupJoinDao;
import io.olvid.messenger.databases.dao.PendingGroupMemberDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.fragments.dialog.GroupMemberAdditionDialogFragment;
import io.olvid.messenger.fragments.dialog.GroupMemberSuppressionDialogFragment;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.owneddetails.EditOwnedGroupDetailsDialogFragment;
import io.olvid.messenger.viewModels.GroupDetailsViewModel;
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment;


public class GroupDetailsActivity extends LockableActivity implements View.OnClickListener, EngineNotificationListener {
    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity";
    public static final String BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA = "group_id";
    public static final String EDIT_DETAILS_INTENT_EXTRA = "edit_details";

    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";

    private GroupDetailsViewModel groupDetailsViewModel;

    private ConstraintLayout mainConstraintLayout;
    private InitialView groupInitialView;
    private TextView groupNameTextView;
    private TextView groupOwnerTextView;
    private TextView groupPersonalNoteTextView;
    private ViewGroup groupManagementButtons;
    private CardView acceptUpdateCardView;
    private CardView secondDetailsCardView;
    private LinearLayout firstDetailsTextViews;
    private LinearLayout secondDetailsTextViews;
    private InitialView firstDetailsInitialView;
    private InitialView secondDetailsInitialView;
    private TextView firstDetailsTitle;
    private TextView secondDetailsTitle;
    private LinearLayout firstDetailsButtons;
    private JsonGroupDetailsWithVersionAndPhoto latestDetails;

    private GroupMembersAdapter groupMembersAdapter;
    private PendingGroupMembersAdapter pendingGroupMembersAdapter;

    private int primary700;
    private Long registrationNumber;

    private final HashMap<BytesKey, Contact> groupMembersHashMap = new HashMap<>();
    private boolean animationsSet = false;
    private boolean groupIsOwned = false;
    private boolean showEditDetails = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        groupDetailsViewModel = new ViewModelProvider(this).get(GroupDetailsViewModel.class);

        setContentView(R.layout.activity_group_details);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mainConstraintLayout = findViewById(R.id.group_details_main_constraint_layout);

        FloatingActionButton discussionButton = findViewById(R.id.group_discussion_button);
        discussionButton.setOnClickListener(this);

        groupInitialView = findViewById(R.id.group_details_initial_view);
        groupInitialView.setOnClickListener(this);
        groupNameTextView = findViewById(R.id.group_name_text_view);
        groupOwnerTextView = findViewById(R.id.group_owner_text_view);
        groupPersonalNoteTextView = findViewById(R.id.group_personal_note_text_view);
        groupManagementButtons = findViewById(R.id.group_management_buttons);

        Button addMembersButton = findViewById(R.id.group_management_add_members_button);
        addMembersButton.setOnClickListener(this);
        Button removeMembersButton = findViewById(R.id.group_management_remove_members_button);
        removeMembersButton.setOnClickListener(this);

        // detail cards
        acceptUpdateCardView = findViewById(R.id.group_accept_update_cardview);
        Button updateButton = findViewById(R.id.button_update);
        updateButton.setOnClickListener(this);

        firstDetailsTitle = findViewById(R.id.first_details_title);
        firstDetailsTextViews = findViewById(R.id.first_details_textviews);
        firstDetailsInitialView = findViewById(R.id.first_details_initial_view);
        firstDetailsInitialView.setOnClickListener(this);
        firstDetailsButtons = findViewById(R.id.first_details_buttons);

        secondDetailsCardView = findViewById(R.id.second_details_cardview);
        secondDetailsTitle = findViewById(R.id.second_details_title);
        secondDetailsTextViews = findViewById(R.id.second_details_textviews);
        secondDetailsInitialView = findViewById(R.id.second_details_initial_view);
        secondDetailsInitialView.setOnClickListener(this);

        Button publishButton = findViewById(R.id.button_publish);
        publishButton.setOnClickListener(this);
        Button discardButton = findViewById(R.id.button_discard);
        discardButton.setOnClickListener(this);


        TextView groupMembersEmptyView = findViewById(R.id.group_members_empty_view);
        EmptyRecyclerView groupMembersRecyclerView = findViewById(R.id.group_members_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        groupMembersRecyclerView.setLayoutManager(layoutManager);
        groupMembersAdapter = new GroupMembersAdapter();
        groupMembersRecyclerView.setEmptyView(groupMembersEmptyView);
        groupMembersRecyclerView.setAdapter(groupMembersAdapter);
        groupMembersRecyclerView.addItemDecoration(new RecyclerViewDividerDecoration(this, 68, 12));

        TextView pendingGroupMembersEmptyView = findViewById(R.id.pending_group_members_empty_view);
        EmptyRecyclerView pendingGroupMembersRecyclerView = findViewById(R.id.pending_group_members_recycler_view);
        LinearLayoutManager pendingLayoutManager = new LinearLayoutManager(this);
        pendingGroupMembersRecyclerView.setLayoutManager(pendingLayoutManager);
        pendingGroupMembersAdapter = new PendingGroupMembersAdapter();
        pendingGroupMembersRecyclerView.setEmptyView(pendingGroupMembersEmptyView);
        pendingGroupMembersRecyclerView.setAdapter(pendingGroupMembersAdapter);
        pendingGroupMembersRecyclerView.addItemDecoration(new RecyclerViewDividerDecoration(this, 68, 12));

        primary700 = ContextCompat.getColor(this, R.color.primary700);

        registrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_GROUP_PHOTO, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_GROUP_PHOTO, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, this);
        final Group group = groupDetailsViewModel.getGroup().getValue();
        App.runThread(() -> {
            if (group != null && group.newPublishedDetails == Group.PUBLISHED_DETAILS_NEW_UNSEEN) {
                group.newPublishedDetails = Group.PUBLISHED_DETAILS_NEW_SEEN;
                AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
            }
        });
    }


    private void handleIntent(Intent intent) {
        byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
        byte[] bytesGroupOwnerAndUid = intent.getByteArrayExtra(BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA);
        this.showEditDetails = intent.getBooleanExtra(EDIT_DETAILS_INTENT_EXTRA, false);


        if (bytesOwnedIdentity == null || bytesGroupOwnerAndUid == null) {
            finish();
            Logger.w("Missing owned identity or group id in intent.");
            return;
        }

        if (groupDetailsViewModel.getGroup() != null) {
            groupDetailsViewModel.getGroup().removeObservers(this);
        }
        if (groupDetailsViewModel.getGroupMembers() != null) {
            groupDetailsViewModel.getGroupMembers().removeObservers(this);
        }
        if (groupDetailsViewModel.getPendingGroupMembers() != null) {
            groupDetailsViewModel.getPendingGroupMembers().removeObservers(this);
        }

        groupDetailsViewModel.setGroup(bytesOwnedIdentity, bytesGroupOwnerAndUid);
        groupDetailsViewModel.getGroup().observe(this, (Group group) -> {
            if (group != null) {
                groupMembersAdapter.showCrown(group.bytesGroupOwnerIdentity);
            }
            displayGroupDetails(group);
        });
        groupDetailsViewModel.getGroupMembers().observe(this, this::displayGroupMembersDetails);
        groupDetailsViewModel.getPendingGroupMembers().observe(this, this::displayPendingGroupMembersDetails);
        App.runThread(()-> {
            try {
                AppSingleton.getEngine().queryGroupOwnerForLatestGroupMembers(bytesGroupOwnerAndUid, bytesOwnedIdentity);
            } catch (Exception e) {
                // nothing to do, an exception is thrown when you are the owner of the group and this is normal
            }
        });
    }

    private void displayGroupDetails(@Nullable final Group group) {
        if (group == null) {
            finish();
            return;
        }
        groupIsOwned = group.bytesGroupOwnerIdentity == null;
        invalidateOptionsMenu();

        groupNameTextView.setText(group.getCustomName());
        if (group.getCustomPhotoUrl() != null) {
            groupInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, group.getCustomPhotoUrl());
        } else {
            groupInitialView.setGroup(group.bytesGroupOwnerAndUid);
        }
        if (groupIsOwned) {
            groupOwnerTextView.setVisibility(View.GONE);
            groupPersonalNoteTextView.setVisibility(View.GONE);
            groupManagementButtons.setVisibility(View.VISIBLE);
        } else {
            groupOwnerTextView.setVisibility(View.VISIBLE);
            groupManagementButtons.setVisibility(View.GONE);
            setGroupOwnerText(group.bytesGroupOwnerIdentity);
            if (group.personalNote != null) {
                groupPersonalNoteTextView.setVisibility(View.VISIBLE);
                groupPersonalNoteTextView.setText(group.personalNote);
            } else {
                groupPersonalNoteTextView.setVisibility(View.GONE);
            }
        }

        try {
            JsonGroupDetailsWithVersionAndPhoto[] jsons = AppSingleton.getEngine().getGroupPublishedAndLatestOrTrustedDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            if (jsons == null || jsons.length == 0) {
                return;
            }

            JsonGroupDetailsWithVersionAndPhoto publishedDetails;
            if (jsons.length == 1) {
                acceptUpdateCardView.setVisibility(View.GONE);
                secondDetailsCardView.setVisibility(View.GONE);
                firstDetailsButtons.setVisibility(View.GONE);

                firstDetailsTitle.setText(R.string.label_group_card);
                firstDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title));

                firstDetailsTextViews.removeAllViews();
                publishedDetails = jsons[0];
                latestDetails = publishedDetails;
                if (publishedDetails.getPhotoUrl() != null) {
                    firstDetailsInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, publishedDetails.getPhotoUrl());
                } else {
                    firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid);
                }

                {
                    TextView tv = getTextView();
                    tv.setText(publishedDetails.getGroupDetails().getName());
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    firstDetailsTextViews.addView(tv);
                }
                if (publishedDetails.getGroupDetails().getDescription() != null && publishedDetails.getGroupDetails().getDescription().length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(publishedDetails.getGroupDetails().getDescription());
                    firstDetailsTextViews.addView(tv);
                }

                App.runThread(() -> {
                    if (group.newPublishedDetails != Group.PUBLISHED_DETAILS_NOTHING_NEW) {
                        group.newPublishedDetails = Group.PUBLISHED_DETAILS_NOTHING_NEW;
                        AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
                    }
                    if ((group.photoUrl == null && jsons[0].getPhotoUrl() != null) ||
                            (group.photoUrl != null && !group.photoUrl.equals(jsons[0].getPhotoUrl()))) {
                        group.photoUrl = jsons[0].getPhotoUrl();
                        AppDatabase.getInstance().groupDao().updatePhotoUrl(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.photoUrl);
                    }
                });
            } else {
                secondDetailsCardView.setVisibility(View.VISIBLE);
                firstDetailsTextViews.removeAllViews();
                secondDetailsTextViews.removeAllViews();

                if (group.bytesGroupOwnerIdentity == null) {
                    firstDetailsTitle.setText(R.string.label_group_card_unpublished_draft);
                    firstDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title_new));

                    secondDetailsTitle.setText(R.string.label_group_card_published);

                    acceptUpdateCardView.setVisibility(View.GONE);
                    firstDetailsButtons.setVisibility(View.VISIBLE);

                    publishedDetails = jsons[0];
                    if (publishedDetails.getPhotoUrl() != null) {
                        secondDetailsInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, publishedDetails.getPhotoUrl());
                    } else {
                        secondDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid);
                    }

                    {
                        TextView tv = getTextView();
                        tv.setText(publishedDetails.getGroupDetails().getName());
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        secondDetailsTextViews.addView(tv);
                    }
                    if (publishedDetails.getGroupDetails().getDescription() != null && publishedDetails.getGroupDetails().getDescription().length() > 0) {
                        TextView tv = getTextView();
                        tv.setText(publishedDetails.getGroupDetails().getDescription());
                        secondDetailsTextViews.addView(tv);
                    }

                    latestDetails = jsons[1];
                    if (latestDetails.getPhotoUrl() != null) {
                        firstDetailsInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, latestDetails.getPhotoUrl());
                    } else {
                        firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid);
                    }

                    {
                        TextView tv = getTextView();
                        tv.setText(latestDetails.getGroupDetails().getName());
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        if (!latestDetails.getGroupDetails().getName().equals(publishedDetails.getGroupDetails().getName())) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        firstDetailsTextViews.addView(tv);
                    }
                    if (latestDetails.getGroupDetails().getDescription() != null && latestDetails.getGroupDetails().getDescription().length() > 0) {
                        TextView tv = getTextView();
                        tv.setText(latestDetails.getGroupDetails().getDescription());
                        if (!latestDetails.getGroupDetails().getDescription().equals(publishedDetails.getGroupDetails().getDescription())) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        firstDetailsTextViews.addView(tv);
                    }

                    App.runThread(() -> {
                        if (group.newPublishedDetails != Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW) {
                            group.newPublishedDetails = Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW;
                            AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
                        }
                        if ((group.photoUrl == null && jsons[0].getPhotoUrl() != null) ||
                                (group.photoUrl != null && !group.photoUrl.equals(jsons[0].getPhotoUrl()))) {
                            group.photoUrl = jsons[0].getPhotoUrl();
                            AppDatabase.getInstance().groupDao().updatePhotoUrl(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.photoUrl);
                        }
                    });
                } else {
                    firstDetailsTitle.setText(R.string.label_group_card_published_update);
                    firstDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title_new));

                    secondDetailsTitle.setText(R.string.label_group_card);

                    acceptUpdateCardView.setVisibility(View.VISIBLE);
                    firstDetailsButtons.setVisibility(View.GONE);

                    JsonGroupDetailsWithVersionAndPhoto trustedDetails = jsons[1];
                    if (trustedDetails.getPhotoUrl() != null) {
                        secondDetailsInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, trustedDetails.getPhotoUrl());
                    } else {
                        secondDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid);
                    }

                    {
                        TextView tv = getTextView();
                        tv.setText(trustedDetails.getGroupDetails().getName());
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        secondDetailsTextViews.addView(tv);
                    }
                    if (trustedDetails.getGroupDetails().getDescription() != null && trustedDetails.getGroupDetails().getDescription().length() > 0) {
                        TextView tv = getTextView();
                        tv.setText(trustedDetails.getGroupDetails().getDescription());
                        secondDetailsTextViews.addView(tv);
                    }

                    publishedDetails = jsons[0];
                    if (publishedDetails.getPhotoUrl() != null) {
                        firstDetailsInitialView.setPhotoUrl(group.bytesGroupOwnerAndUid, publishedDetails.getPhotoUrl());
                    } else {
                        firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid);
                    }

                    {
                        TextView tv = getTextView();
                        tv.setText(publishedDetails.getGroupDetails().getName());
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        if (!publishedDetails.getGroupDetails().getName().equals(trustedDetails.getGroupDetails().getName())) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        firstDetailsTextViews.addView(tv);
                    }
                    if (publishedDetails.getGroupDetails().getDescription() != null && publishedDetails.getGroupDetails().getDescription().length() > 0) {
                        TextView tv = getTextView();
                        tv.setText(publishedDetails.getGroupDetails().getDescription());
                        if (!publishedDetails.getGroupDetails().getDescription().equals(trustedDetails.getGroupDetails().getDescription())) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        firstDetailsTextViews.addView(tv);
                    }

                    App.runThread(() -> {
                        if (group.newPublishedDetails == Group.PUBLISHED_DETAILS_NOTHING_NEW) {
                            group.newPublishedDetails = Group.PUBLISHED_DETAILS_NEW_SEEN;
                            AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
                        }
                        if ((group.photoUrl == null && jsons[1].getPhotoUrl() != null) ||
                                (group.photoUrl != null && !group.photoUrl.equals(jsons[1].getPhotoUrl()))) {
                            group.photoUrl = jsons[1].getPhotoUrl();
                            AppDatabase.getInstance().groupDao().updatePhotoUrl(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.photoUrl);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (!animationsSet) {
            mainConstraintLayout.setLayoutTransition(new LayoutTransition());
            animationsSet = true;
        }
    }

    private TextView getTextView() {
        TextView tv = new AppCompatTextView(this);
        tv.setTextColor(primary700);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.identity_details_margin));
        tv.setLayoutParams(params);
        return tv;
    }

    private void displayGroupMembersDetails(@Nullable List<ContactGroupJoinDao.ContactAndTimestamp> contacts) {
        groupMembersAdapter.setContacts(contacts);
        if (contacts != null) {
            groupMembersHashMap.clear();
            for (ContactGroupJoinDao.ContactAndTimestamp contact : contacts) {
                groupMembersHashMap.put(new BytesKey(contact.contact.bytesContactIdentity), contact.contact);
            }
            if (groupDetailsViewModel.getGroup() != null) {
                Group group = groupDetailsViewModel.getGroup().getValue();
                if (group != null && group.bytesGroupOwnerIdentity != null) {
                    setGroupOwnerText(group.bytesGroupOwnerIdentity);
                }
            }
        }
    }

    private void displayPendingGroupMembersDetails(@Nullable List<PendingGroupMemberDao.PendingGroupMemberAndContact> pendingGroupMembers) {
        pendingGroupMembersAdapter.setPendingGroupMembers(pendingGroupMembers);
    }

    private void setGroupOwnerText(byte[] bytesGroupOwnerIdentity) {
        BytesKey key = new BytesKey(bytesGroupOwnerIdentity);
        if (groupMembersHashMap.containsKey(key)) {
            Contact groupOwner = groupMembersHashMap.get(key);
            if (groupOwner != null) {
                groupOwnerTextView.setText(getString(R.string.text_group_managed_by, groupOwner.getCustomDisplayName()));
            }
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.NEW_GROUP_PHOTO: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY);
                //noinspection ConstantConditions
                boolean isTrusted = (boolean) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_IS_TRUSTED_KEY);

                final Group group = groupDetailsViewModel.getGroup().getValue();
                if (!isTrusted
                        && group != null
                        && Arrays.equals(group.bytesGroupOwnerAndUid, bytesGroupUid)
                        && Arrays.equals(group.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    runOnUiThread(() -> displayGroupDetails(group));
                }
                break;
            }
            case EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY);
                final Group group = groupDetailsViewModel.getGroup().getValue();

                if (group != null
                        && Arrays.equals(group.bytesGroupOwnerAndUid, bytesGroupUid)
                        && Arrays.equals(group.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    runOnUiThread(() -> displayGroupDetails(group));
                }
                break;
            }
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

    @Override
    public void onClick(View view) {
        if (groupDetailsViewModel.getGroup() == null) {
            return;
        }
        final Group group = groupDetailsViewModel.getGroup().getValue();
        if (group == null) {
            return;
        }

        int id = view.getId();
        if (id == R.id.group_discussion_button) {
            App.openGroupDiscussionActivity(this, group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
        } else if (id == R.id.button_update) {
            AppSingleton.getEngine().trustPublishedGroupDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
        } else if (id == R.id.button_publish) {
            AppSingleton.getEngine().publishLatestGroupDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
        } else if (id == R.id.button_discard) {
            AppSingleton.getEngine().discardLatestGroupDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            displayGroupDetails(group);
        } else if (id == R.id.group_management_add_members_button) {
            GroupMemberAdditionDialogFragment groupMemberAdditionDialogFragment = GroupMemberAdditionDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            groupMemberAdditionDialogFragment.show(getSupportFragmentManager(), "dialog");
        } else if (id == R.id.group_management_remove_members_button) {
            GroupMemberSuppressionDialogFragment groupMemberSuppressionDialogFragment = GroupMemberSuppressionDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            groupMemberSuppressionDialogFragment.show(getSupportFragmentManager(), "dialog");
        } else if (view instanceof InitialView) {
            String photoUrl = ((InitialView) view).getPhotoUrl();
            if (photoUrl != null) {
                FullScreenImageFragment fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl);
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, 0)
                        .replace(R.id.overlay, fullScreenImageFragment, FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                        .commit();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
            if (fullScreenImageFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(fullScreenImageFragment)
                        .commit();
            }
        }
        return super.dispatchTouchEvent(event);
    }


    @Override
    public void onBackPressed() {
        Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
        if (fullScreenImageFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit();
        } else {
            if (isTaskRoot()) {
                App.showMainActivityTab(this, MainActivity.GROUPS_TAB);
            }
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        if (groupIsOwned) {
            getMenuInflater().inflate(R.menu.menu_group_details_owned, menu);
            MenuItem deleteItem = menu.findItem(R.id.action_disband);
            if (deleteItem != null) {
                SpannableString spannableString = new SpannableString(deleteItem.getTitle());
                spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                deleteItem.setTitle(spannableString);
            }
        } else {
            getMenuInflater().inflate(R.menu.menu_group_details_joined, menu);
            MenuItem deleteItem = menu.findItem(R.id.action_leave_group);
            if (deleteItem != null) {
                SpannableString spannableString = new SpannableString(deleteItem.getTitle());
                spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                deleteItem.setTitle(spannableString);
            }
        }
        if (showEditDetails) {
            showEditDetails = false;
            menu.performIdentifierAction(R.id.action_rename, 0);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_call) {
            if (groupDetailsViewModel.getGroup() == null) {
                return true;
            }
            final Group group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            List<ContactGroupJoinDao.ContactAndTimestamp> contactAndTimestamps = groupDetailsViewModel.getGroupMembers().getValue();
            if (contactAndTimestamps != null) {
                List<byte[]> bytesContactIdentities = new ArrayList<>(contactAndTimestamps.size());
                for (ContactGroupJoinDao.ContactAndTimestamp contactAndTimestamp : contactAndTimestamps) {
                    bytesContactIdentities.add(contactAndTimestamp.contact.bytesContactIdentity);
                }
                MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, bytesContactIdentities);
                multiCallStartDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.action_rename) {
            if (groupDetailsViewModel.getGroup() == null) {
                return true;
            }
            final Group group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }

            if (group.bytesGroupOwnerIdentity == null) {
                EditOwnedGroupDetailsDialogFragment dialogFragment = EditOwnedGroupDetailsDialogFragment.newInstance(this, group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, latestDetails, () -> displayGroupDetails(group));
                dialogFragment.show(getSupportFragmentManager(), "dialog");
            } else {
                EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(this, group);
                editNameAndPhotoDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.action_disband) {
            if (groupDetailsViewModel.getGroup() == null) {
                return true;
            }
            final Group group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            if (groupIsOwned) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_disband_group)
                        .setMessage(getString(R.string.dialog_message_disband_group, group.getCustomName()))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            if ((groupDetailsViewModel.getGroupMembers().getValue() == null || groupDetailsViewModel.getGroupMembers().getValue().size() == 0) &&
                                    (groupDetailsViewModel.getPendingGroupMembers().getValue() == null || groupDetailsViewModel.getPendingGroupMembers().getValue().size() == 0)) {
                                // group is empty, just delete it
                                try {
                                    AppSingleton.getEngine().disbandGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                    App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                    onBackPressed();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                // group is not empty, second confirmation
                                final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(GroupDetailsActivity.this, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_disband_group)
                                        .setMessage(getString(R.string.dialog_message_disband_non_empty_group_confirmation,
                                                group.getCustomName(),
                                                groupDetailsViewModel.getGroupMembers().getValue() == null ? 0 : groupDetailsViewModel.getGroupMembers().getValue().size(),
                                                groupDetailsViewModel.getPendingGroupMembers().getValue() == null ? 0 : groupDetailsViewModel.getPendingGroupMembers().getValue().size()))
                                        .setPositiveButton(R.string.button_label_ok, (dialog12, which1) -> {
                                            // delete group
                                            try {
                                                AppSingleton.getEngine().disbandGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                                App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                                onBackPressed();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        })
                                        .setNegativeButton(R.string.button_label_cancel, null);
                                confirmationBuilder.create().show();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_leave_group) {
            if (groupDetailsViewModel.getGroup() == null) {
                return true;
            }
            final Group group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            // you can only leave groups you do not own
            if (group.bytesGroupOwnerIdentity != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_leave_group)
                        .setMessage(getString(R.string.dialog_message_leave_group, group.getCustomName()))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            try {
                                AppSingleton.getEngine().leaveGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                App.toast(R.string.toast_message_leaving_group, Toast.LENGTH_SHORT);
                                onBackPressed();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.GroupMemberViewHolder> {
        private final LayoutInflater inflater;
        private List<ContactGroupJoinDao.ContactAndTimestamp> contacts;
        private byte[] byteGroupOwnerIdentity = null;

        @SuppressLint("NotifyDataSetChanged")
        public void setContacts(List<ContactGroupJoinDao.ContactAndTimestamp> contacts) {
            this.contacts = contacts;
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void showCrown(byte[] bytesGroupOwnerIdentity) {
            this.byteGroupOwnerIdentity = bytesGroupOwnerIdentity;
            notifyDataSetChanged();
        }

        GroupMembersAdapter() {
            this.inflater = LayoutInflater.from(GroupDetailsActivity.this);
        }

        @NonNull
        @Override
        public GroupMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_group_member, parent, false);
            return new GroupMemberViewHolder(view);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull GroupMemberViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.shouldAnimateChannelImageView) {
                Drawable drawable = holder.groupMemberEstablishingChannelImageView.getDrawable();
                if (drawable instanceof Animatable) {
                    ((Animatable) drawable).start();
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final GroupMemberViewHolder holder, int position) {
            if (contacts != null) {
                ContactGroupJoinDao.ContactAndTimestamp contact = contacts.get(position);
                holder.groupMemberNameTextView.setText(contact.contact.getCustomDisplayName());
                holder.groupMemberJoinTimestampTextView.setText(getString(R.string.text_joined_group, App.getNiceDateString(GroupDetailsActivity.this, contact.timestamp)));
                holder.groupMemberInitialView.setKeycloakCertified(contact.contact.keycloakManaged);
                holder.groupMemberInitialView.setInactive(!contact.contact.active);
                if (contact.contact.getCustomPhotoUrl() != null) {
                    holder.groupMemberInitialView.setPhotoUrl(contact.contact.bytesContactIdentity, contact.contact.getCustomPhotoUrl());
                } else {
                    holder.groupMemberInitialView.setInitial(contact.contact.bytesContactIdentity, App.getInitial(contact.contact.getCustomDisplayName()));
                }
                if (Arrays.equals(contact.contact.bytesContactIdentity, byteGroupOwnerIdentity)) {
                    holder.groupMemberOwnerCrownImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.groupMemberOwnerCrownImageView.setVisibility(View.GONE);
                }
                switch (contact.contact.newPublishedDetails) {
                    case Contact.PUBLISHED_DETAILS_NOTHING_NEW:
                        holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                        break;
                    case Contact.PUBLISHED_DETAILS_NEW_SEEN:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.GONE);
                        break;
                    case Contact.PUBLISHED_DETAILS_NEW_UNSEEN:
                        holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                        holder.newUnseenPublishedDetailsDot.setVisibility(View.VISIBLE);
                        break;
                }
                if (contact.contact.establishedChannelCount == 0 && contact.contact.active) {
                    holder.shouldAnimateChannelImageView = true;
                    holder.groupMemberEstablishingChannelGroup.setVisibility(View.VISIBLE);
                    final AnimatedVectorDrawableCompat animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots);
                    if (animated != null) {
                        animated.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                            @Override
                            public void onAnimationEnd(Drawable drawable) {
                                new Handler(Looper.getMainLooper()).post(animated::start);
                            }
                        });
                        animated.start();
                    }
                    holder.groupMemberEstablishingChannelImageView.setImageDrawable(animated);
                } else {
                    holder.shouldAnimateChannelImageView = false;
                    holder.groupMemberEstablishingChannelGroup.setVisibility(View.GONE);
                    holder.groupMemberEstablishingChannelImageView.setImageDrawable(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            if (contacts != null) {
                return contacts.size();
            }
            return 0;
        }


        class GroupMemberViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final TextView groupMemberNameTextView;
            final TextView groupMemberJoinTimestampTextView;
            final InitialView groupMemberInitialView;
            final ViewGroup groupMemberEstablishingChannelGroup;
            final ImageView groupMemberEstablishingChannelImageView;
            final ImageView groupMemberOwnerCrownImageView;
            final ViewGroup newPublishedDetailsGroup;
            final ImageView newUnseenPublishedDetailsDot;
            boolean shouldAnimateChannelImageView;

            GroupMemberViewHolder(@NonNull View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                shouldAnimateChannelImageView = false;
                groupMemberNameTextView = itemView.findViewById(R.id.group_member_name_text_view);
                groupMemberJoinTimestampTextView = itemView.findViewById(R.id.group_member_join_timestamp_text_view);
                groupMemberInitialView = itemView.findViewById(R.id.group_member_initial_view);
                groupMemberOwnerCrownImageView = itemView.findViewById(R.id.group_member_owner_crown_image_view);
                groupMemberEstablishingChannelGroup = itemView.findViewById(R.id.group_member_establishing_channel_group);
                groupMemberEstablishingChannelImageView = itemView.findViewById(R.id.group_member_establishing_channel_image_view);
                newPublishedDetailsGroup = itemView.findViewById(R.id.new_published_details_group);
                newUnseenPublishedDetailsDot = itemView.findViewById(R.id.new_unseen_published_details_dot);
            }

            @Override
            public void onClick(View view) {
                int position = this.getLayoutPosition();
                GroupDetailsActivity.this.groupMemberClicked(contacts.get(position).contact);
            }
        }
    }

    private void groupMemberClicked(Contact contact) {
        App.openContactDetailsActivity(this, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
    }

    class PendingGroupMembersAdapter extends RecyclerView.Adapter<PendingGroupMembersAdapter.PendingGroupMemberViewHolder> {
        private final LayoutInflater inflater;
        private List<PendingGroupMemberDao.PendingGroupMemberAndContact> pendingGroupMembers;

        @SuppressLint("NotifyDataSetChanged")
        public void setPendingGroupMembers(List<PendingGroupMemberDao.PendingGroupMemberAndContact> pendingGroupMembers) {
            this.pendingGroupMembers = pendingGroupMembers;
            notifyDataSetChanged();
        }

        PendingGroupMembersAdapter() {
            this.inflater = LayoutInflater.from(GroupDetailsActivity.this);
        }

        @NonNull
        @Override
        public PendingGroupMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_pending_group_member, parent, false);
            return new PendingGroupMemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final PendingGroupMemberViewHolder holder, int position) {
            if (pendingGroupMembers != null) {
                PendingGroupMemberDao.PendingGroupMemberAndContact pendingGroupMember = pendingGroupMembers.get(position);
                if (pendingGroupMember.contact == null) {
                    holder.pendingGroupMemberNameTextView.setText(pendingGroupMember.pendingGroupMember.displayName);
                    holder.pendingGroupMemberInitialView.setInitial(new byte[0], App.getInitial(pendingGroupMember.pendingGroupMember.displayName));
                } else {
                    holder.pendingGroupMemberNameTextView.setText(pendingGroupMember.contact.getCustomDisplayName());
                    if (pendingGroupMember.contact.getCustomPhotoUrl() != null) {
                        holder.pendingGroupMemberInitialView.setPhotoUrl(pendingGroupMember.contact.bytesContactIdentity, pendingGroupMember.contact.getCustomPhotoUrl());
                    } else {
                        holder.pendingGroupMemberInitialView.setInitial(pendingGroupMember.contact.bytesContactIdentity, App.getInitial(pendingGroupMember.contact.getCustomDisplayName()));
                    }
                }
                holder.invitationDeclinedTextView.setVisibility(pendingGroupMember.pendingGroupMember.declined? View.VISIBLE: View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            if (pendingGroupMembers != null) {
                return pendingGroupMembers.size();
            }
            return 0;
        }

        class PendingGroupMemberViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final TextView pendingGroupMemberNameTextView;
            final InitialView pendingGroupMemberInitialView;
            final TextView invitationDeclinedTextView;

            PendingGroupMemberViewHolder(@NonNull View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                pendingGroupMemberNameTextView = itemView.findViewById(R.id.pending_group_member_name_text_view);
                pendingGroupMemberInitialView = itemView.findViewById(R.id.pending_group_member_initial_view);
                invitationDeclinedTextView = itemView.findViewById(R.id.invitation_declined_textview);
            }

            @Override
            public void onClick(View view) {
                if (!groupIsOwned || groupDetailsViewModel.getGroup().getValue() == null) {
                    return;
                }
                int position = this.getLayoutPosition();
                final PendingGroupMemberDao.PendingGroupMemberAndContact pendingGroupMemberAndContact = pendingGroupMembers.get(position);
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(view.getContext(), R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_group_reinvite)
                        .setMessage(getString(R.string.dialog_message_group_reinvite, pendingGroupMemberAndContact.pendingGroupMember.displayName, groupDetailsViewModel.getGroup().getValue().getCustomName()))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            try {
                                AppSingleton.getEngine().reinvitePendingToGroup(pendingGroupMemberAndContact.pendingGroupMember.bytesOwnedIdentity, pendingGroupMemberAndContact.pendingGroupMember.bytesGroupOwnerAndUid, pendingGroupMemberAndContact.pendingGroupMember.bytesIdentity);
                                App.toast(R.string.toast_message_invite_sent, Toast.LENGTH_SHORT);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
        }
    }
}
