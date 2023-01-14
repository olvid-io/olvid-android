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

package io.olvid.engine.engine.types;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.metamanager.NetworkFetchDelegate;

public class ObvMessage {
    private final UID messageUid;
    private final long serverTimestamp;
    private final long downloadTimestamp;
    private final long localDownloadTimestamp;
    private final byte[] messagePayload;
    private final byte[] bytesFromIdentity;
    private final byte[] bytesToIdentity;
    private final ObvAttachment[] attachments;


    public byte[] getIdentifier() {
        return messageUid.getBytes();
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public long getDownloadTimestamp() {
        return downloadTimestamp;
    }

    public long getLocalDownloadTimestamp() {
        return localDownloadTimestamp;
    }

    public byte[] getMessagePayload() {
        return messagePayload;
    }

    public byte[] getBytesFromIdentity() {
        return bytesFromIdentity;
    }

    public byte[] getBytesToIdentity() {
        return bytesToIdentity;
    }

    public ObvAttachment[] getAttachments() {
        return attachments;
    }

    public ObvMessage(NetworkFetchDelegate networkFetchDelegate, Identity ownedIdentity, UID messageUid) {
        DecryptedApplicationMessage receivedMessage = networkFetchDelegate.getMessage(ownedIdentity, messageUid);
        this.messageUid = messageUid;

        if (receivedMessage == null) {
            this.serverTimestamp = 0;
            this.downloadTimestamp = 0;
            this.localDownloadTimestamp = 0;
            this.messagePayload = null;
            this.bytesFromIdentity = null;
            this.bytesToIdentity = null;
            this.attachments = new ObvAttachment[0];
        } else {
            this.messagePayload = receivedMessage.getMessagePayload();
            this.serverTimestamp = receivedMessage.getServerTimestamp();
            this.downloadTimestamp = receivedMessage.getDownloadTimestamp();
            this.localDownloadTimestamp = receivedMessage.getLocalDownloadTimestamp();
            this.bytesFromIdentity = receivedMessage.getFromIdentity().getBytes();
            this.bytesToIdentity = receivedMessage.getToIdentity().getBytes();


            ReceivedAttachment[] receivedAttachments = networkFetchDelegate.getMessageAttachments(ownedIdentity, messageUid);

            this.attachments = new ObvAttachment[receivedAttachments.length];
            for (int i = 0; i < this.attachments.length; i++) {
                ReceivedAttachment receivedAttachment = receivedAttachments[i];
                this.attachments[i] = new ObvAttachment(
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
    }
}
