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

package io.olvid.engine.engine.types;

public class ObvReturnReceipt {
    private final byte[] bytesContactIdentity;
    private final int status;
    private final Integer attachmentNumber; // null if this is a message return receipt

    public ObvReturnReceipt(byte[] bytesContactIdentity, int status) {
        this.bytesContactIdentity = bytesContactIdentity;
        this.status = status;
        this.attachmentNumber = null;
    }

    public ObvReturnReceipt(byte[] bytesContactIdentity, int status, int attachmentNumber) {
        this.bytesContactIdentity = bytesContactIdentity;
        this.status = status;
        this.attachmentNumber = attachmentNumber;
    }

    public byte[] getBytesContactIdentity() {
        return bytesContactIdentity;
    }

    public int getStatus() {
        return status;
    }

    public Integer getAttachmentNumber() {
        return attachmentNumber;
    }
}
