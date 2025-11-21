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

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.FyleProgressSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.UnreadCountsSingleton;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.ContactCacheSingleton;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.entity.jsons.JsonDeleteDiscussion;
import io.olvid.messenger.databases.entity.jsons.JsonDeleteMessages;
import io.olvid.messenger.databases.entity.jsons.JsonDiscussionRead;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonLimitedVisibilityMessageOpened;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference;
import io.olvid.messenger.databases.entity.jsons.JsonOneToOneMessageIdentifier;
import io.olvid.messenger.databases.entity.jsons.JsonPayload;
import io.olvid.messenger.databases.entity.jsons.JsonPoll;
import io.olvid.messenger.databases.entity.jsons.JsonQuerySharedSettings;
import io.olvid.messenger.databases.entity.jsons.JsonReaction;
import io.olvid.messenger.databases.entity.jsons.JsonReturnReceipt;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.entity.jsons.JsonUpdateMessage;
import io.olvid.messenger.databases.entity.jsons.JsonUserMention;
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
                @Index(Message.INBOUND_MESSAGE_ENGINE_IDENTIFIER),
                @Index(Message.LOCATION_TYPE),
                @Index(value = {Message.MESSAGE_TYPE, Message.STATUS}),
                @Index(value = {Message.DISCUSSION_ID, Message.SORT_INDEX}),
                @Index(value = {Message.DISCUSSION_ID, Message.STATUS, Message.SORT_INDEX}),
                @Index(value = {Message.SENDER_SEQUENCE_NUMBER, Message.SENDER_THREAD_IDENTIFIER, Message.SENDER_IDENTIFIER, Message.DISCUSSION_ID}),
        }
)
public class Message {
    public static final String TABLE_NAME = "message_table";
    public static final String FTS_TABLE_NAME = "message_table_fts";

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
    public static final String INBOUND_MESSAGE_ENGINE_IDENTIFIER = "engine_message_identifier";
    public static final String SENDER_IDENTIFIER = "sender_identifier";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String TOTAL_ATTACHMENT_COUNT = "total_attachment_count";
    public static final String IMAGE_AND_VIDEO_COUNT = "image_count";
    public static final String VIDEO_COUNT = "video_count";
    public static final String AUDIO_COUNT = "audio_count";
    public static final String FIRST_ATTACHMENT_NAME = "first_attachment_name";
    public static final String WIPED_ATTACHMENT_COUNT = "wiped_attachment_count";
    public static final String EDITED = "edited";
    public static final String FORWARDED = "forwarded";
    public static final String MENTIONED = "mentioned";
    public static final String BOOKMARKED = "bookmarked";
    public static final String REACTIONS = "reactions";
    public static final String IMAGE_RESOLUTIONS = "image_resolutions"; // null or "" = no images, "102x234;a340x445" = 2 images, second one is animated
    public static final String MISSED_MESSAGE_COUNT = "missed_message_count"; // only used in inbound messages: the number of missing sequence numbers when this message is received. 0 --> everything is fine
    public static final String EXPIRATION_START_TIMESTAMP = "expiration_start_timestamp"; // set when the message is first marked as sent --> this is when expirations are started. Only set for message with an expiration
    public static final String LIMITED_VISIBILITY = "limited_visibility"; // true for read_once messages and messages with a visibility_duration
    public static final String LINK_PREVIEW_FYLE_ID = "link_preview_fyle_id"; // id of attached link preview
    public static final String JSON_MENTIONS = "json_mentions"; // this is also used to store the list of group members that were added/removed from a group
    public static final String JSON_POLL = "json_poll";


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
    public static final int STATUS_SENT_FROM_ANOTHER_DEVICE = 10; // for outbound messages sent from another device. If some day we synchronize outbound status, this should no longer be used.
    public static final int STATUS_DELIVERED_ALL = 11;
    public static final int STATUS_DELIVERED_ALL_READ_ONE = 12;
    public static final int STATUS_DELIVERED_ALL_READ_ALL = 13;


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
    public static final int TYPE_MEDIATOR_INVITATION_SENT = 18;
    public static final int TYPE_MEDIATOR_INVITATION_ACCEPTED = 19;
    public static final int TYPE_MEDIATOR_INVITATION_IGNORED = 20;
    public static final int TYPE_GAINED_GROUP_SEND_MESSAGE = 21;
    public static final int TYPE_LOST_GROUP_SEND_MESSAGE = 22;


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

    @ColumnInfo(name = INBOUND_MESSAGE_ENGINE_IDENTIFIER)
    @Nullable
    public byte[] inboundMessageEngineIdentifier;

    @ColumnInfo(name = SENDER_IDENTIFIER)
    @NonNull
    public byte[] senderIdentifier;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = TOTAL_ATTACHMENT_COUNT)
    public int totalAttachmentCount;

    @ColumnInfo(name = IMAGE_AND_VIDEO_COUNT)
    public int imageAndVideoCount; // For legacy reasons, this is actually the number of images AND videos. The actual image count is imageCount - videoCount

    @ColumnInfo(name = VIDEO_COUNT)
    public int videoCount;

    @ColumnInfo(name = AUDIO_COUNT)
    public int audioCount;

    @ColumnInfo(name = FIRST_ATTACHMENT_NAME)
    @Nullable
    public String firstAttachmentName;

    @ColumnInfo(name = WIPED_ATTACHMENT_COUNT)
    public int wipedAttachmentCount;

    @ColumnInfo(name = EDITED)
    public int edited;

    @ColumnInfo(name = FORWARDED)
    public boolean forwarded;

    @ColumnInfo(name = MENTIONED)
    public boolean mentioned;

    @ColumnInfo(name = BOOKMARKED)
    public boolean bookmarked;

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
    @Nullable
    public Long linkPreviewFyleId;

    @ColumnInfo(name = JSON_MENTIONS)
    @Nullable
    public String jsonMentions;

    @ColumnInfo(name = JSON_POLL)
    @Nullable
    public String jsonPoll;

    public boolean hasAttachments() {
        return totalAttachmentCount > 0;
    }

    // default constructor required by Room
    public Message(long senderSequenceNumber, @Nullable String contentBody, @Nullable String jsonReply, @Nullable String jsonExpiration, @Nullable String jsonReturnReceipt, @Nullable String jsonLocation, int locationType, double sortIndex, long timestamp, int status, int wipeStatus, int messageType, long discussionId, @Nullable byte[] inboundMessageEngineIdentifier, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, int totalAttachmentCount, int imageAndVideoCount, int videoCount, int audioCount, @Nullable String firstAttachmentName, int wipedAttachmentCount, int edited, boolean forwarded, boolean mentioned, boolean bookmarked, @Nullable String reactions, @Nullable String imageResolutions, long missedMessageCount, long expirationStartTimestamp, boolean limitedVisibility, @Nullable Long linkPreviewFyleId, @Nullable String jsonMentions, @Nullable String jsonPoll) {
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
        this.inboundMessageEngineIdentifier = inboundMessageEngineIdentifier;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.totalAttachmentCount = totalAttachmentCount;
        this.imageAndVideoCount = imageAndVideoCount;
        this.videoCount = videoCount;
        this.audioCount = audioCount;
        this.firstAttachmentName = firstAttachmentName;
        this.wipedAttachmentCount = wipedAttachmentCount;
        this.edited = edited;
        this.forwarded = forwarded;
        this.mentioned = mentioned;
        this.bookmarked = bookmarked;
        this.reactions = reactions;
        this.imageResolutions = imageResolutions;
        this.missedMessageCount = missedMessageCount;
        this.expirationStartTimestamp = expirationStartTimestamp;
        this.limitedVisibility = limitedVisibility;
        this.linkPreviewFyleId = linkPreviewFyleId;
        this.jsonMentions = jsonMentions;
        this.jsonPoll = jsonPoll;
    }




    /////////////////////
    // constructor used for inbound and outbound messages
    /////////////////////
    @Ignore
    public Message(AppDatabase db, long senderSequenceNumber, @NonNull JsonMessage jsonMessage, JsonReturnReceipt jsonReturnReceipt, long timestamp, int status, int messageType, long discussionId, @Nullable byte[] inboundMessageEngineIdentifier, @NonNull byte[] senderIdentifier, @NonNull UUID senderThreadIdentifier, int totalAttachmentCount, int imageAndVideoCount, int videoCount, int audioCount, @Nullable String firstAttachmentName) {
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
        this.inboundMessageEngineIdentifier = inboundMessageEngineIdentifier;
        this.senderIdentifier = senderIdentifier;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.totalAttachmentCount = totalAttachmentCount;
        this.imageAndVideoCount = imageAndVideoCount;
        this.videoCount = videoCount;
        this.audioCount = audioCount;
        this.firstAttachmentName = firstAttachmentName;
        this.wipedAttachmentCount = 0;
        this.edited = EDITED_NONE;
        this.forwarded = false;
        this.mentioned = false;
        this.bookmarked = false;
        this.reactions = null;
        this.imageResolutions = null;
        this.missedMessageCount = 0;
        this.expirationStartTimestamp = 0;
        this.linkPreviewFyleId = null;

        if (messageType == TYPE_OUTBOUND_MESSAGE && status != STATUS_SENT_FROM_ANOTHER_DEVICE) {
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
            Double maxSortIndex = db.messageDao().getDiscussionMaxSortIndex(discussionId);
            sortIndex = ((maxSortIndex == null) ? 0 : maxSortIndex) + 10; // append outbound messages 10ms in the future --> better Message ordering
        }
    }

    public static Message createEmptyDraft(long discussionId, byte[] senderIdentifier, UUID senderThreadIdentifier) {
        return new Message(
                0,
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
                0, 0, 0, 0, null, 0,
                EDITED_NONE,
                false,
                false,
                false,
                null,
                null,
                0,
                0,
                false,
                null,
                null,
                null
        );
    }

    // a zero-length senderIdentity indicates no one is responsible for this member entering/leaving the group
    private static Message createOrMergeInfoMessage(AppDatabase db, int messageType, long discussionId, @NonNull byte[] senderIdentity, @NonNull byte[] mention, long timestamp, boolean neverMerge) {
        String jsonMention = null;
        if (messageType == TYPE_GROUP_MEMBER_JOINED || messageType == TYPE_GROUP_MEMBER_LEFT || messageType == TYPE_JOINED_GROUP || messageType == TYPE_RE_JOINED_GROUP) {
            try {
                if (!neverMerge) {
                    // Consolidate added or removed members messages with the previous message
                    Message lastMessage = db.messageDao().getPreviousMessageBySortIndex((double) timestamp, discussionId);
                    if (lastMessage != null
                            && lastMessage.messageType == messageType
                            && lastMessage.jsonMentions != null
                            && Arrays.equals(senderIdentity, lastMessage.senderIdentifier)
                    ) {
                        List<JsonUserMention> mentions = AppSingleton.getJsonObjectMapper().readValue(lastMessage.jsonMentions, new TypeReference<>() {
                        });
                        mentions.add(new JsonUserMention(mention, 0, 0));
                        jsonMention = AppSingleton.getJsonObjectMapper().writeValueAsString(mentions);
                        lastMessage.jsonMentions = jsonMention;
                        lastMessage.timestamp = Math.max(lastMessage.timestamp, timestamp); // also update the message timestamp, but never in the past
                        return lastMessage;
                    }
                }

                // if no previous message was found, or if neverMerge is true, simply create a new json from scratch
                jsonMention = AppSingleton.getJsonObjectMapper().writeValueAsString(new JsonUserMention[]{new JsonUserMention(mention, 0, 0)});
            } catch (Exception ignored) { }
        }

        if (jsonMention == null) {
            // if jsonMention cannot be build, revert to the traditional info message type
            if (messageType == TYPE_GROUP_MEMBER_LEFT && neverMerge) {
                // Special case for TYPE_GROUP_MEMBER_LEFT which can also be disguised TYPE_LEFT_GROUP messages!
                return createInfoMessage(db, TYPE_LEFT_GROUP, discussionId, mention, timestamp, false);
            } else {
                return createInfoMessage(db, messageType, discussionId, mention, timestamp, false);
            }
        }

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
                0, 0, 0, 0, null, 0,
                EDITED_NONE,
                false,
                false,
                false,
                null,
                null,
                0,
                0,
                false,
                null,
                jsonMention,
                null
        );
        message.computeOutboundSortIndex(db);
        return message;
    }


    private static Message createInfoMessage(AppDatabase db, int messageType, long discussionId, @NonNull byte[] senderIdentity, long timestamp, boolean useActualTimestampForSorting) {
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
                0, 0, 0, 0, null, 0,
                EDITED_NONE,
                false,
                false,
                false,
                null,
                null,
                0,
                0,
                false,
                null,
                null,
                null
        );
        if (!useActualTimestampForSorting) {
            message.computeOutboundSortIndex(db);
        }
        return message;
    }

    public static Message createMemberJoinedGroupMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesMemberIdentity, @Nullable byte[] addedBy, long groupUpdateTimestamp) {
        return createOrMergeInfoMessage(db, TYPE_GROUP_MEMBER_JOINED, discussionId, (addedBy == null) ? new byte[0] : addedBy, bytesMemberIdentity, groupUpdateTimestamp, false);
    }

    public static Message createMemberLeftGroupMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesMemberIdentity, @Nullable byte[] removedBy, long groupUpdateTimestamp) {
        return createOrMergeInfoMessage(db, TYPE_GROUP_MEMBER_LEFT, discussionId, (removedBy == null) ? new byte[0] : removedBy, bytesMemberIdentity, groupUpdateTimestamp, false);
    }

    public static Message createLeftGroupMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, @Nullable byte[] removedBy) {
        if (removedBy == null) {
            return createInfoMessage(db, TYPE_LEFT_GROUP, discussionId, bytesOwnedIdentity, System.currentTimeMillis(), false);
        } else {
            return createOrMergeInfoMessage(db, TYPE_GROUP_MEMBER_LEFT, discussionId, removedBy, bytesOwnedIdentity, System.currentTimeMillis(), true);
        }
    }

    public static Message createContactDeletedMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesContactIdentity) {
        return createInfoMessage(db, TYPE_CONTACT_DELETED, discussionId, bytesContactIdentity, System.currentTimeMillis(), false);
    }

    public static Message createContactReAddedMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesContactIdentity) {
        return createInfoMessage(db, TYPE_CONTACT_RE_ADDED, discussionId, bytesContactIdentity, System.currentTimeMillis(), false);
    }

    public static Message createReJoinedGroupMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, @Nullable byte[] inviterIdentity, long groupUpdateTimestamp) {
        if (inviterIdentity != null) {
            return createOrMergeInfoMessage(db, TYPE_RE_JOINED_GROUP, discussionId, inviterIdentity, bytesOwnedIdentity, groupUpdateTimestamp, false);
        } else {
            return createInfoMessage(db, TYPE_RE_JOINED_GROUP, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
        }
    }

    public static Message createJoinedGroupMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, @Nullable byte[] inviterIdentity, long groupUpdateTimestamp) {
        if (inviterIdentity != null) {
            return createOrMergeInfoMessage(db, TYPE_JOINED_GROUP, discussionId, inviterIdentity, bytesOwnedIdentity, groupUpdateTimestamp, false);
        } else {
            return createInfoMessage(db, TYPE_JOINED_GROUP, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
        }
    }

    public static Message createGainedGroupAdminMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, long groupUpdateTimestamp) {
        return createInfoMessage(db, TYPE_GAINED_GROUP_ADMIN, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
    }

    public static Message createLostGroupAdminMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, long groupUpdateTimestamp) {
        return createInfoMessage(db, TYPE_LOST_GROUP_ADMIN, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
    }

    public static Message createGainedGroupSendMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, long groupUpdateTimestamp) {
        return createInfoMessage(db, TYPE_GAINED_GROUP_SEND_MESSAGE, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
    }

    public static Message createLostGroupSendMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesOwnedIdentity, long groupUpdateTimestamp) {
        return createInfoMessage(db, TYPE_LOST_GROUP_SEND_MESSAGE, discussionId, bytesOwnedIdentity, groupUpdateTimestamp, false);
    }

    public static Message createScreenShotDetectedMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesIdentity, long serverTimestamp) {
        return createInfoMessage(db, TYPE_SCREEN_SHOT_DETECTED, discussionId, bytesIdentity, serverTimestamp, false);
    }

    public static Message createMediatorInvitationMessage(@NonNull AppDatabase db, int type, long discussionId, @NonNull byte[] bytesOwnedIdentity, @NonNull String displayName, long serverTimestamp) {
        Message message = createInfoMessage(db, type, discussionId, bytesOwnedIdentity, serverTimestamp, false);
        message.contentBody = displayName;
        return message;
    }

    ///////
    // phoneCallStatus uses the call statuses from CallLogItem
    public static Message createPhoneCallMessage(@NonNull AppDatabase db, long discussionId, @NonNull byte[] bytesContactIdentity, @NonNull CallLogItem callLogItem) {
        boolean unread = (callLogItem.callType == CallLogItem.TYPE_INCOMING) && (callLogItem.callStatus == CallLogItem.STATUS_MISSED || callLogItem.callStatus == CallLogItem.STATUS_BUSY);

        Message message = createInfoMessage(db, TYPE_PHONE_CALL, discussionId, bytesContactIdentity, callLogItem.timestamp, true);
        message.contentBody = ((callLogItem.callType == CallLogItem.TYPE_INCOMING) ? callLogItem.callStatus : -callLogItem.callStatus) + ":" + callLogItem.id;
        message.status = unread ? STATUS_UNREAD : STATUS_READ;
        return message;
    }

    public static Message createDiscussionSettingsUpdateMessage(AppDatabase db, long discussionId, JsonSharedSettings jsonSharedSettings, byte[] bytesIdentityOfInitiator, boolean outbound, Long messageTimestamp) {
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

    public static Message createNewPublishedDetailsMessage(AppDatabase db, long discussionId, byte[] senderIdentity) {
        Message message = createInfoMessage(db, TYPE_NEW_PUBLISHED_DETAILS, discussionId, senderIdentity, System.currentTimeMillis(), true);
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

    public static boolean postDeleteMessagesEverywhereMessage(long discussionId, List<Message> messages, boolean onlyDeleteFromOwnedDevices) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);

        if (discussion == null) {
            return false;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>();
        if (discussion.isNormalOrReadOnly() && !onlyDeleteFromOwnedDevices) {
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
                    Logger.e("Unknown discussion type!!! --> postDeleteMessagesEverywhereMessage() is not possible");
                    contacts = Collections.emptyList();
                    break;
            }

            for (Contact contact : contacts) {
                byteContactIdentities.add(contact.bytesContactIdentity);
            }
        }

        // also notify other owned devices
        if (db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
            byteContactIdentities.add(discussion.bytesOwnedIdentity);
        }

        // for group discussions with no members (or discussion with self)
        if (byteContactIdentities.isEmpty()) {
            return true;
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
                    true,
                    false
            );

            return postMessageOutput.isMessagePostedForAtLeastOneContact();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean postDeleteDiscussionEverywhereMessage(long discussionId, boolean onlyDeleteFromOwnedDevices) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(discussionId);

        if (discussion == null) {
            return false;
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>();
        if (discussion.isNormalOrReadOnly() && !onlyDeleteFromOwnedDevices) {

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
                    Logger.e("Unknown discussion type!!! --> postDeleteDiscussionEverywhereMessage() is not possible");
                    contacts = Collections.emptyList();
                    break;
            }

            for (Contact contact : contacts) {
                byteContactIdentities.add(contact.bytesContactIdentity);
            }
        }

        // also notify other owned devices
        if (db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
            byteContactIdentities.add(discussion.bytesOwnedIdentity);
        }

        // for group discussions with no members (or discussion with self)
        if (byteContactIdentities.isEmpty()) {
            return true;
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
                    true,
                    false
            );

            return postMessageOutput.isMessagePostedForAtLeastOneContact();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @NonNull
    public static ObvPostMessageOutput postUpdateMessageMessage(Message updatedMessage) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(updatedMessage.discussionId);

        if (discussion == null || !discussion.isNormal()) {
            Logger.e("Trying to update a message in a locked discussion!!! --> locally updating instead");
            return new ObvPostMessageOutput(true, new HashMap<>());
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
                return new ObvPostMessageOutput(true, new HashMap<>());
        }

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        // also notify other owned devices
        if (db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
            byteContactIdentities.add(discussion.bytesOwnedIdentity);
        }

        // for group discussions with no members (or discussion with self)
        if (byteContactIdentities.isEmpty()) {
            return new ObvPostMessageOutput(true, new HashMap<>());
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
                    true,
                    false
            );

            if (postMessageOutput.isMessagePostedForAtLeastOneContact()) {
                UnifiedForegroundService.processPostMessageOutput(postMessageOutput);
            }

            return postMessageOutput;
        } catch (Exception e) {
            e.printStackTrace();
            return new ObvPostMessageOutput(false, new HashMap<>());
        }
    }

    public static boolean postReactionMessage(Message message, @Nullable String emoji) {
        AppDatabase db = AppDatabase.getInstance();
        Discussion discussion = db.discussionDao().getById(message.discussionId);

        if (discussion == null || !discussion.isNormalOrReadOnly()) {
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

        ArrayList<byte[]> byteContactIdentities = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            byteContactIdentities.add(contact.bytesContactIdentity);
        }

        // also notify other owned devices
        if (db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
            byteContactIdentities.add(discussion.bytesOwnedIdentity);
        }

        // for group discussions with no members (or discussion with self)
        if (byteContactIdentities.isEmpty()) {
            return true;
        }

        try {
            JsonReaction jsonReaction = JsonReaction.of(discussion, message);
            jsonReaction.reaction = emoji;
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

            return postMessageOutput.isMessagePostedForAtLeastOneContact();
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

            if (discussion == null || !discussion.isNormalOrReadOnly()) {
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
                hasChannels |= contact.hasChannelOrPreKey();
            }

            // also notify other owned devices
            if ((bytesTargetContactIdentity == null || Arrays.equals(bytesTargetContactIdentity, discussion.bytesOwnedIdentity)
                    && db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity))) {
                byteContactIdentities.add(discussion.bytesOwnedIdentity);
                hasChannels = true;
            }

            if (hasChannels) {
                ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                        getDiscussionSettingsUpdatePayloadAsBytes(discussion.discussionType, discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier),
                        null,
                        new ObvOutboundAttachment[0],
                        byteContactIdentities,
                        discussion.bytesOwnedIdentity,
                        false,
                        false
                );

                if (!postMessageOutput.isMessagePostedForAtLeastOneContact()) {
                    // sending failed for all contacts, do nothing
                    return;
                }
            }

            if (!messageNotInDb) {
                status = STATUS_SENT;
                db.messageDao().updateStatus(id, status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void postDiscussionReadMessage(@NonNull Discussion discussion, long timestamp) {
        try {
            JsonDiscussionRead discussionRead = JsonDiscussionRead.of(discussion);
            discussionRead.setLastReadMessageServerTimestamp(timestamp);

            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonDiscussionRead(discussionRead);

            byte[] messagePayload = AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
            AppSingleton.getEngine().post(messagePayload,
                    null,
                    new ObvOutboundAttachment[0],
                    Collections.singletonList(discussion.bytesOwnedIdentity),
                    discussion.bytesOwnedIdentity,
                    true,
                    false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void postLimitedVisibilityMessageOpenedMessage(@NonNull Discussion discussion, @NonNull Message message) {
        try {
            JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened = JsonLimitedVisibilityMessageOpened.of(discussion);
            jsonLimitedVisibilityMessageOpened.setMessageReference(JsonMessageReference.of(message));

            JsonPayload jsonPayload = new JsonPayload();
            jsonPayload.setJsonLimitedVisibilityMessageOpened(jsonLimitedVisibilityMessageOpened);

            byte[] messagePayload = AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
            AppSingleton.getEngine().post(messagePayload,
                    null,
                    new ObvOutboundAttachment[0],
                    Collections.singletonList(discussion.bytesOwnedIdentity),
                    discussion.bytesOwnedIdentity,
                    false,
                    false);
        } catch (Exception e) {
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

            if (discussion == null || !discussion.isNormal()) {
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
                } else {
                    FyleProgressSingleton.INSTANCE.updateProgress(fyleAndStatus.fyle.id, fyleAndStatus.fyleMessageJoinWithStatus.messageId, 0, null);
                }
            }


            ////////////////////
            // compute the contacts to which the message should be sent (the MessageRecipientInfo)
            // compute the contacts to which the message can be sent NOW (byteContactIdentitiesToWhichMessageCanBeSentNow)
            HashMap<BytesKey, MessageRecipientInfo> messageRecipientInfoHashMap = new HashMap<>();
            List<byte[]> byteContactIdentitiesToWhichMessageCanBeSentNow = new ArrayList<>();
            boolean markMessageSent = true;

            switch (discussion.discussionType) {
                case Discussion.TYPE_CONTACT: {
                    Contact contact = db.contactDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    if (contact != null && contact.active) { // this should almost always be true
                        markMessageSent = false;
                        messageRecipientInfoHashMap.put(new BytesKey(contact.bytesContactIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), contact.bytesContactIdentity));
                        if (contact.hasChannelOrPreKey()) {
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
                            if (contact.hasChannelOrPreKey()) {
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
                            if (contact.hasChannelOrPreKey()) {
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

            // wait 2 seconds, then index all file contents
            App.runThread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) { }
                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : attachmentFylesAndStatuses) {
                    try {
                        fyleAndStatus.fyleMessageJoinWithStatus.computeTextContentForFullTextSearch(db, fyleAndStatus.fyle);
                    } catch (Exception ignored) { }
                }
            });

            /////////////////
            // if I have other devices, also add a recipient info for myself
            if (db.ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
                messageRecipientInfoHashMap.put(new BytesKey(discussion.bytesOwnedIdentity), new MessageRecipientInfo(id, attachmentFylesAndStatuses.size(), discussion.bytesOwnedIdentity));
                byteContactIdentitiesToWhichMessageCanBeSentNow.add(discussion.bytesOwnedIdentity);
                markMessageSent = false;
            }


            /////////////////
            // if markMessageSent, the message has no active contact recipient yet, so it can be marked as sent
            if (markMessageSent) {
                status = STATUS_SENT;
                db.messageDao().updateStatus(id, status);
                for (FyleMessageJoinWithStatusDao.FyleAndStatus attachmentFyleAndStatus : attachmentFylesAndStatuses) {
                    attachmentFyleAndStatus.fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().update(attachmentFyleAndStatus.fyleMessageJoinWithStatus);
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
                        getMessagePayloadAsBytes(discussion.discussionType, discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, returnReceiptNonce, returnReceiptKey, null),
                        (hasAttachmentWithPreview && extendedPayload.length > 0) ? extendedPayload : null,
                        attachments,
                        byteContactIdentitiesToWhichMessageCanBeSentNow,
                        discussion.bytesOwnedIdentity,
                        true,
                        false
                );


                if (postMessageOutput.isMessagePostedForAtLeastOneContact()) {
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
                        db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
                    }
                }
            }

            // if attachments were not marked as STATUS_UPLOADING, mark the attachments as STATUS_COMPLETE
            if (!attachmentsMarkedAsUploading) {
                for (FyleMessageJoinWithStatusDao.FyleAndStatus attachmentFyleAndStatus : attachmentFylesAndStatuses) {
                    attachmentFyleAndStatus.fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_COMPLETE;
                    db.fyleMessageJoinWithStatusDao().update(attachmentFyleAndStatus.fyleMessageJoinWithStatus);
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
                    || !discussion.isNormal()
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

            // Check the existing message recipient info to reuse the same nonce and key:
            // - this is required for delivery status on other owned devices which do not receive this message.
            // - if no message was sent yet, pick a random one
            MessageRecipientInfo mriForNonceAndKey = db.messageRecipientInfoDao().getFirstWithNonceAndKeyForMessage(messageRecipientInfo.messageId);
            final byte[] returnReceiptNonce;
            final byte[] returnReceiptKey;
            if (mriForNonceAndKey == null) {
                returnReceiptNonce = AppSingleton.getEngine().getReturnReceiptNonce();
                returnReceiptKey = AppSingleton.getEngine().getReturnReceiptKey();
            } else {
                returnReceiptNonce = mriForNonceAndKey.returnReceiptNonce;
                returnReceiptKey = mriForNonceAndKey.returnReceiptKey;
            }
            final ObvPostMessageOutput postMessageOutput = AppSingleton.getEngine().post(
                    getMessagePayloadAsBytes(discussion.discussionType, discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, returnReceiptNonce, returnReceiptKey, originalServerTimestamp),
                    (hasAttachmentWithPreview && extendedPayload.length > 0) ? extendedPayload : null,
                    attachments,
                    Collections.singletonList(messageRecipientInfo.bytesContactIdentity),
                    discussion.bytesOwnedIdentity,
                    true,
                    false
            );


            if (postMessageOutput.isMessagePostedForAtLeastOneContact()) {
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
                        db.fyleMessageJoinWithStatusDao().update(fyleMessageJoinWithStatus);
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
        if (messageRecipientInfos.isEmpty()) {
            return false;
        }
        // we fetch the discussion only to get our ownedIdentity
        Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
        if (discussion == null) {
            return false;
        }

        // when computing the message status, do not take the recipient info of my other owned devices into account, unless this is the only recipient info (discussion with myself)
        boolean ignoreOwnRecipientInfo = messageRecipientInfos.size() > 1;
        int recipientsCount = 0;
        int processingCount = 0;
        int deliveredCount = 0;
        int readCount = 0;

        for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
            if (ignoreOwnRecipientInfo && Arrays.equals(messageRecipientInfo.bytesContactIdentity, discussion.bytesOwnedIdentity)) {
                continue;
            }

            recipientsCount++;

            if (messageRecipientInfo.engineMessageIdentifier == null) {
                continue;
            }

            if (messageRecipientInfo.timestampSent == null) {
                processingCount++;
            }
            if (messageRecipientInfo.timestampDelivered != null) {
                deliveredCount++;
            }
            if (messageRecipientInfo.timestampRead != null) {
                readCount++;
            }
        }


        final int newStatus;
        if (processingCount != 0) {
            newStatus = STATUS_PROCESSING;
        } else if (deliveredCount == 0) {
            newStatus = STATUS_SENT;
        } else if (recipientsCount > 1 && deliveredCount == recipientsCount) {
            if (readCount == recipientsCount) {
                newStatus = STATUS_DELIVERED_ALL_READ_ALL;
            } else if (readCount > 0) {
                newStatus = STATUS_DELIVERED_ALL_READ_ONE;
            } else {
                newStatus = STATUS_DELIVERED_ALL;
            }
        } else if (readCount > 0) {
            newStatus = STATUS_DELIVERED_AND_READ;
        } else {
            newStatus = STATUS_DELIVERED;
        }

        if (newStatus != status) {
            boolean shouldStartExpiration =
                    (status == STATUS_UNPROCESSED || status == STATUS_PROCESSING || status == STATUS_COMPUTING_PREVIEW)
                    && (newStatus == STATUS_SENT || newStatus == STATUS_DELIVERED || newStatus == STATUS_DELIVERED_AND_READ || newStatus == STATUS_DELIVERED_ALL || newStatus == STATUS_DELIVERED_ALL_READ_ONE || newStatus == STATUS_DELIVERED_ALL_READ_ALL);
            status = newStatus;
            if (shouldStartExpiration && jsonExpiration != null) {
                App.runThread(new ExpiringOutboundMessageSent(this));
            }
            return true;
        }
        return false;
    }

    @Nullable
    public byte[] getAssociatedBytesOwnedIdentity() {
        return AppDatabase.getInstance().discussionDao().getBytesOwnedIdentityForDiscussionId(discussionId);
    }

    @NonNull
    public String getStringContent(Context context) {
        return getStringContent(context, false);
    }

    @NonNull
    public String getStringContent(Context context, Boolean withAttachmentsDescription) {
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
                if (messageType == TYPE_OUTBOUND_MESSAGE && status == Message.STATUS_SENT_FROM_ANOTHER_DEVICE) {
                    return context.getString(R.string.text_message_sharing_location_on_other_device);
                } else if (messageType == TYPE_OUTBOUND_MESSAGE) {
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
        } else if (jsonPoll != null) {
            try {
                //noinspection DataFlowIssue
                return "📊 " + getPoll().question;
            } catch (Exception ignored) {
                if (contentBody == null) {
                    return "";
                }
                return contentBody;
            }
        } else if (messageType == Message.TYPE_LEFT_GROUP) {
            return context.getString(R.string.text_group_left);
        } else if (messageType == Message.TYPE_CONTACT_DELETED) {
            return context.getString(R.string.text_user_removed_from_contacts);
        } else if (messageType == Message.TYPE_CONTACT_INACTIVE_REASON) {
            if (Message.NOT_ACTIVE_REASON_REVOKED.equals(contentBody)) {
                return context.getString(R.string.text_contact_was_blocked_revoked);
            } else {
                return context.getString(R.string.text_contact_was_blocked);
            }
        } else if (messageType == Message.TYPE_CONTACT_RE_ADDED) {
            return context.getString(R.string.text_user_added_to_contacts);
        } else if (messageType == Message.TYPE_RE_JOINED_GROUP) {
            String inviterName = ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(senderIdentifier);
            if (inviterName != null) {
                return context.getString(R.string.text_group_re_joined_by, inviterName);
            }
            return context.getString(R.string.text_group_re_joined);
        } else if (messageType == Message.TYPE_JOINED_GROUP) {
            String inviterName = ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(senderIdentifier);
            if (inviterName != null) {
                return context.getString(R.string.text_group_joined_by, inviterName);
            }
            return context.getString(R.string.text_group_joined);
        } else if (messageType == Message.TYPE_GAINED_GROUP_ADMIN) {
            return context.getString(R.string.text_you_became_admin);
        } else if (messageType == Message.TYPE_LOST_GROUP_ADMIN) {
            return context.getString(R.string.text_you_are_no_longer_admin);

        } else if (messageType == Message.TYPE_GAINED_GROUP_SEND_MESSAGE) {
            return context.getString(R.string.text_you_became_writer);
        } else if (messageType == Message.TYPE_LOST_GROUP_SEND_MESSAGE) {
            return context.getString(R.string.text_you_are_no_longer_writer);
        } else if (messageType == Message.TYPE_MEDIATOR_INVITATION_SENT) {
            return context.getString(R.string.invitation_status_mediator_invite_information_sent,
                    contentBody);
        } else if (messageType == Message.TYPE_MEDIATOR_INVITATION_ACCEPTED) {
            return context.getString(
                    R.string.invitation_status_mediator_invite_information_accepted,
                    contentBody);

        } else if (messageType == Message.TYPE_MEDIATOR_INVITATION_IGNORED) {
            return context.getString(
                    R.string.invitation_status_mediator_invite_information_ignored,
                    contentBody
            );
        } else if (messageType == Message.TYPE_SCREEN_SHOT_DETECTED) {
            if (Arrays.equals(senderIdentifier, AppSingleton.getBytesCurrentIdentity())) {
                return context.getString(R.string.text_you_captured_sensitive_message);
            } else {
                String displayName =
                        ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(senderIdentifier);
                if (displayName != null) {
                    return context.getString(R.string.text_xxx_captured_sensitive_message, displayName);
                } else {
                    return context.getString(R.string.text_unknown_member_captured_sensitive_message);
                }
            }
        } else if (contentBody == null || withAttachmentsDescription) {
            StringBuilder sb = new StringBuilder();
            if (contentBody != null) {
                sb.append(contentBody);
            }
            if (hasAttachments() && withAttachmentsDescription) {
                if (contentBody != null) {
                    sb.append(context.getString(R.string.attachments_joiner));
                }
                sb.append(getAttachmentsStringContent(context));
            }
            return sb.toString();
        } else {
            return contentBody;
        }
    }

    public String getAttachmentsStringContent(Context context) {
        StringBuilder sb = new StringBuilder();
        int fileCount = totalAttachmentCount - imageAndVideoCount - audioCount;
        int imageOnlyCount = imageAndVideoCount - videoCount;
        if (fileCount > 0) {
            sb.append("📎 ");
            if (firstAttachmentName != null) {
                sb.append(firstAttachmentName);
                if (fileCount > 1) {
                    sb.append(context.getString(R.string.attachments_files_joiner));
                    sb.append(context.getResources().getQuantityString(R.plurals.text_others, fileCount - 1, fileCount - 1));
                }
            } else {
                sb.append(context.getResources().getQuantityString(R.plurals.text_files, fileCount, fileCount));
            }
        }
        if (audioCount > 0) {
            if (sb.length() > 0) {
                sb.append(context.getString(R.string.attachments_joiner));
            }
            sb.append("🎤 ");
            sb.append(context.getResources().getQuantityString(R.plurals.text_audios, audioCount, audioCount));
        }
        if (imageOnlyCount > 0) {
            if (sb.length() > 0) {
                sb.append(context.getString(R.string.attachments_joiner));
            }
            sb.append("📷 ");
            sb.append(context.getResources().getQuantityString(R.plurals.text_images, imageOnlyCount, imageOnlyCount));
        }
        if (videoCount > 0) {
            if (sb.length() > 0) {
                sb.append(context.getString(R.string.attachments_joiner));
            }
            sb.append("🎥 ");
            sb.append(context.getResources().getQuantityString(R.plurals.text_videos, videoCount, videoCount));
        }
        return sb.toString();
    }

    // check whether a Message has an empty body and no attachment (it should then be deleted)
    public boolean isEmpty() {
        return (contentBody == null || contentBody.trim().isEmpty())
                && !hasAttachments()
                && wipeStatus != WIPE_STATUS_WIPED
                && wipeStatus != WIPE_STATUS_REMOTE_DELETED;
    }

    public boolean isTextOnly() {
        return contentBody != null && !contentBody.trim().isEmpty() && !hasAttachments() && jsonReply == null;
    }

    public boolean isWithoutText() {
        return contentBody == null || contentBody.trim().isEmpty();
    }

    public boolean isContentHidden() {
        return messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE;
    }

    public boolean isInbound() {
        return messageType == TYPE_INBOUND_MESSAGE || messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE;
    }

    public boolean isIdentityMentioned(byte[] ownedIdentity) {
        try {
            List<JsonUserMention> mentions = getMentions();
            if (mentions != null) {
                for (JsonUserMention mention : mentions) {
                    if (Arrays.equals(ownedIdentity, mention.getUserIdentifier())) {
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            Logger.e("Error handling mentions");
        }
        return false;
    }

    public boolean isOwnMessageReply(byte[] ownedIdentity) {
        try {
            JsonMessageReference reply = getJsonMessage().jsonReply;
            return (reply != null) && Arrays.equals(ownedIdentity, reply.senderIdentifier);
        } catch (Exception ex) {
            Logger.e("Error handling jsonReply");
        }
        return false;
    }

    public boolean isLocationMessage() {
        return jsonLocation != null;
    }

    public boolean isPollMessage() {
        return jsonPoll != null;
    }

    public boolean isCurrentSharingOutboundLocationMessage() {
        return messageType == Message.TYPE_OUTBOUND_MESSAGE
                && locationType == Message.LOCATION_TYPE_SHARE
                && status != Message.STATUS_SENT_FROM_ANOTHER_DEVICE;
    }

    public boolean isForwardable() {
        return (messageType == Message.TYPE_INBOUND_MESSAGE || messageType == Message.TYPE_OUTBOUND_MESSAGE)
                && wipeStatus == Message.WIPE_STATUS_NONE
                && !limitedVisibility
                && !isPollMessage();
    }

    public boolean isBookmarkableAndDetailable() {
        return (messageType == Message.TYPE_INBOUND_MESSAGE
                || messageType == Message.TYPE_OUTBOUND_MESSAGE
                || messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE);
    }

    // return true if message expired and update locationType field
    public boolean isSharingExpired() {
        if (this.jsonLocation != null && this.locationType == LOCATION_TYPE_SHARE) {
            JsonLocation jsonLocation = getJsonLocation();
            if (jsonLocation != null) {
                Long expiration = jsonLocation.sharingExpiration;
                if (expiration != null) {
                    return expiration < System.currentTimeMillis();
                }
            }
        }
        return false;
    }

    @NonNull
    public byte[] getMessagePayloadAsBytes(int discussionType, byte[] bytesOwnedIdentity, byte[] bytesDiscussionIdentifier, byte[] returnReceiptNonce, byte[] returnReceiptKey, Long originalServerTimestamp) throws Exception {
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
                jsonMessage.oneToOneIdentifier = new JsonOneToOneMessageIdentifier(bytesOwnedIdentity, bytesDiscussionIdentifier);
                break;
        }
        jsonMessage.setOriginalServerTimestamp(originalServerTimestamp);
        JsonReturnReceipt jsonReturnReceipt = new JsonReturnReceipt(returnReceiptNonce, returnReceiptKey);
        JsonPayload jsonPayload = new JsonPayload(jsonMessage, jsonReturnReceipt);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    @NonNull
    public byte[] getDiscussionSettingsUpdatePayloadAsBytes(int discussionType, byte[] bytesOwnedIdentity, byte[] bytesDiscussionIdentifier) throws Exception {
        JsonSharedSettings jsonSharedSettings = AppSingleton.getJsonObjectMapper().readValue(contentBody, JsonSharedSettings.class);
        switch (discussionType) {
            case Discussion.TYPE_GROUP:
                jsonSharedSettings.setGroupOwnerAndUid(bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonSharedSettings.setGroupV2Identifier(bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_CONTACT:
            default:
                jsonSharedSettings.setOneToOneIdentifier(new JsonOneToOneMessageIdentifier(bytesOwnedIdentity, bytesDiscussionIdentifier));
                break;
        }
        JsonPayload jsonPayload = new JsonPayload();
        jsonPayload.setJsonSharedSettings(jsonSharedSettings);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    @NonNull
    public static byte[] getDiscussionQuerySharedSettingsPayloadAsBytes(Discussion discussion, Integer knownSharedSettingsVersion, JsonExpiration knownSharedExpiration) throws Exception {
        JsonQuerySharedSettings jsonQuerySharedSettings = JsonQuerySharedSettings.of(discussion);
        jsonQuerySharedSettings.setKnownSharedSettingsVersion(knownSharedSettingsVersion);
        jsonQuerySharedSettings.setKnownSharedExpiration(knownSharedExpiration);

        JsonPayload jsonPayload = new JsonPayload();
        jsonPayload.setJsonQuerySharedSettings(jsonQuerySharedSettings);
        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
    }

    @Nullable
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

    @NonNull
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
                    case LOCATION_TYPE_SHARE_FINISHED:
                        JsonLocation location = AppSingleton.getJsonObjectMapper().readValue(jsonLocation, JsonLocation.class);
                        jsonMessage.jsonLocation = new JsonLocation(JsonLocation.TYPE_END_SHARING, null, null, null, location.latitude, location.longitude, location.altitude, location.precision, location.timestamp);
                        break;
                    case LOCATION_TYPE_SEND:
                    case LOCATION_TYPE_SHARE:
                    default:
                        jsonMessage.jsonLocation = AppSingleton.getJsonObjectMapper().readValue(jsonLocation, JsonLocation.class);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        jsonMessage.jsonUserMentions = getMentions();
        jsonMessage.jsonPoll = getPoll();
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
        if (jsonMessage.jsonUserMentions != null) {
            try {
                jsonMessage.sanitizeJsonUserMentions();
                jsonMentions = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessage.jsonUserMentions);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonMentions = null;
            }
        }
        if (jsonMessage.jsonPoll != null) {
            try {
                jsonPoll = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessage.jsonPoll);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                jsonPoll = null;
            }
        }
    }

    @Nullable
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

    @Nullable
    public List<JsonUserMention> getMentions() {
        if (jsonMentions != null) {
            try {
                return AppSingleton.getJsonObjectMapper().readValue(jsonMentions, new TypeReference<>() {
                });
            } catch (Exception e) {
                Logger.w("Error decoding mentions!\n" + jsonMentions);
            }
        }
        return null;
    }

    @Nullable
    public JsonPoll getPoll() {
        if (jsonPoll != null) {
            try {
                return AppSingleton.getJsonObjectMapper().readValue(jsonPoll, JsonPoll.class);
            } catch (Exception e) {
                Logger.w("Error decoding a poll!\n" + jsonPoll);
            }
        }
        return null;
    }

    // returns true if the attachment count has changed, false if nothing changed
    public boolean recomputeAttachmentCount(AppDatabase db) {
        int totalCount = 0;
        int imageCount = 0;
        int videoCount = 0;
        int audioCount = 0;
        String firstAttachmentName = null;

        StringBuilder sb = new StringBuilder();
        boolean firstImage = true;
        boolean hasLinkPreview = false;
        for (FyleMessageJoinWithStatus fmjoin : db.fyleMessageJoinWithStatusDao().getStatusesForMessage(this.id)) {
            String mimeType = fmjoin.getNonNullMimeType();
            if (mimeType.equals(OpenGraph.MIME_TYPE)) {
                if (!hasLinkPreview) {
                    // always consider only the first link-preview attachment
                    hasLinkPreview = true;
                    if (!Objects.equals(this.linkPreviewFyleId, fmjoin.fyleId)) {
                        this.linkPreviewFyleId = fmjoin.fyleId;
                        db.messageDao().updateLinkPreviewFyleId(this.id, this.linkPreviewFyleId);
                    }
                }
                continue;
            } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) {
                imageCount++;
                if (fmjoin.imageResolution != null && !fmjoin.imageResolution.isEmpty()) {
                    if (!firstImage) {
                        sb.append(";");
                    }
                    firstImage = false;
                    sb.append(fmjoin.imageResolution);
                }
                if (mimeType.startsWith("video/")) {
                    videoCount++;
                }
            } else if (mimeType.startsWith("audio/")) {
                audioCount++;
            } else if (firstAttachmentName == null) {
                firstAttachmentName = fmjoin.fileName;
            }
            totalCount++;
        }
        if (!hasLinkPreview && this.linkPreviewFyleId != null) {
            this.linkPreviewFyleId = null;
            db.messageDao().updateLinkPreviewFyleId(this.id, null);
        }
        String imageResolutions = sb.toString();
        if (this.totalAttachmentCount != totalCount
                || this.imageAndVideoCount != imageCount
                || this.videoCount != videoCount
                || this.audioCount != audioCount
                || !Objects.equals(this.firstAttachmentName, firstAttachmentName)
                || !Objects.equals(this.imageResolutions, imageResolutions)
        ) {
            this.totalAttachmentCount = totalCount;
            this.imageAndVideoCount = imageCount;
            this.videoCount = videoCount;
            this.audioCount = audioCount;
            this.firstAttachmentName = firstAttachmentName;
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
        if (isCurrentSharingOutboundLocationMessage()) {
            UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussionId, false);
        }
        db.messageDao().delete(this);
        UnreadCountsSingleton.INSTANCE.messageDeleted(this);
    }

    // this never deletes the message, even if it has an empty body
    public void deleteAttachments(AppDatabase db) {
        if (wipedAttachmentCount == 0) {
            wipedAttachmentCount = totalAttachmentCount;
        }
        List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(id);
        for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
            db.fyleMessageJoinWithStatusDao().delete(fyleAndStatus.fyleMessageJoinWithStatus);
            switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                    if (fyleAndStatus.fyleMessageJoinWithStatus.engineNumber != null) {
                        AppSingleton.getEngine().markAttachmentForDeletion(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    }
                    break;
                case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                    if (fyleAndStatus.fyleMessageJoinWithStatus.engineNumber != null) {
                        AppSingleton.getEngine().cancelAttachmentUpload(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                    }
                    break;
            }

            if (db.fyleMessageJoinWithStatusDao().countMessageForFyle(fyleAndStatus.fyle.id) == 0) {
                if (fyleAndStatus.fyle.sha256 != null) {
                    try {
                        Fyle.acquireLock(fyleAndStatus.fyle.sha256);
                        fyleAndStatus.fyle.delete();
                    } finally {
                        Fyle.releaseLock(fyleAndStatus.fyle.sha256);
                    }
                } else {
                    fyleAndStatus.fyle.delete();
                }
            }
        }
        totalAttachmentCount = 0;
        imageAndVideoCount = 0;
        videoCount = 0;
        audioCount = 0;
        firstAttachmentName = null;
        imageResolutions = null;
        db.messageDao().updateAttachmentCount(id, 0, 0, 0, 0, null, wipedAttachmentCount, null);
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
            jsonMentions = null;
            jsonPoll = null;
            limitedVisibility = false;
            db.messageDao().updateWipe(id, WIPE_STATUS_WIPED);
            db.reactionDao().deleteAllForMessage(id);
            db.pollVoteDao().deleteAllForMessage(id);
            db.messageMetadataDao().insert(new MessageMetadata(id, MessageMetadata.KIND_WIPED, System.currentTimeMillis()));
            db.messageExpirationDao().deleteWipeExpiration(id);
            UnreadCountsSingleton.INSTANCE.removeLocationSharingMessage(discussionId, id);
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
        jsonMentions = null;
        jsonPoll = null;
        limitedVisibility = false;
        db.messageDao().updateWipe(id, WIPE_STATUS_REMOTE_DELETED);
        db.reactionDao().deleteAllForMessage(id);
        db.pollVoteDao().deleteAllForMessage(id);
        if (messageType == TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            messageType = TYPE_INBOUND_MESSAGE;
            db.messageDao().updateMessageType(id, messageType);
        }
        db.messageMetadataDao().insert(new MessageMetadata(id, MessageMetadata.KIND_REMOTE_DELETED, serverTimestamp, bytesRemoteIdentity));
        db.messageExpirationDao().deleteWipeExpiration(id);
        UnreadCountsSingleton.INSTANCE.removeLocationSharingMessage(discussionId, id);
    }


    private static final Message EMPTY_MESSAGE = new Message(0, null, null, null, null, null, 0, 0, 0, 0, 0, 0, 0, null, new byte[0], UUID.randomUUID(), 0, 0, 0, 0, null, 0, 0, false, false, false, null, null, 0, 0, false, null, null, null);

    public static Message emptyMessage() {
        return EMPTY_MESSAGE;
    }
}
