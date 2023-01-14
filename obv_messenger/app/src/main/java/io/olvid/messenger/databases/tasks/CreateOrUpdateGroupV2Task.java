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

package io.olvid.messenger.databases.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.Message;

public class CreateOrUpdateGroupV2Task implements Runnable {
    private final ObvGroupV2 groupV2;
    private final boolean groupWasJustCreatedByMe; // true only if I just created the group
    private final boolean updatedByMe; // true if I just created the group, or if I updated the group
    private final boolean synchronizeUpdateInProgressWithEngine;

    public CreateOrUpdateGroupV2Task(ObvGroupV2 groupV2, boolean groupWasJustCreatedByMe, boolean updatedByMe, boolean synchronizeUpdateInProgressWithEngine) {
        this.groupV2 = groupV2;
        this.groupWasJustCreatedByMe = groupWasJustCreatedByMe;
        this.updatedByMe = updatedByMe;
        this.synchronizeUpdateInProgressWithEngine = synchronizeUpdateInProgressWithEngine;
    }

    @Override
    public void run() {
        final AppDatabase db = AppDatabase.getInstance();
        try {
            List<Runnable> postCommitRunnableList = db.runInTransaction(() -> {
                List<Runnable> runAfterTransaction = new ArrayList<>();

                boolean groupWasJoinedOrRejoined = false;
                boolean hasUntrustedDetails = false;
                boolean messageInserted = false;
                boolean groupHasMembersWithChannels = false;
                boolean listOfUsersWithChangeSettingsPermissionChanged = false;
                List<byte[]> bytesIdentitiesOfUsersWithChangeSettingsPermission = new ArrayList<>();

                byte[] bytesGroupIdentifier = groupV2.groupIdentifier.getBytes();


                //////////
                // check if published details should be accepted or not
                //////////

                String groupName = null;
                try {
                    JsonGroupDetails trustedGroupDetails = AppSingleton.getJsonObjectMapper().readValue(groupV2.detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                    groupName = trustedGroupDetails.getName();

                    if (groupV2.detailsAndPhotos.serializedPublishedDetails != null) {
                        // there are some published details, check if we can auto-trust them
                        if (updatedByMe || UpdateGroupV2PhotoFromEngineTask.detailsCanBeAutoTrusted(groupV2.detailsAndPhotos)) {
                            runAfterTransaction.add(() -> {
                                try {
                                    AppSingleton.getEngine().trustGroupV2PublishedDetails(groupV2.bytesOwnedIdentity, groupV2.groupIdentifier.getBytes());
                                } catch (Exception ignored) { }
                            });
                        } else {
                            hasUntrustedDetails = UpdateGroupV2PhotoFromEngineTask.userShouldBeNotifiedOfNewPublishedDetails(groupV2.detailsAndPhotos);
                        }
                    }
                } catch (Exception e) {
                    Logger.w("Error deserializing group details --> ignoring them");
                    e.printStackTrace();
                }

                boolean insertDetailsUpdatedMessage = false;
                boolean insertGainedAdminMessage = false;
                boolean insertLostAdminMessage = false;
                Group2 group = db.group2Dao().get(groupV2.bytesOwnedIdentity, bytesGroupIdentifier);
                if (group == null) {
                    // if the group does not exist yet, create it
                    group = new Group2(
                            groupV2.bytesOwnedIdentity,
                            bytesGroupIdentifier,
                            groupV2.groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK,
                            groupName,
                            groupV2.detailsAndPhotos.getNullIfEmptyPhotoUrl(),
                            hasUntrustedDetails ? Group2.PUBLISHED_DETAILS_NEW_UNSEEN : Group2.PUBLISHED_DETAILS_NOTHING_NEW,
                            groupV2.ownPermissions);
                    db.group2Dao().insert(group);
                    if (!groupWasJustCreatedByMe && groupV2.ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
                        insertGainedAdminMessage = true;
                    }
                } else {
                    // if it exists, update any field that might have changed
                    group.name = groupName;
                    group.photoUrl = groupV2.detailsAndPhotos.getNullIfEmptyPhotoUrl();
                    if ((group.newPublishedDetails == Group2.PUBLISHED_DETAILS_NOTHING_NEW) == hasUntrustedDetails) { // weird test to check if group has some (seen or not) unpublished details while there are none at engine level, and vice versa
                        group.newPublishedDetails = hasUntrustedDetails ? Group2.PUBLISHED_DETAILS_NEW_UNSEEN : Group2.PUBLISHED_DETAILS_NOTHING_NEW;
                        insertDetailsUpdatedMessage = hasUntrustedDetails;
                    }
                    if (group.ownPermissionAdmin != groupV2.ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
                        if (groupV2.ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN)) {
                            insertGainedAdminMessage = true;
                        } else {
                            insertLostAdminMessage = true;
                        }
                    }
                    group.ownPermissionAdmin = groupV2.ownPermissions.contains(GroupV2.Permission.GROUP_ADMIN);
                    group.ownPermissionRemoteDeleteAnything = groupV2.ownPermissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
                    group.ownPermissionEditOrRemoteDeleteOwnMessages = groupV2.ownPermissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
                    group.ownPermissionChangeSettings = groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
                    group.ownPermissionSendMessage = groupV2.ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE);
                    db.group2Dao().update(group);
                }


                //////////
                // create (or reuse) the associated discussion if there is none
                //////////

                Discussion discussion = db.discussionDao().getByGroupIdentifier(groupV2.bytesOwnedIdentity, bytesGroupIdentifier);
                if (discussion == null) {
                    discussion = Discussion.createOrReuseGroupV2Discussion(db, group, groupWasJustCreatedByMe);
                    groupWasJoinedOrRejoined = true;

                    if (discussion == null) {
                        Logger.e("Failed to createOrReuseGroupV2Discussion");
                        throw new Exception();
                    }
                } else {
                    // update the corresponding group discussion
                    discussion.title = group.getCustomName();
                    discussion.photoUrl = group.getCustomPhotoUrl();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                    ShortcutActivity.updateShortcut(discussion);
                }

                if (insertDetailsUpdatedMessage) {
                    Message newDetailsMessage = Message.createNewPublishedDetailsMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(newDetailsMessage);
                    if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                } else if (groupV2.detailsAndPhotos.serializedPublishedDetails == null) {
                    // delete all group details updated messages from the discussion
                    db.messageDao().deleteAllDiscussionNewPublishedDetailsMessages(discussion.id);
                }

                if (insertGainedAdminMessage) {
                    Message adminMessage = Message.createGainedGroupAdminMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(adminMessage);
                    if (discussion.updateLastMessageTimestamp(adminMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                } else if (insertLostAdminMessage) {
                    Message adminMessage = Message.createLostGroupAdminMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(adminMessage);
                    if (discussion.updateLastMessageTimestamp(adminMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                }

                ////////////
                // If requested, synchronize frozen/updateInProgress
                ////////////

                if (synchronizeUpdateInProgressWithEngine) {
                    boolean updateInProgress = AppSingleton.getEngine().isGroupV2UpdateInProgress(groupV2.bytesOwnedIdentity, groupV2.groupIdentifier);
                    if ((group.updateInProgress == Group2.UPDATE_NONE) == updateInProgress) { // weird test to check if the values are not in sync
                        group.updateInProgress = updateInProgress ? Group2.UPDATE_SYNCING : Group2.UPDATE_NONE;
                        db.group2Dao().updateUpdateInProgress(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.updateInProgress);
                    }
                }


                ////////////
                // synchronize the members/pending members
                ////////////

                final HashMap<BytesKey, Group2Member> membersToRemove = new HashMap<>();
                final HashMap<BytesKey, HashSet<GroupV2.Permission>> membersToAdd = new HashMap<>();
                final HashMap<BytesKey, Group2PendingMember> pendingToRemove = new HashMap<>();
                final HashMap<BytesKey, ObvGroupV2.ObvGroupV2PendingMember> pendingToAdd = new HashMap<>();
                for (Group2Member group2Member : db.group2MemberDao().getGroupMembers(groupV2.bytesOwnedIdentity, bytesGroupIdentifier)) {
                    membersToRemove.put(new BytesKey(group2Member.bytesContactIdentity), group2Member);
                }
                for (Group2PendingMember group2PendingMember : db.group2PendingMemberDao().getGroupPendingMembers(groupV2.bytesOwnedIdentity, bytesGroupIdentifier)) {
                    pendingToRemove.put(new BytesKey(group2PendingMember.bytesContactIdentity), group2PendingMember);
                }

                for (ObvGroupV2.ObvGroupV2Member obvGroupV2Member : groupV2.otherGroupMembers) {
                    // check if we have at least one group member with channels (to post an ephemeral settings message)
                    if (groupWasJustCreatedByMe && !groupHasMembersWithChannels) {
                        Contact contact = db.contactDao().get(group.bytesOwnedIdentity, obvGroupV2Member.bytesIdentity);
                        if (contact != null && contact.establishedChannelCount > 0) {
                            groupHasMembersWithChannels = true;
                        }
                    }

                    if (obvGroupV2Member.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                        bytesIdentitiesOfUsersWithChangeSettingsPermission.add(obvGroupV2Member.bytesIdentity);
                    }

                    BytesKey key = new BytesKey(obvGroupV2Member.bytesIdentity);
                    Group2Member existingMember = membersToRemove.remove(key);
                    if (existingMember == null) {
                        membersToAdd.put(key, obvGroupV2Member.permissions);
                    } else {
                        // check if permissions changed
                        if (existingMember.permissionAdmin != obvGroupV2Member.permissions.contains(GroupV2.Permission.GROUP_ADMIN) ||
                                existingMember.permissionRemoteDeleteAnything != obvGroupV2Member.permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING) ||
                                existingMember.permissionEditOrRemoteDeleteOwnMessages != obvGroupV2Member.permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES) ||
                                existingMember.permissionChangeSettings != obvGroupV2Member.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS) ||
                                existingMember.permissionSendMessage != obvGroupV2Member.permissions.contains(GroupV2.Permission.SEND_MESSAGE)) {

                            if (existingMember.permissionChangeSettings != obvGroupV2Member.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                                listOfUsersWithChangeSettingsPermissionChanged = true;
                            }
                            existingMember.permissionAdmin = obvGroupV2Member.permissions.contains(GroupV2.Permission.GROUP_ADMIN);
                            existingMember.permissionRemoteDeleteAnything = obvGroupV2Member.permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
                            existingMember.permissionEditOrRemoteDeleteOwnMessages = obvGroupV2Member.permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
                            existingMember.permissionChangeSettings = obvGroupV2Member.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
                            existingMember.permissionSendMessage = obvGroupV2Member.permissions.contains(GroupV2.Permission.SEND_MESSAGE);
                            db.group2MemberDao().update(existingMember);
                        }
                    }
                }

                for (ObvGroupV2.ObvGroupV2PendingMember obvGroupV2PendingMember : groupV2.pendingGroupMembers) {
                    BytesKey key = new BytesKey(obvGroupV2PendingMember.bytesIdentity);
                    Group2PendingMember existingPending = pendingToRemove.remove(key);
                    if (existingPending == null) {
                        pendingToAdd.put(key, obvGroupV2PendingMember);
                    } else {
                        boolean changed = false;
                        // check if permission changed
                        if (existingPending.permissionAdmin != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.GROUP_ADMIN) ||
                                existingPending.permissionRemoteDeleteAnything != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING) ||
                                existingPending.permissionEditOrRemoteDeleteOwnMessages != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES) ||
                                existingPending.permissionChangeSettings != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS) ||
                                existingPending.permissionSendMessage != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.SEND_MESSAGE)) {

                            if (existingPending.permissionChangeSettings != obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                                listOfUsersWithChangeSettingsPermissionChanged = true;
                            }
                            existingPending.permissionAdmin = obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.GROUP_ADMIN);
                            existingPending.permissionRemoteDeleteAnything = obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING);
                            existingPending.permissionEditOrRemoteDeleteOwnMessages = obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
                            existingPending.permissionChangeSettings = obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
                            existingPending.permissionSendMessage = obvGroupV2PendingMember.permissions.contains(GroupV2.Permission.SEND_MESSAGE);
                            changed = true;
                        }

                        // check if details changed
                        if (!Objects.equals(existingPending.identityDetails, obvGroupV2PendingMember.serializedDetails)) {
                            existingPending.setIdentityDetailsAndDisplayName(obvGroupV2PendingMember.serializedDetails);
                            changed = true;
                        }

                        if (changed) {
                            db.group2PendingMemberDao().update(existingPending);
                        }
                    }
                }

                // add/remove members
                for (Map.Entry<BytesKey, Group2Member> mapEntry : membersToRemove.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    Group2Member group2Member = mapEntry.getValue();
                    db.group2MemberDao().delete(group2Member);
                    if (!pendingToAdd.containsKey(key)) {
                        if (group2Member.permissionChangeSettings) {
                            listOfUsersWithChangeSettingsPermissionChanged = true;
                        }

                        // only insert a message if member did not simply become pending
                        messageInserted = true;
                        Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, group2Member.bytesContactIdentity);
                        db.messageDao().insert(groupLeftMessage);

                        // get all MessageRecipientInfo for this guy, delete them, and update message status
                        List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoList = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, group2Member.bytesContactIdentity);
                        for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage: messageRecipientInfoList) {
                            db.messageRecipientInfoDao().delete(messageRecipientInfoAndMessage.messageRecipientInfo);

                            if (messageRecipientInfoAndMessage.message.refreshOutboundStatus()) {
                                db.messageDao().updateStatus(messageRecipientInfoAndMessage.message.id, messageRecipientInfoAndMessage.message.status);
                            }
                        }
                    }
                }

                for (Map.Entry<BytesKey, HashSet<GroupV2.Permission>> mapEntry : membersToAdd.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    HashSet<GroupV2.Permission> permissions = mapEntry.getValue();

                    Contact contact = db.contactDao().get(groupV2.bytesOwnedIdentity, key.bytes);
                    if (contact == null) {
                        Logger.w("Failed to add group2 member: contact does not exist");
                    } else {
                        Group2Member group2Member = new Group2Member(groupV2.bytesOwnedIdentity, bytesGroupIdentifier, key.bytes, permissions);
                        db.group2MemberDao().insert(group2Member);
                        if (!pendingToRemove.containsKey(key)) {
                            if (group2Member.permissionChangeSettings) {
                                listOfUsersWithChangeSettingsPermissionChanged = true;
                            }
                            if (!groupWasJoinedOrRejoined) {
                                messageInserted = true;
                                Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, key.bytes);
                                db.messageDao().insert(groupJoinedMessage);
                            }
                        }

                        if (contact.establishedChannelCount != 0) {
                            List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoAndMessages = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, contact.bytesContactIdentity);
                            long discussionId = discussion.id;
                            runAfterTransaction.add(() -> {
                                if (groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                                    // send discussion ephemeral settings to new member
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                    if (discussionCustomization != null) {
                                        DiscussionCustomization.JsonSharedSettings sharedSettings = discussionCustomization.getSharedSettingsJson();
                                        if (sharedSettings != null) {
                                            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussionId, sharedSettings, contact.bytesOwnedIdentity, true, null);
                                            if (message != null) {
                                                message.postSettingsMessage(true, contact.bytesContactIdentity);
                                            }
                                        }
                                    }
                                }
                                db.runInTransaction(() -> {
                                    for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage : messageRecipientInfoAndMessages) {
                                        messageRecipientInfoAndMessage.message.repost(messageRecipientInfoAndMessage.messageRecipientInfo, null);
                                    }
                                });
                            });
                        }
                    }
                }

                // add/remove pending members
                for (Map.Entry<BytesKey, Group2PendingMember> mapEntry : pendingToRemove.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    Group2PendingMember group2PendingMember = mapEntry.getValue();
                    db.group2PendingMemberDao().delete(group2PendingMember);
                    if (!membersToAdd.containsKey(key)) {
                        if (group2PendingMember.permissionChangeSettings) {
                            listOfUsersWithChangeSettingsPermissionChanged = true;
                        }

                        // only insert a message if pending member did not simply become a member
                        messageInserted = true;
                        Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, group2PendingMember.bytesContactIdentity);
                        db.messageDao().insert(groupLeftMessage);

                        // get all MessageRecipientInfo for this guy, delete them, and update message status
                        List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoList = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, group2PendingMember.bytesContactIdentity);
                        for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage: messageRecipientInfoList) {
                            db.messageRecipientInfoDao().delete(messageRecipientInfoAndMessage.messageRecipientInfo);

                            if (messageRecipientInfoAndMessage.message.refreshOutboundStatus()) {
                                db.messageDao().updateStatus(messageRecipientInfoAndMessage.message.id, messageRecipientInfoAndMessage.message.status);
                            }
                        }
                    }
                }

                for (Map.Entry<BytesKey, ObvGroupV2.ObvGroupV2PendingMember> mapEntry : pendingToAdd.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    ObvGroupV2.ObvGroupV2PendingMember obvGroupV2PendingMember = mapEntry.getValue();

                    Group2PendingMember group2PendingMember = new Group2PendingMember(groupV2.bytesOwnedIdentity, bytesGroupIdentifier, obvGroupV2PendingMember.bytesIdentity, obvGroupV2PendingMember.serializedDetails, obvGroupV2PendingMember.permissions);
                    db.group2PendingMemberDao().insert(group2PendingMember);
                    if (AppSingleton.getContactCustomDisplayName(group2PendingMember.bytesContactIdentity) == null) {
                        AppSingleton.updateCachedCustomDisplayName(group2PendingMember.bytesContactIdentity, group2PendingMember.displayName);
                    }
                    if (!membersToRemove.containsKey(key)) {
                        if (group2PendingMember.permissionChangeSettings) {
                            listOfUsersWithChangeSettingsPermissionChanged = true;
                        }
                        if (!groupWasJoinedOrRejoined) {
                            messageInserted = true;
                            Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, obvGroupV2PendingMember.bytesIdentity);
                            db.messageDao().insert(groupJoinedMessage);
                        }
                    }
                }


                // update the group members name field
                group.groupMembersNames = StringUtils.joinGroupMemberNames(db.group2Dao().getGroupMembersNames(groupV2.bytesOwnedIdentity, bytesGroupIdentifier));
                db.group2Dao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.groupMembersNames);

                if (!Objects.equals(discussion.title, group.getCustomName())) {
                    discussion.title = group.getCustomName();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                }


                if (groupWasJustCreatedByMe && groupHasMembersWithChannels) {
                    // this code block should normally never be executed: when creating a group, there are no members initially
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                    if (discussionCustomization != null) {
                        DiscussionCustomization.JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                        if (jsonSharedSettings != null) {
                            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, group.bytesOwnedIdentity, true, null);
                            if (message != null) {
                                message.postSettingsMessage(true, null);
                            }
                        }
                    }
                }

                if (messageInserted && discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                }

                if (!updatedByMe && listOfUsersWithChangeSettingsPermissionChanged && !bytesIdentitiesOfUsersWithChangeSettingsPermission.isEmpty()) {
                    // the list of users with the permission to change group shared settings has changed
                    //    --> send a query shared settings message to them to have the most up to date shared settings
                    Integer jsonSharedSettingsVersion;
                    Message.JsonExpiration jsonExpiration;
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                    if (discussionCustomization != null) {
                        jsonSharedSettingsVersion = discussionCustomization.sharedSettingsVersion;
                        jsonExpiration = discussionCustomization.getExpirationJson();
                    } else {
                        jsonSharedSettingsVersion = null;
                        jsonExpiration = null;
                    }

                    runAfterTransaction.add(() -> {
                        try {
                            AppSingleton.getEngine().post(
                                    Message.getGroupV2DiscussionQuerySharedSettingsPayloadAsBytes(bytesGroupIdentifier, jsonSharedSettingsVersion, jsonExpiration),
                                    null,
                                    new ObvOutboundAttachment[0],
                                    bytesIdentitiesOfUsersWithChangeSettingsPermission,
                                    groupV2.bytesOwnedIdentity,
                                    false,
                                    false
                            );
                        } catch (Exception e) { e.printStackTrace();}
                    });
                }

                return runAfterTransaction;
            });

            if (postCommitRunnableList != null) {
                for (Runnable runnable : postCommitRunnableList) {
                    App.runThread(runnable);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
