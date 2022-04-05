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

package io.olvid.messenger.databases;


import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask;
import io.olvid.messenger.databases.tasks.InsertContactRevokedMessageTask;
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
            // defer engine database synchronisation to avoid database overload slowness
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // do nothing
        }

        // Check all owned identities and contacts, and group discussions
        syncEngineDatabases();

        // Check status of PROCESSING messages
        List<Message> processingMessages = db.messageDao().getProcessingMessages();
        for (Message message: processingMessages) {
            List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllNotSentByMessageId(message.id);
            HashMap<BytesKey, Boolean> sentStatusCache = new HashMap<>();
            for (MessageRecipientInfo messageRecipientInfo: messageRecipientInfos) {
                if (messageRecipientInfo.engineMessageIdentifier == null) {
                    Contact contact = db.contactDao().get(message.senderIdentifier, messageRecipientInfo.bytesContactIdentity);
                    if (contact.establishedChannelCount > 0) {
                        message.repost(messageRecipientInfo);
                    }
                } else {
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

        // Send UNPROCESSED and COMPUTING_PREVIEW messages
        List<Message> unprocessedMessages = db.messageDao().getUnprocessedAndPreviewingMessages();
        for (Message message: unprocessedMessages) {
            message.post(false, false);
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

        // Update all Messages/attachments
        try {
            engine.resendAllAttachmentNotifications();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error syncing Room messages with Engine attachments.");
        }

        // Check status of all uploading/downloading Fyle
        try {
            List<FyleMessageJoinWithStatus> uploadingFyles = db.fyleMessageJoinWithStatusDao().getUploading();
            for (FyleMessageJoinWithStatus uploadingFyle: uploadingFyles) {
                if ((uploadingFyle.engineMessageIdentifier == null) || (uploadingFyle.engineNumber == null)) {
                    uploadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().updateStatus(uploadingFyle.messageId, uploadingFyle.fyleId, uploadingFyle.status);
                } else if (engine.isOutboxAttachmentSent(uploadingFyle.bytesOwnedIdentity, uploadingFyle.engineMessageIdentifier, uploadingFyle.engineNumber)) {
                    uploadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().updateStatus(uploadingFyle.messageId, uploadingFyle.fyleId, uploadingFyle.status);
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
                        db.fyleMessageJoinWithStatusDao().updateStatus(downloadingFyle.messageId, downloadingFyle.fyleId, downloadingFyle.status);
                        downloadingFyle.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                    }
                } else if (engine.isInboxAttachmentReceived(downloadingFyle.bytesOwnedIdentity, downloadingFyle.engineMessageIdentifier, downloadingFyle.engineNumber)) {
                    downloadingFyle.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().updateStatus(downloadingFyle.messageId, downloadingFyle.fyleId, downloadingFyle.status);
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
                    Fyle.acquireLock(strayFyle.sha256);
                    db.fyleDao().delete(strayFyle);
                    if (strayFyle.filePath != null) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            new File(App.absolutePathFromRelative(strayFyle.filePath)).delete();
                        } catch (Exception ignored) {}
                    }
                    Fyle.releaseLock(strayFyle.sha256);
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
        try {
            List<Contact> appContacts = db.contactDao().getAllSync();
            List<OwnedIdentity> appOwnedIdentities = db.ownedIdentityDao().getAll();
            List<Group> appGroups = db.groupDao().getAllSync();
            HashMap<BytesKey, HashMap<BytesKey, Contact>> contactsHashMap = new HashMap<>();
            HashMap<BytesKey, HashMap<BytesKey, Group>> groupsHashMap = new HashMap<>();
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
            for (OwnedIdentity appOwnedIdentity: appOwnedIdentities) {
                identitiesHashMap.put(new BytesKey(appOwnedIdentity.bytesOwnedIdentity), appOwnedIdentity);
            }

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


                // synchronize Contacts for this OwnedIdentity
                {
                    HashMap<BytesKey, Contact> subMap = contactsHashMap.get(new BytesKey(ownedIdentity.getBytesIdentity()));
                    ObvIdentity[] contactIdentities = engine.getContactsOfOwnedIdentity(ownedIdentity.getBytesIdentity());
                    for (final ObvIdentity contactIdentity : contactIdentities) {
                        Contact contact = null;
                        if (subMap != null) {
                            contact = subMap.get(new BytesKey(contactIdentity.getBytesIdentity()));
                            subMap.remove(new BytesKey(contactIdentity.getBytesIdentity()));
                        }
                        String photoUrl = engine.getContactTrustedDetailsPhotoUrl(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                        boolean oneToOne = engine.isContactOneToOne(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                        int trustLevel = engine.getContactTrustLevel(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                        if (contact == null) {
                            Logger.i("Engine -> App sync: Found unknown Contact");
                            db.runInTransaction(() -> {
                                try {
                                    Contact newContact = new Contact(contactIdentity.getBytesIdentity(), ownedIdentity.getBytesIdentity(), contactIdentity.getIdentityDetails(), false, photoUrl, contactIdentity.isKeycloakManaged(), contactIdentity.isActive(), oneToOne, trustLevel);
                                    db.contactDao().insert(newContact);
                                    if (oneToOne) {
                                        Discussion discussion = db.discussionDao().getByContact(newContact.bytesOwnedIdentity, newContact.bytesContactIdentity);
                                        if (discussion == null) {
                                            discussion = Discussion.createOneToOneDiscussion(newContact.getCustomDisplayName(), newContact.getCustomPhotoUrl(), ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity(), newContact.keycloakManaged, contactIdentity.isActive(), newContact.trustLevel);
                                            discussion.id = db.discussionDao().insert(discussion);


                                            // set default ephemeral message settings
                                            Long existenceDuration = SettingsActivity.getDefaultDiscussionExistenceDuration();
                                            Long visibilityDuration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
                                            boolean readOnce = SettingsActivity.getDefaultDiscussionReadOnce();
                                            if (readOnce || visibilityDuration != null || existenceDuration != null) {
                                                DiscussionCustomization discussionCustomization = new DiscussionCustomization(discussion.id);
                                                discussionCustomization.settingExistenceDuration = existenceDuration;
                                                discussionCustomization.settingVisibilityDuration = visibilityDuration;
                                                discussionCustomization.settingReadOnce = readOnce;
                                                discussionCustomization.sharedSettingsVersion = 0;
                                                db.discussionCustomizationDao().insert(discussionCustomization);
                                                db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), ownedIdentity.getBytesIdentity(), false, 0L));
                                            }

                                            // insert revoked message if needed
                                            if (!contactIdentity.isActive()) {
                                                EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                                                if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
                                                    App.runThread(new InsertContactRevokedMessageTask(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity()));
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                            contact = db.contactDao().get(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                            if (contact == null) {
                                continue;
                            }
                        }
                        int deviceCount = engine.getContactDeviceCount(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                        int establishedChannelCount = engine.getContactEstablishedChannelsCount(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                        if ((contact.deviceCount != deviceCount) || (contact.establishedChannelCount != establishedChannelCount)) {
                            Logger.i("Engine -> App sync: Update contact devices/channel count");
                            contact.deviceCount = deviceCount;
                            contact.establishedChannelCount = establishedChannelCount;
                            db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount);
                        }
                        if (contact.oneToOne != oneToOne) {
                            Logger.i("Engine -> App sync: Update contact oneToOne status");
                            contact.oneToOne = oneToOne;
                            db.contactDao().updateOneToOne(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.oneToOne);
                            if (oneToOne) {
                                Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                if (discussion == null) {
                                    discussion = Discussion.createOneToOneDiscussion(contact.getCustomDisplayName(), contact.getCustomPhotoUrl(), contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged, contact.active, contact.trustLevel);
                                    discussion.id = db.discussionDao().insert(discussion);


                                    // set default ephemeral message settings
                                    Long existenceDuration = SettingsActivity.getDefaultDiscussionExistenceDuration();
                                    Long visibilityDuration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
                                    boolean readOnce = SettingsActivity.getDefaultDiscussionReadOnce();
                                    if (readOnce || visibilityDuration != null || existenceDuration != null) {
                                        DiscussionCustomization discussionCustomization = new DiscussionCustomization(discussion.id);
                                        discussionCustomization.settingExistenceDuration = existenceDuration;
                                        discussionCustomization.settingVisibilityDuration = visibilityDuration;
                                        discussionCustomization.settingReadOnce = readOnce;
                                        discussionCustomization.sharedSettingsVersion = 0;
                                        db.discussionCustomizationDao().insert(discussionCustomization);
                                        db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), contact.bytesOwnedIdentity, false, 0L));
                                    }

                                    // insert revoked message if needed
                                    if (!contact.active) {
                                        EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity());
                                        if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
                                            App.runThread(new InsertContactRevokedMessageTask(ownedIdentity.getBytesIdentity(), contactIdentity.getBytesIdentity()));
                                        }
                                    }
                                }
                            } else {
                                Contact finalContact = contact;
                                db.runInTransaction(() -> {
                                    Discussion discussion = db.discussionDao().getByContact(finalContact.bytesOwnedIdentity, finalContact.bytesContactIdentity);
                                    if (discussion != null) {
                                        discussion.lockWithMessage(db);
                                    }
                                });
                            }
                        }
                        if (contact.keycloakManaged != contactIdentity.isKeycloakManaged()) {
                            Logger.i("Engine -> App sync: Update contact keycloakManaged");
                            contact.keycloakManaged = contactIdentity.isKeycloakManaged();
                            db.contactDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);
                            db.discussionDao().updateKeycloakManaged(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.keycloakManaged);
                        }
                        if (contact.active != contactIdentity.isActive()) {
                            Logger.i("Engine -> App sync: Update contact active");
                            contact.active = contactIdentity.isActive();
                            db.contactDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
                            db.discussionDao().updateActive(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.active);
                        }
                        if (contact.trustLevel != trustLevel) {
                            Logger.i("Engine -> App sync: Update contact trustLevel");
                            contact.trustLevel = trustLevel;
                            db.contactDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                            db.discussionDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                        }
                        if (!Objects.equals(contactIdentity.getIdentityDetails(), contact.getIdentityDetails())
                                || !Objects.equals(contact.photoUrl, photoUrl)) {
                            Logger.i("Engine -> App sync: Update contact details/photoUrl");
                            try {
                                contact.setIdentityDetailsAndDisplayName(contactIdentity.getIdentityDetails());
                                contact.photoUrl = photoUrl;
                                db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);
                                db.contactDao().updatePhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, photoUrl);

                                Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                discussion.title = contact.getCustomDisplayName();
                                db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                                ShortcutActivity.updateShortcut(discussion);
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
                    if (subMap != null && subMap.size() == 0) {
                        contactsHashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));
                    }
                }


                // synchronize Groups for this OwnedIdentity
                {
                    HashMap<BytesKey, Group> subMap = groupsHashMap.get(new BytesKey(ownedIdentity.getBytesIdentity()));

                    ObvGroup[] obvGroups = engine.getGroupsOfOwnedIdentity(ownedIdentity.getBytesIdentity());
                    for (final ObvGroup obvGroup: obvGroups) {
                        Group group = null;
                        if (subMap != null) {
                            group = subMap.get(new BytesKey(obvGroup.getBytesGroupOwnerAndUid()));
                            subMap.remove(new BytesKey(obvGroup.getBytesGroupOwnerAndUid()));
                        }
                        if (group == null) {
                            Logger.i("Engine -> App sync: Found unknown Group");
                            String photoUrl = engine.getGroupTrustedDetailsPhotoUrl(ownedIdentity.getBytesIdentity(), obvGroup.getBytesGroupOwnerAndUid());
                            // create the group, its discussion and all members and pending members
                            db.runInTransaction(() -> {
                                Logger.d("Inserting missing group " + obvGroup.getGroupDetails().getName());
                                try {
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
                                        discussion = Discussion.createGroupDiscussion(newGroup.getCustomName(), newGroup.getCustomPhotoUrl(), newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid);
                                        discussion.id = db.discussionDao().insert(discussion);

                                        // also add default ephemeral settings if you are the group owner
                                        if (newGroup.bytesGroupOwnerIdentity == null) {
                                            Long existenceDuration = SettingsActivity.getDefaultDiscussionExistenceDuration();
                                            Long visibilityDuration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
                                            boolean readOnce = SettingsActivity.getDefaultDiscussionReadOnce();
                                            if (readOnce || visibilityDuration != null || existenceDuration != null) {
                                                DiscussionCustomization discussionCustomization = new DiscussionCustomization(discussion.id);
                                                discussionCustomization.settingExistenceDuration = existenceDuration;
                                                discussionCustomization.settingVisibilityDuration = visibilityDuration;
                                                discussionCustomization.settingReadOnce = readOnce;
                                                discussionCustomization.sharedSettingsVersion = 0;
                                                db.discussionCustomizationDao().insert(discussionCustomization);
                                                db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), newGroup.bytesOwnedIdentity, false, 0L));
                                            }
                                        }
                                    }
                                    Logger.d("Adding " + obvGroup.getBytesGroupMembersIdentities().length + " contacts and " + obvGroup.getPendingGroupMembers().length + " pending");
                                    boolean messageInserted = false;
                                    for (byte[] bytesContactIdentity : obvGroup.getBytesGroupMembersIdentities()) {
                                        ContactGroupJoin contactGroupJoin = new ContactGroupJoin(newGroup.bytesGroupOwnerAndUid, newGroup.bytesOwnedIdentity, bytesContactIdentity);
                                        db.contactGroupJoinDao().insert(contactGroupJoin);
                                        Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(discussion.id, bytesContactIdentity);
                                        db.messageDao().insert(groupJoinedMessage);
                                        messageInserted = true;
                                    }
                                    if (messageInserted) {
                                        if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                        }
                                    }
                                    HashSet<BytesKey> declinedSet = new HashSet<>();
                                    for (byte[] bytesDeclinedPendingMember: obvGroup.getBytesDeclinedPendingMembers()) {
                                        declinedSet.add(new BytesKey(bytesDeclinedPendingMember));
                                    }
                                    for (ObvIdentity obvIdentity : obvGroup.getPendingGroupMembers()) {
                                        PendingGroupMember pendingGroupMember = new PendingGroupMember(obvIdentity.getBytesIdentity(), obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), newGroup.bytesOwnedIdentity, newGroup.bytesGroupOwnerAndUid, declinedSet.contains(new BytesKey(obvIdentity.getBytesIdentity())));
                                        db.pendingGroupMemberDao().insert(pendingGroupMember);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            // check if the discussion exists
                            Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                            if (discussion == null) {
                                Logger.d("Creating missing discussion for existing group !!!");
                                discussion = Discussion.createGroupDiscussion(group.getCustomName(), group.getCustomPhotoUrl(), group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
                                discussion.id = db.discussionDao().insert(discussion);

                                // also add default ephemeral settings if you are the group owner
                                if (group.bytesGroupOwnerIdentity == null) {
                                    Long existenceDuration = SettingsActivity.getDefaultDiscussionExistenceDuration();
                                    Long visibilityDuration = SettingsActivity.getDefaultDiscussionVisibilityDuration();
                                    boolean readOnce = SettingsActivity.getDefaultDiscussionReadOnce();
                                    if (readOnce || visibilityDuration != null || existenceDuration != null) {
                                        DiscussionCustomization discussionCustomization = new DiscussionCustomization(discussion.id);
                                        discussionCustomization.settingExistenceDuration = existenceDuration;
                                        discussionCustomization.settingVisibilityDuration = visibilityDuration;
                                        discussionCustomization.settingReadOnce = readOnce;
                                        discussionCustomization.sharedSettingsVersion = 0;
                                        db.discussionCustomizationDao().insert(discussionCustomization);
                                        db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), group.bytesOwnedIdentity, false, 0L));
                                    }
                                }
                            }

                            // only check for different members
                            final HashSet<BytesKey> contactToRemove = new HashSet<>();
                            final HashSet<BytesKey> contactToAdd = new HashSet<>();
                            for (Contact contact: db.contactGroupJoinDao().getGroupContactsSync(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)) {
                                contactToRemove.add(new BytesKey(contact.bytesContactIdentity));
                            }
                            for (byte[] bytesContactIdentity: obvGroup.getBytesGroupMembersIdentities()) {
                                BytesKey key = new BytesKey(bytesContactIdentity);
                                if (contactToRemove.contains(key)) {
                                    contactToRemove.remove(key);
                                } else {
                                    contactToAdd.add(key);
                                }
                            }
                            DiscussionCustomization.JsonSharedSettings jsonSharedSettings = null;
                            if (!contactToAdd.isEmpty() || !contactToRemove.isEmpty()) {
                                Logger.i("Engine -> App sync: Contact mismatch in group. Remove " + contactToRemove.size() + " and add " + contactToAdd.size());
                                if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                }
                                if (group.bytesGroupOwnerIdentity == null && !contactToAdd.isEmpty()) { // owned group --> check the customization
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                                    if (discussionCustomization != null) {
                                        jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                                    }
                                }
                            }
                            for (BytesKey bytesKey: contactToRemove) {
                                ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(group.bytesGroupOwnerAndUid, group.bytesOwnedIdentity, bytesKey.bytes);
                                db.contactGroupJoinDao().delete(contactGroupJoin);
                                Message groupLeftMessage = Message.createMemberLeftGroupMessage(discussion.id, bytesKey.bytes);
                                db.messageDao().insert(groupLeftMessage);
                            }
                            for (BytesKey bytesKey: contactToAdd) {
                                ContactGroupJoin contactGroupJoin = new ContactGroupJoin(group.bytesGroupOwnerAndUid, group.bytesOwnedIdentity, bytesKey.bytes);
                                db.contactGroupJoinDao().insert(contactGroupJoin);
                                Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(discussion.id, bytesKey.bytes);
                                db.messageDao().insert(groupJoinedMessage);
                            }
                            if (jsonSharedSettings != null) {
                                Message message = Message.createDiscussionSettingsUpdateMessage(discussion.id, jsonSharedSettings, group.bytesOwnedIdentity, true, null);
                                if (message != null) {
                                    message.post(false, true);
                                }
                            }

                            // now check for different pendingMembers
                            HashMap<BytesKey, PendingGroupMember> pendingGroupMembersToRemove = new HashMap<>();
                            HashMap<BytesKey, ObvIdentity> pendingGroupMembersToAdd = new HashMap<>();
                            HashSet<BytesKey> declinedSet = new HashSet<>();
                            for (byte[] bytesDeclinedPendingMember: obvGroup.getBytesDeclinedPendingMembers()) {
                                declinedSet.add(new BytesKey(bytesDeclinedPendingMember));
                            }

                            for (PendingGroupMember pendingGroupMember: db.pendingGroupMemberDao().getGroupPendingMembers(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)) {
                                pendingGroupMembersToRemove.put(new BytesKey(pendingGroupMember.bytesIdentity), pendingGroupMember);
                            }
                            for (ObvIdentity obvIdentity: obvGroup.getPendingGroupMembers()) {
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
                            for (PendingGroupMember pendingGroupMember: pendingGroupMembersToRemove.values()) {
                                db.pendingGroupMemberDao().delete(pendingGroupMember);
                            }
                            for (ObvIdentity obvIdentity: pendingGroupMembersToAdd.values()) {
                                PendingGroupMember pendingGroupMember = new PendingGroupMember(obvIdentity.getBytesIdentity(), obvIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, declinedSet.contains(new BytesKey(obvIdentity.getBytesIdentity())));
                                db.pendingGroupMemberDao().insert(pendingGroupMember);
                            }
                        }
                    }
                    if (subMap != null && subMap.size() == 0) {
                        groupsHashMap.remove(new BytesKey(ownedIdentity.getBytesIdentity()));
                    }
                }
            }

            // now, process all hashmaps to remove app-side-only identities/groups
            // groups first
            for (HashMap<BytesKey, Group> subMap: groupsHashMap.values()) {
                for (Group group: subMap.values()) {
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
            // then contacts
            for (HashMap<BytesKey, Contact> subMap: contactsHashMap.values()) {
                for(Contact contact: subMap.values()) {
                    Logger.i("Engine -> App sync: Deleting app-side-only Contact");
                    contact.delete();
                }
            }
            // and finally owned identities
            for (OwnedIdentity ownedIdentity: identitiesHashMap.values()) {
                Logger.i("Engine -> App sync: Deleting app-side-only OwnedIdentity");
                db.ownedIdentityDao().delete(ownedIdentity);
            }

           AppSingleton.reloadCachedDisplayNamesAndHues();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error syncing Room database with Engine database");
        }
    }
}
