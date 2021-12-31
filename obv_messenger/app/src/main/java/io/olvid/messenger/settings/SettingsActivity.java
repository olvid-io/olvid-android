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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.fragments.dialog.LedColorPickerDialogFragment;

public class SettingsActivity extends LockableActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, LedColorPickerDialogFragment.OnLedColorSelectedListener {

    public static final String SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA = "sub_setting";
    public static final String PREF_HEADER_KEY_BACKUP = "pref_header_key_backup";
    public static final String PREF_HEADER_KEY_PRIVACY = "pref_header_key_privacy";
    public static final String PREF_HEADER_KEY_LOCK_SCREEN = "pref_header_key_lock_screen";

    public static final String FONT_SCALE_CHANGED_BROADCAST_ACTION = "font_scale_changed_action";


    // NON-USER VISIBLE SETTINGS
    static final String PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP = "pref_key_last_available_space_warning_timestamp";
    static final String PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED = "pref_key_first_call_audio_permission_requested";
    static final String PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED = "pref_key_first_call_bluetooth_permission_requested";
    static final String PREF_KEY_LAST_BACKUP_REMINDER_TIMESTAMP = "pref_key_last_backup_reminder_timestamp";
    static final String PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER = "pref_key_compose_message_icon_preferred_order";


    // BETA
    static final String PREF_KEY_ENABLE_BETA_FEATURES = "pref_key_enable_beta_features";
    static final boolean PREF_KEY_ENABLE_BETA_FEATURES_DEFAULT = false;

    static final String PREF_KEY_SCALED_TURN_REGION = "pref_key_scaled_turn_region";
    static final String PREF_KEY_SCALED_TURN_REGION_DEFAULT = "null";

    static final String PREF_KEY_EXPORT_APP_DATABASES = "pref_key_export_app_databases";
    static final String PREF_KEY_STORAGE_EXPLORER = "pref_key_storage_explorer";


    // NOTIFICATIONS
    public static final long[] VIBRATION_PATTERN_1_SHORT = new long[]{0, 100, 2900};
    public static final long[] VIBRATION_PATTERN_2_SHORT = new long[]{0, 100, 50, 100, 2750};
    public static final long[] VIBRATION_PATTERN_3_SHORT = new long[]{0, 100, 50, 100, 50, 100, 2600};
    public static final long[] VIBRATION_PATTERN_1_LONG = new long[]{0, 250, 2750};
    public static final long[] VIBRATION_PATTERN_2_LONG = new long[]{0, 250, 150, 250, 2350};
    public static final long[] VIBRATION_PATTERN_3_LONG = new long[]{0, 250, 150, 250, 150, 250, 1950};

    static final String PREF_KEY_MESSAGE_VIBRATION_PATTERN = "pref_key_message_vibration_pattern";
    static final String PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT = "1";

    static final String PREF_KEY_MESSAGE_RINGTONE = "pref_key_message_ringtone";
    static final String PREF_KEY_MESSAGE_RINGTONE_DEFAULT = Settings.System.DEFAULT_NOTIFICATION_URI.toString();

    static final String PREF_KEY_MESSAGE_LED = "pref_key_message_led_color";
    static final String PREF_KEY_MESSAGE_LED_DEFAULT = "#2f65f5";

    static final String PREF_KEY_CALL_VIBRATION_PATTERN = "pref_key_call_vibration_pattern";
    static final String PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT = "20";

    static final String PREF_KEY_CALL_RINGTONE = "pref_key_call_ringtone";
    static final String PREF_KEY_CALL_RINGTONE_DEFAULT = Settings.System.DEFAULT_RINGTONE_URI.toString();

    static final String PREF_KEY_CALL_USE_FLASH = "pref_key_call_use_flash";
    static final boolean PREF_KEY_CALL_USE_FLASH_DEFAULT = false;

    static final String PREF_KEY_PERMANENT_FOREGROUND_SERVICE = "pref_key_permanent_foreground_service";
    static final boolean PREF_KEY_PERMANENT_FOREGROUND_SERVICE_DEFAULT = false;


    // CUSTOMIZATION
    static final String PREF_KEY_DARK_MODE = "pref_key_dark_mode";
    static final boolean PREF_KEY_DARK_MODE_DEFAULT = false;
    static final String PREF_KEY_DARK_MODE_API29 = "pref_key_dark_mode_api29";
    static final String PREF_KEY_DARK_MODE_DEFAULT_API29 = "Auto";

    static final String PREF_KEY_FONT_SCALE = "pref_key_font_scale";
    static final String PREF_KEY_FONT_SCALE_DEFAULT = "1.0";

    static final String PREF_KEY_USE_INTERNAL_IMAGE_VIEWER = "pref_key_use_internal_image_viewer";
    static final boolean PREF_KEY_USE_INTERNAL_IMAGE_VIEWER_DEFAULT = true;

    static final String PREF_KEY_SORT_CONTACTS_BY_LAST_NAME = "pref_key_sort_contacts_by_last_name";
    static final boolean PREF_KEY_SORT_CONTACTS_BY_LAST_NAME_DEFAULT = false;

    static final String PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT = "pref_key_contact_display_name_format";
    static final String PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT_DEFAULT = "%f %l";

    static final String PREF_KEY_UPPERCASE_LAST_NAME = "pref_key_uppercase_last_name";
    static final boolean PREF_KEY_UPPERCASE_LAST_NAME_DEFAULT = false;


    // PRIVACY
    static final String PREF_KEY_READ_RECEIPT = "pref_key_send_read_receipt";
    static final boolean PREF_KEY_READ_RECEIPT_DEFAULT = false;

    static final String PREF_KEY_HIDE_NOTIFICATION_CONTENTS = "pref_key_hide_notification_contents";
    static final boolean PREF_KEY_HIDE_NOTIFICATION_CONTENTS_DEFAULT = false;

    static final String PREF_KEY_EXPOSE_RECENT_DISCUSSIONS = "pref_key_expose_recent_discussions";
    static final boolean PREF_KEY_EXPOSE_RECENT_DISCUSSIONS_DEFAULT = false;

    static final String PREF_KEY_KEYBOARD_INCOGNITO_MODE = "pref_key_keyboard_incognito_mode";
    static final boolean PREF_KEY_KEYBOARD_INCOGNITO_MODE_DEFAULT = true;

    static final String PREF_KEY_PREVENT_SCREEN_CAPTURE = "pref_key_prevent_screen_capture";
    static final boolean PREF_KEY_PREVENT_SCREEN_CAPTURE_DEFAULT = true;

    static final String PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY = "pref_key_hidden_profile_close_policy";
    public static final int HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK = 1;
    public static final int HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING = 2;
    public static final int HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND = 3;

    static final String PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY = "pref_key_hidden_profile_close_policy_background_grace_delay";
    static final int PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_DEFAULT = -1;
    static final int[] PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES = {0, 10, 30, 60, 120, 300};


    static final String PREF_KEY_DISABLE_PUSH_NOTIFICATIONS = "pref_key_disable_push_notifications";
    static final boolean PREF_KEY_DISABLE_PUSH_NOTIFICATIONS_DEFAULT = false;

    static final String PREF_KEY_PERMANENT_WEBSOCKET = "pref_key_permanent_websocket";
    static final boolean PREF_KEY_PERMANENT_WEBSOCKET_DEFAULT = false;

    // LOCK SCREEN
    static final String PREF_KEY_USE_LOCK_SCREEN = "pref_key_use_lock_screen";
    static final boolean PREF_KEY_USE_LOCK_SCREEN_DEFAULT = false;

    static final String PREF_KEY_UNLOCK_BIOMETRY = "pref_key_unlock_biometry";
    static final boolean PREF_KEY_UNLOCK_BIOMETRY_DEFAULT = true;

    static final String PREF_KEY_LOCK_GRACE_TIME = "pref_key_lock_grace_time";
    static final String PREF_KEY_LOCK_GRACE_TIME_DEFAULT = "30";

    static final String PREF_KEY_KEEP_LOCK_SERVICE_OPEN = "pref_key_keep_lock_service_open";
    static final boolean PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT = true;

    static final String PREF_KEY_PIN_HASH = "pref_key_pin_hash";
    static final String PREF_KEY_PLAIN_PIN = "pref_key_plain_pin";
    static final String PREF_KEY_PIN_IS_A_PASSWORD = "pref_key_pin_is_a_password";
    static final boolean PREF_KEY_PIN_IS_A_PASSWORD_DEFAULT = false;

    static final String PREF_KEY_RESET_PIN = "pref_key_reset_pin";


    // BACKUPS
    static final String PREF_KEY_RELOAD_BACKUP_CONFIGURATION = "pref_key_reload_backup_configuration";
    static final String PREF_KEY_GENERATE_NEW_BACKUP_KEY = "pref_key_generate_new_backup_key";

    static final String PREF_KEY_MANUAL_BACKUP = "pref_key_manual_backup";

    static final String PREF_KEY_ENABLE_AUTOMATIC_BACKUP = "pref_key_enable_automatic_backup";
    static final boolean PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT = false;
    static final String PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID = "pref_key_automatic_backup_device_unique_id";
    static final String PREF_KEY_AUTOMATIC_BACKUP_ACCOUNT = "pref_key_automatic_backup_account";


    // CAMERA SETTINGS
    static final String PREF_KEY_CAMERA_RESOLUTION = "pref_key_camera_resolution";
    static final String PREF_KEY_CAMERA_RESOLUTION_DEFAULT = "-1";

    static final String PREF_KEY_REMOVE_METADATA = "pref_key_remove_metadata";
    static final boolean PREF_KEY_REMOVE_METADATA_DEFAULT = false;


    // OTHER SETTINGS
    static final String PREF_KEY_SEND_WITH_HARDWARE_ENTER = "pref_key_send_with_hardware_enter";
    static final boolean PREF_KEY_SEND_WITH_HARDWARE_ENTER_DEFAULT = false;

    static final String PREF_KEY_QR_CORRECTION_LEVEL = "pref_key_qr_correction_level";
    static final String PREF_KEY_QR_CORRECTION_LEVEL_DEFAULT = "M";

    static final String PREF_KEY_RESET_DIALOGS = "pref_key_reset_dialogs";

    static final String PREF_KEY_PING_CONNECTIVITY_INDICATOR = "pref_key_ping_connectivity_indicator";
    static final String PREF_KEY_PING_CONNECTIVITY_INDICATOR_DEFAULT = "null";

    public enum PingConnectivityIndicator {
        NONE,
        DOT,
        LINE,
        FULL,
    }

    static final String PREF_KEY_SHARE_APP_VERSION = "pref_key_share_app_version";
    static final boolean PREF_KEY_SHARE_APP_VERSION_DEFAULT = true;

    static final String PREF_KEY_NOTIFY_CERTIFICATE_CHANGE = "pref_key_notify_certificate_change";
    static final boolean PREF_KEY_NOTIFY_CERTIFICATE_CHANGE_DEFAULT = false;

    public static final String USER_DIALOG_HIDE_BATTERY_OPTIMIZATION = "user_dialog_hide_battery_optimization";
    public static final String USER_DIALOG_HIDE_BACKGROUND_RESTRICTED = "user_dialog_hide_background_restricted";
    public static final String USER_DIALOG_HIDE_GOOGLE_APIS = "user_dialog_hide_google_apis";
    public static final String USER_DIALOG_HIDE_ALARM_SCHEDULING = "user_dialog_hide_alarm_scheduling";
    public static final String USER_DIALOG_HIDE_OPEN_EXTERNAL_APP = "user_dialog_hide_open_external_app";

    static final String PREF_KEY_DEBUG_LOG_LEVEL = "pref_key_debug_log_level";
    static final boolean PREF_KEY_DEBUG_LOG_LEVEL_DEFAULT = false;

    static final String PREF_KEY_LOW_BANDWIDTH_CALLS = "pref_key_low_bandwidth_calls";
    static final boolean PREF_KEY_LOW_BANDWIDTH_CALLS_DEFAULT = false;

    static final String PREF_KEY_PREFERRED_KEYCLOAK_BROWSER = "pref_key_preferred_keycloak_browser";

    private static final String PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER = "pref_key_use_speaker_output_for_media_player";
    private static final boolean PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER_DEFAULT = true;

    // SEND & RECEIVE MESSAGES
    static final String PREF_KEY_AUTODOWNLOAD_SIZE = "pref_key_autodownload_size";
    static final String PREF_KEY_AUTODOWNLOAD_SIZE_DEFAULT = "10000000";


    static final String PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND = "pref_key_auto_open_limited_visibility_inbound";
    static final boolean PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_DEFAULT = false;

    static final String PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES = "pref_key_retain_wiped_outbound_messages";
    static final boolean PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES_DEFAULT = false;

    static final String PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT = "pref_key_default_discussion_retention_count"; // number of messages to keep
    static final String PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT_DEFAULT = ""; // keep everything by default

    static final String PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION = "pref_key_default_discussion_retention_duration"; // duration in seconds
    static final String PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION_DEFAULT = "null"; // no expiration

    static final String PREF_KEY_CATEGORY_DEFAULT_EPHEMERAL_SETTINGS = "pref_key_category_default_ephemeral_settings";

    static final String PREF_KEY_DEFAULT_READ_ONCE = "pref_key_default_read_once";
    static final boolean PREF_KEY_DEFAULT_READ_ONCE_DEFAULT = false;

    static final String PREF_KEY_DEFAULT_VISIBILITY_DURATION = "pref_key_default_visibility_duration";
    static final String PREF_KEY_DEFAULT_VISIBILITY_DURATION_DEFAULT = "null";

    static final String PREF_KEY_DEFAULT_EXISTENCE_DURATION = "pref_key_default_existence_duration";
    static final String PREF_KEY_DEFAULT_EXISTENCE_DURATION_DEFAULT = "null";

    static final String PREF_KEY_LAST_DELETE_EVERYWHERE = "pref_key_last_delete_everywhere";
    static final boolean PREF_KEY_LAST_DELETE_EVERYWHERE_DEFAULT = false;


    // WEBCLIENT
    static final String PREF_KEY_LANGUAGE_WEBCLIENT = "pref_key_language_webclient";
    static final String PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT = "AppDefault";

    static final String PREF_KEY_THEME_WEBCLIENT = "pref_key_theme_webclient";
    static final String PREF_KEY_THEME_WEBCLIENT_DEFAULT = "BrowserDefault";

    static final String PREF_KEY_SEND_ON_ENTER_WEBCLIENT = "pref_key_send_on_enter_webclient";
    static final boolean PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT = true; //send message when hitting enter by default

    static final String PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER = "pref_key_notification_show_on_browser";
    static final boolean PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER_DEFAULT = true; //notification sound on by default

    static final String PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT = "pref_key_notification_sound_webclient";
    static final boolean PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT = true; //notification sound on by default

    static final String PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE = "pref_key_keep_webclient_alive_after_close";
    static final boolean PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE_DEFAULT = true;

    static final String PREF_KEY_SHOW_ERROR_NOTIFICATIONS = "pref_key_show_error_notifications";
    static final boolean PREF_KEY_SHOW_ERROR_NOTIFICATIONS_DEFAULT = true;

    static final String PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY = "pref_key_notifications_for_messages_after_inactivity";
    static final boolean PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY_DEFAULT = true;

    static final String PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT = "pref_key_require_unlock_before_connecting_to_webclient";
    static final boolean PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT_DEFAULT = true;

    // ACTIVITY VARIABLES

    private WeakReference<LedColorPickerDialogFragment.OnLedColorSelectedListener> onLedColorSelectedListenerWeakReference;
    private HeadersPreferenceFragment headersPreferenceFragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            headersPreferenceFragment = new HeadersPreferenceFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, headersPreferenceFragment)
                    .commit();

            Intent intent = getIntent();
            if (intent != null && intent.hasExtra(SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA)) {
                final String prefKey = intent.getStringExtra(SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA);
                if (prefKey != null) {
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        if (headersPreferenceFragment != null) {
                            Preference preference = headersPreferenceFragment.findPreference(prefKey);
                            if (preference != null) {
                                int position = preference.getOrder();
                                RecyclerView recyclerView = headersPreferenceFragment.getListView();
                                RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                                if (viewHolder != null) {
                                    viewHolder.itemView.setPressed(true);
                                    handler.postDelayed(() -> onPreferenceStartFragment(headersPreferenceFragment, preference), 600);
                                } else {
                                    recyclerView.scrollToPosition(position);
                                    handler.postDelayed(() -> {
                                        RecyclerView.ViewHolder reViewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                                        if (reViewHolder != null) {
                                            reViewHolder.itemView.setPressed(true);
                                        }
                                    }, 100);
                                    handler.postDelayed(() -> onPreferenceStartFragment(headersPreferenceFragment, preference), 700);
                                }
                            }
                        }
                    }, 400);
                }
            }
        }
    }

    public static class HeadersPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_header, rootKey);

            final FragmentActivity activity = requireActivity();
            final BillingClient billingClient = BillingClient.newBuilder(activity)
                    .enablePendingPurchases()
                    .setListener((billingResult, list) -> Logger.d("Purchase updated " + list))
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
                    billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (BillingResult queryBillingResult, List<Purchase> list) -> {
                        if (list.size() > 0) {
                            // there are some subscriptions, add a link
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Preference subscriptionPref = new Preference(activity);
                                subscriptionPref.setIcon(R.drawable.ic_pref_subscription);
                                subscriptionPref.setTitle(R.string.pref_title_subscription);
                                subscriptionPref.setOnPreferenceClickListener((preference) -> {
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=premium_2020_monthly&package=io.olvid.messenger")));
                                    return true;
                                });
                                getPreferenceScreen().addPreference(subscriptionPref);
                            });
                        }
                        billingClient.endConnection();
                    });
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // nothing to do
                }
            });
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());

        if (fragment instanceof LedColorPickerDialogFragment.OnLedColorSelectedListener) {
            onLedColorSelectedListenerWeakReference = new WeakReference<>((LedColorPickerDialogFragment.OnLedColorSelectedListener) fragment);
        } else {
            onLedColorSelectedListenerWeakReference = null;
        }

        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();

        // set the activity title
        setTitle(pref.getTitle());
        return true;
    }


    @Override
    public void onLedColorSelected(int requestCode, String color) {
        if (onLedColorSelectedListenerWeakReference != null) {
            LedColorPickerDialogFragment.OnLedColorSelectedListener onLedColorSelectedListener = onLedColorSelectedListenerWeakReference.get();
            if (onLedColorSelectedListener != null) {
                onLedColorSelectedListener.onLedColorSelected(requestCode, color);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setTitle(R.string.activity_title_settings);
        super.onBackPressed();
    }


    private static boolean useDarkMode() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_DARK_MODE, PREF_KEY_DARK_MODE_DEFAULT);
    }

    private static String useDarkModeApi29() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DARK_MODE_API29, PREF_KEY_DARK_MODE_DEFAULT_API29);
    }

    public static void setDefaultNightMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switch (useDarkModeApi29()) {
                case "Dark":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case "Light":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "Auto":
                default:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        } else {
            if (useDarkMode()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
    }

    public static Boolean getForcedDarkMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switch (useDarkModeApi29()) {
                case "Dark":
                    return true;
                case "Light":
                    return false;
                case "Auto":
                default:
                    return null;
            }
        } else {
            return useDarkMode() ? true : null;
        }
    }

    public static void setForcedDarkMode(boolean forceDarkMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (forceDarkMode) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
                editor.putString(PREF_KEY_DARK_MODE_API29, "Dark");
                editor.apply();
            } else {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
                editor.putString(PREF_KEY_DARK_MODE_API29, "Light");
                editor.apply();
            }
        } else if (forceDarkMode) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
            editor.putBoolean(PREF_KEY_DARK_MODE, true);
            editor.apply();
        }
    }

    public static float getFontScale() {
        String scaleString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_FONT_SCALE, PREF_KEY_FONT_SCALE_DEFAULT);
        if (scaleString != null) {
            try {
                return Float.parseFloat(scaleString);
            } catch (Exception ignored) {
            }
        }
        return 1.0f;
    }

    public static long getLastAvailableSpaceWarningTimestamp() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getLong(PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP, 0L);
    }


    public static void setLastAvailableSpaceWarningTimestamp(long timestamp) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putLong(PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP, timestamp);
        editor.apply();
    }

    public static boolean wasFirstCallAudioPermissionRequested() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED, false);
    }

    public static void setFirstCallAudioPermissionRequested(boolean requested) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED, requested);
        editor.apply();
    }

    public static boolean wasFirstCallBluetoothPermissionRequested() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED, false);
    }

    public static void setFirstCallBluetoothPermissionRequested(boolean requested) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED, requested);
        editor.apply();
    }

    public static long getLastBackupReminderTimestamp() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getLong(PREF_KEY_LAST_BACKUP_REMINDER_TIMESTAMP, 0);
    }

    public static void setLastBackupReminderTimestamp(long timestamp) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putLong(PREF_KEY_LAST_BACKUP_REMINDER_TIMESTAMP, timestamp);
        editor.apply();
    }

    public static boolean useInternalImageViewer() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_INTERNAL_IMAGE_VIEWER, PREF_KEY_USE_INTERNAL_IMAGE_VIEWER_DEFAULT);
    }

    public static void setUseInternalImageViewer(boolean useInternalImageViewer) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_USE_INTERNAL_IMAGE_VIEWER, useInternalImageViewer);
        editor.apply();
    }

    public static boolean getSortContactsByLastName() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SORT_CONTACTS_BY_LAST_NAME, PREF_KEY_SORT_CONTACTS_BY_LAST_NAME_DEFAULT);
    }

    public static void setSortContactsByLastName(boolean sort) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SORT_CONTACTS_BY_LAST_NAME, sort);
        editor.apply();
    }

    public static String getContactDisplayNameFormat() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT, PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT_DEFAULT);
    }

    public static void setContactDisplayNameFormat(String format) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT, format);
        editor.apply();
    }

    public static boolean getUppercaseLastName() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_UPPERCASE_LAST_NAME, PREF_KEY_UPPERCASE_LAST_NAME_DEFAULT);
    }

    public static void setUppercaseLastName(boolean uppercaseLastName) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_UPPERCASE_LAST_NAME, uppercaseLastName);
        editor.apply();
    }

    public static long getAutoDownloadSize() {
        String downloadSizeString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_AUTODOWNLOAD_SIZE, PREF_KEY_AUTODOWNLOAD_SIZE_DEFAULT);
        if (downloadSizeString != null) {
            return Long.parseLong(downloadSizeString);
        }
        return 0;
    }

    public static void setAutoDownloadSize(long autoDownloadSize) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_AUTODOWNLOAD_SIZE, Long.toString(autoDownloadSize));
        editor.apply();
    }

    public static boolean getDefaultSendReadReceipt() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(SettingsActivity.PREF_KEY_READ_RECEIPT, SettingsActivity.PREF_KEY_READ_RECEIPT_DEFAULT);
    }

    public static void setDefaultSendReadReceipt(boolean defaultSendReadReceipt) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_READ_RECEIPT, defaultSendReadReceipt);
        editor.apply();
    }

    public static boolean getSendWithHardwareEnter() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SEND_WITH_HARDWARE_ENTER, PREF_KEY_SEND_WITH_HARDWARE_ENTER_DEFAULT);
    }

    @NonNull
    public static PingConnectivityIndicator getPingConnectivityIndicator() {
        String pingConnectivityIndicator = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PING_CONNECTIVITY_INDICATOR, PREF_KEY_PING_CONNECTIVITY_INDICATOR_DEFAULT);
        if (pingConnectivityIndicator != null) {
            switch (pingConnectivityIndicator) {
                case "dot":
                    return PingConnectivityIndicator.DOT;
                case "line":
                    return PingConnectivityIndicator.LINE;
                case "full":
                    return PingConnectivityIndicator.FULL;
            }
        }
        return PingConnectivityIndicator.NONE;
    }

    public static String getQrCorrectionLevel() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_QR_CORRECTION_LEVEL, PREF_KEY_QR_CORRECTION_LEVEL_DEFAULT);
    }

    public static void setQrCorrectionLevel(String level) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_QR_CORRECTION_LEVEL, level);
        editor.apply();
    }

    public static boolean useDebugLogLevel() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_DEBUG_LOG_LEVEL, PREF_KEY_DEBUG_LOG_LEVEL_DEFAULT);
    }

    public static void setUseDebugLogLevel(boolean useDebugLogLevel) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_DEBUG_LOG_LEVEL, useDebugLogLevel);
        editor.apply();
    }

    public static boolean getDefaultAutoOpenLimitedVisibilityInboundMessages() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND, PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_DEFAULT);
    }

    public static void setDefaultAutoOpenLimitedVisibilityInboundMessages(boolean autoOpen) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND, autoOpen);
        editor.apply();
    }

    public static boolean getDefaultRetainWipedOutboundMessages() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES, PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES_DEFAULT);
    }

    public static void setDefaultRetainWipedOutboundMessages(boolean retain) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES, retain);
        editor.apply();
    }

    public static Long getDefaultDiscussionRetentionCount() {
        try {
            String retentionCountString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT, PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT_DEFAULT);
            if (retentionCountString != null) {
                if ("".equals(retentionCountString)) {
                    return null;
                }
                return Long.parseLong(retentionCountString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultDiscussionRetentionCount(long count) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT, Long.toString(count));
        editor.apply();
    }

    public static Long getDefaultDiscussionRetentionDuration() {
        try {
            String retentionDurationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION, PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION_DEFAULT);
            if (retentionDurationString != null) {
                if ("null".equals(retentionDurationString)) {
                    return null;
                }
                return Long.parseLong(retentionDurationString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultDiscussionRetentionDuration(long duration) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION, Long.toString(duration));
        editor.apply();
    }

    public static boolean getDefaultDiscussionReadOnce() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_DEFAULT_READ_ONCE, PREF_KEY_DEFAULT_READ_ONCE_DEFAULT);
    }

    public static void setDefaultDiscussionReadOnce(boolean readOnce) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_DEFAULT_READ_ONCE, readOnce);
        editor.apply();
    }

    public static Long getDefaultDiscussionVisibilityDuration() {
        try {
            String visibilityDurationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DEFAULT_VISIBILITY_DURATION, PREF_KEY_DEFAULT_VISIBILITY_DURATION_DEFAULT);
            if (visibilityDurationString != null) {
                if ("null".equals(visibilityDurationString)) {
                    return null;
                }
                return Long.parseLong(visibilityDurationString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultDiscussionVisibilityDuration(Long duration) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (duration == null) {
            editor.remove(PREF_KEY_DEFAULT_VISIBILITY_DURATION);
        } else {
            editor.putString(PREF_KEY_DEFAULT_VISIBILITY_DURATION, Long.toString(duration));
        }
        editor.apply();
    }

    public static Long getDefaultDiscussionExistenceDuration() {
        try {
            String existenceDurationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DEFAULT_EXISTENCE_DURATION, PREF_KEY_DEFAULT_EXISTENCE_DURATION_DEFAULT);
            if (existenceDurationString != null) {
                if ("null".equals(existenceDurationString)) {
                    return null;
                }
                return Long.parseLong(existenceDurationString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultDiscussionExistenceDuration(Long duration) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (duration == null) {
            editor.remove(PREF_KEY_DEFAULT_EXISTENCE_DURATION);
        } else {
            editor.putString(PREF_KEY_DEFAULT_EXISTENCE_DURATION, Long.toString(duration));
        }
        editor.apply();
    }

    public static boolean getBetaFeaturesEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_ENABLE_BETA_FEATURES, PREF_KEY_ENABLE_BETA_FEATURES_DEFAULT);
    }

    public static void setBetaFeaturesEnabled(boolean betaFeaturesEnabled) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_ENABLE_BETA_FEATURES, betaFeaturesEnabled);
        editor.apply();
    }

    public static String getScaledTurn() {
        String value = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_SCALED_TURN_REGION, PREF_KEY_SCALED_TURN_REGION_DEFAULT);
        if (PREF_KEY_SCALED_TURN_REGION_DEFAULT.equals(value)) {
            return null;
        }
        return value;
    }

    public static boolean getLastDeleteEverywhere() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_LAST_DELETE_EVERYWHERE, PREF_KEY_LAST_DELETE_EVERYWHERE_DEFAULT);
    }

    public static void setLastDeleteEverywhere(boolean deleteEverywhere) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_LAST_DELETE_EVERYWHERE, deleteEverywhere);
        editor.apply();
    }

    public static boolean useApplicationLockScreen() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_LOCK_SCREEN, PREF_KEY_USE_LOCK_SCREEN_DEFAULT);
    }

    public static boolean isNotificationContentHidden() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_HIDE_NOTIFICATION_CONTENTS, PREF_KEY_HIDE_NOTIFICATION_CONTENTS_DEFAULT);
    }

    public static void setNotificationContentHidden(boolean contentHidden) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_HIDE_NOTIFICATION_CONTENTS, contentHidden);
        editor.apply();
    }

    public static boolean exposeRecentDiscussions() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_EXPOSE_RECENT_DISCUSSIONS, PREF_KEY_EXPOSE_RECENT_DISCUSSIONS_DEFAULT);
    }

    public static void setExposeRecentDiscussions(boolean exposeRecentDiscussions) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_EXPOSE_RECENT_DISCUSSIONS, exposeRecentDiscussions);
        editor.apply();
    }

    public static boolean useKeyboardIncognitoMode() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_KEYBOARD_INCOGNITO_MODE, PREF_KEY_KEYBOARD_INCOGNITO_MODE_DEFAULT);
    }

    public static void setUseKeyboardIncognitoMode(boolean useKeyboardIncognitoMode) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_KEYBOARD_INCOGNITO_MODE, useKeyboardIncognitoMode);
        editor.apply();
    }

    public static boolean preventScreenCapture() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_PREVENT_SCREEN_CAPTURE, PREF_KEY_PREVENT_SCREEN_CAPTURE_DEFAULT);
    }

    public static void setPreventScreenCapture(boolean preventScreenCapture) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_PREVENT_SCREEN_CAPTURE, preventScreenCapture);
        editor.apply();
    }

    public static int getHiddenProfileClosePolicy() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY, -1);
    }

    public static int getHiddenProfileClosePolicyBackgroundGraceDelay() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY, PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_DEFAULT);
    }

    public static String[] getHiddenProfileClosePolicyBackgroundGraceDelayLabels(Context context) {
        return new String[] {
                context.getString(R.string.text_close_profile_instantly),
                context.getString(R.string.text_after_10s),
                context.getString(R.string.text_after_30s),
                context.getString(R.string.text_after_1m),
                context.getString(R.string.text_after_2m),
                context.getString(R.string.text_after_5m),
        };
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isHiddenProfileClosePolicyDefined() {
        return getHiddenProfileClosePolicy() != -1;
    }

    public static void setHiddenProfileClosePolicy(int policy, int backgroundGraceDelay) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY, policy);
        if (policy == HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND) {
            editor.putInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY, backgroundGraceDelay);
        } else {
            editor.remove(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY);
        }
        editor.apply();
    }



    public static boolean useLowBandwidthInCalls() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_LOW_BANDWIDTH_CALLS, PREF_KEY_LOW_BANDWIDTH_CALLS_DEFAULT);
    }

    public static void setUseLowBandwidthInCalls(boolean useLowBandwidthInCalls) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_LOW_BANDWIDTH_CALLS, useLowBandwidthInCalls);
        editor.apply();
    }

    public static boolean usePermanentWebSocket() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_PERMANENT_WEBSOCKET, PREF_KEY_PERMANENT_WEBSOCKET_DEFAULT);
    }

    public static void setUsePermanentWebSocket(boolean userPermanentWebSocket) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_PERMANENT_WEBSOCKET, userPermanentWebSocket);
        editor.apply();
    }

    public static boolean shareAppVersion() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SHARE_APP_VERSION, PREF_KEY_SHARE_APP_VERSION_DEFAULT);
    }

    public static void setShareAppVersion(boolean shareAppVersion) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SHARE_APP_VERSION, shareAppVersion);
        editor.apply();
    }

    public static boolean notifyCertificateChange() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFY_CERTIFICATE_CHANGE, PREF_KEY_NOTIFY_CERTIFICATE_CHANGE_DEFAULT);
    }

    public static void setNotifyCertificateChange(boolean notifyCertificateChange) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFY_CERTIFICATE_CHANGE, notifyCertificateChange);
        editor.apply();
    }

    public static boolean disablePushNotifications() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_DISABLE_PUSH_NOTIFICATIONS, PREF_KEY_DISABLE_PUSH_NOTIFICATIONS_DEFAULT);
    }

    public static void setDisablePushNotifications(boolean disablePushNotifications) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_DISABLE_PUSH_NOTIFICATIONS, disablePushNotifications);
        editor.apply();
    }

    public static final int PIN_SALT_LENGTH = 8;

    public static void savePIN(String PIN, boolean pinIsAPassword) {
        byte[] salt = new byte[PIN_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        byte[] hash = computePINHash(PIN, salt);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (hash == null) {
            Logger.w("Error computing PIN hash, storing it in clear...");
            editor.putString(PREF_KEY_PLAIN_PIN, PIN);
            editor.remove(PREF_KEY_PIN_HASH);
        } else {
            editor.remove(PREF_KEY_PLAIN_PIN);
            byte[] saltAndHash = new byte[PIN_SALT_LENGTH + hash.length];
            System.arraycopy(salt, 0, saltAndHash, 0, PIN_SALT_LENGTH);
            System.arraycopy(hash, 0, saltAndHash, PIN_SALT_LENGTH, hash.length);
            String base64SaltAndHash = Base64.encodeToString(saltAndHash, Base64.NO_PADDING | Base64.NO_WRAP);
            editor.putString(PREF_KEY_PIN_HASH, base64SaltAndHash);
        }
        editor.putBoolean(PREF_KEY_PIN_IS_A_PASSWORD, pinIsAPassword);
        editor.apply();
    }

    public static boolean verifyPIN(String PIN) {
        String plainPIN = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PLAIN_PIN, null);
        String hashedPIN = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PIN_HASH, null);
        if (plainPIN == null && hashedPIN == null) {
            return true;
        } else if (plainPIN != null) {
            return plainPIN.equals(PIN);
        } else {
            byte[] saltAndHash = Base64.decode(hashedPIN, Base64.NO_PADDING | Base64.NO_WRAP);
            byte[] salt = Arrays.copyOfRange(saltAndHash, 0, PIN_SALT_LENGTH);
            byte[] hash = Arrays.copyOfRange(saltAndHash, PIN_SALT_LENGTH, saltAndHash.length);
            byte[] hashCheck = computePINHash(PIN, salt);
            return (hashCheck != null) && Arrays.equals(hash, hashCheck);
        }
    }

    public static void clearPIN() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.remove(PREF_KEY_PLAIN_PIN);
        editor.remove(PREF_KEY_PIN_HASH);
        editor.apply();
    }

    public static byte[] computePINHash(String PIN, byte[] salt) {
        if (PIN == null || PIN.length() == 0) {
            return null;
        }
        PBEKeySpec keySpec = new PBEKeySpec(PIN.toCharArray(), salt, 1000, 160);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return skf.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        }
    }

    public static boolean isPINAPassword() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_PIN_IS_A_PASSWORD, PREF_KEY_PIN_IS_A_PASSWORD_DEFAULT);
    }

    public static boolean useBiometryToUnlock() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_UNLOCK_BIOMETRY, PREF_KEY_UNLOCK_BIOMETRY_DEFAULT);
    }

    public static int getLockGraceTime() {
        String timeString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCK_GRACE_TIME, PREF_KEY_LOCK_GRACE_TIME_DEFAULT);
        if (timeString != null) {
            return Integer.parseInt(timeString);
        }
        return -1;
    }

    public static boolean keepLockServiceOpen() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_KEEP_LOCK_SERVICE_OPEN, PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT);
    }

    public static boolean useAutomaticBackup() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_ENABLE_AUTOMATIC_BACKUP, PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT);
    }

    public static String getAutomaticBackupDeviceUniqueId() {
        String deviceUniqueId = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID, null);
        if (deviceUniqueId == null) {
            deviceUniqueId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
            editor.putString(PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID, deviceUniqueId);
            editor.apply();
        }
        return deviceUniqueId;
    }

    public static String getAutomaticBackupAccount() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_AUTOMATIC_BACKUP_ACCOUNT, null);
    }

    public static void setAutomaticBackupAccount(String email) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_AUTOMATIC_BACKUP_ACCOUNT, email);
        editor.apply();
    }

    public static Uri getMessageRingtone() {
        return Uri.parse(PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_RINGTONE, PREF_KEY_MESSAGE_RINGTONE_DEFAULT));
    }

    public static void setMessageRingtone(String uri) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_MESSAGE_RINGTONE, uri);
        editor.apply();
    }

    public static long[] getMessageVibrationPattern() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_VIBRATION_PATTERN, PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT);
        if (pattern == null) {
            pattern = PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT;
        }
        return intToVibrationPattern(Integer.parseInt(pattern));
    }

    public static String getMessageVibrationPatternRaw() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_VIBRATION_PATTERN, PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT);
        if (pattern == null) {
            pattern = PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT;
        }
        return pattern;
    }

    public static void setMessageVibrationPatternRaw(String pattern) {
        try {
            int val = Integer.parseInt(pattern);
            if (intToVibrationPattern(val).length == 0) {
                pattern = "0";
            }
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
            editor.putString(PREF_KEY_MESSAGE_VIBRATION_PATTERN, pattern);
            editor.apply();
        } catch (Exception ignored) {}
    }

    static long[] intToVibrationPattern(int patternIndex) {
        switch (patternIndex) {
            case 1:
                return VIBRATION_PATTERN_1_SHORT;
            case 2:
                return VIBRATION_PATTERN_2_SHORT;
            case 3:
                return VIBRATION_PATTERN_3_SHORT;
            case 10:
                return VIBRATION_PATTERN_1_LONG;
            case 20:
                return VIBRATION_PATTERN_2_LONG;
            case 30:
                return VIBRATION_PATTERN_3_LONG;
            case 0:
            default:
                return new long[0];
        }
    }

    public static String getMessageLedColor() {
        String color = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_LED, PREF_KEY_MESSAGE_LED_DEFAULT);
        if ("".equals(color)) {
            return null;
        }
        return color;
    }

    public static void setMessageLedColor(String color) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (color == null) {
            editor.putString(PREF_KEY_MESSAGE_LED, "");
        } else {
            editor.putString(PREF_KEY_MESSAGE_LED, color);
        }
        editor.apply();
    }

    public static Uri getCallRingtone() {
        return Uri.parse(PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CALL_RINGTONE, PREF_KEY_CALL_RINGTONE_DEFAULT));
    }

    public static void setCallRingtone(String uri) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_CALL_RINGTONE, uri);
        editor.apply();
    }

    public static long[] getCallVibrationPattern() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CALL_VIBRATION_PATTERN, PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT);
        if (pattern == null) {
            pattern = PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT;
        }
        return intToVibrationPattern(Integer.parseInt(pattern));
    }

    public static String getCallVibrationPatternRaw() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CALL_VIBRATION_PATTERN, PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT);
        if (pattern == null) {
            pattern = PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT;
        }
        return pattern;
    }

    public static void setCallVibrationPatternRaw(String pattern) {
        try {
            int val = Integer.parseInt(pattern);
            if (intToVibrationPattern(val).length == 0) {
                pattern = "0";
            }
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
            editor.putString(PREF_KEY_CALL_VIBRATION_PATTERN, pattern);
            editor.apply();
        } catch (Exception ignored) {}
    }

    public static boolean useFlashOnIncomingCall() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_CALL_USE_FLASH, PREF_KEY_CALL_USE_FLASH_DEFAULT);
    }

    public static void setUseFlashOnIncomingCall(boolean useFlash) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_CALL_USE_FLASH, useFlash);
        editor.apply();
    }

    public static boolean usePermanentForegroundService() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_PERMANENT_FOREGROUND_SERVICE, PREF_KEY_PERMANENT_FOREGROUND_SERVICE_DEFAULT);
    }

    public static void setUsePermanentForegroundService(boolean value) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_PERMANENT_FOREGROUND_SERVICE, value);
        editor.apply();
    }

    public static int getCameraResolution() {
        String qualityString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CAMERA_RESOLUTION, PREF_KEY_CAMERA_RESOLUTION_DEFAULT);
        if (qualityString == null) {
            return -1;
        }
        return Integer.parseInt(qualityString);
    }

    public static void setCameraResolution(int resolution) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_CAMERA_RESOLUTION, Integer.toString(resolution));
        editor.apply();
    }

    public static boolean getMetadataRemovalPreference() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_REMOVE_METADATA, PREF_KEY_REMOVE_METADATA_DEFAULT);
    }

    public static void setMetadataRemovalPreference(boolean remove) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_REMOVE_METADATA, remove);
        editor.apply();
    }

    public static String getLanguageWebclient() {
       String language = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LANGUAGE_WEBCLIENT, PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT);
       if(language == null || "".equals(language)){
           language = getLanguageWebclientDefault();
       }
       return language;
    }

    public static String getLanguageWebclientDefault() {
        return PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT;
    }

    public static String getThemeWebclient() {
        String theme = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_THEME_WEBCLIENT, PREF_KEY_THEME_WEBCLIENT_DEFAULT);
        if(theme == null || "".equals(theme)){
            theme = getThemeWebclientDefault();
        }
        return theme;
    }

    public static String getThemeWebclientDefault() {
        return PREF_KEY_THEME_WEBCLIENT_DEFAULT;
    }

    public static boolean sendOnEnterEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SEND_ON_ENTER_WEBCLIENT, PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT);
    }

    public static boolean sendOnEnterEnabledDefault() {
        return PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT;
    }

    public static boolean showNotificationsOnBrowser() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER_DEFAULT);
    }

    public static boolean notificationsSoundOnWebclient() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT);
    }

    public static boolean notificationsOnWebclientDefault() {
        return PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT;
    }

    public static void setLanguageWebclient(String language) {
        if(language == null || "".equals(language)){
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_LANGUAGE_WEBCLIENT, language);
        editor.apply();
    }

    public static void setThemeWebclient(String theme) {
        if(theme == null || "".equals(theme)){
            return;
        }
        if(theme.equals("dark")|| theme.equals("light")|| theme.equals("BrowserDefault") || theme.equals("AppDefault")){
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
            editor.putString(PREF_KEY_THEME_WEBCLIENT, theme);
            editor.apply();
        }
    }

    public static void setSendOnEnterWebClient(Boolean send) {
        if(send == null){
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SEND_ON_ENTER_WEBCLIENT, send);
        editor.apply();
    }

    public static void setShowNotificationsOnBrowser(Boolean show) {
        if(show == null) {
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, show);
        editor.apply();
    }

    public static void setNotificationsSoundWebclient(Boolean notifications){
        if(notifications == null){
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, notifications);
        editor.apply();
    }

    public static boolean keepWebclientAliveAfterClose() {
        return  PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE, PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE_DEFAULT);
    }

    public static boolean showErrorNotifications() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SHOW_ERROR_NOTIFICATIONS, PREF_KEY_SHOW_ERROR_NOTIFICATIONS_DEFAULT);
    }

    public static boolean notifyAfterInactivity() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY, PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY_DEFAULT);
    }

    public static boolean isUnlockRequiredForWebclient() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT, PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT_DEFAULT);
    }

    public static void setPreferredKeycloakBrowser(String browser) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (browser != null) {
            editor.putString(PREF_KEY_PREFERRED_KEYCLOAK_BROWSER, browser);
        } else {
            editor.remove(PREF_KEY_PREFERRED_KEYCLOAK_BROWSER);
        }
        editor.apply();
    }

    public static String getPreferredKeycloakBrowser() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PREFERRED_KEYCLOAK_BROWSER, null);
    }

    public static void setUseSpeakerOutputForMediaPlayer(boolean useSpeakerOutput) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER, useSpeakerOutput);
        editor.apply();
    }

    public static boolean getUseSpeakerOutputForMediaPlayer() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER, PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER_DEFAULT);
    }

    public static List<Integer> getComposeMessageIconPreferredOrder() {
        String preferredOrderString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER, null);
        try {
            if (preferredOrderString != null) {
                String[] parts = preferredOrderString.split(",");
                List<Integer> preferredIcons = new ArrayList<>(parts.length);
                for (String part : parts) {
                    preferredIcons.add(Integer.parseInt(part));
                }
                return preferredIcons;
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static void setComposeMessageIconPreferredOrder(List<Integer> icons) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        StringBuilder iconSb = new StringBuilder();
        for (Integer icon : icons) {
            if (iconSb.length() > 0) {
                iconSb.append(",");
            }
            iconSb.append(icon);
        }
        editor.putString(PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER, iconSb.toString());
        editor.apply();
    }

}
