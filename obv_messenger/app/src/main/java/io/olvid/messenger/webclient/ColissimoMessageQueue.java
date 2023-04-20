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

package io.olvid.messenger.webclient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask;
import io.olvid.messenger.databases.tasks.UpdateMessageBodyTask;
import io.olvid.messenger.databases.tasks.UpdateReactionsTask;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.notifications.NotificationActionService;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.datatypes.JsonSettings;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.Colissimo;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.ColissimoType;
import io.olvid.messenger.webclient.protobuf.ConnectionPingOuterClass.ConnectionPing;
import io.olvid.messenger.webclient.protobuf.RequestThumbnailOuterClass.RequestThumbnailResponse;
import io.olvid.messenger.webclient.protobuf.create.CreateMessageOuterClass.NotifMessageSent;


public class ColissimoMessageQueue {

    private final BlockingQueue<byte[]> queue;
    private volatile boolean executing = false;
    private ColissimoMessageQueueWorker worker = null;
    private final WebClientManager manager;
    private final UploadDraftAttachmentHandler attachmentHandler;
    private final HashSet<Long> receivedMessageLocalIds;

    private boolean webclientInactive = false; //set to true by timeout after 10 minutes;
    private Timer timeoutDeclareInactive = null;
    private TimerTask declareInactive = null;

    //for Settings
    private final ObjectMapper jsonObjectMapper;

    public ColissimoMessageQueue(WebClientManager manager) {
        queue = new LinkedBlockingQueue<>();
        this.manager = manager;
        this.attachmentHandler = new UploadDraftAttachmentHandler(this.manager);
        this.receivedMessageLocalIds = new HashSet<>();
        this.jsonObjectMapper = new ObjectMapper();
    }

    public void start() {
        if (executing) {
            Logger.d("MessageQueue already started");
            return;
        }
        worker = new ColissimoMessageQueueWorker(this.manager);
        worker.start();
        executing = true;
        if(SettingsActivity.webclientNotifyAfterInactivity()){
            startOrRestartTimeoutDeclareInactive();
        }
    }

    public void stop() {
        this.executing = false;
        if (this.worker != null) {
            this.worker.interrupt();
        }
        stopTimerDeclareInactive();
        //when stopping colissimo queue, just assume all attachments are lost (can't receive chunks anymore) and stop their uploading
        stopAttachmentHandler();
    }

    public void stopAttachmentHandler() {
        attachmentHandler.cancelAllCurrentUploads();
        attachmentHandler.stopTimers();
    }

    class ColissimoMessageQueueWorker extends Thread {
        private final WebClientManager manager;

        ColissimoMessageQueueWorker(WebClientManager manager) {
            this.manager = manager;
        }

        @Override
        public void run() {
            byte[] encryptedColissimo;
            byte[] decryptedColissimo;
            Colissimo colissimo;
            Colissimo outerColissimo;
            long discussionId;
            String attachmentURL;

            while (executing) {
                try {
                    encryptedColissimo = queue.take();
                } catch (InterruptedException e) {
                    continue;
                }

                // decrypt colissimo
                decryptedColissimo = this.manager.decrypt(encryptedColissimo);
                if (decryptedColissimo == null) {
                    Logger.e("Unable to decrypt colissimo, ignoring it");
                    continue;
                }

                try {
                    colissimo = Colissimo.parseFrom(decryptedColissimo);
                } catch (InvalidProtocolBufferException e) {
                    Logger.e("Unable to parse colissimo message, ignoring it");
                    e.printStackTrace();
                    continue;
                }

                Logger.d("New colissimo received: " + colissimo.getType());

                switch (colissimo.getType()) {
                    case COLISSIMO_TYPE_DEFAULT: {
                        Logger.e("Message type not specified, ignoring it");
                        break;
                    }
                    case BYE: {
                        this.manager.handlerByeColissimo();
                        break;
                    }
                    case SETTINGS: {
                        activityAfterInactivity(); //user action : saving settings
                        String settings = colissimo.getSettings().getSettings();
                        try {
                            JsonSettings obj_settings = jsonObjectMapper.readValue(settings, JsonSettings.class);
                            String lang = obj_settings.getLanguage();
                            if (lang != null && Arrays.asList(Constants.SUPPORTED_LANGUAGES).contains(lang) ||
                                    "BrowserDefault".equals(lang) ||
                                    "AppDefault".equals(lang)) {
                                SettingsActivity.setWebclientLanguage(lang);
                            }
                            SettingsActivity.setThemeWebclient(obj_settings.getTheme());
                            SettingsActivity.setWebclientSendOnEnter(obj_settings.isSendOnEnter());
                            SettingsActivity.setPlayWebclientNotificationsSoundInBrowser(obj_settings.isNotificationSound());
                            SettingsActivity.setShowWebclientNotificationsInBrowser(obj_settings.isShowNotifications());

                            //update web service context for language changes
                            if ("BrowserDefault".equals(lang)) {
                                this.manager.getService().getWebClientContext().getResources().getConfiguration().locale = new Locale(obj_settings.getWebDefaultLanguage());
                            } else if (("AppDefault".equals(lang))) {
                                this.manager.getService().getWebClientContext().getResources().getConfiguration().locale = new Locale(obj_settings.getAppDefaultLanguage());
                            } else {
                                this.manager.getService().getWebClientContext().getResources().getConfiguration().locale = new Locale(obj_settings.getLanguage());
                            }
                        } catch (Exception e) {
                            Logger.e("Could not read received settings");
                        }

                        break;
                    }
                    case CONNECTION_PING: {
                        if (colissimo.getConnectionPing() == null || (!colissimo.getConnectionPing().getPing() && !colissimo.getConnectionPing().getPong())) {
                            Logger.e("Received an invalid ping message");
                        } else if (colissimo.getConnectionPing().getPong()) {
                            Logger.d("Received pong message");
                        } else {
                            outerColissimo = Colissimo.newBuilder()
                                    .setType(ColissimoType.CONNECTION_PING)
                                    .setConnectionPing(ConnectionPing.newBuilder().setPong(true).build())
                                    .build();
                            this.manager.sendColissimo(outerColissimo);
                        }
                        break;
                    }
                    // request discussions is considered as first message sent by web, it reset any previous cache to sync it with web content
                    // send list of all discussions containing last message, unread message, ...
                    case REQUEST_DISCUSSIONS: {
                        if (manager.getDiscussionListener() == null) {
                            Logger.e("No active discussion listener found, ignore");
                            break;
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (manager.getMessageListener() != null) {
                                manager.getMessageListener().stop();
                            }
                            if (manager.getAttachmentListener() != null) {
                                manager.getAttachmentListener().stop();
                            }
                            if (manager.getDraftAttachmentListener() != null) {
                                manager.getDraftAttachmentListener().stop();
                            }
                            manager.getDiscussionListener().stop();
                            // update current identity before restart to check if it changed
                            this.manager.updateBytesCurrentIdentity();
                            manager.getDiscussionListener().addListener();
                        });
                        break;
                    }
                    // request messages in a given discussion
                    case REQUEST_MESSAGES: {
                        discussionId = colissimo.getRequestMessage().getDiscussionId();
                        int count = colissimo.getRequestMessage().getCount();

                        if (manager.getMessageListener() == null) {
                            Logger.e("No active message listener, ignoring");
                            break ;
                        }
                        manager.getMessageListener().addListener(discussionId, count);
                        manager.getDraftAttachmentListener().addListener(discussionId);
                        break;
                    }
                    // send a new message in a discussion
                    case CREATE_MESSAGE: {
                        activityAfterInactivity(); //user action : sending a new message
                        discussionId = colissimo.getCreateMessage().getDiscussionId();
                        String body = colissimo.getCreateMessage().getContent();
                        long localId = colissimo.getCreateMessage().getLocalId();
                        long replyMessageId = colissimo.getCreateMessage().getReplyMessageId();

                        // check that message has not already been received, ignore it if it had already been processed
                        if(!receivedMessageLocalIds.contains(localId)){
                            receivedMessageLocalIds.add(localId);
                            // Notify web client that message was received and is being processed
                            outerColissimo = Colissimo.newBuilder()
                                    .setType(ColissimoType.NOTIF_MESSAGE_SENT)
                                    .setNotifMessageSent(NotifMessageSent.newBuilder()
                                            .setLocalId(localId)
                                            .build())
                                    .build();
                            this.manager.sendColissimo(outerColissimo);

                            if(replyMessageId != 0){ // save draft before because there is a reply
                                // no need to save the content, will be done by PostMessageInDiscussionTask
                                saveDraft(discussionId, replyMessageId, null);
                            }
                            App.runThread(new PostMessageInDiscussionTask(
                                    body.trim(),
                                    discussionId,
                                    false,
                                    null,
                                    null
                            ));
                        }
                        break;
                    }
                    case REQUEST_THUMBNAIL: {
                        attachmentURL = colissimo.getRequestThumbnail().getRelativeURL();
                        // protection against ../ pattern in url, absolute  url are protected by concatenation in getThumbnailFromImagePath
                        if (attachmentURL.contains("../")) {
                            break;
                        }

                        byte[] thumbnailBytes = getThumbnailFromImagePath(attachmentURL);
                        if (thumbnailBytes == null) {
                            Logger.e("Could not get bytes from URL : " + attachmentURL);
                            break;
                        }
                        outerColissimo = Colissimo.newBuilder()
                                .setType(ColissimoType.REQUEST_THUMBNAIL_RESPONSE)
                                .setRequestThumbnailResponse(
                                        RequestThumbnailResponse.newBuilder()
                                            .setThumbnail(ByteString.copyFrom(thumbnailBytes))
                                            .setRelativeURL(attachmentURL))
                                .build();
                        this.manager.sendColissimo(outerColissimo);
                        break;
                    }
                    case REQUEST_MARK_DISCUSSION_AS_READ: {
                        activityAfterInactivity(); //user action : moved in conversation or between conversations
                        discussionId = colissimo.getRequestMarkDiscussionAsRead().getDiscussionId();
                        // mark all messages as read in database
                        NotificationActionService.markAllDiscussionMessagesRead(discussionId);
                        // delete android notifications for current discussion
                        AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
                        break;
                    }
                    case REQUEST_SAVE_DRAFT_MESSAGE: {
                        discussionId = colissimo.getRequestSaveDraftMessage().getDiscussionId();
                        String message = colissimo.getRequestSaveDraftMessage().getMessage();
                        long replyMessageId = colissimo.getRequestSaveDraftMessage().getReplyMessageId();
                        saveDraft(discussionId, replyMessageId, message);
                        break;
                    }
                    case REQUEST_DELETE_DRAFT_MESSAGE: {
                        discussionId = colissimo.getRequestDeleteDraftMessage().getDiscussionId();
                        AppDatabase.getInstance().messageDao().deleteDiscussionDraftMessage(discussionId);
                        break;
                    }
                    case SEND_ATTACHMENT_NOTICE: {
                        activityAfterInactivity(); //user action : new draft attachment added
                        long attachmentLocalId = colissimo.getSendAttachmentNotice().getLocalId();
                        byte[] sha256 = colissimo.getSendAttachmentNotice().getSha256().toByteArray();
                        long size = colissimo.getSendAttachmentNotice().getSize();
                        long nbChunks = colissimo.getSendAttachmentNotice().getNumberChunks();
                        String type = colissimo.getSendAttachmentNotice().getType();
                        String fileName = colissimo.getSendAttachmentNotice().getFileName();
                        discussionId = colissimo.getSendAttachmentNotice().getDiscussionId();
                        attachmentHandler.handleAttachmentNotice(attachmentLocalId, sha256, size, nbChunks, type, fileName, discussionId);
                        break;
                    }
                    case SEND_ATTACHMENT_CHUNK: {
                        long attachmentLocalId = colissimo.getSendAttachmentChunk().getLocalId();
                        long offset = colissimo.getSendAttachmentChunk().getOffset();
                        long index = colissimo.getSendAttachmentChunk().getChunkNumber();
                        byte[] chunk = colissimo.getSendAttachmentChunk().getChunk().toByteArray();
                        attachmentHandler.handleAttachmentChunk(attachmentLocalId, offset, index, chunk);
                        break;
                    }
                    case SEND_ATTACHMENT_DONE: {
                        long attachmentLocalId = colissimo.getSendAttachmentDone().getLocalId();
                        attachmentHandler.handleAttachmentDone(attachmentLocalId);
                        break;
                    }
                    case REQUEST_DELETE_DRAFT_ATTACHMENT: {
                        activityAfterInactivity(); //user action : deleted draft
                        long fyleID = colissimo.getRequestDeleteDraftAttachment().getFyleId();
                        long messageId = colissimo.getRequestDeleteDraftAttachment().getMessageId();
                        FyleMessageJoinWithStatusDao.FyleAndStatus draftFyle = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndStatus(messageId, fyleID);
                        if (draftFyle == null || draftFyle.fyleMessageJoinWithStatus == null) {
                            break;
                        }
                        if (draftFyle.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) {
                            if (draftFyle.fyleMessageJoinWithStatus.fyleId == fyleID) {
                                App.runThread(new DeleteAttachmentTask(draftFyle));
                            }
                        }
                        break;
                    }
                    case REQUEST_DOWNLOAD_ATTACHMENT: {
                        activityAfterInactivity(); //user action : downloading an attachment
                        long fyleID = colissimo.getRequestDownloadAttachment().getFyleId();
                        long size = colissimo.getRequestDownloadAttachment().getSize();
                        App.runThread(new SendAttachmentTask(this.manager, fyleID, size));
                        break;
                    }
                    case REQUEST_STOP_DRAFT_ATTACHMENT_UPLOAD: {
                        long localId = colissimo.getRequestStopDraftAttachmentUpload().getLocalId();
                        attachmentHandler.stopAttachmentUpload(localId);
                        break;
                    }
                    case REQUEST_UPDATE_MESSAGE: {
                        long messageId = colissimo.getRequestUpdateMessage().getMessageId();
                        String newContent = colissimo.getRequestUpdateMessage().getNewContent();
                        App.runThread(new UpdateMessageBodyTask(messageId, newContent, null));
                        break;
                    }
                    case REQUEST_ADD_REACTION_TO_MESSAGE:
                        String reaction = colissimo.getRequestAddReactionToMessage().getReaction();
                        long messageId = colissimo.getRequestAddReactionToMessage().getMessageId();
                        App.runThread(new UpdateReactionsTask(messageId, "".equals(reaction) ? null : reaction, null, 0));
                        break;
                    case NOTIF_NEW_DISCUSSION:
                    case NOTIF_FILE_ALREADY_ATTACHED:
                    case NOTIF_MESSAGE_SENT:
                    case NOTIF_UPDATE_DRAFT_ATTACHMENT:
                    case NOTIF_UPDATE_ATTACHMENT:
                    case NOTIF_UPDATE_MESSAGE:
                    case NOTIF_DELETE_DISCUSSION:
                    case NOTIF_DELETE_ATTACHMENT:
                    case NOTIF_DELETE_MESSAGE:
                    case NOTIF_NEW_ATTACHMENT:
                    case NOTIF_UPLOAD_RESULT:
                    case NOTIF_FILE_ALREADY_EXISTS:
                    case NOTIF_UPLOAD_FILE:
                    case REQUEST_DISCUSSIONS_RESPONSE:
                    case REQUEST_MESSAGES_RESPONSE:
                    case REQUEST_THUMBNAIL_RESPONSE:
                    case NOTIF_DISCUSSION_UPDATED:
                    case NOTIF_DISCUSSION_DELETED:
                    case NOTIF_NEW_MESSAGE:
                    case NOTIF_NEW_DRAFT_ATTACHMENT:
                    case NOTIF_DELETE_DRAFT_ATTACHMENT:
                    case RECEIVE_DOWNLOAD_ATTACHMENT_CHUNK:
                    case RECEIVE_DOWNLOAD_ATTACHMENT_DONE:
                    case NOTIF_NO_DRAFT_FOR_DISCUSSION:
                    case REFRESH:
                        Logger.e("Client sent a request response or notification message (ignoring)");
                        break;
                    case UNRECOGNIZED:
                    default:
                        Logger.e("Unexpected value: " + colissimo.getType() + " (ignoring)");
                }
            }
        }
    }

    private void saveDraft(long discussionId, long replyMessageId, @Nullable String content){
        Message.JsonMessageReference jsonReply;
        Message existingDraft = AppDatabase.getInstance().messageDao().getDiscussionDraftMessageSync(discussionId);
        try {
            if (existingDraft != null) {
                // draft already exists, so just add to draft normally
                if (replyMessageId != 0) {
                    Message replyMessage = AppDatabase.getInstance().messageDao().get(replyMessageId);
                    if (replyMessage != null) {
                        jsonReply = Message.JsonMessageReference.of(replyMessage);
                        existingDraft.jsonReply = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonReply);
                        existingDraft.timestamp = System.currentTimeMillis();
                    }
                } else {
                    existingDraft.jsonReply = null;
                }
                if(content != null){
                    existingDraft.contentBody = content;
                    existingDraft.timestamp = System.currentTimeMillis();
                    existingDraft.sortIndex = existingDraft.timestamp;
                }
                AppDatabase.getInstance().messageDao().update(existingDraft);
            } else {
                //create draft and add it
                Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
                if (discussion == null) {
                    return;
                }
                Message draftMessage = Message.createEmptyDraft(discussionId, discussion.bytesOwnedIdentity, discussion.senderThreadIdentifier);
                if (replyMessageId != 0) {
                    Message replyMessage = AppDatabase.getInstance().messageDao().get(replyMessageId);
                    if (replyMessage != null) {
                        jsonReply = Message.JsonMessageReference.of(replyMessage);
                        draftMessage.jsonReply = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonReply);
                        draftMessage.timestamp = System.currentTimeMillis();
                    }
                } else {
                    draftMessage.jsonReply = null;
                }
                if(content != null){
                    draftMessage.contentBody = content;
                    draftMessage.timestamp = System.currentTimeMillis();
                    draftMessage.sortIndex = draftMessage.timestamp;
                }
                draftMessage.id = AppDatabase.getInstance().messageDao().insert(draftMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] getThumbnailFromImagePath(String relativeURL) {
        Bitmap imageBitmap;
        if (relativeURL == null) {
            return null;
        }
        String photoPath = App.absolutePathFromRelative(relativeURL);
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoPath, options);
            int width = options.outWidth;
            int height = options.outHeight;
            if (width == -1 || height == -1) {
                return null;
            }

            int subSampling = Math.min(width, height) / Constants.ATTACHMENT_THUMBNAIL_SIZE;
            options = new BitmapFactory.Options();
            options.inSampleSize = subSampling;
            imageBitmap = BitmapFactory.decodeFile(photoPath, options);
            if (imageBitmap == null) {
                return null;
            }

            Bitmap thumbnail;
            if (width > height) {
                thumbnail = Bitmap.createBitmap(imageBitmap, (imageBitmap.getWidth() - imageBitmap.getHeight()) / 2, 0, imageBitmap.getHeight(), imageBitmap.getHeight());
            } else {
                thumbnail = Bitmap.createBitmap(imageBitmap, 0, (imageBitmap.getHeight() - imageBitmap.getWidth()) / 2, imageBitmap.getWidth(), imageBitmap.getWidth());
            }
            thumbnail = Bitmap.createScaledBitmap(thumbnail, Constants.ATTACHMENT_THUMBNAIL_SIZE, Constants.ATTACHMENT_THUMBNAIL_SIZE, true);
            try {
                ExifInterface exifInterface = new ExifInterface(photoPath);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                thumbnail = PreviewUtils.rotateBitmap(thumbnail, orientation);
            } catch (Exception e) {
                // do nothing
            }
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 70, byteArray);
            byte[] thumbnailBytes = byteArray.toByteArray();
            imageBitmap.recycle();
            thumbnail.recycle();

            return thumbnailBytes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Called only for certain types of messages : either resets inactivity timeout if webclient is not declared inactive, or notify user.
    // Reacts to the first event and then considers webclient as active again.
    private void activityAfterInactivity() {
        if (!SettingsActivity.webclientNotifyAfterInactivity()) {
            return;
        }
        if (webclientInactive) {
            try {
                AndroidNotificationManager.displayWebclientActivityAfterInactivityNotification(App.getContext().getString(R.string.activity_detected_after_inactivity));
                webclientInactive = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        startOrRestartTimeoutDeclareInactive(); //reset anyway, one notification at a time
    }

    private void startOrRestartTimeoutDeclareInactive() {
        try {
            this.stopTimerDeclareInactive();
            this.timeoutDeclareInactive = new Timer("WaitingForReconnectionTimeout");
            this.declareInactive = new DeclareInactiveTask();
            this.timeoutDeclareInactive.schedule(this.declareInactive, Constants.DECLARE_INACTIVE_TIMEOUT);
        } catch (Exception e) {
            Logger.e("Could not schedule reconnection timeout");
        }
    }

    class DeclareInactiveTask extends TimerTask {
        @Override
        public void run() {
            Logger.w("App declared inactive.");
            webclientInactive = true;
            timeoutDeclareInactive.cancel();
        }
    }

    private void stopTimerDeclareInactive() {
        if(this.timeoutDeclareInactive != null){
            this.timeoutDeclareInactive.cancel();
            this.timeoutDeclareInactive = null;
        }
        if(this.declareInactive != null){
            this.declareInactive.cancel();
            this.declareInactive = null;
        }
    }

    public void queue(byte[] encryptedColissimo) {
        queue.add(encryptedColissimo);
    }
}