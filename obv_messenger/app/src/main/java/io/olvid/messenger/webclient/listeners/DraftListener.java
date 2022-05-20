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

package io.olvid.messenger.webclient.listeners;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.HashMap;
import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.datatypes.MessageOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifNewMessageOuterClass;

public class DraftListener {
    private final WebClientManager manager;
    final HashMap<Long, LiveData<Message>> discussionDraftLiveData;
    final HashMap<Long, DraftListener.DraftObserver> draftObservers;


    public DraftListener(WebClientManager manager) {
        this.manager = manager;
        this.discussionDraftLiveData = new HashMap<>();
        this.draftObservers = new HashMap<>();
    }

    // NB: this function shall be launched on main thread
    public void stop() {
        for (Long discussionId:this.draftObservers.keySet()) {
            if (this.draftObservers.get(discussionId) != null && this.discussionDraftLiveData.get(discussionId) != null) {
                    this.discussionDraftLiveData.get(discussionId).removeObserver(this.draftObservers.get(discussionId));
                    this.discussionDraftLiveData.remove(discussionId);
            }
        }
        Logger.d("DraftListener: Stopped draft listeners");
    }

    public void addListener(final long discussionId) {
        if (!discussionDraftLiveData.containsKey(discussionId)) {
            LiveData<Message> liveData = AppDatabase.getInstance().messageDao().getDiscussionDraftMessage(discussionId);
            discussionDraftLiveData.put(discussionId, liveData);
            DraftListener.DraftObserver draftObserver = new DraftListener.DraftObserver(this.manager);
            this.draftObservers.put(discussionId, draftObserver);
            new Handler(Looper.getMainLooper()).post(() -> liveData.observeForever(draftObserver));
            Logger.e("DraftListener: Launched draft listener for: " + discussionId);
        } else {
            Logger.d("DraftListener: Draft listener already launched for: " + discussionId);
        }
    }

    static class DraftObserver  implements androidx.lifecycle.Observer<Message> {
        private final WebClientManager manager;

        DraftObserver(WebClientManager manager) {
            this.manager = manager;
        }

        @Override
        public void onChanged(Message message) {
            if (message != null) { // new draft or change in draft
                FillProtobufMessageFromOlvidMessageAndSend fillProtobuf = new FillProtobufMessageFromOlvidMessageAndSend(message, this.manager);
                Thread t1 = new Thread(fillProtobuf);
                t1.start();
            }
        }

        private static class FillProtobufMessageFromOlvidMessageAndSend implements Runnable {
            final Message message;
            final WebClientManager webClientManager;

            FillProtobufMessageFromOlvidMessageAndSend(Message message, WebClientManager webClientManager) {
                this.message=message;
                this.webClientManager = webClientManager;
            }

            @Override
            public void run() {
                MessageOuterClass.Message.Builder messageBuilder;
                NotifNewMessageOuterClass.NotifNewMessage.Builder notifBuilder;
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder;

                notifBuilder = NotifNewMessageOuterClass.NotifNewMessage.newBuilder();
                colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                messageBuilder = fillProtobufMessageFromOlvidMessage(message, this.webClientManager.getService().getWebClientContext(), this.webClientManager);
                notifBuilder.addMessages(messageBuilder);
                colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_MESSAGE);
                colissimoBuilder.setNotifNewMessage(notifBuilder);
                this.webClientManager.sendColissimo(colissimoBuilder.build());
            }

            MessageOuterClass.Message.Builder fillProtobufMessageFromOlvidMessage(Message message, Context context, WebClientManager manager) {
                MessageOuterClass.Message.Builder messageBuilder;

                messageBuilder = MessageOuterClass.Message.newBuilder();
                messageBuilder.setId(message.id);
                messageBuilder.setDiscussionId(message.discussionId);
                messageBuilder.setSortIndex(message.sortIndex);
                messageBuilder.setStatusValue(message.status);
                messageBuilder.setTypeValue(message.messageType + 1);
                messageBuilder.setContentBody(message.getStringContent(context));
                messageBuilder.setTimestamp(message.timestamp);
                if (message.isContentHidden()) {
                    messageBuilder.setTotalAttachmentCount(0);
                    messageBuilder.setImageCount(0);
                } else {
                    messageBuilder.setTotalAttachmentCount(message.totalAttachmentCount);
                    messageBuilder.setImageCount(message.imageCount);
                }
                messageBuilder.setSenderIdentifier(ByteString.copyFrom(message.senderIdentifier));
                String contactName = AppSingleton.getContactCustomDisplayName(message.senderIdentifier);
                if (contactName == null) {
                    contactName = context.getString(R.string.text_deleted_contact);
                }
                messageBuilder.setSenderName(contactName);
                if(message.getJsonMessage() != null && message.getJsonMessage().getJsonReply() != null && !message.isContentHidden()) {
                    Message replyMessage = AppDatabase.getInstance().messageDao().getBySenderSequenceNumber(message.getJsonMessage().getJsonReply().getSenderSequenceNumber(), message.getJsonMessage().getJsonReply().getSenderThreadIdentifier(), message.getJsonMessage().getJsonReply().getSenderIdentifier(), message.discussionId);
                    if (replyMessage != null) {
                        messageBuilder.setReplyMessageId(replyMessage.id);
                        messageBuilder.setReplyMessageAttachmentCount(replyMessage.totalAttachmentCount);
                        messageBuilder.setReplySenderIdentifier(ByteString.copyFrom(message.getJsonMessage().getJsonReply().getSenderIdentifier()));
                        String replyContactName = AppSingleton.getContactCustomDisplayName(message.getJsonMessage().getJsonReply().getSenderIdentifier());
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
                        messageBuilder.setReplyBody(replyMessage.getStringContent(context));
                    }
                }
                return messageBuilder;
            }
        }
    }

}
