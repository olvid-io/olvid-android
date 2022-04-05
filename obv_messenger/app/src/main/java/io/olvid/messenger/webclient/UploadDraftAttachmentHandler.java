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

package io.olvid.messenger.webclient;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.tasks.AddExistingFyleToDraft;
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webclient.datatypes.Attachment;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifUploadFileOuterClass;
import io.olvid.messenger.webclient.protobuf.notifications.NotifUploadResultOuterClass;

public class UploadDraftAttachmentHandler {
    private final WebClientManager manager;
    private final HashMap<Long, Attachment> attachmentsMap;

    //arrays for the timers, there can be multiple ones at once for different attachments
    private final ArrayList<Timer> timeoutAttachmentDoneReceived;

    UploadDraftAttachmentHandler(WebClientManager manager) {
        this.manager = manager;
        this.attachmentsMap = new HashMap<>();
        this.timeoutAttachmentDoneReceived = new ArrayList<>();
    }

    private void sendUploadResult(long localId, long discussionId, boolean success){
        NotifUploadResultOuterClass.NotifUploadResult.Builder notifBuilder = NotifUploadResultOuterClass.NotifUploadResult.newBuilder();
        ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
        notifBuilder.setLocalId(localId);
        notifBuilder.setDiscussionId(discussionId);
        if(!success) {
            notifBuilder.setResultCode(0); //fail
        } else {
            if("image/jpeg".equals(attachmentsMap.get(localId).getMimeType()) && SettingsActivity.getMetadataRemovalPreference()) {
                notifBuilder.setResultCode(2); // success with EXIF: SHA will be changed
            } else {
                notifBuilder.setResultCode(1); // success without EXIF: SHA will not be changed
            }
        }
        colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_UPLOAD_RESULT);
        colissimoBuilder.setNotifUploadResult(notifBuilder);
        this.manager.sendColissimo(colissimoBuilder.build());
    }

    void handleAttachmentNotice(long attachmentLocalId, byte[] sha256, long size, long nbChunks, String type, String fileName, long discussionId) {
        Random random = new Random();
        String localAttachmentFileName = Logger.toHexString(sha256) + "_" + random.nextInt(65536);
        if(attachmentsMap.get(attachmentLocalId) != null) {
            attachmentsMap.remove(attachmentLocalId); //restart upload
            return;
        }
        Fyle retrievedFyle = AppDatabase.getInstance().fyleDao().getBySha256(sha256);

        if(retrievedFyle != null && retrievedFyle.isComplete()){ // file already exists so notify WebClient and don't send file at all
            // add it to draft
            // send appropriate message : either the file was successfully added to draft (notif_file_already_exists)
            // or file was already in draft (notif_file_already_attached)
            App.runThread(new AddExistingFyleToDraft(
                    retrievedFyle,
                    discussionId,
                    type,
                    fileName,
                    size,
                    sha256,
                    this.manager,
                    attachmentLocalId
            ));
        } else { // file doesn't exist yet
            // create file for this attachment
            File attachmentDir = new File(App.getContext().getCacheDir(), App.WEBCLIENT_ATTACHMENT_FOLDER);
            File attachmentFile = new File(attachmentDir, localAttachmentFileName);
            try {
                //noinspection ResultOfMethodCallIgnored
                attachmentDir.mkdirs();
                if (!attachmentFile.createNewFile()) {
                    // file already exists, will throw an exception if there is an error when creating
                    sendUploadResult(attachmentLocalId, discussionId, false);
                    return;
                }
                this.attachmentsMap.put(attachmentLocalId, new Attachment(sha256, size, nbChunks, type, fileName, attachmentFile, discussionId));
                // notify web client to send chunks for that file
                NotifUploadFileOuterClass.NotifUploadFile.Builder notifUploadFile = NotifUploadFileOuterClass.NotifUploadFile.newBuilder();
                ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
                notifUploadFile.setLocalId(attachmentLocalId);
                colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.NOTIF_UPLOAD_FILE);
                colissimoBuilder.setNotifUploadFile(notifUploadFile);
                this.manager.sendColissimo(colissimoBuilder.build());
            } catch (IOException e) {
                sendUploadResult(attachmentLocalId, discussionId, false);
                e.printStackTrace();
            }
        }
    }

    void handleAttachmentChunk(long attachmentLocalId, long offset, long index, byte [] chunk) {
        Attachment attachment = this.attachmentsMap.get(attachmentLocalId);
        if(attachment == null){ //no attachment associated to id (for example upload was canceled in the middle)
            Logger.e("UploadDraftAttachmentHandler : No attachment for this Id");
            return;
        }
        if(!attachment.getNonReceivedChunksNumber().contains(index)) { // already received that chunk
            Logger.e("UploadDraftAttachmentHandler : Chunk already received");
            return;
        }
        // add chunk to file with offset
        try (RandomAccessFile file = new RandomAccessFile(attachment.getAbsoluteLocalFile(),"rw")) {
            file.seek(offset);
            file.write(chunk);
            attachment.removeChunkIndex(index); // remove only if chunk was successfully added
        } catch (IOException e) {
            sendUploadResult(attachmentLocalId,attachment.getDiscussionId(),false);
            e.printStackTrace();
        }

        // if received done message before but all chunks weren't received yet, finish operation directly from here when all chunks are finally received
        if(attachment.getNonReceivedChunksNumber().isEmpty() && attachment.getDoneReceived()) {
            attachmentDoneUploading(attachmentLocalId, true);
        }
    }

    void handleAttachmentDone(long attachmentLocalId) {

        Attachment attachment = this.attachmentsMap.get(attachmentLocalId);

        if(attachment != null && attachment.getNonReceivedChunksNumber().isEmpty()) { //all chunks received
            attachmentDoneUploading(attachmentLocalId, true);
        } else if(attachment != null) { //not everything was received : indicate upload has failed
            Logger.w("UploadDraftAttachmentHandler : Not everything was received yet");
            attachment.setDoneReceived(true);
            //check that we received all chunks within 15 seconds of receiving the done message if they weren't already all received
            //if they finally arrive, the call to attachmentDoneUploading from handleAttachmentChunk will have removed this attachmentId from this.attachments array
            Timer newTimer = new Timer();
            newTimer.schedule(new AttachmentDoneReceived(attachmentLocalId), Constants.ATTACHMENT_DONE_TIMEOUT_MS);
            this.timeoutAttachmentDoneReceived.add(newTimer);
        }
    }

    //called to finish uploading operation
    void attachmentDoneUploading(long attachmentLocalId, boolean success) {
        Attachment attachment = this.attachmentsMap.get(attachmentLocalId);
        if (attachment == null) {
            return;
        }
        if (success) { //all chunks received
            Logger.w("UploadDraftAttachmentHandler : upload succeeded");
            sendUploadResult(attachmentLocalId, attachment.getDiscussionId(), true);
            App.runThread(new AddFyleToDraftFromUriTask(
                    attachment.getAbsoluteLocalFile(),
                    attachment.getDiscussionId(),
                    attachment.getMimeType(),
                    attachment.getFileName(),
                    attachment.getSha256()
            ));
        } else {
            //noinspection ResultOfMethodCallIgnored
            attachment.getAbsoluteLocalFile().delete();
            sendUploadResult(attachmentLocalId, attachment.getDiscussionId(), false);
        }
        this.attachmentsMap.remove(attachmentLocalId);
    }

    void stopAttachmentUpload(long attachmentLocalId) {
        Logger.e("Stopping draft attachment upload for localId : " + attachmentLocalId);
        Attachment attachment = this.attachmentsMap.get(attachmentLocalId);
        if (attachment == null) {
            return; //no uploading attachment for this localId, abort
        }
        //delete file that was filling up
        //noinspection ResultOfMethodCallIgnored
        attachment.getAbsoluteLocalFile().delete();
        //and delete localId from the map
        //if chunks still arrive later, this localId will not exist and will be ignored
        this.attachmentsMap.remove(attachmentLocalId);
    }

    void cancelAllCurrentUploads() {
        List<Long> localIds = new ArrayList<>(this.attachmentsMap.keySet());
        for (Long localId : localIds) {
            stopAttachmentUpload(localId);
        }
    }

    void stopTimers() {
        for(int i = 0 ; i < this.timeoutAttachmentDoneReceived.size() ; i++){
            if(this.timeoutAttachmentDoneReceived.get(i) != null){
                this.timeoutAttachmentDoneReceived.get(i).cancel();
            }
        }
        this.timeoutAttachmentDoneReceived.clear();
    }

    class AttachmentDoneReceived extends TimerTask {
        private final long attachmentLocalId;
        public AttachmentDoneReceived(long attachmentLocalId){
            this.attachmentLocalId = attachmentLocalId;
        }
        @Override
        public void run() {
            Logger.w("UploadDraftAttachmentHandler : upload timed out");
            if(attachmentsMap.containsKey(attachmentLocalId)){ //attachmentId still in list so haven't received all chunks
                attachmentDoneUploading(attachmentLocalId, false);
            }
        }
    }
}
