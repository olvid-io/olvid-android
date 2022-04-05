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

package io.olvid.engine.encoder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.PrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.PublicKey;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;


public class Encoded {
    public static final int INT_ENCODING_LENGTH = 8;
    public static final int ENCODED_HEADER_LENGTH = 5;

    protected final byte[] data;

    private static final byte BYTE_IDS_BYTE_ARRAY = (byte) 0x00;
    private static final byte BYTE_IDS_INT        = (byte) 0x01;
    private static final byte BYTE_IDS_BOOLEAN    = (byte) 0x02;
    private static final byte BYTE_IDS_LIST       = (byte) 0x03;
    private static final byte BYTE_IDS_DICTIONARY = (byte) 0x04;
    private static final byte BYTE_IDS_BIG_UINT   = (byte) 0x80;
    private static final byte BYTE_IDS_SYM_KEY    = (byte) 0x90;
    private static final byte BYTE_IDS_PUB_KEY    = (byte) 0x91;
    private static final byte BYTE_IDS_PRV_KEY    = (byte) 0x92;


    public Encoded(byte[] bytes) {
        data = bytes;
    }

    public static byte[] encodeChunk(int chunkNumber, byte[] buffer, int bufferFullness) {
        byte[] output = new byte[15 + INT_ENCODING_LENGTH + bufferFullness];
        output[0] = BYTE_IDS_LIST;
        System.arraycopy(bytesFromUInt32(10 + INT_ENCODING_LENGTH + bufferFullness), 0, output, 1, 4);
        output[5] = BYTE_IDS_INT;
        output[6] = 0;
        output[7] = 0;
        output[8] = 0;
        output[9] = INT_ENCODING_LENGTH;
        for (int j=0; j<INT_ENCODING_LENGTH; j++) {
            output[9+INT_ENCODING_LENGTH-j] = (byte)(chunkNumber & 0xff);
            chunkNumber = chunkNumber >>> 8;
        }
        output[10+INT_ENCODING_LENGTH] = BYTE_IDS_BYTE_ARRAY;
        System.arraycopy(bytesFromUInt32(bufferFullness), 0, output, 11 + INT_ENCODING_LENGTH, 4);
        System.arraycopy(buffer, 0, output, 15 + INT_ENCODING_LENGTH, bufferFullness);
        return output;
    }

    public byte[] getBytes() {
        return data;
    }

    public boolean equals(Object o) {
        return (o instanceof Encoded) && Arrays.equals(data, ((Encoded) o).getBytes());
    }

    // region Encoder.of

    public static Encoded fromLongerByteArray(byte[] bytes) throws DecodingException {
        if (bytes.length < 5) {
            throw new DecodingException();
        }

        int len = uint32FromBytes(bytes, 1);
        if (bytes.length < len + 5) {
            throw new DecodingException();
        }
        return new Encoded(Arrays.copyOfRange(bytes, 0, 5+len));
    }

    public static Encoded of(byte[] bytes) {
        byte[] data = new byte[bytes.length + 5];
        data[0] = BYTE_IDS_BYTE_ARRAY;
        byte[] encodedLength = bytesFromUInt32(bytes.length);
        System.arraycopy(encodedLength, 0, data, 1, 4);
        System.arraycopy(bytes, 0, data, 5, bytes.length);
        return new Encoded(data);
    }

    public static Encoded of(UID uid) {
        return Encoded.of(uid.getBytes());
    }

    public static Encoded of(UID[] uids) {
        Encoded[] encodedUids = new Encoded[uids.length];
        for (int i=0; i<uids.length; i++) {
            encodedUids[i] = Encoded.of(uids[i]);
        }
        return Encoded.of(encodedUids);
    }


    public static Encoded of(EncryptedBytes cipher) {
        return Encoded.of(cipher.getBytes());
    }

    public static Encoded of(Identity identity) {
        return Encoded.of(identity.getBytes());
    }

    public static Encoded of(Identity[] identities) {
        Encoded[] encodedIdentities = new Encoded[identities.length];
        for (int i=0; i<identities.length; i++) {
            encodedIdentities[i] = Encoded.of(identities[i]);
        }
        return Encoded.of(encodedIdentities);
    }

    public static Encoded of(Seed seed) {
        return Encoded.of(seed.getBytes());
    }

    public static Encoded of(String string) {
        return Encoded.of(string.getBytes(StandardCharsets.UTF_8));
    }

    public static Encoded of(String[] strings) {
        Encoded[] encodedStrings = new Encoded[strings.length];
        for (int i=0; i<strings.length; i++) {
            encodedStrings[i] = Encoded.of(strings[i]);
        }
        return Encoded.of(encodedStrings);
    }

    public static Encoded of(UUID uuid) {
        return Encoded.of(uuid.toString());
    }

    public static Encoded of(long i) {
        byte[] data = new byte[INT_ENCODING_LENGTH + 5];
        data[0] = BYTE_IDS_INT;
        data[1] = 0;
        data[2] = 0;
        data[3] = 0;
        data[4] = INT_ENCODING_LENGTH;
        for (int j=0; j<INT_ENCODING_LENGTH; j++) {
            data[4+INT_ENCODING_LENGTH-j] = (byte)(i & 0xff);
            i = i >>> 8;
        }
        return new Encoded(data);
    }

    public static Encoded of(boolean b) {
        byte[] data = new byte[1 + 5];
        data[0] = BYTE_IDS_BOOLEAN;
        data[1] = 0;
        data[2] = 0;
        data[3] = 0;
        data[4] = 1;
        if (b) {
            data[5] = (byte) 0x01;
        } else {
            data[5] = (byte) 0x00;
        }
        return new Encoded(data);

    }

    public static Encoded of(PublicKey publicKey) {
        Encoded keyType = Encoded.of(new byte[] {publicKey.getAlgorithmClass(), publicKey.getAlgorithmImplementation()});
        Encoded encodedDict = Encoded.of(publicKey.getKey());
        return pack(BYTE_IDS_PUB_KEY, new Encoded[]{keyType, encodedDict});
    }

    public static Encoded of(PrivateKey privateKey) {
        Encoded keyType = Encoded.of(new byte[] {privateKey.getAlgorithmClass(), privateKey.getAlgorithmImplementation()});
        Encoded encodedDict = Encoded.of(privateKey.getKey());
        return pack(BYTE_IDS_PRV_KEY, new Encoded[]{keyType, encodedDict});
    }

    public static Encoded of(SymmetricKey symmetricKey) {
        Encoded keyType = Encoded.of(new byte[] {symmetricKey.getAlgorithmClass(), symmetricKey.getAlgorithmImplementation()});
        Encoded encodedDict = Encoded.of(symmetricKey.getKey());
        return pack(BYTE_IDS_SYM_KEY, new Encoded[]{keyType, encodedDict});
    }

    public static Encoded of(BigInteger bigInt, int len) throws EncodingException {
        if ((bigInt.signum() < 0)
                || (bigInt.bitLength() > 8*len)) {
            throw new EncodingException();
        }
        byte[] data = new byte[len + 5];
        data[0] = BYTE_IDS_BIG_UINT;
        byte[] encodedLength = bytesFromUInt32(len);
        System.arraycopy(encodedLength, 0, data, 1, 4);
        byte[] bytes = bigInt.toByteArray();
        int offset = len - bytes.length;
        if (offset == -1) {
            System.arraycopy(bytes, 1, data, 5, len);
        } else {
            System.arraycopy(bytes, 0, data, 5+offset, bytes.length);
        }
        return new Encoded(data);
    }

    public static byte[] bytesFromBigUInt(BigInteger bigInt, int len) throws EncodingException {
        if ((bigInt.signum() < 0)
                || (bigInt.bitLength() > 8*len)) {
            throw new EncodingException();
        }
        byte[] data = new byte[len];
        byte[] bytes = bigInt.toByteArray();
        int offset = len - bytes.length;
        if (offset == -1) {
            System.arraycopy(bytes, 1, data, 0, len);
        } else {
            System.arraycopy(bytes, 0, data, offset, bytes.length);
        }
        return data;
    }

    public static Encoded of(Encoded[] list) {
        return pack(BYTE_IDS_LIST, list);
    }

    private static Encoded pack(byte byteId, Encoded[] list) {
        int len = 0;
        for (Encoded encoded: list) {
            len += encoded.data.length;
        }
        byte[] data = new byte[len + 5];
        data[0] = byteId;
        byte[] encodedLength = bytesFromUInt32(len);
        System.arraycopy(encodedLength, 0, data, 1, 4);
        int offset = 5;
        for (Encoded encoded: list) {
            System.arraycopy(encoded.data, 0, data, offset, encoded.data.length);
            offset += encoded.data.length;
        }
        return new Encoded(data);
    }

    public static Encoded of(HashMap<DictionaryKey, Encoded> dict) {
        int len = 0;
        for (Map.Entry<DictionaryKey, Encoded> entry : dict.entrySet()) {
            len += 5 + entry.getKey().data.length;
            len += entry.getValue().data.length;
        }
        byte[] data = new byte[len + 5];
        data[0] = BYTE_IDS_DICTIONARY;
        byte[] encodedLength = bytesFromUInt32(len);
        System.arraycopy(encodedLength, 0, data, 1, 4);
        int offset = 5;
        for (Map.Entry<DictionaryKey, Encoded> entry : dict.entrySet()) {
            Encoded encodedKey = Encoded.of(entry.getKey().data);
            System.arraycopy(encodedKey.data, 0, data, offset, encodedKey.data.length);
            offset += encodedKey.data.length;
            Encoded encodedValue = entry.getValue();
            System.arraycopy(encodedValue.data, 0, data, offset, encodedValue.data.length);
            offset += encodedValue.data.length;
        }
        return new Encoded(data);
    }

    public boolean isEncodedValue() {
        int len = uint32FromBytes(data, 1);
        if (len+5 != data.length) {
            return false;
        }
        switch (data[0]) {
            case BYTE_IDS_BYTE_ARRAY:
            case BYTE_IDS_INT:
            case BYTE_IDS_BOOLEAN:
            case BYTE_IDS_LIST:
            case BYTE_IDS_DICTIONARY:
            case BYTE_IDS_BIG_UINT:
            case BYTE_IDS_SYM_KEY:
            case BYTE_IDS_PUB_KEY:
            case BYTE_IDS_PRV_KEY:
                return true;
            default:
                return false;
        }
    }
    // endregion

    // region Decoders

    public byte[] decodeBytes() throws DecodingException {
        if (data[0] != BYTE_IDS_BYTE_ARRAY) {
            throw new DecodingException();
        }
        if (!isEncodedValue()) {
            throw new DecodingException();
        }
        return Arrays.copyOfRange(data, 5, data.length);
    }

    public String decodeString() throws DecodingException {
        return new String(decodeBytes(), StandardCharsets.UTF_8);
    }

    public UID decodeUid() throws DecodingException {
        return new UID(decodeBytes());
    }

    public Identity decodeIdentity() throws DecodingException {
        return Identity.of(decodeBytes());
    }

    public Seed decodeSeed() throws DecodingException {
        return new Seed(decodeBytes());
    }

    public UUID decodeUuid() throws DecodingException {
        return UUID.fromString(decodeString());
    }

    public EncryptedBytes decodeEncryptedData() throws DecodingException {
        return new EncryptedBytes(decodeBytes());
    }

    public long decodeLong() throws DecodingException {
        if (data[0] != BYTE_IDS_INT) {
            throw new DecodingException();
        }
        if ((data.length != 5 + INT_ENCODING_LENGTH) || !isEncodedValue()) {
            throw new DecodingException();
        }
        long res = 0;
        for (int i=0; i<INT_ENCODING_LENGTH; i++) {
            res = res << 8;
            res += data[i+5] & 0xff;
        }
        return res;
    }

    public boolean decodeBoolean() throws DecodingException {
        if (data[0] != BYTE_IDS_BOOLEAN) {
            throw new DecodingException();
        }
        if ((data.length != 5 + 1) || !isEncodedValue()) {
            throw new DecodingException();
        }
        switch (data[5]) {
            case (byte) 0x00:
                return false;
            case (byte) 0x01:
                return true;
            default:
                throw new DecodingException();
        }
    }

    public PublicKey decodePublicKey() throws DecodingException {
        if (data[0] != BYTE_IDS_PUB_KEY || !isEncodedValue()) { throw new DecodingException(); }
        Encoded[] list = unpack();
        if (list.length != 2) { throw new DecodingException(); }
        byte[] algoBytes = list[0].decodeBytes();
        if (algoBytes.length != 2) { throw new DecodingException(); }
        HashMap<DictionaryKey, Encoded> key = list[1].decodeDictionary();
        return PublicKey.of(algoBytes[0], algoBytes[1], key);
    }

    public PrivateKey decodePrivateKey() throws DecodingException {
        if (data[0] != BYTE_IDS_PRV_KEY || !isEncodedValue()) { throw new DecodingException(); }
        Encoded[] list = unpack();
        if (list.length != 2) { throw new DecodingException(); }
        byte[] algoBytes = list[0].decodeBytes();
        if (algoBytes.length != 2) { throw new DecodingException(); }
        HashMap<DictionaryKey, Encoded> key = list[1].decodeDictionary();
        return PrivateKey.of(algoBytes[0], algoBytes[1], key);
    }

    public SymmetricKey decodeSymmetricKey() throws DecodingException {
        if (data[0] != BYTE_IDS_SYM_KEY || !isEncodedValue()) { throw new DecodingException(); }
        Encoded[] list = unpack();
        if (list.length != 2) { throw new DecodingException(); }
        byte[] algoBytes = list[0].decodeBytes();
        if (algoBytes.length != 2) { throw new DecodingException(); }
        HashMap<DictionaryKey, Encoded> key = list[1].decodeDictionary();
        return SymmetricKey.of(algoBytes[0], algoBytes[1], key);
    }


    public BigInteger decodeBigUInt() throws DecodingException {
        if (data[0] != BYTE_IDS_BIG_UINT || !isEncodedValue()) {
            throw new DecodingException();
        }
        return new BigInteger(1, Arrays.copyOfRange(data, 5, data.length));
    }

    public static BigInteger bigUIntFromBytes(byte[] data) {
        return new BigInteger(1, data);
    }


    // used to decode a list with some additional bytes at the end
    public Encoded[] decodeListWithPadding() throws DecodingException {
        if (data[0] != BYTE_IDS_LIST) {
            throw new DecodingException();
        }
        int totalLen = uint32FromBytes(data, 1);
        if (totalLen+5 > data.length) {
            throw new DecodingException();
        }

        List<Encoded> list = new ArrayList<>();
        int offset = 5;
        while (offset  + 4 < totalLen+5) {
            int len = uint32FromBytes(data, offset+1);
            if (offset + 5 + len > totalLen+5) {
                throw new DecodingException();
            }
            Encoded elem = new Encoded(Arrays.copyOfRange(data, offset, offset+5+len));
            list.add(elem);
            offset += 5 + len;
        }
        return list.toArray(new Encoded[0]);
    }

    public Encoded[] decodeList() throws DecodingException {
        if (data[0] != BYTE_IDS_LIST || !isEncodedValue()) {
            throw new DecodingException();
        }
        return unpack();
    }

    private Encoded[] unpack() throws DecodingException {
        List<Encoded> list = new ArrayList<>();
        int offset = 5;
        while (offset  + 4 < data.length) {
            int len = uint32FromBytes(data, offset+1);
            if (offset + 5 + len > data.length) {
                throw new DecodingException();
            }
            Encoded elem = new Encoded(Arrays.copyOfRange(data, offset, offset+5+len));
            list.add(elem);
            offset += 5 + len;
        }
        return list.toArray(new Encoded[0]);
    }

    public HashMap<DictionaryKey, Encoded> decodeDictionary() throws DecodingException {
        if (data[0] != BYTE_IDS_DICTIONARY || !isEncodedValue()) {
            throw new DecodingException();
        }
        HashMap<DictionaryKey, Encoded> dict = new HashMap<>();
        int offset = 5;
        while (offset + 4 < data.length) {
            int len = uint32FromBytes(data, offset +1);
            if (offset + 5 + len > data.length) {
                throw new DecodingException();
            }
            // Here we do two copyOfRange -> this could be optimized to a single one, assuming we reimplement the decodeBytes checks
            DictionaryKey key = new DictionaryKey(new Encoded(Arrays.copyOfRange(data, offset, offset+5+len)).decodeBytes());
            offset += 5 + len;

            if (offset + 5 > data.length) {
                throw new DecodingException();
            }
            len = uint32FromBytes(data, offset +1);
            if (offset + 5 + len > data.length) {
                throw new DecodingException();
            }
            Encoded value = new Encoded(Arrays.copyOfRange(data, offset, offset+5+len));
            offset += 5 + len;
            dict.put(key, value);
        }
        return dict;
    }

    public UID[] decodeUidArray() throws DecodingException {
        Encoded[] encodedUids = decodeList();
        UID[] uids = new UID[encodedUids.length];
        for (int i=0; i<uids.length; i++) {
            uids[i] = encodedUids[i].decodeUid();
        }
        return uids;
    }

    public Identity[] decodeIdentityArray() throws DecodingException {
        Encoded[] encodedIdentities = decodeList();
        Identity[] identities = new Identity[encodedIdentities.length];
        for (int i=0; i<identities.length; i++) {
            identities[i] = encodedIdentities[i].decodeIdentity();
        }
        return identities;
    }

    public String[] decodeStringArray() throws DecodingException {
        Encoded[] encodedStrings = decodeList();
        String[] strings = new String[encodedStrings.length];
        for (int i=0; i<strings.length; i++) {
            strings[i] = encodedStrings[i].decodeString();
        }
        return strings;
    }
    // endregion

    // region Utility

    static byte[] bytesFromUInt32(int length) {
        byte[] res = new byte[4];
        for (int i=0; i<4; i++) {
            res[3-i] = (byte)(length & 0xff);
            length = length >>> 8;
        }
        return res;
    }

    static int uint32FromBytes(byte[] bytes) {
        return uint32FromBytes(bytes, 0);
    }

    static int uint32FromBytes(byte[] bytes, int offset) {
        int res = 0;
        for (int i=0; i<4; i++) {
            res = res << 8;
            res += bytes[i+offset] & 0xff;
        }
        return res;
    }

    // endregion

}


