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

import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.RequestDownloadAttachmentOuterClass;

public class SendAttachmentTask implements Runnable {
    private final long fyleId ;
    private final WebClientManager manager;


    SendAttachmentTask(WebClientManager manager, long fyleId, long size) {
        this.manager = manager;
        this.fyleId = fyleId;
    }

    private void sendChunk(long fyleId, long chunkNumber, byte[] chunk) {
        RequestDownloadAttachmentOuterClass.ReceiveDownloadAttachmentChunk.Builder chunkBuilder = RequestDownloadAttachmentOuterClass.ReceiveDownloadAttachmentChunk.newBuilder();
        ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
        chunkBuilder.setChunk(ByteString.copyFrom(chunk));
        chunkBuilder.setChunkNumber(chunkNumber);
        chunkBuilder.setFyleId(fyleId);
        colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.RECEIVE_DOWNLOAD_ATTACHMENT_CHUNK);
        colissimoBuilder.setReceiveDownloadAttachmentChunk(chunkBuilder);
        this.manager.sendColissimo(colissimoBuilder.build());
    }

    private void sendResult(long fyleId, boolean success) {
        RequestDownloadAttachmentOuterClass.ReceiveDownloadAttachmentDone.Builder chunkBuilder = RequestDownloadAttachmentOuterClass.ReceiveDownloadAttachmentDone.newBuilder();
        ColissimoOuterClass.Colissimo.Builder colissimoBuilder = ColissimoOuterClass.Colissimo.newBuilder();
        chunkBuilder.setFyleId(fyleId);
        chunkBuilder.setSuccess(success);
        colissimoBuilder.setType(ColissimoOuterClass.ColissimoType.RECEIVE_DOWNLOAD_ATTACHMENT_DONE);
        colissimoBuilder.setReceiveDownloadAttachmentDone(chunkBuilder);
        this.manager.sendColissimo(colissimoBuilder.build());
    }

    @Override
    public void run() {
        Fyle fileToSend = AppDatabase.getInstance().fyleDao().getById(fyleId);
        if (fileToSend == null) {
            Logger.e("fyleId : " + fyleId + " is incorrect, aborting");
            sendResult(fyleId, false);
            return;
        }
        String fyleAbsoluteFilePath = App.absolutePathFromRelative(fileToSend.filePath);
        if (fyleAbsoluteFilePath == null) {
            Logger.e("Could not find attachment, aborting");
            sendResult(fyleId, false);
            return;
        }
        try (InputStream in = new FileInputStream(fyleAbsoluteFilePath)) {
            byte[] buffer = new byte[Constants.CHUNK_SIZE];

            int index = 0;

            boolean eof = false;
            while (!eof) {
                int bufferFullness = 0;
                while (bufferFullness < buffer.length) {
                    int count = in.read(buffer, bufferFullness, buffer.length - bufferFullness);
                    if (count < 0) {
                        eof = true;
                        break;
                    }
                    bufferFullness += count;
                }
                if (bufferFullness == 0) {
                    Logger.w("SendAttachmentTask : Error in read");
                    sendResult(fyleId, false);
                    return;
                }

                if (bufferFullness == buffer.length) {
                    sendChunk(fyleId, index, buffer);
                } else {
                    sendChunk(fyleId, index, Arrays.copyOfRange(buffer, 0, bufferFullness));
                }
                index++;
            }
            sendResult(fyleId, true);
        } catch (IOException e) {
            Logger.w("SendAttachmentTask : Error, aborting");
            sendResult(fyleId, false);
            e.printStackTrace();
        }
    }
}
