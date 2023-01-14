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

package io.olvid.messenger.databases.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;

public class ForwardMessagesTask implements Runnable {
   private final List<Long> messageIdsToForward;
   private final List<Long> discussionsIds;

   private final HashMap<Long, Discussion> discussionCache;
   private final HashMap<Long, Message.JsonExpiration> discussionExpirationCache;

   public ForwardMessagesTask(List<Long> messageIdsToForward, List<Long> discussionsIds) {
      this.messageIdsToForward = messageIdsToForward;
      this.discussionsIds = discussionsIds;
      this.discussionCache = new HashMap<>();
      this.discussionExpirationCache = new HashMap<>();
   }

   @Override
   public void run() {
      if (messageIdsToForward == null || messageIdsToForward.size() == 0
              || discussionsIds == null || discussionsIds.size() == 0) {
         return;
      }

      AppDatabase db = AppDatabase.getInstance();

      List<Message> messagesToForward = new ArrayList<>(messageIdsToForward.size());
      for (Long messageId : messageIdsToForward) {
         if (messageId == null) {
            continue;
         }

         Message message = db.messageDao().get(messageId);
         if (message == null) {
            Logger.w("ForwardMessagesTask: message not found");
            continue;
         }
         messagesToForward.add(message);
      }

      // sort messages so they are forwarded in their original order
      Collections.sort(messagesToForward, (Message message1, Message message2) -> {
         // messages in the same discussion cannot have the same sortIndex
         return (message1.sortIndex < message2.sortIndex) ? -1 : 1;
      });

      for (Message message : messagesToForward) {
         if ((message.messageType != Message.TYPE_OUTBOUND_MESSAGE
                 && message.messageType != Message.TYPE_INBOUND_MESSAGE)
                 || message.wipeStatus != Message.WIPE_STATUS_NONE
                 || message.limitedVisibility) {
            // this kind of message should never be forwarded
            Logger.w("ForwardMessagesTask: trying to forward a message that cannot be forwarded");
            continue;
         }

         String body = message.contentBody;

         boolean allAttachmentsComplete = true;
         List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(message.id);
         for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
            if (!fyleAndStatus.fyle.isComplete()) {
               allAttachmentsComplete = false;
               break;
            }
         }

         if (!allAttachmentsComplete) {
            Logger.w("ForwardMessagesTask: skipping message with incomplete attachments");
            continue;
         }

         // our message is complete, we can safely iterate over the discussions in which it should be forwarded
         for (Long discussionId : discussionsIds) {
            if (discussionId == null) {
               continue;
            }

            final Discussion discussion;
            if (discussionCache.containsKey(discussionId)) {
               discussion = discussionCache.get(discussionId);
            } else {
               discussion = db.discussionDao().getById(discussionId);
               discussionCache.put(discussionId, discussion);
            }

            if (discussion == null) {
               Logger.w("ForwardMessagesTask: discussion not found");
               continue;
            }


            final Message.JsonExpiration jsonExpiration;
            if (discussionExpirationCache.containsKey(discussionId)) {
               jsonExpiration = discussionExpirationCache.get(discussionId);
            } else {
               DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
               jsonExpiration = (discussionCustomization == null) ? null : discussionCustomization.getExpirationJson();
               discussionExpirationCache.put(discussionId, jsonExpiration);
            }

            final Message.JsonMessage jsonMessage = new Message.JsonMessage(body);
            if (message.isLocationMessage()) {
               // manually copy location data to forwarded message (to be sure it becomes a send location message and not a sharing location)
               Message.JsonLocation jsonLocation = message.getJsonLocation();
               if (jsonLocation == null) {
                  continue;
               }
               jsonLocation.setType(Message.JsonLocation.TYPE_SEND);
               jsonMessage.setJsonLocation(jsonLocation);
            }
            if (jsonExpiration != null) {
               jsonMessage.setJsonExpiration(jsonExpiration);
            }

            db.runInTransaction(() -> {
               discussion.lastOutboundMessageSequenceNumber++;
               db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
               discussion.updateLastMessageTimestamp(System.currentTimeMillis());
               db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);

               Message newMessage = new Message(
                       db,
                       discussion.lastOutboundMessageSequenceNumber,
                       jsonMessage,
                       null,
                       System.currentTimeMillis(),
                       Message.STATUS_UNPROCESSED,
                       Message.TYPE_OUTBOUND_MESSAGE,
                       discussionId,
                       null,
                       discussion.bytesOwnedIdentity,
                       discussion.senderThreadIdentifier,
                       0, 0
               );
               if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE) {
                  newMessage.forwarded = true;
               }
               newMessage.id = db.messageDao().insert(newMessage);

               for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                  FyleMessageJoinWithStatus fyleMessageJoinWithStatus = FyleMessageJoinWithStatus.createDraft(fyleAndStatus.fyle.id,
                          newMessage.id,
                          discussion.bytesOwnedIdentity,
                          fyleAndStatus.fyle.filePath,
                          fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                          fyleAndStatus.fyleMessageJoinWithStatus.mimeType,
                          fyleAndStatus.fyleMessageJoinWithStatus.size
                  );
                  db.fyleMessageJoinWithStatusDao().insert(fyleMessageJoinWithStatus);
               }
               newMessage.recomputeAttachmentCount(db);
               db.messageDao().updateAttachmentCount(newMessage.id, newMessage.totalAttachmentCount, newMessage.imageCount, 0, newMessage.imageResolutions);

               newMessage.post(false, null);
            });
         }
      }
   }
}
