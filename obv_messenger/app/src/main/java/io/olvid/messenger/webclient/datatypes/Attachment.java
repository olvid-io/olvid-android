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

package io.olvid.messenger.webclient.datatypes;

import java.io.File;
import java.util.HashSet;

public class Attachment {

    private final byte[] sha256;
    private final long size;
    private final long expectedChunkCount;
    private final String mimeType;
    private final HashSet<Long> nonReceivedChunksNumber;
    private final File absoluteLocalFile;
    private final long discussionId;
    private final String fileName;
    //doneReceived keeps tracks of whether or not a done message has been received
    //used to keep receiving chunks for 15 seconds after done message in case they were not received in order (slow connection).
    private Boolean doneReceived;

    public Attachment(byte[] sha256, long size, long expexctedChunkCount, String mimeType, String fileName, File absoluteLocalFile, long discussionId) {
        this.sha256 = sha256;
        this.size = size;
        this.expectedChunkCount = expexctedChunkCount;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.nonReceivedChunksNumber = new HashSet<>();
        for (long i = 0; i < expectedChunkCount; i++) {
            this.nonReceivedChunksNumber.add(i);
        }
        this.absoluteLocalFile = absoluteLocalFile;
        this.discussionId = discussionId;
        this.doneReceived = false;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getSha256() {
        return sha256;
    }

    public long getSize() {
        return size;
    }

    public HashSet<Long> getNonReceivedChunksNumber() {
        return nonReceivedChunksNumber;
    }

    public void removeChunkIndex(long index) {
        nonReceivedChunksNumber.remove(index);
    }

    public File getAbsoluteLocalFile() {
        return absoluteLocalFile;
    }

    public long getDiscussionId() {
        return discussionId;
    }


    public Boolean getDoneReceived() {
        return doneReceived;
    }

    public void setDoneReceived(Boolean doneReceived) {
        this.doneReceived = doneReceived;
    }

}
