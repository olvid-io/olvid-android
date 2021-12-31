/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.messenger.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.SelectableArrayAdapter;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.services.UnifiedForegroundService;

public class PrivacyPreferenceFragment extends PreferenceFragmentCompat {
    FragmentActivity activity;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_privacy, rootKey);
        activity = requireActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        SwitchPreference screenCapturePreference = screen.findPreference(SettingsActivity.PREF_KEY_PREVENT_SCREEN_CAPTURE);
        if (screenCapturePreference != null) {
            screenCapturePreference.setOnPreferenceChangeListener((Preference pref, Object checked) -> {
                if (activity != null) {
                    Window window = activity.getWindow();
                    if (window != null) {
                        if (checked instanceof Boolean) {
                            if ((Boolean) checked) {
                                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                            }
                        }
                    }
                }
                return true;
            });
        }

        SwitchPreference exposeRecentDiscussionsPreference = screen.findPreference(SettingsActivity.PREF_KEY_EXPOSE_RECENT_DISCUSSIONS);
        if (exposeRecentDiscussionsPreference != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                screen.removePreference(exposeRecentDiscussionsPreference);
            } else {
                exposeRecentDiscussionsPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    App.runThread(() -> {
                        try {
                            // wait 1 second for the setting to actually be updated
                            Thread.sleep(1_000);
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                        ShortcutActivity.startPublishingShareTargets(App.getContext());
                    });
                    return true;
                });
            }
        }

        Preference hiddenProfileClosePolicyPreference = screen.findPreference(SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY);
        if (hiddenProfileClosePolicyPreference != null) {
            updateHiddenProfileClosePolicySummary(hiddenProfileClosePolicyPreference);
            hiddenProfileClosePolicyPreference.setOnPreferenceClickListener((Preference preference) -> {
                showHiddenProfileClosePolicyChooserDialog(activity, () -> updateHiddenProfileClosePolicySummary(hiddenProfileClosePolicyPreference));
                return true;
            });
        }

        SwitchPreference permanentWebSocketPreference = screen.findPreference(SettingsActivity.PREF_KEY_PERMANENT_WEBSOCKET);
        if (permanentWebSocketPreference != null) {
            permanentWebSocketPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                App.runThread(() -> {
                    try {
                        // wait 1 second for the setting to actually be updated
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    activity.startService(new Intent(activity, UnifiedForegroundService.class));
                });
                return true;
            });
        }

        NoClickSwitchPreference disablePushPreference = screen.findPreference(SettingsActivity.PREF_KEY_DISABLE_PUSH_NOTIFICATIONS);
        if (disablePushPreference != null) {
            if (ConnectionResult.SUCCESS != GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)) {
                // google services unavailable --> disable this preference
                disablePushPreference.setChecked(true);
                disablePushPreference.setEnabled(false);
            } else {
                disablePushPreference.setOnPreferenceClickListener((Preference pref) -> {
                    if (pref instanceof NoClickSwitchPreference) {
                        NoClickSwitchPreference preference = (NoClickSwitchPreference) pref;
                        if (preference.isChecked()) {
                            if (preference.callChangeListener(false)) {
                                preference.setChecked(false);
                                App.runThread(App::refreshRegisterToPushNotification);
                            }
                        } else {
                            if (SettingsActivity.usePermanentWebSocket()) {
                                if (preference.callChangeListener(true)) {
                                    preference.setChecked(true);
                                    App.runThread(App::refreshRegisterToPushNotification);
                                }
                            } else {
                                AlertDialog dialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_disable_push_confirmation)
                                        .setMessage(R.string.dialog_message_disable_push_confirmation)
                                        .setNegativeButton(R.string.button_label_cancel, null)
                                        .setNeutralButton(R.string.button_label_enable_permanent_websocket, (DialogInterface dialogInterface, int which) -> {
                                            if (preference.callChangeListener(true)) {
                                                preference.setChecked(true);
                                            }
                                            SettingsActivity.setUsePermanentWebSocket(true);
                                            if (permanentWebSocketPreference != null) {
                                                permanentWebSocketPreference.setChecked(true);
                                            }
                                            activity.startService(new Intent(activity, UnifiedForegroundService.class));
                                            App.runThread(App::refreshRegisterToPushNotification);
                                        })
                                        .setPositiveButton(R.string.button_label_only_disable_push, (DialogInterface dialogInterface, int which) -> {
                                            if (preference.callChangeListener(true)) {
                                                preference.setChecked(true);
                                                App.runThread(App::refreshRegisterToPushNotification);
                                            }
                                        })
                                        .create();
                                dialog.show();
                            }
                        }
                    }
                    return true;
                });
            }
        }
    }

    private static void updateHiddenProfileClosePolicySummary(@NonNull Preference preference) {
        switch (SettingsActivity.getHiddenProfileClosePolicy()) {
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK:
                preference.setSummary(R.string.pref_hidden_profile_close_policy_summary_screen_lock);
                break;
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING:
                preference.setSummary(R.string.pref_hidden_profile_close_policy_summary_manual_switching);
                break;
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND:
                int backgroundGraceDelay = SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelay();
                int index = -1;
                for (int i = 0; i < SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES.length; i++) {
                    if (SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES[i] == backgroundGraceDelay) {
                        index = i;
                        break;
                    }
                }
                if (index == -1) {
                    preference.setSummary(null);
                }
                preference.setSummary(preference.getContext().getString(R.string.pref_hidden_profile_close_policy_summary_background, SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelayLabels(preference.getContext())[index]));
                break;
            case -1:
            default:
                preference.setSummary(R.string.pref_hidden_profile_close_policy_summary_not_set);
        }
    }

    public static void showHiddenProfileClosePolicyChooserDialog(Context context, @Nullable Runnable dismissCallback) {
        Integer initiallySelectedEntry = null;
        switch (SettingsActivity.getHiddenProfileClosePolicy()) {
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING:
                initiallySelectedEntry = 0;
                break;
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK:
                initiallySelectedEntry = 1;
                break;
            case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND:
                initiallySelectedEntry = 2;
                break;
        }

        SelectableArrayAdapter<String> policyAdapter = new SelectableArrayAdapter<>(context, initiallySelectedEntry, new String[]{
                context.getString(R.string.pref_hidden_profile_close_policy_dialog_entry_manual_switching),
                context.getString(R.string.pref_hidden_profile_close_policy_dialog_entry_screen_lock),
                context.getString(R.string.pref_hidden_profile_close_policy_dialog_entry_background),
        });

        AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setTitle(R.string.pref_hidden_profile_close_policy_dialog_title)
                .setAdapter(policyAdapter, (DialogInterface dialog, int which) -> {
                    switch (which) {
                        case 0: { // manual switch
                            SettingsActivity.setHiddenProfileClosePolicy(SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING, 0);
                            break;
                        }
                        case 1: { // screen lock
                            SettingsActivity.setHiddenProfileClosePolicy(SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK, 0);
                            if (!SettingsActivity.useApplicationLockScreen()) {
                                AlertDialog.Builder bd = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_hidden_profile_lock_screen_not_active)
                                        .setMessage(R.string.dialog_message_hidden_profile_lock_screen_not_active)
                                        .setPositiveButton(R.string.button_label_configure_lock_screen, (DialogInterface d, int w) -> {
                                            Intent intent = new Intent(context, MainActivity.class);
                                            intent.setAction(MainActivity.FORWARD_ACTION);
                                            intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, SettingsActivity.class.getName());
                                            intent.putExtra(SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA, SettingsActivity.PREF_HEADER_KEY_LOCK_SCREEN);
                                            context.startActivity(intent);
                                        });

                                if (dialog instanceof AlertDialog) {
                                    ((AlertDialog) dialog).setOnDismissListener(null);
                                    if (dismissCallback != null) {
                                        bd.setOnDismissListener((DialogInterface dialogInterface) -> dismissCallback.run());
                                    }
                                }
                                bd.create().show();
                            }
                            break;
                        }
                        case 2: { // background with delay
                            ((AlertDialog) dialog).setOnDismissListener(null);

                            int backgroundGraceDelay = SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelay();
                            Integer index = null;
                            for (int i = 0; i < SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES.length; i++) {
                                if (SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES[i] == backgroundGraceDelay) {
                                    index = i;
                                    break;
                                }
                            }
                            SelectableArrayAdapter<String> delayAdapter = new SelectableArrayAdapter<>(context, index, SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelayLabels(context));
                            AlertDialog.Builder bd = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_profile_close_policy_background_delay_dialog_title)
                                    .setAdapter(delayAdapter, (DialogInterface d, int w) -> {
                                        try {
                                            SettingsActivity.setHiddenProfileClosePolicy(SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND, SettingsActivity.PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES[w]);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    })
                                    .setNegativeButton(R.string.button_label_cancel, null);
                            if (dismissCallback != null) {
                                bd.setOnDismissListener((DialogInterface dialogInterface) -> dismissCallback.run());
                            }
                            bd.create().show();
                            break;
                        }
                    }
                })
                .setNegativeButton(R.string.button_label_cancel, null);

        if (dismissCallback != null) {
            builder.setOnDismissListener((DialogInterface dialogInterface) -> dismissCallback.run());
        }

        builder.create().show();
    }
}