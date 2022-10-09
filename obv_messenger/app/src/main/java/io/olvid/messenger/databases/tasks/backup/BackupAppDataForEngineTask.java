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

import java.util.ArrayList;
import java.util.Arrays;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
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
                ownedIdentityPojo.groups2 = new ArrayList<>();

                for (Contact contact : db.contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity)) {
                    ContactPojo_0 contactPojo = new ContactPojo_0();
                    contactPojo.contact_identity = contact.bytesContactIdentity;
                    contactPojo.custom_name = contact.customDisplayName;
                    contactPojo.custom_hue = contact.customNameHue;
                    contactPojo.personal_note = contact.personalNote;

                    Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                        contactPojo.discussion_customization = DiscussionCustomizationPojo_0.of(discussion, discussionCustomization, true);
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
                        groupPojo.discussion_customization = DiscussionCustomizationPojo_0.of(discussion, discussionCustomization, true);
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
                        groupPojo.discussion_customization = DiscussionCustomizationPojo_0.of(discussion, discussionCustomization, false);
                    }

                    if (!groupPojo.isEmpty()) {
                        ownedIdentityPojo.groups.add(groupPojo);
                    }
                }

                for (Group2 group2 : db.group2Dao().getAllForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)) {
                    Group2Pojo_0 group2Pojo = new Group2Pojo_0();
                    group2Pojo.group_identifier = group2.bytesGroupIdentifier;
                    group2Pojo.custom_name = group2.customName;
                    group2Pojo.personal_note = group2.personalNote;

                    Discussion discussion = db.discussionDao().getByGroupIdentifier(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                        group2Pojo.discussion_customization = DiscussionCustomizationPojo_0.of(discussion, discussionCustomization, false);
                    }

                    if (!group2Pojo.isEmpty()) {
                        ownedIdentityPojo.groups2.add(group2Pojo);
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
