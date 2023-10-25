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

package io.olvid.messenger.settings;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.billing.BillingUtils;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.firebase.ObvFirebaseMessagingService;
import io.olvid.messenger.google_services.GoogleServicesUtils;
import io.olvid.messenger.services.BackupCloudProviderService;

public class SettingsActivity extends LockableActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    public static final String SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA = "sub_setting";
    public static final String PREF_HEADER_KEY_BACKUP = "pref_header_key_backup";
    public static final String PREF_HEADER_KEY_NOTIFICATIONS = "pref_header_key_notifications";
    //    public static final String PREF_HEADER_KEY_PRIVACY = "pref_header_key_privacy";
    public static final String PREF_HEADER_KEY_LOCK_SCREEN = "pref_header_key_lock_screen";
    public static final String PREF_HEADER_KEY_LOCATION = "pref_header_key_location";

    public static final String ACTIVITY_RECREATE_REQUIRED_ACTION = "activity_recreate_required_action";


    // NON-USER VISIBLE SETTINGS
    static final String PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP = "pref_key_last_available_space_warning_timestamp";
    static final String PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED = "pref_key_first_call_audio_permission_requested";
    static final String PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED = "pref_key_first_call_bluetooth_permission_requested";
    static final String PREF_KEY_LAST_BACKUP_REMINDER_TIMESTAMP = "pref_key_last_backup_reminder_timestamp";
    static final String PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER = "pref_key_compose_message_icon_preferred_order";

    static final String PREF_KEY_PREFERRED_REACTIONS = "pref_key_preferred_reactions";
    static final String[] PREF_KEY_PREFERRED_REACTIONS_DEFAULT = new String[]{"❤️", "👍", "👎", "😂", "😮", "😢"};


    // BETA
    static final String PREF_KEY_ENABLE_BETA_FEATURES = "pref_key_enable_beta_features";
    static final boolean PREF_KEY_ENABLE_BETA_FEATURES_DEFAULT = false;

    static final String PREF_KEY_SCALED_TURN_REGION = "pref_key_scaled_turn_region";
    static final String PREF_KEY_SCALED_TURN_REGION_DEFAULT = "global";

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
    public static final String PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT = "1";

    static final String PREF_KEY_MESSAGE_RINGTONE = "pref_key_message_ringtone";
    public static final String PREF_KEY_MESSAGE_RINGTONE_DEFAULT = Settings.System.DEFAULT_NOTIFICATION_URI.toString();

    static final String PREF_KEY_MESSAGE_LED_COLOR = "pref_key_message_led_color";
    public static final String PREF_KEY_MESSAGE_LED_COLOR_DEFAULT = "#2f65f5";

    static final String PREF_KEY_CALL_VIBRATION_PATTERN = "pref_key_call_vibration_pattern";
    public static final String PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT = "20";

    static final String PREF_KEY_CALL_RINGTONE = "pref_key_call_ringtone";
    public static final String PREF_KEY_CALL_RINGTONE_DEFAULT = Settings.System.DEFAULT_RINGTONE_URI.toString();

    static final String PREF_KEY_CALL_USE_FLASH = "pref_key_call_use_flash";
    static final boolean PREF_KEY_CALL_USE_FLASH_DEFAULT = false;

    static final String PREF_KEY_PERMANENT_FOREGROUND_SERVICE = "pref_key_permanent_foreground_service";
    static final boolean PREF_KEY_PERMANENT_FOREGROUND_SERVICE_DEFAULT = false;


    // CUSTOMIZATION
    static final String PREF_KEY_APP_LANGUAGE = "pref_key_app_language";
    static final String PREF_KEY_DARK_MODE = "pref_key_dark_mode";
    static final boolean PREF_KEY_DARK_MODE_DEFAULT = false;
    static final String PREF_KEY_DARK_MODE_API29 = "pref_key_dark_mode_api29";
    static final String PREF_KEY_DARK_MODE_DEFAULT_API29 = "Auto";

    static final String PREF_KEY_FONT_SCALE = "pref_key_font_scale";
    static final String PREF_KEY_FONT_SCALE_DEFAULT = "1.0";

    static final String PREF_KEY_SCREEN_SCALE = "pref_key_screen_scale";
    static final String PREF_KEY_SCREEN_SCALE_DEFAULT = "1.0";

    static final String PREF_KEY_USE_SYSTEM_EMOJIS = "pref_key_use_system_emojis";
    static final boolean PREF_KEY_USE_SYSTEM_EMOJIS_DEFAULT = false;

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

    static final String PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS = "pref_key_disable_notification_suggestions";
    static final boolean PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS_DEFAULT = true;

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
    static final boolean PREF_KEY_PERMANENT_WEBSOCKET_DEFAULT = !BuildConfig.USE_FIREBASE_LIB;

    // CONTACTS & GROUPS

    static final String PREF_KEY_AUTO_JOIN_GROUPS = "pref_key_auto_join_groups";
    static final String PREF_KEY_AUTO_JOIN_GROUPS_DEFAULT = "contacts";

    public enum AutoJoinGroupsCategory {
        EVERYONE,
        CONTACTS,
        NOBODY;

        public String getStringValue() {
            switch (this) {
                case NOBODY:
                    return  "nobody";
                case EVERYONE:
                    return  "everyone";
                case CONTACTS:
                default:
                    return  "contacts";
            }
        }
    }

    static final String PREF_KEY_SHOW_TRUST_LEVELS = "pref_key_show_trust_levels";
    static final boolean PREF_KEY_SHOW_TRUST_LEVELS_DEFAULT = false;

    // LOCK SCREEN
    static final String PREF_KEY_USE_LOCK_SCREEN = "pref_key_use_lock_screen";
    static final boolean PREF_KEY_USE_LOCK_SCREEN_DEFAULT = false;

    static final String PREF_KEY_UNLOCK_BIOMETRY = "pref_key_unlock_biometry";
    static final boolean PREF_KEY_UNLOCK_BIOMETRY_DEFAULT = true;

    static final String PREF_KEY_LOCK_GRACE_TIME = "pref_key_lock_grace_time";
    static final String PREF_KEY_LOCK_GRACE_TIME_DEFAULT = "30";

    static final String PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES = "pref_key_unlock_failed_wipe_messages";
    static final boolean PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES_DEFAULT = false;

    static final String PREF_KEY_USE_EMERGENCY_PIN = "pref_key_use_emergency_pin";
    static final boolean PREF_KEY_USE_EMERGENCY_PIN_DEFAULT = false;

    static final String PREF_KEY_KEEP_LOCK_SERVICE_OPEN = "pref_key_keep_lock_service_open";
    static final boolean PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT = true;

    static final String PREF_KEY_PIN_IS_A_PASSWORD = "pref_key_pin_is_a_password";
    static final boolean PREF_KEY_PIN_IS_A_PASSWORD_DEFAULT = false;

    static final String PREF_KEY_PIN_HASH = "pref_key_pin_hash";
    static final String PREF_KEY_PLAIN_PIN = "pref_key_plain_pin";
    static final String PREF_KEY_EMERGENCY_PIN_HASH = "pref_key_emergency_pin_hash";
    static final String PREF_KEY_EMERGENCY_PLAIN_PIN = "pref_key_emergency_plain_pin";

    static final String PREF_KEY_RESET_PIN = "pref_key_reset_pin";


    // BACKUPS
    static final String PREF_KEY_RELOAD_BACKUP_CONFIGURATION = "pref_key_reload_backup_configuration";
    static final String PREF_KEY_GENERATE_NEW_BACKUP_KEY = "pref_key_generate_new_backup_key";

    static final String PREF_KEY_MANUAL_BACKUP = "pref_key_manual_backup";

    static final String PREF_KEY_ENABLE_AUTOMATIC_BACKUP = "pref_key_enable_automatic_backup";
    static final boolean PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT = false;
    static final String PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID = "pref_key_automatic_backup_device_unique_id";
    static final String PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION = "pref_key_automatic_backup_configuration";
    static final String PREF_KEY_MDM_AUTOMATIC_BACKUP = "pref_key_mdm_automatic_backup";
    static final String PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY = "pref_key_mdm_webdav_key_escrow_public_key";

    static final String PREF_KEY_MANAGE_CLOUD_BACKUPS = "pref_key_manage_cloud_backups";

    // CAMERA SETTINGS
    static final String PREF_KEY_CAMERA_RESOLUTION = "pref_key_camera_resolution";
    static final String PREF_KEY_CAMERA_RESOLUTION_DEFAULT = "-1";

    static final String PREF_KEY_REMOVE_METADATA = "pref_key_remove_metadata";
    static final boolean PREF_KEY_REMOVE_METADATA_DEFAULT = false;


    // OTHER SETTINGS
    static final String PREF_KEY_SEND_WITH_HARDWARE_ENTER = "pref_key_send_with_hardware_enter";
    static final boolean PREF_KEY_SEND_WITH_HARDWARE_ENTER_DEFAULT = false;

    static final String PREF_KEY_SENDING_FOREGROUND_SERVICE = "pref_key_sending_foreground_service";
    static final boolean PREF_KEY_SENDING_FOREGROUND_SERVICE_DEFAULT = true;

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

    static final String PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE = "pref_key_block_untrusted_certificate";
    static final BlockUntrustedCertificate PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE_DEFAULT = BlockUntrustedCertificate.ISSUER_CHANGED;

    public enum BlockUntrustedCertificate {
        ALWAYS,
        ISSUER_CHANGED,
        NEVER,
    }

    static final String PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS = "pref_key_no_notify_certificate_change_for_previews";
    static final boolean PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS_DEFAULT = false;

    public static final String USER_DIALOG_HIDE_BATTERY_OPTIMIZATION = "user_dialog_hide_battery_optimization";
    public static final String USER_DIALOG_HIDE_BACKGROUND_RESTRICTED = "user_dialog_hide_background_restricted";
    public static final String USER_DIALOG_HIDE_GOOGLE_APIS = "user_dialog_hide_google_apis";
    public static final String USER_DIALOG_HIDE_ALARM_SCHEDULING = "user_dialog_hide_alarm_scheduling";
    public static final String USER_DIALOG_HIDE_ALLOW_NOTIFICATIONS = "user_dialog_hide_allow_notifications";
    public static final String USER_DIALOG_HIDE_FULL_SCREEN_NOTIFICATION = "user_dialog_hide_full_screen_notification";
    public static final String USER_DIALOG_HIDE_OPEN_EXTERNAL_APP = "user_dialog_hide_open_external_app";
    public static final String USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION = "user_dialog_hide_forward_message_explanation";
    public static final String USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION = "user_dialog_hide_open_external_app_location";
    public static final String USER_DIALOG_HIDE_ADD_DEVICE_EXPLANATION = "user_dialog_hide_add_device_explanation";

    static final String PREF_KEY_DEBUG_LOG_LEVEL = "pref_key_debug_log_level";
    static final boolean PREF_KEY_DEBUG_LOG_LEVEL_DEFAULT = false;

    static final String PREF_KEY_LOW_BANDWIDTH_CALLS = "pref_key_low_bandwidth_calls";
    static final boolean PREF_KEY_LOW_BANDWIDTH_CALLS_DEFAULT = false;

    static final String PREF_KEY_USE_LEGACY_ZXING_SCANNER = "pref_key_use_legacy_zxing_scanner";
    static final boolean PREF_KEY_USE_LEGACY_ZXING_SCANNER_DEFAULT = false;


    static final String PREF_KEY_PREFERRED_KEYCLOAK_BROWSER = "pref_key_preferred_keycloak_browser";

    private static final String PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER = "pref_key_use_speaker_output_for_media_player";
    private static final boolean PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER_DEFAULT = true;

    // SEND & RECEIVE MESSAGES
    static final String PREF_KEY_AUTODOWNLOAD_SIZE = "pref_key_autodownload_size";
    static final String PREF_KEY_AUTODOWNLOAD_SIZE_DEFAULT = "10000000";


    static final String PREF_KEY_LINK_PREVIEW_OUTBOUND = "pref_key_link_preview_outbound";
    public static final boolean PREF_KEY_LINK_PREVIEW_OUTBOUND_DEFAULT = true;
    static final String PREF_KEY_LINK_PREVIEW_INBOUND = "pref_key_link_preview_inbound";
    public static final boolean PREF_KEY_LINK_PREVIEW_INBOUND_DEFAULT = false;

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


    // LOCATION
    static final String PREF_KEY_LOCATION_INTEGRATION = "pref_key_location_integration";
    public enum LocationIntegrationEnum {
        NONE, // used if user do not chose integration
        OSM,
        MAPS,
        BASIC;

        public String getString() {
            switch (this) {
                case OSM:
                    return PREF_VALUE_LOCATION_INTEGRATION_OSM;
                case MAPS:
                    return PREF_VALUE_LOCATION_INTEGRATION_MAPS;
                case BASIC:
                    return PREF_VALUE_LOCATION_INTEGRATION_BASIC;
                case NONE:
                default:
                    return null;
            }
        }
    }
    public static final String PREF_VALUE_LOCATION_INTEGRATION_OSM = "osm";
    public static final String PREF_VALUE_LOCATION_INTEGRATION_MAPS = "maps";
    public static final String PREF_VALUE_LOCATION_INTEGRATION_BASIC = "basic";

    static final String PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION = "pref_key_location_share_duration";
    static final long PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT = 3_600_000L;

    static final String PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL = "pref_key_location_share_interval";
    static final long PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL_DEFAULT = 60_000L;

    static final String PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS = "pref_key_location_hide_error_notifications";
    static final boolean PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS_DEFAULT = false;

    static final String PREF_KEY_LOCATION_OSM_LANGUAGE = "pref_key_location_osm_language";
    static final String PREF_KEY_LOCATION_OSM_LANGUAGE_DEFAULT = "default";

    // ACTIVITY VARIABLES

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
                                // TODO: remove this once location is no longer in beta
                                if (!getBetaFeaturesEnabled() && position > 6) {
                                    position--;
                                }
                                RecyclerView recyclerView = headersPreferenceFragment.getListView();
                                if (recyclerView != null) {
                                    RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
                                    if (viewHolder != null) {
                                        viewHolder.itemView.setPressed(true);
                                        handler.postDelayed(() -> onPreferenceStartFragment(headersPreferenceFragment, preference), 600);
                                    } else {
                                        recyclerView.scrollToPosition(position);
                                        int finalPosition = position;
                                        handler.postDelayed(() -> {
                                            RecyclerView.ViewHolder reViewHolder = recyclerView.findViewHolderForAdapterPosition(finalPosition);
                                            if (reViewHolder != null) {
                                                reViewHolder.itemView.setPressed(true);
                                            }
                                        }, 100);
                                        handler.postDelayed(() -> onPreferenceStartFragment(headersPreferenceFragment, preference), 700);
                                    }
                                }
                            }
                        }
                    }, 400);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_about) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
            List<String> extraFeatures = new ArrayList<>();
            if (SettingsActivity.getBetaFeaturesEnabled()) {
                extraFeatures.add("beta");
            }
            int uptimeSeconds = (int) ((System.currentTimeMillis() - App.appStartTimestamp) / 1000);
            final String uptime;
            if (uptimeSeconds > 86400) {
                uptime = getResources().getQuantityString(R.plurals.text_app_uptime_days, uptimeSeconds / 86400, uptimeSeconds / 86400, (uptimeSeconds % 86400) / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
            } else if (uptimeSeconds > 3600) {
                uptime = getString(R.string.text_app_uptime_hours, uptimeSeconds / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
            } else {
                uptime = getString(R.string.text_app_uptime, uptimeSeconds / 60, uptimeSeconds % 60);
            }
            builder.setTitle(R.string.dialog_title_about_olvid)
                    .setPositiveButton(R.string.button_label_ok, null);
            StringBuilder sb = new StringBuilder();
            if (extraFeatures.isEmpty() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                sb.append(getString(R.string.dialog_message_about_olvid, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Constants.SERVER_API_VERSION, Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION, AppDatabase.DB_SCHEMA_VERSION, uptime));
            } else {
                String features = String.join(getString(R.string.text_contact_names_separator), extraFeatures);
                sb.append(getString(R.string.dialog_message_about_olvid_extra_features, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Constants.SERVER_API_VERSION, Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION, AppDatabase.DB_SCHEMA_VERSION, features, uptime));
            }
            if (BuildConfig.USE_FIREBASE_LIB && GoogleServicesUtils.googleServicesAvailable(this) && !SettingsActivity.disablePushNotifications()) {
                sb.append("\n");
                CharSequence date;
                if (ObvFirebaseMessagingService.getLastPushNotificationTimestamp() == null) {
                    date = "-";
                } else {
                    date = StringUtils.getLongNiceDateString(this, ObvFirebaseMessagingService.getLastPushNotificationTimestamp());
                }
                sb.append(getString(R.string.dialog_message_about_last_push_notification, date));
            }

            if (BuildConfig.USE_GOOGLE_LIBS) {
                sb.append("\n\n");

                String link = getString(R.string.activity_title_open_source_licenses);
                sb.append(link);
                SpannableString spannableString = new SpannableString(sb);
                spannableString.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        GoogleServicesUtils.openOssMenuActivity(SettingsActivity.this);
                    }
                }, spannableString.length() - link.length(), spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setMessage(spannableString);
            } else {
                builder.setMessage(sb);
            }
            Dialog dialog = builder.create();

            dialog.setOnShowListener(dialog1 -> {
                if (dialog1 instanceof AlertDialog) {
                    TextView messageTextView = ((AlertDialog) dialog1).findViewById(android.R.id.message);
                    if (messageTextView != null) {
                        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    }
                }
            });
            dialog.show();

            return true;
        } else if (itemId == R.id.action_check_update) {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
            }
            return true;
        } else if (itemId == R.id.action_help_faq) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://olvid.io/faq/")));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class HeadersPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_header, rootKey);

            final FragmentActivity activity = requireActivity();

            if (BuildConfig.USE_BILLING_LIB) {
                BillingUtils.loadSubscriptionSettingsHeader(activity, getPreferenceScreen());
            }

            {
                if (!getBetaFeaturesEnabled()) {
                    Preference locationHeaderPreference = findPreference(PREF_HEADER_KEY_LOCATION);
                    if (locationHeaderPreference != null) {
                        getPreferenceScreen().removePreference(locationHeaderPreference);
                    }
                }
            }

            {
                App.runThread(() -> {
                    if (AppDatabase.getInstance().actionShortcutConfigurationDao().countAll() > 0) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Preference actionShortcutPreference = new Preference(activity);
                            actionShortcutPreference.setWidgetLayoutResource(R.layout.preference_widget_header_chevron);
                            actionShortcutPreference.setIcon(R.drawable.ic_pref_widget);
                            actionShortcutPreference.setTitle(R.string.pref_title_widgets);
                            actionShortcutPreference.setFragment(WidgetListFragment.class.getName());
                            getPreferenceScreen().addPreference(actionShortcutPreference);
                        });
                    }
                });
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        String prefFragmentName = pref.getFragment();
        if (prefFragmentName == null) {
            return false;
        }
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), prefFragmentName);

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
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (forceDarkMode) {
                editor.putString(PREF_KEY_DARK_MODE_API29, "Dark");
            } else {
                editor.putString(PREF_KEY_DARK_MODE_API29, "Light");
            }
        } else {
            editor.putBoolean(PREF_KEY_DARK_MODE, forceDarkMode);
        }
        editor.apply();
    }

    public static float getFontScale() {
        String scaleString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_FONT_SCALE, PREF_KEY_FONT_SCALE_DEFAULT);
        try {
            return Float.parseFloat(scaleString);
        } catch (Exception ignored) {
        }
        return 1.0f;
    }

    public static float getScreenScale() {
        String scaleString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_SCREEN_SCALE, PREF_KEY_SCREEN_SCALE_DEFAULT);
        try {
            return Float.parseFloat(scaleString);
        } catch (Exception ignored) {
        }
        return 1.0f;
    }

    public static Context overrideContextScales(Context baseContext) {
        final Context newContext;
        float customFontScale = SettingsActivity.getFontScale();
        float customScreenScale = SettingsActivity.getScreenScale();
        if (customFontScale != 1.0f || customScreenScale != 1.0f) {
            Configuration baseConfiguration = baseContext.getResources().getConfiguration();
            Configuration configuration = new Configuration();
            configuration.fontScale = baseContext.getResources().getConfiguration().fontScale * customFontScale;
            configuration.densityDpi = (int) (baseContext.getResources().getConfiguration().densityDpi * customScreenScale);
            configuration.screenWidthDp = (int) (baseConfiguration.screenWidthDp / customScreenScale);
            configuration.screenHeightDp = (int) (baseConfiguration.screenHeightDp / customScreenScale);
            configuration.smallestScreenWidthDp = (int) (baseConfiguration.smallestScreenWidthDp / customScreenScale);
            newContext = baseContext.createConfigurationContext(configuration);
        } else {
            newContext = baseContext;
        }
        return newContext;
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


    public static boolean useSystemEmojis() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_SYSTEM_EMOJIS, PREF_KEY_USE_SYSTEM_EMOJIS_DEFAULT);
    }

    public static void setUseSystemEmojis(boolean useSystemEmojis) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_USE_SYSTEM_EMOJIS, useSystemEmojis);
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
        return Long.parseLong(downloadSizeString);
    }

    public static void setAutoDownloadSize(long autoDownloadSize) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_AUTODOWNLOAD_SIZE, Long.toString(autoDownloadSize));
        editor.apply();
    }

    public static boolean isLinkPreviewInbound() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(SettingsActivity.PREF_KEY_LINK_PREVIEW_INBOUND, SettingsActivity.PREF_KEY_LINK_PREVIEW_INBOUND_DEFAULT);
    }

    public static boolean isLinkPreviewOutbound() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(SettingsActivity.PREF_KEY_LINK_PREVIEW_OUTBOUND, SettingsActivity.PREF_KEY_LINK_PREVIEW_OUTBOUND_DEFAULT);
    }

    public static void setLinkPreviewInbound(boolean defaultLinkPreviewInbound) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_LINK_PREVIEW_INBOUND, defaultLinkPreviewInbound);
        editor.apply();
    }

    public static void setLinkPreviewOutbound(boolean defaultLinkPreviewOutbound) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_LINK_PREVIEW_OUTBOUND, defaultLinkPreviewOutbound);
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

    public static boolean useSendingForegroundService() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SENDING_FOREGROUND_SERVICE, PREF_KEY_SENDING_FOREGROUND_SERVICE_DEFAULT);
    }

    public static void setSendingForegroundService(boolean enabled) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SENDING_FOREGROUND_SERVICE, enabled);
        editor.apply();
    }

    @NonNull
    public static PingConnectivityIndicator getPingConnectivityIndicator() {
        String pingConnectivityIndicator = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PING_CONNECTIVITY_INDICATOR, PREF_KEY_PING_CONNECTIVITY_INDICATOR_DEFAULT);
        switch (pingConnectivityIndicator) {
            case "dot":
                return PingConnectivityIndicator.DOT;
            case "line":
                return PingConnectivityIndicator.LINE;
            case "full":
                return PingConnectivityIndicator.FULL;
        }
        return PingConnectivityIndicator.NONE;
    }

    public static void setPingConnectivityIndicator(String indicatorString) {
        String pingConnectivityIndicatorString;
        switch (indicatorString) {
            case "dot":
            case "line":
            case "full":
                pingConnectivityIndicatorString = indicatorString;
                break;
            default:
                pingConnectivityIndicatorString = null;
                break;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (pingConnectivityIndicatorString == null) {
            editor.remove(PREF_KEY_PING_CONNECTIVITY_INDICATOR);
        } else {
            editor.putString(PREF_KEY_PING_CONNECTIVITY_INDICATOR, pingConnectivityIndicatorString);
        }
        editor.apply();

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
            if ("".equals(retentionCountString)) {
                return null;
            }
            long count = Long.parseLong(retentionCountString);
            return count == 0 ? null : count;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultDiscussionRetentionCount(long count) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (count == 0) {
            editor.remove(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT);
        } else {
            editor.putString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT, Long.toString(count));
        }
        editor.apply();
    }

    public static Long getDefaultDiscussionRetentionDuration() {
        try {
            String retentionDurationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION, PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION_DEFAULT);
            if ("null".equals(retentionDurationString)) {
                return null;
            }
            return Long.parseLong(retentionDurationString);
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
            if ("null".equals(visibilityDurationString)) {
                return null;
            }
            return Long.parseLong(visibilityDurationString);
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
            if ("null".equals(existenceDurationString)) {
                return null;
            }
            return Long.parseLong(existenceDurationString);
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

    public static void resetScaledTurn() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.remove(PREF_KEY_SCALED_TURN_REGION);
        editor.apply();
    }


    public static AutoJoinGroupsCategory getAutoJoinGroups() {
        String stringValue = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_AUTO_JOIN_GROUPS, PREF_KEY_AUTO_JOIN_GROUPS_DEFAULT);
        return getAutoJoinGroupsFromString(stringValue);
    }

    @NonNull
    public static AutoJoinGroupsCategory getAutoJoinGroupsFromString(String stringValue) {
        if (stringValue != null) {
            switch (stringValue) {
                case "nobody":
                    return AutoJoinGroupsCategory.NOBODY;
                case "everyone":
                    return AutoJoinGroupsCategory.EVERYONE;
                case "contacts":
                default:
                    break;
            }
        }
        return AutoJoinGroupsCategory.CONTACTS;
    }

    public static void setAutoJoinGroups(@NonNull AutoJoinGroupsCategory autoJoinGroups) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_AUTO_JOIN_GROUPS, autoJoinGroups.getStringValue());
        editor.apply();
    }

    public static boolean showTrustLevels() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SHOW_TRUST_LEVELS, PREF_KEY_SHOW_TRUST_LEVELS_DEFAULT);
    }

    public static void setShowTrustLevels(boolean show) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SHOW_TRUST_LEVELS, show);
        editor.apply();
    }

    public static boolean useApplicationLockScreen() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_LOCK_SCREEN, PREF_KEY_USE_LOCK_SCREEN_DEFAULT);
    }

    public static boolean useEmergencyPIN() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_EMERGENCY_PIN, PREF_KEY_USE_EMERGENCY_PIN_DEFAULT);
    }

    public static boolean wipeMessagesOnUnlockFails() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES, PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES_DEFAULT);
    }

    public static void setWipeMessagesOnUnlockFails(boolean wipe) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES, wipe);
        editor.apply();
    }

    public static boolean isNotificationContentHidden() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_HIDE_NOTIFICATION_CONTENTS, PREF_KEY_HIDE_NOTIFICATION_CONTENTS_DEFAULT);
    }

    public static void setNotificationContentHidden(boolean contentHidden) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_HIDE_NOTIFICATION_CONTENTS, contentHidden);
        editor.apply();
    }

    public static boolean isNotificationSuggestionAllowed() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS, PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS_DEFAULT);
    }

    public static void setNotificationSuggestionAllowed(boolean suggestionsAllowed) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS, suggestionsAllowed);
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
        return new String[]{
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

    public static boolean useLegacyZxingScanner() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_USE_LEGACY_ZXING_SCANNER, PREF_KEY_USE_LEGACY_ZXING_SCANNER_DEFAULT);
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

    public static BlockUntrustedCertificate getBlockUntrustedCertificate() {
        // never block a connection for API < 24 as the shutdownInput/shutdownOutput technique we use is not supported yet
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return BlockUntrustedCertificate.NEVER;
        }
        String value = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE, null);
        if (value != null) {
            switch (value) {
                case "always":
                    return BlockUntrustedCertificate.ALWAYS;
                case "never":
                    return BlockUntrustedCertificate.NEVER;
                case "issuer":
                    return BlockUntrustedCertificate.ISSUER_CHANGED;
            }
        }
        return PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE_DEFAULT;
    }

    public static void setBlockUntrustedCertificate(String untrustedCertificateCategoryString) {
        String untrustedCertificateCategory;
        switch (untrustedCertificateCategoryString) {
            case "always":
            case "issuer":
            case "never":
                untrustedCertificateCategory = untrustedCertificateCategoryString;
                break;
            default:
                untrustedCertificateCategory = null;
                break;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (untrustedCertificateCategory == null) {
            editor.remove(PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE);
        } else {
            editor.putString(PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE, untrustedCertificateCategory);
        }
        editor.apply();
    }

    public static boolean getNoNotifyCertificateChangeForPreviews() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS, PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS_DEFAULT);
    }

    public static void setNoNotifyCertificateChangeForPreviews(boolean doNotNotify) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS, doNotNotify);
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

    public static void saveEmergencyPIN(String PIN) {
        byte[] salt = new byte[PIN_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        byte[] hash = computePINHash(PIN, salt);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (hash == null) {
            Logger.w("Error computing PIN hash, storing it in clear...");
            editor.putString(PREF_KEY_EMERGENCY_PLAIN_PIN, PIN);
            editor.remove(PREF_KEY_EMERGENCY_PIN_HASH);
        } else {
            editor.remove(PREF_KEY_EMERGENCY_PLAIN_PIN);
            byte[] saltAndHash = new byte[PIN_SALT_LENGTH + hash.length];
            System.arraycopy(salt, 0, saltAndHash, 0, PIN_SALT_LENGTH);
            System.arraycopy(hash, 0, saltAndHash, PIN_SALT_LENGTH, hash.length);
            String base64SaltAndHash = Base64.encodeToString(saltAndHash, Base64.NO_PADDING | Base64.NO_WRAP);
            editor.putString(PREF_KEY_EMERGENCY_PIN_HASH, base64SaltAndHash);
        }
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

    public static boolean verifyEmergencyPIN(String PIN) {
        String plainPIN = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_EMERGENCY_PLAIN_PIN, null);
        String hashedPIN = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_EMERGENCY_PIN_HASH, null);
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
        clearEmergencyPIN();
    }

    public static void clearEmergencyPIN() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_USE_EMERGENCY_PIN, false);
        editor.remove(PREF_KEY_EMERGENCY_PLAIN_PIN);
        editor.remove(PREF_KEY_EMERGENCY_PIN_HASH);
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

    public static void setUseBiometryToUnlock(boolean useBiometry) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_UNLOCK_BIOMETRY, useBiometry);
        editor.apply();
    }

    public static int getLockGraceTime() {
        String timeString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCK_GRACE_TIME, PREF_KEY_LOCK_GRACE_TIME_DEFAULT);
        return Integer.parseInt(timeString);
    }

    public static void setLockGraceTime(int graceTime) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_LOCK_GRACE_TIME, Integer.toString(graceTime));
        editor.apply();
    }

    public static boolean keepLockServiceOpen() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_KEEP_LOCK_SERVICE_OPEN, PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT);
    }

    public static void setKeepLockServiceOpen(boolean keepOpen) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_KEEP_LOCK_SERVICE_OPEN, keepOpen);
        editor.apply();
    }

    public static void setUseAutomaticBackup(boolean useAutomaticBackup) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_ENABLE_AUTOMATIC_BACKUP, useAutomaticBackup);
        editor.apply();
    }

    public static boolean useAutomaticBackup() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_ENABLE_AUTOMATIC_BACKUP, PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT);
    }

    public static void setMdmAutomaticBackup(boolean mdmAutomaticBackup) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (mdmAutomaticBackup) {
            editor.putBoolean(PREF_KEY_MDM_AUTOMATIC_BACKUP, true);
        } else {
            editor.remove(PREF_KEY_MDM_AUTOMATIC_BACKUP);
        }
        editor.apply();
    }

    public static boolean isMdmAutomaticBackup() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_MDM_AUTOMATIC_BACKUP, false);
    }

    public static void setMdmWebdavKeyEscrowPublicKey(String publicKeyPem) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY, publicKeyPem);
        editor.apply();
    }

    public static String getMdmWebdavKeyEscrowPublicKey() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY, null);
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

    // this method is used when migrating to the new serialized backup configuration
    @Nullable
    public static String migrateAutomaticBackupAccount() {
        String account = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString("pref_key_automatic_backup_account", null);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.remove("pref_key_automatic_backup_account");
        editor.apply();
        return account;
    }

    public static BackupCloudProviderService.CloudProviderConfiguration getAutomaticBackupConfiguration() {
        String serializedConfiguration = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION, null);
        if (serializedConfiguration != null) {
            try {
                return AppSingleton.getJsonObjectMapper().readValue(serializedConfiguration, BackupCloudProviderService.CloudProviderConfiguration.class);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void setAutomaticBackupConfiguration(BackupCloudProviderService.CloudProviderConfiguration configuration) throws Exception {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (configuration == null) {
            editor.remove(PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION);
        } else {
            editor.putString(PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION, AppSingleton.getJsonObjectMapper().writeValueAsString(configuration));
        }
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
        return intToVibrationPattern(Integer.parseInt(pattern));
    }

    public static String getMessageVibrationPatternRaw() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_VIBRATION_PATTERN, PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT);
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
        } catch (Exception ignored) {
        }
    }

    public static long[] intToVibrationPattern(int patternIndex) {
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
        String color = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_MESSAGE_LED_COLOR, PREF_KEY_MESSAGE_LED_COLOR_DEFAULT);
        if ("".equals(color)) {
            return null;
        }
        return color;
    }

    public static void setMessageLedColor(String color) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (color == null) {
            editor.putString(PREF_KEY_MESSAGE_LED_COLOR, "");
        } else {
            editor.putString(PREF_KEY_MESSAGE_LED_COLOR, color);
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
        return intToVibrationPattern(Integer.parseInt(pattern));
    }

    public static String getCallVibrationPatternRaw() {
        String pattern = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_CALL_VIBRATION_PATTERN, PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT);
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
        } catch (Exception ignored) {
        }
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

    public static String getWebclientLanguage() {
        String language = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LANGUAGE_WEBCLIENT, PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT);
        if ("".equals(language)) {
            language = PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT;
        }
        return language;
    }

    public static void setWebclientLanguage(String language) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (language == null || "".equals(language)) {
            editor.remove(PREF_KEY_LANGUAGE_WEBCLIENT);
        } else {
            editor.putString(PREF_KEY_LANGUAGE_WEBCLIENT, language);
        }
        editor.apply();
    }

    public static String gWebclientTheme() {
        String theme = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_THEME_WEBCLIENT, PREF_KEY_THEME_WEBCLIENT_DEFAULT);
        if ("".equals(theme)) {
            theme = PREF_KEY_THEME_WEBCLIENT_DEFAULT;
        }
        return theme;
    }

    public static void setThemeWebclient(String theme) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (theme == null) {
            editor.remove(PREF_KEY_THEME_WEBCLIENT);
        } else {
            switch (theme) {
                case "dark":
                case "light":
                case "BrowserDefault":
                case "AppDefault":
                    editor.putString(PREF_KEY_THEME_WEBCLIENT, theme);
                    break;
                default:
                    editor.remove(PREF_KEY_THEME_WEBCLIENT);
                    break;
            }
        }
        editor.apply();
    }

    public static boolean getWebclientSendOnEnter() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SEND_ON_ENTER_WEBCLIENT, PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT);
    }

    public static void setWebclientSendOnEnter(Boolean send) {
        if (send == null) {
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SEND_ON_ENTER_WEBCLIENT, send);
        editor.apply();
    }

    public static boolean showWebclientNotificationsInBrowser() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER_DEFAULT);
    }

    public static void setShowWebclientNotificationsInBrowser(Boolean show) {
        if (show == null) {
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, show);
        editor.apply();
    }

    public static boolean playWebclientNotificationsSoundInBrowser() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT);
    }


    public static void setPlayWebclientNotificationsSoundInBrowser(Boolean notifications) {
        if (notifications == null) {
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, notifications);
        editor.apply();
    }

    public static boolean keepWebclientAliveAfterClose() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE, PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE_DEFAULT);
    }

    public static void setKeepWebclientAliveAfterClose(boolean keepAlive) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE, keepAlive);
        editor.apply();
    }

    public static boolean showWebclientErrorNotifications() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_SHOW_ERROR_NOTIFICATIONS, PREF_KEY_SHOW_ERROR_NOTIFICATIONS_DEFAULT);
    }

    public static void setShowWebclientErrorNotifications(boolean show) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_SHOW_ERROR_NOTIFICATIONS, show);
        editor.apply();
    }

    public static boolean webclientNotifyAfterInactivity() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY, PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY_DEFAULT);
    }

    public static void setWebclientNotifyAfterInactivity(boolean notify) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY, notify);
        editor.apply();
    }

    public static boolean isWebclientUnlockRequired() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT, PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT_DEFAULT);
    }

    public static void setWebclientUnlockRequired(boolean unlockRequired) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putBoolean(PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT, unlockRequired);
        editor.apply();
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
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void setComposeMessageIconPreferredOrder(@NonNull List<Integer> icons) {
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

    public static List<String> getPreferredReactions() {
        String preferredReactions = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_PREFERRED_REACTIONS, null);
        try {
            if (preferredReactions != null) {
                String[] reactions = preferredReactions.split(",");
                return new ArrayList<>(Arrays.asList(reactions)); // build a new ArrayList to make the list mutable
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(Arrays.asList(PREF_KEY_PREFERRED_REACTIONS_DEFAULT));
    }

    public static void setPreferredReactions(List<String> reactions) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (reactions == null) {
            editor.remove(PREF_KEY_PREFERRED_REACTIONS);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String reaction : reactions) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(reaction);
            }
            editor.putString(PREF_KEY_PREFERRED_REACTIONS, sb.toString());
        }
        editor.apply();
    }

    @NonNull
    public static LocationIntegrationEnum getLocationIntegration() {
        String integrationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_INTEGRATION, null);

        if (integrationString == null) {
            return LocationIntegrationEnum.NONE;
        }

        switch (integrationString) {
            case PREF_VALUE_LOCATION_INTEGRATION_OSM: {
                return LocationIntegrationEnum.OSM;
            }
            case PREF_VALUE_LOCATION_INTEGRATION_MAPS: {
                return LocationIntegrationEnum.MAPS;
            }
            case PREF_VALUE_LOCATION_INTEGRATION_BASIC: {
                return LocationIntegrationEnum.BASIC;
            }
            default: {
                return LocationIntegrationEnum.NONE;
            }
        }
    }

    // return language set by user ot system default language
    public static String getLocationOpenStreetMapLanguage() {
        String language = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_OSM_LANGUAGE, PREF_KEY_LOCATION_OSM_LANGUAGE_DEFAULT);
        if (language.equals(PREF_KEY_LOCATION_OSM_LANGUAGE_DEFAULT)) {
            return Locale.getDefault().getLanguage();
        }
        return language;
    }

    public static void setLocationIntegration(String integrationString) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        if (integrationString == null) {
            editor.remove(PREF_KEY_LOCATION_INTEGRATION);
        } else {
            switch (integrationString) {
                case PREF_VALUE_LOCATION_INTEGRATION_MAPS:
                case PREF_VALUE_LOCATION_INTEGRATION_BASIC:
                case PREF_VALUE_LOCATION_INTEGRATION_OSM: {
                    editor.putString(PREF_KEY_LOCATION_INTEGRATION, integrationString);
                    break;
                }
                default: {
                    editor.remove(PREF_KEY_LOCATION_INTEGRATION);
                    break;
                }
            }
        }
        editor.apply();
    }

    public static String getLocationOpenStreetMapRawLanguage() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_OSM_LANGUAGE, PREF_KEY_LOCATION_OSM_LANGUAGE_DEFAULT);
    }

    public static void setLocationOpenStreetMapRawLanguage(String language) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_LOCATION_OSM_LANGUAGE, language);
        editor.apply();
    }


    public static Long getLocationDefaultSharingDurationValue() {
        String durationString = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION, null);

        if (durationString == null) {
            return PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT;
        }
        try {
            return Long.parseLong(durationString);
        } catch (NumberFormatException e) {
            return PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT;
        }
    }

    public static void setLocationDefaultSharingDurationValue(long duration) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION, Long.toString(duration));
        editor.apply();
    }

    public static String getLocationDefaultSharingDurationLongString(Context context) {
        String duration = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION, null);

        // default value not set
        if (duration == null) {
            duration = String.valueOf(PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT);
        }

        String[] valuesArray = context.getResources().getStringArray(R.array.share_location_duration_values);
        String[] longStringArray = context.getResources().getStringArray(R.array.share_location_duration_long_strings);

        int index = Arrays.asList(valuesArray).indexOf(duration);
        if (index >= 0 && index < longStringArray.length) {
            return longStringArray[index];
        }

        // fallback mechanism
        if (longStringArray.length == 0) {
            return "";
        }
        return longStringArray[0];
    }

    public static long getLocationDefaultSharingIntervalValue() {
        String interval = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL, null);

        if (interval == null) {
            return PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL_DEFAULT;
        }
        try {
            return Long.parseLong(interval);
        } catch (NumberFormatException e) {
            return PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL_DEFAULT;
        }
    }

    public static void setLocationDefaultSharingIntervalValue(long interval) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit();
        editor.putString(PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL, Long.toString(interval));
        editor.apply();
    }

    public static String getLocationDefaultSharingIntervalLongString(Context context) {
        String duration = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL, null);

        // default value not set
        if (duration == null) {
            duration = String.valueOf(PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL_DEFAULT);
        }

        String[] valuesArray = context.getResources().getStringArray(R.array.share_location_interval_values);
        String[] longStringArray = context.getResources().getStringArray(R.array.share_location_interval_long_strings);

        int index = Arrays.asList(valuesArray).indexOf(duration);
        if (index >= 0 && index < longStringArray.length) {
            return longStringArray[index];
        }

        // fallback mechanism
        if (longStringArray.length == 0) {
            return "";
        }
        return longStringArray[0];
    }

    public static boolean hideLocationErrorsNotifications() {
        return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                .getBoolean(PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS, PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS_DEFAULT);
    }
}
