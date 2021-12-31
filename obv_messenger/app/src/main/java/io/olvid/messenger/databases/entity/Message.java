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

package io.olvid.messenger.databases.entity;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.tasks.ComputeAttachmentPreviewsAndPostMessageTask;
import io.olvid.messenger.databases.tasks.ExpiringOutboundMessageSent;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = Message.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = Discussion.class,
                                  parentColumns = "id",
                                  childColumns = Message.DISCUSSION_ID,
                                  onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(Message.DISCUSSION_ID),
                @Index(Message.ENGINE_MESSAGE_IDENTIFIER),
                @Index(Message.SORT_INDEX),
                @Index(Message.TIMESTAMP),
                @Index(value = {Message.MESSAGE_TYPE, Message.STATUS}),
                @Index(value = {Message.DISCUSSION_ID, Message.STATUS}),
                @Index(value = {Message.SENDER_SEQUENCE_NUMBER, Message.SENDER_THREAD_IDENTIFIER, Message.SENDER_IDENTIFIER, Message.DISCUSSION_ID})
        }
)
public class Message {
    public static final String TABLE_NAME = "message_table";

    public static final String SENDER_SEQUENCE_NUMBER = "sender_sequence_number";
    public static final String CONTENT_BODY = "content_body";
    public static final String JSON_REPLY = "json_reply";
    public static final String JSON_EXPIRATION = "json_expiration"; // for inbound messages, this is null unless it is an ephemeral message
    public static final String JSON_RETURN_RECEIPT = "json_return_receipt";
    public static final String SORT_INDEX = "sort_index";
    public static final String TIMESTAMP = "timestamp";
    public static final String STATUS = "status";
    public static final String WIPE_STATUS = "wipe_status";
    public static final String MESSAGE_TYPE = "message_type";
    public static final String DISCUSSION_ID = "discussion_id";
    public static final String ENGINE_MESSAGE_IDENTIFIER = "engine_message_identifier";
    public static final String SENDER_IDENTIFIER = "sender_identifier";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String TOTAL_ATTACHMENT_COUNT = "total_attachment_count";
    public static final String IMAGE_COUNT = "image_count";
    public static final String WIPED_ATTACHMENT_COUNT = "wiped_attachment_count";
    public static final String EDITED = "edited";
    public static final String REACTIONS = "reactions";
    public static final String IMAGE_RESOLUTIONS = "image_resolutions"; // null or "" = no images, "102x234;a340x445" = 2 images, second one is animated
    public static final String MISSED_MESSAGE_COUNT = "missed_message_count"; // only used in inbound messages: the number of missing sequence numbers when this message is received. 0 --> everything is fine


    // This enum is used in protobuf for webclient, please send notification if you modify anything
    public static final int STATUS_UNPROCESSED = 0; // message not passed to the engine yes
    public static final int STATUS_PROCESSING = 1; // message passed to the Engine (at least for some recipients)
    public static final int STATUS_SENT = 2; // message sent to ALL recipients
    public static final int STATUS_UNREAD = 3;
    public static final int STATUS_READ = 4;
    public static final int STATUS_DRAFT = 5;
    public static final int STATUS_DELIVERED = 6;
    public static final int STATUS_DELIVERED_AND_READ = 7;
    public static final int STATUS_COMPUTING_PREVIEW = 8; // computing a preview of the image/video attachments before passing to engine


    public static final int WIPE_STATUS_NONE = 0;
    public static final int WIPE_STATUS_WIPE_ON_READ = 1;
    public static final int WIPE_STATUS_WIPED = 2;
    public static final int WIPE_STATUS_REMOTE_DELETED = 3;

    public static final int RETURN_RECEIPT_STATUS_DELIVERED = 1;
    public static final int RETURN_RECEIPT_STATUS_READ = 2;

    // This enum is used in protobuf for webclient, please send notification if you modify anything
    public static final int TYPE_INBOUND_MESSAGE = 0;
    public static final int TYPE_OUTBOUND_MESSAGE = 1;
    public static final int TYPE_GROUP_MEMBER_JOINED = 2;
    public static final int TYPE_GROUP_MEMBER_LEFT = 3;
    public static final int TYPE_LEFT_GROUP = 4;
    public static final int TYPE_CONTACT_DELETED = 5;
    public static final int TYPE_INBOUND_EPHEMERAL_MESSAGE = 6;
    public static final int TYPE_DISCUSSION_SETTINGS_UPDATE = 7;
    public static final int TYPE_DISCUSSION_REMOTELY_DELETED = 8;
    public static final int TYPE_PHONE_CALL = 9;
    public static final int TYPE_NEW_PUBLISHED_DETAILS = 10;
    public static final int TYPE_CONTACT_INACTIVE_REASON = 11;


    public static final int EDITED_NONE = 0;
    public static final int EDITED_UNSEEN = 1;
    public static final int EDITED_SEEN = 2;

    public static final String NOT_ACTIVE_REASON_REVOKED = "revoked";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = SENDER_SEQUENCE_NUMBER)
    public long senderSequenceNumber;

    @ColumnInfo(name = CONTENT_BODY)
    @Nullable
    public String contentBody;

    @ColumnInfo(name = JSON_REPLY)
    @Nullable
    public String jsonReply;

    @ColumnInfo(name = JSON_EXPIRATION)
    @Nullable
    public String jsonExpiration;

    @ColumnInfo(name = JSON_RETURN_RECEIPT)
    @Nullable
    public String jsonReturnReceipt;

    @ColumnInfo(name = SORT_INDEX)
    public double sortIndex;

    @ColumnInfo(name = TIMESTAMP)
    public long timestamp;

    @ColumnInfo(name = STATUS)
    public int status;

    @ColumnInfo(name = WIPE_STATUS)
    public int wipeStatus;

    @ColumnInfo(name = MESSAGE_TYPE)
    public int messageType;

    @ColumnInfo(name = DISCUSSION_ID)
    public long discussionId;

    @ColumnInfo(name = ENGINE_MESSAGE_IDENTIFIER)
    public byte[] engineMessageIdentifier;

    @ColumnInfo(name = SENDER_IDENTIFIER)
    @NonNull
    public byte[] senderIdentifier;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = TOTAL_ATTACHMENT_COUNT)
    public int totalAttachmentCount;

    @ColumnInfo(name = IMAGE_COUNT)
    public int imageCount;

    @ColumnInfo(name = WIPED_ATTACHMENT_COUNT)
    public int wipedAttachmentCount;

    @ColumnInfo(name = EDITED)
    public int edited;

    @ColumnInfo(name = REACTIONS)
    @Nullable
    public String reactions;

    @ColumnInfo(name = IMAGE_RESOLUTIONS)
    @Nullable
    public String imageResolutions;

    @ColumnInfo(name = MISSED_MESSAGE_COUNT)
    public long missedMessageCount;

    public boolean hasAttachments() {
        return totalAttachmentCount != 0;
    }

    // default constructor required by Room
    public Message(long senderSequenceNumber, @Nullable String contentBody, @Nullable String jsonReply, @Nullable String jsonExpiration, @Nullable String jsonReturnReceipt, double sortIndex, long timestamp, int status, int wipeStatus, int messageType, long discussionId, byte[] engineMessageIdentifier, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, int totalAttachmentCount, int imageCount, int wipedAttachmentCount, int edited, @Nullable String reactions, @Nullable String imageResolutions, long missedMessageCount) {
        this.senderSequenceNumber = senderSequenceNumber;
        this.contentBody = contentBody;
        this.jsonReply = jsonReply;
        this.jsonExpiration = jsonExpiration;
        this.jsonReturnReceipt = jsonReturnReceipt;
        this.sortIndex = sortIndex;
        this.timestamp = timestamp;
        this.status = status;
        this.wipeStatus = wipeStatus;
        this.messageType = messageType;
        this.discussionId = discussionId;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.totalAttachmentCount = totalAttachmentCount;
        this.imageCount = imageCount;
        this.wipedAttachmentCount = wipedAttachmentCount;
        this.edited = edited;
        this.reactions = reactions;
        this.imageResolutions = imageResolutions;
        this.missedMessageCount = missedMessageCount;
    }




    /////////////////////
    // constructor used for inbound and outbound messages
    /////////////////////
    @Ignore
    public Message(AppDatabase db, long senderSequenceNumber, @NonNull JsonMessage jsonMessage, JsonReturnReceipt jsonReturnReceipt, long timestamp, int status, int messageType, long discussionId, byte[] engineMessageIdentifier, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, int totalAttachmentCount, int imageCount) {
        this.senderSequenceNumber = senderSequenceNumber;
        this.setJsonMessage(jsonMessage);
        try {
            this.jsonReturnReceipt = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonReturnReceipt);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        this.timestamp = timestamp;
        this.status = status;
        this.wipeStatus = WIPE_STATUS_NONE;
        this.messageType = messageType;
        this.discussionId = discussionId;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.totalAttachmentCount = totalAttachmentCount;
        this.imageCount = imageCount;
        this.wipedAttachmentCount = 0;
        this.edited = EDITED_NONE;
        this.reactions = null;
        this.imageResolutions = null;
        this.missedMessageCount = 0;

        if (messageType == TYPE_OUTBOUND_MESSAGE) {
            computeOutboundSortIndex(db);
        } else {
            computeSortIndex(db);
        }
    }

    @Ignore
    private final static Object sortIndexLock = new Object();

    public void computeSortIndex(AppDatabase db) {
        synchronized (sortIndexLock) {
            MessageDao messageDao = db.messageDao();

            Message followingMessage = messageDao.getNextMessageBySequenceNumber(senderSequenceNumber, senderThreadIdentifier, senderIdentifier, discussionId);
            if (followingMessage == null || followingMessage.timestamp > timestamp) {
                Message previousMessage = messageDao.getPreviousMessageBySequenceNumber(senderSequenceNumber, senderThreadIdentifier, senderIdentifier, discussionId);
                if (previousMessage == null || previousMessage.timestamp < timestamp) {
                    this.sortIndex = timestamp;
                } else {
                    Double nextSortIndex = messageDao.getNextSortIndex(previousMessage.sortIndex, discussionId);
                    if (nextSortIndex != null) {
                        this.sortIndex = (previousMessage.sortIndex + nextSortIndex) / 2;
                    } else {
                        this.sortIndex = previousMessage.sortIndex + 10;
                    }
                    // also update the message timestamp to avoid weird time lines
                    this.timestamp = previousMessage.timestamp;
                }
            } else {
                Double previousSortIndex = messageDao.getPreviousSortIndex(followingMessage.sortIndex, discussionId);
                if (previousSortIndex != null) {
                    this.sortIndex = (followingMessage.sortIndex + previousSortIndex) / 2;
                } else {
                    this.sortIndex = followingMessage.sortIndex - 10;
                }
                // also update the message timestamp to avoid weird time lines
                this.timestamp = followingMessage.timestamp;
            }
        }
    }

    public void computeOutboundSortIndex(AppDatabase db) {
        synchronized (sortIndexLock) {
            double maxSortIndex = db.messageDao().getDiscussionMaxSortIndex(discussionId);
            sortIndex = maxSortIndex + 10; // append outbound messages 10ms in the future --> better Message ordering
        }
    }

    public static Message createEmptyDraft(long discussionId, byte[] senderIdentifier, UUID senderThreadIdentifier) {
        return new Message(0,
                null,
                null,
                null,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                STATUS_DRAFT,
                WIPE_STATUS_NONE,
                TYPE_OUTBOUND_MESSAGE,
                discussionId,
                null,
                senderIdentifier,
                senderThreadIdentifier,
                0, 0, 0,
                EDITED_NONE,
                null,
                null,
                0);
    }

    private static Message createInfoMessage(int messageType, long discussionId, byte[] senderIdentity, long timestamp) {
        return new Message(
                0,
                null,
                null,
                null,
                null,
                timestamp,
                timestamp,
                STATUS_READ,
                WIPE_STATUS_NONE,
                messageType,
                discussionId,
                null,
                senderIdentity,
                new UUID(0, 0),
                0, 0, 0,
                EDITED_NONE,
                null,
                null,
                0
        );
    }

    public static Message createMemberJoinedGroupMessage(long discussionId, byte[] bytesMemberIdentity) {
        return createInfoMessage(TYPE_GROUP_MEMBER_JOINED, discussionId, bytesMemberIdentity, System.currentTimeMillis());
    }

    public static Message createMemberLeftGroupMessage(long discussionId, byte[] bytesMemberIdentity) {
        return createInfoMessage(TYPE_GROUP_MEMBER_LEFT, discussionId, bytesMemberIdentity, System.currentTimeMillis());
    }

    public static Message createLeftGroupMessage(long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(TYPE_LEFT_GROUP, discussionId, bytesOwnedIdentity, System.currentTimeMillis());
    }

    public static Message createContactDeletedMessage(long discussionId, byte[] bytesContactIdentity) {
        return createInfoMessage(TYPE_CONTACT_DELETED, discussionId, bytesContactIdentity, System.currentTimeMillis());
    }

    ///////
    // phoneCallStatus uses the call statuses from CallLogItem
    public static Message createPhoneCallMessage(long discussionId, byte[] bytesContactIdentity, CallLogItem callLogItem) {
        boolean unread = (callLogItem.callType == CallLogItem.TYPE_INCOMING) && (callLogItem.callStatus == CallLogItem.STATUS_MISSED || callLogItem.callStatus == CallLogItem.STATUS_BUSY);

        Message message = createInfoMessage(TYPE_PHONE_CALL, discussionId, bytesContactIdentity, callLogItem.timestamp);
        message.contentBody = ((callLogItem.callType == CallLogItem.TYPE_INCOMING) ? callLogItem.callStatus : -callLogItem.callStatus) + ":" + callLogItem.id;
        message.status = unread ? STATUS_UNREAD : STATUS_READ;
        return message;
    }

    public static Message createDiscussionSettingsUpdateMessage(long discussionId, DiscussionCustomization.JsonSharedSettings jsonSharedSettings, byte[] bytesIdentityOfInitiator, boolean outbound, Long messageTimestamp) {
        try {
            double sortIndex;
            long timestamp;

            if (messageTimestamp == null) {
                timestamp = System.currentTimeMillis();
                sortIndex = timestamp;
            } else if (messageTimestamp == 0L) {
                sortIndex = 0;
                timestamp = System.currentTimeMillis();
            } else {
                sortIndex = messageTimestamp;
                timestamp = messageTimestamp;
            }

            String serializedSharedSettings = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonSharedSettings);

            Message message = createInfoMessage(TYPE_DISCUSSION_SETTINGS_UPDATE, discussionId, bytesIdentityOfInitiator, timestamp);
            message.contentBody = serializedSharedSettings;
            message.sortIndex = sortIndex;
            message.status = outbound ? STATUS_UNPROCESSED : STATUS_READ;
            return message;
        } catch (IOException e) {
            return null;
        }
    }

    public static Message createDiscussionRemotelyDeletedMessage(long discussionId, byte[] remoteIdentity, long serverTimestamp) {
        return createInfoMessage(TYPE_DISCUSSION_REMOTELY_DELETED, discussionId, remoteIdentity, serverTimestamp);
    }

    public static Message createNewPublishedDetailsMessage(long discussionId, byte[] bytesContactIdentity) {
        Message message = createInfoMessage(TYPE_NEW_PUBLISHED_DETAILS, discussionId, bytesContactIdentity, System.currentTimeMillis());
        message.status = STATUS_UNREAD;
        return message;
    }

    public static Message createContactInactiveReasonMessage(long discussionId, byte[] bytesContactIdentity, @NonNull ObvContactActiveOrInactiveReason notActiveReason) {
        Message message = createInfoMessage(TYPE_CONTACT_INACTIVE_REASON, discussionId, bytesContactIdentity, System.currentTimeMillis());
        switch (notActiveReason) {
            case REVOKED:
                message.contentBody = NOT_ACTIVE_REASON_REVOKED;
                break;
            case FORCEFULLY_UNBLOCKED:
            default:
                break;
        }
        return message;
    }

    public static boolean postDeleteMessagesEverywhereMessage(long discussionId, List<Message> messages) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);
        List<Contact> contacts;
        if (discussion.bytesGroupOwnerAndUid != null) {
            contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
        } else if (discussion.bytesContactIdentity != null) {
            contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity));
        } else {
            Logger.e("Trying to delete everywhere in a locked discussion!!! --> locally deleting instead");
            return true;
        }

        // for group discussions with no members (or discussion with self)
        if (contacts.size() == 0) {
            return true;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        try {
            JsonDeleteMessages jsonDeleteMessages = JsonDeleteMessages.of(discussion, messages);
            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonDeleteMessages(jsonDeleteMessages);

            ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                    null,
                    new ObvOutboundAttachment[0],
                    byteContactIdentities,
                    discussion.bytesOwnedIdentity,
                    false,
                    false
            );

            return postMessageOutput.isMessageSent();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean postDeleteDiscussionEverywhereMessage(long discussionId) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);
        List<Contact> contacts;
        if (discussion.bytesGroupOwnerAndUid != null) {
            contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
        } else if (discussion.bytesContactIdentity != null) {
            contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity));
        } else {
            Logger.e("Trying to delete everywhere a locked discussion!!! --> locally deleting instead");
            return true;
        }

        // for group discussions with no members (or discussion with self)
        if (contacts.size() == 0) {
            return true;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        try {
            JsonDeleteDiscussion jsonDeleteDiscussion = JsonDeleteDiscussion.of(discussion);
            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonDeleteDiscussion(jsonDeleteDiscussion);

            ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                    null,
                    new ObvOutboundAttachment[0],
                    byteContactIdentities,
                    discussion.bytesOwnedIdentity,
                    false,
                    false
            );

            return postMessageOutput.isMessageSent();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean postUpdateMessageMessage(Message updatedMessage) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(updatedMessage.discussionId);
        List<Contact> contacts;
        if (discussion.bytesGroupOwnerAndUid != null) {
            contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
        } else if (discussion.bytesContactIdentity != null) {
            contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity));
        } else {
            Logger.e("Trying to update a message in a locked discussion!!! --> locally deleting instead");
            return true;
        }

        // for group discussions with no members (or discussion with self)
        if (contacts.size() == 0) {
            return true;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        try {
            JsonUpdateMessage jsonUpdateMessage = JsonUpdateMessage.of(discussion, updatedMessage);
            jsonUpdateMessage.setBody(updatedMessage.contentBody);
            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonUpdateMessage(jsonUpdateMessage);

            ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                    null,
                    new ObvOutboundAttachment[0],
                    byteContactIdentities,
                    discussion.bytesOwnedIdentity,
                    false,
                    false
            );

            return postMessageOutput.isMessageSent();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean postReactionMessage(Message message, @Nullable String emoji) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(message.discussionId);
        List<Contact> contacts;
        if (discussion.bytesGroupOwnerAndUid != null) {
            contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
        } else if (discussion.bytesContactIdentity != null) {
            contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity));
        } else {
            Logger.e("Trying to react a message in a locked discussion!!!");
            return true;
        }

        // for group discussions with no members (or discussion with self)
        if (contacts.size() == 0) {
            return true;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        try {
            JsonReaction jsonReaction = JsonReaction.of(discussion, message);
            jsonReaction.setReaction(emoji);
            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonReaction(jsonReaction);

            ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                    null,
                    new ObvOutboundAttachment[0],
                    byteContactIdentities,
                    discussion.bytesOwnedIdentity,
                    false,
                    false
            );

            return postMessageOutput.isMessageSent();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }






    public void post(boolean showToasts, boolean messageNotInDb) {
        try {
            AppDatabase db = AppDatabase.getInstance();
            Discussion discussion = db.discussionDao().getById(discussionId);
            List<Contact> contacts;
            if (discussion.bytesGroupOwnerAndUid != null) {
                contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
            } else if (discussion.bytesContactIdentity != null) {
                contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity));
            } else {
                Logger.e("Trying to post in a locked discussion!!!");
                return;
            }
            // only post the message to contacts with channel --> other will wait
            // we start building the list of messageRecipientInfos with contacts with no channel yet
            ArrayList<MessageRecipientInfo> messageRecipientInfos = new ArrayList<>();
            ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
            boolean hasContactsWithChannel = false;
            List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles = messageNotInDb ? new ArrayList<>() : db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);
            for (Contact contact : contacts) {
                if (contact.establishedChannelCount > 0) {
                    hasContactsWithChannel = true;
                    byteContactIdentities.add(contact.bytesContactIdentity);
                } else if (contact.active) {
                    MessageRecipientInfo messageRecipientInfo = new MessageRecipientInfo(id, attachmentFyles.size(), contact.bytesContactIdentity);
                    messageRecipientInfos.add(messageRecipientInfo);
                } else {
                    Logger.i("Posting a message for an inactive contact --> not creating the MessageRecipientInfo");
                }
            }
            if (!hasContactsWithChannel) {
                if (contacts.size() == 0) {
                    // message was posted to a group discussion with no members --> mark it as sent
                    status = STATUS_SENT;
                    db.messageDao().updateStatus(id, status);
                    for (FyleMessageJoinWithStatusDao.FyleAndStatus attachmentFyleAndStatus: attachmentFyles) {
                        attachmentFyleAndStatus.fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                        db.fyleMessageJoinWithStatusDao().updateStatus(attachmentFyleAndStatus.fyleMessageJoinWithStatus.messageId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.fyleId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.status);
                    }
                } else {
                    if (showToasts) {
                        App.toast(R.string.toast_message_no_channel_message_delayed, Toast.LENGTH_SHORT);
                    }
                }
                return;
            }
            ObvOutboundAttachment[] attachments = new ObvOutboundAttachment[attachmentFyles.size()];
            boolean hasAttachmentWithPreview = false;
            for (int i = 0; i < attachments.length; i++) {
                Fyle fyle = attachmentFyles.get(i).fyle;
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                attachments[i] = new ObvOutboundAttachment(fyle.filePath, fyleMessageJoinWithStatus.size, attachmentFyles.get(i).getMetadata());

                hasAttachmentWithPreview |= PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleMessageJoinWithStatus.getNonNullMimeType());

                if (attachmentFyles.get(i).fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COPYING) {
                    // attachment is still being imported in the App --> do not send yet
                    if (showToasts) {
                        App.toast(R.string.toast_message_attachment_being_copied, Toast.LENGTH_LONG);
                    }
                    return;
                }
            }

            if (hasAttachmentWithPreview) {
                try {
                    ComputeAttachmentPreviewsAndPostMessageTask computeAttachmentPreviewsAndPostMessageTask = new ComputeAttachmentPreviewsAndPostMessageTask(id, null);
                    App.runThread(computeAttachmentPreviewsAndPostMessageTask);
                } catch (Exception e) {
                    // nothing to do, this happens if another preview task is already running for this message
                }
                return;
            }

            final ObvPostMessageOutput postMessageOutput;
            final byte[] returnReceiptNonce;
            final byte[] returnReceiptKey;
            if (messageType == TYPE_OUTBOUND_MESSAGE) {
                returnReceiptNonce = AppSingleton.getEngine().getReturnReceiptNonce();
                returnReceiptKey = AppSingleton.getEngine().getReturnReceiptKey();

                postMessageOutput = AppSingleton.getEngine().post(
                        getMessagePayloadAsBytes(discussion.bytesGroupOwnerAndUid, returnReceiptNonce, returnReceiptKey),
                        null,
                        attachments,
                        byteContactIdentities,
                        discussion.bytesOwnedIdentity,
                        true,
                        false
                );
            } else if (messageType == TYPE_DISCUSSION_SETTINGS_UPDATE) {
                returnReceiptNonce = null;
                returnReceiptKey = null;
                postMessageOutput = AppSingleton.getEngine().post(
                        getDiscussionSettingsUpdatePayloadAsBytes(discussion.bytesGroupOwnerAndUid),
                        null,
                        new ObvOutboundAttachment[0],
                        byteContactIdentities,
                        discussion.bytesOwnedIdentity,
                        false,
                        false
                );
            } else {
                return;
            }

            if (!postMessageOutput.isMessageSent()) {
                // sending failed for all contacts, do nothing, at next restart it will try again...
                return;
            } else if (messageType == TYPE_OUTBOUND_MESSAGE) {
                // update the list of messageRecipientInfos with the engine output
                byte[] firstEngineMessageIdentifier = null;
                for (ObvPostMessageOutput.BytesKey bytesKeyContactIdentity : postMessageOutput.getMessageIdentifierByContactIdentity().keySet()) {
                    byte[] engineMessageIdentifier = postMessageOutput.getMessageIdentifierByContactIdentity().get(bytesKeyContactIdentity);
                    MessageRecipientInfo messageRecipientInfo = new MessageRecipientInfo(id, attachments.length, bytesKeyContactIdentity.getBytes(), engineMessageIdentifier, returnReceiptNonce, returnReceiptKey);
                    messageRecipientInfos.add(messageRecipientInfo);
                    if (firstEngineMessageIdentifier == null && engineMessageIdentifier != null) {
                        firstEngineMessageIdentifier = engineMessageIdentifier;
                    }
                }
                for (int i = 0; i < attachments.length; i++) {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                    fyleMessageJoinWithStatus.engineMessageIdentifier = firstEngineMessageIdentifier;
                    fyleMessageJoinWithStatus.engineNumber = i;
                    db.fyleMessageJoinWithStatusDao().updateEngineIdentifier(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
                }
                // insert all messageRecipientInfos (whether passed to the engine or not)
                db.messageRecipientInfoDao().insert(messageRecipientInfos.toArray(new MessageRecipientInfo[0]));
            }

            if (!messageNotInDb) {
                for (int i = 0; i < attachments.length; i++) {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                    fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_UPLOADING;
                    db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                }
                if (messageType == TYPE_OUTBOUND_MESSAGE) {
                    status = STATUS_PROCESSING;
                } else {
                    status = STATUS_SENT;
                }
                db.messageDao().updateStatus(id, status);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error posting a message to the engine;");
        }
    }

    public void repost(MessageRecipientInfo messageRecipientInfo) {
        try {
            AppDatabase db = AppDatabase.getInstance();
            Discussion discussion = db.discussionDao().getById(discussionId);
            if (discussion.bytesGroupOwnerAndUid == null && discussion.bytesContactIdentity == null) {
                // discussion is locked, simply mark the MessageRecipientInfo as processed
                if (messageRecipientInfo.engineMessageIdentifier == null) {
                    messageRecipientInfo.engineMessageIdentifier = new byte[0];
                }
                if (messageRecipientInfo.timestampSent == null) {
                    messageRecipientInfo.timestampSent = 0L;
                }
                db.messageRecipientInfoDao().update(messageRecipientInfo);
                if (refreshOutboundStatus()) {
                    db.messageDao().updateStatus(id, status);
                }
                return;
            }

            List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);
            ObvOutboundAttachment[] attachments = new ObvOutboundAttachment[attachmentFyles.size()];
            boolean hasAttachmentWithPreview = false;
            for (int i = 0; i < attachments.length; i++) {
                Fyle fyle = attachmentFyles.get(i).fyle;
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                attachments[i] = new ObvOutboundAttachment(fyle.filePath, fyleMessageJoinWithStatus.size, attachmentFyles.get(i).getMetadata());

                hasAttachmentWithPreview |= PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleMessageJoinWithStatus.getNonNullMimeType());
            }

            if (hasAttachmentWithPreview) {
                try {
                    ComputeAttachmentPreviewsAndPostMessageTask computeAttachmentPreviewsAndPostMessageTask = new ComputeAttachmentPreviewsAndPostMessageTask(id, messageRecipientInfo);
                    App.runThread(computeAttachmentPreviewsAndPostMessageTask);
                } catch (Exception e) {
                    // nothing to do, this happens if another preview task is already running for this message
                }
                return;
            }

            List<byte[]> bytesContactIdentities = new ArrayList<>();
            bytesContactIdentities.add(messageRecipientInfo.bytesContactIdentity);

            final ObvPostMessageOutput postMessageOutput;
            final byte[] returnReceiptNonce;
            final byte[] returnReceiptKey;
            if (messageType == TYPE_OUTBOUND_MESSAGE) {
                returnReceiptNonce = AppSingleton.getEngine().getReturnReceiptNonce();
                returnReceiptKey = AppSingleton.getEngine().getReturnReceiptKey();

                postMessageOutput = AppSingleton.getEngine().post(
                        getMessagePayloadAsBytes(discussion.bytesGroupOwnerAndUid, returnReceiptNonce, returnReceiptKey),
                        null,
                        attachments,
                        bytesContactIdentities,
                        discussion.bytesOwnedIdentity,
                        true,
                        false
                );
            } else if (messageType == TYPE_DISCUSSION_SETTINGS_UPDATE) {
                returnReceiptNonce = null;
                returnReceiptKey = null;

                postMessageOutput = AppSingleton.getEngine().post(
                        getDiscussionSettingsUpdatePayloadAsBytes(discussion.bytesGroupOwnerAndUid),
                        null,
                        new ObvOutboundAttachment[0],
                        bytesContactIdentities,
                        discussion.bytesOwnedIdentity,
                        false,
                        false
                );
            } else {
                return;
            }

            if (!postMessageOutput.isMessageSent()) {
                // sending failed for all contacts, do nothing, at next restart it will try again...
                return;
            } else {
                // update the list of messageRecipientInfos with the engine output
                byte[] messageIdentifier = postMessageOutput.getMessageIdentifierByContactIdentity().get(new ObvPostMessageOutput.BytesKey(messageRecipientInfo.bytesContactIdentity));
                messageRecipientInfo.engineMessageIdentifier = messageIdentifier;
                messageRecipientInfo.returnReceiptNonce = returnReceiptNonce;
                messageRecipientInfo.returnReceiptKey = returnReceiptKey;
                db.messageRecipientInfoDao().update(messageRecipientInfo);
                for (int i = 0; i < attachments.length; i++) {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                    fyleMessageJoinWithStatus.engineMessageIdentifier = messageIdentifier;
                    fyleMessageJoinWithStatus.engineNumber = i;
                    db.fyleMessageJoinWithStatusDao().updateEngineIdentifier(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
                }
            }

            for (int i = 0; i < attachments.length; i++) {
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_UPLOADING;
                db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
            }
            if (refreshOutboundStatus()) {
                db.messageDao().updateStatus(id, status);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("Error reposting a message to the engine;");
        }
    }


    public void sendMessageReturnReceipt(Discussion discussion, int status) {
        JsonReturnReceipt returnReceipt = getJsonReturnReceipt();
        if (discussion != null && discussion.id == discussionId && returnReceipt != null) {
            AppSingleton.getEngine().sendReturnReceipt(discussion.bytesOwnedIdentity, senderIdentifier, status, returnReceipt.nonce, returnReceipt.key, null);
        }
    }

    public void sendAttachmentReturnReceipt(byte[] bytesOwnedIdentity, int status, int attachmentNumber) {
        JsonReturnReceipt returnReceipt = getJsonReturnReceipt();
        if (returnReceipt != null) {
            AppSingleton.getEngine().sendReturnReceipt(bytesOwnedIdentity, senderIdentifier, status, returnReceipt.nonce, returnReceipt.key, attachmentNumber);
        }
    }

    public boolean refreshOutboundStatus() {
        if (messageType != TYPE_OUTBOUND_MESSAGE) {
            return false;
        }
        List<MessageRecipientInfo> messageRecipientInfos = AppDatabase.getInstance().messageRecipientInfoDao().getAllByMessageId(id);
        if (messageRecipientInfos.size() == 0) {
            return false;
        }
        int newStatus = 100000;
        for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
            if (messageRecipientInfo.timestampSent == null) {
                newStatus = STATUS_PROCESSING;
                break;
            } else if (messageRecipientInfo.timestampDelivered == null) {
                newStatus = STATUS_SENT;
            } else if (messageRecipientInfo.timestampRead == null) {
                newStatus = Math.min(newStatus, STATUS_DELIVERED);
            } else {
                newStatus = Math.min(newStatus, STATUS_DELIVERED_AND_READ);
            }
        }
        if (newStatus != status) {
            status = newStatus;
            if (status == STATUS_SENT && jsonExpiration != null) {
                App.runThread(new ExpiringOutboundMessageSent(this));
            }
            return true;
        }
        return false;
    }

    public byte[] getAssociatedBytesOwnedIdentity() {
        return AppDatabase.getInstance().discussionDao().getBytesOwnedIdentityForDiscussionId(discussionId);
    }


    @NonNull
    public String getStringContent(Context context) {
        if (messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            return context.getString(R.string.text_message_content_hidden);
        } else if (wipeStatus == WIPE_STATUS_WIPED) {
            return context.getString(R.string.text_message_content_wiped);
        } else if (wipeStatus == WIPE_STATUS_REMOTE_DELETED) {
            return context.getString(R.string.text_message_content_remote_deleted);
        } else if (contentBody == null) {
            return "";
        } else {
            return contentBody;
        }
    }

    // check whether a Message has an empty body and no attachment (it should then be deleted)
    public boolean isEmpty() {
        return (contentBody == null || contentBody.trim().length() == 0)
                && !hasAttachments()
                && wipeStatus != WIPE_STATUS_WIPED
                && wipeStatus != WIPE_STATUS_REMOTE_DELETED;
    }

    public boolean isTextOnly() {
        return contentBody != null && contentBody.trim().length() > 0 && !hasAttachments() && jsonReply == null;
    }

    public boolean isWithoutText() {
        return contentBody == null || contentBody.trim().length() == 0;
    }

    public boolean isContentHidden() {
        return messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE;
    }

    public boolean isInbound() {
        return messageType == TYPE_INBOUND_MESSAGE || messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE;
    }

    public byte[] getMessagePayloadAsBytes(byte[] bytesGroupOwnerAndUid, byte[] returnReceiptNonce, byte[] returnReceiptKey) throws Exception {
        JsonMessage jsonMessage = getJsonMessage();
        if (bytesGroupOwnerAndUid != null) {
            jsonMessage.setGroupOwnerAndUid(bytesGroupOwnerAndUid);
        }
        JsonReturnReceipt jsonReturnReceipt = new JsonReturnReceipt(returnReceiptNonce, returnReceiptKey);
        JsonPayload jsonPayload = new JsonPayload(jsonMessage, jsonReturnReceipt);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    public byte[] getDiscussionSettingsUpdatePayloadAsBytes(byte[] bytesGroupOwnerAndUid) throws Exception {
        DiscussionCustomization.JsonSharedSettings jsonSharedSettings = AppSingleton.getJsonObjectMapper().readValue(contentBody, DiscussionCustomization.JsonSharedSettings.class);
        if (bytesGroupOwnerAndUid != null) {
            jsonSharedSettings.setGroupOwnerAndUid(bytesGroupOwnerAndUid);
        }
        JsonPayload jsonPayload = new JsonPayload();
        jsonPayload.setJsonSharedSettings(jsonSharedSettings);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    public JsonMessage getJsonMessage() {
        JsonMessage jsonMessage = new JsonMessage();
        jsonMessage.body = contentBody;
        jsonMessage.senderSequenceNumber = senderSequenceNumber;
        jsonMessage.senderThreadIdentifier = senderThreadIdentifier;
        if (jsonReply != null) {
            try {
                jsonMessage.jsonReply = AppSingleton.getJsonObjectMapper().readValue(jsonReply, JsonMessageReference.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (jsonExpiration != null) {
            try {
                jsonMessage.jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(jsonExpiration, JsonExpiration.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return jsonMessage;
    }

    public void setJsonMessage(JsonMessage jsonMessage) {
        contentBody = jsonMessage.body;
        if (jsonMessage.jsonReply != null) {
            try {
                jsonReply = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessage.jsonReply);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonReply = null;
            }
        }
        if (jsonMessage.jsonExpiration != null) {
            try {
                jsonExpiration = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessage.jsonExpiration);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonExpiration = null;
            }
        }
    }

    public JsonReturnReceipt getJsonReturnReceipt() {
        if (jsonReturnReceipt != null) {
            try {
                return AppSingleton.getJsonObjectMapper().readValue(jsonReturnReceipt, JsonReturnReceipt.class);
            } catch (Exception e) {
                Logger.w("Error decoding a return receipt!\n" + jsonReturnReceipt);
            }
        }
        return null;
    }

    // returns true if the attachment count has changed, false if nothing changed
    public boolean recomputeAttachmentCount(AppDatabase db) {
        int totalCount = 0;
        int imageCount = 0;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (FyleMessageJoinWithStatus fmjoin : db.fyleMessageJoinWithStatusDao().getStatusesForMessage(this.id)) {
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(fmjoin.getNonNullMimeType())) {
                imageCount++;
                if (fmjoin.imageResolution != null && fmjoin.imageResolution.length() > 0) {
                    if (!first) {
                        sb.append(";");
                    }
                    first = false;
                    sb.append(fmjoin.imageResolution);
                }
            }
            totalCount++;
        }
        String imageResolutions = sb.toString();
        if (this.totalAttachmentCount != totalCount || this.imageCount != imageCount || !Objects.equals(this.imageResolutions, imageResolutions)) {
            this.totalAttachmentCount = totalCount;
            this.imageCount = imageCount;
            this.imageResolutions = imageResolutions;
            return true;
        }
        return false;
    }

    // deletes the message and all its attachments --> should be run in a transaction
    public void delete(AppDatabase db) {
        if (!db.inTransaction()) {
            Logger.e("WARNING: Calling Message.delete() outside a transaction");
        }
        deleteAttachments(db);
        db.messageDao().delete(this);
    }

    // this never deletes the message, even if it has an empty body
    public void deleteAttachments(AppDatabase db) {
        if (wipedAttachmentCount == 0) {
            wipedAttachmentCount = totalAttachmentCount;
        }
        List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);
        for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus: fyleAndStatuses) {
            db.fyleMessageJoinWithStatusDao().delete(fyleAndStatus.fyleMessageJoinWithStatus);
            switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                    AppSingleton.getEngine().deleteAttachment(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    break;
                case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                    AppSingleton.getEngine().cancelAttachmentUpload(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    break;
            }

            if (db.fyleMessageJoinWithStatusDao().countMessageForFyle(fyleAndStatus.fyle.id) == 0) {
                if (fyleAndStatus.fyle.sha256 != null) {
                    Fyle.acquireLock(fyleAndStatus.fyle.sha256);
                    fyleAndStatus.fyle.delete();
                    Fyle.releaseLock(fyleAndStatus.fyle.sha256);
                } else {
                    fyleAndStatus.fyle.delete();
                }
            }
        }
        totalAttachmentCount = 0;
        imageCount = 0;
        imageResolutions = null;
        db.messageDao().updateAttachmentCount(id, 0, 0, wipedAttachmentCount, null);
    }

    // this method clears the message's body and deletes it if there are no more attachments
    public void wipe(AppDatabase db) {
        if (isInbound() && totalAttachmentCount == 0) {
            delete(db);
        } else {
            contentBody = null;
            jsonReply = null;
            edited = EDITED_NONE;
            wipeStatus = WIPE_STATUS_WIPED;
            reactions = null;
            imageResolutions = null;
            db.messageDao().updateWipe(id, WIPE_STATUS_WIPED);
            db.reactionDao().deleteAllForMessage(id);
            db.messageMetadataDao().insert(new MessageMetadata(id, MessageMetadata.KIND_WIPED, System.currentTimeMillis()));
            db.messageExpirationDao().deleteWipeExpiration(id);
        }
    }

    public void remoteDelete(AppDatabase db, byte[] bytesRemoteIdentity, long serverTimestamp) {
        contentBody = null;
        jsonReply = null;
        edited = EDITED_NONE;
        wipeStatus = WIPE_STATUS_REMOTE_DELETED;
        reactions = null;
        db.messageDao().updateWipe(id, WIPE_STATUS_REMOTE_DELETED);
        db.reactionDao().deleteAllForMessage(id);
        if (messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            messageType = TYPE_INBOUND_MESSAGE;
            db.messageDao().updateMessageType(id, messageType);
        }
        db.messageMetadataDao().insert(new MessageMetadata(id, MessageMetadata.KIND_REMOTE_DELETED, serverTimestamp, bytesRemoteIdentity));
        db.messageExpirationDao().deleteWipeExpiration(id);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonPayload {
        JsonMessage jsonMessage;
        JsonReturnReceipt jsonReturnReceipt;
        JsonWebrtcMessage jsonWebrtcMessage;
        DiscussionCustomization.JsonSharedSettings jsonSharedSettings;
        JsonUpdateMessage jsonUpdateMessage;
        JsonDeleteMessages jsonDeleteMessages;
        JsonDeleteDiscussion jsonDeleteDiscussion;
        JsonReaction jsonReaction;

        public JsonPayload(JsonMessage jsonMessage, JsonReturnReceipt jsonReturnReceipt) {
            this.jsonMessage = jsonMessage;
            this.jsonReturnReceipt = jsonReturnReceipt;
        }

        public JsonPayload() {
        }

        @JsonProperty("message")
        public JsonMessage getJsonMessage() {
            return jsonMessage;
        }

        @JsonProperty("message")
        public void setJsonMessage(JsonMessage jsonMessage) {
            this.jsonMessage = jsonMessage;
        }

        @JsonProperty("rr")
        public JsonReturnReceipt getJsonReturnReceipt() {
            return jsonReturnReceipt;
        }

        @JsonProperty("rr")
        public void setJsonReturnReceipt(JsonReturnReceipt jsonReturnReceipt) {
            this.jsonReturnReceipt = jsonReturnReceipt;
        }

        @JsonProperty("rtc")
        public JsonWebrtcMessage getJsonWebrtcMessage() {
            return jsonWebrtcMessage;
        }

        @JsonProperty("rtc")
        public void setJsonWebrtcMessage(JsonWebrtcMessage jsonWebrtcMessage) {
            this.jsonWebrtcMessage = jsonWebrtcMessage;
        }

        @JsonProperty("settings")
        public DiscussionCustomization.JsonSharedSettings getJsonSharedSettings() {
            return jsonSharedSettings;
        }

        @JsonProperty("settings")
        public void setJsonSharedSettings(DiscussionCustomization.JsonSharedSettings jsonSharedSettings) {
            this.jsonSharedSettings = jsonSharedSettings;
        }

        @JsonProperty("upm")
        public JsonUpdateMessage getJsonUpdateMessage() {
            return jsonUpdateMessage;
        }

        @JsonProperty("upm")
        public void setJsonUpdateMessage(JsonUpdateMessage jsonUpdateMessage) {
            this.jsonUpdateMessage = jsonUpdateMessage;
        }

        @JsonProperty("delm")
        public JsonDeleteMessages getJsonDeleteMessages() {
            return jsonDeleteMessages;
        }

        @JsonProperty("delm")
        public void setJsonDeleteMessages(JsonDeleteMessages jsonDeleteMessages) {
            this.jsonDeleteMessages = jsonDeleteMessages;
        }

        @JsonProperty("deld")
        public JsonDeleteDiscussion getJsonDeleteDiscussion() {
            return jsonDeleteDiscussion;
        }

        @JsonProperty("deld")
        public void setJsonDeleteDiscussion(JsonDeleteDiscussion jsonDeleteDiscussion) {
            this.jsonDeleteDiscussion = jsonDeleteDiscussion;
        }

        @JsonProperty("reacm")
        public JsonReaction getJsonReaction() {
            return jsonReaction;
        }

        @JsonProperty("reacm")
        public void setJsonReaction(JsonReaction jsonReaction) {
            this.jsonReaction = jsonReaction;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonMessage {
        String body;
        long senderSequenceNumber;
        UUID senderThreadIdentifier;
        byte[] groupUid;
        byte[] groupOwner;
        JsonMessageReference jsonReply;
        JsonExpiration jsonExpiration;

        public JsonMessage(String body) {
            this.body = body;
        }

        public JsonMessage() {
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @JsonProperty("ssn")
        public long getSenderSequenceNumber() {
            return senderSequenceNumber;
        }

        @JsonProperty("ssn")
        public void setSenderSequenceNumber(long senderSequenceNumber) {
            this.senderSequenceNumber = senderSequenceNumber;
        }

        @JsonProperty("sti")
        public UUID getSenderThreadIdentifier() {
            return senderThreadIdentifier;
        }

        @JsonProperty("sti")
        public void setSenderThreadIdentifier(UUID senderThreadIdentifier) {
            this.senderThreadIdentifier = senderThreadIdentifier;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonProperty("re")
        public JsonMessageReference getJsonReply() {
            return jsonReply;
        }

        @JsonProperty("re")
        public void setJsonReply(JsonMessageReference jsonMessageReference) {
            this.jsonReply = jsonMessageReference;
        }

        @JsonProperty("exp")
        public JsonExpiration getJsonExpiration() {
            return jsonExpiration;
        }

        @JsonProperty("exp")
        public void setJsonExpiration(JsonExpiration jsonExpiration) {
            this.jsonExpiration = jsonExpiration;
        }

        @JsonIgnore
        public boolean isEmpty() {
            return (body == null || body.trim().length() == 0) && jsonReply == null;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonMessageReference {
        long senderSequenceNumber;
        UUID senderThreadIdentifier;
        byte[] senderIdentifier;

        public static JsonMessageReference of(Message message) {
            JsonMessageReference jsonMessageReference = new JsonMessageReference();
            jsonMessageReference.senderSequenceNumber = message.senderSequenceNumber;
            jsonMessageReference.senderThreadIdentifier = message.senderThreadIdentifier;
            jsonMessageReference.senderIdentifier = message.senderIdentifier;
            return jsonMessageReference;
        }

        @JsonProperty("ssn")
        public long getSenderSequenceNumber() {
            return senderSequenceNumber;
        }

        @JsonProperty("ssn")
        public void setSenderSequenceNumber(long senderSequenceNumber) {
            this.senderSequenceNumber = senderSequenceNumber;
        }

        @JsonProperty("sti")
        public UUID getSenderThreadIdentifier() {
            return senderThreadIdentifier;
        }

        @JsonProperty("sti")
        public void setSenderThreadIdentifier(UUID senderThreadIdentifier) {
            this.senderThreadIdentifier = senderThreadIdentifier;
        }

        @JsonProperty("si")
        public byte[] getSenderIdentifier() {
            return senderIdentifier;
        }

        @JsonProperty("si")
        public void setSenderIdentifier(byte[] senderIdentifier) {
            this.senderIdentifier = senderIdentifier;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonExpiration {
        Long existenceDuration; // in seconds
        Long visibilityDuration; // in seconds
        Boolean readOnce;

        @JsonProperty("ex")
        public Long getExistenceDuration() {
            return existenceDuration;
        }

        @JsonProperty("ex")
        public void setExistenceDuration(Long existenceDuration) {
            this.existenceDuration = existenceDuration;
        }

        @JsonProperty("vis")
        public Long getVisibilityDuration() {
            return visibilityDuration;
        }

        @JsonProperty("vis")
        public void setVisibilityDuration(Long visibilityDuration) {
            this.visibilityDuration = visibilityDuration;
        }

        @JsonProperty("ro")
        public Boolean getReadOnce() {
            return readOnce;
        }

        @JsonProperty("ro")
        public void setReadOnce(Boolean readOnce) {
            this.readOnce = readOnce;
        }

        @JsonIgnore
        @NonNull
        public JsonExpiration computeGcd(@Nullable JsonExpiration jsonExpiration) {
            if (jsonExpiration != null) {
                readOnce = (readOnce != null && readOnce) || (jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce());
                if (jsonExpiration.getVisibilityDuration() != null) {
                    if (visibilityDuration == null) {
                        visibilityDuration = jsonExpiration.getVisibilityDuration();
                    } else {
                        visibilityDuration = Math.min(visibilityDuration, jsonExpiration.getVisibilityDuration());
                    }
                }
                if (jsonExpiration.getExistenceDuration() != null) {
                    if (existenceDuration == null) {
                        existenceDuration = jsonExpiration.getExistenceDuration();
                    } else {
                        existenceDuration = Math.min(existenceDuration, jsonExpiration.getExistenceDuration());
                    }
                }
            }
            return this;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof JsonExpiration)) {
                return false;
            }
            JsonExpiration other = (JsonExpiration) obj;
            if ((readOnce != null && readOnce) ^ (other.readOnce != null && other.readOnce)) {
                return false;
            }
            if (!Objects.equals(visibilityDuration, other.visibilityDuration)) {
                return false;
            }
            return Objects.equals(existenceDuration, other.existenceDuration);
        }

        public boolean likeNull() {
            return (readOnce == null || !readOnce) && (visibilityDuration == null) && (existenceDuration == null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonReturnReceipt {
        byte[] nonce;
        byte[] key;

        @SuppressWarnings("unused")
        public JsonReturnReceipt() {
        }

        public JsonReturnReceipt(byte[] nonce, byte[] key) {
            this.nonce = nonce;
            this.key = key;
        }

        public byte[] getNonce() {
            return nonce;
        }

        public void setNonce(byte[] nonce) {
            this.nonce = nonce;
        }

        public byte[] getKey() {
            return key;
        }

        public void setKey(byte[] key) {
            this.key = key;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonWebrtcMessage {
        UUID callIdentifier;
        Integer messageType;
        String serializedMessagePayload;

        public JsonWebrtcMessage() {
        }

        @JsonProperty("ci")
        public UUID getCallIdentifier() {
            return callIdentifier;
        }

        @JsonProperty("ci")
        public void setCallIdentifier(UUID callIdentifier) {
            this.callIdentifier = callIdentifier;
        }

        @JsonProperty("mt")
        public Integer getMessageType() {
            return messageType;
        }

        @JsonProperty("mt")
        public void setMessageType(Integer messageType) {
            this.messageType = messageType;
        }

        @JsonProperty("smp")
        public String getSerializedMessagePayload() {
            return serializedMessagePayload;
        }

        @JsonProperty("smp")
        public void setSerializedMessagePayload(String serializedMessagePayload) {
            this.serializedMessagePayload = serializedMessagePayload;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonUpdateMessage {
        String body;
        byte[] groupUid;
        byte[] groupOwner;
        JsonMessageReference messageReference;

        public static JsonUpdateMessage of(Discussion discussion, Message message) throws Exception {
            JsonUpdateMessage jsonUpdateMessage = new JsonUpdateMessage();
            jsonUpdateMessage.messageReference = JsonMessageReference.of(message);
            if (discussion.bytesGroupOwnerAndUid != null) {
                jsonUpdateMessage.setGroupOwnerAndUid(discussion.bytesGroupOwnerAndUid);
            }
            return jsonUpdateMessage;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonProperty("ref")
        public JsonMessageReference getMessageReference() {
            return messageReference;
        }

        @JsonProperty("ref")
        public void setMessageReference(JsonMessageReference messageReference) {
            this.messageReference = messageReference;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonReaction {
        @Nullable String reaction; // reaction is null to remove previous reaction
        byte[] groupUid;
        byte[] groupOwner;
        JsonMessageReference messageReference;

        public static JsonReaction of(Discussion discussion, Message message) throws Exception {
            JsonReaction jsonReaction = new JsonReaction();

            jsonReaction.messageReference = JsonMessageReference.of(message);
            if (discussion.bytesGroupOwnerAndUid != null) {
                jsonReaction.setGroupOwnerAndUid(discussion.bytesGroupOwnerAndUid);
            }
            return jsonReaction;
        }

        @JsonProperty("reac")
        @Nullable
        public String getReaction() {
            return reaction;
        }

        @JsonProperty("reac")
        public void setReaction(@Nullable String reaction) {
            this.reaction = reaction;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonProperty("ref")
        public JsonMessageReference getMessageReference() {
            return messageReference;
        }

        @JsonProperty("ref")
        public void setMessageReference(JsonMessageReference messageReference) {
            this.messageReference = messageReference;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonDeleteMessages {
        byte[] groupUid;
        byte[] groupOwner;
        List<JsonMessageReference> messageReferences;

        public static JsonDeleteMessages of(Discussion discussion, List<Message> messages) throws Exception {
            JsonDeleteMessages jsonDeleteMessages = new JsonDeleteMessages();
            jsonDeleteMessages.messageReferences = new ArrayList<>(messages.size());
            for (Message message: messages) {
                switch (message.messageType) {
                    case TYPE_INBOUND_MESSAGE:
                    case TYPE_OUTBOUND_MESSAGE:
                    case TYPE_INBOUND_EPHEMERAL_MESSAGE:
                        jsonDeleteMessages.messageReferences.add(JsonMessageReference.of(message));
                        break;
                }
            }
            if (discussion.bytesGroupOwnerAndUid != null) {
                jsonDeleteMessages.setGroupOwnerAndUid(discussion.bytesGroupOwnerAndUid);
            }
            return jsonDeleteMessages;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonProperty("refs")
        public List<JsonMessageReference> getMessageReferences() {
            return messageReferences;
        }

        @JsonProperty("refs")
        public void setMessageReferences(List<JsonMessageReference> messageReferences) {
            this.messageReferences = messageReferences;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonDeleteDiscussion {
        byte[] groupUid;
        byte[] groupOwner;

        public static JsonDeleteDiscussion of(Discussion discussion) throws Exception {
            JsonDeleteDiscussion jsonDeleteDiscussion = new JsonDeleteDiscussion();
            if (discussion.bytesGroupOwnerAndUid != null) {
                jsonDeleteDiscussion.setGroupOwnerAndUid(discussion.bytesGroupOwnerAndUid);
            }
            return jsonDeleteDiscussion;
        }

        @JsonProperty("guid")
        public byte[] getGroupUid() {
            return groupUid;
        }

        @JsonProperty("guid")
        public void setGroupUid(byte[] groupUid) {
            this.groupUid = groupUid;
        }

        @JsonProperty("go")
        public byte[] getGroupOwner() {
            return groupOwner;
        }

        @JsonProperty("go")
        public void setGroupOwner(byte[] groupOwner) {
            this.groupOwner = groupOwner;
        }

        @JsonIgnore
        public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
            if (bytesGroupOwnerAndUid.length < 32) {
                throw new Exception();
            }
            byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            setGroupOwner(bytesGroupOwner);
            setGroupUid(bytesGroupUid);
        }
    }
}
