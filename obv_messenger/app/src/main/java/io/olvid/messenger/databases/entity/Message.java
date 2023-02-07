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

package io.olvid.messenger.databases.entity;

import android.content.Context;
import android.location.Location;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.tasks.ComputeAttachmentPreviewsAndPostMessageTask;
import io.olvid.messenger.databases.tasks.ExpiringOutboundMessageSent;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;
import io.olvid.messenger.services.UnifiedForegroundService;

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
                @Index(value = {Message.SENDER_SEQUENCE_NUMBER, Message.SENDER_THREAD_IDENTIFIER, Message.SENDER_IDENTIFIER, Message.DISCUSSION_ID}),
        }
)
public class Message {
    public static final String TABLE_NAME = "message_table";

    public static final String SENDER_SEQUENCE_NUMBER = "sender_sequence_number";
    public static final String CONTENT_BODY = "content_body";
    public static final String JSON_REPLY = "json_reply";
    public static final String JSON_EXPIRATION = "json_expiration"; // for inbound messages, this is null unless it is an ephemeral message
    public static final String JSON_RETURN_RECEIPT = "json_return_receipt";
    public static final String JSON_LOCATION = "json_location"; // for location messages, this is null if message is not a location message
    public static final String LOCATION_TYPE = "location_type";
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
    public static final String FORWARDED = "forwarded";
    public static final String REACTIONS = "reactions";
    public static final String IMAGE_RESOLUTIONS = "image_resolutions"; // null or "" = no images, "102x234;a340x445" = 2 images, second one is animated
    public static final String MISSED_MESSAGE_COUNT = "missed_message_count"; // only used in inbound messages: the number of missing sequence numbers when this message is received. 0 --> everything is fine
    public static final String EXPIRATION_START_TIMESTAMP = "expiration_start_timestamp"; // set when the message is first marked as sent --> this is when expirations are started. Only set for message with an expiration
    public static final String LIMITED_VISIBILITY = "limited_visibility"; // true for read_once messages and messages with a visibility_duration
    public static final String LINK_PREVIEW_FYLE_ID = "link_preview_fyle_id"; // id of attached link preview




    // This enum is used in protobuf for webclient, please send notification if you modify anything
    public static final int STATUS_UNPROCESSED = 0; // MessageRecipientInfos not created yet
    public static final int STATUS_PROCESSING = 1; // MessageRecipientInfos were created for all recipients (we know exactly who will receive the message)
    public static final int STATUS_SENT = 2; // message (and all attachments) uploaded for all recipients for which the message was sent to the engine
    public static final int STATUS_UNREAD = 3;
    public static final int STATUS_READ = 4;
    public static final int STATUS_DRAFT = 5;
    public static final int STATUS_DELIVERED = 6;
    public static final int STATUS_DELIVERED_AND_READ = 7;
    public static final int STATUS_COMPUTING_PREVIEW = 8; // computing a preview of the image/video attachments before passing to engine
    public static final int STATUS_UNDELIVERED = 9; // for outbound messages, the message could not be uploaded/delivered and will never be

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
    public static final int TYPE_CONTACT_RE_ADDED = 12;
    public static final int TYPE_RE_JOINED_GROUP = 13;
    public static final int TYPE_JOINED_GROUP = 14;
    public static final int TYPE_GAINED_GROUP_ADMIN = 15;
    public static final int TYPE_LOST_GROUP_ADMIN = 16;
    public static final int TYPE_SCREEN_SHOT_DETECTED = 17;


    public static final int EDITED_NONE = 0;
    public static final int EDITED_UNSEEN = 1;
    public static final int EDITED_SEEN = 2;

    public static final int LOCATION_TYPE_NONE = 0; // not a location message
    public static final int LOCATION_TYPE_SEND = 1; // send location one time
    public static final int LOCATION_TYPE_SHARE = 2; // sharing location still in progress
    public static final int LOCATION_TYPE_SHARE_FINISHED = 3; // sharing location ended

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

    @ColumnInfo(name = JSON_LOCATION)
    @Nullable
    public String jsonLocation;

    @ColumnInfo(name = LOCATION_TYPE)
    public int locationType;

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

    @ColumnInfo(name = FORWARDED)
    public boolean forwarded;

    @ColumnInfo(name = REACTIONS)
    @Nullable
    public String reactions;

    @ColumnInfo(name = IMAGE_RESOLUTIONS)
    @Nullable
    public String imageResolutions;

    @ColumnInfo(name = MISSED_MESSAGE_COUNT)
    public long missedMessageCount;

    @ColumnInfo(name = EXPIRATION_START_TIMESTAMP)
    public long expirationStartTimestamp;

    @ColumnInfo(name = LIMITED_VISIBILITY)
    public boolean limitedVisibility;

    @ColumnInfo(name = LINK_PREVIEW_FYLE_ID)
    public Long linkPreviewFyleId;

    public boolean hasAttachments() {
        return totalAttachmentCount > 0;
    }

    // default constructor required by Room
    public Message(long senderSequenceNumber, @Nullable String contentBody, @Nullable String jsonReply, @Nullable String jsonExpiration, @Nullable String jsonReturnReceipt, @Nullable String jsonLocation, int locationType, double sortIndex, long timestamp, int status, int wipeStatus, int messageType, long discussionId, byte[] engineMessageIdentifier, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, int totalAttachmentCount, int imageCount, int wipedAttachmentCount, int edited, boolean forwarded, @Nullable String reactions, @Nullable String imageResolutions, long missedMessageCount, long expirationStartTimestamp, boolean limitedVisibility, @Nullable Long linkPreviewFyleId) {
        this.senderSequenceNumber = senderSequenceNumber;
        this.contentBody = contentBody;
        this.jsonReply = jsonReply;
        this.jsonExpiration = jsonExpiration;
        this.jsonReturnReceipt = jsonReturnReceipt;
        this.jsonLocation = jsonLocation;
        this.locationType = locationType;
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
        this.forwarded = forwarded;
        this.reactions = reactions;
        this.imageResolutions = imageResolutions;
        this.missedMessageCount = missedMessageCount;
        this.expirationStartTimestamp = expirationStartTimestamp;
        this.limitedVisibility = limitedVisibility;
        this.linkPreviewFyleId = linkPreviewFyleId;
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
        this.forwarded = false;
        this.reactions = null;
        this.imageResolutions = null;
        this.missedMessageCount = 0;
        this.expirationStartTimestamp = 0;
        this.linkPreviewFyleId = null;

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
                null,
                LOCATION_TYPE_NONE,
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
                false,
                null,
                null,
                0,
                0,
                false,
                null
        );
    }

    private static Message createInfoMessage(AppDatabase db, int messageType, long discussionId, byte[] senderIdentity, long timestamp, boolean useActualTimestampForSorting) {
        Message message = new Message(
                0,
                null,
                null,
                null,
                null,
                null,
                LOCATION_TYPE_NONE,
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
                false,
                null,
                null,
                0,
                0,
                false,
                null
        );
        if (!useActualTimestampForSorting) {
            message.computeOutboundSortIndex(db);
        }
        return message;
    }

    public static Message createMemberJoinedGroupMessage(AppDatabase db, long discussionId, byte[] bytesMemberIdentity) {
        return createInfoMessage(db, TYPE_GROUP_MEMBER_JOINED, discussionId, bytesMemberIdentity, System.currentTimeMillis(), false);
    }

    public static Message createMemberLeftGroupMessage(AppDatabase db, long discussionId, byte[] bytesMemberIdentity) {
        return createInfoMessage(db, TYPE_GROUP_MEMBER_LEFT, discussionId, bytesMemberIdentity, System.currentTimeMillis(), false);
    }

    public static Message createLeftGroupMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(db, TYPE_LEFT_GROUP, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
    }

    public static Message createContactDeletedMessage(AppDatabase db, long discussionId, byte[] bytesContactIdentity) {
        return createInfoMessage(db, TYPE_CONTACT_DELETED, discussionId, bytesContactIdentity, System.currentTimeMillis(), false);
    }

    public static Message createContactReAddedMessage(AppDatabase db, long discussionId, byte[] bytesContactIdentity) {
        return createInfoMessage(db, TYPE_CONTACT_RE_ADDED, discussionId, bytesContactIdentity, System.currentTimeMillis(), false);
    }

    public static Message createReJoinedGroupMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(db, TYPE_RE_JOINED_GROUP, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
    }

    public static Message createJoinedGroupMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(db, TYPE_JOINED_GROUP, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
    }

    public static Message createGainedGroupAdminMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(db, TYPE_GAINED_GROUP_ADMIN, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
    }

    public static Message createLostGroupAdminMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity) {
        return createInfoMessage(db, TYPE_LOST_GROUP_ADMIN, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
    }

    public static Message createScreenShotDetectedMessage(AppDatabase db, long discussionId, byte[] bytesOwnedIdentity, long serverTimestamp) {
        return createInfoMessage(db, TYPE_SCREEN_SHOT_DETECTED, discussionId, bytesOwnedIdentity, serverTimestamp, false);
    }

    ///////
    // phoneCallStatus uses the call statuses from CallLogItem
    public static Message createPhoneCallMessage(AppDatabase db, long discussionId, byte[] bytesContactIdentity, CallLogItem callLogItem) {
        boolean unread = (callLogItem.callType == CallLogItem.TYPE_INCOMING) && (callLogItem.callStatus == CallLogItem.STATUS_MISSED || callLogItem.callStatus == CallLogItem.STATUS_BUSY);

        Message message = createInfoMessage(db, TYPE_PHONE_CALL, discussionId, bytesContactIdentity, callLogItem.timestamp, true);
        message.contentBody = ((callLogItem.callType == CallLogItem.TYPE_INCOMING) ? callLogItem.callStatus : -callLogItem.callStatus) + ":" + callLogItem.id;
        message.status = unread ? STATUS_UNREAD : STATUS_READ;
        return message;
    }

    public static Message createDiscussionSettingsUpdateMessage(AppDatabase db, long discussionId, DiscussionCustomization.JsonSharedSettings jsonSharedSettings, byte[] bytesIdentityOfInitiator, boolean outbound, Long messageTimestamp) {
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

            Message message = createInfoMessage(db, TYPE_DISCUSSION_SETTINGS_UPDATE, discussionId, bytesIdentityOfInitiator, timestamp, true);
            message.contentBody = serializedSharedSettings;
            message.sortIndex = sortIndex;
            message.status = outbound ? STATUS_UNPROCESSED : STATUS_READ;
            return message;
        } catch (IOException e) {
            return null;
        }
    }

    public static Message createDiscussionRemotelyDeletedMessage(AppDatabase db, long discussionId, byte[] remoteIdentity, long serverTimestamp) {
        return createInfoMessage(db, TYPE_DISCUSSION_REMOTELY_DELETED, discussionId, remoteIdentity, serverTimestamp, false);
    }

    public static Message createNewPublishedDetailsMessage(AppDatabase db, long discussionId, byte[] bytesContactIdentity) {
        Message message = createInfoMessage(db, TYPE_NEW_PUBLISHED_DETAILS, discussionId, bytesContactIdentity, System.currentTimeMillis(), true);
        message.status = STATUS_UNREAD;
        return message;
    }

    public static Message createContactInactiveReasonMessage(AppDatabase db, long discussionId, byte[] bytesContactIdentity, @NonNull ObvContactActiveOrInactiveReason notActiveReason) {
        Message message = createInfoMessage(db, TYPE_CONTACT_INACTIVE_REASON, discussionId, bytesContactIdentity, System.currentTimeMillis(), false);
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

        if (!discussion.canPostMessages()) {
            Logger.e("Trying to delete everywhere in a locked discussion!!! --> locally deleting instead");
            return true;
        }

        List<Contact> contacts;
        switch (discussion.discussionType) {
            case Discussion.TYPE_CONTACT:
                contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier));
                break;
            case Discussion.TYPE_GROUP:
                contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            default:
                Logger.e("Unknown discussion type!!! --> locally deleting instead");
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

        if (!discussion.canPostMessages()) {
            Logger.e("Trying to delete everywhere a locked discussion!!! --> locally deleting instead");
            return true;
        }

        List<Contact> contacts;
        switch (discussion.discussionType) {
            case Discussion.TYPE_CONTACT:
                contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier));
                break;
            case Discussion.TYPE_GROUP:
                contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            default:
                Logger.e("Unknown discussion type!!! --> locally deleting instead");
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

        if (!discussion.canPostMessages()) {
            Logger.e("Trying to update a message in a locked discussion!!! --> locally updating instead");
            return true;
        }

        List<Contact> contacts;
        switch (discussion.discussionType) {
            case Discussion.TYPE_CONTACT:
                contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier));
                break;
            case Discussion.TYPE_GROUP:
                contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            default:
                Logger.e("Unknown discussion type!!! --> locally updating instead");
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
            jsonUpdateMessage.setJsonLocation(updatedMessage.getJsonLocation());
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

        if (!discussion.canPostMessages()) {
            Logger.e("Trying to react a message in a locked discussion!!!");
            return true;
        }

        List<Contact> contacts;
        switch (discussion.discussionType) {
            case Discussion.TYPE_CONTACT:
                contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier));
                break;
            case Discussion.TYPE_GROUP:
                contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
            default:
                Logger.e("Unknown discussion type for reaction!!!");
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
                    true,
                    false
            );

            return postMessageOutput.isMessageSent();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void postSettingsMessage(boolean messageNotInDb, byte[] bytesTargetContactIdentity) {
        if (messageType != TYPE_DISCUSSION_SETTINGS_UPDATE) {
            Logger.e("Called Message.postSettingsMessage for a message of type " + messageType);
            return;
        }
        try {
            AppDatabase db = AppDatabase.getInstance();
            Discussion discussion = db.discussionDao().getById(discussionId);

            if (!discussion.canPostMessages()) {
                Logger.e("Trying to post settings message in a locked discussion!!!");
                return;
            }

            List<Contact> contacts;
            switch (discussion.discussionType) {
                case Discussion.TYPE_CONTACT:
                    contacts = Collections.singletonList(db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier));
                    break;
                case Discussion.TYPE_GROUP:
                    contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    break;
                default:
                    Logger.e("Unknown discussion type for settings message post!!!");
                    return;
            }

            boolean hasChannels = false;
            ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
            for (Contact contact : contacts) {
                if (bytesTargetContactIdentity != null && !Arrays.equals(bytesTargetContactIdentity, contact.bytesContactIdentity)) {
                    continue;
                }
                byteContactIdentities.add(contact.bytesContactIdentity);
                hasChannels |= contact.establishedChannelCount > 0;
            }

            if (hasChannels) {
                ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                        getDiscussionSettingsUpdatePayloadAsBytes(discussion.discussionType, discussion.bytesDiscussionIdentifier),
                        null,
                        new ObvOutboundAttachment[0],
                        byteContactIdentities,
                        discussion.bytesOwnedIdentity,
                        false,
                        false
                );

                if (!postMessageOutput.isMessageSent()) {
                    // sending failed for all contacts, do nothing
                    return;
                }
            }

            if (!messageNotInDb) {
                status = STATUS_SENT;
                db.messageDao().updateStatus(id, status);
            }
        }  catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void post(boolean showToasts, byte[] extendedPayload) {
        if (messageType != TYPE_OUTBOUND_MESSAGE) {
            Logger.e("Called Message.post for a message of type " + messageType);
            return;
        }

        try {
            AppDatabase db = AppDatabase.getInstance();
            Discussion discussion = db.discussionDao().getById(discussionId);

            if (!db.inTransaction()) {
                Logger.e("Called Message.post() outside a transaction");
            }

            if (!discussion.canPostMessages()) {
                Logger.e("Trying to post in a locked discussion!!!");
                return;
            }


            //////////////////
            // first get all attachments
            List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFylesAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);

            // check for still COPYING files --> posting will be retried once copied
            for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : attachmentFylesAndStatuses) {
                if (fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COPYING) {
                    if (showToasts) {
                        App.toast(R.string.toast_message_attachment_being_copied, Toast.LENGTH_LONG);
                    }
                    return;
                }
            }


            ////////////////////
            // compute the contacts to which the message should be sent (the MessageRecipientInfo)
            // compute the contacts to which the message can be send NOW (byteContactIdentitiesToWhichMessageCanBeSentNow)
            HashMap<BytesKey, MessageRecipientInfo> messageRecipientInfoHashMap = new HashMap<>();
            List<byte[]> byteContactIdentitiesToWhichMessageCanBeSentNow = new ArrayList<>();
            boolean markMessageSent = true;

            switch (discussion.discussionType) {
                case Discussion.TYPE_CONTACT: {
                    Contact contact = db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    if (contact != null && contact.active) { // this should almost always be true
                        markMessageSent = false;
                        messageRecipientInfoHashMap.put(new BytesKey(contact.bytesContactIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), contact.bytesContactIdentity));
                        if (contact.establishedChannelCount > 0) {
                            byteContactIdentitiesToWhichMessageCanBeSentNow.add(contact.bytesContactIdentity);
                        } else if (showToasts) {
                            App.toast(R.string.toast_message_no_channel_message_delayed, Toast.LENGTH_SHORT);
                        }
                    }
                    break;
                }
                case Discussion.TYPE_GROUP: {
                    List<Contact> contacts = db.contactGroupJoinDao().getGroupContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    for (Contact contact : contacts) {
                        if (contact.active) {
                            markMessageSent = false;
                            messageRecipientInfoHashMap.put(new BytesKey(contact.bytesContactIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), contact.bytesContactIdentity));
                            if (contact.establishedChannelCount > 0) {
                                byteContactIdentitiesToWhichMessageCanBeSentNow.add(contact.bytesContactIdentity);
                            }
                        }
                    }
                    break;
                }
                case Discussion.TYPE_GROUP_V2: {
                    List<Contact> contacts = db.group2MemberDao().getGroupMemberContactsSync(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    for (Contact contact : contacts) {
                        if (contact.active) {
                            markMessageSent = false;
                            messageRecipientInfoHashMap.put(new BytesKey(contact.bytesContactIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), contact.bytesContactIdentity));
                            if (contact.establishedChannelCount > 0) {
                                byteContactIdentitiesToWhichMessageCanBeSentNow.add(contact.bytesContactIdentity);
                            }
                        }
                    }
                    for (Group2PendingMember group2PendingMember : db.group2PendingMemberDao().getGroupPendingMembers(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier)) {
                        messageRecipientInfoHashMap.put(new BytesKey(group2PendingMember.bytesContactIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), group2PendingMember.bytesContactIdentity));
                    }
                    break;
                }
                default:
                    Logger.e("Unknown discussion type for message post!!!");
                    return;
            }



            /////////////////
            // if markMessageSent, the message has no active contact recipient yet, so it can be marked as sent
            if (markMessageSent) {
                status = STATUS_SENT;
                db.messageDao().updateStatus(id, status);
                for (FyleMessageJoinWithStatusDao.FyleAndStatus attachmentFyleAndStatus : attachmentFylesAndStatuses) {
                    attachmentFyleAndStatus.fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().updateStatus(attachmentFyleAndStatus.fyleMessageJoinWithStatus.messageId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.fyleId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.status);
                }

                // still create the MessageRecipientInfo for GroupV2 pending members
                if (!messageRecipientInfoHashMap.isEmpty()) {
                    db.messageRecipientInfoDao().insert(messageRecipientInfoHashMap.values().toArray(new MessageRecipientInfo[0]));
                }

                // start the expiration timers
                App.runThread(new ExpiringOutboundMessageSent(this));
                return;
            }

            ///////////////
            // there are at least some of our contacts to whom we can send the message --> send it
            boolean attachmentsMarkedAsUploading = false;
            if (!byteContactIdentitiesToWhichMessageCanBeSentNow.isEmpty()) {

                // first build the attachment list
                ObvOutboundAttachment[] attachments = new ObvOutboundAttachment[attachmentFylesAndStatuses.size()];
                boolean hasAttachmentWithPreview = false;
                for (int i = 0; i < attachments.length; i++) {
                    Fyle fyle = attachmentFylesAndStatuses.get(i).fyle;
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFylesAndStatuses.get(i).fyleMessageJoinWithStatus;
                    attachments[i] = new ObvOutboundAttachment(fyle.filePath, fyleMessageJoinWithStatus.size, attachmentFylesAndStatuses.get(i).getMetadata());

                    hasAttachmentWithPreview |= PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleMessageJoinWithStatus.getNonNullMimeType());
                }

                if (hasAttachmentWithPreview && extendedPayload == null) {
                    try {
                        ComputeAttachmentPreviewsAndPostMessageTask computeAttachmentPreviewsAndPostMessageTask = new ComputeAttachmentPreviewsAndPostMessageTask(id, null);
                        App.runThread(computeAttachmentPreviewsAndPostMessageTask);
                    } catch (Exception e) {
                        // nothing to do, this happens if another preview task is already running for this message
                    }
                    return;
                }

                final byte[] returnReceiptNonce = AppSingleton.getEngine().getReturnReceiptNonce();
                final byte[] returnReceiptKey = AppSingleton.getEngine().getReturnReceiptKey();
                final ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                        getMessagePayloadAsBytes(discussion.discussionType, discussion.bytesDiscussionIdentifier, returnReceiptNonce, returnReceiptKey, null),
                        (hasAttachmentWithPreview && extendedPayload.length > 0) ? extendedPayload : null,
                        attachments,
                        byteContactIdentitiesToWhichMessageCanBeSentNow,
                        discussion.bytesOwnedIdentity,
                        true,
                        false
                );


                if (postMessageOutput.isMessageSent()) {
                    // "sending" successful at least for some contacts --> start the sending service
                    UnifiedForegroundService.processPostMessageOutput(postMessageOutput);

                    // update the list of MessageRecipientInfo with the engine output
                    byte[] firstEngineMessageIdentifier = null;
                    for (ObvBytesKey obvBytesKeyContactIdentity : postMessageOutput.getMessageIdentifierByContactIdentity().keySet()) {
                        byte[] engineMessageIdentifier = postMessageOutput.getMessageIdentifierByContactIdentity().get(obvBytesKeyContactIdentity);
                        MessageRecipientInfo messageRecipientInfo = messageRecipientInfoHashMap.get(new BytesKey(obvBytesKeyContactIdentity.getBytes()));

                        if (messageRecipientInfo != null) {
                            messageRecipientInfo.engineMessageIdentifier = engineMessageIdentifier;
                            messageRecipientInfo.returnReceiptNonce = returnReceiptNonce;
                            messageRecipientInfo.returnReceiptKey = returnReceiptKey;
                        }

                        if (firstEngineMessageIdentifier == null && engineMessageIdentifier != null) {
                            firstEngineMessageIdentifier = engineMessageIdentifier;
                        }
                    }

                    // update the engine identifier for the attachments to track upload progress
                    attachmentsMarkedAsUploading = true;
                    for (int i = 0; i < attachments.length; i++) {
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFylesAndStatuses.get(i).fyleMessageJoinWithStatus;
                        fyleMessageJoinWithStatus.engineMessageIdentifier = firstEngineMessageIdentifier;
                        fyleMessageJoinWithStatus.engineNumber = i;
                        db.fyleMessageJoinWithStatusDao().updateEngineIdentifier(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);

                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_UPLOADING;
                        db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                    }
                }
            }

            // if attachments were not marked as STATUS_UPLOADING, mark the attachments as STATUS_COMPLETE
            if (!attachmentsMarkedAsUploading) {
                for (FyleMessageJoinWithStatusDao.FyleAndStatus attachmentFyleAndStatus : attachmentFylesAndStatuses) {
                    attachmentFyleAndStatus.fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().updateStatus(attachmentFyleAndStatus.fyleMessageJoinWithStatus.messageId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.fyleId, attachmentFyleAndStatus.fyleMessageJoinWithStatus.status);
                }
            }

            /////////////
            // - insert all MessageRecipientInfo (whether passed to the engine or not)
            // - update message status as STATUS_PROCESSING
            db.messageRecipientInfoDao().insert(messageRecipientInfoHashMap.values().toArray(new MessageRecipientInfo[0]));

            status = STATUS_PROCESSING;
            db.messageDao().updateStatus(id, status);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void repost(MessageRecipientInfo messageRecipientInfo, byte[] extendedPayload) {
        if (messageType != TYPE_OUTBOUND_MESSAGE) {
            Logger.e("Called Message.repost for a message of type " + messageType);
            return;
        }

        try {
            AppDatabase db = AppDatabase.getInstance();
            Discussion discussion = db.discussionDao().getById(discussionId);
            if (discussion == null
                    || !discussion.canPostMessages()
                    || wipeStatus == WIPE_STATUS_WIPED
                    || wipeStatus == WIPE_STATUS_REMOTE_DELETED) {
                // message cannot be reposted, simply mark the MessageRecipientInfo as processed
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

            // for groupV2, check the recipient is indeed a member
            // also get the "original server timestamp" at which the message was first sent
            Long originalServerTimestamp = null;
            if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                Group2Member group2Member = db.group2MemberDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, messageRecipientInfo.bytesContactIdentity);
                if (group2Member == null) {
                    // not yet a member of the group, do nothing
                    return;
                }
                if (jsonLocation != null && (locationType == LOCATION_TYPE_SHARE || locationType == LOCATION_TYPE_SHARE_FINISHED)) {
                    try {
                        JsonLocation location = AppSingleton.getJsonObjectMapper().readValue(jsonLocation, JsonLocation.class);
                        originalServerTimestamp = location.timestamp;
                    } catch (Exception e) {
                        e.printStackTrace();
                        originalServerTimestamp = db.messageRecipientInfoDao().getOriginalServerTimestampForMessage(messageRecipientInfo.messageId);
                    }
                } else {
                    originalServerTimestamp = db.messageRecipientInfoDao().getOriginalServerTimestampForMessage(messageRecipientInfo.messageId);
                }
            }

            //////////////////
            // first get all attachments
            List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);
            ObvOutboundAttachment[] attachments = new ObvOutboundAttachment[attachmentFyles.size()];
            boolean hasAttachmentWithPreview = false;
            for (int i = 0; i < attachments.length; i++) {
                Fyle fyle = attachmentFyles.get(i).fyle;
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                attachments[i] = new ObvOutboundAttachment(fyle.filePath, fyleMessageJoinWithStatus.size, attachmentFyles.get(i).getMetadata());

                hasAttachmentWithPreview |= PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleMessageJoinWithStatus.getNonNullMimeType());
            }

            if (hasAttachmentWithPreview && extendedPayload == null) {
                try {
                    ComputeAttachmentPreviewsAndPostMessageTask computeAttachmentPreviewsAndPostMessageTask = new ComputeAttachmentPreviewsAndPostMessageTask(id, messageRecipientInfo);
                    App.runThread(computeAttachmentPreviewsAndPostMessageTask);
                } catch (Exception e) {
                    // nothing to do, this happens if another preview task is already running for this message
                }
                return;
            }

            final byte[] returnReceiptNonce = AppSingleton.getEngine().getReturnReceiptNonce();
            final byte[] returnReceiptKey = AppSingleton.getEngine().getReturnReceiptKey();
            final ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    getMessagePayloadAsBytes(discussion.discussionType, discussion.bytesDiscussionIdentifier, returnReceiptNonce, returnReceiptKey, originalServerTimestamp),
                    (hasAttachmentWithPreview && extendedPayload.length > 0) ? extendedPayload : null,
                    attachments,
                    Collections.singletonList(messageRecipientInfo.bytesContactIdentity),
                    discussion.bytesOwnedIdentity,
                    true,
                    false
            );


            if (postMessageOutput.isMessageSent()) {
                UnifiedForegroundService.processPostMessageOutput(postMessageOutput);

                // update the MessageRecipientInfo with the engine output
                byte[] messageIdentifier = postMessageOutput.getMessageIdentifierByContactIdentity().get(new ObvBytesKey(messageRecipientInfo.bytesContactIdentity));
                if (messageIdentifier != null) {
                    messageRecipientInfo.engineMessageIdentifier = messageIdentifier;
                    messageRecipientInfo.returnReceiptNonce = returnReceiptNonce;
                    messageRecipientInfo.returnReceiptKey = returnReceiptKey;
                    db.messageRecipientInfoDao().update(messageRecipientInfo);

                    for (int i = 0; i < attachments.length; i++) {
                        FyleMessageJoinWithStatus fyleMessageJoinWithStatus = attachmentFyles.get(i).fyleMessageJoinWithStatus;
                        fyleMessageJoinWithStatus.engineMessageIdentifier = messageIdentifier;
                        fyleMessageJoinWithStatus.engineNumber = i;
                        db.fyleMessageJoinWithStatusDao().updateEngineIdentifier(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);

                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_UPLOADING;
                        db.fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                    }
                    if (refreshOutboundStatus()) {
                        db.messageDao().updateStatus(id, status);
                    }
                }
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
        if (messageType != TYPE_OUTBOUND_MESSAGE || status == STATUS_UNDELIVERED) {
            return false;
        }
        List<MessageRecipientInfo> messageRecipientInfos = AppDatabase.getInstance().messageRecipientInfoDao().getAllByMessageId(id);
        if (messageRecipientInfos.size() == 0) {
            return false;
        }
        int newStatus = 100000;
        boolean passedToEngineForEveryone = true;
        for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
            if (messageRecipientInfo.engineMessageIdentifier == null) {
                passedToEngineForEveryone = false;
                continue;
            }
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

        // never mark a message as delivered/read if not delivered to all recipients
        if (!passedToEngineForEveryone && newStatus != 100000 && newStatus > STATUS_SENT) {
            newStatus = STATUS_SENT;
        }

        if (newStatus != 100000 && newStatus != status) {
            boolean shouldStartExpiration =
                    (status == STATUS_UNPROCESSED || status == STATUS_PROCESSING || status == STATUS_COMPUTING_PREVIEW)
                    && (newStatus == STATUS_SENT || newStatus == STATUS_DELIVERED || newStatus == STATUS_DELIVERED_AND_READ);
            status = newStatus;
            if (shouldStartExpiration && jsonExpiration != null) {
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
        } else if (isLocationMessage()) {
            // location sharing
            if (locationType == LOCATION_TYPE_SHARE_FINISHED) {
                return context.getString(R.string.text_message_sharing_location_finished);
            } else if (locationType == LOCATION_TYPE_SHARE) {
                if (messageType == TYPE_OUTBOUND_MESSAGE) {
                    return context.getString(R.string.text_message_sharing_location);
                } else {
                    return context.getString(R.string.text_message_receiving_shared_location);
                }
            }
            // location sending
            if (messageType == TYPE_OUTBOUND_MESSAGE) {
                return context.getString(R.string.text_message_location_sent);
            } else {
                return context.getString(R.string.text_message_location_received);
            }
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

    public boolean isLocationMessage() {
        return jsonLocation != null;
    }

    public boolean isForwardable() {
        return (messageType == Message.TYPE_INBOUND_MESSAGE || messageType == Message.TYPE_OUTBOUND_MESSAGE)
                && wipeStatus == Message.WIPE_STATUS_NONE
                && !limitedVisibility;
    }

    // return true if message expired and update locationType field
    public boolean isSharingExpired() {
        if (this.jsonLocation != null && this.locationType == LOCATION_TYPE_SHARE) {
            Long expiration = getJsonLocation().sharingExpiration;
            if (expiration != null) {
                return expiration < System.currentTimeMillis();
            }
        }
        return false;
    }

    public byte[] getMessagePayloadAsBytes(int discussionType, byte[] bytesDiscussionIdentifier, byte[] returnReceiptNonce, byte[] returnReceiptKey, Long originalServerTimestamp) throws Exception {
        JsonMessage jsonMessage = getJsonMessage();
        switch (discussionType) {
            case Discussion.TYPE_GROUP:
                jsonMessage.setGroupOwnerAndUid(bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonMessage.groupV2Identifier = bytesDiscussionIdentifier;
                break;
            case Discussion.TYPE_CONTACT:
            default:
                break;
        }
        jsonMessage.setOriginalServerTimestamp(originalServerTimestamp);
        JsonReturnReceipt jsonReturnReceipt = new JsonReturnReceipt(returnReceiptNonce, returnReceiptKey);
        JsonPayload jsonPayload = new JsonPayload(jsonMessage, jsonReturnReceipt);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    public byte[] getDiscussionSettingsUpdatePayloadAsBytes(int discussionType, byte[] bytesDiscussionIdentifier) throws Exception {
        DiscussionCustomization.JsonSharedSettings jsonSharedSettings = AppSingleton.getJsonObjectMapper().readValue(contentBody, DiscussionCustomization.JsonSharedSettings.class);
        switch (discussionType) {
            case Discussion.TYPE_GROUP:
                jsonSharedSettings.setGroupOwnerAndUid(bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonSharedSettings.groupV2Identifier = bytesDiscussionIdentifier;
                break;
            case Discussion.TYPE_CONTACT:
            default:
                break;
        }
        JsonPayload jsonPayload = new JsonPayload();
        jsonPayload.setJsonSharedSettings(jsonSharedSettings);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    public static byte[] getGroupV2DiscussionQuerySharedSettingsPayloadAsBytes(byte[] bytesGroupIdentifier, Integer knownSharedSettingsVersion, JsonExpiration knownSharedExpiration) throws Exception {
        JsonPayload jsonPayload = new JsonPayload();
        jsonPayload.setJsonQuerySharedSettings(new JsonQuerySharedSettings(bytesGroupIdentifier, knownSharedSettingsVersion, knownSharedExpiration));
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    public JsonLocation getJsonLocation() {
        if (this.jsonLocation != null) {
            try {
                return AppSingleton.getJsonObjectMapper().readValue(this.jsonLocation, JsonLocation.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public JsonMessage getJsonMessage() {
        JsonMessage jsonMessage = new JsonMessage();
        jsonMessage.body = contentBody;
        if (forwarded) {
            jsonMessage.forwarded = true;
        }
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
                // if message was already sent once and has an existence duration, compensate the original existence duration with the elapsed time
                if (expirationStartTimestamp != 0 && jsonMessage.jsonExpiration.existenceDuration != null) {
                    jsonMessage.jsonExpiration.existenceDuration = jsonMessage.jsonExpiration.existenceDuration - (System.currentTimeMillis() - expirationStartTimestamp) / 1000L;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (jsonLocation != null) {
            try {
                switch (locationType) {
                    case LOCATION_TYPE_NONE:
                        break;
                    case LOCATION_TYPE_SEND:
                    case LOCATION_TYPE_SHARE:
                    default:
                        jsonMessage.jsonLocation = AppSingleton.getJsonObjectMapper().readValue(jsonLocation, JsonLocation.class);
                        break;
                    case LOCATION_TYPE_SHARE_FINISHED:
                        JsonLocation location = AppSingleton.getJsonObjectMapper().readValue(jsonLocation, JsonLocation.class);
                        jsonMessage.jsonLocation = new JsonLocation(JsonLocation.TYPE_END_SHARING, null, null, null, location.latitude, location.longitude, location.altitude, location.precision, location.timestamp);
                        break;
                }
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
                limitedVisibility = (jsonMessage.jsonExpiration.readOnce != null && jsonMessage.jsonExpiration.readOnce) || (jsonMessage.jsonExpiration.visibilityDuration != null);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonExpiration = null;
                limitedVisibility = false;
            }
        } else {
            limitedVisibility = false;
        }
        if (jsonMessage.jsonLocation != null) {
            try {
                jsonLocation = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessage.jsonLocation);
                switch (jsonMessage.jsonLocation.getType()) {
                    case JsonLocation.TYPE_SEND:
                        this.locationType = LOCATION_TYPE_SEND;
                        break;
                    case JsonLocation.TYPE_SHARING:
                        this.locationType = LOCATION_TYPE_SHARE;
                        break;
                    case JsonLocation.TYPE_END_SHARING:
                        this.locationType = LOCATION_TYPE_SHARE_FINISHED;
                        break;
                    default:
                        this.locationType = LOCATION_TYPE_NONE;
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonLocation = null;
                locationType = LOCATION_TYPE_NONE;
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
        boolean hasLinkPreview = false;
        for (FyleMessageJoinWithStatus fmjoin : db.fyleMessageJoinWithStatusDao().getStatusesForMessage(this.id)) {
            if (fmjoin.getNonNullMimeType().equals(OpenGraph.MIME_TYPE)) {
                if (!hasLinkPreview) {
                    // always consider only the first link-preview attachment
                    hasLinkPreview = true;
                    if (!Objects.equals(this.linkPreviewFyleId, fmjoin.fyleId)) {
                        this.linkPreviewFyleId = fmjoin.fyleId;
                        db.messageDao().updateLinkPreviewFyleId(this.id, this.linkPreviewFyleId);
                    }
                }
                continue;
            } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(fmjoin.getNonNullMimeType())) {
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
        if (!hasLinkPreview && this.linkPreviewFyleId != null) {
            this.linkPreviewFyleId = null;
            db.messageDao().updateLinkPreviewFyleId(this.id, null);
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

        // stop sharing location if needed
        if (locationType == LOCATION_TYPE_SHARE) {
            UnifiedForegroundService.LocationSharingSubService.stopSharingLocation(App.getContext(), discussionId);
        }

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
                    AppSingleton.getEngine().markAttachmentForDeletion(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
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
        linkPreviewFyleId = null;
        db.messageDao().updateLinkPreviewFyleId(id, null);
    }

    // this method clears the message's body and deletes it if there are no more attachments
    public void wipe(AppDatabase db) {
        if (isInbound() && totalAttachmentCount == 0) {
            delete(db);
        } else {
            contentBody = null;
            jsonReply = null;
            jsonLocation = null;
            locationType = LOCATION_TYPE_NONE;
            edited = EDITED_NONE;
            forwarded = false;
            wipeStatus = WIPE_STATUS_WIPED;
            reactions = null;
            imageResolutions = null;
            limitedVisibility = false;
            db.messageDao().updateWipe(id, WIPE_STATUS_WIPED);
            db.reactionDao().deleteAllForMessage(id);
            db.messageMetadataDao().insert(new MessageMetadata(id, MessageMetadata.KIND_WIPED, System.currentTimeMillis()));
            db.messageExpirationDao().deleteWipeExpiration(id);
        }
    }

    public void remoteDelete(AppDatabase db, @NonNull byte[] bytesRemoteIdentity, long serverTimestamp) {
        contentBody = null;
        jsonReply = null;
        jsonLocation = null;
        locationType = LOCATION_TYPE_NONE;
        edited = EDITED_NONE;
        forwarded = false;
        wipeStatus = WIPE_STATUS_REMOTE_DELETED;
        reactions = null;
        imageResolutions = null;
        limitedVisibility = false;
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
        JsonQuerySharedSettings jsonQuerySharedSettings;
        JsonUpdateMessage jsonUpdateMessage;
        JsonDeleteMessages jsonDeleteMessages;
        JsonDeleteDiscussion jsonDeleteDiscussion;
        JsonReaction jsonReaction;
        JsonScreenCaptureDetection jsonScreenCaptureDetection;

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

        @JsonProperty("qss")
        public JsonQuerySharedSettings getJsonQuerySharedSettings() {
            return jsonQuerySharedSettings;
        }

        @JsonProperty("qss")
        public void setJsonQuerySharedSettings(JsonQuerySharedSettings jsonQuerySharedSettings) {
            this.jsonQuerySharedSettings = jsonQuerySharedSettings;
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

        @JsonProperty("scd")
        public JsonScreenCaptureDetection getJsonScreenCaptureDetection() {
            return jsonScreenCaptureDetection;
        }

        @JsonProperty("scd")
        public void setJsonScreenCaptureDetection(JsonScreenCaptureDetection jsonScreenCaptureDetection) {
            this.jsonScreenCaptureDetection = jsonScreenCaptureDetection;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonMessage {
        String body;
        long senderSequenceNumber;
        UUID senderThreadIdentifier;
        byte[] groupUid;
        byte[] groupOwner;
        byte[] groupV2Identifier;
        Boolean forwarded;
        Long originalServerTimestamp;
        JsonMessageReference jsonReply;
        JsonExpiration jsonExpiration;
        JsonLocation jsonLocation;

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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
        }

        @JsonProperty("fw")
        public Boolean isForwarded() {
            return forwarded;
        }

        @JsonProperty("fw")
        public void setForwarded(Boolean forwarded) {
            this.forwarded = forwarded;
        }

        @JsonProperty("ost")
        public Long getOriginalServerTimestamp() {
            return originalServerTimestamp;
        }

        @JsonProperty("ost")
        public void setOriginalServerTimestamp(Long originalServerTimestamp) {
            this.originalServerTimestamp = originalServerTimestamp;
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

        @JsonProperty("loc")
        public JsonLocation getJsonLocation() {
            return jsonLocation;
        }

        @JsonProperty("loc")
        public void setJsonLocation(JsonLocation jsonLocation) {
            this.jsonLocation = jsonLocation;
        }

        @JsonIgnore
        public boolean isEmpty() {
            return (body == null || body.trim().length() == 0) && jsonReply == null && jsonLocation == null;
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

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
    public static class JsonQuerySharedSettings {
        byte[] groupV2Identifier;
        Integer knownSharedSettingsVersion;
        JsonExpiration knownSharedExpiration;

        public JsonQuerySharedSettings() {
        }

        public JsonQuerySharedSettings(byte[] groupV2Identifier, Integer knownSharedSettingsVersion, JsonExpiration knownSharedExpiration) {
            this.groupV2Identifier = groupV2Identifier;
            this.knownSharedSettingsVersion = knownSharedSettingsVersion;
            this.knownSharedExpiration = knownSharedExpiration;
        }

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
        }

        @JsonProperty("ksv")
        public Integer getKnownSharedSettingsVersion() {
            return knownSharedSettingsVersion;
        }

        @JsonProperty("ksv")
        public void setKnownSharedSettingsVersion(Integer knownSharedSettingsVersion) {
            this.knownSharedSettingsVersion = knownSharedSettingsVersion;
        }

        @JsonProperty("exp")
        public JsonExpiration getKnownSharedExpiration() {
            return knownSharedExpiration;
        }

        @JsonProperty("exp")
        public void setKnownSharedExpiration(JsonExpiration knownSharedExpiration) {
            this.knownSharedExpiration = knownSharedExpiration;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonUpdateMessage {
        String body;
        byte[] groupUid;
        byte[] groupOwner;
        byte[] groupV2Identifier;
        JsonMessageReference messageReference;
        JsonLocation jsonLocation;

        public static JsonUpdateMessage of(Discussion discussion, Message message) throws Exception {
            JsonUpdateMessage jsonUpdateMessage = new JsonUpdateMessage();
            jsonUpdateMessage.messageReference = JsonMessageReference.of(message);
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP:
                    jsonUpdateMessage.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    jsonUpdateMessage.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                    break;
                case Discussion.TYPE_CONTACT:
                default:
                    break;
            }
            return jsonUpdateMessage;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @JsonProperty("loc")
        public JsonLocation getJsonLocation() {
            return jsonLocation;
        }

        @JsonProperty("loc")
        public void setJsonLocation(JsonLocation jsonLocation) {
            this.jsonLocation = jsonLocation;
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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
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
    public static class JsonLocation {
        @JsonIgnore
        public static final int TYPE_SEND = 1;
        @JsonIgnore
        public static final int TYPE_SHARING = 2;
        @JsonIgnore
        public static final int TYPE_END_SHARING = 3;

        // -- message metadata --
        int type;
        long timestamp; // location timestamp
        // -- sharing message fields --
        Long count; // null if not sharing
        Long sharingInterval; // null if not sharing (else in ms)
        Long sharingExpiration; // can be null if endless sharing (else in ms)
        // -- location --
        double latitude;
        double longitude;
        // -- optional metadata --
        Double altitude; // meters (default value null)
        Float precision; // meters (default value null)
        String address; // (default value empty string or null)

        public JsonLocation() {}

        private JsonLocation(int type, @Nullable Long sharingExpiration, @Nullable Long sharingInterval, @Nullable Long count, double latitude, double longitude, Double altitude, Float precision, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.precision = precision;
            this.timestamp = timestamp;
            this.type = type;
            this.count = count;
            this.sharingExpiration = sharingExpiration;
            this.sharingInterval = sharingInterval;
        }

        public static JsonLocation startSharingLocationMessage(@Nullable Long sharingExpiration, @NotNull Long interval, @NotNull Location location) {
            return new JsonLocation(
                    TYPE_SHARING,
                    sharingExpiration,
                    interval,
                    1L,
                    location.getLatitude(),
                    location.getLongitude(),
                    location.hasAltitude() ? location.getAltitude() : null,
                    location.hasAccuracy() ? location.getAccuracy() : null,
                    location.getTime()
            );
        }

        public static JsonLocation updateSharingLocationMessage(@NotNull JsonLocation originalJsonLocation, @NotNull Location location) {
            return new JsonLocation(
                    TYPE_SHARING,
                    originalJsonLocation.getSharingExpiration(),
                    originalJsonLocation.getSharingInterval(),
                    originalJsonLocation.getCount() + 1,
                    location.getLatitude(),
                    location.getLongitude(),
                    location.hasAltitude() ? location.getAltitude() : null,
                    location.hasAccuracy() ? location.getAccuracy() : null,
                    location.getTime()
            );
        }

        public static JsonLocation endOfSharingLocationMessage(@NotNull Long count) {
            JsonLocation endOfSharingJsonLocation = new JsonLocation();
            endOfSharingJsonLocation.type = TYPE_END_SHARING;
            endOfSharingJsonLocation.count = count;
            return endOfSharingJsonLocation;
        }

        public static JsonLocation sendLocationMessage(@NotNull Location location) {
            return new JsonLocation(
                    TYPE_SEND,
                    null,
                    null,
                    null,
                    location.getLatitude(),
                    location.getLongitude(),
                    location.hasAltitude() ? location.getAltitude() : null,
                    location.hasAccuracy() ? location.getAccuracy() : null,
                    location.getTime()
            );
        }

        // ----- sharing message metadata -----
        @JsonProperty("c")
        public Long getCount() { return count; }
        @JsonProperty("c")
        public void setCount(Long count) { this.count = count; }

        @JsonProperty("se")
        public Long getSharingExpiration() { return sharingExpiration; }
        @JsonProperty("se")
        public void setSharingExpiration(Long sharingExpiration) { this.sharingExpiration = sharingExpiration; }

        @JsonProperty("i")
        public Long getSharingInterval() { return sharingInterval; }
        @JsonProperty("i")
        public void setSharingInterval(Long sharingInterval) { this.sharingInterval = sharingInterval; }

        // -- message metadata --
        @JsonProperty("t")
        public int getType() { return type; }
        @JsonProperty("t")
        public void setType(int type) { this.type = type; }

        @JsonProperty("ts")
        public long getTimestamp() { return timestamp; }
        @JsonProperty("ts")
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        // ----- location -----
        @JsonProperty("long")
        public double getLongitude() { return longitude; }
        @JsonProperty("long")
        public void setLongitude(double longitude) { this.longitude = longitude; }

        @JsonProperty("lat")
        public double getLatitude() { return latitude; }
        @JsonProperty("lat")
        public void setLatitude(double latitude) { this.latitude = latitude; }

        // ----- optional metadata -----
        @JsonProperty("alt")
        public Double getAltitude() { return altitude; }
        @JsonProperty("alt")
        public void setAltitude(Double altitude) { this.altitude = altitude; }

        @JsonProperty("prec")
        public Float getPrecision() { return precision; }
        @JsonProperty("prec")
        public void setPrecision(Float accuracy) { this.precision = accuracy; }

        @JsonProperty("add")
        public String getAddress() { return address; }
        @JsonProperty("add")
        public void setAddress(String address) { this.address = address; }


        ////////////
        // formatters

        private final static DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
        private final static DecimalFormat truncated5 = new DecimalFormat("#0.00000", decimalSymbols);
        private final static DecimalFormat truncated1 = new DecimalFormat("#0.0", decimalSymbols);
        private final static DecimalFormat truncated0 = new DecimalFormat("#0", decimalSymbols);

        @JsonIgnore
        public String getLocationMessageBody() {
            return "https://maps.google.com/?q=" + this.getTruncatedLatitudeString() + "+" + this.getTruncatedLongitudeString();
        }

        @JsonIgnore
        public String getTruncatedLatitudeString() {
            return truncated5.format(latitude);
        }

        @JsonIgnore
        public String getTruncatedLongitudeString() {
            return truncated5.format(longitude);
        }

        @JsonIgnore
        public String getTruncatedPrecisionString(Context context) {
            if (precision == null) {
                return "-";
            }
            return context.getString(R.string.xx_meters, truncated1.format(precision));
        }

        @JsonIgnore
        public String getTruncatedAltitudeString(Context context) {
            if (altitude == null) {
                return "-";
            }
            return context.getString(R.string.xx_meters, truncated0.format(altitude));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonReaction {
        @Nullable String reaction; // reaction is null to remove previous reaction
        byte[] groupUid;
        byte[] groupOwner;
        byte[] groupV2Identifier;
        JsonMessageReference messageReference;

        public static JsonReaction of(Discussion discussion, Message message) throws Exception {
            JsonReaction jsonReaction = new JsonReaction();

            jsonReaction.messageReference = JsonMessageReference.of(message);
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP:
                    jsonReaction.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    jsonReaction.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                    break;
                case Discussion.TYPE_CONTACT:
                default:
                    break;
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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
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
        byte[] groupV2Identifier;
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
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP:
                    jsonDeleteMessages.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    jsonDeleteMessages.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                    break;
                case Discussion.TYPE_CONTACT:
                default:
                    break;
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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
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
        byte[] groupV2Identifier;

        public static JsonDeleteDiscussion of(Discussion discussion) throws Exception {
            JsonDeleteDiscussion jsonDeleteDiscussion = new JsonDeleteDiscussion();
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP:
                    jsonDeleteDiscussion.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    jsonDeleteDiscussion.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                    break;
                case Discussion.TYPE_CONTACT:
                default:
                    break;
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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
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
    public static class JsonScreenCaptureDetection {
        byte[] groupUid;
        byte[] groupOwner;
        byte[] groupV2Identifier;

        public static JsonScreenCaptureDetection of(Discussion discussion) throws Exception {
            JsonScreenCaptureDetection jsonScreenCaptureDetection = new JsonScreenCaptureDetection();
            switch (discussion.discussionType) {
                case Discussion.TYPE_GROUP:
                    jsonScreenCaptureDetection.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    jsonScreenCaptureDetection.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                    break;
                case Discussion.TYPE_CONTACT:
                default:
                    break;
            }
            return jsonScreenCaptureDetection;
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

        @JsonProperty("gid2")
        public byte[] getGroupV2Identifier() {
            return groupV2Identifier;
        }

        @JsonProperty("gid2")
        public void setGroupV2Identifier(byte[] groupV2Identifier) {
            this.groupV2Identifier = groupV2Identifier;
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
