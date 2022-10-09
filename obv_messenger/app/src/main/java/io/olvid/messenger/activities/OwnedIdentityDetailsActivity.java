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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.identities.ObvUrlIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.MuteNotificationDialog;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.DeleteOwnedIdentityAndEverythingRelatedToItTask;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.SubscriptionStatusFragment;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.owneddetails.EditOwnedIdentityDetailsDialogFragment;
import io.olvid.messenger.plus_button.PlusButtonActivity;
import io.olvid.messenger.settings.SettingsActivity;


public class OwnedIdentityDetailsActivity extends LockableActivity implements View.OnClickListener {
    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";

    private InitialView myIdInitialView;
    private TextView myIdNameTextView;
    private CardView latestDetailsCardView;
    private LinearLayout latestDetailsTextViews;
    private InitialView latestDetailsInitialView;
    private LinearLayout publishedDetailsTextViews;
    private InitialView publishedDetailsInitialView;
    private CardView inactiveCardView;
    private boolean keycloakManaged = false;

    private JsonIdentityDetailsWithVersionAndPhoto latestDetails;
    private IdentityObserver identityObserver;
    private int primary700;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_owned_identity_details);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        myIdInitialView = findViewById(R.id.myid_initial_view);
        myIdInitialView.setOnClickListener(this);
        myIdNameTextView = findViewById(R.id.myid_name_text_view);

        inactiveCardView = findViewById(R.id.identity_inactive_card_view);
        Button reactivateIdButton = findViewById(R.id.button_reactivate_identity);
        reactivateIdButton.setOnClickListener(this);

        latestDetailsCardView = findViewById(R.id.latest_details_cardview);
        latestDetailsTextViews = findViewById(R.id.latest_details_textviews);
        latestDetailsInitialView = findViewById(R.id.latest_details_initial_view);
        latestDetailsInitialView.setOnClickListener(this);
        publishedDetailsTextViews = findViewById(R.id.published_details_textviews);
        publishedDetailsInitialView = findViewById(R.id.published_details_initial_view);
        publishedDetailsInitialView.setOnClickListener(this);

        Button publishButton = findViewById(R.id.button_publish);
        publishButton.setOnClickListener(this);
        Button discardButton = findViewById(R.id.button_discard);
        discardButton.setOnClickListener(this);
        Button addContactButton = findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(this);


        primary700 = ContextCompat.getColor(this, R.color.primary700);

        identityObserver = new IdentityObserver();
        AppSingleton.getCurrentIdentityLiveData().observe(this, identityObserver);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_owned_identity_details, menu);
        if (keycloakManaged) {
            getMenuInflater().inflate(R.menu.menu_owned_identity_details_keycloak, menu);
        }
        if (identityObserver.ownedIdentity != null) {
            if (identityObserver.ownedIdentity.shouldMuteNotifications()) {
                getMenuInflater().inflate(R.menu.menu_owned_identity_details_muted, menu);
            }
            if (identityObserver.ownedIdentity.isHidden()) {
                getMenuInflater().inflate(R.menu.menu_owned_identity_details_hidden, menu);
                MenuItem neutralNotificationItem = menu.findItem(R.id.action_neutral_notification);
                neutralNotificationItem.setChecked(identityObserver.ownedIdentity.prefShowNeutralNotificationWhenHidden);
            }
        }
        // make the delete profile item red
        MenuItem deleteItem = menu.findItem(R.id.action_delete_owned_identity);
        if (deleteItem != null) {
            SpannableString spannableString = new SpannableString(deleteItem.getTitle());
            spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteItem.setTitle(spannableString);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_unmute) {
            if (identityObserver.ownedIdentity != null && identityObserver.ownedIdentity.shouldMuteNotifications()) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unmute_notifications)
                        .setPositiveButton(R.string.button_label_unmute_notifications, (dialog, which) -> App.runThread(() -> {
                            identityObserver.ownedIdentity.prefMuteNotifications = false;
                            AppDatabase.getInstance().ownedIdentityDao().updateMuteNotifications(identityObserver.ownedIdentity.bytesOwnedIdentity, identityObserver.ownedIdentity.prefMuteNotifications, null);
                        }))
                        .setNegativeButton(R.string.button_label_cancel, null);

                if (identityObserver.ownedIdentity.prefMuteNotificationsTimestamp == null) {
                    builder.setMessage(R.string.dialog_message_unmute_notifications);
                } else {
                    builder.setMessage(getString(R.string.dialog_message_unmute_notifications_muted_until, StringUtils.getLongNiceDateString(this, identityObserver.ownedIdentity.prefMuteNotificationsTimestamp)));
                }
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_mute) {
            MuteNotificationDialog muteNotificationDialog = new MuteNotificationDialog(this, (Long muteExpirationTimestamp, boolean muteWholeProfile) -> App.runThread(() -> {
                if (identityObserver.ownedIdentity != null) {
                    identityObserver.ownedIdentity.prefMuteNotifications = true;
                    identityObserver.ownedIdentity.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                    AppDatabase.getInstance().ownedIdentityDao().updateMuteNotifications(identityObserver.ownedIdentity.bytesOwnedIdentity, identityObserver.ownedIdentity.prefMuteNotifications, identityObserver.ownedIdentity.prefMuteNotificationsTimestamp);
                }
            }), MuteNotificationDialog.MuteType.PROFILE);

            muteNotificationDialog.show();
            return true;
        } else if (itemId == R.id.action_neutral_notification) {
            if (identityObserver.ownedIdentity != null) {
                if (identityObserver.ownedIdentity.prefShowNeutralNotificationWhenHidden) {
                    App.runThread(() -> AppDatabase.getInstance().ownedIdentityDao().updateShowNeutralNotificationWhenHidden(identityObserver.ownedIdentity.bytesOwnedIdentity, false));
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_neutral_notification_when_hidden)
                            .setMessage(R.string.dialog_message_neutral_notification_when_hidden)
                            .setPositiveButton(R.string.button_label_activate, (dialog, which) -> App.runThread(() -> AppDatabase.getInstance().ownedIdentityDao().updateShowNeutralNotificationWhenHidden(identityObserver.ownedIdentity.bytesOwnedIdentity, true)))
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                }
            }
            return true;
        } else if (itemId == R.id.action_rename) {
            if (identityObserver.ownedIdentity != null) {
                EditOwnedIdentityDetailsDialogFragment dialogFragment = EditOwnedIdentityDetailsDialogFragment.newInstance(
                        this,
                        identityObserver.ownedIdentity.bytesOwnedIdentity,
                        latestDetails,
                        identityObserver.ownedIdentity.customDisplayName,
                        identityObserver.ownedIdentity.unlockPassword != null,
                        identityObserver.ownedIdentity.keycloakManaged,
                        identityObserver.ownedIdentity.active,
                        () -> identityObserver.reload());
                dialogFragment.show(getSupportFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.action_refresh_subscription_status) {
            if (identityObserver.ownedIdentity == null) {
                return true;
            }
            AppSingleton.getEngine().recreateServerSession(identityObserver.ownedIdentity.bytesOwnedIdentity);
            return true;
        } else if (itemId == R.id.action_unbind_from_keycloak) {
            if (identityObserver.ownedIdentity != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unbind_from_keycloak)
                        .setMessage(R.string.dialog_message_unbind_from_keycloak)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                            KeycloakManager.getInstance().unregisterKeycloakManagedIdentity(identityObserver.ownedIdentity.bytesOwnedIdentity);
                            AppSingleton.getEngine().unbindOwnedIdentityFromKeycloak(identityObserver.ownedIdentity.bytesOwnedIdentity);
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_delete_owned_identity) {
            App.runThread(() -> {
                OwnedIdentity ownedIdentity = identityObserver.ownedIdentity;
                if (ownedIdentity == null) {
                    return;
                }

                // first check if this is the last profile (or last unhidden profile)
                int otherNotHiddenOwnedIdentityCount = AppDatabase.getInstance().ownedIdentityDao().countNotHidden() - (ownedIdentity.isHidden() ? 0 : 1);

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle((otherNotHiddenOwnedIdentityCount >= 1) ? R.string.dialog_title_delete_profile : R.string.dialog_title_delete_last_profile)
                        .setMessage((otherNotHiddenOwnedIdentityCount >= 1) ? R.string.dialog_message_delete_profile : R.string.dialog_message_delete_last_profile)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialogInterface, int which) -> showDeleteProfileDialog(ownedIdentity, otherNotHiddenOwnedIdentityCount < 1));
                runOnUiThread(() -> builder.create().show());
            });
            return true;
        } else if (itemId == R.id.action_debug_information) {
            OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
            if (ownedIdentity != null) {
                StringBuilder sb = new StringBuilder();
                try {
                    Identity ownIdentity = Identity.of(ownedIdentity.bytesOwnedIdentity);
                    sb.append(getString(R.string.debug_label_server)).append(" ");
                    sb.append(ownIdentity.getServer()).append("\n\n");
                } catch (DecodingException ignored) {}
                sb.append(getString(R.string.debug_label_identity_link)).append("\n");
                sb.append(new ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName).getUrlRepresentation()).append("\n\n");
                sb.append(getString(R.string.debug_label_capabilities)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_continuous_gathering, ownedIdentity.capabilityWebrtcContinuousIce)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_one_to_one_contacts, ownedIdentity.capabilityOneToOneContacts)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_groups_v2, ownedIdentity.capabilityGroupsV2)).append("\n");

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
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean notifyOnDelete = true;

    private void showDeleteProfileDialog(OwnedIdentity ownedIdentity, boolean deleteAllHiddenOwnedIdentities) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_delete_profile, null);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch notifySwitch = dialogView.findViewById(R.id.notify_contacts_switch);
        notifySwitch.setOnCheckedChangeListener((CompoundButton compoundButton, boolean checked) -> notifyOnDelete = checked);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button deleteButton = dialogView.findViewById(R.id.delete_button);
        deleteButton.setEnabled(false);
        EditText typeDeleteEditText = dialogView.findViewById(R.id.type_delete_edit_text);
        typeDeleteEditText.addTextChangedListener(new TextChangeListener() {
            final String target = getString(R.string.text_delete_capitalized);

            @Override
            public void afterTextChanged(Editable s) {
                deleteButton.setEnabled(s != null && target.equals(s.toString().toUpperCase()));
            }
        });
        TextView explanationTextView = dialogView.findViewById(R.id.delete_dialog_confirmation_explanation);
        explanationTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        if (ownedIdentity.active) {
            explanationTextView.setText(R.string.explanation_delete_owned_identity);
            notifySwitch.setChecked(true);
        } else {
            explanationTextView.setText(R.string.explanation_delete_inactive_owned_identity);
            notifySwitch.setChecked(false);
        }

        AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_delete_profile_method)
                .setView(dialogView);
        final Dialog dialog = builder.create();
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        deleteButton.setOnClickListener(v -> App.runThread(() -> {
            List<byte[]> bytesOwnedIdentities = new ArrayList<>();
            bytesOwnedIdentities.add(ownedIdentity.bytesOwnedIdentity);
            if (deleteAllHiddenOwnedIdentities) {
                List<OwnedIdentity> hiddenOwnedIdentities = AppDatabase.getInstance().ownedIdentityDao().getAllHidden();
                for (OwnedIdentity hiddenOwnedIdentity: hiddenOwnedIdentities) {
                    bytesOwnedIdentities.add(hiddenOwnedIdentity.bytesOwnedIdentity);
                }
            }

            if (notifyOnDelete) {
                try {
                    for (byte[] bytesOwnedIdentity: bytesOwnedIdentities) {
                        AppSingleton.getEngine().deleteOwnedIdentityAndNotifyContacts(bytesOwnedIdentity);
                    }
                    runOnUiThread(dialog::dismiss);
                    for (byte[] bytesOwnedIdentity: bytesOwnedIdentities) {
                        App.runThread(new DeleteOwnedIdentityAndEverythingRelatedToItTask(bytesOwnedIdentity));
                    }
                    App.toast(R.string.toast_message_profile_deleted, Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                }
            } else {
                try {
                    for (byte[] bytesOwnedIdentity: bytesOwnedIdentities) {
                        AppSingleton.getEngine().deleteOwnedIdentity(bytesOwnedIdentity);
                    }
                    runOnUiThread(dialog::dismiss);
                    for (byte[] bytesOwnedIdentity: bytesOwnedIdentities) {
                        App.runThread(new DeleteOwnedIdentityAndEverythingRelatedToItTask(bytesOwnedIdentity));
                    }
                    App.toast(R.string.toast_message_profile_deleted, Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                }
            }
        }));

        dialog.show();
    }

    public void displayDetails(OwnedIdentity ownedIdentity) {
        if (ownedIdentity == null) {
            finish();
            return;
        }

        keycloakManaged = ownedIdentity.keycloakManaged;
        invalidateOptionsMenu();

        myIdInitialView.setOwnedIdentity(ownedIdentity);
        myIdNameTextView.setText(ownedIdentity.getCustomDisplayName());

        if (ownedIdentity.active) {
            inactiveCardView.setVisibility(View.GONE);
        } else {
            inactiveCardView.setVisibility(View.VISIBLE);
        }

        SubscriptionStatusFragment subscriptionStatusFragment = SubscriptionStatusFragment.newInstance(ownedIdentity.bytesOwnedIdentity, ownedIdentity.getApiKeyStatus(), ownedIdentity.apiKeyExpirationTimestamp, ownedIdentity.getApiKeyPermissions(), false, !keycloakManaged);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.subscription_status_placeholder, subscriptionStatusFragment);
        transaction.commit();

        try {
            JsonIdentityDetailsWithVersionAndPhoto[] jsons = AppSingleton.getEngine().getOwnedIdentityPublishedAndLatestDetails(ownedIdentity.bytesOwnedIdentity);
            if (jsons == null || jsons.length == 0) {
                return;
            }
            publishedDetailsTextViews.removeAllViews();
            JsonIdentityDetails publishedDetails = jsons[0].getIdentityDetails();
            String publishedFirstLine = publishedDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
            String publishedSecondLine = publishedDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
            if (jsons[0].getPhotoUrl() != null) {
                publishedDetailsInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, jsons[0].getPhotoUrl());
            } else {
                publishedDetailsInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, StringUtils.getInitial(publishedFirstLine));
            }
            {
                TextView tv = makeTextView();
                tv.setText(publishedFirstLine);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                publishedDetailsTextViews.addView(tv);
            }
            if (publishedSecondLine != null && publishedSecondLine.length() > 0) {
                TextView tv = makeTextView();
                tv.setText(publishedSecondLine);
                publishedDetailsTextViews.addView(tv);
            }
            if (publishedDetails.getCustomFields() != null) {
                List<String> keys = new ArrayList<>(publishedDetails.getCustomFields().size());
                keys.addAll(publishedDetails.getCustomFields().keySet());
                Collections.sort(keys);
                for (String key: keys) {
                    TextView tv = makeTextView();
                    String value = publishedDetails.getCustomFields().get(key);
                    SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(spannableString);
                    publishedDetailsTextViews.addView(tv);
                }
            }

            // update OwnedIdentity photoUrl if not in sync
            if ((ownedIdentity.photoUrl == null && jsons[0].getPhotoUrl() != null) ||
                    (ownedIdentity.photoUrl != null && !ownedIdentity.photoUrl.equals(jsons[0].getPhotoUrl()))) {
                App.runThread(() -> {
                    ownedIdentity.photoUrl = jsons[0].getPhotoUrl();
                    AppDatabase.getInstance().ownedIdentityDao().updateIdentityDetailsAndPhoto(ownedIdentity.bytesOwnedIdentity, ownedIdentity.identityDetails, ownedIdentity.displayName, ownedIdentity.photoUrl);
                });
            }

            if (jsons.length == 2) {
                latestDetails = jsons[1];
                latestDetailsCardView.setVisibility(View.VISIBLE);
                latestDetailsTextViews.removeAllViews();
                JsonIdentityDetails latestDetails = jsons[1].getIdentityDetails();
                String latestFirstLine  = latestDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                String latestSecondLine = latestDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                if (jsons[1].getPhotoUrl() != null) {
                    latestDetailsInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, jsons[1].getPhotoUrl());
                } else {
                    latestDetailsInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, StringUtils.getInitial(latestFirstLine));
                }
                {
                    TextView tv = makeTextView();
                    tv.setText(latestFirstLine);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    if (!latestFirstLine.equals(publishedFirstLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    latestDetailsTextViews.addView(tv);
                }
                if (latestSecondLine != null && latestSecondLine.length() > 0) {
                    TextView tv = makeTextView();
                    tv.setText(latestSecondLine);
                    if (!latestSecondLine.equals(publishedSecondLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    latestDetailsTextViews.addView(tv);
                }
                if (latestDetails.getCustomFields() != null) {
                    List<String> keys = new ArrayList<>(latestDetails.getCustomFields().size());
                    keys.addAll(latestDetails.getCustomFields().keySet());
                    Collections.sort(keys);
                    for (String key: keys) {
                        TextView tv = makeTextView();
                        String value = latestDetails.getCustomFields().get(key);
                        if (value == null) {
                            continue;
                        }
                        SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                        if (!(publishedDetails.getCustomFields() != null && value.equals(publishedDetails.getCustomFields().get(key)))) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        latestDetailsTextViews.addView(tv);
                    }
                }

                if (ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                }
            } else {
                latestDetails = jsons[0];
                latestDetailsCardView.setVisibility(View.GONE);

                if (ownedIdentity.unpublishedDetails != OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW;
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                }
            }
        } catch (Exception e) {
            // nothing to do
        }
    }

    private TextView makeTextView() {
        TextView tv = new AppCompatTextView(this);
        tv.setTextColor(primary700);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setMaxLines(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, getResources().getDimensionPixelSize(R.dimen.identity_details_margin), 0, 0);
        tv.setLayoutParams(params);
        return tv;
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
                App.showMainActivityTab(this, MainActivity.DISCUSSIONS_TAB);
            }
            finish();
        }
    }

    @Override
    public void onClick(View view) {
        byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
        if (bytesOwnedIdentity == null) {
            return;
        }

        int id = view.getId();
        if (id == R.id.add_contact_button) {
            startActivity(new Intent(this, PlusButtonActivity.class));
        } else if (id == R.id.button_publish) {
            AppSingleton.getEngine().publishLatestIdentityDetails(bytesOwnedIdentity);
        } else if (id == R.id.button_discard) {
            AppSingleton.getEngine().discardLatestIdentityDetails(bytesOwnedIdentity);
            identityObserver.reload();
        } else if (id == R.id.button_reactivate_identity) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_identity_reactivation_confirmation)
                    .setMessage(R.string.dialog_message_identity_reactivation_confirmation)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                        if (identityObserver.ownedIdentity != null) {
                            try {
                                AppSingleton.getEngine().registerToPushNotification(identityObserver.ownedIdentity.bytesOwnedIdentity, AppSingleton.retrieveFirebaseToken(), true, false);
                            } catch (Exception e) {
                                App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                            }
                        }
                    });
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

    class IdentityObserver implements Observer<OwnedIdentity> {
        @Nullable private OwnedIdentity ownedIdentity = null;

        @Override
        public void onChanged(OwnedIdentity ownedIdentity) {
            if (this.ownedIdentity != null && (ownedIdentity == null || !Arrays.equals(ownedIdentity.bytesOwnedIdentity, this.ownedIdentity.bytesOwnedIdentity))) {
                // the owned identity change --> leave the activity
                finish();
            } else {
                this.ownedIdentity = ownedIdentity;
                reload();
            }
        }

        public void reload() {
            latestDetails = null;
            displayDetails(ownedIdentity);
        }
    }
}
