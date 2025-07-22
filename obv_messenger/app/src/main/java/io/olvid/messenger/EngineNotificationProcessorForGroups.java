/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.UpdateGroupNameAndPhotoTask;
import io.olvid.messenger.databases.tasks.new_message.ProcessReadyToProcessOnHoldMessagesTask;
import io.olvid.messenger.settings.SettingsActivity;

public class EngineNotificationProcessorForGroups implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessorForGroups(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName: new String[] {
                EngineNotifications.GROUP_CREATED,
                EngineNotifications.GROUP_MEMBER_ADDED,
                EngineNotifications.GROUP_MEMBER_REMOVED,
                EngineNotifications.GROUP_DELETED,
                EngineNotifications.PENDING_GROUP_MEMBER_ADDED,
                EngineNotifications.PENDING_GROUP_MEMBER_REMOVED,
                EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED,
                EngineNotifications.NEW_GROUP_PHOTO,
                EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED,
                EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED,
                EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS,

        }) {
            engine.addNotificationListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.GROUP_CREATED: {
                final ObvGroup obvGroup = (ObvGroup) userInfo.get(EngineNotifications.GROUP_CREATED_GROUP_KEY);
                final Boolean hasMultipleDetails = (Boolean) userInfo.get(EngineNotifications.GROUP_CREATED_HAS_MULTIPLE_DETAILS_KEY);
                final String photoUrl = (String) userInfo.get(EngineNotifications.GROUP_CREATED_PHOTO_URL_KEY);
                final Boolean createdOnOtherDevice = (Boolean) userInfo.get(EngineNotifications.GROUP_CREATED_ON_OTHER_DEVICE_KEY);
                if (obvGroup == null || hasMultipleDetails == null || createdOnOtherDevice == null) {
                    break;
                }
                final byte[] bytesGroupOwnerAndUid = obvGroup.getBytesGroupOwnerAndUid();
                final byte[] bytesOwnedIdentity = obvGroup.getBytesOwnedIdentity();
                final byte[] bytesGroupOwnerIdentity = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - UID.UID_LENGTH);

                try {
                    db.runInTransaction(() -> {
                        // create the group, if needed
                        Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (group != null) {
                            // This case should normally never happen
                            Logger.e("Received a GROUP_CREATED notification but the group already exists on the App side.");
                        } else {
                            group = new Group(bytesGroupOwnerAndUid, bytesOwnedIdentity, obvGroup.getGroupDetails(), photoUrl, obvGroup.getBytesGroupOwnerIdentity(), hasMultipleDetails);
                            db.groupDao().insert(group);
                        }

                        // create the discussion if it does not exist
                        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (discussion == null) {
                            discussion = Discussion.createOrReuseGroupDiscussion(db, group, createdOnOtherDevice);

                            if (discussion == null) {
                                throw new RuntimeException("Unable to create group discussion");
                            }
                        }

                        List<String> fullSearchItems = new ArrayList<>();

                        // add members
                        boolean messageInserted = false;
                        for (byte[] bytesGroupMemberIdentity : obvGroup.getBytesGroupMembersIdentities()) {
                            Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesGroupMemberIdentity);
                            if (contact != null) {
                                fullSearchItems.add(contact.fullSearchDisplayName);
                                ContactGroupJoin contactGroupJoin = new ContactGroupJoin(bytesGroupOwnerAndUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                db.contactGroupJoinDao().insert(contactGroupJoin);
                                Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, contact.bytesContactIdentity, bytesGroupOwnerIdentity);
                                db.messageDao().upsert(groupJoinedMessage);
                                messageInserted = true;
                            }
                        }
                        if (messageInserted) {
                            if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                        }

                        // add pending members
                        HashSet<BytesKey> declinedSet = new HashSet<>();
                        for (byte[] bytesDeclinedPendingMember : obvGroup.getBytesDeclinedPendingMembers()) {
                            declinedSet.add(new BytesKey(bytesDeclinedPendingMember));
                        }
                        for (ObvIdentity obvPendingGroupMember : obvGroup.getPendingGroupMembers()) {
                            PendingGroupMember pendingGroupMember = new PendingGroupMember(obvPendingGroupMember.getBytesIdentity(), obvPendingGroupMember.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), bytesOwnedIdentity, bytesGroupOwnerAndUid, declinedSet.contains(new BytesKey(obvPendingGroupMember.getBytesIdentity())));
                            db.pendingGroupMemberDao().insert(pendingGroupMember);
                        }

                        group.groupMembersNames = StringUtils.joinContactDisplayNames(
                                SettingsActivity.getAllowContactFirstName() ?
                                        db.groupDao().getGroupMembersFirstNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                        :
                                        db.groupDao().getGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid));
                        db.groupDao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.groupMembersNames, group.computeFullSearch(fullSearchItems));

                        // if createdByMeOnOtherDevice, query my devices for the sharedSettings
                        if (obvGroup.getBytesGroupOwnerIdentity() == null && createdOnOtherDevice && db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(bytesOwnedIdentity)) {
                            try {
                                AppSingleton.getEngine().post(
                                        Message.getDiscussionQuerySharedSettingsPayloadAsBytes(discussion, null, null),
                                        null,
                                        new ObvOutboundAttachment[0],
                                        Collections.singletonList(bytesOwnedIdentity),
                                        bytesOwnedIdentity,
                                        false,
                                        false
                                );
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        // mark all on hold messages (if any) as ready to process
                        int count = db.onHoldInboxMessageDao().markAsReadyToProcessForGroupDiscussion(
                                bytesOwnedIdentity,
                                bytesGroupOwnerAndUid
                        );
                        if (count > 0) {
                            Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                        }
                    });
                    // if the transaction finished without an exception, trigger the process of on hold messages that are ready to process
                    AppSingleton.getEngine().runTaskOnEngineNotificationQueue(new ProcessReadyToProcessOnHoldMessagesTask(engine, db, bytesOwnedIdentity));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            }
            case EngineNotifications.GROUP_MEMBER_ADDED: {
                byte[] bytesGroupOwnerAndUid = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || bytesGroupOwnerAndUid == null) {
                    break;
                }
                try {
                    Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                    if (group != null) {
                        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                        if (contact != null) {
                            db.runInTransaction(() -> {
                                Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                                if (discussion == null) {
                                    // this case should never happen
                                    discussion = Discussion.createOrReuseGroupDiscussion(db, group, false);

                                    if (discussion == null) {
                                        throw new RuntimeException("Unable to create group discussion");
                                    }
                                }

                                ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(bytesGroupOwnerAndUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                if (contactGroupJoin == null) {
                                    contactGroupJoin = new ContactGroupJoin(bytesGroupOwnerAndUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                    db.contactGroupJoinDao().insert(contactGroupJoin);

                                    group.groupMembersNames = StringUtils.joinContactDisplayNames(
                                            SettingsActivity.getAllowContactFirstName() ?
                                                    db.groupDao().getGroupMembersFirstNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                                    :
                                                    db.groupDao().getGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                    );
                                    db.groupDao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.groupMembersNames, group.fullSearchField + " " + contact.fullSearchDisplayName);

                                    Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, contact.bytesContactIdentity, Arrays.copyOfRange(group.bytesGroupOwnerAndUid, 0, group.bytesGroupOwnerAndUid.length - UID.UID_LENGTH));
                                    db.messageDao().upsert(groupJoinedMessage);
                                    if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                    }
                                }

                                // resend all unsent messages for this contact
                                if (contact.hasChannelOrPreKey()) {
                                    List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoAndMessages = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, contact.bytesContactIdentity);
                                    App.runThread(() -> db.runInTransaction(() -> {
                                        for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage : messageRecipientInfoAndMessages) {
                                            messageRecipientInfoAndMessage.message.repost(messageRecipientInfoAndMessage.messageRecipientInfo, null);
                                        }
                                    }));
                                }

                                // if you are the group owner, check if the group discussion has some shared settings, and resend them
                                if (group.bytesGroupOwnerIdentity == null) {
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                                    if (discussionCustomization != null) {
                                        JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                                        if (jsonSharedSettings != null) {
                                            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, bytesOwnedIdentity, true, null);
                                            if (message != null) {
                                                message.postSettingsMessage(true, bytesContactIdentity);
                                            }
                                        }
                                    }
                                }

                                // mark all on hold messages (if any) as ready to process
                                int count = db.onHoldInboxMessageDao().markAsReadyToProcessForGroupDiscussion(
                                        bytesOwnedIdentity,
                                        bytesGroupOwnerAndUid
                                );
                                if (count > 0) {
                                    Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                                }
                            });

                            // if the transaction finished without an exception, trigger the process of on hold messages that are ready to process
                            AppSingleton.getEngine().runTaskOnEngineNotificationQueue(new ProcessReadyToProcessOnHoldMessagesTask(engine, db, bytesOwnedIdentity));
                        } else {
                            Logger.w("Contact not found while processing a \"Group Member Added\" notification.");
                        }
                    } else {
                        Logger.i("Trying to add group member to a non-existing (yet) group");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case EngineNotifications.GROUP_MEMBER_REMOVED: {
                byte[] bytesGroupOwnerAndUid = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || bytesGroupOwnerAndUid == null) {
                    break;
                }
                try {
                    db.runInTransaction(() -> {
                        Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (group != null) {
                            Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                            if (contact != null) {
                                Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                                if (discussion == null) {
                                    // this case should never happen
                                    discussion = Discussion.createOrReuseGroupDiscussion(db, group, false);

                                    if (discussion == null) {
                                        throw new RuntimeException("Unable to create group discussion");
                                    }

                                    // mark all on hold messages (if any) as ready to process
                                    int count = db.onHoldInboxMessageDao().markAsReadyToProcessForGroupDiscussion(
                                            bytesOwnedIdentity,
                                            bytesGroupOwnerAndUid
                                    );
                                    if (count > 0) {
                                        Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                                    }
                                }

                                ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(bytesGroupOwnerAndUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                if (contactGroupJoin != null) {
                                    db.contactGroupJoinDao().delete(contactGroupJoin);

                                    group.groupMembersNames = StringUtils.joinContactDisplayNames(
                                            SettingsActivity.getAllowContactFirstName() ?
                                                    db.groupDao().getGroupMembersFirstNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                                    :
                                                    db.groupDao().getGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                    );

                                    List<String> fullSearchItems = new ArrayList<>();
                                    for (Contact groupContact : db.contactGroupJoinDao().getGroupContactsSync(bytesOwnedIdentity, bytesGroupOwnerAndUid)) {
                                        if (groupContact != null) {
                                            fullSearchItems.add(groupContact.fullSearchDisplayName);
                                        }
                                    }

                                    db.groupDao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.groupMembersNames, group.computeFullSearch(fullSearchItems));

                                    Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, contact.bytesContactIdentity, null);
                                    db.messageDao().upsert(groupLeftMessage);
                                    if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                    }
                                }
                            } else {
                                Logger.w("Contact not found while processing a \"Group Member Removed\" notification.");
                            }
                        } else {
                            Logger.i("Trying to remove a group member from a non-existing group");
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case EngineNotifications.GROUP_DELETED: {
                byte[] bytesGroupOwnerAndUid = (byte[]) userInfo.get(EngineNotifications.GROUP_DELETED_BYTES_GROUP_OWNER_AND_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_DELETED_BYTES_OWNED_IDENTITY_KEY);

                if (bytesGroupOwnerAndUid == null || bytesOwnedIdentity == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                if (group != null) {
                    db.runInTransaction(()-> {
                        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (discussion != null) {
                            discussion.lockWithMessage(db);
                        }
                        db.groupDao().delete(group);
                    });
                }
                break;
            }
            case EngineNotifications.PENDING_GROUP_MEMBER_ADDED: {
                byte[] groupId = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY);
                ObvIdentity obvIdentity = (ObvIdentity) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || obvIdentity == null || groupId == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, groupId);
                if (group != null) {
                    PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(bytesOwnedIdentity, groupId, obvIdentity.getBytesIdentity());
                    if (pendingGroupMember == null) {
                        pendingGroupMember = new PendingGroupMember(obvIdentity.getBytesIdentity(), obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), bytesOwnedIdentity, groupId, false);
                        db.pendingGroupMemberDao().insert(pendingGroupMember);
                    } else {
                        pendingGroupMember.displayName = obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                        db.pendingGroupMemberDao().update(pendingGroupMember);
                    }
                }
                break;
            }
            case EngineNotifications.PENDING_GROUP_MEMBER_REMOVED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY);
                ObvIdentity obvIdentity = (ObvIdentity) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || obvIdentity == null || bytesGroupUid == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
                if (group != null) {
                    PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(bytesOwnedIdentity, bytesGroupUid, obvIdentity.getBytesIdentity());
                    if (pendingGroupMember != null) {
                        db.pendingGroupMemberDao().delete(pendingGroupMember);
                    }
                }
                break;
            }
            case EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean declined = (Boolean) userInfo.get(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_DECLINED_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || bytesGroupUid == null || declined == null) {
                    break;
                }
                PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(bytesOwnedIdentity, bytesGroupUid, bytesContactIdentity);
                if (pendingGroupMember != null) {
                    pendingGroupMember.declined = declined;
                    db.pendingGroupMemberDao().update(pendingGroupMember);
                }
                break;
            }
            case EngineNotifications.NEW_GROUP_PHOTO: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupOwnerAndUid = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_VERSION_KEY);
                Boolean isTrusted = (Boolean) userInfo.get(EngineNotifications.NEW_GROUP_PHOTO_IS_TRUSTED_KEY);
                if (version == null || isTrusted == null) {
                    break;
                }

                if (isTrusted) {
                    try {
                        JsonGroupDetailsWithVersionAndPhoto[] jsons = engine.getGroupPublishedAndLatestOrTrustedDetails(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (jsons[jsons.length-1].getVersion() == version) {
                            App.runThread(new UpdateGroupNameAndPhotoTask(bytesGroupOwnerAndUid, bytesOwnedIdentity, jsons[jsons.length-1].getGroupDetails().getName(), jsons[jsons.length-1].getPhotoUrl()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetails = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY);
                if (bytesGroupUid == null || bytesOwnedIdentity == null || groupDetails == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
                // this notification should only happen for groups you own
                if (group != null && group.bytesGroupOwnerIdentity == null) {
                    App.runThread(new UpdateGroupNameAndPhotoTask(bytesGroupUid, bytesOwnedIdentity, groupDetails.getGroupDetails().getName(), groupDetails.getPhotoUrl()));
                }
                break;
            }
            case EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetailsWithVersionAndPhoto = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY);
                if (bytesGroupUid == null || bytesOwnedIdentity == null || groupDetailsWithVersionAndPhoto == null) {
                    break;
                }
                String groupName = groupDetailsWithVersionAndPhoto.getGroupDetails().getName();
                if (groupName == null) {
                    break;
                }
                App.runThread(new UpdateGroupNameAndPhotoTask(bytesGroupUid, bytesOwnedIdentity, groupName, groupDetailsWithVersionAndPhoto.getPhotoUrl()));
                break;
            }
            case EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupOwnerAndUid = (byte[]) userInfo.get(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY);

                if (bytesOwnedIdentity != null && bytesGroupOwnerAndUid != null) {
                    Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                    if (group != null && group.bytesGroupOwnerIdentity != null) {
                        try {
                            group.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_UNSEEN;
                            db.groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
                            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                            if (discussion != null) {
                                Message newDetailsMessage = Message.createNewPublishedDetailsMessage(db, discussion.id, group.bytesGroupOwnerIdentity);
                                newDetailsMessage.id = db.messageDao().insert(newDetailsMessage);
                                UnreadCountsSingleton.INSTANCE.newUnreadMessage(discussion.id, newDetailsMessage.id, false, newDetailsMessage.timestamp);
                                if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }


        }
    }



    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return registrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return registrationNumber != null;
    }
}
