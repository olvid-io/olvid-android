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

package io.olvid.engine.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.containers.CiphertextAndKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCKeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;

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

                byte[] signature = Arrays.copyOfRange(response, Constants.SIGNATURE_PADDING_LENGTH, response.length);
                byte[] formattedChallenge = new byte[Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length + challenge.length + Constants.SIGNATURE_PADDING_LENGTH];
                System.arraycopy(Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX, 0, formattedChallenge, 0, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length);
                System.arraycopy(challenge, 0, formattedChallenge, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length, challenge.length);
                System.arraycopy(response, 0, formattedChallenge, Constants.SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX.length + challenge.length, Constants.SIGNATURE_PADDING_LENGTH);
                SignaturePublicKey signaturePublicKey = pk.getSignaturePublicKey();
                Signature signatureImplem = Suite.getSignature(signaturePublicKey);
                assertTrue(signatureImplem.verify(signaturePublicKey, formattedChallenge, signature));
            }
        }
    }

    @Test
    public void test_kemDecrypt() throws Exception {
        PRNGService prng = Suite.getDefaultPRNGService(0);
        for (int i=0; i<10; i++) {
            EncryptionEciesMDCKeyPair pair = EncryptionEciesMDCKeyPair.generate(prng);
            KemEcies256Kem512MDC kem = new KemEcies256Kem512MDC();
            for (int j = 0; j<100; j++) {
                CiphertextAndKey ciphertextAndKey = kem.encrypt(pair.getPublicKey(), prng);
                AuthEncKey dec = kem.decrypt(pair.getPrivateKey(), ciphertextAndKey.getCiphertext().getBytes());
                assertEquals(ciphertextAndKey.getKey(), dec);
            }
        }
    }
}
