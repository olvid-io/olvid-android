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
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.UpdateAllGroupMembersNames;
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
                        if (ownedIdentityPojo.mute_notifications != null && ownedIdentityPojo.mute_notifications) {
                            ownedIdentity.prefMuteNotifications = true;
                            ownedIdentity.prefMuteNotificationsTimestamp = ownedIdentityPojo.mute_notification_timestamp;
                            ownedIdentity.prefMuteNotificationsExceptMentioned = ownedIdentityPojo.mute_notifications_except_mentioned != null && ownedIdentityPojo.mute_notifications_except_mentioned;
                            db.ownedIdentityDao().updateMuteNotifications(ownedIdentity.bytesOwnedIdentity, ownedIdentity.prefMuteNotifications, ownedIdentity.prefMuteNotificationsTimestamp, ownedIdentity.prefMuteNotificationsExceptMentioned);
                        }
                        if (ownedIdentityPojo.show_neutral_notification_when_hidden != null && ownedIdentityPojo.show_neutral_notification_when_hidden) {
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
                                                    contactPojo.discussion_customization.applyTo(discussionCustomization, discussion, true);
                                                    db.discussionCustomizationDao().update(discussionCustomization);
                                                    db.discussionDao().updatePinned(discussion.id, discussion.pinned);
                                                });
                                            } else {
                                                // create the customization
                                                db.runInTransaction(() -> {
                                                    DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                    contactPojo.discussion_customization.applyTo(customization, discussion, true);
                                                    db.discussionCustomizationDao().insert(customization);
                                                    db.discussionDao().updatePinned(discussion.id, discussion.pinned);
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
                                                        boolean sharedSettingsRestored = groupPojo.discussion_customization.applyTo(discussionCustomization, discussion, true);
                                                        db.discussionCustomizationDao().update(discussionCustomization);
                                                        db.discussionDao().updatePinned(discussion.id, discussion.pinned);

                                                        if (sharedSettingsRestored) {
                                                            JsonExpiration expiration = new JsonExpiration();
                                                            expiration.setReadOnce(discussionCustomization.settingReadOnce);
                                                            expiration.setVisibilityDuration(discussionCustomization.settingVisibilityDuration);
                                                            expiration.setExistenceDuration(discussionCustomization.settingExistenceDuration);
                                                            if (!expiration.likeNull()) {
                                                                JsonSharedSettings settings = new JsonSharedSettings();
                                                                //noinspection ConstantConditions
                                                                settings.setVersion(discussionCustomization.sharedSettingsVersion);
                                                                settings.setGroupUid(groupPojo.group_uid);
                                                                settings.setGroupOwner(groupPojo.group_owner_identity);
                                                                settings.setJsonExpiration(expiration);

                                                                Message message = Message.createDiscussionSettingsUpdateMessage(db,
                                                                        discussion.id,
                                                                        settings,
                                                                        ownedIdentity.bytesOwnedIdentity,
                                                                        true,
                                                                        System.currentTimeMillis()
                                                                );
                                                                if (message != null) {
                                                                    message.id = db.messageDao().insert(message);
                                                                    message.postSettingsMessage(false, null);
                                                                }
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    // create the customization
                                                    db.runInTransaction(() -> {
                                                        DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                        boolean sharedSettingsRestored = groupPojo.discussion_customization.applyTo(customization, discussion, true);
                                                        db.discussionCustomizationDao().insert(customization);
                                                        db.discussionDao().updatePinned(discussion.id, discussion.pinned);

                                                        if (sharedSettingsRestored) {
                                                            JsonExpiration expiration = new JsonExpiration();
                                                            expiration.setReadOnce(customization.settingReadOnce);
                                                            expiration.setVisibilityDuration(customization.settingVisibilityDuration);
                                                            expiration.setExistenceDuration(customization.settingExistenceDuration);
                                                            if (!expiration.likeNull()) {
                                                                JsonSharedSettings settings = new JsonSharedSettings();
                                                                //noinspection ConstantConditions
                                                                settings.setVersion(customization.sharedSettingsVersion);
                                                                settings.setGroupUid(groupPojo.group_uid);
                                                                settings.setGroupOwner(groupPojo.group_owner_identity);
                                                                settings.setJsonExpiration(expiration);

                                                                Message message = Message.createDiscussionSettingsUpdateMessage(db,
                                                                        discussion.id,
                                                                        settings,
                                                                        ownedIdentity.bytesOwnedIdentity,
                                                                        true,
                                                                        System.currentTimeMillis()
                                                                );
                                                                if (message != null) {
                                                                    message.id = db.messageDao().insert(message);
                                                                    message.postSettingsMessage(false, null);
                                                                }
                                                            }
                                                        }
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
                                                        groupPojo.discussion_customization.applyTo(discussionCustomization, discussion, false);
                                                        db.discussionCustomizationDao().update(discussionCustomization);
                                                        db.discussionDao().updatePinned(discussion.id, discussion.pinned);
                                                    });
                                                } else {
                                                    // create the customization
                                                    db.runInTransaction(() -> {
                                                        DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                        groupPojo.discussion_customization.applyTo(customization, discussion, false);
                                                        db.discussionCustomizationDao().insert(customization);
                                                        db.discussionDao().updatePinned(discussion.id, discussion.pinned);
                                                    });
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (ownedIdentityPojo.groups2 != null) {
                            for (Group2Pojo_0 group2Pojo : ownedIdentityPojo.groups2) {
                                Group2 group2 = db.group2Dao().get(ownedIdentityPojo.owned_identity, group2Pojo.group_identifier);
                                if (group2 != null) {
                                    if (group2Pojo.custom_name != null) {
                                        group2.customName = group2Pojo.custom_name;
                                        db.group2Dao().updateCustomName(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier, group2.customName);
                                    }

                                    if (group2Pojo.personal_note != null) {
                                        group2.personalNote = group2Pojo.personal_note;
                                        db.group2Dao().updatePersonalNote(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier, group2.personalNote);
                                    }

                                    if (group2Pojo.discussion_customization != null) {
                                        Discussion discussion = db.discussionDao().getByGroupIdentifier(ownedIdentityPojo.owned_identity, group2Pojo.group_identifier);
                                        if (discussion != null) {
                                            if (group2Pojo.custom_name != null) {
                                                discussion.title = group2.getCustomName();
                                                db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                                            }

                                            DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);

                                            if (discussionCustomization != null) {
                                                // there is already a discussion customization, proceed carefully
                                                db.runInTransaction(() -> {
                                                    group2Pojo.discussion_customization.applyTo(discussionCustomization, discussion, true);
                                                    db.discussionCustomizationDao().update(discussionCustomization);
                                                    db.discussionDao().updatePinned(discussion.id, discussion.pinned);
                                                });
                                            } else {
                                                // create the customization
                                                db.runInTransaction(() -> {
                                                    DiscussionCustomization customization = new DiscussionCustomization(discussion.id);
                                                    group2Pojo.discussion_customization.applyTo(customization, discussion, true);
                                                    db.discussionCustomizationDao().insert(customization);
                                                    db.discussionDao().updatePinned(discussion.id, discussion.pinned);
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                new UpdateAllGroupMembersNames().run();

                AppSingleton.reloadCachedDisplayNamesAndHues();
            }

            if (appBackupPojo.settings != null) {
                appBackupPojo.settings.restore();
            }
            new Handler(Looper.getMainLooper()).post(SettingsActivity::setDefaultNightMode);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
