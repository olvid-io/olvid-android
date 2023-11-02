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

package io.olvid.messenger.discussion.settings;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.entity.DiscussionCustomization;


public class DiscussionSettingsActivity extends LockableActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id";
    public static final String SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA = "sub_setting_pref_key_to_open_intent_extra";

    public static final String PREF_KEY_DISCUSSION_CATEGORY_LOCKED_EXPLANATION = "pref_key_discussion_category_locked_explanation";
    public static final String PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS = "pref_key_discussion_category_shared_ephemeral_settings";
    public static final String PREF_KEY_DISCUSSION_CATEGORY_RETENTION_POLICY = "pref_key_discussion_category_retention_policy";
    public static final String PREF_KEY_DISCUSSION_CATEGORY_SEND_RECEIVE = "pref_key_discussion_category_send_receive";

    public static final String PREF_KEY_DISCUSSION_PIN = "pref_key_discussion_pin";
    public static final String PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS = "pref_key_discussion_mute_notifications";

    public static final String PREF_KEY_DISCUSSION_COLOR = "pref_key_discussion_color";
    public static final String PREF_KEY_DISCUSSION_BACKGROUND_IMAGE = "pref_key_discussion_background_image";

    public static final String PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION = "pref_key_discussion_message_custom_notification";
    public static final String PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN = "pref_key_discussion_message_vibration_pattern";
    public static final String PREF_KEY_DISCUSSION_MESSAGE_RINGTONE = "pref_key_discussion_message_ringtone";
    public static final String PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR = "pref_key_discussion_message_led_color";

    public static final String PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION = "pref_key_discussion_call_custom_notification";
    public static final String PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN = "pref_key_discussion_call_vibration_pattern";
    public static final String PREF_KEY_DISCUSSION_CALL_RINGTONE = "pref_key_discussion_call_ringtone";
    public static final String PREF_KEY_DISCUSSION_CALL_USE_FLASH = "pref_key_discussion_call_use_flash";

    public static final String PREF_KEY_DISCUSSION_READ_RECEIPT = "pref_key_discussion_read_receipt";
    public static final String PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND = "pref_key_discussion_auto_open_limited_visibility_inbound";
    public static final String PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES = "pref_key_discussion_retain_wiped_outbound_messages";

    public static final String PREF_KEY_DISCUSSION_RETENTION_COUNT = "pref_key_discussion_retention_count";
    public static final String PREF_KEY_DISCUSSION_RETENTION_DURATION = "pref_key_discussion_retention_duration";

    public static final String PREF_KEY_DISCUSSION_READ_ONCE = "pref_key_discussion_read_once";
    public static final String PREF_KEY_DISCUSSION_VISIBILITY_DURATION = "pref_key_discussion_visibility_duration";
    public static final String PREF_KEY_DISCUSSION_EXISTENCE_DURATION = "pref_key_discussion_existence_duration";


    private DiscussionSettingsViewModel discussionSettingsViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        discussionSettingsViewModel = new ViewModelProvider(this).get(DiscussionSettingsViewModel.class);

        discussionSettingsViewModel.getDiscussionCustomization().observe(this, (DiscussionCustomization discussionCustomization) -> {
            discussionSettingsViewModel.notifySettingsChangedListeners(discussionCustomization);

            // if viewModel has not recorded any modifications, update its content
            discussionSettingsViewModel.updateEphemeralSettingsFromCustomization(discussionCustomization);
            discussionSettingsViewModel.updateNotificationsFromCustomization(discussionCustomization);
        });
        discussionSettingsViewModel.getNonAdminGroupDiscussionLiveData().observe(this, (Boolean nonAdminGroup) -> discussionSettingsViewModel.notifyLockedOrNonGroupAdminChanged());
        discussionSettingsViewModel.getDiscussionLockedLiveData().observe(this, (Boolean locked) -> discussionSettingsViewModel.notifyLockedOrNonGroupAdminChanged());


        DiscussionSettingsHeadersFragment settingsFragment = new DiscussionSettingsHeadersFragment();
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, settingsFragment).commit();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(DISCUSSION_ID_INTENT_EXTRA)) {
            long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
            discussionSettingsViewModel.setDiscussionId(discussionId);


            final String prefKey = intent.getStringExtra(SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA);
            if (prefKey != null) {
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    Preference preference = settingsFragment.findPreference(prefKey);
                    if (preference != null && preference.isEnabled()) {
                        int position = preference.getOrder() - (discussionSettingsViewModel.isLocked() ? 0 : 1);
                        RecyclerView recyclerView = settingsFragment.getListView();
                        if (recyclerView != null) {
                            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                            if (viewHolder != null) {
                                viewHolder.itemView.setPressed(true);
                                handler.postDelayed(() -> onPreferenceStartFragment(settingsFragment, preference), 600);
                            } else {
                                recyclerView.scrollToPosition(position);
                                handler.postDelayed(() -> {
                                    RecyclerView.ViewHolder reViewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                                    if (reViewHolder != null) {
                                        reViewHolder.itemView.setPressed(true);
                                    }
                                }, 100);
                                handler.postDelayed(() -> onPreferenceStartFragment(settingsFragment, preference), 700);
                            }
                        }
                    }
                }, 400);
            }
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
    public void onBackPressed() {
        setTitle(R.string.activity_title_discussion_settings);
        if (discussionSettingsViewModel.isMessageNotificationModified()) {
            discussionSettingsViewModel.saveCustomMessageNotification();
        }
        if (discussionSettingsViewModel.isEphemeralSettingsModified()) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_shared_ephemeral_settings_modified)
                    .setMessage(R.string.dialog_message_shared_ephemeral_settings_modified)
                    .setNegativeButton(R.string.button_label_discard, (dialog, which) -> {
                        discussionSettingsViewModel.discardEphemeralSettings();
                        super.onBackPressed();
                    })
                    .setPositiveButton(R.string.button_label_update, (dialog, which) -> {
                        discussionSettingsViewModel.saveEphemeralSettingsAndNotifyPeers();
                        super.onBackPressed();
                    });
            builder.create().show();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        String prefFragmentName = pref.getFragment();
        if (prefFragmentName == null) {
            return false;
        }
        final Fragment fragment;
        try {
            fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), prefFragmentName);
        } catch (Exception e) {
            return false;
        }

        try {
            // Replace the existing Fragment with the new Fragment
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
                    .replace(android.R.id.content, fragment)
                    .addToBackStack(null)
                    .commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // set the activity title
        setTitle(pref.getTitle());
        return true;
    }
}
