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

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.tasks.GroupCloningTasks;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.fragments.dialog.GroupV2MemberAdditionDialogFragment;
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment;
import io.olvid.messenger.owneddetails.EditOwnedGroupDetailsDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.GroupV2DetailsViewModel;

public class GroupV2DetailsActivity extends LockableActivity implements EngineNotificationListener, View.OnClickListener {
    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity";
    public static final String BYTES_GROUP_IDENTIFIER_INTENT_EXTRA = "group_identifier";
    public static final String EDIT_DETAILS_INTENT_EXTRA = "edit_details";

    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";

    public static final String[] NOTIFICATIONS_TO_LISTEN_TO = new String[] {
        EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
        EngineNotifications.GROUP_V2_PHOTO_CHANGED,
        EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED,
        EngineNotifications.GROUP_V2_UPDATE_FAILED,
    };

    private GroupV2DetailsViewModel groupDetailsViewModel;

    private ConstraintLayout mainConstraintLayout;
    private LinearLayout groupMembersListLinearLayout;
    private CardView updateInProgressCard;
    private TextView updateInProgressTitleTextView;
    private ImageView groupAdminImageView;
    private InitialView groupInitialView;
    private TextView groupNameTextView;
    private TextView groupPersonalNoteTextView;
    private View acceptUpdateGroup;
    private CardView secondDetailsCardView;
    private InitialView firstDetailsInitialView;
    private LinearLayout firstDetailsTextViews;
    private TextView firstDetailsTitle;
    private InitialView secondDetailsInitialView;
    private LinearLayout secondDetailsTextViews;
    private Button editGroupButton;
    private Button saveButton;
    private Button discardButton;
    private Button addGroupMembersButton;
    private FloatingActionButton discussionButton;
    private View publishingOpacityMask;

    private int primary700;
    private boolean showEditDetails = false;
    private boolean animationsSet = false;
    private boolean groupAdmin = false;
    private boolean editingMembers = false;
    private int updateInProgress = Group2.UPDATE_NONE;

    private JsonGroupDetails publishedDetails = null;
    private String publishedPhotoUrl = null;

    private GroupMembersAdapter groupMembersAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        groupDetailsViewModel = new ViewModelProvider(this).get(GroupV2DetailsViewModel.class);

        setContentView(R.layout.activity_group_v2_details);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mainConstraintLayout = findViewById(R.id.group_details_main_constraint_layout);
        groupMembersListLinearLayout = findViewById(R.id.group_members_list);

        updateInProgressCard = findViewById(R.id.update_in_progress_card);
        updateInProgressTitleTextView = findViewById(R.id.update_in_progress_title);

        discussionButton = findViewById(R.id.group_discussion_button);
        discussionButton.setOnClickListener(this);

        publishingOpacityMask = findViewById(R.id.publishing_opacity_mask);

        groupAdminImageView = findViewById(R.id.admin_indicator_image_view);
        groupInitialView = findViewById(R.id.initial_view);
        groupInitialView.setOnClickListener(this);
        groupNameTextView = findViewById(R.id.group_name_text_view);
        groupPersonalNoteTextView = findViewById(R.id.group_personal_note_text_view);

        // detail cards
        acceptUpdateGroup = findViewById(R.id.update_details_group);
        Button updateButton = findViewById(R.id.button_update);
        updateButton.setOnClickListener(this);

        firstDetailsTitle = findViewById(R.id.first_details_title);
        firstDetailsTextViews = findViewById(R.id.first_details_textviews);
        firstDetailsInitialView = findViewById(R.id.first_details_initial_view);
        firstDetailsInitialView.setOnClickListener(this);

        secondDetailsCardView = findViewById(R.id.second_details_cardview);
        secondDetailsTextViews = findViewById(R.id.second_details_textviews);
        secondDetailsInitialView = findViewById(R.id.second_details_initial_view);
        secondDetailsInitialView.setOnClickListener(this);

        editGroupButton = findViewById(R.id.button_edit_group);
        editGroupButton.setOnClickListener(this);
        discardButton = findViewById(R.id.button_discard);
        discardButton.setOnClickListener(this);
        saveButton = findViewById(R.id.button_save);
        saveButton.setOnClickListener(this);
        addGroupMembersButton = findViewById(R.id.button_add_members);
        addGroupMembersButton.setOnClickListener(this);

        TextView groupMembersEmptyView = findViewById(R.id.group_members_empty_view);
        EmptyRecyclerView groupMembersRecyclerView = findViewById(R.id.group_members_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        groupMembersRecyclerView.setLayoutManager(layoutManager);
        groupMembersAdapter = new GroupMembersAdapter();
        groupMembersRecyclerView.setEmptyView(groupMembersEmptyView);
        groupMembersRecyclerView.setAdapter(groupMembersAdapter);
        groupMembersRecyclerView.addItemDecoration(new ItemDecorationSimpleDivider(this, 68, 12));

        new ItemTouchHelper(new SwipeCallback()).attachToRecyclerView(groupMembersRecyclerView);

        primary700 = ContextCompat.getColor(this, R.color.primary700);

        registrationNumber = null;
        for (String notification : NOTIFICATIONS_TO_LISTEN_TO) {
            AppSingleton.getEngine().addNotificationListener(notification, this);
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (String notification : NOTIFICATIONS_TO_LISTEN_TO) {
            AppSingleton.getEngine().removeNotificationListener(notification, this);
        }
        final Group2 group = groupDetailsViewModel.getGroup().getValue();
        App.runThread(() -> {
            if (group != null && group.newPublishedDetails == Group2.PUBLISHED_DETAILS_NEW_UNSEEN) {
                group.newPublishedDetails = Group2.PUBLISHED_DETAILS_NEW_SEEN;
                AppDatabase.getInstance().group2Dao().updateNewPublishedDetails(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.newPublishedDetails);
            }
        });
    }

    private void handleIntent(Intent intent) {
        byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
        byte[] bytesGroupIdentifier = intent.getByteArrayExtra(BYTES_GROUP_IDENTIFIER_INTENT_EXTRA);
        this.showEditDetails = intent.getBooleanExtra(EDIT_DETAILS_INTENT_EXTRA, false);

        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            finish();
            Logger.w("GroupV2DetailsActivity Missing owned identity or group identifier in intent.");
            return;
        }

        groupDetailsViewModel.setGroup(bytesOwnedIdentity, bytesGroupIdentifier);

        groupDetailsViewModel.getGroup().observe(this, this::displayGroup);
        groupDetailsViewModel.getGroupMembers().observe(this, groupMembersAdapter);
        groupDetailsViewModel.isEditingGroupMembersLiveData().observe(this, this::editingMembersChanged);

        fetchEngineGroupCards();
    }


    private void displayGroup(Group2 group) {
        if (group == null) {
            finish();
            return;
        }

        if (updateInProgress != group.updateInProgress) {
            updateInProgress = group.updateInProgress;
            if (updateInProgress != Group2.UPDATE_NONE) {
                if (editingMembers) {
                    publishingOpacityMask.setVisibility(View.VISIBLE);
                } else {
                    publishingOpacityMask.setVisibility(View.GONE);
                }
                editGroupButton.setEnabled(false);
                saveButton.setEnabled(false);
                discardButton.setEnabled(false);
                addGroupMembersButton.setEnabled(false);
                updateInProgressCard.setVisibility(View.VISIBLE);
                if (updateInProgress == Group2.UPDATE_SYNCING) {
                    updateInProgressTitleTextView.setText(R.string.label_group_update_in_progress_title);
                } else {
                    updateInProgressTitleTextView.setText(R.string.label_group_update_in_progress_title_for_creation);
                }
            } else {
                publishingOpacityMask.setVisibility(View.GONE);
                editGroupButton.setEnabled(true);
                saveButton.setEnabled(true);
                discardButton.setEnabled(true);
                addGroupMembersButton.setEnabled(true);
                updateInProgressCard.setVisibility(View.GONE);
            }
        }

        if (groupAdmin != group.ownPermissionAdmin) {
            groupAdmin = group.ownPermissionAdmin;
            invalidateOptionsMenu();
        }

        groupInitialView.setGroup2(group);
        String name = group.getTruncatedCustomName();
        if (name.length() == 0) {
            SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_group));
            spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            groupNameTextView.setText(spannableString);
        } else {
            groupNameTextView.setText(name);
        }

        if (group.personalNote != null) {
            groupPersonalNoteTextView.setVisibility(View.VISIBLE);
            groupPersonalNoteTextView.setText(group.personalNote);
        } else {
            groupPersonalNoteTextView.setVisibility(View.GONE);
        }

        if (groupAdmin) {
            groupAdminImageView.setVisibility(View.VISIBLE);
            if (editingMembers) {
                editGroupButton.setVisibility(View.GONE);
                addGroupMembersButton.setVisibility(View.VISIBLE);
                saveButton.setVisibility(View.VISIBLE);
                discardButton.setVisibility(View.VISIBLE);
            } else {
                editGroupButton.setVisibility(View.VISIBLE);
                addGroupMembersButton.setVisibility(View.GONE);
                saveButton.setVisibility(View.GONE);
                discardButton.setVisibility(View.GONE);
            }
        } else {
            groupAdminImageView.setVisibility(View.GONE);
            if (editingMembers) {
                // if we lose admin while editing, discard all changes
                onClick(discardButton);
            }
            editGroupButton.setVisibility(View.GONE);
            addGroupMembersButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            discardButton.setVisibility(View.GONE);
        }
    }

    private void editingMembersChanged(Boolean editingMembers) {
        this.editingMembers = editingMembers != null && editingMembers;
        if (this.editingMembers) {
            discussionButton.hide();
            if (updateInProgress != Group2.UPDATE_NONE) {
                publishingOpacityMask.setVisibility(View.VISIBLE);
            } else {
                publishingOpacityMask.setVisibility(View.GONE);
            }
            editGroupButton.setVisibility(View.GONE);
            addGroupMembersButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            discardButton.setVisibility(View.VISIBLE);
        } else {
            discussionButton.show();
            publishingOpacityMask.setVisibility(View.GONE);
            if (groupAdmin) {
                editGroupButton.setVisibility(View.VISIBLE);
            } else {
                editGroupButton.setVisibility(View.GONE);
            }
            addGroupMembersButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            discardButton.setVisibility(View.GONE);
        }
    }

    private void fetchEngineGroupCards() {
        byte[] bytesOwnedIdentity = groupDetailsViewModel.getBytesOwnedIdentity();
        byte[] bytesGroupIdentifier = groupDetailsViewModel.getBytesGroupIdentifier();
        App.runThread(() -> {
            ObvGroupV2.ObvGroupV2DetailsAndPhotos detailsAndPhotos = AppSingleton.getEngine().getGroupV2DetailsAndPhotos(bytesOwnedIdentity, bytesGroupIdentifier);
            runOnUiThread(() -> displayEngineGroupCards(detailsAndPhotos));
        });
    }

    private void displayEngineGroupCards(ObvGroupV2.ObvGroupV2DetailsAndPhotos detailsAndPhotos) {
        byte[] bytesGroupIdentifier = groupDetailsViewModel.getBytesGroupIdentifier();

        if (detailsAndPhotos.serializedPublishedDetails == null) {
            acceptUpdateGroup.setVisibility(View.GONE);
            secondDetailsCardView.setVisibility(View.GONE);

            firstDetailsTitle.setText(R.string.label_group_card);
            firstDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title));

            firstDetailsTextViews.removeAllViews();

            try {
                JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                publishedDetails = groupDetails;
                publishedPhotoUrl = detailsAndPhotos.getNullIfEmptyPhotoUrl();

                if (publishedPhotoUrl != null) {
                    firstDetailsInitialView.setPhotoUrl(bytesGroupIdentifier, publishedPhotoUrl);
                } else {
                    firstDetailsInitialView.setGroup(bytesGroupIdentifier);
                }

                {
                    TextView tv = getTextView();
                    if (groupDetails.getName() != null && groupDetails.getName().length() > 0) {
                        tv.setText(groupDetails.getName());
                    } else {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_group));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    firstDetailsTextViews.addView(tv);
                }

                if (groupDetails.getDescription() != null && groupDetails.getDescription().length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(groupDetails.getDescription());
                    firstDetailsTextViews.addView(tv);
                }
            } catch (Exception e) {
                firstDetailsTextViews.removeAllViews();
                firstDetailsInitialView.setUnknown();
                TextView tv = getTextView();
                SpannableString spannableString = new SpannableString(getString(R.string.text_unable_to_display_contact_name));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(spannableString);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                firstDetailsTextViews.addView(tv);
            }
        } else {
            acceptUpdateGroup.setVisibility(View.VISIBLE);
            secondDetailsCardView.setVisibility(View.VISIBLE);

            firstDetailsTitle.setText(R.string.label_group_card_published_update);
            firstDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title_new));

            firstDetailsTextViews.removeAllViews();
            secondDetailsTextViews.removeAllViews();

            try {
                JsonGroupDetails publishedGroupDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedPublishedDetails, JsonGroupDetails.class);
                publishedDetails = publishedGroupDetails;
                publishedPhotoUrl = detailsAndPhotos.getNullIfEmptyPublishedPhotoUrl();

                if (publishedPhotoUrl != null) {
                    firstDetailsInitialView.setPhotoUrl(bytesGroupIdentifier, publishedPhotoUrl);
                } else {
                    firstDetailsInitialView.setGroup(bytesGroupIdentifier);
                }

                {
                    TextView tv = getTextView();
                    if (publishedGroupDetails.getName() != null && publishedGroupDetails.getName().length() > 0) {
                        tv.setText(publishedGroupDetails.getName());
                    } else {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_group));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    firstDetailsTextViews.addView(tv);
                }

                if (publishedGroupDetails.getDescription() != null && publishedGroupDetails.getDescription().length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(publishedGroupDetails.getDescription());
                    firstDetailsTextViews.addView(tv);
                }
            } catch (Exception e) {
                firstDetailsTextViews.removeAllViews();
                firstDetailsInitialView.setUnknown();
                TextView tv = getTextView();
                SpannableString spannableString = new SpannableString(getString(R.string.text_unable_to_display_contact_name));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(spannableString);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                firstDetailsTextViews.addView(tv);
            }


            try {
                JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                if (detailsAndPhotos.getNullIfEmptyPhotoUrl() != null) {
                    secondDetailsInitialView.setPhotoUrl(bytesGroupIdentifier, detailsAndPhotos.getNullIfEmptyPhotoUrl());
                } else {
                    secondDetailsInitialView.setGroup(bytesGroupIdentifier);
                }

                {
                    TextView tv = getTextView();
                    if (groupDetails.getName() != null && groupDetails.getName().length() > 0) {
                        tv.setText(groupDetails.getName());
                    } else {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_unnamed_group));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    secondDetailsTextViews.addView(tv);
                }

                if (groupDetails.getDescription() != null && groupDetails.getDescription().length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(groupDetails.getDescription());
                    secondDetailsTextViews.addView(tv);
                }
            } catch (Exception e) {
                secondDetailsTextViews.removeAllViews();
                secondDetailsInitialView.setUnknown();
                TextView tv = getTextView();
                SpannableString spannableString = new SpannableString(getString(R.string.text_unable_to_display_contact_name));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(spannableString);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                secondDetailsTextViews.addView(tv);
            }
        }

        if (!animationsSet) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                LayoutTransition layoutTransition = new LayoutTransition();
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
                mainConstraintLayout.setLayoutTransition(layoutTransition);
                LayoutTransition layoutTransitionMembers = new LayoutTransition();
                groupMembersListLinearLayout.setLayoutTransition(layoutTransitionMembers);
            }, 100);
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

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        if (groupAdmin) {
            getMenuInflater().inflate(R.menu.menu_group_details_owned_v2, menu);
            MenuItem deleteItem = menu.findItem(R.id.action_disband);
            if (deleteItem != null) {
                SpannableString spannableString = new SpannableString(deleteItem.getTitle());
                spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                deleteItem.setTitle(spannableString);
            }
        } else {
            getMenuInflater().inflate(R.menu.menu_group_details_joined_v2, menu);
        }

        MenuItem leaveItem = menu.findItem(R.id.action_leave_group);
        if (leaveItem != null) {
            SpannableString spannableString = new SpannableString(leaveItem.getTitle());
            spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            leaveItem.setTitle(spannableString);
        }

        if (groupAdmin && showEditDetails) {
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
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            App.runThread(() -> {
                List<Contact> contacts = AppDatabase.getInstance().group2MemberDao().getGroupMemberContactsSync(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                if (contacts != null) {
                    ArrayList<BytesKey> bytesContactIdentities = new ArrayList<>();
                    for (Contact contact : contacts) {
                        bytesContactIdentities.add(new BytesKey(contact.bytesContactIdentity));
                    }
                    MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupIdentifier, bytesContactIdentities);
                    new Handler(Looper.getMainLooper()).post(() -> multiCallStartDialogFragment.show(getSupportFragmentManager(), "dialog"));
                }
            });
            return true;
        } else if (itemId == R.id.action_rename) {
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }

            if (group.ownPermissionAdmin) {
                EditOwnedGroupDetailsDialogFragment dialogFragment = EditOwnedGroupDetailsDialogFragment.newInstanceV2(this, group.bytesOwnedIdentity, group.bytesGroupIdentifier, publishedDetails, publishedPhotoUrl, group.personalNote, this::fetchEngineGroupCards);
                dialogFragment.show(getSupportFragmentManager(), "dialog");
            } else {
                EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(this, group);
                editNameAndPhotoDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.action_disband) {
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            if (group.ownPermissionAdmin) {
                final String groupName;
                if (group.getCustomName().length() == 0) {
                    groupName = getString(R.string.text_unnamed_group);
                } else {
                    groupName = group.getCustomName();
                }
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_disband_group)
                        .setMessage(getString(R.string.dialog_message_disband_group, groupName))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            if ((groupDetailsViewModel.getGroupMembers().getValue() == null || groupDetailsViewModel.getGroupMembers().getValue().size() == 0)) {
                                // group is empty, just delete it
                                try {
                                    AppSingleton.getEngine().disbandGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                                    App.toast(R.string.toast_message_group_disbanded, Toast.LENGTH_SHORT);
                                    onBackPressed();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                // group is not empty, second confirmation
                                final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_disband_group)
                                        .setMessage(getString(R.string.dialog_message_disband_non_empty_group_v2_confirmation,
                                                groupName,
                                                groupDetailsViewModel.getGroupMembers().getValue() == null ? 0 : groupDetailsViewModel.getGroupMembers().getValue().size()))
                                        .setPositiveButton(R.string.button_label_ok, (dialog12, which1) -> {
                                            // disband group
                                            try {
                                                AppSingleton.getEngine().disbandGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
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
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            App.runThread(() -> {
                if (group.ownPermissionAdmin) {
                    // check you are not the only admin (among members, pending members could decline)
                    boolean otherAdmin = false;
                    List<Group2Member> group2Members = AppDatabase.getInstance().group2MemberDao().getGroupMembers(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                    for (Group2Member group2Member : group2Members) {
                        if (group2Member.permissionAdmin) {
                            otherAdmin = true;
                            break;
                        }
                    }
                    if (!otherAdmin) {
                        // you are the only admin --> cannot leave the group
                        // check if there is a pending admin to change the error message
                        boolean pendingAdmin = false;
                        List<Group2PendingMember> group2PendingMembers = AppDatabase.getInstance().group2PendingMemberDao().getGroupPendingMembers(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                        for (Group2PendingMember group2Member : group2PendingMembers) {
                            if (group2Member.permissionAdmin) {
                                pendingAdmin = true;
                                break;
                            }
                        }
                        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_unable_to_leave_group)
                                .setPositiveButton(R.string.button_label_ok, null);
                        if (pendingAdmin) {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group_pending_admin);
                        } else {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group);
                        }
                        runOnUiThread(() -> builder.create().show());
                        return;
                    }
                }

                final String groupName;
                if (group.getCustomName().length() == 0) {
                    groupName = getString(R.string.text_unnamed_group);
                } else {
                    groupName = group.getCustomName();
                }

                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_leave_group)
                        .setMessage(getString(R.string.dialog_message_leave_group, groupName))
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            try {
                                AppSingleton.getEngine().leaveGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                                App.toast(R.string.toast_message_leaving_group_v2, Toast.LENGTH_SHORT);
                                onBackPressed();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                runOnUiThread(() -> builder.create().show());
            });
            return true;
        } else if (itemId == R.id.action_sync_group) {
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            try {
                AppSingleton.getEngine().reDownloadGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
            } catch (Exception ignored) {}
            return true;
        } else if (itemId == R.id.action_clone_group) {
            final Group2 group = groupDetailsViewModel.getGroup().getValue();
            if (group == null) {
                return true;
            }
            App.runThread(() -> {
                GroupCloningTasks.ClonabilityOutput clonabilityOutput = GroupCloningTasks.getClonability(group);
                new Handler(Looper.getMainLooper()).post(() -> GroupCloningTasks.initiateGroupCloningOrWarnUser(this, clonabilityOutput));
            });
            return true;
        } else if (itemId == R.id.action_debug_information) {
            Group2 group2 = groupDetailsViewModel.getGroup().getValue();
            if (group2 != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.debug_label_number_of_members_and_invited)).append(" ");
                sb.append(groupDetailsViewModel.getMembersCount()).append("/").append(groupDetailsViewModel.getMembersAndPendingCount()).append("\n");
                try {
                    int version = AppSingleton.getEngine().getGroupV2Version(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                    sb.append(getString(R.string.debug_label_group_version)).append(" ").append(version).append("\n\n");
                } catch (Exception ignored) { }
                try {
                    GroupV2.Identifier groupIdentifier = GroupV2.Identifier.of(group2.bytesGroupIdentifier);
                    switch (groupIdentifier.category) {
                        case GroupV2.Identifier.CATEGORY_SERVER: {
                            sb.append(getString(R.string.debug_label_group_type)).append(" ").append(getString(R.string.debug_label_group_type_user_managed)).append("\n");
                            break;
                        }
                        case GroupV2.Identifier.CATEGORY_KEYCLOAK: {
                            sb.append(getString(R.string.debug_label_group_type)).append(" ").append(getString(R.string.debug_label_group_type_keycloak)).append("\n");
                            break;
                        }
                    }
                    sb.append(getString(R.string.debug_label_server)).append(" ");
                    sb.append(groupIdentifier.serverUrl).append("\n\n");
                } catch (DecodingException ignored) {}

                TextView textView = new TextView(this);
                int sixteenDp = (int) (16 * getResources().getDisplayMetrics().density);
                textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp);
                textView.setTextIsSelectable(true);
                textView.setAutoLinkMask(Linkify.ALL);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setText(sb);

                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.menu_action_debug_information)
                        .setView(textView)
                        .setPositiveButton(R.string.button_label_ok, null);
                builder.create().show();
            }
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        final Group2 group = groupDetailsViewModel.getGroup().getValue();
        if (group == null) {
            return;
        }
        int id = view.getId();
        if (id == R.id.group_discussion_button) {
            App.openGroupV2DiscussionActivity(this, group.bytesOwnedIdentity, group.bytesGroupIdentifier);
        } else if (id == R.id.button_update) {
            try {
                AppSingleton.getEngine().trustGroupV2PublishedDetails(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
            } catch (Exception e) {
                App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
            }
        } else if (id == R.id.button_add_members) {
            if (editingMembers) {
                ArrayList<BytesKey> addedMembers = new ArrayList<>(groupDetailsViewModel.getChangeSet().membersAdded.keySet());
                ArrayList<BytesKey> removedMembers = new ArrayList<>(groupDetailsViewModel.getChangeSet().membersRemoved);
                GroupV2MemberAdditionDialogFragment groupMemberAdditionDialogFragment = GroupV2MemberAdditionDialogFragment.newInstance(group.bytesOwnedIdentity, group.bytesGroupIdentifier, addedMembers, removedMembers);
                groupMemberAdditionDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
        } else if (id == R.id.button_edit_group) {
            groupDetailsViewModel.startEditingMembers();
        } else if (id == R.id.button_discard) {
            groupDetailsViewModel.discardGroupEdits();
        } else if (id == R.id.button_save) {
            groupDetailsViewModel.publishGroupEdits();
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
        } else if (editingMembers) {
            if (!groupDetailsViewModel.discardGroupEdits()) {
                finish();
            }
        } else {
            finish();
        }
    }

    class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.GroupMemberViewHolder> implements Observer<List<Group2MemberDao.Group2MemberOrPending>> {
        private final LayoutInflater inflater;
        private List<Group2MemberDao.Group2MemberOrPending> groupMembers;
        private byte[] bytesContactIdentityBeingBound;

        private static final int CONTACT_CHANGE_MASK = 1;


        public GroupMembersAdapter() {
            this.inflater = LayoutInflater.from(GroupV2DetailsActivity.this);
        }

        @NonNull
        @Override
        public GroupMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GroupMemberViewHolder(inflater.inflate(R.layout.item_view_group_v2_member, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull GroupMemberViewHolder holder, int position) {
            // never called
        }

        @Override
        public void onBindViewHolder(@NonNull GroupMemberViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (groupMembers == null) {
                return;
            }

            int changesMask = -1;
            if (payloads.size() > 0 && payloads.get(0) instanceof Integer) {
                changesMask = (int) payloads.get(0);
            }

            Group2MemberDao.Group2MemberOrPending group2Member = groupMembers.get(position);
            holder.bytesContactIdentity = group2Member.bytesContactIdentity;
            holder.contact = group2Member.contact;
            this.bytesContactIdentityBeingBound = group2Member.bytesContactIdentity;

            if ((changesMask & CONTACT_CHANGE_MASK) != 0) {
                JsonIdentityDetails identityDetails;
                try {
                    identityDetails = AppSingleton.getJsonObjectMapper().readValue(group2Member.identityDetails, JsonIdentityDetails.class);
                } catch (Exception e) {
                    identityDetails = null;
                }


                if (group2Member.contact != null) {
                    holder.initialView.setContact(group2Member.contact);
                } else {
                    if (identityDetails != null) {
                        holder.initialView.setInitial(group2Member.bytesContactIdentity, StringUtils.getInitial(identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName())));
                    } else {
                        holder.initialView.setUnknown();
                    }
                    holder.initialView.setKeycloakCertified(false);
                    holder.initialView.setLocked(false);
                    holder.initialView.setInactive(false);
                    holder.initialView.setNullTrustLevel();
                }

                if (group2Member.contact != null && group2Member.contact.customDisplayName != null) {
                    holder.contactNameTextView.setText(group2Member.contact.customDisplayName);
                    if (identityDetails == null) {
                        holder.contactNameTextView.setMaxLines(2);
                        holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                    } else {
                        holder.contactNameTextView.setMaxLines(1);
                        holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                        holder.contactNameSecondLineTextView.setText(identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                    }
                } else {
                    if (identityDetails == null) {
                        SpannableString spannableString = new SpannableString(getString(R.string.text_unable_to_display_contact_name));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        holder.contactNameTextView.setText(spannableString);
                        holder.contactNameTextView.setMaxLines(2);
                        holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                    } else {
                        holder.contactNameTextView.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                        String secondLine = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                        if (secondLine == null) {
                            holder.contactNameTextView.setMaxLines(2);
                            holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                        } else {
                            holder.contactNameTextView.setMaxLines(1);
                            holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                            holder.contactNameSecondLineTextView.setText(secondLine);
                        }
                    }
                }
            }

            if (group2Member.permissionAdmin) {
                holder.adminIndicatorImageView.setVisibility(View.VISIBLE);
            } else {
                holder.adminIndicatorImageView.setVisibility(View.GONE);
            }


            if (editingMembers) {
                holder.deleteButton.setVisibility(View.VISIBLE);
                holder.adminGroup.setVisibility(View.VISIBLE);
                holder.adminSwitch.setVisibility(View.VISIBLE);
                holder.adminSwitch.setChecked(group2Member.permissionAdmin);
                if (group2Member.permissionAdmin) {
                    holder.adminLabel.setText(R.string.label_admin);
                } else {
                    holder.adminLabel.setText(R.string.label_not_admin);
                }
            } else {
                holder.deleteButton.setVisibility(View.GONE);
                holder.adminSwitch.setVisibility(View.GONE);
                if (group2Member.permissionAdmin) {
                    holder.adminGroup.setVisibility(View.VISIBLE);
                    if (group2Member.pending) {
                        holder.adminLabel.setText(R.string.label_pending_admin);
                    } else {
                        holder.adminLabel.setText(R.string.label_admin);
                    }
                } else {
                    if (group2Member.pending) {
                        holder.adminGroup.setVisibility(View.VISIBLE);
                        holder.adminLabel.setText(R.string.label_pending);
                    } else {
                        holder.adminGroup.setVisibility(View.GONE);
                    }
                }
            }
            this.bytesContactIdentityBeingBound = null;
        }

        @Override
        public void onViewRecycled(@NonNull GroupMemberViewHolder holder) {
            super.onViewRecycled(holder);
            holder.bytesContactIdentity = null;
            holder.contact = null;
        }

        @Override
        public int getItemCount() {
            if (groupMembers == null) {
                return 0;
            }
            return groupMembers.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<Group2MemberDao.Group2MemberOrPending> groupMembers) {
            if (this.groupMembers == null || groupMembers == null) {
                this.groupMembers = groupMembers;
                notifyDataSetChanged();
            } else {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @NonNull private final List<Group2MemberDao.Group2MemberOrPending> oldList = GroupMembersAdapter.this.groupMembers;
                    @NonNull private final List<Group2MemberDao.Group2MemberOrPending> newList = groupMembers;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        Group2MemberDao.Group2MemberOrPending oldItem = oldList.get(oldItemPosition);
                        Group2MemberDao.Group2MemberOrPending newItem = newList.get(newItemPosition);
                        return Arrays.equals(oldItem.bytesContactIdentity, newItem.bytesContactIdentity);
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        return false;
                    }

                    @Override
                    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                        Group2MemberDao.Group2MemberOrPending oldItem = oldList.get(oldItemPosition);
                        Group2MemberDao.Group2MemberOrPending newItem = newList.get(newItemPosition);

                        int changesMask = 0;

                        if (oldItem.contact != newItem.contact) {
                            changesMask |= CONTACT_CHANGE_MASK;
                        }

                        if (!Objects.equals(oldItem.identityDetails, newItem.identityDetails)) {
                            changesMask |= CONTACT_CHANGE_MASK;
                        }


                        return changesMask;
                    }
                });
                this.groupMembers = groupMembers;
                result.dispatchUpdatesTo(this);
            }
        }


        class GroupMemberViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
            final InitialView initialView;
            final ImageView adminIndicatorImageView;
            final TextView contactNameTextView;
            final TextView contactNameSecondLineTextView;
            final View adminGroup;
            final TextView adminLabel;
            @SuppressLint("UseSwitchCompatOrMaterialCode")
            final Switch adminSwitch;
            final ImageView deleteButton;

            byte[] bytesContactIdentity;
            Contact contact;

            public GroupMemberViewHolder(@NonNull View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                initialView = itemView.findViewById(R.id.initial_view);
                adminIndicatorImageView = itemView.findViewById(R.id.admin_indicator_image_view);
                contactNameTextView = itemView.findViewById(R.id.contact_name_text_view);
                contactNameSecondLineTextView = itemView.findViewById(R.id.contact_name_second_line_text_view);
                adminGroup = itemView.findViewById(R.id.group_admin_group);
                adminLabel = itemView.findViewById(R.id.group_admin_label);
                adminSwitch = itemView.findViewById(R.id.group_admin_switch);
                adminSwitch.setOnCheckedChangeListener(this);
                deleteButton = itemView.findViewById(R.id.delete_button);
                deleteButton.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.delete_button) {
                    if (bytesContactIdentity != null) {
                        groupDetailsViewModel.memberRemoved(bytesContactIdentity);
                    }
                } else if (!editingMembers) {
                    if (bytesContactIdentity != null) {
                        if (contact != null) {
                            if (contact.oneToOne) {
                                App.openOneToOneDiscussionActivity(GroupV2DetailsActivity.this, contact.bytesOwnedIdentity, contact.bytesContactIdentity, false);
                            } else {
                                App.openContactDetailsActivity(GroupV2DetailsActivity.this, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            }
                        } else {
                            App.toast(getString(R.string.toast_message_not_yet_in_your_contacts, contactNameTextView.getText()), Toast.LENGTH_SHORT, Gravity.BOTTOM);
                        }
                    }
                }
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (bytesContactIdentity == null || Arrays.equals(bytesContactIdentityBeingBound, bytesContactIdentity)) {
                    // ignore check changes during onBindViewHolder
                    return;
                }
                groupDetailsViewModel.adminChanged(bytesContactIdentity, isChecked);
                if (isChecked) {
                    adminLabel.setText(R.string.label_admin);
                } else {
                    adminLabel.setText(R.string.label_not_admin);
                }
            }
        }
    }

    private class SwipeCallback extends ItemTouchHelper.SimpleCallback {
        private final Paint redPaint;

        private SwipeCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            redPaint = new Paint();
            redPaint.setColor(ContextCompat.getColor(GroupV2DetailsActivity.this, R.color.red));
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            if (!groupAdmin) {
                return 0;
            }
            return super.getMovementFlags(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (viewHolder instanceof GroupMembersAdapter.GroupMemberViewHolder) {
                groupDetailsViewModel.memberRemoved(((GroupMembersAdapter.GroupMemberViewHolder) viewHolder).bytesContactIdentity);
            }
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            c.save();
            c.drawPaint(redPaint);
            c.restore();

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    // region EngineNotificationListener

    private Long registrationNumber;

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
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.GROUP_V2_CREATED_OR_UPDATED: {
                ObvGroupV2 groupV2 = (ObvGroupV2) userInfo.get(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY);
                if (groupV2 != null
                        && Arrays.equals(groupV2.bytesOwnedIdentity, groupDetailsViewModel.getBytesOwnedIdentity())
                        && Arrays.equals(groupV2.groupIdentifier.getBytes(), groupDetailsViewModel.getBytesGroupIdentifier())) {
                    runOnUiThread(() -> displayEngineGroupCards(groupV2.detailsAndPhotos));
                }
                break;
            }
            case EngineNotifications.GROUP_V2_PHOTO_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY);
                if (Arrays.equals(bytesOwnedIdentity, groupDetailsViewModel.getBytesOwnedIdentity())
                        && Arrays.equals(bytesGroupIdentifier, groupDetailsViewModel.getBytesGroupIdentifier())) {
                    fetchEngineGroupCards();
                }
                break;
            }
            case EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY);
                Boolean updating = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY);
                //noinspection unused
                Boolean creating = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_CREATING_KEY);
                if (Arrays.equals(bytesOwnedIdentity, groupDetailsViewModel.getBytesOwnedIdentity())
                        && Arrays.equals(bytesGroupIdentifier, groupDetailsViewModel.getBytesGroupIdentifier())) {
                    if (updating != null && !updating) {
                        groupDetailsViewModel.publicationFinished();
                    }
                }
                break;
            }
            case EngineNotifications.GROUP_V2_UPDATE_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_GROUP_IDENTIFIER_KEY);
                Boolean error = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_FAILED_ERROR_KEY);
                if (Arrays.equals(bytesOwnedIdentity, groupDetailsViewModel.getBytesOwnedIdentity())
                        && Arrays.equals(bytesGroupIdentifier, groupDetailsViewModel.getBytesGroupIdentifier())) {
                    groupDetailsViewModel.publicationFinished();
                    if (error != null && error) {
                        App.toast(R.string.toast_message_unable_to_update_group, Toast.LENGTH_LONG);
                    }
                }
                break;
            }
        }
    }
    // endregion
}
