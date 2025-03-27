/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.util.HashMap;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesPublicKey;


public class EncoderUnitTest {
    static class testVector {
        byte algorithmImplementationByteIdValue;
        String encodedPublicKey;
        String encodedPrivateKey;
        String xCoordinate;
        String yCoordinate;
        String scalar;

        public void setAlgorithmImplementationByteIdValue(byte algorithmImplementationByteIdValue) {
            this.algorithmImplementationByteIdValue = algorithmImplementationByteIdValue;
        }

        public void setEncodedPublicKey(String encodedPublicKey) {
            this.encodedPublicKey = encodedPublicKey;
        }

        public void setEncodedPrivateKey(String encodedPrivateKey) {
            this.encodedPrivateKey = encodedPrivateKey;
        }

        public void setxCoordinate(String xCoordinate) {
            this.xCoordinate = xCoordinate;
        }

        public void setyCoordinate(String yCoordinate) {
            this.yCoordinate = yCoordinate;
        }

        public void setScalar(String scalar) {
            this.scalar = scalar;
        }

        public byte getAlgorithmImplementationByteIdValue() {
            return algorithmImplementationByteIdValue;
        }

        public String getEncodedPublicKey() {
            return encodedPublicKey;
        }

        public String getEncodedPrivateKey() {
            return encodedPrivateKey;
        }

        public String getxCoordinate() {
            return xCoordinate;
        }

        public String getyCoordinate() {
            return yCoordinate;
        }

        public String getScalar() {
            return scalar;
        }
    }

    static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len/2];
        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    @Test
    public void test_encodeEncryptionKey() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsEncodeEncryptionEciesPublicKey.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, testVector.class);
            while (iter.hasNext()) {
                testVector vec = (testVector) iter.next();
                HashMap<DictionaryKey, Encoded> key = new HashMap<>();
                key.put(new DictionaryKey(EncryptionEciesPublicKey.PUBLIC_X_COORD_KEY_NAME), Encoded.of(new BigInteger(vec.getxCoordinate()), 32));
                key.put(new DictionaryKey(EncryptionEciesPublicKey.PUBLIC_Y_COORD_KEY_NAME), Encoded.of(new BigInteger(vec.getyCoordinate()), 32));
                EncryptionEciesPublicKey publicKey = (EncryptionEciesPublicKey) EncryptionEciesPublicKey.of(vec.algorithmImplementationByteIdValue, key);

                EncryptionEciesPublicKey decodedPublicKey = (EncryptionEciesPublicKey) new Encoded(fromHex(vec.encodedPublicKey)).decodePublicKey();
                assertEquals(publicKey.getClass(), decodedPublicKey.getClass());
                assertEquals(publicKey.getAx(), decodedPublicKey.getAx());
                assertEquals(publicKey.getAy(), decodedPublicKey.getAy());
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsEncodeEncryptionEciesPrivateKey.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, testVector.class);
            while (iter.hasNext()) {
                testVector vec = (testVector) iter.next();
                HashMap<DictionaryKey, Encoded> key = new HashMap<>();
                key.put(new DictionaryKey(EncryptionEciesPrivateKey.SECRET_EXPONENT_KEY_NAME), Encoded.of(new BigInteger(vec.getScalar()), 32));
                EncryptionEciesPrivateKey privateKey = (EncryptionEciesPrivateKey) EncryptionEciesPrivateKey.of(vec.algorithmImplementationByteIdValue, key);

                EncryptionEciesPrivateKey decodedPrivateKey = (EncryptionEciesPrivateKey) new Encoded(fromHex(vec.encodedPrivateKey)).decodePrivateKey();
                assertEquals(privateKey.getClass(), decodedPrivateKey.getClass());
                assertEquals(privateKey.getA(), decodedPrivateKey.getA());
            }
        }
    }


    @Test
    public void test_encodeBigUInt() throws Exception {
        SecureRandom rand = new SecureRandom();
        for (int i=0; i<1024; i++) {
            int len = rand.nextInt(513);
            BigInteger r = new BigInteger(len, rand);
            assertEquals(r, Encoded.of(r, 1+(len-1)/8).decodeBigUInt());
        }
        byte[] expected = {(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x03};
        assertArrayEquals(expected, Encoded.of(BigInteger.valueOf(3), 2).data);
    }

    @Test
    public void test_encodeBytes() throws Exception {
        SecureRandom rand = new SecureRandom();
        for (int i=0; i<1024; i++) {
            int len = rand.nextInt(100);
            byte[] m = new byte[len];
            rand.nextBytes(m);
            assertArrayEquals(m, Encoded.of(m).decodeBytes());
        }
        byte[] src = {};
        byte[] expected = {(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00};
        assertArrayEquals(expected, Encoded.of(src).data);
        byte[] src2 = {1, 2, 3, 4, 5};
        byte[] expected2 = {0x00, 0x00, 0x00, 0x00, 0x05, 1, 2, 3, 4, 5};
        assertArrayEquals(expected2, Encoded.of(src2).data);
    }

}