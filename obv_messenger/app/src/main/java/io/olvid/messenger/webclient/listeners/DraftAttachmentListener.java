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

package io.olvid.messenger.webclient.listeners;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.google.protobuf.ByteString;

import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.datatypes.DraftAttachmentOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifDeleteDraftAttachmentOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifNewDraftAttachmentOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifUpdateDraftAttachmentOuterClass;

public class DraftAttachmentListener {
    private final WebClientManager manager;
    final HashMap<Long, LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>>> discussionDraftAttachementsLiveData;
    final HashMap<Long, DraftAttachmentListener.DraftAttachmentObserver> draftAttachmentsObservers;

    public DraftAttachmentListener(WebClientManager manager) {
        this.manager = manager;
        this.discussionDraftAttachementsLiveData = new HashMap<>();
        this.draftAttachmentsObservers = new HashMap<>();
    }

    // NB: this function shall be launched on main thread
    public void stop() {
        for (Long discussionId:this.draftAttachmentsObservers.keySet()) {
            DraftAttachmentListener.DraftAttachmentObserver draftAttachmentObserver = this.draftAttachmentsObservers.get(discussionId);
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> liveData = this.discussionDraftAttachementsLiveData.get(discussionId);
            if (draftAttachmentObserver != null && liveData != null) {
                    liveData.removeObserver(draftAttachmentObserver);
                    this.discussionDraftAttachementsLiveData.remove(discussionId);
            }
        }
        Logger.d("DraftAttachmentListener: Stopped draft attachments listeners");
    }

    public void addListener(final long discussionId) {
        if (!discussionDraftAttachementsLiveData.containsKey(discussionId)) {
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> liveData ;
            liveData = AppDatabase.getInstance().fyleDao().getDiscussionDraftFyles(discussionId);
            discussionDraftAttachementsLiveData.put(discussionId, liveData);
            DraftAttachmentListener.DraftAttachmentObserver draftAttachmentObserver = new DraftAttachmentObserver(this.manager, liveData, discussionId);
            this.draftAttachmentsObservers.put(discussionId, draftAttachmentObserver);
            new Handler(Looper.getMainLooper()).post(() -> liveData.observeForever(draftAttachmentObserver));
            Logger.d("DraftAttachmentListener: Launched draft attachment listener for: " + discussionId);
        }
        else {
            Logger.d("DraftAttachmentListener: Draft listener already launched for: " + discussionId);
        }
    }

    static class DraftAttachmentObserver extends AbstractObserver<Long,FyleMessageJoinWithStatusDao.FyleAndStatus> {
        private final WebClientManager manager;
        private final long discussionId;

        DraftAttachmentObserver(WebClientManager manager, LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> liveData, long discussionId) {
            super(liveData);
            this.manager = manager;
            this.cacheMemory = new HashMap<>();
            this.discussionId = discussionId;
        }

        @Override
        boolean batchedElementHandler(List<FyleMessageJoinWithStatusDao.FyleAndStatus> elements) {
            return false;
        }

        @Override
        boolean equals(FyleMessageJoinWithStatusDao.FyleAndStatus element1, FyleMessageJoinWithStatusDao.FyleAndStatus element2) {
            if ((element1.fyle.filePath == null && element2.fyle.filePath != null) || (element1.fyle.filePath != null && element2.fyle.filePath == null)) {
                return false;
            } else if (element1.fyle.filePath == null) { //both null, regular equals
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
            DraftAttachmentOuterClass.DraftAttachment.Builder draftAttachmentBuilder;
            NotifNewDraftAttachmentOuterClass.NotifNewDraftAttachment.Builder notifBuilder;
            ColissimoOuterClass.Colissimo.Builder colissimoBuilder;

            notifBuilder = NotifNewDraftAttachmentOuterClass.NotifNewDraftAttachment.newBuilder();
            colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
            draftAttachmentBuilder = fillProtobufDraftAttachmentFromOlvidDraftAttachment(element, discussionId);
            draftAttachmentBuilder.setStatus(DraftAttachmentOuterClass.DraftAttachmentStatus.READY);
            notifBuilder.setDraftAttachment(draftAttachmentBuilder);
            colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_NEW_DRAFT_ATTACHMENT);
            colissimoBuilder.setNotifNewDraftAttachment(notifBuilder);
            this.manager.sendColissimo(colissimoBuilder.build());
        }

        @Override
        void deletedElementHandler(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            DraftAttachmentOuterClass.DraftAttachment.Builder draftAttachmentBuilder;
            NotifDeleteDraftAttachmentOuterClass.NotifDeleteDraftAttachment.Builder notifBuilder;
            ColissimoOuterClass.Colissimo.Builder colissimoBuilder;

            notifBuilder = NotifDeleteDraftAttachmentOuterClass.NotifDeleteDraftAttachment.newBuilder();
            colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
            draftAttachmentBuilder = fillProtobufDraftAttachmentFromOlvidDraftAttachment(element, discussionId);
            draftAttachmentBuilder.setStatus(DraftAttachmentOuterClass.DraftAttachmentStatus.DELETED);
            notifBuilder.setDraftAttachment(draftAttachmentBuilder);
            colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_DELETE_DRAFT_ATTACHMENT);
            colissimoBuilder.setNotifDeleteDraftAttachment(notifBuilder);
            this.manager.sendColissimo(colissimoBuilder.build());
        }

        @Override
        void modifiedElementHandler(FyleMessageJoinWithStatusDao.FyleAndStatus element) {
            DraftAttachmentOuterClass.DraftAttachment.Builder draftAttachmentBuilder;
            NotifUpdateDraftAttachmentOuterClass.NotifUpdateDraftAttachment.Builder notifBuilder;
            ColissimoOuterClass.Colissimo.Builder colissimoBuilder;

            notifBuilder = NotifUpdateDraftAttachmentOuterClass.NotifUpdateDraftAttachment.newBuilder();
            colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
            draftAttachmentBuilder = fillProtobufDraftAttachmentFromOlvidDraftAttachment(element, discussionId);
            draftAttachmentBuilder.setStatus(DraftAttachmentOuterClass.DraftAttachmentStatus.READY);
            notifBuilder.setDraftAttachment(draftAttachmentBuilder);
            colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_UPDATE_DRAFT_ATTACHMENT);
            colissimoBuilder.setNotifUpdateDraftAttachment(notifBuilder);
            this.manager.sendColissimo(colissimoBuilder.build());
        }

        DraftAttachmentOuterClass.DraftAttachment.Builder fillProtobufDraftAttachmentFromOlvidDraftAttachment(FyleMessageJoinWithStatusDao.FyleAndStatus fyle, long discussionId) {
            DraftAttachmentOuterClass.DraftAttachment.Builder draftAttachmentBuilder;
            draftAttachmentBuilder = DraftAttachmentOuterClass.DraftAttachment.newBuilder();
            draftAttachmentBuilder.setFyleId(fyle.fyle.id);
            draftAttachmentBuilder.setMessageId(fyle.fyleMessageJoinWithStatus.messageId);
            draftAttachmentBuilder.setName(fyle.fyleMessageJoinWithStatus.fileName);
            draftAttachmentBuilder.setMime(fyle.fyleMessageJoinWithStatus.getNonNullMimeType());
            if (fyle.fyle.sha256 != null) {
                draftAttachmentBuilder.setSha256(ByteString.copyFrom(fyle.fyle.sha256));
            }
            if(fyle.fyle.filePath != null) {
                draftAttachmentBuilder.setPath(fyle.fyle.filePath);
            }
            draftAttachmentBuilder.setDiscussionId(discussionId);
            return draftAttachmentBuilder;
        }
    }
}
