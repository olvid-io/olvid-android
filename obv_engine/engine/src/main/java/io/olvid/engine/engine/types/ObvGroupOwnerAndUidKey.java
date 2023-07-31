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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Arrays;

import io.olvid.engine.datatypes.UID;

public class ObvGroupOwnerAndUidKey {
    public final byte[] groupOwner;
    public final byte[] groupUid;

    public ObvGroupOwnerAndUidKey(byte[] groupOwnerAndUid) {
        this.groupOwner = Arrays.copyOfRange(groupOwnerAndUid, 0, groupOwnerAndUid.length - UID.UID_LENGTH);
        this.groupUid = Arrays.copyOfRange(groupOwnerAndUid, groupOwnerAndUid.length - UID.UID_LENGTH, groupOwnerAndUid.length);
    }

    public ObvGroupOwnerAndUidKey(byte[] groupOwner, byte[] groupUid) {
        this.groupOwner = groupOwner;
        this.groupUid = groupUid;
    }

    public byte[] getGroupOwnerAndUid() {
        byte[] out = new byte[groupOwner.length + groupUid.length];
        System.arraycopy(groupOwner, 0, out, 0, groupOwner.length);
        System.arraycopy(groupUid, 0, out, groupOwner.length, groupUid.length);
        return out;
    }



    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ObvGroupOwnerAndUidKey)) return false;
        return Arrays.equals(groupOwner, ((ObvGroupOwnerAndUidKey) other).groupOwner)
                && Arrays.equals(groupUid, ((ObvGroupOwnerAndUidKey) other).groupUid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(groupOwner) * 31 + Arrays.hashCode(groupUid);
    }



    public static class Serializer extends JsonSerializer<ObvGroupOwnerAndUidKey> {
        @Override
        public void serialize(ObvGroupOwnerAndUidKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(serializers.getConfig().getBase64Variant().encode(value.groupOwner) + "-" + serializers.getConfig().getBase64Variant().encode(value.groupUid));
        }
    }

    public static class Deserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext context) throws IOException {
            String[] parts = key.split("-");
            if (parts.length != 2) {
                throw new IOException();
            }
            return new ObvGroupOwnerAndUidKey(context.getConfig().getBase64Variant().decode(parts[0]), context.getConfig().getBase64Variant().decode(parts[1]));
        }
    }
}
