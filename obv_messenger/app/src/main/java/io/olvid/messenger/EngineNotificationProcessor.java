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

package io.olvid.messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.tasks.HandleReceiveReturnReceipt;
import io.olvid.messenger.databases.tasks.InsertContactRevokedMessageTask;
import io.olvid.messenger.databases.tasks.UpdateContactActiveTask;
import io.olvid.messenger.databases.tasks.backup.BackupAppDataForEngineTask;
import io.olvid.messenger.databases.tasks.HandleMessageExtendedPayloadTask;
import io.olvid.messenger.databases.tasks.HandleNewMessageNotificationTask;
import io.olvid.messenger.databases.tasks.UpdateContactKeycloakManagedTask;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.tasks.UpdateContactDisplayNameAndPhotoTask;
import io.olvid.messenger.databases.tasks.UpdateGroupNameAndPhotoTask;
import io.olvid.messenger.services.GoogleDriveService;

public class EngineNotificationProcessor implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessor(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName: new String[] {
                EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED,
                EngineNotifications.NEW_MESSAGE_RECEIVED,
                EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED,
                EngineNotifications.NEW_CONTACT,
                EngineNotifications.CONTACT_DELETED,
                EngineNotifications.UI_DIALOG,
                EngineNotifications.UI_DIALOG_DELETED,
                EngineNotifications.NEW_CONTACT_DEVICE,
                EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS,
                EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS,
                EngineNotifications.ATTACHMENT_DOWNLOADED,
                EngineNotifications.ATTACHMENT_UPLOADED,
                EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED,
                EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED,
                EngineNotifications.MESSAGE_UPLOADED,
                EngineNotifications.GROUP_CREATED,
                EngineNotifications.GROUP_DELETED,
                EngineNotifications.GROUP_MEMBER_ADDED,
                EngineNotifications.GROUP_MEMBER_REMOVED,
                EngineNotifications.PENDING_GROUP_MEMBER_ADDED,
                EngineNotifications.PENDING_GROUP_MEMBER_REMOVED,
                EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED,
                EngineNotifications.API_KEY_ACCEPTED,
//                EngineNotifications.API_KEY_REJECTED,
                EngineNotifications.OWNED_IDENTITY_LIST_UPDATED,
                EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED,
                EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED,
                EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED,
                EngineNotifications.CONTACT_ACTIVE_CHANGED,
                EngineNotifications.CONTACT_REVOKED,
                EngineNotifications.NEW_CONTACT_PHOTO,
                EngineNotifications.NEW_GROUP_PHOTO,
                EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS,
                EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED,
                EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED,
                EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS,
                EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED,
                EngineNotifications.RETURN_RECEIPT_RECEIVED,
                EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED,
                EngineNotifications.BACKUP_FINISHED,
                EngineNotifications.APP_BACKUP_REQUESTED,
                EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS,
        }) {
                    engine.addNotificationListener(notificationName, this);
        }
    }


    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY);
                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    try {
                        contact.establishedChannelCount = engine.getContactEstablishedChannelsCount(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                        db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount);

                        if (contact.establishedChannelCount > 0) {
                            // Resend all UNPROCESSED_MESSAGES
                            // direct messages
                            List<Message> unprocessedMessages = db.messageDao().getUnprocessedOrPreviewingMessagesForContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            for (Message message : unprocessedMessages) {
                                message.post(false, false);
                            }
                            // group message
                            unprocessedMessages = db.messageDao().getUnprocessedOrPreviewingGroupMessagesForContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            for (Message message : unprocessedMessages) {
                                message.post(false, false);
                            }

                            // Search for MessageRecipientInfo indicating a message was not sent to this user
                            List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoAndMessages = db.messageRecipientInfoDao().getAllNotProcessedByContactIdentity(contact.bytesContactIdentity, contact.bytesOwnedIdentity);
                            App.runThread(() -> {
                                for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage : messageRecipientInfoAndMessages) {
                                    messageRecipientInfoAndMessage.message.repost(messageRecipientInfoAndMessage.messageRecipientInfo);
                                }
                            });

                            // resend all discussion shared ephemeral message settings
                            List<Long> discussionIds = new ArrayList<>();
                            // direct discussion
                            Discussion directDiscussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                            if (directDiscussion != null) {
                                discussionIds.add(directDiscussion.id);
                            }
                            // owned group discussions
                            discussionIds.addAll(db.contactGroupJoinDao().getAllOwnedGroupDiscussionIdsWithSpecificContact(bytesOwnedIdentity, bytesContactIdentity));

                            for (Long discussionId : discussionIds) {
                                if (discussionId == null) {
                                    continue;
                                }
                                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                if (discussionCustomization == null) {
                                    continue;
                                }
                                DiscussionCustomization.JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                                if (jsonSharedSettings != null) {
                                    // send the json to contact
                                    Message message = Message.createDiscussionSettingsUpdateMessage(discussionId, jsonSharedSettings, bytesOwnedIdentity, true, null);
                                    if (message != null) {
                                        message.post(false, true);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.RETURN_RECEIPT_RECEIVED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_BYTES_OWNED_IDENTITY_KEY);
                byte[] serverUid = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY);
                byte[] returnReceiptNonce = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_NONCE_KEY);
                byte[] encryptedPayload = (byte[]) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY);
                Long timestamp = (Long) userInfo.get(EngineNotifications.RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY);
                if (bytesOwnedIdentity == null || serverUid == null || returnReceiptNonce == null || encryptedPayload == null || timestamp == null) {
                    break;
                }

                new HandleReceiveReturnReceipt(engine, bytesOwnedIdentity, serverUid, returnReceiptNonce, encryptedPayload, timestamp).run();
                break;
            }
            case EngineNotifications.NEW_MESSAGE_RECEIVED: {
                ObvMessage obvMessage = (ObvMessage) userInfo.get(EngineNotifications.NEW_MESSAGE_RECEIVED_MESSAGE_KEY);
                if (obvMessage == null) {
                    break;
                }
                new HandleNewMessageNotificationTask(engine, obvMessage).run();
                break;
            }
            case EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_IDENTIFIER_KEY);
                byte[] extendedPayload = (byte[]) userInfo.get(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY);
                if (bytesOwnedIdentity == null || messageIdentifier == null || extendedPayload == null) {
                    break;
                }
                App.runThread(new HandleMessageExtendedPayloadTask(bytesOwnedIdentity, messageIdentifier, extendedPayload));
                break;
            }
            case EngineNotifications.NEW_CONTACT: {
                final byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_OWNED_IDENTITY_KEY);
                final ObvIdentity contactIdentity = (ObvIdentity) userInfo.get(EngineNotifications.NEW_CONTACT_CONTACT_IDENTITY_KEY);
                final Boolean hasUntrustedPublishedDetails = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_HAS_UNTRUSTED_PUBLISHED_DETAILS_KEY);
                if (bytesOwnedIdentity == null || contactIdentity == null || hasUntrustedPublishedDetails == null) {
                    break;
                }
                Contact contact = db.contactDao().get(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                if (contact == null) {
                    db.runInTransaction(() -> {
                        try {
                            Contact createdContact = new Contact(contactIdentity.getBytesIdentity(), bytesOwnedIdentity, contactIdentity.getIdentityDetails(), hasUntrustedPublishedDetails, null, contactIdentity.isKeycloakManaged(), contactIdentity.isActive());
                            if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                                AppSingleton.updateCachedCustomDisplayName(createdContact.bytesContactIdentity, createdContact.getCustomDisplayName());
                                AppSingleton.updateCachedKeycloakManaged(createdContact.bytesContactIdentity, createdContact.keycloakManaged);
                            }
                            db.contactDao().insert(createdContact);
                            Discussion discussion = Discussion.createOneToOneDiscussion(createdContact.getCustomDisplayName(), null, bytesOwnedIdentity, contactIdentity.getBytesIdentity(), createdContact.keycloakManaged, createdContact.active);
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
                                db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), bytesOwnedIdentity, false, 0L));
                            }

                            // insert revoked message if needed
                            if (!contactIdentity.isActive()) {
                                EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                                if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
                                    App.runThread(new InsertContactRevokedMessageTask(bytesOwnedIdentity, contactIdentity.getBytesIdentity()));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    contact = db.contactDao().get(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                    try {
                        contact.deviceCount = engine.getContactDeviceCount(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                        contact.establishedChannelCount = engine.getContactEstablishedChannelsCount(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount);
                }
                break;
            }
            case EngineNotifications.CONTACT_DELETED: {
                final byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DELETED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DELETED_BYTES_CONTACT_IDENTITY_KEY);
                final Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    contact.delete();
                    if (Arrays.equals(contact.bytesContactIdentity, AppSingleton.getBytesCurrentIdentity())) {
                        AppSingleton.reloadCachedDisplayNamesAndHues();
                    }
                }
                break;
            }
            case EngineNotifications.UI_DIALOG: {
                UUID dialogUuid = (UUID) userInfo.get(EngineNotifications.UI_DIALOG_UUID_KEY);
                Invitation existingInvitation = db.invitationDao().getByDialogUuid(dialogUuid);
                ObvDialog dialog = (ObvDialog) userInfo.get(EngineNotifications.UI_DIALOG_DIALOG_KEY);
                Long creationTimestamp = (Long) userInfo.get(EngineNotifications.UI_DIALOG_CREATION_TIMESTAMP_KEY);
                if (dialog == null || creationTimestamp == null) {
                    break;
                }
                switch (dialog.getCategory().getId()) {
                    case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                    case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                    case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                    case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                    case ObvDialog.Category.GROUP_JOINED_DIALOG_CATEGORY: {
                        Invitation invitation = new Invitation(dialog, creationTimestamp);
                        db.invitationDao().insert(invitation);
                        break;
                    }
                    case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                    case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY:
                    case ObvDialog.Category.INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY:
                    case ObvDialog.Category.INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY:
                    case ObvDialog.Category.AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY: {
                        Invitation invitation = new Invitation(dialog, creationTimestamp);
                        db.invitationDao().insert(invitation);
                        // only notify if the invitation is different from the previous notification
                        if ((existingInvitation == null) || (existingInvitation.associatedDialog.getCategory().getId() != invitation.associatedDialog.getCategory().getId())) {
                            AndroidNotificationManager.displayInvitationNotification(invitation);
                        }
                        break;
                    }
                }
                break;
            }
            case EngineNotifications.UI_DIALOG_DELETED: {
                App.runThread(() -> {
                    UUID dialogUuid = (UUID) userInfo.get(EngineNotifications.UI_DIALOG_DELETED_UUID_KEY);
                    Invitation existingInvitation = db.invitationDao().getByDialogUuid(dialogUuid);
                    if (existingInvitation != null) {
                        db.invitationDao().delete(existingInvitation);
                    }
                });
                break;
            }
            case EngineNotifications.NEW_CONTACT_DEVICE: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY);
                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    try {
                        contact.deviceCount = engine.getContactDeviceCount(bytesOwnedIdentity, bytesContactIdentity);
                        db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOADED: {
                final ObvAttachment downloadedAttachment = (ObvAttachment) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOADED_ATTACHMENT_KEY);
                if (downloadedAttachment == null) {
                    break;
                }
                try {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(downloadedAttachment.getBytesOwnedIdentity(), downloadedAttachment.getMessageIdentifier(), downloadedAttachment.getNumber());
                    if (fyleMessageJoinWithStatus != null) {
                        Fyle fyle = db.fyleDao().getById(fyleMessageJoinWithStatus.fyleId);
                        final byte[] sha256 = fyle.sha256;
                        if (sha256 == null) {
                            break;
                        }
                        Fyle.acquireLock(sha256);
                        try {
                            Fyle.SizeAndSha256 sizeAndSha256 = Fyle.computeSHA256FromFile(App.absolutePathFromRelative(downloadedAttachment.getUrl()));
                            if ((sizeAndSha256 == null) || !Arrays.equals(sha256, sizeAndSha256.sha256)) {
                                // OMG, the metadata contained an erroneous sha256!!! Delete everything
                                List<Long> messageIds = db.fyleMessageJoinWithStatusDao().getMessageIdsForFyleSync(fyle.id);
                                if ((messageIds.size() == 1) && (messageIds.get(0) == fyleMessageJoinWithStatus.messageId)) {
                                    fyle.delete();
                                } else {
                                    db.fyleMessageJoinWithStatusDao().delete(fyleMessageJoinWithStatus);
                                }

                                Message message = db.messageDao().get(fyleMessageJoinWithStatus.messageId);
                                if (message != null) {
                                    message.recomputeAttachmentCount(db);
                                    db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, 0, message.imageResolutions);
                                }
                            } else {
                                // the file matches its metadata sha256, move the file to the Fyle directory and mark it as complete
                                fyle.moveToFyleDirectory(App.absolutePathFromRelative(downloadedAttachment.getUrl()));
                                db.fyleDao().update(fyle);

                                // mark the corresponding FyleMessageJoinWithStatus as complete too
                                fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                                fyleMessageJoinWithStatus.progress = 1;
                                //noinspection ConstantConditions
                                fyleMessageJoinWithStatus.filePath = fyle.filePath;
                                db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                                fyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);

                                // check all other FyleMessageJoinWithStatus that are still in STATUS_DOWNLOADABLE or STATUS_DOWNLOADING and "complete" them
                                List<FyleMessageJoinWithStatus> fyleMessageJoinWithStatusList = db.fyleMessageJoinWithStatusDao().getForFyleId(fyle.id);
                                for (FyleMessageJoinWithStatus otherFyleMessageJoinWithStatus : fyleMessageJoinWithStatusList) {
                                    switch (otherFyleMessageJoinWithStatus.status) {
                                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                                        case FyleMessageJoinWithStatus.STATUS_FAILED:
                                            otherFyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                                            otherFyleMessageJoinWithStatus.progress = 1;
                                            otherFyleMessageJoinWithStatus.filePath = fyleMessageJoinWithStatus.filePath;
                                            otherFyleMessageJoinWithStatus.size = fyleMessageJoinWithStatus.size;
                                            db.fyleMessageJoinWithStatusDao().update(otherFyleMessageJoinWithStatus);
                                            otherFyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, null);
                                            engine.deleteAttachment(downloadedAttachment.getBytesOwnedIdentity(), otherFyleMessageJoinWithStatus.engineMessageIdentifier, otherFyleMessageJoinWithStatus.engineNumber);
                                            break;
                                    }
                                }
                            }
                        } finally {
                            Fyle.releaseLock(sha256);
                        }
                    }
                    engine.deleteAttachment(downloadedAttachment);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                Integer attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                Float progress = (Float) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY);
                if (attachmentNumber == null || progress == null) {
                    break;
                }
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, messageIdentifier, attachmentNumber);
                if (fyleMessageJoinWithStatus != null) {
                    db.fyleMessageJoinWithStatusDao().updateProgress(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, progress);
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOADED_ATTACHMENT_NUMBER_KEY);
                if ((engineMessageIdentifier != null) && (engineNumber != null)) {
                    db.runInTransaction(() -> {
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        if (fyleMessageJoinWithStatus != null) {
                            fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                            db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                        }

                        List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                        if (messageRecipientInfos.size() > 0) {
                            long timestamp = System.currentTimeMillis();
                            long messageId = messageRecipientInfos.get(0).messageId;
                            List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                            for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                                if (messageRecipientInfo.markAttachmentSent(engineNumber)) {
                                    if (messageRecipientInfo.unsentAttachmentNumbers == null) {
                                        messageRecipientInfo.timestampSent = timestamp;
                                    }
                                    updatedMessageRecipientInfos.add(messageRecipientInfo);
                                }
                            }

                            if (updatedMessageRecipientInfos.size() > 0) {
                                db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                                Message message = db.messageDao().get(messageId);
                                if (message != null) {
                                    if (message.refreshOutboundStatus()) {
                                        db.messageDao().updateStatus(message.id, message.status);
                                    }
                                }
                            }
                        }
                    });
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY);
                if ((engineMessageIdentifier != null) && (engineNumber != null)) {
                    db.runInTransaction(() -> {
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                        if (fyleMessageJoinWithStatus != null) {
                            fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                            db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                        }

                        List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                        if (messageRecipientInfos.size() > 0) {
                            long timestamp = System.currentTimeMillis();
                            long messageId = messageRecipientInfos.get(0).messageId;
                            List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                            for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                                if (messageRecipientInfo.markAttachmentSent(engineNumber)) {
                                    if (messageRecipientInfo.unsentAttachmentNumbers == null) {
                                        messageRecipientInfo.timestampSent = timestamp;
                                    }
                                    updatedMessageRecipientInfos.add(messageRecipientInfo);
                                }
                            }

                            if (updatedMessageRecipientInfos.size() > 0) {
                                db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                                Message message = db.messageDao().get(messageId);
                                if (message != null) {
                                    if (message.refreshOutboundStatus()) {
                                        db.messageDao().updateStatus(message.id, message.status);
                                    }
                                }
                            }
                        }
                    });
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_IDENTIFIER_KEY);
                Integer engineNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY);
                if ((engineMessageIdentifier != null) && (engineNumber != null)) {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, engineMessageIdentifier, engineNumber);
                    if (fyleMessageJoinWithStatus != null) {
                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_FAILED;
                        db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                    }
                }
                break;
            }

            case EngineNotifications.MESSAGE_UPLOADED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_BYTES_OWNED_IDENTITY_KEY);
                byte[] engineMessageIdentifier = (byte[]) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_IDENTIFIER_KEY);
                Long timestampFromServer = (Long) userInfo.get(EngineNotifications.MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER);

                List<MessageRecipientInfo> messageRecipientInfos = db.messageRecipientInfoDao().getAllByEngineMessageIdentifier(bytesOwnedIdentity, engineMessageIdentifier);
                if (messageRecipientInfos.size() > 0) {
                    long messageId = messageRecipientInfos.get(0).messageId;
                    List<MessageRecipientInfo> updatedMessageRecipientInfos = new ArrayList<>(messageRecipientInfos.size());
                    for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
                        if (messageRecipientInfo.unsentAttachmentNumbers == null) {
                            messageRecipientInfo.timestampSent = timestampFromServer;
                            updatedMessageRecipientInfos.add(messageRecipientInfo);
                        }
                    }

                    if (updatedMessageRecipientInfos.size() > 0) {
                        db.messageRecipientInfoDao().update(updatedMessageRecipientInfos.toArray(new MessageRecipientInfo[0]));

                        Message message = db.messageDao().get(messageId);
                        if (message != null) {
                            if (message.refreshOutboundStatus()) {
                                db.messageDao().updateStatus(message.id, message.status);
                            }
                        }
                    }
                }
                break;
            }
            case EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY);
                byte[] messageIdentifier = (byte[]) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY);
                Integer attachmentNumber = (Integer) userInfo.get(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                Float progress = (Float) userInfo.get(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY);
                if (attachmentNumber == null || progress == null) {
                    break;
                }
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, messageIdentifier, attachmentNumber);
                if (fyleMessageJoinWithStatus != null) {
                    db.fyleMessageJoinWithStatusDao().updateProgress(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, progress);
                }
                break;
            }
            case EngineNotifications.GROUP_CREATED: {
                final ObvGroup obvGroup = (ObvGroup) userInfo.get(EngineNotifications.GROUP_CREATED_GROUP_KEY);
                final Boolean hasMultipleDetails = (Boolean) userInfo.get(EngineNotifications.GROUP_CREATED_HAS_MULTIPLE_DETAILS_KEY);
                final String photoUrl = (String) userInfo.get(EngineNotifications.GROUP_CREATED_PHOTO_URL_KEY);
                if (obvGroup == null || hasMultipleDetails == null) {
                    break;
                }
                final byte[] bytesGroupUid = obvGroup.getBytesGroupOwnerAndUid();

                final String groupName = obvGroup.getGroupDetails().getName();
                final byte[] bytesOwnedIdentity = obvGroup.getBytesOwnedIdentity();

                // create the group, if needed
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
                if (group != null) {
                    // This case should normally never happen
                    Logger.e("Received a GROUP_CREATED notification but the group already exists on the App side.");
                } else {
                    group = new Group(bytesGroupUid, bytesOwnedIdentity, obvGroup.getGroupDetails(), photoUrl, obvGroup.getBytesGroupOwnerIdentity(), hasMultipleDetails);
                    db.groupDao().insert(group);
                }

                // create the discussion if it does not exist
                Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupUid);
                if (discussion == null) {
                    discussion = Discussion.createGroupDiscussion(groupName, photoUrl, bytesOwnedIdentity, bytesGroupUid);
                    discussion.id = db.discussionDao().insert(discussion);

                    // also add default ephemeral settings if you are the group owner
                    if (obvGroup.getBytesGroupOwnerIdentity() == null) {
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
                            db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), bytesOwnedIdentity, false, 0L));
                        }
                    }
                }

                // add members
                boolean messageInserted = false;
                for (byte[] bytesGroupMemberIdentity : obvGroup.getBytesGroupMembersIdentities()) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesGroupMemberIdentity);
                    if (contact != null) {
                        ContactGroupJoin contactGroupJoin = new ContactGroupJoin(bytesGroupUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                        db.contactGroupJoinDao().insert(contactGroupJoin);
                        Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(discussion.id, contact.bytesContactIdentity);
                        db.messageDao().insert(groupJoinedMessage);
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
                    PendingGroupMember pendingGroupMember = new PendingGroupMember(obvPendingGroupMember.getBytesIdentity(), obvPendingGroupMember.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), bytesOwnedIdentity, bytesGroupUid, declinedSet.contains(new BytesKey(obvPendingGroupMember.getBytesIdentity())));
                    db.pendingGroupMemberDao().insert(pendingGroupMember);
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
            case EngineNotifications.GROUP_MEMBER_ADDED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || bytesGroupUid == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
                if (group != null) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupUid);
                        if (discussion == null) {
                            // this case should never happen
                            discussion = Discussion.createGroupDiscussion(group.getCustomName(), group.getCustomPhotoUrl(), bytesOwnedIdentity, bytesGroupUid);
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
                                    db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), bytesOwnedIdentity, false, 0L));
                                }
                            }
                        }
                        ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(bytesGroupUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                        if (contactGroupJoin == null) {
                            contactGroupJoin = new ContactGroupJoin(bytesGroupUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            db.contactGroupJoinDao().insert(contactGroupJoin);
                            Message groupJoinedMessage = Message.createMemberJoinedGroupMessage(discussion.id, contact.bytesContactIdentity);
                            db.messageDao().insert(groupJoinedMessage);
                            if (discussion.updateLastMessageTimestamp(System.currentTimeMillis())) {
                                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                            AndroidNotificationManager.displayGroupMemberNotification(group, contact, true, discussion.id);
                        }

                        // resend all UNPROCESSED_MESSAGES for this group
                        List<Message> unprocessedMessages = db.messageDao().getUnprocessedAndPreviewingGroupMessages(group.bytesGroupOwnerAndUid, group.bytesOwnedIdentity);
                        for (Message message: unprocessedMessages) {
                            message.post(false, false);
                        }

                        // if you are the group owner, check if the group discussion has some shared settings, and resend them
                        if (group.bytesGroupOwnerIdentity == null) {
                            DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                            if (discussionCustomization != null) {
                                DiscussionCustomization.JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                                if (jsonSharedSettings != null) {
                                    Message message = Message.createDiscussionSettingsUpdateMessage(discussion.id, jsonSharedSettings, bytesOwnedIdentity, true, null);
                                    if (message != null) {
                                        message.post(false, true);
                                    }
                                }
                            }
                        }
                    } else {
                        Logger.w("Contact not found while processing a \"Group Member Added\" notification.");
                    }
                } else {
                    Logger.i("Trying to add group member to a non-existing (yet) group");
                }
                break;
            }
            case EngineNotifications.GROUP_MEMBER_REMOVED: {
                byte[] bytesGroupUid = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_CONTACT_IDENTITY_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || bytesGroupUid == null) {
                    break;
                }
                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupUid);
                if (group != null) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupUid);
                        if (discussion == null) {
                            // this case should never happen
                            discussion = Discussion.createGroupDiscussion(group.getCustomName(), group.getCustomPhotoUrl(), bytesOwnedIdentity, bytesGroupUid);
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
                                    db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(discussion.id, discussionCustomization.getSharedSettingsJson(), bytesOwnedIdentity, false, 0L));
                                }
                            }
                        }
                        ContactGroupJoin contactGroupJoin = db.contactGroupJoinDao().get(bytesGroupUid, contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                        if (contactGroupJoin != null) {
                            db.contactGroupJoinDao().delete(contactGroupJoin);
                            Message groupLeftMessage = Message.createMemberLeftGroupMessage(discussion.id, contact.bytesContactIdentity);
                            db.messageDao().insert(groupLeftMessage);
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
                    PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(obvIdentity.getBytesIdentity(), bytesOwnedIdentity, groupId);
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
                    PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(obvIdentity.getBytesIdentity(), bytesOwnedIdentity, bytesGroupUid);
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
                PendingGroupMember pendingGroupMember = db.pendingGroupMemberDao().get(bytesContactIdentity, bytesOwnedIdentity, bytesGroupUid);
                if (pendingGroupMember != null) {
                    pendingGroupMember.declined = declined;
                    db.pendingGroupMemberDao().update(pendingGroupMember);
                }
                break;
            }
            case EngineNotifications.API_KEY_ACCEPTED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_OWNED_IDENTITY_KEY);
                EngineAPI.ApiKeyStatus apiKeyStatus = (EngineAPI.ApiKeyStatus) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY);
                @SuppressWarnings("unchecked")
                List<EngineAPI.ApiKeyPermission> apiKeyPermissions = (List<EngineAPI.ApiKeyPermission>) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_PERMISSIONS_KEY);
                Long apiKeyExpirationTimestamp = (Long) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (bytesOwnedIdentity != null && apiKeyPermissions != null && apiKeyStatus != null) {
                    OwnedIdentity identity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                    if (identity != null) {
                        boolean changed = false;
                        if (identity.getApiKeyStatus() != apiKeyStatus) {
                            changed = true;
                            identity.setApiKeyStatus(apiKeyStatus);
                        }
                        if (!Objects.equals(identity.apiKeyExpirationTimestamp, apiKeyExpirationTimestamp)) {
                            changed = true;
                            identity.apiKeyExpirationTimestamp = apiKeyExpirationTimestamp;
                        }
                        long oldPermissions = identity.apiKeyPermissions;
                        identity.setApiKeyPermissions(apiKeyPermissions);
                        if (oldPermissions != identity.apiKeyPermissions) {
                            changed = true;
                        }
                        if (changed) {
                            db.ownedIdentityDao().updateApiKey(identity.bytesOwnedIdentity, identity.apiKeyStatus, identity.apiKeyPermissions, identity.apiKeyExpirationTimestamp);
                            App.openAppDialogApiKeyPermissionsUpdated(identity);
                        }
                    }
                }
                break;
            }
//            case EngineNotifications.API_KEY_REJECTED: {
//                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_REJECTED_OWNED_IDENTITY_KEY);
//                if (bytesOwnedIdentity != null) {
//                    OwnedIdentity identity = db.ownedIdentityDao().get(bytesOwnedIdentity);
//                    if (identity != null && identity.apiKeyStatus != OwnedIdentity.API_KEY_STATUS_INVALID) {
//                        identity.apiKeyStatus = OwnedIdentity.API_KEY_STATUS_INVALID;
//                        db.ownedIdentityDao().update(identity);
//                    }
//                }
//                break;
//            }
            case EngineNotifications.OWNED_IDENTITY_LIST_UPDATED: {
                App.runThread(() -> {
                    // only check for ownedIdentityManaged status change, do not handle insertions or deletions here
                    try {
                        ObvIdentity[] engineOwnedIdentities = AppSingleton.getEngine().getOwnedIdentities();
                        Map<BytesKey, ObvIdentity> engineOwnedIdentitiesMap = new HashMap<>();
                        for (ObvIdentity obvIdentity: engineOwnedIdentities) {
                            engineOwnedIdentitiesMap.put(new BytesKey(obvIdentity.getBytesIdentity()), obvIdentity);
                        }


                        List<OwnedIdentity> appOwnedIdentities = AppDatabase.getInstance().ownedIdentityDao().getAll();
                        for (OwnedIdentity appOwnedIdentity: appOwnedIdentities) {
                            ObvIdentity obvIdentity = engineOwnedIdentitiesMap.get(new BytesKey(appOwnedIdentity.bytesOwnedIdentity));
                            if (obvIdentity != null) {
                                // check whether managed status is the same
                                if (obvIdentity.isKeycloakManaged() != appOwnedIdentity.keycloakManaged) {
                                    appOwnedIdentity.keycloakManaged = obvIdentity.isKeycloakManaged();
                                    AppDatabase.getInstance().ownedIdentityDao().updateKeycloakManaged(appOwnedIdentity.bytesOwnedIdentity, appOwnedIdentity.keycloakManaged);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // do nothing --> will be done at next restart
                    }
                });
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                JsonIdentityDetails identityDetails = (JsonIdentityDetails) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_IDENTITY_DETAILS_KEY);
                String photoUrl = (String) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_PHOTO_URL_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && identityDetails != null) {
                    try {
                        ownedIdentity.setIdentityDetails(identityDetails);
                        ownedIdentity.photoUrl = photoUrl;
                        db.ownedIdentityDao().updateIdentityDetailsAndPhoto(ownedIdentity.bytesOwnedIdentity, ownedIdentity.identityDetails, ownedIdentity.displayName, ownedIdentity.photoUrl);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // failed, but we do nothing, this will be done again at next app startup
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY);

                if (identityDetails != null) {
                    App.runThread(new UpdateContactDisplayNameAndPhotoTask(bytesContactIdentity, bytesOwnedIdentity, identityDetails));
                }
                break;
            }
            case EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean keycloakManaged = (Boolean) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY);
                if (bytesOwnedIdentity == null || bytesContactIdentity == null || keycloakManaged == null) {
                    break;
                }

                App.runThread(new UpdateContactKeycloakManagedTask(bytesOwnedIdentity, bytesContactIdentity, keycloakManaged));
                break;
            }
            case EngineNotifications.CONTACT_ACTIVE_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean active = (Boolean) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_ACTIVE_KEY);
                if (bytesOwnedIdentity == null || bytesContactIdentity == null || active == null) {
                    break;
                }

                App.runThread(new UpdateContactActiveTask(bytesOwnedIdentity, bytesContactIdentity, active));
                break;
            }
            case EngineNotifications.CONTACT_REVOKED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_REVOKED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_REVOKED_BYTES_CONTACT_IDENTITY_KEY);

                App.runThread(new InsertContactRevokedMessageTask(bytesOwnedIdentity, bytesContactIdentity));
                break;
            }
            case EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY);

                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    try {
                        contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_UNSEEN;
                        db.contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);
                        Discussion discussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                        if (discussion != null) {
                            Message newDetailsMessage = Message.createNewPublishedDetailsMessage(discussion.id, bytesContactIdentity);
                            db.messageDao().insert(newDetailsMessage);
                            if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.NEW_CONTACT_PHOTO: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_VERSION_KEY);
                Boolean isTrusted = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY);
                if (version == null || isTrusted == null) {
                    break;
                }

                if (isTrusted) {
                    try {
                        JsonIdentityDetailsWithVersionAndPhoto[] jsons = engine.getContactPublishedAndTrustedDetails(bytesOwnedIdentity, bytesContactIdentity);
                        if (jsons[jsons.length-1].getVersion() == version) {
                            Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                            if (contact != null) {
                                App.runThread(new UpdateContactDisplayNameAndPhotoTask(bytesContactIdentity, bytesOwnedIdentity, jsons[jsons.length-1]));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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

                Group group = db.groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                if (group != null) {
                    try {
                        group.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_UNSEEN;
                        db.groupDao().updatePublishedDetailsStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.newPublishedDetails);
                        Discussion discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, bytesGroupOwnerAndUid);
                        if (discussion != null) {
                            Message newDetailsMessage = Message.createNewPublishedDetailsMessage(discussion.id, group.bytesGroupOwnerIdentity);
                            db.messageDao().insert(newDetailsMessage);
                            if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY);
                Boolean hasUnpublished = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && hasUnpublished != null) {
                    if (hasUnpublished && ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                        ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                        db.ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                    } else if (!hasUnpublished && ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_EXIST) {
                        ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW;
                        db.ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                    }
                }
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                Boolean active = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && active != null) {
                    ownedIdentity.active = active;
                    db.ownedIdentityDao().updateActive(ownedIdentity.bytesOwnedIdentity, ownedIdentity.active);

                    AppSingleton.markIdentityActive(ownedIdentity, active);
                }
                break;
            }
            case EngineNotifications.BACKUP_FINISHED: {
                byte[] backupKeyUid = (byte[]) userInfo.get(EngineNotifications.BACKUP_FINISHED_BYTES_BACKUP_KEY_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.BACKUP_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(EngineNotifications.BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid != null && version != null && encryptedContent != null
                        && SettingsActivity.useAutomaticBackup()) {
                    GoogleDriveService.uploadBackupToDrive(encryptedContent, () -> engine.markBackupUploaded(backupKeyUid, version));
                }
                break;
            }
            case EngineNotifications.APP_BACKUP_REQUESTED: {
                byte[] backupKeyUid = (byte[]) userInfo.get(EngineNotifications.APP_BACKUP_REQUESTED_BYTES_BACKUP_KEY_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.APP_BACKUP_REQUESTED_VERSION_KEY);

                if (backupKeyUid != null && version != null) {
                    App.runThread(new BackupAppDataForEngineTask(backupKeyUid, version));
                }
                break;
            }
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS: {
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY);
                //noinspection ConstantConditions
                boolean updated = (boolean) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY);

                if (appInfo != null) {
                    if (updated) {
                        Integer latest = appInfo.get("latest_android");
                        if (latest != null && latest > BuildConfig.VERSION_CODE) {
                            App.openAppDialogNewVersionAvailable();
                        }
                    } else {
                        Integer min = appInfo.get("min_android");
                        if (min != null && min > BuildConfig.VERSION_CODE) {
                            App.openAppDialogOutdatedVersion();
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
