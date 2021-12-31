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

import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePublicKey;
import io.olvid.engine.encoder.Encoded;

import static org.junit.Assert.*;

public class AsymmetricCryptoUnitTest {
    static class TestVector {
        String plaintext;
        String ciphertext;
        String seed;
        int algorithmImplementationByteIdValue;
        String encodedPublicKey;
        String encodedRecipientPrivateKey;
        String encodedPrivateKey;
        String challenge;
        String response;

        public String getEncodedPrivateKey() {
            return encodedPrivateKey;
        }

        public void setEncodedPrivateKey(String encodedPrivateKey) {
            this.encodedPrivateKey = encodedPrivateKey;
        }

        public String getChallenge() {
            return challenge;
        }

        public String getResponse() {
            return response;
        }

        public String getPlaintext() {
            return plaintext;
        }

        public String getCiphertext() {
            return ciphertext;
        }

        public String getSeed() {
            return seed;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public void setPlaintext(String plaintext) {
            this.plaintext = plaintext;
        }

        public void setCiphertext(String ciphertext) {
            this.ciphertext = ciphertext;
        }

        public void setSeed(String seed) {
            this.seed = seed;
        }

        public int getAlgorithmImplementationByteIdValue() {
            return algorithmImplementationByteIdValue;
        }

        public void setAlgorithmImplementationByteIdValue(int algorithmImplementationByteIdValue) {
            this.algorithmImplementationByteIdValue = algorithmImplementationByteIdValue;
        }

        public String getEncodedPublicKey() {
            return encodedPublicKey;
        }

        public void setEncodedPublicKey(String encodedPublicKey) {
            this.encodedPublicKey = encodedPublicKey;
        }

        public String getEncodedRecipientPrivateKey() {
            return encodedRecipientPrivateKey;
        }

        public void setEncodedRecipientPrivateKey(String encodedRecipientPrivateKey) {
            this.encodedRecipientPrivateKey = encodedRecipientPrivateKey;
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

    @Before
    public void setUp() throws Exception {
        Logger.setOutputLogLevel(Logger.DEBUG);
    }



    @Test
    public void test_ServerAuthentication() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsServerAuthentication.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                PRNGService prng = PRNGServiceHmacSHA256.getInstance();
                ServerAuthenticationECSdsaPublicKey pk = (ServerAuthenticationECSdsaPublicKey) new Encoded(fromHex(vec.encodedPublicKey)).decodePublicKey();
                ServerAuthenticationECSdsaPrivateKey sk = (ServerAuthenticationECSdsaPrivateKey) new Encoded(fromHex(vec.encodedPrivateKey)).decodePrivateKey();
                byte[] challenge = fromHex(vec.challenge);
                byte[] expectedResponse = fromHex(vec.response);
                ServerAuthentication serverAuthentication = Suite.getServerAuthentication(pk);
                prng.reseed(new Seed(fromHex(vec.seed)));
                byte[] response = serverAuthentication.solveChallenge(challenge, sk, pk, prng);
                assertArrayEquals(response, expectedResponse);

                byte[] signature = Arrays.copyOfRange(response, ServerAuthenticationECSdsa.PADDING_LENGTH, response.length);
                byte[] formattedChallenge = new byte[ServerAuthenticationECSdsa.PREFIX.length + challenge.length + ServerAuthenticationECSdsa.PADDING_LENGTH];
                System.arraycopy(ServerAuthenticationECSdsa.PREFIX, 0, formattedChallenge, 0, ServerAuthenticationECSdsa.PREFIX.length);
                System.arraycopy(challenge, 0, formattedChallenge, ServerAuthenticationECSdsa.PREFIX.length, challenge.length);
                System.arraycopy(response, 0, formattedChallenge, ServerAuthenticationECSdsa.PREFIX.length + challenge.length, ServerAuthenticationECSdsa.PADDING_LENGTH);
                SignaturePublicKey signaturePublicKey = pk.getSignaturePublicKey();
                Signature signatureImplem = Suite.getSignature(signaturePublicKey);
                assertTrue(signatureImplem.verify(signaturePublicKey, formattedChallenge, signature));
            }
        }
    }
}
