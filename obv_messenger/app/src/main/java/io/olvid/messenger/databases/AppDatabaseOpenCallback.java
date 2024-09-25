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

package io.olvid.messenger.databases;


import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvContactInfo;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask;
import io.olvid.messenger.databases.tasks.CreateOrUpdateGroupV2Task;
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask;
import io.olvid.messenger.databases.tasks.UpdateAllGroupMembersNames;
import io.olvid.messenger.settings.SettingsActivity;

public class AppDatabaseOpenCallback implements Runnable {
    private final AppDatabase db;
    private Engine engine;
    AppDatabaseOpenCallback(AppDatabase db) {
        this.db = db;
    }

    @Override
    public void run() {
        engine = AppSingleton.getEngine();
        if (engine == null) {
            return;
        }

        try {
            // defer engine database synchronisation to avoid database overload during startup
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // do nothing
        }

        // Check all owned identities and contacts, and group discussions
        syncEngineDatabases();

        // Check status of PROCESSING messages
        //  - query engine to update status of PROCESSING messages
        //  - post all UNPROCESSED or COMPUTING_PREVIEW messages
        //  - for all contacts with channels, get all MessageRecipientInfo without an engineMessageIdentifier and repost

        // Query engine to update status of PROCESSING messages
        List<Message> processingMessages = db.messageDao().getProcessingMessages();
        for (Message message: processingMessages) {
            List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllNotSentByMessageId(message.id);
            HashMap<BytesKey, Boolean> sentStatusCache = new HashMap<>();
            for (MessageRecipientInfo messageRecipientInfo: messageRecipientInfos) {
                if (messageRecipientInfo.engineMessageIdentifier != null) {
                    Boolean sent = sentStatusCache.get(new BytesKey(messageRecipientInfo.engineMessageIdentifier));
                    if (sent == null) {
                        sent = engine.isOutboxMessageSent(message.getAssociatedBytesOwnedIdentity(), messageRecipientInfo.engineMessageIdentifier);
                        sentStatusCache.put(new BytesKey(messageRecipientInfo.engineMessageIdentifier), sent);
                    }
                    if (sent) {
                        messageRecipientInfo.timestampSent = 0L;
                        db.messageRecipientInfoDao().update(messageRecipientInfo);
                        if (message.refreshOutboundStatus()) {
                            db.messageDao().updateStatus(message.id, message.status);
                        }
                    }
                }
            }
        }

        // Post UNPROCESSED and COMPUTING_PREVIEW messages
        List<Message> unprocessedMessages = db.messageDao().getUnprocessedAndPreviewingMessages();
        for (Message message: unprocessedMessages) {
            db.runInTransaction(() -> message.post(false, null));
        }

        // Repost messages of all contact with channel
        for (Contact contact : db.contactDao().getAllWithChannel()) {
            db.runInTransaction(() -> {
                for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage : db.messageRecipientInfoDao().getAllUnsentForContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity)) {
                    messageRecipientInfoAndMessage.message.repost(messageRecipientInfoAndMessage.messageRecipientInfo, null);
                }
            });
        }



        // Update Invitation/Dialogs
        try {
            // first detect dialogs that should be deleted
            Set<UUID> obvDialogUuids = engine.getAllPersistedDialogUuids();
            List<Invitation> invitations = db.invitationDao().getAll();
            for (Invitation invitation : invitations) {
                if (!obvDialogUuids.contains(invitation.dialogUuid)) {
                    // this dialog no longer exists on engine side --> delete it
                    db.invitationDao().delete(invitation);
                }
            }

            // then update all ongoing dialogs
            engine.resendAllPersistedDialogs();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error syncing Room invitations with Engine dialogs.");
        }

        // Clean up any pre-discussion with no invitation
        try {
            for (Discussion discussion : db.discussionDao().getAllPreDiscussions()) {
                if (!db.invitationDao().discussionHasInvitations(discussion.id)) {
                    db.discussionDao().delete(discussion);
                    ShortcutActivity.disableShortcut(discussion.id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error cleaning up pre-discussions without invitation.");
        }

        // Update all attachments
        try {
            engine.resendAllAttachmentNotifications();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error syncing Room attachments with Engine attachments.");
        }

        // Check status of all uploading/downloading Fyle
        try {
            List<FyleMessageJoinWithStatus> uploadingFyles = db.fyleMessageJoinWithStatusDao().getUploading();
            for (FyleMessageJoinWithStatus uploadingFyle: uploadingFyles) {
                if ((uploadingFyle.engineMessageIdentifier == null) || (uploadingFyle.engineNumber == null)) {
                    uploadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().update(uploadingFyle);
                } else if (engine.isOutboxAttachmentSent(uploadingFyle.bytesOwnedIdentity, uploadingFyle.engineMessageIdentifier, uploadingFyle.engineNumber)) {
                    uploadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().update(uploadingFyle);
                }
            }
            List<FyleMessageJoinWithStatus> downloadingFyles = db.fyleMessageJoinWithStatusDao().getDownloading();
            for (FyleMessageJoinWithStatus downloadingFyle: downloadingFyles) {
                if ((downloadingFyle.engineMessageIdentifier == null) || (downloadingFyle.engineNumber == null)) {
                    Fyle fyle = db.fyleDao().getById(downloadingFyle.fyleId);
                    if (fyle == null || !fyle.isComplete()) {
                        db.fyleMessageJoinWithStatusDao().delete(downloadingFyle);
                        Message message = db.messageDao().get(downloadingFyle.messageId);
                        if (message != null) {
                            message.recomputeAttachmentCount(db);
                            if (message.isEmpty()) {
                                db.messageDao().delete(message);
                            } else {
                                db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, 0, message.imageResolutions);
                            }
                        }
                    } else {
                        downloadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                        db.fyleMessageJoinWithStatusDao().update(downloadingFyle);
                        downloadingFyle.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                    }
                } else if (engine.isInboxAttachmentReceived(downloadingFyle.bytesOwnedIdentity, downloadingFyle.engineMessageIdentifier, downloadingFyle.engineNumber)) {
                    // If a message still exists in the engine, a download completed notification will be resent by the call to resendAllAttachmentNotifications() above.
                    // However, if the message no longer exists, all we can do is mark the app attachment as downloaded,
                    // but we do not have anything in the engine to fetch to actually complete the download
                    downloadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().update(downloadingFyle);
                    downloadingFyle.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error refreshing Fyle statuses.");
        }


        // delete stray Fyle with no associated Message
        try {
            List<Fyle> strayFyles = db.fyleDao().getStray();
            for (Fyle strayFyle: strayFyles) {
                if (strayFyle.sha256 != null) {
                    Logger.i("Cleaning stray Fyle with sha256 " + Logger.toHexString(strayFyle.sha256));
                    try {
                        Fyle.acquireLock(strayFyle.sha256);
                        db.fyleDao().delete(strayFyle);
                        if (strayFyle.filePath != null) {
                            try {
                                //noinspection ResultOfMethodCallIgnored
                                new File(App.absolutePathFromRelative(strayFyle.filePath)).delete();
                            } catch (Exception ignored) {
                            }
                        }
                    } finally {
                        Fyle.releaseLock(strayFyle.sha256);
                    }
                } else {
                    Logger.i("Cleaning stray Fyle with NULL sha256");
                    db.fyleDao().delete(strayFyle);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error cleaning stray Fyles.");
        }

        // apply the discussion retention policies
        new ApplyDiscussionRetentionPoliciesTask(null).run();

        // delete empty locked discussions
        db.discussionDao().deleteEmptyLockedDiscussions();
    }



    private void syncEngineDatabases() {
        Logger.d("🔁 Starting App database synchronisation");
        long timestamp = System.currentTimeMillis();
        try {
            List<Contact> appContacts = db.contactDao().getAllSync();
            List<OwnedIdentity> appOwnedIdentities = db.ownedIdentityDao().getAll();
            List<Group> appGroups = db.groupDao().getAll();
            List<Group2> appGroups2 = db.group2Dao().getAll();
            HashMap<BytesKey, HashMap<BytesKey, Contact>> contactsHashMap = new HashMap<>();
            HashMap<BytesKey, HashMap<BytesKey, Group>> groupsHashMap = new HashMap<>();
            HashMap<BytesKey, HashMap<BytesKey, Group2>> groups2HashMap = new HashMap<>();
            HashMap<BytesKey, OwnedIdentity> identitiesHashMap = new HashMap<>();
            for (Contact appContact: appContacts) {
                HashMap<BytesKey, Contact> subMap = contactsHashMap.get(new BytesKey(appContact.bytesOwnedIdentity));
                if (subMap == null) {
                    subMap = new HashMap<>();
                    contactsHashMap.put(new BytesKey(appContact.bytesOwnedIdentity), subMap);
                }
                subMap.put(new BytesKey(appContact.bytesContactIdentity), appContact);
            }
            for (Group appGroup: appGroups) {
                HashMap<BytesKey, Group> subMap = groupsHashMap.get(new BytesKey(appGroup.bytesOwnedIdentity));
                if (subMap == null) {
                    subMap = new HashMap<>();
                    groupsHashMap.put(new BytesKey(appGroup.bytesOwnedIdentity), subMap);
                }
                subMap.put(new BytesKey(appGroup.bytesGroupOwnerAndUid), appGroup);
            }
            for (Group2 appGroup2 : appGroups2) {
                HashMap<BytesKey, Group2> subMap = groups2HashMap.get(new BytesKey(appGroup2.bytesOwnedIdentity));
                if (subMap == null) {
                    subMap = new HashMap<>();
                    groups2HashMap.put(new BytesKey(appGroup2.bytesOwnedIdentity), subMap);
                }
                subMap.put(new BytesKey(appGroup2.bytesGroupIdentifier), appGroup2);
            }
            for (OwnedIdentity appOwnedIdentity: appOwnedIdentities) {
                identitiesHashMap.put(new BytesKey(appOwnedIdentity.bytesOwnedIdentity), appOwnedIdentity);
            }

            Logger.d("🔁 Warmup: " + (System.currentTimeMillis() - timestamp));
            timestamp = System.currentTimeMillis();

            ObvIdentity[] ownedIdentities = engine.getOwnedIdentities();
            for (final ObvIdentity ownedIdentity: ownedIdentities) {
                // despite already having a hashmap with our app owned identities, we query the DB to be really sure!
                OwnedIdentity dbOwnedIdentity = db.ownedIdentityDao().get(ownedIdentity.getBytesIdentity());
                if (dbOwnedIdentity == null) {
                    Logger.i("Engine -> App sync: Found unknown OwnedIdentity");
                    dbOwnedIdentity = new OwnedIdentity(ownedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);
                    db.ownedIdentityDao().insert(dbOwnedIdentity);
                } else {
                    if (ownedIdentity.isKeycloakManaged() != dbOwnedIdentity.keycloakManaged) {
                        Logger.i("Engine -> App sync: Update OwnedIdentity keycloakManaged");
                        dbOwnedIdentity.keycloakManaged = ownedIdentity.isKeycloakManaged();
                        db.ownedIdentityDao().updateKeycloakManaged(dbOwnedIdentity.bytesOwnedIdentity, dbOwnedIdentity.keycloakManaged);
                    }
                    if (ownedIdentity.isActive() != dbOwnedIdentity.active) {
                        Logger.i("Engine -> App sync: Update OwnedIdentity active");
                        dbOwnedIdentity.active = ownedIdentity.isActive();
                        db.ownedIdentityDao().updateActive(dbOwnedIdentity.bytesOwnedIdentity, dbOwnedIdentity.active);
                    }
                    if (!ownedIdentity.getIdentityDetails().equals(dbOwnedIdentity.getIdentityDetails())) {
                        Logger.i("Engine -> App sync: Update OwnedIdentity details");
                        dbOwnedIdentity.setIdentityDetails(ownedIdentity.getIdentityDetails());
                        db.ownedIdentityDao().updateIdentityDetailsAndDisplayName(dbOwnedIdentity.bytesOwnedIdentity, dbOwnedIdentity.identityDetails, dbOwnedIdentity.displayName);
                    }
                }
                {
                    // update own capabilities
                    List<ObvCapability> ownCapabilities = engine.getOwnCapabilities(dbOwnedIdentity.bytesOwnedIdentity);
                    if (ownCapabilities != null) {
                        for (ObvCapability obvCapability : ObvCapability.values()) {
                            boolean capable = ownCapabilities.contains(obvCapability);

                            switch (obvCapability) {
                                case WEBRTC_CONTINUOUS_ICE:
                                    if (capable != dbOwnedIdentity.capabilityWebrtcContinuousIce) {
                                        Logger.i("Engine -> App sync: Update own capability WEBRTC_CONTINUOUS_ICE");
                                        db.ownedIdentityDao().updateCapabilityWebrtcContinuousIce(dbOwnedIdentity.bytesOwnedIdentity, capable);
                                    }
                                    break;
                                case ONE_TO_ONE_CONTACTS:
                                    if (capable != dbOwnedIdentity.capabilityOneToOneContacts) {
                                        Logger.i("Engine -> App sync: Update own capability ONE_TO_ONE_CONTACTS");
                                        db.ownedIdentityDao().updateCapabilityOneToOneContacts(dbOwnedIdentity.bytesOwnedIdentity, capable);
                                    }
                                    break;
                                case GROUPS_V2:
                                    if (capable != dbOwnedIdentity.capabilityGroupsV2) {
                                        Logger.i("Engine -> App sync: Update own capability GROUPS_V2");
                                        db.ownedIdentityDao().updateCapabilityGroupsV2(dbOwnedIdentity.bytesOwnedIdentity, capable);
                                    }
                                    break;
                            }
                        }
                    }
                }
                identitiesHashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));

                Logger.d("🔁 Sync owned identity: " + (System.currentTimeMillis() - timestamp));
                timestamp = System.currentTimeMillis();

                ////////////////
                // synchronize OwnedDevices
                new OwnedDevicesSynchronisationWithEngineTask(ownedIdentity.getBytesIdentity()).run();

                Logger.d("🔁 Sync devices: " + (System.currentTimeMillis() - timestamp));
                timestamp = System.currentTimeMillis();

                ////////////////
                // synchronize Contacts for this OwnedIdentity
                {
                    HashMap<BytesKey, Contact> subMap = contactsHashMap.get(new BytesKey(ownedIdentity.getBytesIdentity()));
                    List<ObvContactInfo> contactIdentities = engine.getContactsInfoOfOwnedIdentity(ownedIdentity.getBytesIdentity());
                    for (final ObvContactInfo contactInfo : contactIdentities) {
                        Contact contact = null;
                        if (subMap != null) {
                            contact = subMap.remove(new BytesKey(contactInfo.bytesContactIdentity));
                        }
                        if (contact == null) {
                            Logger.i("Engine -> App sync: Found unknown Contact");
                            try {
                                db.runInTransaction(() -> {
                                    Contact newContact;
                                    try {
                                        newContact = new Contact(contactInfo.bytesContactIdentity, contactInfo.bytesContactIdentity, contactInfo.identityDetails, false, contactInfo.photoUrl, contactInfo.keycloakManaged, contactInfo.active, contactInfo.oneToOne, contactInfo.trustLevel, contactInfo.recentlyOnline);
                                        db.contactDao().insert(newContact);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }

                                    if (contactInfo.oneToOne) {
                                        Discussion discussion = Discussion.createOrReuseOneToOneDiscussion(db, newContact);

                                        if (discussion == null) {
                                            throw new RuntimeException("Unable to create discussion!");
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            contact = db.contactDao().get(ownedIdentity.getBytesIdentity(), contactInfo.bytesContactIdentity);
                            if (contact == null) {
                                continue;
                            }
                        }

                        if ((contact.deviceCount != contactInfo.contactDeviceCount.deviceCount)
                                || (contact.establishedChannelCount != contactInfo.contactDeviceCount.establishedChannelCount
                                || contact.preKeyCount != contactInfo.contactDeviceCount.preKeyCount)) {
                            Logger.i("Engine -> App sync: Update contact devices/channel count");
                            contact.deviceCount = contactInfo.contactDeviceCount.deviceCount;
                            contact.establishedChannelCount = contactInfo.contactDeviceCount.establishedChannelCount;
                            contact.preKeyCount = contactInfo.contactDeviceCount.preKeyCount;
                            db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount, contact.preKeyCount);
                        }

                        if (contact.oneToOne != contactInfo.oneToOne) {
                            Logger.i("Engine -> App sync: Update contact oneToOne status");
                            contact.oneToOne = contactInfo.oneToOne;
                            db.contactDao().updateOneToOne(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.oneToOne);
                            Contact finalContact = contact;
                            if (contactInfo.oneToOne) {
                                try {
                                    db.runInTransaction(() -> {
                                        Discussion discussion = Discussion.createOrReuseOneToOneDiscussion(db, finalContact);

                                        if (discussion == null) {
                                            throw new RuntimeException("Unable to create discussion!");
                                        }
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                db.runInTransaction(() -> {
                                    Discussion discussion = db.discussionDao().getByContact(finalContact.bytesOwnedIdentity, finalContact.bytesContactIdentity);
                                    if (discussion != null) {
                                        discussion.lockWithMessage(db);
                                    }
                                });
                            }
                        }
                        if (contact.keycloakManaged != contactInfo.keycloakManaged) {
                            Logger.i("Engine -> App sync: Update contact keycloakManaged");
                            contact.keycloakManaged = contactInfo.keycloakManaged;
                            db.contactDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);
                            db.discussionDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);
                        }
                        if (contact.active != contactInfo.active) {
                            Logger.i("Engine -> App sync: Update contact active");
                            contact.active = contactInfo.active;
                            db.contactDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
                            db.discussionDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
                        }
                        if (contact.trustLevel != contactInfo.trustLevel) {
                            Logger.i("Engine -> App sync: Update contact trustLevel");
                            contact.trustLevel = contactInfo.trustLevel;
                            db.contactDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                            db.discussionDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                        }
                        if (contact.recentlyOnline != contactInfo.recentlyOnline) {
                            Logger.i("Engine -> App sync: Update contact recentlyOnline");
                            contact.recentlyOnline = contactInfo.recentlyOnline;
                            db.contactDao().updateRecentlyOnline(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.recentlyOnline);
                        }
                        if (!Objects.equals(contactInfo.identityDetails, contact.getIdentityDetails())
                                || !Objects.equals(contact.photoUrl, contactInfo.photoUrl)) {
                            Logger.i("Engine -> App sync: Update contact details/photoUrl");
                            try {
                                contact.setIdentityDetailsAndDisplayName(contactInfo.identityDetails);
                                contact.photoUrl = contactInfo.photoUrl;
                                db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.firstName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);
                                db.contactDao().updatePhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.photoUrl);

                                Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                if (discussion != null) {
                                    discussion.title = contact.getCustomDisplayName();
                                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                                    ShortcutActivity.updateShortcut(discussion);

                                    new UpdateAllGroupMembersNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity).run();
                                }
                            } catch (Exception e) {
                                // do nothing
                            }
                        }
                        {
                            // update contact capabilities
                            List<ObvCapability> contactCapabilities = engine.getContactCapabilities(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            if (contactCapabilities != null) {
                                for (ObvCapability obvCapability : ObvCapability.values()) {
                                    boolean capable = contactCapabilities.contains(obvCapability);

                                    switch (obvCapability) {
                                        case WEBRTC_CONTINUOUS_ICE:
                                            if (capable != contact.capabilityWebrtcContinuousIce) {
                                                Logger.i("Engine -> App sync: Update contact capability WEBRTC_CONTINUOUS_ICE");
                                                db.contactDao().updateCapabilityWebrtcContinuousIce(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                            }
                                            break;
                                        case ONE_TO_ONE_CONTACTS:
                                            if (capable != contact.capabilityOneToOneContacts) {
                                                Logger.i("Engine -> App sync: Update contact capability ONE_TO_ONE_CONTACTS");
                                                db.contactDao().updateCapabilityOneToOneContacts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                            }
                                            break;
                                        case GROUPS_V2:
                                            if (capable != contact.capabilityGroupsV2) {
                                                Logger.i("Engine -> App sync: Update contact capability GROUPS_V2");
                                                db.contactDao().updateCapabilityGroupsV2(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                            }
                                            break;
                                    }
                                }
                            }
                        }
                    }
                    if (subMap != null && subMap.isEmpty()) {
                        contactsHashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));
                    }
                }

                Logger.d("🔁 Sync contacts: " + (System.currentTimeMillis() - timestamp));
                timestamp = System.currentTimeMillis();

                // synchronize Groups for this OwnedIdentity
                {
                    HashMap<BytesKey, Group> subMap = groupsHashMap.get(new BytesKey(ownedIdentity.getBytesIdentity()));

                    ObvGroup[] obvGroups = engine.getGroupsOfOwnedIdentity(ownedIdentity.getBytesIdentity());
                    for (final ObvGroup obvGroup: obvGroups) {
                        Group group = null;
                        if (subMap != null) {
                            group = subMap.remove(new BytesKey(obvGroup.getBytesGroupOwnerAndUid()));
                        }
                        if (group == null) {
                            Logger.i("Engine -> App sync: Found unknown Group");
                            String photoUrl = engine.getGroupTrustedDetailsPhotoUrl(ownedIdentity.getBytesIdentity(), obvGroup.getBytesGroupOwnerAndUid());
                            // create the group, its discussion and all members and pending members
                            try {
                                db.runInTransaction(() -> {
                                    Logger.d("Inserting missing group " + obvGroup.getGroupDetails().getName());
                                    Group newGroup = new Group(
                                            obvGroup.getBytesGroupOwnerAndUid(),
                                            obvGroup.getBytesOwnedIdentity(),
                                            obvGroup.getGroupDetails(),
                                            photoUrl,
                                            obvGroup.getBytesGroupOwnerIdentity(),
                                            false);
                                    db.groupDao().insert(newGroup);
                                    Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid);
                                    if (discussion == null) {
                                        Logger.d("Creating associated group discussion");
                                        discussion = Discussion.createOrReuseGroupDiscussion(db, newGroup, false);

                                        if (discussion == null) {
                                            throw new RuntimeException("Unable to create group discussion");
                                        }
                                    }
                                    Logger.d("Adding " + obvGroup.getBytesGroupMembersIdentities().length + " contacts and " + obvGroup.getPendingGroupMembers().length + " pending");
                                    boolean messageInserted = false;
                                    for (byte[] bytesContactIdentity : obvGroup.getBytesGroupMembersIdentities()) {
                                        ContactGroupJoin contactGroupJoin = new ContactGroupJoin(newGroup.bytesGroupOwnerAndUid, newGroup.bytesOwnedIdentity, bytesContactIdentity);
                                        db.contactGroupJoinDao().insert(contactGroupJoin);

                                        Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, bytesContactIdentity);
                                        db.messageDao().insert(groupJoinedMessage);
                                        messageInserted = true;
                                    }
                                    if (messageInserted) {
                                        if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                        }
                                    }

                                    newGroup.groupMembersNames = StringUtils.joinContactDisplayNames(
                                            SettingsActivity.getAllowContactFirstName() ?
                                                    db.groupDao().getGroupMembersFirstNames(newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid)
                                                    :
                                                    db.groupDao().getGroupMembersNames(newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid)
                                    );
                                    db.groupDao().updateGroupMembersNames(newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid, newGroup.groupMembersNames);

                                    HashSet<BytesKey> declinedSet = new HashSet<>();
                                    for (byte[] bytesDeclinedPendingMember : obvGroup.getBytesDeclinedPendingMembers()) {
                                        declinedSet.add(new BytesKey(bytesDeclinedPendingMember));
                                    }
                                    for (ObvIdentity obvIdentity : obvGroup.getPendingGroupMembers()) {
                                        PendingGroupMember pendingGroupMember = new PendingGroupMember(obvIdentity.getBytesIdentity(), obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid, declinedSet.contains(new BytesKey(obvIdentity.getBytesIdentity())));
                                        db.pendingGroupMemberDao().insert(pendingGroupMember);
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            // check if the discussion exists
                            Group finalGroup = group;
                            try {
                                db.runInTransaction(() -> {
                                    Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid);
                                    if (discussion == null) {
                                        Logger.d("Creating missing discussion for existing group !!!");
                                        discussion = Discussion.createOrReuseGroupDiscussion(db, finalGroup, false);

                                        if (discussion == null) {
                                            throw new RuntimeException("Unable to create group discussion");
                                        }
                                    }

                                    // only check for different members
                                    final HashSet<BytesKey> contactToRemove = new HashSet<>();
                                    final HashSet<BytesKey> contactToAdd = new HashSet<>();
                                    for (Contact contact : db.contactGroupJoinDao().getGroupContactsSync(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid)) {
                                        contactToRemove.add(new BytesKey(contact.bytesContactIdentity));
                                    }
                                    for (byte[] bytesContactIdentity : obvGroup.getBytesGroupMembersIdentities()) {
                                        BytesKey key = new BytesKey(bytesContactIdentity);
                                        if (!contactToRemove.remove(key)) {
                                            contactToAdd.add(key);
                                        }
                                    }
                                    for (BytesKey bytesKey : contactToRemove) {
                                        ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(finalGroup.bytesGroupOwnerAndUid, finalGroup.bytesOwnedIdentity, bytesKey.bytes);
                                        db.contactGroupJoinDao().delete(contactGroupJoin);
                                        Message groupLeftMessage = Message.createMemberLeftGroupMessage(db, discussion.id, bytesKey.bytes);
                                        db.messageDao().insert(groupLeftMessage);
                                    }
                                    for (BytesKey bytesKey : contactToAdd) {
                                        ContactGroupJoin contactGroupJoin = new ContactGroupJoin(finalGroup.bytesGroupOwnerAndUid, finalGroup.bytesOwnedIdentity, bytesKey.bytes);
                                        db.contactGroupJoinDao().insert(contactGroupJoin);
                                        Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(db, discussion.id, bytesKey.bytes);
                                        db.messageDao().insert(groupJoinedMessage);
                                    }

                                    if (!contactToAdd.isEmpty() || !contactToRemove.isEmpty()) {
                                        Logger.i("Engine -> App sync: Contact mismatch in group. Remove " + contactToRemove.size() + " and add " + contactToAdd.size());
                                        if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                        }

                                        finalGroup.groupMembersNames = StringUtils.joinContactDisplayNames(
                                                SettingsActivity.getAllowContactFirstName() ?
                                                        db.groupDao().getGroupMembersFirstNames(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid)
                                                        :
                                                        db.groupDao().getGroupMembersNames(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid)
                                        );
                                        db.groupDao().updateGroupMembersNames(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid, finalGroup.groupMembersNames);

                                        if (finalGroup.bytesGroupOwnerIdentity == null && !contactToAdd.isEmpty()) { // owned group --> check the customization
                                            DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                                            if (discussionCustomization != null) {
                                                JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();

                                                if (jsonSharedSettings != null) {
                                                    Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, finalGroup.bytesOwnedIdentity, true, null);
                                                    if (message != null) {
                                                        message.postSettingsMessage(true, null);
                                                    }
                                                }
                                            }
                                        }
                                    }


                                    // now check for different pendingMembers
                                    HashMap<BytesKey, PendingGroupMember> pendingGroupMembersToRemove = new HashMap<>();
                                    HashMap<BytesKey, ObvIdentity> pendingGroupMembersToAdd = new HashMap<>();
                                    HashSet<BytesKey> declinedSet = new HashSet<>();
                                    for (byte[] bytesDeclinedPendingMember : obvGroup.getBytesDeclinedPendingMembers()) {
                                        declinedSet.add(new BytesKey(bytesDeclinedPendingMember));
                                    }

                                    for (PendingGroupMember pendingGroupMember : db.pendingGroupMemberDao().getGroupPendingMembers(finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid)) {
                                        pendingGroupMembersToRemove.put(new BytesKey(pendingGroupMember.bytesIdentity), pendingGroupMember);
                                    }
                                    for (ObvIdentity obvIdentity : obvGroup.getPendingGroupMembers()) {
                                        BytesKey key = new BytesKey(obvIdentity.getBytesIdentity());
                                        if (pendingGroupMembersToRemove.containsKey(key)) {
                                            PendingGroupMember pendingGroupMember = pendingGroupMembersToRemove.get(key);
                                            if (pendingGroupMember != null && (pendingGroupMember.declined ^ declinedSet.contains(key))) {
                                                pendingGroupMember.declined = declinedSet.contains(key);
                                                db.pendingGroupMemberDao().update(pendingGroupMember);
                                            }
                                            pendingGroupMembersToRemove.remove(key);
                                        } else {
                                            pendingGroupMembersToAdd.put(key, obvIdentity);
                                        }
                                    }

                                    if (!pendingGroupMembersToAdd.isEmpty() || !pendingGroupMembersToRemove.isEmpty()) {
                                        Logger.i("Engine -> App sync: Pending group member mismatch in group. Remove " + pendingGroupMembersToRemove.size() + " and add " + pendingGroupMembersToAdd.size());
                                    }
                                    for (PendingGroupMember pendingGroupMember : pendingGroupMembersToRemove.values()) {
                                        db.pendingGroupMemberDao().delete(pendingGroupMember);
                                    }
                                    for (ObvIdentity obvIdentity : pendingGroupMembersToAdd.values()) {
                                        PendingGroupMember pendingGroupMember = new PendingGroupMember(obvIdentity.getBytesIdentity(), obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), finalGroup.bytesOwnedIdentity, finalGroup.bytesGroupOwnerAndUid, declinedSet.contains(new BytesKey(obvIdentity.getBytesIdentity())));
                                        db.pendingGroupMemberDao().insert(pendingGroupMember);
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (subMap != null && subMap.isEmpty()) {
                        groupsHashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));
                    }
                }

                Logger.d("🔁 Sync groups v1: " + (System.currentTimeMillis() - timestamp));
                timestamp = System.currentTimeMillis();

                // synchronize groups V2
                {
                    HashMap<BytesKey, Group2> subMap = groups2HashMap.get(new BytesKey(ownedIdentity.getBytesIdentity()));

                    List<ObvGroupV2> obvGroupsV2 = engine.getGroupsV2OfOwnedIdentity(ownedIdentity.getBytesIdentity());
                    for (final ObvGroupV2 obvGroupV2 : obvGroupsV2) {
                        if (subMap != null) {
                            subMap.remove(new BytesKey(obvGroupV2.groupIdentifier.getBytes()));
                        }
                        new CreateOrUpdateGroupV2Task(obvGroupV2, false, false, false,true, null).run();
                    }

                    if (subMap != null && subMap.isEmpty()) {
                        groups2HashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));
                    }
                }


                Logger.d("🔁 Sync groups v2: " + (System.currentTimeMillis() - timestamp));
                timestamp = System.currentTimeMillis();
            }

            // now, process all hashmaps to remove app-side-only identities/groups
            // groupsV2 first
            for (HashMap<BytesKey, Group2> subMap: groups2HashMap.values()) {
                for (Group2 group2: subMap.values()) {
                    // safe-check before deletion
                    if (engine.getGroupV2Version(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier) != null) {
                        Logger.e("Engine -> App sync: Would have deleted a Group2 that still exists in engine!");
                    } else {
                        Logger.i("Engine -> App sync: Deleting app-side-only Group2");
                        db.runInTransaction(() -> {
                            Discussion discussion = db.discussionDao().getByGroupIdentifier(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
                            if (discussion != null) {
                                discussion.lockWithMessage(db);
                            }
                            db.group2Dao().delete(group2);
                            // Group2Member and Group2PendingMember are cascade deleted
                        });
                    }
                }
            }
            // then groups
            for (HashMap<BytesKey, Group> subMap: groupsHashMap.values()) {
                for (Group group: subMap.values()) {
                    // safe-check before deletion
                    if (engine.getGroupPublishedAndLatestOrTrustedDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid) != null) {
                        Logger.e("Engine -> App sync: Would have deleted a Group that still exists in engine!");
                    } else {
                        Logger.i("Engine -> App sync: Deleting app-side-only Group");
                        db.runInTransaction(() -> {
                            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                            if (discussion != null) {
                                discussion.lockWithMessage(db);
                            }
                            db.groupDao().delete(group);
                            // ContactGroupJoin elements are automatically cascade deleted
                        });
                    }
                }
            }
            // then contacts
            for (HashMap<BytesKey, Contact> subMap: contactsHashMap.values()) {
                for(Contact contact: subMap.values()) {
                    // safe-check before deletion
                    if (engine.getContactTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity) != 0) {
                        Logger.e("Engine -> App sync: Would have deleted a Contact that still exists in engine!");
                    } else {
                        Logger.i("Engine -> App sync: Deleting app-side-only Contact");
                        contact.delete();
                    }
                }
            }
            // and finally owned identities
            for (OwnedIdentity ownedIdentity: identitiesHashMap.values()) {
                // safe-check before deletion
                if (engine.getOwnedIdentity(ownedIdentity.bytesOwnedIdentity) != null) {
                    Logger.e("Engine -> App sync: Would have deleted an OwnedIdentity that still exists in engine!");
                } else {
                    Logger.i("Engine -> App sync: Deleting app-side-only OwnedIdentity");
                    db.ownedIdentityDao().delete(ownedIdentity);
                }
            }

           AppSingleton.reloadCachedDisplayNamesAndHues();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error syncing Room database with Engine database");
        }
        Logger.d("🔁 Finished App database synchronisation");
    }
}
