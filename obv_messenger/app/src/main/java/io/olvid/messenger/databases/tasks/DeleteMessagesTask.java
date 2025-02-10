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

package io.olvid.messenger.databases.tasks;

import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.UnreadCountsSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.services.UnifiedForegroundService;

public class DeleteMessagesTask implements Runnable {
    private static final int BATCH_SIZE = 100;

    private final boolean wholeDiscussion;
    private final Long discussionId;
    private final List<Long> selectedMessageIds;
    private final SecureDeleteEverywhereDialogBuilder.DeletionChoice deletionChoice;
    private final boolean processingRemoteDeleteRequest;

    // Used to delete all messages in a discussion
    public DeleteMessagesTask(long discussionId, SecureDeleteEverywhereDialogBuilder.DeletionChoice deletionChoice, boolean processingRemoteDeleteRequest) {
        this.wholeDiscussion = true;
        this.discussionId = discussionId;
        this.selectedMessageIds = null;
        this.deletionChoice = deletionChoice;
        this.processingRemoteDeleteRequest = processingRemoteDeleteRequest;
    }

    // Used to delete a specific set of messages. If deletionChoice is not LOCAL, all messages have to be in the same discussion
    // bytesOwnedIdentity should never be null if deletionChoice != LOCAL
    public DeleteMessagesTask(List<Long> selectedMessageIds, SecureDeleteEverywhereDialogBuilder.DeletionChoice deletionChoice) {
        this.wholeDiscussion = false;
        this.discussionId = null;
        this.selectedMessageIds = new ArrayList<>(selectedMessageIds);
        this.deletionChoice = deletionChoice;
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
            // if deleting a whole discussion stop sharing location if currently sharing
            if (UnifiedForegroundService.LocationSharingSubService.isDiscussionSharingLocation(discussionId)) {
                UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(discussionId, true);
            }
        } else {
            //noinspection ConstantConditions
            int size = selectedMessageIds.size();
            messages = new ArrayList<>(size);
            for (int i = 0; i < size; i += BATCH_SIZE) {
                messages.addAll(db.messageDao().getMany(selectedMessageIds.subList(i, Math.min(i + BATCH_SIZE, size))));
            }
            for (Message message : messages) {
                // we found an outbound sharing location message, stop sharing in this discussion
                if (message.isCurrentSharingOutboundLocationMessage()) {
                    UnifiedForegroundService.LocationSharingSubService.stopSharingInDiscussion(message.discussionId, true);
                }
            }
        }

        HashMap<Long, Fyle> fyles = new HashMap<>();
        HashSet<Long> discussionIds = new HashSet<>();

        for (Message message : messages) {
            discussionIds.add(message.discussionId);
        }

        // before deleting anything, check if deleteEverywhere works
        if (deletionChoice != SecureDeleteEverywhereDialogBuilder.DeletionChoice.LOCAL) {
            boolean success = false;
            if (wholeDiscussion) {
                success = Message.postDeleteDiscussionEverywhereMessage(discussionId, deletionChoice == SecureDeleteEverywhereDialogBuilder.DeletionChoice.OWNED_DEVICES);
            } else {
                if (discussionIds.size() != 1) {
                    Logger.e("Delete everywhere from multiple discussions not implemented! Aborting.");
                } else {
                    long singleDiscussionId = discussionIds.toArray(new Long[0])[0];
                    success = Message.postDeleteMessagesEverywhereMessage(singleDiscussionId, messages, deletionChoice == SecureDeleteEverywhereDialogBuilder.DeletionChoice.OWNED_DEVICES);
                }
            }
            if (!success) {
                App.toast(R.string.toast_message_unable_to_delete_everywhere, Toast.LENGTH_SHORT);
                return;
            }
        }


        for (Message message : messages) {
            if (message.hasAttachments() || message.linkPreviewFyleId != null) {
                List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(message.id);
                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                    if (fyleAndStatus.fyleMessageJoinWithStatus.engineNumber != null) {
                        switch (fyleAndStatus.fyleMessageJoinWithStatus.status) {
                            case FyleMessageJoinWithStatus.STATUS_DOWNLOADING:
                            case FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE:
                                engine.markAttachmentForDeletion(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                                break;
                            case FyleMessageJoinWithStatus.STATUS_UPLOADING:
                                engine.cancelAttachmentUpload(fyleAndStatus.fyleMessageJoinWithStatus.bytesOwnedIdentity, fyleAndStatus.fyleMessageJoinWithStatus.engineMessageIdentifier, fyleAndStatus.fyleMessageJoinWithStatus.engineNumber);
                                break;
                        }
                    }
                    fyles.put(fyleAndStatus.fyle.id, fyleAndStatus.fyle);
                }
            }
        }

        {
            // actually delete the messages
            int size = messages.size();
            for (int i = 0; i < size; i += BATCH_SIZE) {
                db.messageDao().delete(messages.subList(i, Math.min(i + BATCH_SIZE, size)).toArray(new Message[0]));
            }
            UnreadCountsSingleton.INSTANCE.messageBatchDeleted(messages);
        }

        // Check whether this discussion is locked and empty. If so, delete it
        if (wholeDiscussion) {
            if (discussion.isLocked()) {
                // downgrade to pre discussion if any invitation is pending
                if (db.invitationDao().discussionHasInvitations(discussionId)) {
                    db.discussionDao().updateStatus(discussionId, Discussion.STATUS_PRE_DISCUSSION);
                    // also delete any DiscussionCustomization
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                    if (discussionCustomization != null) {
                        db.discussionCustomizationDao().delete(discussionCustomization);
                    }
                } else {
                    db.discussionDao().delete(discussion);
                    ShortcutActivity.disableShortcut(discussionId);
                }
            } else if (db.messageDao().getDiscussionDraftMessageSync(discussionId) == null && !db.invitationDao().discussionHasInvitations(discussionId)) {
                // only remove discussion from list if there is no draft and no invitation
                discussion.lastMessageTimestamp = 0;
                db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
            }
        } else {
            for (Long discussionId : discussionIds) {
                Discussion aDiscussion = db.discussionDao().getById(discussionId);
                if (aDiscussion != null && aDiscussion.isLocked()) {
                    if (db.messageDao().countMessagesInDiscussion(discussionId) == 0) {
                        // do not actually delete empty locked discussion if any invitation is pending
                        if (!db.invitationDao().discussionHasInvitations(discussionId)) {
                            db.discussionDao().delete(aDiscussion);
                            ShortcutActivity.disableShortcut(discussionId);
                        }
                    }
                }
            }
        }

        // now handle possibly orphaned fyles
        for (Fyle fyle : fyles.values()) {
            long count = db.fyleMessageJoinWithStatusDao().countMessageForFyle(fyle.id);
            if (count == 0) {
                if (fyle.sha256 != null) {
                    try {
                        Fyle.acquireLock(fyle.sha256);
                        fyle.delete();
                    } finally {
                        Fyle.releaseLock(fyle.sha256);
                    }
                } else {
                    fyle.delete();
                }
            }
        }
    }
}
