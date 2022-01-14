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

package io.olvid.engine.engine.types;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.metamanager.NetworkFetchDelegate;

public class ObvAttachment {
    private final byte[] metadata;
    private final String url; // should always be the getNoBackupFilesDir() relative path to the file
    private final boolean downloadRequested;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final long messageServerTimestamp;
    private final int number;
    private final long expectedLength;
    private final long receivedLength;

    public byte[] getMetadata() {
        return metadata;
    }

    public String getUrl() {
        return url;
    }

    public boolean isDownloadRequested() {
        return downloadRequested;
    }

    public long getMessageServerTimestamp() {
        return messageServerTimestamp;
    }

    public byte[] getBytesOwnedIdentity() {
        return ownedIdentity.getBytes();
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public byte[] getMessageIdentifier() {
        return messageUid.getBytes();
    }

    public int getNumber() {
        return number;
    }

    public long getExpectedLength() {
        return expectedLength;
    }

    public long getReceivedLength() {
        return receivedLength;
    }


    ObvAttachment(byte[] metadata, String url, boolean downloadRequested, Identity ownedIdentity, UID messageUid, long messageServerTimestamp, int number, long expectedLength, long receivedLength) {
        this.metadata = metadata;
        this.url = url;
        this.downloadRequested = downloadRequested;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.messageServerTimestamp = messageServerTimestamp;
        this.number = number;
        this.expectedLength = expectedLength;
        this.receivedLength = receivedLength;
    }

    public static ObvAttachment create(NetworkFetchDelegate networkFetchDelegate, Identity ownedIdentity, UID messageUid, int attachmentNumber) {
        ReceivedAttachment receivedAttachment = networkFetchDelegate.getAttachment(ownedIdentity, messageUid, attachmentNumber);
        if (receivedAttachment == null) {
            return null;
        }
        DecryptedApplicationMessage receivedMessage = networkFetchDelegate.getMessage(ownedIdentity, receivedAttachment.getMessageUid());
        if (receivedMessage == null) {
            return null;
        }
        Identity ownIdentity = receivedMessage.getToIdentity();
        if (ownIdentity == null) {
            return null;
        }

        return new ObvAttachment(
                receivedAttachment.getMetadata(),
                receivedAttachment.getUrl(),
                receivedAttachment.isDownloadRequested(),
                receivedAttachment.getOwnedIdentity(),
                receivedAttachment.getMessageUid(),
                receivedMessage.getServerTimestamp(),
                receivedAttachment.getAttachmentNumber(),
                receivedAttachment.getExpectedLength(),
                receivedAttachment.getReceivedLength()
        );
    }
}
