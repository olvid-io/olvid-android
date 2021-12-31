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

package io.olvid.engine.crypto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.math.BigInteger;
import java.net.URL;
import java.util.Arrays;

import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.symmetric.AuthEncAES256ThenSHA256Key;
import io.olvid.engine.datatypes.key.symmetric.MACHmacSha256Key;
import io.olvid.engine.datatypes.key.symmetric.SymEncCTRAES256Key;

import static org.junit.Assert.*;


public class SymmetricCryptoUnitTest {
    static class TestVector {
        String iv;
        String key;
        String plaintext;
        String ciphertext;
        String inputMessagePart;
        int numberOfRepetition;
        String digest;
        String mac;
        String data;
        String seed;
        String[] bounds;
        String[] values;
        String entropyInput;
        String nonce;
        String personalizationString;
        String[] generatedBytes;

        public String getIv() {return this.iv;}
        public void setIv(String iv) {this.iv = iv;}
        public String getKey() {return this.key;}
        public void setKey(String key) {this.key = key;}
        public String getPlaintext() {return this.plaintext;}
        public void setPlaintext(String plaintext) {this.plaintext = plaintext;}
        public String getCiphertext() {return this.ciphertext;}
        public void setCiphertext(String ciphertext) {this.ciphertext = ciphertext;}
        public String getInputMessagePart() {return this.inputMessagePart;}
        public void setInputMessagePart(String inputMessagePart) {this.inputMessagePart = inputMessagePart;}
        public int getNumberOfRepetition() {return numberOfRepetition;}
        public void setNumberOfRepetition(int numberOfRepetition) {this.numberOfRepetition = numberOfRepetition;}
        public String getDigest() {return digest;}
        public void setDigest(String digest) {this.digest = digest;}
        public String getData() {return data;}
        public void setData(String data) {this.data = data;}
        public String getMac() {return mac;}
        public void setMac(String mac) {this.mac = mac;}
        public String getSeed() {return seed;}
        public void setSeed(String seed) {this.seed = seed;}
        public String[] getBounds() {return bounds;}
        public void setBounds(String[] bounds) {this.bounds = bounds;}
        public String[] getValues() {return values;}
        public void setValues(String[] values) {this.values = values;}
        public String getEntropyInput() {return entropyInput;}
        public void setEntropyInput(String entropyInput) {this.entropyInput = entropyInput;}
        public String getNonce() {return nonce;}
        public void setNonce(String nonce) {this.nonce = nonce;}
        public String getPersonalizationString() {return personalizationString;}
        public void setPersonalizationString(String personalizationString) {this.personalizationString = personalizationString;}
        public String[] getGeneratedBytes() {return generatedBytes;}
        public void setGeneratedBytes(String[] generatedBytes) {this.generatedBytes = generatedBytes;}
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
    public void test_AuthEncAES256ThenSHA256() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsAuthenticatedEncryptionWithAES256CTRThenHMACWithSHA256.json");
        JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
        parser.nextToken();
        parser.nextToken();
        MappingIterator iter = mapper.readValues(parser, TestVector.class);
        while (iter.hasNext()) {
            TestVector vec = (TestVector) iter.next();
            AuthEncAES256ThenSHA256 enc = new AuthEncAES256ThenSHA256();
            byte[] keyBytes = fromHex(vec.key);
            AuthEncAES256ThenSHA256Key key = AuthEncAES256ThenSHA256Key.of(Arrays.copyOfRange(keyBytes, 0, 32), Arrays.copyOfRange(keyBytes, 32, 64));
            PRNGServiceHmacSHA256 prng = PRNGServiceHmacSHA256.getInstance();
            prng.reseed(new Seed(fromHex(vec.seed)));
            assertArrayEquals(fromHex(vec.ciphertext), enc.encrypt(key, fromHex(vec.plaintext), prng).getBytes());

            enc = new AuthEncAES256ThenSHA256();
            assertArrayEquals(enc.decrypt(key, new EncryptedBytes(fromHex(vec.ciphertext))), fromHex(vec.plaintext));
        }
    }

    @Test
    public void test_HashSHA256() throws Exception {
        {
            byte[] inp1 = {};
            byte[] out2 = { (byte) 0xa0,(byte) 0xad,(byte) 0xbe,(byte) 0xdf,(byte) 0xf0,(byte) 0x84,(byte) 0x7c,(byte) 0x62,(byte) 0xfd,(byte) 0xd5,(byte) 0xe0,(byte) 0xde,(byte) 0xda,(byte) 0xaa,(byte) 0x70,(byte) 0xbb,(byte) 0xce,(byte) 0xe4,(byte) 0x0d,(byte) 0xbf,(byte) 0xce,(byte) 0x83,(byte) 0x75,(byte) 0x08,(byte) 0x26,(byte) 0x74,(byte) 0x12,(byte) 0x93,(byte) 0xc7,(byte) 0x8c,(byte) 0x25,(byte) 0xac};
            byte[] out1 = { (byte) 0xe3,(byte) 0xb0,(byte) 0xc4,(byte) 0x42,(byte) 0x98,(byte) 0xfc,(byte) 0x1c,(byte) 0x14,(byte) 0x9a,(byte) 0xfb,(byte) 0xf4,(byte) 0xc8,(byte) 0x99,(byte) 0x6f,(byte) 0xb9,(byte) 0x24,(byte) 0x27,(byte) 0xae,(byte) 0x41,(byte) 0xe4,(byte) 0x64,(byte) 0x9b,(byte) 0x93,(byte) 0x4c,(byte) 0xa4,(byte) 0x95,(byte) 0x99,(byte) 0x1b,(byte) 0x78,(byte) 0x52,(byte) 0xb8,(byte) 0x55};
            byte[] inp4 = { (byte) 0x74};
            byte[] out3 = { (byte) 0x1e,(byte) 0x27,(byte) 0xc6,(byte) 0x04,(byte) 0xfe,(byte) 0x31,(byte) 0x05,(byte) 0x76,(byte) 0xce,(byte) 0x3e,(byte) 0x7f,(byte) 0xa1,(byte) 0x80,(byte) 0x4c,(byte) 0xd6,(byte) 0xb7,(byte) 0x3e,(byte) 0x8c,(byte) 0xbb,(byte) 0xab,(byte) 0xe8,(byte) 0x8a,(byte) 0x00,(byte) 0x72,(byte) 0xe0,(byte) 0x4c,(byte) 0x93,(byte) 0x0f,(byte) 0x41,(byte) 0xae,(byte) 0xca,(byte) 0x56};
            byte[] inp2 = { (byte) 0x6a,(byte) 0x89,(byte) 0xe0,(byte) 0xed,(byte) 0xa9,(byte) 0xb9,(byte) 0x01,(byte) 0xe5,(byte) 0xf3,(byte) 0xf5,(byte) 0x11,(byte) 0x0e,(byte) 0xe9,(byte) 0x67,(byte) 0xce,(byte) 0x6c,(byte) 0x34,(byte) 0xfe,(byte) 0x0d,(byte) 0x3d,(byte) 0x60,(byte) 0x33,(byte) 0x14,(byte) 0x5b,(byte) 0x8b,(byte) 0x63,(byte) 0x4e,(byte) 0x04,(byte) 0xa0,(byte) 0x65,(byte) 0x0a,(byte) 0xab};
            byte[] inp3 = { (byte) 0xea,(byte) 0x83,(byte) 0x6b,(byte) 0xa0,(byte) 0x7c,(byte) 0xa1,(byte) 0x3e,(byte) 0xb2,(byte) 0x87,(byte) 0xbc,(byte) 0x47,(byte) 0x22,(byte) 0xf7,(byte) 0x34,(byte) 0xc6,(byte) 0x4d,(byte) 0x7c,(byte) 0x7a,(byte) 0x4c,(byte) 0xe7,(byte) 0x6e,(byte) 0x89,(byte) 0x02,(byte) 0x9e,(byte) 0x60,(byte) 0x9f,(byte) 0x4e,(byte) 0xe4,(byte) 0x04,(byte) 0xea,(byte) 0x36,(byte) 0x94,(byte) 0xff,(byte) 0xf7,(byte) 0x00,(byte) 0x4a,(byte) 0xa2,(byte) 0x68,(byte) 0x45,(byte) 0xf9,(byte) 0xec,(byte) 0x94,(byte) 0x7d,(byte) 0x91,(byte) 0xa2,(byte) 0x2b,(byte) 0x01,(byte) 0xcd,(byte) 0x51,(byte) 0x2b,(byte) 0x54,(byte) 0x62,(byte) 0xee,(byte) 0x80,(byte) 0x4f,(byte) 0xd6,(byte) 0xce,(byte) 0xb2,(byte) 0x60,(byte) 0x29,(byte) 0xad,(byte) 0x1e,(byte) 0x6b,(byte) 0xf4,(byte) 0x03,(byte) 0x0f,(byte) 0x55,(byte) 0x83,(byte) 0xce,(byte) 0x0b,(byte) 0xf9,(byte) 0xa7,(byte) 0xa8,(byte) 0x5a,(byte) 0x3a,(byte) 0x5c,(byte) 0x04,(byte) 0x2b,(byte) 0xab,(byte) 0x5c,(byte) 0x82,(byte) 0x40,(byte) 0xf6,(byte) 0x3a,(byte) 0xb1,(byte) 0xae,(byte) 0xc8,(byte) 0x96,(byte) 0x3d,(byte) 0xe1,(byte) 0x82,(byte) 0xb1,(byte) 0x61,(byte) 0x31,(byte) 0x41,(byte) 0x7d,(byte) 0xdb,(byte) 0xfd,(byte) 0x33,(byte) 0x4d,(byte) 0xc4,(byte) 0x2e,(byte) 0x6f,(byte) 0xac,(byte) 0x7f,(byte) 0x30,(byte) 0xb6,(byte) 0x71,(byte) 0x3a,(byte) 0x4e,(byte) 0x48,(byte) 0xbf,(byte) 0x19,(byte) 0x2f,(byte) 0x67,(byte) 0x61,(byte) 0xf7,(byte) 0x10,(byte) 0x27,(byte) 0x6c,(byte) 0xa8,(byte) 0x1d,(byte) 0xa7,(byte) 0xc2,(byte) 0xe9,(byte) 0x53,(byte) 0xa6,(byte) 0x88,(byte) 0xe1,(byte) 0x3e,(byte) 0xa9,(byte) 0x4d,(byte) 0x08,(byte) 0xb3,(byte) 0x5d,(byte) 0x76,(byte) 0x5c,(byte) 0xa4,(byte) 0x61,(byte) 0x9f,(byte) 0x01,(byte) 0x19,(byte) 0xee,(byte) 0xf9,(byte) 0x9b,(byte) 0x87,(byte) 0xe3,(byte) 0xc6,(byte) 0x0c,(byte) 0x2c,(byte) 0x77,(byte) 0x0c,(byte) 0x11,(byte) 0xe1,(byte) 0xd1,(byte) 0x87,(byte) 0x25,(byte) 0x34,(byte) 0x35,(byte) 0x7c,(byte) 0x06,(byte) 0xd2,(byte) 0xa1,(byte) 0x7d,(byte) 0xc1,(byte) 0x1e,(byte) 0x45,(byte) 0x83,(byte) 0xa6,(byte) 0xa6,(byte) 0x81,(byte) 0xce,(byte) 0x32,(byte) 0xf7,(byte) 0x2f,(byte) 0x4f,(byte) 0xfa,(byte) 0xd2,(byte) 0x8e,(byte) 0x06,(byte) 0xce,(byte) 0x5a,(byte) 0x5c,(byte) 0xa6,(byte) 0x51,(byte) 0xed,(byte) 0xb9,(byte) 0xa9,(byte) 0xbf,(byte) 0xc7,(byte) 0x93,(byte) 0x87,(byte) 0x50,(byte) 0x12,(byte) 0xd8,(byte) 0x75,(byte) 0x07,(byte) 0xac,(byte) 0x6e,(byte) 0x4c,(byte) 0x1a,(byte) 0x62,(byte) 0x2d,(byte) 0xc9,(byte) 0x3a,(byte) 0xed,(byte) 0x8f,(byte) 0x86,(byte) 0x27,(byte) 0xe2,(byte) 0xf0,(byte) 0xdd,(byte) 0x6b,(byte) 0x73,(byte) 0xa7,(byte) 0xea,(byte) 0xaa,(byte) 0x41,(byte) 0xa3,(byte) 0x20,(byte) 0x8a,(byte) 0x38,(byte) 0x80,(byte) 0xc7,(byte) 0x71,(byte) 0xbb,(byte) 0x3a,(byte) 0xfc,(byte) 0xe3,(byte) 0x46,(byte) 0x23,(byte) 0x24,(byte) 0xfa,(byte) 0x4b,(byte) 0x1f,(byte) 0x83,(byte) 0x98,(byte) 0xec,(byte) 0xd8,(byte) 0x61,(byte) 0xed,(byte) 0x90,(byte) 0x10,(byte) 0x82,(byte) 0x9a,(byte) 0x37,(byte) 0xea,(byte) 0xf1,(byte) 0xa1,(byte) 0xfa,(byte) 0x52,(byte) 0xce,(byte) 0x1b,(byte) 0xef,(byte) 0x0d,(byte) 0x62,(byte) 0x39,(byte) 0xd3,(byte) 0x3f,(byte) 0xbb,(byte) 0x0d,(byte) 0xf2,(byte) 0xd9,(byte) 0x1f,(byte) 0xc0,(byte) 0x12,(byte) 0x56,(byte) 0x48,(byte) 0xa9,(byte) 0xd7,(byte) 0x87,(byte) 0xeb,(byte) 0x95,(byte) 0x0d,(byte) 0x71,(byte) 0x2b,(byte) 0x19,(byte) 0x72,(byte) 0xf0,(byte) 0xa1,(byte) 0x86,(byte) 0xa0,(byte) 0x3d,(byte) 0xa5,(byte) 0x3b,(byte) 0xd4,(byte) 0x13,(byte) 0xc3,(byte) 0x02,(byte) 0x72,(byte) 0x29,(byte) 0x34,(byte) 0xa5,(byte) 0x74,(byte) 0x96,(byte) 0x64,(byte) 0x02,(byte) 0x94,(byte) 0x6a,(byte) 0xd9,(byte) 0xd5,(byte) 0x89,(byte) 0xbb,(byte) 0xce,(byte) 0x42,(byte) 0xbc,(byte) 0xdd,(byte) 0x30,(byte) 0xc1,(byte) 0x6b,(byte) 0x82,(byte) 0xc0,(byte) 0x07,(byte) 0x2d,(byte) 0x98,(byte) 0xc3,(byte) 0x39,(byte) 0x8a,(byte) 0x49,(byte) 0xf7,(byte) 0x3c,(byte) 0x7b,(byte) 0xbd,(byte) 0x1f,(byte) 0x68,(byte) 0x98,(byte) 0xb5,(byte) 0xe7,(byte) 0xf0,(byte) 0xd0,(byte) 0x41,(byte) 0xb4,(byte) 0x34,(byte) 0xb6,(byte) 0x7c,(byte) 0xe0,(byte) 0x11,(byte) 0x53,(byte) 0x08,(byte) 0x57,(byte) 0xc2,(byte) 0x7c,(byte) 0xfd,(byte) 0x9a,(byte) 0x87,(byte) 0x48,(byte) 0x51,(byte) 0xc7,(byte) 0x1d,(byte) 0x7b,(byte) 0x87,(byte) 0xea,(byte) 0x66,(byte) 0xb1,(byte) 0x1e,(byte) 0xda,(byte) 0x6d,(byte) 0x9a,(byte) 0xa2,(byte) 0xcd,(byte) 0x81,(byte) 0xbe,(byte) 0x1a,(byte) 0xa3,(byte) 0x2d,(byte) 0x4e,(byte) 0x48,(byte) 0xda,(byte) 0x4e,(byte) 0xec,(byte) 0xc5,(byte) 0x7e,(byte) 0xdd,(byte) 0x73,(byte) 0x12,(byte) 0x54,(byte) 0xac,(byte) 0x02,(byte) 0x7f,(byte) 0x0a,(byte) 0xaf,(byte) 0xd3,(byte) 0x3d,(byte) 0xda,(byte) 0xd8,(byte) 0x96,(byte) 0x3f,(byte) 0xa6,(byte) 0x71,(byte) 0x21,(byte) 0x5a,(byte) 0xb7,(byte) 0x22,(byte) 0xd1,(byte) 0x06,(byte) 0xae,(byte) 0xf8,(byte) 0xde,(byte) 0x40,(byte) 0xf7,(byte) 0xd3,(byte) 0x89,(byte) 0xa8,(byte) 0x3b,(byte) 0xe5,(byte) 0xb5,(byte) 0x8e,(byte) 0x86,(byte) 0x48,(byte) 0xd9,(byte) 0x89,(byte) 0xe2,(byte) 0xbc,(byte) 0xe8,(byte) 0xd0,(byte) 0x3b,(byte) 0x5d,(byte) 0x70,(byte) 0x1f,(byte) 0x04,(byte) 0x34,(byte) 0x5a,(byte) 0x56,(byte) 0x9f,(byte) 0xc0,(byte) 0xe7,(byte) 0xf6,(byte) 0x99,(byte) 0xe1,(byte) 0x82,(byte) 0xeb,(byte) 0x7c,(byte) 0xb9,(byte) 0xeb,(byte) 0xa6,(byte) 0xb1,(byte) 0x6d,(byte) 0xe4,(byte) 0xa4,(byte) 0x5e,(byte) 0xfe,(byte) 0x3a,(byte) 0xf1,(byte) 0xd0,(byte) 0x25,(byte) 0xb9,(byte) 0xbc,(byte) 0xf6,(byte) 0xf2,(byte) 0x2a,(byte) 0x55,(byte) 0x5b,(byte) 0xcd,(byte) 0x44,(byte) 0x04,(byte) 0x87,(byte) 0xd1,(byte) 0x51,(byte) 0x88,(byte) 0x4d,(byte) 0x63,(byte) 0x48,(byte) 0x07,(byte) 0x7c,(byte) 0x34,(byte) 0x8d,(byte) 0x3b,(byte) 0xd4,(byte) 0x44,(byte) 0xba,(byte) 0x32,(byte) 0x8c,(byte) 0x86,(byte) 0xbc,(byte) 0x64,(byte) 0x64,(byte) 0xc1,(byte) 0x49,(byte) 0x8d,(byte) 0x32,(byte) 0x72,(byte) 0xb5,(byte) 0x52,(byte) 0x3b,(byte) 0xd8,(byte) 0xd1,(byte) 0x7c,(byte) 0x42,(byte) 0xe3,(byte) 0xac,(byte) 0xdb,(byte) 0xb8,(byte) 0x9e,(byte) 0xe8,(byte) 0xe8,(byte) 0xda,(byte) 0x9d,(byte) 0xb9,(byte) 0x16,(byte) 0x80,(byte) 0x88,(byte) 0x32,(byte) 0x85,(byte) 0xaf,(byte) 0x52,(byte) 0x65,(byte) 0xc6,(byte) 0x3b,(byte) 0x2a,(byte) 0xa0,(byte) 0x6a,(byte) 0xac,(byte) 0xdf,(byte) 0x78,(byte) 0x48,(byte) 0xaf,(byte) 0xb7,(byte) 0xd3,(byte) 0x19,(byte) 0x59,(byte) 0x95,(byte) 0x03,(byte) 0xfa,(byte) 0x8d,(byte) 0x2e,(byte) 0x32,(byte) 0x49,(byte) 0xbc,(byte) 0xab,(byte) 0x0d,(byte) 0x79,(byte) 0x20,(byte) 0x64,(byte) 0xbe,(byte) 0xc0,(byte) 0xc9,(byte) 0x40,(byte) 0xaf,(byte) 0xa2,(byte) 0x72,(byte) 0x1b,(byte) 0xfa,(byte) 0x2e,(byte) 0xd6,(byte) 0x76,(byte) 0x29,(byte) 0x7a,(byte) 0x1a,(byte) 0x22,(byte) 0xfd,(byte) 0xb0,(byte) 0x41,(byte) 0xb2,(byte) 0x2b,(byte) 0x1e,(byte) 0x56,(byte) 0x19,(byte) 0x5f,(byte) 0xd5,(byte) 0x6f,(byte) 0x00,(byte) 0x4c,(byte) 0xd3,(byte) 0x1d,(byte) 0x50,(byte) 0x01,(byte) 0x17,(byte) 0xde,(byte) 0x8d,(byte) 0x07,(byte) 0x10,(byte) 0x3c,(byte) 0x17,(byte) 0x4b,(byte) 0x1a,(byte) 0xed,(byte) 0xfa,(byte) 0xe4,(byte) 0xd0,(byte) 0x2c,(byte) 0xa0,(byte) 0xa6,(byte) 0xd2,(byte) 0x55,(byte) 0x65,(byte) 0x7e,(byte) 0xf1,(byte) 0x43,(byte) 0x8e,(byte) 0xb3,(byte) 0x97,(byte) 0x5a,(byte) 0xde,(byte) 0xdb,(byte) 0xd7,(byte) 0x27,(byte) 0x04,(byte) 0x1b,(byte) 0x15,(byte) 0x56,(byte) 0x1e,(byte) 0x60,(byte) 0x26,(byte) 0xde,(byte) 0xaa,(byte) 0xab,(byte) 0x25,(byte) 0x9b,(byte) 0x44,(byte) 0xd0,(byte) 0xe3,(byte) 0x47,(byte) 0x2e,(byte) 0x98,(byte) 0x09,(byte) 0x4f,(byte) 0xa6,(byte) 0xe7,(byte) 0x1c,(byte) 0x9b,(byte) 0xf5,(byte) 0x93,(byte) 0xe5,(byte) 0x75,(byte) 0x70,(byte) 0xdd,(byte) 0x79,(byte) 0xe6,(byte) 0x6f,(byte) 0x39,(byte) 0xaa,(byte) 0xb8,(byte) 0x17,(byte) 0x2d,(byte) 0x05,(byte) 0x18,(byte) 0xdc,(byte) 0x0c,(byte) 0x15,(byte) 0xc2,(byte) 0x94,(byte) 0x13,(byte) 0xf2,(byte) 0x95,(byte) 0xe5,(byte) 0x6e,(byte) 0xc9,(byte) 0x9b,(byte) 0x08,(byte) 0xf1,(byte) 0x4e,(byte) 0x9f,(byte) 0xc7,(byte) 0xf8,(byte) 0x0a,(byte) 0xa9,(byte) 0x5b,(byte) 0x90,(byte) 0x44,(byte) 0x31,(byte) 0x25,(byte) 0xa2,(byte) 0xed,(byte) 0x28,(byte) 0x1e,(byte) 0x78,(byte) 0x52,(byte) 0x1b,(byte) 0xcd,(byte) 0x69,(byte) 0xb6,(byte) 0x9a,(byte) 0x9f,(byte) 0xf0,(byte) 0x37,(byte) 0x4f,(byte) 0xc5,(byte) 0xbd,(byte) 0x09,(byte) 0x21,(byte) 0xeb,(byte) 0x8c,(byte) 0x86,(byte) 0x1c,(byte) 0xd0,(byte) 0xee,(byte) 0x33,(byte) 0x93,(byte) 0x30,(byte) 0xb5,(byte) 0x50,(byte) 0xe6,(byte) 0x92,(byte) 0x56,(byte) 0xe8,(byte) 0x15,(byte) 0xe4,(byte) 0x2a,(byte) 0x83,(byte) 0x9f,(byte) 0xd6,(byte) 0xc0,(byte) 0x57,(byte) 0x07,(byte) 0xb3,(byte) 0x50,(byte) 0xed,(byte) 0x30,(byte) 0x88,(byte) 0x39,(byte) 0xf4,(byte) 0xb4,(byte) 0x64,(byte) 0xa2,(byte) 0xe2,(byte) 0xaa,(byte) 0xdd,(byte) 0x35,(byte) 0x6b,(byte) 0x09,(byte) 0x1e,(byte) 0xc9,(byte) 0xcb,(byte) 0xeb,(byte) 0xc4,(byte) 0xcc,(byte) 0x49,(byte) 0x00,(byte) 0xf1,(byte) 0x9a,(byte) 0x00,(byte) 0xcc,(byte) 0xe1,(byte) 0x75,(byte) 0x71,(byte) 0xb3,(byte) 0x15,(byte) 0x16,(byte) 0x76,(byte) 0x60,(byte) 0x77,(byte) 0x19,(byte) 0xc8,(byte) 0x3e,(byte) 0x5d,(byte) 0xf2,(byte) 0x67,(byte) 0xd7,(byte) 0xba,(byte) 0xf8,(byte) 0x03,(byte) 0xf3,(byte) 0x74,(byte) 0x0f,(byte) 0xb9,(byte) 0x29,(byte) 0x93,(byte) 0x2e,(byte) 0x16,(byte) 0xfb,(byte) 0x71,(byte) 0x5d,(byte) 0x08,(byte) 0x46,(byte) 0x52,(byte) 0xf4,(byte) 0x46,(byte) 0xd4,(byte) 0x71,(byte) 0x2c,(byte) 0x45,(byte) 0xa5,(byte) 0x75,(byte) 0xd4,(byte) 0xea,(byte) 0x25,(byte) 0x46,(byte) 0xd0,(byte) 0x6a,(byte) 0xdf,(byte) 0x10,(byte) 0x61,(byte) 0x27,(byte) 0xfa,(byte) 0x2c,(byte) 0x68,(byte) 0xb4,(byte) 0x04,(byte) 0xcb,(byte) 0xec,(byte) 0x16,(byte) 0xad,(byte) 0x55,(byte) 0x56,(byte) 0xaf,(byte) 0x36,(byte) 0x9e,(byte) 0xa3,(byte) 0x72,(byte) 0x7d,(byte) 0xd9,(byte) 0x74,(byte) 0xc1,(byte) 0xcc,(byte) 0x54,(byte) 0xe6,(byte) 0x42,(byte) 0xd1,(byte) 0xca,(byte) 0x6c,(byte) 0x2b,(byte) 0xb0,(byte) 0x7e,(byte) 0x09,(byte) 0xce,(byte) 0xfc,(byte) 0xc3,(byte) 0xb6,(byte) 0x6f,(byte) 0x9a,(byte) 0x61,(byte) 0xa6,(byte) 0x79,(byte) 0xdf,(byte) 0xf3,(byte) 0xa2,(byte) 0x01,(byte) 0x4d,(byte) 0xcd,(byte) 0x26,(byte) 0xdc,(byte) 0x61,(byte) 0x79,(byte) 0x5b,(byte) 0x6b,(byte) 0x16,(byte) 0x36,(byte) 0xca,(byte) 0x10,(byte) 0xba,(byte) 0x78,(byte) 0x23,(byte) 0x3e,(byte) 0x75,(byte) 0x6f,(byte) 0x3e,(byte) 0xa1,(byte) 0xfc,(byte) 0xb7,(byte) 0x65,(byte) 0x3f,(byte) 0x58,(byte) 0xb5,(byte) 0x3f,(byte) 0x2a,(byte) 0x1a,(byte) 0xc4,(byte) 0x07,(byte) 0x62,(byte) 0x2a,(byte) 0x7d,(byte) 0x73,(byte) 0xeb,(byte) 0x8c,(byte) 0xd4,(byte) 0x96,(byte) 0x88,(byte) 0xef,(byte) 0x19,(byte) 0x5a,(byte) 0xb6,(byte) 0x1f,(byte) 0xd2,(byte) 0x1a,(byte) 0x49,(byte) 0x09,(byte) 0x29,(byte) 0x2c,(byte) 0x55,(byte) 0xd9,(byte) 0x43,(byte) 0x2d,(byte) 0xd4,(byte) 0xcb,(byte) 0xc9,(byte) 0xf7,(byte) 0x83,(byte) 0xbf,(byte) 0x22,(byte) 0xa6,(byte) 0x0b,(byte) 0x06,(byte) 0x83,(byte) 0x8d,(byte) 0x92,(byte) 0x56,(byte) 0xf2,(byte) 0xb1,(byte) 0x39,(byte) 0x02,(byte) 0x43,(byte) 0xb3,(byte) 0x25,(byte) 0x59,(byte) 0x98,(byte) 0xd8,(byte) 0xb0,(byte) 0x9b,(byte) 0xf6,(byte) 0x91,(byte) 0x56,(byte) 0x5d,(byte) 0x05,(byte) 0xe6,(byte) 0x5e,(byte) 0xa0,(byte) 0x0d,(byte) 0xae,(byte) 0xda,(byte) 0xe6,(byte) 0x6d,(byte) 0x8c,(byte) 0x46,(byte) 0x1a,(byte) 0xfb,(byte) 0xa8,(byte) 0xb1,(byte) 0x20,(byte) 0x14,(byte) 0xf4,(byte) 0xea,(byte) 0x8b,(byte) 0x03,(byte) 0x93,(byte) 0x74,(byte) 0xa3,(byte) 0x25,(byte) 0xcf,(byte) 0x13,(byte) 0xd3,(byte) 0x37,(byte) 0x13,(byte) 0x21,(byte) 0x57,(byte) 0x1c,(byte) 0x2f,(byte) 0x17,(byte) 0x03,(byte) 0x27,(byte) 0x39,(byte) 0x7f,(byte) 0xd6,(byte) 0x7b,(byte) 0x8a,(byte) 0x4b,(byte) 0x10,(byte) 0x34,(byte) 0x4e,(byte) 0xd7,(byte) 0x3e,(byte) 0x39,(byte) 0x91,(byte) 0x16,(byte) 0x87,(byte) 0x62,(byte) 0x04,(byte) 0x62,(byte) 0x93,(byte) 0x1a,(byte) 0x98,(byte) 0xa0,(byte) 0x3a,(byte) 0xbd,(byte) 0xf2,(byte) 0xc2,(byte) 0xbd,(byte) 0x1d,(byte) 0xc8,(byte) 0x33,(byte) 0x89,(byte) 0x45,(byte) 0x26,(byte) 0x07,(byte) 0x09,(byte) 0xbd,(byte) 0x63,(byte) 0x71,(byte) 0x95,(byte) 0x3c,(byte) 0xc1,(byte) 0x88,(byte) 0x9b,(byte) 0xf9,(byte) 0x58,(byte) 0x73,(byte) 0x25,(byte) 0x64,(byte) 0xf7,(byte) 0xd9,(byte) 0xd5,(byte) 0x17,(byte) 0x8c,(byte) 0xfb,(byte) 0x6a,(byte) 0xc3,(byte) 0x2a,(byte) 0xcc,(byte) 0x90,(byte) 0x95,(byte) 0xaf,(byte) 0xe1,(byte) 0x34,(byte) 0x11,(byte) 0x78,(byte) 0x61,(byte) 0x96,(byte) 0xe5,(byte) 0xc1,(byte) 0x69,(byte) 0x8c,(byte) 0x70,(byte) 0x8b,(byte) 0x68,(byte) 0x8c};
            byte[] out4 = { (byte) 0xe3,(byte) 0xb9,(byte) 0x8a,(byte) 0x4d,(byte) 0xa3,(byte) 0x1a,(byte) 0x12,(byte) 0x7d,(byte) 0x4b,(byte) 0xde,(byte) 0x6e,(byte) 0x43,(byte) 0x03,(byte) 0x3f,(byte) 0x66,(byte) 0xba,(byte) 0x27,(byte) 0x4c,(byte) 0xab,(byte) 0x0e,(byte) 0xb7,(byte) 0xeb,(byte) 0x1c,(byte) 0x70,(byte) 0xec,(byte) 0x41,(byte) 0x40,(byte) 0x2b,(byte) 0xf6,(byte) 0x27,(byte) 0x3d,(byte) 0xd8};
            HashSHA256 hash = new HashSHA256();
            assertArrayEquals(out1, hash.digest(inp1));
            assertArrayEquals(out2, hash.digest(inp2));
            assertArrayEquals(out3, hash.digest(inp3));
            assertArrayEquals(out4, hash.digest(inp4));
        }

        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsSHA256.json");
        JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
        parser.nextToken();
        parser.nextToken();
        MappingIterator iter = mapper.readValues(parser, TestVector.class);
        while (iter.hasNext()) {
            TestVector vec = (TestVector) iter.next();
            HashSHA256 hash = new HashSHA256();
            assertEquals(vec.numberOfRepetition, 1);
            assertArrayEquals(fromHex(vec.digest), hash.digest(vec.inputMessagePart.getBytes()));
        }
    }

    @Test
    public void test_MACHmacSha256() throws Exception {
        {
            byte[] inp = {(byte) 0xb8, (byte) 0x9b, (byte) 0x24, (byte) 0x82, (byte) 0x28, (byte) 0x73, (byte) 0x17, (byte) 0xad, (byte) 0x67, (byte) 0x09, (byte) 0x3b, (byte) 0x8d, (byte) 0xee, (byte) 0x9c, (byte) 0x19, (byte) 0x5b, (byte) 0x5e, (byte) 0xee, (byte) 0xd9, (byte) 0xf8, (byte) 0x36, (byte) 0x51, (byte) 0x6a, (byte) 0xf7, (byte) 0x6d, (byte) 0x15, (byte) 0x7d, (byte) 0x1a, (byte) 0xd3, (byte) 0x16, (byte) 0x36, (byte) 0x79,};
            byte[] mac = {(byte) 0x08, (byte) 0xf8, (byte) 0x54, (byte) 0x20, (byte) 0x8a, (byte) 0x95, (byte) 0xc7, (byte) 0x0a, (byte) 0x17, (byte) 0x31, (byte) 0xcb, (byte) 0x3a, (byte) 0xe0, (byte) 0xa0, (byte) 0xe5, (byte) 0xcf, (byte) 0x82, (byte) 0x92, (byte) 0x30, (byte) 0x57, (byte) 0xb6, (byte) 0x93, (byte) 0xa1, (byte) 0x49, (byte) 0x87, (byte) 0x6a, (byte) 0x12, (byte) 0xfb, (byte) 0xec, (byte) 0xde, (byte) 0x69, (byte) 0xa8,};
            byte[] key = {(byte) 0x1b, (byte) 0xe2, (byte) 0x08, (byte) 0x84, (byte) 0xe6, (byte) 0x63, (byte) 0xe2, (byte) 0x4f, (byte) 0xb8, (byte) 0x4c, (byte) 0xeb, (byte) 0x8c, (byte) 0x68, (byte) 0xab, (byte) 0xc7, (byte) 0x2a, (byte) 0x54, (byte) 0x0d, (byte) 0xff, (byte) 0x01, (byte) 0x52, (byte) 0xcd, (byte) 0xd7, (byte) 0x84, (byte) 0xa1, (byte) 0x6b, (byte) 0x82, (byte) 0x6f, (byte) 0xc6, (byte) 0x9f, (byte) 0x70, (byte) 0xad,};
            assertArrayEquals(mac, new MACHmacSha256().digest(MACHmacSha256Key.of(key), inp));
        }
        {
            byte[] inp = {(byte) 0x9e, (byte) 0xed, (byte) 0x61, (byte) 0x6d, (byte) 0x50, (byte) 0xa3, (byte) 0x81, (byte) 0xdc, (byte) 0x3f, (byte) 0xa8, (byte) 0xaf, (byte) 0x02, (byte) 0xbb, (byte) 0x79, (byte) 0x73, (byte) 0xfa, (byte) 0x6a, (byte) 0x87, (byte) 0x55, (byte) 0xd7, (byte) 0xdc, (byte) 0x0c, (byte) 0x55, (byte) 0x81, (byte) 0x1d, (byte) 0xe1, (byte) 0x0a, (byte) 0x17, (byte) 0x51, (byte) 0x6a, (byte) 0xda, (byte) 0x9c,};
            byte[] key = {(byte) 0x45, (byte) 0xa8, (byte) 0xad, (byte) 0x2e, (byte) 0xa7, (byte) 0x9a, (byte) 0xd1, (byte) 0xc7, (byte) 0x71, (byte) 0x14, (byte) 0x7a, (byte) 0x94, (byte) 0xc9, (byte) 0x26, (byte) 0x49, (byte) 0x70, (byte) 0x7b, (byte) 0xbf, (byte) 0xad, (byte) 0xee, (byte) 0xd7, (byte) 0x81, (byte) 0x07, (byte) 0x16, (byte) 0xad, (byte) 0xf0, (byte) 0x91, (byte) 0x83, (byte) 0x89, (byte) 0x32, (byte) 0xc1, (byte) 0xdf,};
            byte[] mac = {(byte) 0x4b, (byte) 0xd4, (byte) 0xd7, (byte) 0x11, (byte) 0x6b, (byte) 0x00, (byte) 0xf0, (byte) 0x4f, (byte) 0x7e, (byte) 0x07, (byte) 0xe2, (byte) 0xa0, (byte) 0x0a, (byte) 0xf3, (byte) 0xbf, (byte) 0xa1, (byte) 0x02, (byte) 0x6a, (byte) 0xa7, (byte) 0x72, (byte) 0xb9, (byte) 0xbd, (byte) 0xf5, (byte) 0x49, (byte) 0x4b, (byte) 0xd4, (byte) 0x9a, (byte) 0xc7, (byte) 0x03, (byte) 0x40, (byte) 0x6d, (byte) 0x73,};
            assertArrayEquals(mac, new MACHmacSha256().digest(MACHmacSha256Key.of(key), inp));
        }
        {
            byte[] inp = {(byte) 0x32, (byte) 0x85, (byte) 0x27, (byte) 0x68, (byte) 0xab, (byte) 0x54, (byte) 0xa1, (byte) 0x67, (byte) 0x9d, (byte) 0x81, (byte) 0x60, (byte) 0x4b, (byte) 0xcc, (byte) 0xb6, (byte) 0xdf, (byte) 0x3c, (byte) 0xe2, (byte) 0xd9, (byte) 0x01, (byte) 0x59, (byte) 0xf0, (byte) 0x5e, (byte) 0x66, (byte) 0x46, (byte) 0x02, (byte) 0x5b, (byte) 0x06, (byte) 0xad, (byte) 0xf1, (byte) 0x85, (byte) 0xe3, (byte) 0x09,};
            byte[] mac = {(byte) 0x97, (byte) 0x6e, (byte) 0x97, (byte) 0xc5, (byte) 0x58, (byte) 0x68, (byte) 0x64, (byte) 0x37, (byte) 0x96, (byte) 0x29, (byte) 0xf0, (byte) 0x04, (byte) 0x22, (byte) 0x99, (byte) 0xa7, (byte) 0xfe, (byte) 0x3d, (byte) 0xf3, (byte) 0x2b, (byte) 0xd8, (byte) 0x83, (byte) 0x61, (byte) 0x77, (byte) 0x2b, (byte) 0x3f, (byte) 0xe0, (byte) 0xb8, (byte) 0x8f, (byte) 0x57, (byte) 0x25, (byte) 0x0f, (byte) 0x60,};
            byte[] key = {(byte) 0x6c, (byte) 0x16, (byte) 0x99, (byte) 0x4d, (byte) 0xc4, (byte) 0xc0, (byte) 0xd9, (byte) 0x78, (byte) 0x21, (byte) 0x24, (byte) 0x2e, (byte) 0x6b, (byte) 0x57, (byte) 0x39, (byte) 0x6c, (byte) 0x90, (byte) 0x45, (byte) 0xad, (byte) 0x80, (byte) 0x11, (byte) 0x03, (byte) 0xd5, (byte) 0x03, (byte) 0xb5, (byte) 0x6d, (byte) 0x92, (byte) 0xb7, (byte) 0x21, (byte) 0xdd, (byte) 0x58, (byte) 0x7e, (byte) 0xcd,};
            assertArrayEquals(mac, new MACHmacSha256().digest(MACHmacSha256Key.of(key), inp));
        }
        {
            byte[] key = {(byte) 0xfc, (byte) 0x2c, (byte) 0xb3, (byte) 0x5d, (byte) 0x67, (byte) 0x42, (byte) 0x5a, (byte) 0xfb, (byte) 0x83, (byte) 0x44, (byte) 0x73, (byte) 0x04, (byte) 0xd6, (byte) 0xb8, (byte) 0x4f, (byte) 0xb9, (byte) 0x01, (byte) 0x0e, (byte) 0xa0, (byte) 0x56, (byte) 0x95, (byte) 0x7d, (byte) 0xae, (byte) 0x4e, (byte) 0x51, (byte) 0x49, (byte) 0x6c, (byte) 0x83, (byte) 0x50, (byte) 0xbd, (byte) 0xb9, (byte) 0xe6,};
            byte[] inp = {(byte) 0x15, (byte) 0x9c, (byte) 0xf2, (byte) 0x4c, (byte) 0x5f, (byte) 0xb0, (byte) 0x3e, (byte) 0x28, (byte) 0xf6, (byte) 0xec, (byte) 0x0f, (byte) 0xef,};
            byte[] mac = {(byte) 0xcd, (byte) 0x93, (byte) 0xf3, (byte) 0x20, (byte) 0xda, (byte) 0xc4, (byte) 0x7c, (byte) 0x58, (byte) 0x3a, (byte) 0x01, (byte) 0xb6, (byte) 0x43, (byte) 0x45, (byte) 0x9d, (byte) 0xfa, (byte) 0x31, (byte) 0x18, (byte) 0xea, (byte) 0x2e, (byte) 0x95, (byte) 0x3a, (byte) 0x8d, (byte) 0x9c, (byte) 0x5f, (byte) 0xec, (byte) 0x18, (byte) 0x40, (byte) 0x65, (byte) 0xda, (byte) 0xc2, (byte) 0xe0, (byte) 0xfa,};
            assertArrayEquals(mac, new MACHmacSha256().digest(MACHmacSha256Key.of(key), inp));
        }
        {
            byte[] key = {(byte) 0xea, (byte) 0x6b, (byte) 0x89, (byte) 0x32, (byte) 0x27, (byte) 0x89, (byte) 0x79, (byte) 0x87, (byte) 0xa5, (byte) 0x38, (byte) 0x9b, (byte) 0x28, (byte) 0x42, (byte) 0xfa, (byte) 0x25, (byte) 0x52, (byte) 0xc7, (byte) 0x41, (byte) 0x70, (byte) 0x03, (byte) 0x74, (byte) 0x32, (byte) 0x40, (byte) 0x5f, (byte) 0x58, (byte) 0x24, (byte) 0xc0, (byte) 0x9c, (byte) 0x63, (byte) 0x32, (byte) 0xda, (byte) 0x69,};
            byte[] inp = {(byte) 0xe3, (byte) 0xf1, (byte) 0x37, (byte) 0xc5, (byte) 0x9c, (byte) 0x70, (byte) 0x20, (byte) 0x2a, (byte) 0xfd, (byte) 0x19, (byte) 0x8c, (byte) 0x2d, (byte) 0x9e, (byte) 0x56, (byte) 0xd0, (byte) 0x51, (byte) 0x11, (byte) 0xbe, (byte) 0x94, (byte) 0x21, (byte) 0x0a, (byte) 0x6e, (byte) 0xac, (byte) 0x8f, (byte) 0xa6, (byte) 0xc7, (byte) 0x30, (byte) 0x1f, (byte) 0x5e, (byte) 0x5b, (byte) 0x90, (byte) 0x13, (byte) 0xae, (byte) 0x5e, (byte) 0x65, (byte) 0x07, (byte) 0xc2, (byte) 0x7d, (byte) 0x9a, (byte) 0x74, (byte) 0xdd, (byte) 0x90, (byte) 0xb3, (byte) 0x09, (byte) 0x5b, (byte) 0x63, (byte) 0xf2, (byte) 0x23, (byte) 0x68, (byte) 0xd5, (byte) 0xd2, (byte) 0x1f, (byte) 0x8c, (byte) 0x5e, (byte) 0x10, (byte) 0x49, (byte) 0xcd, (byte) 0xbe, (byte) 0xe6, (byte) 0xa4, (byte) 0x0d, (byte) 0x3e, (byte) 0xa4, (byte) 0x22, (byte) 0xd0, (byte) 0x98, (byte) 0x34, (byte) 0x28, (byte) 0x9d, (byte) 0xab, (byte) 0x6a, (byte) 0x72, (byte) 0xee, (byte) 0xa2, (byte) 0x13, (byte) 0xc3, (byte) 0x0b, (byte) 0x35, (byte) 0x05, (byte) 0x66, (byte) 0x6b, (byte) 0xad, (byte) 0xd8, (byte) 0x6b, (byte) 0x86, (byte) 0x48, (byte) 0x82, (byte) 0x9c, (byte) 0xfd, (byte) 0x46, (byte) 0x90, (byte) 0x1d, (byte) 0xee, (byte) 0x0b, (byte) 0xd1, (byte) 0x63, (byte) 0xd8, (byte) 0x08, (byte) 0x2a, (byte) 0xdf, (byte) 0xfe, (byte) 0x68, (byte) 0xa0, (byte) 0xc6, (byte) 0xa7, (byte) 0xf0, (byte) 0x92, (byte) 0xf9, (byte) 0x12, (byte) 0xc1, (byte) 0xe5, (byte) 0x4e, (byte) 0x39, (byte) 0x3b, (byte) 0x00, (byte) 0xba, (byte) 0x25, (byte) 0xed, (byte) 0x09, (byte) 0xf0, (byte) 0xb5, (byte) 0x39, (byte) 0x06, (byte) 0x4c, (byte) 0x03, (byte) 0xce, (byte) 0x50,};
            byte[] mac = {(byte) 0x87, (byte) 0x34, (byte) 0x18, (byte) 0x95, (byte) 0xb8, (byte) 0xec, (byte) 0x38, (byte) 0x84, (byte) 0x05, (byte) 0xa8, (byte) 0xba, (byte) 0x89, (byte) 0xbc, (byte) 0x7a, (byte) 0x57, (byte) 0xe7, (byte) 0xa7, (byte) 0x25, (byte) 0xe8, (byte) 0x12, (byte) 0x07, (byte) 0xb0, (byte) 0x2c, (byte) 0xd8, (byte) 0xde, (byte) 0x19, (byte) 0xf3, (byte) 0x53, (byte) 0x3a, (byte) 0x05, (byte) 0xfe, (byte) 0x4f,};
            assertArrayEquals(mac, new MACHmacSha256().digest(MACHmacSha256Key.of(key), inp));
        }


        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsHMACWithSHA256.json");
        JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
        parser.nextToken();
        parser.nextToken();
        MappingIterator iter = mapper.readValues(parser, TestVector.class);
        while (iter.hasNext()) {
            TestVector vec = (TestVector) iter.next();
            assertArrayEquals(fromHex(vec.mac), new MACHmacSha256().digest(MACHmacSha256Key.of(fromHex(vec.key)), fromHex(vec.data)));
        }
    }

    @Test
    public void test_PRNGHmacSHA256() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPRNGWithHMACWithSHA256.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                byte[] entropyInput = fromHex(vec.entropyInput);
                byte[] nonce = fromHex(vec.nonce);
                byte[] personalizationString = fromHex(vec.personalizationString);
                byte[] seed = new byte[entropyInput.length + nonce.length + personalizationString.length];
                System.arraycopy(entropyInput, 0, seed, 0, entropyInput.length);
                System.arraycopy(nonce, 0, seed, entropyInput.length, nonce.length);
                System.arraycopy(personalizationString, 0, seed, entropyInput.length + nonce.length, personalizationString.length);
                PRNGHmacSHA256 prng = new PRNGHmacSHA256(new Seed(seed));
                for (int i = 0; i < vec.generatedBytes.length; i++) {
                    byte[] out = fromHex(vec.generatedBytes[i]);
                    assertArrayEquals(out, prng.bytes(out.length));
                }
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPRNGGenBigInt.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                PRNGHmacSHA256 prng = new PRNGHmacSHA256(new Seed(fromHex(vec.seed)));
                for (int i = 0; i < vec.values.length; i++) {
                    assertEquals(new BigInteger(vec.values[i]), prng.bigInt(new BigInteger(vec.bounds[i])));
                }
            }
        }
    }

    @Test
    public void test_PRNGServiceHmacSHA256() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPRNGWithHMACWithSHA256.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                byte[] entropyInput = fromHex(vec.entropyInput);
                byte[] nonce = fromHex(vec.nonce);
                byte[] personalizationString = fromHex(vec.personalizationString);
                byte[] seed = new byte[entropyInput.length + nonce.length + personalizationString.length];
                System.arraycopy(entropyInput, 0, seed, 0, entropyInput.length);
                System.arraycopy(nonce, 0, seed, entropyInput.length, nonce.length);
                System.arraycopy(personalizationString, 0, seed, entropyInput.length + nonce.length, personalizationString.length);
                PRNGServiceHmacSHA256 prng = PRNGServiceHmacSHA256.getInstance();
                prng.reseed(new Seed(seed));
                for (int i = 0; i < vec.generatedBytes.length; i++) {
                    byte[] out = fromHex(vec.generatedBytes[i]);
                    assertArrayEquals(out, prng.bytes(out.length));
                }
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPRNGGenBigInt.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                PRNGServiceHmacSHA256 prng = PRNGServiceHmacSHA256.getInstance();
                prng.reseed(new Seed(fromHex(vec.seed)));
                for (int i = 0; i < vec.values.length; i++) {
                    assertEquals(new BigInteger(vec.values[i]), prng.bigInt(new BigInteger(vec.bounds[i])));
                }
            }
        }
    }

    @Test
    public void test_SymEncCtrAES256() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsAES256CTR.json");
        JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
        parser.nextToken();
        parser.nextToken();
        MappingIterator iter = mapper.readValues(parser, TestVector.class);
        while (iter.hasNext()) {
            TestVector vec = (TestVector) iter.next();
            SymEncCtrAES256 enc = new SymEncCtrAES256(SymEncCTRAES256Key.of(fromHex(vec.key)));
            byte[] cipher = new byte[enc.ciphertextLengthFromPlaintextLength(fromHex(vec.plaintext).length)];
            enc.encrypt(fromHex(vec.iv), fromHex(vec.plaintext), cipher);
            assertArrayEquals(fromHex(vec.ciphertext), cipher);

            byte[] plain = enc.decrypt(new EncryptedBytes(fromHex(vec.ciphertext)));
            assertArrayEquals(fromHex(vec.plaintext), plain);

        }
    }

    @Test
    public void test_CommitmentSHA256() throws Exception {
        Commitment commitmentScheme = new CommitmentWithSHA256();
        PRNGService prng = Suite.getDefaultPRNGService(0);
        final int TAG_LENGTH = 50;
        final int VALUE_LENGTH = 75;
        for (int i=0; i<500; i++) {
            byte[] tag = prng.bytes(TAG_LENGTH);
            byte[] value = prng.bytes(VALUE_LENGTH);
            Commitment.CommitmentOutput commitmentOutput = commitmentScheme.commit(tag, value, prng);

            byte[] opened = commitmentScheme.open(tag, commitmentOutput.commitment, commitmentOutput.decommitment);
            assertArrayEquals(value, opened);
        }
    }

}