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

package io.olvid.messenger.webclient.listeners;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.ContactCacheSingleton;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.RequestMessageOuterClass.RequestMessageResponse;
import io.olvid.messenger.webclient.protobuf.datatypes.MessageOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifDeleteMessageOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifNewMessageOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifUpdateMessageOuterClass;

public class MessageListener {
    private final WebClientManager manager;
    ConcurrentHashMap<Long, MutableLiveData<Integer>> discussionWatchedMessagesCount;
    ConcurrentHashMap<Long, LiveData<List<Message>>> discussionMessagesLiveData;
    ConcurrentHashMap<Long, MessageObserver> messageObservers;

    public MessageListener(WebClientManager manager) {
        this.manager = manager;
        this.discussionWatchedMessagesCount = new ConcurrentHashMap<>();
        this.discussionMessagesLiveData = new ConcurrentHashMap<>();
        this.messageObservers = new ConcurrentHashMap<>();
    }

    // NB: this function shall be launched on main thread
    public void stop() {
        for (Long discussionId:this.messageObservers.keySet()) {
            if (this.messageObservers.get(discussionId) != null && this.discussionMessagesLiveData.get(discussionId) != null) {
                this.discussionMessagesLiveData.get(discussionId).removeObserver(this.messageObservers.get(discussionId));
            }
        }
        this.messageObservers = new ConcurrentHashMap<>();
        this.discussionWatchedMessagesCount = new ConcurrentHashMap<>();
        this.discussionMessagesLiveData = new ConcurrentHashMap<>();
        Logger.d("MessageListener: Stopped message listeners");
    }

    public void addListener(final long discussionId, int messageCount) {
        if (!discussionWatchedMessagesCount.containsKey(discussionId)) {
            MutableLiveData<Integer> mutableLiveData;
            mutableLiveData = new MutableLiveData<>(null);
            discussionWatchedMessagesCount.put(discussionId, mutableLiveData);
            LiveData<List<Message>> liveData = Transformations.switchMap(mutableLiveData, input -> {
                if (input == null) {
                    return (null);
                }
                return AppDatabase.getInstance().messageDao().getLastDiscussionMessages(discussionId, input);
            });
            discussionMessagesLiveData.put(discussionId, liveData);
            MessageObserver messageObserver = new MessageObserver(this.manager, liveData, discussionId);
            this.messageObservers.put(discussionId, messageObserver);
            new Handler(Looper.getMainLooper()) {}.post(() -> liveData.observeForever(messageObserver));
            Logger.d("MessageListener: Launched listener for: " + discussionId);
            //Send drafts if first subscription to this discussion
            CreateAndSendDraft fillProtobuf = new CreateAndSendDraft(discussionId, this.manager);
            Thread t1 = new Thread(fillProtobuf);
            t1.start();

        }
        else {
            Logger.d("MessageListener: Listener already launched for: " + discussionId);

        }
        discussionWatchedMessagesCount.get(discussionId).postValue(messageCount);
    }

    public void removeListener(final long discussionId) {
        LiveData<List<Message>> liveData;
        MessageObserver observer;
        discussionWatchedMessagesCount.remove(discussionId);
        if (discussionMessagesLiveData.containsKey(discussionId) && this.messageObservers.containsKey(discussionId)) {
            liveData = discussionMessagesLiveData.get(discussionId);
            observer = this.messageObservers.get(discussionId);
            if (liveData != null && observer != null) {
                new Handler(Looper.getMainLooper()).post(() -> liveData.removeObserver(observer));
            }
            Logger.d("MessageListener: Removed listener for discussion: " + discussionId);
        }
        discussionMessagesLiveData.remove(discussionId);
        this.messageObservers.remove(discussionId);
    }

    static class MessageObserver extends AbstractObserver<Long, Message> {
        private final WebClientManager webClientManager;
        final long discussionId;

        MessageObserver(WebClientManager manager, LiveData<List<Message>> liveData, long discussionId) {
            super(liveData);
            this.webClientManager = manager;
            this.discussionId = discussionId;
        }

        static class batchAndSendNewMessagesTask implements Runnable {
            private final WebClientManager manager;
            private final List<Message> elements;
            private final long discussionId;

            batchAndSendNewMessagesTask(WebClientManager manager, List<Message> elements, final long discussionId) {
                this.manager = manager;
                this.elements = elements;
                this.discussionId = discussionId;
            }

            @Override
            public void run() {
                int index;
                RequestMessageResponse.Builder requestMessageResponseBuilder;
                ColissimoOuterClass.Colissimo colissimo;

                index = 0;
                requestMessageResponseBuilder = RequestMessageResponse.newBuilder();
                // add as many messages as possible in requestMessageResponse, if size is exceeded, send other messages using notif_new_message messages
                while (index < elements.size()) {
                    Message message = elements.get(index);
                    requestMessageResponseBuilder.addMessages(FillProtobufMessageFromOlvidMessageAndSend.fillProtobufMessageFromOlvidMessage(message, this.manager.getService().getWebClientContext(), this.manager));
                    index++;
                    if (requestMessageResponseBuilder.build().getSerializedSize() > Constants.MAX_PAYLOAD_SIZE) {
                        requestMessageResponseBuilder.removeMessages(requestMessageResponseBuilder.getMessagesCount() - 1);
                        index--;
                        Logger.d("WebClient: request_message_response payload too large. Limiting to " + requestMessageResponseBuilder.getMessagesCount() + "/" + elements.size() + " messages");
                        break;
                    }
                    // only add the listener here, in case we exceeded the MAX_PAYLOAD_SIZE and message was removed from colissimo
                    if(message.totalAttachmentCount > 0) {
                        this.manager.getAttachmentListener().addListener(message.id);
                    }
                }

                // send response colissimo first
                requestMessageResponseBuilder.setDiscussionId(discussionId);
                colissimo = ColissimoOuterClass.Colissimo.newBuilder()
                        .setType(ColissimoOuterClass.ColissimoType.REQUEST_MESSAGES_RESPONSE)
                        .setRequestMessageResponse(requestMessageResponseBuilder.build())
                        .build();
                manager.sendColissimo(colissimo);

                // if all discussions were sent just quit
                if (index == elements.size()) {
                    return;
                }

                // send other messages in notif messages
                NotifNewMessageOuterClass.NotifNewMessage.Builder notifBuilder = NotifNewMessageOuterClass.NotifNewMessage.newBuilder();
                while (index < elements.size()) {
                    Message message = elements.get(index);
                    notifBuilder.addMessages(FillProtobufMessageFromOlvidMessageAndSend.fillProtobufMessageFromOlvidMessage(message, this.manager.getService().getWebClientContext(), this.manager));
                    index++;
                    if (notifBuilder.build().getSerializedSize() > Constants.MAX_PAYLOAD_SIZE) {
                        notifBuilder.removeMessages(notifBuilder.getMessagesCount() - 1);
                        index--;
                        Logger.d("WebClient: notif_new_message payload too large. Limiting to " + notifBuilder.getMessagesCount() + "/" + elements.size() + " messages");

                        if (notifBuilder.getMessagesCount() == 0) {
                            Logger.w("WebClient:  a single Message exceeds the MAX_PAYLOAD_SIZE. Skipping the whole Message");
                            index++;
                        } else {
                            colissimo = ColissimoOuterClass.Colissimo.newBuilder()
                                    .setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE)
                                    .setNotifNewMessage(notifBuilder.build())
                                    .build();
                            this.manager.sendColissimo(colissimo);
                        }
                        notifBuilder = NotifNewMessageOuterClass.NotifNewMessage.newBuilder();
                    } else {
                        // only add the listener here, in case we exceeded the MAX_PAYLOAD_SIZE and message was removed from colissimo
                        if (message.totalAttachmentCount > 0) {
                            this.manager.getAttachmentListener().addListener(message.id);
                        }
                    }
                }

                // send last notif message if necessary
                if (notifBuilder.getMessagesCount() > 0) {
                    colissimo = ColissimoOuterClass.Colissimo.newBuilder()
                            .setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE)
                            .setNotifNewMessage(notifBuilder.build())
                            .build();
                    this.manager.sendColissimo(colissimo);
                }
            }
        }

        @Override
        boolean batchedElementHandler(List<Message> elements) {
            Runnable task = new batchAndSendNewMessagesTask(webClientManager, elements, this.discussionId);
            Thread thread = new Thread(task);
            thread.start();
            return true;
        }

        @Override
        boolean equals(Message element1, Message element2) {
            boolean sameContent = Objects.equals(element1.contentBody, element2.contentBody);
            boolean sameReactions = Objects.equals(element1.reactions, element2.reactions);
            boolean sameLocation = Objects.equals(element1.jsonLocation, element2.jsonLocation)
                    && element1.locationType == element2.locationType;
            return sameContent && sameReactions && sameLocation
                    && element1.id == element2.id
                    && element1.status == element2.status
                    && element1.wipeStatus == element2.wipeStatus
                    && element1.edited == element2.edited;
        }

        @Override
        Long getElementKey(Message element) {
            return (element.id);
        }

        @Override
        public void onChanged(List<Message> elements) {
            super.onChanged(elements);
        }

        @Override
        void newElementHandler(Message element) {
            //add listener for attachments in this message
            if(element.totalAttachmentCount > 0) {
                this.webClientManager.getAttachmentListener().addListener(element.id);
            }
            FillProtobufMessageFromOlvidMessageAndSend fillProtobuf = new FillProtobufMessageFromOlvidMessageAndSend(element, this.webClientManager, ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        @Override
        void modifiedElementHandler(Message element) {
            FillProtobufMessageFromOlvidMessageAndSend fillProtobuf = new FillProtobufMessageFromOlvidMessageAndSend(element, this.webClientManager, ColissimoOuterClass.ColissimoType.NOTIF_UPDATE_MESSAGE);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        @Override
        void deletedElementHandler(Message element) {
            this.webClientManager.getAttachmentListener().removeListener(element.id);
            FillProtobufMessageFromOlvidMessageAndSend fillProtobuf = new FillProtobufMessageFromOlvidMessageAndSend(element, this.webClientManager, ColissimoOuterClass.ColissimoType.NOTIF_DELETE_MESSAGE);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        private static class FillProtobufMessageFromOlvidMessageAndSend implements Runnable {
            final Message message;
            final WebClientManager webClientManager;
            final ColissimoOuterClass.ColissimoType colissimoType;
            FillProtobufMessageFromOlvidMessageAndSend(Message message, WebClientManager webClientManager, ColissimoOuterClass.ColissimoType colissimoType) {
                this.message=message;
                this.webClientManager = webClientManager;
                this.colissimoType = colissimoType;
            }

            @Override
            public void run() {
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                if (colissimoType == ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE) {
                    colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE);
                    NotifNewMessageOuterClass.NotifNewMessage.Builder notifBuilder = NotifNewMessageOuterClass.NotifNewMessage.newBuilder();
                    notifBuilder.addMessages(fillProtobufMessageFromOlvidMessage(message, this.webClientManager.getService().getWebClientContext(), this.webClientManager));
                    colissimoBuilder.setNotifNewMessage(notifBuilder);
                } else if (colissimoType == ColissimoOuterClass.ColissimoType.NOTIF_UPDATE_MESSAGE) {
                    colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_UPDATE_MESSAGE);
                    NotifUpdateMessageOuterClass.NotifUpdateMessage.Builder notifBuilder = NotifUpdateMessageOuterClass.NotifUpdateMessage.newBuilder();
                    notifBuilder.setMessage(fillProtobufMessageFromOlvidMessage(message, this.webClientManager.getService().getWebClientContext(), this.webClientManager));
                    colissimoBuilder.setNotifUpdateMessage(notifBuilder);
                } else if (colissimoType == ColissimoOuterClass.ColissimoType.NOTIF_DELETE_MESSAGE) {
                    colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_DELETE_MESSAGE);
                    NotifDeleteMessageOuterClass.NotifDeleteMessage.Builder notifBuilder = NotifDeleteMessageOuterClass.NotifDeleteMessage.newBuilder();
                    notifBuilder.setMessage(fillProtobufMessageFromOlvidMessage(message, this.webClientManager.getService().getWebClientContext(), this.webClientManager));
                    colissimoBuilder.setNotifDeleteMessage(notifBuilder);
                }
                this.webClientManager.sendColissimo(colissimoBuilder.build());
            }

            public static MessageOuterClass.Message.Builder fillProtobufMessageFromOlvidMessage(Message message, @NonNull Context context, WebClientManager manager) {
                MessageOuterClass.Message.Builder messageBuilder;

                // fill essential data
                messageBuilder = MessageOuterClass.Message.newBuilder();
                messageBuilder.setId(message.id);
                messageBuilder.setDiscussionId(message.discussionId);
                messageBuilder.setSortIndex(message.sortIndex);
                messageBuilder.setStatusValue(message.status);
                messageBuilder.setTypeValue(message.messageType + 1);
                messageBuilder.setTimestamp(message.timestamp);

                // If message have been wiped, only send needed info
                if (message.wipeStatus != Message.WIPE_STATUS_NONE && message.wipeStatus != Message.WIPE_STATUS_WIPE_ON_READ) {
                    messageBuilder.setRemotelyDeleted(message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED);
                    messageBuilder.setWiped(message.wipeStatus == Message.WIPE_STATUS_WIPED);
                    return messageBuilder;
                }

                // Same mechanism with limited visibility  messages (read once, limited visibility duration), and discussion settings update messages
                if(message.messageType == Message.TYPE_DISCUSSION_SETTINGS_UPDATE || message.jsonExpiration != null) {
                    try {
                        boolean readOnce = false;
                        String visibility = "";
                        String existence = "";
                        Long visibilityDuration = null;
                        Long existenceDuration = null;

                        if (message.messageType == Message.TYPE_DISCUSSION_SETTINGS_UPDATE) {
                            JsonSharedSettings jsonSharedSettings = AppSingleton.getJsonObjectMapper().readValue(message.contentBody, JsonSharedSettings.class);
                            if (jsonSharedSettings != null && jsonSharedSettings.getJsonExpiration() != null) {
                                visibilityDuration = jsonSharedSettings.getJsonExpiration().getVisibilityDuration();
                                existenceDuration = jsonSharedSettings.getJsonExpiration().getExistenceDuration();
                                readOnce = jsonSharedSettings.getJsonExpiration().getReadOnce() != null && jsonSharedSettings.getJsonExpiration().getReadOnce();
                            }
                        } else {
                            JsonExpiration jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, JsonExpiration.class);
                            visibilityDuration = jsonExpiration.getVisibilityDuration();
                            existenceDuration = jsonExpiration.getExistenceDuration();
                            readOnce = jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce();
                        }

                        if (visibilityDuration != null) {
                            if (visibilityDuration < 60) {
                                visibility = context.getString(R.string.text_timer_s, visibilityDuration);
                            } else if (visibilityDuration < 3600) {
                                visibility = context.getString(R.string.text_timer_m, visibilityDuration / 60);
                            } else if (visibilityDuration < 86400) {
                                visibility = context.getString(R.string.text_timer_h, visibilityDuration / 3600);
                            } else if (visibilityDuration < 31536000) {
                                visibility = context.getString(R.string.text_timer_d, visibilityDuration / 86400);
                            } else {
                                visibility = context.getString(R.string.text_timer_y, visibilityDuration / 31536000);
                            }
                        }
                        if (existenceDuration != null) {
                            if (existenceDuration < 60) {
                                existence = context.getString(R.string.text_timer_s, existenceDuration);
                            } else if (existenceDuration < 3600) {
                                existence = context.getString(R.string.text_timer_m, existenceDuration / 60);
                            } else if (existenceDuration < 86400) {
                                existence = context.getString(R.string.text_timer_h, existenceDuration / 3600);
                            } else if (existenceDuration < 31536000) {
                                existence = context.getString(R.string.text_timer_d, existenceDuration / 86400);
                            } else {
                                existence = context.getString(R.string.text_timer_y, existenceDuration / 31536000);
                            }
                        }
                        messageBuilder.setReadOnce(readOnce);
                        messageBuilder.setVisibilityDuration(visibility);
                        messageBuilder.setExistenceDuration(existence);
                    } catch (Exception e) {
                        Logger.e("Unable to parse jsonExpiration in MessageListener", e);
                    }
                }

                // if message is read once have a limited visibility duration, do not send more info to web client
                // warning TYPE_DISCUSSION_SETTINGS_UPDATE have to be filled
                if  (message.messageType != Message.TYPE_DISCUSSION_SETTINGS_UPDATE &&
                        (messageBuilder.getReadOnce() || !messageBuilder.getVisibilityDuration().isEmpty())) {
                    return messageBuilder;
                }

                // fill other data needed for messages shown in webclient
                messageBuilder.setEdited(message.edited);
                messageBuilder.setWipeOnRead(message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ);
                //set content (handle location message manually)
                if (message.isLocationMessage()) {
                    StringBuilder sb = new StringBuilder();
                    // if sharing message add getStringContent to specify it is sharing
                    if (message.locationType == Message.LOCATION_TYPE_SHARE || message.locationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                        sb.append(message.getStringContent(context)).append("\n");
                    }
                    // add address if possible
                    //noinspection DataFlowIssue
                    String address = message.getJsonLocation().getAddress();
                    if (address != null && !address.isEmpty()) {
                        sb.append(address).append("\n");
                    }
                    // add contentBody to get a link to google maps
                    sb.append(message.contentBody);
                    messageBuilder.setContentBody(sb.toString());
                }
                else {
                    //using getStringContent returns correct string for ephemeral messages, remotely deleted messages and wiped messages
                    messageBuilder.setContentBody(message.getStringContent(context));
                }

                if (message.isContentHidden()) {
                    messageBuilder.setTotalAttachmentCount(0);
                    messageBuilder.setImageCount(0);
                } else {
                    messageBuilder.setTotalAttachmentCount(message.totalAttachmentCount);
                    messageBuilder.setImageCount(message.imageCount);
                }
                messageBuilder.setSenderIdentifier(ByteString.copyFrom(message.senderIdentifier));
                String contactName = ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(message.senderIdentifier);
                if (contactName == null) {
                    messageBuilder.setSenderName(context.getString(R.string.text_deleted_contact));
                    messageBuilder.setSenderIsSelf(false);
                } else {
                    if (Arrays.equals(manager.getBytesCurrentOwnedIdentity(), message.senderIdentifier)){
                        messageBuilder.setSenderName(context.getString(R.string.text_you));
                        messageBuilder.setSenderIsSelf(true);
                    } else{
                        messageBuilder.setSenderName(contactName);
                        messageBuilder.setSenderIsSelf(false);
                    }
                }

                if(message.reactions != null && !message.reactions.isEmpty()){
                    messageBuilder.setReactions(message.reactions);
                }

                if(message.getJsonMessage().getJsonReply() != null && !message.isContentHidden()){
                    Message replyMessage = AppDatabase.getInstance().messageDao().getBySenderSequenceNumber(message.getJsonMessage().getJsonReply().getSenderSequenceNumber(), message.getJsonMessage().getJsonReply().getSenderThreadIdentifier(), message.getJsonMessage().getJsonReply().getSenderIdentifier(), message.discussionId);
                    if(replyMessage != null) {
                        messageBuilder.setReplyMessageId(replyMessage.id);
                        messageBuilder.setReplyMessageAttachmentCount(replyMessage.totalAttachmentCount);
                        messageBuilder.setReplySenderIdentifier(ByteString.copyFrom(message.getJsonMessage().getJsonReply().getSenderIdentifier()));
                        String replyContactName = ContactCacheSingleton.INSTANCE.getContactCustomDisplayName(message.getJsonMessage().getJsonReply().getSenderIdentifier());
                        if (replyContactName == null) {
                            messageBuilder.setReplyAuthor(context.getString(R.string.text_deleted_contact));
                            messageBuilder.setSenderIsSelf(false);
                        } else {
                            if(Arrays.equals(manager.getBytesCurrentOwnedIdentity(), message.getJsonMessage().getJsonReply().getSenderIdentifier())){
                                messageBuilder.setReplyAuthor(context.getString(R.string.text_you));
                                messageBuilder.setSenderIsSelf(true);
                            } else{
                                messageBuilder.setReplyAuthor(replyContactName);
                                messageBuilder.setSenderIsSelf(false);
                            }
                        }
                        // truncate the replyBody to 500 bytes max
                        String replyBody = replyMessage.getStringContent(context);
                        if (replyBody.length() > 500) {
                            replyBody = replyBody.substring(0, 499) + "…";
                        }
                        messageBuilder.setReplyBody(replyBody);
                    }
                }
                if (messageBuilder.build().getSerializedSize() > Constants.MAX_PAYLOAD_SIZE) {
                    if (message.contentBody != null && message.contentBody.length() > 500) {
                        Logger.d("WebClient: a single Message exceeds the MAX_PAYLOAD_SIZE. Removing the contentBody from this Message");
                        messageBuilder.setContentBody(context.getString(R.string.text_message_too_large_for_webclient));
                    }
                }
                return messageBuilder;
            }
        }
    }

    private static class CreateAndSendDraft implements Runnable {
        final WebClientManager webClientManager;
        private final long discussionId;

        CreateAndSendDraft(long discussionId, WebClientManager webClientManager) {
            this.discussionId = discussionId;
            this.webClientManager = webClientManager;
        }

        @Override
        public void run() {
            MessageOuterClass.Message.Builder messageBuilder;
            NotifNewMessageOuterClass.NotifNewMessage.Builder notifBuilder;
            ColissimoOuterClass.Colissimo.Builder colissimoBuilder;

            notifBuilder = NotifNewMessageOuterClass.NotifNewMessage.newBuilder();
            colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
            io.olvid.messenger.databases.entity.Message draft = AppDatabase.getInstance().messageDao().getDiscussionDraftMessageSync(discussionId);
            if(draft != null){
                messageBuilder = MessageObserver.FillProtobufMessageFromOlvidMessageAndSend.fillProtobufMessageFromOlvidMessage(draft, this.webClientManager.getService().getWebClientContext(), this.webClientManager);
                notifBuilder.addMessages(messageBuilder);
                colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE);
                colissimoBuilder.setNotifNewMessage(notifBuilder);
                this.webClientManager.sendColissimo(colissimoBuilder.build());
            }
        }
    }
}
