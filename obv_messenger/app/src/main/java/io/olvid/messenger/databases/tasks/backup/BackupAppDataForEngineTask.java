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

package io.olvid.messenger.databases.tasks.backup;

import java.util.ArrayList;
import java.util.Arrays;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class BackupAppDataForEngineTask implements Runnable {
    private final byte[] bytesBackupKeyUid;
    private final int version;

    public BackupAppDataForEngineTask(byte[] bytesBackupKeyUid, int version) {
        this.bytesBackupKeyUid = bytesBackupKeyUid;
        this.version = version;
    }

    @Override
    public void run() {
        try {
            AppDatabase db = AppDatabase.getInstance();

            AppBackupPojo_0 appBackupPojo = new AppBackupPojo_0();
            appBackupPojo.owned_identities = new ArrayList<>();

            for (OwnedIdentity ownedIdentity : db.ownedIdentityDao().getAll()) {
                OwnedIdentityPojo_0 ownedIdentityPojo = new OwnedIdentityPojo_0();
                ownedIdentityPojo.owned_identity = ownedIdentity.bytesOwnedIdentity;
                ownedIdentityPojo.custom_name = ownedIdentity.customDisplayName;
                ownedIdentityPojo.unlock_password = ownedIdentity.unlockPassword;
                ownedIdentityPojo.unlock_salt = ownedIdentity.unlockSalt;
                if (ownedIdentity.prefMuteNotifications
                        && (ownedIdentity.prefMuteNotificationsTimestamp == null || ownedIdentity.prefMuteNotificationsTimestamp > System.currentTimeMillis())) {
                    ownedIdentityPojo.mute_notifications = true;
                    ownedIdentityPojo.mute_notification_timestamp = ownedIdentity.prefMuteNotificationsTimestamp;
                }
                ownedIdentityPojo.show_neutral_notification_when_hidden = ownedIdentity.prefShowNeutralNotificationWhenHidden;
                ownedIdentityPojo.contacts = new ArrayList<>();
                ownedIdentityPojo.groups = new ArrayList<>();

                for (Contact contact : db.contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity)) {
                    ContactPojo_0 contactPojo = new ContactPojo_0();
                    contactPojo.contact_identity = contact.bytesContactIdentity;
                    contactPojo.custom_name = contact.customDisplayName;
                    contactPojo.custom_hue = contact.customNameHue;
                    contactPojo.personal_note = contact.personalNote;

                    Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                        if (discussionCustomization != null) {
                            DiscussionCustomizationPojo_0 discussionCustomizationPojo = new DiscussionCustomizationPojo_0();
                            discussionCustomizationPojo.serialized_color_json = discussionCustomization.serializedColorJson;
                            discussionCustomizationPojo.send_read_receipt = discussionCustomization.prefSendReadReceipt;
                            if (discussionCustomization.shouldMuteNotifications()) {
                                discussionCustomizationPojo.mute_notifications = true;
                                discussionCustomizationPojo.mute_notification_timestamp = discussionCustomization.prefMuteNotificationsTimestamp;
                            }
                            discussionCustomizationPojo.auto_open_limited_visibility = discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages;
                            discussionCustomizationPojo.retain_wiped_outbound = discussionCustomization.prefRetainWipedOutboundMessages;
                            discussionCustomizationPojo.retention_count = discussionCustomization.prefDiscussionRetentionCount;
                            discussionCustomizationPojo.retention_duration = discussionCustomization.prefDiscussionRetentionDuration;
                            Message.JsonExpiration expiration = discussionCustomization.getExpirationJson();
                            if (expiration != null) {
                                discussionCustomizationPojo.settings_existence_duration = expiration.getExistenceDuration();
                                discussionCustomizationPojo.settings_visibility_duration = expiration.getVisibilityDuration();
                                discussionCustomizationPojo.settings_read_once = expiration.getReadOnce();
                            }

                            if (!discussionCustomizationPojo.isEmpty()) {
                                contactPojo.discussion_customization = discussionCustomizationPojo;
                            }
                        }
                    }

                    if (!contactPojo.isEmpty()) {
                        ownedIdentityPojo.contacts.add(contactPojo);
                    }
                }

                for (Group ownedGroup: db.groupDao().getAllOwned(ownedIdentity.bytesOwnedIdentity)) {
                    GroupPojo_0 groupPojo = new GroupPojo_0();
                    groupPojo.group_uid = Arrays.copyOfRange(ownedGroup.bytesGroupOwnerAndUid, ownedGroup.bytesGroupOwnerAndUid.length - 32, ownedGroup.bytesGroupOwnerAndUid.length);
                    groupPojo.group_owner_identity = Arrays.copyOfRange(ownedGroup.bytesGroupOwnerAndUid, 0, ownedGroup.bytesGroupOwnerAndUid.length - 32);

                    Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(ownedGroup.bytesOwnedIdentity, ownedGroup.bytesGroupOwnerAndUid);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                        if (discussionCustomization != null) {
                            DiscussionCustomizationPojo_0 discussionCustomizationPojo = new DiscussionCustomizationPojo_0();
                            discussionCustomizationPojo.serialized_color_json = discussionCustomization.serializedColorJson;
                            discussionCustomizationPojo.send_read_receipt = discussionCustomization.prefSendReadReceipt;
                            if (discussionCustomization.shouldMuteNotifications()) {
                                discussionCustomizationPojo.mute_notifications = true;
                                discussionCustomizationPojo.mute_notification_timestamp = discussionCustomization.prefMuteNotificationsTimestamp;
                            }
                            discussionCustomizationPojo.auto_open_limited_visibility = discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages;
                            discussionCustomizationPojo.retain_wiped_outbound = discussionCustomization.prefRetainWipedOutboundMessages;
                            discussionCustomizationPojo.retention_count = discussionCustomization.prefDiscussionRetentionCount;
                            discussionCustomizationPojo.retention_duration = discussionCustomization.prefDiscussionRetentionDuration;

                            discussionCustomizationPojo.shared_settings_version = discussionCustomization.sharedSettingsVersion;
                            Message.JsonExpiration expiration = discussionCustomization.getExpirationJson();
                            if (expiration != null) {
                                discussionCustomizationPojo.settings_existence_duration = expiration.getExistenceDuration();
                                discussionCustomizationPojo.settings_visibility_duration = expiration.getVisibilityDuration();
                                discussionCustomizationPojo.settings_read_once = expiration.getReadOnce();
                            }

                            if (!discussionCustomizationPojo.isEmpty()) {
                                groupPojo.discussion_customization = discussionCustomizationPojo;
                            }
                        }
                    }

                    if (!groupPojo.isEmpty()) {
                        ownedIdentityPojo.groups.add(groupPojo);
                    }
                }

                for (Group joinedGroup : db.groupDao().getAllJoined(ownedIdentity.bytesOwnedIdentity)) {
                    GroupPojo_0 groupPojo = new GroupPojo_0();
                    groupPojo.group_uid = Arrays.copyOfRange(joinedGroup.bytesGroupOwnerAndUid, joinedGroup.bytesGroupOwnerAndUid.length - 32, joinedGroup.bytesGroupOwnerAndUid.length);
                    groupPojo.group_owner_identity = Arrays.copyOfRange(joinedGroup.bytesGroupOwnerAndUid, 0, joinedGroup.bytesGroupOwnerAndUid.length - 32);
                    groupPojo.custom_name = joinedGroup.customName;
                    groupPojo.personal_note = joinedGroup.personalNote;

                    Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(joinedGroup.bytesOwnedIdentity, joinedGroup.bytesGroupOwnerAndUid);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                        if (discussionCustomization != null) {
                            DiscussionCustomizationPojo_0 discussionCustomizationPojo = new DiscussionCustomizationPojo_0();
                            discussionCustomizationPojo.serialized_color_json = discussionCustomization.serializedColorJson;
                            discussionCustomizationPojo.send_read_receipt = discussionCustomization.prefSendReadReceipt;
                            if (discussionCustomization.shouldMuteNotifications()) {
                                discussionCustomizationPojo.mute_notifications = true;
                                discussionCustomizationPojo.mute_notification_timestamp = discussionCustomization.prefMuteNotificationsTimestamp;
                            }
                            discussionCustomizationPojo.auto_open_limited_visibility = discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages;
                            discussionCustomizationPojo.retain_wiped_outbound = discussionCustomization.prefRetainWipedOutboundMessages;
                            discussionCustomizationPojo.retention_count = discussionCustomization.prefDiscussionRetentionCount;
                            discussionCustomizationPojo.retention_duration = discussionCustomization.prefDiscussionRetentionDuration;

                            if (!discussionCustomizationPojo.isEmpty()) {
                                groupPojo.discussion_customization = discussionCustomizationPojo;
                            }
                        }
                    }

                    if (!groupPojo.isEmpty()) {
                        ownedIdentityPojo.groups.add(groupPojo);
                    }
                }

                if (!ownedIdentityPojo.isEmpty()) {
                    appBackupPojo.owned_identities.add(ownedIdentityPojo);
                }
            }

            appBackupPojo.settings = SettingsPojo_0.build();

            AppSingleton.getEngine().appBackupSuccess(bytesBackupKeyUid, version, AppSingleton.getJsonObjectMapper().writeValueAsString(appBackupPojo));
        } catch (Exception e) {
            e.printStackTrace();
            AppSingleton.getEngine().appBackupFailed(bytesBackupKeyUid, version);
        }
    }
}
