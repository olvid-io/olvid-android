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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;

public class ReceivedAttachment {
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final int attachmentNumber;
    private final byte[] metadata;
    private final String url;
    private final long expectedLength;
    private final long receivedLength;
    private final boolean downloadRequested;

    public ReceivedAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber, byte[] metadata, String url, long expectedLength, long receivedLength, boolean downloadRequested) {
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.attachmentNumber = attachmentNumber;
        this.metadata = metadata;
        this.url = url;
        this.expectedLength = expectedLength;
        this.receivedLength = receivedLength;
        this.downloadRequested = downloadRequested;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public int getAttachmentNumber() {
        return attachmentNumber;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public String getUrl() {
        return url;
    }

    public long getExpectedLength() {
        return expectedLength;
    }

    public long getReceivedLength() {
        return receivedLength;
    }

    public boolean isDownloadRequested() {
        return downloadRequested;
    }
}
