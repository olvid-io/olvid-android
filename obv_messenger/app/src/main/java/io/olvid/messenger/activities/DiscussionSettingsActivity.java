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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.ImageViewPreference;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.MuteNotificationDialog;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.tasks.RemoveDiscussionBackgroundImageTask;
import io.olvid.messenger.databases.tasks.SetDiscussionBackgroundImageTask;
import io.olvid.messenger.fragments.dialog.ColorPickerDialogFragment;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.DiscussionSettingsViewModel;


public class DiscussionSettingsActivity extends LockableActivity {
    public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id";
    public static final String BYTES_GROUP_OWNED_AND_UID_INTENT_EXTRA = "bytes_group_owned_and_uid";
    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity_intent_extra";
    public static final String LOCKED_INTENT_EXTRA = "locked_intent_extra";
    public static final String SCROLL_TO_EPHEMERAL = "scroll_to_ephemeral";

    public static final String PREF_KEY_DISCUSSION_READ_RECEIPT = "pref_key_discussion_read_receipt";
    public static final String PREF_KEY_DISCUSSION_COLOR = "pref_key_discussion_color";
    public static final String PREF_KEY_DISCUSSION_BACKGROUND_IMAGE = "pref_key_discussion_background_image";
    public static final String PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS = "pref_key_discussion_mute_notifications";
    public static final String PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND = "pref_key_discussion_auto_open_limited_visibility_inbound";
    public static final String PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES = "pref_key_discussion_retain_wiped_outbound_messages";

    public static final String PREF_KEY_DISCUSSION_CATEGORY_RETENTION_POLICY = "pref_key_discussion_category_retention_policy";
    public static final String PREF_KEY_DISCUSSION_RETENTION_COUNT = "pref_key_discussion_retention_count";
    public static final String PREF_KEY_DISCUSSION_RETENTION_DURATION = "pref_key_discussion_retention_duration";

    public static final String PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS = "pref_key_discussion_category_shared_ephemeral_settings";
    public static final String PREF_KEY_DISCUSSION_READ_ONCE = "pref_key_discussion_read_once";
    public static final String PREF_KEY_DISCUSSION_VISIBILITY_DURATION = "pref_key_discussion_visibility_duration";
    public static final String PREF_KEY_DISCUSSION_EXISTENCE_DURATION = "pref_key_discussion_existence_duration";

    private SettingsFragment settingsFragment;
    private DiscussionSettingsViewModel discussionSettingsViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        discussionSettingsViewModel = new ViewModelProvider(this).get(DiscussionSettingsViewModel.class);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        LiveData<DiscussionCustomization> discussionCustomizationLiveData = discussionSettingsViewModel.getDiscussionCustomization();
        LiveData<Group> groupLiveData = discussionSettingsViewModel.getDiscussionGroup();


        settingsFragment = new SettingsFragment();
        settingsFragment.setDiscussionSettingsViewModel(discussionSettingsViewModel);
        settingsFragment.setPreferenceDataStore(new DiscussionSettingsDataStore());

        discussionCustomizationLiveData.observe(this, settingsFragment);
        groupLiveData.observe(this, settingsFragment::onGroupChanged);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, settingsFragment).commit();

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(DISCUSSION_ID_INTENT_EXTRA) && intent.hasExtra(LOCKED_INTENT_EXTRA) && intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)) {
            long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
            byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
            boolean locked = intent.getBooleanExtra(LOCKED_INTENT_EXTRA, false);
            discussionSettingsViewModel.setBytesOwnedIdentity(bytesOwnedIdentity);
            if (intent.hasExtra(BYTES_GROUP_OWNED_AND_UID_INTENT_EXTRA)) {
                byte[] bytesGroupOwnerAndUid = intent.getByteArrayExtra(BYTES_GROUP_OWNED_AND_UID_INTENT_EXTRA);
                discussionSettingsViewModel.setBytesGroupOwnerAndUid(bytesGroupOwnerAndUid);
            }
            discussionSettingsViewModel.setLocked(locked);
            discussionSettingsViewModel.setDiscussionId(discussionId); // set after bytesGroupOwnerAndUid to be sure the observer is called once the group is set

            if (intent.hasExtra(SCROLL_TO_EPHEMERAL)) {
                settingsFragment.scrollToPreference(PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS);
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (discussionSettingsViewModel.isSettingsModified()) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_shared_ephemeral_settings_modified)
                    .setMessage(R.string.dialog_message_shared_ephemeral_settings_modified)
                    .setNegativeButton(R.string.button_label_discard, null)
                    .setPositiveButton(R.string.button_label_update, (dialog, which) -> discussionSettingsViewModel.updateCustomizationAndNotifyPeers())
                    .setOnDismissListener(dialog -> super.onBackPressed());
            builder.create().show();
        } else {
            super.onBackPressed();
        }
    }


    public static class SettingsFragment extends PreferenceFragmentCompat implements Observer<DiscussionCustomization> {
        ImageViewPreference colorPickerPreference;
        ImageViewPreference backgroundImagePreference;

        ListPreference readReceiptPreference;
        NoClickSwitchPreference muteNotificationsPreference;
        ListPreference autoOpenLimitedVisibilityPreference;
        ListPreference retainWipedOutboundMessagesPreference;

        PreferenceCategory retentionPolicyCategory;
        EditTextPreference discussionRetentionCountPreference;
        ListPreference discussionRetentionDurationPreference;

        PreferenceCategory sharedSettingsCategory;
        SwitchPreference readOncePreference;
        ListPreference visibilityDurationPreference;
        ListPreference existenceDurationPreference;

        private DiscussionSettingsDataStore discussionSettingsDataStore;
        private DiscussionSettingsViewModel discussionSettingsViewModel;

        public static final int REQUEST_CODE_BACKGROUND_IMAGE = 18;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            if (discussionSettingsViewModel == null) {
                return;
            }
            if (discussionSettingsDataStore != null) {
                getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);
            }
            boolean locked = discussionSettingsViewModel.isLocked();

            addPreferencesFromResource(R.xml.discussion_preferences);
            PreferenceScreen screen = getPreferenceScreen();

            readReceiptPreference = screen.findPreference(PREF_KEY_DISCUSSION_READ_RECEIPT);
            if (readReceiptPreference != null) {
                CharSequence[] readReceiptPreferenceEntries = readReceiptPreference.getEntries();
                if (SettingsActivity.getDefaultSendReadReceipt()) {
                    readReceiptPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
                } else {
                    readReceiptPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
                }
                if (locked) {
                    readReceiptPreference.setEnabled(false);
                }
            }

            colorPickerPreference = screen.findPreference(PREF_KEY_DISCUSSION_COLOR);
            if (colorPickerPreference != null) {
                colorPickerPreference.setOnPreferenceClickListener(preference -> {
                    if (discussionSettingsViewModel != null && discussionSettingsViewModel.getDiscussionId() != null) {
                        ColorPickerDialogFragment colorPickerDialogFragment = ColorPickerDialogFragment.newInstance(discussionSettingsViewModel.getDiscussionId());
                        colorPickerDialogFragment.show(getChildFragmentManager(), "dialog");
                        return true;
                    }
                    return false;
                });
            }

            backgroundImagePreference = findPreference(PREF_KEY_DISCUSSION_BACKGROUND_IMAGE);
            if (backgroundImagePreference != null) {
                backgroundImagePreference.setOnPreferenceClickListener(preference -> {
                    if (discussionSettingsViewModel != null) {
                        DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                        if (discussionCustomization != null && discussionCustomization.backgroundImageUrl != null) {
                            App.runThread(new RemoveDiscussionBackgroundImageTask(discussionCustomization.discussionId));
                        } else {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                    .setType("image/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                            App.startActivityForResult(SettingsFragment.this, intent, REQUEST_CODE_BACKGROUND_IMAGE);
                        }
                        return true;
                    }
                    return false;
                });
            }

            muteNotificationsPreference = findPreference(PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS);
            if (muteNotificationsPreference != null) {
                muteNotificationsPreference.setOnPreferenceClickListener(preference -> {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                        discussionSettingsDataStore.putBoolean(PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS, false);
                    } else {
                        Context context = getContext();
                        Long discussionId = discussionSettingsViewModel.getDiscussionId();
                        if (discussionId != null && context != null) {
                            MuteNotificationDialog muteNotificationDialog = new MuteNotificationDialog(context, (Long muteExpirationTimestamp, boolean muteWholeProfile) -> App.runThread(() -> {
                                DiscussionCustomization discussionCust = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
                                boolean insert = false;
                                if (discussionCust == null) {
                                    discussionCust = new DiscussionCustomization(discussionId);
                                    insert = true;
                                }
                                discussionCust.prefMuteNotifications = true;
                                discussionCust.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                                if (insert) {
                                    AppDatabase.getInstance().discussionCustomizationDao().insert(discussionCust);
                                } else {
                                    AppDatabase.getInstance().discussionCustomizationDao().update(discussionCust);
                                }
                            }), MuteNotificationDialog.MuteType.DISCUSSION);

                            muteNotificationDialog.show();
                        }
                    }
                    return true;
                });
                if (locked) {
                    muteNotificationsPreference.setEnabled(false);
                }
            }

            autoOpenLimitedVisibilityPreference = screen.findPreference(PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND);
            if (autoOpenLimitedVisibilityPreference != null) {
                CharSequence[] autoOpenLimitedVisibilityPreferenceEntries = autoOpenLimitedVisibilityPreference.getEntries();
                if (SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages()) {
                    autoOpenLimitedVisibilityPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
                } else {
                    autoOpenLimitedVisibilityPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
                }
                if (locked) {
                    autoOpenLimitedVisibilityPreference.setEnabled(false);
                }
            }

            retainWipedOutboundMessagesPreference = screen.findPreference(PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES);
            if (retainWipedOutboundMessagesPreference != null) {
                CharSequence[] retainWipedOutboundMessagesPreferenceEntries = retainWipedOutboundMessagesPreference.getEntries();
                if (SettingsActivity.getDefaultRetainWipedOutboundMessages()) {
                    retainWipedOutboundMessagesPreferenceEntries[0] = getString(R.string.pref_text_app_default_true);
                } else {
                    retainWipedOutboundMessagesPreferenceEntries[0] = getString(R.string.pref_text_app_default_false);
                }
                if (locked) {
                    retainWipedOutboundMessagesPreference.setEnabled(false);
                }
            }


            retentionPolicyCategory = screen.findPreference(PREF_KEY_DISCUSSION_CATEGORY_RETENTION_POLICY);
            if (retentionPolicyCategory != null) {
                discussionRetentionCountPreference = screen.findPreference(PREF_KEY_DISCUSSION_RETENTION_COUNT);
                if (discussionRetentionCountPreference != null) {
                    final Long defaultRetentionCount = SettingsActivity.getDefaultDiscussionRetentionCount();
                    String defaultRetentionString;
                    if (defaultRetentionCount == null) {
                        defaultRetentionString = getString(R.string.pref_discussion_retention_count_summary_null);
                        discussionRetentionCountPreference.setDialogMessage(R.string.pref_discussion_retention_count_dialog_message_default_null);
                    } else {
                        defaultRetentionString = getString(R.string.pref_discussion_retention_count_summary, defaultRetentionCount);
                        discussionRetentionCountPreference.setDialogMessage(getString(R.string.pref_discussion_retention_count_dialog_message_default_number, defaultRetentionCount));
                    }

                    discussionRetentionCountPreference.setSummaryProvider(preference -> {
                        EditTextPreference editTextPreference = (EditTextPreference) preference;
                        String value = editTextPreference.getText();
                        if (value == null || value.length() == 0) {
                            return getString(R.string.pref_text_app_default_string, defaultRetentionString);
                        }
                        try {
                            long count = Long.parseLong(value);
                            if (count == 0) {
                                return getString(R.string.pref_discussion_retention_count_summary_null);
                            } else {
                                return getString(R.string.pref_discussion_retention_count_summary, count);
                            }
                        } catch (Exception e) {
                            return getString(R.string.pref_discussion_retention_count_summary_null);
                        }
                    });
                    discussionRetentionCountPreference.setOnBindEditTextListener(editText -> {
                        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                        editText.setHint(R.string.pref_discussion_retention_count_hint);
                        editText.selectAll();
                    });
                    discussionRetentionCountPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                        Long maxMessages = null;
                        if (newValue == null || ((String) newValue).length() == 0) {
                            maxMessages = defaultRetentionCount;
                        } else {
                            try {
                                maxMessages = Long.parseLong((String) newValue);
                            } catch (Exception e) {
                                // do nothing
                            }
                        }
                        if (maxMessages == null || maxMessages == 0) {
                            return true;
                        }
                        long finalMaxMessages = maxMessages;
                        App.runThread(() -> {
                            int count = AppDatabase.getInstance().messageDao().countExpirableMessagesInDiscussion(discussionSettingsViewModel.getDiscussionId());
                            int toDelete = (int) ((long) count - finalMaxMessages);
                            if (toDelete > 0) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                            .setTitle(R.string.dialog_title_confirm_retention_policy)
                                            .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, toDelete, toDelete))
                                            .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> discussionSettingsDataStore.putString(PREF_KEY_DISCUSSION_RETENTION_COUNT, (String) newValue))
                                            .setNegativeButton(R.string.button_label_cancel, null);
                                    builder.create().show();
                                });
                            } else {
                                discussionSettingsDataStore.putString(PREF_KEY_DISCUSSION_RETENTION_COUNT, (String) newValue);
                            }
                        });
                        return false;
                    });
                }

                discussionRetentionDurationPreference = screen.findPreference(PREF_KEY_DISCUSSION_RETENTION_DURATION);
                if (discussionRetentionDurationPreference != null) {
                    Long defaultRetentionDuration = SettingsActivity.getDefaultDiscussionRetentionDuration();
                    String defaultRetentionString;
                    String shortDefaultRetentionString;
                    if (defaultRetentionDuration == null) {
                        defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary_null);
                        shortDefaultRetentionString = defaultRetentionString;
                    } else {
                        int index = discussionRetentionDurationPreference.findIndexOfValue(Long.toString(defaultRetentionDuration));
                        if (index == -1) {
                            defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary_null);
                            shortDefaultRetentionString = defaultRetentionString;
                        } else {
                            defaultRetentionString = getString(R.string.pref_discussion_retention_duration_summary, discussionRetentionDurationPreference.getEntries()[index]);
                            shortDefaultRetentionString = discussionRetentionDurationPreference.getEntries()[index].toString();
                        }
                    }

                    CharSequence[] entries = discussionRetentionDurationPreference.getEntries();
                    entries[0] = getString(R.string.pref_text_app_default_string, shortDefaultRetentionString);

                    discussionRetentionDurationPreference.setSummaryProvider((Preference preference) -> {
                        ListPreference listPreference = (ListPreference) preference;
                        String value = listPreference.getValue();
                        CharSequence entry = listPreference.getEntry();
                        if (value == null || "null".equals(value)) {
                            return getString(R.string.pref_text_app_default_string, defaultRetentionString);
                        } else if ("0".equals(value)) {
                            return getString(R.string.pref_discussion_retention_duration_summary_null);
                        }
                        return getString(R.string.pref_discussion_retention_duration_summary, entry);
                    });
                    discussionRetentionDurationPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                        Long retentionDuration = null;
                        if (newValue == null || "null".equals(newValue)) {
                            retentionDuration = defaultRetentionDuration;
                        } else {
                            try {
                                retentionDuration = Long.parseLong((String) newValue);
                            } catch (Exception e) {
                                // do nothing
                            }
                        }
                        if (retentionDuration == null || retentionDuration == 0) {
                            return true;
                        }
                        Long finalRetentionDuration = retentionDuration;
                        App.runThread(() -> {
                            int toDelete = AppDatabase.getInstance().messageDao().countOldDiscussionMessages(discussionSettingsViewModel.getDiscussionId(), System.currentTimeMillis() - 1000L * finalRetentionDuration);
                            if (toDelete > 0) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                            .setTitle(R.string.dialog_title_confirm_retention_policy)
                                            .setMessage(getResources().getQuantityString(R.plurals.dialog_message_confirm_retention_policy, toDelete, toDelete))
                                            .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> discussionSettingsDataStore.putString(PREF_KEY_DISCUSSION_RETENTION_DURATION, (String) newValue))
                                            .setNegativeButton(R.string.button_label_cancel, null);
                                    builder.create().show();
                                });
                            } else {
                                discussionSettingsDataStore.putString(PREF_KEY_DISCUSSION_RETENTION_DURATION, (String) newValue);
                            }
                        });
                        return false;
                    });

                }
            }

            sharedSettingsCategory = screen.findPreference(PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS);
            if (sharedSettingsCategory != null) {
                readOncePreference = screen.findPreference(PREF_KEY_DISCUSSION_READ_ONCE);
                visibilityDurationPreference = screen.findPreference(PREF_KEY_DISCUSSION_VISIBILITY_DURATION);
                existenceDurationPreference = screen.findPreference(PREF_KEY_DISCUSSION_EXISTENCE_DURATION);
                if (discussionSettingsViewModel.isGroup()) {
                    onGroupChanged(discussionSettingsViewModel.getDiscussionGroup().getValue());
                }
                if (locked) {
                    if (readOncePreference != null) {
                        readOncePreference.setEnabled(false);
                    }
                    if (visibilityDurationPreference != null) {
                        visibilityDurationPreference.setEnabled(false);
                    }
                    if (existenceDurationPreference != null) {
                        existenceDurationPreference.setEnabled(false);
                    }
                }
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            if (data == null) {
                return;
            }
            if (requestCode == REQUEST_CODE_BACKGROUND_IMAGE) {
                if (resultCode == RESULT_OK) {
                    Long discussionId = discussionSettingsViewModel.getDiscussionId();
                    Uri backgroundImageUrl = data.getData();
                    if (discussionId != null && backgroundImageUrl != null) {
                        App.runThread(new SetDiscussionBackgroundImageTask(backgroundImageUrl, discussionId));
                    }
                }
            }
        }

        @Override
        public void onChanged(@Nullable DiscussionCustomization discussionCustomization) {
            if (readReceiptPreference != null) {
                readReceiptPreference.setValue(discussionSettingsDataStore.getString(PREF_KEY_DISCUSSION_READ_RECEIPT, "null"));
            }
            if (colorPickerPreference != null) {
                if (discussionCustomization != null) {
                    DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                    colorPickerPreference.setColor(colorJson);
                }
            }
            if (backgroundImagePreference != null) {
                if (discussionCustomization == null || discussionCustomization.backgroundImageUrl == null) {
                    backgroundImagePreference.setImage(null);
                    backgroundImagePreference.setSummary(R.string.pref_discussion_background_image_click_to_choose_summary);
                } else {
                    Bitmap bitmap = BitmapFactory.decodeFile(App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl));
                    if (bitmap.getByteCount() < SelectDetailsPhotoViewModel.MAX_BITMAP_SIZE) {
                        backgroundImagePreference.setImage(App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl));
                    }
                    backgroundImagePreference.setSummary(R.string.pref_discussion_background_image_click_to_remove_summary);
                }
            }
            if (muteNotificationsPreference != null) {
                boolean shouldMute = discussionCustomization != null && discussionCustomization.shouldMuteNotifications();
                muteNotificationsPreference.setChecked(shouldMute);
                if (!shouldMute) {
                    muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_summary));
                } else if (discussionCustomization.prefMuteNotificationsTimestamp == null) {
                    muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_on_summary));
                } else {
                    CharSequence dateString = App.getNiceDateString(getContext(), discussionCustomization.prefMuteNotificationsTimestamp);
                    muteNotificationsPreference.setSummary(getString(R.string.pref_discussion_mute_notifications_on_until_summary, dateString));
                }
            }

            if (autoOpenLimitedVisibilityPreference != null) {
                autoOpenLimitedVisibilityPreference.setValue(discussionSettingsDataStore.getString(PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND, "null"));
            }

            if (retainWipedOutboundMessagesPreference != null) {
                retainWipedOutboundMessagesPreference.setValue(discussionSettingsDataStore.getString(PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES, "null"));
            }

            if (discussionRetentionCountPreference != null) {
                discussionRetentionCountPreference.setText(discussionSettingsDataStore.getString(PREF_KEY_DISCUSSION_RETENTION_COUNT, ""));
            }

            if (discussionRetentionDurationPreference != null) {
                discussionRetentionDurationPreference.setValue(discussionSettingsDataStore.getString(PREF_KEY_DISCUSSION_RETENTION_DURATION, "null"));
            }

            // if viewmodel has not recorded any modifications, update its content
            discussionSettingsViewModel.updateSettingsFromCustomization(discussionCustomization);

            if (readOncePreference != null) {
                readOncePreference.setChecked(discussionSettingsViewModel.getSettingsReadOnce());
            }
            if (visibilityDurationPreference != null) {
                if (discussionSettingsViewModel.getSettingsVisibilityDuration() == null) {
                    visibilityDurationPreference.setValue("null");
                    visibilityDurationPreference.setSummary(R.string.pref_discussion_visibility_duration_summary_null);
                } else {
                    visibilityDurationPreference.setValue(Long.toString(discussionSettingsViewModel.getSettingsVisibilityDuration()));
                    visibilityDurationPreference.setSummary(getString(R.string.pref_discussion_visibility_duration_summary, visibilityDurationPreference.getEntry()));
                }
            }
            if (existenceDurationPreference != null) {
                if (discussionSettingsViewModel.getSettingsExistenceDuration() == null) {
                    existenceDurationPreference.setValue("null");
                    existenceDurationPreference.setSummary(R.string.pref_discussion_existence_duration_summary_null);
                } else {
                    existenceDurationPreference.setValue(Long.toString(discussionSettingsViewModel.getSettingsExistenceDuration()));
                    existenceDurationPreference.setSummary(getString(R.string.pref_discussion_existence_duration_summary, existenceDurationPreference.getEntry()));
                }
            }
        }

        public void onGroupChanged(Group group) {
            if (!discussionSettingsViewModel.isGroup()
                    || (group != null && group.bytesGroupOwnerIdentity == null)) {
                // own group or no group
                if (sharedSettingsCategory != null) {
                    sharedSettingsCategory.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary);
                }
                if (readOncePreference != null) {
                    readOncePreference.setEnabled(true);
                }
                if (visibilityDurationPreference != null) {
                    visibilityDurationPreference.setEnabled(true);
                }
                if (existenceDurationPreference != null) {
                    existenceDurationPreference.setEnabled(true);
                }
            } else {
                // joined group
                if (sharedSettingsCategory != null) {
                    sharedSettingsCategory.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary_only_owner);
                }
                if (readOncePreference != null) {
                    readOncePreference.setEnabled(false);
                }
                if (visibilityDurationPreference != null) {
                    visibilityDurationPreference.setEnabled(false);
                }
                if (existenceDurationPreference != null) {
                    existenceDurationPreference.setEnabled(false);
                }
            }
        }

        public void setPreferenceDataStore(DiscussionSettingsDataStore discussionSettingsDataStore) {
            this.discussionSettingsDataStore = discussionSettingsDataStore;
            if (getPreferenceManager() != null) {
                getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);
            }
        }

        public void setDiscussionSettingsViewModel(DiscussionSettingsViewModel discussionSettingsViewModel) {
            this.discussionSettingsViewModel = discussionSettingsViewModel;
        }
    }


    public class DiscussionSettingsDataStore extends PreferenceDataStore {
        @Override
        public void putString(String key, @Nullable String value) {
            if (value == null) {
                return;
            }
            switch (key) {
                case PREF_KEY_DISCUSSION_READ_RECEIPT: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            break;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    switch (value) {
                        case "0":
                            discussionCustomization.prefSendReadReceipt = false;
                            break;
                        case "1":
                            discussionCustomization.prefSendReadReceipt = true;
                            break;
                        case "null":
                        default:
                            discussionCustomization.prefSendReadReceipt = null;
                            break;
                    }
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
                case PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            break;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    switch (value) {
                        case "0":
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = false;
                            break;
                        case "1":
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = true;
                            break;
                        case "null":
                        default:
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = null;
                            break;
                    }
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
                case PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            break;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    switch (value) {
                        case "0":
                            discussionCustomization.prefRetainWipedOutboundMessages = false;
                            break;
                        case "1":
                            discussionCustomization.prefRetainWipedOutboundMessages = true;
                            break;
                        case "null":
                        default:
                            discussionCustomization.prefRetainWipedOutboundMessages = null;
                            break;
                    }
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
                case PREF_KEY_DISCUSSION_VISIBILITY_DURATION: {
                    Long visibilityDuration = null;
                    if (!"null".equals(value)) {
                        try {
                            visibilityDuration = Long.parseLong(value);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    discussionSettingsViewModel.setSettingsVisibilityDuration(visibilityDuration);
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    settingsFragment.onChanged(discussionCustomization);
                    break;
                }
                case PREF_KEY_DISCUSSION_EXISTENCE_DURATION: {
                    Long existenceDuration = null;
                    if (!"null".equals(value)) {
                        try {
                            existenceDuration = Long.parseLong(value);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    discussionSettingsViewModel.setSettingsExistenceDuration(existenceDuration);
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    settingsFragment.onChanged(discussionCustomization);
                    break;
                }
                case PREF_KEY_DISCUSSION_RETENTION_COUNT: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            break;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    if ("".equals(value)) {
                        discussionCustomization.prefDiscussionRetentionCount = null;
                    } else {
                        try {
                            discussionCustomization.prefDiscussionRetentionCount = Long.parseLong(value);
                        } catch (Exception e) {
                            discussionCustomization.prefDiscussionRetentionCount = null;
                        }
                    }
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
                case PREF_KEY_DISCUSSION_RETENTION_DURATION: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            break;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    if ("null".equals(value)) {
                        discussionCustomization.prefDiscussionRetentionDuration = null;
                    } else {
                        try {
                            discussionCustomization.prefDiscussionRetentionDuration = Long.parseLong(value);
                        } catch (Exception e) {
                            discussionCustomization.prefDiscussionRetentionDuration = null;
                        }
                    }
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
            }
        }

        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            switch (key) {
                case PREF_KEY_DISCUSSION_READ_RECEIPT: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        if (discussionCustomization.prefSendReadReceipt == null) {
                            return "null";
                        }
                        return discussionCustomization.prefSendReadReceipt ? "1" : "0";
                    }
                    return "null";
                }
                case PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        if (discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages == null) {
                            return "null";
                        }
                        return discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages ? "1" : "0";
                    }
                    return "null";
                }
                case PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        if (discussionCustomization.prefRetainWipedOutboundMessages == null) {
                            return "null";
                        }
                        return discussionCustomization.prefRetainWipedOutboundMessages ? "1" : "0";
                    }
                    return "null";
                }
                case PREF_KEY_DISCUSSION_RETENTION_COUNT: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        if (discussionCustomization.prefDiscussionRetentionCount == null) {
                            return "";
                        }
                        return Long.toString(discussionCustomization.prefDiscussionRetentionCount);
                    }
                    return "";
                }
                case PREF_KEY_DISCUSSION_RETENTION_DURATION: {
                        DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                        if (discussionCustomization != null) {
                            if (discussionCustomization.prefDiscussionRetentionDuration == null) {
                                return "null";
                            }
                            return Long.toString(discussionCustomization.prefDiscussionRetentionDuration);
                        }
                        return "null";
                }
                default:
                    return null;
            }
        }


        @Override
        public void putBoolean(String key, boolean value) {
            switch (key) {
                case PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    boolean insert = false;
                    if (discussionCustomization == null) {
                        if (discussionSettingsViewModel.getDiscussionId() == null) {
                            return;
                        }
                        discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
                        insert = true;
                    }
                    discussionCustomization.prefMuteNotifications = value;
                    DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
                    if (insert) {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
                    } else {
                        App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
                    }
                    break;
                }
                case PREF_KEY_DISCUSSION_READ_ONCE: {
                    discussionSettingsViewModel.setSettingsReadOnce(value);
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    settingsFragment.onChanged(discussionCustomization);
                    break;
                }
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            switch (key) {
                case PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS: {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null) {
                        return discussionCustomization.shouldMuteNotifications();
                    }
                    return false;
                }
                case PREF_KEY_DISCUSSION_READ_ONCE: {
                    return discussionSettingsViewModel.getSettingsReadOnce();
                }
                default:
                    return false;
            }
        }
    }
}
