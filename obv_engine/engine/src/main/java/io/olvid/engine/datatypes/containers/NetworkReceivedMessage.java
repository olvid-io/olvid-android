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

public class NetworkReceivedMessage {
    private final UID messageUid;
    private final long serverTimestamp;
    private final EncryptedBytes encryptedContent;
    private final Header header;
    private final boolean hasExtendedPayload;

    public NetworkReceivedMessage(UID messageUid, long serverTimestamp, EncryptedBytes encryptedContent, Header header, boolean hasExtendedPayload) {
        this.messageUid = messageUid;
        this.serverTimestamp = serverTimestamp;
        this.encryptedContent = encryptedContent;
        this.header = header;
        this.hasExtendedPayload = hasExtendedPayload;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public EncryptedBytes getEncryptedContent() {
        return encryptedContent;
    }

    public Header getHeader() {
        return header;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public Identity getOwnedIdentity() {
        return header.ownedIdentity;
    }

    public boolean hasExtendedPayload() {
        return hasExtendedPayload;
    }

    public static class Header {
        private final Identity ownedIdentity;
        private final EncryptedBytes wrappedKey;

        public Header(Identity ownedIdentity, EncryptedBytes wrappedKey) {
            this.ownedIdentity = ownedIdentity;
            this.wrappedKey = wrappedKey;
        }

        public Identity getOwnedIdentity() {
            return ownedIdentity;
        }

        public EncryptedBytes getWrappedKey() {
            return wrappedKey;
        }
    }
}
