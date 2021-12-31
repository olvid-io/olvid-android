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

import java.util.Arrays;
import java.util.HashMap;

public class ObvPostMessageOutput {
    private final boolean messageSent;
    // the following hashmap contains the messageUid from the engine for contacts to which the message was sent,
    // and null for contacts for which sending failed
    private final HashMap<BytesKey, byte[]> messageIdentifierByContactIdentity;

    public ObvPostMessageOutput(boolean messageSent, HashMap<BytesKey, byte[]> messageIdentifierByContactIdentity) {
        this.messageSent = messageSent;
        this.messageIdentifierByContactIdentity = messageIdentifierByContactIdentity;
    }

    public boolean isMessageSent() {
        return messageSent;
    }

    public HashMap<BytesKey, byte[]> getMessageIdentifierByContactIdentity() {
        return messageIdentifierByContactIdentity;
    }

    public static class BytesKey {
        final byte[] bytes;

        public BytesKey(byte[] bytes) {
            this.bytes = bytes;
        }

        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BytesKey)) return false;
            return Arrays.equals(bytes, ((BytesKey)other).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
