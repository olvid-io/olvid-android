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

import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.concurrent.Callable;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;

public class RestoreAppDataFromBackupTask implements Callable<Boolean> {
    private final String appDataBackup;

    public RestoreAppDataFromBackupTask(String appDataBackup) {
        this.appDataBackup = appDataBackup;
    }

    @Override
    public Boolean call() {
        try {
            AppDatabase db = AppDatabase.getInstance();

            AppBackupPojo_0 appBackupPojo = AppSingleton.getJsonObjectMapper().readValue(appDataBackup, AppBackupPojo_0.class);

            if (appBackupPojo.owned_identities != null) {
                for (OwnedIdentityPojo_0 ownedIdentityPojo : appBackupPojo.owned_identities) {
                    OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(ownedIdentityPojo.owned_identity);
                    if (ownedIdentity != null) {
                        if (ownedIdentityPojo.custom_name != null) {
                            ownedIdentity.customDisplayName = ownedIdentityPojo.custom_name;
                            db.ownedIdentityDao().updateCustomDisplayName(ownedIdentity.bytesOwnedIdentity, ownedIdentity.customDisplayName);
                        }
                        if (ownedIdentityPojo.unlock_password != null) {
                            ownedIdentity.unlockPassword = ownedIdentityPojo.unlock_password;
                            ownedIdentity.unlockSalt = ownedIdentityPojo.unlock_salt;
                            db.ownedIdentityDao().updateUnlockPasswordAndSalt(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unlockPassword, ownedIdentity.unlockSalt);
                        }
                        if (ownedIdentityPojo.mute_notifications) {
                            ownedIdentity.prefMuteNotifications = true;
                            ownedIdentity.prefMuteNotificationsTimestamp = ownedIdentityPojo.mute_notification_timestamp;
                            db.ownedIdentityDao().updateMuteNotifications(ownedIdentity.bytesOwnedIdentity, ownedIdentity.prefMuteNotifications, ownedIdentity.prefMuteNotificationsTimestamp);
                        }
                        if (ownedIdentityPojo.show_neutral_notification_when_hidden) {
                            ownedIdentity.prefShowNeutralNotificationWhenHidden = true;
                            db.ownedIdentityDao().updateShowNeutralNotificationWhenHidden(ownedIdentity.bytesOwnedIdentity, ownedIdentity.prefShowNeutralNotificationWhenHidden);
                        }

                        if (ownedIdentityPojo.contacts != null) {
                            for (ContactPojo_0 contactPojo : ownedIdentityPojo.contacts) {
                                Contact contact = db.contactDao().get(ownedIdentityPojo.owned_identity, contactPojo.contact_identity);
                                if (contact != null) {
                                    if (contactPojo.custom_name != null) {
                                        contact.setCustomDisplayName(contactPojo.custom_name);
                                        db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);
                                    }

                                    if (contactPojo.custom_hue != null) {
                                        contact.customNameHue = contactPojo.custom_hue;
                                        db.contactDao().updateCustomNameHue(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.customNameHue);
                                    }

                                    if (contactPojo.personal_note != null) {
                                        contact.personalNote = contactPojo.personal_note;
                                        db.contactDao().updatePersonalNote(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.personalNote);
                                    }

                                    if (contactPojo.discussion_customization != null) {
                                        Discussion discussion = db.discussionDao().getByContact(ownedIdentityPojo.owned_identity, contactPojo.contact_identity);
                                        if (discussion != null) {
                                            if (contactPojo.custom_name != null) {
                                                discussion.title = contact.getCustomDisplayName();
                                                db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                                            }

                                            DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);

                                            if (discussionCustomization != null) {
                                                // there is already a discussion customization, proceed carefully
                                                db.runInTransaction(() -> {
                                                    discussionCustomization.serializedColorJson = contactPojo.discussion_customization.serialized_color_json;
                                                    discussionCustomization.prefSendReadReceipt = contactPojo.discussion_customization.send_read_receipt;
                                                    discussionCustomization.prefMuteNotifications = contactPojo.discussion_customization.mute_notifications;
                                                    if (discussionCustomization.prefMuteNotifications) {
                                                        discussionCustomization.prefMuteNotificationsTimestamp = contactPojo.discussion_customization.mute_notification_timestamp;
                                                    }
                                                    discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = contactPojo.discussion_customization.auto_open_limited_visibility;
                                                    discussionCustomization.prefSendReadReceipt = contactPojo.discussion_customization.retain_wiped_outbound;
                                                    discussionCustomization.prefDiscussionRetentionCount = contactPojo.discussion_customization.retention_count;
                                                    discussionCustomization.prefDiscussionRetentionDuration = contactPojo.discussion_customization.retention_duration;
                                                    if (contactPojo.discussion_customization.shared_settings_version != null &&
                                                            (discussionCustomization.sharedSettingsVersion == null || contactPojo.discussion_customization.shared_settings_version > discussionCustomization.sharedSettingsVersion)) {
                                                        discussionCustomization.sharedSettingsVersion = contactPojo.discussion_customization.shared_settings_version;
                                                        discussionCustomization.settingExistenceDuration = contactPojo.discussion_customization.settings_existence_duration;
                                                        discussionCustomization.settingVisibilityDuration = contactPojo.discussion_customization.settings_visibility_duration;
                                                        discussionCustomization.settingReadOnce = contactPojo.discussion_customization.settings_read_once;
                                                    }

                                                    db.discussionCustomizationDao().update(discussionCustomization);
                                                });
                                            } else {
                                                // create the customization
                                                db.runInTransaction(() -> {
                                                    DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                    customization.serializedColorJson = contactPojo.discussion_customization.serialized_color_json;
                                                    customization.prefSendReadReceipt = contactPojo.discussion_customization.send_read_receipt;
                                                    customization.prefMuteNotifications = contactPojo.discussion_customization.mute_notifications;
                                                    if (customization.prefMuteNotifications) {
                                                        customization.prefMuteNotificationsTimestamp = contactPojo.discussion_customization.mute_notification_timestamp;
                                                    }
                                                    customization.prefAutoOpenLimitedVisibilityInboundMessages = contactPojo.discussion_customization.auto_open_limited_visibility;
                                                    customization.prefSendReadReceipt = contactPojo.discussion_customization.retain_wiped_outbound;
                                                    customization.prefDiscussionRetentionCount = contactPojo.discussion_customization.retention_count;
                                                    customization.prefDiscussionRetentionDuration = contactPojo.discussion_customization.retention_duration;
                                                    if (contactPojo.discussion_customization.shared_settings_version != null) {
                                                        customization.sharedSettingsVersion = contactPojo.discussion_customization.shared_settings_version;
                                                        customization.settingExistenceDuration = contactPojo.discussion_customization.settings_existence_duration;
                                                        customization.settingVisibilityDuration = contactPojo.discussion_customization.settings_visibility_duration;
                                                        customization.settingReadOnce = contactPojo.discussion_customization.settings_read_once;
                                                    }

                                                    db.discussionCustomizationDao().insert(customization);
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (ownedIdentityPojo.groups != null) {
                            for (GroupPojo_0 groupPojo : ownedIdentityPojo.groups) {
                                if (groupPojo.group_owner_identity == null || groupPojo.group_uid == null) {
                                    continue;
                                }
                                byte[] bytesGroupOwnerAndUid = new byte[groupPojo.group_owner_identity.length + groupPojo.group_uid.length];
                                System.arraycopy(groupPojo.group_owner_identity, 0, bytesGroupOwnerAndUid, 0, groupPojo.group_owner_identity.length);
                                System.arraycopy(groupPojo.group_uid, 0, bytesGroupOwnerAndUid, groupPojo.group_owner_identity.length, groupPojo.group_uid.length);
                                Group group = db.groupDao().get(ownedIdentityPojo.owned_identity, bytesGroupOwnerAndUid);

                                if (group != null) {
                                    if (Arrays.equals(ownedIdentityPojo.owned_identity, groupPojo.group_owner_identity)) {
                                        // group is owned
                                        if (groupPojo.discussion_customization != null) {
                                            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(ownedIdentityPojo.owned_identity, bytesGroupOwnerAndUid);
                                            if (discussion != null) {
                                                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);

                                                if (discussionCustomization != null) {
                                                    // there is already a discussion customization, proceed carefully
                                                    db.runInTransaction(() -> {
                                                        discussionCustomization.serializedColorJson = groupPojo.discussion_customization.serialized_color_json;
                                                        discussionCustomization.prefSendReadReceipt = groupPojo.discussion_customization.send_read_receipt;
                                                        discussionCustomization.prefMuteNotifications = groupPojo.discussion_customization.mute_notifications;
                                                        if (discussionCustomization.prefMuteNotifications) {
                                                            discussionCustomization.prefMuteNotificationsTimestamp = groupPojo.discussion_customization.mute_notification_timestamp;
                                                        }
                                                        discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = groupPojo.discussion_customization.auto_open_limited_visibility;
                                                        discussionCustomization.prefSendReadReceipt = groupPojo.discussion_customization.retain_wiped_outbound;
                                                        discussionCustomization.prefDiscussionRetentionCount = groupPojo.discussion_customization.retention_count;
                                                        discussionCustomization.prefDiscussionRetentionDuration = groupPojo.discussion_customization.retention_duration;
                                                        if (groupPojo.discussion_customization.shared_settings_version != null &&
                                                                (discussionCustomization.sharedSettingsVersion == null || groupPojo.discussion_customization.shared_settings_version > discussionCustomization.sharedSettingsVersion)) {
                                                            discussionCustomization.sharedSettingsVersion = groupPojo.discussion_customization.shared_settings_version;
                                                            discussionCustomization.settingExistenceDuration = groupPojo.discussion_customization.settings_existence_duration;
                                                            discussionCustomization.settingVisibilityDuration = groupPojo.discussion_customization.settings_visibility_duration;
                                                            discussionCustomization.settingReadOnce = groupPojo.discussion_customization.settings_read_once;

                                                            Message.JsonExpiration expiration = new Message.JsonExpiration();
                                                            expiration.setReadOnce(discussionCustomization.settingReadOnce);
                                                            expiration.setVisibilityDuration(discussionCustomization.settingVisibilityDuration);
                                                            expiration.setExistenceDuration(discussionCustomization.settingExistenceDuration);
                                                            if (!expiration.likeNull()) {
                                                                DiscussionCustomization.JsonSharedSettings settings = new DiscussionCustomization.JsonSharedSettings();
                                                                settings.setVersion(discussionCustomization.sharedSettingsVersion);
                                                                settings.setGroupUid(groupPojo.group_uid);
                                                                settings.setGroupOwner(groupPojo.group_owner_identity);
                                                                settings.setJsonExpiration(expiration);

                                                                Message message = Message.createDiscussionSettingsUpdateMessage(discussion.id,
                                                                        settings,
                                                                        ownedIdentity.bytesOwnedIdentity,
                                                                        true,
                                                                        System.currentTimeMillis()
                                                                );
                                                                if (message != null) {
                                                                    message.id = db.messageDao().insert(message);
                                                                    message.post(false, false);
                                                                }
                                                            }
                                                        }

                                                        db.discussionCustomizationDao().update(discussionCustomization);
                                                    });
                                                } else {
                                                    // create the customization
                                                    db.runInTransaction(() -> {
                                                        DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                        customization.serializedColorJson = groupPojo.discussion_customization.serialized_color_json;
                                                        customization.prefSendReadReceipt = groupPojo.discussion_customization.send_read_receipt;
                                                        customization.prefMuteNotifications = groupPojo.discussion_customization.mute_notifications;
                                                        if (customization.prefMuteNotifications) {
                                                            customization.prefMuteNotificationsTimestamp = groupPojo.discussion_customization.mute_notification_timestamp;
                                                        }
                                                        customization.prefAutoOpenLimitedVisibilityInboundMessages = groupPojo.discussion_customization.auto_open_limited_visibility;
                                                        customization.prefSendReadReceipt = groupPojo.discussion_customization.retain_wiped_outbound;
                                                        customization.prefDiscussionRetentionCount = groupPojo.discussion_customization.retention_count;
                                                        customization.prefDiscussionRetentionDuration = groupPojo.discussion_customization.retention_duration;
                                                        if (groupPojo.discussion_customization.shared_settings_version != null) {
                                                            customization.sharedSettingsVersion = groupPojo.discussion_customization.shared_settings_version;
                                                            customization.settingExistenceDuration = groupPojo.discussion_customization.settings_existence_duration;
                                                            customization.settingVisibilityDuration = groupPojo.discussion_customization.settings_visibility_duration;
                                                            customization.settingReadOnce = groupPojo.discussion_customization.settings_read_once;

                                                            Message.JsonExpiration expiration = new Message.JsonExpiration();
                                                            expiration.setReadOnce(customization.settingReadOnce);
                                                            expiration.setVisibilityDuration(customization.settingVisibilityDuration);
                                                            expiration.setExistenceDuration(customization.settingExistenceDuration);
                                                            if (!expiration.likeNull()) {
                                                                DiscussionCustomization.JsonSharedSettings settings = new DiscussionCustomization.JsonSharedSettings();
                                                                settings.setVersion(customization.sharedSettingsVersion);
                                                                settings.setGroupUid(groupPojo.group_uid);
                                                                settings.setGroupOwner(groupPojo.group_owner_identity);
                                                                settings.setJsonExpiration(expiration);

                                                                Message message = Message.createDiscussionSettingsUpdateMessage(discussion.id,
                                                                        settings,
                                                                        ownedIdentity.bytesOwnedIdentity,
                                                                        true,
                                                                        System.currentTimeMillis()
                                                                );
                                                                if (message != null) {
                                                                    message.id = db.messageDao().insert(message);
                                                                    message.post(false, false);
                                                                }
                                                            }
                                                        }

                                                        db.discussionCustomizationDao().insert(customization);
                                                    });
                                                }
                                            }
                                        }
                                    } else {
                                        // group is joined
                                        if (groupPojo.custom_name != null) {
                                            group.customName = groupPojo.custom_name;
                                            db.groupDao().updateCustomName(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.customName);
                                        }

                                        if (groupPojo.personal_note != null) {
                                            group.personalNote = groupPojo.personal_note;
                                            db.groupDao().updatePersonalNote(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.personalNote);
                                        }

                                        if (groupPojo.discussion_customization != null) {
                                            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(ownedIdentityPojo.owned_identity, bytesGroupOwnerAndUid);
                                            if (discussion != null) {
                                                if (groupPojo.custom_name != null) {
                                                    discussion.title = group.getCustomName();
                                                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                                                }

                                                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);

                                                if (discussionCustomization != null) {
                                                    // there is already a discussion customization, proceed carefully
                                                    db.runInTransaction(() -> {
                                                        discussionCustomization.serializedColorJson = groupPojo.discussion_customization.serialized_color_json;
                                                        discussionCustomization.prefSendReadReceipt = groupPojo.discussion_customization.send_read_receipt;
                                                        discussionCustomization.prefMuteNotifications = groupPojo.discussion_customization.mute_notifications;
                                                        if (discussionCustomization.prefMuteNotifications) {
                                                            discussionCustomization.prefMuteNotificationsTimestamp = groupPojo.discussion_customization.mute_notification_timestamp;
                                                        }
                                                        discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = groupPojo.discussion_customization.auto_open_limited_visibility;
                                                        discussionCustomization.prefSendReadReceipt = groupPojo.discussion_customization.retain_wiped_outbound;
                                                        discussionCustomization.prefDiscussionRetentionCount = groupPojo.discussion_customization.retention_count;
                                                        discussionCustomization.prefDiscussionRetentionDuration = groupPojo.discussion_customization.retention_duration;

                                                        db.discussionCustomizationDao().update(discussionCustomization);
                                                    });
                                                } else {
                                                    // create the customization
                                                    db.runInTransaction(() -> {
                                                        DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                        customization.serializedColorJson = groupPojo.discussion_customization.serialized_color_json;
                                                        customization.prefSendReadReceipt = groupPojo.discussion_customization.send_read_receipt;
                                                        customization.prefMuteNotifications = groupPojo.discussion_customization.mute_notifications;
                                                        if (customization.prefMuteNotifications) {
                                                            customization.prefMuteNotificationsTimestamp = groupPojo.discussion_customization.mute_notification_timestamp;
                                                        }
                                                        customization.prefAutoOpenLimitedVisibilityInboundMessages = groupPojo.discussion_customization.auto_open_limited_visibility;
                                                        customization.prefSendReadReceipt = groupPojo.discussion_customization.retain_wiped_outbound;
                                                        customization.prefDiscussionRetentionCount = groupPojo.discussion_customization.retention_count;
                                                        customization.prefDiscussionRetentionDuration = groupPojo.discussion_customization.retention_duration;
                                                        
                                                        db.discussionCustomizationDao().insert(customization);
                                                    });
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AppSingleton.reloadCachedDisplayNamesAndHues();
            }

            if (appBackupPojo.settings != null) {
                appBackupPojo.settings.restore();
            }
            new Handler(Looper.getMainLooper()).post(SettingsActivity::setDefaultNightMode);
            SettingsActivity.setLastBackupReminderTimestamp(System.currentTimeMillis());

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
