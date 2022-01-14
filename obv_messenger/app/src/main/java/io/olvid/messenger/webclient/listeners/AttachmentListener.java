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

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.ColissimoType;
import io.olvid.messenger.webclient.protobuf.datatypes.AttachmentOuterClass;
import io.olvid.messenger.webclient.protobuf.datatypes.AttachmentOuterClass.Attachment;
import io.olvid.messenger.webclient.protobuf.notifications.NotifDeleteAttachmentOuterClass.NotifDeleteAttachment;
import io.olvid.messenger.webclient.protobuf.notifications.NotifNewAttachmentOuterClass.NotifNewAttachment;
import io.olvid.messenger.webclient.protobuf.notifications.NotifUpdateAttachmentOuterClass.NotifUpdateAttachment;

public class AttachmentListener {

    private final WebClientManager manager;
    final HashMap<Long, LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>>> messageAttachmentsLiveData;
    final HashMap<Long, AttachmentListener.AttachmentObserver> messageAttachmentsObservers;

    public AttachmentListener(WebClientManager manager) {
        this.manager = manager;
        this.messageAttachmentsLiveData = new HashMap<>();
        this.messageAttachmentsObservers = new HashMap<>();
    }

    // NB: this function shall be launched on main thread
    public void stop() {
        for (Long messageId : this.messageAttachmentsObservers.keySet()) {
            if (this.messageAttachmentsObservers.get(messageId) != null && this.messageAttachmentsLiveData.get(messageId) != null) {
                this.messageAttachmentsLiveData.get(messageId).removeObserver(this.messageAttachmentsObservers.get(messageId));
                this.messageAttachmentsLiveData.remove(messageId);
            }
        }
        Logger.d("AttachmentListener: Stopped attachment listeners for all messages");
    }

    public void addListener(final long messageId) {
        if (!messageAttachmentsLiveData.containsKey(messageId)) {
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> liveData;
            liveData = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFylesAndStatusForMessage(messageId);
            messageAttachmentsLiveData.put(messageId, liveData);
            AttachmentObserver attachmentObserver = new AttachmentObserver(this.manager, liveData);
            this.messageAttachmentsObservers.put(messageId, attachmentObserver);
            // keep looper on main thread to add listeners in MessageListener
            new Handler(Looper.getMainLooper()).post(() -> liveData.observeForever(attachmentObserver));
            Logger.d("AttachmentListener: Launched attachment listener for message : " + messageId);
        } else {
            Logger.d("AttachmentListener: Attachment listener already launched for: " + messageId);
        }
    }

    public void removeListener(final long messageId) {
        if (messageAttachmentsLiveData.containsKey(messageId) && messageAttachmentsLiveData.get(messageId) != null) {
            if (this.messageAttachmentsObservers.get(messageId) != null) {
                this.messageAttachmentsLiveData.get(messageId).removeObserver(this.messageAttachmentsObservers.get(messageId));
            }
            messageAttachmentsLiveData.remove(messageId);
            Logger.w(("AttachmentListener: Removed attachment listener for message : " + messageId));
        } else {
            Logger.w("AttachmentListener: No listener to remove for message : " + messageId);
        }
    }

    static class AttachmentObserver extends AbstractObserver<Long, FyleMessageJoinWithStatusDao.FyleAndStatus> {
        private final WebClientManager manager;

        AttachmentObserver(WebClientManager manager, LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> liveData) {
            super(liveData);
            this.manager = manager;
            this.cacheMemory = new HashMap<>();
        }

        @Override
        boolean batchedElementHandler(List<FyleMessageJoinWithStatusDao.FyleAndStatus> elements) {
            return false;
        }

        @Override
        boolean equals(FyleMessageJoinWithStatusDao.FyleAndStatus element1, FyleMessageJoinWithStatusDao.FyleAndStatus element2) {
            //also watch change in the file path
            //if only one of the paths is null, then not equal
            if ((element1.fyle.filePath == null && element2.fyle.filePath != null) || (element1.fyle.filePath != null && element2.fyle.filePath == null)) {
                return false;
            } else if (element1.fyle.filePath == null && element2.fyle.filePath == null) { //both null, regular equals
                return element1.equals(element2);
            } else { //otherwise, compare them and decide
                return element1.equals(element2) && element1.fyle.filePath.equals(element2.fyle.filePath);
            }
        }

        @Override
        Long getElementKey(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            return element.fyle.id;
        }

        @Override
        void newElementHandler(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            FillProtobufAttachmentFromOlvidMessageAndSend fillProtobuf = new FillProtobufAttachmentFromOlvidMessageAndSend(element, this.manager, ColissimoType.NOTIF_NEW_ATTACHMENT);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        @Override
        void deletedElementHandler(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            FillProtobufAttachmentFromOlvidMessageAndSend fillProtobuf = new FillProtobufAttachmentFromOlvidMessageAndSend(element, this.manager, ColissimoType.NOTIF_DELETE_ATTACHMENT);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        @Override
        void modifiedElementHandler(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            FillProtobufAttachmentFromOlvidMessageAndSend fillProtobuf = new FillProtobufAttachmentFromOlvidMessageAndSend(element, this.manager, ColissimoType.NOTIF_UPDATE_ATTACHMENT);
            Thread fillAndSendProtobuf = new Thread(fillProtobuf);
            fillAndSendProtobuf.start();
        }

        private static class FillProtobufAttachmentFromOlvidMessageAndSend implements Runnable {
            final FyleMessageJoinWithStatusDao.FyleAndStatus element;
            final WebClientManager webClientManager;
            final ColissimoType colissimoType;
            FillProtobufAttachmentFromOlvidMessageAndSend(FyleMessageJoinWithStatusDao.FyleAndStatus element, WebClientManager webClientManager, ColissimoType colissimoType) {
                this.element=element;
                this.webClientManager = webClientManager;
                this.colissimoType = colissimoType;
            }

            @Override
            public void run() {
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                if (colissimoType == ColissimoType.NOTIF_NEW_ATTACHMENT) {
                    colissimoBuilder.setType(ColissimoType.NOTIF_NEW_ATTACHMENT);
                    NotifNewAttachment.Builder notifBuilder = NotifNewAttachment.newBuilder();
                    notifBuilder.setAttachment(fillProtobufAttachmentFromOlvidAttachment(element));
                    colissimoBuilder.setNotifNewAttachment(notifBuilder);
                } else if (colissimoType == ColissimoType.NOTIF_UPDATE_ATTACHMENT) {
                    colissimoBuilder.setType(ColissimoType.NOTIF_UPDATE_ATTACHMENT);
                    NotifUpdateAttachment.Builder notifBuilder = NotifUpdateAttachment.newBuilder();
                    notifBuilder.setAttachment(fillProtobufAttachmentFromOlvidAttachment(element));
                    colissimoBuilder.setNotifUpdateAttachment(notifBuilder);
                } else if (colissimoType == ColissimoType.NOTIF_DELETE_ATTACHMENT) {
                    colissimoBuilder.setType(ColissimoType.NOTIF_DELETE_ATTACHMENT);
                    NotifDeleteAttachment.Builder notifBuilder = NotifDeleteAttachment.newBuilder();
                    Attachment.Builder attachmentBuilder;
                    attachmentBuilder = fillProtobufAttachmentFromOlvidAttachment(element);
                    attachmentBuilder.setStatus(AttachmentOuterClass.AttachmentStatus.DOWNLOAD_DELETED);
                    notifBuilder.setAttachment(attachmentBuilder);
                    colissimoBuilder.setNotifDeleteAttachment(notifBuilder);
                }
                this.webClientManager.sendColissimo(colissimoBuilder.build());
            }

            Attachment.Builder fillProtobufAttachmentFromOlvidAttachment(FyleMessageJoinWithStatusDao.FyleAndStatus fyle) {
                Attachment.Builder attachmentBuilder;
                attachmentBuilder = Attachment.newBuilder();
                attachmentBuilder.setFyleId(fyle.fyle.id);
                attachmentBuilder.setMessageId(fyle.fyleMessageJoinWithStatus.messageId);
                attachmentBuilder.setName(fyle.fyleMessageJoinWithStatus.fileName);
                attachmentBuilder.setMime(fyle.fyleMessageJoinWithStatus.getNonNullMimeType());
                if (fyle.fyle.filePath != null) {
                    attachmentBuilder.setPath(fyle.fyle.filePath);
                } else {
                    attachmentBuilder.setPath("");
                }
                attachmentBuilder.setStatus(AttachmentOuterClass.AttachmentStatus.READY_FOR_DOWNLOAD);
                // size cannot be trusted for old attachments (prior to build 108) --> use the real file size on disk, use the db size as fallback
                try {
                    File file = new File(App.absolutePathFromRelative(fyle.fyle.filePath));
                    attachmentBuilder.setSize(file.length());
                } catch (Exception e) {
                    attachmentBuilder.setSize(fyle.fyleMessageJoinWithStatus.size);
                }
                // get discussionId (for displaying only 1 discussion at a time in gallery) and timestamp (for ordering in gallery) of associated message
                Message associatedMessage = AppDatabase.getInstance().messageDao().get(fyle.fyleMessageJoinWithStatus.messageId);
                if (associatedMessage != null) {
                    attachmentBuilder.setMessageTimestamp(associatedMessage.timestamp);
                    attachmentBuilder.setDiscussionId(associatedMessage.discussionId);
                }
                return attachmentBuilder;
            }
        }

    }
}
