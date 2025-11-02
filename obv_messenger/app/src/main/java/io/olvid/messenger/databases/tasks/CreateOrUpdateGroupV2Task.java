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

package io.olvid.messenger.databases.tasks;

import androidx.annotation.Nullable;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.UnreadCountsSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.ContactCacheSingleton;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.new_message.ProcessReadyToProcessOnHoldMessagesTask;
import io.olvid.messenger.settings.SettingsActivity;

public class CreateOrUpdateGroupV2Task implements Runnable {
    private final ObvGroupV2 groupV2;
    private final boolean groupWasJustCreatedByMe; // true only if I just created the group (on this device, or on another)
    private final boolean updatedByMe; // true if I just created the group, or if I updated the group
    private final byte[] updatedBy; // identity bytes that updated the group
    private final byte[][] groupLeavers; // array of identity bytes of users who left the group of their own will and should not be marked as removed by updateBy
    private final boolean createdOnOtherDevice; // true only if groupWasJustCreatedByMe is true and it was created on another device
    private final boolean synchronizeUpdateInProgressWithEngine;

    public CreateOrUpdateGroupV2Task(ObvGroupV2 groupV2, boolean groupWasJustCreatedByMe, boolean updatedByMe, boolean createdOnOtherDevice, boolean synchronizeUpdateInProgressWithEngine, byte[] updatedBy, byte[][] groupLeavers) {
        this.groupV2 = groupV2;
        this.groupWasJustCreatedByMe = groupWasJustCreatedByMe;
        this.updatedByMe = updatedByMe;
        this.updatedBy = updatedBy;
        this.groupLeavers = groupLeavers;
        this.createdOnOtherDevice = createdOnOtherDevice;
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
                boolean needToPostSettingsUpdateMessage = groupWasJustCreatedByMe && !createdOnOtherDevice && db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(groupV2.bytesOwnedIdentity);
                List<byte[]> bytesIdentitiesOfNewMembersWithChangeSettingsPermission = new ArrayList<>();

                byte[] bytesGroupIdentifier = groupV2.groupIdentifier.getBytes();

                byte[] updateAuthor = updatedByMe ? groupV2.bytesOwnedIdentity : updatedBy;
                HashSet<BytesKey> leavers = new HashSet<>();
                if (groupLeavers != null) {
                    for (byte[] leaver : groupLeavers) {
                        leavers.add(new BytesKey(leaver));
                    }
                }

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
                boolean insertGainedSendMessage = false;
                boolean insertLostSendMessage = false;
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
                    if (!groupWasJustCreatedByMe && !groupV2.ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE)) {
                        insertLostSendMessage = true;
                    }
                    if (groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                        bytesIdentitiesOfNewMembersWithChangeSettingsPermission.add(groupV2.bytesOwnedIdentity);
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
                    if (!group.ownPermissionChangeSettings && groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                        bytesIdentitiesOfNewMembersWithChangeSettingsPermission.add(groupV2.bytesOwnedIdentity);
                    }
                    group.ownPermissionChangeSettings = groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS);
                    if (group.ownPermissionSendMessage != groupV2.ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE)) {
                        if (groupV2.ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE)) {
                            insertGainedSendMessage = true;
                        } else {
                            insertLostSendMessage = true;
                        }
                    }
                    group.ownPermissionSendMessage = groupV2.ownPermissions.contains(GroupV2.Permission.SEND_MESSAGE);
                    db.group2Dao().update(group);
                }


                //////////
                // create (or reuse) the associated discussion if there is none
                //////////

                Discussion discussion = db.discussionDao().getByGroupIdentifier(groupV2.bytesOwnedIdentity, bytesGroupIdentifier);
                if (discussion == null) {
                    discussion = Discussion.createOrReuseGroupV2Discussion(db, group, groupWasJustCreatedByMe, createdOnOtherDevice, updatedBy);
                    groupWasJoinedOrRejoined = true;

                    if (discussion == null) {
                        Logger.e("Failed to createOrReuseGroupV2Discussion");
                        throw new Exception();
                    }
                    if (db.messageDao().countMessagesInDiscussion(discussion.id) == 0) {
                        // never insert a "lost write" message in an empty discussion
                        insertLostSendMessage = false;
                    }
                } else {
                    // update the corresponding group discussion
                    discussion.title = group.getCustomName();
                    discussion.photoUrl = group.getCustomPhotoUrl();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                    if (!group.ownPermissionSendMessage && discussion.status != Discussion.STATUS_READ_ONLY) {
                        db.discussionDao().updateStatus(discussion.id, Discussion.STATUS_READ_ONLY);
                    }
                    if (group.ownPermissionSendMessage && discussion.status != Discussion.STATUS_NORMAL) {
                        db.discussionDao().updateStatus(discussion.id, Discussion.STATUS_NORMAL);
                    }

                    ShortcutActivity.updateShortcut(discussion);
                }

                if (insertDetailsUpdatedMessage) {
                    Message newDetailsMessage = Message.createNewPublishedDetailsMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    newDetailsMessage.id = db.messageDao().insert(newDetailsMessage);
                    UnreadCountsSingleton.INSTANCE.newUnreadMessage(discussion.id, newDetailsMessage.id, false, newDetailsMessage.timestamp);
                    if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                } else if (groupV2.detailsAndPhotos.serializedPublishedDetails == null) {
                    // delete all group details updated messages from the discussion
                    List<Message> messageList = db.messageDao().getAllDiscussionNewPublishedDetailsMessages(discussion.id);
                    db.messageDao().delete(messageList.toArray(new Message[0]));
                    UnreadCountsSingleton.INSTANCE.messageBatchDeleted(messageList);
                }

                if (insertGainedAdminMessage) {
                    Message adminMessage = Message.createGainedGroupAdminMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(adminMessage);
                    if (discussion.updateLastMessageTimestamp(adminMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                } else if (insertLostAdminMessage && !insertLostSendMessage) { // only insert lost admin if we did not lose write permission too
                    Message adminMessage = Message.createLostGroupAdminMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(adminMessage);
                    if (discussion.updateLastMessageTimestamp(adminMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                }

                if (!insertGainedAdminMessage && insertGainedSendMessage) { // only insert a "can write" message if we are not an admin
                    Message gainedSendMessage = Message.createGainedGroupSendMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(gainedSendMessage);
                    if (discussion.updateLastMessageTimestamp(gainedSendMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                } else if (insertLostSendMessage) {
                    Message lostSendMessage = Message.createLostGroupSendMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(lostSendMessage);
                    if (discussion.updateLastMessageTimestamp(lostSendMessage.timestamp)) {
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
                    if (groupWasJustCreatedByMe && !createdOnOtherDevice && !needToPostSettingsUpdateMessage) {
                        Contact contact = db.contactDao().get(group.bytesOwnedIdentity, obvGroupV2Member.bytesIdentity);
                        if (contact != null && contact.hasChannelOrPreKey()) {
                            needToPostSettingsUpdateMessage = true;
                        }
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

                            if (!existingMember.permissionChangeSettings && obvGroupV2Member.permissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                                bytesIdentitiesOfNewMembersWithChangeSettingsPermission.add(existingMember.bytesContactIdentity);
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


                // for keycloak groups, keep track of keycloak user Ids to avoid left & join messages when a user updates their key on keycloak
                final HashSet<String> addedKeycloakUserIds = new HashSet<>();
                final HashSet<String> removedKeycloakUserIds = new HashSet<>();
                final HashMap<BytesKey, String> memberBytesIdentityToKeycloakUserIdMap = new HashMap<>();
                final HashMap<BytesKey, String> pendingMemberBytesIdentityToKeycloakUserIdMap = new HashMap<>();
                boolean keycloakGroup = groupV2.groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK;
                boolean membersWereAdded = false;

                if (keycloakGroup) {
                    for (BytesKey key : membersToAdd.keySet()) {
                        Contact contact = db.contactDao().get(group.bytesOwnedIdentity, key.bytes);
                        if (contact != null) {
                            String keycloakUserId = keycloakUserIdFromSerializedDetails(contact.identityDetails);
                            if (keycloakUserId != null) {
                                addedKeycloakUserIds.add(keycloakUserId);
                                memberBytesIdentityToKeycloakUserIdMap.put(key, keycloakUserId);
                            }
                        }
                    }
                    for (Map.Entry<BytesKey, ObvGroupV2.ObvGroupV2PendingMember> pendingMemberEntry : pendingToAdd.entrySet()) {
                        String keycloakUserId = keycloakUserIdFromSerializedDetails(pendingMemberEntry.getValue().serializedDetails);
                        if (keycloakUserId != null) {
                            addedKeycloakUserIds.add(keycloakUserId);
                            pendingMemberBytesIdentityToKeycloakUserIdMap.put(pendingMemberEntry.getKey(), keycloakUserId);
                        }
                    }

                    for (BytesKey key : membersToRemove.keySet()) {
                        Contact contact = db.contactDao().get(group.bytesOwnedIdentity, key.bytes);
                        if (contact != null) {
                            String keycloakUserId = keycloakUserIdFromSerializedDetails(contact.identityDetails);
                            if (keycloakUserId != null) {
                                removedKeycloakUserIds.add(keycloakUserId);
                                memberBytesIdentityToKeycloakUserIdMap.put(key, keycloakUserId);
                            }
                        }
                    }
                    for (Map.Entry<BytesKey, Group2PendingMember> pendingMemberEntry : pendingToRemove.entrySet()) {
                        String keycloakUserId = keycloakUserIdFromSerializedDetails(pendingMemberEntry.getValue().identityDetails);
                        if (keycloakUserId != null) {
                            removedKeycloakUserIds.add(keycloakUserId);
                            pendingMemberBytesIdentityToKeycloakUserIdMap.put(pendingMemberEntry.getKey(), keycloakUserId);
                        }
                    }
                }


                // add/remove members
                for (Map.Entry<BytesKey, Group2Member> mapEntry : membersToRemove.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    Group2Member group2Member = mapEntry.getValue();
                    db.group2MemberDao().delete(group2Member);
                    // only take appropriate actions if member did not simply become pending
                    if (!pendingToAdd.containsKey(key)) {
                        // for keycloak groups, only insert a left group message if the user's keycloakUserId actually left the group
                        if (!keycloakGroup || !addedKeycloakUserIds.contains(memberBytesIdentityToKeycloakUserIdMap.get(key))) {
                            messageInserted = true;
                            Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, group2Member.bytesContactIdentity, leavers.contains(key) ? null : updateAuthor);
                            db.messageDao().upsert(groupLeftMessage);
                        }

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
                        if (group2Member.permissionChangeSettings) {
                            bytesIdentitiesOfNewMembersWithChangeSettingsPermission.add(key.bytes);
                        }

                        if (!pendingToRemove.containsKey(key)) {
                            // for keycloak groups, only insert a joined group message if the user's keycloakUserId actually joined the group
                            if (!groupWasJoinedOrRejoined && (!keycloakGroup || !removedKeycloakUserIds.contains(memberBytesIdentityToKeycloakUserIdMap.get(key)))) {
                                messageInserted = true;
                                Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, key.bytes, updateAuthor);
                                db.messageDao().upsert(groupJoinedMessage);
                            }
                            membersWereAdded = true;
                        }

                        if (contact.hasChannelOrPreKey()) {
                            List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoAndMessages = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, contact.bytesContactIdentity);
                            long discussionId = discussion.id;
                            runAfterTransaction.add(() -> {
                                if (groupV2.ownPermissions.contains(GroupV2.Permission.CHANGE_SETTINGS)) {
                                    // send discussion ephemeral settings to new member
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                    if (discussionCustomization != null) {
                                        JsonSharedSettings sharedSettings = discussionCustomization.getSharedSettingsJson();
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
                    // only take appropriate actions if pending member did not simply become a member
                    if (!membersToAdd.containsKey(key)) {
                        // for keycloak groups, only insert a left group message if the user's keycloakUserId actually left the group
                        if (!keycloakGroup || !addedKeycloakUserIds.contains(pendingMemberBytesIdentityToKeycloakUserIdMap.get(key))) {
                            messageInserted = true;
                            Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, group2PendingMember.bytesContactIdentity, leavers.contains(key) ? null : updateAuthor);
                            db.messageDao().upsert(groupLeftMessage);
                        }

                        // get all MessageRecipientInfo for this guy, delete them, and update message status
                        List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoList = db.messageRecipientInfoDao().getUnsentForContactInDiscussion(discussion.id, group2PendingMember.bytesContactIdentity);
                        for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage: messageRecipientInfoList) {
                            db.messageRecipientInfoDao().delete(messageRecipientInfoAndMessage.messageRecipientInfo);

                            if (messageRecipientInfoAndMessage.message.refreshOutboundStatus()) {
                                db.messageDao().updateStatus(messageRecipientInfoAndMessage.message.id, messageRecipientInfoAndMessage.message.status);
                            }
                        }

                        // if he is not a contact, also remove his name from cache
                        if (Arrays.equals(group2PendingMember.bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())
                                && db.contactDao().get(group2PendingMember.bytesOwnedIdentity, group2PendingMember.bytesContactIdentity) == null) {
                            ContactCacheSingleton.INSTANCE.updateCacheContactDeleted(group2PendingMember.bytesContactIdentity);
                        }
                    }
                }

                for (Map.Entry<BytesKey, ObvGroupV2.ObvGroupV2PendingMember> mapEntry : pendingToAdd.entrySet()) {
                    BytesKey key = mapEntry.getKey();
                    ObvGroupV2.ObvGroupV2PendingMember obvGroupV2PendingMember = mapEntry.getValue();

                    Group2PendingMember group2PendingMember = new Group2PendingMember(groupV2.bytesOwnedIdentity, bytesGroupIdentifier, obvGroupV2PendingMember.bytesIdentity, obvGroupV2PendingMember.serializedDetails, obvGroupV2PendingMember.permissions);
                    db.group2PendingMemberDao().insert(group2PendingMember);
                    if (Arrays.equals(AppSingleton.getBytesCurrentIdentity(), groupV2.bytesOwnedIdentity)
                            && ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(group2PendingMember.bytesContactIdentity) == null) {
                        ContactCacheSingleton.INSTANCE.updateCachedCustomDisplayName(group2PendingMember);
                    }
                    if (!membersToRemove.containsKey(key)) {
                        // for keycloak groups, only insert a joined group message if the user's keycloakUserId actually joined the group
                        if (!groupWasJoinedOrRejoined && (!keycloakGroup || !removedKeycloakUserIds.contains(pendingMemberBytesIdentityToKeycloakUserIdMap.get(key)))) {
                            messageInserted = true;
                            Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, obvGroupV2PendingMember.bytesIdentity, updateAuthor);
                            db.messageDao().upsert(groupJoinedMessage);
                        }
                        membersWereAdded = true;
                    }
                }

                if (membersWereAdded || groupWasJoinedOrRejoined) {
                    // during the transaction, mark as ready to process all pending messages for this group as some messages
                    // for this group (when first joining) or from new members (when updating) may be on hold
                    int count = db.onHoldInboxMessageDao().markAsReadyToProcessForGroupV2Discussion(groupV2.bytesOwnedIdentity, bytesGroupIdentifier);
                    if (count > 0) {
                        Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                    }

                    runAfterTransaction.add(() -> {
                        // once the transaction is committed, process on hold messages that are ready to process
                        AppSingleton.getEngine().runTaskOnEngineNotificationQueue(
                                new ProcessReadyToProcessOnHoldMessagesTask(AppSingleton.getEngine(), db, groupV2.bytesOwnedIdentity)
                        );
                    });
                }


                // update the group members name field
                List<String> fullSearchItems = new ArrayList<>();
                for (Group2MemberDao.Group2MemberOrPending group2MemberOrPending : db.group2MemberDao().getGroupMembersAndPendingSync(group.bytesOwnedIdentity, group.bytesGroupIdentifier)) {
                    if (group2MemberOrPending.contact != null) {
                        fullSearchItems.add(group2MemberOrPending.contact.fullSearchDisplayName);
                    } else {
                        try {
                            JsonIdentityDetails jsonIdentityDetails = AppSingleton.getJsonObjectMapper().readValue(group2MemberOrPending.identityDetails, JsonIdentityDetails.class);
                            fullSearchItems.add(StringUtils.unAccent(jsonIdentityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false)));
                        } catch (Exception ignored) {}
                    }
                }

                group.groupMembersNames = StringUtils.joinContactDisplayNames(
                        SettingsActivity.getAllowContactFirstName() ?
                                db.group2Dao().getGroupMembersFirstNames(groupV2.bytesOwnedIdentity, bytesGroupIdentifier)
                                :
                                db.group2Dao().getGroupMembersNames(groupV2.bytesOwnedIdentity, bytesGroupIdentifier)
                );
                db.group2Dao().updateGroupMembersNames(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.groupMembersNames, group.computeFullSearch(fullSearchItems));

                if (!Objects.equals(discussion.title, group.getCustomName())) {
                    discussion.title = group.getCustomName();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);
                }


                if (groupWasJustCreatedByMe && !createdOnOtherDevice && needToPostSettingsUpdateMessage) {
                    // this code block should normally never be of any use: when creating a group, there are no members initially and my other devices are not yet aware the group exists
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                    if (discussionCustomization != null) {
                        JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
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

                if (((groupWasJustCreatedByMe && createdOnOtherDevice) || !updatedByMe)
                        && !bytesIdentitiesOfNewMembersWithChangeSettingsPermission.isEmpty()) {
                    // the list of users with the permission to change group shared settings has changed
                    //    --> send a query shared settings message to them to have the most up to date shared settings
                    Integer jsonSharedSettingsVersion;
                    JsonExpiration jsonExpiration;
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                    if (discussionCustomization != null) {
                        jsonSharedSettingsVersion = discussionCustomization.sharedSettingsVersion;
                        jsonExpiration = discussionCustomization.getExpirationJson();
                    } else {
                        jsonSharedSettingsVersion = null;
                        jsonExpiration = null;
                    }

                    final Discussion finalDiscussion = discussion;
                    runAfterTransaction.add(() -> {
                        try {
                            AppSingleton.getEngine().post(
                                    Message.getDiscussionQuerySharedSettingsPayloadAsBytes(finalDiscussion, jsonSharedSettingsVersion, jsonExpiration),
                                    null,
                                    new ObvOutboundAttachment[0],
                                    bytesIdentitiesOfNewMembersWithChangeSettingsPermission,
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

    @Nullable
    private String keycloakUserIdFromSerializedDetails(String serializedDetails) {
        try {
            JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(serializedDetails, JsonIdentityDetails.class);
            if (identityDetails.getSignedUserDetails() != null) {
                JwtConsumer noVerificationConsumer = new JwtConsumerBuilder()
                        .setSkipSignatureVerification()
                        .setSkipAllValidators()
                        .build();
                JwtClaims claims = noVerificationConsumer.processToClaims(identityDetails.getSignedUserDetails());
                if (claims != null) {
                    JsonKeycloakUserDetails keycloakUserDetails = AppSingleton.getJsonObjectMapper().readValue(claims.getRawJson(), JsonKeycloakUserDetails.class);
                    return keycloakUserDetails.getId();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
