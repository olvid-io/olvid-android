/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsPojo_0 {
    public Boolean beta;

    public String message_vibration_pattern;
    public String message_led_color;
    public String call_vibration_pattern;
    public Boolean call_use_flash;
    public Boolean permanent_foreground_service;

    public Boolean dark_mode;
    public Boolean system_emojis;
    public Boolean internal_viewer;

    public Boolean hide_notification_contents;
    public Boolean allow_notification_suggestions;
    public Boolean expose_recent_discussions;
    public Boolean incognito_keyboard;
    public Boolean prevent_screen_capture;
    public Integer hidden_profile_policy;
    public Integer hidden_profile_background_grace;
    public Boolean disable_push_notifications;
    public Boolean permanent_websocket;

    public String auto_join_groups; // sync
    public Boolean show_trust_level;
    public Boolean contact_sort_last_name;
    public Boolean contact_uppercase_last_name;
    public String contact_display_name_format;

    public Boolean lock_biometry;
    public Integer lock_delay_s;
    public Boolean lock_notification;
    public Boolean lock_wipe_on_fail;

    public Long auto_download_size;
    public Boolean link_preview_inbound;
    public Boolean link_preview_outbound;
    public Boolean send_read_receipt; // sync
    public Boolean auto_open_limited_visibility;
    public Boolean retain_wiped_outbound;
    public Long default_retention_count;
    public Long default_retention_duration;
    public Boolean default_read_once;
    public Long default_visibility_duration;
    public Long default_existence_duration;

    public String map_integration;
    public String custom_osm_server;
    public String osm_language;
    public Long location_share_duration;
    public Long location_share_interval;
    public Boolean disable_address_lookup;
    public String custom_address_server;


    public Integer camera_resize_resolution;
    public Boolean remove_jpeg_metadata;

    public String automatic_backup_configuration;

    public Boolean share_app_version;
    public Boolean notify_certificate_change;
    public String block_unknown_certificate;
    public Boolean no_notify_certificate_for_link_previews;
    public Boolean sending_foreground_service;
    public String connectivity_indicator;
    public String qr_correction_level;
    public Boolean low_bandwidth_calls;
    public Boolean debug_log_level;

    public Boolean wc_send_on_enter;
    public Boolean wc_browser_notification;
    public Boolean wc_browser_notification_sound;
    public Boolean wc_keep_after_close;
    public Boolean wc_error_notification;
    public Boolean wc_inactivity_indicator;
    public Boolean wc_require_unlock;

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

        settingsPojo.hide_notification_contents = SettingsActivity.isNotificationContentHidden();
        settingsPojo.allow_notification_suggestions = SettingsActivity.isNotificationSuggestionAllowed();
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
        settingsPojo.show_trust_level = SettingsActivity.showTrustLevels();
        settingsPojo.contact_sort_last_name = SettingsActivity.getSortContactsByLastName();
        settingsPojo.contact_uppercase_last_name = SettingsActivity.getUppercaseLastName();
        settingsPojo.contact_display_name_format = SettingsActivity.getContactDisplayNameFormat();

        settingsPojo.lock_biometry = SettingsActivity.useBiometryToUnlock();
        settingsPojo.lock_delay_s = SettingsActivity.getLockGraceTime();
        settingsPojo.lock_notification = SettingsActivity.keepLockServiceOpen();
        settingsPojo.lock_wipe_on_fail = SettingsActivity.wipeMessagesOnUnlockFails();

        settingsPojo.auto_download_size = SettingsActivity.getAutoDownloadSize();
        settingsPojo.link_preview_inbound = SettingsActivity.isLinkPreviewInbound();
        settingsPojo.link_preview_outbound = SettingsActivity.isLinkPreviewOutbound();
        settingsPojo.send_read_receipt = SettingsActivity.getDefaultSendReadReceipt();
        settingsPojo.auto_open_limited_visibility = SettingsActivity.getDefaultAutoOpenLimitedVisibilityInboundMessages();
        settingsPojo.retain_wiped_outbound = SettingsActivity.getDefaultRetainWipedOutboundMessages();
        settingsPojo.default_retention_count = SettingsActivity.getDefaultDiscussionRetentionCount();
        settingsPojo.default_retention_duration = SettingsActivity.getDefaultDiscussionRetentionDuration();
        settingsPojo.default_read_once = SettingsActivity.getDefaultDiscussionReadOnce();
        settingsPojo.default_visibility_duration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
        settingsPojo.default_existence_duration = SettingsActivity.getDefaultDiscussionExistenceDuration();

        settingsPojo.map_integration = SettingsActivity.getLocationIntegration().getString();
        settingsPojo.custom_osm_server = (SettingsActivity.getLocationIntegration() == SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM) ? SettingsActivity.getLocationCustomOsmServerUrl() : null;
        settingsPojo.osm_language = SettingsActivity.getLocationOpenStreetMapRawLanguage();
        settingsPojo.location_share_duration = SettingsActivity.getLocationDefaultSharingDurationValue();
        settingsPojo.location_share_interval = SettingsActivity.getLocationDefaultSharingIntervalValue();
        settingsPojo.disable_address_lookup = SettingsActivity.getLocationDisableAddressLookup();
        settingsPojo.custom_address_server = SettingsActivity.getLocationCustomAddressServer();

        settingsPojo.camera_resize_resolution = SettingsActivity.getCameraResolution();
        settingsPojo.remove_jpeg_metadata = SettingsActivity.getMetadataRemovalPreference();

        BackupCloudProviderService.CloudProviderConfiguration configuration = SettingsActivity.getAutomaticBackupConfiguration();
        if (configuration != null) {
            try {
                settingsPojo.automatic_backup_configuration = AppSingleton.getJsonObjectMapper().writeValueAsString(configuration);
            } catch (Exception ignored) { }
        }

        settingsPojo.share_app_version = SettingsActivity.shareAppVersion();
        settingsPojo.notify_certificate_change = SettingsActivity.notifyCertificateChange();
        SettingsActivity.BlockUntrustedCertificate blockUntrustedCertificate = SettingsActivity.getBlockUntrustedCertificate();
        switch (blockUntrustedCertificate) {
            case ALWAYS:
                settingsPojo.block_unknown_certificate = "always";
                break;
            case ISSUER_CHANGED:
                settingsPojo.block_unknown_certificate = "issuer";
                break;
            case NEVER:
                settingsPojo.block_unknown_certificate = "never";
                break;
            default:
                break;
        }
        settingsPojo.no_notify_certificate_for_link_previews = SettingsActivity.getNoNotifyCertificateChangeForPreviews();
        settingsPojo.sending_foreground_service = SettingsActivity.useSendingForegroundService();
        SettingsActivity.PingConnectivityIndicator pingConnectivityIndicator = SettingsActivity.getPingConnectivityIndicator();
        switch (pingConnectivityIndicator) {
            case DOT:
                settingsPojo.connectivity_indicator = "dot";
                break;
            case LINE:
                settingsPojo.connectivity_indicator = "line";
                break;
            case FULL:
                settingsPojo.connectivity_indicator = "full";
                break;
            case NONE:
            default:
                break;
        }
        settingsPojo.qr_correction_level = SettingsActivity.getQrCorrectionLevel();
        settingsPojo.low_bandwidth_calls = SettingsActivity.useLowBandwidthInCalls();
        settingsPojo.debug_log_level = SettingsActivity.useDebugLogLevel();

        settingsPojo.wc_send_on_enter = SettingsActivity.getWebclientSendOnEnter();
        settingsPojo.wc_browser_notification = SettingsActivity.showWebclientNotificationsInBrowser();
        settingsPojo.wc_browser_notification_sound = SettingsActivity.playWebclientNotificationsSoundInBrowser();
        settingsPojo.wc_keep_after_close = SettingsActivity.keepWebclientAliveAfterClose();
        settingsPojo.wc_error_notification = SettingsActivity.showWebclientErrorNotifications();
        settingsPojo.wc_inactivity_indicator = SettingsActivity.webclientNotifyAfterInactivity();
        settingsPojo.wc_require_unlock = SettingsActivity.isWebclientUnlockRequired();

        settingsPojo.preferred_reactions = SettingsActivity.getPreferredReactions();
        return settingsPojo;
    }

    @JsonIgnore
    public void restore() {
        if (beta != null) { SettingsActivity.setBetaFeaturesEnabled(beta); }

        if (message_vibration_pattern != null) { SettingsActivity.setMessageVibrationPatternRaw(message_vibration_pattern); }
        if (message_led_color != null) { SettingsActivity.setMessageLedColor(message_led_color); }
        if (call_vibration_pattern != null) { SettingsActivity.setCallVibrationPatternRaw(call_vibration_pattern); }
        if (call_use_flash != null) { SettingsActivity.setUseFlashOnIncomingCall(call_use_flash); }
        if (permanent_foreground_service != null) { SettingsActivity.setUsePermanentForegroundService(permanent_foreground_service); }

        if (dark_mode != null) { SettingsActivity.setForcedDarkMode(dark_mode); }
        if (system_emojis != null) { SettingsActivity.setUseSystemEmojis(system_emojis); }
        if (internal_viewer != null) { SettingsActivity.setUseInternalImageViewer(internal_viewer); }

        if (hide_notification_contents != null) { SettingsActivity.setNotificationContentHidden(hide_notification_contents); }
        if (allow_notification_suggestions != null) { SettingsActivity.setNotificationSuggestionAllowed(allow_notification_suggestions); }
        if (expose_recent_discussions != null) { SettingsActivity.setExposeRecentDiscussions(expose_recent_discussions); }
        if (incognito_keyboard != null) { SettingsActivity.setUseKeyboardIncognitoMode(incognito_keyboard); }
        if (prevent_screen_capture != null) { SettingsActivity.setPreventScreenCapture(prevent_screen_capture); }
        if (hidden_profile_policy != null) {
            SettingsActivity.setHiddenProfileClosePolicy(hidden_profile_policy, hidden_profile_background_grace != null ? hidden_profile_background_grace : 0);
        }
        if (disable_push_notifications != null) { SettingsActivity.setDisablePushNotifications(disable_push_notifications); }
        if (permanent_websocket != null) { SettingsActivity.setUsePermanentWebSocket(permanent_websocket); }

        SettingsActivity.AutoJoinGroupsCategory oldAutoJoinGroupsCategory = null;
        if (auto_join_groups != null) {
            oldAutoJoinGroupsCategory = SettingsActivity.getAutoJoinGroups();
            SettingsActivity.setAutoJoinGroups(SettingsActivity.getAutoJoinGroupsFromString(auto_join_groups));

            try {
                AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingAutoJoinGroups(auto_join_groups));
            } catch (Exception e) {
                Logger.w("Failed to propagate auto join group setting change to other devices");
                e.printStackTrace();
            }
        }
        if (show_trust_level != null) { SettingsActivity.setShowTrustLevels(show_trust_level); }
        if (contact_sort_last_name != null) { SettingsActivity.setSortContactsByLastName(contact_sort_last_name); }
        if (contact_uppercase_last_name != null) { SettingsActivity.setUppercaseLastName(contact_uppercase_last_name); }
        if (contact_display_name_format != null) { SettingsActivity.setContactDisplayNameFormat(contact_display_name_format); }

        if (lock_biometry != null) {SettingsActivity.setUseBiometryToUnlock(lock_biometry); }
        if (lock_delay_s != null) {SettingsActivity.setLockGraceTime(lock_delay_s); }
        if (lock_notification != null) {SettingsActivity.setKeepLockServiceOpen(lock_notification); }
        if (lock_wipe_on_fail != null) {SettingsActivity.setWipeMessagesOnUnlockFails(lock_wipe_on_fail); }

        if (auto_download_size != null) { SettingsActivity.setAutoDownloadSize(auto_download_size); }
        if (link_preview_inbound != null) { SettingsActivity.setLinkPreviewInbound(link_preview_inbound); }
        if (link_preview_outbound != null) { SettingsActivity.setLinkPreviewOutbound(link_preview_outbound); }
        if (send_read_receipt != null) {
            SettingsActivity.setDefaultSendReadReceipt(send_read_receipt);

            try {
                AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingDefaultSendReadReceipts(send_read_receipt));
            } catch (Exception e) {
                Logger.w("Failed to propagate default send read receipt setting change to other devices");
                e.printStackTrace();
            }
        }
        if (auto_open_limited_visibility != null) { SettingsActivity.setDefaultAutoOpenLimitedVisibilityInboundMessages(auto_open_limited_visibility); }
        if (retain_wiped_outbound != null) { SettingsActivity.setDefaultRetainWipedOutboundMessages(retain_wiped_outbound); }
        if (default_retention_count != null) { SettingsActivity.setDefaultDiscussionRetentionCount(default_retention_count); }
        if (default_retention_duration != null) { SettingsActivity.setDefaultDiscussionRetentionDuration(default_retention_duration); }
        if (default_read_once != null) { SettingsActivity.setDefaultDiscussionReadOnce(default_read_once); }
        if (default_visibility_duration != null && default_visibility_duration > 0) { SettingsActivity.setDefaultDiscussionVisibilityDuration(default_visibility_duration); }
        if (default_existence_duration != null && default_existence_duration > 0) { SettingsActivity.setDefaultDiscussionExistenceDuration(default_existence_duration); }

        if (map_integration != null) { SettingsActivity.setLocationIntegration(map_integration, custom_osm_server); }
        if (osm_language != null) { SettingsActivity.setLocationOpenStreetMapRawLanguage(osm_language); }
        if (location_share_duration != null) { SettingsActivity.setLocationDefaultSharingDurationValue(location_share_duration); }
        if (location_share_interval != null) { SettingsActivity.setLocationDefaultSharingIntervalValue(location_share_interval); }
        if (disable_address_lookup != null) { SettingsActivity.setLocationDisableAddressLookup(disable_address_lookup); }
        if (custom_address_server != null) { SettingsActivity.setLocationCustomAddressServer(custom_address_server); }

        if (camera_resize_resolution != null) { SettingsActivity.setCameraResolution(camera_resize_resolution); }
        if (remove_jpeg_metadata != null) { SettingsActivity.setMetadataRemovalPreference(remove_jpeg_metadata); }

        if (automatic_backup_configuration != null) {
            try {
                BackupCloudProviderService.CloudProviderConfiguration configuration = AppSingleton.getJsonObjectMapper().readValue(automatic_backup_configuration, BackupCloudProviderService.CloudProviderConfiguration.class);
                SettingsActivity.setAutomaticBackupConfiguration(configuration);
                SettingsActivity.setUseAutomaticBackup(true);
                AppSingleton.getEngine().setAutoBackupEnabled(true, false);
            } catch (Exception ignored) { }
        }

        if (share_app_version != null) { SettingsActivity.setShareAppVersion(share_app_version); }
        if (notify_certificate_change != null) { SettingsActivity.setNotifyCertificateChange(notify_certificate_change); }
        if (block_unknown_certificate != null) { SettingsActivity.setBlockUntrustedCertificate(block_unknown_certificate); }
        if (no_notify_certificate_for_link_previews != null) { SettingsActivity.setNoNotifyCertificateChangeForPreviews(no_notify_certificate_for_link_previews); }
        if (sending_foreground_service != null) { SettingsActivity.setSendingForegroundService(sending_foreground_service); }
        if (connectivity_indicator != null) { SettingsActivity.setPingConnectivityIndicator(connectivity_indicator); }
        if (qr_correction_level != null) { SettingsActivity.setQrCorrectionLevel(qr_correction_level); }
        if (low_bandwidth_calls != null) { SettingsActivity.setUseLowBandwidthInCalls(low_bandwidth_calls); }
        if (debug_log_level != null) { SettingsActivity.setUseDebugLogLevel(debug_log_level); }

        if (wc_send_on_enter != null) { SettingsActivity.setWebclientSendOnEnter(wc_send_on_enter); }
        if (wc_browser_notification != null) { SettingsActivity.setShowWebclientNotificationsInBrowser(wc_browser_notification); }
        if (wc_browser_notification_sound != null) { SettingsActivity.setPlayWebclientNotificationsSoundInBrowser(wc_browser_notification_sound); }
        if (wc_keep_after_close != null) { SettingsActivity.setKeepWebclientAliveAfterClose(wc_keep_after_close); }
        if (wc_error_notification != null) { SettingsActivity.setShowWebclientErrorNotifications(wc_error_notification); }
        if (wc_inactivity_indicator != null) { SettingsActivity.setWebclientNotifyAfterInactivity(wc_inactivity_indicator); }
        if (wc_require_unlock != null) { SettingsActivity.setWebclientUnlockRequired(wc_require_unlock); }


        if (preferred_reactions != null) { SettingsActivity.setPreferredReactions(preferred_reactions); }

        //////////////////
        // various hooks that may be required
        //////////////////

        // run hooks for notification channel and discussion name
        App.runThread(new ContactDisplayNameFormatChangedTask());
        AndroidNotificationManager.updateMessageChannel(false);

        // auto accept relevant group invitations
        if (oldAutoJoinGroupsCategory != null) {
            SettingsActivity.AutoJoinGroupsCategory newCategory = SettingsActivity.getAutoJoinGroups();
            if (newCategory != oldAutoJoinGroupsCategory
                    && newCategory != SettingsActivity.AutoJoinGroupsCategory.NOBODY
                    && (newCategory != SettingsActivity.AutoJoinGroupsCategory.CONTACTS || oldAutoJoinGroupsCategory != SettingsActivity.AutoJoinGroupsCategory.EVERYONE)) {
                App.runThread(() -> {
                    List<Invitation> groupInvitations = AppDatabase.getInstance().invitationDao().getAllGroupInvites();
                    final List<Invitation> invitationsToAccept;
                    if (newCategory == SettingsActivity.AutoJoinGroupsCategory.CONTACTS) {
                        // filter invitations to keep only those from a oneToOne contact
                        invitationsToAccept = new ArrayList<>();
                        for (Invitation groupInvitation : groupInvitations) {
                            byte[] bytesGroupOwnerIdentity = groupInvitation.associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity();
                            if (bytesGroupOwnerIdentity != null) {
                                Contact contact = AppDatabase.getInstance().contactDao().get(groupInvitation.bytesOwnedIdentity, bytesGroupOwnerIdentity);
                                if (contact != null && contact.oneToOne) {
                                    invitationsToAccept.add(groupInvitation);
                                }
                            }
                        }
                    } else {
                        invitationsToAccept = groupInvitations;
                    }

                    for (Invitation groupInvitation: invitationsToAccept) {
                        try {
                            ObvDialog obvDialog = groupInvitation.associatedDialog;
                            obvDialog.setResponseToAcceptGroupInvite(true);
                            AppSingleton.getEngine().respondToDialog(obvDialog);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }

        // refresh display if needed
        if (show_trust_level != null) {
            Intent recreateRequiredIntent = new Intent(SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION);
            recreateRequiredIntent.setPackage(App.getContext().getPackageName());
            // we delay sending this intent so we are sure the setting is updated when activities are recreated
            new Handler(Looper.getMainLooper()).postDelayed(() -> LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(recreateRequiredIntent), 200);
        }

        // restart foreground service
        if (permanent_foreground_service != null || permanent_websocket != null) {
            App.getContext().startService(new Intent(App.getContext(), UnifiedForegroundService.class));
        }

        // update share app version
        if (share_app_version != null) {
            if (SettingsActivity.shareAppVersion()) {
                AppSingleton.getEngine().connectWebsocket(false, "android", Integer.toString(android.os.Build.VERSION.SDK_INT), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
            } else {
                AppSingleton.getEngine().connectWebsocket(false, null, null, 0, null);
            }
        }

        // recent discussions
        if (expose_recent_discussions != null) {
            ShortcutActivity.startPublishingShareTargets(App.getContext());
        }

        // Google push
        if (disable_push_notifications != null) {
            App.runThread(App::refreshRegisterToPushNotification);
        }
    }
}
