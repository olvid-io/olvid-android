/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

public class DecryptedApplicationMessage {
    private final UID messageUid;
    private final byte[] messagePayload;
    private final Identity fromIdentity;
    private final Identity toIdentity;
    private final long serverTimestamp;
    private final long downloadTimestamp;
    private final long localDownloadTimestamp;

    public DecryptedApplicationMessage(UID messageUid, byte[] messagePayload, Identity fromIdentity, Identity toIdentity, long serverTimestamp, long downloadTimestamp, long localDownloadTimestamp) {
        this.messageUid = messageUid;
        this.messagePayload = messagePayload;
        this.fromIdentity = fromIdentity;
        this.toIdentity = toIdentity;
        this.serverTimestamp = serverTimestamp;
        this.downloadTimestamp = downloadTimestamp;
        this.localDownloadTimestamp = localDownloadTimestamp;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public byte[] getMessagePayload() {
        return messagePayload;
    }

    public Identity getFromIdentity() {
        return fromIdentity;
    }

    public Identity getToIdentity() {
        return toIdentity;
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
}
