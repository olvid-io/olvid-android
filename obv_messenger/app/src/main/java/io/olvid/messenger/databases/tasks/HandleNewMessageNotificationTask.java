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

package io.olvid.messenger.databases.tasks;


import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Group2Member;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.ReactionRequest;
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest;
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
import io.olvid.messenger.databases.entity.jsons.JsonQuerySharedSettings;
import io.olvid.messenger.databases.entity.jsons.JsonReaction;
import io.olvid.messenger.databases.entity.jsons.JsonReturnReceipt;
import io.olvid.messenger.databases.entity.jsons.JsonScreenCaptureDetection;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.entity.jsons.JsonUpdateMessage;
import io.olvid.messenger.databases.entity.jsons.JsonUserMention;
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcMessage;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.services.AvailableSpaceHelper;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

public class HandleNewMessageNotificationTask implements Runnable {
    @NonNull
    private final Engine engine;
    @NonNull
    private final ObvMessage obvMessage;
    private final AppDatabase db;

    // this HashMap is used to store groupV2 messages received before the group blob is downloaded. This is only useful in a multi-device setting
    // Map from ownedIdentity to Map from groupIdentifier to a list of ObvMessage to be processed as a fifo
    private static final HashMap<BytesKey, HashMap<BytesKey, List<ObvMessage>>> pendingGroupV2Messages = new HashMap<>();
    public static final long GROUP_V2_MESSAGES_TTL = 2 * 86_400_000L; // 2 days


    public HandleNewMessageNotificationTask(@NonNull Engine engine, @NonNull ObvMessage obvMessage) {
        this.engine = engine;
        this.obvMessage = obvMessage;
        this.db = AppDatabase.getInstance();
    }

    @Override
    public void run() {
        int count = db.messageDao().getCountForEngineIdentifier(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
        if (count > 0) {
            // content was already inserted in database
            engine.markMessageForDeletion(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
            return;
        }
        try {
            JsonPayload messagePayload;
            try {
                messagePayload = AppSingleton.getJsonObjectMapper().readValue(obvMessage.getMessagePayload(), JsonPayload.class);
            } catch (Exception e) {
                Logger.e("Received a message that cannot be deserialized! Deleting it...");
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }
            JsonReturnReceipt jsonReturnReceipt = messagePayload.getJsonReturnReceipt();
            JsonMessage jsonMessage = messagePayload.getJsonMessage();
            JsonWebrtcMessage jsonWebrtcMessage = messagePayload.getJsonWebrtcMessage();

            JsonSharedSettings jsonSharedSettings = messagePayload.getJsonSharedSettings();
            JsonQuerySharedSettings jsonQuerySharedSettings = messagePayload.getJsonQuerySharedSettings();
            JsonDeleteDiscussion jsonDeleteDiscussion = messagePayload.getJsonDeleteDiscussion();
            JsonDeleteMessages jsonDeleteMessages = messagePayload.getJsonDeleteMessages();
            JsonUpdateMessage jsonUpdateMessage = messagePayload.getJsonUpdateMessage();
            JsonReaction jsonReaction = messagePayload.getJsonReaction();
            JsonScreenCaptureDetection jsonScreenCaptureDetection = messagePayload.getJsonScreenCaptureDetection();
            JsonDiscussionRead jsonDiscussionRead = messagePayload.getJsonDiscussionRead();
            JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened = messagePayload.getJsonLimitedVisibilityMessageOpened();

            if (jsonWebrtcMessage != null) {
                App.handleWebrtcMessage(obvMessage.getBytesToIdentity(), obvMessage.getBytesFromIdentity(), jsonWebrtcMessage, obvMessage.getDownloadTimestamp(), obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }


            final MessageSender messageSender = MessageSender.of(db, obvMessage.getBytesToIdentity(), obvMessage.getBytesFromIdentity());
            if (messageSender == null) {
                Logger.e("Received a message from an unknown contact!!!");
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonQuerySharedSettings != null) {
                handleQuerySharedSettingsMessage(jsonQuerySharedSettings, messageSender);
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonSharedSettings != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonSharedSettings.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleSharedSettingsMessage(jsonSharedSettings, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonDeleteDiscussion != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonDeleteDiscussion.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleDeleteDiscussion(jsonDeleteDiscussion, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonDeleteMessages != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonDeleteMessages.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleDeleteMessages(jsonDeleteMessages, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonUpdateMessage != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonUpdateMessage.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleUpdateMessage(jsonUpdateMessage, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonReaction != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonReaction.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleReactionMessage(jsonReaction, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonScreenCaptureDetection != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonScreenCaptureDetection.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleScreenCaptureDetectionMessage(jsonScreenCaptureDetection, messageSender, obvMessage.getServerTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonDiscussionRead != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonDiscussionRead.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleDiscussionReadMessage(jsonDiscussionRead, messageSender);
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonLimitedVisibilityMessageOpened != null) {
                if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonLimitedVisibilityMessageOpened.getGroupV2Identifier(), obvMessage)) {
                    return;
                }
                handleLimitedVisibilityOpenedMessage(jsonLimitedVisibilityMessageOpened, messageSender, obvMessage.getServerTimestamp(), obvMessage.getDownloadTimestamp());
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (jsonMessage == null) {
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }


            if (jsonMessage.isEmpty() && obvMessage.getAttachments().length == 0) {
                // we received an empty message with no other attachments, simply discard it.
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            if (putGroupV2MessageOnHoldIfAppropriate(messageSender, jsonMessage.getGroupV2Identifier(), obvMessage)) {
                return;
            }

            Discussion discussion = getDiscussion(jsonMessage.getGroupUid(), jsonMessage.getGroupOwner(), jsonMessage.getGroupV2Identifier(), jsonMessage.getOneToOneIdentifier(), messageSender, GroupV2.Permission.SEND_MESSAGE);
            if (discussion == null) {
                // we don't have a discussion to post this message in: probably a message from a not-oneToOne contact --> discarding it
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            RemoteDeleteAndEditRequest remoteDeleteAndEditRequest = db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(jsonMessage.getSenderSequenceNumber(), jsonMessage.getSenderThreadIdentifier(), messageSender.getSenderIdentity(), discussion.id);
            List<ReactionRequest> reactionRequests = db.reactionRequestDao().getAllBySenderSequenceNumber(jsonMessage.getSenderSequenceNumber(), jsonMessage.getSenderThreadIdentifier(), messageSender.getSenderIdentity(), discussion.id);
            JsonExpiration jsonExpiration = jsonMessage.getJsonExpiration();

            if (jsonExpiration != null && jsonExpiration.readOnce != null && jsonExpiration.readOnce && messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                // never display read once messages received from another device
                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
                return;
            }

            boolean messageShouldBeRemoteDeleted = false;
            if (remoteDeleteAndEditRequest != null && remoteDeleteAndEditRequest.requestType == RemoteDeleteAndEditRequest.TYPE_DELETE) {
                // check whether the remote delete request comes from the message sender or an authorized group member
                if (remoteDeleteAndEditRequest.remoteDeleter == messageSender.bytesOwnedIdentity || discussion.discussionType != Discussion.TYPE_GROUP_V2) {
                    messageShouldBeRemoteDeleted = true;
                } else {
                    Group2Member group2Member = db.group2MemberDao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier, remoteDeleteAndEditRequest.remoteDeleter);
                    if (group2Member != null) {
                        messageShouldBeRemoteDeleted = group2Member.permissionRemoteDeleteAnything || (group2Member.permissionEditOrRemoteDeleteOwnMessages && Arrays.equals(remoteDeleteAndEditRequest.remoteDeleter, messageSender.getSenderIdentity()));
                    }
                }
            }

            if (messageShouldBeRemoteDeleted) {
                // a request to remote delete the received message was already received!

                // check if we should keep a trace of this remote deleted message
                if (Arrays.equals(messageSender.bytesOwnedIdentity, remoteDeleteAndEditRequest.remoteDeleter)
                        || !SettingsActivity.getRetainRemoteDeletedMessages()) {
                    // no need to create a "remote deleted message"
                    db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest);
                    db.reactionRequestDao().delete(reactionRequests.toArray(new ReactionRequest[0]));
                    if (jsonReturnReceipt != null) {
                        AppSingleton.getEngine().sendReturnReceipt(messageSender.bytesOwnedIdentity, messageSender.getSenderIdentity(), Message.RETURN_RECEIPT_STATUS_DELIVERED, jsonReturnReceipt.nonce, jsonReturnReceipt.key, null);
                    }
                } else {
                    // we create a "remote deleted message"
                    Pair<Long, Boolean> transactionResult = db.runInTransaction(() -> {
                        boolean sendExpireIntent = false;

                        final Message message;
                        if (messageSender.type == MessageSender.Type.CONTACT) {
                            message = new Message(
                                    db,
                                    jsonMessage.getSenderSequenceNumber(),
                                    jsonMessage,
                                    jsonReturnReceipt,
                                    obvMessage.getServerTimestamp(),
                                    Message.STATUS_READ,
                                    Message.TYPE_INBOUND_MESSAGE,
                                    discussion.id,
                                    obvMessage.getIdentifier(),
                                    messageSender.getSenderIdentity(),
                                    jsonMessage.getSenderThreadIdentifier(),
                                    0,
                                    0
                            );
                        } else {
                            message = new Message(
                                    db,
                                    jsonMessage.getSenderSequenceNumber(),
                                    jsonMessage,
                                    jsonReturnReceipt,
                                    obvMessage.getServerTimestamp(),
                                    Message.STATUS_SENT_FROM_ANOTHER_DEVICE,
                                    Message.TYPE_OUTBOUND_MESSAGE,
                                    discussion.id,
                                    obvMessage.getIdentifier(),
                                    messageSender.getSenderIdentity(),
                                    jsonMessage.getSenderThreadIdentifier(),
                                    0,
                                    0
                            );
                        }
                        message.missedMessageCount = processSequenceNumber(db, discussion.id, messageSender.getSenderIdentity(), jsonMessage.getSenderThreadIdentifier(), jsonMessage.getSenderSequenceNumber());
                        message.contentBody = null;
                        message.jsonLocation = null;
                        message.locationType = Message.LOCATION_TYPE_NONE;
                        message.jsonReply = null;
                        message.forwarded = false;
                        message.mentioned = false;
                        message.edited = Message.EDITED_NONE;
                        message.wipeStatus = Message.WIPE_STATUS_REMOTE_DELETED;
                        message.wipedAttachmentCount = obvMessage.getAttachments().length;
                        message.limitedVisibility = false;

                        message.id = db.messageDao().insert(message);

                        if (discussion.updateLastMessageTimestamp(obvMessage.getServerTimestamp())) {
                            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                        }

                        db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_UPLOADED, obvMessage.getServerTimestamp()));
                        db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_DELIVERED, System.currentTimeMillis()));
                        db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_REMOTE_DELETED, remoteDeleteAndEditRequest.serverTimestamp, remoteDeleteAndEditRequest.remoteDeleter));

                        if (jsonExpiration != null) {
                            if (jsonExpiration.getExistenceDuration() != null) {
                                long elapsedTimeBeforeDownload = obvMessage.getDownloadTimestamp() - obvMessage.getServerTimestamp();
                                if (elapsedTimeBeforeDownload < 0) {
                                    elapsedTimeBeforeDownload = 0;
                                }
                                long expirationTimestamp = obvMessage.getLocalDownloadTimestamp() + jsonExpiration.getExistenceDuration() * 1000L - elapsedTimeBeforeDownload;
                                MessageExpiration messageExpiration = new MessageExpiration(message.id, expirationTimestamp, false);
                                db.messageExpirationDao().insert(messageExpiration);
                                sendExpireIntent = true;
                            }
                        }

                        db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest);
                        db.reactionRequestDao().delete(reactionRequests.toArray(new ReactionRequest[0]));

                        return new Pair<>(message.id, sendExpireIntent);
                    });

                    if (transactionResult.second) {
                        App.runThread(MessageExpirationService::scheduleNextExpiration);
                    }

                    Message message = db.messageDao().get(transactionResult.first);
                    if (message == null) {
                        Logger.w("Failed to insert new message in db.");
                        return;
                    }

                    if (jsonReturnReceipt != null) {
                        // send DELIVERED return receipt
                        message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_DELIVERED);
                    }
                }

                engine.deleteMessageAndAttachments(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());
            } else {
                Fyle.JsonMetadata[] attachmentMetadatas = new Fyle.JsonMetadata[obvMessage.getAttachments().length];

                int imageCount = 0;
                int attachmentCount = 0;
                for (int i = 0; i < obvMessage.getAttachments().length; i++) {
                    try {
                        attachmentMetadatas[i] = AppSingleton.getJsonObjectMapper().readValue(obvMessage.getAttachments()[i].getMetadata(), Fyle.JsonMetadata.class);
                        String mimeType = PreviewUtils.getNonNullMimeType(attachmentMetadatas[i].getType(), attachmentMetadatas[i].getFileName());
                        // correct the received mime type in case it was invalid
                        attachmentMetadatas[i].setType(mimeType);
                        if (Objects.equals(mimeType, OpenGraph.MIME_TYPE)) {
                            continue;
                        } else if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) {
                            imageCount++;
                        }
                        attachmentCount++;
                    } catch (Exception e) {
                        attachmentMetadatas[i] = null;
                    }
                }

                int finalAttachmentCount = attachmentCount;
                int finalImageCount = imageCount;
                Pair<Long, Boolean> transactionResult = db.runInTransaction(() -> {
                    boolean sendExpireIntent = false;
                    int messageType;
                    int messageStatus;

                    if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
                        messageType = Message.TYPE_OUTBOUND_MESSAGE;
                        messageStatus = Message.STATUS_SENT_FROM_ANOTHER_DEVICE;
                        // read once messages have already been discarded
                        // existence duration and visibility duration should be treated the same way --> we move visibility to existence
                        if (jsonExpiration != null && jsonExpiration.getVisibilityDuration() != null) {
                            if (jsonExpiration.getExistenceDuration() == null || jsonExpiration.getExistenceDuration() > jsonExpiration.getVisibilityDuration()) {
                                jsonExpiration.setExistenceDuration(jsonExpiration.getVisibilityDuration());
                            }
                        }
                    } else if (jsonExpiration == null || (jsonExpiration.getVisibilityDuration() == null && (jsonExpiration.getReadOnce() == null || !jsonExpiration.getReadOnce()))) {
                        messageType = Message.TYPE_INBOUND_MESSAGE;
                        messageStatus = Message.STATUS_UNREAD;
                        jsonMessage.setJsonExpiration(null); // we clear the jsonExpiration part before creating the message. The existence duration is taken into account a few lines below
                    } else {
                        // the message has a visibility duration or is read once
                        messageType = Message.TYPE_INBOUND_EPHEMERAL_MESSAGE;
                        messageStatus = Message.STATUS_UNREAD;
                    }

                    long messageServerTimestamp = obvMessage.getServerTimestamp();
                    if (jsonMessage.getOriginalServerTimestamp() != null && discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                        messageServerTimestamp = Math.min(messageServerTimestamp, jsonMessage.getOriginalServerTimestamp());
                    }

                    Message message = new Message(
                            db,
                            jsonMessage.getSenderSequenceNumber(),
                            jsonMessage,
                            jsonReturnReceipt,
                            messageServerTimestamp,
                            messageStatus,
                            messageType,
                            discussion.id,
                            obvMessage.getIdentifier(),
                            messageSender.getSenderIdentity(),
                            jsonMessage.getSenderThreadIdentifier(),
                            finalAttachmentCount,
                            finalImageCount
                    );
                    message.missedMessageCount = processSequenceNumber(db, discussion.id, messageSender.getSenderIdentity(), jsonMessage.getSenderThreadIdentifier(), jsonMessage.getSenderSequenceNumber());
                    message.forwarded = jsonMessage.isForwarded() != null && jsonMessage.isForwarded();
                    message.mentioned = message.isIdentityMentioned(messageSender.bytesOwnedIdentity) || message.isOwnMessageReply(messageSender.bytesOwnedIdentity);
                    boolean edited = false;

                    if (remoteDeleteAndEditRequest != null && remoteDeleteAndEditRequest.requestType == RemoteDeleteAndEditRequest.TYPE_EDIT) {
                        String newBody = remoteDeleteAndEditRequest.body == null ? null : remoteDeleteAndEditRequest.body.trim();
                        String mentions = remoteDeleteAndEditRequest.getSanitizedSerializedMentions();
                        if (!Objects.equals(message.contentBody, newBody)) {
                            edited = true;
                            message.contentBody = newBody;
                            message.jsonMentions = mentions;
                            message.mentioned = message.isIdentityMentioned(messageSender.bytesOwnedIdentity) || message.isOwnMessageReply(messageSender.bytesOwnedIdentity);
                            message.edited = Message.EDITED_UNSEEN;
                        }
                    }

                    // if start sharing location message check this contact is not already sharing their location in this discussion
                    // if already sharing force end of previous sharing message
                    if (message.locationType == Message.LOCATION_TYPE_SHARE) {
                        if (Arrays.equals(message.senderIdentifier, messageSender.bytesOwnedIdentity)) {
                            UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(message.discussionId, true);
                        } else {
                            List<Message> currentLocationSharingMessages = db.messageDao().getCurrentLocationSharingMessagesOfIdentityInDiscussion(message.senderIdentifier, message.discussionId);
                            if (currentLocationSharingMessages != null && currentLocationSharingMessages.size() > 0) {
                                for (Message m : currentLocationSharingMessages) {
                                    Logger.e("This identity was already sharing it location, marking sharing as finished for message: " + m.id);
                                    db.messageDao().updateLocationType(m.id, Message.LOCATION_TYPE_SHARE_FINISHED);
                                }
                            }
                        }
                    }

                    message.id = db.messageDao().insert(message);

                    if (discussion.updateLastMessageTimestamp(obvMessage.getServerTimestamp())) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }

                    db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_UPLOADED, obvMessage.getServerTimestamp()));
                    db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_DELIVERED, System.currentTimeMillis()));
                    if (edited) {
                        db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_EDITED, remoteDeleteAndEditRequest.serverTimestamp));
                    }

                    if (jsonExpiration != null) {
                        if (jsonExpiration.getExistenceDuration() != null) {
                            long elapsedTimeBeforeDownload = obvMessage.getDownloadTimestamp() - obvMessage.getServerTimestamp();
                            if (elapsedTimeBeforeDownload < 0) {
                                elapsedTimeBeforeDownload = 0;
                            }
                            long expirationTimestamp = obvMessage.getLocalDownloadTimestamp() + jsonExpiration.getExistenceDuration() * 1000L - elapsedTimeBeforeDownload;
                            MessageExpiration messageExpiration = new MessageExpiration(message.id, expirationTimestamp, false);
                            db.messageExpirationDao().insert(messageExpiration);
                            sendExpireIntent = true;
                        }
                    }

                    if (remoteDeleteAndEditRequest != null) {
                        db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest);
                    }
                    if (reactionRequests != null) {
                        for (ReactionRequest reactionRequest : reactionRequests) {
                            final byte[] reacterOrNull = Arrays.equals(reactionRequest.reacter, messageSender.bytesOwnedIdentity) ? null : reactionRequest.reacter;
                            new UpdateReactionsTask(message.id, reactionRequest.reaction, reacterOrNull, reactionRequest.serverTimestamp, false).run();
                        }
                        db.reactionRequestDao().delete(reactionRequests.toArray(new ReactionRequest[0]));
                    }

                    return new Pair<>(message.id, sendExpireIntent);
                });

                if (transactionResult.second) {
                    App.runThread(MessageExpirationService::scheduleNextExpiration);
                }

                Message message = db.messageDao().get(transactionResult.first);
                if (message == null) {
                    Logger.w("Failed to insert new message in db.");
                    return;
                }

                if (jsonReturnReceipt != null) {
                    // send DELIVERED return receipt
                    message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_DELIVERED);
                }

                List<FyleMessageJoinWithStatus> fyleMessageJoinWithStatusesToDownload = new ArrayList<>();

                for (int i = 0; i < obvMessage.getAttachments().length; i++) {
                    ObvAttachment attachment = obvMessage.getAttachments()[i];
                    Fyle.JsonMetadata attachmentMetadata = attachmentMetadatas[i];
                    if (attachment == null) {
                        continue;
                    }
                    try {
                        if (attachmentMetadata == null || attachmentMetadata.getFileName() == null) {
                            throw new Exception();
                        }
                        final byte[] sha256 = attachmentMetadata.getSha256();
                        try {
                            Fyle.acquireLock(sha256);
                            Fyle fyle = db.fyleDao().getBySha256(sha256);
                            if (fyle != null) {
                                // we know this attachment
                                if (fyle.isComplete()) {
                                    // the fyle is already complete, simply link it and cancel the download
                                    final String imageResolution = PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(attachmentMetadata.getType(), attachmentMetadata.getFileName())) ? null : "";

                                    //noinspection ConstantConditions
                                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = new FyleMessageJoinWithStatus(
                                            fyle.id,
                                            message.id,
                                            messageSender.bytesOwnedIdentity,
                                            fyle.filePath,
                                            attachmentMetadata.getFileName(),
                                            attachmentMetadata.getType(),
                                            FyleMessageJoinWithStatus.STATUS_COMPLETE,
                                            attachment.getExpectedLength(),
                                            1,
                                            attachment.getMessageIdentifier(),
                                            attachment.getNumber(),
                                            imageResolution
                                    );
                                    db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                                    fyleMessageJoinWithStatus.sendReturnReceipt(FyleMessageJoinWithStatus.RECEPTION_STATUS_DELIVERED, message);
                                    engine.markAttachmentForDeletion(attachment);
                                } else {
                                    // the fyle is incomplete, so no need to create the Fyle, but still create a STATUS_DOWNLOADABLE FyleMessageJoinWithStatus
                                    final String imageResolution = PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(attachmentMetadata.getType(), attachmentMetadata.getFileName())) ? null : "";

                                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = new FyleMessageJoinWithStatus(
                                            fyle.id,
                                            message.id,
                                            messageSender.bytesOwnedIdentity,
                                            attachment.getUrl(),
                                            attachmentMetadata.getFileName(),
                                            attachmentMetadata.getType(),
                                            attachment.isDownloadRequested() ? FyleMessageJoinWithStatus.STATUS_DOWNLOADING : FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
                                            attachment.getExpectedLength(),
                                            (float) attachment.getReceivedLength() / attachment.getExpectedLength(),
                                            attachment.getMessageIdentifier(),
                                            attachment.getNumber(),
                                            imageResolution
                                    );
                                    db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                                    fyleMessageJoinWithStatusesToDownload.add(fyleMessageJoinWithStatus);
                                }
                            } else {
                                // the file is unknown, create it and a STATUS_DOWNLOADABLE FyleMessageJoinWithStatus
                                // this is the "normal" case
                                final String imageResolution = PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(attachmentMetadata.getType(), attachmentMetadata.getFileName())) ? null : "";

                                fyle = new Fyle(attachmentMetadata.getSha256());
                                fyle.id = db.fyleDao().insert(fyle);
                                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = new FyleMessageJoinWithStatus(
                                        fyle.id,
                                        message.id,
                                        messageSender.bytesOwnedIdentity,
                                        attachment.getUrl(),
                                        attachmentMetadata.getFileName(),
                                        attachmentMetadata.getType(),
                                        attachment.isDownloadRequested() ? FyleMessageJoinWithStatus.STATUS_DOWNLOADING : FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE,
                                        attachment.getExpectedLength(),
                                        (float) attachment.getReceivedLength() / attachment.getExpectedLength(),
                                        attachment.getMessageIdentifier(),
                                        attachment.getNumber(),
                                        imageResolution
                                );
                                db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
                                fyleMessageJoinWithStatusesToDownload.add(fyleMessageJoinWithStatus);
                            }
                            if (message.linkPreviewFyleId == null && Objects.equals(attachmentMetadata.getType(), OpenGraph.MIME_TYPE)) {
                                message.linkPreviewFyleId = fyle.id;
                                db.messageDao().updateLinkPreviewFyleId(message.id, fyle.id);
                            }
                        } finally {
                            Fyle.releaseLock(sha256);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Logger.d("Error reading an attachment or creating the fyle (or message already expired and deleted)");
                        engine.markAttachmentForDeletion(attachment);
                    }
                }
                if (messageSender.type == MessageSender.Type.CONTACT) {
                    OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(messageSender.bytesOwnedIdentity);
                    AndroidNotificationManager.displayReceivedMessageNotification(discussion, message, messageSender.contact, ownedIdentity);
                }

                engine.markMessageForDeletion(obvMessage.getBytesToIdentity(), obvMessage.getIdentifier());

                if (!fyleMessageJoinWithStatusesToDownload.isEmpty()) {
                    AvailableSpaceHelper.refreshAvailableSpace(false);
                }

                // auto-download attachments if needed
                long downloadSize = SettingsActivity.getAutoDownloadSize();

                for (FyleMessageJoinWithStatus fyleMessageJoinWithStatus : fyleMessageJoinWithStatusesToDownload) {
                    if (Objects.equals(message.linkPreviewFyleId, fyleMessageJoinWithStatus.fyleId) // always download link previews
                            || ((downloadSize == -1 || fyleMessageJoinWithStatus.size < downloadSize)
                            && (AvailableSpaceHelper.getAvailableSpace() == null || AvailableSpaceHelper.getAvailableSpace() > fyleMessageJoinWithStatus.size))) {
                        AppSingleton.getEngine().downloadSmallAttachment(obvMessage.getBytesToIdentity(), fyleMessageJoinWithStatus.engineMessageIdentifier, fyleMessageJoinWithStatus.engineNumber);
                        fyleMessageJoinWithStatus.status = FyleMessageJoinWithStatus.STATUS_DOWNLOADING;
                        AppDatabase.getInstance().fyleMessageJoinWithStatusDao().updateStatus(fyleMessageJoinWithStatus.messageId, fyleMessageJoinWithStatus.fyleId, fyleMessageJoinWithStatus.status);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // this must be run inside the transaction where the message is created.
    // It updates the latest discussion sequence number and updates the missing message count in the following sequenceNumber message (if this is not the latest)
    // It returns the number of missing messages before this one
    private long processSequenceNumber(@NonNull AppDatabase db, long discussionId, @NonNull byte[] senderIdentifier, @Nullable UUID senderThreadIdentifier, long senderSequenceNumber) {
        if (senderThreadIdentifier == null) {
            return 0;
        }

        LatestDiscussionSenderSequenceNumber latestDiscussionSenderSequenceNumber = db.latestDiscussionSenderSequenceNumberDao().get(discussionId, senderIdentifier, senderThreadIdentifier);

        if (latestDiscussionSenderSequenceNumber != null && senderSequenceNumber < latestDiscussionSenderSequenceNumber.latestSequenceNumber) {
            Message message = db.messageDao().getFollowingBySenderSequenceNumber(senderSequenceNumber, senderThreadIdentifier, senderIdentifier, discussionId);
            if (message == null) {
                return 0;
            }

            if (message.missedMessageCount < (message.senderSequenceNumber - senderSequenceNumber)) {
                // the message is older than the number of messages missed in the following message --> nothing to do
                return 0;
            } else {
                long remainingMissedCount = message.missedMessageCount - (message.senderSequenceNumber - senderSequenceNumber);

                message.missedMessageCount = message.senderSequenceNumber - 1 - senderSequenceNumber;
                db.messageDao().updateMissedMessageCount(message.id, message.missedMessageCount);

                return remainingMissedCount;
            }
        } else if (latestDiscussionSenderSequenceNumber != null && senderSequenceNumber > latestDiscussionSenderSequenceNumber.latestSequenceNumber) {
            db.latestDiscussionSenderSequenceNumberDao().updateLatestSequenceNumber(discussionId, senderIdentifier, senderThreadIdentifier, senderSequenceNumber);

            return senderSequenceNumber - 1 - latestDiscussionSenderSequenceNumber.latestSequenceNumber;
        } else if (latestDiscussionSenderSequenceNumber == null) {
            latestDiscussionSenderSequenceNumber = new LatestDiscussionSenderSequenceNumber(discussionId, senderIdentifier, senderThreadIdentifier, senderSequenceNumber);
            db.latestDiscussionSenderSequenceNumberDao().insert(latestDiscussionSenderSequenceNumber);
            return 0;
        } else { // senderSequenceNumber == latestSequenceNumber (this should normally not happen...)
            return 0;
        }
    }


    private void handleQuerySharedSettingsMessage(JsonQuerySharedSettings jsonQuerySharedSettings, @NonNull MessageSender messageSender) {
        Discussion discussion = getDiscussion(jsonQuerySharedSettings.getGroupUid(), jsonQuerySharedSettings.getGroupOwner(), jsonQuerySharedSettings.getGroupV2Identifier(), jsonQuerySharedSettings.getOneToOneIdentifier(), messageSender, null);

        if (discussion == null) {
            return;
        }

        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
        if (discussionCustomization == null) {
            // if there is no DiscussionCustomization there are no ephemeral settings for this discussion --> no need to send anything
            return;
        }

        JsonSharedSettings sharedSettings = discussionCustomization.getSharedSettingsJson();
        if (sharedSettings == null) {
            // there are no ephemeral settings for this discussion --> no need to send anything
            return;
        }

        if (jsonQuerySharedSettings.getKnownSharedSettingsVersion() != null && sharedSettings.getVersion() < jsonQuerySharedSettings.getKnownSharedSettingsVersion()) {
            // the user has a more recent version of the settings --> no need to send anything
            return;
        }

        if ((jsonQuerySharedSettings.getKnownSharedSettingsVersion() != null) && (sharedSettings.getVersion() == jsonQuerySharedSettings.getKnownSharedSettingsVersion()) && Objects.equals(sharedSettings.getJsonExpiration(), jsonQuerySharedSettings.getKnownSharedExpiration())) {
            // the user already has the latest version --> no need to send anything
            return;
        }

        Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, sharedSettings, messageSender.bytesOwnedIdentity, true, null);
        if (message != null) {
            message.postSettingsMessage(true, messageSender.getSenderIdentity());
        }
    }

    private void handleSharedSettingsMessage(JsonSharedSettings jsonSharedSettings, @NonNull MessageSender messageSender, long serverTimestamp) {
        Discussion discussion = getDiscussion(jsonSharedSettings.getGroupUid(), jsonSharedSettings.getGroupOwner(), jsonSharedSettings.getGroupV2Identifier(), jsonSharedSettings.getOneToOneIdentifier(), messageSender, GroupV2.Permission.CHANGE_SETTINGS);

        if (discussion == null) {
            return;
        }

        switch (discussion.discussionType) {
            case Discussion.TYPE_GROUP: {
                Group group = db.groupDao().get(messageSender.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                if (!Arrays.equals(group.bytesGroupOwnerIdentity, messageSender.getSenderIdentity())
                        && (group.bytesGroupOwnerIdentity != null || !Arrays.equals(messageSender.getSenderIdentity(), messageSender.bytesOwnedIdentity))) {
                    Logger.e("Received a group shared settings update by someone else than the group owner");
                    return;
                }
                break;
            }
            case Discussion.TYPE_GROUP_V2: // no need to check permissions, this is already done in getDiscussion()
            case Discussion.TYPE_CONTACT:
                break;
        }

        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
        if (discussionCustomization == null) {
            discussionCustomization = new DiscussionCustomization(discussion.id);
            db.discussionCustomizationDao().insert(discussionCustomization);
        }

        boolean oldReadOnce = discussionCustomization.settingReadOnce;
        Long oldVisibilityDuration = discussionCustomization.settingVisibilityDuration;
        Long oldExistenceDuration = discussionCustomization.settingExistenceDuration;

        boolean resendSettings = false;
        boolean gcdWasComputed = false;

        // check the version of the discussion customization
        if (discussionCustomization.sharedSettingsVersion == null || discussionCustomization.sharedSettingsVersion < jsonSharedSettings.getVersion()) {
            // the settings are newer --> replace them
            discussionCustomization.sharedSettingsVersion = jsonSharedSettings.getVersion();
            if (jsonSharedSettings.getJsonExpiration() == null) {
                discussionCustomization.settingReadOnce = false;
                discussionCustomization.settingVisibilityDuration = null;
                discussionCustomization.settingExistenceDuration = null;
            } else {
                discussionCustomization.settingReadOnce = jsonSharedSettings.getJsonExpiration().getReadOnce() != null && jsonSharedSettings.getJsonExpiration().getReadOnce();
                discussionCustomization.settingVisibilityDuration = jsonSharedSettings.getJsonExpiration().getVisibilityDuration();
                discussionCustomization.settingExistenceDuration = jsonSharedSettings.getJsonExpiration().getExistenceDuration();
            }
        } else if (discussionCustomization.sharedSettingsVersion == jsonSharedSettings.getVersion()) {
            // version are the same, compute the "gcd" of settings
            if (jsonSharedSettings.getJsonExpiration() == null) {
                // received settings impose no constraint --> do nothing
                return;
            } else {
                // if sharedSettingsVersion is non null, getExpirationJson() never returns null)
                JsonExpiration gcdExpiration = jsonSharedSettings.getJsonExpiration().computeGcd(discussionCustomization.getExpirationJson());
                discussionCustomization.settingReadOnce = gcdExpiration.getReadOnce() != null && gcdExpiration.getReadOnce();
                discussionCustomization.settingVisibilityDuration = gcdExpiration.getVisibilityDuration();
                discussionCustomization.settingExistenceDuration = gcdExpiration.getExistenceDuration();
                // also send GCDed settings to everyone, just in case, but only do this if the GCD actually changed something to the received settings
                resendSettings = !jsonSharedSettings.getJsonExpiration().equals(gcdExpiration);
                gcdWasComputed = true;
            }
        } else {
            // we received an older version --> ignore it and, for one-to-one or group v2 (when I have the permission) discussions, resend our current discussion settings, just in case
            resendSettings = true;
        }

        if (resendSettings) {
            if (discussion.discussionType == Discussion.TYPE_CONTACT) {
                jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                if (jsonSharedSettings != null) {
                    Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, messageSender.bytesOwnedIdentity, true, null);
                    if (message != null) {
                        message.postSettingsMessage(true, null);
                    }
                }
            } else if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                Group2 group2 = db.group2Dao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                if (group2 != null && group2.ownPermissionChangeSettings) {
                    jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                    if (jsonSharedSettings != null) {
                        Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, jsonSharedSettings, messageSender.bytesOwnedIdentity, true, null);
                        if (message != null) {
                            message.postSettingsMessage(true, gcdWasComputed ? null : messageSender.getSenderIdentity());
                        }
                    }
                }
            }
        }

        // if we arrive here, settings might have been updated
        if (oldReadOnce ^ discussionCustomization.settingReadOnce
                || !Objects.equals(oldVisibilityDuration, discussionCustomization.settingVisibilityDuration)
                || !Objects.equals(oldExistenceDuration, discussionCustomization.settingExistenceDuration)) {
            // there was indeed a change save it and add a message in the discussion
            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, discussionCustomization.getSharedSettingsJson(), messageSender.getSenderIdentity(), false, serverTimestamp);
            if (message != null) {
                message.id = db.messageDao().insert(message);
                db.discussionCustomizationDao().update(discussionCustomization);
            }
        }
    }

    private void handleDeleteDiscussion(JsonDeleteDiscussion jsonDeleteDiscussion, @NonNull MessageSender messageSender, long serverTimestamp) {
        Discussion discussion = getDiscussion(jsonDeleteDiscussion.getGroupUid(), jsonDeleteDiscussion.getGroupOwner(), jsonDeleteDiscussion.getGroupV2Identifier(), jsonDeleteDiscussion.getOneToOneIdentifier(), messageSender, GroupV2.Permission.REMOTE_DELETE_ANYTHING);

        if (discussion == null) {
            return;
        }
        int messagesToDelete = db.messageDao().countMessagesInDiscussion(discussion.id);

        new DeleteMessagesTask(discussion.bytesOwnedIdentity, discussion.id, false, true).run();
        // stop sharing location if needed
        if (UnifiedForegroundService.LocationSharingSubService.isDiscussionSharingLocation(discussion.id)) {
            UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussion.id, true);
        }
        // clear notifications if needed
        AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussion.id);
        // reload the discussion
        discussion = db.discussionDao().getById(discussion.id);
        if (messagesToDelete > 0) {
            Message message = Message.createDiscussionRemotelyDeletedMessage(db, discussion.id, messageSender.getSenderIdentity(), serverTimestamp);
            db.messageDao().insert(message);
            if (discussion.updateLastMessageTimestamp(serverTimestamp)) {
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            }
        }
    }

    private void handleDeleteMessages(JsonDeleteMessages jsonDeleteMessages, @NonNull MessageSender messageSender, long serverTimestamp) {
        if (jsonDeleteMessages.getMessageReferences() == null || jsonDeleteMessages.getMessageReferences().size() == 0) {
            return;
        }

        Discussion discussion = getDiscussion(jsonDeleteMessages.getGroupUid(), jsonDeleteMessages.getGroupOwner(), jsonDeleteMessages.getGroupV2Identifier(), jsonDeleteMessages.getOneToOneIdentifier(), messageSender, null);
        if (discussion == null) {
            return;
        }

        boolean remoteDeletePermission = getDiscussion(jsonDeleteMessages.getGroupUid(), jsonDeleteMessages.getGroupOwner(), jsonDeleteMessages.getGroupV2Identifier(), jsonDeleteMessages.getOneToOneIdentifier(), messageSender, GroupV2.Permission.REMOTE_DELETE_ANYTHING) != null;
        boolean editAndDeleteOwnMessagesPermission = getDiscussion(jsonDeleteMessages.getGroupUid(), jsonDeleteMessages.getGroupOwner(), jsonDeleteMessages.getGroupV2Identifier(), jsonDeleteMessages.getOneToOneIdentifier(), messageSender, GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES) != null;

        for (JsonMessageReference messageReference : jsonDeleteMessages.getMessageReferences()) {
            Message message = db.messageDao().getBySenderSequenceNumber(messageReference.getSenderSequenceNumber(), messageReference.getSenderThreadIdentifier(), messageReference.getSenderIdentifier(), discussion.id);
            if (message != null) {
                // only delete if the user has the permission to delete other users' messages
                if (messageSender.type == MessageSender.Type.OWNED_IDENTITY
                        || remoteDeletePermission
                        || (editAndDeleteOwnMessagesPermission && Arrays.equals(message.senderIdentifier, messageSender.getSenderIdentity()))) {
                    // stop sharing location if needed
                    if (message.isCurrentSharingOutboundLocationMessage()) {
                        UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussion.id, true);
                    }

                    if (messageSender.type == MessageSender.Type.OWNED_IDENTITY || !SettingsActivity.getRetainRemoteDeletedMessages()) {
                        db.runInTransaction(() -> message.delete(db));
                    } else {
                        db.runInTransaction(() -> {
                            message.remoteDelete(db, messageSender.getSenderIdentity(), serverTimestamp);
                            message.deleteAttachments(db);
                        });
                    }
                    AndroidNotificationManager.remoteDeleteMessageNotification(discussion, message.id);
                }
            } else {
                RemoteDeleteAndEditRequest remoteDeleteAndEditRequest = db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(messageReference.getSenderSequenceNumber(), messageReference.getSenderThreadIdentifier(), messageReference.getSenderIdentifier(), discussion.id);
                if (remoteDeleteAndEditRequest != null) {
                    // an edit/delete request already exists
                    if (remoteDeleteAndEditRequest.requestType != RemoteDeleteAndEditRequest.TYPE_DELETE
                            || messageSender.type == MessageSender.Type.OWNED_IDENTITY
                            || remoteDeletePermission
                            || (editAndDeleteOwnMessagesPermission && Arrays.equals(messageReference.getSenderIdentifier(), messageSender.getSenderIdentity()))) {
                        // delete the edit, replace it with our new delete
                        db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest);
                    } else {
                        // we already have a delete and the new one does not have the proper permission --> do nothing
                        return;
                    }
                }

                remoteDeleteAndEditRequest = new RemoteDeleteAndEditRequest(discussion.id, messageReference.getSenderIdentifier(), messageReference.getSenderThreadIdentifier(), messageReference.getSenderSequenceNumber(), serverTimestamp, RemoteDeleteAndEditRequest.TYPE_DELETE, null, null, messageSender.getSenderIdentity());
                db.remoteDeleteAndEditRequestDao().insert(remoteDeleteAndEditRequest);
            }
        }
    }

    private void handleUpdateMessage(JsonUpdateMessage jsonUpdateMessage, @NonNull MessageSender messageSender, long serverTimestamp) {
        if (jsonUpdateMessage.getMessageReference() == null
                || !Arrays.equals(messageSender.getSenderIdentity(), jsonUpdateMessage.getMessageReference().getSenderIdentifier())) { // only the original author can edit a message
            return;
        }

        Discussion discussion = getDiscussion(jsonUpdateMessage.getGroupUid(), jsonUpdateMessage.getGroupOwner(), jsonUpdateMessage.getGroupV2Identifier(), jsonUpdateMessage.getOneToOneIdentifier(), messageSender, GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);

        if (discussion == null) {
            return;
        }

        Message message = db.messageDao().getBySenderSequenceNumber(jsonUpdateMessage.getMessageReference().getSenderSequenceNumber(), jsonUpdateMessage.getMessageReference().getSenderThreadIdentifier(), jsonUpdateMessage.getMessageReference().getSenderIdentifier(), discussion.id);
        String newBody = jsonUpdateMessage.getBody() == null ? null : jsonUpdateMessage.getBody().trim();
        String newMentions = null;
        try {
            jsonUpdateMessage.sanitizeJsonUserMentions();
            newMentions = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonUpdateMessage.getJsonUserMentions());
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.w("Unable to serialize mentions");
        }

        if (message != null) {
            if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                return;
            }

            // normal updateMessage message
            if (jsonUpdateMessage.getJsonLocation() == null) {
                List<JsonUserMention> mentions = message.getMentions();
                HashSet<JsonUserMention> mentionSet = mentions == null ? null : new HashSet<>(mentions);
                HashSet<JsonUserMention> newMentionSet = jsonUpdateMessage.getJsonUserMentions() == null ? null : new HashSet<>(jsonUpdateMessage.getJsonUserMentions());
                boolean mentionsDidNotChange = Objects.equals(mentionSet, newMentionSet);
                if (Objects.equals(message.contentBody, newBody) && mentionsDidNotChange) {
                    // no update needed --> do nothing
                    return;
                }

                String finalNewMentions = newMentions;
                db.runInTransaction(() -> {
                    message.jsonMentions = finalNewMentions;
                    db.messageDao().updateMentions(message.id, finalNewMentions);
                    db.messageDao().updateMentioned(message.id, message.isIdentityMentioned(discussion.bytesOwnedIdentity) || message.isOwnMessageReply(discussion.bytesOwnedIdentity));
                    message.contentBody = newBody;
                    db.messageDao().updateBody(message.id, newBody);
                    db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_EDITED, serverTimestamp));
                });

                // never edit the content of an hidden message notification!
                if (message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE && messageSender.type == MessageSender.Type.CONTACT) {
                    boolean editMentionsMyself = false;
                    // check if I was mentioned in the update but not in the original message
                    if (!mentionsDidNotChange && newMentionSet != null) {
                        if (mentionSet != null) {
                            newMentionSet.removeAll(mentionSet);
                        }
                        for (JsonUserMention mention : newMentionSet) {
                            if (Arrays.equals(mention.getUserIdentifier(), discussion.bytesOwnedIdentity)) {
                                editMentionsMyself = true;
                                break;
                            }
                        }
                    }

                    AndroidNotificationManager.editMessageNotification(discussion, message, messageSender.contact, newBody, editMentionsMyself);
                }
            } else { // update location message
                // check message is a sharing location one
                if (message.jsonLocation == null) {
                    Logger.e("HandleNewMessageNotificationTask: trying to update a message that is not a location message");
                    return;
                }
                if (message.locationType != Message.LOCATION_TYPE_SHARE && message.locationType != Message.LOCATION_TYPE_SHARE_FINISHED) {
                    Logger.w("HandleNewMessageNotificationTask: trying to update a message that is not location sharing");
                    return;
                }

                JsonLocation jsonLocation = jsonUpdateMessage.getJsonLocation();
                // handle end of sharing messages
                if (jsonLocation.getType() == JsonLocation.TYPE_END_SHARING) {
                    db.messageDao().updateLocationType(message.id, Message.LOCATION_TYPE_SHARE_FINISHED);
                    db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_LOCATION_SHARING_END, serverTimestamp));
                }
                // handle simple location update messages
                else if (jsonLocation.getType() == JsonLocation.TYPE_SHARING) {
                    JsonLocation oldJsonLocation = message.getJsonLocation();
                    Long count;
                    if (oldJsonLocation == null) {
                        count = -1L;
                    } else {
                        count = oldJsonLocation.getCount();
                    }

                    // check count is valid
                    if (jsonLocation.getCount() != null && count != null && jsonLocation.getCount() > count) {
                        db.runInTransaction(() -> {
                            try {
                                // update json location data and compute new sharing expiration
                                String jsonLocationString = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonUpdateMessage.getJsonLocation());
                                db.messageDao().updateLocation(message.id, newBody, jsonLocationString);

                                // create or update location metadata for message
                                MessageMetadata messageMetadata = db.messageMetadataDao().getByKind(message.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE);
                                if (messageMetadata != null) {
                                    db.messageMetadataDao().updateTimestamp(messageMetadata.id, serverTimestamp);
                                } else {
                                    db.messageMetadataDao().insert(new MessageMetadata(message.id, MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE, serverTimestamp));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        Logger.i("HandleNewMessageNotificationTask: updateLocationMessage: received invalid count, ignoring");
                    }
                }
            }
        } else {
            RemoteDeleteAndEditRequest remoteDeleteAndEditRequest = db.remoteDeleteAndEditRequestDao().getBySenderSequenceNumber(jsonUpdateMessage.getMessageReference().getSenderSequenceNumber(), jsonUpdateMessage.getMessageReference().getSenderThreadIdentifier(), jsonUpdateMessage.getMessageReference().getSenderIdentifier(), discussion.id);
            if (remoteDeleteAndEditRequest != null) {
                // an edit/delete request already exists
                if (remoteDeleteAndEditRequest.requestType == RemoteDeleteAndEditRequest.TYPE_DELETE || remoteDeleteAndEditRequest.serverTimestamp > serverTimestamp) {
                    // we have a delete or a newer edit --> ignore it
                    return;
                } else {
                    // the new edit will replace this one
                    db.remoteDeleteAndEditRequestDao().delete(remoteDeleteAndEditRequest);
                }
            }

            remoteDeleteAndEditRequest = new RemoteDeleteAndEditRequest(discussion.id, jsonUpdateMessage.getMessageReference().getSenderIdentifier(), jsonUpdateMessage.getMessageReference().getSenderThreadIdentifier(), jsonUpdateMessage.getMessageReference().getSenderSequenceNumber(), serverTimestamp, RemoteDeleteAndEditRequest.TYPE_EDIT, newBody, newMentions, null);
            db.remoteDeleteAndEditRequestDao().insert(remoteDeleteAndEditRequest);
        }
    }

    private void handleReactionMessage(@NonNull JsonReaction jsonReaction, @NonNull MessageSender messageSender, long serverTimestamp) {
        if (jsonReaction.getMessageReference() == null) {
            return;
        }

        Discussion discussion = getDiscussion(jsonReaction.getGroupUid(), jsonReaction.getGroupOwner(), jsonReaction.getGroupV2Identifier(), jsonReaction.getOneToOneIdentifier(), messageSender, null);

        if (discussion == null) {
            return;
        }

        Message message = db.messageDao().getBySenderSequenceNumber(
                jsonReaction.getMessageReference().getSenderSequenceNumber(),
                jsonReaction.getMessageReference().getSenderThreadIdentifier(),
                jsonReaction.getMessageReference().getSenderIdentifier(),
                discussion.id);

        if (message != null) {
            if (message.wipeStatus == Message.WIPE_STATUS_WIPED
                    || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                return;
            }

            // only show a notification if a contact reacted to an outbound message or a message I am mentioned in
            if (messageSender.type == MessageSender.Type.CONTACT && (message.messageType == Message.TYPE_OUTBOUND_MESSAGE || message.mentioned)) {
                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(messageSender.bytesOwnedIdentity);
                AndroidNotificationManager.displayReactionNotification(ownedIdentity, discussion, message, jsonReaction.getReaction(), messageSender.contact);
            }
            new UpdateReactionsTask(message.id, jsonReaction.getReaction(), messageSender.type == MessageSender.Type.CONTACT ? messageSender.getSenderIdentity() : null, serverTimestamp, false).run();
        } else {
            ReactionRequest reactionRequest = db.reactionRequestDao().getBySenderSequenceNumberAndReacter(jsonReaction.getMessageReference().getSenderSequenceNumber(), jsonReaction.getMessageReference().getSenderThreadIdentifier(), jsonReaction.getMessageReference().getSenderIdentifier(), discussion.id, messageSender.getSenderIdentity());
            if (reactionRequest != null) {
                if (reactionRequest.serverTimestamp < serverTimestamp) {
                    // we got a newer reaction --> update it
                    reactionRequest.reaction = jsonReaction.getReaction();
                    reactionRequest.serverTimestamp = serverTimestamp;
                    db.reactionRequestDao().update(reactionRequest);
                }
            } else {
                // also create a reactionRequest if the reaction is null, in case we later receive an older reaction we want to cancel
                reactionRequest = new ReactionRequest(
                        discussion.id,
                        jsonReaction.getMessageReference().getSenderIdentifier(),
                        jsonReaction.getMessageReference().getSenderThreadIdentifier(),
                        jsonReaction.getMessageReference().getSenderSequenceNumber(),
                        messageSender.getSenderIdentity(),
                        serverTimestamp,
                        jsonReaction.getReaction()
                );
                db.reactionRequestDao().insert(reactionRequest);
            }
        }
    }

    private void handleScreenCaptureDetectionMessage(@NonNull JsonScreenCaptureDetection jsonScreenCaptureDetection, @NonNull MessageSender messageSender, long serverTimestamp) {
        Discussion discussion = getDiscussion(jsonScreenCaptureDetection.getGroupUid(), jsonScreenCaptureDetection.getGroupOwner(), jsonScreenCaptureDetection.getGroupV2Identifier(), jsonScreenCaptureDetection.getOneToOneIdentifier(), messageSender, null);

        if (discussion == null) {
            return;
        }

        Message message = Message.createScreenShotDetectedMessage(db, discussion.id, messageSender.getSenderIdentity(), serverTimestamp);
        db.messageDao().insert(message);
        if (discussion.updateLastMessageTimestamp(message.timestamp)) {
            db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
        }
    }

    private void handleDiscussionReadMessage(@NonNull JsonDiscussionRead jsonDiscussionRead, @NonNull MessageSender messageSender) {
        if (messageSender.type != MessageSender.Type.OWNED_IDENTITY) {
            return;
        }

        Discussion discussion = getDiscussion(jsonDiscussionRead.getGroupUid(), jsonDiscussionRead.getGroupOwner(), jsonDiscussionRead.getGroupV2Identifier(), jsonDiscussionRead.getOneToOneIdentifier(), messageSender, null);

        if (discussion == null) {
            return;
        }

        db.messageDao().markDiscussionMessagesReadUpTo(discussion.id, jsonDiscussionRead.getLastReadMessageServerTimestamp());
        if (db.messageDao().getServerTimestampOfLatestUnreadInboundMessageInDiscussion(discussion.id) == null) {
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussion.id);
        }
    }


    private void handleLimitedVisibilityOpenedMessage(@NonNull JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened, @NonNull MessageSender messageSender, long serverTimestamp, long downloadTimestamp) {
        if (messageSender.type != MessageSender.Type.OWNED_IDENTITY || jsonLimitedVisibilityMessageOpened.messageReference == null) {
            return;
        }

        Discussion discussion = getDiscussion(jsonLimitedVisibilityMessageOpened.getGroupUid(), jsonLimitedVisibilityMessageOpened.getGroupOwner(), jsonLimitedVisibilityMessageOpened.getGroupV2Identifier(), jsonLimitedVisibilityMessageOpened.getOneToOneIdentifier(), messageSender, null);

        if (discussion == null) {
            return;
        }

        JsonMessageReference messageReference = jsonLimitedVisibilityMessageOpened.messageReference;
        Message message = db.messageDao().getBySenderSequenceNumber(messageReference.getSenderSequenceNumber(), messageReference.getSenderThreadIdentifier(), messageReference.getSenderIdentifier(), discussion.id);

        if (message == null) {
            return;
        }

        new InboundEphemeralMessageClicked(messageSender.bytesOwnedIdentity, message, downloadTimestamp - serverTimestamp).run();
    }



    private Discussion getDiscussion(byte[] bytesGroupUid, byte[] bytesGroupOwner, byte[] bytesGroupIdentifier, JsonOneToOneMessageIdentifier oneToOneMessageIdentifier, MessageSender messageSender, @Nullable GroupV2.Permission requiredPermission) {
        // handle the special case of messages from another device differently: the message shoud also be accepted for locked/pre discussions
        if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
            if (bytesGroupIdentifier != null) {
                return db.discussionDao().getByGroupIdentifierWithAnyStatus(messageSender.bytesOwnedIdentity, bytesGroupIdentifier);
            } else if (bytesGroupUid != null && bytesGroupOwner != null) {
                byte[] bytesGroupOwnerAndUid = new byte[bytesGroupUid.length + bytesGroupOwner.length];
                System.arraycopy(bytesGroupOwner, 0, bytesGroupOwnerAndUid, 0, bytesGroupOwner.length);
                System.arraycopy(bytesGroupUid, 0, bytesGroupOwnerAndUid, bytesGroupOwner.length, bytesGroupUid.length);
                return db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(messageSender.bytesOwnedIdentity, bytesGroupOwnerAndUid);
            } else if (oneToOneMessageIdentifier != null) {
                return db.discussionDao().getByContactWithAnyStatus(messageSender.bytesOwnedIdentity, oneToOneMessageIdentifier.getBytesContactIdentity(messageSender.bytesOwnedIdentity));
            } else {
                return null;
            }
        }

        // we keep this test for now to maintain backward compatibility, but oneToOneMessageIdentifier should always be non-null in this case
        if (bytesGroupUid == null && bytesGroupOwner == null && bytesGroupIdentifier == null) {
            if (oneToOneMessageIdentifier != null) {
                byte[] bytesContactIdentity = oneToOneMessageIdentifier.getBytesContactIdentity(messageSender.bytesOwnedIdentity);
                if (bytesContactIdentity == null) {
                    return null;
                }
                return db.discussionDao().getByContact(messageSender.bytesOwnedIdentity, bytesContactIdentity);
            } else {
                return db.discussionDao().getByContact(messageSender.bytesOwnedIdentity, messageSender.getSenderIdentity());
            }
        } else if (bytesGroupIdentifier != null) {
            // check the send is indeed a member or pending member with appropriate send message permission
            boolean requiredPermissionFulfilled = false;
            Group2Member group2Member = db.group2MemberDao().get(messageSender.bytesOwnedIdentity, bytesGroupIdentifier, messageSender.getSenderIdentity());
            if (group2Member != null) {
                if (requiredPermission != null) {
                    switch (requiredPermission) {
                        case GROUP_ADMIN:
                            requiredPermissionFulfilled = group2Member.permissionAdmin;
                            break;
                        case REMOTE_DELETE_ANYTHING:
                            requiredPermissionFulfilled = group2Member.permissionRemoteDeleteAnything;
                            break;
                        case EDIT_OR_REMOTE_DELETE_OWN_MESSAGES:
                            requiredPermissionFulfilled = group2Member.permissionEditOrRemoteDeleteOwnMessages;
                            break;
                        case CHANGE_SETTINGS:
                            requiredPermissionFulfilled = group2Member.permissionChangeSettings;
                            break;
                        case SEND_MESSAGE:
                            requiredPermissionFulfilled = group2Member.permissionSendMessage;
                            break;
                    }
                } else {
                    requiredPermissionFulfilled = true;
                }
            } else {
                Group2PendingMember group2PendingMember = db.group2PendingMemberDao().get(messageSender.bytesOwnedIdentity, bytesGroupIdentifier, messageSender.getSenderIdentity());
                if (group2PendingMember != null) {
                    if (requiredPermission != null) {
                        switch (requiredPermission) {
                            case GROUP_ADMIN:
                                requiredPermissionFulfilled = group2PendingMember.permissionAdmin;
                                break;
                            case REMOTE_DELETE_ANYTHING:
                                requiredPermissionFulfilled = group2PendingMember.permissionRemoteDeleteAnything;
                                break;
                            case EDIT_OR_REMOTE_DELETE_OWN_MESSAGES:
                                requiredPermissionFulfilled = group2PendingMember.permissionEditOrRemoteDeleteOwnMessages;
                                break;
                            case CHANGE_SETTINGS:
                                requiredPermissionFulfilled = group2PendingMember.permissionChangeSettings;
                                break;
                            case SEND_MESSAGE:
                                requiredPermissionFulfilled = group2PendingMember.permissionSendMessage;
                                break;
                        }
                    } else {
                        requiredPermissionFulfilled = true;
                    }
                }
            }

            if (!requiredPermissionFulfilled) {
                Logger.i("Received a group V2 message for an unknown group, from someone not in the group, or from someone without proper permission --> IGNORING IT!");
                return null;
            }

            return db.discussionDao().getByGroupIdentifier(messageSender.bytesOwnedIdentity, bytesGroupIdentifier);
        } else {
            if (bytesGroupUid == null || bytesGroupOwner == null) {
                Logger.i("Received a message with one of groupOwner or groupUid null, IGNORING IT!");
                return null;
            }
            byte[] bytesGroupOwnerAndUid = new byte[bytesGroupUid.length + bytesGroupOwner.length];
            System.arraycopy(bytesGroupOwner, 0, bytesGroupOwnerAndUid, 0, bytesGroupOwner.length);
            System.arraycopy(bytesGroupUid, 0, bytesGroupOwnerAndUid, bytesGroupOwner.length, bytesGroupUid.length);

            // only post the message in the discussion if the sender is indeed in this discussion!
            if ((db.contactGroupJoinDao().get(bytesGroupOwnerAndUid, messageSender.bytesOwnedIdentity, messageSender.getSenderIdentity()) == null &&
                    db.pendingGroupMemberDao().get(messageSender.bytesOwnedIdentity, bytesGroupOwnerAndUid, messageSender.getSenderIdentity()) == null)) {
                Logger.i("Received a message for an unknown group, or from someone not in the group, IGNORING IT!");
                return null;
            }

            return db.discussionDao().getByGroupOwnerAndUid(messageSender.bytesOwnedIdentity, bytesGroupOwnerAndUid);
        }
    }


    // region group v2 messages on hold

    private boolean putGroupV2MessageOnHoldIfAppropriate(@NonNull MessageSender messageSender, @Nullable byte[] bytesGroupIdentifier, @NonNull ObvMessage obvMessage) {
        if (bytesGroupIdentifier == null) {
            return false;
        }
        if (db.group2Dao().get(messageSender.bytesOwnedIdentity, bytesGroupIdentifier) == null) {
            if (obvMessage.getLocalDownloadTimestamp() < System.currentTimeMillis() - GROUP_V2_MESSAGES_TTL) {
                // if message is too old, do not put it on hold and let it be discarded
                return false;
            }

            synchronized (pendingGroupV2Messages) {
                // put the message on hold
                BytesKey ownedIdentityBytesKey = new BytesKey(messageSender.bytesOwnedIdentity);
                HashMap<BytesKey, List<ObvMessage>> ownedIdentityMap = pendingGroupV2Messages.get(ownedIdentityBytesKey);
                if (ownedIdentityMap == null) {
                    ownedIdentityMap = new HashMap<>();
                    pendingGroupV2Messages.put(ownedIdentityBytesKey, ownedIdentityMap);
                }
                BytesKey groupIdentifierBytesKey = new BytesKey(bytesGroupIdentifier);
                List<ObvMessage> groupMessages = ownedIdentityMap.get(groupIdentifierBytesKey);
                if (groupMessages == null) {
                    groupMessages = new ArrayList<>();
                    ownedIdentityMap.put(groupIdentifierBytesKey, groupMessages);
                }
                groupMessages.add(obvMessage);
            }
            return true;
        }
        return false;
    }

    // this method is called right after a groupV2 is created
    public static void processAllGroupV2MessagesOnHold(@NonNull Engine engine, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesGroupIdentifier) {
        synchronized (pendingGroupV2Messages) {
            BytesKey ownedIdentityBytesKey = new BytesKey(bytesOwnedIdentity);
            HashMap<BytesKey, List<ObvMessage>> ownedIdentityMap = pendingGroupV2Messages.get(ownedIdentityBytesKey);
            if (ownedIdentityMap != null) {
                BytesKey groupIdentifierBytesKey = new BytesKey(bytesGroupIdentifier);
                List<ObvMessage> groupMessages = ownedIdentityMap.remove(groupIdentifierBytesKey);
                if (groupMessages != null) {
                    for (ObvMessage obvMessage : groupMessages) {
                        new HandleNewMessageNotificationTask(engine, obvMessage).run();
                    }
                }
            }
        }
    }

    // endregion



    private static class MessageSender {
        private enum Type {
            CONTACT,
            OWNED_IDENTITY,
        }

        private final @NonNull Type type;

        private final Contact contact;
        private final @NonNull byte[] bytesOwnedIdentity;

        private MessageSender(@NonNull Type type, Contact contact, @NonNull byte[] bytesOwnedIdentity) {
            this.type = type;
            this.contact = contact;
            this.bytesOwnedIdentity = bytesOwnedIdentity;
        }

        @Nullable
        static MessageSender of(AppDatabase db, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
            if (Arrays.equals(bytesOwnedIdentity, bytesContactIdentity)) {
                return new MessageSender(Type.OWNED_IDENTITY, null, bytesOwnedIdentity);
            } else {
                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    return new MessageSender(Type.CONTACT, contact, bytesOwnedIdentity);
                } else {
                    return null;
                }
            }
        }

        public byte[] getSenderIdentity() {
            switch (type) {
                case CONTACT:
                    return contact.bytesContactIdentity;
                case OWNED_IDENTITY:
                default:
                    return bytesOwnedIdentity;
            }
        }
    }
}
