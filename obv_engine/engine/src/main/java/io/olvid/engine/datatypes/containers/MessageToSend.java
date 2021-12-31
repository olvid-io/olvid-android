/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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


import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public class MessageToSend {
    private final Identity ownedIdentity;
    private final UID uid;
    private final String server;
    private final EncryptedBytes encryptedContent;
    private final EncryptedBytes encryptedExtendedContent;
    private final Header[] headers;
    private final Attachment[] attachments;
    private final boolean isApplicationMessage;
    private final boolean isVoipMessage;

    public MessageToSend(Identity ownedIdentity, UID uid, String server, EncryptedBytes encryptedContent, Header[] headers) {
        this(ownedIdentity, uid, server, encryptedContent, null, headers, new Attachment[0], false, false);
    }

    public MessageToSend(Identity ownedIdentity, UID uid, String server, EncryptedBytes encryptedContent, EncryptedBytes encryptedExtendedContent, Header[] headers, Attachment[] attachments, boolean isApplicationMessage, boolean isVoipMessage) {
        this.ownedIdentity = ownedIdentity;
        this.uid = uid;
        this.server = server;
        this.encryptedContent = encryptedContent;
        this.encryptedExtendedContent = encryptedExtendedContent;
        this.headers = headers;
        this.attachments = attachments;
        this.isApplicationMessage = isApplicationMessage;
        this.isVoipMessage = isVoipMessage;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getUid() {
        return uid;
    }

    public String getServer() {
        return server;
    }

    public EncryptedBytes getEncryptedContent() {
        return encryptedContent;
    }

    public EncryptedBytes getEncryptedExtendedContent() {
        return encryptedExtendedContent;
    }

    public Header[] getHeaders() {
        return headers;
    }

    public Attachment[] getAttachments() {
        return attachments;
    }

    public boolean isApplicationMessage() {
        return isApplicationMessage;
    }

    public boolean isVoipMessage() {
        return isVoipMessage;
    }

    public static class Header {
        private final UID deviceUid;
        private final Identity toIdentity;
        private final EncryptedBytes wrappedMessageKey;


        public Header(UID deviceUid, Identity toIdentity, EncryptedBytes wrappedMessageKey) {
            this.deviceUid = deviceUid;
            this.toIdentity = toIdentity;
            this.wrappedMessageKey = wrappedMessageKey;
        }

        public UID getDeviceUid() {
            return deviceUid;
        }

        public Identity getToIdentity() {
            return toIdentity;
        }

        public EncryptedBytes getWrappedMessageKey() {
            return wrappedMessageKey;
        }
    }

    public static class Attachment {
        private final String url;
        private final boolean deleteAfterSend;
        private final long attachmentLength;
        private final AuthEncKey key;

        public Attachment(String url, boolean deleteAfterSend, long attachmentLength, AuthEncKey key) {
            this.url = url;
            this.deleteAfterSend = deleteAfterSend;
            this.attachmentLength = attachmentLength;
            this.key = key;
        }

        public String getUrl() {
            return url;
        }

        public boolean isDeleteAfterSend() {
            return deleteAfterSend;
        }

        public long getAttachmentLength() {
            return attachmentLength;
        }

        public AuthEncKey getKey() {
            return key;
        }
    }
}
