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

package io.olvid.messenger.activities;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvTrustOrigin;
import io.olvid.engine.engine.types.identities.ObvUrlIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.dialog.ContactIntroductionDialogFragment;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.ContactDetailsViewModel;


public class ContactDetailsActivity extends LockableActivity implements View.OnClickListener, EngineNotificationListener {
    public static final String CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA = "contact_bytes_identity";
    public static final String CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA = "contact_bytes_owned_identity";

    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";


    private ContactDetailsViewModel contactDetailsViewModel;

    private ConstraintLayout mainConstraintLayout;
    private FloatingActionButton discussionButton;
    private InitialView contactInitialView;
    private TextView contactNameTextView;
    private TextView personalNoteTextView;
    private TextView trustOriginsListTextView;
    private Button exchangeDigitsButton;
    private CardView revokedCardView;
    private TextView revokedExplanationTextView;
    private Button unblockRevokedButton;
    private Button reblockRevokedButton;
    private CardView noChannelCardView;
    private ImageView noChannelSpinner;
    private CardView notOneToOneCardView;
    private TextView notOneToOneTitleTextView;
    private TextView notOneToOneExplanationTextView;
    private Button notOneToOneInviteButton;
    private Button notOneToOneRejectButton;
    private CardView acceptUpdateCardView;
    private CardView trustedDetailsCardView;
    private LinearLayout publishedDetailsTextViews;
    private LinearLayout trustedDetailsTextViews;
    private InitialView publishedDetailsInitialView;
    private InitialView trustedDetailsInitialView;
    private TextView publishDetailsTitle;
    private Button introduceButton;
    private FilteredDiscussionListFragment contactGroupDiscussionsFragment;
    private JsonIdentityDetails publishedDetails;

    private int primary700;
    private Long registrationNumber;

    private boolean animationsSet = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contactDetailsViewModel = new ViewModelProvider(this).get(ContactDetailsViewModel.class);

        setContentView(R.layout.activity_contact_details);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mainConstraintLayout = findViewById(R.id.contact_details_main_constraint_layout);

        discussionButton = findViewById(R.id.contact_discussion_button);
        discussionButton.setOnClickListener(this);

        revokedCardView = findViewById(R.id.contact_revoked_cardview);
        revokedExplanationTextView = findViewById(R.id.contact_revoked_explanation);
        unblockRevokedButton = findViewById(R.id.button_contact_revoked_forcefully_unblock);
        unblockRevokedButton.setOnClickListener(this);
        reblockRevokedButton = findViewById(R.id.button_contact_revoked_forcefully_reblock);
        reblockRevokedButton.setOnClickListener(this);

        noChannelCardView = findViewById(R.id.contact_no_channel_cardview);
        Button restartChannelButton = findViewById(R.id.contact_no_channel_restart_button);
        restartChannelButton.setOnClickListener(this);
        noChannelSpinner = findViewById(R.id.contact_no_channel_spinner);

        notOneToOneCardView = findViewById(R.id.contact_not_one_to_one_card);
        notOneToOneTitleTextView = findViewById(R.id.contact_not_one_to_one_header);
        notOneToOneExplanationTextView = findViewById(R.id.contact_not_one_to_one_explanation);
        notOneToOneInviteButton = findViewById(R.id.contact_not_one_to_one_invite_button);
        notOneToOneInviteButton.setOnClickListener(this);
        notOneToOneRejectButton = findViewById(R.id.contact_not_one_to_one_reject_button);
        notOneToOneRejectButton.setOnClickListener(this);


        contactInitialView = findViewById(R.id.contact_details_initial_view);
        contactInitialView.setOnClickListener(this);
        contactNameTextView = findViewById(R.id.contact_name_text_view);
        personalNoteTextView = findViewById(R.id.contact_personal_note_text_view);


        // detail cards
        acceptUpdateCardView = findViewById(R.id.contact_accept_update_cardview);
        Button updateButton = findViewById(R.id.button_update);
        updateButton.setOnClickListener(this);


        publishDetailsTitle = findViewById(R.id.published_details_title);
        publishedDetailsTextViews = findViewById(R.id.published_details_textviews);
        publishedDetailsInitialView = findViewById(R.id.published_details_initial_view);
        publishedDetailsInitialView.setOnClickListener(this);
        introduceButton = findViewById(R.id.contact_introduce_button);
        Button shareButton = findViewById(R.id.contact_share_button);

        shareButton.setOnClickListener(this);
        introduceButton.setOnClickListener(this);
        introduceButton.setEnabled(false);


        trustedDetailsCardView = findViewById(R.id.trusted_details_cardview);
        trustedDetailsTextViews = findViewById(R.id.trusted_details_textviews);
        trustedDetailsInitialView = findViewById(R.id.trusted_details_initial_view);
        trustedDetailsInitialView.setOnClickListener(this);

        View groupEmptyView = findViewById(R.id.contact_group_list_empty_view);

        contactGroupDiscussionsFragment = new FilteredDiscussionListFragment();
        contactGroupDiscussionsFragment.removeBottomPadding();
        contactGroupDiscussionsFragment.setEmptyView(groupEmptyView);
        contactGroupDiscussionsFragment.setOnClickDelegate((view, searchableDiscussion) -> App.openDiscussionActivity(view.getContext(), searchableDiscussion.discussionId));

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.contact_group_list_placeholder, contactGroupDiscussionsFragment);
        transaction.commit();


        trustOriginsListTextView = findViewById(R.id.contact_trust_origins_list);
        trustOriginsListTextView.setMovementMethod(LinkMovementMethod.getInstance());

        exchangeDigitsButton = findViewById(R.id.contact_trust_origin_exchange_digits_button);
        exchangeDigitsButton.setOnClickListener(this);

        primary700 = ContextCompat.getColor(this, R.color.primary700);

        registrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_CONTACT_PHOTO, this);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_CONTACT_PHOTO, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS, this);
        if (contactDetailsViewModel.getContactAndInvitation() == null || contactDetailsViewModel.getContactAndInvitation().getValue() == null) {
            return;
        }
        final Contact contact = contactDetailsViewModel.getContactAndInvitation().getValue().contact;
        App.runThread(() -> {
            if (contact.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN) {
                contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_SEEN;
                AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        if (contactDetailsViewModel.getContactAndInvitation() == null || contactDetailsViewModel.getContactAndInvitation().getValue() == null) {
            return true;
        }
        final Contact contact = contactDetailsViewModel.getContactAndInvitation().getValue().contact;
        if (contact.oneToOne) {
            getMenuInflater().inflate(R.menu.menu_contact_details, menu);
        } else {
            getMenuInflater().inflate(R.menu.menu_contact_details_not_one_to_one, menu);
        }
        if (contact.active) {
            getMenuInflater().inflate(R.menu.menu_contact_details_recreate_channels, menu);
            if (contact.establishedChannelCount > 0) {
                getMenuInflater().inflate(R.menu.menu_contact_details_call, menu);
            }
        }
        MenuItem deleteItem = menu.findItem(R.id.action_delete_contact);
        if (deleteItem != null) {
            SpannableString spannableString = new SpannableString(deleteItem.getTitle());
            spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteItem.setTitle(spannableString);
        }
        return true;
    }


    public void displayDetails(ContactDetailsViewModel.ContactAndInvitation contactAndInvitation) {
        if (contactAndInvitation == null) {
            return;
        }
        Contact contact = contactAndInvitation.contact;
        Invitation invitation = contactAndInvitation.invitation;

        invalidateOptionsMenu();

        if (contact.oneToOne) {
            setTitle(R.string.activity_title_contact_details);
            introduceButton.setVisibility(View.VISIBLE);
            notOneToOneCardView.setVisibility(View.GONE);
            discussionButton.setVisibility(View.VISIBLE);
        } else {
            setTitle(R.string.activity_title_user_details);
            introduceButton.setVisibility(View.GONE);
            notOneToOneCardView.setVisibility(View.VISIBLE);
            discussionButton.setVisibility(View.INVISIBLE);
            if (invitation == null) {
                notOneToOneTitleTextView.setText(R.string.label_contact_not_one_to_one);
                notOneToOneExplanationTextView.setText(getString(R.string.explanation_contact_not_one_to_one, contact.getCustomDisplayName()));
                notOneToOneInviteButton.setVisibility(View.VISIBLE);
                notOneToOneInviteButton.setText(R.string.button_label_invite);
                notOneToOneRejectButton.setVisibility(View.GONE);
            } else if (invitation.categoryId == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY) {
                notOneToOneTitleTextView.setText(R.string.invitation_status_one_to_one_invitation);
                notOneToOneExplanationTextView.setText(getString(R.string.invitation_status_description_one_to_one_invitation_sent, contact.getCustomDisplayName()));
                notOneToOneInviteButton.setVisibility(View.GONE);
                notOneToOneRejectButton.setVisibility(View.VISIBLE);
                notOneToOneRejectButton.setText(R.string.button_label_abort);
            } else if (invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                AndroidNotificationManager.clearInvitationNotification(invitation.dialogUuid);
                notOneToOneTitleTextView.setText(R.string.invitation_status_one_to_one_invitation);
                notOneToOneExplanationTextView.setText(getString(R.string.invitation_status_description_one_to_one_invitation, contact.getCustomDisplayName()));
                notOneToOneInviteButton.setVisibility(View.VISIBLE);
                notOneToOneInviteButton.setText(R.string.button_label_accept);
                notOneToOneRejectButton.setVisibility(View.VISIBLE);
                notOneToOneRejectButton.setText(R.string.button_label_reject);
            }
        }

        String displayName = contact.getCustomDisplayName();
        contactInitialView.setContact(contact);
        contactNameTextView.setText(displayName);
        if (contact.personalNote != null) {
            personalNoteTextView.setVisibility(View.VISIBLE);
            personalNoteTextView.setText(contact.personalNote);
        } else {
            personalNoteTextView.setVisibility(View.GONE);
        }

        if (contact.establishedChannelCount > 0) {
            introduceButton.setEnabled(true);
            noChannelCardView.setVisibility(View.GONE);
            noChannelSpinner.setImageDrawable(null);
            notOneToOneInviteButton.setEnabled(true);
        } else {
            if (contact.active) {
                introduceButton.setEnabled(false);
                noChannelCardView.setVisibility(View.VISIBLE);
                final AnimatedVectorDrawableCompat animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots);
                if (animated != null) {
                    animated.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                        @Override
                        public void onAnimationEnd(Drawable drawable) {
                            new Handler(Looper.getMainLooper()).post(animated::start);
                        }
                    });
                    noChannelSpinner.setImageDrawable(animated);
                    animated.start();
                }
            } else {
                introduceButton.setEnabled(false);
                noChannelCardView.setVisibility(View.GONE);
            }
            notOneToOneInviteButton.setEnabled(false);
        }

        EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
        if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
            revokedCardView.setVisibility(View.VISIBLE);
            if (reasons.contains(ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED)) {
                revokedExplanationTextView.setText(R.string.explanation_contact_revoked_and_unblocked);
                reblockRevokedButton.setVisibility(View.VISIBLE);
                unblockRevokedButton.setVisibility(View.GONE);
            } else {
                revokedExplanationTextView.setText(R.string.explanation_contact_revoked);
                reblockRevokedButton.setVisibility(View.GONE);
                unblockRevokedButton.setVisibility(View.VISIBLE);
            }
        } else {
            revokedCardView.setVisibility(View.GONE);
        }

        try {
            JsonIdentityDetailsWithVersionAndPhoto[] jsons = AppSingleton.getEngine().getContactPublishedAndTrustedDetails(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            if (jsons == null || jsons.length == 0) {
                return;
            }
            if (jsons.length == 1) {
                acceptUpdateCardView.setVisibility(View.GONE);
                trustedDetailsCardView.setVisibility(View.GONE);

                publishDetailsTitle.setText(R.string.label_olvid_card);
                publishDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title));

                publishedDetailsTextViews.removeAllViews();
                publishedDetails = jsons[0].getIdentityDetails();
                String publishedFirstLine = publishedDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                String publishedSecondLine = publishedDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                publishedDetailsInitialView.setInitial(contact.bytesContactIdentity, StringUtils.getInitial(publishedFirstLine));
                if (jsons[0].getPhotoUrl() != null) {
                    publishedDetailsInitialView.setPhotoUrl(contact.bytesContactIdentity, jsons[0].getPhotoUrl());
                }

                {
                    TextView tv = getTextView();
                    tv.setText(publishedFirstLine);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    publishedDetailsTextViews.addView(tv);
                }
                if (publishedSecondLine != null && publishedSecondLine.length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(publishedSecondLine);
                    publishedDetailsTextViews.addView(tv);
                }
                if (publishedDetails.getCustomFields() != null) {
                    List<String> keys = new ArrayList<>(publishedDetails.getCustomFields().size());
                    keys.addAll(publishedDetails.getCustomFields().keySet());
                    Collections.sort(keys);
                    for (String key : keys) {
                        TextView tv = getTextView();
                        String value = publishedDetails.getCustomFields().get(key);
                        SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                        publishedDetailsTextViews.addView(tv);
                    }
                }

                App.runThread(() -> {
                    if (contact.newPublishedDetails != Contact.PUBLISHED_DETAILS_NOTHING_NEW) {
                        contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NOTHING_NEW;
                        AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);
                    }
                    if ((contact.photoUrl == null && jsons[0].getPhotoUrl() != null) ||
                            (contact.photoUrl != null && !contact.photoUrl.equals(jsons[0].getPhotoUrl()))) {
                        contact.photoUrl = jsons[0].getPhotoUrl();
                        AppDatabase.getInstance().contactDao().updatePhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.photoUrl);
                        AppSingleton.updateCachedPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
                    }
                });
            } else {
                publishDetailsTitle.setText(R.string.label_olvid_card_published_update);
                publishDetailsTitle.setBackground(ContextCompat.getDrawable(this, R.drawable.background_identity_title_new));

                acceptUpdateCardView.setVisibility(View.VISIBLE);
                trustedDetailsCardView.setVisibility(View.VISIBLE);

                trustedDetailsTextViews.removeAllViews();
                publishedDetailsTextViews.removeAllViews();

                JsonIdentityDetails trustedDetails = jsons[1].getIdentityDetails();
                String trustedFirstLine  = trustedDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                String trustedSecondLine = trustedDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                trustedDetailsInitialView.setInitial(contact.bytesContactIdentity, StringUtils.getInitial(trustedFirstLine));
                if (jsons[1].getPhotoUrl() != null) {
                    trustedDetailsInitialView.setPhotoUrl(contact.bytesContactIdentity, jsons[1].getPhotoUrl());
                }

                {
                    TextView tv = getTextView();
                    tv.setText(trustedFirstLine);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    trustedDetailsTextViews.addView(tv);
                }
                if (trustedSecondLine != null && trustedSecondLine.length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(trustedSecondLine);
                    trustedDetailsTextViews.addView(tv);
                }
                if (trustedDetails.getCustomFields() != null) {
                    List<String> keys = new ArrayList<>(trustedDetails.getCustomFields().size());
                    keys.addAll(trustedDetails.getCustomFields().keySet());
                    Collections.sort(keys);
                    for (String key: keys) {
                        TextView tv = getTextView();
                        String value = trustedDetails.getCustomFields().get(key);
                        if (value == null) {
                            continue;
                        }
                        SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                        trustedDetailsTextViews.addView(tv);
                    }
                }


                publishedDetails = jsons[0].getIdentityDetails();
                String publishedFirstLine = publishedDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                String publishedSecondLine = publishedDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                publishedDetailsInitialView.setInitial(contact.bytesContactIdentity, StringUtils.getInitial(publishedFirstLine));
                if (jsons[0].getPhotoUrl() != null) {
                    publishedDetailsInitialView.setPhotoUrl(contact.bytesContactIdentity, jsons[0].getPhotoUrl());
                }

                {
                    TextView tv = getTextView();
                    tv.setText(publishedFirstLine);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    if (!publishedFirstLine.equals(trustedFirstLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    publishedDetailsTextViews.addView(tv);
                }
                if (publishedSecondLine != null && publishedSecondLine.length() > 0) {
                    TextView tv = getTextView();
                    tv.setText(publishedSecondLine);
                    if (!publishedSecondLine.equals(trustedSecondLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    publishedDetailsTextViews.addView(tv);
                }
                if (publishedDetails.getCustomFields() != null) {
                    List<String> keys = new ArrayList<>(publishedDetails.getCustomFields().size());
                    keys.addAll(publishedDetails.getCustomFields().keySet());
                    Collections.sort(keys);
                    for (String key : keys) {
                        TextView tv = getTextView();
                        String value = publishedDetails.getCustomFields().get(key);
                        if (value == null) {
                            continue;
                        }
                        SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                        if (!(trustedDetails.getCustomFields() != null && value.equals(trustedDetails.getCustomFields().get(key)))) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        publishedDetailsTextViews.addView(tv);
                    }
                }

                App.runThread(() -> {
                    if (contact.newPublishedDetails == Contact.PUBLISHED_DETAILS_NOTHING_NEW) {
                        contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_SEEN;
                        AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);
                    }
                    if ((contact.photoUrl == null && jsons[1].getPhotoUrl() != null) ||
                            (contact.photoUrl != null && !contact.photoUrl.equals(jsons[1].getPhotoUrl()))) {
                        contact.photoUrl = jsons[1].getPhotoUrl();
                        AppDatabase.getInstance().contactDao().updatePhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.photoUrl);
                        AppSingleton.updateCachedPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (!animationsSet) {
            mainConstraintLayout.setLayoutTransition(new LayoutTransition());
            animationsSet = true;
        }
        App.runThread(new DisplayTrustOriginsTask(trustOriginsListTextView, exchangeDigitsButton, contact));
    }

    private TextView getTextView() {
        TextView tv = new AppCompatTextView(this);
        tv.setTextColor(primary700);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setMaxLines(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.identity_details_margin));
        tv.setLayoutParams(params);
        return tv;
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.NEW_CONTACT_PHOTO: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY);
                Boolean isTrusted = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY);
                if (contactDetailsViewModel.getContactAndInvitation() == null) {
                    break;
                }
                final ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();
                if (isTrusted != null && !isTrusted
                        && contactAndInvitation != null
                        && Arrays.equals(contactAndInvitation.contact.bytesContactIdentity, bytesContactIdentity)
                        && Arrays.equals(contactAndInvitation.contact.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    runOnUiThread(() -> displayDetails(contactAndInvitation));
                }
                break;
            }
            case EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY);
                if (contactDetailsViewModel.getContactAndInvitation() == null) {
                    break;
                }
                final ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();

                if ( contactAndInvitation != null
                        && Arrays.equals(contactAndInvitation.contact.bytesContactIdentity, bytesContactIdentity)
                        && Arrays.equals(contactAndInvitation.contact.bytesOwnedIdentity, bytesOwnedIdentity))  {
                    runOnUiThread(() -> displayDetails(contactAndInvitation));
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

    static class DisplayTrustOriginsTask implements Runnable {
        private final WeakReference<TextView> textViewWeakReference;
        private final WeakReference<Button> buttonWeakReference;
        private final Contact contact;
        private final Context context;

        DisplayTrustOriginsTask(TextView textView, Button exchangeDigitsButton, Contact contact) {
            this.textViewWeakReference = new WeakReference<>(textView);
            this.buttonWeakReference = new WeakReference<>(exchangeDigitsButton);
            this.contact = contact;
            this.context = App.getContext();
        }

        @Override
        public void run() {
            if (contact.trustLevel < 4) {
                final Button button = buttonWeakReference.get();
                if (button != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        button.setText(button.getContext().getString(R.string.button_label_exchange_digits_with_user, contact.getCustomDisplayName()));
                        button.setVisibility(View.VISIBLE);
                        button.getParent().requestLayout();
                    });
                }
            }

            try {
                ObvTrustOrigin[] trustOrigins = AppSingleton.getEngine().getContactTrustOrigins(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                final SpannableStringBuilder builder = new SpannableStringBuilder();
                boolean first = true;
                for (ObvTrustOrigin trustOrigin : trustOrigins) {
                    if (!first) {
                        builder.append("\n");
                    }
                    first = false;
                    builder.append(trustOriginToCharSequence(trustOrigin, contact.bytesOwnedIdentity));
                }
                final TextView textView = textViewWeakReference.get();
                if (textView != null) {
                    new Handler(Looper.getMainLooper()).post(() -> textView.setText(builder));
                }
            } catch (Exception e) {
                final TextView textView = textViewWeakReference.get();
                if (textView != null) {
                    new Handler(Looper.getMainLooper()).post(() -> textView.setText(R.string.message_error_trust_origin));
                }
            }
        }

        private CharSequence trustOriginToCharSequence(final ObvTrustOrigin trustOrigin, final byte[] bytesOwnedIdentity) {
            switch (trustOrigin.getType()) {
                case DIRECT:
                    return context.getString(R.string.trust_origin_direct_type, StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
                case INTRODUCTION: {
                    String text = context.getString(R.string.trust_origin_introduction_type, StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
                    SpannableString link = (trustOrigin.getMediatorOrGroupOwner().getIdentityDetails() == null) ? null : new SpannableString(trustOrigin.getMediatorOrGroupOwner().getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                    if (link != null && link.length() > 0) {
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                App.openContactDetailsActivity(view.getContext(), bytesOwnedIdentity, trustOrigin.getMediatorOrGroupOwner().getBytesIdentity());
                            }
                        };
                        link.setSpan(clickableSpan, 0, link.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        link = new SpannableString(context.getString(R.string.text_deleted_contact));
                        StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                        link.setSpan(styleSpan, 0, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    return TextUtils.concat(text, link);
                }
                case GROUP: {
                    String text = context.getString(R.string.trust_origin_group_type, StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
                    SpannableString link = (trustOrigin.getMediatorOrGroupOwner().getIdentityDetails() == null) ? null : new SpannableString(trustOrigin.getMediatorOrGroupOwner().getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                    if (link != null && link.length() > 0) {
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                App.openContactDetailsActivity(view.getContext(), bytesOwnedIdentity, trustOrigin.getMediatorOrGroupOwner().getBytesIdentity());
                            }
                        };
                        link.setSpan(clickableSpan, 0, link.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        link = new SpannableString(context.getString(R.string.text_deleted_contact));
                        StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                        link.setSpan(styleSpan, 0, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    return TextUtils.concat(text, link);
                }
                case KEYCLOAK: {
                    return context.getString(R.string.trust_origin_keycloak_type, trustOrigin.getKeycloakServer(), StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
                }
                case SERVER_GROUP_V2: {
                    String text = context.getString(R.string.trust_origin_group_v2_type, StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
                    Group2 group2 = AppDatabase.getInstance().group2Dao().get(bytesOwnedIdentity, trustOrigin.getBytesGroupIdentifier());
                    SpannableString link;
                    if (group2 == null) {
                        link = new SpannableString(context.getString(R.string.text_deleted_group));
                        StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                        link.setSpan(styleSpan, 0, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        String groupName = group2.getTruncatedCustomName();
                        link = new SpannableString(groupName);
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                App.openGroupV2DetailsActivity(view.getContext(), bytesOwnedIdentity, trustOrigin.getBytesGroupIdentifier());
                            }
                        };
                        link.setSpan(clickableSpan, 0, link.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    return TextUtils.concat(text, link);
                }
                default:
                    return context.getString(R.string.trust_origin_unknown_type, StringUtils.getNiceDateString(context, trustOrigin.getTimestamp()));
            }
        }
    }

    private void handleIntent(Intent intent) {
        if (!intent.hasExtra(CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA) || !intent.hasExtra(CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA)) {
            finish();
            Logger.w("Missing contact identity in intent.");
            return;
        }

        byte[] contactBytesIdentity = intent.getByteArrayExtra(CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA);
        byte[] contactBytesOwnedIdentity = intent.getByteArrayExtra(CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA);

        if (contactDetailsViewModel.getContactAndInvitation() != null) {
            contactDetailsViewModel.getContactAndInvitation().removeObservers(this);
        }
        if (contactDetailsViewModel.getGroupDiscussions() != null) {
            contactDetailsViewModel.getGroupDiscussions().removeObservers(this);
        }
        contactDetailsViewModel.setContactBytes(contactBytesOwnedIdentity, contactBytesIdentity);
        contactDetailsViewModel.getContactAndInvitation().observe(this, this::displayDetails);

        contactGroupDiscussionsFragment.setUnfilteredDiscussions(contactDetailsViewModel.getGroupDiscussions());
    }


    @Override
    public void onClick(View view) {
        if (contactDetailsViewModel.getContactAndInvitation() == null) {
            return;
        }
        final ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();
        if (contactAndInvitation == null) {
            return;
        }
        final Contact contact = contactAndInvitation.contact;

        int id = view.getId();
        if (id == R.id.contact_introduce_button) {
            if (contact.establishedChannelCount > 0) {
                ContactIntroductionDialogFragment contactIntroductionDialogFragment = ContactIntroductionDialogFragment.newInstance(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.getCustomDisplayName());
                contactIntroductionDialogFragment.show(getSupportFragmentManager(), "dialog");
            } else { // this should never happen as the button should be disabled when no channel exists
                App.toast(R.string.toast_message_established_channel_required_for_introduction, Toast.LENGTH_LONG);
            }
        } else if (id == R.id.contact_not_one_to_one_invite_button) {
            if (contactAndInvitation.invitation != null) {
                // this is an accept for an invitation
                if (contactAndInvitation.invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                    try {
                        ObvDialog obvDialog = contactAndInvitation.invitation.associatedDialog;
                        obvDialog.setResponseToAcceptOneToOneInvitation(true);
                        AppSingleton.getEngine().respondToDialog(obvDialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // this is an invite initiation
                try {
                    AppSingleton.getEngine().startOneToOneInvitationProtocol(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (id == R.id.contact_not_one_to_one_reject_button) {
            if (contactAndInvitation.invitation != null) {
                if (contactAndInvitation.invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                    // this is a reject invitation
                    try {
                        ObvDialog obvDialog = contactAndInvitation.invitation.associatedDialog;
                        obvDialog.setResponseToAcceptOneToOneInvitation(false);
                        AppSingleton.getEngine().respondToDialog(obvDialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (contactAndInvitation.invitation.categoryId == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY) {
                    // this is an abort
                    try {
                        ObvDialog obvDialog = contactAndInvitation.invitation.associatedDialog;
                        obvDialog.setAbortOneToOneInvitationSent(true);
                        AppSingleton.getEngine().respondToDialog(obvDialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (id == R.id.contact_discussion_button) {
            App.openOneToOneDiscussionActivity(this, contact.bytesOwnedIdentity, contact.bytesContactIdentity, true);
        } else if (id == R.id.contact_share_button) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            String identityUrl = new ObvUrlIdentity(contact.bytesContactIdentity, publishedDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, false)).getUrlRepresentation();
            if (identityUrl == null) {
                return;
            }
            intent.putExtra(Intent.EXTRA_TEXT, identityUrl);
            startActivity(Intent.createChooser(intent, getString(R.string.title_sharing_chooser)));
        } else if (id == R.id.contact_no_channel_restart_button) {
            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_restart_channel_establishment)
                    .setMessage(R.string.dialog_message_restart_channel_establishment)
                    .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                        try {
                            AppSingleton.getEngine().restartAllOngoingChannelEstablishmentProtocols(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                        } catch (Exception e) {
                            App.toast(R.string.toast_message_channel_restart_failed, Toast.LENGTH_SHORT);
                            return;
                        }
                        App.toast(R.string.toast_message_channel_restart_sucessful, Toast.LENGTH_SHORT);
                    })
                    .setNegativeButton(R.string.button_label_cancel, null);
            builder.create().show();
        } else if (id == R.id.button_update) {
            AppSingleton.getEngine().trustPublishedContactDetails(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
        } else if (id == R.id.button_contact_revoked_forcefully_reblock) {
            if (!AppSingleton.getEngine().reBlockForcefullyUnblockedContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity)) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
            }
        } else if (id == R.id.button_contact_revoked_forcefully_unblock) {
            if (!AppSingleton.getEngine().forcefullyUnblockContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity)) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
            }
        } else if (id == R.id.contact_trust_origin_exchange_digits_button) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
            builder.setMessage(getString(R.string.dialog_message_exchange_digits, contact.getCustomDisplayName()))
                    .setTitle(R.string.dialog_title_exchange_digits)
                    .setPositiveButton(R.string.button_label_ok, (dialogInterface, i) -> {
                        try {
                            AppSingleton.getEngine().startTrustEstablishmentProtocol(contact.bytesContactIdentity, contact.getCustomDisplayName(), contact.bytesOwnedIdentity);
                            App.openOneToOneDiscussionActivity(view.getContext(), contact.bytesOwnedIdentity, contact.bytesContactIdentity, true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton(R.string.button_label_cancel, null);
            builder.create().show();
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
                App.showMainActivityTab(this, MainActivity.CONTACTS_TAB);
            }
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_call) {
            if (contactDetailsViewModel.getContactAndInvitation() == null || contactDetailsViewModel.getContactAndInvitation().getValue() == null) {
                return true;
            }
            final Contact contact = contactDetailsViewModel.getContactAndInvitation().getValue().contact;
            if (contact.establishedChannelCount == 0) {
                return true;
            }

            App.startWebrtcCall(this, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            return true;
        } else if (itemId == R.id.action_rename) {
            if (contactDetailsViewModel.getContactAndInvitation() == null || contactDetailsViewModel.getContactAndInvitation().getValue() == null) {
                return true;
            }
            final Contact contact = contactDetailsViewModel.getContactAndInvitation().getValue().contact;

            EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(this, contact);
            editNameAndPhotoDialogFragment.show(getSupportFragmentManager(), "dialog");
            return true;
        } else if (itemId == R.id.action_recreate_channels) {
            final ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();
            if (contactAndInvitation != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_recreate_channels)
                        .setMessage(R.string.dialog_message_recreate_channels)
                        .setPositiveButton(R.string.button_label_ok, (dialogInterface, which) -> {
                            try {
                                AppSingleton.getEngine().recreateAllChannels(contactAndInvitation.contact.bytesOwnedIdentity, contactAndInvitation.contact.bytesContactIdentity);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_delete_contact) {
            final ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();
            if (contactAndInvitation != null) {
                App.runThread(new PromptToDeleteContactTask(this, contactAndInvitation.contact.bytesOwnedIdentity, contactAndInvitation.contact.bytesContactIdentity, this::onBackPressed));
            }
            return true;
        } else if (itemId == R.id.action_debug_information) {
            ContactDetailsViewModel.ContactAndInvitation contactAndInvitation = contactDetailsViewModel.getContactAndInvitation().getValue();
            if (contactAndInvitation != null) {
                Contact contact = contactAndInvitation.contact;
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.debug_label_number_of_channels_and_devices)).append("\n");
                sb.append(contact.establishedChannelCount).append("/").append(contact.deviceCount).append("\n\n");
                try {
                    Identity contactIdentity = Identity.of(contact.bytesContactIdentity);
                    sb.append(getString(R.string.debug_label_server)).append(" ");
                    sb.append(contactIdentity.getServer()).append("\n\n");
                } catch (DecodingException ignored) {}
                sb.append(getString(R.string.debug_label_identity_link)).append("\n");
                sb.append(new ObvUrlIdentity(contact.bytesContactIdentity, contact.displayName).getUrlRepresentation()).append("\n\n");
                sb.append(getString(R.string.debug_label_capabilities)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_continuous_gathering, contact.capabilityWebrtcContinuousIce)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_one_to_one_contacts, contact.capabilityOneToOneContacts)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_groups_v2, contact.capabilityGroupsV2)).append("\n");

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
        return super.onOptionsItemSelected(item);
    }
}
