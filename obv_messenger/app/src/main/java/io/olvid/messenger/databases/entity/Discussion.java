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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.UnreadCountsSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.ExpiringOutboundMessageSent;
import io.olvid.messenger.databases.tasks.InsertContactRevokedMessageTask;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(tableName = Discussion.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = Discussion.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE),
        },
        indices = {
                @Index(Discussion.BYTES_OWNED_IDENTITY),
                @Index(value = {Discussion.BYTES_OWNED_IDENTITY, Discussion.DISCUSSION_TYPE, Discussion.BYTES_DISCUSSION_IDENTIFIER}, unique = true),
                @Index(Discussion.TITLE),
        }
)
public class Discussion {
    public static final String TABLE_NAME = "discussion_table";

    public static final String TITLE = "title";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String DISCUSSION_TYPE = "discussion_type"; // discussion type can be: contact, group, groupV2
    public static final String BYTES_DISCUSSION_IDENTIFIER = "bytes_discussion_identifier"; // the bytes_contact_identity or bytes_group_owner_and_uid or bytes_groupV2_identifier, depending on discussion type
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER = "last_outbound_message_sequence_number";
    public static final String LAST_MESSAGE_TIMESTAMP = "last_message_timestamp";
    public static final String LAST_REMOTE_DELETE_TIMESTAMP = "last_remote_delete_timestamp";
    public static final String PHOTO_URL = "photo_url";
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";
    public static final String PINNED = "pinned";
    public static final String UNREAD = "unread"; // specify if discussion as been manually marked as unread
    public static final String ACTIVE = "active";
    public static final String ARCHIVED = "archived";
    public static final String TRUST_LEVEL = "trust_level";
    public static final String STATUS = "status"; // normal, locked, or pre-discussion

    public static final int TYPE_CONTACT = 1;
    public static final int TYPE_GROUP = 2;
    public static final int TYPE_GROUP_V2 = 3;

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_LOCKED = 2;
    public static final int STATUS_PRE_DISCUSSION = 3;
    public static final int STATUS_READ_ONLY = 4;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = TITLE)
    @Nullable
    public String title;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = DISCUSSION_TYPE)
    public int discussionType;

    @ColumnInfo(name = BYTES_DISCUSSION_IDENTIFIER)
    @NonNull
    public byte[] bytesDiscussionIdentifier;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER)
    public long lastOutboundMessageSequenceNumber;

    @ColumnInfo(name = LAST_MESSAGE_TIMESTAMP)
    public long lastMessageTimestamp;

    @ColumnInfo(name = LAST_REMOTE_DELETE_TIMESTAMP)
    public long lastRemoteDeleteTimestamp;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = KEYCLOAK_MANAGED)
    public boolean keycloakManaged;

    // specify if discussion as been manually marked as unread
    @ColumnInfo(name = UNREAD)
    public boolean unread;

    @ColumnInfo(name = PINNED)
    public int pinned;

    @ColumnInfo(name = ARCHIVED)
    public boolean archived;

    @ColumnInfo(name = ACTIVE)
    public boolean active;

    @ColumnInfo(name = TRUST_LEVEL)
    @Nullable
    public Integer trustLevel;

    @ColumnInfo(name = STATUS)
    public int status;

    // default constructor required by Room
    public Discussion(@Nullable String title, @NonNull byte[] bytesOwnedIdentity, int discussionType, @NonNull byte[] bytesDiscussionIdentifier, @NonNull UUID senderThreadIdentifier, long lastOutboundMessageSequenceNumber, long lastMessageTimestamp, @Nullable String photoUrl, boolean keycloakManaged, boolean unread, int pinned, boolean archived, boolean active, @Nullable Integer trustLevel, int status) {
        this.title = title;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.discussionType = discussionType;
        this.bytesDiscussionIdentifier = bytesDiscussionIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.lastOutboundMessageSequenceNumber = lastOutboundMessageSequenceNumber;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.photoUrl = photoUrl;
        this.keycloakManaged = keycloakManaged;
        this.unread = unread;
        this.pinned = pinned;
        this.archived = archived;
        this.active = active;
        this.trustLevel = trustLevel;
        this.status = status;
    }


    @Ignore
    public static Discussion createOrReuseOneToOneDiscussion(@NonNull AppDatabase db, @NonNull Contact contact) {
        if (!db.inTransaction()) {
            Logger.e("ERROR: running discussion createOrReuseOneToOneDiscussion outside a transaction");
            return null;
        }

        // check if a discussion already exists
        Discussion discussion = db.discussionDao().getByContactWithAnyStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity);

        if (discussion == null) {
            // no existing discussion --> create a new one
            discussion = new Discussion(
                    contact.getCustomDisplayName(),
                    contact.bytesOwnedIdentity,
                    TYPE_CONTACT,
                    contact.bytesContactIdentity,
                    UUID.randomUUID(),
                    0,
                    System.currentTimeMillis(),
                    contact.getCustomPhotoUrl(),
                    contact.keycloakManaged,
                    false,
                    0,
                    false,
                    contact.active,
                    contact.trustLevel,
                    STATUS_NORMAL
            );
            discussion.id = db.discussionDao().insert(discussion);

            // set default ephemeral message settings
            setDefaultEphemeralMessageSettings(discussion, db, contact.bytesOwnedIdentity);

            // insert revoked message if needed
            if (!contact.active) {
                EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
                    App.runThread(new InsertContactRevokedMessageTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity));
                }
            }
        } else if (!discussion.isNormal()) {
            if (discussion.isLocked()) {
                Message contactReAdded = Message.createContactReAddedMessage(db, discussion.id, contact.bytesContactIdentity);
                db.messageDao().insert(contactReAdded);
            }

            if (discussion.isPreDiscussion()) {
                // set default ephemeral message settings
                setDefaultEphemeralMessageSettings(discussion, db, contact.bytesOwnedIdentity);
            }

            discussion.title = contact.getCustomDisplayName();
            discussion.lastMessageTimestamp = System.currentTimeMillis();
            discussion.photoUrl = contact.getCustomPhotoUrl();
            discussion.keycloakManaged = contact.keycloakManaged;
            discussion.active = contact.active;
            discussion.trustLevel = contact.trustLevel;
            discussion.status = STATUS_NORMAL;
            db.discussionDao().updateAll(discussion);

            ShortcutActivity.updateShortcut(discussion);

            // insert revoked message if needed
            if (!contact.active) {
                EnumSet<ObvContactActiveOrInactiveReason> reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED)) {
                    App.runThread(new InsertContactRevokedMessageTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity));
                }
            }
        }

        // if we have a channel with the contact, post a shared settings message
        if (contact.hasChannelOrPreKey()) {
            try {
                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                if (discussionCustomization != null) {
                    JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                    if (jsonSharedSettings != null) {
                        Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, contact.bytesOwnedIdentity, true, null);
                        if (message != null) {
                            message.postSettingsMessage(true, null);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return discussion;
    }

    private static void setDefaultEphemeralMessageSettings(Discussion discussion, @NonNull AppDatabase db, @NonNull byte[] contact) {
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
            db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(db, discussion.id, discussionCustomization.getSharedSettingsJson(), contact, false, 0L));
        }
    }

    private static void setEphemeralMessageSettings(Discussion discussion, @NonNull AppDatabase db, @NonNull byte[] contact, @NonNull JsonExpiration jsonExpiration) {
        Long existenceDuration = jsonExpiration.getExistenceDuration();
        Long visibilityDuration = jsonExpiration.getVisibilityDuration();
        boolean readOnce = jsonExpiration.getReadOnce();
        if (readOnce || visibilityDuration != null || existenceDuration != null) {
            DiscussionCustomization discussionCustomization = new DiscussionCustomization(discussion.id);
            discussionCustomization.settingExistenceDuration = existenceDuration;
            discussionCustomization.settingVisibilityDuration = visibilityDuration;
            discussionCustomization.settingReadOnce = readOnce;
            discussionCustomization.sharedSettingsVersion = 0;
            db.discussionCustomizationDao().insert(discussionCustomization);
            db.messageDao().insert(Message.createDiscussionSettingsUpdateMessage(db, discussion.id, discussionCustomization.getSharedSettingsJson(), contact, false, 0L));
        }
    }

    @Ignore
    public static Discussion createOrReuseGroupDiscussion(AppDatabase db, @NonNull Group group, boolean createdByMeOnOtherDevice) {
        if (!db.inTransaction()) {
            Logger.e("ERROR: running discussion createOrReuseGroupDiscussion outside a transaction");
            return null;
        }

        // check if a discussion already exists
        Discussion discussion = db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);

        if (discussion == null) {
            discussion = new Discussion(
                    group.getCustomName(),
                    group.bytesOwnedIdentity,
                    TYPE_GROUP,
                    group.bytesGroupOwnerAndUid,
                    UUID.randomUUID(),
                    0,
                    System.currentTimeMillis(),
                    group.getCustomPhotoUrl(),
                    false,
                    false,
                    0,
                    false,
                    true,
                    null,
                    STATUS_NORMAL
            );
            discussion.id = db.discussionDao().insert(discussion);

            if (group.bytesGroupOwnerIdentity == null && !createdByMeOnOtherDevice) {
                setDefaultEphemeralMessageSettings(discussion, db, group.bytesOwnedIdentity);
            }
        } else if (!discussion.isNormal()) {
            if (discussion.isLocked()) {
                Message reJoinedGroup = Message.createReJoinedGroupMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                db.messageDao().insert(reJoinedGroup);
            }

            discussion.title = group.getCustomName();
            discussion.lastMessageTimestamp = System.currentTimeMillis();
            discussion.photoUrl = group.getCustomPhotoUrl();
            discussion.keycloakManaged = false;
            discussion.active = true;
            discussion.trustLevel = null;
            discussion.status = STATUS_NORMAL;
            db.discussionDao().updateAll(discussion);

            ShortcutActivity.updateShortcut(discussion);
        }

        return discussion;
    }

    @Ignore
    public static Discussion createOrReuseGroupV2Discussion(AppDatabase db, @NonNull Group2 group2, boolean groupWasJustCreatedByMe, boolean createdOnOtherDevice, JsonExpiration jsonExpirationSettings) {
        if (!db.inTransaction()) {
            Logger.e("ERROR: running discussion createOrReuseGroupV2Discussion outside a transaction");
            return null;
        }

        // check if a discussion already exists
        Discussion discussion = db.discussionDao().getByGroupIdentifierWithAnyStatus(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);

        if (discussion == null) {
            discussion = new Discussion(
                    group2.getCustomName(),
                    group2.bytesOwnedIdentity,
                    TYPE_GROUP_V2,
                    group2.bytesGroupIdentifier,
                    UUID.randomUUID(),
                    0,
                    System.currentTimeMillis(),
                    group2.getCustomPhotoUrl(),
                    group2.keycloakManaged,
                    false,
                    0,
                    false,
                    true,
                    null,
                    group2.ownPermissionSendMessage ? STATUS_NORMAL : STATUS_READ_ONLY
            );
            discussion.id = db.discussionDao().insert(discussion);

            // if the is a group we just created, we should apply ephemeral settings
            if (groupWasJustCreatedByMe) {
                if (jsonExpirationSettings != null) {
                    setEphemeralMessageSettings(discussion, db, group2.bytesOwnedIdentity, jsonExpirationSettings);
                } else if (!createdOnOtherDevice) {
                    setDefaultEphemeralMessageSettings(discussion, db, group2.bytesOwnedIdentity);
                }
            } else {
                Message reJoinedGroup = Message.createJoinedGroupMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                db.messageDao().insert(reJoinedGroup);
            }
        } else if (!discussion.isNormal()) {
            if (discussion.isLocked()) {
                Message reJoinedGroup = Message.createReJoinedGroupMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                db.messageDao().insert(reJoinedGroup);
            }

            discussion.title = group2.getCustomName();
            discussion.lastMessageTimestamp = System.currentTimeMillis();
            discussion.photoUrl = group2.getCustomPhotoUrl();
            discussion.keycloakManaged = group2.keycloakManaged;
            discussion.active = true;
            discussion.trustLevel = null;
            discussion.status = group2.ownPermissionSendMessage ? STATUS_NORMAL : STATUS_READ_ONLY;
            db.discussionDao().updateAll(discussion);

            ShortcutActivity.updateShortcut(discussion);
        }

        return discussion;
    }


    public boolean isLocked() {
        return status == STATUS_LOCKED;
    }

    public boolean isNormal() {
        return status == STATUS_NORMAL;
    }

    public boolean isPreDiscussion() { return status == STATUS_PRE_DISCUSSION; }

    public boolean isReadOnly() { return status == STATUS_READ_ONLY; }

    public boolean isNormalOrReadOnly() {
        return status == STATUS_NORMAL || status == STATUS_READ_ONLY;
    }

    public boolean updateLastMessageTimestamp(long lastMessageTimestamp) {
        if (lastMessageTimestamp > this.lastMessageTimestamp) {
            this.lastMessageTimestamp = lastMessageTimestamp;
            return true;
        }
        return false;
    }

    public void lockWithMessage(AppDatabase db) {
        if (!db.inTransaction()) {
            Logger.e("ERROR: running discussion lockWithMessage outside a transaction");
        }

        if (!isNormalOrReadOnly()) {
            Logger.w("Locking a discussion which cannot be locked");
            return;
        }

        // delete draft messages
        db.messageDao().deleteDiscussionDraftMessage(id);
        List<Message> messageList = db.messageDao().getAllDiscussionNewPublishedDetailsMessages(id);
        db.messageDao().delete(messageList.toArray(new Message[0]));
        UnreadCountsSingleton.INSTANCE.messageBatchDeleted(messageList);

        HashSet<Long> messageIdsAlreadyMarkedUndelivered = new HashSet<>();

        for (MessageRecipientInfo messageRecipientInfo : db.messageRecipientInfoDao().getAllUnsentForDiscussion(id)) {
            messageRecipientInfo.timestampSent = 0L;
            db.messageRecipientInfoDao().update(messageRecipientInfo);

            // if message was already treated, do nothing for the recipient info
            if (messageIdsAlreadyMarkedUndelivered.contains(messageRecipientInfo.messageId)) {
                continue;
            }

            messageIdsAlreadyMarkedUndelivered.add(messageRecipientInfo.messageId);

            Message message = db.messageDao().get(messageRecipientInfo.messageId);
            if (message != null && message.messageType == Message.TYPE_OUTBOUND_MESSAGE) {
                if (messageRecipientInfo.engineMessageIdentifier != null) {
                    try {
                        UnifiedForegroundService.processUploadedMessageIdentifier(messageRecipientInfo.engineMessageIdentifier);
                        AppSingleton.getEngine().cancelMessageSending(bytesOwnedIdentity, messageRecipientInfo.engineMessageIdentifier);
                    } catch (Exception ignored) {
                    }
                }
                message.status = Message.STATUS_UNDELIVERED;
                db.messageDao().updateStatus(message.id, message.status);
                db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_UNDELIVERED, System.currentTimeMillis()));
                if (message.jsonExpiration != null) {
                    App.runThread(new ExpiringOutboundMessageSent(message));
                }
            }
        }

        if (db.messageDao().countMessagesInDiscussion(id) == 0) {
            // discussion is empty and will be locked --> delete it
            db.discussionDao().delete(this);
            ShortcutActivity.disableShortcut(id);
        } else {
            // clear the LatestDiscussionSenderSequenceNumber to avoid "xx messages missing" if you re-join this discussion
            db.latestDiscussionSenderSequenceNumberDao().deleteForDiscussion(id);

            // the discussion has messages, do not delete it and insert a discussion left message
            switch (discussionType) {
                case TYPE_CONTACT: {
                    Message contactDeletedMessage = Message.createContactDeletedMessage(db, id, bytesDiscussionIdentifier);
                    db.messageDao().insert(contactDeletedMessage);
                    break;
                }
                case TYPE_GROUP:
                case TYPE_GROUP_V2: {
                    Message groupLeftMessage = Message.createLeftGroupMessage(db, id, bytesOwnedIdentity);
                    db.messageDao().insert(groupLeftMessage);
                    break;
                }
                default:
                    return;
            }

            // copy the discussion photoUrl to customPhotos folder, unless it is already in there :)
            if (photoUrl != null && !photoUrl.startsWith(AppSingleton.CUSTOM_PHOTOS_DIRECTORY)) {
                try {
                    int i = 0;
                    String relativeOutputPath;
                    do {
                        relativeOutputPath = AppSingleton.CUSTOM_PHOTOS_DIRECTORY + File.separator + Logger.getUuidString(UUID.randomUUID());
                        i++;
                    } while (i < 10 && new File(App.absolutePathFromRelative(relativeOutputPath)).exists());

                    // move or copy file
                    File oldFile = new File(App.absolutePathFromRelative(photoUrl));
                    File newFile = new File(App.absolutePathFromRelative(relativeOutputPath));
                    // do not rename (as the contact may simply be not oneToOne) always copy.
                    try (FileInputStream fileInputStream = new FileInputStream(oldFile); FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fileInputStream.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, c);
                        }
                    }
                    photoUrl = relativeOutputPath;
                } catch (Exception e) {
                    e.printStackTrace();
                    photoUrl = null;
                }
                db.discussionDao().updateTitleAndPhotoUrl(id, title, photoUrl);
            }

            // lock the discussion
            status = STATUS_LOCKED;
            keycloakManaged = false;
            active = true;
            trustLevel = null;

            db.discussionDao().updateAsLocked(id);

            // stop sharing location in this discussion
            if (UnifiedForegroundService.LocationSharingSubService.isDiscussionSharingLocation(id)) {
                UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(id, true);
            }
            // also mark as ended all currently sharing location messages
            for (Message message : db.messageDao().getCurrentlySharingInboundLocationMessagesInDiscussion(id)) {
                db.messageDao().updateLocationType(message.id, Message.LOCATION_TYPE_SHARE_FINISHED);
                UnreadCountsSingleton.INSTANCE.removeLocationSharingMessage(message.discussionId, message.id);
            }

            ShortcutActivity.updateShortcut(this);
        }
    }
}
