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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Arrays;

public class ObvBytesKey implements Comparable<ObvBytesKey> {
    final byte[] bytes;

    public ObvBytesKey(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ObvBytesKey)) return false;
        return Arrays.equals(bytes, ((ObvBytesKey) other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public int compareTo(ObvBytesKey other) {
        if (bytes.length != other.bytes.length) {
            return bytes.length - other.bytes.length;
        }
        for (int i=0; i<bytes.length; i++) {
            if (bytes[i] != other.bytes[i]) {
                return (bytes[i] & 0xff) - (other.bytes[i] & 0xff);
            }
        }
        return 0;
    }


    public static class KeySerializer extends JsonSerializer<ObvBytesKey> {
        @Override
        public void serialize(ObvBytesKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(serializers.getConfig().getBase64Variant().encode(value.bytes));
        }
    }

    public static class KeyDeserializer extends com.fasterxml.jackson.databind.KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return new ObvBytesKey(ctxt.getConfig().getBase64Variant().decode(key));
        }
    }

    public static class Serializer extends JsonSerializer<ObvBytesKey> {
        @Override
        public void serialize(ObvBytesKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(serializers.getConfig().getBase64Variant().encode(value.bytes));
        }
    }

    public static class Deserializer extends JsonDeserializer<ObvBytesKey> {
        @Override
        public ObvBytesKey deserialize(JsonParser p, DeserializationContext context) throws IOException {
            return new ObvBytesKey(context.getConfig().getBase64Variant().decode(p.getValueAsString()));
        }
    }
}
