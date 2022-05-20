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

package io.olvid.messenger.databases.tasks;

import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;

public class DeleteMessagesTask implements Runnable {
    private static final int BATCH_SIZE = 100;

    private final boolean wholeDiscussion;
    private final Long discussionId;
    private final List<Long> selectedMessageIds;
    private final boolean deleteEverywhere;
    private final boolean processingRemoteDeleteRequest;
    private final @NonNull
    byte[] bytesOwnedIdentity;

    public DeleteMessagesTask(@NonNull byte[] bytesOwnedIdentity, long discussionId, boolean deleteEverywhere, boolean processingRemoteDeleteRequest) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.wholeDiscussion = true;
        this.discussionId = discussionId;
        this.selectedMessageIds = null;
        this.deleteEverywhere = deleteEverywhere;
        this.processingRemoteDeleteRequest = processingRemoteDeleteRequest;
    }

    public DeleteMessagesTask(@NonNull byte[] bytesOwnedIdentity, List<Long> selectedMessageIds, boolean deleteEverywhere) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.wholeDiscussion = false;
        this.discussionId = null;
        this.selectedMessageIds = new ArrayList<>(selectedMessageIds);
        this.deleteEverywhere = deleteEverywhere;
        this.processingRemoteDeleteRequest = false;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        List<Message> messages;
        Discussion discussion = null;
        Engine engine = AppSingleton.getEngine();

        if (wholeDiscussion) {
            if (discussionId == null) {
                return;
            }
            discussion = db.discussionDao().getById(discussionId);
            if (discussion == null) {
                return;
            }
            if (processingRemoteDeleteRequest) {
                // do not delete draft when remote deleting a discussion
                messages = db.messageDao().getAllNonDraftDiscussionMessagesSync(discussionId);
            } else {
                messages = db.messageDao().getAllDiscussionMessagesSync(discussionId);
            }
        } else {
            //noinspection ConstantConditions
            int size = selectedMessageIds.size();
            messages = new ArrayList<>(size);
            for (int i = 0; i < size; i += BATCH_SIZE) {
                messages.addAll(db.messageDao().getMany(selectedMessageIds.subList(i, Math.min(i + BATCH_SIZE, size))));
            }
        }

        HashSet<Fyle> fyles = new HashSet<>();
        HashSet<Long> discussionIds = new HashSet<>();

        for (Message message : messages) {
            discussionIds.add(message.discussionId);
        }

        // before deleting anything, check if deleteEverywhere works
        if (deleteEverywhere) {
            boolean success = false;
            if (wholeDiscussion) {
                success = Message.postDeleteDiscussionEverywhereMessage(discussionId);
            } else {
                if (discussionIds.size() != 1) {
                    Logger.e("Delete everywhere from multiple discussions not implemented! Aborting.");
                } else {
                    long singleDiscussionId = discussionIds.toArray(new Long[0])[0];
                    success = Message.postDeleteMessagesEverywhereMessage(singleDiscussionId, messages);
                }
            }
            if (!success) {
                App.toast(R.string.toast_message_unable_to_delete_everywhere, Toast.LENGTH_SHORT);
                return;
            }
        }


        for (Message message : messages) {
            if (message.hasAttachments()) {
                List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(message.id);
                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                    switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                        case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                            engine.deleteAttachment(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                            break;
                        case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                            engine.cancelAttachmentUpload(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                            break;
                    }
                    fyles.add(fyleAndStatus.fyle);

                    // delete the fyle message join for deleteEverywhere, as the cascade delete will not work
                    if (deleteEverywhere && !wholeDiscussion) {
                        db.fyleMessageJoinWithStatusDao().delete(fyleAndStatus.fyleMessageJoinWithStatus);
                    }
                }
            }
        }

        if (deleteEverywhere && !wholeDiscussion) {
            List<Message> infoMessages = new ArrayList<>();
            // simply wipe the messages
            long timestamp = System.currentTimeMillis();
            for (Message message : messages) {
                switch (message.messageType) {
                    case Message.TYPE_GROUP_MEMBER_JOINED:
                    case Message.TYPE_GROUP_MEMBER_LEFT:
                    case Message.TYPE_LEFT_GROUP:
                    case Message.TYPE_CONTACT_DELETED:
                    case Message.TYPE_DISCUSSION_SETTINGS_UPDATE:
                    case Message.TYPE_DISCUSSION_REMOTELY_DELETED:
                    case Message.TYPE_PHONE_CALL:
                    case Message.TYPE_NEW_PUBLISHED_DETAILS:
                    case Message.TYPE_CONTACT_INACTIVE_REASON:
                        infoMessages.add(message);
                        break;
                    default:
                        message.remoteDelete(db, bytesOwnedIdentity, timestamp);
                        if (message.wipedAttachmentCount == 0 && message.totalAttachmentCount != 0) {
                            message.wipedAttachmentCount = message.totalAttachmentCount;
                            message.totalAttachmentCount = 0;
                            message.imageCount = 0;
                            message.imageResolutions = null;
                            db.messageDao().updateAttachmentCount(message.id, 0, 0, message.wipedAttachmentCount, null);
                        }
                        break;
                }
            }
            int size = infoMessages.size();
            for (int i = 0; i < size; i += BATCH_SIZE) {
                db.messageDao().delete(infoMessages.subList(i, Math.min(i + BATCH_SIZE, size)).toArray(new Message[0]));
            }
        } else {
            // actually delete the messages
            int size = messages.size();
            for (int i = 0; i < size; i += BATCH_SIZE) {
                db.messageDao().delete(messages.subList(i, Math.min(i + BATCH_SIZE, size)).toArray(new Message[0]));
            }
        }

        // Check whether this discussion is locked and empty. If so, delete it
        if (wholeDiscussion) {
            if (discussion.isLocked()) {
                db.discussionDao().delete(discussion);
            } else if (db.messageDao().getDiscussionDraftMessageSync(discussionId) == null) {
                // only remove discussion from list if there is no draft
                discussion.lastMessageTimestamp = 0;
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            }
        } else {
            for (Long discussionId : discussionIds) {
                Discussion aDiscussion = db.discussionDao().getById(discussionId);
                if (aDiscussion != null && aDiscussion.isLocked()) {
                    if (db.messageDao().countMessagesInDiscussion(discussionId) == 0) {
                        db.discussionDao().delete(aDiscussion);
                    }
                }
            }
        }

        // now handle possibly orphaned fyles
        for (Fyle fyle : fyles) {
            long count = db.fyleMessageJoinWithStatusDao().countMessageForFyle(fyle.id);
            if (count == 0) {
                if (fyle.sha256 != null) {
                    Fyle.acquireLock(fyle.sha256);
                    fyle.delete();
                    Fyle.releaseLock(fyle.sha256);
                } else {
                    fyle.delete();
                }
            }
        }
    }
}
