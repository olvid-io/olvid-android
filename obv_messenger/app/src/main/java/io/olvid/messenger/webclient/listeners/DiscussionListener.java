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

import androidx.lifecycle.LiveData;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.Colissimo;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.ColissimoType;
import io.olvid.messenger.webclient.protobuf.RequestDiscussionsOuterClass.RequestDiscussionsResponse;
import io.olvid.messenger.webclient.protobuf.datatypes.DiscussionOuterClass.Discussion;
import io.olvid.messenger.webclient.protobuf.datatypes.MessageOuterClass.Message;
import io.olvid.messenger.webclient.protobuf.notifications.NotifDeleteDiscussionOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifNewDiscussionOuterClass.NotifNewDiscussion;

public class DiscussionListener {
    private LiveData<List<DiscussionDao.DiscussionAndLastMessage>> liveData;
    private DiscussionObserver observer;
    private final WebClientManager manager;

    public DiscussionListener(WebClientManager manager) {
        this.manager = manager;
        this.liveData = null;
        this.observer = null;
        Logger.d("DiscussionListener: Started DAO observer (DAOObserver)");
    }

    // NB: this function shall be launched on main thread
    public void addListener() {
        if (this.observer != null) {
            Logger.d("Conversation observer already launched, ignoring");
            return ;
        }
        this.liveData = AppDatabase.getInstance().discussionDao().getAllDiscussionsAndLastMessages(this.manager.getBytesCurrentOwnedIdentity());
        if (this.liveData != null) {
            this.observer = new DiscussionObserver(this.manager, this.liveData);
            this.liveData.observeForever(this.observer);
        }
        else {
            Logger.e("Unable to launch conversation listener");
        }
    }

    // NB: this function shall be launched on main thread
    public void removeListener() {
        if (this.observer != null && this.liveData != null) {
            this.liveData.removeObserver(this.observer);
        }
        else {
            Logger.d("No observer to remove in DiscussionListener");
        }
        this.liveData = null;
        this.observer = null;
    }

    // NB: this function shall be launched on main thread
    public void stop() {
        removeListener();
        Logger.d("DiscussionListener: Stopped discussion listener");
    }

    static class DiscussionObserver extends AbstractObserver<Long, DiscussionDao.DiscussionAndLastMessage> {
        private final WebClientManager manager;

        DiscussionObserver(WebClientManager manager, LiveData<List<DiscussionDao.DiscussionAndLastMessage>> liveData) {
            super(liveData);
            this.manager = manager;
        }

        @Override
        boolean batchedElementHandler(List<DiscussionDao.DiscussionAndLastMessage> elements) {
            int index;
            Colissimo colissimo;
            RequestDiscussionsResponse.Builder requestDiscussionsResponseBuilder;

            // add as much discussion as possible in requestDiscussionResponse, if size is exceeded
            // send other discussion using notif_new_discussion messages
            index = 0;
            requestDiscussionsResponseBuilder = RequestDiscussionsResponse.newBuilder();
            while (index < elements.size()) {
                requestDiscussionsResponseBuilder.addDiscussions(fillDiscussionFromDiscussionAndLastMessage(elements.get(index), this.manager.getService().getWebClientContext()));
                index++;
                if (requestDiscussionsResponseBuilder.build().getSerializedSize() > Constants.MAX_FRAME_SIZE) {
                    break ;
                }
            }

            // send response colissimo first
            colissimo = Colissimo.newBuilder()
                    .setType(ColissimoType.REQUEST_DISCUSSIONS_RESPONSE)
                    .setRequestDiscussionsResponse(requestDiscussionsResponseBuilder)
                    .build();
            this.manager.sendColissimo(colissimo);

            // if all discussions were sent just quit
            if (index == elements.size()) {
                return true;
            }

            // send other discussions in notif messages
            NotifNewDiscussion.Builder notifBuilder = NotifNewDiscussion.newBuilder();
            for (DiscussionDao.DiscussionAndLastMessage element:elements.subList(index, elements.size())) {
                notifBuilder.addDiscussions(fillDiscussionFromDiscussionAndLastMessage(element, this.manager.getService().getWebClientContext()));
                if (notifBuilder.build().getSerializedSize() > Constants.MAX_FRAME_SIZE) {
                    colissimo = Colissimo.newBuilder()
                            .setType(ColissimoType.NOTIF_NEW_DISCUSSION)
                            .setNotifNewDiscussion(notifBuilder)
                            .build();
                    this.manager.sendColissimo(colissimo);
                    notifBuilder = NotifNewDiscussion.newBuilder();
                }
            }

            // send last notif message if necessary
            if (notifBuilder.getDiscussionsCount() > 0) {
                colissimo = Colissimo.newBuilder()
                        .setType(ColissimoType.NOTIF_NEW_DISCUSSION)
                        .setNotifNewDiscussion(notifBuilder)
                        .build();
                this.manager.sendColissimo(colissimo);
            }
            return true;
        }

        @Override
        boolean equals(DiscussionDao.DiscussionAndLastMessage element1, DiscussionDao.DiscussionAndLastMessage element2) {
            // security check for any null element
            if (element1 == null && element2 == null) {
                return true;
            } else if (element1 == null || element2 == null) {
                return false;
            } else if (element1.unreadCount != element2.unreadCount) {
                return false;
            }

            // check discussion content
            if (element1.discussion == null && element2.discussion == null) {
                // nothing
            } else if (element1.discussion == null || element2.discussion == null) {
                return false;
            } else if (element1.discussion.lastMessageTimestamp != element2.discussion.lastMessageTimestamp
                    || !element1.discussion.title.equals(element2.discussion.title)
                    || !Objects.equals(element1.discussion.photoUrl, element2.discussion.photoUrl)
                    || element1.discussion.status != element2.discussion.status) {
                return false;
            }

            // check message content
            if (element1.message == null && element2.message == null) {
                // nothing
            } else if (element1.message == null || element2.message == null) {
                return false;
            } else if (element1.unreadCount != element2.unreadCount
                    || element1.message.imageCount != element2.message.imageCount
                    || element1.message.edited != element2.message.edited
                    || element1.message.wipeStatus != element2.message.wipeStatus
                    || !Objects.equals(element1.message.contentBody, element2.message.contentBody)) {

                return false;
            }
            return true;
        }

        @Override
        Long getElementKey(DiscussionDao.DiscussionAndLastMessage element) {
            return (element.discussion.id);
        }

        @Override
        void newElementHandler(DiscussionDao.DiscussionAndLastMessage element) {
            Discussion.Builder discussionBuilder;
            NotifNewDiscussion.Builder notifBuilder;
            Colissimo.Builder colissimoBuilder;

            discussionBuilder = fillDiscussionFromDiscussionAndLastMessage(element, this.manager.getService().getWebClientContext());
            colissimoBuilder = Colissimo.newBuilder();
            colissimoBuilder.setType(ColissimoType.NOTIF_NEW_DISCUSSION);
            notifBuilder = NotifNewDiscussion.newBuilder().addDiscussions(discussionBuilder);
            colissimoBuilder.setNotifNewDiscussion(notifBuilder);
            this.manager.sendColissimo(colissimoBuilder.build());
        }

        @Override
        void deletedElementHandler(DiscussionDao.DiscussionAndLastMessage element) {
            Discussion.Builder discussionBuilder = fillDiscussionFromDiscussionAndLastMessage(element, this.manager.getService().getWebClientContext());
            NotifDeleteDiscussionOuterClass.NotifDeleteDiscussion.Builder notifBuilder = NotifDeleteDiscussionOuterClass.NotifDeleteDiscussion.newBuilder();
            Colissimo.Builder colissimoBuilder = Colissimo.newBuilder();

            notifBuilder.setDiscussion(discussionBuilder);
            colissimoBuilder.setType(ColissimoType.NOTIF_DELETE_DISCUSSION);
            colissimoBuilder.setNotifDeleteDiscussion(notifBuilder);
            this.manager.sendColissimo(colissimoBuilder.build());
        }

        @Override
        void modifiedElementHandler(DiscussionDao.DiscussionAndLastMessage element) {
            newElementHandler(element);
        }

        private Discussion.Builder fillDiscussionFromDiscussionAndLastMessage(DiscussionDao.DiscussionAndLastMessage discussionAndLastMessage, Context context) {
            Discussion.Builder discussionBuilder;
            Message.Builder lastMessageBuilder;

            discussionBuilder = Discussion.newBuilder();
            lastMessageBuilder = Message.newBuilder();
            discussionBuilder.setId(discussionAndLastMessage.discussion.id);
            discussionBuilder.setTitle(discussionAndLastMessage.discussion.title);
            if(discussionAndLastMessage.discussion.photoUrl != null){
                discussionBuilder.setPhotoURL(discussionAndLastMessage.discussion.photoUrl);
            }
            switch (discussionAndLastMessage.discussion.discussionType) {
                case io.olvid.messenger.databases.entity.Discussion.TYPE_CONTACT:
                    discussionBuilder.setContactIdentity(ByteString.copyFrom(discussionAndLastMessage.discussion.bytesDiscussionIdentifier));
                    break;
                case io.olvid.messenger.databases.entity.Discussion.TYPE_GROUP:
                case io.olvid.messenger.databases.entity.Discussion.TYPE_GROUP_V2:
                    discussionBuilder.setGroupOwnerAndUid(ByteString.copyFrom(discussionAndLastMessage.discussion.bytesDiscussionIdentifier));
                    break;
            }
            discussionBuilder.setUnreadMessagesCount(discussionAndLastMessage.unreadCount);
            discussionBuilder.setDiscussionTimestamp(discussionAndLastMessage.discussion.lastMessageTimestamp);
            //set last message fields
            if (discussionAndLastMessage.message != null) {
                String contactName = AppSingleton.getContactCustomDisplayName(discussionAndLastMessage.message.senderIdentifier);
                if (contactName == null) {
                    lastMessageBuilder.setSenderName(context.getString(R.string.text_deleted_contact));
                    lastMessageBuilder.setSenderIsSelf(false);
                } else {
                    if(Arrays.equals(manager.getBytesCurrentOwnedIdentity(), discussionAndLastMessage.message.senderIdentifier)){
                        lastMessageBuilder.setSenderName(context.getString(R.string.text_you));
                        lastMessageBuilder.setSenderIsSelf(true);
                    } else{
                        lastMessageBuilder.setSenderName(contactName);
                        lastMessageBuilder.setSenderIsSelf(false);
                    }
                }
                lastMessageBuilder.setTypeValue(discussionAndLastMessage.message.messageType + 1);
                lastMessageBuilder.setStatusValue(discussionAndLastMessage.message.status);
                lastMessageBuilder.setTimestamp(discussionAndLastMessage.message.timestamp);
                if (discussionAndLastMessage.message.messageType == io.olvid.messenger.databases.entity.Message.TYPE_INBOUND_MESSAGE
                        || discussionAndLastMessage.message.messageType == io.olvid.messenger.databases.entity.Message.TYPE_INBOUND_EPHEMERAL_MESSAGE
                        || discussionAndLastMessage.message.messageType == io.olvid.messenger.databases.entity.Message.TYPE_OUTBOUND_MESSAGE) {
                    // do not send message body if message is read once or have a limited visibility duration (inbound and outbound)
                    try {
                        if (discussionAndLastMessage.message.jsonExpiration != null) {
                            io.olvid.messenger.databases.entity.Message.JsonExpiration jsonExpiration;
                            jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(discussionAndLastMessage.message.jsonExpiration, io.olvid.messenger.databases.entity.Message.JsonExpiration.class);
                            if ((jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce()) || jsonExpiration.getVisibilityDuration() != null) {
                                // hardcode message body and return builder without adding other info
                                lastMessageBuilder.setContentBody(context.getString(R.string.text_message_content_hidden));
                                discussionBuilder.setLastMessage(lastMessageBuilder);
                                return discussionBuilder;
                            }
                        }
                    } catch (Exception e) {
                        Logger.e("Unable to parse jsonExpiration in DiscussionListener", e);
                    }
                    lastMessageBuilder.setContentBody(discussionAndLastMessage.message.getStringContent(context));
                    lastMessageBuilder.setTotalAttachmentCount(discussionAndLastMessage.message.totalAttachmentCount);
                }
                // add last message in discussion
                discussionBuilder.setLastMessage(lastMessageBuilder);
            }
            return (discussionBuilder);
        }
    }
}
