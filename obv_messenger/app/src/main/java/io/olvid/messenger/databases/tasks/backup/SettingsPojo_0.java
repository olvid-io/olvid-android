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

package io.olvid.messenger.databases.tasks.backup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;

@JsonIgnoreProperties(ignoreUnknown = true)
class SettingsPojo_0 {
    public Boolean beta;

    public String message_vibration_pattern;
    public String message_led_color;
    public String call_vibration_pattern;
    public Boolean call_use_flash;
    public Boolean permanent_foreground_service;

    public Boolean dark_mode;
    public Boolean system_emojis;
    public Boolean internal_viewer;
    public Boolean contact_sort_last_name;
    public Boolean contact_uppercase_last_name;
    public String contact_display_name_format;

    public Boolean send_read_receipt;
    public Boolean hide_notification_contents;
    public Boolean expose_recent_discussions;
    public Boolean incognito_keyboard;
    public Boolean prevent_screen_capture;
    public Integer hidden_profile_policy;
    public Integer hidden_profile_background_grace;
    public Boolean disable_push_notifications;
    public Boolean permanent_websocket;

    public String auto_join_groups;
    public Boolean show_trust_level;

    public Long auto_download_size;
    public Boolean auto_open_limited_visibility;
    public Boolean retain_wiped_outbound;
    public Long default_retention_count;
    public Long default_retention_duration;
    public Boolean default_read_once;
    public Long default_visibility_duration;
    public Long default_existence_duration;

    public Integer camera_resize_resolution;
    public Boolean remove_jpeg_metadata;

    public Boolean share_app_version;
    public Boolean notify_certificate_change;
    public Boolean sending_foreground_service;
    public String qr_correction_level;
    public Boolean low_bandwidth_calls;
    public Boolean debug_log_level;

    public List<String> preferred_reactions;

    @JsonIgnore
    static SettingsPojo_0 build() {
        SettingsPojo_0 settingsPojo = new SettingsPojo_0();

        settingsPojo.beta = SettingsActivity.getBetaFeaturesEnabled();

        settingsPojo.message_vibration_pattern = SettingsActivity.getMessageVibrationPatternRaw();
        settingsPojo.message_led_color = SettingsActivity.getMessageLedColor();
        settingsPojo.call_vibration_pattern = SettingsActivity.getCallVibrationPatternRaw();
        settingsPojo.call_use_flash = SettingsActivity.useFlashOnIncomingCall();
        settingsPojo.permanent_foreground_service = SettingsActivity.usePermanentForegroundService();

        settingsPojo.dark_mode = SettingsActivity.getForcedDarkMode();
        settingsPojo.system_emojis = SettingsActivity.useSystemEmojis();
        settingsPojo.internal_viewer = SettingsActivity.useInternalImageViewer();
        settingsPojo.contact_sort_last_name = SettingsActivity.getSortContactsByLastName();
        settingsPojo.contact_uppercase_last_name = SettingsActivity.getUppercaseLastName();
        settingsPojo.contact_display_name_format = SettingsActivity.getContactDisplayNameFormat();

        settingsPojo.send_read_receipt = SettingsActivity.getDefaultSendReadReceipt();
        settingsPojo.hide_notification_contents = SettingsActivity.isNotificationContentHidden();
        settingsPojo.expose_recent_discussions = SettingsActivity.exposeRecentDiscussions();
        settingsPojo.incognito_keyboard = SettingsActivity.useKeyboardIncognitoMode();
        settingsPojo.prevent_screen_capture = SettingsActivity.preventScreenCapture();
        settingsPojo.hidden_profile_policy = SettingsActivity.getHiddenProfileClosePolicy();
        if (settingsPojo.hidden_profile_policy == SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND) {
            settingsPojo.hidden_profile_background_grace = SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelay();
        }
        settingsPojo.disable_push_notifications = SettingsActivity.disablePushNotifications();
        settingsPojo.permanent_websocket = SettingsActivity.usePermanentWebSocket();

        switch (SettingsActivity.getAutoJoinGroups()) {
            case NOBODY:
                settingsPojo.auto_join_groups = "nobody";
                break;
            case EVERYONE:
                settingsPojo.auto_join_groups = "everyone";
                break;
            case CONTACTS:
                settingsPojo.auto_join_groups = "contacts";
                break;
        }

        settingsPojo.auto_download_size = SettingsActivity.getAutoDownloadSize();
        settingsPojo.auto_open_limited_visibility = SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages();
        settingsPojo.retain_wiped_outbound = SettingsActivity.getDefaultRetainWipedOutboundMessages();
        settingsPojo.default_retention_count = SettingsActivity.getDefaultDiscussionRetentionCount();
        settingsPojo.default_retention_duration = SettingsActivity.getDefaultDiscussionRetentionDuration();
        settingsPojo.default_read_once = SettingsActivity.getDefaultDiscussionReadOnce();
        settingsPojo.default_visibility_duration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
        settingsPojo.default_existence_duration = SettingsActivity.getDefaultDiscussionExistenceDuration();

        settingsPojo.camera_resize_resolution = SettingsActivity.getCameraResolution();
        settingsPojo.remove_jpeg_metadata = SettingsActivity.getMetadataRemovalPreference();

        settingsPojo.share_app_version = SettingsActivity.shareAppVersion();
        settingsPojo.notify_certificate_change = SettingsActivity.notifyCertificateChange();
        settingsPojo.sending_foreground_service = SettingsActivity.useSendingForegroundService();
        settingsPojo.qr_correction_level = SettingsActivity.getQrCorrectionLevel();
        settingsPojo.low_bandwidth_calls = SettingsActivity.useLowBandwidthInCalls();
        settingsPojo.debug_log_level = SettingsActivity.useDebugLogLevel();

        settingsPojo.preferred_reactions = SettingsActivity.getPreferredReactions();
        return settingsPojo;
    }

    @JsonIgnore
    void restore() {
        if (beta != null) { SettingsActivity.setBetaFeaturesEnabled(beta); }

        if (message_vibration_pattern != null) { SettingsActivity.setMessageVibrationPatternRaw(message_vibration_pattern); }
        if (message_led_color != null) { SettingsActivity.setMessageLedColor(message_led_color); }
        if (call_vibration_pattern != null) { SettingsActivity.setCallVibrationPatternRaw(call_vibration_pattern); }
        if (call_use_flash != null) { SettingsActivity.setUseFlashOnIncomingCall(call_use_flash); }
        if (permanent_foreground_service != null) { SettingsActivity.setUsePermanentForegroundService(permanent_foreground_service); }

        if (dark_mode != null) { SettingsActivity.setForcedDarkMode(dark_mode); }
        if (system_emojis != null) { SettingsActivity.setUseSystemEmojis(system_emojis); }
        if (internal_viewer != null) { SettingsActivity.setUseInternalImageViewer(internal_viewer); }
        if (contact_sort_last_name != null) { SettingsActivity.setSortContactsByLastName(contact_sort_last_name); }
        if (contact_uppercase_last_name != null) { SettingsActivity.setUppercaseLastName(contact_uppercase_last_name); }
        if (contact_display_name_format != null) { SettingsActivity.setContactDisplayNameFormat(contact_display_name_format); }

        if (send_read_receipt != null) { SettingsActivity.setDefaultSendReadReceipt(send_read_receipt); }
        if (hide_notification_contents != null) { SettingsActivity.setNotificationContentHidden(hide_notification_contents); }
        if (expose_recent_discussions != null) { SettingsActivity.setExposeRecentDiscussions(expose_recent_discussions); }
        if (incognito_keyboard != null) { SettingsActivity.setUseKeyboardIncognitoMode(incognito_keyboard); }
        if (prevent_screen_capture != null) { SettingsActivity.setPreventScreenCapture(prevent_screen_capture); }
        if (hidden_profile_policy != null) {
            SettingsActivity.setHiddenProfileClosePolicy(hidden_profile_policy, hidden_profile_background_grace != null ? hidden_profile_background_grace : 0);
        }
        if (disable_push_notifications != null) { SettingsActivity.setDisablePushNotifications(disable_push_notifications); }
        if (permanent_websocket != null) { SettingsActivity.setUsePermanentWebSocket(permanent_websocket); }

        if (auto_join_groups != null) { SettingsActivity.setAutoJoinGroups(SettingsActivity.getAutoJoinGroupsFromString(auto_join_groups)); }
        if (show_trust_level != null) { SettingsActivity.setShowTrustLevels(show_trust_level); }

        if (auto_download_size != null) { SettingsActivity.setAutoDownloadSize(auto_download_size); }
        if (auto_open_limited_visibility != null) { SettingsActivity.setDefaultAutoOpenLimitedVisibilityInboundMessages(auto_open_limited_visibility); }
        if (retain_wiped_outbound != null) { SettingsActivity.setDefaultRetainWipedOutboundMessages(retain_wiped_outbound); }
        if (default_retention_count != null) { SettingsActivity.setDefaultDiscussionRetentionCount(default_retention_count); }
        if (default_retention_duration != null) { SettingsActivity.setDefaultDiscussionRetentionDuration(default_retention_duration); }
        if (default_read_once != null) { SettingsActivity.setDefaultDiscussionReadOnce(default_read_once); }
        if (default_visibility_duration != null && default_visibility_duration > 0) { SettingsActivity.setDefaultDiscussionVisibilityDuration(default_visibility_duration); }
        if (default_existence_duration != null && default_existence_duration > 0) { SettingsActivity.setDefaultDiscussionExistenceDuration(default_existence_duration); }

        if (camera_resize_resolution != null) { SettingsActivity.setCameraResolution(camera_resize_resolution); }
        if (remove_jpeg_metadata != null) { SettingsActivity.setMetadataRemovalPreference(remove_jpeg_metadata); }

        if (share_app_version != null) { SettingsActivity.setShareAppVersion(share_app_version); }
        if (notify_certificate_change != null) { SettingsActivity.setNotifyCertificateChange(notify_certificate_change); }
        if (sending_foreground_service != null) { SettingsActivity.setSendingForegroundService(sending_foreground_service); }
        if (qr_correction_level != null) { SettingsActivity.setQrCorrectionLevel(qr_correction_level); }
        if (low_bandwidth_calls != null) { SettingsActivity.setUseLowBandwidthInCalls(low_bandwidth_calls); }
        if (debug_log_level != null) { SettingsActivity.setUseDebugLogLevel(debug_log_level); }

        if (preferred_reactions != null) { SettingsActivity.setPreferredReactions(preferred_reactions); }

        // run hooks for notification channel and discussion name
        App.runThread(new ContactDisplayNameFormatChangedTask());
        AndroidNotificationManager.updateMessageChannel(false);
    }
}
